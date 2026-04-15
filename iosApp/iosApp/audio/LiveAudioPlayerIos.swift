@preconcurrency import AVFoundation
import Combine

private class Counter { var value = 0 }

// MARK: - LiveAudioPlayerIos
// iOS equivalent of Android's AudioEngine.
// Uses AVAudioEngine for pad playback (looping + fade + panning).
// Uses AVAudioPlayer for click/accent (low-latency).

@MainActor
class LiveAudioPlayerIos: ObservableObject {
    @Published var beatOn = false
    @Published var currentBeat = 0

    var fadeInMs: Int64 = 2000
    var fadeOutMs: Int64 = 1500
    var padTargetVolume: Float = 0.5
    var currentClickVolume: Float = 0.5
    var currentClickChannel: String = "mono"
    var currentAccents: [Int] = [1, 0, 0, 0]

    private let engine = AVAudioEngine()
    private var padPlayerNode: AVAudioPlayerNode?
    private var padMixerNode: AVAudioMixerNode?
    private var padBuffer: AVAudioPCMBuffer?

    private var clickPlayer: AVAudioPlayer?
    private var accentPlayer: AVAudioPlayer?

    private var clickTimer: DispatchSourceTimer?
    private var isClickRunning = false
    private var fadeTimer: Timer?

    init() {
        engine.prepare()
        try? engine.start()
    }

    // MARK: - Pad

    func startPad(resName: String, padChannel: String) {
        guard let url = Bundle.main.url(forResource: resName, withExtension: "caf") else {
            print("LiveAudioPlayerIos: missing resource \(resName).caf")
            return
        }
        startPadFromFile(filePath: url.path, padChannel: padChannel)
    }

    func startPadFromFile(filePath: String, padChannel: String) {
        stopPadImmediate()
        guard let audioFile = try? AVAudioFile(forReading: URL(fileURLWithPath: filePath)) else { return }

        guard let buffer = AVAudioPCMBuffer(
            pcmFormat: audioFile.processingFormat,
            frameCapacity: AVAudioFrameCount(audioFile.length)
        ) else { return }
        try? audioFile.read(into: buffer)

        let playerNode = AVAudioPlayerNode()
        let mixerNode = AVAudioMixerNode()
        engine.attach(playerNode)
        engine.attach(mixerNode)
        engine.connect(playerNode, to: mixerNode, format: buffer.format)
        engine.connect(mixerNode, to: engine.mainMixerNode, format: nil)

        applyPanning(mixerNode: mixerNode, channel: padChannel)
        mixerNode.outputVolume = 0

        if !engine.isRunning { try? engine.start() }

        playerNode.scheduleBuffer(buffer, at: nil, options: .loops, completionHandler: nil)
        playerNode.play()

        padPlayerNode = playerNode
        padMixerNode = mixerNode
        padBuffer = buffer

        // Fade in
        let steps = 30
        let stepMs = Double(fadeInMs) / Double(steps) / 1000.0
        let target = padTargetVolume
        let counter = Counter()
        fadeTimer?.invalidate()
        fadeTimer = Timer.scheduledTimer(withTimeInterval: stepMs, repeats: true) { [weak self] t in
            counter.value += 1
            let fraction = Float(counter.value) / Float(steps)
            self?.padMixerNode?.outputVolume = fraction * fraction * target
            if counter.value >= steps { t.invalidate() }
        }
    }

    func stopPad(onComplete: (() -> Void)? = nil) {
        fadeTimer?.invalidate()
        guard let mixer = padMixerNode else {
            onComplete?()
            return
        }
        let startVolume = mixer.outputVolume
        let steps = 30
        let stepMs = Double(fadeOutMs) / Double(steps) / 1000.0
        let counter = Counter()
        let capturedPlayer = padPlayerNode
        let capturedMixer = padMixerNode
        padPlayerNode = nil
        padMixerNode = nil
        fadeTimer = Timer.scheduledTimer(withTimeInterval: stepMs, repeats: true) { [weak self] t in
            counter.value += 1
            let fraction = 1.0 - Float(counter.value) / Float(steps)
            capturedMixer?.outputVolume = fraction * fraction * startVolume
            if counter.value >= steps {
                t.invalidate()
                capturedPlayer?.stop()
                if let p = capturedPlayer { self?.engine.detach(p) }
                if let m = capturedMixer { self?.engine.detach(m) }
                onComplete?()
            }
        }
    }

    func stopPadImmediate() {
        fadeTimer?.invalidate()
        padPlayerNode?.stop()
        if let p = padPlayerNode { engine.detach(p) }
        if let m = padMixerNode { engine.detach(m) }
        padPlayerNode = nil
        padMixerNode = nil
    }

    func updatePadPanning(channel: String) {
        guard let mixer = padMixerNode else { return }
        applyPanning(mixerNode: mixer, channel: channel)
    }

    private func applyPanning(mixerNode: AVAudioMixerNode, channel: String) {
        switch channel.lowercased() {
        case "left":  mixerNode.pan = -1.0
        case "right": mixerNode.pan =  1.0
        default:      mixerNode.pan =  0.0
        }
    }

    // MARK: - Click

    func startClick(bpm: Int, channel: String, volume: Float, accents: [Int]) {
        stopClick()
        isClickRunning = true
        currentClickVolume = volume
        currentClickChannel = channel
        currentAccents = accents

        let intervalNs = UInt64(60_000_000_000 / bpm)
        let beat = Counter()
        let timer = DispatchSource.makeTimerSource(queue: .main)
        timer.schedule(deadline: .now(), repeating: .nanoseconds(Int(intervalNs)))
        timer.setEventHandler { [weak self] in
            guard let self, self.isClickRunning else { return }
            let state = self.currentAccents[beat.value % self.currentAccents.count]
            if state != 2 {
                let player = state == 1 ? self.accentPlayer : self.clickPlayer
                player?.currentTime = 0
                player?.volume = self.currentClickVolume
                player?.play()
            }
            self.currentBeat = beat.value % self.currentAccents.count
            self.beatOn = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.08) { self.beatOn = false }
            beat.value += 1
        }
        timer.resume()
        clickTimer = timer

        // Load click sounds lazily
        if clickPlayer == nil {
            if let url = Bundle.main.url(forResource: "click", withExtension: "caf") {
                clickPlayer = try? AVAudioPlayer(contentsOf: url)
                clickPlayer?.prepareToPlay()
            }
        }
        if accentPlayer == nil {
            if let url = Bundle.main.url(forResource: "click_accent", withExtension: "caf") {
                accentPlayer = try? AVAudioPlayer(contentsOf: url)
                accentPlayer?.prepareToPlay()
            }
        }
    }

    func stopClick() {
        isClickRunning = false
        clickTimer?.cancel()
        clickTimer = nil
        beatOn = false
        currentBeat = 0
    }

    func restartClick(bpm: Int, channel: String, volume: Float, accents: [Int]) {
        stopClick()
        startClick(bpm: bpm, channel: channel, volume: volume, accents: accents)
    }

    func release() {
        stopClick()
        stopPadImmediate()
        engine.stop()
    }
}
