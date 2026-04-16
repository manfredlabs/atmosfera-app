import SwiftUI

struct ContentView: View {
    @EnvironmentObject var appState: AppState
    @State private var selectedTab = 1
    @State private var loadError: String? = nil

    init() {
        // Match Android NavigationBar: containerColor=PadIdle, indicator=PadActive
        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(red: 26/255, green: 26/255, blue: 26/255, alpha: 1) // PadIdle

        let normal = UITabBarItemAppearance()
        normal.normal.iconColor = UIColor(red: 102/255, green: 102/255, blue: 102/255, alpha: 0.5) // TextSecondary@0.5
        normal.normal.titleTextAttributes = [
            .foregroundColor: UIColor(red: 102/255, green: 102/255, blue: 102/255, alpha: 0.5)
        ]
        normal.selected.iconColor = UIColor(red: 224/255, green: 224/255, blue: 224/255, alpha: 1) // TextPrimary
        normal.selected.titleTextAttributes = [
            .foregroundColor: UIColor(red: 224/255, green: 224/255, blue: 224/255, alpha: 1)
        ]

        appearance.stackedLayoutAppearance = normal
        appearance.inlineLayoutAppearance = normal
        appearance.compactInlineLayoutAppearance = normal

        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
    }

    var body: some View {
        ZStack {
            Color.darkBg.ignoresSafeArea()

            TabView(selection: $selectedTab) {
                NavigationStack {
                    SettingsScreen()
                }
                    .tabItem {
                        Label("Config", systemImage: "gearshape")
                    }
                    .tag(0)

                HomeScreen()
                    .tabItem {
                        Label("Live", systemImage: "music.note")
                    }
                    .tag(1)

                PlaylistScreen()
                    .tabItem {
                        Label("Playlist", systemImage: "list.bullet.rectangle")
                    }
                    .tag(2)

                LabsScreen()
                    .tabItem {
                        Label("Labs", systemImage: "flask.fill")
                    }
                    .tag(3)
            }
            .tint(.textPrimary)

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
