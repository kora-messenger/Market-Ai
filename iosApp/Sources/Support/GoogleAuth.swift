import Foundation
import UIKit
import GoogleSignIn

/// Real GIDSignIn flow. The iOS OAuth client ID is supplied via Info.plist
/// (GOOGLE_IOS_CLIENT_ID) once the iOS client is registered in the Google
/// Cloud project — until then the sign-in button shows an honest note and
/// stays disabled (same pattern the Android build used pre-registration).
enum GoogleAuth {
    static var clientID: String {
        Bundle.main.object(forInfoDictionaryKey: "GOOGLE_IOS_CLIENT_ID") as? String ?? ""
    }

    static var isConfigured: Bool { !clientID.isEmpty }

    static func signIn() async throws -> String {
        guard isConfigured else {
            throw ApiError.notConfigured("Google Sign-In is not configured on this device yet.")
        }
        let config = GIDConfiguration(clientID: clientID)
        GIDSignIn.sharedInstance.configuration = config

        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let root = scene.keyWindow?.rootViewController else {
            throw ApiError.notConfigured("Cannot present the Google Sign-In screen.")
        }
        let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: root)
        guard let idToken = result.user.idToken?.tokenString, !idToken.isEmpty else {
            throw ApiError.notConfigured("Google did not return an ID token. Please try again.")
        }
        return idToken
    }
}
