import SwiftUI

struct ContentView: View {
    @EnvironmentObject var appState: AppState
    @State private var loadError: String? = nil

    private struct TabItem {
        let tag: Int
        let label: String
        let icon: String
    }

    private let tabs = [
        TabItem(tag: 0, label: "Config",   icon: "gearshape"),
        TabItem(tag: 1, label: "Live",     icon: "music.note"),
        TabItem(tag: 2, label: "Playlist", icon: "music.note.list"),
        TabItem(tag: 3, label: "Labs",     icon: "flask.fill"),
    ]

    var body: some View {
        ZStack {
            Color.darkBg.ignoresSafeArea()

            VStack(spacing: 0) {
                // Content area
                Group {
                    switch appState.selectedTab {
                    case 0:
                        NavigationStack {
                            SettingsScreen()
                        }
                    case 1:
                        HomeScreen()
                    case 2:
                        NavigationStack {
                            PlaylistScreen()
                        }
                    case 3:
                        LabsScreen()
                    default:
                        HomeScreen()
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                // Custom bottom bar (matches Android Material3 NavigationBar)
                if appState.showBottomBar {
                    customTabBar
                }
            }

            if let err = loadError {
                Color.black.opacity(0.8).ignoresSafeArea()
                VStack {
                    Text("Startup Error").font(.headline).foregroundColor(.red)
                    Text(err).font(.caption).foregroundColor(.white)
                        .padding()
                }
            }
        }
        .preferredColorScheme(.dark)
        .onChange(of: appState.playlistLocked) { _, locked in
            if locked { appState.selectedTab = 2 }
        }
        .task {
            do {
                try await appState.loadInitialData()
            } catch {
                loadError = error.localizedDescription
            }
        }
    }

    // MARK: - Custom Tab Bar

    private var visibleTabs: [TabItem] {
        appState.playlistLocked
            ? tabs.filter { $0.tag == 2 }
            : tabs
    }

    private var customTabBar: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                ForEach(visibleTabs, id: \.tag) { tab in
                    Button {
                        withAnimation(.easeInOut(duration: 0.15)) {
                            appState.selectedTab = tab.tag
                        }
                    } label: {
                        VStack(spacing: 4) {
                            ZStack {
                                // Indicator pill (PadActive) behind selected icon
                                if appState.selectedTab == tab.tag {
                                    Capsule()
                                        .fill(Color.padActive)
                                        .frame(width: 56, height: 28)
                                }

                                Image(systemName: tab.icon)
                                    .font(.system(size: 20))
                            }
                            .frame(height: 28)

                            Text(tab.label)
                                .font(.spaceGrotesk(.medium, size: 11))
                        }
                        .frame(maxWidth: .infinity)
                        .foregroundColor(
                            appState.selectedTab == tab.tag
                                ? .textPrimary
                                : .textSecondary.opacity(0.5)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.top, 10)
            .padding(.bottom, 6)
            .background(Color.padIdle)
        }
        .padding(.bottom, safeAreaBottom)
        .background(Color.padIdle)
    }

    private var safeAreaBottom: CGFloat {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first?.windows.first?.safeAreaInsets.bottom ?? 0
    }
}
