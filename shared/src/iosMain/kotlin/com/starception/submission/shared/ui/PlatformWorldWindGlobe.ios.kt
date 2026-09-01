package com.starception.submission.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.UIKit.UIColor
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

private const val WORLDWIND_SCRIPT =
    "https://unpkg.com/@nasaworldwind/worldwind@0.11.0/build/dist/worldwind.min.js"

/**
 * NASA Web WorldWind hosted by WKWebView.
 *
 * WorldWind Kotlin's native iOS module starts at a newer Kotlin/Compose
 * toolchain than this app currently uses. Web WorldWind is the same globe
 * engine family and gives the existing iOS target a real interactive globe
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
    modifier: Modifier,
) {
    val pageKey = remember(latitude, longitude, qiblaBearing) {
        "$latitude,$longitude,$qiblaBearing"
    }
    val html = remember(latitude, longitude, qiblaBearing) {
        worldWindHtml(latitude, longitude, qiblaBearing)
    }

    UIKitView(
        factory = {
            WKWebView(
                frame = CGRectMake(0.0, 0.0, 1.0, 1.0),
                configuration = WKWebViewConfiguration(),
            ).apply {
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
                val heading = headingDegrees?.toString() ?: "null"
                val accuracy = headingAccuracyDegrees?.toString() ?: "null"
                webView.evaluateJavaScript(
                    "window.updateCompassHeading && window.updateCompassHeading($heading, $accuracy)",
                    completionHandler = null,
                )
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.loadHTMLString("", null)
        },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true,
        ),
    )
}

private fun worldWindHtml(
    latitude: Double,
    longitude: Double,
    qiblaBearing: Double,
): String = """
<!doctype html>
<html>
<head>
  <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
  <style>
    html, body { margin:0; width:100%; height:100%; overflow:hidden; background:transparent; }
    #shell { position:relative; width:100%; height:100%; overflow:hidden; border-radius:50%;
      background:#07121d; }
    #globe { display:block; width:100%; height:100%; touch-action:none; background:#07121d;
      border-radius:50%; }
    #status { position:absolute; inset:0; display:flex; align-items:center; justify-content:center;
      padding:20px; color:#dce8f2; background:#07121d; font:600 14px -apple-system,sans-serif;
      text-align:center; }
    #overlay { position:absolute; inset:0; width:100%; height:100%; pointer-events:none; }
    #heading-cone { transform-origin:150px var(--user-y); transition:transform 90ms linear; }
    #accuracy-ring { transition:stroke 240ms ease, filter 240ms ease; }
  </style>
  <script src="$WORLDWIND_SCRIPT"></script>
</head>
<body>
  <div id="shell">
    <canvas id="globe">WorldWind globe</canvas>
    <svg id="overlay" viewBox="0 0 300 300" aria-hidden="true">
      <defs>
        <linearGradient id="cone-gradient" x1="0" y1="1" x2="0" y2="0">
          <stop offset="0" stop-color="#10b981" stop-opacity=".62"/>
          <stop offset="1" stop-color="#10b981" stop-opacity="0"/>
        </linearGradient>
      </defs>
      <path id="heading-cone" fill="url(#cone-gradient)"/>
      <circle id="user-ring" fill="white"/>
      <circle id="user-dot" fill="#10b981"/>
      <circle id="accuracy-ring" cx="150" cy="150" r="144" fill="none" stroke="white"
        stroke-width="8" opacity=".94"/>
    </svg>
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
      wwd.addLayer(new WorldWind.AtmosphereLayer());

      const userLat = $latitude;
      const userLon = $longitude;
      const qiblaBearing = $qiblaBearing;
      const makkahLat = 21.4225;
      const makkahLon = 39.8262;
      const routeLayer = new WorldWind.RenderableLayer('Qibla route');

      const routeAttributes = new WorldWind.ShapeAttributes(null);
      routeAttributes.outlineColor = new WorldWind.Color(0.20, 0.88, 0.55, 1.0);
      routeAttributes.outlineWidth = 5;
      const route = new WorldWind.SurfacePolyline([
        new WorldWind.Location(userLat, userLon),
        new WorldWind.Location(makkahLat, makkahLon)
      ], routeAttributes);
      route.pathType = WorldWind.GREAT_CIRCLE;
      routeLayer.addRenderable(route);

      function marker(lat, lon, color, label) {
        const attributes = new WorldWind.PlacemarkAttributes(null);
        const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48">' +
          '<circle cx="24" cy="24" r="18" fill="' + color + '" stroke="white" stroke-width="4"/>' +
          '<circle cx="24" cy="24" r="5" fill="white"/></svg>';
        attributes.imageSource = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
        attributes.imageScale = 0.72;
        attributes.labelAttributes.color = WorldWind.Color.WHITE;
        attributes.labelAttributes.offset = new WorldWind.Offset(
          WorldWind.OFFSET_FRACTION, 0.5, WorldWind.OFFSET_FRACTION, -0.45
        );
        const placemark = new WorldWind.Placemark(
          new WorldWind.Position(lat, lon, 0), false, attributes
        );
        placemark.label = label;
        placemark.altitudeMode = WorldWind.CLAMP_TO_GROUND;
        routeLayer.addRenderable(placemark);
      }

      marker(makkahLat, makkahLon, '#e2b94f', 'Kaaba');
      wwd.addLayer(routeLayer);

      const toRadians = value => value * Math.PI / 180;
      const toDegrees = value => value * 180 / Math.PI;
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
      const dLat = toRadians(makkahLat - userLat);
      const dLon = toRadians(makkahLon - userLon);
      const a = Math.sin(dLat / 2) ** 2 + Math.cos(toRadians(userLat)) *
        Math.cos(toRadians(makkahLat)) * Math.sin(dLon / 2) ** 2;
      const distanceMeters = 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
      const routeMidpoint = midpoint(userLat, userLon, makkahLat, makkahLon);
      const cameraHeading = bearingBetween(
        routeMidpoint.latitude, routeMidpoint.longitude, makkahLat, makkahLon
      );
      wwd.navigator.lookAtLocation.latitude = routeMidpoint.latitude;
      wwd.navigator.lookAtLocation.longitude = routeMidpoint.longitude;
      wwd.navigator.range = 6378137 * 2.0;
      wwd.navigator.heading = cameraHeading;
      wwd.navigator.tilt = 0;

      const halfAngle = angularDistance(userLat, userLon, makkahLat, makkahLon) / 2;
      const focalLength = 1 / Math.tan(toRadians(22.5));
      const normalizedOffset = Math.sin(halfAngle) * focalLength / (2.0 + Math.cos(halfAngle));
      const userY = 150 + normalizedOffset * 150;
      const coneLength = 112;
      const coneHalfWidth = 58;
      const cone = document.getElementById('heading-cone');
      const userRing = document.getElementById('user-ring');
      const userDot = document.getElementById('user-dot');
      const accuracyRing = document.getElementById('accuracy-ring');
      cone.setAttribute('d', 'M 150 ' + userY + ' L ' + (150 - coneHalfWidth) + ' ' +
        (userY - coneLength) + ' Q 150 ' + (userY - coneLength - 18) + ' ' +
        (150 + coneHalfWidth) + ' ' + (userY - coneLength) + ' Z');
      cone.style.setProperty('--user-y', userY + 'px');
      userRing.setAttribute('cx', '150');
      userRing.setAttribute('cy', String(userY));
      userRing.setAttribute('r', '12');
      userDot.setAttribute('cx', '150');
      userDot.setAttribute('cy', String(userY));
      userDot.setAttribute('r', '7');

      window.updateCompassHeading = function (heading, accuracy) {
        const hasHeading = heading !== null && Number.isFinite(heading);
        const relative = hasHeading ? heading - qiblaBearing : 0;
        cone.style.transform = 'rotate(' + relative + 'deg)';
        cone.style.opacity = hasHeading ? '1' : '.38';
        const color = accuracy === null ? '#ffffff' :
          accuracy <= 10 ? '#10b981' : accuracy <= 25 ? '#ffa500' : '#ff4444';
        accuracyRing.setAttribute('stroke', color);
        accuracyRing.style.filter = 'drop-shadow(0 0 7px ' + color + ')';
        userDot.setAttribute('fill', color === '#ffffff' ? '#10b981' : color);
        wwd.redraw();
      };
      window.updateCompassHeading(null, null);
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
