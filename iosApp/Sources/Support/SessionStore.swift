import Foundation
import SwiftUI

@MainActor
final class SessionStore: ObservableObject {
    @Published var token: String?
    @Published var email: String?
    @Published var name: String?

    private let tokenKey = "marketscope.session.token"
    private let emailKey = "marketscope.session.email"
    private let nameKey = "marketscope.session.name"

    var isSignedIn: Bool { token?.isEmpty == false }

    init() {
        let d = UserDefaults.standard
        token = d.string(forKey: tokenKey)
        email = d.string(forKey: emailKey)
        name = d.string(forKey: nameKey)
    }

    func signIn(token: String, email: String, name: String) {
        self.token = token
        self.email = email
        self.name = name
        let d = UserDefaults.standard
        d.set(token, forKey: tokenKey)
        d.set(email, forKey: emailKey)
        d.set(name, forKey: nameKey)
    }

    func signOut() {
        token = nil; email = nil; name = nil
        let d = UserDefaults.standard
        d.removeObject(forKey: tokenKey)
        d.removeObject(forKey: emailKey)
        d.removeObject(forKey: nameKey)
    }
}
