import CoreLocation
import Foundation

/// 출퇴근 규칙: 위치 감지·알림 버튼·화면 버튼이 모두 여기로 모인다
enum Work {
    private static var store: Store { Store.shared }

    /// 직장 반경 안으로 들어옴
    static func onEnter(_ wpId: String, at: Date = Date()) {
        let d = store.data
        guard let wp = d.wp(wpId), d.activeRecord(wpId) == nil else { return }

        // GPS 가 잠깐 튀어서 떠난 것으로 자동 퇴근됐다면 몇 분 안에 돌아왔을 때 되돌린다
        if var r = d.records.filter({ $0.wpId == wpId && $0.autoExit && $0.end != nil }).max(by: { $0.end! < $1.end! }),
           at.timeIntervalSince(r.end!) < Double(d.exitDelayMin * 60) {
            r.end = nil
            r.leave = nil
            r.autoExit = false
            store.update { $0.put(r) }
            Notifier.scheduleShiftEnd(wp, r)
            if r.overtime { Notifier.scheduleOvertime(wp) }
            return
        }

        guard bestAtSamePlace(d, wp, at) else { return }
        let date = dayKey(wp.shiftDay(for: at))
        let rec = d.record(wpId, date)
        if rec.end != nil { return }
        let fresh = rec.arrive == nil || rec.leave != nil
        if rec.arrive == nil {
            store.update {
                var r = $0.record(wpId, date)
                r.arrive = at
                $0.put(r)
            }
        }
        guard fresh else { return }
        let workday = wp.isWorkday(dayFromKey(date) ?? at)
        Notifier.arrival(wp, workday: workday)
        if workday { store.prompt = Prompt(kind: .arrive, wpId: wpId) }
    }

    /// 직장 반경 밖으로 나감
    static func onExit(_ wpId: String, at: Date = Date()) {
        let d = store.data
        guard let wp = d.wp(wpId) else { return }
        if var a = d.activeRecord(wpId) {
            // 퇴근 버튼을 안 눌렀어도 떠난 시간을 퇴근 시간으로 기록
            a.end = at
            a.leave = at
            a.autoExit = true
            store.update { $0.put(a) }
            Notifier.clearShift(wp)
            Notifier.info(wp, "직장을 떠나 퇴근이 기록되었어요 (\(fmtTime(at)))")
        } else if var r = d.records.filter({
            guard $0.wpId == wpId, $0.leave == nil, let t = $0.arrive ?? $0.start else { return false }
            return at.timeIntervalSince(t) < 36 * 3600
        }).max(by: { $0.date < $1.date }) {
            r.leave = at
            store.update { $0.put(r) }
            Notifier.remove(["arrive-\(wpId)"])
            if r.end != nil { Notifier.info(wp, "직장을 떠났어요 (\(fmtTime(at)))") }
        }
        if store.prompt?.wpId == wpId { store.prompt = nil }
    }

    static func markArrive(_ wpId: String, at: Date = Date()) {
        guard let wp = store.data.wp(wpId) else { return }
        let date = dayKey(wp.shiftDay(for: at))
        store.update {
            var r = $0.record(wpId, date)
            if r.arrive == nil {
                r.arrive = at
                $0.put(r)
            }
        }
    }

    static func clockIn(_ wpId: String, at: Date = Date()) {
        let d = store.data
        guard let wp = d.wp(wpId), d.activeRecord(wpId) == nil else { return }
        let date = dayKey(wp.shiftDay(for: at))
        var r = d.record(wpId, date)
        if r.end != nil {
            r.start = at
            r.end = nil
            r.leave = nil
            r.overtime = false
            r.autoExit = false
        } else {
            if r.arrive == nil { r.arrive = at }
            r.start = at
            r.leave = nil
        }
        store.update { $0.put(r) }
        Notifier.remove(["arrive-\(wpId)"])
        Notifier.info(wp, "출근이 기록되었어요 (\(fmtTime(at))) 오늘도 화이팅!")
        Notifier.scheduleShiftEnd(wp, r)
        store.prompt = nil
    }

    static func clockOut(_ wpId: String, at: Date = Date()) {
        let d = store.data
        guard let wp = d.wp(wpId), var a = d.activeRecord(wpId) else { return }
        a.end = at
        a.autoExit = false
        store.update { $0.put(a) }
        Notifier.clearShift(wp)
        Notifier.info(wp, "퇴근이 기록되었어요 (\(fmtTime(at))) 오늘도 수고하셨어요!")
        store.prompt = nil
    }

    static func startOvertime(_ wpId: String) {
        let d = store.data
        guard let wp = d.wp(wpId), var a = d.activeRecord(wpId) else { return }
        a.overtime = true
        store.update { $0.put(a) }
        Notifier.remove(Notifier.countdownIds(wp) + ["end-\(wp.id)"])
        Notifier.scheduleOvertime(wp)
        store.prompt = nil
    }

    /// 달력에서 기록을 고친 뒤 알림 예약을 맞춘다
    static func afterEdit(_ wpId: String) {
        let d = store.data
        guard let wp = d.wp(wpId) else { return }
        Notifier.clearShift(wp)
        if let a = d.activeRecord(wpId) {
            Notifier.scheduleShiftEnd(wp, a)
            if a.overtime { Notifier.scheduleOvertime(wp) }
        }
    }

    // MARK: 직장 관리

    @discardableResult
    static func saveWorkplace(_ wp: Workplace) -> Workplace {
        store.update { d in
            if let i = d.workplaces.firstIndex(where: { $0.id == wp.id }) {
                d.workplaces[i] = wp
            } else {
                d.workplaces.append(wp)
            }
        }
        LocationService.shared.syncRegions()
        afterEdit(wp.id)
        return wp
    }

    /// 원래 직장 바로 아래에 복사본을 넣는다 (기록은 복사하지 않음)
    static func copyWorkplace(_ wpId: String) -> Workplace? {
        guard let src = store.data.wp(wpId) else { return nil }
        var copy = src
        copy.id = newId()
        copy.name = src.name + " (복사)"
        store.update { d in
            let i = d.workplaces.firstIndex { $0.id == wpId } ?? (d.workplaces.count - 1)
            d.workplaces.insert(copy, at: i + 1)
        }
        LocationService.shared.syncRegions()
        return copy
    }

    static func deleteWorkplace(_ wpId: String) {
        if let wp = store.data.wp(wpId) { Notifier.clearShift(wp) }
        Notifier.remove(["info-\(wpId)"])
        store.update { d in
            d.workplaces.removeAll { $0.id == wpId }
            d.records.removeAll { $0.wpId == wpId }
        }
        LocationService.shared.syncRegions()
    }

    /// 같은 장소에 직장이 여러 개면(복사한 시간대별 일) 출근 시간이 지금과 가장 가까운
    /// 근무일 직장 하나만 도착 알림을 띄운다
    private static func bestAtSamePlace(_ d: AppData, _ wp: Workplace, _ at: Date) -> Bool {
        guard let lat = wp.lat, let lng = wp.lng else { return true }
        let here = CLLocation(latitude: lat, longitude: lng)
        let group = d.workplaces.filter { o in
            guard let la = o.lat, let ln = o.lng else { return false }
            return here.distance(from: CLLocation(latitude: la, longitude: ln)) < 50
        }
        if group.count <= 1 { return true }
        func score(_ o: Workplace) -> Double {
            let day = o.shiftDay(for: at)
            guard o.isWorkday(day) else { return .greatestFiniteMagnitude }
            return abs(at.timeIntervalSince(o.shiftStart(day)))
        }
        return group.min { score($0) < score($1) }?.id == wp.id
    }
}
