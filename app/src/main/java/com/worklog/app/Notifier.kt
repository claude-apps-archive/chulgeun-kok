package com.worklog.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate

object Notifier {
    const val CH_ARRIVE = "arrive"
    const val CH_COUNT = "countdown"
    const val CH_END = "endwork"
    const val CH_OVER = "overtime"
    const val CH_INFO = "info"

    const val T_ARRIVE = 1
    const val T_COUNT = 2
    const val T_END = 3
    const val T_OVER = 4
    const val T_INFO = 5

    fun id(wpId: String, type: Int) = (wpId.hashCode() and 0x00FFFFFF) * 8 + type

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        fun ch(id: String, name: String, desc: String, imp: Int) =
            NotificationChannel(id, name, imp).apply {
                description = desc
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        nm.createNotificationChannels(
            listOf(
                ch(CH_ARRIVE, "출근 알림", "직장에 도착하면 출근할지 물어봐요", NotificationManager.IMPORTANCE_HIGH),
                ch(CH_COUNT, "퇴근 카운트다운", "퇴근 10분 전부터 1분마다 알려줘요", NotificationManager.IMPORTANCE_HIGH),
                ch(CH_END, "퇴근 알림", "퇴근 시간이 되면 알려줘요", NotificationManager.IMPORTANCE_HIGH),
                ch(CH_OVER, "연장 근무", "퇴근 버튼을 누를 때까지 떠 있어요", NotificationManager.IMPORTANCE_HIGH),
                ch(CH_INFO, "기록 알림", "출퇴근이 기록되면 알려줘요", NotificationManager.IMPORTANCE_DEFAULT),
            )
        )
    }

    fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(ctx: Context, action: String, wpId: String): PendingIntent = PendingIntent.getBroadcast(
        ctx, (action + wpId).hashCode(),
        Intent(ctx, ActionReceiver::class.java).setAction(action).putExtra(ActionReceiver.EX_WP, wpId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun popup(ctx: Context, mode: String, wpId: String): PendingIntent = PendingIntent.getActivity(
        ctx, ("p$mode$wpId").hashCode(),
        PopupActivity.intent(ctx, mode, wpId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun builder(ctx: Context, ch: String, name: String, color: Int) =
        NotificationCompat.Builder(ctx, ch)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(swatch(color).b.toArgb())
            .setSubText(name)
            .setContentIntent(openApp(ctx))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, id: Int, n: Notification) {
        val m = NotificationManagerCompat.from(ctx)
        if (m.areNotificationsEnabled()) runCatching { m.notify(id, n) }
    }

    fun cancel(ctx: Context, wpId: String, vararg types: Int) {
        val m = NotificationManagerCompat.from(ctx)
        types.forEach { m.cancel(id(wpId, it)) }
    }

    fun arrival(ctx: Context, wp: Workplace, workday: Boolean) {
        val b = builder(ctx, CH_ARRIVE, wp.name, wp.color)
            .setContentTitle(if (workday) "직장에 도착하였습니다." else "${wp.name}에 도착했어요")
            .setContentText(if (workday) "근무하시겠습니까?" else "오늘은 근무일이 아니에요. 근무하시겠습니까?")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .addAction(0, "출근", action(ctx, ActionReceiver.CLOCK_IN, wp.id))
            .addAction(0, "나중에", action(ctx, ActionReceiver.LATER, wp.id))
        if (workday) b.setFullScreenIntent(popup(ctx, PopupActivity.MODE_ARRIVE, wp.id), true)
        post(ctx, id(wp.id, T_ARRIVE), b.build())
    }

    /** 1분 뒤 저절로 사라지는 카운트다운 알림 */
    fun countdown(ctx: Context, wp: Workplace, minutes: Int) {
        val n = builder(ctx, CH_COUNT, wp.name, wp.color)
            .setContentTitle("퇴근까지 ${minutes}분 남았습니다.")
            .setContentText("${wp.name} · 퇴근 ${wp.end}")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setTimeoutAfter(55_000)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).cancel(id(wp.id, T_COUNT))
        post(ctx, id(wp.id, T_COUNT), n)
    }

    fun endPrompt(ctx: Context, wp: Workplace) {
        val n = builder(ctx, CH_END, wp.name, wp.color)
            .setContentTitle("퇴근시간이 되었습니다.")
            .setContentText("퇴근하시겠습니까?")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, "연장 근무", action(ctx, ActionReceiver.OVERTIME, wp.id))
            .addAction(0, "퇴근", action(ctx, ActionReceiver.CLOCK_OUT, wp.id))
            .setFullScreenIntent(popup(ctx, PopupActivity.MODE_END, wp.id), true)
            .build()
        post(ctx, id(wp.id, T_END), n)
    }

    /** 퇴근을 누르거나 직장을 떠날 때까지 계속 떠 있는 알림 */
    fun overtime(ctx: Context, wp: Workplace, rec: DayRecord) {
        val since = runCatching { wp.shiftEndAt(LocalDate.parse(rec.date)) }.getOrDefault(System.currentTimeMillis())
        val n = builder(ctx, CH_OVER, wp.name, wp.color)
            .setContentTitle("연장 근무를 하는 당신.")
            .setContentText("퇴근하시겠습니까?")
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setWhen(since)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .addAction(0, "퇴근", action(ctx, ActionReceiver.CLOCK_OUT, wp.id))
            .setDeleteIntent(action(ctx, ActionReceiver.OVERTIME_DISMISSED, wp.id))
            .build()
        post(ctx, id(wp.id, T_OVER), n)
    }

    fun info(ctx: Context, wp: Workplace, text: String) {
        val n = builder(ctx, CH_INFO, wp.name, wp.color)
            .setContentTitle(wp.name)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setTimeoutAfter(15_000)
            .setAutoCancel(true)
            .build()
        post(ctx, id(wp.id, T_INFO), n)
    }

    fun test(ctx: Context) {
        val n = builder(ctx, CH_ARRIVE, "출근콕", 5)
            .setContentTitle("알림 테스트")
            .setContentText("이 알림이 갤럭시 핏3에도 보이면 연동 완료!")
            .setAutoCancel(true)
            .addAction(0, "확인", action(ctx, ActionReceiver.LATER, "test"))
            .build()
        post(ctx, id("test", T_ARRIVE), n)
    }
}
