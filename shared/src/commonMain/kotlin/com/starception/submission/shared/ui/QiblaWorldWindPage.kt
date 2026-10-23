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

private const val WORLDWIND_SCRIPT =
    "https://unpkg.com/@nasaworldwind/worldwind@0.11.0/build/dist/worldwind.min.js"

internal fun qiblaWorldWindHtml(
    latitude: Double,
    longitude: Double,
    qiblaBearing: Double,
    expanded: Boolean,
): String = """
<!doctype html>
<html>
<head>
  <meta charset="utf-8">
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
    #accuracy-ring { transition:stroke 240ms ease, filter 240ms ease; display:${if (expanded) "none" else "block"}; }
    #guidance { position:absolute; right:12px; top:12px; z-index:5; border-radius:12px;
      padding:6px 8px; max-width:32%; box-sizing:border-box; text-align:center;
      background:rgba(7,11,16,.75); color:white;
      font:600 11px/1.3 -apple-system,sans-serif; pointer-events:none; }
    #guidance:empty { display:none; }
    .marker-label { fill:white; font:600 12px -apple-system,sans-serif; text-anchor:middle;
      paint-order:stroke; stroke:#07121d; stroke-width:3px; stroke-linejoin:round; }
    #user-marker, #kaaba-marker { visibility:hidden; }
    #kaaba-icon { filter:drop-shadow(0 1px 3px rgba(0,0,0,.9)); }
  </style>
  <script src="$WORLDWIND_SCRIPT"></script>
</head>
<body>
  <div id="shell">
    <canvas id="globe">WorldWind globe</canvas>
    <svg id="overlay" xmlns="http://www.w3.org/2000/svg" aria-label="Qibla locations and facing direction">
      <defs>
        <radialGradient id="heading-light" gradientUnits="userSpaceOnUse" cx="0" cy="0" r="84">
          <stop offset="0" stop-color="currentColor" stop-opacity="1"/>
          <stop offset=".2" stop-color="currentColor" stop-opacity=".8"/>
          <stop offset=".55" stop-color="currentColor" stop-opacity=".4"/>
          <stop offset="1" stop-color="currentColor" stop-opacity="0"/>
        </radialGradient>
      </defs>
      <circle id="accuracy-ring" fill="none" stroke="#72d9bd" stroke-width="5" opacity=".72"/>
      <path id="qibla-route" fill="none" stroke="#e3c078" stroke-width="1.5" opacity=".7"/>
      <g id="user-marker" role="img" aria-label="Your location">
        <g id="heading-cone" opacity="0" style="color:#94a3b8">
          <path d="M0 0 L-45.13 -70.85 A84 84 0 0 1 45.13 -70.85 Z" fill="url(#heading-light)"/>
        </g>
        <circle r="9" fill="white"/>
        <circle id="user-dot" r="6.8" fill="#94a3b8"/>
        <text class="marker-label" y="26">You</text>
      </g>
      <g id="kaaba-marker" role="img" aria-label="Kaaba in Makkah">
        <svg id="kaaba-icon" x="-16" y="-18" width="32" height="36" viewBox="24 16 96 108">
          <polygon points="30,38 70,20 114,35 74,55" fill="#4b4a47"/>
          <polygon points="30,38 74,55 74,119 30,98" fill="#292929"/>
          <polygon points="74,55 114,35 114,98 74,119" fill="#111111"/>
          <polygon points="30,58 74,75 74,87 30,70" fill="#d4af37"/>
          <polygon points="74,75 114,57 114,69 74,87" fill="#f0cb52"/>
          <rect x="88" y="84" width="14" height="28" rx="2" fill="#b99028"/>
        </svg>
        <text id="kaaba-label" class="marker-label" y="-25">Kaaba</text>
      </g>
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
      const overlay = document.getElementById('overlay');
      const userMarker = document.getElementById('user-marker');
      const kaabaMarker = document.getElementById('kaaba-marker');
      const headingCone = document.getElementById('heading-cone');
      const userDot = document.getElementById('user-dot');
      const route = document.getElementById('qibla-route');
      // Match Android's 200 km marker lift, keeping icons legible above the globe surface.
      const markerAltitude = 200000;
      wwd.pixelScale = Math.min(window.devicePixelRatio || 1, 3);

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
      // Android uses a vertical 45-degree perspective. Web WorldWind's
      // frustum width is exactly the eye distance (tan(horizontal half-FOV) = .5).
      // Convert apparent globe size between projections instead of applying an arbitrary zoom factor.
      function fitRoute() {
        const width = Math.max(1, wwd.canvas.clientWidth);
        const height = Math.max(1, wwd.canvas.clientHeight);
        const aspect = width / height;
        const nativeMinimum = earthRadius * 2.05 * Math.min(2.2, Math.max(1, 1 / aspect));
        const nativeRange = Math.min(earthRadius * 5, Math.max(nativeMinimum, distanceMeters * 1.8));
        const nativeHalfFovTangent = Math.tan(toRadians(22.5)) * Math.min(1, aspect);
        const webHalfFovTangent = .5 * Math.min(1, 1 / aspect);
        const nativeEyeDistance = earthRadius + nativeRange;
        const nativeLimbDistance = Math.sqrt(nativeEyeDistance ** 2 - earthRadius ** 2);
        const webLimbDistance = nativeLimbDistance * nativeHalfFovTangent / webHalfFovTangent;
        const convertedRange = Math.sqrt(earthRadius ** 2 + webLimbDistance ** 2) - earthRadius;
        const fittedRange = earthRadius * (Math.sqrt(1 + (1 / (.90 * webHalfFovTangent)) ** 2) - 1);
        // For very long routes the endpoints approach the limb. Keep both above
        // the visible hemisphere rather than projecting a marker through Earth.
        const visibilityRange = earthRadius / Math.max(.08, Math.cos(distanceRadians / 2)) - earthRadius;
        wwd.navigator.lookAtLocation.latitude = routeMidpoint.latitude;
        wwd.navigator.lookAtLocation.longitude = routeMidpoint.longitude;
        wwd.navigator.range = Math.max(convertedRange, fittedRange, visibilityRange * 1.05);
        wwd.navigator.heading = cameraHeading;
        wwd.navigator.tilt = width > height ? 12 : 0;
        overlay.setAttribute('viewBox', '0 0 ' + width + ' ' + height);
        const ring = document.getElementById('accuracy-ring');
        ring.setAttribute('cx', width / 2);
        ring.setAttribute('cy', height / 2);
        ring.setAttribute('r', Math.min(width, height) * .48);
        wwd.redraw();
      }

      function projectLocation(latitude, longitude, altitude) {
        const point = new WorldWind.Vec3(0, 0, 0);
        const screen = new WorldWind.Vec3(0, 0, 0);
        wwd.globe.computePointFromPosition(latitude, longitude, altitude, point);
        if (!wwd.drawContext.project(point, screen)) return null;
        return {
          x: screen[0] * wwd.canvas.clientWidth / wwd.canvas.width,
          y: (wwd.canvas.height - screen[1]) * wwd.canvas.clientHeight / wwd.canvas.height
        };
      }
      const start = new WorldWind.Location(userLat, userLon);
      const destination = new WorldWind.Location(makkahLat, makkahLon);
      function placeMarker(element, point) {
        element.style.visibility = point ? 'visible' : 'hidden';
        if (point) element.setAttribute('transform', 'translate(' + point.x + ' ' + point.y + ')');
      }
      function renderLocations() {
        const user = projectLocation(userLat, userLon, markerAltitude);
        const kaaba = projectLocation(makkahLat, makkahLon, markerAltitude);
        placeMarker(userMarker, user);
        placeMarker(kaabaMarker, kaaba);
        if (user && kaaba) {
          const separation = Math.hypot(kaaba.x - user.x, kaaba.y - user.y);
          // At the destination the coordinates coincide; say so explicitly.
          const atKaaba = distanceMeters < 100;
          document.getElementById('kaaba-label').textContent = atKaaba ? 'You · Kaaba' : 'Kaaba';
          userMarker.style.visibility = atKaaba ? 'hidden' : 'visible';
          const coneScale = Math.min(1, Math.max(.4, separation * .8 / 84));
          headingCone.dataset.scale = coneScale;
          let commands = [];
          for (let i = 0; i <= 48; i++) {
            const location = new WorldWind.Location(0, 0);
            WorldWind.Location.interpolateGreatCircle(i / 48, start, destination, location);
            const p = projectLocation(location.latitude, location.longitude, markerAltitude);
            if (p) commands.push((commands.length ? 'L' : 'M') + p.x + ' ' + p.y);
          }
          route.setAttribute('d', commands.join(' '));
          if (displayedHeading !== null) {
            const forward = new WorldWind.Location(0, 0);
            WorldWind.Location.greatCircleLocation(start, displayedHeading, .01, forward);
            const facing = projectLocation(forward.latitude, forward.longitude, markerAltitude);
            if (facing) {
              const angle = toDegrees(Math.atan2(facing.x - user.x, user.y - facing.y));
              headingCone.setAttribute('transform', 'rotate(' + angle + ') scale(' + coneScale + ')');
              headingCone.dataset.angle = angle;
            }
          }
        }
      }
      wwd.redrawCallbacks.push(function (_, stage) {
        if (stage === WorldWind.AFTER_REDRAW) renderLocations();
      });

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
        if (Math.abs(delta) <= 0.35) targetHeading = displayedHeading;
        else displayedHeading = (displayedHeading + delta * (1 - Math.exp(-elapsed / 140)) + 360) % 360;
        renderLocations();
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
        userDot.setAttribute('fill', markerColor);
        headingCone.style.color = markerColor;
        document.querySelectorAll('#heading-light stop').forEach(function (stop) {
          stop.setAttribute('stop-color', markerColor);
        });
        headingCone.setAttribute('opacity', hasHeading ? '1' : '0');
        if (!hasHeading) {
          if (animationFrame !== null) cancelAnimationFrame(animationFrame);
          animationFrame = null;
          displayedHeading = null;
          lastFrameTime = null;
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
