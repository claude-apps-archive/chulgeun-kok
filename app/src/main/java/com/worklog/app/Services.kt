package com.worklog.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.time.LocalDate
import java.util.Locale

// ---------- 권한 ----------

object Perms {
    fun has(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    fun location(ctx: Context) = has(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
    fun background(ctx: Context) = Build.VERSION.SDK_INT < 29 || has(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    fun notifications(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    fun overlay(ctx: Context) = Settings.canDrawOverlays(ctx)
    fun fullScreen(ctx: Context) =
        Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    fun battery(ctx: Context) =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    fun exactAlarm(ctx: Context) =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun coreOk(ctx: Context) = location(ctx) && background(ctx) && notifications(ctx) && overlay(ctx)
}

// ---------- 위치 / 지오펜스 ----------

data class GeoHit(val label: String, val lat: Double, val lng: Double)

object Geo {
    private fun pi(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 7, Intent(ctx, GeofenceReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
    )

    @SuppressLint("MissingPermission")
    fun registerAll(ctx: Context) {
        if (!Perms.location(ctx) || !Perms.background(ctx)) return
        val client = LocationServices.getGeofencingClient(ctx)
        val fences = Repo.get(ctx).workplaces.filter { it.hasLocation }.map { wp ->
            Geofence.Builder()
                .setRequestId(wp.id)
                .setCircularRegion(wp.lat!!, wp.lng!!, wp.radius.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .setNotificationResponsiveness(30_000)
                .build()
        }
        client.removeGeofences(pi(ctx)).addOnCompleteListener {
            if (fences.isEmpty()) return@addOnCompleteListener
            val req = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(fences)
                .build()
            try {
                client.addGeofences(req, pi(ctx))
            } catch (_: SecurityException) {
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun currentLocation(ctx: Context, cb: (Pair<Double, Double>?) -> Unit) {
        if (!Perms.location(ctx)) return cb(null)
        LocationServices.getFusedLocationProviderClient(ctx)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { loc -> cb(loc?.let { it.latitude to it.longitude }) }
            .addOnFailureListener { cb(null) }
    }

    @Suppress("DEPRECATION")
    fun search(ctx: Context, q: String): List<GeoHit> = try {
        Geocoder(ctx, Locale.KOREA).getFromLocationName(q, 5)
            ?.map { GeoHit(it.getAddressLine(0) ?: q, it.latitude, it.longitude) }
            ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val ev = GeofencingEvent.fromIntent(intent) ?: return
        if (ev.hasError()) return
        val ids = ev.triggeringGeofences?.map { it.requestId } ?: return
        val now = System.currentTimeMillis()
        when (ev.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> ids.forEach { Work.onEnter(ctx, it, now) }
            Geofence.GEOFENCE_TRANSITION_EXIT -> ids.forEach { Work.onExit(ctx, it, now) }
        }
    }
}

// ---------- 알람 ----------

object Alarms {
    const val ACTION_COUNTDOWN = "com.worklog.app.COUNTDOWN"
    const val ACTION_EXIT = "com.worklog.app.EXIT_CONFIRM"
    const val EX_WP = "wp"
    const val EX_DATE = "date"
    const val EX_END = "endAt"

    fun nextShiftEnd(wp: Workplace, base: Long): Pair<LocalDate, Long>? {
        val t = dateOf(base)
        for (off in -1L..60L) {
            val d = t.plusDays(off)
            if (wp.isWorkday(d)) {
                val e = wp.shiftEndAt(d)
                if (e > base) return d to e
            }
        }
        return null
    }

    fun scheduleAll(ctx: Context) = Repo.get(ctx).workplaces.forEach { scheduleFor(ctx, it) }

    /** 다음 퇴근 10분 전, 또는 카운트다운 중이면 다음 1분 지점에 알람 */
    fun scheduleFor(ctx: Context, wp: Workplace, base: Long = System.currentTimeMillis()) {
        val next = nextShiftEnd(wp, base)
        if (next == null) {
            cancelFor(ctx, wp.id)
            return
        }
        val (date, endAt) = next
        val k = (endAt - base) / 60_000L
        val at = if (k >= 10) endAt - 10 * 60_000L else endAt - k * 60_000L
        val intent = Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_COUNTDOWN)
            .putExtra(EX_WP, wp.id).putExtra(EX_DATE, date.toString()).putExtra(EX_END, endAt)
        val pi = PendingIntent.getBroadcast(
            ctx, ("cd" + wp.id).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        set(ctx, at, pi, alarmClock = k < 10)
    }

    fun cancelFor(ctx: Context, wpId: String) {
        val pi = PendingIntent.getBroadcast(
            ctx, ("cd$wpId").hashCode(),
            Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_COUNTDOWN),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        ctx.getSystemService(AlarmManager::class.java).cancel(pi)
    }

    private fun exitPI(ctx: Context, wpId: String, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        ctx, ("ex$wpId").hashCode(),
        Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_EXIT).putExtra(EX_WP, wpId),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    fun scheduleExitConfirm(ctx: Context, wpId: String, at: Long) {
        exitPI(ctx, wpId, PendingIntent.FLAG_UPDATE_CURRENT)?.let { set(ctx, at, it, alarmClock = false) }
    }

    fun cancelExitConfirm(ctx: Context, wpId: String) {
        exitPI(ctx, wpId, PendingIntent.FLAG_NO_CREATE)?.let {
            ctx.getSystemService(AlarmManager::class.java).cancel(it)
        }
    }

    private fun set(ctx: Context, at: Long, pi: PendingIntent, alarmClock: Boolean) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            when {
                // 1분 간격 카운트다운은 절전 모드에서도 밀리지 않도록 알람시계 방식 사용
                exactOk && alarmClock -> am.setAlarmClock(AlarmManager.AlarmClockInfo(at, Notifier.openApp(ctx)), pi)
                exactOk -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val wpId = intent.getStringExtra(Alarms.EX_WP) ?: return
        when (intent.action) {
            Alarms.ACTION_COUNTDOWN -> Work.onCountdown(
                ctx, wpId,
                intent.getStringExtra(Alarms.EX_DATE) ?: return,
                intent.getLongExtra(Alarms.EX_END, 0L),
            )
            Alarms.ACTION_EXIT -> Work.confirmExit(ctx, wpId)
        }
    }
}

// ---------- 알림 버튼 / 부팅 ----------

class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val CLOCK_IN = "com.worklog.app.CLOCK_IN"
        const val CLOCK_OUT = "com.worklog.app.CLOCK_OUT"
        const val OVERTIME = "com.worklog.app.OVERTIME"
        const val OVERTIME_DISMISSED = "com.worklog.app.OVERTIME_DISMISSED"
        const val LATER = "com.worklog.app.LATER"
        const val EX_WP = "wp"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val wpId = intent.getStringExtra(EX_WP) ?: return
        when (intent.action) {
            CLOCK_IN -> Work.clockIn(ctx, wpId)
            CLOCK_OUT -> Work.clockOut(ctx, wpId)
            OVERTIME -> Work.startOvertime(ctx, wpId)
            OVERTIME_DISMISSED -> Work.overtimeDismissed(ctx, wpId)
            LATER -> Notifier.cancel(ctx, wpId, Notifier.T_ARRIVE)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Work.restore(ctx)
    }
}
