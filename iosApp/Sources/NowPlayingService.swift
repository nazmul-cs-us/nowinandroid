import MediaPlayer
import Shared
import UIKit

/// Native owner of the system Now Playing card.
///
/// Shared Kotlin selects the active Surah, hadith, or dua. Swift performs the
/// final MediaPlayer write on the app's main queue, where iOS owns the audio
/// lifecycle and accepts Lock Screen and Control Center metadata.
final class NowPlayingService: NSObject, IosNowPlayingService, @unchecked Sendable {
    static let shared = NowPlayingService()

    private lazy var artwork: MPMediaItemArtwork? = {
        guard let image = UIImage(named: "DeenlyNowPlayingArtwork") else { return nil }
        return MPMediaItemArtwork(boundsSize: image.size) { _ in image }
    }()

    private override init() {
        super.init()
        DispatchQueue.main.async {
            UIApplication.shared.beginReceivingRemoteControlEvents()
        }
    }

    func publish(
        title: String,
        artist: String,
        albumTitle: String,
        playing: Bool,
        elapsedSeconds: Double,
        durationSeconds: Double
    ) {
        DispatchQueue.main.async { [self] in
            var info: [String: Any] = [
                MPMediaItemPropertyTitle: title,
                MPMediaItemPropertyArtist: artist,
                MPMediaItemPropertyAlbumTitle: albumTitle,
                MPNowPlayingInfoPropertyPlaybackRate: playing ? 1.0 : 0.0,
                MPNowPlayingInfoPropertyDefaultPlaybackRate: 1.0,
            ]
            if elapsedSeconds >= 0 {
                info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = elapsedSeconds
            }
            if durationSeconds > 0 {
                info[MPMediaItemPropertyPlaybackDuration] = durationSeconds
            }
            if let artwork {
                info[MPMediaItemPropertyArtwork] = artwork
            }

            let center = MPNowPlayingInfoCenter.default()
            center.nowPlayingInfo = info
            center.playbackState = playing ? .playing : .paused
            NSLog(
                "[NowPlaying] published '%@' playing=%@ elapsed=%.1f duration=%.1f",
                title,
                playing ? "true" : "false",
                elapsedSeconds,
                durationSeconds
            )
        }
    }

    func clear() {
        DispatchQueue.main.async {
            let center = MPNowPlayingInfoCenter.default()
            center.nowPlayingInfo = nil
            center.playbackState = .stopped
            NSLog("[NowPlaying] cleared")
        }
    }
}
