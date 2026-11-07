package com.starception.submission.family

import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class FamilySignalDiagnosticsTest {
    @Test fun rejectedVideoOfferReportsOnlyTypeSizeAndKnownError() {
        val privateValue = "private-sdp-and-credentials-marker"
        val payload = JSONObject().put("type", "offer").put("sdp", privateValue)
        val message = familySignalFailureDiagnostic(payload, IllegalStateException("invalid_signal"))
        assertTrue(message.contains("type=offer"))
        assertTrue(message.contains("bytes=${payload.toString().toByteArray(Charsets.UTF_8).size}"))
        assertTrue(message.contains("serverCode=invalid_signal"))
        assertFalse(message.contains(privateValue))
    }

    @Test fun arbitraryTypesAndExceptionMessagesAreWithheld() {
        val privateValue = "private-user@xmpp.test/private-token"
        val payload = JSONObject().put("type", privateValue).put("candidate", privateValue)
        val message = familySignalFailureDiagnostic(payload, IllegalStateException(privateValue))
        assertTrue(message.contains("type=other"))
        assertTrue(message.contains("serverCode=none"))
        assertFalse(message.contains(privateValue))
    }

    @Test fun transportFailureIsDistinguishedFromAServerRejection() {
        val message = familySignalFailureDiagnostic(JSONObject().put("type", "ice"),
            java.io.IOException("private-transport-details"))
        assertTrue(message.contains("exception=IOException"))
        assertTrue(message.contains("serverCode=none"))
        assertFalse(message.contains("private-transport-details"))
    }
}
