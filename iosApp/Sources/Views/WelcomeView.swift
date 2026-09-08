import SwiftUI

struct WelcomeView: View {
    @EnvironmentObject var session: SessionStore
    @State private var signingIn = false
    @State private var errorText: String?

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            VStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 22)
                        .fill(Theme.brandGradient)
                        .frame(width: 84, height: 84)
                    Image(systemName: "chart.line.uptrend.xyaxis")
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(.white)
                }
                Text("MarketScope AI")
                    .font(.system(size: 30, weight: .bold))
                    .foregroundColor(Theme.textPrimary)
                Text("AI-powered market analysis, live signals\nand a real trading community.")
                    .font(.subheadline)
                    .foregroundColor(Theme.textMuted)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }
            Spacer()

            Button {
                Task { await googleSignIn() }
            } label: {
                HStack(spacing: 10) {
                    if signingIn { ProgressView().tint(Theme.textPrimary) }
                    else { Image(systemName: "g.circle.fill").font(.title3) }
                    Text(signingIn ? "Signing in…" : "Continue with Google")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 15)
                .background(
                    RoundedRectangle(cornerRadius: 14)
                        .fill(Color.white)
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.card, lineWidth: 1.5))
                )
            }
            .disabled(signingIn || !GoogleAuth.isConfigured)
            .padding(.horizontal, 24)

            if !GoogleAuth.isConfigured {
                Text("Google Sign-In activates once the iOS OAuth client is registered in Google Cloud (same account setup as Android).")
                    .font(.caption2)
                    .foregroundColor(Theme.textMuted)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 28)
                    .padding(.top, 10)
            }
            if let errorText {
                Text(errorText)
                    .font(.footnote)
                    .foregroundColor(Theme.bearRed)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 28)
                    .padding(.top, 8)
            }
            Spacer().frame(height: 46)
        }
        .background(Theme.background.ignoresSafeArea())
    }

    private func googleSignIn() async {
        signingIn = true
        errorText = nil
        do {
            let idToken = try await GoogleAuth.signIn()
            let user = try await Api.authenticate(idToken: idToken)
            guard let token = user["token"] as? String, !token.isEmpty else {
                throw ApiError.backend("Sign-in could not be completed. Please try again.")
            }
            session.signIn(
                token: token,
                email: user["email"] as? String ?? "",
                name: user["name"] as? String ?? "Trader"
            )
        } catch {
            errorText = error.localizedDescription
        }
        signingIn = false
    }
}
