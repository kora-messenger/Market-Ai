import SwiftUI

struct ProfileView: View {
    @EnvironmentObject var session: SessionStore

    var body: some View {
        NavigationView {
            VStack(spacing: 16) {
                ZStack {
                    Circle().fill(Theme.brandGradient).frame(width: 84, height: 84)
                    Text(initial)
                        .font(.system(size: 32, weight: .bold)).foregroundColor(.white)
                }
                .padding(.top, 24)
                VStack(spacing: 4) {
                    Text(session.name ?? "Trader")
                        .font(.title3.bold()).foregroundColor(Theme.textPrimary)
                    Text(session.email ?? "")
                        .font(.subheadline).foregroundColor(Theme.textMuted)
                }
                Spacer()
                Button {
                    session.signOut()
                } label: {
                    Text("Sign out")
                        .font(.system(size: 15, weight: .semibold)).foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 14)
                        .background(RoundedRectangle(cornerRadius: 14).fill(Theme.bearRed))
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 32)
            }
            .frame(maxWidth: .infinity)
            .background(Theme.background.ignoresSafeArea())
            .navigationBarHidden(true)
        }
        .navigationViewStyle(.stack)
    }

    private var initial: String {
        String((session.name ?? "T").prefix(1)).uppercased()
    }
}
