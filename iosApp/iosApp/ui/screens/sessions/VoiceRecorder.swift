import AVFoundation

/// Records AAC audio into a temp M4A file, matching Android VoiceRecorder's format.
/// Single-use: create → start → stop/cancel. Create a new instance for each recording.
final class VoiceRecorder {
    private var recorder: AVAudioRecorder?
    private let url: URL

    static let mimeType = "audio/mp4"

    init() {
        url = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
            .appendingPathExtension("m4a")
    }

    /// Activates the audio session and starts recording. Throws on permission denial or hardware error.
    func start() throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playAndRecord, mode: .default, options: .defaultToSpeaker)
        try session.setActive(true)

        let settings: [String: Any] = [
            AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
            AVSampleRateKey: 16_000,
            AVNumberOfChannelsKey: 1,
            AVEncoderBitRateKey: 48_000,
            AVEncoderAudioQualityKey: AVAudioQuality.medium.rawValue,
        ]
        recorder = try AVAudioRecorder(url: url, settings: settings)
        recorder?.record()
    }

    /// Stops recording and returns the raw audio bytes, or nil if nothing was captured.
    func stop() -> Data? {
        recorder?.stop()
        recorder = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        return try? Data(contentsOf: url)
    }

    /// Discards the recording without returning data.
    func cancel() {
        recorder?.stop()
        recorder = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        try? FileManager.default.removeItem(at: url)
    }
}
