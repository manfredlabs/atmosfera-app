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

            ChannelSegmentedBar(channel: $appState.padChannel, activeColor: .ledAmber, isActive: isPadActive)
                .onChange(of: appState.padChannel){ _, ch in
                    let linked: String
                    switch ch {
                    case "left": linked = "right"
                    case "right": linked = "left"
                    default: linked = "mono"
                    }
                    appState.clickChannel = linked
                    appState.liveAudio.updatePadPanning(channel: ch)
                    appState.liveAudio.currentClickChannel = linked
                    appState.saveSettings()
                }

            settingsSlider(label: "Volume", value: $appState.padVolume,
                          accent: isPadActive ? .ledAmber : .textSecondary,
                          dimAccent: isPadActive ? .ledAmberDim : .padActive)
                .onChange(of: appState.padVolume) { _, v in
                    appState.liveAudio.padTargetVolume = v
                    appState.saveSettings()
                }
        }
    }

    // MARK: - CLICK Section

    private var clickSection: some View {
        let isClickActive = appState.clickEnabled
        return VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "CLICK", color: isClickActive ? .clickTeal : .textSecondary)

            ChannelSegmentedBar(channel: $appState.clickChannel, activeColor: .clickTeal, isActive: isClickActive)
                .onChange(of: appState.clickChannel){ _, ch in
                    let linked: String
                    switch ch {
                    case "left": linked = "right"
                    case "right": linked = "left"
                    default: linked = "mono"
                    }
                    appState.padChannel = linked
                    appState.liveAudio.currentClickChannel = ch
                    appState.liveAudio.updatePadPanning(channel: linked)
                    appState.saveSettings()
                }

            settingsSlider(label: "Volume", value: $appState.clickVolume,
                          accent: isClickActive ? .clickTeal : .textSecondary,
                          dimAccent: isClickActive ? .clickTealDim : .padActive)
                .onChange(of: appState.clickVolume) { _, _ in appState.saveSettings() }

            timeSignatureChips
        }
    }

    // MARK: - Time Signature

    private var timeSignatureChips: some View {
        let isClickActive = appState.clickEnabled
        let signatures = ["2/4", "3/4", "4/4", "5/4", "6/4", "6/8", "7/4", "7/8"]
        return HStack {
            Text("Signature")
                .font(.spaceGrotesk(.regular, size: 13))
                .foregroundColor(.textSecondary)
                .frame(width: 60, alignment: .leading)
            Spacer()
            Menu {
                ForEach(signatures, id: \.self) { sig in
                    Button {
                        appState.timeSignature = sig
                        appState.saveSettings()
                    } label: {
                        if appState.timeSignature == sig {
                            Label(sig, systemImage: "checkmark")
                        } else {
                            Text(sig)
                        }
                    }
                }
            } label: {
                HStack(spacing: 6) {
                    Text(appState.timeSignature)
                        .font(.spaceGrotesk(.bold, size: 14))
                        .foregroundColor(isClickActive ? .clickTeal : .textPrimary)
                    Image(systemName: "chevron.down")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundColor(isClickActive ? .clickTeal.opacity(0.7) : .textSecondary)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(
                    isClickActive ? Color.clickTeal.opacity(0.15) : Color.padActive
                )
                .clipShape(Capsule())
                .overlay(
                    Capsule()
                        .stroke(
                            isClickActive ? Color.clickTeal.opacity(0.5) : Color.padBorder.opacity(0.8),
                            lineWidth: 1
                        )
                )
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

    private func settingsSlider(label: String, value: Binding<Float>, accent: Color, dimAccent: Color) -> some View {
        HStack {
            Text(label)
                .font(.spaceGrotesk(.regular, size: 13))
                .foregroundColor(.textSecondary)
                .frame(width: 60, alignment: .leading)
            StyledSlider(
                value: value,
                thumbColor: accent,
                activeTrackColor: dimAccent,
                inactiveTrackColor: .padBorder.opacity(0.3)
            )
        }
    }

}
