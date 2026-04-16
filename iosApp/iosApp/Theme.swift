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

    var body: some View {
        HStack(spacing: 2) {
            ForEach(options, id: \.self) { option in
                let isSelected = selected == option
                Button {
                    selected = option
                } label: {
                    Text(option)
                        .font(.spaceGrotesk(isSelected ? .bold : .regular, size: fontSize))
                        .foregroundColor(isSelected ? activeTextColor : inactiveTextColor)
                        .frame(maxWidth: .infinity, minHeight: 34)
                        .background(isSelected ? activeColor.opacity(0.15) : inactiveColor)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
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

struct AmberSlider: View {
    @Binding var value: Double
    var range: ClosedRange<Double> = 0...1
    var accentColor: Color = .ledAmber

    var body: some View {
        Slider(value: $value, in: range)
            .tint(accentColor)
    }
}

struct ChannelPills: View {
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
                }
                .padding(3)
            }
        }
        .frame(height: 40)
        .background(Color.padIdle)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.padBorder.opacity(0.5), lineWidth: 1)
        )
    }
}
