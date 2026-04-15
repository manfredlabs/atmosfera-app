import SwiftUI

private let allNotes = ["c","cs","d","ds","e","f","fs","g","gs","a","as","b"]
private let noteLabels = ["C","C#","D","D#","E","F","F#","G","G#","A","A#","B"]
private let allModes = ["neu","maj","min"]

struct HomeScreen: View {
    @EnvironmentObject var appState: AppState
    @State private var selectedNote = "c"
    @State private var selectedMode = "maj"
    @State private var bpm = 90
    @State private var clickEnabled = false
    @State private var accents = [1,0,0,0]
    @State private var isPlaying = false
    @State private var tapTimes: [Date] = []

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 0) {
                    padGrid
                    Divider().padding(.vertical, 8)
                    noteRow
                    modeRow
                    Divider().padding(.vertical, 8)
                    clickControls
                    Divider().padding(.vertical, 8)
                    volumeControls
                }
                .frame(maxWidth: 600)
                .padding(.horizontal)
            }
            .navigationTitle("Live")
        }
    }

    // MARK: - Pad Grid (4 rows × 3 cols)
    private var padGrid: some View {
        let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 3)
        return LazyVGrid(columns: columns, spacing: 8) {
            ForEach(Array(allNotes.enumerated()), id: \.offset) { idx, note in
                let label = noteLabels[idx]
                let isActive = appState.playingNote == note && isPlaying
                Button {
                    tapPad(note: note)
                } label: {
                    VStack(spacing: 2) {
                        Text(label)
                            .font(.title2.bold())
                        Text(selectedMode)
                            .font(.caption2)
                            .opacity(0.7)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 72)
                    .background(isActive ? Color.accentColor : Color(.secondarySystemBackground))
                    .foregroundColor(isActive ? .white : .primary)
                    .cornerRadius(12)
                }
            }
        }
        .padding(.vertical, 8)
    }

    private var noteRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(Array(allNotes.enumerated()), id: \.offset) { idx, note in
                    Button(noteLabels[idx]) {
                        selectedNote = note
                        if isPlaying { restartPad() }
                    }
                    .buttonStyle(.bordered)
                    .tint(selectedNote == note ? .accentColor : .secondary)
                }
            }
            .padding(.horizontal)
        }
    }

    private var modeRow: some View {
        HStack(spacing: 12) {
            ForEach(allModes, id: \.self) { mode in
                Button(mode.capitalized) {
                    selectedMode = mode
                    if isPlaying { restartPad() }
                }
                .buttonStyle(.bordered)
                .tint(selectedMode == mode ? .accentColor : .secondary)
            }
        }
        .padding(.vertical, 4)
    }

    // MARK: - Click controls
    private var clickControls: some View {
        VStack(spacing: 10) {
            HStack {
                Text("BPM: \(bpm)")
                    .font(.headline)
                Spacer()
                Button("Tap") { handleTap() }
                    .buttonStyle(.bordered)
            }
            Slider(value: Binding(
                get: { Double(bpm) },
                set: { bpm = Int($0); if clickEnabled { restartClick() } }
            ), in: 40...240, step: 1)
            Toggle("Click", isOn: $clickEnabled)
                .onChange(of: clickEnabled) { _, on in
                    on ? startClick() : stopClick()
                }
            // Accent pattern
            HStack(spacing: 8) {
                ForEach(Array(accents.enumerated()), id: \.offset) { idx, val in
                    let isCurrent = clickEnabled && appState.liveAudio.beatOn && appState.liveAudio.currentBeat == idx
                    Button {
                        accents[idx] = (val + 1) % 3
                        if clickEnabled { restartClick() }
                    } label: {
                        Circle()
                            .fill(val == 1 ? Color.accentColor : val == 2 ? Color.gray.opacity(0.3) : Color(.systemBackground))
                            .overlay(Circle().stroke(Color.accentColor, lineWidth: isCurrent ? 3 : 1))
                            .frame(width: 36, height: 36)
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }

    private var volumeControls: some View {
        VStack(spacing: 8) {
            HStack {
                Text("Pad Volume")
                Slider(value: $appState.padVolume, in: 0...1)
                    .onChange(of: appState.padVolume) { _, v in
                        appState.liveAudio.padTargetVolume = v
                    }
            }
            HStack {
                Text("Click Volume")
                Slider(value: $appState.clickVolume, in: 0...1)
            }
        }
        .padding(.bottom, 8)
    }

    // MARK: - Actions

    private func tapPad(note: String) {
        if isPlaying && appState.playingNote == note {
            appState.liveAudio.stopPad()
            isPlaying = false
            appState.playingNote = nil
        } else {
            selectedNote = note
            restartPad()
        }
    }

    private func restartPad() {
        let resName = "pad_\(selectedNote)_\(selectedMode)"
        appState.liveAudio.padTargetVolume = appState.padVolume
        appState.liveAudio.startPad(resName: resName, padChannel: appState.padChannel)
        isPlaying = true
        appState.playingNote = selectedNote
    }

    private func startClick() {
        appState.liveAudio.startClick(
            bpm: bpm, channel: appState.clickChannel,
            volume: appState.clickVolume, accents: accents
        )
    }

    private func stopClick() {
        appState.liveAudio.stopClick()
    }

    private func restartClick() {
        appState.liveAudio.restartClick(
            bpm: bpm, channel: appState.clickChannel,
            volume: appState.clickVolume, accents: accents
        )
    }

    private func handleTap() {
        let now = Date()
        tapTimes.append(now)
        tapTimes = tapTimes.filter { now.timeIntervalSince($0) < 3.0 }
        if tapTimes.count >= 2 {
            let intervals = zip(tapTimes, tapTimes.dropFirst()).map { $1.timeIntervalSince($0) }
            let avg = intervals.reduce(0, +) / Double(intervals.count)
            bpm = max(40, min(240, Int(60.0 / avg)))
            if clickEnabled { restartClick() }
        }
    }
}
