import SwiftUI

struct RootView: View {
    @EnvironmentObject var session: SessionStore

    var body: some View {
        if session.isSignedIn {
            MainTabView()
        } else {
            WelcomeView()
        }
    }
}
