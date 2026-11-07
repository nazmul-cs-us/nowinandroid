package com.starception.submission.family

import android.content.Context
import android.telecom.Connection
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class FamilyTelecomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val id = "11111111-2222-3333-4444-555555555555"
    @Before fun reset() { FamilyTelecom.end() }
    @After fun cleanup() { FamilyTelecom.end() }
    private fun state(incoming: Boolean, stage: String) = FamilyUiState(me = "me@xmpp.test",
        call = FamilyCall(id, if (incoming) "peer@xmpp.test" else "me@xmpp.test",
            if (incoming) "me@xmpp.test" else "peer@xmpp.test", "voice", stage, 2,
            System.currentTimeMillis() + 45000, true), mediaConnected = false)

    @Test fun incomingAcceptanceActivatesTelecomBeforeMediaConnects() {
        val connection = object : Connection() {}.apply { setRinging() }
        FamilyTelecom.update(context, state(true, "ringing"))
        FamilyTelecom.attach(connection, id)
        assertEquals(Connection.STATE_RINGING, connection.state)
        FamilyTelecom.update(context, state(true, "active"))
        assertEquals(Connection.STATE_ACTIVE, connection.state)
    }

    @Test fun outgoingAcceptanceActivatesTelecomBeforeMediaConnects() {
        val connection = object : Connection() {}.apply { setDialing() }
        FamilyTelecom.update(context, state(false, "ringing"))
        FamilyTelecom.attach(connection, id)
        assertEquals(Connection.STATE_DIALING, connection.state)
        FamilyTelecom.accepted(id)
        assertEquals(Connection.STATE_ACTIVE, connection.state)
    }

    @Test fun connectionAttachedAfterAcceptanceIsImmediatelyActive() {
        FamilyTelecom.update(context, state(true, "ringing"))
        FamilyTelecom.accepted(id)
        val connection = object : Connection() {}.apply { setRinging() }
        FamilyTelecom.attach(connection, id)
        assertEquals(Connection.STATE_ACTIVE, connection.state)
    }

    @Test fun anotherCallCannotActivateThisConnection() {
        val connection = object : Connection() {}.apply { setRinging() }
        FamilyTelecom.update(context, state(true, "ringing"))
        FamilyTelecom.attach(connection, id)
        FamilyTelecom.accepted("another-call")
        assertEquals(Connection.STATE_RINGING, connection.state)
    }

    @Test fun lateConnectionCannotRestoreAnEndedCall() {
        FamilyTelecom.update(context, state(true, "ringing"))
        FamilyTelecom.accepted(id)
        FamilyTelecom.end()
        val connection = object : Connection() {}.apply { setRinging() }
        FamilyTelecom.attach(connection, id)
        assertEquals(Connection.STATE_DISCONNECTED, connection.state)
    }
}
