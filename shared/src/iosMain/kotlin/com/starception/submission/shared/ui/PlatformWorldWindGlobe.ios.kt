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

private const val WORLDWIND_SCRIPT =
    "https://unpkg.com/@nasaworldwind/worldwind@0.11.0/build/dist/worldwind.min.js"

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
        worldWindHtml(latitude, longitude, qiblaBearing, expanded)
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

private fun worldWindHtml(
    latitude: Double,
    longitude: Double,
    qiblaBearing: Double,
    expanded: Boolean,
): String = """
<!doctype html>
<html>
<head>
  <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
  <style>
    html, body { margin:0; width:100%; height:100%; overflow:hidden; background:transparent; }
    #shell { position:relative; width:100%; height:100%; overflow:hidden; border-radius:${if (expanded) "24px" else "50%"};
      background:#07121d; }
    #globe { position:absolute; inset:0; z-index:1; display:block; width:100%; height:100%;
      touch-action:none; pointer-events:none; background:#07121d; border-radius:inherit; }
    #status { position:absolute; inset:0; display:flex; align-items:center; justify-content:center;
      padding:20px; color:#dce8f2; background:#07121d; font:600 14px -apple-system,sans-serif;
      text-align:center; z-index:4; }
    #overlay { position:absolute; inset:0; z-index:3; width:100%; height:100%; pointer-events:none; }
    #accuracy-ring { transition:stroke 240ms ease, filter 240ms ease; }
    #guidance { position:absolute; right:12px; top:12px; z-index:5; border-radius:12px;
      padding:8px 12px; background:rgba(7,11,16,.75); color:white;
      font:600 12px -apple-system,sans-serif; pointer-events:none; }
    #guidance:empty { display:none; }
    @keyframes radar-pulse { from { transform:scale(.85); } to { transform:scale(1); } }
  </style>
  <script src="$WORLDWIND_SCRIPT"></script>
</head>
<body>
  <div id="shell">
    <canvas id="globe">WorldWind globe</canvas>
    <svg id="overlay" viewBox="0 0 300 300" aria-hidden="true">
      <circle id="accuracy-ring" cx="150" cy="150" r="145" fill="none" stroke="#72d9bd"
        stroke-width="5" opacity=".72"/>
    </svg>
    <div id="guidance" role="status"></div>
    <div id="status">Loading WorldWind globe…</div>
  </div>
  <script>
  (function () {
    const status = document.getElementById('status');
    try {
      if (!window.WorldWind) throw new Error('WorldWind could not be loaded');
      WorldWind.Logger.setLoggingLevel(WorldWind.Logger.LEVEL_WARNING);
      const wwd = new WorldWind.WorldWindow('globe');
      wwd.addLayer(new WorldWind.BMNGOneImageLayer());
      wwd.addLayer(new WorldWind.BMNGLayer());
      if (${expanded}) {
        const atmosphere = new WorldWind.AtmosphereLayer();
        atmosphere.time = new Date();
        wwd.addLayer(atmosphere);
      }

      const userLat = $latitude;
      const userLon = $longitude;
      const qiblaBearing = $qiblaBearing;
      const makkahLat = 21.4225;
      const makkahLon = 39.8262;
      const markerLayer = new WorldWind.RenderableLayer('Qibla markers');
      const imageUri = svg => 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
      function userMarkerSvg(heading, color) {
        const radius = 180;
        const sweep = 65;
        const start = heading - sweep / 2;
        const end = heading + sweep / 2;
        const point = angle => {
          const radians = angle * Math.PI / 180;
          return [200 + radius * Math.sin(radians), 200 - radius * Math.cos(radians)];
        };
        const first = point(start);
        const last = point(end);
        return `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400" viewBox="0 0 400 400"><defs><radialGradient id="beam" cx="50%" cy="50%" r="45%"><stop offset="0" stop-color="${'$'}{color}" stop-opacity="1"/><stop offset=".2" stop-color="${'$'}{color}" stop-opacity=".8"/><stop offset=".55" stop-color="${'$'}{color}" stop-opacity=".4"/><stop offset="1" stop-color="${'$'}{color}" stop-opacity="0"/></radialGradient></defs><path d="M200 200 L${'$'}{first[0]} ${'$'}{first[1]} A${'$'}{radius} ${'$'}{radius} 0 0 1 ${'$'}{last[0]} ${'$'}{last[1]} Z" fill="url(#beam)" opacity="${'$'}{heading === null ? 0 : 1}"/><circle cx="200" cy="200" r="21" fill="white"/><circle cx="200" cy="200" r="16.8" fill="${'$'}{color}"/></svg>`;
      }
      const kaabaSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="144" height="144" viewBox="0 0 144 144"><polygon points="30,38 70,20 114,35 74,55" fill="#4b4a47"/><polygon points="30,38 74,55 74,119 30,98" fill="#292929"/><polygon points="74,55 114,35 114,98 74,119" fill="#111111"/><polygon points="30,58 74,75 74,87 30,70" fill="#d4af37"/><polygon points="74,75 114,57 114,69 74,87" fill="#f0cb52"/><rect x="88" y="84" width="14" height="28" rx="2" fill="#b99028"/></svg>`;
      function placemark(lat, lon, image, label, scale) {
        const attributes = new WorldWind.PlacemarkAttributes(null);
        attributes.imageSource = imageUri(image);
        attributes.imageScale = scale;
        attributes.imageOffset = new WorldWind.Offset(
          WorldWind.OFFSET_FRACTION, 0.5, WorldWind.OFFSET_FRACTION, 0.5
        );
        attributes.labelAttributes.color = WorldWind.Color.WHITE;
        attributes.labelAttributes.offset = new WorldWind.Offset(
          WorldWind.OFFSET_FRACTION, 0.5, WorldWind.OFFSET_PIXELS, -34
        );
        const marker = new WorldWind.Placemark(
          new WorldWind.Position(lat, lon, 0), false, attributes
        );
        marker.altitudeMode = WorldWind.CLAMP_TO_GROUND;
        marker.label = label;
        marker.eyeDistanceScaling = false;
        markerLayer.addRenderable(marker);
        return marker;
      }
      const userPlacemark = placemark(
        userLat,
        userLon,
        userMarkerSvg(null, '#94a3b8'),
        'You',
        0.5
      );
      placemark(makkahLat, makkahLon, kaabaSvg, 'Kaaba', 0.2);
      wwd.addLayer(markerLayer);

      function toRadians(value) { return value * Math.PI / 180; }
      function toDegrees(value) { return value * 180 / Math.PI; }
      function angularDistance(lat1, lon1, lat2, lon2) {
        const dLat = toRadians(lat2 - lat1);
        const dLon = toRadians(lon2 - lon1);
        const a = Math.sin(dLat / 2) ** 2 + Math.cos(toRadians(lat1)) *
          Math.cos(toRadians(lat2)) * Math.sin(dLon / 2) ** 2;
        return 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
      }
      function midpoint(lat1, lon1, lat2, lon2) {
        const p1 = toRadians(lat1);
        const p2 = toRadians(lat2);
        const l1 = toRadians(lon1);
        const dl = toRadians(lon2 - lon1);
        const bx = Math.cos(p2) * Math.cos(dl);
        const by = Math.cos(p2) * Math.sin(dl);
        return {
          latitude: toDegrees(Math.atan2(
            Math.sin(p1) + Math.sin(p2),
            Math.sqrt((Math.cos(p1) + bx) ** 2 + by ** 2)
          )),
          longitude: toDegrees(l1 + Math.atan2(by, Math.cos(p1) + bx))
        };
      }
      function bearingBetween(lat1, lon1, lat2, lon2) {
        const p1 = toRadians(lat1);
        const p2 = toRadians(lat2);
        const dl = toRadians(lon2 - lon1);
        return (toDegrees(Math.atan2(
          Math.sin(dl) * Math.cos(p2),
          Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl)
        )) + 360) % 360;
      }
      const earthRadius = 6378137;
      const distanceRadians = angularDistance(userLat, userLon, makkahLat, makkahLon);
      const distanceMeters = distanceRadians * earthRadius;
      const routeMidpoint = midpoint(userLat, userLon, makkahLat, makkahLon);
      const cameraHeading = bearingBetween(
        routeMidpoint.latitude, routeMidpoint.longitude, makkahLat, makkahLon
      );
      // Match Android QiblaGlobeView: frame both geographic markers around the
      // great-circle midpoint and pull back in proportion to their distance.
      // Web WorldWind's navigator range produces a wider view than the native
      // engine for the same Earth-radius multiple, so convert the native range
      // before applying it to keep the globe and markers on-screen.
      const baseRange = distanceMeters * 1.8;
      const minimumRange = earthRadius * 2.05;
      const maximumRange = earthRadius * 5.0;
      const nativeEquivalentRange = Math.min(
        maximumRange,
        Math.max(minimumRange, baseRange)
      );
      function fitRoute() {
        const width = Math.max(1, wwd.canvas.clientWidth);
        const height = Math.max(1, wwd.canvas.clientHeight);
        const aspect = width / height;
        const aspectBoost = aspect < 1 ? Math.sqrt(1 / aspect) : 1;
        // The circular compass uses a fixed full-sphere view on Android.
        // Only the larger globe sheet varies range with route distance.
        const cameraRange = ${if (expanded) "Math.max(earthRadius * 1.67, nativeEquivalentRange * 0.68 * aspectBoost)" else "earthRadius * 1.67 * aspectBoost"};
        wwd.navigator.lookAtLocation.latitude = routeMidpoint.latitude;
        wwd.navigator.lookAtLocation.longitude = routeMidpoint.longitude;
        wwd.navigator.range = Math.min(maximumRange, cameraRange);
        wwd.navigator.heading = cameraHeading;
        wwd.navigator.tilt = ${if (expanded) "width > height ? 12 : 0" else "0"};
        wwd.redraw();
      }

      const accuracyRing = document.getElementById('accuracy-ring');
      fitRoute();
      if (window.ResizeObserver) {
        new ResizeObserver(function () { fitRoute(); }).observe(document.getElementById('shell'));
      }

      let displayedHeading = null;
      let targetHeading = null;
      let markerColor = '#94a3b8';
      let animationFrame = null;
      let lastFrameTime = null;
      function shortestTurn(from, to) { return ((to - from + 540) % 360) - 180; }
      function drawHeading(time) {
        const elapsed = lastFrameTime === null ? 16 : Math.min(64, time - lastFrameTime);
        lastFrameTime = time;
        const delta = shortestTurn(displayedHeading, targetHeading);
        displayedHeading = (displayedHeading + delta * (1 - Math.exp(-elapsed / 100)) + 360) % 360;
        if (Math.abs(delta) < 0.1) displayedHeading = targetHeading;
        // Android's circular globe uses device heading minus CAMERA heading.
        // The midpoint's north differs from the user's north on a curved Earth;
        // subtracting the initial user-to-Kaaba bearing rotates this beam incorrectly.
        userPlacemark.attributes.imageSource = imageUri(
          userMarkerSvg(shortestTurn(cameraHeading, displayedHeading), markerColor)
        );
        wwd.redraw();
        animationFrame = displayedHeading === targetHeading ? null : requestAnimationFrame(drawHeading);
      }
      window.updateCompassHeading = function (heading, accuracy) {
        const hasHeading = heading !== null && Number.isFinite(heading);
        const hasAccuracy = hasHeading && accuracy !== null && Number.isFinite(accuracy) && accuracy >= 0;
        const aligned = hasHeading && Math.abs(shortestTurn(qiblaBearing, heading)) <= 5;
        markerColor = !hasAccuracy ? '#94a3b8' :
          accuracy <= 10 ? (aligned ? '#00c853' : '#10b981') : accuracy <= 25 ? '#ffa500' : '#ff4444';
        accuracyRing.setAttribute('stroke', ${if (expanded) "markerColor" else "'#ffffff'"});
        const turn = hasHeading ? shortestTurn(heading, qiblaBearing) : null;
        document.getElementById('guidance').textContent = !${expanded} ? '' :
          !hasHeading ? 'Waiting for compass' :
          aligned ? '✓ QIBLA · Aligned' :
          'Turn ' + (turn > 0 ? 'right ' : 'left ') + Math.round(Math.abs(turn)) + '°';
        accuracyRing.style.filter = 'drop-shadow(0 0 5px ' + markerColor + ')';
        if (!hasHeading) {
          if (animationFrame !== null) cancelAnimationFrame(animationFrame);
          animationFrame = null;
          displayedHeading = null;
          lastFrameTime = null;
          userPlacemark.attributes.imageSource = imageUri(userMarkerSvg(null, markerColor));
          wwd.redraw();
          return;
        }
        targetHeading = ((heading % 360) + 360) % 360;
        if (displayedHeading === null) displayedHeading = targetHeading;
        if (animationFrame === null) {
          lastFrameTime = null;
          animationFrame = requestAnimationFrame(drawHeading);
        }
      };
      const initialReading = window.pendingCompassReading || [null, null];
      window.updateCompassHeading(initialReading[0], initialReading[1]);
      status.style.display = 'none';
      wwd.redraw();
    } catch (error) {
      status.textContent = 'WorldWind globe unavailable. Check your connection and try again.';
      console.error(error);
    }
  }());
  </script>
</body>
</html>
""".trimIndent()
