package com.worklog.app

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min

enum class PayPeriod(val label: String) { DAY("일급"), WEEK("주급"), MONTH("월급"), YEAR("연봉") }

data class CalcInput(val wage: Int, val dailyMin: Int, val daysPerWeek: Double)

data class PayResult(
    val weeklyHours: Double,
    val holidayHours: Double,
    val base: Double,
    val holiday: Double,
    val pension: Double,
    val health: Double,
    val care: Double,
    val employment: Double,
) {
    val gross get() = base + holiday
    val deductions get() = pension + health + care + employment
    val net get() = gross - deductions
}

/** 한 달 평균 주 수 (365 / 7 / 12). 주 40시간 + 주휴 8시간이면 월 209시간 */
const val WEEKS_PER_MONTH = 365.0 / 7 / 12

fun calcPay(
    wage: Double,
    dailyMin: Double,
    daysPerWeek: Double,
    period: PayPeriod,
    holidayOn: Boolean,
    insuranceOn: Boolean,
    r: Rates,
): PayResult {
    val dailyH = dailyMin / 60
    val weeklyH = dailyH * daysPerWeek
    // 주휴수당: 주 15시간 이상이면 (주 근로시간 / 40) × 8시간, 최대 8시간
    val holH = if (holidayOn && weeklyH >= 15) min(weeklyH, 40.0) / 40.0 * 8.0 else 0.0
    val (base, hol) = when (period) {
        PayPeriod.DAY -> wage * dailyH to (if (daysPerWeek > 0) wage * holH / daysPerWeek else 0.0)
        PayPeriod.WEEK -> wage * weeklyH to wage * holH
        PayPeriod.MONTH -> wage * weeklyH * WEEKS_PER_MONTH to wage * holH * WEEKS_PER_MONTH
        PayPeriod.YEAR -> wage * weeklyH * WEEKS_PER_MONTH * 12 to wage * holH * WEEKS_PER_MONTH * 12
    }
    val gross = base + hol
    val pension = if (insuranceOn) gross * r.pension / 100 else 0.0
    val health = if (insuranceOn) gross * r.health / 100 else 0.0
    val care = health * r.care / 100
    val employment = if (insuranceOn) gross * r.employment / 100 else 0.0
    return PayResult(weeklyH, holH, base, hol, pension, health, care, employment)
}

fun Workplace.calcInput(): CalcInput {
    val dpw = if (days.isNotEmpty()) {
        days.size.toDouble()
    } else {
        val ym = YearMonth.now()
        val n = dates.count { runCatching { YearMonth.from(LocalDate.parse(it)) == ym }.getOrDefault(false) }
        n * 7.0 / ym.lengthOfMonth()
    }
    return CalcInput(wage, paidMin, dpw)
}
