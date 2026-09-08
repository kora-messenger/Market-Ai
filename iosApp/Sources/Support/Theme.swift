import SwiftUI

/// MarketScope AI design language — white/light only, per the product decision.
enum Theme {
    static let background = Color(hex: 0xFFFFFF)
    static let card = Color(hex: 0xF3F4F7)
    static let textPrimary = Color(hex: 0x111318)
    static let textMuted = Color(hex: 0x6B7280)
    static let accentViolet = Color(hex: 0x7C3AED)
    static let accentCyan = Color(hex: 0x0891B2)
    static let bullGreen = Color(hex: 0x16A34A)
    static let bearRed = Color(hex: 0xDC2626)
    static let goldAmber = Color(hex: 0xD97706)

    static let brandGradient = LinearGradient(
        colors: [accentViolet, Color(hex: 0x0891B2)],
        startPoint: .topLeading, endPoint: .bottomTrailing
    )
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

struct CardBackground: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(16)
            .background(RoundedRectangle(cornerRadius: 16).fill(Theme.card))
    }
}

extension View {
    func cardStyle() -> some View { modifier(CardBackground()) }
}
