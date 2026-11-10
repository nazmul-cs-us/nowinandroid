package com.starception.submission.family

import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLog
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class FamilyClientSnapshotTest {
    @Test fun olderInboxSnapshotsCannotRestoreClearedBadges() = runBlocking {
        val client = FamilyClient(ApplicationProvider.getApplicationContext())
        fun inbox(revision: Int, count: Int) = JSONObject().put("inboxVersion", revision).put("unreadCount", count)
            .put("conversations", JSONArray()).put("notifications", JSONArray())
        try {
            client.apply(inbox(2, 3)); assertEquals(3, client.state.value.unreadCount)
            client.apply(inbox(3, 0)); client.apply(inbox(2, 3))
            assertEquals(0, client.state.value.unreadCount)
            client.disconnect(clearSaved = false)
            client.apply(inbox(1, 1)); assertEquals(1, client.state.value.unreadCount)
        } finally { client.disconnect(clearSaved = false) }
    }

    @Test fun relayFetchFailureIdentifiesTheStepWithoutLoggingAccountOrCallData() = runBlocking {
        ShadowLog.clear()
        val client = FamilyClient(ApplicationProvider.getApplicationContext())
        val privateValue = "private-startup-diagnostic-789"
        try {
            // No socket exists: this must fail before native media is initialized.
            client.apply(JSONObject().put("call", JSONObject()
                .put("id", privateValue).put("caller", "$privateValue@xmpp.starception.com")
                .put("callee", "ios@xmpp.starception.com").put("kind", "voice")
                .put("stage", "active").put("version", 2).put("selected", true)
                .put("expires", System.currentTimeMillis() + 45_000)))
            val messages = ShadowLog.getLogsForTag("FamilyCalls").joinToString("\n") { it.msg }
            assertTrue(messages.contains("Call startup failed: step=relay-credentials"))
            assertTrue(messages.contains("serverCode=not_connected"))
            assertFalse(messages.contains("Media startup:"))
            assertFalse(messages.contains(privateValue))
            assertFalse(messages.contains("@xmpp.starception.com"))
            assertNull(client.state.value.call)
        } finally { client.disconnect(clearSaved = false) }
    }

    @Test fun diagnosticsKeepProtocolPayloadsAndAccountNamesOutOfLogs() = runBlocking {
        ShadowLog.clear()
        val client = FamilyClient(ApplicationProvider.getApplicationContext())
        val privateValue = "private-protocol-value-789"
        try {
            client.apply(JSONObject().put("call", JSONObject()
                .put("id", privateValue).put("caller", "$privateValue@xmpp.starception.com")
                .put("callee", "ios@xmpp.starception.com").put("kind", "voice")
                .put("stage", "ended").put("version", 2).put("expires", 0)
                .put("reason", privateValue)))
            val messages = ShadowLog.getLogsForTag("FamilyCalls").joinToString("\n") { it.msg }
            assertTrue(messages.contains("Diagnostics v2 loaded"))
            assertTrue(messages.contains("stage=ended"))
            assertFalse(messages.contains(privateValue))
            assertFalse(messages.contains("@xmpp.starception.com"))
        } finally { client.disconnect(clearSaved = false) }
    }

    @Test fun delayedEmptySnapshotKeepsIncomingCallButCurrentSnapshotCanEndIt() = runBlocking {
        val client = FamilyClient(ApplicationProvider.getApplicationContext())
        val id = "d244dbda-0aa5-4d82-b970-a68c58e2ca0d"
        try {
            val invitation = JSONObject().put("id", id)
                .put("caller", "android@xmpp.starception.com")
                .put("callee", "ios@xmpp.starception.com")
                .put("kind", "voice").put("stage", "ringing")
                .put("version", 1).put("expires", System.currentTimeMillis() + 45_000)
            client.apply(JSONObject().put("call", invitation))

            // This snapshot was requested before the invitation arrived.
            client.apply(JSONObject().put("calls", JSONArray()).put("_localCallRevision", 0))
            assertEquals(id, client.state.value.call?.id)

            // A subsequent snapshot can reconcile a missed server end event.
            client.apply(JSONObject().put("calls", JSONArray()).put("_localCallRevision", 1))
            assertNull(client.state.value.call)
        } finally { client.disconnect(clearSaved = false) }
    }

    @Test fun emptyInitialSnapshotWithoutACallIsHarmless() = runBlocking {
        val client = FamilyClient(ApplicationProvider.getApplicationContext())
        try {
            client.apply(JSONObject().put("calls", JSONArray()).put("_localCallRevision", 0))
            assertNull(client.state.value.call)
        } finally { client.disconnect(clearSaved = false) }
    }
}
