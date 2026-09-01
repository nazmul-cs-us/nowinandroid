import ActivityKit
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
        .filter { $0.entry.enabled && $0.date > Date() }
        .sorted { $0.date < $1.date }
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

        let events = prayers.flatMap { prayer -> [(String, Date, String, String)] in
            let dayKey = Self.identifierDateFormatter.string(from: prayer.date)
            let start = (
                "\(prayerNotificationPrefix)\(dayKey).\(prayer.entry.name).start",
                prayer.date,
                "Time for \(prayer.entry.name)",
                "It is time to pray \(prayer.entry.name) in \(payload.locationName)."
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
                    "\(prayer.entry.name) begins in \(prayer.entry.priorMinutes) minutes."
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
            if payload.soundEnabled { content.sound = .default }

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
              let next = prayers.first else {
            if #available(iOS 16.1, *) {
                Task { [weak self] in
                    for activity in Activity<PrayerActivityAttributes>.activities {
                        await Self.end(activity)
                    }
                    self?.reconcileLiveActivityTokens()
                }
            }
            return
        }

        let state = PrayerActivityAttributes.ContentState(
            prayerName: next.entry.name,
            prayerDate: next.date,
            activeUntil: next.date.addingTimeInterval(TimeInterval(60 * next.entry.activeMinutes))
        )
        Task { [weak self] in
            if let activity = Activity<PrayerActivityAttributes>.activities.first {
                await Self.update(activity, state: state)
            } else if ActivityAuthorizationInfo().areActivitiesEnabled {
                do {
                    let activity = try Self.requestLiveActivity(
                        attributes: PrayerActivityAttributes(locationName: payload.locationName),
                        state: state
                    )
                    self?.observePushTokens(for: activity)
                } catch {
                    NSLog("Live Activity request failed: %@", error.localizedDescription)
                }
            }
        }
    }

    @available(iOS 16.1, *)
    private static func requestLiveActivity(
        attributes: PrayerActivityAttributes,
        state: PrayerActivityAttributes.ContentState
    ) throws -> Activity<PrayerActivityAttributes> {
        if #available(iOS 16.2, *) {
            return try Activity.request(
                attributes: attributes,
                content: ActivityContent(
                    state: state,
                    staleDate: state.activeUntil,
                    relevanceScore: 1
                ),
                pushType: .token
            )
        }
        return try Activity.request(
            attributes: attributes,
            contentState: state,
            pushType: .token
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
                    staleDate: state.activeUntil,
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
        [.banner, .list, .sound]
    }

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
