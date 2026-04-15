@preconcurrency import AVFoundation
import Combine

private class Counter { var value = 0 }

// MARK: - MixAudioPlayerIos
// iOS equivalent of Android's MixAudioEngine.
// Uses multiple AVAudioPlayerNode on a shared AVAudioEngine.

@MainActor
class MixAudioPlayerIos: ObservableObject {
    @Published var trackPlaying: [Int64: Bool] = [:]
    @Published var mutedTracks: [Int64: Bool] = [:]
    @Published private(set) var isPaused = false

    var fadeInMs: Int64 = 2000
    var fadeOutMs: Int64 = 1500

    private let engine = AVAudioEngine()
    private var playerNodes: [Int64: AVAudioPlayerNode] = [:]
    private var mixerNodes: [Int64: AVAudioMixerNode] = [:]
    private var mutedVolumes: [Int64: Float] = [:]
    private var padTrackIds = Set<Int64>()
    private var customTrackFiles: [Int64: AVAudioFile] = [:]

    private var clickTrackId: Int64? = nil
    private var clickTimer: DispatchSourceTimer?
    private var isClickRunning = false
    private var clickPlayer: AVAudioPlayer?
    private var accentPlayer: AVAudioPlayer?

    init() {
        engine.prepare()
        do { try engine.start() } catch { print("MixAudioEngine start failed: \(error)") }
        loadClickSounds()
    }

    private func loadClickSounds() {
        if let url = Bundle.main.url(forResource: "click", withExtension: "caf") {
            clickPlayer = try? AVAudioPlayer(contentsOf: url)
            clickPlayer?.prepareToPlay()
        }
        if let url = Bundle.main.url(forResource: "click_accent", withExtension: "caf") {
            accentPlayer = try? AVAudioPlayer(contentsOf: url)
            accentPlayer?.prepareToPlay()
        }
    }

    // MARK: - Track lifecycle

    func startTrack(_ track: MixTrackItem) {
        switch track.trackType {
        case "pad":    startPadTrack(track)
        case "custom": startCustomTrack(track)
        case "click":  startClickTrack(track)
        default: break
        }
        trackPlaying[track.id] = true
    }

    func stopTrack(_ trackId: Int64) {
        if trackId == clickTrackId {
            stopClickInternal()
            return
        }
        playerNodes[trackId]?.stop()
        if let p = playerNodes.removeValue(forKey: trackId) { engine.detach(p) }
        if let m = mixerNodes.removeValue(forKey: trackId) { engine.detach(m) }
        padTrackIds.remove(trackId)
        customTrackFiles.removeValue(forKey: trackId)
        trackPlaying[trackId] = false
    }

    func startAll(_ tracks: [MixTrackItem]) {
        isPaused = false
        tracks.forEach { startTrack($0) }
    }

    func stopAll() {
        isPaused = false
        let ids = Array(playerNodes.keys) + (clickTrackId.map { [$0] } ?? [])
        ids.forEach { stopTrack($0) }
        trackPlaying.removeAll()
    }

    func pauseAll() {
        isPaused = true
        for (id, node) in playerNodes where !padTrackIds.contains(id) {
            node.pause()
            trackPlaying[id] = false
        }
        padTrackIds.forEach { stopTrack($0) }
        if let cid = clickTrackId { stopTrack(cid) }
    }

    func resumeAll(_ allTracks: [MixTrackItem]) {
        isPaused = false
        for (id, node) in playerNodes where !padTrackIds.contains(id) {
            node.play()
            trackPlaying[id] = true
        }
        allTracks.filter { $0.trackType == "pad" || $0.trackType == "click" }
            .forEach { startTrack($0) }
    }

    func seekAllCustom(positionMs: Int64) {
        // AVAudioPlayerNode seek by sample position
        for (id, node) in playerNodes where !padTrackIds.contains(id) {
            guard let file = customTrackFiles[id] else { continue }
            let sampleRate = file.processingFormat.sampleRate
            let samplePosition = AVAudioFramePosition(Double(positionMs) / 1000.0 * sampleRate)
            let remaining = AVAudioFrameCount(file.length - samplePosition)
            if remaining > 0 {
                node.stop()
                node.scheduleSegment(file, startingFrame: samplePosition, frameCount: remaining, at: nil)
                node.play()
            }
        }
    }

    func getCustomDurationMs() -> Int64 {
        customTrackFiles.values.map { file in
            Int64(Double(file.length) / file.processingFormat.sampleRate * 1000.0)
        }.max() ?? 0
    }

    func getCustomPositionMs() -> Int64 {
        guard let (id, node) = playerNodes.first(where: { !padTrackIds.contains($0.key) }),
              let file = customTrackFiles[id],
              let time = node.lastRenderTime,
              let playerTime = node.playerTime(forNodeTime: time) else { return 0 }
        return Int64(Double(playerTime.sampleTime) / file.processingFormat.sampleRate * 1000.0)
    }

    func hasCustomTracks() -> Bool {
        playerNodes.keys.contains { !padTrackIds.contains($0) }
    }

    // MARK: - Volume / mute

    func setTrackVolume(_ trackId: Int64, volume: Float) {
        if mutedTracks[trackId] == true {
            mutedVolumes[trackId] = volume
        } else {
            mixerNodes[trackId]?.outputVolume = volume
        }
    }

    func muteTrack(_ trackId: Int64) {
        guard mutedTracks[trackId] != true else { return }
        mutedTracks[trackId] = true
        if let mixer = mixerNodes[trackId] {
            mutedVolumes[trackId] = mixer.outputVolume
            mixer.outputVolume = 0
        }
    }

    func unmuteTrack(_ trackId: Int64) {
        guard mutedTracks[trackId] == true else { return }
        mutedTracks.removeValue(forKey: trackId)
        if let mixer = mixerNodes[trackId] {
            mixer.outputVolume = mutedVolumes.removeValue(forKey: trackId) ?? 0.5
        }
    }

    func isPlaying(_ trackId: Int64) -> Bool { trackPlaying[trackId] == true }
    func isAnyPlaying() -> Bool { trackPlaying.values.contains(true) }

    func release() {
        stopAll()
        engine.stop()
    }

    // MARK: - Private track starters

    private func startPadTrack(_ track: MixTrackItem) {
        stopTrack(track.id)
        let resName: String
        if let packId = track.soundPackId, packId > 0, let path = track.filePath {
            startPadFromFile(track: track, path: path)
            return
        } else {
            let note = track.note ?? "c"
            let mode = track.padMode ?? "maj"
            resName = "pad_\(note)_\(mode)"
        }
        guard let url = Bundle.main.url(forResource: resName, withExtension: "caf"),
              let file = try? AVAudioFile(forReading: url) else { return }
        startPadWithFile(track: track, file: file)
        padTrackIds.insert(track.id)
    }

    private func startPadFromFile(track: MixTrackItem, path: String) {
        guard let file = try? AVAudioFile(forReading: URL(fileURLWithPath: path)) else { return }
        startPadWithFile(track: track, file: file)
        padTrackIds.insert(track.id)
    }

    private func startPadWithFile(track: MixTrackItem, file: AVAudioFile) {
        guard let buffer = AVAudioPCMBuffer(pcmFormat: file.processingFormat,
                                            frameCapacity: AVAudioFrameCount(file.length)) else { return }
        try? file.read(into: buffer)
        let playerNode = AVAudioPlayerNode()
        let mixerNode = AVAudioMixerNode()
        engine.attach(playerNode)
        engine.attach(mixerNode)
        engine.connect(playerNode, to: mixerNode, format: buffer.format)
        engine.connect(mixerNode, to: engine.mainMixerNode, format: nil)
        mixerNode.pan = pan(for: track.channel)
        mixerNode.outputVolume = 0
        if !engine.isRunning { try? engine.start() }
        playerNode.scheduleBuffer(buffer, at: nil, options: .loops)
        playerNode.play()
        playerNodes[track.id] = playerNode
        mixerNodes[track.id] = mixerNode
        // Fade in
        let steps = 30
        let stepInterval = Double(fadeInMs) / Double(steps) / 1000.0
        let target = track.volume
        let counter = Counter()
        Timer.scheduledTimer(withTimeInterval: stepInterval, repeats: true) { [weak self] t in
            counter.value += 1
            let f = Float(counter.value) / Float(steps)
            self?.mixerNodes[track.id]?.outputVolume = f * f * target
            if counter.value >= steps { t.invalidate() }
        }
    }

    private func startCustomTrack(_ track: MixTrackItem) {
        guard let path = track.filePath,
              let file = try? AVAudioFile(forReading: URL(fileURLWithPath: path)) else { return }
        stopTrack(track.id)
        let playerNode = AVAudioPlayerNode()
        let mixerNode = AVAudioMixerNode()
        engine.attach(playerNode)
        engine.attach(mixerNode)
        engine.connect(playerNode, to: mixerNode, format: file.processingFormat)
        engine.connect(mixerNode, to: engine.mainMixerNode, format: nil)
        mixerNode.pan = pan(for: track.channel)
        mixerNode.outputVolume = track.volume
        if !engine.isRunning { try? engine.start() }
        playerNode.scheduleFile(file, at: nil) { [weak self] in
            DispatchQueue.main.async { self?.trackPlaying[track.id] = false }
        }
        playerNode.play()
        playerNodes[track.id] = playerNode
        mixerNodes[track.id] = mixerNode
        customTrackFiles[track.id] = file
    }

    private func startClickTrack(_ track: MixTrackItem) {
        stopTrack(track.id)
        clickTrackId = track.id
        isClickRunning = true
        let bpm = track.bpm ?? 120
        let accents = track.accents?.split(separator: ",").compactMap { Int($0) } ?? [1, 0, 0, 0]
        let intervalNs = UInt64(60_000_000_000 / bpm)
        let beat = Counter()
        let timer = DispatchSource.makeTimerSource(queue: .main)
        timer.schedule(deadline: .now(), repeating: .nanoseconds(Int(intervalNs)))
        timer.setEventHandler { [weak self] in
            guard let self, self.isClickRunning else { return }
            let state = accents[beat.value % accents.count]
            if state != 2 && self.mutedTracks[track.id] != true {
                let player = state == 1 ? self.accentPlayer : self.clickPlayer
                player?.currentTime = 0
                player?.volume = track.volume
                player?.play()
            }
            beat.value += 1
        }
        timer.resume()
        clickTimer = timer
    }

    private func stopClickInternal() {
        isClickRunning = false
        clickTimer?.cancel()
        clickTimer = nil
        if let id = clickTrackId {
            trackPlaying[id] = false
            clickTrackId = nil
        }
    }

    private func pan(for channel: String) -> Float {
        switch channel.lowercased() {
        case "left":  return -1.0
        case "right": return  1.0
        default:      return  0.0
        }
    }
}
