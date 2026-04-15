import SwiftUI
import UniformTypeIdentifiers

private let allNotes = ["c","cs","d","ds","e","f","fs","g","gs","a","as","b"]
private let noteLabels = ["C","C#","D","D#","E","F","F#","G","G#","A","A#","B"]
private let allModes = ["neu","maj","min"]

struct SoundPackScreen: View {
    @EnvironmentObject var appState: AppState
    let pack: SoundPackItem

    @State private var pads: [SoundPadItem] = []
    @State private var padMode = "maj"
    @State private var selectedNote = ""
    @State private var selectedMode = ""
    @State private var showFilePicker = false

    var body: some View {
        ZStack {
            Color.darkBg.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 20) {
                    ScreenHeader(title: "SOUND PACK")
                        .padding(.top, 8)

                    // Name (read-only display)
                    VStack(alignment: .leading, spacing: 8) {
                        SectionHeader(title: "NAME")
                        Text(pack.name)
                            .font(.spaceGrotesk(.regular, size: 18))
                            .foregroundColor(.textPrimary)
                            .padding(.horizontal, 16)
                            .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
                            .background(Color.padIdle)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(Color.padBorder.opacity(0.5), lineWidth: 1)
                            )
                    }

                    // Mode pill
                    VStack(alignment: .leading, spacing: 10) {
                        SectionHeader(title: "KEY")
                        PillSelector(
                            options: ["NEU", "MAJ", "MIN"],
                            selected: Binding(
                                get: { padMode.uppercased() },
                                set: { padMode = $0.lowercased() }
                            )
                        )

                        // Note grid 4×3
                        noteGrid
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
                .frame(maxWidth: 600)
            }
        }
        .navigationBarHidden(true)
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
        .onChange(of: appState.currentPads) { _, new in pads = new }
    }

    // MARK: - Note Grid

    private var noteGrid: some View {
        let chunked = stride(from: 0, to: allNotes.count, by: 3).map {
            Array(Array(allNotes.enumerated())[$0..<min($0+3, allNotes.count)])
        }
        return VStack(spacing: 6) {
            ForEach(chunked.indices, id: \.self) { rowIdx in
                HStack(spacing: 6) {
                    ForEach(chunked[rowIdx], id: \.offset) { idx, note in
                        let hasPad = pads.contains { $0.note == note && $0.mode == padMode }
                        Button {
                            selectedNote = note
                            selectedMode = padMode
                            showFilePicker = true
                        } label: {
                            VStack(spacing: 2) {
                                Text(noteLabels[idx])
                                    .font(.spaceGrotesk(hasPad ? .bold : .regular, size: 15))
                                    .foregroundColor(hasPad ? .ledAmber : .textSecondary)
                                Image(systemName: hasPad ? "checkmark.circle.fill" : "plus.circle")
                                    .font(.system(size: 10))
                                    .foregroundColor(hasPad ? .ledAmber.opacity(0.6) : .textSecondary.opacity(0.3))
                            }
                            .frame(maxWidth: .infinity, minHeight: 54)
                            .background(hasPad ? Color.ledAmber.opacity(0.15) : Color.padIdle)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(hasPad ? Color.ledAmber.opacity(0.5) : Color.padBorder.opacity(0.3), lineWidth: 1)
                            )
                        }
                    }
                }
            }
        }
    }

    // MARK: - File assignment

    private func assignPad(note: String, mode: String, url: URL) {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("soundpacks/\(pack.id)")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let dest = dir.appendingPathComponent("\(note)_\(mode)_\(url.lastPathComponent)")
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
