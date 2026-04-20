import SwiftUI
import UniformTypeIdentifiers

private let allNotes = ["c","cs","d","ds","e","f","fs","g","gs","a","as","b"]
private let noteLabels = ["C","C#","D","D#","E","F","F#","G","G#","A","A#","B"]
private let allModes = ["neu","maj","min"]

struct SoundPackScreen: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) var dismiss
    let pack: SoundPackItem?  // nil = new pack

    @State private var pads: [SoundPadItem] = []
    @State private var padMode = "maj"
    @State private var selectedNotes: Set<String> = []
    @State private var noteFiles: [String: URL] = [:]
    @State private var showFilePicker = false
    @State private var pendingFileNote = ""
    @State private var packName = ""
    @State private var packDescription = ""
    @State private var isProcessing = false
    @State private var createdPackId: Int64? = nil
    @FocusState private var nameFieldFocused: Bool
    @FocusState private var descFieldFocused: Bool

    private var isNewPack: Bool { pack == nil }
    private var resolvedPackId: Int64? { createdPackId ?? pack?.id }

    private var allFilesSelected: Bool {
        !selectedNotes.isEmpty && selectedNotes.allSatisfy { noteFiles[$0] != nil }
    }

    private var metadataChanged: Bool {
        guard let pack = pack else { return true }
        return packName.trimmingCharacters(in: .whitespaces) != pack.name ||
               packDescription.trimmingCharacters(in: .whitespaces) != pack.description
    }

    private var canSave: Bool {
        !packName.trimmingCharacters(in: .whitespaces).isEmpty &&
        (allFilesSelected || metadataChanged) && !isProcessing
    }

    var body: some View {
        ZStack {
            Color.darkBg.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 20) {
                    // Header with back button
                    ZStack {
                        HStack {
                            Button { dismiss() } label: {
                                Image(systemName: "chevron.left")
                                    .font(.system(size: 18, weight: .medium))
                                    .foregroundColor(.textSecondary)
                                    .frame(width: 44, height: 44)
                            }
                            Spacer()
                        }
                        Text("SOUND PACK")
                            .font(.spaceGrotesk(.light, size: 22))
                            .foregroundColor(.textPrimary)
                            .tracking(2)
                    }
                    .padding(.top, 8)

                    // NAME
                    VStack(alignment: .leading, spacing: 8) {
                        SectionHeader(title: "NAME", size: 12)
                        TextField("e.g. Warm Pads", text: $packName)
                            .font(.spaceGrotesk(.regular, size: 18))
                            .foregroundColor(.textPrimary)
                            .padding(.horizontal, 16)
                            .frame(height: 48)
                            .background(Color.padIdle)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(nameFieldFocused ? Color.ledAmber : Color.padBorder.opacity(0.5), lineWidth: 1)
                            )
                            .focused($nameFieldFocused)
                            .autocorrectionDisabled()
                            .onChange(of: packName) { _, newName in
                                if newName.count > 20 { packName = String(newName.prefix(20)) }
                            }
                    }

                    // DESCRIPTION
                    VStack(alignment: .leading, spacing: 8) {
                        SectionHeader(title: "DESCRIPTION", size: 12)
                        TextField("", text: $packDescription, prompt: Text("e.g. Warm ambient pads").foregroundColor(.textSecondary.opacity(0.4)))
                            .font(.spaceGrotesk(.regular, size: 16))
                            .foregroundColor(.textPrimary)
                            .padding(.horizontal, 16)
                            .frame(height: 48)
                            .background(Color.padIdle)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(descFieldFocused ? Color.ledAmber : Color.padBorder.opacity(0.5), lineWidth: 1)
                            )
                            .focused($descFieldFocused)
                            .autocorrectionDisabled()
                    }

                    // KEY
                    VStack(alignment: .leading, spacing: 10) {
                        SectionHeader(title: "KEY", size: 12)
                        PillSelector(
                            options: ["NEU", "MAJ", "MIN"],
                            selected: Binding(
                                get: { padMode.uppercased() },
                                set: {
                                    padMode = $0.lowercased()
                                    selectedNotes = []
                                    noteFiles = [:]
                                }
                            )
                        )

                        // Note grid 4×3
                        noteGrid
                    }

                    // FILES (shown when notes selected)
                    if !selectedNotes.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            SectionHeader(title: "FILES", size: 12)
                            let orderedNotes = allNotes.filter { selectedNotes.contains($0) }
                            ForEach(orderedNotes, id: \.self) { note in
                                let idx = allNotes.firstIndex(of: note)!
                                let fileUrl = noteFiles[note]
                                HStack(spacing: 10) {
                                    Text(noteLabels[idx])
                                        .font(.spaceGrotesk(.bold, size: 15))
                                        .foregroundColor(.ledAmber)
                                        .frame(width: 36)

                                    Button {
                                        pendingFileNote = note
                                        showFilePicker = true
                                    } label: {
                                        HStack {
                                            Text(fileUrl?.lastPathComponent ?? "Select file...")
                                                .font(.spaceGrotesk(.regular, size: 13))
                                                .foregroundColor(fileUrl != nil ? .textPrimary : .textSecondary.opacity(0.4))
                                                .lineLimit(1)
                                            Spacer()
                                        }
                                        .padding(.horizontal, 10)
                                        .frame(maxWidth: .infinity, minHeight: 44)
                                        .background(Color.padIdle)
                                        .clipShape(RoundedRectangle(cornerRadius: 8))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 8)
                                                .stroke(fileUrl != nil ? Color.padActive.opacity(0.5) : Color.padBorder.opacity(0.3), lineWidth: 1)
                                        )
                                    }

                                    Button {
                                        noteFiles.removeValue(forKey: note)
                                        selectedNotes.remove(note)
                                    } label: {
                                        Image(systemName: "xmark")
                                            .font(.system(size: 16))
                                            .foregroundColor(.textSecondary.opacity(0.5))
                                            .frame(width: 32, height: 32)
                                    }
                                }
                                .frame(height: 44)
                            }
                        }
                    }

                    Spacer().frame(height: 8)

                    // Processing indicator
                    if isProcessing {
                        HStack(spacing: 12) {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .padActive))
                                .scaleEffect(0.8)
                            Text("Importing...")
                                .font(.spaceGrotesk(.regular, size: 13))
                                .foregroundColor(.textSecondary)
                        }
                    }

                    // CANCEL / SAVE buttons
                    HStack(spacing: 12) {
                        Button { dismiss() } label: {
                            Text("CANCEL")
                                .font(.spaceGrotesk(.bold, size: 14))
                                .tracking(2)
                                .foregroundColor(.textSecondary)
                                .frame(maxWidth: .infinity, minHeight: 50)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 10)
                                        .stroke(Color.padBorder.opacity(0.5), lineWidth: 1)
                                )
                        }
                        Button { savePack() } label: {
                            Text("SAVE")
                                .font(.spaceGrotesk(.bold, size: 14))
                                .tracking(2)
                                .foregroundColor(canSave ? .darkBg : .textSecondary)
                                .frame(maxWidth: .infinity, minHeight: 50)
                                .background(canSave ? Color.ledAmber : Color.padActive)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                        }
                        .disabled(!canSave)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
                .frame(maxWidth: 600)
            }
        }
        .navigationBarHidden(true)
        .onAppear { appState.showBottomBar = false }
        .onDisappear { appState.showBottomBar = true }
        .fileImporter(isPresented: $showFilePicker,
                      allowedContentTypes: [.audio]) { result in
            if case .success(let url) = result {
                noteFiles[pendingFileNote] = url
            }
        }
        .task {
            if let pack = pack {
                // Edit mode: load existing pack data
                await appState.refreshPads(packId: pack.id)
                pads = appState.currentPads
                packName = pack.name
                packDescription = pack.description
            } else {
                // New mode: auto-generate name "MySoundPack#N"
                let allPacks = appState.allPacks
                let maxNum = allPacks.compactMap { p -> Int? in
                    guard p.name.hasPrefix("MySoundPack#") else { return nil }
                    return Int(p.name.dropFirst("MySoundPack#".count))
                }.max() ?? 0
                packName = "MySoundPack#\(maxNum + 1)"
                nameFieldFocused = true
            }
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
                        let isSelected = selectedNotes.contains(note)
                        let hasPad = pads.contains { $0.note == note && $0.mode == padMode }
                        Text(noteLabels[idx])
                            .font(.spaceGrotesk(isSelected ? .bold : .regular, size: 15))
                            .foregroundColor(
                                isSelected ? .ledAmber :
                                hasPad ? .padActive :
                                .textSecondary
                            )
                            .frame(maxWidth: .infinity, minHeight: 54)
                            .background(
                                isSelected ? Color.ledAmber.opacity(0.15) :
                                hasPad ? Color.padActive.opacity(0.15) :
                                Color.padIdle
                            )
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(
                                        isSelected ? Color.ledAmber :
                                        hasPad ? Color.padActive.opacity(0.5) :
                                        Color.padBorder.opacity(0.3),
                                        lineWidth: 1
                                    )
                            )
                            .opacity(hasPad && !isSelected ? 0.4 : 1.0)
                            .contentShape(Rectangle())
                            .onTapGesture {
                                if !hasPad {
                                    if isSelected {
                                        noteFiles.removeValue(forKey: note)
                                        selectedNotes.remove(note)
                                    } else {
                                        selectedNotes.insert(note)
                                    }
                                }
                            }
                            .onLongPressGesture {
                                if hasPad {
                                    selectedNotes.insert(note)
                                }
                            }
                    }
                }
            }
        }
    }

    // MARK: - Save

    private func savePack() {
        guard canSave else { return }
        isProcessing = true
        Task {
            let actualPackId: Int64
            if let existingId = resolvedPackId, !isNewPack {
                // Edit mode: update metadata
                actualPackId = existingId
                if let pack = pack {
                    if packName.trimmingCharacters(in: .whitespaces) != pack.name {
                        await appState.renameSoundPack(id: existingId, name: packName.trimmingCharacters(in: .whitespaces))
                    }
                    if packDescription.trimmingCharacters(in: .whitespaces) != pack.description {
                        await appState.updateSoundPackDescription(id: existingId, description: packDescription.trimmingCharacters(in: .whitespaces))
                    }
                }
            } else {
                // New mode: create pack in DB
                let newId = await appState.createSoundPack(
                    name: packName.trimmingCharacters(in: .whitespaces),
                    description: packDescription.trimmingCharacters(in: .whitespaces)
                )
                createdPackId = newId
                actualPackId = newId
            }

            // Assign files
            if allFilesSelected {
                for (note, url) in noteFiles {
                    assignPad(note: note, mode: padMode, url: url, packId: actualPackId)
                }
            }
            isProcessing = false
            selectedNotes = []
            noteFiles = [:]
            dismiss()
        }
    }

    // MARK: - File assignment

    private func assignPad(note: String, mode: String, url: URL, packId: Int64) {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("soundpacks/\(packId)")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let dest = dir.appendingPathComponent("\(note)_\(mode)_\(url.lastPathComponent)")
        if url.startAccessingSecurityScopedResource() {
            defer { url.stopAccessingSecurityScopedResource() }
            try? FileManager.default.copyItem(at: url, to: dest)
        } else {
            try? FileManager.default.copyItem(at: url, to: dest)
        }
        Task {
            await appState.assignPad(packId: packId, note: note, mode: mode, filePath: dest.path)
            pads = appState.currentPads
        }
    }
}
