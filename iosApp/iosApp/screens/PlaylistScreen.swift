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
    @State private var draggingIndex: Int? = nil
    @State private var dragOffsetY: CGFloat = 0
    @State private var itemHeights: [String: CGFloat] = [:]

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
                ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                    let isDragging = draggingIndex == index

                    cardContent(for: item, at: index)
                        .background(
                            GeometryReader { geo in
                                Color.clear
                                    .onAppear { itemHeights[item.id] = geo.size.height }
                                    .onChange(of: geo.size.height) { _, h in itemHeights[item.id] = h }
                            }
                        )
                        .offset(y: isDragging ? dragOffsetY : cardDisplacement(for: index))
                        .zIndex(isDragging ? 1 : 0)
                        .scaleEffect(isDragging ? 1.04 : 1.0)
                        .opacity(isDragging ? 0.93 : 1.0)
                        .animation(isDragging ? nil : .spring(response: 0.35, dampingFraction: 0.6), value: cardDisplacement(for: index))
                        .animation(.spring(response: 0.35, dampingFraction: 0.6), value: isDragging)
                        .simultaneousGesture(reorderGesture(index: index))
                }
            }
            .padding(.horizontal, 20)
        }
        .scrollDisabled(draggingIndex != nil)
    }

    @ViewBuilder
    private func cardContent(for item: PlaylistItem, at index: Int) -> some View {
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
                mixAudio: appState.mixAudio,
                isPlaying: appState.playingMixId == project.id,
                isExpanded: expandedCardId == item.id && isLocked,
                isLocked: isLocked,
                onHeaderClick: { handleHeaderClick(item) },
                onRemove: { removeMixFromPlaylist(project) }
            )
        }
    }

    // MARK: - Drag reorder

    private var averageHeight: CGFloat {
        guard !itemHeights.isEmpty else { return 92 }
        return itemHeights.values.reduce(0, +) / CGFloat(itemHeights.count)
    }

    private func cardDisplacement(for index: Int) -> CGFloat {
        guard let di = draggingIndex else { return 0 }
        let avgH = averageHeight
        guard avgH > 0 else { return 0 }
        let steps = Int(round(dragOffsetY / avgH))
        let ti = max(0, min(items.count - 1, di + steps))
        let draggedH = itemHeights[items.indices.contains(di) ? items[di].id : ""] ?? avgH
        let shift = draggedH + 8
        if di < ti && index > di && index <= ti { return -shift }
        if di > ti && index >= ti && index < di { return shift }
        return 0
    }

    private func reorderGesture(index: Int) -> some Gesture {
        LongPressGesture(minimumDuration: 0.3)
            .sequenced(before: DragGesture(minimumDistance: 0, coordinateSpace: .global))
            .onChanged { value in
                guard !isLocked else { return }
                switch value {
                case .second(true, let drag):
                    if let drag = drag {
                        if draggingIndex == nil {
                            draggingIndex = index
                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        }
                        let di = draggingIndex ?? index
                        let avgH = averageHeight
                        if avgH > 0 {
                            let minOff = -CGFloat(di) * (avgH + 8)
                            let maxOff = CGFloat(items.count - 1 - di) * (avgH + 8)
                            dragOffsetY = max(minOff, min(maxOff, drag.translation.height))
                        } else {
                            dragOffsetY = drag.translation.height
                        }
                    }
                default: break
                }
            }
            .onEnded { _ in
                guard let di = draggingIndex else { return }
                let avgH = averageHeight
                var ti = di
                if avgH > 0 {
                    let steps = Int(round(dragOffsetY / avgH))
                    ti = max(0, min(items.count - 1, di + steps))
                }
                withAnimation(.spring(response: 0.35, dampingFraction: 0.6)) {
                    draggingIndex = nil
                    dragOffsetY = 0
                }
                if di != ti {
                    items.insert(items.remove(at: di), at: ti)
                }
                Task { await saveOrder() }
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
            switch item.kind {
            case .song(var song):
                song.sortOrder = idx
                await appState.updateSong(song)
            case .mix(let project):
                await appState.updateMixProjectSortOrder(id: project.id, sortOrder: Int32(idx))
            }
        }
    }

    private func removeMixFromPlaylist(_ project: MixProjectItem) {
        appState.mixAudio.stopAll()
        appState.playingMixId = nil
        Task { await appState.toggleMixProjectInPlaylist(id: project.id) }
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
    @State private var offsetX: CGFloat = 0
    private let revealWidth: CGFloat = 140

    var body: some View {
        ZStack(alignment: .trailing) {
            // Swipe-reveal background (edit + delete)
            if offsetX < -1 && !isLocked {
                HStack(spacing: 0) {
                    Spacer()
                    Button {
                        withAnimation(.easeOut(duration: 0.2)) { offsetX = 0 }
                        onEdit()
                    } label: {
                        Image(systemName: "pencil")
                            .font(.system(size: 22))
                            .foregroundColor(.ledAmber)
                            .frame(width: 70, maxHeight: .infinity)
                    }
                    .background(Color.padIdle)

                    Button {
                        withAnimation(.easeOut(duration: 0.2)) { offsetX = 0 }
                        showDeleteConfirm = true
                    } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 22))
                            .foregroundColor(Color(red: 1, green: 0.42, blue: 0.42))
                            .frame(width: 70, maxHeight: .infinity)
                    }
                    .background(Color.padIdle)
                }
                .background(Color.darkBg)
            }

            // Foreground card
            VStack(spacing: 0) {
                // Header
                Button(action: {
                    if offsetX < -1 {
                        withAnimation(.easeOut(duration: 0.2)) { offsetX = 0 }
                    } else {
                        onHeaderClick()
                    }
                }) {
                    HStack(spacing: 14) {
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

                // Expanded controls (locked mode)
                if isExpanded {
                    VStack(spacing: 8) {
                        Rectangle().fill(Color.padBorder.opacity(0.3)).frame(height: 1)

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

                        // PAD volume
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

                        // CLICK volume
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
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 14)
                }
            }
            .background(isPlaying ? Color.ledAmber.opacity(0.07) : Color.padIdle)
            .offset(x: offsetX)
        }
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(isPlaying ? Color.ledAmber.opacity(0.7) : Color.padBorder.opacity(0.3), lineWidth: 1)
        )
        .animation(.easeInOut(duration: 0.2), value: isExpanded)
        .gesture(
            DragGesture(minimumDistance: 20)
                .onChanged { value in
                    guard !isLocked else { return }
                    let w = value.translation.width
                    guard abs(w) > abs(value.translation.height) else { return }
                    withAnimation(.interactiveSpring()) {
                        offsetX = min(0, max(-revealWidth, w))
                    }
                }
                .onEnded { _ in
                    guard !isLocked else { return }
                    withAnimation(.easeOut(duration: 0.2)) {
                        offsetX = offsetX < -revealWidth / 2 ? -revealWidth : 0
                    }
                }
        )
        .onChange(of: isLocked) { _, _ in
            withAnimation(.easeOut(duration: 0.2)) { offsetX = 0 }
        }
        .sheet(isPresented: $showDeleteConfirm) {
            DeleteConfirmSheet(
                title: "Delete \"\(song.name)\"?",
                message: "This action cannot be undone.",
                actionLabel: "DELETE",
                onAction: { onDelete(); showDeleteConfirm = false },
                onCancel: { showDeleteConfirm = false }
            )
        }
    }
}

// MARK: - Mix Card

private struct MixCardView: View {
    let project: MixProjectItem
    @ObservedObject var mixAudio: MixAudioPlayerIos
    let isPlaying: Bool
    let isExpanded: Bool
    let isLocked: Bool
    let onHeaderClick: () -> Void
    let onRemove: () -> Void

    @EnvironmentObject var appState: AppState
    @State private var tracks: [MixTrackItem] = []
    @State private var showDeleteConfirm = false
    @State private var offsetX: CGFloat = 0
    @State private var positionMs: Int64 = 0
    @State private var durationMs: Int64 = 0
    @State private var isSeeking = false
    @State private var seekProgress: Float = 0

    private let revealWidth: CGFloat = 70
    private let timer = Timer.publish(every: 0.25, on: .main, in: .common).autoconnect()

    private var hasCustom: Bool { tracks.contains { $0.trackType == "custom" } }
    private var anyPlaying: Bool { mixAudio.isAnyPlaying() }

    var body: some View {
        ZStack(alignment: .trailing) {
            // Swipe background - delete only
            if offsetX < -1 && !isLocked {
                HStack(spacing: 0) {
                    Spacer()
                    Button {
                        withAnimation(.easeOut(duration: 0.2)) { offsetX = 0 }
                        showDeleteConfirm = true
                    } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 22))
                            .foregroundColor(Color(red: 1, green: 0.42, blue: 0.42))
                            .frame(width: 70, maxHeight: .infinity)
                    }
                    .background(Color.padIdle)
                }
                .background(Color.darkBg)
            }

            // Foreground card
            VStack(spacing: 0) {
                // Header
                Button(action: {
                    if offsetX < -1 {
                        withAnimation(.easeOut(duration: 0.2)) { offsetX = 0 }
                    } else {
                        onHeaderClick()
                    }
                }) {
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
                            Text("\(tracks.count) tracks · Mix")
                                .font(.spaceGrotesk(.regular, size: 13))
                                .foregroundColor(.textSecondary.opacity(0.6))
                        }

                        Spacer()

                        if isPlaying || mixAudio.isPaused {
                            Image(systemName: "waveform")
                                .foregroundColor(.labsPurple)
                        }
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 20)
                }
                .buttonStyle(.plain)

                // Expanded controls
                if isExpanded {
                    VStack(spacing: 6) {
                        Rectangle().fill(Color.padBorder.opacity(0.3)).frame(height: 1)

                        // Seek bar (custom tracks only)
                        if hasCustom {
                            HStack {
                                Text(formatTime(isSeeking ? Int64(seekProgress * Float(durationMs)) : positionMs))
                                    .font(.spaceGrotesk(.regular, size: 10))
                                    .foregroundColor(.textSecondary)
                                    .frame(width: 32)

                                Slider(
                                    value: $seekProgress,
                                    in: 0...1,
                                    onEditingChanged: { editing in
                                        if editing {
                                            isSeeking = true
                                        } else {
                                            let targetMs = Int64(seekProgress * Float(durationMs))
                                            mixAudio.seekAllCustom(positionMs: targetMs)
                                            positionMs = targetMs
                                            isSeeking = false
                                        }
                                    }
                                )
                                .tint(.labsPurple)
                                .frame(height: 20)

                                Text(formatTime(durationMs))
                                    .font(.spaceGrotesk(.regular, size: 10))
                                    .foregroundColor(.textSecondary)
                                    .frame(width: 32, alignment: .trailing)
                            }
                        }

                        // Transport controls
                        HStack(spacing: 12) {
                            // Play / Resume
                            Button {
                                if mixAudio.isPaused {
                                    mixAudio.resumeAll(tracks)
                                } else if !anyPlaying {
                                    mixAudio.startAll(tracks)
                                    appState.playingMixId = project.id
                                }
                            } label: {
                                Image(systemName: "play.fill")
                                    .font(.system(size: 26))
                                    .foregroundColor((!anyPlaying || mixAudio.isPaused) ? .labsPurple : .labsPurple.opacity(0.3))
                                    .frame(width: 40, height: 40)
                            }

                            // Pause (custom tracks only)
                            if hasCustom {
                                Button {
                                    mixAudio.pauseAll()
                                } label: {
                                    Image(systemName: "pause.fill")
                                        .font(.system(size: 26))
                                        .foregroundColor((anyPlaying && !mixAudio.isPaused) ? .labsPurple : .textSecondary.opacity(0.3))
                                        .frame(width: 40, height: 40)
                                }
                            }

                            // Stop
                            Button {
                                mixAudio.stopAll()
                                appState.playingMixId = nil
                            } label: {
                                Image(systemName: "stop.fill")
                                    .font(.system(size: 26))
                                    .foregroundColor((anyPlaying || mixAudio.isPaused) ? Color(red: 1, green: 0.42, blue: 0.42) : .textSecondary.opacity(0.3))
                                    .frame(width: 40, height: 40)
                            }
                        }

                        // Per-track sliders
                        ForEach(tracks) { track in
                            MixTrackSliderView(
                                track: track,
                                projectId: project.id,
                                isTrackPlaying: mixAudio.trackPlaying[track.id] == true,
                                isMuted: mixAudio.mutedTracks[track.id] == true,
                                onVolumeChange: { vol in
                                    mixAudio.setTrackVolume(track.id, volume: vol)
                                    Task { await appState.updateMixTrackVolume(trackId: track.id, projectId: project.id, volume: vol) }
                                },
                                onMuteToggle: {
                                    if mixAudio.mutedTracks[track.id] == true {
                                        mixAudio.unmuteTrack(track.id)
                                    } else {
                                        mixAudio.muteTrack(track.id)
                                    }
                                }
                            )
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 14)
                }
            }
            .background((isPlaying || mixAudio.isPaused) ? Color.labsPurple.opacity(0.07) : Color.padIdle)
            .offset(x: offsetX)
        }
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke((isPlaying || mixAudio.isPaused) ? Color.labsPurple.opacity(0.8) : Color.padBorder.opacity(0.3), lineWidth: 1)
        )
        .animation(.easeInOut(duration: 0.2), value: isExpanded)
        .gesture(
            DragGesture(minimumDistance: 20)
                .onChanged { value in
                    guard !isLocked else { return }
                    let w = value.translation.width
                    guard abs(w) > abs(value.translation.height) else { return }
                    withAnimation(.interactiveSpring()) {
                        offsetX = min(0, max(-revealWidth, w))
                    }
                }
                .onEnded { _ in
                    guard !isLocked else { return }
                    withAnimation(.easeOut(duration: 0.2)) {
                        offsetX = offsetX < -revealWidth / 2 ? -revealWidth : 0
                    }
                }
        )
        .onChange(of: isLocked) { _, _ in
            withAnimation(.easeOut(duration: 0.2)) { offsetX = 0 }
        }
        .task { tracks = await appState.getTracksForProject(projectId: project.id) }
        .onReceive(timer) { _ in
            guard isExpanded && (anyPlaying || mixAudio.isPaused) && hasCustom && !isSeeking else { return }
            positionMs = mixAudio.getCustomPositionMs()
            durationMs = mixAudio.getCustomDurationMs()
            if durationMs > 0 {
                seekProgress = Float(positionMs) / Float(durationMs)
            }
        }
        .sheet(isPresented: $showDeleteConfirm) {
            DeleteConfirmSheet(
                title: "Remove \"\(project.name)\" from playlist?",
                message: "The mix will still be available in Mix Studio.",
                actionLabel: "REMOVE",
                onAction: { onRemove(); showDeleteConfirm = false },
                onCancel: { showDeleteConfirm = false }
            )
        }
    }

    private func formatTime(_ ms: Int64) -> String {
        let totalSec = max(0, ms / 1000)
        let m = totalSec / 60
        let s = totalSec % 60
        return String(format: "%d:%02d", m, s)
    }
}

// MARK: - Mix Track Slider

private struct MixTrackSliderView: View {
    let track: MixTrackItem
    let projectId: Int64
    let isTrackPlaying: Bool
    let isMuted: Bool
    let onVolumeChange: (Float) -> Void
    let onMuteToggle: () -> Void

    @State private var vol: Float

    init(track: MixTrackItem, projectId: Int64, isTrackPlaying: Bool, isMuted: Bool,
         onVolumeChange: @escaping (Float) -> Void, onMuteToggle: @escaping () -> Void) {
        self.track = track
        self.projectId = projectId
        self.isTrackPlaying = isTrackPlaying
        self.isMuted = isMuted
        self.onVolumeChange = onVolumeChange
        self.onMuteToggle = onMuteToggle
        _vol = State(initialValue: track.volume)
    }

    private var icon: String {
        switch track.trackType {
        case "pad": return "pianokeys"
        case "click": return "metronome"
        default: return "doc.fill"
        }
    }

    private var color: Color {
        switch track.trackType {
        case "pad": return .ledAmber
        case "click": return .clickTeal
        default: return .labsPurple
        }
    }

    var body: some View {
        HStack(spacing: 8) {
            // Mute toggle
            Button(action: onMuteToggle) {
                Image(systemName: isMuted ? "speaker.slash.fill" : icon)
                    .font(.system(size: 14))
                    .foregroundColor(isMuted ? .textSecondary.opacity(0.4) : (isTrackPlaying ? color : color.opacity(0.3)))
                    .frame(width: 18, height: 18)
            }

            // Label
            Text(track.label)
                .font(.spaceGrotesk(.regular, size: 11))
                .foregroundColor(.textSecondary)
                .lineLimit(1)
                .frame(width: 56, alignment: .leading)

            // Volume slider
            Slider(value: $vol, in: 0...1)
                .tint(color)
                .frame(height: 20)
                .onChange(of: vol) { _, v in onVolumeChange(v) }
        }
        .frame(height: 32)
        .opacity(isMuted ? 0.45 : 1)
    }
}

// MARK: - Delete Confirm Sheet

private struct DeleteConfirmSheet: View {
    let title: String
    let message: String
    let actionLabel: String
    let onAction: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Text(title)
                .font(.spaceGrotesk(.bold, size: 18))
                .foregroundColor(.textPrimary)
                .multilineTextAlignment(.center)

            Spacer().frame(height: 8)

            Text(message)
                .font(.spaceGrotesk(.regular, size: 14))
                .foregroundColor(.textSecondary)

            Spacer().frame(height: 24)

            HStack(spacing: 12) {
                Button(action: onCancel) {
                    Text("CANCEL")
                        .font(.spaceGrotesk(.regular, size: 13))
                        .foregroundColor(.textSecondary)
                        .frame(maxWidth: .infinity, minHeight: 40)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color.textSecondary, lineWidth: 1)
                        )
                }

                Button(action: onAction) {
                    Text(actionLabel)
                        .font(.spaceGrotesk(.bold, size: 13))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, minHeight: 40)
                        .background(Color(red: 1, green: 0.42, blue: 0.42))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
            }
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 32)
        .presentationDetents([.height(180)])
        .presentationDragIndicator(.hidden)
        .presentationBackground(Color.padIdle)
    }
}
