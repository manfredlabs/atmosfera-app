import Foundation
import Combine
import Shared

// MARK: - Swift mirror types
// Pure Swift types used in SwiftUI views, mapped from Kotlin objects via KVC.

struct SongItem: Identifiable, Equatable, Hashable {
    var id: Int64
    var name: String
    var note: String
    var isMajor: Bool
    var bpm: Int
    var accents: String
    var padEnabled: Bool
    var clickEnabled: Bool
    var createdAt: Int64
    var sortOrder: Int
    var padMode: String
    var soundPackId: Int64
    var padVolume: Float
    var padChannel: String
    var clickVolume: Float
    var clickChannel: String

    var accentList: [Int] {
        accents.split(separator: ",").compactMap { Int($0.trimmingCharacters(in: .whitespaces)) }
    }
}

struct SoundPackItem: Identifiable, Equatable, Hashable {
    var id: Int64
    var name: String
    var description: String
    var isDefault: Bool
    var createdAt: Int64
}

struct SoundPadItem: Identifiable, Equatable {
    var id: Int64
    var packId: Int64
    var note: String
    var mode: String
    var filePath: String
    var createdAt: Int64
}

struct MixProjectItem: Identifiable, Equatable, Hashable {
    var id: Int64
    var name: String
    var createdAt: Int64
    var sortOrder: Int
    var inPlaylist: Bool
    var padVolume: Float
    var padChannel: String
    var clickVolume: Float
    var clickChannel: String
}

struct MixTrackItem: Identifiable, Equatable {
    var id: Int64
    var projectId: Int64
    var trackType: String
    var label: String
    var volume: Float
    var channel: String
    var sortOrder: Int
    var note: String?
    var padMode: String?
    var soundPackId: Int64?
    var bpm: Int?
    var accents: String?
    var filePath: String?
    var fileName: String?
}

// MARK: - AppState

@MainActor
class AppState: ObservableObject {
    // DB
    private let db: AtmosDb
    private let dbHelper: IosDbHelper

    // Published state
    @Published var allSongs: [SongItem] = []
    @Published var allPacks: [SoundPackItem] = []
    @Published var currentPackId: Int64 = -1
    @Published var currentPads: [SoundPadItem] = []
    @Published var allMixProjects: [MixProjectItem] = []

    // Audio engines
    let liveAudio = LiveAudioPlayerIos()
    let mixAudio = MixAudioPlayerIos()

    // Live screen state
    @Published var playingNote: String? = nil
    @Published var playingSongId: Int64? = nil
    @Published var playingMixId: Int64? = nil

    // Settings
    @Published var padVolume: Float = 0.5
    @Published var clickVolume: Float = 0.5
    @Published var padChannel: String = "mono"
    @Published var clickChannel: String = "mono"
    @Published var fadeInMs: Int64 = 2000
    @Published var fadeOutMs: Int64 = 1500
    @Published var timeSignature: String = "4/4"
    @Published var clickEnabled: Bool = false
    @Published var selectedTab: Int = 1
    @Published var liveBpm: Int = 90
    @Published var tapTempoLongPress: Bool = true
    @Published var showBottomBar: Bool = true
    @Published var playlistLocked: Bool = false

    private var cancellables = Set<AnyCancellable>()
    private let defaults = UserDefaults.standard

    init() {
        print("AppState: creating DatabaseDriverFactory...")
        let factory = DatabaseDriverFactory()
        print("AppState: creating AtmosDb...")
        db = AtmosDb(driverFactory: factory)
        print("AppState: creating IosDbHelper...")
        dbHelper = IosDbHelper(db: db)
        print("AppState: loading settings...")
        loadSettings()
        liveAudio.fadeInMs = fadeInMs
        liveAudio.fadeOutMs = fadeOutMs
        mixAudio.fadeInMs = fadeInMs
        mixAudio.fadeOutMs = fadeOutMs
        print("AppState: init complete")
    }

    func loadInitialData() async throws {
        await ensureDefaultPack()
        await refreshSongs()
        await refreshPacks()
        await refreshMixProjects()
        if currentPackId == -1, let def = allPacks.first(where: { $0.isDefault }) ?? allPacks.first {
            currentPackId = def.id
            await refreshPads(packId: def.id)
        }
    }

    // MARK: - Refresh

    func refreshSongs() async {
        do {
            let raw = try await dbHelper.getAllSongs()
            allSongs = (raw as NSArray).compactMap { songFromKotlin($0) }
                .sorted { $0.sortOrder < $1.sortOrder }
        } catch {
            print("refreshSongs: \(error)")
        }
    }

    func refreshPacks() async {
        do {
            let raw = try await dbHelper.getAllPacks()
            allPacks = (raw as NSArray).compactMap { packFromKotlin($0) }
        } catch {
            print("refreshPacks: \(error)")
        }
    }

    func refreshPads(packId: Int64) async {
        currentPackId = packId
        do {
            let raw = try await dbHelper.getPadsForPack(packId: packId)
            currentPads = (raw as NSArray).compactMap { padFromKotlin($0) }
        } catch {
            print("refreshPads: \(error)")
        }
    }

    func getPadsForPack(packId: Int64) async -> [SoundPadItem] {
        do {
            let raw = try await dbHelper.getPadsForPack(packId: packId)
            return (raw as NSArray).compactMap { padFromKotlin($0) }
        } catch {
            print("getPadsForPack: \(error)")
            return []
        }
    }

    func refreshMixProjects() async {
        do {
            let raw = try await dbHelper.getMixProjects()
            allMixProjects = (raw as NSArray).compactMap { mixProjectFromKotlin($0) }
                .sorted { $0.sortOrder < $1.sortOrder }
        } catch {
            print("refreshMixProjects: \(error)")
        }
    }

    func getTracksForProject(projectId: Int64) async -> [MixTrackItem] {
        do {
            let raw = try await dbHelper.getTracksForProject(projectId: projectId)
            return (raw as NSArray).compactMap { mixTrackFromKotlin($0) }
                .sorted { $0.sortOrder < $1.sortOrder }
        } catch {
            print("getTracksForProject: \(error)")
            return []
        }
    }

    // MARK: - Song CRUD

    func insertSong(_ song: SongItem) async {
        do {
            try await dbHelper.insertSong(
                name: song.name, note: song.note, isMajor: song.isMajor,
                bpm: Int32(song.bpm), accents: song.accents,
                padEnabled: song.padEnabled, clickEnabled: song.clickEnabled,
                sortOrder: Int32(song.sortOrder), padMode: song.padMode,
                soundPackId: song.soundPackId, padVolume: song.padVolume,
                padChannel: song.padChannel, clickVolume: song.clickVolume,
                clickChannel: song.clickChannel
            )
        } catch { print("insertSong: \(error)") }
        await refreshSongs()
    }

    func updateSong(_ song: SongItem) async {
        do {
            try await dbHelper.updateSong(
                id: song.id, name: song.name, note: song.note, isMajor: song.isMajor,
                bpm: Int32(song.bpm), accents: song.accents,
                padEnabled: song.padEnabled, clickEnabled: song.clickEnabled,
                createdAt: song.createdAt, sortOrder: Int32(song.sortOrder),
                padMode: song.padMode, soundPackId: song.soundPackId,
                padVolume: song.padVolume, padChannel: song.padChannel,
                clickVolume: song.clickVolume, clickChannel: song.clickChannel
            )
        } catch { print("updateSong: \(error)") }
        await refreshSongs()
    }

    func deleteSong(_ song: SongItem) async {
        do { try await dbHelper.deleteSongById(id: song.id) }
        catch { print("deleteSong: \(error)") }
        await refreshSongs()
    }

    /// Persists new sort order by updating each song individually.
    func updateSongOrder(_ songs: [SongItem]) async {
        for (idx, song) in songs.enumerated() {
            do {
                try await dbHelper.updateSong(
                    id: song.id, name: song.name, note: song.note, isMajor: song.isMajor,
                    bpm: Int32(song.bpm), accents: song.accents,
                    padEnabled: song.padEnabled, clickEnabled: song.clickEnabled,
                    createdAt: song.createdAt, sortOrder: Int32(idx),
                    padMode: song.padMode, soundPackId: song.soundPackId,
                    padVolume: song.padVolume, padChannel: song.padChannel,
                    clickVolume: song.clickVolume, clickChannel: song.clickChannel
                )
            } catch { print("updateSongOrder[\(idx)]: \(error)") }
        }
        await refreshSongs()
    }

    // MARK: - SoundPack CRUD

    @discardableResult
    func createSoundPack(name: String, description: String = "") async -> Int64 {
        var newId: Int64 = -1
        do {
            let result = try await dbHelper.insertSoundPack(name: name, description: description, isDefault: false)
            newId = (result as? NSNumber)?.int64Value ?? -1
        }
        catch { print("createSoundPack: \(error)") }
        await refreshPacks()
        return newId
    }

    func deleteSoundPack(id: Int64) async {
        do { try await dbHelper.deleteSoundPackById(id: id) }
        catch { print("deleteSoundPack: \(error)") }
        await refreshPacks()
    }

    func renameSoundPack(id: Int64, name: String) async {
        do { try await dbHelper.renameSoundPack(id: id, name: name) }
        catch { print("renameSoundPack: \(error)") }
        await refreshPacks()
    }

    func updateSoundPackDescription(id: Int64, description: String) async {
        do { try await dbHelper.updateSoundPackDescription(id: id, description: description) }
        catch { print("updateSoundPackDescription: \(error)") }
        await refreshPacks()
    }

    func setDefaultPack(id: Int64) async {
        do { try await dbHelper.setDefaultPack(id: id) }
        catch { print("setDefaultPack: \(error)") }
        await refreshPacks()
    }

    func assignPad(packId: Int64, note: String, mode: String, filePath: String) async {
        do { _ = try await dbHelper.assignPad(packId: packId, note: note, mode: mode, filePath: filePath) }
        catch { print("assignPad: \(error)") }
        await refreshPads(packId: packId)
    }

    func removePadAssignment(packId: Int64, note: String, mode: String) async {
        do { try await dbHelper.removePadAssignment(packId: packId, note: note, mode: mode) }
        catch { print("removePadAssignment: \(error)") }
        await refreshPads(packId: packId)
    }

    // MARK: - MixProject CRUD

    @discardableResult
    func createMixProject(name: String) async -> Int64 {
        do {
            let result = try await dbHelper.insertMixProject(name: name)
            await refreshMixProjects()
            // KMP Long -> NSNumber in ObjC bridge
            if let n = result as? NSNumber { return n.int64Value }
            if let i = result as? Int64 { return i }
        } catch { print("createMixProject: \(error)") }
        return 0
    }

    func deleteMixProject(id: Int64) async {
        do { try await dbHelper.deleteMixProjectById(id: id) }
        catch { print("deleteMixProject: \(error)") }
        await refreshMixProjects()
    }

    func toggleMixProjectInPlaylist(id: Int64) async {
        do { try await dbHelper.toggleMixProjectInPlaylist(id: id) }
        catch { print("toggleMixProjectInPlaylist: \(error)") }
        await refreshMixProjects()
    }

    func renameMixProject(id: Int64, name: String) async {
        do { try await dbHelper.renameMixProject(id: id, name: name) }
        catch { print("renameMixProject: \(error)") }
        await refreshMixProjects()
    }

    func updateMixProjectSortOrder(id: Int64, sortOrder: Int32) async {
        do { try await dbHelper.updateMixProjectSortOrder(id: id, sortOrder: sortOrder) }
        catch { print("updateMixProjectSortOrder: \(error)") }
    }

    // MARK: - MixTrack CRUD

    @discardableResult
    func addMixTrack(
        projectId: Int64, trackType: String, label: String, volume: Float = 0.5,
        channel: String = "mono", sortOrder: Int = 0, note: String? = nil,
        padMode: String? = nil, soundPackId: Int64? = nil, bpm: Int? = nil,
        accents: String? = nil, filePath: String? = nil, fileName: String? = nil
    ) async -> Int64 {
        do {
            let result = try await dbHelper.insertMixTrack(
                projectId: projectId, trackType: trackType, label: label,
                volume: volume, channel: channel, sortOrder: Int32(sortOrder),
                note: note, padMode: padMode, soundPackId: soundPackId.map { KotlinLong(value: $0) },
                bpm: bpm.map { KotlinInt(value: Int32($0)) }, accents: accents,
                filePath: filePath, fileName: fileName
            )
            if let n = result as? NSNumber { return n.int64Value }
            if let i = result as? Int64 { return i }
        } catch { print("addMixTrack: \(error)") }
        return 0
    }

    func deleteMixTrack(trackId: Int64, projectId: Int64) async {
        do { try await dbHelper.deleteMixTrackById(trackId: trackId, projectId: projectId) }
        catch { print("deleteMixTrack: \(error)") }
    }

    func updateMixTrackVolume(trackId: Int64, projectId: Int64, volume: Float) async {
        do { try await dbHelper.updateMixTrackVolume(trackId: trackId, projectId: projectId, volume: volume) }
        catch { print("updateMixTrackVolume: \(error)") }
    }

    func updateMixTrackChannel(trackId: Int64, projectId: Int64, channel: String) async {
        do { try await dbHelper.updateMixTrackChannel(trackId: trackId, projectId: projectId, channel: channel) }
        catch { print("updateMixTrackChannel: \(error)") }
    }

    // MARK: - Settings persistence

    func saveSettings() {
        defaults.set(padVolume, forKey: "padVolume")
        defaults.set(clickVolume, forKey: "clickVolume")
        defaults.set(padChannel, forKey: "padChannel")
        defaults.set(clickChannel, forKey: "clickChannel")
        defaults.set(fadeInMs, forKey: "fadeInMs")
        defaults.set(fadeOutMs, forKey: "fadeOutMs")
        defaults.set(currentPackId, forKey: "currentPackId")
        defaults.set(timeSignature, forKey: "timeSignature")
    }

    func loadSettings() {
        padVolume = defaults.float(forKey: "padVolume").nonZeroOr(0.5)
        clickVolume = defaults.float(forKey: "clickVolume").nonZeroOr(0.5)
        padChannel = defaults.string(forKey: "padChannel") ?? "mono"
        clickChannel = defaults.string(forKey: "clickChannel") ?? "mono"
        fadeInMs = Int64(defaults.integer(forKey: "fadeInMs")).nonZeroOr(2000)
        fadeOutMs = Int64(defaults.integer(forKey: "fadeOutMs")).nonZeroOr(1500)
        currentPackId = Int64(defaults.integer(forKey: "currentPackId").nonZeroOr(-1))
        timeSignature = defaults.string(forKey: "timeSignature") ?? "4/4"
        liveBpm = defaults.integer(forKey: "liveBpm").nonZeroOr(90)
        tapTempoLongPress = defaults.object(forKey: "tapTempoLongPress") == nil ? true : defaults.bool(forKey: "tapTempoLongPress")
    }

    // MARK: - Default pack bootstrap

    private func ensureDefaultPack() async {
        guard !defaults.bool(forKey: "default_pack_checked") else { return }
        do {
            let raw = try await dbHelper.getAllPacks()
            let packs = (raw as NSArray).compactMap { packFromKotlin($0) }
            if packs.isEmpty {
                _ = try await dbHelper.insertSoundPack(
                    name: "Atmos", description: "Default sound pack", isDefault: true
                )
            }
        } catch { print("ensureDefaultPack: \(error)") }
        defaults.set(true, forKey: "default_pack_checked")
    }

    // MARK: - Kotlin → Swift mapping (direct property access)

    func songFromKotlin(_ obj: Any) -> SongItem? {
        guard let s = obj as? Song else { return nil }
        return SongItem(
            id: s.id,
            name: s.name,
            note: s.note,
            isMajor: s.isMajor,
            bpm: Int(s.bpm),
            accents: s.accents,
            padEnabled: s.padEnabled,
            clickEnabled: s.clickEnabled,
            createdAt: s.createdAt,
            sortOrder: Int(s.sortOrder),
            padMode: s.padMode,
            soundPackId: s.soundPackId,
            padVolume: s.padVolume,
            padChannel: s.padChannel,
            clickVolume: s.clickVolume,
            clickChannel: s.clickChannel
        )
    }

    func packFromKotlin(_ obj: Any) -> SoundPackItem? {
        guard let pack = obj as? SoundPack else { return nil }
        return SoundPackItem(
            id: pack.id,
            name: pack.name,
            description: pack.description_,
            isDefault: pack.isDefault,
            createdAt: pack.createdAt
        )
    }

    func padFromKotlin(_ obj: Any) -> SoundPadItem? {
        guard let p = obj as? SoundPad else { return nil }
        return SoundPadItem(
            id: p.id,
            packId: p.packId,
            note: p.note,
            mode: p.mode,
            filePath: p.filePath,
            createdAt: p.createdAt
        )
    }

    func mixProjectFromKotlin(_ obj: Any) -> MixProjectItem? {
        guard let m = obj as? MixProject else { return nil }
        return MixProjectItem(
            id: m.id,
            name: m.name,
            createdAt: m.createdAt,
            sortOrder: Int(m.sortOrder),
            inPlaylist: m.inPlaylist,
            padVolume: m.padVolume,
            padChannel: m.padChannel,
            clickVolume: m.clickVolume,
            clickChannel: m.clickChannel
        )
    }

    func mixTrackFromKotlin(_ obj: Any) -> MixTrackItem? {
        guard let t = obj as? MixTrack else { return nil }
        return MixTrackItem(
            id: t.id,
            projectId: t.projectId,
            trackType: t.trackType,
            label: t.label,
            volume: t.volume,
            channel: t.channel,
            sortOrder: Int(t.sortOrder),
            note: t.note,
            padMode: t.padMode,
            soundPackId: t.soundPackId.map { $0.int64Value },
            bpm: t.bpm.map { Int($0.int32Value) },
            accents: t.accents,
            filePath: t.filePath,
            fileName: t.fileName
        )
    }
}

// MARK: - Numeric helpers

private extension Float {
    func nonZeroOr(_ fallback: Float) -> Float { self == 0 ? fallback : self }
}
private extension Int64 {
    func nonZeroOr(_ fallback: Int64) -> Int64 { self == 0 ? fallback : self }
}
private extension Int {
    func nonZeroOr(_ fallback: Int) -> Int { self == 0 ? fallback : self }
}
