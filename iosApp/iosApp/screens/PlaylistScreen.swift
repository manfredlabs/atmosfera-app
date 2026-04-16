import SwiftUI

private let noteLabels: [String: String] = [
    "c": "C", "cs": "C#", "d": "D", "ds": "D#",
    "e": "E", "f": "F", "fs": "F#", "g": "G",
    "gs": "G#", "a": "A", "as": "A#", "b": "B"
]

struct PlaylistScreen: View {
    @EnvironmentObject var appState: AppState
    @State private var showAddSong = false
    @State private var editingSong: SongItem? = nil
    @State private var isLocked = false
    @State private var expandedCardId: String? = nil
    @State private var items: [PlaylistItem] = []

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                header
                if items.isEmpty {
                    emptyState
                } else {
                    cardList
                }
            }
            .frame(maxWidth: 600)
            .frame(maxWidth: .infinity)

            // FAB
            if !isLocked {
                Button { showAddSong = true } label: {
                    Image(systemName: "plus")
                        .font(.title2)
                        .foregroundColor(.textPrimary)
                        .frame(width: 56, height: 56)
                        .background(Color.padActive)
                        .clipShape(Circle())
                }
                .padding(24)
            }
        }
        .background(Color.darkBg)
        .sheet(isPresented: $showAddSong) {
            AddSongScreen(existingSong: nil)
                .onDisappear { refreshItems() }
        }
        .sheet(item: $editingSong) { song in
            AddSongScreen(existingSong: song)
                .onDisappear { refreshItems() }
        }
        .task { refreshItems() }
        .onChange(of: appState.allSongs) { _, _ in refreshItems() }
        .onChange(of: appState.allMixProjects) { _, _ in refreshItems() }
    }

    // MARK: - Header

    private var header: some View {
        ZStack {
            ScreenHeader(title: "PLAYLIST")

            HStack {
                Spacer()
                Button { isLocked.toggle(); if !isLocked { expandedCardId = nil } } label: {
                    Image(systemName: isLocked ? "lock.fill" : "lock.open")
                        .foregroundColor(isLocked ? .ledAmber : .textSecondary.opacity(0.5))
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
    }

    // MARK: - Empty state

    private var emptyState: some View {
        VStack(spacing: 8) {
            Spacer()
            Text("No songs saved")
                .font(.spaceGrotesk(.regular, size: 16))
                .foregroundColor(.textSecondary.opacity(0.5))
            Text("Tap + to add")
                .font(.spaceGrotesk(.regular, size: 13))
                .foregroundColor(.textSecondary.opacity(0.3))
            Spacer()
        }
    }

    // MARK: - Card list

    private var cardList: some View {
        ScrollView {
            LazyVStack(spacing: 8) {
                ForEach(items) { item in
                    switch item.kind {
                    case .song(let song):
                        SongCardView(
                            song: song,
                            isPlaying: appState.playingSongId == song.id,
                            isExpanded: expandedCardId == item.id && isLocked,
                            isLocked: isLocked,
                            packName: appState.allPacks.first(where: { $0.id == song.soundPackId })?.name ?? "Atmos",
                            onHeaderClick: { handleHeaderClick(item) },
                            onPlay: { playSong(song) },
                            onStop: { stopSong() },
                            onEdit: { editingSong = song },
                            onDelete: { deleteSong(song) }
                        )
                    case .mix(let project):
                        MixCardView(
                            project: project,
                            isPlaying: appState.playingMixId == project.id,
                            isExpanded: expandedCardId == item.id && isLocked,
                            onHeaderClick: { handleHeaderClick(item) }
                        )
                    }
                }
                .onMove { from, to in
                    items.move(fromOffsets: from, toOffset: to)
                    Task { await saveOrder() }
                }
            }
            .padding(.horizontal, 20)
        }
    }

    // MARK: - Actions

    private func refreshItems() {
        Task {
            await appState.refreshSongs()
            await appState.refreshMixProjects()
            let songItems = appState.allSongs.map { PlaylistItem(kind: .song($0)) }
            let mixItems = appState.allMixProjects.filter(\.inPlaylist).map { PlaylistItem(kind: .mix($0)) }
            items = (songItems + mixItems).sorted { a, b in
                a.sortOrder < b.sortOrder || (a.sortOrder == b.sortOrder && a.createdAt > b.createdAt)
            }
        }
    }

    private func handleHeaderClick(_ item: PlaylistItem) {
        guard isLocked else { return }
        if expandedCardId == item.id {
            expandedCardId = nil
        } else {
            expandedCardId = item.id
        }
    }

    private func playSong(_ song: SongItem) {
        let isDefaultPack = appState.allPacks.first(where: { $0.id == song.soundPackId })?.isDefault ?? true
        let resName: String
        if !isDefaultPack, let pad = appState.currentPads.first(where: { $0.note == song.note && $0.mode == song.padMode }) {
            appState.liveAudio.padTargetVolume = song.padVolume
            appState.liveAudio.startPadFromFile(filePath: pad.filePath, padChannel: song.padChannel)
        } else {
            resName = "pad_\(song.note)_\(song.padMode)"
            appState.liveAudio.padTargetVolume = song.padVolume
            appState.liveAudio.startPad(resName: resName, padChannel: song.padChannel)
        }
        if song.clickEnabled {
            appState.liveAudio.startClick(bpm: song.bpm, channel: song.clickChannel, volume: song.clickVolume, accents: song.accentList)
        }
        appState.playingSongId = song.id
        appState.playingNote = song.note
    }

    private func stopSong() {
        appState.liveAudio.stopPad()
        appState.liveAudio.stopClick()
        appState.playingSongId = nil
        appState.playingNote = nil
    }

    private func deleteSong(_ song: SongItem) {
        Task { await appState.deleteSong(song) }
    }

    private func saveOrder() async {
        for (idx, item) in items.enumerated() {
            if case .song(var song) = item.kind {
                song.sortOrder = idx
                await appState.updateSong(song)
            }
        }
    }
}

// MARK: - PlaylistItem

struct PlaylistItem: Identifiable {
    enum Kind {
        case song(SongItem)
        case mix(MixProjectItem)
    }
    let kind: Kind
    var sortOrder: Int {
        get {
            switch kind {
            case .song(let s): return s.sortOrder
            case .mix(let m): return m.sortOrder
            }
        }
        set {} // used in reorder
    }
    var createdAt: Int64 {
        switch kind {
        case .song(let s): return s.createdAt
        case .mix(let m): return m.createdAt
        }
    }
    var id: String {
        switch kind {
        case .song(let s): return "song_\(s.id)"
        case .mix(let m): return "mix_\(m.id)"
        }
    }
}

// MARK: - Song Card

private struct SongCardView: View {
    let song: SongItem
    let isPlaying: Bool
    let isExpanded: Bool
    let isLocked: Bool
    let packName: String
    let onHeaderClick: () -> Void
    let onPlay: () -> Void
    let onStop: () -> Void
    let onEdit: () -> Void
    let onDelete: () -> Void

    @EnvironmentObject var appState: AppState
    @State private var showDeleteConfirm = false

    var body: some View {
        VStack(spacing: 0) {
            // Header row
            Button(action: onHeaderClick) {
                HStack(spacing: 14) {
                    // Note badge
                    Text(noteLabels[song.note] ?? song.note.uppercased())
                        .font(.spaceGrotesk(.bold, size: 20))
                        .foregroundColor(.ledAmber)
                        .frame(width: 52, height: 52)
                        .background(Color.ledAmber.opacity(0.15))
                        .clipShape(RoundedRectangle(cornerRadius: 10))

                    VStack(alignment: .leading, spacing: 3) {
                        Text(song.name)
                            .font(.spaceGrotesk(.regular, size: 18))
                            .foregroundColor(.textPrimary)
                            .lineLimit(1)

                        let meta = [song.padMode.uppercased(),
                                    song.clickEnabled ? "\(song.bpm) BPM" : nil,
                                    packName].compactMap { $0 }.joined(separator: " • ")
                        Text(meta)
                            .font(.spaceGrotesk(.regular, size: 13))
                            .foregroundColor(.textSecondary.opacity(0.6))
                    }

                    Spacer()
                }
                .padding(.horizontal, 18)
                .padding(.vertical, 20)
            }
            .buttonStyle(.plain)

            // Expanded controls
            if isExpanded {
                VStack(spacing: 8) {
                    Divider().background(Color.padBorder.opacity(0.3))

                    // Transport
                    HStack(spacing: 16) {
                        Button(action: onPlay) {
                            Image(systemName: "play.fill")
                                .foregroundColor(isPlaying ? .ledAmber.opacity(0.3) : .ledAmber)
                                .font(.title3)
                        }
                        Button(action: onStop) {
                            Image(systemName: "stop.fill")
                                .foregroundColor(isPlaying ? Color(red: 1, green: 0.42, blue: 0.42) : .textSecondary.opacity(0.3))
                                .font(.title3)
                        }
                    }

                    // PAD volume slider
                    HStack(spacing: 6) {
                        Image(systemName: "pianokeys")
                            .font(.system(size: 14))
                            .foregroundColor(.ledAmber)
                            .frame(width: 16)
                        Text("PAD")
                            .font(.spaceGrotesk(.regular, size: 11))
                            .foregroundColor(.textSecondary)
                            .frame(width: 34, alignment: .leading)
                        Slider(value: $appState.padVolume, in: 0...1)
                            .tint(.ledAmber)
                            .onChange(of: appState.padVolume) { _, v in
                                appState.liveAudio.padTargetVolume = v
                            }
                    }

                    // CLICK volume slider (only if click enabled)
                    if song.clickEnabled {
                        HStack(spacing: 6) {
                            Image(systemName: "metronome")
                                .font(.system(size: 14))
                                .foregroundColor(.clickTeal)
                                .frame(width: 16)
                            Text("CLICK")
                                .font(.spaceGrotesk(.regular, size: 11))
                                .foregroundColor(.textSecondary)
                                .frame(width: 34, alignment: .leading)
                            Slider(value: $appState.clickVolume, in: 0...1)
                                .tint(.clickTeal)
                                .onChange(of: appState.clickVolume) { _, _ in
                                    appState.saveSettings()
                                }
                        }
                    }

                    // Edit / Delete row
                    if !isLocked || isExpanded {
                        HStack(spacing: 12) {
                            Button { onEdit() } label: {
                                HStack(spacing: 4) {
                                    Image(systemName: "pencil")
                                    Text("Edit")
                                }
                                .font(.spaceGrotesk(.regular, size: 13))
                                .foregroundColor(.ledAmber)
                                .frame(maxWidth: .infinity, minHeight: 36)
                                .background(Color.padIdle)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.padBorder.opacity(0.3), lineWidth: 1))
                            }

                            Button { showDeleteConfirm = true } label: {
                                HStack(spacing: 4) {
                                    Image(systemName: "trash")
                                    Text("Delete")
                                }
                                .font(.spaceGrotesk(.regular, size: 13))
                                .foregroundColor(Color(red: 1, green: 0.42, blue: 0.42))
                                .frame(maxWidth: .infinity, minHeight: 36)
                                .background(Color.padIdle)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.padBorder.opacity(0.3), lineWidth: 1))
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 14)
            }
        }
        .background(isPlaying ? Color.ledAmber.opacity(0.07) : Color.padIdle)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(isPlaying ? Color.ledAmber.opacity(0.7) : Color.padBorder.opacity(0.3), lineWidth: 1)
        )
        .animation(.easeInOut(duration: 0.2), value: isExpanded)
        .alert("Delete Song?", isPresented: $showDeleteConfirm) {
            Button("Delete", role: .destructive) { onDelete() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Delete \"\(song.name)\"? This cannot be undone.")
        }
    }
}

// MARK: - Mix Card (simplified)

private struct MixCardView: View {
    let project: MixProjectItem
    let isPlaying: Bool
    let isExpanded: Bool
    let onHeaderClick: () -> Void

    var body: some View {
        Button(action: onHeaderClick) {
            HStack(spacing: 14) {
                Image(systemName: "slider.horizontal.3")
                    .foregroundColor(.labsPurple)
                    .frame(width: 52, height: 52)
                    .background(Color.labsPurple.opacity(0.15))
                    .clipShape(RoundedRectangle(cornerRadius: 10))

                VStack(alignment: .leading, spacing: 3) {
                    Text(project.name)
                        .font(.spaceGrotesk(.regular, size: 18))
                        .foregroundColor(.textPrimary)
                        .lineLimit(1)
                    Text("Mix Project")
                        .font(.spaceGrotesk(.regular, size: 13))
                        .foregroundColor(.textSecondary.opacity(0.6))
                }

                Spacer()

                if isPlaying {
                    Image(systemName: "waveform")
                        .foregroundColor(.labsPurple)
                }
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 20)
        }
        .buttonStyle(.plain)
        .background(isPlaying ? Color.labsPurple.opacity(0.07) : Color.padIdle)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(isPlaying ? Color.labsPurple.opacity(0.7) : Color.padBorder.opacity(0.3), lineWidth: 1)
        )
    }
}
