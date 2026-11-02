import SwiftUI

struct PrimaryButton: View {
    var title: String
    var color: Color = Palette.purple
    var textColor: Color = .white
    var height: CGFloat = 54
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(textColor)
                .frame(maxWidth: .infinity, minHeight: height)
                .background(color)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

struct SoftButton: View {
    var title: String
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(Palette.purple)
                .padding(.horizontal, 14)
                .padding(.vertical, 11)
                .background(Palette.lavender)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

struct Pill: View {
    var title: String
    var selected: Bool
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 14, weight: selected ? .semibold : .regular))
                .foregroundColor(selected ? .white : Palette.ink)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .frame(maxWidth: .infinity)
                .background(selected ? Palette.purple : Palette.field)
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

struct FieldLabel: View {
    var text: String
    var body: some View {
        Text(text)
            .font(.system(size: 14, weight: .semibold))
            .foregroundColor(Palette.ink)
            .padding(.top, 20)
            .padding(.bottom, 8)
    }
}

/// 디자인의 연회색 둥근 입력칸
struct FieldStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(.horizontal, 14)
            .padding(.vertical, 14)
            .background(Palette.field)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

extension View {
    func fieldStyle() -> some View { modifier(FieldStyle()) }
}

struct EmptyCard: View {
    var text: String
    var button: String
    var action: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            HStack(spacing: -10) {
                Buddy(color: swatch(0).b, shape: 4, size: 48)
                Buddy(color: swatch(2).b, shape: 5, size: 48)
                Buddy(color: swatch(1).b, shape: 0, size: 48, happy: false)
            }
            Text(text)
                .font(.system(size: 14))
                .foregroundColor(Palette.sub)
                .multilineTextAlignment(.center)
            PrimaryButton(title: button, action: action)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(Palette.field)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}

struct ResultRow: View {
    var label: String
    var value: String
    var bold = false
    var color: Color = Palette.ink

    var body: some View {
        HStack {
            Text(label).foregroundColor(bold ? Palette.ink : Palette.sub)
            Spacer()
            Text(value).foregroundColor(color).fontWeight(bold ? .bold : .regular)
        }
        .font(.system(size: 14))
        .padding(.vertical, 4)
    }
}

/// 앱이 열려 있을 때 화면 가운데 뜨는 출근/퇴근 팝업
struct PromptOverlay: View {
    let prompt: Prompt
    let wp: Workplace
    @EnvironmentObject var store: Store

    var body: some View {
        let sw = swatch(wp.color)
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea()
            VStack(spacing: 0) {
                ZStack {
                    Circle().fill(sw.a.opacity(0.3)).frame(width: 120, height: 120)
                    Buddy(color: sw.b, shape: wp.color, size: 84, happy: prompt.kind != .end)
                }
                Text(wp.name).font(.system(size: 14)).foregroundColor(Palette.sub).padding(.top, 16)
                Text(title)
                    .font(.system(size: 21, weight: .bold))
                    .foregroundColor(Palette.ink)
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
                    .padding(.top, 6)
                    .padding(.bottom, 24)
                if prompt.kind == .end {
                    HStack(spacing: 10) {
                        PrimaryButton(title: "연장 근무", color: Palette.lavender, textColor: Palette.purple) {
                            Work.startOvertime(wp.id)
                        }
                        PrimaryButton(title: "퇴근") { Work.clockOut(wp.id) }
                    }
                } else {
                    PrimaryButton(title: "출근", height: 60) {
                        if prompt.kind == .arrive { Work.clockIn(wp.id) } else { store.prompt = nil }
                    }
                    PrimaryButton(title: "나중에", color: .white, textColor: Palette.sub) {
                        Notifier.remove(["arrive-\(wp.id)"])
                        store.prompt = nil
                    }
                    .padding(.top, 4)
                }
            }
            .padding(24)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 32, style: .continuous))
            .padding(28)
        }
        .transition(.opacity)
    }

    private var title: String {
        switch prompt.kind {
        case .end: return "퇴근시간이 되었습니다.\n퇴근하시겠습니까?"
        case .test: return "팝업 미리보기예요!\n앱을 쓰는 중에는 이렇게 떠요"
        case .arrive: return "직장에 도착하였습니다.\n근무하시겠습니까?"
        }
    }
}
