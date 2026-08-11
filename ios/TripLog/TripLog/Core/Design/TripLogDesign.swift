import SwiftUI

enum TripLogDesign {
    static let horizontalPadding: CGFloat = 16
    static let cardRadius: CGFloat = 16
    static let controlRadius: CGFloat = 12
}

enum TripLogPalette {
    static let blue = Color(red: 59 / 255, green: 130 / 255, blue: 246 / 255)
    static let background = dynamic(light: 0xF8F9FA, dark: 0x0F1014)
    static let surface = dynamic(light: 0xFFFFFF, dark: 0x17181D)
    static let surfaceMuted = dynamic(light: 0xF9FAFB, dark: 0x1F2027)
    static let surfaceSoft = dynamic(light: 0xF3F4F6, dark: 0x292B33)
    static let border = dynamic(light: 0xE5E7EB, dark: 0x343944)
    static let textMuted = dynamic(light: 0x6B7280, dark: 0xB5BAC6)
    static let textSoft = dynamic(light: 0x9CA3AF, dark: 0x7F8795)

    private static func dynamic(light: UInt, dark: UInt) -> Color {
        Color(UIColor { traits in
            UIColor(rgb: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }
}

private extension UIColor {
    convenience init(rgb: UInt) {
        self.init(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }
}

struct TripLogCardModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(16)
            .background(TripLogPalette.surfaceMuted)
            .clipShape(RoundedRectangle(cornerRadius: TripLogDesign.cardRadius, style: .continuous))
    }
}

private struct TripLogFocusRingModifier: ViewModifier {
    let isFocused: Bool
    let cornerRadius: CGFloat

    func body(content: Content) -> some View {
        content
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(
                        isFocused ? TripLogPalette.blue.opacity(0.92) : TripLogPalette.border,
                        lineWidth: isFocused ? 1.5 : 1
                    )
            }
            .shadow(
                color: isFocused ? TripLogPalette.blue.opacity(0.18) : .clear,
                radius: 8
            )
            .animation(.easeInOut(duration: 0.2), value: isFocused)
    }
}

private struct TripLogInputModifier: ViewModifier {
    @FocusState private var internalFocus: Bool
    let externalFocus: Bool?
    let height: CGFloat?
    let cornerRadius: CGFloat
    let horizontalPadding: CGFloat

    @ViewBuilder
    func body(content: Content) -> some View {
        if let externalFocus {
            styled(content, isFocused: externalFocus)
        } else {
            styled(content.focused($internalFocus), isFocused: internalFocus)
        }
    }

    private func styled<Field: View>(_ field: Field, isFocused: Bool) -> some View {
        field
            .textFieldStyle(.plain)
            .foregroundStyle(.primary)
            .padding(.horizontal, horizontalPadding)
            .frame(height: height)
            .glassEffect(
                .regular
                    .tint(TripLogPalette.surfaceSoft.opacity(0.45))
                    .interactive(),
                in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            )
            .modifier(TripLogFocusRingModifier(isFocused: isFocused, cornerRadius: cornerRadius))
    }
}

struct TripLogPrimaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled
    let color: Color

    init(color: Color = .accentColor) {
        self.color = color
    }

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .foregroundStyle(isEnabled ? Color.white : TripLogPalette.textMuted)
            .frame(maxWidth: .infinity)
            .frame(height: 54)
            .glassEffect(
                .regular
                    .tint(isEnabled ? color : TripLogPalette.surfaceSoft)
                    .interactive(),
                in: RoundedRectangle(cornerRadius: 16, style: .continuous)
            )
            .opacity(isEnabled ? 1 : 0.62)
    }
}

private struct TripLogRefreshableModifier: ViewModifier {
    let action: () async -> Void
    @State private var pullDistance: CGFloat = 0
    @GestureState private var isPullGestureActive = false

    private var showsPullIndicator: Bool {
        isPullGestureActive && pullDistance > 2
    }

    func body(content: Content) -> some View {
        content
            .refreshable { await action() }
            .onScrollGeometryChange(for: CGFloat.self) { geometry in
                max(0, -(geometry.contentOffset.y + geometry.contentInsets.top))
            } action: { _, newPullDistance in
                pullDistance = newPullDistance
            }
            .simultaneousGesture(
                DragGesture(minimumDistance: 0)
                    .updating($isPullGestureActive) { _, isActive, _ in
                        isActive = true
                    }
            )
            .overlay(alignment: .top) {
                if showsPullIndicator {
                    TripLogLoadingIndicator(size: 24, lineWidth: 2)
                        .padding(8)
                        .glassEffect(.regular, in: Circle())
                        .padding(.top, 6)
                        .transition(.scale(scale: 0.8).combined(with: .opacity))
                        .allowsHitTesting(false)
                        .zIndex(100)
                }
            }
            .animation(.easeOut(duration: 0.14), value: showsPullIndicator)
    }
}

extension View {
    func tripLogRefreshable(action: @escaping () async -> Void) -> some View {
        modifier(TripLogRefreshableModifier(action: action))
    }

    func tripLogCard() -> some View {
        modifier(TripLogCardModifier())
    }

    func tripLogScreenBackground() -> some View {
        background(TripLogPalette.background.ignoresSafeArea())
    }

    func tripLogInputStyle(
        isFocused: Bool? = nil,
        height: CGFloat? = 48,
        cornerRadius: CGFloat = TripLogDesign.controlRadius,
        horizontalPadding: CGFloat = 14
    ) -> some View {
        modifier(
            TripLogInputModifier(
                externalFocus: isFocused,
                height: height,
                cornerRadius: cornerRadius,
                horizontalPadding: horizontalPadding
            )
        )
    }

    func tripLogAuthInputStyle(isFocused: Bool? = nil) -> some View {
        tripLogInputStyle(isFocused: isFocused)
    }

    func tripLogFocusRing(
        isFocused: Bool,
        cornerRadius: CGFloat = TripLogDesign.controlRadius
    ) -> some View {
        modifier(TripLogFocusRingModifier(isFocused: isFocused, cornerRadius: cornerRadius))
    }

}

struct TripLogBackButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "chevron.left")
                .font(.system(size: 17, weight: .semibold))
                .frame(width: 18, height: 18)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
        .controlSize(.regular)
        .tint(TripLogPalette.blue)
        .accessibilityLabel("뒤로 가기")
    }
}

struct TripLogFormHeader: View {
    let title: String
    let onBack: (() -> Void)?

    init(_ title: String, onBack: (() -> Void)? = nil) {
        self.title = title
        self.onBack = onBack
    }

    var body: some View {
        HStack(spacing: 12) {
            if let onBack {
                TripLogBackButton(action: onBack)
            }

            Text(title)
                .font(.headline.bold())

            Spacer()
        }
        .padding(.horizontal, TripLogDesign.horizontalPadding)
        .padding(.top, 14)
        .padding(.bottom, 18)
        .background(TripLogPalette.background.ignoresSafeArea(edges: .top))
        .overlay(alignment: .bottom) {
            Divider()
        }
    }
}

struct TripLogLoadingIndicator: View {
    let title: String?
    let size: CGFloat
    let lineWidth: CGFloat
    let color: Color

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(
        _ title: String? = nil,
        size: CGFloat = 22,
        lineWidth: CGFloat = 2,
        color: Color = TripLogPalette.blue
    ) {
        self.title = title
        self.size = size
        self.lineWidth = lineWidth
        self.color = color
    }

    var body: some View {
        VStack(spacing: title == nil ? 0 : 10) {
            TimelineView(.animation(minimumInterval: 1 / 60, paused: reduceMotion)) { context in
                let rotation = reduceMotion
                    ? 45.0
                    : context.date.timeIntervalSinceReferenceDate
                        .truncatingRemainder(dividingBy: 0.82) / 0.82 * 360

                ZStack {
                    Circle()
                        .stroke(color.opacity(0.16), lineWidth: lineWidth)

                    Circle()
                        .trim(from: 0.08, to: 0.68)
                        .stroke(
                            color,
                            style: StrokeStyle(
                                lineWidth: lineWidth,
                                lineCap: .round,
                                lineJoin: .round
                            )
                        )
                        .rotationEffect(.degrees(rotation))
                }
            }
            .frame(width: size, height: size)

            if let title {
                Text(title)
                    .font(.subheadline)
                    .foregroundStyle(TripLogPalette.textMuted)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(title ?? "불러오는 중")
    }
}

struct TripLogEmptyView: View {
    let icon: String
    let title: String
    let message: String

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 38))
                .foregroundStyle(.blue)
            Text(title)
                .font(.headline)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 44)
        .tripLogCard()
    }
}
