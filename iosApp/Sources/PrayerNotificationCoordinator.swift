import ActivityKit
import AVFoundation
import Foundation
import UserNotifications

private let prayerSchedulePayloadKey = "ios_prayer_schedule_payload"
private let prayerNotificationPrefix = "prayer."
private let prayerPushTokenSnapshotKey = "ios_prayer_push_token_snapshot"

private struct PrayerPushTokenSnapshot: Codable, Equatable {
    let schemaVersion: Int
    var bundleIdentifier: String
    var apnsDeviceToken: String?
    var liveActivityTokens: [String: String]
    var pushToStartToken: String?
    var updatedAt: Date
}

private struct PrayerSchedulePayload: Decodable {
    let version: Int
    let locationName: String
    let timeZoneOffset: Double?
    let notificationsEnabled: Bool
    let liveActivitiesEnabled: Bool?
    let soundEnabled: Bool
    let days: [PrayerDay]
}

private struct PrayerDay: Decodable {
    let date: String
    let prayers: [PrayerEntry]
}

private struct PrayerEntry: Decodable {
    let name: String
    let hour: Int
    let minute: Int
    let enabled: Bool
    let priorMinutes: Int
    let activeMinutes: Int
    let adhanEnabled: Bool?
    let adhanVolume: Int?
    let localName: String?
}

private struct DatedPrayer {
    let entry: PrayerEntry
    let date: Date
}

@MainActor
final class PrayerNotificationCoordinator: NSObject, UNUserNotificationCenterDelegate {
    static let shared = PrayerNotificationCoordinator()

    private let center = UNUserNotificationCenter.current()
    private var defaultsObserver: NSObjectProtocol?
    private var lastPayload: String?
    private var hasStarted = false
    private var activityUpdatesTask: Task<Void, Never>?
    private var activityPushTokenTasks: [String: Task<Void, Never>] = [:]
    private var activityEndTasks: [String: Task<Void, Never>] = [:]
    private var pushToStartTokenTask: Task<Void, Never>?
    private var uploadTask: Task<Void, Never>?
    private var tokenSnapshot: PrayerPushTokenSnapshot

    private override init() {
        tokenSnapshot = Self.loadTokenSnapshot()
        super.init()
    }

    func start() {
        guard !hasStarted else { return }
        hasStarted = true
        center.delegate = self
        observeLiveActivityTokens()
        uploadTokenSnapshot(tokenSnapshot)
        refresh()
        defaultsObserver = NotificationCenter.default.addObserver(
            forName: UserDefaults.didChangeNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in self?.refresh() }
        }
    }

    func didRegisterForRemoteNotifications(deviceToken: Data) {
        updateTokenSnapshot { snapshot in
            snapshot.apnsDeviceToken = deviceToken.hexString
        }
    }

    func didFailToRegisterForRemoteNotifications(error: Error) {
        NSLog("APNs registration failed: %@", error.localizedDescription)
    }

    func refresh() {
        guard let encoded = UserDefaults.standard.string(forKey: prayerSchedulePayloadKey),
              encoded != lastPayload,
              let data = encoded.data(using: .utf8),
              let payload = try? JSONDecoder().decode(PrayerSchedulePayload.self, from: data),
              payload.version == 1 else { return }

        lastPayload = encoded
        let prayers = datedPrayers(from: payload)
        updateLiveActivity(payload: payload, prayers: prayers)

        guard payload.notificationsEnabled else {
            removePrayerNotifications()
            return
        }

        Task { [weak self] in
            guard let self,
                  (try? await center.requestAuthorization(
                    options: [.alert, .badge, .sound]
                  )) == true else { return }
            await replaceNotifications(payload: payload, prayers: prayers)
        }
    }

    private func datedPrayers(from payload: PrayerSchedulePayload) -> [DatedPrayer] {
        allDatedPrayers(from: payload)
            .filter { $0.entry.enabled && $0.date > Date() }
    }

    /// Every dated prayer in the published schedule, unfiltered — the Live
    /// Activity needs yesterday's/earlier prayers to find the segment start
    /// for its approach progress bar.
    private func allDatedPrayers(from payload: PrayerSchedulePayload) -> [DatedPrayer] {
        let timeZone = scheduleTimeZone(for: payload)
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        return payload.days.flatMap { day -> [DatedPrayer] in
            let dateParts = day.date.split(separator: "-").compactMap { Int($0) }
            guard dateParts.count == 3 else { return [] }
            return day.prayers.compactMap { prayer in
                var components = DateComponents()
                components.calendar = calendar
                components.timeZone = timeZone
                components.year = dateParts[0]
                components.month = dateParts[1]
                components.day = dateParts[2]
                components.hour = prayer.hour
                components.minute = prayer.minute
                guard let date = calendar.date(from: components) else { return nil }
                return DatedPrayer(entry: prayer, date: date)
            }
        }
        .sorted { $0.date < $1.date }
    }

    private static let liveActivityTimeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        return formatter
    }()

    /// The Android-parity Live Activity state: prayer name, its Arabic name,
    /// the wall-clock time, the inter-prayer segment for the approach bar,
    /// and today's full schedule strip.
    private func liveActivityState(
        payload: PrayerSchedulePayload,
        next: DatedPrayer
    ) -> PrayerActivityAttributes.ContentState {
        let timeZone = scheduleTimeZone(for: payload)
        let formatter = Self.liveActivityTimeFormatter.copy() as! DateFormatter
        formatter.timeZone = timeZone

        let all = allDatedPrayers(from: payload)
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let today = all.filter { calendar.isDate($0.date, inSameDayAs: next.date) }
        let times = today.map { prayer in
            PrayerActivityAttributes.PrayerTime(
                name: prayer.entry.name,
                arabic: prayer.entry.localName ?? "",
                time: formatter.string(from: prayer.date),
                isNext: prayer.date == next.date
            )
        }
        return PrayerActivityAttributes.ContentState(
            prayerName: next.entry.name,
            prayerArabicName: next.entry.localName ?? "",
            prayerTime: formatter.string(from: next.date),
            prayerDate: next.date,
            previousPrayerDate: all.last(where: { $0.date < next.date })?.date,
            activeUntil: next.date.addingTimeInterval(
                TimeInterval(60 * next.entry.activeMinutes)
            ),
            times: times
        )
    }

    private func replaceNotifications(
        payload: PrayerSchedulePayload,
        prayers: [DatedPrayer]
    ) async {
        let requests = await center.pendingNotificationRequests()
        let identifiers = requests.map(\.identifier).filter {
            $0.hasPrefix(prayerNotificationPrefix)
        }
        center.removePendingNotificationRequests(withIdentifiers: identifiers)

        let events = prayers.flatMap { prayer -> [(String, Date, String, String, Bool, Int?)] in
            let dayKey = Self.identifierDateFormatter.string(from: prayer.date)
            let start = (
                "\(prayerNotificationPrefix)\(dayKey).\(prayer.entry.name).start",
                prayer.date,
                "Time for \(prayer.entry.name)",
                "It is time to pray \(prayer.entry.name) in \(payload.locationName).",
                prayer.entry.adhanEnabled ?? true,
                prayer.entry.adhanVolume
            )
            guard prayer.entry.priorMinutes > 0 else { return [start] }
            let reminderDate = prayer.date.addingTimeInterval(
                TimeInterval(-60 * prayer.entry.priorMinutes)
            )
            guard reminderDate > Date() else { return [start] }
            return [
                (
                    "\(prayerNotificationPrefix)\(dayKey).\(prayer.entry.name).reminder",
                    reminderDate,
                    "\(prayer.entry.name) is approaching",
                    "\(prayer.entry.name) begins in \(prayer.entry.priorMinutes) minutes.",
                    false,
                    nil
                ),
                start,
            ]
        }
        .sorted { $0.1 < $1.1 }
        .prefix(60)

        for event in events {
            let content = UNMutableNotificationContent()
            content.title = event.2
            content.body = event.3
            content.categoryIdentifier = "PRAYER_TIME"
            content.threadIdentifier = "PRAYER_TIMES"
            content.interruptionLevel = .timeSensitive
            // Per-prayer adhan: the prayer-START notification plays the bundled
            // adhan audio (system-managed: honors the volume/mute switch);
            // reminders keep the default sound. The adhan is 14s, within iOS's
            // 30s notification-sound limit.
            let isPrayerStart = event.0.hasSuffix(".start")
            if isPrayerStart, event.4 {
                // Per-prayer volume: the adhan ships pre-scaled at 5/25/50/75/100
                // and the schedule selects the file for the stored percent —
                // notification sounds cannot be scaled at playback time.
                let volume = event.5 ?? 5
                let fileName: String?
                switch volume {
                case 0: fileName = nil // muted: silent notification, like Android's 0%
                case 1...14: fileName = "short_adhan_5.caf"
                case 15...37: fileName = "short_adhan_25.caf"
                case 38...62: fileName = "short_adhan_50.caf"
                case 63...87: fileName = "short_adhan_75.caf"
                default: fileName = "short_adhan.caf"
                }
                if let fileName {
                    content.sound = UNNotificationSound(named: UNNotificationSoundName(fileName))
                    // Foreground presentation reads these to play the adhan
                    // through AVAudioPlayer: the simulator never plays
                    // notification sounds, and in-app playback scales the
                    // volume continuously instead of jumping between the
                    // five pre-baked files.
                    content.userInfo = ["adhan": true, "adhanVolume": volume]
                }
            } else if payload.soundEnabled {
                content.sound = .default
            }

            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = scheduleTimeZone(for: payload)
            var components = calendar.dateComponents(
                [.year, .month, .day, .hour, .minute],
                from: event.1
            )
            components.timeZone = calendar.timeZone
            let trigger = UNCalendarNotificationTrigger(
                dateMatching: components,
                repeats: false
            )
            try? await center.add(
                UNNotificationRequest(
                    identifier: event.0,
                    content: content,
                    trigger: trigger
                )
            )
        }
    }

    private func scheduleTimeZone(for payload: PrayerSchedulePayload) -> TimeZone {
        guard let offset = payload.timeZoneOffset else { return .autoupdatingCurrent }
        return TimeZone(secondsFromGMT: Int((offset * 3_600).rounded())) ?? .autoupdatingCurrent
    }

    private func removePrayerNotifications() {
        Task { [weak self] in
            guard let self else { return }
            let requests = await center.pendingNotificationRequests()
            center.removePendingNotificationRequests(
                withIdentifiers: requests.map(\.identifier).filter {
                    $0.hasPrefix(prayerNotificationPrefix)
                }
            )
        }
    }

    private func updateLiveActivity(
        payload: PrayerSchedulePayload,
        prayers: [DatedPrayer]
    ) {
        guard #available(iOS 16.1, *),
               payload.notificationsEnabled,
              payload.liveActivitiesEnabled ?? true,
              let next = prayers.first else {
            if #available(iOS 16.1, *) {
                Task { [weak self] in
                    self?.activityEndTasks.values.forEach { $0.cancel() }
                    self?.activityEndTasks.removeAll()
                    for activity in Activity<PrayerActivityAttributes>.activities {
                        await Self.end(activity)
                    }
                    self?.reconcileLiveActivityTokens()
                }
            }
            return
        }

        // The stale date must stay in the FUTURE or ActivityKit rejects the
        // whole request (ActivityInput error 0). Filtered-next guarantees
        // next.date > now, so anchoring staleness at the prayer time is safe;
        // the prayer window stays in the content state for the widget.
        guard next.date > Date() else { return }
        let state = liveActivityState(payload: payload, next: next)
        Task { [weak self] in
            await self?.reconcileLiveActivity(payload: payload, proposedState: state)
        }
    }

    @available(iOS 16.1, *)
    private func reconcileLiveActivity(
        payload: PrayerSchedulePayload,
        proposedState: PrayerActivityAttributes.ContentState
    ) async {
        let now = Date()
        let activities = Activity<PrayerActivityAttributes>.activities
        var activity = activities.first

        // This feature owns exactly one event. Clean up remnants from interrupted
        // debug runs instead of making people cycle between duplicate activities.
        for duplicate in activities.dropFirst() {
            await Self.end(duplicate)
        }

        if let existing = activity {
            let currentState: PrayerActivityAttributes.ContentState
            if #available(iOS 16.2, *) {
                currentState = existing.content.state
            } else {
                currentState = existing.contentState
            }

            if now >= currentState.activeUntil {
                activityEndTasks[existing.id]?.cancel()
                activityEndTasks[existing.id] = nil
                await Self.end(existing)
                activity = nil
            } else if currentState.prayerDate != proposedState.prayerDate {
                // Keep the current prayer through its configured active window;
                // don't silently turn one event into the next prayer.
                scheduleEnd(for: existing, at: currentState.activeUntil)
                return
            } else if existing.attributes.locationName != payload.locationName {
                // Attributes are immutable, so location changes require a new event.
                activityEndTasks[existing.id]?.cancel()
                activityEndTasks[existing.id] = nil
                await Self.end(existing)
                activity = nil
            } else {
                if currentState != proposedState {
                    await Self.update(existing, state: proposedState)
                }
                scheduleEnd(for: existing, at: proposedState.activeUntil)
                return
            }
        }

        guard activity == nil,
              proposedState.activeUntil > now,
              proposedState.activeUntil.timeIntervalSince(now) <= 8 * 60 * 60,
              ActivityAuthorizationInfo().areActivitiesEnabled else { return }

        do {
            let created = try Self.requestLiveActivity(
                attributes: PrayerActivityAttributes(locationName: payload.locationName),
                state: proposedState
            )
            observePushTokens(for: created)
            scheduleEnd(for: created, at: proposedState.activeUntil)
        } catch {
            NSLog("Live Activity request failed: %@", error.localizedDescription)
        }
    }

    @available(iOS 16.1, *)
    private func scheduleEnd(
        for activity: Activity<PrayerActivityAttributes>,
        at endDate: Date
    ) {
        activityEndTasks[activity.id]?.cancel()
        let delay = max(0, endDate.timeIntervalSinceNow)
        let nanoseconds = UInt64(min(delay, 8 * 60 * 60) * 1_000_000_000)
        activityEndTasks[activity.id] = Task { [weak self] in
            do {
                try await Task.sleep(nanoseconds: nanoseconds)
            } catch {
                return
            }
            await Self.end(activity)
            self?.activityEndTasks[activity.id] = nil
            self?.reconcileLiveActivityTokens()
        }
    }

    @available(iOS 16.1, *)
    private static func requestLiveActivity(
        attributes: PrayerActivityAttributes,
        state: PrayerActivityAttributes.ContentState
    ) throws -> Activity<PrayerActivityAttributes> {
        // No pushType: local state drives this event while the process is
        // available. The stale date and scheduled end share the event boundary.
        if #available(iOS 16.2, *) {
            return try Activity.request(
                attributes: attributes,
                content: ActivityContent(
                    state: state,
                    staleDate: max(state.activeUntil, state.prayerDate),
                    relevanceScore: 1
                )
            )
        }
        return try Activity.request(
            attributes: attributes,
            contentState: state
        )
    }

    @available(iOS 16.1, *)
    private static func update(
        _ activity: Activity<PrayerActivityAttributes>,
        state: PrayerActivityAttributes.ContentState
    ) async {
        if #available(iOS 16.2, *) {
            await activity.update(
                ActivityContent(
                    state: state,
                    staleDate: max(state.activeUntil, state.prayerDate),
                    relevanceScore: 1
                )
            )
        } else {
            await activity.update(using: state)
        }
    }

    @available(iOS 16.1, *)
    private static func end(_ activity: Activity<PrayerActivityAttributes>) async {
        if #available(iOS 16.2, *) {
            await activity.end(nil, dismissalPolicy: .immediate)
        } else {
            await activity.end(using: nil, dismissalPolicy: .immediate)
        }
    }

    private func observeLiveActivityTokens() {
        guard #available(iOS 16.1, *) else { return }

        reconcileLiveActivityTokens()
        for activity in Activity<PrayerActivityAttributes>.activities {
            observePushTokens(for: activity)
        }

        activityUpdatesTask = Task { [weak self] in
            for await activity in Activity<PrayerActivityAttributes>.activityUpdates {
                guard !Task.isCancelled else { return }
                self?.observePushTokens(for: activity)
                self?.reconcileLiveActivityTokens()
            }
        }

        if #available(iOS 17.2, *) {
            pushToStartTokenTask = Task { [weak self] in
                for await token in Activity<PrayerActivityAttributes>.pushToStartTokenUpdates {
                    guard !Task.isCancelled else { return }
                    self?.updateTokenSnapshot { snapshot in
                        snapshot.pushToStartToken = token.hexString
                    }
                }
            }
        }
    }

    @available(iOS 16.1, *)
    private func observePushTokens(for activity: Activity<PrayerActivityAttributes>) {
        let activityID = activity.id
        if let token = activity.pushToken {
            updateTokenSnapshot { snapshot in
                snapshot.liveActivityTokens[activityID] = token.hexString
            }
        }

        guard activityPushTokenTasks[activityID] == nil else { return }
        activityPushTokenTasks[activityID] = Task { [weak self] in
            for await token in activity.pushTokenUpdates {
                guard !Task.isCancelled else { return }
                self?.updateTokenSnapshot { snapshot in
                    snapshot.liveActivityTokens[activityID] = token.hexString
                }
            }
            self?.activityPushTokenTasks[activityID] = nil
            self?.reconcileLiveActivityTokens()
        }
    }

    @available(iOS 16.1, *)
    private func reconcileLiveActivityTokens() {
        let activeIDs = Set(Activity<PrayerActivityAttributes>.activities.map(\.id))
        updateTokenSnapshot { snapshot in
            snapshot.liveActivityTokens = snapshot.liveActivityTokens.filter {
                activeIDs.contains($0.key)
            }
        }
    }

    private func updateTokenSnapshot(
        _ update: (inout PrayerPushTokenSnapshot) -> Void
    ) {
        var next = tokenSnapshot
        update(&next)
        next.updatedAt = tokenSnapshot.updatedAt
        guard next != tokenSnapshot else { return }

        next.updatedAt = Date()
        tokenSnapshot = next
        persistTokenSnapshot(next)
        uploadTokenSnapshot(next)
    }

    private func persistTokenSnapshot(_ snapshot: PrayerPushTokenSnapshot) {
        guard let data = try? Self.tokenEncoder.encode(snapshot) else { return }
        UserDefaults.standard.set(data, forKey: prayerPushTokenSnapshotKey)
    }

    private func uploadTokenSnapshot(_ snapshot: PrayerPushTokenSnapshot) {
        guard let configuredURL = Bundle.main.object(
            forInfoDictionaryKey: "PrayerPushBackendURL"
        ) as? String,
              let url = URL(string: configuredURL),
              url.scheme?.lowercased() == "https",
              url.host != nil,
              let body = try? Self.tokenEncoder.encode(snapshot) else { return }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        uploadTask?.cancel()
        uploadTask = Task {
            do {
                let (_, response) = try await URLSession.shared.upload(for: request, from: body)
                guard !Task.isCancelled else { return }
                let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
                guard (200..<300).contains(statusCode) else {
                    NSLog("Push token upload returned HTTP %ld", statusCode)
                    return
                }
            } catch is CancellationError {
                return
            } catch {
                NSLog("Push token upload failed: %@", error.localizedDescription)
            }
        }
    }

    private static func loadTokenSnapshot() -> PrayerPushTokenSnapshot {
        if let data = UserDefaults.standard.data(forKey: prayerPushTokenSnapshotKey),
           let stored = try? tokenDecoder.decode(PrayerPushTokenSnapshot.self, from: data) {
            return stored
        }
        return PrayerPushTokenSnapshot(
            schemaVersion: 1,
            bundleIdentifier: Bundle.main.bundleIdentifier ?? "",
            apnsDeviceToken: nil,
            liveActivityTokens: [:],
            pushToStartToken: nil,
            updatedAt: Date()
        )
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        // Prayer START with the adhan attached: play the audio in-app rather
        // than through the notification sound — the simulator never plays
        // notification sounds, and AVAudioPlayer honors the stored per-prayer
        // volume continuously instead of picking a pre-scaled file.
        if notification.request.identifier.hasSuffix(".start"),
           notification.request.content.userInfo["adhan"] as? Bool == true {
            let volume = notification.request.content.userInfo["adhanVolume"] as? Int ?? 100
            playAdhan(volumePercent: volume)
            // No .sound: the adhan itself IS the sound; adding the default
            // notification sound would talk over it.
            return [.banner, .list]
        }
        return [.banner, .list, .sound]
    }

    /// Foreground adhan playback on the bundled full-scale audio, scaled by
    /// the per-prayer percent. Ambient session semantics keep the hardware
    /// mute switch in charge, matching notification-sound behavior.
    private func playAdhan(volumePercent: Int) {
        guard volumePercent > 0,
              let url = Bundle.main.url(
                forResource: "short_adhan",
                withExtension: "caf"
              ) else { return }
        do {
            let player = try AVAudioPlayer(contentsOf: url)
            player.volume = Float(min(max(volumePercent, 0), 100)) / 100
            player.prepareToPlay()
            player.play()
            adhanPlayer = player
        } catch {
            NSLog("Adhan playback failed: %@", error.localizedDescription)
        }
    }

    private var adhanPlayer: AVAudioPlayer?

    private static let identifierDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyyMMddHHmm"
        return formatter
    }()

    private static let tokenEncoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.sortedKeys]
        return encoder
    }()

    private static let tokenDecoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }()
}

private extension Data {
    var hexString: String {
        map { String(format: "%02x", $0) }.joined()
    }
}
