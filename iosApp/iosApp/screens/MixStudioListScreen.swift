import SwiftUI

struct MixStudioListScreen: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) private var dismiss
    @State private var projects: [MixProjectItem] = []
    @State private var selectedProject: MixProjectItem? = nil
    @State private var projectToDelete: MixProjectItem? = nil

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Color.darkBg.ignoresSafeArea()

            VStack(spacing: 16) {
                ZStack {
                    HStack {
                        Button { dismiss() } label: {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 18, weight: .medium))
                                .foregroundColor(.textSecondary)
                        }
                        Spacer()
                    }
                    ScreenHeader(title: "MIX STUDIO")
                }
                .padding(.top, 8)

                if projects.isEmpty {
                    Spacer()
                    VStack(spacing: 12) {
                        Image(systemName: "waveform")
                            .font(.system(size: 48))
                            .foregroundColor(.textSecondary.opacity(0.3))
                        Text("No projects yet")
                            .font(.spaceGrotesk(.regular, size: 16))
                            .foregroundColor(.textSecondary.opacity(0.5))
                        Text("Tap + to create your first mix")
                            .font(.spaceGrotesk(.regular, size: 13))
                            .foregroundColor(.textSecondary.opacity(0.3))
                    }
                    Spacer()
                } else {
                    ScrollView {
                        LazyVStack(spacing: 10) {
                            ForEach(projects) { project in
                                projectCard(project)
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
                    await appState.createMixProject(name: "New Mix")
                    projects = appState.allMixProjects
                    if let newProject = projects.last {
                        selectedProject = newProject
                    }
                }
            } label: {
                Image(systemName: "plus")
                    .font(.title2)
                    .foregroundColor(.textPrimary)
                    .frame(width: 56, height: 56)
                    .background(Color.labsPurple)
                    .clipShape(Circle())
            }
            .padding(24)
        }
        .navigationBarHidden(true)
        .onAppear { appState.showBottomBar = false }
        .sheet(item: $projectToDelete){ project in
            deleteSheet(project)
        }
        .navigationDestination(item: $selectedProject) { project in
            MixStudioEditorScreen(project: project)
        }
        .task { await appState.refreshMixProjects(); projects = appState.allMixProjects }
        .onChange(of: appState.allMixProjects) { _, new in projects = new }
    }

    // MARK: - Project Card

    private func projectCard(_ project: MixProjectItem) -> some View {
        DarkSurface {
            HStack(spacing: 14) {
                // Purple icon
                ZStack {
                    RoundedRectangle(cornerRadius: 10)
                        .fill(Color.labsPurple.opacity(0.15))
                        .frame(width: 44, height: 44)
                    Image(systemName: "waveform")
                        .font(.system(size: 20))
                        .foregroundColor(.labsPurple)
                }

                VStack(alignment: .leading, spacing: 3) {
                    Text(project.name)
                        .font(.spaceGrotesk(.regular, size: 18))
                        .foregroundColor(.textPrimary)
                }

                Spacer()

                if project.inPlaylist {
                    Image(systemName: "text.badge.checkmark")
                        .font(.system(size: 16))
                        .foregroundColor(.labsPurple.opacity(0.6))
                }

                Image(systemName: "chevron.right")
                    .font(.system(size: 14))
                    .foregroundColor(.textSecondary.opacity(0.4))
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 16)
        }
        .contentShape(Rectangle())
        .onTapGesture { selectedProject = project }
        .contextMenu {
            Button { selectedProject = project } label: {
                Label("Edit", systemImage: "pencil")
            }
            Button {
                Task {
                    await appState.toggleMixProjectInPlaylist(id: project.id)
                    projects = appState.allMixProjects
                }
            } label: {
                Label(project.inPlaylist ? "Remove from Playlist" : "Add to Playlist",
                      systemImage: project.inPlaylist ? "minus.circle" : "plus.circle")
            }
            Button(role: .destructive) { projectToDelete = project } label: {
                Label("Delete", systemImage: "trash")
            }
        }
    }

    // MARK: - Delete Sheet

    private func deleteSheet(_ project: MixProjectItem) -> some View {
        VStack(spacing: 0) {
            Text("Delete \"\(project.name)\"?")
                .font(.spaceGrotesk(.bold, size: 18))
                .foregroundColor(.textPrimary)
            Spacer().frame(height: 8)
            Text("This action cannot be undone.")
                .font(.spaceGrotesk(.regular, size: 14))
                .foregroundColor(.textSecondary)
            Spacer().frame(height: 24)
            HStack(spacing: 12) {
                Button { projectToDelete = nil } label: {
                    Text("CANCEL")
                        .font(.spaceGrotesk(.regular, size: 13))
                        .foregroundColor(.textSecondary)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.textSecondary, lineWidth: 1))
                }
                Button {
                    let id = project.id
                    projectToDelete = nil
                    Task {
                        await appState.deleteMixProject(id: id)
                        projects = appState.allMixProjects
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
