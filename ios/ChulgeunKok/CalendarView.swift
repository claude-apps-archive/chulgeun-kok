import SwiftUI

struct CalendarView: View {
    @EnvironmentObject var store: Store
    @State private var month = Calendar.current.date(from: Calendar.current.dateComponents([.year, .month], from: Date())) ?? Date()
    @State private var sel = startOfDay(Date())
    @State private var editing: DayRecord?

    private let cal = Calendar.current

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    monthHeader
                    grid
                    legend.padding(.top, 12)
                    detail.padding(.top, 22)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 30)
            }
            .navigationTitle("달력")
            .sheet(item: $editing) { r in
                if let wp = store.data.wp(r.wpId) { RecordEditor(initial: r, wp: wp) }
            }
        }
    }

    private var monthHeader: some View {
        let c = cal.dateComponents([.year, .month], from: month)
        return HStack {
            Button { month = cal.date(byAdding: .month, value: -1, to: month) ?? month } label: {
                Image(systemName: "chevron.left").padding(10)
            }
            Spacer()
            Text("\(String(c.year ?? 0))년 \(c.month ?? 0)월").font(.system(size: 17, weight: .bold))
            Spacer()
            Button { month = cal.date(byAdding: .month, value: 1, to: month) ?? month } label: {
                Image(systemName: "chevron.right").padding(10)
            }
        }
        .foregroundColor(Palette.ink)
        .padding(.vertical, 4)
    }

    private var cells: [Date?] {
        let lead = isoWeekday(month) - 1
        let count = cal.range(of: .day, in: .month, for: month)?.count ?? 30
        return Array(repeating: nil, count: lead) + (0..<count).map { addDays(month, $0) }
    }

    private var grid: some View {
        let attended = Dictionary(grouping: store.data.records.filter { $0.start != nil }, by: { $0.date })
        let today = startOfDay(Date())
        return VStack(spacing: 0) {
            HStack {
                ForEach(weekdayNames, id: \.self) { n in
                    Text(n).font(.system(size: 13)).foregroundColor(Palette.sub).frame(maxWidth: .infinity)
                }
            }
            .padding(.bottom, 6)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 0), count: 7), spacing: 0) {
                ForEach(Array(cells.enumerated()), id: \.offset) { _, day in
                    if let d = day {
                        let isSel = cal.isDate(d, inSameDayAs: sel)
                        let isToday = cal.isDate(d, inSameDayAs: today)
                        Button { sel = d } label: {
                            VStack(spacing: 2) {
                                Text("\(cal.component(.day, from: d))")
                                    .font(.system(size: 15, weight: isSel || isToday ? .bold : .regular))
                                    .foregroundColor(isSel ? .white : isToday ? Palette.purple : isoWeekday(d) == 7 ? Palette.coral : Palette.ink)
                                    .frame(width: 34, height: 34)
                                    .background(Circle().fill(isSel ? Palette.purple : .clear))
                                HStack(spacing: 2) {
                                    ForEach((attended[dayKey(d)] ?? []).prefix(3)) { r in
                                        Circle().fill(swatch(store.data.wp(r.wpId)?.color ?? 0).b).frame(width: 6, height: 6)
                                    }
                                }
                                .frame(height: 8)
                            }
                            .frame(maxWidth: .infinity, minHeight: 54)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    } else {
                        Color.clear.frame(height: 54)
                    }
                }
            }
        }
    }

    private var legend: some View {
        VStack(spacing: 0) {
            ForEach(store.data.workplaces) { wp in
                let att = store.data.records.filter { r in
                    guard r.wpId == wp.id, r.start != nil, let d = dayFromKey(r.date) else { return false }
                    return cal.isDate(d, equalTo: month, toGranularity: .month)
                }.count
                let count = cal.range(of: .day, in: .month, for: month)?.count ?? 30
                let sched = (0..<count).filter { wp.isWorkday(addDays(month, $0)) }.count
                HStack {
                    Circle().fill(swatch(wp.color).b).frame(width: 12, height: 12)
                    Text(wp.name).font(.system(size: 15)).foregroundColor(Palette.ink).padding(.leading, 8)
                    Spacer()
                    Text("\(att)/\(sched)").font(.system(size: 14)).foregroundColor(Palette.sub)
                }
                .padding(.vertical, 12)
                .padding(.horizontal, 4)
                Divider()
            }
        }
    }

    private var detail: some View {
        let key = dayKey(sel)
        let recs = store.data.records.filter { $0.date == key && $0.hasAny }
        let missing = store.data.workplaces.filter { w in !recs.contains { $0.wpId == w.id } }
        let c = cal.dateComponents([.month, .day], from: sel)
        return VStack(alignment: .leading, spacing: 0) {
            Text("\(c.month ?? 0)월 \(c.day ?? 0)일 (\(weekdayNames[isoWeekday(sel) - 1]))")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(Palette.ink)
                .padding(.horizontal, 4)
            if recs.isEmpty {
                Text("이 날의 기록이 없어요").font(.system(size: 14)).foregroundColor(Palette.sub).padding(4).padding(.top, 4)
            }
            ForEach(recs) { r in
                if let wp = store.data.wp(r.wpId) {
                    RecordCard(rec: r, wp: wp) { editing = r }.padding(.top, 12)
                }
            }
            if !missing.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(missing) { w in
                            SoftButton(title: "+ \(w.name) 기록 추가") { editing = DayRecord(wpId: w.id, date: key) }
                        }
                    }
                }
                .padding(.top, 12)
            }
        }
    }
}

struct RecordCard: View {
    let rec: DayRecord
    let wp: Workplace
    var onEdit: () -> Void

    var body: some View {
        let sw = swatch(wp.color)
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Buddy(color: sw.b, shape: wp.color, size: 36)
                VStack(alignment: .leading, spacing: 2) {
                    Text(wp.name).font(.system(size: 16, weight: .bold)).foregroundColor(Palette.ink)
                    Text("예정 \(wp.start) ~ \(wp.end)" + (rec.overtime ? " · 연장 근무" : ""))
                        .font(.system(size: 12)).foregroundColor(Palette.sub)
                }
                .padding(.leading, 6)
                Spacer()
                Button("수정", action: onEdit).font(.system(size: 15, weight: .bold)).foregroundColor(Palette.purple)
            }
            HStack {
                ForEach([("도착", rec.arrive), ("출근", rec.start), ("퇴근", rec.end), ("떠남", rec.leave)], id: \.0) { item in
                    VStack(spacing: 2) {
                        Text(item.0).font(.system(size: 12)).foregroundColor(Palette.sub)
                        Text(fmtTime(item.1)).font(.system(size: 16, weight: .bold)).foregroundColor(Palette.ink)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .padding(.vertical, 10)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .padding(.top, 10)
            if let m = rec.workedMin() {
                Text("근무 \(fmtDur(m)) · 일급 약 \(won(rec.pay(wp) ?? 0))")
                    .font(.system(size: 14, weight: .semibold)).foregroundColor(sw.b).padding(.top, 10)
            }
            if !rec.memo.isEmpty {
                Text("한 일: \(rec.memo)").font(.system(size: 13)).foregroundColor(Palette.ink).padding(.top, 6)
            }
        }
        .padding(16)
        .background(sw.a.opacity(0.18))
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

struct RecordEditor: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) private var dismiss
    @State private var r: DayRecord
    let wp: Workplace
    private let isNew: Bool

    init(initial: DayRecord, wp: Workplace) {
        _r = State(initialValue: initial)
        self.wp = wp
        isNew = !initial.hasAny
    }

    var body: some View {
        let day = dayFromKey(r.date) ?? Date()
        NavigationStack {
            Form {
                Section("시간") {
                    TimeRow(label: "도착", value: $r.arrive, def: wp.shiftStart(day).addingTimeInterval(-600))
                    TimeRow(label: "출근", value: $r.start, def: wp.shiftStart(day))
                    TimeRow(label: "퇴근", value: $r.end, def: wp.shiftEnd(day))
                    TimeRow(label: "떠남", value: $r.leave, def: wp.shiftEnd(day))
                }
                Section("메모 (오늘 한 일)") {
                    TextField("예: 홀 서빙, 재고 정리", text: $r.memo)
                }
                if !isNew {
                    Section {
                        Button("이 기록 삭제", role: .destructive) {
                            store.update { d in d.records.removeAll { $0.wpId == r.wpId && $0.date == r.date } }
                            Work.afterEdit(r.wpId)
                            dismiss()
                        }
                    }
                }
            }
            .navigationTitle("\(wp.name) · \(r.date)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("취소") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("저장") { save(day) }.fontWeight(.bold) }
            }
        }
    }

    /// 고른 시각을 기록 날짜에 붙인다. 퇴근·떠남이 출근보다 이르면 다음 날로 본다
    private func save(_ day: Date) {
        func pin(_ d: Date?) -> Date? {
            guard let d else { return nil }
            return timeDate(hmString(d), on: day)
        }
        var n = r
        n.arrive = pin(r.arrive)
        n.start = pin(r.start)
        n.end = pin(r.end)
        n.leave = pin(r.leave)
        if let s = n.start {
            if let e = n.end, e < s { n.end = addDays(e, 1) }
            if let l = n.leave, l < s { n.leave = addDays(l, 1) }
        }
        n.autoExit = false
        store.update { $0.put(n) }
        Work.afterEdit(n.wpId)
        dismiss()
    }
}

struct TimeRow: View {
    let label: String
    @Binding var value: Date?
    let def: Date

    var body: some View {
        HStack {
            Text(label).foregroundColor(Palette.sub).frame(width: 44, alignment: .leading)
            if let v = value {
                DatePicker("", selection: Binding(get: { v }, set: { value = $0 }), displayedComponents: .hourAndMinute)
                    .labelsHidden()
                    .environment(\.locale, Locale(identifier: "en_GB"))
                Spacer()
                Button { value = nil } label: {
                    Image(systemName: "xmark.circle.fill").foregroundColor(Palette.sub)
                }
                .buttonStyle(.plain)
            } else {
                Button("시간 입력") { value = def }.foregroundColor(Palette.purple)
                Spacer()
            }
        }
    }
}
