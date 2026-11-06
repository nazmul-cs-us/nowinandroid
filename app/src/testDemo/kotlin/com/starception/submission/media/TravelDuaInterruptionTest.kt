package com.starception.submission.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelDuaInterruptionTest {
    private val coordinator = TravelDuaInterruptionController()
    private val travel = Any()

    private class Player {
        var playing = true
        var position = 12345
        var resumes = 0
        fun pause(): (() -> Unit)? {
            if (!playing) return null
            playing = false
            return { playing = true; resumes++ }
        }
    }

    @Test fun travelDuaPausesAndResumesTheSameTrackAndPosition() {
        val player = Player()
        coordinator.register(player, pause = player::pause)
        assertTrue(coordinator.begin(travel))
        assertFalse(player.playing)
        assertEquals(12345, player.position)
        assertTrue(coordinator.finish(travel))
        assertTrue(player.playing)
        assertEquals(1, player.resumes)
        assertEquals(12345, player.position)
    }

    @Test fun alreadyPausedPlaybackDoesNotStartAfterTravelDua() {
        val player = Player().apply { playing = false }
        coordinator.register(player, pause = player::pause)
        coordinator.begin(travel)
        assertFalse(coordinator.hasWaitingPlayback)
        assertFalse(coordinator.finish(travel))
        assertFalse(player.playing)
    }

    @Test fun pauseFailureDoesNotLeavePlaybackLockedBehindATravelDua() {
        val player = Player()
        coordinator.register(player, pause = player::pause)
        coordinator.register(Any()) { error("player could not pause") }
        assertFalse(coordinator.begin(travel))
        assertFalse(coordinator.isActive)
        assertTrue(player.playing)
    }

    @Test fun cancellingQueuedPlaybackReleasesItsWaiter() {
        val owner = Any()
        coordinator.begin(travel)
        var discarded = false
        var started = false
        coordinator.deferIfInterrupted(owner, onDiscard = { discarded = true }) { started = true }
        coordinator.cancel(owner)
        assertTrue(discarded)
        coordinator.finish(travel)
        assertFalse(started)
    }

    @Test fun newPlayRequestWaitsForTravelDuaAndSupersedesOldTrack() {
        val player = Player()
        coordinator.register(player, pause = player::pause)
        coordinator.begin(travel)
        var newPlaying = false
        assertTrue(coordinator.deferIfInterrupted(Any()) { newPlaying = true })
        assertFalse(newPlaying)
        coordinator.finish(travel)
        assertTrue(newPlaying)
        assertFalse(player.playing)
    }

    @Test fun stoppedOrDestroyedPlaybackIsNotResumed() {
        val player = Player()
        coordinator.register(player, pause = player::pause)
        coordinator.begin(travel)
        coordinator.unregister(player)
        coordinator.finish(travel)
        assertFalse(player.playing)
        assertEquals(0, player.resumes)
    }

    @Test fun duplicateTravelStartsCannotReplaceTheResumeTarget() {
        val player = Player()
        coordinator.register(player, pause = player::pause)
        coordinator.begin(travel)
        assertFalse(coordinator.begin(Any()))
        assertFalse(coordinator.finish(Any()))
        assertFalse(player.playing)
        coordinator.finish(travel)
        assertTrue(player.playing)
    }

    @Test fun latestQueuedRequestWinsAndDiscardedWaitersAreReleased() {
        coordinator.begin(travel)
        val events = mutableListOf<String>()
        coordinator.deferIfInterrupted(Any(), onDiscard = { events += "discard old" }) { events += "play old" }
        coordinator.deferIfInterrupted(Any()) { events += "play new" }
        coordinator.finish(travel)
        assertEquals(listOf("discard old", "play new"), events)
    }

    @Test fun preparedSpeechResumesItsOwningQueueBeforeStartingTheClip() {
        val group = Any()
        val queue = Player()
        val clip = Any()
        val events = mutableListOf<String>()
        coordinator.register(queue, group) {
            queue.pause()
            ({ queue.playing = true; events += "resume queue" })
        }
        coordinator.begin(travel)
        coordinator.register(clip, group) { null }
        coordinator.deferIfInterrupted(clip, resumeGroupBeforeStart = true) {
            assertTrue(queue.playing)
            events += "play clip"
        }
        coordinator.finish(travel)
        assertEquals(listOf("resume queue", "play clip"), events)
    }

    @Test fun newRecordingDoesNotResumeAnOldSpeechClipInTheSameSourceGroup() {
        val group = Any()
        val speech = Player()
        val recording = Any()
        coordinator.register(speech, group, speech::pause)
        coordinator.register(recording, group) { null }
        coordinator.begin(travel)
        var recordingStarted = false
        coordinator.deferIfInterrupted(recording) { recordingStarted = true }
        coordinator.finish(travel)
        assertTrue(recordingStarted)
        assertFalse(speech.playing)
    }
}
