import SwiftUI

struct MixStudioListScreen: View {
    @EnvironmentObject var appState: AppState
    @State private var projects: [MixProjectItem] = []
    @State private var selectedProject: MixProjectItem? = nil
    @State private var showCreate = false
    @State private var newProjectName = ""

    var body: some View {
        NavigationStack {
            projectList
                .frame(maxWidth: 600)
                .navigationTitle("Mix Studio")
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button { showCreate = true } label: { Image(systemName: "plus") }
                    }
                }
                .alert("New Mix", isPresented: $showCreate) {
                    TextField("Name", text: $newProjectName)
                    Button("Create") {
                        guard !newProjectName.isEmpty else { return }
                        let name = newProjectName
                        newProjectName = ""
                        Task {
                            await appState.createMixProject(name: name)
                            projects = appState.allMixProjects
                        }
                    }
                    Button("Cancel", role: .cancel) { newProjectName = "" }
                }
                .navigationDestination(item: $selectedProject) { project in
                    MixStudioEditorScreen(project: project)
                }
                .task { await appState.refreshMixProjects(); projects = appState.allMixProjects }
                .onChange(of: appState.allMixProjects) { _, new in projects = new }
        }
    }

    @ViewBuilder
    private var projectList: some View {
        if projects.isEmpty {
            ContentUnavailableView("No mixes", systemImage: "slider.horizontal.3",
                description: Text("Tap + to create a mix project."))
        } else {
            List {
                ForEach(projects) { project in
                    projectRow(project)
                }
            }
        }
    }

    private func projectRow(_ project: MixProjectItem) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(project.name).font(.headline)
                if project.inPlaylist {
                    Label("In playlist", systemImage: "checkmark.circle.fill")
                        .font(.caption).foregroundStyle(.green)
                }
            }
            Spacer()
            Image(systemName: "chevron.right").foregroundStyle(.secondary)
        }
        .contentShape(Rectangle())
        .onTapGesture { selectedProject = project }
        .swipeActions(edge: .trailing) {
            Button(role: .destructive) {
                Task {
                    await appState.deleteMixProject(id: project.id)
                    projects = appState.allMixProjects
                }
            } label: { Label("Delete", systemImage: "trash") }
        }
        .swipeActions(edge: .leading) {
            Button {
                Task {
                    await appState.toggleMixProjectInPlaylist(id: project.id)
                    projects = appState.allMixProjects
                }
            } label: {
                Label(project.inPlaylist ? "Remove" : "Playlist",
                      systemImage: project.inPlaylist ? "minus.circle" : "plus.circle")
            }
            .tint(project.inPlaylist ? .orange : .green)
        }
    }
}
