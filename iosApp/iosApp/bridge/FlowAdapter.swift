import Foundation
import DatawatchShared

/// Bridges Kotlin `Flow<T>` to Swift `AsyncThrowingStream<T, Error>`.
///
/// KMP shared ViewModels expose `StateFlow<T>` and `Flow<T>` via the ObjC header.
/// Swift `Task {}` blocks collect these flows and push values into `@Observable`
/// `@Published` properties. This adapter encapsulates the boilerplate.
///
/// Usage example (in a SwiftUI ViewModel):
/// ```swift
/// let stream = FlowAdapter.stream(from: sharedVm.sessions)
/// for await sessions in stream { self.sessions = sessions }
/// ```
///
/// Threading: Kotlin coroutines run on `Dispatchers.Default`; the collected
/// values are forwarded to Swift and should be dispatched to `@MainActor`
/// by the caller if they drive `@Published` / `@Observable` properties.
enum FlowAdapter {

    /// Wrap a `Kotlinx_coroutines_coreFlowCollector`-compatible Kotlin Flow
    /// as a Swift `AsyncThrowingStream`. The stream terminates when the Flow
    /// completes or the owning `Task` is cancelled.
    ///
    /// - Parameter flow: Any Kotlin `Flow<T>` exposed via the KMP ObjC interop.
    /// - Returns: An `AsyncThrowingStream` that yields each emitted value.
    static func stream<T: AnyObject>(
        from flow: Kotlinx_coroutines_coreFlow
    ) -> AsyncThrowingStream<T, Error> {
        AsyncThrowingStream { continuation in
            let collector = FlowCollector<T>(continuation: continuation)
            Task {
                do {
                    try await withCheckedThrowingContinuation { (inner: CheckedContinuation<Void, Error>) in
                        flow.collect(collector: collector) { error in
                            if let error {
                                inner.resume(throwing: error)
                            } else {
                                inner.resume()
                            }
                        }
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }
}

// ── Internal collector ────────────────────────────────────────────────────

/// ObjC-compatible `FlowCollector` that forwards emitted values to a
/// Swift `AsyncThrowingStream.Continuation`.
private final class FlowCollector<T: AnyObject>: Kotlinx_coroutines_coreFlowCollector {
    private let continuation: AsyncThrowingStream<T, Error>.Continuation

    init(continuation: AsyncThrowingStream<T, Error>.Continuation) {
        self.continuation = continuation
    }

    func emit(value: Any?, completionHandler: @escaping (Error?) -> Void) {
        if let typed = value as? T {
            continuation.yield(typed)
        }
        completionHandler(nil)
    }
}
