import Foundation

func newId() -> String { String(UUID().uuidString.prefix(8)).lowercased() }

/// 직장 / 알바처. days: 1(월)~7(일), dates: "yyyy-MM-dd" 로 지정한 근무일
struct Workplace: Codable, Identifiable, Equatable {
    var id: String
    var name: String = ""
    var wage: Int = 10320
    var start: String = "09:00"
    var end: String = "18:00"
    var breakMin: Int = 60
    var days: Set<Int> = [1, 2, 3, 4, 5]
    var dates: Set<String> = []
    var color: Int = 0
    var lat: Double?
    var lng: Double?
    var radius: Int = 150

    init(id: String = newId(), color: Int = 0) {
        self.id = id
        self.color = color
    }
}

/// 하루 기록. date 는 근무(시프트)가 시작하는 날짜
struct DayRecord: Codable, Identifiable, Equatable {
    var wpId: String
    var date: String
    var arrive: Date?
    var start: Date?
    var end: Date?
    var leave: Date?
    var overtime = false
    var memo = ""
    /// 퇴근 버튼 없이 직장을 떠나서 자동으로 퇴근 처리된 기록
    var autoExit = false

    var id: String { wpId + "|" + date }

    init(wpId: String, date: String) {
        self.wpId = wpId
        self.date = date
    }
}

/// 4대보험 근로자 부담률(%). care 는 건강보험료 대비 장기요양보험료 비율
struct Rates: Codable, Equatable {
    var pension = 4.75
    var health = 3.595
    var care = 13.14
    var employment = 0.9
}

struct AppData: Codable {
    var workplaces: [Workplace] = []
    var records: [DayRecord] = []
    var rates = Rates()
    var exitDelayMin = 3
}

// MARK: - 시간

private let keyFormatter: DateFormatter = {
    let f = DateFormatter()
    f.calendar = Calendar(identifier: .gregorian)
    f.locale = Locale(identifier: "en_US_POSIX")
    f.dateFormat = "yyyy-MM-dd"
    return f
}()

private let hmFormatter: DateFormatter = {
    let f = DateFormatter()
    f.locale = Locale(identifier: "en_US_POSIX")
    f.dateFormat = "HH:mm"
    return f
}()

func dayKey(_ d: Date) -> String {
    keyFormatter.timeZone = .current
    return keyFormatter.string(from: d)
}

func dayFromKey(_ s: String) -> Date? {
    keyFormatter.timeZone = .current
    return keyFormatter.date(from: s)
}

func hm(_ s: String) -> (Int, Int) {
    let p = s.split(separator: ":").compactMap { Int($0) }
    return p.count == 2 ? (p[0], p[1]) : (9, 0)
}

func hmString(_ d: Date) -> String {
    let c = Calendar.current.dateComponents([.hour, .minute], from: d)
    return String(format: "%02d:%02d", c.hour ?? 0, c.minute ?? 0)
}

/// "09:30" 을 오늘 날짜의 Date 로 (DatePicker 용)
func timeDate(_ s: String, on day: Date = Date()) -> Date {
    let (h, m) = hm(s)
    return Calendar.current.date(bySettingHour: h, minute: m, second: 0, of: day) ?? day
}

func fmtTime(_ d: Date?) -> String {
    guard let d else { return "-" }
    hmFormatter.timeZone = .current
    return hmFormatter.string(from: d)
}

func fmtDur(_ min: Int) -> String {
    let h = min / 60
    let m = min % 60
    if m == 0 { return "\(h)시간" }
    if h == 0 { return "\(m)분" }
    return "\(h)시간 \(m)분"
}

/// 1(월) ~ 7(일)
func isoWeekday(_ d: Date) -> Int {
    let w = Calendar.current.component(.weekday, from: d)
    return (w + 5) % 7 + 1
}

let weekdayNames = ["월", "화", "수", "목", "금", "토", "일"]

func startOfDay(_ d: Date) -> Date { Calendar.current.startOfDay(for: d) }

func addDays(_ d: Date, _ n: Int) -> Date { Calendar.current.date(byAdding: .day, value: n, to: d) ?? d }

/// 09:00~10:00 = 60분. 퇴근이 출근보다 이르면 다음 날 퇴근(야간 근무)으로 본다
func shiftMinutes(_ start: String, _ end: String) -> Int {
    let (sh, sm) = hm(start)
    let (eh, em) = hm(end)
    let d = (eh * 60 + em) - (sh * 60 + sm)
    return d <= 0 ? d + 1440 : d
}

extension Workplace {
    var shiftMin: Int { shiftMinutes(start, end) }
    var paidMin: Int { max(0, shiftMin - breakMin) }
    var hasLocation: Bool { lat != nil && lng != nil }

    func isWorkday(_ day: Date) -> Bool {
        dates.contains(dayKey(day)) || days.contains(isoWeekday(day))
    }

    func shiftStart(_ day: Date) -> Date { timeDate(start, on: startOfDay(day)) }
    func shiftEnd(_ day: Date) -> Date { shiftStart(day).addingTimeInterval(Double(shiftMin) * 60) }

    /// 지금 시각이 속한 근무 날짜. 어제 시작한 야간 근무가 아직 안 끝났으면 어제
    func shiftDay(for now: Date) -> Date {
        let t = startOfDay(now)
        let y = addDays(t, -1)
        return isWorkday(y) && now < shiftEnd(y) ? y : t
    }

    var daysLabel: String {
        let d = days.sorted().map { weekdayNames[$0 - 1] }.joined(separator: " ")
        let extra = dates.isEmpty ? "" : "지정일 \(dates.count)일"
        let s = [d, extra].filter { !$0.isEmpty }.joined(separator: " · ")
        return s.isEmpty ? "근무일 없음" : s
    }
}

extension DayRecord {
    var hasAny: Bool { arrive != nil || start != nil || end != nil || leave != nil || !memo.isEmpty }

    func workedMin() -> Int? {
        guard let start, let end else { return nil }
        return max(0, Int(end.timeIntervalSince(start) / 60))
    }

    /// 4시간 이상 일했으면 설정한 휴게시간을 빼고 계산
    func paidMin(_ wp: Workplace) -> Int? {
        guard let w = workedMin() else { return nil }
        return w >= 240 ? max(0, w - wp.breakMin) : w
    }

    func pay(_ wp: Workplace) -> Double? {
        guard let m = paidMin(wp) else { return nil }
        return Double(m) / 60 * Double(wp.wage)
    }
}

extension AppData {
    func wp(_ id: String) -> Workplace? { workplaces.first { $0.id == id } }

    func record(_ wpId: String, _ date: String) -> DayRecord {
        records.first { $0.wpId == wpId && $0.date == date } ?? DayRecord(wpId: wpId, date: date)
    }

    mutating func put(_ r: DayRecord) {
        records.removeAll { $0.wpId == r.wpId && $0.date == r.date }
        records.append(r)
    }

    func activeRecord(_ wpId: String) -> DayRecord? {
        records.filter { $0.wpId == wpId && $0.start != nil && $0.end == nil }
            .max { $0.start! < $1.start! }
    }
}

// MARK: - 저장소

/// 앱이 열려 있을 때 화면 가운데 띄우는 출근/퇴근 팝업
struct Prompt: Identifiable, Equatable {
    enum Kind { case arrive, end, test }
    let id = UUID()
    let kind: Kind
    let wpId: String
}

final class Store: ObservableObject {
    static let shared = Store()

    @Published private(set) var data: AppData
    @Published var prompt: Prompt?

    private let url: URL

    private init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        url = dir.appendingPathComponent("worklog.json")
        if let raw = try? Data(contentsOf: url), let d = try? JSONDecoder().decode(AppData.self, from: raw) {
            data = d
        } else {
            data = AppData()
        }
    }

    func update(_ f: (inout AppData) -> Void) {
        var d = data
        f(&d)
        data = d
        if let raw = try? JSONEncoder().encode(d) {
            // 잠금 상태에서 위치 이벤트로 깨어나도 쓸 수 있도록 첫 잠금 해제 이후 접근 허용
            try? raw.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        }
    }
}
