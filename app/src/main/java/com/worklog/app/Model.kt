package com.worklog.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.max

/** 직장 / 알바처. days: 1(월)~7(일), dates: "yyyy-MM-dd" 로 지정한 근무일 */
data class Workplace(
    val id: String,
    val name: String = "",
    val wage: Int = 10320,
    val start: String = "09:00",
    val end: String = "18:00",
    val breakMin: Int = 60,
    val days: Set<Int> = setOf(1, 2, 3, 4, 5),
    val dates: Set<String> = emptySet(),
    val color: Int = 0,
    val lat: Double? = null,
    val lng: Double? = null,
    val radius: Int = 150,
)

/** 하루 기록. date 는 근무(시프트)가 시작하는 날짜. 시간은 epoch millis */
data class DayRecord(
    val wpId: String,
    val date: String,
    val arrive: Long? = null,
    val start: Long? = null,
    val end: Long? = null,
    val leave: Long? = null,
    val overtime: Boolean = false,
    val memo: String = "",
)

/** 4대보험 근로자 부담률(%). care 는 건강보험료 대비 장기요양보험료 비율 */
data class Rates(
    val pension: Double = 4.75,
    val health: Double = 3.595,
    val care: Double = 13.14,
    val employment: Double = 0.9,
)

data class AppData(
    val workplaces: List<Workplace> = emptyList(),
    val records: List<DayRecord> = emptyList(),
    val pendingExit: Map<String, Long> = emptyMap(),
    val rates: Rates = Rates(),
    val exitDelayMin: Int = 3,
)

// ---------- 시간 ----------

val zone: ZoneId get() = ZoneId.systemDefault()
private val HM = DateTimeFormatter.ofPattern("HH:mm")

fun hm(s: String): LocalTime = runCatching { LocalTime.parse(s) }.getOrDefault(LocalTime.of(9, 0))
fun fmtTime(ms: Long?): String =
    if (ms == null) "-" else Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().format(HM)

fun fmtDur(min: Long): String {
    val h = min / 60
    val m = min % 60
    return when {
        m == 0L -> "${h}시간"
        h == 0L -> "${m}분"
        else -> "${h}시간 ${m}분"
    }
}

fun today(): LocalDate = LocalDate.now(zone)
fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
fun millisAt(d: LocalDate, t: LocalTime): Long = d.atTime(t).atZone(zone).toInstant().toEpochMilli()
fun dowKo(d: DayOfWeek): String = "월화수목금토일"[d.value - 1].toString()

/** 09:00~10:00 = 60분. 퇴근이 출근보다 이르면 다음 날 퇴근(야간 근무)으로 본다 */
fun shiftMinutes(start: String, end: String): Int {
    val d = hm(end).toSecondOfDay() / 60 - hm(start).toSecondOfDay() / 60
    return if (d <= 0) d + 1440 else d
}

val Workplace.shiftMin: Int get() = shiftMinutes(start, end)
val Workplace.paidMin: Int get() = max(0, shiftMin - breakMin)
val Workplace.hasLocation: Boolean get() = lat != null && lng != null
fun Workplace.isWorkday(d: LocalDate) = dates.contains(d.toString()) || days.contains(d.dayOfWeek.value)
fun Workplace.shiftEndAt(d: LocalDate): Long = millisAt(d, hm(start)) + shiftMin * 60_000L

/** 지금 시각이 속한 근무 날짜. 어제 시작한 야간 근무가 아직 안 끝났으면 어제 */
fun Workplace.shiftDateFor(now: Long): LocalDate {
    val t = dateOf(now)
    val y = t.minusDays(1)
    return if (isWorkday(y) && now < shiftEndAt(y)) y else t
}

// ---------- 기록 ----------

fun AppData.wp(id: String) = workplaces.find { it.id == id }
fun AppData.record(wpId: String, date: String) =
    records.find { it.wpId == wpId && it.date == date } ?: DayRecord(wpId, date)

fun AppData.withRecord(r: DayRecord) =
    copy(records = records.filterNot { it.wpId == r.wpId && it.date == r.date } + r)

fun AppData.activeRecord(wpId: String) =
    records.filter { it.wpId == wpId && it.start != null && it.end == null }.maxByOrNull { it.start!! }

val DayRecord.hasAny: Boolean get() = arrive != null || start != null || end != null || leave != null || memo.isNotBlank()

fun DayRecord.workedMin(): Long? =
    if (start != null && end != null) max(0L, (end - start) / 60_000) else null

/** 4시간 이상 일했으면 설정한 휴게시간을 빼고 계산 */
fun DayRecord.paidMin(wp: Workplace): Long? =
    workedMin()?.let { if (it >= 240) max(0L, it - wp.breakMin) else it }

fun DayRecord.pay(wp: Workplace): Double? = paidMin(wp)?.let { it / 60.0 * wp.wage }

fun newId(): String = UUID.randomUUID().toString().take(8)

// ---------- 저장소 ----------

object Repo {
    private var prefs: SharedPreferences? = null
    private val state = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = state

    @Synchronized
    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("worklog", Context.MODE_PRIVATE)
        state.value = p.getString("data", null)
            ?.let { runCatching { appDataFromJson(JSONObject(it)) }.getOrNull() }
            ?: AppData()
        prefs = p
    }

    fun get(ctx: Context): AppData {
        init(ctx)
        return state.value
    }

    @Synchronized
    fun update(ctx: Context, f: (AppData) -> AppData) {
        init(ctx)
        val n = f(state.value)
        state.value = n
        prefs?.edit()?.putString("data", n.toJson().toString())?.commit()
    }
}

// ---------- JSON ----------

private fun JSONObject.longOrNull(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null
private fun JSONObject.doubleOrNull(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null

private fun Workplace.toJson() = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("wage", wage)
    put("start", start)
    put("end", end)
    put("breakMin", breakMin)
    put("days", JSONArray(days.toList()))
    put("dates", JSONArray(dates.toList()))
    put("color", color)
    put("lat", lat ?: JSONObject.NULL)
    put("lng", lng ?: JSONObject.NULL)
    put("radius", radius)
}

private fun workplaceFromJson(o: JSONObject) = Workplace(
    id = o.getString("id"),
    name = o.optString("name"),
    wage = o.optInt("wage", 10320),
    start = o.optString("start", "09:00"),
    end = o.optString("end", "18:00"),
    breakMin = o.optInt("breakMin", 0),
    days = o.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: emptySet(),
    dates = o.optJSONArray("dates")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
    color = o.optInt("color"),
    lat = o.doubleOrNull("lat"),
    lng = o.doubleOrNull("lng"),
    radius = o.optInt("radius", 150),
)

private fun DayRecord.toJson() = JSONObject().apply {
    put("wpId", wpId)
    put("date", date)
    put("arrive", arrive ?: JSONObject.NULL)
    put("start", start ?: JSONObject.NULL)
    put("end", end ?: JSONObject.NULL)
    put("leave", leave ?: JSONObject.NULL)
    put("overtime", overtime)
    put("memo", memo)
}

private fun recordFromJson(o: JSONObject) = DayRecord(
    wpId = o.getString("wpId"),
    date = o.getString("date"),
    arrive = o.longOrNull("arrive"),
    start = o.longOrNull("start"),
    end = o.longOrNull("end"),
    leave = o.longOrNull("leave"),
    overtime = o.optBoolean("overtime"),
    memo = o.optString("memo"),
)

private fun AppData.toJson(): JSONObject = JSONObject().apply {
    put("workplaces", JSONArray().apply { workplaces.forEach { put(it.toJson()) } })
    put("records", JSONArray().apply { records.forEach { put(it.toJson()) } })
    put("pendingExit", JSONObject().apply { pendingExit.forEach { (k, v) -> put(k, v) } })
    put("rates", JSONObject().apply {
        put("pension", rates.pension)
        put("health", rates.health)
        put("care", rates.care)
        put("employment", rates.employment)
    })
    put("exitDelayMin", exitDelayMin)
}

private fun appDataFromJson(o: JSONObject): AppData {
    val ws = o.optJSONArray("workplaces")
        ?.let { a -> (0 until a.length()).map { workplaceFromJson(a.getJSONObject(it)) } } ?: emptyList()
    val rs = o.optJSONArray("records")
        ?.let { a -> (0 until a.length()).map { recordFromJson(a.getJSONObject(it)) } } ?: emptyList()
    val pe = o.optJSONObject("pendingExit")
        ?.let { p -> p.keys().asSequence().associateWith { p.getLong(it) } } ?: emptyMap()
    val r = o.optJSONObject("rates")?.let {
        Rates(
            it.optDouble("pension", 4.75),
            it.optDouble("health", 3.595),
            it.optDouble("care", 13.14),
            it.optDouble("employment", 0.9),
        )
    } ?: Rates()
    return AppData(ws, rs, pe, r, o.optInt("exitDelayMin", 3))
}
