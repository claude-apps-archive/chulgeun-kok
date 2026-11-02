import Foundation

enum PayPeriod: String, CaseIterable, Identifiable {
    case day = "일급", week = "주급", month = "월급", year = "연봉"
    var id: String { rawValue }
}

struct CalcInput: Equatable {
    var wage: Int
    var dailyMin: Int
    var daysPerWeek: Double
}

struct PayResult {
    var weeklyHours: Double
    var holidayHours: Double
    var base: Double
    var holiday: Double
    var pension: Double
    var health: Double
    var care: Double
    var employment: Double

    var gross: Double { base + holiday }
    var deductions: Double { pension + health + care + employment }
    var net: Double { gross - deductions }
}

/// 한 달 평균 주 수 (365 / 7 / 12). 주 40시간 + 주휴 8시간이면 월 209시간
let weeksPerMonth = 365.0 / 7 / 12

func calcPay(
    wage: Double, dailyMin: Double, daysPerWeek: Double,
    period: PayPeriod, holidayOn: Bool, insuranceOn: Bool, rates r: Rates
) -> PayResult {
    let dailyH = dailyMin / 60
    let weeklyH = dailyH * daysPerWeek
    // 주휴수당: 주 15시간 이상이면 (주 근로시간 / 40) × 8시간, 최대 8시간
    let holH = holidayOn && weeklyH >= 15 ? min(weeklyH, 40) / 40 * 8 : 0
    let base: Double
    let hol: Double
    switch period {
    case .day:
        base = wage * dailyH
        hol = daysPerWeek > 0 ? wage * holH / daysPerWeek : 0
    case .week:
        base = wage * weeklyH
        hol = wage * holH
    case .month:
        base = wage * weeklyH * weeksPerMonth
        hol = wage * holH * weeksPerMonth
    case .year:
        base = wage * weeklyH * weeksPerMonth * 12
        hol = wage * holH * weeksPerMonth * 12
    }
    let gross = base + hol
    let health = insuranceOn ? gross * r.health / 100 : 0
    return PayResult(
        weeklyHours: weeklyH,
        holidayHours: holH,
        base: base,
        holiday: hol,
        pension: insuranceOn ? gross * r.pension / 100 : 0,
        health: health,
        care: health * r.care / 100,
        employment: insuranceOn ? gross * r.employment / 100 : 0
    )
}

extension Workplace {
    var calcInput: CalcInput {
        let dpw: Double
        if !days.isEmpty {
            dpw = Double(days.count)
        } else {
            let cal = Calendar.current
            let now = Date()
            let n = dates.filter { s in
                guard let d = dayFromKey(s) else { return false }
                return cal.isDate(d, equalTo: now, toGranularity: .month)
            }.count
            let len = cal.range(of: .day, in: .month, for: now)?.count ?? 30
            dpw = Double(n) * 7 / Double(len)
        }
        return CalcInput(wage: wage, dailyMin: paidMin, daysPerWeek: dpw)
    }
}

struct MonthStat: Identifiable {
    let wp: Workplace
    let days: Int
    let minutes: Int
    let pay: Double
    var id: String { wp.id }
}

/// month 가 속한 달의 직장별 출근일 수, 근무시간, 기록 기준 급여
func monthStats(_ d: AppData, _ month: Date) -> (days: Int, stats: [MonthStat]) {
    let cal = Calendar.current
    let recs = d.records.filter { r in
        guard r.start != nil, let day = dayFromKey(r.date) else { return false }
        return cal.isDate(day, equalTo: month, toGranularity: .month)
    }
    let stats: [MonthStat] = d.workplaces.compactMap { wp in
        let mine = recs.filter { $0.wpId == wp.id }
        guard !mine.isEmpty else { return nil }
        return MonthStat(
            wp: wp,
            days: Set(mine.map { $0.date }).count,
            minutes: mine.reduce(0) { $0 + ($1.paidMin(wp) ?? 0) },
            pay: mine.reduce(0) { $0 + ($1.pay(wp) ?? 0) }
        )
    }
    return (Set(recs.map { $0.date }).count, stats)
}

func won(_ v: Double) -> String {
    let f = NumberFormatter()
    f.numberStyle = .decimal
    f.maximumFractionDigits = 0
    return (f.string(from: NSNumber(value: v.rounded())) ?? "0") + "원"
}

func fmtNum(_ d: Double) -> String {
    d.truncatingRemainder(dividingBy: 1) == 0 ? String(Int(d)) : String(format: "%.1f", d)
}

func fmtRate(_ d: Double) -> String {
    var s = String(format: "%.3f", d)
    while s.hasSuffix("0") { s.removeLast() }
    if s.hasSuffix(".") { s.removeLast() }
    return s
}
