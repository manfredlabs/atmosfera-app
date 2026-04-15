import SwiftUI

struct PlaylistScreen: View {
    @EnvironmentObject var appState: AppState
    @State private var showAddSong = false
    @State private var editingSong: SongItem? = nil
    @State private var songs: [SongItem] = []

    var body: some View {
        NavigationStack {
            songList
                .frame(maxWidth: 600)
                .navigationTitle("Playlist")
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button { showAddSong = true } label: { Image(systemName: "plus") }
                    }
                }
                .sheet(isPresented: $showAddSong) {
                    AddSongScreen(existingSong: nil)
                        .onDisappear { Task { await appState.refreshSongs(); songs = appState.allSongs } }
                }
                .sheet(item: $editingSong) { song in
                    AddSongScreen(existingSong: song)
                        .onDisappear { Task { await appState.refreshSongs(); songs = appState.allSongs } }
                }
                .task { await appState.refreshSongs(); songs = appState.allSongs }
                .onChange(of: appState.allSongs) { _, new in songs = new }
        }
    }

    @ViewBuilder
    private var songList: some View {
        if songs.isEmpty {
            ContentUnavailableView("No songs", systemImage: "music.note.list",
                description: Text("Tap + to add a song to your playlist."))
        } else {
            List {
                ForEach(songs) { song in
                    SongCard(song: song, isPlaying: appState.playingSongId == song.id)
                        .contentShape(Rectangle())
                        .onTapGesture { editingSong = song }
                }
                .onMove { from, to in
                    songs.move(fromOffsets: from, toOffset: to)
                    let reordered = songs.enumerated().map { idx, s in
                        var copy = s
                        copy.sortOrder = idx
                        return copy
                    }
                    Task { await appState.updateSongOrder(reordered) }
                }
                .onDelete { indexSet in
                    let toDelete = indexSet.map { songs[$0] }
                    songs.remove(atOffsets: indexSet)
                    Task { for s in toDelete { await appState.deleteSong(s) } }
                }
            }
            .environment(\.editMode, .constant(.active))
        }
    }
}

private struct SongCard: View {
    let song: SongItem
    let isPlaying: Bool

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(song.name).font(.headline)
                HStack(spacing: 8) {
                    Text(song.note.uppercased() + " " + song.padMode)
                        .font(.caption).foregroundStyle(.secondary)
                    Text("\(song.bpm) BPM")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            Spacer()
            HStack(spacing: 4) {
                if song.padEnabled  { Image(systemName: "waveform").font(.caption).foregroundStyle(.secondary) }
                if song.clickEnabled { Image(systemName: "metronome").font(.caption).foregroundStyle(.secondary) }
            }
        }
        .padding(.vertical, 4)
        .listRowBackground(isPlaying ? Color.accentColor.opacity(0.15) : nil)
    }
}
