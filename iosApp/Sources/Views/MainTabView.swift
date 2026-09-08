import SwiftUI

struct MainTabView: View {
    var body: some View {
        TabView {
            HomeView()
                .tabItem { Label("Home", systemImage: "house.fill") }
            SignalsView()
                .tabItem { Label("Signals", systemImage: "bolt.fill") }
            CommunityView()
                .tabItem { Label("Community", systemImage: "person.3.fill") }
            CalendarView()
                .tabItem { Label("Calendar", systemImage: "calendar") }
            ProfileView()
                .tabItem { Label("Profile", systemImage: "person.crop.circle") }
        }
        .tint(Theme.accentViolet)
    }
}
