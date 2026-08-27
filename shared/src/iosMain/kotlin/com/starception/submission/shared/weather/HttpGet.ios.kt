/*
 * Copyright 2021 The Android Open Source Project
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

package com.starception.submission.shared.weather

import com.starception.submission.core.logging.SharedLog
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue

private const val TAG = "HttpGet"
private const val TIMEOUT_SECONDS = 5.0

internal actual suspend fun httpGet(url: String): String? =
    suspendCancellableCoroutine { continuation ->
        val target = NSURL.URLWithString(url)
        if (target == null) {
            SharedLog.w(TAG, "Malformed URL")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val request = NSMutableURLRequest(uRL = target)
        request.setHTTPMethod("GET")
        request.setValue("application/json", forHTTPHeaderField = "Accept")
        request.setValue("Starception-iOS/1.0", forHTTPHeaderField = "User-Agent")

        val configuration = NSURLSessionConfiguration.defaultSessionConfiguration
        configuration.timeoutIntervalForRequest = TIMEOUT_SECONDS
        val session = NSURLSession.sessionWithConfiguration(configuration)

        // The request form, not dataTaskWithURL, so the Accept and User-Agent
        // headers actually apply and iOS asks exactly as Android does.
        val task = session.dataTaskWithRequest(request) { data: NSData?, response: NSURLResponse?, error: NSError? ->
            val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt()
            when {
                error != null -> {
                    SharedLog.w(TAG, "Request failed: ${error.localizedDescription}")
                    continuation.resume(null)
                }

                status !in 200..299 -> {
                    SharedLog.w(TAG, "Unexpected status $status")
                    continuation.resume(null)
                }

                data == null -> continuation.resume(null)

                else -> continuation.resume(
                    NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?,
                )
            }
        }

        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }
