import SwiftUI

struct ContentView: View {
    @EnvironmentObject var appState: AppState
    @State private var selectedTab = 0

    var body: some View {
        TabView(selection: $selectedTab) {
            HomeScreen()
                .tabItem {
                    Label("Live", systemImage: "music.note")
                }
                .tag(0)

            PlaylistScreen()
                .tabItem {
                    Label("Playlist", systemImage: "list.bullet")
                }
                .tag(1)

            MixStudioListScreen()
                .tabItem {
                    Label("Mix", systemImage: "slider.horizontal.3")
                }
                .tag(2)

            SettingsScreen()
                .tabItem {
                    Label("Settings", systemImage: "gearshape")
                }
                .tag(3)
        }
        .task {
            await appState.loadInitialData()
        }
    }
}
