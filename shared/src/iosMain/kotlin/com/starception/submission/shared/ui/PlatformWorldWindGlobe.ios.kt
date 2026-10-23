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

package com.starception.submission.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.UIKit.UIColor
import platform.WebKit.WKWebView
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.darwin.NSObject
import platform.WebKit.WKWebViewConfiguration

/**
 * NASA Web WorldWind hosted by WKWebView.
 *
 * WorldWind Kotlin's native iOS module starts at a newer Kotlin/Compose
 * toolchain than this app currently uses. Web WorldWind is the same globe
 * engine family and gives the existing iOS target a sensor-driven globe
 * without forcing an app-wide Kotlin, AGP and Compose migration.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalForeignApi::class)
@Composable
internal actual fun PlatformWorldWindGlobe(
    latitude: Double,
    longitude: Double,
    headingDegrees: Double?,
    headingAccuracyDegrees: Double?,
    qiblaBearing: Double,
    expanded: Boolean,
    modifier: Modifier,
) {
    val pageKey = remember(latitude, longitude, qiblaBearing, expanded) {
        "$latitude,$longitude,$qiblaBearing,$expanded"
    }
    val html = remember(latitude, longitude, qiblaBearing, expanded) {
        qiblaWorldWindHtml(latitude, longitude, qiblaBearing, expanded)
    }

    val latestHeading = rememberUpdatedState(headingDegrees)
    val latestAccuracy = rememberUpdatedState(headingAccuracyDegrees)
    // A heading may arrive before the external WorldWind script has loaded.
    // Replay the latest reading once navigation finishes, even if the phone is still.
    val navigationDelegate = remember {
        object : NSObject(), WKNavigationDelegateProtocol {
            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                webView.evaluateJavaScript(
                    compassUpdateScript(latestHeading.value, latestAccuracy.value),
                    completionHandler = null,
                )
            }
        }
    }

    UIKitView(
        factory = {
            WKWebView(
                frame = CGRectMake(0.0, 0.0, 1.0, 1.0),
                configuration = WKWebViewConfiguration(),
            ).apply {
                this.navigationDelegate = navigationDelegate
                opaque = false
                backgroundColor = UIColor.clearColor
                scrollView.scrollEnabled = false
                scrollView.bounces = false
                tag = pageKey.hashCode().toLong()
                loadHTMLString(html, NSURL.URLWithString("https://unpkg.com"))
            }
        },
        modifier = modifier,
        update = { webView ->
            if (webView.tag != pageKey.hashCode().toLong()) {
                webView.tag = pageKey.hashCode().toLong()
                webView.loadHTMLString(html, NSURL.URLWithString("https://unpkg.com"))
            } else {
                webView.evaluateJavaScript(
                    compassUpdateScript(headingDegrees, headingAccuracyDegrees),
                    completionHandler = null,
                )
            }
        },
        onRelease = { webView ->
            webView.navigationDelegate = null
            webView.stopLoading()
            webView.loadHTMLString("", null)
        },
        properties = UIKitInteropProperties(
            interactionMode = null,
            isNativeAccessibilityEnabled = true,
        ),
    )
}

private fun compassUpdateScript(heading: Double?, accuracy: Double?): String {
    val validHeading = heading?.takeIf { it.isFinite() }?.toString() ?: "null"
    val validAccuracy = accuracy?.takeIf { it.isFinite() && it >= 0 }?.toString() ?: "null"
    return "window.pendingCompassReading = [$validHeading, $validAccuracy]; " +
        "window.updateCompassHeading && window.updateCompassHeading($validHeading, $validAccuracy)"
}
