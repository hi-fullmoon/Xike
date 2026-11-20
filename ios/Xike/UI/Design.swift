import SwiftUI
import UIKit

struct XikePalette {
    var accent: Accent = .ocean
    var style: AppStyle = .breathe
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
    var mood: Mood
    var size: CGFloat = 32

    private static let images: [String: UIImage] = Dictionary(uniqueKeysWithValues: Mood.choices.compactMap { mood in
        guard let url = Bundle.main.url(forResource: mood.emojiFile, withExtension: "png", subdirectory: "drawable-nodpi"),
              let image = UIImage(contentsOfFile: url.path) else { return nil }
        return (mood.emojiFile, image)
    })

    var body: some View {
        Group {
            if let image = Self.images[mood.emojiFile] { Image(uiImage: image).resizable().scaledToFit() }
            else { Image(systemName: "face.smiling").resizable().scaledToFit() }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
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
