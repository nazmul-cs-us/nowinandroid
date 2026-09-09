/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.core.data.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.NetworkRequest.Builder
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.util.Log
import androidx.core.content.getSystemService
import androidx.tracing.trace
import com.starception.submission.core.network.Dispatcher
import com.starception.submission.core.network.NiaDispatchers.IO
import com.starception.submission.core.network.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ConnectivityManagerNetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    @param:ApplicationScope appScope: CoroutineScope,
    @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : NetworkMonitor {
    override val isOnline: SharedFlow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService<ConnectivityManager>()
        if (connectivityManager == null) {
            channel.trySend(false)
            channel.close()
            return@callbackFlow
        }

        /**
         * The callback's methods are invoked on changes to *any* network matching the [NetworkRequest],
         * not just the active network. So we can simply track the presence (or absence) of such [Network].
         */
        val callback = object : NetworkCallback() {

            private val networks = mutableSetOf<Network>()

            override fun onAvailable(network: Network) {
                networks += network
                channel.trySend(true)
            }

            override fun onLost(network: Network) {
                networks -= network
                channel.trySend(networks.isNotEmpty())
            }
        }

        val callbackRegistered = try {
            trace("NetworkMonitor.registerNetworkCallback") {
                val request = Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(request, callback)
            }
            true
        } catch (exception: RuntimeException) {
            // ConnectivityManager enforces a per-UID callback quota shared with
            // libraries such as WorkManager. Exceeding it throws
            // TooManyRequestsException from this coroutine and used to terminate
            // the whole app. Keep reporting a safe, periodically refreshed value
            // until a later subscription can register the callback again.
            Log.e(TAG, "Unable to register network callback; using polling fallback", exception)
            false
        }

        /**
         * Sends the latest connectivity status to the underlying channel.
         */
        channel.trySend(connectivityManager.isCurrentlyConnected())

        if (callbackRegistered) {
            awaitClose {
                runCatching {
                    connectivityManager.unregisterNetworkCallback(callback)
                }.onFailure { exception ->
                    Log.w(TAG, "Unable to unregister network callback", exception)
                }
            }
        } else {
            while (currentCoroutineContext().isActive) {
                delay(POLL_INTERVAL_MILLIS)
                channel.trySend(connectivityManager.isCurrentlyConnected())
            }
        }
    }
        .distinctUntilChanged()
        .conflate()
        .flowOn(ioDispatcher)
        // callbackFlow is cold. Sharing it at application scope guarantees that
        // activity recreation and multiple collectors still use one platform
        // callback rather than consuming ConnectivityManager's per-UID quota.
        .shareIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            replay = 1,
        )

    @Suppress("DEPRECATION")
    private fun ConnectivityManager.isCurrentlyConnected() = when {
        VERSION.SDK_INT >= VERSION_CODES.M ->
            activeNetwork
                ?.let(::getNetworkCapabilities)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        else -> activeNetworkInfo?.isConnected
    } ?: false

    private companion object {
        const val TAG = "NetworkMonitor"
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val POLL_INTERVAL_MILLIS = 30_000L
    }
}
