import CoreLocation
import MapKit
import UIKit
import UserNotifications

// MARK: - 위치 / 직장 반경 감지

struct GeoHit: Identifiable {
    let id = UUID()
    let label: String
    let lat: Double
    let lng: Double
    /// 장소 이름 (직장 이름 자동 채우기용)
    var name = ""
}

/// iOS 의 지역 모니터링(지오펜스)은 앱이 꺼져 있어도 반경을 드나들 때 앱을 깨워 준다
final class LocationService: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let shared = LocationService()

    let manager = CLLocationManager()
    @Published var status: CLAuthorizationStatus = .notDetermined
    @Published var fullAccuracy = true
    private var oneShot: ((CLLocation?) -> Void)?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        status = manager.authorizationStatus
        fullAccuracy = manager.accuracyAuthorization == .fullAccuracy
    }

    func requestWhenInUse() { manager.requestWhenInUseAuthorization() }
    func requestAlways() { manager.requestAlwaysAuthorization() }

    /// 등록된 직장들로 감시 반경을 다시 맞춘다 (iOS 는 앱당 최대 20곳)
    func syncRegions() {
        guard CLLocationManager.isMonitoringAvailable(for: CLCircularRegion.self) else { return }
        for r in manager.monitoredRegions { manager.stopMonitoring(for: r) }
        guard status == .authorizedAlways || status == .authorizedWhenInUse else { return }
        for wp in Store.shared.data.workplaces.filter({ $0.hasLocation }).prefix(20) {
            let region = CLCircularRegion(
                center: CLLocationCoordinate2D(latitude: wp.lat!, longitude: wp.lng!),
                radius: min(Double(wp.radius), manager.maximumRegionMonitoringDistance),
                identifier: wp.id
            )
            region.notifyOnEntry = true
            region.notifyOnExit = true
            manager.startMonitoring(for: region)
        }
    }

    func currentLocation(_ cb: @escaping (CLLocation?) -> Void) {
        oneShot = cb
        manager.requestLocation()
    }

    /// 장소 이름·주소 검색. near 를 주면 그 주변 결과를 먼저 찾는다
    func search(_ q: String, near: MKCoordinateRegion? = nil, _ cb: @escaping ([GeoHit]) -> Void) {
        let req = MKLocalSearch.Request()
        req.naturalLanguageQuery = q
        if let near { req.region = near }
        MKLocalSearch(request: req).start { resp, _ in
            let hits = (resp?.mapItems ?? []).prefix(10).map { item in
                GeoHit(
                    label: item.placemark.title ?? (item.name ?? q),
                    lat: item.placemark.coordinate.latitude,
                    lng: item.placemark.coordinate.longitude,
                    name: item.name ?? ""
                )
            }
            DispatchQueue.main.async { cb(Array(hits)) }
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        status = manager.authorizationStatus
        fullAccuracy = manager.accuracyAuthorization == .fullAccuracy
        syncRegions()
    }

    func locationManager(_ manager: CLLocationManager, didStartMonitoringFor region: CLRegion) {
        // 이미 직장 안에 있는 상태에서 등록했을 때도 도착을 알 수 있게 현재 상태를 물어본다
        manager.requestState(for: region)
    }

    func locationManager(_ manager: CLLocationManager, didDetermineState state: CLRegionState, for region: CLRegion) {
        if state == .inside { Work.onEnter(region.identifier) }
    }

    func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        Work.onEnter(region.identifier)
    }

    func locationManager(_ manager: CLLocationManager, didExitRegion region: CLRegion) {
        Work.onExit(region.identifier)
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        oneShot?(locations.last)
        oneShot = nil
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        oneShot?(nil)
        oneShot = nil
    }
}

// MARK: - 알림

enum Notifier {
    static let center = UNUserNotificationCenter.current()

    static func registerCategories() {
        let clockIn = UNNotificationAction(identifier: "CLOCK_IN", title: "출근", options: [])
        let later = UNNotificationAction(identifier: "LATER", title: "나중에", options: [])
        let overtime = UNNotificationAction(identifier: "OVERTIME", title: "연장 근무", options: [])
        let clockOut = UNNotificationAction(identifier: "CLOCK_OUT", title: "퇴근", options: [])
        center.setNotificationCategories([
            UNNotificationCategory(identifier: "ARRIVE", actions: [clockIn, later], intentIdentifiers: []),
            UNNotificationCategory(identifier: "END", actions: [overtime, clockOut], intentIdentifiers: []),
            UNNotificationCategory(identifier: "OVERTIME", actions: [clockOut], intentIdentifiers: []),
        ])
    }

    static func requestAuth(_ done: ((Bool) -> Void)? = nil) {
        center.requestAuthorization(options: [.alert, .sound, .badge]) { ok, _ in
            DispatchQueue.main.async { done?(ok) }
        }
    }

    static func authStatus() async -> UNAuthorizationStatus {
        await center.notificationSettings().authorizationStatus
    }

    /// at 이 없으면 바로, 있으면 그 시각에 보낸다. 이미 지난 시각이면 보내지 않는다
    private static func add(
        _ id: String, wpId: String, name: String, title: String, body: String,
        category: String? = nil, at: Date? = nil
    ) {
        var trigger: UNNotificationTrigger?
        if let at {
            let t = at.timeIntervalSinceNow
            guard t > 0 else { return }
            trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(1, t), repeats: false)
        }
        let c = UNMutableNotificationContent()
        c.title = title
        c.subtitle = name
        c.body = body
        c.sound = .default
        c.threadIdentifier = wpId
        c.userInfo = ["wp": wpId]
        if let category { c.categoryIdentifier = category }
        center.add(UNNotificationRequest(identifier: id, content: c, trigger: trigger))
    }

    static func arrival(_ wp: Workplace, workday: Bool) {
        add(
            "arrive-\(wp.id)", wpId: wp.id, name: wp.name,
            title: workday ? "직장에 도착하였습니다." : "\(wp.name)에 도착했어요",
            body: workday ? "근무하시겠습니까?" : "오늘은 근무일이 아니에요. 근무하시겠습니까?",
            category: "ARRIVE"
        )
    }

    static func info(_ wp: Workplace, _ text: String) {
        add("info-\(wp.id)", wpId: wp.id, name: wp.name, title: wp.name, body: text)
    }

    static func countdownIds(_ wp: Workplace) -> [String] { (1...10).map { "cd-\(wp.id)-\($0)" } }
    static func overtimeIds(_ wp: Workplace) -> [String] { (0..<16).map { "ot-\(wp.id)-\($0)" } }

    /// 출근하면 그날 퇴근 10분 전부터 1분마다 카운트다운, 퇴근 시각에 퇴근 알림을 예약
    static func scheduleShiftEnd(_ wp: Workplace, _ rec: DayRecord) {
        guard let day = dayFromKey(rec.date) else { return }
        remove(countdownIds(wp) + ["end-\(wp.id)"])
        let end = wp.shiftEnd(day)
        for m in 1...10 {
            add(
                "cd-\(wp.id)-\(m)", wpId: wp.id, name: wp.name,
                title: "퇴근까지 \(m)분 남았습니다.", body: "\(wp.name) · 퇴근 \(wp.end)",
                at: end.addingTimeInterval(Double(-m * 60))
            )
        }
        add(
            "end-\(wp.id)", wpId: wp.id, name: wp.name,
            title: "퇴근시간이 되었습니다.", body: "퇴근하시겠습니까?", category: "END", at: end
        )
    }

    /// iOS 는 지울 수 없는 알림을 만들 수 없어서, 퇴근할 때까지 30분마다 다시 알린다 (최대 8시간)
    static func scheduleOvertime(_ wp: Workplace) {
        remove(overtimeIds(wp))
        for k in 0..<16 {
            add(
                "ot-\(wp.id)-\(k)", wpId: wp.id, name: wp.name,
                title: "연장 근무를 하는 당신.", body: "퇴근하시겠습니까?", category: "OVERTIME",
                at: k == 0 ? nil : Date().addingTimeInterval(Double(k * 30 * 60))
            )
        }
    }

    static func clearShift(_ wp: Workplace) {
        remove(countdownIds(wp) + overtimeIds(wp) + ["end-\(wp.id)", "arrive-\(wp.id)"])
    }

    static func remove(_ ids: [String]) {
        center.removePendingNotificationRequests(withIdentifiers: ids)
        center.removeDeliveredNotifications(withIdentifiers: ids)
    }

    /// 앱을 열면 이미 지나간 카운트다운 알림은 알림 센터에서 치운다
    static func cleanupDeliveredCountdowns() {
        center.getDeliveredNotifications { list in
            let ids = list.map { $0.request.identifier }.filter { $0.hasPrefix("cd-") }
            center.removeDeliveredNotifications(withIdentifiers: ids)
        }
    }

    static func test() {
        add(
            "test", wpId: "test", name: "출근콕", title: "알림 테스트",
            body: "이 알림이 애플워치에도 보이면 연동 완료!", category: "ARRIVE",
            at: Date().addingTimeInterval(5)
        )
    }
}

/// 알림의 출근·퇴근·연장 근무 버튼 처리. 버튼은 앱을 열지 않고 백그라운드에서 기록한다
final class NotificationHandler: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationHandler()

    func userNotificationCenter(
        _ center: UNUserNotificationCenter, willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let c = notification.request.content
        if let wp = c.userInfo["wp"] as? String, wp != "test" {
            DispatchQueue.main.async {
                if c.categoryIdentifier == "END" { Store.shared.prompt = Prompt(kind: .end, wpId: wp) }
            }
        }
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let c = response.notification.request.content
        let wp = c.userInfo["wp"] as? String ?? ""
        let action = response.actionIdentifier
        DispatchQueue.main.async {
            let store = Store.shared
            switch action {
            case "CLOCK_IN": Work.clockIn(wp)
            case "CLOCK_OUT": Work.clockOut(wp)
            case "OVERTIME": Work.startOvertime(wp)
            case "LATER": Notifier.remove(["arrive-\(wp)"])
            case UNNotificationDefaultActionIdentifier:
                // 알림 자체를 눌러 앱을 열면 화면 가운데 팝업으로 이어서 보여 준다
                if c.categoryIdentifier == "ARRIVE", store.data.wp(wp) != nil, store.data.activeRecord(wp) == nil {
                    store.prompt = Prompt(kind: .arrive, wpId: wp)
                } else if c.categoryIdentifier == "END" || c.categoryIdentifier == "OVERTIME",
                          store.data.activeRecord(wp) != nil {
                    store.prompt = Prompt(kind: .end, wpId: wp)
                }
            default: break
            }
            completionHandler()
        }
    }
}

func openAppSettings() {
    if let url = URL(string: UIApplication.openSettingsURLString) {
        UIApplication.shared.open(url)
    }
}
