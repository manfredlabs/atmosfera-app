import SwiftUI

struct SoundPackListScreen: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) var dismiss
    @State private var packs: [SoundPackItem] = []
    @State private var selectedPack: SoundPackItem? = nil
    @State private var navigateToNew = false
    @State private var packToDelete: SoundPackItem? = nil

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Color.darkBg.ignoresSafeArea()

                VStack(spacing: 16) {
                    // Header with back button
                    ZStack {
                        HStack {
                            Button { dismiss() } label: {
                                Image(systemName: "chevron.left")
                                    .font(.system(size: 18, weight: .medium))
                                    .foregroundColor(.textSecondary)
                                    .frame(width: 44, height: 44)
                            }
                            Spacer()
                        }
                        Text("SOUND PACKS")
                            .font(.spaceGrotesk(.light, size: 22))
                            .foregroundColor(.textPrimary)
                            .tracking(2)
                    }
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
                Button {
                    Task {
                        await appState.createSoundPack(name: "New Sound Pack")
                        packs = appState.allPacks
                        if let newPack = packs.last(where: { !$0.isDefault }) {
                            selectedPack = newPack
                        }
                    }
                } label: {
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
        .sheet(item: $packToDelete) { pack in
            deleteSheet(pack)
        }
        .navigationDestination(item: $selectedPack) { pack in
            SoundPackScreen(pack: pack)
        }
        .task { await appState.refreshPacks(); packs = appState.allPacks }
        .onChange(of: appState.allPacks) { _, new in packs = new }
    }

    // MARK: - Pack Card

    private func packCard(_ pack: SoundPackItem) -> some View {
        let revealWidth: CGFloat = 70
        return SwipeRevealCard(
            revealWidth: revealWidth,
            enabled: !pack.isDefault,
            background: {
                HStack(spacing: 0) {
                    Spacer()
                    Button {
                        packToDelete = pack
                    } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 22))
                            .foregroundColor(Color(red: 1, green: 0.42, blue: 0.42))
                            .frame(minWidth: 70, maxWidth: 70, maxHeight: .infinity)
                    }
                    .background(Color.padIdle)
                }
                .background(Color.darkBg)
            },
            content: {
                DarkSurface(borderColor: .padBorder.opacity(0.3)) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(pack.name)
                                .font(.spaceGrotesk(.regular, size: 16))
                                .foregroundColor(.textPrimary)
                            let subtitle = pack.isDefault ? "Built-in" : pack.description
                            if !subtitle.isEmpty {
                                Text(subtitle)
                                    .font(.spaceGrotesk(.regular, size: 11))
                                    .foregroundColor(.textSecondary.opacity(0.6))
                            }
                        }
                        Spacer()
                    }
                    .padding(16)
                }
            }
        )
        .contentShape(Rectangle())
        .onTapGesture {
            appState.currentPackId = pack.id
            appState.saveSettings()
            Task { await appState.refreshPads(packId: pack.id) }
            if !pack.isDefault { selectedPack = pack }
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
                        .font(.spaceGrotesk(.regular, size: 13))
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
