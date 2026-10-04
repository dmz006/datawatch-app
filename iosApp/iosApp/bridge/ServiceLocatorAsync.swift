import Foundation
import DatawatchShared

/// async/await wrappers over `IosServiceLocator`'s callback API so ViewModels can
/// poll sequentially — awaiting one fetch before scheduling the next. Each Kotlin
/// callback fires exactly once, which is what `CheckedContinuation` requires.
enum ServiceLocatorAsync {
    struct TransportError: LocalizedError {
        let message: String
        var errorDescription: String? { message }
    }

    static func listSessions(profile: ServerProfile) async throws -> [Session] {
        try await withCheckedThrowingContinuation { cont in
            IosServiceLocator.shared.listSessions(
                profile: profile,
                onSuccess: { cont.resume(returning: $0) },
                onError: { cont.resume(throwing: TransportError(message: $0)) }
            )
        }
    }

    static func listAlerts(profile: ServerProfile) async throws -> (alerts: [Alert], unreadCount: Int) {
        try await withCheckedThrowingContinuation { cont in
            IosServiceLocator.shared.listAlerts(
                profile: profile,
                onSuccess: { cont.resume(returning: ($0.alerts, Int($0.unreadCount))) },
                onError: { cont.resume(throwing: TransportError(message: $0)) }
            )
        }
    }

    static func getStats(profile: ServerProfile) async throws -> StatsDto {
        try await withCheckedThrowingContinuation { cont in
            IosServiceLocator.shared.getStats(
                profile: profile,
                onSuccess: { cont.resume(returning: $0) },
                onError: { cont.resume(throwing: TransportError(message: $0)) }
            )
        }
    }

    // MARK: Autonomous PRDs

    static func listPrds(profile: ServerProfile) async throws -> [PrdDto] {
        try await withCheckedThrowingContinuation { cont in
            IosServiceLocator.shared.listPrds(
                profile: profile,
                onSuccess: { cont.resume(returning: $0) },
                onError: { cont.resume(throwing: TransportError(message: $0)) }
            )
        }
    }

    static func getPrd(profile: ServerProfile, prdId: String) async throws -> PrdDto {
        try await withCheckedThrowingContinuation { cont in
            IosServiceLocator.shared.getPrd(
                profile: profile,
                prdId: prdId,
                onSuccess: { cont.resume(returning: $0) },
                onError: { cont.resume(throwing: TransportError(message: $0)) }
            )
        }
    }

    /// `body` is a flat string map serialised to a JSON object (e.g. `["reason": "…"]`).
    static func prdAction(profile: ServerProfile, prdId: String, action: String, body: [String: String]? = nil) async throws {
        let bodyJson: String? = body.flatMap { dict in
            (try? JSONSerialization.data(withJSONObject: dict)).flatMap { String(data: $0, encoding: .utf8) }
        }
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
            IosServiceLocator.shared.prdAction(
                profile: profile,
                prdId: prdId,
                action: action,
                bodyJson: bodyJson,
                onSuccess: { cont.resume(returning: ()) },
                onError: { cont.resume(throwing: TransportError(message: $0)) }
            )
        }
    }

    static func cancelPrd(profile: ServerProfile, prdId: String, hard: Bool = false) async throws {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
            IosServiceLocator.shared.cancelPrd(
                profile: profile,
                prdId: prdId,
                hard: hard,
                onSuccess: { cont.resume(returning: ()) },
                onError: { cont.resume(throwing: TransportError(message: $0)) }
            )
        }
    }
}
