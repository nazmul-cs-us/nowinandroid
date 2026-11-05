package com.starception.submission.family

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FamilyEndpointTest {
    @Test fun acceptsUsernamesAndFullAccountsOnTheSameServer() {
        val endpoint = FamilyEndpoint()
        assertEquals(endpoint.androidTestAccount, endpoint.account(" Android "))
        assertEquals(endpoint.iosTestAccount, endpoint.account("ios@xmpp.starception.com"))
    }

    @Test fun rejectsForeignAccountsAndResources() {
        val endpoint = FamilyEndpoint()
        listOf("", "android@other.example", "android/device", "ios@xmpp.starception.com/resource", "a@@xmpp.starception.com")
            .forEach { assertNull(endpoint.account(it)) }
    }

    @Test fun suggestsOnlyTheOppositeProvisionedTestAccount() {
        val endpoint = FamilyEndpoint()
        assertEquals(endpoint.iosTestAccount, endpoint.testPeer("android"))
        assertEquals(endpoint.androidTestAccount, endpoint.testPeer("ios"))
        assertEquals("", endpoint.testPeer("someone-else"))
    }
}
