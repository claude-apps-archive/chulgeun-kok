import SwiftUI
import UserNotifications

struct HomeView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var location: LocationService
    @Environment(\.scenePhase) private var phase
    @State private var showSettings = false
    @State private var showMonths = false
    @State private var editing: Workplace?
    @State private var notifOK = true

    var body: some View {
        TimelineView(.periodic(from: .now, by: 30)) { tl in
            let now = tl.date
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    header(now)
                    if !permsOK { banner }
                    heroCard(now)
                    Text("오늘의 근무")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(Palette.ink)
                        .padding(.top, 26)
                        .padding(.bottom, 12)
                    if store.data.workplaces.isEmpty {
                        EmptyCard(text: "아직 등록된 직장이 없어요.\n알바처나 회사를 추가해 보세요!", button: "직장 추가하기") {
                            editing = Workplace()
                        }
                    }
                    let today = startOfDay(now)
                    ForEach(store.data.workplaces.sorted { a, b in
                        (a.isWorkday(today) ? 0 : 1) < (b.isWorkday(today) ? 0 : 1)
                    }) { wp in
                        WorkCard(wp: wp, now: now).padding(.bottom, 12)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
            }
        }
        .background(Color.white)
        .sheet(isPresented: $showSettings) { SettingsView() }
        .sheet(isPresented: $showMonths) {
            MonthSummaryView(start: Calendar.current.date(byAdding: .month, value: -1, to: Date()) ?? Date())
        }
        .sheet(item: $editing) { PlaceEditor(initial: $0) }
        .task { await refreshNotif() }
        .onChange(of: phase) { p in
            if p == .active { Task { await refreshNotif() } }
        }
    }

    private var permsOK: Bool { notifOK && location.status == .authorizedAlways }

    private func refreshNotif() async {
        notifOK = await Notifier.authStatus() == .authorized
    }

    private func header(_ now: Date) -> some View {
        let c = Calendar.current.dateComponents([.month, .day], from: now)
        return HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text("\(c.month ?? 1)월 \(c.day ?? 1)일")
                    .font(.system(size: 26, weight: .bold))
                    .foregroundColor(Palette.ink)
                Text("\(weekdayNames[isoWeekday(now) - 1])요일")
                    .font(.system(size: 15))
                    .foregroundColor(Palette.sub)
            }
            Spacer()
            Button { showSettings = true } label: {
                Image(systemName: "gearshape.fill").font(.system(size: 22)).foregroundColor(Palette.ink)
            }
        }
        .padding(.top, 24)
        .padding(.bottom, 16)
    }

    private var banner: some View {
        Button { showSettings = true } label: {
            HStack(spacing: 10) {
                Image(systemName: "exclamationmark.triangle.fill").foregroundColor(Color(hex: 0xFF7A2F))
                Text("자동 출퇴근 감지를 하려면 권한 설정이 필요해요")
                    .font(.system(size: 13))
                    .foregroundColor(Palette.ink)
                Spacer()
                Image(systemName: "chevron.right").foregroundColor(Palette.sub)
            }
            .padding(14)
            .background(Color(hex: 0xFFF1E8))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
        .padding(.bottom, 14)
    }

    private func heroCard(_ now: Date) -> some View {
        let active = store.data.workplaces.compactMap { wp in store.data.activeRecord(wp.id).map { (wp, $0) } }.first
        let month = monthStats(store.data, now)
        let pay = month.stats.reduce(0) { $0 + $1.pay }
        return Button { showMonths = true } label: {
            HStack(spacing: 14) {
                Buddy(color: active.map { swatch($0.0.color).b } ?? Color(hex: 0xFF8A4C),
                      shape: active?.0.color ?? 4, size: 92)
                VStack(alignment: .leading, spacing: 4) {
                    Text("이번 달 \(month.days)일 출근\n약 \(won(pay))")
                        .font(.system(size: 21, weight: .bold))
                        .foregroundColor(Palette.purpleDeep)
                        .lineSpacing(3)
                    if let act = active, let s = act.1.start {
                        Text("\(act.0.name)에서 \(fmtDur(Int(now.timeIntervalSince(s) / 60)))째 열일 중!")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(Palette.purple)
                    } else {
                        Text("오늘도 잘하고 있어요!")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(Palette.purple)
                    }
                    Text("눌러서 지난달 보기 ›")
                        .font(.system(size: 11))
                        .foregroundColor(Palette.purple.opacity(0.7))
                }
                Spacer(minLength: 0)
            }
            .padding(18)
            .background(Palette.hero)
            .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

struct WorkCard: View {
    let wp: Workplace
    let now: Date
    @EnvironmentObject var store: Store

    var body: some View {
        let sw = swatch(wp.color)
        let day = wp.shiftDay(for: now)
        let active = store.data.activeRecord(wp.id)
        let rec = active ?? store.data.record(wp.id, dayKey(day))
        let workday = wp.isWorkday(day)
        let done = active == nil && rec.end != nil

        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                ZStack {
                    Circle().fill(Color.white).frame(width: 46, height: 46)
                    Buddy(color: sw.b, shape: wp.color, size: 36, happy: workday || active != nil)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(wp.start) ~ \(wp.end) · \(fmtDur(wp.paidMin))")
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.88))
                    Text(wp.name)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                }
                Spacer()
                ZStack {
                    Circle().fill(Color.white.opacity(done ? 1 : 0.35)).frame(width: 30, height: 30)
                    if done { Image(systemName: "checkmark").font(.system(size: 14, weight: .bold)).foregroundColor(sw.b) }
                }
            }
            Text(status(active: active, rec: rec, workday: workday))
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(.white)
                .padding(.top, 10)
            if let l = rec.leave {
                Text("직장 떠남 \(fmtTime(l))").font(.system(size: 12)).foregroundColor(.white.opacity(0.88))
            }
            if active != nil || rec.end == nil {
                Button {
                    if active != nil { Work.clockOut(wp.id) } else { Work.clockIn(wp.id) }
                } label: {
                    Text(active != nil ? "퇴근하기" : "출근하기")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(sw.b)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 9)
                        .background(Color.white)
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
                .padding(.top, 12)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(sw.gradient)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func status(active: DayRecord?, rec: DayRecord, workday: Bool) -> String {
        if let a = active, let s = a.start {
            return "출근 \(fmtTime(s)) · \(fmtDur(Int(now.timeIntervalSince(s) / 60)))째 근무 중" + (a.overtime ? " (연장 근무)" : "")
        }
        if rec.end != nil { return "출근 \(fmtTime(rec.start))  →  퇴근 \(fmtTime(rec.end))" }
        if let a = rec.arrive { return "도착 \(fmtTime(a)) · 아직 출근 전이에요" }
        return workday ? "오늘 근무일이에요" : "오늘은 쉬는 날"
    }
}
