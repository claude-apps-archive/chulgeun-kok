import SwiftUI
import UserNotifications

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // 위치 이벤트로 앱이 백그라운드에서 깨어날 때도 delegate 가 먼저 준비돼 있어야 한다
        UNUserNotificationCenter.current().delegate = NotificationHandler.shared
        Notifier.registerCategories()
        LocationService.shared.syncRegions()
        return true
    }
}

@main
struct ChulgeunKokApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var store = Store.shared
    @StateObject private var location = LocationService.shared

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
                .environmentObject(location)
                .preferredColorScheme(.light)
        }
    }
}

struct ContentView: View {
    @EnvironmentObject var store: Store
    @Environment(\.scenePhase) private var phase

    var body: some View {
        ZStack {
            TabView {
                HomeView().tabItem { Label("홈", systemImage: "house.fill") }
                PlacesView().tabItem { Label("직장", systemImage: "mappin.and.ellipse") }
                CalendarView().tabItem { Label("달력", systemImage: "calendar") }
                PayView().tabItem { Label("급여", systemImage: "wonsign.circle.fill") }
            }
            .tint(Palette.purple)

            if let p = store.prompt {
                if let wp = store.data.wp(p.wpId) {
                    PromptOverlay(prompt: p, wp: wp)
                } else if p.kind == .test {
                    PromptOverlay(prompt: p, wp: { var w = Workplace(id: "test", color: 5); w.name = "테스트 직장"; return w }())
                }
            }
        }
        .animation(.easeInOut(duration: 0.2), value: store.prompt)
        .onAppear { Notifier.requestAuth() }
        .onChange(of: phase) { p in
            if p == .active { Notifier.cleanupDeliveredCountdowns() }
        }
    }
}
