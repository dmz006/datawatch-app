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
}
