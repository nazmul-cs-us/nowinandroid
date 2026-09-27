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

package com.starception.submission.shared.translation

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import kotlin.coroutines.resume

internal actual suspend fun httpGetText(url: String): String? =
    httpRequestText(url = url, method = "GET", body = null)

internal actual suspend fun httpPostJson(url: String, jsonBody: String): String? =
    httpRequestText(url = url, method = "POST", body = jsonBody)

private suspend fun httpRequestText(url: String, method: String, body: String?): String? =
    suspendCancellableCoroutine { continuation ->
        val source = NSURL.URLWithString(url)
        if (source == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val request = NSMutableURLRequest(uRL = source).apply {
            setHTTPMethod(method)
            if (body != null) {
                setHTTPBody(
                    NSString.create(string = body).dataUsingEncoding(NSUTF8StringEncoding),
                )
                setValue("application/json", forHTTPHeaderField = "Content-Type")
            }
            setValue("Mozilla/5.0", forHTTPHeaderField = "User-Agent")
        }
        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
            val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt()
            val text = if (error == null && status in 200..299 && data != null) {
                NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
            } else {
                null
            }
            continuation.resume(text)
        }
        task.resume()
    }
