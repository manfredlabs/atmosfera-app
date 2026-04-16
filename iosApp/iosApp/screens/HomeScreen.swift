import SwiftUI

private let allNotes = ["c","cs","d","ds","e","f","fs","g","gs","a","as","b"]
private let noteLabels = ["C","C#","D","D#","E","F","F#","G","G#","A","A#","B"]

struct HomeScreen: View {
    @EnvironmentObject var appState: AppState
    @State private var padMode = "maj"
    @State private var bpm = 90
    @State private var accents = [1,0,0,0]
    @State private var isPlaying = false
    @State private var showPackSheet = false
    @State private var showTapTempo = false

    private var clickEnabled: Bool {
        get { appState.clickEnabled }
    }
    private func setClickEnabled(_ v: Bool) { appState.clickEnabled = v }

    var body: some View {
        GeometryReader { geo in
            let otherContentHeight: CGFloat = 200
            let availableForPads = geo.size.height - otherContentHeight
            let padFromHeight = (availableForPads - 24) / 4
            let padFromWidth = (geo.size.width - 40) / 3
            let padSize = min(padFromWidth, padFromHeight)
            let gridWidth = padSize * 3 + 16
            let padFontSize = min(max(padSize * 0.3, 29), 60)

            VStack(spacing: 16) {
                topBar
                padGrid(padSize: padSize, fontSize: padFontSize)
                clickRow
                accentRow
            }
            .frame(width: gridWidth)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Color.darkBg)
        .sheet(isPresented: $showPackSheet) { packSheet }
        .sheet(isPresented: $showTapTempo) { tapTempoSheet }
    }

    // MARK: - Top bar: NEU/MAJ/MIN + Pack chip

    private var topBar: some View {
        HStack {
            // Mode pills
            HStack(spacing: 2) {
                ForEach(["neu", "maj", "min"], id: \.self) { mode in
                    let isSelected = padMode == mode
                    Button {
                        padMode = mode
                        if isPlaying { restartPad() }
                    } label: {
                        Text(mode.uppercased())
                            .font(.spaceGrotesk(isSelected ? .bold : .regular, size: 13))
                            .foregroundColor(isSelected ? .textPrimary : .textSecondary)
                            .frame(width: 48, height: 26)
                            .background(isSelected ? Color.padActive : Color.padIdle)
                            .clipShape(RoundedRectangle(cornerRadius: 6))
                    }
                }
            }
            .padding(3)
            .background(Color.padIdle)
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.padBorder.opacity(0.5), lineWidth: 1))

            Spacer()

            // Pack chip
            Button { showPackSheet = true } label: {
                let packName = appState.allPacks.first(where: { $0.id == appState.currentPackId })?.name ?? "Atmos"
                Text(packName)
                    .font(.spaceGrotesk(.regular, size: 12))
                    .foregroundColor(.textSecondary)
                    .padding(.horizontal, 14)
                    .frame(height: 32)
                    .background(Color.padIdle)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.padBorder.opacity(0.5), lineWidth: 1))
            }
        }
        .padding(.top, 8)
    }

    // MARK: - Pad Grid 4×3

    private func padGrid(padSize: CGFloat, fontSize: CGFloat) -> some View {
        let rows = Array(allNotes.enumerated()).map { ($0.offset, $0.element) }
        let chunked = stride(from: 0, to: rows.count, by: 3).map { Array(rows[$0..<min($0+3, rows.count)]) }
        let isDefaultPack = appState.allPacks.first(where: { $0.id == appState.currentPackId })?.isDefault ?? true

        return VStack(spacing: 8) {
            ForEach(chunked.indices, id: \.self) { rowIdx in
                HStack(spacing: 8) {
                    ForEach(chunked[rowIdx], id: \.0) { idx, note in
                        let label = noteLabels[idx]
                        let isActive = appState.playingNote == note && isPlaying
                        let padAvailable = isDefaultPack || hasPad(note: note)

                        Button {
                            if padAvailable { tapPad(note: note) }
                        } label: {
                            Text(label)
                                .font(.spaceGrotesk(isActive ? .bold : .medium, size: fontSize))
                                .foregroundColor(isActive ? .ledAmber : .textOnPad)
                                .frame(width: padSize, height: padSize)
                                .background(isActive ? Color.ledAmber.opacity(0.15) : Color.padIdle)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(isActive ? Color.ledAmber.opacity(0.5) : Color.padBorder.opacity(0.3), lineWidth: 1)
                                )
                        }
                        .opacity(padAvailable ? 1 : 0.3)
                        .simultaneousGesture(
                            LongPressGesture().onEnded { _ in
                                // Long press on pad → could open song config
                            }
                        )
                        .animation(.easeInOut(duration: 0.2), value: isActive)
                    }
                }
            }
        }
    }

    // MARK: - Click row: CLICK ON/OFF + − BPM + 

    private var clickRow: some View {
        GeometryReader { geo in
            let colWidth = (geo.size.width - 16) / 3
            ZStack {
                HStack(spacing: 8) {
                    // Col 1: CLICK ON/OFF
                    Button { toggleClick() } label: {
                        Text(clickEnabled ? "CLICK ON" : "CLICK OFF")
                            .font(.spaceGrotesk(.bold, size: 13))
                            .foregroundColor(clickEnabled ? .clickTeal : .textSecondary)
                            .frame(width: colWidth, height: 36)
                            .background(clickEnabled ? Color.clickTealDim : Color.padIdle)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(clickEnabled ? Color.clickTeal.opacity(0.4) : Color.padBorder.opacity(0.3), lineWidth: 1)
                            )
                    }

                    // Col 2: − button aligned leading
                    HStack {
                        Button { if bpm > 30 { bpm -= 1; if clickEnabled { restartClick() } } } label: {
                            Text("−")
                                .font(.spaceGrotesk(.bold, size: 20))
                                .foregroundColor(bpm <= 30 ? .textSecondary.opacity(0.2) : .textSecondary)
                                .frame(width: 48, height: 36)
                                .background(Color.padIdle)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.padBorder.opacity(bpm <= 30 ? 0.15 : 0.3), lineWidth: 1))
                        }
                        Spacer()
                    }
                    .frame(width: colWidth)

                    // Col 3: + button aligned trailing
                    HStack {
                        Spacer()
                        Button { if bpm < 240 { bpm += 1; if clickEnabled { restartClick() } } } label: {
                            Text("+")
                                .font(.spaceGrotesk(.bold, size: 20))
                                .foregroundColor(bpm >= 240 ? .textSecondary.opacity(0.2) : .textSecondary)
                                .frame(width: 48, height: 36)
                                .background(Color.padIdle)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.padBorder.opacity(bpm >= 240 ? 0.15 : 0.3), lineWidth: 1))
                        }
                    }
                    .frame(width: colWidth)
                }

                // BPM overlay centered over right 2/3 (cols 2-3)
                HStack(alignment: .bottom, spacing: 3) {
                    Text("\(bpm)")
                        .font(.spaceGrotesk(.bold, size: 24))
                        .foregroundColor(clickEnabled ? .clickTeal : .textSecondary)
                    Text("BPM")
                        .font(.spaceGrotesk(.regular, size: 10))
                        .foregroundColor(clickEnabled ? .clickTeal.opacity(0.5) : .textSecondary.opacity(0.4))
                        .padding(.bottom, 3)
                }
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.leading, colWidth + 8)
                .onLongPressGesture { showTapTempo = true }
                .allowsHitTesting(true)
            }
        }
        .frame(height: 36)
    }

    // MARK: - Accent circles

    private var accentRow: some View {
        HStack(spacing: 10) {
            ForEach(Array(accents.enumerated()), id: \.offset) { index, beatState in
                let isAccent = beatState == 1
                let isMuted = beatState == 2
                let isCurrent = clickEnabled && appState.liveAudio.beatOn && appState.liveAudio.currentBeat == index

                Button {
                    accents[index] = (beatState + 1) % 3
                    if clickEnabled { restartClick() }
                } label: {
                    ZStack {
                        Circle()
                            .fill(accentBgColor(isMuted: isMuted, isCurrent: isCurrent, isAccent: isAccent))
                        Circle()
                            .stroke(accentBorderColor(isMuted: isMuted, isCurrent: isCurrent, isAccent: isAccent), lineWidth: 1)
                        Text(isMuted ? "×" : "\(index + 1)")
                            .font(.spaceGrotesk(isAccent ? .bold : .regular, size: 16))
                            .foregroundColor(accentTextColor(isMuted: isMuted, isCurrent: isCurrent, isAccent: isAccent))
                    }
                    .frame(width: 36, height: 36)
                }
            }
        }
    }

    // MARK: - Accent color helpers

    private func accentBgColor(isMuted: Bool, isCurrent: Bool, isAccent: Bool) -> Color {
        if isMuted && isCurrent { return .clickTeal.opacity(0.3) }
        if isMuted { return .padIdle.opacity(0.5) }
        if isCurrent && isAccent { return .clickTeal }
        if isCurrent { return .clickTeal.opacity(0.7) }
        if isAccent && clickEnabled { return .clickTealDim }
        if isAccent { return .padActive }
        return .padIdle
    }

    private func accentBorderColor(isMuted: Bool, isCurrent: Bool, isAccent: Bool) -> Color {
        if isMuted { return .padBorder.opacity(0.15) }
        if isCurrent { return .clickTeal }
        if isAccent && clickEnabled { return .clickTeal.opacity(0.5) }
        if isAccent { return .padBorder.opacity(0.8) }
        return .padBorder.opacity(0.3)
    }

    private func accentTextColor(isMuted: Bool, isCurrent: Bool, isAccent: Bool) -> Color {
        if isMuted { return .textSecondary.opacity(0.2) }
        if isCurrent { return .textPrimary }
        if isAccent && clickEnabled { return .clickTeal }
        if isAccent { return .textSecondary }
        return .textSecondary.opacity(0.4)
    }

    // MARK: - Pack sheet

    private var packSheet: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("SOUND PACK")
                .font(.spaceGrotesk(.bold, size: 12))
                .foregroundColor(.textSecondary)
                .tracking(2)
                .padding(.bottom, 4)

            ForEach(appState.allPacks, id: \.id) { pack in
                let isSelected = pack.id == appState.currentPackId
                Button {
                    appState.currentPackId = pack.id
                    Task { await appState.refreshPads(packId: pack.id) }
                    if isPlaying { restartPad() }
                    showPackSheet = false
                } label: {
                    HStack {
                        Text(pack.name)
                            .font(.spaceGrotesk(isSelected ? .bold : .regular, size: 15))
                            .foregroundColor(isSelected ? .ledAmber : .textSecondary)
                        Spacer()
                        if isSelected {
                            Text("✓")
                                .font(.spaceGrotesk(.regular, size: 14))
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
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
    }

    // MARK: - Tap Tempo sheet

    private var tapTempoSheet: some View {
        TapTempoView(
            onApply: { newBpm in
                bpm = newBpm
                if clickEnabled { restartClick() }
                showTapTempo = false
            },
            onDismiss: { showTapTempo = false }
        )
        .background(Color.darkBg)
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
    }

    // MARK: - Helpers

    private func hasPad(note: String) -> Bool {
        appState.currentPads.contains { $0.note == note && $0.mode == padMode }
    }

    private func tapPad(note: String) {
        if isPlaying && appState.playingNote == note {
            appState.liveAudio.stopPad()
            isPlaying = false
            appState.playingNote = nil
        } else {
            restartPadWithNote(note)
        }
    }

    private func restartPad() {
        guard let note = appState.playingNote else { return }
        restartPadWithNote(note)
    }

    private func restartPadWithNote(_ note: String) {
        let isDefaultPack = appState.allPacks.first(where: { $0.id == appState.currentPackId })?.isDefault ?? true

        if !isDefaultPack, let pad = appState.currentPads.first(where: { $0.note == note && $0.mode == padMode }) {
            appState.liveAudio.padTargetVolume = appState.padVolume
            appState.liveAudio.startPadFromFile(filePath: pad.filePath, padChannel: appState.padChannel)
        } else {
            let resName = "pad_\(note)_\(padMode)"
            appState.liveAudio.padTargetVolume = appState.padVolume
            appState.liveAudio.startPad(resName: resName, padChannel: appState.padChannel)
        }
        isPlaying = true
        appState.playingNote = note
    }

    private func toggleClick() {
        setClickEnabled(!clickEnabled)
        if clickEnabled { startClick() } else { stopClick() }
    }

    private func startClick() {
        appState.liveAudio.startClick(bpm: bpm, channel: appState.clickChannel, volume: appState.clickVolume, accents: accents)
    }

    private func stopClick() {
        appState.liveAudio.stopClick()
    }

    private func restartClick() {
        appState.liveAudio.restartClick(bpm: bpm, channel: appState.clickChannel, volume: appState.clickVolume, accents: accents)
    }
}

// MARK: - Tap Tempo View (reusable)

struct TapTempoView: View {
    var onApply: (Int) -> Void
    var onDismiss: () -> Void

    @State private var tapTimes: [Date] = []
    @State private var tapBpm = 0

    var body: some View {
        VStack(spacing: 16) {
            Text("TAP TEMPO")
                .font(.spaceGrotesk(.bold, size: 12))
                .foregroundColor(.textSecondary)
                .tracking(2)

            Text(tapBpm > 0 ? "\(tapBpm)" : "—")
                .font(.spaceGrotesk(.bold, size: 48))
                .foregroundColor(tapBpm > 0 ? .clickTeal : .textSecondary.opacity(0.3))

            Text(tapBpm > 0 ? "BPM" : "Tap the button to start")
                .font(.spaceGrotesk(.regular, size: 13))
                .foregroundColor(.textSecondary.opacity(0.6))

            // Tap button
            Button { handleTap() } label: {
                Text("TAP")
                    .font(.spaceGrotesk(.bold, size: 20))
                    .foregroundColor(.clickTeal)
                    .frame(width: 100, height: 100)
                    .background(Color.clickTealDim)
                    .clipShape(Circle())
                    .overlay(Circle().stroke(Color.clickTeal.opacity(0.5), lineWidth: 2))
            }

            // Reset / Apply
            HStack(spacing: 8) {
                Button {
                    tapTimes.removeAll()
                    tapBpm = 0
                } label: {
                    Text("Reset")
                        .font(.spaceGrotesk(.regular, size: 14))
                        .foregroundColor(.textSecondary)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(Color.padIdle)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.padBorder.opacity(0.3), lineWidth: 1))
                }

                Button {
                    if tapBpm > 0 { onApply(tapBpm) }
                } label: {
                    Text("Apply")
                        .font(.spaceGrotesk(.bold, size: 14))
                        .foregroundColor(tapBpm > 0 ? .clickTeal : .textSecondary.opacity(0.3))
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(tapBpm > 0 ? Color.clickTealDim : Color.padIdle)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(tapBpm > 0 ? Color.clickTeal.opacity(0.4) : Color.padBorder.opacity(0.3), lineWidth: 1)
                        )
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }

    private func handleTap() {
        let now = Date()
        if let last = tapTimes.last, now.timeIntervalSince(last) > 2.0 {
            tapTimes.removeAll()
        }
        tapTimes.append(now)
        if tapTimes.count > 8 { tapTimes.removeFirst() }
        if tapTimes.count >= 2 {
            let intervals = zip(tapTimes, tapTimes.dropFirst()).map { $1.timeIntervalSince($0) }
            let avg = intervals.reduce(0, +) / Double(intervals.count)
            tapBpm = max(30, min(240, Int(60.0 / avg)))
        }
    }
}
