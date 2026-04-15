import SwiftUI

private let allNotes = ["c","cs","d","ds","e","f","fs","g","gs","a","as","b"]
private let noteLabels = ["C","C#","D","D#","E","F","F#","G","G#","A","A#","B"]
private let allModes = ["neu","maj","min"]
private let channelOptions = ["mono","left","right"]

struct AddSongScreen: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) var dismiss

    let existingSong: SongItem?

    @State private var name = ""
    @State private var selectedNoteIdx = 0
    @State private var selectedModeIdx = 1
    @State private var bpm = 90
    @State private var accents = [1,0,0,0]
    @State private var padEnabled = true
    @State private var clickEnabled = true
    @State private var padVolume: Float = 0.5
    @State private var clickVolume: Float = 0.5
    @State private var padChannelIdx = 0
    @State private var clickChannelIdx = 0

    var body: some View {
        NavigationStack {
            Form {
                Section("Song") {
                    TextField("Name", text: $name)
                    Picker("Note", selection: $selectedNoteIdx) {
                        ForEach(Array(noteLabels.enumerated()), id: \.offset) { idx, lbl in
                            Text(lbl).tag(idx)
                        }
                    }
                    Picker("Mode", selection: $selectedModeIdx) {
                        ForEach(Array(allModes.enumerated()), id: \.offset) { idx, m in
                            Text(m.capitalized).tag(idx)
                        }
                    }
                }
                Section("Tempo") {
                    Stepper("BPM: \(bpm)", value: $bpm, in: 40...240)
                    // Accent pattern
                    HStack {
                        Text("Accents")
                        Spacer()
                        ForEach(Array(accents.enumerated()), id: \.offset) { idx, val in
                            Button {
                                accents[idx] = (val + 1) % 3
                            } label: {
                                Circle()
                                    .fill(val == 1 ? Color.accentColor : val == 2 ? Color.gray.opacity(0.3) : Color(.secondarySystemBackground))
                                    .frame(width: 28, height: 28)
                                    .overlay(Circle().stroke(Color.accentColor, lineWidth: 1))
                            }
                        }
                    }
                }
                Section("Pad") {
                    Toggle("Enabled", isOn: $padEnabled)
                    HStack {
                        Text("Volume")
                        Slider(value: $padVolume, in: 0...1)
                    }
                    Picker("Channel", selection: $padChannelIdx) {
                        ForEach(Array(channelOptions.enumerated()), id: \.offset) { idx, ch in
                            Text(ch.capitalized).tag(idx)
                        }
                    }
                    .pickerStyle(.segmented)
                }
                Section("Click") {
                    Toggle("Enabled", isOn: $clickEnabled)
                    HStack {
                        Text("Volume")
                        Slider(value: $clickVolume, in: 0...1)
                    }
                    Picker("Channel", selection: $clickChannelIdx) {
                        ForEach(Array(channelOptions.enumerated()), id: \.offset) { idx, ch in
                            Text(ch.capitalized).tag(idx)
                        }
                    }
                    .pickerStyle(.segmented)
                }
            }
            .frame(maxWidth: 600)
            .navigationTitle(existingSong == nil ? "Add Song" : "Edit Song")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .onAppear { populateFromExisting() }
        }
    }

    private func populateFromExisting() {
        guard let s = existingSong else { return }
        name = s.name
        selectedNoteIdx = allNotes.firstIndex(of: s.note) ?? 0
        selectedModeIdx = allModes.firstIndex(of: s.padMode) ?? 1
        bpm = s.bpm
        accents = s.accentList
        padEnabled = s.padEnabled
        clickEnabled = s.clickEnabled
        padVolume = s.padVolume
        clickVolume = s.clickVolume
        padChannelIdx = channelOptions.firstIndex(of: s.padChannel) ?? 0
        clickChannelIdx = channelOptions.firstIndex(of: s.clickChannel) ?? 0
    }

    private func save() {
        let song = SongItem(
            id: existingSong?.id ?? 0,
            name: name.trimmingCharacters(in: .whitespaces),
            note: allNotes[selectedNoteIdx],
            isMajor: selectedModeIdx == 1,
            bpm: bpm,
            accents: accents.map(String.init).joined(separator: ","),
            padEnabled: padEnabled,
            clickEnabled: clickEnabled,
            createdAt: existingSong?.createdAt ?? Int64(Date().timeIntervalSince1970 * 1000),
            sortOrder: existingSong?.sortOrder ?? 0,
            padMode: allModes[selectedModeIdx],
            soundPackId: existingSong?.soundPackId ?? -1,
            padVolume: padVolume,
            padChannel: channelOptions[padChannelIdx],
            clickVolume: clickVolume,
            clickChannel: channelOptions[clickChannelIdx]
        )
        Task {
            if existingSong != nil { await appState.updateSong(song) }
            else { await appState.insertSong(song) }
        }
        dismiss()
    }
}
