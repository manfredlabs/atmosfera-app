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
    @State private var clickEnabled = true
    @State private var padVolume: Double = 0.5
    @State private var clickVolume: Double = 0.5
    @State private var padChannel = "mono"
    @State private var clickChannel = "mono"
    @FocusState private var nameFieldFocused: Bool

    private var isEditMode: Bool { existingSong != nil }

    var body: some View {
        ZStack(alignment: .top) {
            Color.darkBg.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 20) {
                    // Header
                    ScreenHeader(title: isEditMode ? "EDIT SONG" : "NEW SONG")
                        .padding(.top, 8)

                    // ─── Name ───
                    VStack(alignment: .leading, spacing: 8) {
                        SectionHeader(title: "NAME")
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

                    // ─── Key ───
                    VStack(alignment: .leading, spacing: 10) {
                        SectionHeader(title: "KEY")

                        // NEU / MAJ / MIN pills
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
                        SectionHeader(title: "CLICK")

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
            }
        }
        .onAppear { populateFromExisting() }
    }

    // MARK: - Note Grid 4×3

    private var noteGrid: some View {
        let chunked = stride(from: 0, to: allNotes.count, by: 3).map {
            Array(Array(allNotes.enumerated())[$0..<min($0+3, allNotes.count)])
        }
        return VStack(spacing: 6) {
            ForEach(chunked.indices, id: \.self) { rowIdx in
                HStack(spacing: 6) {
                    ForEach(chunked[rowIdx], id: \.offset) { idx, note in
                        let isSelected = selectedNote == note
                        Button { selectedNote = note } label: {
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
                        }
                    }
                }
            }
        }
    }

    // MARK: - Click Row

    private var clickRow: some View {
        ZStack {
            HStack(spacing: 6) {
                Button { clickEnabled = false } label: {
                    Text("CLICK ON")
                        .font(.spaceGrotesk(.bold, size: 13))
                        .foregroundColor(.clickTeal)
                        .frame(maxWidth: .infinity, minHeight: 36)
                        .background(Color.clickTeal.opacity(0.15))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.clickTeal, lineWidth: 1))
                }

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

            HStack(alignment: .bottom, spacing: 3) {
                Text("\(bpm)")
                    .font(.spaceGrotesk(.bold, size: 24))
                    .foregroundColor(.clickTeal)
                Text("BPM")
                    .font(.spaceGrotesk(.regular, size: 10))
                    .foregroundColor(.clickTeal.opacity(0.5))
                    .padding(.bottom, 3)
            }
        }
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
                            .font(.system(size: 18))
                            .foregroundColor(accentColor)
                    }
                    Text(label)
                        .font(.spaceGrotesk(.medium, size: 15))
                        .foregroundColor(.textPrimary)
                    Spacer()
                }

                HStack {
                    AmberSlider(value: volume, accentColor: accentColor)
                    ChannelPills(channel: channel, activeColor: accentColor)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
    }

    // MARK: - Data

    private func populateFromExisting() {
        guard let s = existingSong else { return }
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
            soundPackId: existingSong?.soundPackId ?? -1,
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
