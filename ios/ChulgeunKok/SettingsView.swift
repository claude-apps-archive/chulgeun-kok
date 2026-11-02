import SwiftUI
import UserNotifications

struct SettingsView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var location: LocationService
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var phase
    @State private var notif: UNAuthorizationStatus = .notDetermined

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    PermRow(title: "알림", desc: "출근·퇴근 알림과 카운트다운을 받아요", ok: notif == .authorized) {
                        if notif == .notDetermined {
                            Notifier.requestAuth { _ in Task { await refresh() } }
                        } else {
                            openAppSettings()
                        }
                    }
                    PermRow(
                        title: "위치 '항상 허용'",
                        desc: "앱을 닫아도 직장 도착·출발을 감지하려면 '항상'으로 바꿔 주세요",
                        ok: location.status == .authorizedAlways
                    ) {
                        switch location.status {
                        case .notDetermined: location.requestWhenInUse()
                        case .authorizedWhenInUse: location.requestAlways()
                        default: openAppSettings()
                        }
                    }
                    PermRow(title: "정확한 위치", desc: "꺼져 있으면 반경 감지가 잘 안 돼요", ok: location.fullAccuracy) {
                        openAppSettings()
                    }
                } header: {
                    Text("권한")
                } footer: {
                    Text("위치는 처음에 '앱을 사용하는 동안'만 고를 수 있어요. 그다음 한 번 더 눌러 '항상 허용으로 변경'을 고르거나, 설정 > 출근콕 > 위치에서 '항상'으로 바꿔 주세요.")
                }

                Section {
                    Button("알림 테스트 (5초 뒤 도착)") { Notifier.test() }
                    Button("팝업 미리보기") {
                        store.prompt = Prompt(kind: .test, wpId: store.data.workplaces.first?.id ?? "test")
                        dismiss()
                    }
                } header: {
                    Text("테스트")
                } footer: {
                    Text("알림 테스트를 누르고 화면을 끄면, 잠금 화면과 애플워치에 알림이 오는지 확인할 수 있어요.")
                }

                Section {
                    Picker("떠남 확정 대기", selection: Binding(
                        get: { store.data.exitDelayMin },
                        set: { v in store.update { $0.exitDelayMin = v } }
                    )) {
                        ForEach([1, 3, 5, 10], id: \.self) { Text("\($0)분").tag($0) }
                    }
                    .pickerStyle(.segmented)
                } header: {
                    Text("떠남 확정 대기 시간")
                } footer: {
                    Text("반경을 벗어나 자동 퇴근된 뒤 이 시간 안에 다시 돌아오면 GPS 오차로 보고 퇴근 기록을 되돌려요.")
                }

                Section {
                    RateField(label: "국민연금", value: rate(\.pension))
                    RateField(label: "건강보험", value: rate(\.health))
                    RateField(label: "장기요양 (건강보험료 대비)", value: rate(\.care))
                    RateField(label: "고용보험", value: rate(\.employment))
                    Button("기본값으로 되돌리기") { store.update { $0.rates = Rates() } }
                } header: {
                    Text("4대보험 근로자 부담률 (%)")
                }

                Section {
                    Text("• 아이폰은 다른 앱 위에 화면을 띄울 수 없어서, 출근·퇴근은 알림 배너의 버튼(길게 누르기)으로 고르거나 앱을 열면 가운데 팝업이 떠요.\n• 연장 근무 알림은 iOS에서 지워지지 않게 할 수 없어서, 퇴근할 때까지 30분마다 다시 알려 드려요.\n• 애플워치를 쓰면 알림과 출근·퇴근 버튼이 워치에도 떠요. (Watch 앱 > 알림 > 출근콕 미러링)")
                        .font(.system(size: 13))
                        .foregroundColor(Palette.ink)
                } header: {
                    Text("아이폰에서 알아 둘 점")
                }
            }
            .navigationTitle("설정")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("완료") { dismiss() } } }
            .task { await refresh() }
            .onChange(of: phase) { p in
                if p == .active { Task { await refresh() } }
            }
        }
    }

    private func refresh() async {
        notif = await Notifier.authStatus()
    }

    private func rate(_ key: WritableKeyPath<Rates, Double>) -> Binding<Double> {
        Binding(get: { store.data.rates[keyPath: key] }, set: { v in store.update { $0.rates[keyPath: key] = v } })
    }
}

struct PermRow: View {
    let title: String
    let desc: String
    let ok: Bool
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.system(size: 15, weight: .semibold)).foregroundColor(Palette.ink)
                    Text(desc).font(.system(size: 12)).foregroundColor(Palette.sub)
                }
                Spacer()
                Text(ok ? "허용됨" : "설정하기")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(ok ? Color(hex: 0x1E9E5A) : Palette.coral)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Capsule().fill(ok ? Color(hex: 0xE3F7EC) : Color(hex: 0xFFE9E3)))
            }
        }
        .disabled(ok)
    }
}

struct RateField: View {
    let label: String
    @Binding var value: Double

    var body: some View {
        HStack {
            Text(label)
            Spacer()
            TextField("", value: $value, format: .number)
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.trailing)
                .frame(width: 90)
        }
    }
}
