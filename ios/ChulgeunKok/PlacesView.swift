import SwiftUI

struct PlacesView: View {
    @EnvironmentObject var store: Store
    @State private var editing: Workplace?
    @State private var reorder = false
    @State private var deleting: Workplace?

    var body: some View {
        NavigationStack {
            List {
                Text(reorder ? "오른쪽 손잡이를 끌어 순서를 바꾸고 '완료'를 눌러 주세요"
                             : "꾹 누르면 복사 · 순서 변경 · 삭제를 할 수 있어요")
                    .font(.system(size: 14))
                    .foregroundColor(Palette.sub)
                    .listRowSeparator(.hidden)
                    .listRowInsets(EdgeInsets(top: 0, leading: 20, bottom: 8, trailing: 20))
                if store.data.workplaces.isEmpty {
                    EmptyCard(text: "등록된 직장이 없어요", button: "직장 추가하기") { add() }
                        .listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets(top: 6, leading: 20, bottom: 6, trailing: 20))
                }
                ForEach(store.data.workplaces) { wp in
                    PlaceRow(wp: wp, reorder: reorder)
                        .contentShape(Rectangle())
                        .onTapGesture { if !reorder { editing = wp } }
                        .contextMenu {
                            Button {
                                if let c = Work.copyWorkplace(wp.id) { editing = c }
                            } label: { Label("복사하기", systemImage: "plus.square.on.square") }
                            Button { reorder = true } label: { Label("순서 변경", systemImage: "arrow.up.arrow.down") }
                            Button(role: .destructive) { deleting = wp } label: { Label("삭제하기", systemImage: "trash") }
                        }
                        .listRowSeparator(.hidden)
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets(top: 6, leading: 20, bottom: 6, trailing: 20))
                }
                .onMove { from, to in
                    store.update { $0.workplaces.move(fromOffsets: from, toOffset: to) }
                }
            }
            .listStyle(.plain)
            .environment(\.editMode, .constant(reorder ? .active : .inactive))
            .navigationTitle("직장")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    if reorder {
                        Button("완료") { reorder = false }.fontWeight(.bold)
                    } else {
                        Button { add() } label: {
                            Image(systemName: "plus.circle.fill").font(.system(size: 26)).foregroundColor(Palette.purple)
                        }
                    }
                }
            }
            .sheet(item: $editing) { PlaceEditor(initial: $0) }
            .alert(
                "\(deleting?.name ?? "") 삭제",
                isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } })
            ) {
                Button("삭제", role: .destructive) {
                    if let d = deleting { Work.deleteWorkplace(d.id) }
                    deleting = nil
                }
                Button("취소", role: .cancel) { deleting = nil }
            } message: {
                Text("이 직장의 출퇴근 기록도 모두 지워져요. 삭제할까요?")
            }
        }
    }

    private func add() {
        editing = Workplace(color: store.data.workplaces.count % swatches.count)
    }
}

struct PlaceRow: View {
    let wp: Workplace
    let reorder: Bool

    var body: some View {
        let sw = swatch(wp.color)
        HStack(spacing: 14) {
            Buddy(color: sw.b, shape: wp.color, size: 58)
            VStack(alignment: .leading, spacing: 2) {
                Text(wp.name).font(.system(size: 17, weight: .bold)).foregroundColor(Palette.ink)
                Text("시급 \(won(Double(wp.wage))) · \(wp.start)~\(wp.end) (\(fmtDur(wp.paidMin)))")
                    .font(.system(size: 13)).foregroundColor(Palette.sub)
                Text(wp.daysLabel).font(.system(size: 13)).foregroundColor(Palette.sub)
                Text(wp.hasLocation ? "반경 \(wp.radius)m 자동 감지 중" : "위치 미설정 · 자동 감지 꺼짐")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(wp.hasLocation ? sw.b : Palette.coral)
            }
            Spacer()
            if !reorder { Image(systemName: "pencil").foregroundColor(Palette.sub) }
        }
        .padding(16)
        .background(sw.a.opacity(0.22))
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}

struct PlaceEditor: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var location: LocationService
    @Environment(\.dismiss) private var dismiss

    @State private var w: Workplace
    @State private var wageText: String
    @State private var newDate = Date()
    @State private var showMap = false
    @State private var showCoords = false
    @State private var coordText = ""
    @State private var locating = false
    @State private var message: String?
    @State private var calc: CalcInput?
    @State private var confirmDelete = false

    init(initial: Workplace) {
        _w = State(initialValue: initial)
        _wageText = State(initialValue: String(initial.wage))
    }

    private var isNew: Bool { store.data.wp(w.id) == nil }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    let sw = swatch(w.color)
                    HStack {
                        Spacer()
                        ZStack {
                            Circle().fill(sw.a.opacity(0.3)).frame(width: 110, height: 110)
                            Buddy(color: sw.b, shape: w.color, size: 78)
                        }
                        Spacer()
                    }
                    .padding(.top, 8)

                    FieldLabel(text: "장소 이름")
                    TextField("예: 카페 OO점, OO회사", text: $w.name).fieldStyle()

                    FieldLabel(text: "시급")
                    HStack(spacing: 8) {
                        TextField("시급 (원)", text: $wageText)
                            .keyboardType(.numberPad)
                            .fieldStyle()
                        SoftButton(title: "월급 계산기") { calc = current().calcInput }
                    }
                    Text("2026년 최저시급 10,320원").font(.system(size: 12)).foregroundColor(Palette.sub).padding(.top, 6)

                    FieldLabel(text: "근무 요일")
                    HStack(spacing: 6) {
                        ForEach(1...7, id: \.self) { d in
                            let on = w.days.contains(d)
                            Button {
                                if on { w.days.remove(d) } else { w.days.insert(d) }
                            } label: {
                                Text(weekdayNames[d - 1])
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundColor(on ? .white : (d == 7 ? Palette.coral : Palette.ink))
                                    .frame(maxWidth: .infinity)
                                    .aspectRatio(1, contentMode: .fit)
                                    .background(Circle().fill(on ? Palette.purple : Palette.field))
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    FieldLabel(text: "특정 날짜 (요일과 별개로 근무하는 날)")
                    HStack {
                        DatePicker("", selection: $newDate, displayedComponents: .date)
                            .labelsHidden()
                            .environment(\.locale, Locale(identifier: "ko_KR"))
                        SoftButton(title: "+ 날짜 추가") { w.dates.insert(dayKey(newDate)) }
                        Spacer()
                    }
                    if !w.dates.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(w.dates.sorted(), id: \.self) { ds in
                                    Button { w.dates.remove(ds) } label: {
                                        HStack(spacing: 4) {
                                            Text(dateChip(ds))
                                            Image(systemName: "xmark").font(.system(size: 11, weight: .bold))
                                        }
                                        .font(.system(size: 13))
                                        .foregroundColor(Palette.purple)
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 8)
                                        .background(Palette.lavender)
                                        .clipShape(Capsule())
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                        .padding(.top, 8)
                    }

                    HStack(spacing: 10) {
                        timeBox("출근 시간", get: { w.start }, set: { w.start = $0 })
                        timeBox("퇴근 시간", get: { w.end }, set: { w.end = $0 })
                    }

                    FieldLabel(text: "휴게 시간")
                    HStack(spacing: 8) {
                        ForEach([(0, "없음"), (30, "30분"), (60, "1시간"), (90, "1시간 30분")], id: \.0) { item in
                            Pill(title: item.1, selected: w.breakMin == item.0) { w.breakMin = item.0 }
                        }
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(w.start) ~ \(w.end) = \(fmtDur(w.shiftMin))")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(Palette.purpleDeep)
                        if w.breakMin > 0 {
                            Text("휴게 \(fmtDur(w.breakMin)) 빼면 실근무 \(fmtDur(w.paidMin))")
                                .font(.system(size: 13)).foregroundColor(Palette.purple)
                        }
                        if hm(w.end) <= hm(w.start) {
                            Text("퇴근이 다음 날인 야간 근무로 계산해요").font(.system(size: 12)).foregroundColor(Palette.sub)
                        }
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Palette.lavender)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .padding(.top, 14)

                    locationSection

                    FieldLabel(text: "감지 반경 \(w.radius)m")
                    Slider(
                        value: Binding(get: { Double(w.radius) }, set: { w.radius = Int(($0 / 10).rounded()) * 10 }),
                        in: 50...500
                    )
                    .tint(Palette.purple)
                    Text("GPS 오차 때문에 100~200m를 권장해요. 너무 작으면 일하는 중에 떠난 것으로 잘못 감지될 수 있어요.")
                        .font(.system(size: 12)).foregroundColor(Palette.sub)

                    FieldLabel(text: "대표 컬러")
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(0..<swatches.count, id: \.self) { i in
                                Button { w.color = i } label: {
                                    ZStack {
                                        Circle().fill(swatches[i].a.opacity(0.3)).frame(width: 52, height: 52)
                                        Buddy(color: swatches[i].b, shape: i, size: 36)
                                    }
                                    .overlay(Circle().stroke(w.color == i ? Palette.ink : .clear, lineWidth: 3))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(3)
                    }

                    PrimaryButton(title: "저장하기") { save() }.padding(.top, 28)
                    if !isNew {
                        PrimaryButton(title: "이 직장 삭제", color: .white, textColor: Palette.coral) { confirmDelete = true }
                            .padding(.top, 8)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 32)
            }
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(isNew ? "직장 추가" : "직장 수정")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } }
            }
            .sheet(item: Binding(get: { calc.map { CalcSheetItem(input: $0) } }, set: { calc = $0?.input })) {
                CalcSheet(input: $0.input, rates: store.data.rates)
            }
            .sheet(isPresented: $showMap) {
                MapPickerView(lat: w.lat, lng: w.lng, radius: w.radius) { lat, lng, place in
                    w.lat = lat
                    w.lng = lng
                    if w.name.trimmingCharacters(in: .whitespaces).isEmpty, let place { w.name = place }
                }
            }
            .alert(message ?? "", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
                Button("확인", role: .cancel) {}
            }
            .alert("\(w.name) 삭제", isPresented: $confirmDelete) {
                Button("삭제", role: .destructive) {
                    Work.deleteWorkplace(w.id)
                    dismiss()
                }
                Button("취소", role: .cancel) {}
            } message: {
                Text("이 직장의 출퇴근 기록도 모두 지워져요. 삭제할까요?")
            }
        }
    }

    private var locationSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            FieldLabel(text: "직장 위치")
            if let lat = w.lat, let lng = w.lng {
                Text(String(format: "위치가 정해졌어요 (위도 %.5f, 경도 %.5f)", lat, lng))
                    .font(.system(size: 14)).foregroundColor(Palette.ink)
            } else {
                Text("아직 위치가 설정되지 않았어요").font(.system(size: 14)).foregroundColor(Palette.coral)
            }
            PrimaryButton(title: w.hasLocation ? "지도에서 위치 바꾸기" : "지도에서 위치 고르기") { showMap = true }
            SoftButton(title: locating ? "위치 찾는 중..." : "지금 있는 곳을 직장으로") { useCurrentLocation() }
            Text("지도에서 장소 이름을 검색하거나, 지도를 움직여 핀을 직장에 맞추면 돼요")
                .font(.system(size: 12)).foregroundColor(Palette.sub)
            Button(showCoords ? "좌표 직접 입력 닫기" : "좌표 직접 입력") { showCoords.toggle() }
                .font(.system(size: 12)).foregroundColor(Palette.purple)
            if showCoords {
                HStack(spacing: 8) {
                    TextField("37.5665, 126.9780", text: $coordText).fieldStyle()
                    SoftButton(title: "적용") {
                        let p = coordText.split(whereSeparator: { $0 == "," || $0 == " " }).compactMap { Double($0) }
                        if p.count >= 2 {
                            w.lat = p[0]
                            w.lng = p[1]
                        } else {
                            message = "예: 37.5665, 126.9780 형식으로 입력해 주세요"
                        }
                    }
                }
            }
        }
    }

    private func timeBox(_ label: String, get: @escaping () -> String, set: @escaping (String) -> Void) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            FieldLabel(text: label)
            DatePicker(
                "",
                selection: Binding(get: { timeDate(get()) }, set: { set(hmString($0)) }),
                displayedComponents: .hourAndMinute
            )
            .labelsHidden()
            .environment(\.locale, Locale(identifier: "en_GB"))
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(Palette.field)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .frame(maxWidth: .infinity)
    }

    private func dateChip(_ ds: String) -> String {
        guard let d = dayFromKey(ds) else { return ds }
        let c = Calendar.current.dateComponents([.month, .day], from: d)
        return "\(c.month ?? 0)/\(c.day ?? 0)(\(weekdayNames[isoWeekday(d) - 1]))"
    }

    private func current() -> Workplace {
        var c = w
        c.name = w.name.trimmingCharacters(in: .whitespaces)
        c.wage = Int(wageText.filter(\.isNumber)) ?? 0
        return c
    }

    private func useCurrentLocation() {
        if location.status == .notDetermined {
            location.requestWhenInUse()
            return
        }
        if location.status == .denied || location.status == .restricted {
            message = "설정에서 출근콕의 위치 접근을 허용해 주세요"
            return
        }
        locating = true
        location.currentLocation { loc in
            locating = false
            if let loc {
                w.lat = loc.coordinate.latitude
                w.lng = loc.coordinate.longitude
            } else {
                message = "위치를 가져오지 못했어요. 잠시 뒤 다시 시도해 주세요"
            }
        }
    }

    private func save() {
        let nw = current()
        if nw.name.isEmpty { message = "장소 이름을 입력해 주세요"; return }
        if nw.days.isEmpty && nw.dates.isEmpty { message = "근무 요일이나 날짜를 하나 이상 골라 주세요"; return }
        Work.saveWorkplace(nw)
        dismiss()
    }
}

struct CalcSheetItem: Identifiable {
    let input: CalcInput
    var id: String { "\(input.wage)-\(input.dailyMin)-\(input.daysPerWeek)" }
}
