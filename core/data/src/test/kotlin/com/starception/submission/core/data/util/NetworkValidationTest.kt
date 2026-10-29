package com.starception.submission.core.data.util

import android.net.NetworkCapabilities
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NetworkValidationTest {
    @Test fun mobileLinkWithoutValidatedInternetIsOffline() {
        val capabilities = ShadowNetworkCapabilities.newInstance()
        val shadow = shadowOf(capabilities)
        shadow.clearCapabilities()
        shadow.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        assertFalse(capabilities.hasValidatedInternet())

        shadow.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        assertTrue(capabilities.hasValidatedInternet())

        shadow.removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        assertFalse(capabilities.hasValidatedInternet())
    }
}
