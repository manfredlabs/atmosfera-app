import SwiftUI

struct LabsScreen: View {
    @EnvironmentObject var appState: AppState
    @State private var navigateToTapTempo = false
    @State private var navigateToMixStudio = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                ScreenHeader(title: "LABS")
                    .padding(.top, 16)

                // Tap Tempo card
                Button { navigateToTapTempo = true } label: {
                    labCard(
                        icon: "hand.tap",
                        title: "Tap Tempo",
                        subtitle: "Find BPM by tapping"
                    )
                }

                // Mix Studio card
                Button { navigateToMixStudio = true } label: {
                    labCard(
                        icon: "headphones",
                        title: "Mix Studio",
                        subtitle: "Multi-track mixer"
                    )
                }

                Spacer()
            }
            .padding(.horizontal, 20)
            .frame(maxWidth: .infinity)
            .background(Color.darkBg)
            .onAppear { appState.showBottomBar = true }
            .navigationBarHidden(true)
            .navigationDestination(isPresented: $navigateToTapTempo) {
                TapTempoScreen()
            }
            .navigationDestination(isPresented: $navigateToMixStudio) {
                MixStudioListScreen()
            }
        }
    }

    private func labCard(icon: String, title: String, subtitle: String) -> some View {
        HStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color.labsPurple.opacity(0.15))
                    .frame(width: 44, height: 44)
                Image(systemName: icon)
                    .foregroundColor(.labsPurple)
                    .font(.system(size: 24))
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.spaceGrotesk(.regular, size: 18))
                    .foregroundColor(.textPrimary)
                Text(subtitle)
                    .font(.spaceGrotesk(.regular, size: 13))
                    .foregroundColor(.textSecondary.opacity(0.6))
            }

            Spacer()

            Image(systemName: "chevron.right")
                .foregroundColor(.textSecondary.opacity(0.4))
                .font(.system(size: 20))
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 16)
        .background(Color.padIdle)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.padBorder.opacity(0.3), lineWidth: 1)
        )
    }
}

// MARK: - Tap Tempo Screen

struct TapTempoScreen: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) private var dismiss

    @State private var tapTimes: [Date] = []
    @State private var tapBpm = 0
    @State private var tapCount = 0

    var body: some View {
        VStack(spacing: 16) {
            // Header with back arrow
            ZStack {
                HStack {
                    Button { dismiss() } label: {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundColor(.textSecondary)
                    }
                    Spacer()
                }
                Text("TAP TEMPO")
                    .font(.spaceGrotesk(.bold, size: 22))
                    .foregroundColor(.textSecondary)
                    .tracking(6)
            }
            .frame(height: 48)

            Spacer().frame(height: 8)

            // BPM display
            Text(tapBpm > 0 ? "\(tapBpm)" : "—")
                .font(.spaceGrotesk(.bold, size: 64))
                .foregroundColor(tapBpm > 0 ? .clickTeal : .textSecondary.opacity(0.3))

            Text(tapBpm > 0 ? "BPM  ·  \(tapCount) taps" : "Tap the button to start")
                .font(.spaceGrotesk(.regular, size: 13))
                .foregroundColor(.textSecondary.opacity(0.6))

            Spacer().frame(height: 8)

            // Tap button
            Button { handleTap() } label: {
                Text("TAP")
                    .font(.spaceGrotesk(.bold, size: 24))
                    .foregroundColor(.clickTeal)
                    .frame(width: 140, height: 140)
                    .background(Color.clickTealDim)
                    .clipShape(Circle())
                    .overlay(Circle().stroke(Color.clickTeal.opacity(0.5), lineWidth: 2))
            }

            Spacer().frame(height: 8)

            // Action buttons
            HStack(spacing: 8) {
                Button {
                    tapTimes.removeAll()
                    tapBpm = 0
                    tapCount = 0
                } label: {
                    Text("Reset")
                        .font(.spaceGrotesk(.regular, size: 14))
                        .foregroundColor(.textSecondary)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background(Color.padIdle)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.padBorder.opacity(0.3), lineWidth: 1))
                }

                Button {
                    if tapBpm > 0 {
                        applyBpmAndGoLive()
                    }
                } label: {
                    Text("Apply & Go Live")
                        .font(.spaceGrotesk(.bold, size: 14))
                        .foregroundColor(tapBpm > 0 ? .clickTeal : .textSecondary.opacity(0.3))
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background(tapBpm > 0 ? Color.clickTealDim : Color.padIdle)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(tapBpm > 0 ? Color.clickTeal.opacity(0.4) : Color.padBorder.opacity(0.3), lineWidth: 1)
                        )
                }
            }

            Spacer()

            Rectangle().fill(Color.padBorder.opacity(0.3)).frame(height: 1)

            // Long press BPM shortcut toggle
            HStack {
                Text("Long press BPM shortcut")
                    .font(.spaceGrotesk(.regular, size: 14))
                    .foregroundColor(.textPrimary)
                Spacer()
                Toggle("", isOn: Binding(
                    get: { appState.tapTempoLongPress },
                    set: { newValue in
                        appState.tapTempoLongPress = newValue
                        UserDefaults.standard.set(newValue, forKey: "tapTempoLongPress")
                    }
                ))
                    .toggleStyle(SwitchToggleStyle(tint: .clickTeal))
                    .labelsHidden()
            }
            .frame(height: 52)
            .padding(.horizontal, 12)
            .background(Color.padIdle)
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.darkBg)
        .navigationBarHidden(true)
        .onAppear { appState.showBottomBar = false }
    }

    private func handleTap() {
        let now = Date()
        if let last = tapTimes.last, now.timeIntervalSince(last) > 2.0 {
            tapTimes.removeAll()
        }
        tapTimes.append(now)
        if tapTimes.count > 8 { tapTimes.removeFirst() }
        tapCount = tapTimes.count
        if tapTimes.count >= 2 {
            let intervals = zip(tapTimes, tapTimes.dropFirst()).map { $1.timeIntervalSince($0) }
            let avg = intervals.reduce(0, +) / Double(intervals.count)
            tapBpm = max(30, min(240, Int(60.0 / avg)))
        }
    }

    private func applyBpmAndGoLive() {
        appState.liveBpm = tapBpm
        UserDefaults.standard.set(tapBpm, forKey: "liveBpm")
        dismiss()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            appState.selectedTab = 1
        }
    }
}
