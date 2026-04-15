import SwiftUI
import UniformTypeIdentifiers

private let channelOptions = ["mono","left","right"]

struct MixStudioEditorScreen: View {
    @EnvironmentObject var appState: AppState
    let project: MixProjectItem
    @State private var tracks: [MixTrackItem] = []
    @State private var showAddTrack = false
    @State private var showFilePicker = false
    @State private var isPlaying = false

    var body: some View {
        VStack(spacing: 0) {
            // Transport bar
            transportBar

            Divider()

            // Track list
            if tracks.isEmpty {
                ContentUnavailableView("No tracks", systemImage: "waveform",
                    description: Text("Tap + to add tracks."))
            } else {
                List {
                    ForEach(tracks) { track in
                        TrackRow(
                            track: track,
                            isPlaying: appState.mixAudio.trackPlaying[track.id] == true,
                            isMuted: appState.mixAudio.mutedTracks[track.id] == true,
                            onVolume: { vol in appState.mixAudio.setTrackVolume(track.id, volume: vol) },
                            onMute: {
                                if appState.mixAudio.mutedTracks[track.id] == true {
                                    appState.mixAudio.unmuteTrack(track.id)
                                } else {
                                    appState.mixAudio.muteTrack(track.id)
                                }
                            }
                        )
                    }
                    .onMove { from, to in
                        tracks.move(fromOffsets: from, toOffset: to)
                    }
                    .onDelete { indexSet in
                        let toDelete = indexSet.map { tracks[$0] }
                        tracks.remove(atOffsets: indexSet)
                        toDelete.forEach { t in
                            appState.mixAudio.stopTrack(t.id)
                            Task { await appState.deleteMixTrack(trackId: t.id, projectId: project.id) }
                        }
                    }
                }
                .environment(\.editMode, .constant(.active))
            }
        }
        .frame(maxWidth: 600)
        .navigationTitle(project.name)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button { showAddTrack = true } label: { Image(systemName: "plus") }
            }
        }
        .confirmationDialog("Add Track", isPresented: $showAddTrack) {
            Button("Pad Track") { addPadTrack() }
            Button("Click Track") { addClickTrack() }
            Button("Audio File") { showFilePicker = true }
            Button("Cancel", role: .cancel) {}
        }
        .fileImporter(isPresented: $showFilePicker,
                      allowedContentTypes: [.audio]) { result in
            if case .success(let url) = result {
                addCustomTrack(url: url)
            }
        }
        .onDisappear {
            appState.mixAudio.stopAll()
        }
        .task {
            tracks = await appState.getTracksForProject(projectId: project.id)
        }
    }

    // MARK: - Transport

    private var transportBar: some View {
        HStack(spacing: 24) {
            Button {
                if isPlaying {
                    appState.mixAudio.stopAll()
                    isPlaying = false
                } else {
                    appState.mixAudio.startAll(tracks)
                    isPlaying = true
                }
            } label: {
                Image(systemName: isPlaying ? "stop.fill" : "play.fill")
                    .font(.title2)
            }

            if isPlaying {
                Button {
                    if appState.mixAudio.isPaused {
                        appState.mixAudio.resumeAll(tracks)
                    } else {
                        appState.mixAudio.pauseAll()
                    }
                } label: {
                    Image(systemName: appState.mixAudio.isPaused ? "play.circle" : "pause.fill")
                        .font(.title2)
                }
            }

            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }

    // MARK: - Track creation helpers

    private func addPadTrack() {
        Task {
            let sortOrder = tracks.count
            let id = await appState.addMixTrack(
                projectId: project.id, trackType: "pad", label: "Pad",
                volume: 0.5, channel: "mono", sortOrder: sortOrder,
                note: "c", padMode: "maj"
            )
            let track = MixTrackItem(
                id: id, projectId: project.id, trackType: "pad", label: "Pad",
                volume: 0.5, channel: "mono", sortOrder: sortOrder, note: "c", padMode: "maj"
            )
            tracks.append(track)
        }
    }

    private func addClickTrack() {
        Task {
            let sortOrder = tracks.count
            let id = await appState.addMixTrack(
                projectId: project.id, trackType: "click", label: "Click",
                volume: 0.5, channel: "mono", sortOrder: sortOrder,
                bpm: 90, accents: "1,0,0,0"
            )
            let track = MixTrackItem(
                id: id, projectId: project.id, trackType: "click", label: "Click",
                volume: 0.5, channel: "mono", sortOrder: sortOrder, bpm: 90, accents: "1,0,0,0"
            )
            tracks.append(track)
        }
    }

    private func addCustomTrack(url: URL) {
        let dest = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("mixstudio/\(project.id)/\(url.lastPathComponent)")
        try? FileManager.default.createDirectory(at: dest.deletingLastPathComponent(),
                                                 withIntermediateDirectories: true)
        if url.startAccessingSecurityScopedResource() {
            defer { url.stopAccessingSecurityScopedResource() }
            try? FileManager.default.copyItem(at: url, to: dest)
        } else {
            try? FileManager.default.copyItem(at: url, to: dest)
        }
        let label = url.deletingPathExtension().lastPathComponent
        Task {
            let sortOrder = tracks.count
            let id = await appState.addMixTrack(
                projectId: project.id, trackType: "custom", label: label,
                volume: 0.5, channel: "mono", sortOrder: sortOrder,
                filePath: dest.path, fileName: url.lastPathComponent
            )
            let track = MixTrackItem(
                id: id, projectId: project.id, trackType: "custom", label: label,
                volume: 0.5, channel: "mono", sortOrder: sortOrder,
                filePath: dest.path, fileName: url.lastPathComponent
            )
            tracks.append(track)
        }
    }
}

// MARK: - TrackRow

private struct TrackRow: View {
    let track: MixTrackItem
    let isPlaying: Bool
    let isMuted: Bool
    let onVolume: (Float) -> Void
    let onMute: () -> Void
    @State private var volume: Float

    init(track: MixTrackItem, isPlaying: Bool, isMuted: Bool,
         onVolume: @escaping (Float) -> Void, onMute: @escaping () -> Void) {
        self.track = track
        self.isPlaying = isPlaying
        self.isMuted = isMuted
        self.onVolume = onVolume
        self.onMute = onMute
        _volume = State(initialValue: track.volume)
    }

    var typeIcon: String {
        switch track.trackType {
        case "pad":    return "waveform"
        case "click":  return "metronome"
        case "custom": return "music.note"
        default:       return "questionmark"
        }
    }

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: typeIcon)
                .foregroundStyle(isPlaying ? .accentColor : .secondary)
                .frame(width: 20)

            VStack(alignment: .leading, spacing: 2) {
                Text(track.label).font(.subheadline.bold())
                Slider(value: Binding(get: { volume }, set: { volume = $0; onVolume($0) }), in: 0...1)
                    .labelsHidden()
            }

            Button { onMute() } label: {
                Image(systemName: isMuted ? "speaker.slash.fill" : "speaker.wave.2")
                    .foregroundStyle(isMuted ? .secondary : .primary)
            }
            .buttonStyle(.plain)
        }
        .listRowBackground(isPlaying ? Color.accentColor.opacity(0.1) : nil)
    }
}
