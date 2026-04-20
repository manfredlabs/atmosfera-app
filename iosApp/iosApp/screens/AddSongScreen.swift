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
    @State private var selectedNote = "c"
    @State private var padMode = "maj"
    @State private var bpm = 90
    @State private var accents = [1,0,0,0]
    @State private var padEnabled = true
    @State private var clickEnabled = false
    @State private var padVolume: Double = 0.5
    @State private var clickVolume: Double = 0.5
    @State private var padChannel = "mono"
    @State private var clickChannel = "mono"
    @State private var timeSignature = "4/4"
    @State private var showPackSheet = false
    @State private var selectedPackId: Int64 = -1
    @State private var packPads: [SoundPadItem] = []
    @FocusState private var nameFieldFocused: Bool

    private var isEditMode: Bool { existingSong != nil }
    private var isDefaultPack: Bool { appState.allPacks.first { $0.id == selectedPackId }?.isDefault ?? true }
    private var availableModes: [String] {
        if isDefaultPack { return ["neu", "maj", "min"] }
        return Array(Set(packPads.map(\.mode)))
    }
    private func availableNotes(for mode: String) -> Set<String> {
        if isDefaultPack { return Set(allNotes) }
        return Set(packPads.filter { $0.mode == mode }.map(\.note))
    }

    var body: some View {
        ZStack(alignment: .top) {
            Color.darkBg.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 20) {
                    // Header
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
                        Text(isEditMode ? "EDIT SONG" : "NEW SONG")
                            .font(.spaceGrotesk(.light, size: 22))
                            .foregroundColor(.textPrimary)
                            .tracking(2)
                    }
                    .padding(.top, 8)

                    // ─── Name ───
                    VStack(alignment: .leading, spacing: 8) {
                        SectionHeader(title: "NAME", size: 12)
                        TextField("e.g. How Great Is Our God", text: $name)
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
                            .onChange(of: name) { _, new in
                                if new.count > 30 { name = String(new.prefix(30)) }
                            }
                    }

                    // ─── Sound Pack ───
                    VStack(alignment: .leading, spacing: 8) {
                        SectionHeader(title: "SOUND PACK", size: 12)
                        Button { showPackSheet = true }label: {
                            HStack {
                                Text(appState.allPacks.first { $0.id == selectedPackId }?.name ?? "Atmos")
                                    .font(.spaceGrotesk(.regular, size: 15))
                                    .foregroundColor(.textPrimary)
                                Spacer()
                                Text("▼")
                                    .font(.system(size: 11))
                                    .foregroundColor(.textSecondary)
                            }
                            .padding(.horizontal, 16)
                            .frame(height: 48)
                            .background(Color.padIdle)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.padBorder.opacity(0.3), lineWidth: 1))
                        }
                    }

                    // ─── Key ───
                    VStack(alignment: .leading, spacing: 10) {
                        SectionHeader(title: "KEY", size: 12)

                        // NEU / MAJ / MIN pills with availability
                        PillSelector(
                            options: ["NEU", "MAJ", "MIN"],
                            selected: Binding(
                                get: { padMode.uppercased() },
                                set: { val in
                                    let mode = val.lowercased()
                                    if availableModes.contains(mode) { padMode = mode }
                                }
                            ),
                            disabledOptions: Set(["neu", "maj", "min"].filter { !availableModes.contains($0) }.map { $0.uppercased() })
                        )

                        // Note grid 4×3
                        noteGrid
                    }

                    // ─── Pad Routing ───
                    trackCard(
                        label: "PAD",
                        icon: "pianokeys",
                        accentColor: .ledAmber,
                        accentDimColor: .ledAmberDim,
                        volume: $padVolume,
                        channel: $padChannel
                    )

                    // ─── Click ───
                    VStack(alignment: .leading, spacing: 10) {
                        SectionHeader(title: "CLICK", size: 12)

                        if !clickEnabled {
                            // Full-width OFF
                            Button { clickEnabled = true } label: {
                                Text("CLICK OFF  —  TAP TO ENABLE")
                                    .font(.spaceGrotesk(.bold, size: 13))
                                    .foregroundColor(.textSecondary.opacity(0.5))
                                    .frame(maxWidth: .infinity, minHeight: 36)
                                    .background(Color.padIdle)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.padBorder.opacity(0.3), lineWidth: 1))
                            }
                        } else {
                            // Click ON + BPM controls
                            clickRow

                            // Time Signature
                            timeSignatureGrid

                            // Accent circles
                            accentRow

                            // Click routing card
                            trackCard(
                                label: "CLICK",
                                icon: "metronome",
                                accentColor: .clickTeal,
                                accentDimColor: .clickTealDim,
                                volume: $clickVolume,
                                channel: $clickChannel
                            )
                        }
                    }

                    Spacer(minLength: 16)

                    // ─── Bottom Buttons ───
                    HStack(spacing: 12) {
                        Button { dismiss() } label: {
                            Text("CANCEL")
                                .font(.spaceGrotesk(.bold, size: 14))
                                .foregroundColor(.textSecondary)
                                .tracking(2)
                                .frame(maxWidth: .infinity, minHeight: 50)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 10)
                                        .stroke(Color.padBorder.opacity(0.5), lineWidth: 1)
                                )
                        }
                        Button { save() } label: {
                            Text("SAVE")
                                .font(.spaceGrotesk(.bold, size: 14))
                                .foregroundColor(name.trimmingCharacters(in: .whitespaces).isEmpty ? .textSecondary : .darkBg)
                                .tracking(2)
                                .frame(maxWidth: .infinity, minHeight: 50)
                                .background(name.trimmingCharacters(in: .whitespaces).isEmpty ? Color.padActive : Color.ledAmber)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                        }
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
                .frame(maxWidth: 600)
                .frame(maxWidth: .infinity)
            }
        }
        .onAppear {
            appState.showBottomBar = false
            populateFromExisting()
            if !isEditMode {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                    nameFieldFocused = true
                }
            }
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $showPackSheet){ packSheet }
    }

    // MARK: - Time Signature Grid

    private var timeSignatureGrid: some View {
        let signatures = ["2/4", "3/4", "4/4", "5/4", "6/4", "6/8", "7/4", "7/8"]
        let rows = stride(from: 0, to: signatures.count, by: 4).map {
            Array(signatures[$0..<min($0+4, signatures.count)])
        }
        return VStack(alignment: .leading, spacing: 10) {
            SectionHeader(title: "TIME SIGNATURE", size: 12)
            ForEach(rows.indices, id: \.self) { rowIdx in
                HStack(spacing: 6) {
                    ForEach(rows[rowIdx], id: \.self) { sig in
                        let isSelected = timeSignature == sig
                        Button {
                            timeSignature = sig
                            let beats = Int(sig.split(separator: "/").first ?? "4") ?? 4
                            accents = (0..<beats).map { $0 == 0 ? 1 : 0 }
                        } label: {
                            Text(sig)
                                .font(.spaceGrotesk(isSelected ? .bold : .regular, size: 14))
                                .foregroundColor(isSelected ? .clickTeal : .textSecondary.opacity(0.7))
                                .frame(maxWidth: .infinity, minHeight: 44)
                                .background(isSelected ? Color.clickTeal.opacity(0.15) : Color.padIdle)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8)
                                        .stroke(isSelected ? Color.clickTeal.opacity(0.5) : Color.padBorder.opacity(0.3), lineWidth: 1)
                                )
                        }
                    }
                }
            }
        }
    }

    // MARK: - Pack Sheet

    private var packSheet: some View {
        VStack(alignment: .leading, spacing: 6) {
            SectionHeader(title: "SOUND PACK", size: 12)
                .padding(.bottom, 4)
            ForEach(appState.allPacks) { pack in
                let isSelected = selectedPackId == pack.id
                Button {
                    selectedPackId = pack.id
                    Task { await loadPackPads(packId: pack.id) }
                    showPackSheet = false
                } label: {
                    HStack {
                        Text(pack.name)
                            .font(.spaceGrotesk(isSelected ? .bold : .regular, size: 15))
                            .foregroundColor(isSelected ? .ledAmber : .textSecondary)
                        Spacer()
                        if isSelected {
                            Text("✓")
                                .font(.system(size: 14))
                                .foregroundColor(.ledAmber)
                        }
                    }
                    .padding(.horizontal, 16)
                    .frame(height: 48)
                    .background(isSelected ? Color.ledAmber.opacity(0.15) : Color.padIdle)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(isSelected ? Color.ledAmber : Color.padBorder.opacity(0.3), lineWidth: 1)
                    )
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.darkBg)
        .presentationDetents([.height(CGFloat(appState.allPacks.count) * 54 + 60)])
        .presentationDragIndicator(.visible)
        .presentationBackgroundInteraction(.disabled)
    }

    // MARK: - Note Grid 4×3

    private var noteGrid: some View {
        let chunked = stride(from: 0, to: allNotes.count, by: 3).map {
            Array(Array(allNotes.enumerated())[$0..<min($0+3, allNotes.count)])
        }
        let notesForMode = availableNotes(for: padMode)
        return VStack(spacing: 6) {
            ForEach(chunked.indices, id: \.self) { rowIdx in
                HStack(spacing: 6) {
                    ForEach(chunked[rowIdx], id: \.offset) { idx, note in
                        let isSelected = selectedNote == note
                        let isAvailable = notesForMode.contains(note)
                        Button { if isAvailable { selectedNote = note } } label: {
                            Text(noteLabels[idx])
                                .font(.spaceGrotesk(isSelected ? .bold : .regular, size: 15))
                                .foregroundColor(isSelected ? .ledAmber : .textSecondary)
                                .frame(maxWidth: .infinity, minHeight: 54)
                                .background(isSelected ? Color.ledAmber.opacity(0.15) : Color.padIdle)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8)
                                        .stroke(isSelected ? Color.ledAmber : Color.padBorder.opacity(0.3), lineWidth: 1)
                                )
                                .opacity(isAvailable ? 1.0 : 0.3)
                        }
                    }
                }
            }
        }
    }

    // MARK: - Click Row

    private var clickRow: some View {
        GeometryReader { geo in
            let colWidth = (geo.size.width - 12) / 3
            ZStack {
                HStack(spacing: 6) {
                    // Col 1: CLICK ON
                    Button { clickEnabled = false } label: {
                        Text("CLICK ON")
                            .font(.spaceGrotesk(.bold, size: 13))
                            .foregroundColor(.clickTeal)
                            .frame(width: colWidth, height: 36)
                            .background(Color.clickTeal.opacity(0.15))
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.clickTeal, lineWidth: 1))
                    }

                    // Col 2: − aligned leading
                    HStack {
                        Button { if bpm > 30 { bpm -= 1 } } label: {
                            Text("−")
                                .font(.spaceGrotesk(.bold, size: 20))
                                .foregroundColor(bpm <= 30 ? .textSecondary.opacity(0.2) : .textSecondary)
                                .frame(width: 44, height: 36)
                                .background(Color.padIdle)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.padBorder.opacity(bpm <= 30 ? 0.15 : 0.3), lineWidth: 1))
                        }
                        Spacer()
                    }
                    .frame(width: colWidth)

                    // Col 3: + aligned trailing
                    HStack {
                        Spacer()
                        Button { if bpm < 240 { bpm += 1 } } label: {
                            Text("+")
                                .font(.spaceGrotesk(.bold, size: 20))
                                .foregroundColor(bpm >= 240 ? .textSecondary.opacity(0.2) : .textSecondary)
                                .frame(width: 44, height: 36)
                                .background(Color.padIdle)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.padBorder.opacity(bpm >= 240 ? 0.15 : 0.3), lineWidth: 1))
                        }
                    }
                    .frame(width: colWidth)
                }

                // BPM overlay centered over right 2/3
                HStack(alignment: .bottom, spacing: 3) {
                    Text("\(bpm)")
                        .font(.spaceGrotesk(.bold, size: 24))
                        .foregroundColor(.clickTeal)
                    Text("BPM")
                        .font(.spaceGrotesk(.regular, size: 10))
                        .foregroundColor(.clickTeal.opacity(0.5))
                        .padding(.bottom, 3)
                }
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.leading, colWidth + 6)
            }
        }
        .frame(height: 36)
    }

    // MARK: - Accent Row

    private var accentRow: some View {
        HStack(spacing: 10) {
            ForEach(Array(accents.enumerated()), id: \.offset) { index, beatState in
                let isAccent = beatState == 1
                let isMuted = beatState == 2
                Button {
                    accents[index] = beatState == 1 ? 0 : beatState == 0 ? 2 : 1
                } label: {
                    ZStack {
                        Circle()
                            .fill(isMuted ? Color.clear : isAccent ? Color.clickTeal.opacity(0.2) : Color.clear)
                        Circle()
                            .stroke(
                                isMuted ? Color.padBorder.opacity(0.15) : isAccent ? Color.clickTeal : Color.padBorder.opacity(0.4),
                                lineWidth: 1
                            )
                        Text(isMuted ? "×" : "\(index + 1)")
                            .font(.spaceGrotesk(isAccent ? .bold : .regular, size: 16))
                            .foregroundColor(
                                isMuted ? .textSecondary.opacity(0.2) : isAccent ? .clickTeal : .textSecondary.opacity(0.4)
                            )
                    }
                    .frame(width: 36, height: 36)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - Track Card (Pad / Click routing)

    private func trackCard(
        label: String, icon: String, accentColor: Color, accentDimColor: Color,
        volume: Binding<Double>, channel: Binding<String>
    ) -> some View {
        DarkSurface(borderColor: accentColor.opacity(0.35)) {
            VStack(spacing: 4) {
                HStack(spacing: 10) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 8)
                            .fill(accentColor.opacity(0.15))
                            .frame(width: 36, height: 36)
                        Image(systemName: icon)
                            .font(.system(size: 20))
                            .foregroundColor(accentColor)
                    }
                    Text(label)
                        .font(.spaceGrotesk(.medium, size: 15))
                        .foregroundColor(.textPrimary)
                    Spacer()
                }

                HStack {
                    StyledSlider(
                        value: Binding(get: { Float(volume.wrappedValue) }, set: { volume.wrappedValue = Double($0) }),
                        thumbColor: accentColor,
                        activeTrackColor: accentDimColor,
                        inactiveTrackColor: .padBorder.opacity(0.3)
                    )
                    ChannelPills(channel: channel, activeColor: accentColor)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
    }

    // MARK: - Data

    private func populateFromExisting() {
        guard let s = existingSong else {
            // Auto-generate name for new songs
            let maxNum = appState.allSongs
                .compactMap { $0.name.hasPrefix("MySong#") ? Int($0.name.dropFirst(7)) : nil }
                .max() ?? 0
            name = "MySong#\(maxNum + 1)"
            selectedPackId = appState.currentPackId
            Task { await loadPackPads(packId: selectedPackId) }
            return
        }
        name = s.name
        selectedNote = s.note
        padMode = s.padMode
        bpm = s.bpm
        accents = s.accentList
        padEnabled = s.padEnabled
        clickEnabled = s.clickEnabled
        padVolume = Double(s.padVolume)
        clickVolume = Double(s.clickVolume)
        padChannel = s.padChannel
        clickChannel = s.clickChannel
        selectedPackId = s.soundPackId == -1 ? appState.currentPackId : s.soundPackId
        let beats = accents.count
        timeSignature = [2: "2/4", 3: "3/4", 5: "5/4", 6: "6/4", 7: "7/4"][beats] ?? "4/4"
        Task { await loadPackPads(packId: selectedPackId) }
    }

    private func loadPackPads(packId: Int64) async {
        let pads = await appState.getPadsForPack(packId: packId)
        await MainActor.run {
            packPads = pads
            let defaultPack = appState.allPacks.first { $0.id == selectedPackId }?.isDefault ?? true
            if !defaultPack {
                let modes = Array(Set(pads.map(\.mode)))
                if !modes.contains(padMode) && !modes.isEmpty {
                    padMode = modes.first!
                }
                let notes = Set(pads.filter { $0.mode == padMode }.map(\.note))
                if !notes.contains(selectedNote) && !notes.isEmpty {
                    selectedNote = notes.first!
                }
            }
        }
    }

    private func save() {
        let song = SongItem(
            id: existingSong?.id ?? 0,
            name: name.trimmingCharacters(in: .whitespaces),
            note: selectedNote,
            isMajor: padMode == "maj",
            bpm: bpm,
            accents: accents.map(String.init).joined(separator: ","),
            padEnabled: padEnabled,
            clickEnabled: clickEnabled,
            createdAt: existingSong?.createdAt ?? Int64(Date().timeIntervalSince1970 * 1000),
            sortOrder: existingSong?.sortOrder ?? 0,
            padMode: padMode,
            soundPackId: selectedPackId,
            padVolume: Float(padVolume),
            padChannel: padChannel,
            clickVolume: Float(clickVolume),
            clickChannel: clickChannel
        )
        Task {
            if existingSong != nil { await appState.updateSong(song) }
            else { await appState.insertSong(song) }
        }
        dismiss()
    }
}
