/*
 * Copyright 2026 The Android Open Source Project
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

package com.starception.submission.shared.connectivity

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreFoundation.CFRunLoopGetMain
import platform.CoreFoundation.kCFRunLoopCommonModes
import platform.SystemConfiguration.SCNetworkReachabilityCreateWithAddress
import platform.SystemConfiguration.SCNetworkReachabilityFlags
import platform.SystemConfiguration.SCNetworkReachabilityFlagsVar
import platform.SystemConfiguration.SCNetworkReachabilityGetFlags
import platform.SystemConfiguration.SCNetworkReachabilityRef
import platform.SystemConfiguration.SCNetworkReachabilityScheduleWithRunLoop
import platform.SystemConfiguration.SCNetworkReachabilitySetCallback
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsConnectionRequired
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsReachable
import platform.posix.AF_INET
import platform.posix.sockaddr_in

/**
 * NWPathMonitor's update handler is a C block, which pure C interop cannot
 * create — so the monitor uses the classic SCNetworkReachability API: a
 * zero-address target (the default route), a C function-pointer callback via
 * [staticCFunction], and the main run loop.
 */
@OptIn(ExperimentalForeignApi::class)
private object ReachabilityState {
    val online = MutableStateFlow(true)

    private var target: SCNetworkReachabilityRef? = null

    init {
        memScoped {
            val address = alloc<sockaddr_in>()
            address.sin_family = AF_INET.toUByte()
            address.sin_len = sizeOf<sockaddr_in>().toUByte()
            address.sin_port = 0u
            address.sin_addr.s_addr = 0u
            val created = SCNetworkReachabilityCreateWithAddress(null, address.ptr.reinterpret())
                ?: return@memScoped
            target = created
            SCNetworkReachabilitySetCallback(
                created,
                staticCFunction(::onReachability),
                null,
            )
            val flags = alloc<SCNetworkReachabilityFlagsVar>()
            if (SCNetworkReachabilityGetFlags(created, flags.ptr)) {
                online.value = isReachable(flags.value)
            }
            SCNetworkReachabilityScheduleWithRunLoop(created, CFRunLoopGetMain(), kCFRunLoopCommonModes)
        }
    }
}

/** Top-level C callback — [staticCFunction] functions cannot capture state. */
@OptIn(ExperimentalForeignApi::class)
private fun onReachability(
    target: SCNetworkReachabilityRef?,
    flags: SCNetworkReachabilityFlags,
    info: kotlinx.cinterop.COpaquePointer?,
) {
    ReachabilityState.online.value = isReachable(flags)
}

@OptIn(ExperimentalForeignApi::class)
private fun isReachable(flags: SCNetworkReachabilityFlags): Boolean =
    (flags and kSCNetworkReachabilityFlagsReachable.convert()) != 0u &&
        (flags and kSCNetworkReachabilityFlagsConnectionRequired.convert()) == 0u

/** SCNetworkReachability-backed connectivity for the shared iOS UI. */
actual class ConnectivityMonitor actual constructor() {
    actual val isOnline: StateFlow<Boolean> = ReachabilityState.online.asStateFlow()
}
