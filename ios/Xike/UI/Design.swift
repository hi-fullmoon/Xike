import SwiftUI
import UIKit

struct XikePalette {
    var accent: Accent = .ocean
    var style: AppStyle = .breathe
    var moodIconStyle: MoodIconStyle = .classic
    var dark = false

    var primary: Color { dark ? Self.swatches[accent]!.1 : Self.swatches[accent]!.0 }
    var onPrimary: Color { dark ? Color(hex: 0x20201E) : .white }
    var accentSurface: Color { dark ? Self.swatches[accent]!.0.opacity(0.55) : Self.swatches[accent]!.1 }
    var background: Color {
        switch (style, dark) {
        case (.breathe, false): Color(hex: 0xF8F7F2)
        case (.paper, false): Color(hex: 0xF3EEE4)
        case (.focus, false): Color(hex: 0xF3F6F4)
        case (.breathe, true): Color(hex: 0x121614)
        case (.paper, true): Color(hex: 0x191714)
        case (.focus, true): Color(hex: 0x101312)
        }
    }
    var surface: Color {
        switch (style, dark) {
        case (.breathe, false): .white
        case (.paper, false): Color(hex: 0xFFFCF5)
        case (.focus, false): Color(hex: 0xFCFDFC)
        case (.breathe, true): Color(hex: 0x1B211E)
        case (.paper, true): Color(hex: 0x242019)
        case (.focus, true): Color(hex: 0x1A1E1C)
        }
    }
    var text: Color { Color(hex: dark ? 0xE8E9E4 : 0x20231F) }
    var muted: Color { Color(hex: dark ? 0xB6BDB6 : 0x63706A) }
    var outline: Color { Color(hex: dark ? 0x363C37 : 0xD6DCD4) }
    var quiet: Color { dark ? surface.opacity(0.95) : Color(hex: 0xF0F4F0) }

    var radius: CGFloat { style == .breathe ? 28 : style == .paper ? 6 : 14 }
    var buttonRadius: CGFloat { style == .breathe ? 18 : style == .paper ? 6 : 10 }
    var horizontal: CGFloat { style == .breathe ? 20 : style == .paper ? 24 : 16 }
    var vertical: CGFloat { style == .breathe ? 18 : style == .paper ? 22 : 12 }
    var gap: CGFloat { style == .breathe ? 15 : style == .paper ? 19 : 10 }
    var inset: CGFloat { style == .breathe ? 18 : 14 }
    var maxWidth: CGFloat { style == .breathe ? 640 : style == .paper ? 680 : 720 }

    private static let swatches: [Accent: (Color, Color)] = [
        .pine: (Color(hex: 0x496738), Color(hex: 0xE4ECD9)),
        .violet: (Color(hex: 0x66567E), Color(hex: 0xEAE3F0)),
        .ocean: (Color(hex: 0x355E5B), Color(hex: 0xDFECE8)),
        .terra: (Color(hex: 0x925B49), Color(hex: 0xF2E3DC)),
        .amber: (Color(hex: 0x82611E), Color(hex: 0xF4E8C8)),
        .rose: (Color(hex: 0x8B5360), Color(hex: 0xF3E0E4)),
    ]
}

extension Color {
    init(hex value: UInt32) {
        self.init(red: Double((value >> 16) & 255) / 255,
                  green: Double((value >> 8) & 255) / 255, blue: Double(value & 255) / 255)
    }
}

private struct PaletteKey: EnvironmentKey { static let defaultValue = XikePalette() }
private struct LanguageKey: EnvironmentKey { static let defaultValue = XikeLanguage.automatic }

extension EnvironmentValues {
    var palette: XikePalette {
        get { self[PaletteKey.self] }
        set { self[PaletteKey.self] = newValue }
    }
    var xikeLanguage: XikeLanguage {
        get { self[LanguageKey.self] }
        set { self[LanguageKey.self] = newValue }
    }
}

struct XikeCard<Content: View>: View {
    @Environment(\.palette) private var palette
    @ViewBuilder let content: Content

    var body: some View {
        content
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(palette.inset)
            .background(palette.surface, in: RoundedRectangle(cornerRadius: palette.radius))
            .overlay {
                if palette.style != .breathe {
                    RoundedRectangle(cornerRadius: palette.radius).strokeBorder(palette.outline, lineWidth: 1)
                }
            }
    }
}

struct PageScroll<Content: View>: View {
    @Environment(\.palette) private var palette
    @ViewBuilder let content: Content

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: palette.gap) { content }
                .padding(.horizontal, palette.horizontal)
                .padding(.vertical, palette.vertical)
                .frame(maxWidth: palette.maxWidth)
                .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
    }
}

struct XikeTitle: View {
    @Environment(\.palette) private var palette
    @Environment(\.xikeLanguage) private var language
    @ScaledMetric(relativeTo: .largeTitle) private var titleSize = 35.0
    var title: String
    var large = true

    var body: some View {
        Text(title)
            .font(font)
            .foregroundStyle(palette.text)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityAddTraits(.isHeader)
    }

    private var font: Font {
        if !large { return .headline }
        if palette.style == .focus { return .system(size: titleSize - 4, weight: .bold) }
        let size = titleSize + (palette.style == .paper ? 3 : 0)
        if !language.isEnglish {
            return .custom("NotoSerifCJKsc-Regular", fixedSize: size)
        }
        return .system(size: size, weight: .medium, design: .serif)
    }
}

struct HiddenSettingsTitle: View {
    let title: String
    var large = true
    let action: () -> Void
    @State private var firstTap: ContinuousClock.Instant?
    @State private var taps = 0

    var body: some View {
        Button {
            let now = ContinuousClock.now
            if let firstTap, firstTap.duration(to: now) <= .seconds(2) {
                taps += 1
            } else {
                firstTap = now
                taps = 1
            }
            if taps == 3 { open() }
        } label: {
            XikeTitle(title: title, large: large)
        }
        .buttonStyle(.plain)
        .simultaneousGesture(TapGesture(count: 3).onEnded { open() })
    }

    private func open() {
        firstTap = nil
        taps = 0
        action()
    }
}

struct XikeAction: View {
    @Environment(\.palette) private var palette
    var title: String
    var enabled = true
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.callout.weight(.semibold)).multilineTextAlignment(.center)
                .padding(.vertical, 13).padding(.horizontal, 12)
                .frame(maxWidth: .infinity, minHeight: 48)
                .foregroundStyle(enabled ? palette.onPrimary : palette.muted.opacity(0.7))
                .background(enabled ? palette.primary : palette.quiet,
                            in: RoundedRectangle(cornerRadius: palette.buttonRadius))
        }
        .buttonStyle(.plain).disabled(!enabled)
    }
}

struct BrandMark: View {
    @Environment(\.palette) private var palette
    var diameter: CGFloat = 32

    var body: some View {
        ZStack {
            Circle().fill(palette.primary)
            Canvas { context, size in
                let k = size.width / 108
                func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * k, y: y * k) }
                var path = Path()
                path.move(to: p(71, 37))
                path.addCurve(to: p(53.3, 31.6), control1: p(66.2, 32.7), control2: p(59.6, 30.7))
                path.addCurve(to: p(33.5, 54.2), control1: p(42.1, 33.2), control2: p(33.5, 42.8))
                path.addCurve(to: p(56.4, 77.2), control1: p(33.5, 66.9), control2: p(43.7, 77.2))
                path.addCurve(to: p(79.1, 58.8), control1: p(67.6, 77.2), control2: p(76.9, 69.3))
                path.move(to: p(50, 46)); path.addLine(to: p(50, 63))
                path.move(to: p(62, 43)); path.addLine(to: p(62, 60))
                context.stroke(path, with: .color(palette.onPrimary), style: StrokeStyle(lineWidth: 4.8 * k, lineCap: .round))
            }
        }
        .frame(width: diameter, height: diameter)
        .accessibilityHidden(true)
    }
}

struct MoodArtwork: View {
    @Environment(\.palette) private var palette
    var mood: Mood
    var size: CGFloat = 32
    var style: MoodIconStyle? = nil
    var emphasized = true

    private static let images: [String: UIImage] = Dictionary(uniqueKeysWithValues: Mood.choices.compactMap { mood in
        guard let url = Bundle.main.url(forResource: mood.emojiFile, withExtension: "png", subdirectory: "drawable-nodpi"),
              let image = UIImage(contentsOfFile: url.path) else { return nil }
        return (mood.emojiFile, image)
    })

    private var activeStyle: MoodIconStyle { style ?? palette.moodIconStyle }

    private var semanticColor: Color {
        switch mood.score {
        case 1: Color(hex: palette.dark ? 0xAEC4DD : 0x506881)
        case 2: Color(hex: palette.dark ? 0xCAB5D7 : 0x766084)
        case 3: Color(hex: palette.dark ? 0xB5C0B8 : 0x626C65)
        case 4: Color(hex: palette.dark ? 0x99CBB6 : 0x427461)
        default: Color(hex: palette.dark ? 0xE4C282 : 0x8B6A2C)
        }
    }

    var body: some View {
        Group {
            switch activeStyle {
            case .classic:
                if let image = Self.images[mood.emojiFile] { Image(uiImage: image).resizable().scaledToFit() }
                else { Image(systemName: "face.smiling").resizable().scaledToFit() }
            case .duotone:
                Canvas { context, canvasSize in
                    drawFace(context: &context, canvasSize: canvasSize, color: semanticColor,
                             fillAlpha: emphasized ? 0.22 : 0.14, strokeScale: 1.65)
                }
            case .line:
                Canvas { context, canvasSize in
                    drawFace(context: &context, canvasSize: canvasSize,
                             color: emphasized ? semanticColor : palette.muted,
                             fillAlpha: 0, strokeScale: 1.6)
                }
            case .ring:
                Canvas { context, canvasSize in
                    drawRing(context: &context, canvasSize: canvasSize, color: semanticColor)
                }
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    private func drawFace(
        context: inout GraphicsContext,
        canvasSize: CGSize,
        color: Color,
        fillAlpha: Double,
        strokeScale: CGFloat
    ) {
        let u = min(canvasSize.width, canvasSize.height) / 24
        let stroke = StrokeStyle(lineWidth: strokeScale * u, lineCap: .round, lineJoin: .round)
        let circleRect = CGRect(x: (12 - 9.15) * u, y: (12 - 9.15) * u, width: 18.3 * u, height: 18.3 * u)
        let circle = Path(ellipseIn: circleRect)
        if fillAlpha > 0 { context.fill(circle, with: .color(color.opacity(fillAlpha))) }
        context.stroke(circle, with: .color(color), style: stroke)

        func quad(_ x0: CGFloat, _ y0: CGFloat, _ cx: CGFloat, _ cy: CGFloat, _ x1: CGFloat, _ y1: CGFloat) {
            var p = Path()
            p.move(to: CGPoint(x: x0 * u, y: y0 * u))
            p.addQuadCurve(to: CGPoint(x: x1 * u, y: y1 * u), control: CGPoint(x: cx * u, y: cy * u))
            context.stroke(p, with: .color(color), style: stroke)
        }
        func dot(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat = 1.05) {
            let rect = CGRect(x: (x - r) * u, y: (y - r) * u, width: r * 2 * u, height: r * 2 * u)
            context.fill(Path(ellipseIn: rect), with: .color(color))
        }

        switch mood.score {
        case 1:
            quad(8.2, 10.6, 9.3, 11.75, 10.4, 10.9)
            quad(15.8, 10.6, 14.7, 11.75, 13.6, 10.9)
            quad(9.3, 16.2, 12, 13.9, 14.7, 16.2)
        case 2:
            dot(9.2, 10.4); dot(14.8, 10.4)
            quad(9.5, 15.7, 12, 14.3, 14.5, 15.7)
        case 3:
            dot(9.2, 10.4); dot(14.8, 10.4)
            var line = Path()
            line.move(to: CGPoint(x: 9.3 * u, y: 15 * u))
            line.addLine(to: CGPoint(x: 14.7 * u, y: 15 * u))
            context.stroke(line, with: .color(color), style: stroke)
        case 4:
            dot(9.2, 10.2); dot(14.8, 10.2)
            quad(9.2, 14.3, 12, 16.5, 14.8, 14.3)
        default:
            quad(8.1, 10.8, 9.5, 9.1, 10.9, 10.8)
            quad(13.1, 10.8, 14.5, 9.1, 15.9, 10.8)
            quad(8.7, 14.1, 12, 17.4, 15.3, 14.1)
        }
    }

    private func drawRing(context: inout GraphicsContext, canvasSize: CGSize, color: Color) {
        let u = min(canvasSize.width, canvasSize.height) / 24
        let glowAlpha = emphasized ? 0.18 : 0.11

        func circlePath(_ r: CGFloat) -> Path {
            Path(ellipseIn: CGRect(x: (12 - r) * u, y: (12 - r) * u, width: r * 2 * u, height: r * 2 * u))
        }

        switch mood.score {
        case 1:
            context.stroke(circlePath(4.6), with: .color(color), style: StrokeStyle(lineWidth: 1.65 * u, lineCap: .round))
        case 2:
            context.stroke(circlePath(8.4), with: .color(color), lineWidth: 1.55 * u)
            context.stroke(circlePath(5.4), with: .color(color.opacity(0.55)), lineWidth: 1.25 * u)
            context.fill(circlePath(2.4), with: .color(color.opacity(0.72)))
        case 3:
            context.stroke(circlePath(8.4), with: .color(color), lineWidth: 1.55 * u)
            context.stroke(circlePath(5.4), with: .color(color.opacity(0.65)), lineWidth: 1.35 * u)
            context.fill(circlePath(2.6), with: .color(color.opacity(0.82)))
        case 4:
            context.fill(circlePath(8.6), with: .color(color.opacity(glowAlpha)))
            context.stroke(circlePath(8.4), with: .color(color), lineWidth: 1.6 * u)
            context.stroke(circlePath(5.4), with: .color(color.opacity(0.75)), lineWidth: 1.4 * u)
            context.fill(circlePath(2.8), with: .color(color))
        default:
            context.fill(circlePath(10.6), with: .color(color.opacity(glowAlpha + 0.04)))
            context.stroke(circlePath(8.4), with: .color(color), lineWidth: 1.65 * u)
            context.stroke(circlePath(5.4), with: .color(color.opacity(0.78)), lineWidth: 1.45 * u)
            context.fill(circlePath(2.6), with: .color(color))
        }
    }
}

struct FeelingArtwork: View {
    @Environment(\.palette) private var palette
    var feeling: Feeling
    var size: CGFloat = 15
    var color: Color? = nil
    var style: MoodIconStyle? = nil

    private var activeStyle: MoodIconStyle { style ?? palette.moodIconStyle }
    private var ink: Color { color ?? palette.primary }

    var body: some View {
        if activeStyle != .classic {
            Canvas { context, canvasSize in
                let u = min(canvasSize.width, canvasSize.height) / 24
                let fillAlpha: Double = activeStyle == .duotone ? 0.22 : (activeStyle == .ring ? 0.16 : 0)
                let stroke = StrokeStyle(lineWidth: (activeStyle == .line ? 1.75 : 1.7) * u, lineCap: .round, lineJoin: .round)

                if activeStyle == .ring {
                    drawRingGlyph(context: &context, u: u, fillAlpha: fillAlpha)
                    return
                }
                switch feeling {
                case .happy, .excited, .hopeful, .energetic:
                    var path = Path()
                    path.move(to: CGPoint(x: 12 * u, y: 3.8 * u))
                    path.addQuadCurve(to: CGPoint(x: 20.2 * u, y: 12 * u), control: CGPoint(x: 13.2 * u, y: 10.8 * u))
                    path.addQuadCurve(to: CGPoint(x: 12 * u, y: 20.2 * u), control: CGPoint(x: 13.2 * u, y: 13.2 * u))
                    path.addQuadCurve(to: CGPoint(x: 3.8 * u, y: 12 * u), control: CGPoint(x: 10.8 * u, y: 13.2 * u))
                    path.addQuadCurve(to: CGPoint(x: 12 * u, y: 3.8 * u), control: CGPoint(x: 10.8 * u, y: 10.8 * u))
                    path.closeSubpath()
                    if fillAlpha > 0 { context.fill(path, with: .color(ink.opacity(fillAlpha))) }
                    context.stroke(path, with: .color(ink), style: stroke)
                case .peaceful, .relaxed, .safe, .content, .grateful:
                    var path = Path()
                    path.move(to: CGPoint(x: 19 * u, y: 5 * u))
                    path.addCurve(to: CGPoint(x: 6 * u, y: 17.5 * u), control1: CGPoint(x: 11 * u, y: 5 * u), control2: CGPoint(x: 6 * u, y: 9.5 * u))
                    path.addCurve(to: CGPoint(x: 19 * u, y: 5 * u), control1: CGPoint(x: 14 * u, y: 17.5 * u), control2: CGPoint(x: 19 * u, y: 13 * u))
                    path.closeSubpath()
                    if fillAlpha > 0 { context.fill(path, with: .color(ink.opacity(fillAlpha))) }
                    context.stroke(path, with: .color(ink), style: stroke)
                    var stem = Path()
                    stem.move(to: CGPoint(x: 5.5 * u, y: 18.5 * u))
                    stem.addLine(to: CGPoint(x: 13.5 * u, y: 10.5 * u))
                    context.stroke(stem, with: .color(ink), style: stroke)
                case .sad, .disappointed, .hurt, .lonely:
                    var path = Path()
                    path.move(to: CGPoint(x: 12 * u, y: 4.2 * u))
                    path.addCurve(to: CGPoint(x: 6.2 * u, y: 14.6 * u), control1: CGPoint(x: 12 * u, y: 4.2 * u), control2: CGPoint(x: 6.2 * u, y: 10.8 * u))
                    path.addCurve(to: CGPoint(x: 12 * u, y: 20 * u), control1: CGPoint(x: 6.2 * u, y: 17.8 * u), control2: CGPoint(x: 8.8 * u, y: 20 * u))
                    path.addCurve(to: CGPoint(x: 17.8 * u, y: 14.6 * u), control1: CGPoint(x: 15.2 * u, y: 20 * u), control2: CGPoint(x: 17.8 * u, y: 17.8 * u))
                    path.addCurve(to: CGPoint(x: 12 * u, y: 4.2 * u), control1: CGPoint(x: 17.8 * u, y: 10.8 * u), control2: CGPoint(x: 12 * u, y: 4.2 * u))
                    path.closeSubpath()
                    if fillAlpha > 0 { context.fill(path, with: .color(ink.opacity(fillAlpha))) }
                    context.stroke(path, with: .color(ink), style: stroke)
                case .anxious, .irritated, .angry:
                    var path = Path()
                    path.move(to: CGPoint(x: 12 * u, y: 4.5 * u))
                    path.addLine(to: CGPoint(x: 19.5 * u, y: 12 * u))
                    path.addLine(to: CGPoint(x: 12 * u, y: 19.5 * u))
                    path.addLine(to: CGPoint(x: 4.5 * u, y: 12 * u))
                    path.closeSubpath()
                    if fillAlpha > 0 { context.fill(path, with: .color(ink.opacity(fillAlpha))) }
                    context.stroke(path, with: .color(ink), style: stroke)
                    context.fill(Path(ellipseIn: CGRect(x: 10.4 * u, y: 10.4 * u, width: 3.2 * u, height: 3.2 * u)), with: .color(ink))
                case .tired, .bored:
                    var path = Path()
                    path.move(to: CGPoint(x: 18.2 * u, y: 14.8 * u))
                    path.addCurve(to: CGPoint(x: 9.6 * u, y: 18.2 * u), control1: CGPoint(x: 16.6 * u, y: 17.8 * u), control2: CGPoint(x: 13 * u, y: 19.4 * u))
                    path.addCurve(to: CGPoint(x: 5.8 * u, y: 9.6 * u), control1: CGPoint(x: 6.1 * u, y: 17 * u), control2: CGPoint(x: 4.5 * u, y: 13.1 * u))
                    path.addCurve(to: CGPoint(x: 11 * u, y: 5.3 * u), control1: CGPoint(x: 6.6 * u, y: 7.3 * u), control2: CGPoint(x: 8.6 * u, y: 5.7 * u))
                    path.addCurve(to: CGPoint(x: 12.9 * u, y: 13.8 * u), control1: CGPoint(x: 9.4 * u, y: 8.2 * u), control2: CGPoint(x: 10.2 * u, y: 11.9 * u))
                    path.addCurve(to: CGPoint(x: 18.2 * u, y: 14.8 * u), control1: CGPoint(x: 14.5 * u, y: 14.9 * u), control2: CGPoint(x: 16.4 * u, y: 15.2 * u))
                    path.closeSubpath()
                    if fillAlpha > 0 { context.fill(path, with: .color(ink.opacity(fillAlpha))) }
                    context.stroke(path, with: .color(ink), style: stroke)
                }
            }
            .frame(width: size, height: size)
            .accessibilityHidden(true)
        }
    }

    private func drawRingGlyph(context: inout GraphicsContext, u: CGFloat, fillAlpha: Double) {
        func circle(_ r: CGFloat, cy: CGFloat = 12) -> Path {
            Path(ellipseIn: CGRect(x: (12 - r) * u, y: (cy - r) * u, width: r * 2 * u, height: r * 2 * u))
        }
        switch feeling {
        case .happy, .excited, .hopeful, .energetic:
            context.fill(circle(9.2), with: .color(ink.opacity(fillAlpha)))
            context.stroke(circle(6.2), with: .color(ink), lineWidth: 1.6 * u)
            context.fill(circle(2.4), with: .color(ink))
        case .peaceful, .relaxed, .safe, .content, .grateful:
            context.fill(circle(8.4), with: .color(ink.opacity(fillAlpha)))
            context.stroke(circle(8.0), with: .color(ink), lineWidth: 1.6 * u)
            context.fill(circle(3.8), with: .color(ink.opacity(0.65)))
        case .sad, .disappointed, .hurt, .lonely:
            context.stroke(circle(5.2, cy: 14), with: .color(ink), lineWidth: 1.65 * u)
        case .anxious, .irritated, .angry:
            context.stroke(circle(8.0), with: .color(ink), lineWidth: 1.6 * u)
            context.stroke(circle(4.5), with: .color(ink.opacity(0.72)), lineWidth: 1.4 * u)
        case .tired, .bored:
            context.fill(circle(7.4), with: .color(ink.opacity(fillAlpha)))
            context.stroke(circle(7.4), with: .color(ink.opacity(0.8)), lineWidth: 1.55 * u)
            var line = Path()
            line.move(to: CGPoint(x: 8.5 * u, y: 12 * u))
            line.addLine(to: CGPoint(x: 15.5 * u, y: 12 * u))
            context.stroke(line, with: .color(ink), style: StrokeStyle(lineWidth: 1.6 * u, lineCap: .round))
        }
    }
}

struct WeatherArtwork: View {
    @Environment(\.palette) private var palette
    var weatherCode: Int
    var fallbackSymbol: String = "cloud.sun"
    var size: CGFloat = 22
    var color: Color? = nil
    var style: MoodIconStyle? = nil

    private var activeStyle: MoodIconStyle { style ?? palette.moodIconStyle }
    private var ink: Color { color ?? palette.primary }

    var body: some View {
        Group {
            if activeStyle == .classic {
                Image(systemName: fallbackSymbol)
                    .font(.system(size: size * 0.9, weight: .regular))
                    .foregroundStyle(ink)
            } else {
                Canvas { context, canvasSize in
                    drawWeather(context: &context, canvasSize: canvasSize)
                }
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    private func drawWeather(context: inout GraphicsContext, canvasSize: CGSize) {
        let u = min(canvasSize.width, canvasSize.height) / 24
        let fillAlpha: Double = activeStyle == .duotone ? 0.20 : (activeStyle == .ring ? 0.14 : 0)
        let stroke = StrokeStyle(lineWidth: (activeStyle == .line ? 1.6 : 1.65) * u, lineCap: .round, lineJoin: .round)

        func cloud(offsetY: CGFloat = 0, scale: CGFloat = 1) -> Path {
            func x(_ v: CGFloat) -> CGFloat { (12 + (v - 12) * scale) * u }
            func y(_ v: CGFloat) -> CGFloat { (12 + (v - 12) * scale + offsetY) * u }
            var p = Path()
            p.move(to: CGPoint(x: x(7.4), y: y(17.5)))
            p.addLine(to: CGPoint(x: x(16.6), y: y(17.5)))
            p.addCurve(to: CGPoint(x: x(20.4), y: y(13.4)), control1: CGPoint(x: x(19.2), y: y(17.5)), control2: CGPoint(x: x(20.6), y: y(15.6)))
            p.addCurve(to: CGPoint(x: x(16.4), y: y(10.1)), control1: CGPoint(x: x(20.2), y: y(11.3)), control2: CGPoint(x: x(18.5), y: y(10.0)))
            p.addCurve(to: CGPoint(x: x(10.8), y: y(6.4)), control1: CGPoint(x: x(15.7), y: y(7.5)), control2: CGPoint(x: x(13.4), y: y(6.0)))
            p.addCurve(to: CGPoint(x: x(6.8), y: y(11.0)), control1: CGPoint(x: x(8.4), y: y(6.8)), control2: CGPoint(x: x(6.8), y: y(8.7)))
            p.addCurve(to: CGPoint(x: x(3.9), y: y(14.7)), control1: CGPoint(x: x(4.9), y: y(11.3)), control2: CGPoint(x: x(3.7), y: y(12.8)))
            p.addCurve(to: CGPoint(x: x(7.4), y: y(17.5)), control1: CGPoint(x: x(4.1), y: y(16.4)), control2: CGPoint(x: x(5.5), y: y(17.5)))
            p.closeSubpath()
            return p
        }

        switch weatherCode {
        case 0:
            if activeStyle == .ring {
                context.fill(Path(ellipseIn: CGRect(x: 2.4 * u, y: 2.4 * u, width: 19.2 * u, height: 19.2 * u)), with: .color(ink.opacity(0.14)))
            }
            let sun = Path(ellipseIn: CGRect(x: 7.4 * u, y: 7.4 * u, width: 9.2 * u, height: 9.2 * u))
            if fillAlpha > 0 { context.fill(sun, with: .color(ink.opacity(fillAlpha))) }
            context.stroke(sun, with: .color(ink), style: stroke)
            for i in 0..<8 {
                let angle = Double(i) * .pi / 4
                let dx = CGFloat(cos(angle)), dy = CGFloat(sin(angle))
                var ray = Path()
                ray.move(to: CGPoint(x: (12 + dx * 7.2) * u, y: (12 + dy * 7.2) * u))
                ray.addLine(to: CGPoint(x: (12 + dx * 9.3) * u, y: (12 + dy * 9.3) * u))
                context.stroke(ray, with: .color(ink), style: stroke)
            }
        case 1, 2:
            let sun = Path(ellipseIn: CGRect(x: 12.6 * u, y: 5.2 * u, width: 6.4 * u, height: 6.4 * u))
            if fillAlpha > 0 { context.fill(sun, with: .color(ink.opacity(fillAlpha))) }
            context.stroke(sun, with: .color(ink), style: stroke)
            for deg in [-135.0, -90.0, -45.0, 0.0] {
                let rad = deg * .pi / 180
                let dx = CGFloat(cos(rad)), dy = CGFloat(sin(rad))
                var ray = Path()
                ray.move(to: CGPoint(x: (15.8 + dx * 5.0) * u, y: (8.4 + dy * 5.0) * u))
                ray.addLine(to: CGPoint(x: (15.8 + dx * 6.3) * u, y: (8.4 + dy * 6.3) * u))
                context.stroke(ray, with: .color(ink), style: StrokeStyle(lineWidth: stroke.lineWidth * 0.9, lineCap: .round))
            }
            let c = cloud(offsetY: 2.0, scale: 0.88)
            if fillAlpha > 0 { context.fill(c, with: .color(ink.opacity(fillAlpha))) }
            context.stroke(c, with: .color(ink), style: stroke)
        case 3:
            let c = cloud(offsetY: 0.5, scale: 0.96)
            if fillAlpha > 0 { context.fill(c, with: .color(ink.opacity(fillAlpha))) }
            context.stroke(c, with: .color(ink), style: stroke)
        case 45, 48:
            let c = cloud(offsetY: -2.2, scale: 0.82)
            if fillAlpha > 0 { context.fill(c, with: .color(ink.opacity(fillAlpha))) }
            context.stroke(c, with: .color(ink), style: stroke)
            var l1 = Path(); l1.move(to: CGPoint(x: 5.5 * u, y: 17.5 * u)); l1.addLine(to: CGPoint(x: 18.5 * u, y: 17.5 * u))
            var l2 = Path(); l2.move(to: CGPoint(x: 7.5 * u, y: 20.3 * u)); l2.addLine(to: CGPoint(x: 16.5 * u, y: 20.3 * u))
            context.stroke(l1, with: .color(ink.opacity(0.85)), style: stroke)
            context.stroke(l2, with: .color(ink.opacity(0.6)), style: stroke)
        case 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82:
            let c = cloud(offsetY: -2.4, scale: 0.84)
            if fillAlpha > 0 { context.fill(c, with: .color(ink.opacity(fillAlpha))) }
            context.stroke(c, with: .color(ink), style: stroke)
            for (idx, x) in [CGFloat(8.6), 12.2, 15.8].enumerated() {
                let top: CGFloat = idx == 1 ? 17.2 : 16.4
                var drop = Path()
                drop.move(to: CGPoint(x: x * u, y: top * u))
                drop.addLine(to: CGPoint(x: (x - 1.1) * u, y: (top + 3.2) * u))
                context.stroke(drop, with: .color(ink), style: stroke)
            }
        case 71, 73, 75, 77, 85, 86:
            let c = cloud(offsetY: -2.4, scale: 0.84)
            if fillAlpha > 0 { context.fill(c, with: .color(ink.opacity(fillAlpha))) }
            context.stroke(c, with: .color(ink), style: stroke)
            for pt in [(CGFloat(8.4), CGFloat(18.0)), (12.0, 19.6), (15.6, 18.0)] {
                let r = 1.15 * u
                context.fill(Path(ellipseIn: CGRect(x: pt.0 * u - r, y: pt.1 * u - r, width: r * 2, height: r * 2)), with: .color(ink))
            }
        case 95, 96, 99:
            let c = cloud(offsetY: -2.4, scale: 0.84)
            if fillAlpha > 0 { context.fill(c, with: .color(ink.opacity(fillAlpha))) }
            context.stroke(c, with: .color(ink), style: stroke)
            var bolt = Path()
            bolt.move(to: CGPoint(x: 12.6 * u, y: 14.5 * u))
            bolt.addLine(to: CGPoint(x: 10.0 * u, y: 18.2 * u))
            bolt.addLine(to: CGPoint(x: 12.8 * u, y: 18.2 * u))
            bolt.addLine(to: CGPoint(x: 11.0 * u, y: 21.4 * u))
            context.stroke(bolt, with: .color(ink), style: stroke)
        default:
            let ring = Path(ellipseIn: CGRect(x: 3.6 * u, y: 3.6 * u, width: 16.8 * u, height: 16.8 * u))
            if fillAlpha > 0 { context.fill(ring, with: .color(ink.opacity(fillAlpha))) }
            context.stroke(ring, with: .color(ink), style: stroke)
        }
    }
}

struct XikeNavigation: View {
    @Environment(\.palette) private var palette
    @Environment(\.xikeLanguage) private var language
    @Binding var selection: XikeTab

    var body: some View {
        HStack(spacing: 3) {
            ForEach(XikeTab.allCases) { tab in
                Button {
                    selection = tab
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: tab.icon).font(.system(size: 19, weight: .medium))
                        Text(tab.title(language)).font(.caption.weight(selection == tab ? .semibold : .medium))
                            .multilineTextAlignment(.center).lineLimit(2)
                    }
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .foregroundStyle(selection == tab ? palette.primary : palette.muted)
                    .background {
                        if selection == tab && palette.style != .paper {
                            RoundedRectangle(cornerRadius: palette.style == .breathe ? 18 : 10)
                                .fill(palette.accentSurface)
                        }
                    }
                    .overlay(alignment: .bottom) {
                        if selection == tab && palette.style == .paper {
                            Rectangle().fill(palette.primary).frame(height: 2)
                        }
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selection == tab ? .isSelected : [])
                .accessibilityIdentifier("tab-" + tab.rawValue)
            }
        }
        .frame(maxWidth: palette.maxWidth)
        .padding(.horizontal, 14)
        .padding(.vertical, 5)
        .frame(maxWidth: .infinity)
        .background(palette.quiet.ignoresSafeArea(edges: .bottom))
    }
}
