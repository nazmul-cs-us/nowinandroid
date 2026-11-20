# Deenly voice assistant (Android)

Settings → **Voice assistant** offers two independent, explicit choices:

1. **Hey Deenly**: an optional microphone foreground service. Download the keyword
   model and Whisper first, enable the switch, accept the explanation, and grant
   microphone/notification permissions while the app is visible. It never enables
   itself on install, boot, assistant selection, or process restart.
2. **Choose Deenly as default assistant**: opens Android's assistant-role chooser
   (with the system default-app settings as a fallback). Only Android and the user
   change the default. Assistant-button/gesture behavior and unlocking requirements
   depend on the device. Assistant binding does not start a microphone.

The normal mic button/default-assistant entry also recognizes these explicit playback
commands. Other utterances open the existing in-place search results. Typed searches
never automatically execute a playback command.

## Screen-off commands

Say **“Hey Deenly”**, wait for the short tone, then say an English command:

- “Play Bakarah” / “Play Al-Baqarah” / “Play surah 2”.
- “Play” followed by any of the 114 canonical English surah names.
- “Pause”, “resume”, “stop”, “next surah”, or “previous surah”.

The lightweight keyword model runs continuously only while enabled. Whisper runs
only after the wake phrase. Waiting audio is never saved. The bounded command
recording stays in the app's non-backed-up cache and is deleted after recognition/cancellation. Raw
transcripts are not placed in notifications or logs by this feature. Initial
recording downloads may need internet; downloaded Quran audio plays offline.

The persistent **Deenly hands-free microphone** notification includes **Turn off
microphone**. Revoking microphone/notification access or blocking this notification
channel stops listening. Sessions end after 12 hours, when the app's task is closed,
on process death/force-stop, or on restart. There is deliberately no boot receiver,
sticky restart, remote enable action, or battery-optimization bypass.

Family calls, foreground voice search and other microphone users take priority.
The keyword model uses a separate wake-only keyword file, never the existing
yes/no lesson-completion keywords. English command recognition does not depend on
the user's selected keyword/transcription mode for other workflows.

## Samsung acceptance checks (manual; do not install automatically)

- Install the rebuilt APK manually. Confirm both choices initially leave the mic off.
- Verify the two model downloads and denied/granted microphone/notification paths.
- Enable Hey Deenly, lock the screen, wait, and issue “play Bakarah”. Confirm surah 2
  plays through the existing Quran media session/notification and selected reciter.
- Exercise pause/resume/next/previous/stop while locked, including an uncached surah.
- Check that ordinary conversation and Quran playback do not trigger the wake phrase.
  The custom phrase's sensitivity and pronunciation variants require real-device QA.
- Start a family/phone call and foreground voice search. Verify the background mic
  yields, then returns to the wake phrase after the other capture ends.
- Stop from the notification, revoke permissions, close the task, force-stop and
  reboot. Verify no microphone resumes until the user explicitly enables it again.
- Choose Deenly in Android's default-assistant chooser and invoke the system gesture
  on cold/warm starts, from a detail page, after rotation and after permission denial.
- Check battery use over several hours. This is software wake-word detection, not
  Google's privileged low-power hardware hotword implementation. Samsung battery
  management can still stop the service; no reliability claim bypasses Android policy.
