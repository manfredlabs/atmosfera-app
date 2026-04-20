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
    @State private var projectName: String = ""
    @State private var inPlaylist: Bool = false

    var body: some View {
        ZStack {
            Color.darkBg.ignoresSafeArea()

            VStack(spacing: 0) {
                // Header
                ScreenHeader(title: "MIX STUDIO")
                    .padding(.top, 8)

                // Editable project name
                TextField("e.g. Worship Set 1", text: $projectName)
                    .font(.spaceGrotesk(.regular, size: 18))
                    .foregroundColor(.textPrimary)
                    .padding(.horizontal, 16)
                    .frame(height: 48)
                    .background(Color.padIdle)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(Color.padBorder.opacity(0.5), lineWidth: 1)
                    )
                    .padding(.horizontal, 20)
                    .padding(.vertical, 8)
                    .onChange(of: projectName) { _, newName in
                        guard !newName.isEmpty else { return }
                        Task { await appState.renameMixProject(id: project.id, name: newName) }
                    }

                // Transport bar
                transportBar
                    .padding(.bottom, 8)

                Rectangle()
                    .fill(Color.padBorder.opacity(0.3))
                    .frame(height: 1)

                // Track list
                if tracks.isEmpty {
                    Spacer()
                    VStack(spacing: 12) {
                        Image(systemName: "waveform")
                            .font(.system(size: 48))
                            .foregroundColor(.textSecondary.opacity(0.3))
                        Text("No tracks")
                            .font(.spaceGrotesk(.regular, size: 16))
                            .foregroundColor(.textSecondary.opacity(0.5))
                        Text("Tap + to add tracks")
                            .font(.spaceGrotesk(.regular, size: 13))
                            .foregroundColor(.textSecondary.opacity(0.3))
                    }
                    Spacer()
                } else {
                    ScrollView {
                        LazyVStack(spacing: 8) {
                            ForEach(tracks) { track in
                                TrackRowView(
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
                                    },
                                    onDelete: {
                                        appState.mixAudio.stopTrack(track.id)
                                        tracks.removeAll { $0.id == track.id }
                                        Task { await appState.deleteMixTrack(trackId: track.id, projectId: project.id) }
                                    }
                                )
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 12)
                    }
                }
            }
            .frame(maxWidth: 600)
            .padding(.horizontal, 20)

            // Playlist button + FAB
            VStack {
                Spacer()

                // Playlist toggle button
                Button {
                    inPlaylist.toggle()
                    Task { await appState.toggleMixProjectInPlaylist(id: project.id) }
                } label: {
                    Text(inPlaylist ? "REMOVE FROM PLAYLIST" : "SEND TO PLAYLIST")
                        .font(.spaceGrotesk(.bold, size: 13))
                        .tracking(2)
                        .foregroundColor(inPlaylist ? .labsPurple : .textPrimary)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background(inPlaylist ? Color.labsPurple.opacity(0.15) : Color.labsPurple)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(Color.labsPurple, lineWidth: inPlaylist ? 1 : 0)
                        )
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 8)

                HStack {
                    Spacer()
                    Button { showAddTrack = true } label: {
                        Image(systemName: "plus")
                            .font(.title2)
                            .foregroundColor(.textPrimary)
                            .frame(width: 56, height: 56)
                            .background(Color.labsPurple)
                            .clipShape(Circle())
                    }
                    .padding(24)
                }
            }
        }
        .navigationBarHidden(true)
        .onAppear { appState.showBottomBar = false }
        .confirmationDialog("Add Track",isPresented: $showAddTrack) {
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
            projectName = project.name
            inPlaylist = project.inPlaylist
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
                    .foregroundColor(isPlaying ? .labsPurple : .textPrimary)
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
                        .foregroundColor(.textPrimary)
                }
            }

            Spacer()
        }
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

// MARK: - TrackRow (dark themed)

private struct TrackRowView: View {
    let track: MixTrackItem
    let isPlaying: Bool
    let isMuted: Bool
    let onVolume: (Float) -> Void
    let onMute: () -> Void
    let onDelete: () -> Void
    @State private var volume: Float

    init(track: MixTrackItem, isPlaying: Bool, isMuted: Bool,
         onVolume: @escaping (Float) -> Void, onMute: @escaping () -> Void,
         onDelete: @escaping () -> Void) {
        self.track = track
        self.isPlaying = isPlaying
        self.isMuted = isMuted
        self.onVolume = onVolume
        self.onMute = onMute
        self.onDelete = onDelete
        _volume = State(initialValue: track.volume)
    }

    var typeIcon: String {
        switch track.trackType {
        case "pad":    return "pianokeys"
        case "click":  return "metronome"
        case "custom": return "music.note"
        default:       return "questionmark"
        }
    }

    var accentColor: Color {
        switch track.trackType {
        case "pad":   return .ledAmber
        case "click": return .clickTeal
        default:      return .labsPurple
        }
    }

    var body: some View {
        DarkSurface(borderColor: isPlaying ? accentColor.opacity(0.5) : .padBorder) {
            HStack(spacing: 12) {
                // Type icon
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(accentColor.opacity(0.15))
                        .frame(width: 36, height: 36)
                    Image(systemName: typeIcon)
                        .font(.system(size: 16))
                        .foregroundColor(isPlaying ? accentColor : .textSecondary)
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text(track.label)
                        .font(.spaceGrotesk(.medium, size: 14))
                        .foregroundColor(.textPrimary)
                    Slider(value: Binding(get: { volume }, set: { volume = $0; onVolume($0) }), in: 0...1)
                        .tint(accentColor)
                }

                Button { onMute() } label: {
                    Image(systemName: isMuted ? "speaker.slash.fill" : "speaker.wave.2")
                        .font(.system(size: 14))
                        .foregroundColor(isMuted ? .textSecondary.opacity(0.4) : .textPrimary)
                }
                .buttonStyle(.plain)

                Button { onDelete() } label: {
                    Image(systemName: "trash")
                        .font(.system(size: 14))
                        .foregroundColor(.textSecondary.opacity(0.4))
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
        }
    }
}
