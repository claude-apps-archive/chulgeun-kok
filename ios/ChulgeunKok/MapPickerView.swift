import MapKit
import SwiftUI

/// 지도를 움직여 가운데 핀을 직장 위에 맞추는 화면.
/// 검색 결과를 누르면 그곳으로 이동하고, 감지 반경을 원으로 보여 준다.
struct MapPickerView: View {
    let radius: Int
    var onPick: (Double, Double, String?) -> Void

    @EnvironmentObject var location: LocationService
    @Environment(\.dismiss) private var dismiss
    @State private var region: MKCoordinateRegion
    @State private var query = ""
    @State private var hits: [GeoHit] = []
    @State private var placeName: String?
    @State private var placeLabel: String?
    @State private var message: String?
    private let hasInitial: Bool

    init(lat: Double?, lng: Double?, radius: Int, onPick: @escaping (Double, Double, String?) -> Void) {
        self.radius = radius
        self.onPick = onPick
        hasInitial = lat != nil && lng != nil
        let center = CLLocationCoordinate2D(latitude: lat ?? 37.5665, longitude: lng ?? 126.9780)
        let meters: CLLocationDistance = hasInitial ? 500 : 3000
        _region = State(initialValue: MKCoordinateRegion(center: center, latitudinalMeters: meters, longitudinalMeters: meters))
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                HStack(spacing: 8) {
                    TextField("장소 이름이나 주소 (예: 웨스틴조선호텔)", text: $query)
                        .fieldStyle()
                        .submitLabel(.search)
                        .onSubmit { search() }
                    SoftButton(title: "검색") { search() }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)

                if !hits.isEmpty {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 0) {
                            ForEach(hits) { h in
                                Button {
                                    region = MKCoordinateRegion(
                                        center: CLLocationCoordinate2D(latitude: h.lat, longitude: h.lng),
                                        latitudinalMeters: 400, longitudinalMeters: 400
                                    )
                                    placeName = h.name.isEmpty ? nil : h.name
                                    placeLabel = h.label
                                    hits = []
                                } label: {
                                    VStack(alignment: .leading, spacing: 2) {
                                        if !h.name.isEmpty {
                                            Text(h.name).font(.system(size: 15, weight: .semibold)).foregroundColor(Palette.ink)
                                        }
                                        Text(h.label).font(.system(size: 12)).foregroundColor(Palette.sub).lineLimit(2)
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.vertical, 10)
                                }
                                .buttonStyle(.plain)
                                Divider()
                            }
                        }
                        .padding(.horizontal, 16)
                    }
                    .frame(maxHeight: 220)
                }

                GeometryReader { geo in
                    // 지도 화면 1pt 가 몇 m 인지로 감지 반경 원의 크기를 맞춘다
                    let metersPerPoint = region.span.latitudeDelta * 111_000 / max(geo.size.height, 1)
                    let diameter = CGFloat(Double(radius) * 2 / max(metersPerPoint, 0.0001))
                    ZStack {
                        Map(coordinateRegion: $region, showsUserLocation: true)
                        Circle()
                            .fill(Palette.purple.opacity(0.18))
                            .overlay(Circle().stroke(Palette.purple, lineWidth: 2))
                            .frame(width: min(diameter, 2000), height: min(diameter, 2000))
                            .allowsHitTesting(false)
                        Image(systemName: "mappin")
                            .font(.system(size: 40, weight: .bold))
                            .foregroundColor(Palette.purple)
                            .offset(y: -20)
                            .allowsHitTesting(false)
                        VStack {
                            Spacer()
                            HStack {
                                Spacer()
                                Button { goToMyLocation() } label: {
                                    Text("내 위치")
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(Palette.purple)
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 10)
                                        .background(Capsule().fill(Color.white).shadow(radius: 3))
                                }
                                .padding(16)
                            }
                        }
                    }
                }

                VStack(alignment: .leading, spacing: 10) {
                    Text(placeLabel ?? "지도를 움직여 보라색 핀을 직장 입구에 맞춰 주세요. 원은 감지 반경(\(radius)m)이에요.")
                        .font(.system(size: 13))
                        .foregroundColor(placeLabel == nil ? Palette.sub : Palette.ink)
                        .lineLimit(2)
                    PrimaryButton(title: "이 위치로 정하기") {
                        onPick(region.center.latitude, region.center.longitude, placeName)
                        dismiss()
                    }
                }
                .padding(16)
            }
            .navigationTitle("직장 위치 고르기")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
            .alert(message ?? "", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
                Button("확인", role: .cancel) {}
            }
            .onAppear {
                // 위치가 아직 없으면 지금 있는 곳에서 시작한다
                if !hasInitial, location.status == .authorizedAlways || location.status == .authorizedWhenInUse {
                    goToMyLocation()
                }
            }
        }
    }

    private func search() {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return }
        location.search(q, near: region) { found in
            hits = found
            if found.isEmpty { message = "검색 결과가 없어요. 지도를 직접 움직여 맞춰 주세요" }
        }
    }

    private func goToMyLocation() {
        switch location.status {
        case .notDetermined:
            location.requestWhenInUse()
        case .denied, .restricted:
            message = "설정에서 출근콕의 위치 접근을 허용해 주세요"
        default:
            location.currentLocation { loc in
                if let loc {
                    region = MKCoordinateRegion(center: loc.coordinate, latitudinalMeters: 500, longitudinalMeters: 500)
                } else {
                    message = "현재 위치를 가져오지 못했어요. 잠시 뒤 다시 시도해 주세요"
                }
            }
        }
    }
}
