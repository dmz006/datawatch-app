import Foundation
import CryptoKit
import Security

/// Fetches the TLS leaf certificate a server presents — without trusting it or
/// sending any request body — so the user can review and pin its SHA-256
/// fingerprint (trust-on-first-use). The pin is stored in
/// `ServerProfile.trustAnchorSha256` and enforced by the shared Kotlin transport.
enum CertProbe {
    struct Fingerprint {
        /// Lowercase hex SHA-256 of the leaf certificate DER.
        let sha256Hex: String
        let subject: String

        var display: String { CertProbe.display(sha256Hex) }
    }

    enum ProbeError: LocalizedError {
        case notHttps
        case noCertificate

        var errorDescription: String? {
            switch self {
            case .notHttps:      return "Enter an https:// URL first."
            case .noCertificate: return "Could not reach the server or it presented no certificate."
            }
        }
    }

    /// `AB:CD:…` grouping for a hex fingerprint.
    static func display(_ hex: String) -> String {
        let clean = hex.lowercased().filter { $0.isHexDigit }
        var parts: [String] = []
        var i = clean.startIndex
        while i < clean.endIndex {
            let j = clean.index(i, offsetBy: 2, limitedBy: clean.endIndex) ?? clean.endIndex
            parts.append(String(clean[i..<j]).uppercased())
            i = j
        }
        return parts.joined(separator: ":")
    }

    static func fetch(baseUrl: String) async throws -> Fingerprint {
        guard let url = URL(string: baseUrl.trimmingCharacters(in: .whitespaces)),
              url.scheme?.lowercased() == "https"
        else { throw ProbeError.notHttps }

        let delegate = Delegate()
        let session = URLSession(configuration: .ephemeral, delegate: delegate, delegateQueue: nil)
        defer { session.invalidateAndCancel() }

        var request = URLRequest(url: url)
        request.httpMethod = "HEAD"
        request.timeoutInterval = 10
        // The delegate cancels the challenge after capturing the certificate, so the
        // request itself is expected to fail — only the capture matters.
        _ = try? await session.data(for: request)

        guard let fp = delegate.captured else { throw ProbeError.noCertificate }
        return fp
    }

    private final class Delegate: NSObject, URLSessionDelegate {
        var captured: Fingerprint?

        func urlSession(
            _ session: URLSession,
            didReceive challenge: URLAuthenticationChallenge,
            completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
        ) {
            guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
                  let trust = challenge.protectionSpace.serverTrust,
                  let chain = SecTrustCopyCertificateChain(trust) as? [SecCertificate],
                  let leaf = chain.first
            else {
                completionHandler(.performDefaultHandling, nil)
                return
            }
            let der = SecCertificateCopyData(leaf) as Data
            let hex = SHA256.hash(data: der).map { String(format: "%02x", $0) }.joined()
            let subject = (SecCertificateCopySubjectSummary(leaf) as String?) ?? "(unknown subject)"
            captured = Fingerprint(sha256Hex: hex, subject: subject)
            completionHandler(.cancelAuthenticationChallenge, nil)
        }
    }
}
