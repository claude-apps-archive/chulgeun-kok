package com.worklog.app

import android.content.Context
import android.location.Location
import java.time.LocalDate

/** 출퇴근 규칙: 지오펜스·알람·알림 버튼이 모두 여기로 모인다 */
object Work {

    fun restore(ctx: Context) {
        Notifier.ensureChannels(ctx)
        Geo.registerAll(ctx)
        Alarms.scheduleAll(ctx)
        val d = Repo.get(ctx)
        d.workplaces.forEach { wp ->
            d.activeRecord(wp.id)?.let { if (it.overtime) Notifier.overtime(ctx, wp, it) }
        }
    }

    /** 직장 반경 안으로 들어옴 */
    fun onEnter(ctx: Context, wpId: String, at: Long) {
        val d = Repo.get(ctx)
        val wp = d.wp(wpId) ?: return
        // GPS 가 잠깐 튀어서 나갔다 들어온 경우: 떠남 처리를 취소
        if (d.pendingExit.containsKey(wpId)) {
            Repo.update(ctx) { it.copy(pendingExit = it.pendingExit - wpId) }
            Alarms.cancelExitConfirm(ctx, wpId)
            return
        }
        if (d.activeRecord(wpId) != null) return
        if (!bestAtSamePlace(d, wp, at)) return
        val date = wp.shiftDateFor(at).toString()
        val rec = d.record(wpId, date)
        if (rec.end != null) return
        val fresh = rec.arrive == null || rec.leave != null
        if (rec.arrive == null) Repo.update(ctx) { it.withRecord(it.record(wpId, date).copy(arrive = at)) }
        val workday = wp.isWorkday(LocalDate.parse(date))
        Notifier.arrival(ctx, wp, workday)
        if (fresh && workday) PopupActivity.show(ctx, PopupActivity.MODE_ARRIVE, wpId)
    }

    /** 직장 반경 밖으로 나감. 몇 분 뒤에도 밖이면 떠난 것으로 확정 */
    fun onExit(ctx: Context, wpId: String, at: Long) {
        val d = Repo.get(ctx)
        if (d.wp(wpId) == null || d.pendingExit.containsKey(wpId)) return
        Repo.update(ctx) { it.copy(pendingExit = it.pendingExit + (wpId to at)) }
        Alarms.scheduleExitConfirm(ctx, wpId, at + d.exitDelayMin * 60_000L)
    }

    fun confirmExit(ctx: Context, wpId: String) {
        val at = Repo.get(ctx).pendingExit[wpId] ?: return
        Repo.update(ctx) { it.copy(pendingExit = it.pendingExit - wpId) }
        val d = Repo.get(ctx)
        val wp = d.wp(wpId) ?: return
        val active = d.activeRecord(wpId)
        if (active != null) {
            // 퇴근 버튼을 안 눌렀어도 떠난 시간을 퇴근 시간으로 기록
            Repo.update(ctx) { it.withRecord(active.copy(end = at, leave = at)) }
            Notifier.cancel(ctx, wpId, Notifier.T_COUNT, Notifier.T_END, Notifier.T_OVER, Notifier.T_ARRIVE)
            Notifier.info(ctx, wp, "직장을 떠나 퇴근이 기록되었어요 (${fmtTime(at)})")
            Alarms.scheduleFor(ctx, wp)
        } else {
            val rec = d.records
                .filter {
                    it.wpId == wpId && it.leave == null && (it.arrive != null || it.start != null) &&
                        at - (it.arrive ?: it.start!!) < 36 * 3_600_000L
                }
                .maxByOrNull { it.date }
            if (rec != null) Repo.update(ctx) { it.withRecord(rec.copy(leave = at)) }
            Notifier.cancel(ctx, wpId, Notifier.T_ARRIVE, Notifier.T_END)
            if (rec?.end != null) Notifier.info(ctx, wp, "직장을 떠났어요 (${fmtTime(at)})")
        }
    }

    fun copyWorkplace(ctx: Context, wpId: String): Workplace? {
        val src = Repo.get(ctx).wp(wpId) ?: return null
        val copy = src.copy(id = newId(), name = "${src.name} (복사)")
        Repo.update(ctx) { d ->
            val i = d.workplaces.indexOfFirst { it.id == wpId }
            val list = d.workplaces.toMutableList().apply { add(if (i < 0) size else i + 1, copy) }
            d.copy(workplaces = list)
        }
        Geo.registerAll(ctx)
        Alarms.scheduleFor(ctx, copy)
        return copy
    }

    fun moveWorkplace(ctx: Context, wpId: String, delta: Int) {
        Repo.update(ctx) { d ->
            val i = d.workplaces.indexOfFirst { it.id == wpId }
            val j = i + delta
            if (i < 0 || j !in d.workplaces.indices) return@update d
            val list = d.workplaces.toMutableList()
            list[i] = list[j].also { list[j] = list[i] }
            d.copy(workplaces = list)
        }
    }

    fun deleteWorkplace(ctx: Context, wpId: String) {
        Repo.update(ctx) { d ->
            d.copy(
                workplaces = d.workplaces.filterNot { it.id == wpId },
                records = d.records.filterNot { it.wpId == wpId },
                pendingExit = d.pendingExit - wpId,
            )
        }
        Alarms.cancelFor(ctx, wpId)
        Alarms.cancelExitConfirm(ctx, wpId)
        Notifier.cancel(ctx, wpId, Notifier.T_ARRIVE, Notifier.T_COUNT, Notifier.T_END, Notifier.T_OVER, Notifier.T_INFO)
        Geo.registerAll(ctx)
    }

    /**
     * 같은 장소에 직장이 여러 개면(복사한 시간대별 일) 지금 시각과 출근 시간이 가장 가까운
     * 근무일 직장 하나만 도착 알림을 띄운다.
     */
    private fun bestAtSamePlace(d: AppData, wp: Workplace, at: Long): Boolean {
        val lat = wp.lat ?: return true
        val lng = wp.lng ?: return true
        val dist = FloatArray(1)
        val group = d.workplaces.filter { o ->
            o.lat != null && o.lng != null &&
                run { Location.distanceBetween(lat, lng, o.lat, o.lng, dist); dist[0] < 50f }
        }
        if (group.size <= 1) return true
        fun score(o: Workplace): Long {
            val day = o.shiftDateFor(at)
            if (!o.isWorkday(day)) return Long.MAX_VALUE
            return kotlin.math.abs(at - millisAt(day, hm(o.start)))
        }
        return group.minByOrNull { score(it) }?.id == wp.id
    }

    fun markArrive(ctx: Context, wpId: String, at: Long = System.currentTimeMillis()) {
        val wp = Repo.get(ctx).wp(wpId) ?: return
        val date = wp.shiftDateFor(at).toString()
        Repo.update(ctx) { d ->
            val r = d.record(wpId, date)
            if (r.arrive == null) d.withRecord(r.copy(arrive = at)) else d
        }
    }

    fun clockIn(ctx: Context, wpId: String, at: Long = System.currentTimeMillis()) {
        val d = Repo.get(ctx)
        val wp = d.wp(wpId) ?: return
        if (d.activeRecord(wpId) != null) return
        val date = wp.shiftDateFor(at).toString()
        val rec = d.record(wpId, date)
        val updated = if (rec.end != null) {
            rec.copy(start = at, end = null, leave = null, overtime = false)
        } else {
            rec.copy(arrive = rec.arrive ?: at, start = at, leave = null)
        }
        Repo.update(ctx) { it.withRecord(updated) }
        Notifier.cancel(ctx, wpId, Notifier.T_ARRIVE)
        Notifier.info(ctx, wp, "출근이 기록되었어요 (${fmtTime(at)}) 오늘도 화이팅!")
        Alarms.scheduleFor(ctx, wp)
    }

    fun clockOut(ctx: Context, wpId: String, at: Long = System.currentTimeMillis()) {
        val d = Repo.get(ctx)
        val wp = d.wp(wpId) ?: return
        val active = d.activeRecord(wpId) ?: return
        Repo.update(ctx) { it.withRecord(active.copy(end = at)) }
        Notifier.cancel(ctx, wpId, Notifier.T_COUNT, Notifier.T_END, Notifier.T_OVER)
        Notifier.info(ctx, wp, "퇴근이 기록되었어요 (${fmtTime(at)}) 오늘도 수고하셨어요!")
        Alarms.scheduleFor(ctx, wp)
    }

    fun startOvertime(ctx: Context, wpId: String) {
        val d = Repo.get(ctx)
        val wp = d.wp(wpId) ?: return
        val active = d.activeRecord(wpId) ?: return
        val updated = active.copy(overtime = true)
        Repo.update(ctx) { it.withRecord(updated) }
        Notifier.cancel(ctx, wpId, Notifier.T_END, Notifier.T_COUNT)
        Notifier.overtime(ctx, wp, updated)
    }

    /** 연장 근무 알림을 지워도 퇴근 전이면 다시 띄운다 */
    fun overtimeDismissed(ctx: Context, wpId: String) {
        val d = Repo.get(ctx)
        val wp = d.wp(wpId) ?: return
        val active = d.activeRecord(wpId) ?: return
        if (active.overtime) Notifier.overtime(ctx, wp, active)
    }

    fun afterManualEdit(ctx: Context, wpId: String) {
        val d = Repo.get(ctx)
        val wp = d.wp(wpId) ?: return
        if (d.activeRecord(wpId) == null) {
            Notifier.cancel(ctx, wpId, Notifier.T_COUNT, Notifier.T_END, Notifier.T_OVER)
        }
        Alarms.scheduleFor(ctx, wp)
    }

    /** 퇴근 10분 전부터 1분마다 불린다 */
    fun onCountdown(ctx: Context, wpId: String, date: String, endAt: Long) {
        val d = Repo.get(ctx)
        val wp = d.wp(wpId) ?: return
        val now = System.currentTimeMillis()
        val rec = d.record(wpId, date)
        if (rec.start == null || rec.end != null) {
            // 출근 안 했거나 이미 퇴근: 이번 근무는 건너뜀
            Alarms.scheduleFor(ctx, wp, maxOf(now + 20_000, endAt))
            return
        }
        val left = Math.round((endAt - now) / 60_000.0).toInt()
        if (left >= 1) {
            Notifier.countdown(ctx, wp, minOf(left, 10))
        } else {
            Notifier.cancel(ctx, wpId, Notifier.T_COUNT)
            if (!rec.overtime && now - endAt < 2 * 3_600_000L) {
                Notifier.endPrompt(ctx, wp)
                PopupActivity.show(ctx, PopupActivity.MODE_END, wpId)
            }
        }
        Alarms.scheduleFor(ctx, wp, now + 20_000)
    }
}
