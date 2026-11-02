import SwiftUI

struct PayView: View {
    @EnvironmentObject var store: Store
    @State private var selId: String?

    var body: some View {
        let wp = selId.flatMap { store.data.wp($0) } ?? store.data.workplaces.first
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    if !store.data.workplaces.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(store.data.workplaces) { w in
                                    Pill(title: w.name, selected: w.id == wp?.id) { selId = w.id }.fixedSize()
                                }
                            }
                            .padding(.horizontal, 20)
                        }
                    }
                    CalcPanel(input: wp?.calcInput ?? CalcInput(wage: 10320, dailyMin: 480, daysPerWeek: 5), rates: store.data.rates)
                        .id(wp?.id ?? "none")
                }
                .padding(.bottom, 30)
            }
            .navigationTitle("급여 계산기")
        }
    }
}

/// 직장 수정 화면의 "월급 계산기" 버튼으로 여는 계산기
struct CalcSheet: View {
    let input: CalcInput
    let rates: Rates
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView { CalcPanel(input: input, rates: rates).padding(.bottom, 30) }
                .navigationTitle("월급 계산기")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
        }
    }
}

struct CalcPanel: View {
    let rates: Rates
    @State private var wage: String
    @State private var hh: String
    @State private var mm: String
    @State private var dpw: String
    @State private var period = PayPeriod.month
    @State private var holiday = true
    @State private var insurance = false

    init(input: CalcInput, rates: Rates) {
        self.rates = rates
        _wage = State(initialValue: String(input.wage))
        _hh = State(initialValue: String(input.dailyMin / 60))
        _mm = State(initialValue: String(input.dailyMin % 60))
        _dpw = State(initialValue: fmtNum(input.daysPerWeek))
    }

    var body: some View {
        let dailyMin = (Int(hh) ?? 0) * 60 + (Int(mm) ?? 0)
        let res = calcPay(
            wage: Double(Int(wage) ?? 0), dailyMin: Double(dailyMin), daysPerWeek: Double(dpw) ?? 0,
            period: period, holidayOn: holiday, insuranceOn: insurance, rates: rates
        )
        VStack(alignment: .leading, spacing: 0) {
            FieldLabel(text: "시급")
            TextField("시급 (원)", text: $wage).keyboardType(.numberPad).fieldStyle()

            FieldLabel(text: "하루 근무시간 (휴게시간 제외)")
            HStack(spacing: 8) {
                TextField("8", text: $hh).keyboardType(.numberPad).fieldStyle()
                Text("시간")
                TextField("0", text: $mm).keyboardType(.numberPad).fieldStyle()
                Text("분")
            }

            FieldLabel(text: "일주일에 며칠 일하나요?")
            TextField("5", text: $dpw).keyboardType(.decimalPad).fieldStyle()
            Text("주 \(fmtNum(res.weeklyHours))시간 근무" + (res.holidayHours > 0 ? " · 주휴 \(fmtNum(res.holidayHours))시간" : ""))
                .font(.system(size: 13)).foregroundColor(Palette.sub).padding(.top, 6)

            FieldLabel(text: "급여 형태")
            HStack(spacing: 6) {
                ForEach(PayPeriod.allCases) { p in
                    Pill(title: p.rawValue, selected: p == period) { period = p }
                }
            }

            Toggle(isOn: $holiday) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("주휴수당 포함").font(.system(size: 15, weight: .semibold))
                    Text("주 15시간 이상 일하면 받을 수 있어요").font(.system(size: 12)).foregroundColor(Palette.sub)
                }
            }
            .tint(Palette.purple)
            .padding(.top, 14)
            Toggle(isOn: $insurance) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("4대보험 공제").font(.system(size: 15, weight: .semibold))
                    Text("국민연금 · 건강보험 · 장기요양 · 고용보험 근로자 부담분").font(.system(size: 12)).foregroundColor(Palette.sub)
                }
            }
            .tint(Palette.purple)
            .padding(.top, 14)

            VStack(alignment: .leading, spacing: 0) {
                Text("\(period.rawValue) 예상 " + (insurance ? "실수령액" : "(세전)"))
                    .font(.system(size: 14, weight: .semibold)).foregroundColor(Palette.purpleDeep)
                Text(won(insurance ? res.net : res.gross))
                    .font(.system(size: 30, weight: .bold)).foregroundColor(Palette.purpleDeep)
                VStack(spacing: 0) {
                    ResultRow(label: "기본급", value: won(res.base))
                    if holiday {
                        ResultRow(label: period == .day ? "주휴수당 (하루 환산)" : "주휴수당", value: won(res.holiday))
                    }
                    ResultRow(label: "세전 합계", value: won(res.gross), bold: true)
                    if insurance {
                        Divider().padding(.vertical, 6)
                        ResultRow(label: "국민연금 \(fmtRate(rates.pension))%", value: "-" + won(res.pension))
                        ResultRow(label: "건강보험 \(fmtRate(rates.health))%", value: "-" + won(res.health))
                        ResultRow(label: "장기요양 (건강보험료의 \(fmtRate(rates.care))%)", value: "-" + won(res.care))
                        ResultRow(label: "고용보험 \(fmtRate(rates.employment))%", value: "-" + won(res.employment))
                        ResultRow(label: "공제 합계", value: "-" + won(res.deductions), bold: true)
                        Divider().padding(.vertical, 6)
                        ResultRow(label: "실수령액", value: won(res.net), bold: true, color: Palette.purple)
                    }
                }
                .padding(14)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                .padding(.top, 12)
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Palette.hero)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .padding(.top, 20)

            Text("* 근로소득세, 연장·야간·휴일 가산수당은 빠진 예상 금액이에요. 월급은 한 달 평균 4.345주로 계산해요. 보험료율은 설정에서 바꿀 수 있어요.")
                .font(.system(size: 12)).foregroundColor(Palette.sub).padding(.top, 10)
        }
        .padding(.horizontal, 20)
    }
}

/// 홈의 보라색 카드를 누르면 여는 월별 출근일·급여 화면 (처음엔 지난달)
struct MonthSummaryView: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) private var dismiss
    @State private var month: Date

    init(start: Date) {
        _month = State(initialValue: start)
    }

    var body: some View {
        let cal = Calendar.current
        let c = cal.dateComponents([.year, .month], from: month)
        let isCurrent = cal.isDate(month, equalTo: Date(), toGranularity: .month)
        let m = monthStats(store.data, month)
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HStack {
                        Button { month = cal.date(byAdding: .month, value: -1, to: month) ?? month } label: {
                            Image(systemName: "chevron.left").padding(10)
                        }
                        Spacer()
                        Text("\(String(c.year ?? 0))년 \(c.month ?? 0)월" + (isCurrent ? " (이번 달)" : ""))
                            .font(.system(size: 17, weight: .bold))
                        Spacer()
                        Button { month = cal.date(byAdding: .month, value: 1, to: month) ?? month } label: {
                            Image(systemName: "chevron.right").padding(10)
                        }
                        .disabled(isCurrent)
                    }
                    .foregroundColor(Palette.ink)

                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(c.month ?? 0)월 출근 \(m.days)일").font(.system(size: 15, weight: .semibold)).foregroundColor(Palette.purpleDeep)
                        Text(won(m.stats.reduce(0) { $0 + $1.pay })).font(.system(size: 30, weight: .bold)).foregroundColor(Palette.purpleDeep)
                        Text("총 근무 \(fmtDur(m.stats.reduce(0) { $0 + $1.minutes }))")
                            .font(.system(size: 13, weight: .semibold)).foregroundColor(Palette.purple)
                    }
                    .padding(20)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Palette.hero)
                    .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                    .padding(.top, 8)

                    FieldLabel(text: "직장별")
                    if m.stats.isEmpty {
                        Text("이 달에는 출근 기록이 없어요").font(.system(size: 14)).foregroundColor(Palette.sub)
                    }
                    ForEach(m.stats) { st in
                        let sw = swatch(st.wp.color)
                        HStack(spacing: 12) {
                            Buddy(color: sw.b, shape: st.wp.color, size: 40)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(st.wp.name).font(.system(size: 15, weight: .bold)).foregroundColor(Palette.ink)
                                Text("\(st.days)일 · \(fmtDur(st.minutes))").font(.system(size: 13)).foregroundColor(Palette.sub)
                            }
                            Spacer()
                            Text(won(st.pay)).font(.system(size: 15, weight: .bold)).foregroundColor(sw.b)
                        }
                        .padding(14)
                        .background(sw.a.opacity(0.18))
                        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                        .padding(.vertical, 6)
                    }
                    Text("* 기록된 출근~퇴근 시간 × 시급으로 계산한 금액이에요. 주휴수당, 4대보험·세금 공제는 빠져 있어요.")
                        .font(.system(size: 12)).foregroundColor(Palette.sub).padding(.top, 12)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 30)
            }
            .navigationTitle("월별 출근 · 급여")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
        }
    }
}
