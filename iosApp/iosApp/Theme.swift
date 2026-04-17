import SwiftUI

// MARK: - Color Tokens (matching Android Color.kt exactly)

extension Color {
    // Backgrounds
    static let darkBg = Color(red: 10/255, green: 10/255, blue: 10/255)
    static let padIdle = Color(red: 26/255, green: 26/255, blue: 26/255)
    static let padActive = Color(red: 51/255, green: 51/255, blue: 51/255)
    static let padBorder = Color(red: 42/255, green: 42/255, blue: 42/255)

    // Text
    static let textPrimary = Color(red: 224/255, green: 224/255, blue: 224/255)
    static let textSecondary = Color(red: 102/255, green: 102/255, blue: 102/255)
    static let textOnPad = Color(red: 153/255, green: 153/255, blue: 153/255)

    // Amber (Pad accent)
    static let ledAmber = Color(red: 232/255, green: 163/255, blue: 23/255)
    static let ledAmberDim = Color(red: 74/255, green: 52/255, blue: 8/255)

    // Teal (Click accent)
    static let clickTeal = Color(red: 27/255, green: 152/255, blue: 166/255)
    static let clickTealDim = Color(red: 10/255, green: 53/255, blue: 56/255)

    // Purple (Labs accent)
    static let labsPurple = Color(red: 156/255, green: 106/255, blue: 222/255)
    static let labsPurpleDim = Color(red: 42/255, green: 31/255, blue: 61/255)
}

// MARK: - Typography (Space Grotesk)

extension Font {
    static func spaceGrotesk(_ weight: SpaceGroteskWeight, size: CGFloat) -> Font {
        .custom(weight.fontName, size: size)
    }

    enum SpaceGroteskWeight {
        case light, regular, medium, semiBold, bold

        var fontName: String {
            switch self {
            case .light:    return "SpaceGrotesk-Light"
            case .regular:  return "SpaceGrotesk-Regular"
            case .medium:   return "SpaceGrotesk-Medium"
            case .semiBold: return "SpaceGrotesk-SemiBold"
            case .bold:     return "SpaceGrotesk-Bold"
            }
        }
    }
}

// MARK: - Reusable Components

struct DarkSurface<Content: View>: View {
    let cornerRadius: CGFloat
    let borderColor: Color
    let content: () -> Content

    init(
        cornerRadius: CGFloat = 12,
        borderColor: Color = .padBorder,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.cornerRadius = cornerRadius
        self.borderColor = borderColor
        self.content = content
    }

    var body: some View {
        content()
            .background(Color.padIdle)
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .stroke(borderColor, lineWidth: 1)
            )
    }
}

struct PillSelector: View {
    let options: [String]
    @Binding var selected: String
    var activeColor: Color = .ledAmber
    var inactiveColor: Color = .padIdle
    var activeTextColor: Color = .ledAmber
    var inactiveTextColor: Color = .textSecondary
    var fontSize: CGFloat = 14
    var disabledOptions: Set<String> = []

    var body: some View {
        HStack(spacing: 2) {
            ForEach(options, id: \.self) { option in
                let isSelected = selected == option
                let isDisabled = disabledOptions.contains(option)
                Button {
                    if !isDisabled { selected = option }
                } label: {
                    Text(option)
                        .font(.spaceGrotesk(isSelected ? .bold : .regular, size: fontSize))
                        .foregroundColor(isSelected ? activeTextColor : inactiveTextColor)
                        .frame(maxWidth: .infinity, minHeight: 34)
                        .background(isSelected ? activeColor.opacity(0.15) : inactiveColor)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                        .opacity(isDisabled ? 0.3 : 1.0)
                }
            }
        }
        .padding(3)
        .frame(height: 40)
        .background(Color.padIdle)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.padBorder.opacity(0.5), lineWidth: 1)
        )
    }
}

struct SectionHeader: View {
    let title: String
    var color: Color = .textSecondary
    var size: CGFloat = 13

    var body: some View {
        Text(title)
            .font(.spaceGrotesk(.bold, size: size))
            .foregroundColor(color)
            .tracking(2)
    }
}

struct ScreenHeader: View {
    let title: String
    var color: Color = .textSecondary

    var body: some View {
        Text(title)
            .font(.spaceGrotesk(.light, size: 22))
            .foregroundColor(color)
            .tracking(6)
    }
}

struct StyledSlider: View {
    @Binding var value: Float
    var range: ClosedRange<Float> = 0...1
    var thumbColor: Color = .ledAmber
    var activeTrackColor: Color = .ledAmberDim
    var inactiveTrackColor: Color = .padBorder.opacity(0.3)
    var height: CGFloat = 28

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let frac = CGFloat((value - range.lowerBound) / (range.upperBound - range.lowerBound))
            let thumbSize: CGFloat = 20
            let trackH: CGFloat = 4
            let thumbX = frac * (w - thumbSize)

            ZStack(alignment: .leading) {
                // Inactive track
                RoundedRectangle(cornerRadius: 2)
                    .fill(inactiveTrackColor)
                    .frame(height: trackH)
                // Active track
                RoundedRectangle(cornerRadius: 2)
                    .fill(activeTrackColor)
                    .frame(width: thumbX + thumbSize / 2, height: trackH)
                // Thumb
                Circle()
                    .fill(thumbColor)
                    .frame(width: thumbSize, height: thumbSize)
                    .shadow(color: thumbColor.opacity(0.3), radius: 4)
                    .offset(x: thumbX)
            }
            .frame(height: height)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { drag in
                        let pct = Float(max(0, min(1, drag.location.x / w)))
                        value = range.lowerBound + pct * (range.upperBound - range.lowerBound)
                    }
            )
        }
        .frame(height: height)
    }
}

struct ChannelPills: View {
    @Binding var channel: String
    var activeColor: Color = .ledAmber
    var isActive: Bool = true

    var body: some View {
        HStack(spacing: 4) {
            ForEach(["L", "M", "R"], id: \.self) { val in
                let isSelected: Bool = {
                    switch channel.lowercased() {
                    case "left": return val == "L"
                    case "right": return val == "R"
                    default: return val == "M"
                    }
                }()

                Button {
                    switch val {
                    case "L": channel = "left"
                    case "R": channel = "right"
                    default: channel = "mono"
                    }
                } label: {
                    Text(val)
                        .font(.spaceGrotesk(isSelected ? .bold : .regular, size: 11))
                        .foregroundColor(
                            isSelected && isActive ? activeColor :
                            isSelected ? .textPrimary :
                            .textSecondary.opacity(0.5)
                        )
                        .frame(width: 28, height: 28)
                        .background(
                            isSelected && isActive ? activeColor.opacity(0.2) :
                            isSelected ? Color.padActive :
                            Color.clear
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                        .overlay(
                            RoundedRectangle(cornerRadius: 4)
                                .stroke(
                                    isSelected && isActive ? activeColor :
                                    Color.padBorder.opacity(0.3),
                                    lineWidth: 1
                                )
                        )
                }
                .buttonStyle(.plain)
            }
        }
    }
}

// Full-width segmented bar for Settings/Config screen (matches Android SettingsScreen)
struct ChannelSegmentedBar: View {
    @Binding var channel: String
    var activeColor: Color = .ledAmber
    var isActive: Bool = true

    var body: some View {
        HStack(spacing: 0) {
            ForEach(["L", "M", "R"], id: \.self) { val in
                let isSelected: Bool = {
                    switch channel.lowercased() {
                    case "left": return val == "L"
                    case "right": return val == "R"
                    default: return val == "M"
                    }
                }()

                Button {
                    switch val {
                    case "L": channel = "left"
                    case "R": channel = "right"
                    default: channel = "mono"
                    }
                } label: {
                    Text(val)
                        .font(.spaceGrotesk(isSelected ? .bold : .regular, size: 14))
                        .foregroundColor(
                            isSelected && isActive ? activeColor :
                            isSelected ? .textPrimary :
                            .textSecondary
                        )
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(
                            isSelected && isActive ? activeColor.opacity(0.15) :
                            isSelected ? Color.padActive :
                            Color.padIdle
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                        .padding(3)
                }
                .buttonStyle(.plain)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: 40)
        .background(Color.padIdle)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.padBorder.opacity(0.5), lineWidth: 1)
        )
    }
}

// MARK: - SwipeRevealCard

struct SwipeRevealCard<Background: View, Content: View>: View {
    var revealWidth: CGFloat = 70
    var enabled: Bool = true
    @ViewBuilder var background: () -> Background
    @ViewBuilder var content: () -> Content

    @State private var offsetX: CGFloat = 0

    var body: some View {
        ZStack(alignment: .trailing) {
            if offsetX < -1 && enabled {
                background()
            }
            content()
                .offset(x: offsetX)
                .gesture(
                    enabled ?
                    DragGesture(minimumDistance: 20)
                        .onChanged { value in
                            let w = value.translation.width
                            guard abs(w) > abs(value.translation.height) else { return }
                            withAnimation(.interactiveSpring()) {
                                offsetX = min(0, w)
                            }
                        }
                        .onEnded { value in
                            withAnimation(.easeOut(duration: 0.2)) {
                                offsetX = value.translation.width < -revealWidth / 2 ? -revealWidth : 0
                            }
                        }
                    : nil
                )
        }
    }
}
