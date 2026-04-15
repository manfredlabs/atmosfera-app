import SwiftUI

struct SoundPackListScreen: View {
    @EnvironmentObject var appState: AppState
    @State private var packs: [SoundPackItem] = []
    @State private var selectedPack: SoundPackItem? = nil
    @State private var showCreate = false
    @State private var newPackName = ""

    var body: some View {
        NavigationStack {
            List {
                ForEach(packs) { pack in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 6) {
                                Text(pack.name).font(.headline)
                                if pack.isDefault {
                                    Text("Default")
                                        .font(.caption2)
                                        .padding(.horizontal, 6).padding(.vertical, 2)
                                        .background(Color.accentColor.opacity(0.2))
                                        .cornerRadius(4)
                                }
                            }
                        }
                        Spacer()
                        if appState.currentPackId == pack.id {
                            Image(systemName: "checkmark").foregroundStyle(.accentColor)
                        }
                    }
                    .contentShape(Rectangle())
                    .onTapGesture {
                        appState.currentPackId = pack.id
                        appState.saveSettings()
                        Task { await appState.refreshPads(packId: pack.id) }
                        selectedPack = pack
                    }
                    .swipeActions(edge: .trailing) {
                        if !pack.isDefault {
                            Button(role: .destructive) {
                                Task {
                                    await appState.deleteSoundPack(id: pack.id)
                                    packs = appState.allPacks
                                }
                            } label: { Label("Delete", systemImage: "trash") }
                        }
                    }
                }
            }
            .frame(maxWidth: 600)
            .navigationTitle("Sound Packs")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { showCreate = true } label: { Image(systemName: "plus") }
                }
            }
            .alert("New Sound Pack", isPresented: $showCreate) {
                TextField("Name", text: $newPackName)
                Button("Create") {
                    guard !newPackName.isEmpty else { return }
                    let name = newPackName
                    newPackName = ""
                    Task {
                        await appState.createSoundPack(name: name)
                        packs = appState.allPacks
                    }
                }
                Button("Cancel", role: .cancel) { newPackName = "" }
            }
            .navigationDestination(item: $selectedPack) { pack in
                SoundPackScreen(pack: pack)
            }
            .task { await appState.refreshPacks(); packs = appState.allPacks }
            .onChange(of: appState.allPacks) { _, new in packs = new }
        }
    }
}
