import Foundation
import MarketScopeShared

/// Swift bridge over the Kotlin Multiplatform SharedApiClient.
/// Kotlin suspend functions surface in Swift as completion-handler methods whose
/// result type is the exported kotlinx JsonElement class; each wrapper converts
/// to async/await and parses the JSON text (JsonElement.toString() is its
/// JSON encoding) via JSONSerialization.
typealias KJsonElement = MarketScopeShared.Kotlinx_serialization_jsonJsonElement

enum ApiError: LocalizedError {
    case notConfigured(String)
    case emptyResponse
    case backend(String)

    var errorDescription: String? {
        switch self {
        case .notConfigured(let msg): return msg
        case .emptyResponse: return "Empty response from the server."
        case .backend(let msg): return msg
        }
    }
}

enum Api {
    static func parseDict(_ text: String) throws -> [String: Any] {
        let data = text.data(using: .utf8) ?? Data()
        let obj = try JSONSerialization.jsonObject(with: data, options: [])
        guard let dict = obj as? [String: Any] else { throw ApiError.emptyResponse }
        return dict
    }

    static func parseArray(_ text: String) throws -> [[String: Any]] {
        let data = text.data(using: .utf8) ?? Data()
        let obj = try JSONSerialization.jsonObject(with: data, options: [])
        if let arr = obj as? [[String: Any]] { return arr }
        throw ApiError.emptyResponse
    }

    // ------------------------------------------------------------- wrappers

    private static func raw(
        _ invoke: @escaping (@escaping (KJsonElement?, Error?) -> Void) -> Void
    ) async throws -> String {
        try await withCheckedThrowingContinuation { cont in
            invoke { element, error in
                if let error { cont.resume(throwing: error); return }
                guard let element else { cont.resume(throwing: ApiError.emptyResponse); return }
                cont.resume(returning: String(describing: element))
            }
        }
    }

    private static func objectCall(
        _ invoke: @escaping (@escaping (KJsonElement?, Error?) -> Void) -> Void
    ) async throws -> [String: Any] {
        try parseDict(try await raw(invoke))
    }

    private static func arrayCall(
        _ invoke: @escaping (@escaping (KJsonElement?, Error?) -> Void) -> Void
    ) async throws -> [[String: Any]] {
        try parseArray(try await raw(invoke))
    }

    static func authenticate(idToken: String) async throws -> [String: Any] {
        try await objectCall { completion in
            SharedApiClient.shared.authenticateWithGoogle(idToken: idToken, completionHandler: completion)
        }
    }

    static func trialStatus(token: String) async throws -> [String: Any] {
        try await objectCall { completion in
            SharedApiClient.shared.fetchTrialStatus(sessionToken: token, completionHandler: completion)
        }
    }

    static func watchlist() async throws -> [[String: Any]] {
        try await arrayCall { completion in
            SharedApiClient.shared.fetchMarketsWatchlist(completionHandler: completion)
        }
    }

    static func signalStats(range: String) async throws -> [String: Any] {
        try await objectCall { completion in
            SharedApiClient.shared.fetchSignalStats(range: range, completionHandler: completion)
        }
    }

    static func signalsFeed(token: String) async throws -> [String: Any] {
        try await objectCall { completion in
            SharedApiClient.shared.fetchDailySignalsFeed(sessionToken: token, limit: 50, completionHandler: completion)
        }
    }

    static func communityStats() async throws -> [String: Any] {
        try await objectCall { completion in
            SharedApiClient.shared.fetchCommunityStats(completionHandler: completion)
        }
    }

    static func communityFeed(token: String) async throws -> [String: Any] {
        try await objectCall { completion in
            SharedApiClient.shared.fetchCommunityFeed(sessionToken: token, offset: 0, limit: 20, completionHandler: completion)
        }
    }

    static func marketNews(category: String) async throws -> [[String: Any]] {
        try await arrayCall { completion in
            SharedApiClient.shared.fetchMarketNews(category: category, limit: 30, completionHandler: completion)
        }
    }

    static func economicCalendar() async throws -> [[String: Any]] {
        try await arrayCall { completion in
            SharedApiClient.shared.fetchEconomicCalendar(completionHandler: completion)
        }
    }
}
