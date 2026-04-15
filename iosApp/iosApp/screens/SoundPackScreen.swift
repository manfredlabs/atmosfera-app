import SwiftUI
import UniformTypeIdentifiers

private let allNotes = ["c","cs","d","ds","e","f","fs","g","gs","a","as","b"]
private let noteLabels = ["C","C#","D","D#","E","F","F#","G","G#","A","A#","B"]
private let allModes = ["neu","maj","min"]

struct SoundPackScreen: View {
    @EnvironmentObject var appState: AppState
    let pack: SoundPackItem

    @State private var pads: [SoundPadItem] = []
    @State private var selectedNote = ""
    @State private var selectedMode = ""
    @State private var showFilePicker = false

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                // Grid 4×3 (same as HomeScreen)
                let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 3)
                LazyVGrid(columns: columns, spacing: 8) {
                    ForEach(Array(allNotes.enumerated()), id: \.offset) { idx, note in
                        ForEach(allModes, id: \.self) { mode in
                            let key = "\(note):\(mode)"
                            let assigned = pads.first { $0.note == note && $0.mode == mode }
                            Button {
                                selectedNote = note
                                selectedMode = mode
                                showFilePicker = true
                            } label: {
                                VStack(spacing: 2) {
                                    Text(noteLabels[idx]).font(.subheadline.bold())
                                    Text(mode).font(.caption2)
                                    Image(systemName: assigned != nil ? "checkmark.circle.fill" : "plus.circle")
                                        .font(.caption)
                                        .foregroundStyle(assigned != nil ? .green : .secondary)
                                }
                                .frame(maxWidth: .infinity)
                                .frame(height: 64)
                                .background(Color(.secondarySystemBackground))
                                .cornerRadius(10)
                            }
                        }
                    }
                }
                .padding()
            }
            .frame(maxWidth: 600)
        }
        .navigationTitle(pack.name)
        .fileImporter(isPresented: $showFilePicker,
                      allowedContentTypes: [.audio]) { result in
            if case .success(let url) = result {
                assignPad(note: selectedNote, mode: selectedMode, url: url)
            }
        }
        .task {
            await appState.refreshPads(packId: pack.id)
            pads = appState.currentPads
        }
    }

    private func assignPad(note: String, mode: String, url: URL) {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("soundpacks/\(pack.id)")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let dest = dir.appendingPathComponent("\(note)_\(mode)_\(url.lastPathComponent)")
        // Copy if not already in our documents directory
        if url.startAccessingSecurityScopedResource() {
            defer { url.stopAccessingSecurityScopedResource() }
            try? FileManager.default.copyItem(at: url, to: dest)
        } else {
            try? FileManager.default.copyItem(at: url, to: dest)
        }
        Task {
            await appState.assignPad(packId: pack.id, note: note, mode: mode, filePath: dest.path)
            pads = appState.currentPads
        }
    }
}
