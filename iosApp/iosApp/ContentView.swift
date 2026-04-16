import SwiftUI

struct ContentView: View {
    @EnvironmentObject var appState: AppState
    @State private var selectedTab = 0
    @State private var loadError: String? = nil

    var body: some View {
        ZStack {
            Color.darkBg.ignoresSafeArea()

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

                NavigationStack {
                    SettingsScreen()
                }
                    .tabItem {
                        Label("Settings", systemImage: "gearshape")
                    }
                    .tag(3)
            }
            .tint(.ledAmber)

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
        .task {
            do {
                try await appState.loadInitialData()
            } catch {
                loadError = error.localizedDescription
            }
        }
    }
}
