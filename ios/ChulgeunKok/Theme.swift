import SwiftUI

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

enum Palette {
    static let purple = Color(hex: 0x7B3FE4)
    static let purpleDeep = Color(hex: 0x3A1580)
    static let lavender = Color(hex: 0xEFE7FC)
    static let ink = Color(hex: 0x16161A)
    static let sub = Color(hex: 0x8A8A95)
    static let field = Color(hex: 0xF3F3F6)
    static let line = Color(hex: 0xEDEDF1)
    static let coral = Color(hex: 0xEE5E45)
    static let heroA = Color(hex: 0xC9AEF8)
    static let heroB = Color(hex: 0xA57AF2)
    static var hero: LinearGradient {
        LinearGradient(colors: [heroA, heroB], startPoint: .topLeading, endPoint: .bottomTrailing)
    }
}

/// 직장 대표 컬러: 그라데이션 두 색
struct Swatch {
    let name: String
    let a: Color
    let b: Color
    var gradient: LinearGradient { LinearGradient(colors: [a, b], startPoint: .leading, endPoint: .trailing) }
}

let swatches: [Swatch] = [
    Swatch(name: "핑크", a: Color(hex: 0xF59BBE), b: Color(hex: 0xE5679A)),
    Swatch(name: "오렌지", a: Color(hex: 0xFFB547), b: Color(hex: 0xFF7A2F)),
    Swatch(name: "민트", a: Color(hex: 0x43D1C3), b: Color(hex: 0x1FA89C)),
    Swatch(name: "블루", a: Color(hex: 0x95AFEE), b: Color(hex: 0x5E7FD6)),
    Swatch(name: "코랄", a: Color(hex: 0xFF967A), b: Color(hex: 0xEE5E45)),
    Swatch(name: "퍼플", a: Color(hex: 0xB896F6), b: Color(hex: 0x7B3FE4)),
    Swatch(name: "옐로", a: Color(hex: 0xFFD66B), b: Color(hex: 0xF2A516)),
    Swatch(name: "그린", a: Color(hex: 0x8EDB8E), b: Color(hex: 0x3DAA5C)),
]

func swatch(_ i: Int) -> Swatch { swatches[((i % swatches.count) + swatches.count) % swatches.count] }

/// 얼굴이 있는 동글동글 캐릭터. shape 0~5: 원, 둥근 네모, 오각형, 구름, 꽃, 다이아
struct Buddy: View {
    var color: Color
    var shape: Int
    var size: CGFloat = 56
    var happy = true

    var body: some View {
        Canvas { ctx, sz in
            let w = min(sz.width, sz.height)
            let cx = sz.width / 2
            let cy = sz.height / 2
            func circle(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> Path {
                Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
            }
            let fill = GraphicsContext.Shading.color(color)
            switch ((shape % 6) + 6) % 6 {
            case 0:
                ctx.fill(circle(cx, cy, w * 0.46), with: fill)
            case 1:
                ctx.fill(Path(roundedRect: CGRect(x: cx - w * 0.44, y: cy - w * 0.44, width: w * 0.88, height: w * 0.88),
                              cornerRadius: w * 0.24), with: fill)
            case 2:
                var p = Path()
                for i in 0..<5 {
                    let a = (-90.0 + 72.0 * Double(i)) * .pi / 180
                    let pt = CGPoint(x: cx + w * 0.40 * CGFloat(cos(a)), y: cy + w * 0.04 + w * 0.40 * CGFloat(sin(a)))
                    if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
                }
                p.closeSubpath()
                ctx.fill(p, with: fill)
                ctx.stroke(p, with: fill, style: StrokeStyle(lineWidth: w * 0.12, lineJoin: .round))
            case 3:
                ctx.fill(circle(cx - w * 0.22, cy + w * 0.04, w * 0.22), with: fill)
                ctx.fill(circle(cx, cy - w * 0.08, w * 0.28), with: fill)
                ctx.fill(circle(cx + w * 0.24, cy + w * 0.06, w * 0.2), with: fill)
                ctx.fill(Path(roundedRect: CGRect(x: cx - w * 0.45, y: cy, width: w * 0.9, height: w * 0.3),
                              cornerRadius: w * 0.15), with: fill)
            case 4:
                let o = w * 0.19
                for (dx, dy) in [(-o, -o), (o, -o), (-o, o), (o, o)] {
                    ctx.fill(circle(cx + dx, cy + dy, w * 0.23), with: fill)
                }
                ctx.fill(circle(cx, cy, w * 0.3), with: fill)
            default:
                var c2 = ctx
                c2.translateBy(x: cx, y: cy)
                c2.rotate(by: .degrees(45))
                c2.fill(Path(roundedRect: CGRect(x: -w * 0.33, y: -w * 0.33, width: w * 0.66, height: w * 0.66),
                             cornerRadius: w * 0.16), with: fill)
            }

            let ink = GraphicsContext.Shading.color(Color(hex: 0x1B1B22))
            let ey = cy + w * 0.02
            let ex = w * 0.13
            let er = w * 0.07
            for s in [-1.0, 1.0] {
                let x = cx + CGFloat(s) * ex
                if happy {
                    ctx.fill(circle(x, ey, er), with: ink)
                    ctx.fill(circle(x + er * 0.3, ey - er * 0.35, er * 0.38), with: .color(.white))
                } else {
                    var lid = Path()
                    lid.move(to: CGPoint(x: x - er, y: ey - er * 0.2))
                    lid.addQuadCurve(to: CGPoint(x: x + er, y: ey - er * 0.2), control: CGPoint(x: x, y: ey + er))
                    ctx.stroke(lid, with: ink, style: StrokeStyle(lineWidth: w * 0.03, lineCap: .round))
                }
            }
            var mouth = Path()
            mouth.move(to: CGPoint(x: cx - w * 0.055, y: ey + er * 0.8))
            mouth.addQuadCurve(to: CGPoint(x: cx + w * 0.055, y: ey + er * 0.8),
                               control: CGPoint(x: cx, y: ey + er * 0.8 + w * 0.08))
            ctx.stroke(mouth, with: ink, style: StrokeStyle(lineWidth: w * 0.028, lineCap: .round))
        }
        .frame(width: size, height: size)
    }
}
