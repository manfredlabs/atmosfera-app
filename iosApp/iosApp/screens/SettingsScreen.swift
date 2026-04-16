import SwiftUI

struct SettingsScreen: View {
    @EnvironmentObject var appState: AppState

    private var isPadActive: Bool { appState.playingNote != nil }

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                ScreenHeader(title: "SETTINGS")
                    .padding(.top, 16)

                padSection
                divider
                clickSection
                divider
                soundPacksSection
            }
            .padding(.horizontal, 20)
            .frame(maxWidth: 600)
            .frame(maxWidth: .infinity)
        }
        .background(Color.darkBg)
    }

    private var divider: some View {
        Rectangle().fill(Color.padBorder.opacity(0.3)).frame(height: 1)
    }

    // MARK: - PAD Section

    private var padSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "PAD", color: isPadActive ? .ledAmber : .textSecondary)

            ChannelPills(channel: $appState.padChannel, activeColor: isPadActive ? .ledAmber : .textPrimary)
                .onChange(of: appState.padChannel) { _, _ in appState.saveSettings() }

            settingsSlider(label: "Volume", value: $appState.padVolume, accent: isPadActive ? .ledAmber : .textSecondary)
                .onChange(of: appState.padVolume) { _, v in
                    appState.liveAudio.padTargetVolume = v
                    appState.saveSettings()
                }

            fadeSlider(label: "Fade In", valueMs: $appState.fadeInMs, accent: isPadActive ? .ledAmber : .textSecondary) { ms in
                appState.liveAudio.fadeInMs = ms
                appState.mixAudio.fadeInMs = ms
                appState.saveSettings()
            }

            fadeSlider(label: "Fade Out", valueMs: $appState.fadeOutMs, accent: isPadActive ? .ledAmber : .textSecondary) { ms in
                appState.liveAudio.fadeOutMs = ms
                appState.mixAudio.fadeOutMs = ms
                appState.saveSettings()
            }
        }
    }

    // MARK: - CLICK Section

    private var clickSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "CLICK", color: .textSecondary)

            ChannelPills(channel: $appState.clickChannel, activeColor: .clickTeal)
                .onChange(of: appState.clickChannel) { _, _ in appState.saveSettings() }

            settingsSlider(label: "Volume", value: $appState.clickVolume, accent: .clickTeal)
                .onChange(of: appState.clickVolume) { _, _ in appState.saveSettings() }

            timeSignatureGrid
        }
    }

    // MARK: - Time Signature

    private var timeSignatureGrid: some View {
        let signatures = ["2/4", "3/4", "4/4", "5/4", "6/4", "6/8", "7/4", "7/8"]
        let rows = stride(from: 0, to: signatures.count, by: 4).map {
            Array(signatures[$0..<min($0+4, signatures.count)])
        }
        return VStack(alignment: .leading, spacing: 10) {
            Text("Time Signature")
                .font(.spaceGrotesk(.regular, size: 13))
                .foregroundColor(.textSecondary)
            ForEach(rows.indices, id: \.self) { rowIdx in
                HStack(spacing: 6) {
                    ForEach(rows[rowIdx], id: \.self) { sig in
                        let isSelected = appState.timeSignature == sig
                        Button {
                            appState.timeSignature = sig
                            appState.saveSettings()
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

    // MARK: - Sound Packs

    private var soundPacksSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "SOUND PACKS")

            NavigationLink {
                SoundPackListScreen()
            } label: {
                HStack {
                    Text("Sound Packs")
                        .font(.spaceGrotesk(.regular, size: 14))
                        .foregroundColor(.textPrimary)
                    Spacer()
                    Image(systemName: "gearshape")
                        .foregroundColor(.textSecondary)
                        .font(.system(size: 18))
                }
                .frame(height: 44)
                .padding(.horizontal, 12)
                .background(Color.padIdle)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }
        }
    }

    // MARK: - Helpers

    private func settingsSlider(label: String, value: Binding<Float>, accent: Color) -> some View {
        HStack {
            Text(label)
                .font(.spaceGrotesk(.regular, size: 13))
                .foregroundColor(.textSecondary)
                .frame(width: 60, alignment: .leading)
            Slider(value: value, in: 0...1)
                .tint(accent)
        }
    }

    private func fadeSlider(label: String, valueMs: Binding<Int64>, accent: Color, onChange: @escaping (Int64) -> Void) -> some View {
        HStack {
            Text(label)
                .font(.spaceGrotesk(.regular, size: 13))
                .foregroundColor(.textSecondary)
                .frame(width: 60, alignment: .leading)
            Slider(value: Binding(
                get: { Double(valueMs.wrappedValue) },
                set: {
                    let rounded = Int64(($0 / 500).rounded() * 500)
                    valueMs.wrappedValue = rounded
                    onChange(rounded)
                }
            ), in: 0...5000, step: 500)
            .tint(accent)
            Text(String(format: "%.1fs", Double(valueMs.wrappedValue) / 1000))
                .font(.spaceGrotesk(.regular, size: 11))
                .foregroundColor(.textSecondary.opacity(0.6))
                .frame(width: 36, alignment: .trailing)
        }
    }
}
