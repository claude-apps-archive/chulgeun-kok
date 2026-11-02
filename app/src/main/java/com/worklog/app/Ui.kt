package com.worklog.app

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class Tab { HOME, PLACES, CALENDAR, PAY }

fun won(v: Double): String = String.format(Locale.KOREA, "%,d원", v.roundToLong())
fun fmtNum(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else String.format(Locale.US, "%.1f", d)
fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    return tick
}

// ======================================================================
// 앱 뼈대
// ======================================================================

@Composable
fun App() {
    val ctx = LocalContext.current
    val data by Repo.data.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var editing by remember { mutableStateOf<Workplace?>(null) }
    var settings by remember { mutableStateOf(false) }
    var calc by remember { mutableStateOf<CalcInput?>(null) }
    var recEdit by remember { mutableStateOf<DayRecord?>(null) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(10_000)
            value = System.currentTimeMillis()
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Perms.has(ctx, Manifest.permission.POST_NOTIFICATIONS)) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = editing != null || settings) {
        if (editing != null) editing = null else settings = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
    ) {
        val e = editing
        when {
            e != null -> PlaceEditor(e, data, onClose = { editing = null }, onCalc = { calc = it })
            settings -> SettingsScreen(data, onBack = { settings = false })
            else -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        Tab.HOME -> HomeScreen(
                            data, now,
                            onSettings = { settings = true },
                            onAdd = { editing = Workplace(newId()) },
                        )
                        Tab.PLACES -> PlacesScreen(
                            data,
                            onEdit = { editing = it },
                            onAdd = { editing = Workplace(newId(), color = data.workplaces.size % SWATCHES.size) },
                        )
                        Tab.CALENDAR -> CalendarScreen(data, onEdit = { recEdit = it })
                        Tab.PAY -> PayScreen(data)
                    }
                }
                BottomBar(tab) { tab = it }
            }
        }

        calc?.let { c -> CalcDialog(c, data.rates) { calc = null } }

        recEdit?.let { r ->
            data.wp(r.wpId)?.let { wp ->
                RecordEditor(
                    r, wp,
                    onDismiss = { recEdit = null },
                    onSave = { nr ->
                        Repo.update(ctx) { it.withRecord(nr) }
                        Work.afterManualEdit(ctx, nr.wpId)
                        recEdit = null
                    },
                    onDelete = {
                        Repo.update(ctx) { d ->
                            d.copy(records = d.records.filterNot { it.wpId == r.wpId && it.date == r.date })
                        }
                        Work.afterManualEdit(ctx, r.wpId)
                        recEdit = null
                    },
                )
            }
        }
    }
}

@Composable
fun BottomBar(tab: Tab, onTab: (Tab) -> Unit) {
    Column {
        HorizontalDivider(color = Line)
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFFBFBFD))
                .padding(vertical = 8.dp)
        ) {
            listOf(Tab.HOME to "홈", Tab.PLACES to "직장", Tab.CALENDAR to "달력", Tab.PAY to "급여").forEach { (t, label) ->
                val c = if (t == tab) Ink else Sub
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTab(t) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                        when (t) {
                            Tab.HOME -> Icon(Icons.Filled.Home, null, tint = c)
                            Tab.PLACES -> Icon(Icons.Filled.Place, null, tint = c)
                            Tab.CALENDAR -> Icon(Icons.Filled.DateRange, null, tint = c)
                            Tab.PAY -> Text("₩", color = c, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        label, fontSize = 11.sp, color = c,
                        fontWeight = if (t == tab) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

// ======================================================================
// 공통 컴포넌트
// ======================================================================

@Composable
fun Header(title: String, onBack: (() -> Unit)? = null) {
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .padding(top = if (onBack == null) 24.dp else 8.dp, bottom = 4.dp)
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Ink)
            }
        }
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

@Composable
fun Label(text: String) {
    Text(
        text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    number: Boolean = false,
) {
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = Sub, fontSize = 14.sp) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = FieldBg,
            unfocusedContainerColor = FieldBg,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Purple,
        ),
    )
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Purple,
    textColor: Color = Color.White,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun SoftButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Lavender)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Purple, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Purple else FieldBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (selected) Color.White else Ink, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
fun ToggleRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(desc, fontSize = 12.sp, color = Sub)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Purple, checkedThumbColor = Color.White),
        )
    }
}

@Composable
fun EmptyCard(text: String, button: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(FieldBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row {
            Buddy(swatch(0).b, 4, Modifier.size(48.dp))
            Buddy(swatch(2).b, 5, Modifier.size(48.dp))
            Buddy(swatch(1).b, 0, Modifier.size(48.dp), happy = false)
        }
        Spacer(Modifier.height(12.dp))
        Text(text, color = Sub, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        PrimaryButton(button, Modifier.fillMaxWidth(), onClick = onClick)
    }
}

// ======================================================================
// 홈
// ======================================================================

@Composable
fun HomeScreen(data: AppData, now: Long, onSettings: () -> Unit, onAdd: () -> Unit) {
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    val permsOk = remember(tick) { Perms.coreOk(ctx) }
    val td = dateOf(now)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${td.monthValue}월 ${td.dayOfMonth}일", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("${dowKo(td.dayOfWeek)}요일", fontSize = 15.sp, color = Sub)
            }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "설정", tint = Ink) }
        }
        Spacer(Modifier.height(16.dp))
        if (!permsOk) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFFF1E8))
                    .clickable(onClick = onSettings)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Warning, null, tint = Color(0xFFFF7A2F))
                Text(
                    "자동 출퇴근 감지를 하려면 권한 설정이 필요해요",
                    Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                    fontSize = 13.sp, color = Ink,
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Sub)
            }
            Spacer(Modifier.height(14.dp))
        }
        HeroCard(data, now)
        Spacer(Modifier.height(26.dp))
        Text("오늘의 근무", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(12.dp))
        if (data.workplaces.isEmpty()) {
            EmptyCard("아직 등록된 직장이 없어요.\n알바처나 회사를 추가해 보세요!", "직장 추가하기", onAdd)
        }
        data.workplaces.sortedBy { if (it.isWorkday(td)) 0 else 1 }.forEach { wp ->
            WorkCard(wp, data, now)
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

data class MonthStat(val wp: Workplace, val days: Int, val minutes: Long, val pay: Double)

/** 한 달 동안 직장별 출근일 수, 근무시간, 기록 기준 급여 */
fun monthStats(d: AppData, ym: YearMonth): List<MonthStat> {
    val recs = d.records.filter {
        it.start != null && runCatching { YearMonth.from(LocalDate.parse(it.date)) == ym }.getOrDefault(false)
    }
    return d.workplaces.mapNotNull { wp ->
        val mine = recs.filter { it.wpId == wp.id }
        if (mine.isEmpty()) return@mapNotNull null
        MonthStat(
            wp,
            mine.map { it.date }.distinct().size,
            mine.sumOf { it.paidMin(wp) ?: 0L },
            mine.sumOf { it.pay(wp) ?: 0.0 },
        )
    }
}

fun monthDays(d: AppData, ym: YearMonth): Int = d.records
    .filter { it.start != null && runCatching { YearMonth.from(LocalDate.parse(it.date)) == ym }.getOrDefault(false) }
    .map { it.date }.distinct().size

@Composable
fun HeroCard(data: AppData, now: Long) {
    var showMonths by remember { mutableStateOf(false) }
    val active = data.workplaces.firstNotNullOfOrNull { wp -> data.activeRecord(wp.id)?.let { wp to it } }
    val ym = YearMonth.from(dateOf(now))
    val days = monthDays(data, ym)
    val pay = monthStats(data, ym).sumOf { it.pay }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(HeroA, HeroB)))
            .clickable { showMonths = true }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Buddy(
            color = active?.let { swatch(it.first.color).b } ?: Color(0xFFFF8A4C),
            shape = active?.first?.color ?: 4,
            modifier = Modifier.size(92.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "이번 달 ${days}일 출근\n약 ${won(pay)}",
                color = PurpleDeep, fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (active != null) "${active.first.name}에서 ${fmtDur((now - active.second.start!!) / 60_000)}째 열일 중!"
                else "오늘도 잘하고 있어요!",
                color = Purple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            )
            Text("눌러서 지난달 보기 ›", color = Purple.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
    if (showMonths) MonthSummaryDialog(data, YearMonth.from(dateOf(now)).minusMonths(1)) { showMonths = false }
}

@Composable
fun MonthSummaryDialog(data: AppData, start: YearMonth, onDismiss: () -> Unit) {
    var month by remember { mutableStateOf(start) }
    val current = YearMonth.now()
    val stats = monthStats(data, month)
    val days = monthDays(data, month)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.White) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Header("월별 출근 · 급여", onBack = onDismiss)
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton({ month = month.minusMonths(1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 달", tint = Ink)
                        }
                        Text(
                            "${month.year}년 ${month.monthValue}월" + if (month == current) " (이번 달)" else "",
                            Modifier.weight(1f),
                            textAlign = TextAlign.Center, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink,
                        )
                        IconButton({ month = month.plusMonths(1) }, enabled = month < current) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달",
                                tint = if (month < current) Ink else Line,
                            )
                        }
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(HeroA, HeroB)))
                            .padding(20.dp)
                    ) {
                        Text("${month.monthValue}월 출근 ${days}일", color = PurpleDeep, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(won(stats.sumOf { it.pay }), color = PurpleDeep, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "총 근무 ${fmtDur(stats.sumOf { it.minutes })}",
                            color = Purple, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Label("직장별")
                    if (stats.isEmpty()) Text("이 달에는 출근 기록이 없어요", color = Sub, fontSize = 14.sp)
                    stats.forEach { st ->
                        val sw = swatch(st.wp.color)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(sw.a.copy(alpha = 0.18f))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Buddy(sw.b, st.wp.color, Modifier.size(40.dp))
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                            ) {
                                Text(st.wp.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                                Text("${st.days}일 · ${fmtDur(st.minutes)}", fontSize = 13.sp, color = Sub)
                            }
                            Text(won(st.pay), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = sw.b)
                        }
                    }
                    Text(
                        "* 기록된 출근~퇴근 시간 × 시급으로 계산한 금액이에요. 주휴수당, 4대보험·세금 공제는 빠져 있어요.",
                        fontSize = 12.sp, color = Sub, modifier = Modifier.padding(top = 12.dp, bottom = 30.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun WorkCard(wp: Workplace, data: AppData, now: Long) {
    val ctx = LocalContext.current
    val sw = swatch(wp.color)
    val date = wp.shiftDateFor(now)
    val active = data.activeRecord(wp.id)
    val rec = active ?: data.record(wp.id, date.toString())
    val workday = wp.isWorkday(date)
    val done = active == null && rec.end != null
    val status = when {
        active != null -> "출근 ${fmtTime(active.start)} · ${fmtDur((now - active.start!!) / 60_000)}째 근무 중" +
            if (active.overtime) " (연장 근무)" else ""
        rec.end != null -> "출근 ${fmtTime(rec.start)}  →  퇴근 ${fmtTime(rec.end)}"
        rec.arrive != null -> "도착 ${fmtTime(rec.arrive)} · 아직 출근 전이에요"
        workday -> "오늘 근무일이에요"
        else -> "오늘은 쉬는 날"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(sw.a, sw.b)))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) { Buddy(sw.b, wp.color, Modifier.size(36.dp), happy = workday || active != null) }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    "${wp.start} ~ ${wp.end} · ${fmtDur(wp.paidMin.toLong())}",
                    color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp,
                )
                Text(wp.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (done) 1f else 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Filled.Check, null, tint = sw.b, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(status, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (rec.leave != null) Text("직장 떠남 ${fmtTime(rec.leave)}", color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp)
        val btn: Pair<String, () -> Unit>? = when {
            active != null -> "퇴근하기" to { Work.clockOut(ctx, wp.id) }
            rec.end == null -> "출근하기" to { Work.clockIn(ctx, wp.id) }
            else -> null
        }
        if (btn != null) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .clickable { btn.second() }
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Text(btn.first, color = sw.b, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

// ======================================================================
// 직장 목록 / 편집
// ======================================================================

fun daysLabel(wp: Workplace): String {
    val d = wp.days.sorted().joinToString(" ") { "월화수목금토일"[it - 1].toString() }
    val extra = if (wp.dates.isNotEmpty()) "지정일 ${wp.dates.size}일" else ""
    return listOf(d, extra).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "근무일 없음" }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(data: AppData, onEdit: (Workplace) -> Unit, onAdd: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var menuFor by remember { mutableStateOf<Workplace?>(null) }
    var deleteFor by remember { mutableStateOf<Workplace?>(null) }
    var reorder by remember { mutableStateOf(false) }
    BackHandler(enabled = reorder) { reorder = false }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("직장", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
            if (reorder) {
                SoftButton("완료") { reorder = false }
            } else {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Purple)
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Add, "직장 추가", tint = Color.White) }
            }
        }
        Text(
            if (reorder) "화살표로 순서를 바꾸고 '완료'를 눌러 주세요"
            else "꾹 누르면 복사 · 순서 변경 · 삭제를 할 수 있어요",
            color = Sub, fontSize = 14.sp,
        )
        Spacer(Modifier.height(18.dp))
        if (data.workplaces.isEmpty()) EmptyCard("등록된 직장이 없어요", "직장 추가하기", onAdd)
        data.workplaces.forEachIndexed { i, wp ->
            val sw = swatch(wp.color)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(sw.a.copy(alpha = 0.22f))
                    .combinedClickable(
                        enabled = !reorder,
                        onClick = { onEdit(wp) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuFor = wp
                        },
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Buddy(sw.b, wp.color, Modifier.size(58.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 14.dp)
                ) {
                    Text(wp.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(
                        "시급 ${String.format(Locale.KOREA, "%,d", wp.wage)}원 · ${wp.start}~${wp.end} (${fmtDur(wp.paidMin.toLong())})",
                        fontSize = 13.sp, color = Sub,
                    )
                    Text(daysLabel(wp), fontSize = 13.sp, color = Sub)
                    Text(
                        if (wp.hasLocation) "반경 ${wp.radius}m 자동 감지 중" else "위치 미설정 · 자동 감지 꺼짐",
                        fontSize = 12.sp,
                        color = if (wp.hasLocation) sw.b else Coral,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (reorder) {
                    Column {
                        IconButton({ Work.moveWorkplace(ctx, wp.id, -1) }, enabled = i > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, "위로", tint = if (i > 0) Ink else Line)
                        }
                        IconButton({ Work.moveWorkplace(ctx, wp.id, 1) }, enabled = i < data.workplaces.size - 1) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown, "아래로",
                                tint = if (i < data.workplaces.size - 1) Ink else Line,
                            )
                        }
                    }
                } else {
                    Icon(Icons.Filled.Edit, "수정", tint = Sub)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(24.dp))
    }

    menuFor?.let { wp ->
        ModalBottomSheet(onDismissRequest = { menuFor = null }, containerColor = Color.White) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Buddy(swatch(wp.color).b, wp.color, Modifier.size(40.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(wp.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text("${wp.start} ~ ${wp.end} · ${daysLabel(wp)}", fontSize = 13.sp, color = Sub)
                    }
                }
                Spacer(Modifier.height(16.dp))
                MenuRow(Icons.Filled.Add, "복사하기", "같은 곳, 다른 시간대 일을 만들 때 편해요") {
                    menuFor = null
                    val copy = Work.copyWorkplace(ctx, wp.id)
                    if (copy != null) {
                        toast(ctx, "복사했어요. 시간과 날짜를 바꿔 저장하세요")
                        onEdit(copy)
                    }
                }
                MenuRow(Icons.AutoMirrored.Filled.List, "순서 변경", "홈과 목록에 보이는 순서를 바꿔요") {
                    menuFor = null
                    reorder = true
                }
                MenuRow(Icons.Filled.Delete, "삭제하기", "이 직장과 출퇴근 기록을 지워요", color = Coral) {
                    menuFor = null
                    deleteFor = wp
                }
            }
        }
    }

    deleteFor?.let { wp ->
        DeleteDialog(wp, onDismiss = { deleteFor = null }) {
            Work.deleteWorkplace(ctx, wp.id)
            deleteFor = null
        }
    }
}

@Composable
fun MenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    color: Color = Ink,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (color == Coral) Color(0xFFFFE9E3) else Lavender),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (color == Coral) Coral else Purple) }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = color)
            Text(desc, fontSize = 12.sp, color = Sub)
        }
    }
}

@Composable
fun DeleteDialog(wp: Workplace, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("${wp.name} 삭제") },
        text = { Text("이 직장의 출퇴근 기록도 모두 지워져요. 삭제할까요?") },
        confirmButton = { TextButton(onConfirm) { Text("삭제", color = Coral, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onDismiss) { Text("취소", color = Sub) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaceEditor(initial: Workplace, data: AppData, onClose: () -> Unit, onCalc: (CalcInput) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isNew = data.wp(initial.id) == null
    var w by remember(initial.id) { mutableStateOf(initial) }
    var wageText by remember(initial.id) { mutableStateOf(initial.wage.toString()) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<GeoHit>>(emptyList()) }
    var coordText by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }

    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.ACCESS_FINE_LOCATION] == true) toast(ctx, "다시 '현재 위치로'를 눌러 주세요")
    }

    fun current(): Workplace = w.copy(name = w.name.trim(), wage = wageText.filter { it.isDigit() }.toIntOrNull() ?: 0)

    fun save() {
        val nw = current()
        if (nw.name.isEmpty()) return toast(ctx, "장소 이름을 입력해 주세요")
        if (nw.days.isEmpty() && nw.dates.isEmpty()) return toast(ctx, "근무 요일이나 날짜를 하나 이상 골라 주세요")
        Repo.update(ctx) { d ->
            d.copy(
                workplaces = if (d.wp(nw.id) != null) d.workplaces.map { if (it.id == nw.id) nw else it }
                else d.workplaces + nw
            )
        }
        Geo.registerAll(ctx)
        Alarms.scheduleFor(ctx, nw)
        if (!nw.hasLocation) toast(ctx, "위치를 설정하지 않아 자동 감지는 꺼져 있어요")
        else if (!Perms.background(ctx)) toast(ctx, "설정에서 위치 '항상 허용'을 켜야 자동 감지돼요")
        onClose()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Header(if (isNew) "직장 추가" else "직장 수정", onBack = onClose)
        Column(Modifier.padding(horizontal = 20.dp)) {
            val sw = swatch(w.color)
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(sw.a.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) { Buddy(sw.b, w.color, Modifier.size(78.dp)) }
            }

            Label("장소 이름")
            Field(w.name, { w = w.copy(name = it) }, "예: 카페 OO점, OO회사")

            Label("시급")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(wageText, { wageText = it.filter { c -> c.isDigit() } }, "시급 (원)", Modifier.weight(1f), number = true)
                Spacer(Modifier.width(8.dp))
                SoftButton("월급 계산기") { onCalc(current().calcInput()) }
            }
            Text("2026년 최저시급 10,320원", fontSize = 12.sp, color = Sub, modifier = Modifier.padding(top = 6.dp))

            Label("근무 요일")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { d ->
                    val on = d in w.days
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(if (on) Purple else FieldBg)
                            .clickable { w = w.copy(days = if (on) w.days - d else w.days + d) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "월화수목금토일"[d - 1].toString(),
                            color = if (on) Color.White else if (d == 7) Coral else Ink,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Label("특정 날짜 (요일과 별개로 근무하는 날)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                w.dates.sorted().forEach { ds ->
                    val ld = LocalDate.parse(ds)
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Lavender)
                            .clickable { w = w.copy(dates = w.dates - ds) }
                            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${ld.monthValue}/${ld.dayOfMonth}(${dowKo(ld.dayOfWeek)})", color = Purple, fontSize = 13.sp)
                        Icon(Icons.Filled.Close, "삭제", tint = Purple, modifier = Modifier.size(16.dp))
                    }
                }
                SoftButton("+ 날짜 추가") {
                    val t = today()
                    DatePickerDialog(ctx, { _, y, m, d ->
                        w = w.copy(dates = w.dates + LocalDate.of(y, m + 1, d).toString())
                    }, t.year, t.monthValue - 1, t.dayOfMonth).show()
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeBox("출근 시간", w.start, Modifier.weight(1f)) { w = w.copy(start = it) }
                TimeBox("퇴근 시간", w.end, Modifier.weight(1f)) { w = w.copy(end = it) }
            }

            Label("휴게 시간")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "없음", 30 to "30분", 60 to "1시간", 90 to "1시간 30분").forEach { (m, l) ->
                    Pill(l, w.breakMin == m) { w = w.copy(breakMin = m) }
                }
            }

            Column(
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Lavender)
                    .padding(14.dp)
            ) {
                Text(
                    "${w.start} ~ ${w.end} = ${fmtDur(w.shiftMin.toLong())}",
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = PurpleDeep,
                )
                if (w.breakMin > 0) {
                    Text("휴게 ${fmtDur(w.breakMin.toLong())} 빼면 실근무 ${fmtDur(w.paidMin.toLong())}", fontSize = 13.sp, color = Purple)
                }
                if (hm(w.end) <= hm(w.start)) Text("퇴근이 다음 날인 야간 근무로 계산해요", fontSize = 12.sp, color = Sub)
            }

            Label("직장 위치")
            Text(
                if (w.hasLocation) "위도 %.5f, 경도 %.5f".format(w.lat, w.lng) else "아직 위치가 설정되지 않았어요",
                fontSize = 14.sp, color = if (w.hasLocation) Ink else Coral,
            )
            Spacer(Modifier.height(8.dp))
            SoftButton(if (locating) "위치 찾는 중..." else "지금 있는 곳을 직장으로", Modifier.fillMaxWidth()) {
                if (!Perms.location(ctx)) {
                    locLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                } else {
                    locating = true
                    Geo.currentLocation(ctx) { p ->
                        locating = false
                        if (p != null) w = w.copy(lat = p.first, lng = p.second) else toast(ctx, "위치를 가져오지 못했어요. GPS를 켜 주세요")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(query, { query = it }, "주소나 장소 이름으로 찾기", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                SoftButton("검색") {
                    val q = query.trim()
                    if (q.isNotEmpty()) scope.launch {
                        hits = withContext(Dispatchers.IO) { Geo.search(ctx, q) }
                        if (hits.isEmpty()) toast(ctx, "검색 결과가 없어요")
                    }
                }
            }
            hits.forEach { h ->
                Text(
                    h.label,
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            w = w.copy(lat = h.lat, lng = h.lng)
                            hits = emptyList()
                        }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    fontSize = 14.sp, color = Ink,
                )
                HorizontalDivider(color = Line)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(coordText, { coordText = it }, "좌표 직접 입력 (37.5665, 126.9780)", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                SoftButton("적용") {
                    val p = coordText.split(",", " ").mapNotNull { it.trim().toDoubleOrNull() }
                    if (p.size >= 2) w = w.copy(lat = p[0], lng = p[1]) else toast(ctx, "예: 37.5665, 126.9780 형식으로 입력해 주세요")
                }
            }
            Text(
                "구글 지도에서 장소를 길게 누르면 좌표를 복사할 수 있어요",
                fontSize = 12.sp, color = Sub, modifier = Modifier.padding(top = 6.dp),
            )

            Label("감지 반경 ${w.radius}m")
            Slider(
                value = w.radius.toFloat(),
                onValueChange = { w = w.copy(radius = (it / 10).roundToInt() * 10) },
                valueRange = 50f..500f,
                colors = SliderDefaults.colors(thumbColor = Purple, activeTrackColor = Purple),
            )
            Text(
                "GPS 오차 때문에 100~200m를 권장해요. 너무 작으면 일하는 중에 떠난 것으로 잘못 감지될 수 있어요.",
                fontSize = 12.sp, color = Sub,
            )

            Label("대표 컬러")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SWATCHES.forEachIndexed { i, s ->
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(s.a.copy(alpha = 0.3f))
                            .border(3.dp, if (w.color == i) Ink else Color.Transparent, CircleShape)
                            .clickable { w = w.copy(color = i) },
                        contentAlignment = Alignment.Center,
                    ) { Buddy(s.b, i, Modifier.size(36.dp)) }
                }
            }

            Spacer(Modifier.height(28.dp))
            PrimaryButton("저장하기", Modifier.fillMaxWidth()) { save() }
            if (!isNew) {
                Spacer(Modifier.height(8.dp))
                PrimaryButton("이 직장 삭제", Modifier.fillMaxWidth(), color = Color.White, textColor = Coral) {
                    confirmDelete = true
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmDelete) {
        DeleteDialog(initial, onDismiss = { confirmDelete = false }) {
            Work.deleteWorkplace(ctx, initial.id)
            confirmDelete = false
            onClose()
        }
    }
}

@Composable
fun TimeBox(label: String, value: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    Column(modifier) {
        Label(label)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(FieldBg)
                .clickable {
                    val t = hm(value)
                    TimePickerDialog(ctx, { _, h, m -> onPick("%02d:%02d".format(h, m)) }, t.hour, t.minute, true).show()
                }
                .padding(16.dp)
        ) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        }
    }
}

// ======================================================================
// 달력
// ======================================================================

@Composable
fun CalendarScreen(data: AppData, onEdit: (DayRecord) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var sel by remember { mutableStateOf(today()) }
    val td = today()
    val attended = remember(data.records) { data.records.filter { it.start != null }.groupBy { it.date } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Header("달력")
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton({ month = month.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 달", tint = Ink)
                }
                Text(
                    "${month.year}년 ${month.monthValue}월",
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink,
                )
                IconButton({ month = month.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달", tint = Ink)
                }
            }
            Row {
                "월화수목금토일".forEach { c ->
                    Text(c.toString(), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp, color = Sub)
                }
            }
            Spacer(Modifier.height(6.dp))
            val lead = month.atDay(1).dayOfWeek.value - 1
            val cells: List<LocalDate?> = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            cells.chunked(7).forEach { week ->
                Row {
                    week.forEach { d ->
                        Column(
                            Modifier
                                .weight(1f)
                                .height(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = d != null) { if (d != null) sel = d },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            if (d != null) {
                                val isSel = d == sel
                                val isToday = d == td
                                Box(
                                    Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isSel) Purple else Color.Transparent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "${d.dayOfMonth}",
                                        fontSize = 15.sp,
                                        fontWeight = if (isSel || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSel -> Color.White
                                            isToday -> Purple
                                            d.dayOfWeek == DayOfWeek.SUNDAY -> Coral
                                            else -> Ink
                                        },
                                    )
                                }
                                Row(
                                    Modifier.height(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    attended[d.toString()].orEmpty().take(3).forEach { r ->
                                        Box(
                                            Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(swatch(data.wp(r.wpId)?.color ?: 0).b)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Spacer(Modifier.height(12.dp))
            data.workplaces.forEach { wp ->
                val att = data.records.count {
                    it.wpId == wp.id && it.start != null &&
                        runCatching { YearMonth.from(LocalDate.parse(it.date)) == month }.getOrDefault(false)
                }
                val sched = (1..month.lengthOfMonth()).count { wp.isWorkday(month.atDay(it)) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(swatch(wp.color).b)
                    )
                    Text(
                        wp.name,
                        Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                        fontSize = 15.sp, color = Ink,
                    )
                    Text("$att/$sched", fontSize = 14.sp, color = Sub)
                }
                HorizontalDivider(color = Line)
            }

            Spacer(Modifier.height(22.dp))
            Text(
                "${sel.monthValue}월 ${sel.dayOfMonth}일 (${dowKo(sel.dayOfWeek)})",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            val recs = data.records.filter { it.date == sel.toString() && it.hasAny }
            if (recs.isEmpty()) {
                Text("이 날의 기록이 없어요", color = Sub, fontSize = 14.sp, modifier = Modifier.padding(4.dp, 8.dp))
            }
            recs.forEach { r ->
                val wp = data.wp(r.wpId) ?: return@forEach
                RecordCard(r, wp) { onEdit(r) }
            }
            val missing = data.workplaces.filter { w -> recs.none { it.wpId == w.id } }
            if (missing.isNotEmpty()) {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    missing.forEach { w -> SoftButton("+ ${w.name} 기록 추가") { onEdit(DayRecord(w.id, sel.toString())) } }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
fun RecordCard(r: DayRecord, wp: Workplace, onEdit: () -> Unit) {
    val sw = swatch(wp.color)
    Column(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(sw.a.copy(alpha = 0.18f))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Buddy(sw.b, wp.color, Modifier.size(36.dp))
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            ) {
                Text(wp.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("예정 ${wp.start} ~ ${wp.end}" + if (r.overtime) " · 연장 근무" else "", fontSize = 12.sp, color = Sub)
            }
            TextButton(onEdit) { Text("수정", color = Purple, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .padding(vertical = 10.dp)
        ) {
            listOf("도착" to r.arrive, "출근" to r.start, "퇴근" to r.end, "떠남" to r.leave).forEach { (l, t) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(l, fontSize = 12.sp, color = Sub)
                    Text(fmtTime(t), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
            }
        }
        r.workedMin()?.let { m ->
            Text(
                "근무 ${fmtDur(m)} · 일급 약 ${won(r.pay(wp) ?: 0.0)}",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = sw.b,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (r.memo.isNotBlank()) {
            Text("한 일: ${r.memo}", fontSize = 13.sp, color = Ink, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun RecordEditor(
    rec: DayRecord,
    wp: Workplace,
    onDismiss: () -> Unit,
    onSave: (DayRecord) -> Unit,
    onDelete: () -> Unit,
) {
    var r by remember(rec) { mutableStateOf(rec) }
    val date = LocalDate.parse(rec.date)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("${wp.name} · ${date.monthValue}월 ${date.dayOfMonth}일", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                TimeEditRow("도착", r.arrive, date, hm(wp.start).minusMinutes(10), null) { r = r.copy(arrive = it) }
                TimeEditRow("출근", r.start, date, hm(wp.start), null) { r = r.copy(start = it) }
                TimeEditRow("퇴근", r.end, date, hm(wp.end), r.start) { r = r.copy(end = it) }
                TimeEditRow("떠남", r.leave, date, hm(wp.end), r.start) { r = r.copy(leave = it) }
                Spacer(Modifier.height(10.dp))
                Field(r.memo, { r = r.copy(memo = it) }, "메모 (오늘 한 일)")
            }
        },
        confirmButton = {
            TextButton({ onSave(r) }) { Text("저장", color = Purple, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                if (rec.hasAny) TextButton(onDelete) { Text("삭제", color = Coral) }
                TextButton(onDismiss) { Text("취소", color = Sub) }
            }
        },
    )
}

@Composable
fun TimeEditRow(label: String, value: Long?, date: LocalDate, def: LocalTime, after: Long?, onChange: (Long?) -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.width(44.dp), fontSize = 14.sp, color = Sub)
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(FieldBg)
                .clickable {
                    val t = value?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: def
                    TimePickerDialog(ctx, { _, h, m ->
                        var ms = millisAt(date, LocalTime.of(h, m))
                        if (after != null && ms < after) ms += 86_400_000L
                        onChange(ms)
                    }, t.hour, t.minute, true).show()
                }
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            Text(if (value == null) "선택" else fmtTime(value), fontSize = 15.sp, color = if (value == null) Sub else Ink)
        }
        if (value != null) {
            IconButton({ onChange(null) }) { Icon(Icons.Filled.Close, "지우기", tint = Sub) }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

// ======================================================================
// 급여 계산기
// ======================================================================

@Composable
fun PayScreen(data: AppData) {
    var selId by rememberSaveable { mutableStateOf(data.workplaces.firstOrNull()?.id) }
    val wp = selId?.let { data.wp(it) }
    val input = wp?.calcInput() ?: CalcInput(10320, 480, 5.0)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Header("급여 계산기")
        if (data.workplaces.isNotEmpty()) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                data.workplaces.forEach { w -> Pill(w.name, w.id == selId) { selId = w.id } }
            }
        }
        CalcPanel(input, data.rates)
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
fun CalcDialog(input: CalcInput, rates: Rates, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.White) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Header("월급 계산기", onBack = onDismiss)
                CalcPanel(input, rates)
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun CalcPanel(input: CalcInput, rates: Rates) {
    var wage by remember(input) { mutableStateOf(input.wage.toString()) }
    var hh by remember(input) { mutableStateOf((input.dailyMin / 60).toString()) }
    var mm by remember(input) { mutableStateOf((input.dailyMin % 60).toString()) }
    var dpw by remember(input) { mutableStateOf(fmtNum(input.daysPerWeek)) }
    var period by rememberSaveable { mutableStateOf(PayPeriod.MONTH) }
    var holiday by rememberSaveable { mutableStateOf(true) }
    var ins by rememberSaveable { mutableStateOf(false) }
    val dailyMin = (hh.toIntOrNull() ?: 0) * 60 + (mm.toIntOrNull() ?: 0)
    val res = calcPay(
        (wage.toIntOrNull() ?: 0).toDouble(), dailyMin.toDouble(), dpw.toDoubleOrNull() ?: 0.0,
        period, holiday, ins, rates,
    )

    Column(Modifier.padding(horizontal = 20.dp)) {
        Label("시급")
        Field(wage, { wage = it.filter { c -> c.isDigit() } }, "시급 (원)", number = true)

        Label("하루 근무시간 (휴게시간 제외)")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Field(hh, { hh = it.filter { c -> c.isDigit() } }, "8", Modifier.weight(1f), number = true)
            Text("시간", Modifier.padding(horizontal = 8.dp), color = Ink)
            Field(mm, { mm = it.filter { c -> c.isDigit() } }, "0", Modifier.weight(1f), number = true)
            Text("분", Modifier.padding(start = 8.dp), color = Ink)
        }

        Label("일주일에 며칠 일하나요?")
        Field(dpw, { dpw = it }, "5", number = true)
        Text(
            "주 ${fmtNum(res.weeklyHours)}시간 근무" +
                if (res.holidayHours > 0) " · 주휴 ${fmtNum(res.holidayHours)}시간" else "",
            fontSize = 13.sp, color = Sub, modifier = Modifier.padding(top = 6.dp),
        )

        Label("급여 형태")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PayPeriod.entries.forEach { p -> Pill(p.label, p == period, Modifier.weight(1f)) { period = p } }
        }

        ToggleRow("주휴수당 포함", "주 15시간 이상 일하면 받을 수 있어요", holiday) { holiday = it }
        ToggleRow("4대보험 공제", "국민연금 · 건강보험 · 장기요양 · 고용보험 근로자 부담분", ins) { ins = it }

        Column(
            Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(HeroA, HeroB)))
                .padding(20.dp)
        ) {
            Text(
                "${period.label} 예상 " + if (ins) "실수령액" else "(세전)",
                color = PurpleDeep, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(won(if (ins) res.net else res.gross), color = PurpleDeep, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(14.dp)
            ) {
                ResultRow("기본급", won(res.base))
                if (holiday) ResultRow(if (period == PayPeriod.DAY) "주휴수당 (하루 환산)" else "주휴수당", won(res.holiday))
                ResultRow("세전 합계", won(res.gross), bold = true)
                if (ins) {
                    HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 6.dp))
                    ResultRow("국민연금 ${fmtRate(rates.pension)}%", "-" + won(res.pension))
                    ResultRow("건강보험 ${fmtRate(rates.health)}%", "-" + won(res.health))
                    ResultRow("장기요양 (건강보험료의 ${fmtRate(rates.care)}%)", "-" + won(res.care))
                    ResultRow("고용보험 ${fmtRate(rates.employment)}%", "-" + won(res.employment))
                    ResultRow("공제 합계", "-" + won(res.deductions), bold = true)
                    HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 6.dp))
                    ResultRow("실수령액", won(res.net), bold = true, color = Purple)
                }
            }
        }
        Text(
            "* 근로소득세, 연장·야간·휴일 가산수당은 빠진 예상 금액이에요. 월급은 한 달 평균 4.345주로 계산해요. 보험료율은 설정에서 바꿀 수 있어요.",
            fontSize = 12.sp, color = Sub, modifier = Modifier.padding(top = 10.dp),
        )
    }
}

fun fmtRate(d: Double): String = String.format(Locale.US, "%.3f", d).trimEnd('0').trimEnd('.')

@Composable
fun ResultRow(label: String, value: String, bold: Boolean = false, color: Color = Ink) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp, color = if (bold) Ink else Sub)
        Text(value, fontSize = 14.sp, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

// ======================================================================
// 설정
// ======================================================================

private data class PermState(
    val notif: Boolean,
    val loc: Boolean,
    val bg: Boolean,
    val overlay: Boolean,
    val full: Boolean,
    val exact: Boolean,
    val battery: Boolean,
)

private fun open(ctx: Context, action: String, withPackage: Boolean = true) {
    val i = Intent(action)
    if (withPackage) i.data = Uri.parse("package:${ctx.packageName}")
    runCatching { ctx.startActivity(i) }.onFailure {
        runCatching {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        }
    }
}

@Composable
fun SettingsScreen(data: AppData, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    var refresh by remember { mutableIntStateOf(0) }
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
        Geo.registerAll(ctx)
    }
    val oneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++
        Geo.registerAll(ctx)
    }
    val s = remember(tick, refresh) {
        PermState(
            Perms.notifications(ctx), Perms.location(ctx), Perms.background(ctx), Perms.overlay(ctx),
            Perms.fullScreen(ctx), Perms.exactAlarm(ctx), Perms.battery(ctx),
        )
    }
    LaunchedEffect(s.loc, s.bg) { if (s.loc && s.bg) Geo.registerAll(ctx) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Header("설정", onBack = onBack)
        Column(Modifier.padding(horizontal = 20.dp)) {
            Label("권한")
            PermRow("알림", "출근·퇴근 알림과 카운트다운을 받아요", s.notif) {
                if (Build.VERSION.SDK_INT >= 33 && !Perms.has(ctx, Manifest.permission.POST_NOTIFICATIONS)) {
                    oneLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        )
                    }
                }
            }
            PermRow("위치 (정확한 위치)", "직장 도착과 떠남을 감지해요", s.loc) {
                locLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            PermRow("위치 '항상 허용'", "앱을 닫아도 감지하려면 '항상 허용'을 골라 주세요", s.bg) {
                when {
                    !s.loc -> toast(ctx, "먼저 위치 권한을 허용해 주세요")
                    Build.VERSION.SDK_INT >= 29 -> oneLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
            PermRow("다른 앱 위에 표시", "다른 앱을 쓰는 중에도 화면 가운데에 출근 팝업을 띄워요", s.overlay) {
                open(ctx, Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            }
            if (Build.VERSION.SDK_INT >= 34) {
                PermRow("전체 화면 알림", "화면이 꺼져 있을 때도 팝업을 띄워요", s.full) {
                    open(ctx, Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                PermRow("정확한 알람", "1분 단위 퇴근 카운트다운에 필요해요", s.exact) {
                    open(ctx, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                }
            }
            PermRow("배터리 최적화 제외", "휴대폰이 앱을 잠재우지 않게 해요", s.battery) {
                open(ctx, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            }
            TextButton({ open(ctx, Settings.ACTION_APPLICATION_DETAILS_SETTINGS) }) {
                Text("앱 정보 화면 열기", color = Purple)
            }

            Label("삼성 휴대폰 · 갤럭시 핏3 팁")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(FieldBg)
                    .padding(14.dp)
            ) {
                Text(
                    "• 설정 > 배터리 > 백그라운드 사용 제한 > '절전 예외 앱'에 출근콕을 추가하세요.\n" +
                        "• Galaxy Wearable 앱 > 알림 > 앱 알림 관리에서 출근콕을 켜면 핏3로 알림이 가요.\n" +
                        "• 핏3에서 '출근' 버튼이 안 보이면 폰 알림이나 팝업에서 눌러 주세요.",
                    fontSize = 13.sp, color = Ink, lineHeight = 20.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("알림 테스트", Modifier.weight(1f)) { Notifier.test(ctx) }
                SoftButton("팝업 테스트", Modifier.weight(1f)) {
                    ctx.startActivity(PopupActivity.intent(ctx, PopupActivity.MODE_TEST, "test"))
                }
            }

            Label("떠남 확정 대기 시간")
            Text("반경 밖으로 나간 뒤 이 시간 동안 돌아오지 않으면 떠난 것으로 기록해요 (GPS 튐 방지)", fontSize = 12.sp, color = Sub)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 5, 10).forEach { m ->
                    Pill("${m}분", data.exitDelayMin == m) { Repo.update(ctx) { it.copy(exitDelayMin = m) } }
                }
            }

            Label("4대보험 근로자 부담률")
            key(data.rates == Rates()) {
            RateField("국민연금", data.rates.pension) { v -> Repo.update(ctx) { it.copy(rates = it.rates.copy(pension = v)) } }
            RateField("건강보험", data.rates.health) { v -> Repo.update(ctx) { it.copy(rates = it.rates.copy(health = v)) } }
            RateField("장기요양 (건강보험료 대비)", data.rates.care) { v -> Repo.update(ctx) { it.copy(rates = it.rates.copy(care = v)) } }
            RateField("고용보험", data.rates.employment) { v -> Repo.update(ctx) { it.copy(rates = it.rates.copy(employment = v)) } }
            }
            TextButton({ Repo.update(ctx) { it.copy(rates = Rates()) } }) { Text("기본값으로 되돌리기", color = Purple) }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
fun PermRow(title: String, desc: String, ok: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = !ok, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(desc, fontSize = 12.sp, color = Sub)
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (ok) Color(0xFFE3F7EC) else Color(0xFFFFE9E3))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                if (ok) "허용됨" else "설정하기", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (ok) Color(0xFF1E9E5A) else Coral,
            )
        }
    }
}

@Composable
fun RateField(label: String, value: Double, onValue: (Double) -> Unit) {
    var t by remember { mutableStateOf(fmtRate(value)) }
    Row(
        Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp, color = Ink)
        Field(t, { s -> t = s; s.toDoubleOrNull()?.let(onValue) }, "", Modifier.width(110.dp), number = true)
        Text("%", Modifier.padding(start = 6.dp), color = Sub)
    }
}
