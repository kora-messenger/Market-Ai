import SwiftUI

@main
struct MarketScopeApp: App {
    @StateObject private var session = SessionStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(session)
                // MarketScope AI is always white/light — enforced at the root.
                .preferredColorScheme(.light)
        }
    }
}
