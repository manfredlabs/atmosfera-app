import SwiftUI

struct SoundPackListScreen: View {
    @EnvironmentObject var appState: AppState
    @State private var packs: [SoundPackItem] = []
    @State private var selectedPack: SoundPackItem? = nil
    @State private var showCreate = false
    @State private var newPackName = ""
    @State private var packToDelete: SoundPackItem? = nil

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottomTrailing) {
                Color.darkBg.ignoresSafeArea()

                VStack(spacing: 16) {
                    ScreenHeader(title: "SOUND PACKS")
                        .padding(.top, 8)

                    if packs.isEmpty {
                        Spacer()
                        VStack(spacing: 12) {
                            Image(systemName: "speaker.wave.3")
                                .font(.system(size: 48))
                                .foregroundColor(.textSecondary.opacity(0.3))
                            Text("No sound packs")
                                .font(.spaceGrotesk(.regular, size: 16))
                                .foregroundColor(.textSecondary.opacity(0.5))
                            Text("Tap + to create one")
                                .font(.spaceGrotesk(.regular, size: 13))
                                .foregroundColor(.textSecondary.opacity(0.3))
                        }
                        Spacer()
                    } else {
                        ScrollView {
                            VStack(spacing: 8) {
                                ForEach(packs) { pack in
                                    packCard(pack)
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
                .frame(maxWidth: 600)
                .frame(maxWidth: .infinity)

                // FAB
                Button { showCreate = true } label: {
                    Image(systemName: "plus")
                        .font(.title2)
                        .foregroundColor(.textPrimary)
                        .frame(width: 56, height: 56)
                        .background(Color.padActive)
                        .clipShape(Circle())
                }
                .padding(24)
            }
            .navigationBarHidden(true)
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
            .sheet(item: $packToDelete) { pack in
                deleteSheet(pack)
            }
            .navigationDestination(item: $selectedPack) { pack in
                SoundPackScreen(pack: pack)
            }
            .task { await appState.refreshPacks(); packs = appState.allPacks }
            .onChange(of: appState.allPacks) { _, new in packs = new }
        }
    }

    // MARK: - Pack Card

    private func packCard(_ pack: SoundPackItem) -> some View {
        DarkSurface {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(pack.name)
                        .font(.spaceGrotesk(.regular, size: 16))
                        .foregroundColor(.textPrimary)
                    if pack.isDefault {
                        Text("Built-in")
                            .font(.spaceGrotesk(.regular, size: 11))
                            .foregroundColor(.textSecondary.opacity(0.6))
                    }
                }
                Spacer()
            }
            .padding(16)
        }
        .contentShape(Rectangle())
        .onTapGesture {
            appState.currentPackId = pack.id
            appState.saveSettings()
            Task { await appState.refreshPads(packId: pack.id) }
            if !pack.isDefault { selectedPack = pack }
        }
        .swipeActions(edge: .trailing) {
            if !pack.isDefault {
                Button(role: .destructive) {
                    packToDelete = pack
                } label: { Label("Delete", systemImage: "trash") }
            }
        }
        .contextMenu {
            if !pack.isDefault {
                Button(role: .destructive) { packToDelete = pack } label: {
                    Label("Delete", systemImage: "trash")
                }
            }
        }
    }

    // MARK: - Delete Sheet

    private func deleteSheet(_ pack: SoundPackItem) -> some View {
        VStack(spacing: 0) {
            Text("Delete \"\(pack.name)\"?")
                .font(.spaceGrotesk(.bold, size: 18))
                .foregroundColor(.textPrimary)
            Spacer().frame(height: 8)
            Text("All pads in this pack will be removed.")
                .font(.spaceGrotesk(.regular, size: 14))
                .foregroundColor(.textSecondary)
            Spacer().frame(height: 24)
            HStack(spacing: 12) {
                Button { packToDelete = nil } label: {
                    Text("CANCEL")
                        .font(.spaceGrotesk(.regular, size: 13))
                        .foregroundColor(.textSecondary)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.textSecondary, lineWidth: 1))
                }
                Button {
                    let id = pack.id
                    packToDelete = nil
                    Task {
                        await appState.deleteSoundPack(id: id)
                        packs = appState.allPacks
                    }
                } label: {
                    Text("DELETE")
                        .font(.spaceGrotesk(.bold, size: 13))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(Color(red: 1, green: 0.42, blue: 0.42))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
            }
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 32)
        .padding(.top, 20)
        .frame(maxWidth: .infinity)
        .background(Color.padIdle)
        .presentationDetents([.height(200)])
        .presentationDragIndicator(.visible)
    }
}
