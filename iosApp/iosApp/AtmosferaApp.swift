import SwiftUI
import AVFoundation

@main
struct AtmosferaApp: App {
    @StateObject private var appState: AppState

    init() {
        // Catch Kotlin/Native NSExceptions
        NSSetUncaughtExceptionHandler { exception in
            let info = """
            CRASH: \(exception.name.rawValue)
            Reason: \(exception.reason ?? "unknown")
            Stack: \(exception.callStackSymbols.joined(separator: "\n"))
            """
            UserDefaults.standard.set(info, forKey: "last_crash")
            NSLog("ATMOSFERA CRASH: %@", info)
        }

        NSLog("ATMOSFERA: App init starting...")

        // Configure audio session BEFORE any audio engine init
        do {
            try AVAudioSession.sharedInstance().setCategory(
                .playback,
                mode: .default,
                options: [.mixWithOthers]
            )
            try AVAudioSession.sharedInstance().setActive(true)
            NSLog("ATMOSFERA: Audio session configured OK")
        } catch {
            NSLog("ATMOSFERA: Audio session setup failed: %@", error.localizedDescription)
        }

        NSLog("ATMOSFERA: Creating AppState...")
        _appState = StateObject(wrappedValue: AppState())
        NSLog("ATMOSFERA: App init complete")
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appState)
                .onAppear {
                    if let crash = UserDefaults.standard.string(forKey: "last_crash") {
                        NSLog("ATMOSFERA PREVIOUS CRASH: %@", crash)
                    }
                }
        }
    }
}
