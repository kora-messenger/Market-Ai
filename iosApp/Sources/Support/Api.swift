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
    // ------------------------------------------------------------- wrappers

    /// A JsonElement's description is its own JSON text (KotlinBase.description
    /// calls Kotlin's toString()), so each element parses individually.
    private static func parseElement(_ el: KJsonElement) -> Any? {
        guard let data = String(describing: el).data(using: .utf8) else { return nil }
        return try? JSONSerialization.jsonObject(with: data)
    }

    private static func objectCall(
        _ invoke: @escaping (@escaping ([String: KJsonElement]?, Error?) -> Void) -> Void
    ) async throws -> [String: Any] {
        try await withCheckedThrowingContinuation { cont in
            invoke { dict, error in
                if let error { cont.resume(throwing: error); return }
                guard let dict else { cont.resume(throwing: ApiError.emptyResponse); return }
                var out: [String: Any] = [:]
                for (key, value) in dict {
                    out[key] = parseElement(value) ?? NSNull()
                }
                cont.resume(returning: out)
            }
        }
    }

    private static func arrayCall(
        _ invoke: @escaping (@escaping ([KJsonElement]?, Error?) -> Void) -> Void
    ) async throws -> [[String: Any]] {
        try await withCheckedThrowingContinuation { cont in
            invoke { arr, error in
                if let error { cont.resume(throwing: error); return }
                guard let arr else { cont.resume(throwing: ApiError.emptyResponse); return }
                var out: [[String: Any]] = []
                for el in arr {
                    if let row = parseElement(el) as? [String: Any] {
                        out.append(row)
                    }
                }
                cont.resume(returning: out)
            }
        }
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
