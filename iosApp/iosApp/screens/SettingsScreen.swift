import SwiftUI

private let channelOptions = ["mono","left","right"]

struct SettingsScreen: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        NavigationStack {
            Form {
                Section("Pad") {
                    HStack {
                        Text("Volume")
                        Slider(value: $appState.padVolume, in: 0...1)
                            .onChange(of: appState.padVolume) { _, v in
                                appState.liveAudio.padTargetVolume = v
                                appState.saveSettings()
                            }
                    }
                    HStack {
                        Text("Channel")
                        Spacer()
                        Picker("Channel", selection: $appState.padChannel) {
                            ForEach(channelOptions, id: \.self) { Text($0.capitalized).tag($0) }
                        }
                        .pickerStyle(.segmented)
                        .frame(maxWidth: 160)
                        .onChange(of: appState.padChannel) { _, _ in appState.saveSettings() }
                    }
                }

                Section("Click") {
                    HStack {
                        Text("Volume")
                        Slider(value: $appState.clickVolume, in: 0...1)
                            .onChange(of: appState.clickVolume) { _, _ in appState.saveSettings() }
                    }
                    HStack {
                        Text("Channel")
                        Spacer()
                        Picker("Channel", selection: $appState.clickChannel) {
                            ForEach(channelOptions, id: \.self) { Text($0.capitalized).tag($0) }
                        }
                        .pickerStyle(.segmented)
                        .frame(maxWidth: 160)
                        .onChange(of: appState.clickChannel) { _, _ in appState.saveSettings() }
                    }
                }

                Section("Fade") {
                    HStack {
                        Text("Fade In: \(appState.fadeInMs)ms")
                        Slider(value: Binding(
                            get: { Double(appState.fadeInMs) },
                            set: { appState.fadeInMs = Int64($0)
                                  appState.liveAudio.fadeInMs = Int64($0)
                                  appState.mixAudio.fadeInMs = Int64($0)
                                  appState.saveSettings() }
                        ), in: 0...5000, step: 100)
                    }
                    HStack {
                        Text("Fade Out: \(appState.fadeOutMs)ms")
                        Slider(value: Binding(
                            get: { Double(appState.fadeOutMs) },
                            set: { appState.fadeOutMs = Int64($0)
                                  appState.liveAudio.fadeOutMs = Int64($0)
                                  appState.mixAudio.fadeOutMs = Int64($0)
                                  appState.saveSettings() }
                        ), in: 0...5000, step: 100)
                    }
                }

                Section("About") {
                    LabeledContent("Version", value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—")
                }
            }
            .frame(maxWidth: 600)
            .navigationTitle("Settings")
        }
    }
}
