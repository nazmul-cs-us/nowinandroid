# LibGDX 3D Visualization Engine

Technical guide for the LibGDX-based 3D visualization of salah training data, embedded within Jetpack Compose.

## Overview

An interactive 3D visualization engine that displays collected sensor training data in three modes: scatter plot, animated humanoid model, and gravity vectors. Built with LibGDX 1.12.1 and rendered within a Jetpack Compose `AndroidView`.

## Dependencies

```toml
# gradle/libs.versions.toml
[versions]
libgdx = "1.12.1"

[libraries]
libgdx-core = { group = "com.badlogicgames.gdx", name = "gdx", version.ref = "libgdx" }
libgdx-backend-android = { group = "com.badlogicgames.gdx", name = "gdx-backend-android", version.ref = "libgdx" }
libgdx-platform-arm64 = { group = "com.badlogicgames.gdx", name = "gdx-platform", version.ref = "libgdx" }
```

Native libraries for arm64-v8a, armeabi-v7a, x86, x86_64.

---

## Architecture

### File Structure

```
feature/salah/visualization/
  SalahVisualization3D.kt     # Core LibGDX renderer (ApplicationAdapter)
  Visualization3DView.kt      # Compose AndroidView wrapper
  LibGDXFragment.kt           # Named fragment for GL surface
  VisualizationControls.kt    # Material 3 controls panel
  VisualizationMode.kt        # Mode enum + VisualizationState data class
```

### Component Diagram

```
Compose UI
  -> VisualizationControls (mode, filters, playback)
  -> Visualization3DView (AndroidView wrapper)
     -> LibGDXFragment (AndroidFragmentApplication)
        -> SalahVisualization3D (ApplicationAdapter)
           -> PerspectiveCamera + CameraInputController
           -> ModelBatch + Environment (lighting)
           -> ShapeRenderer (axes, grid, wireframes)
```

---

## Visualization Modes

### 1. Scatter Plot (`SCATTER`)
- 3D scatter plot of data points colored by posture
- Each data point is a small sphere `ModelInstance`
- Configurable axis mapping (X/Y/Z can be: pitch, roll, accel magnitude, gyro magnitude, etc.)
- Adjustable point size (1-10)
- Highlight sphere follows current playback position
- Posture filtering (show/hide specific postures)

### 2. Humanoid Model (`PHONE_MODEL`)
- Animated humanoid figure performing prayer postures
- Built from capsule-shaped body parts (head, torso, arms, legs)
- Prayer mat on the ground (green, 3x6 units)
- 6 distinct poses: Standing, Ruku, Going to Sujud, Sujud, Jalsa, Tashahhud
- Skin, clothing (white thobe), and pants materials with specular highlights
- Camera auto-positions for optimal viewing

### 3. Gravity Vectors (`GRAVITY_VECTOR`)
- Mean acceleration vectors per posture with arrow models
- Wireframe reference sphere (radius = 9.81 m/s^2, representing 1g)
- Arrow direction shows mean gravity orientation for each posture
- Color-coded by posture

---

## Humanoid Model Geometry

### Body Parts (all capsule-shaped unless noted)

| Part | Size | Color |
|------|------|-------|
| Head | sphere r=0.5 | Skin (warm tone) |
| Torso | capsule r=0.6, h=3.0 | White (clothing) |
| Upper Arm | capsule r=0.22, h=1.6 | White (clothing) |
| Forearm | capsule r=0.18, h=1.4 | Skin |
| Upper Leg | capsule r=0.28, h=2.0 | Dark (pants) |
| Lower Leg | capsule r=0.22, h=1.8 | Dark (pants) |
| Prayer Mat | box 3x0.05x6 | Green |

### Coordinate System
- **Y axis**: Up
- **X axis**: Right
- **Z axis**: Toward viewer
- Figure faces **-Z direction** (toward Qibla)
- **groundY = 0**: Feet level, prayer mat at y = -0.025

### Standing Pose (Qiyam) - Reference Heights
```
Head center:      y = 7.3  (torsoY + 2.0)
Torso center:     y = 5.3  (hipY + 1.5)
Hip joint:        y = 3.8  (groundY + 3.8)
Upper leg center: y = 2.8
Lower leg center: y = 0.9
Feet:             y = 0.0  (ground level)
```

---

## Compose Integration

### Visualization3DView.kt

Key patterns for embedding LibGDX in Compose:

1. **Custom FrameLayout** prevents `LazyColumn` from stealing touch events:
   ```kotlin
   override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
       parent?.requestDisallowInterceptTouchEvent(true)
       return super.dispatchTouchEvent(ev)
   }
   ```

2. **LibGDXFragment** (named public class) satisfies Android's requirement for fragment recreation:
   ```kotlin
   class LibGDXFragment : AndroidFragmentApplication()
   ```

3. **State synchronization** via `rememberUpdatedState` refs to avoid stale closures:
   ```kotlin
   val currentState by rememberUpdatedState(state)
   val currentOnStateChange by rememberUpdatedState(onStateChange)
   ```

4. **LaunchedEffect** blocks synchronize Compose state changes to GL thread:
   ```kotlin
   LaunchedEffect(state.mode, visualizationRef) {
       val viz = visualizationRef ?: return@LaunchedEffect
       awaitReady(viz)
       viz.setMode(state.mode)
   }
   ```

5. **Bidirectional playback callback** syncs GL thread state back to Compose:
   ```kotlin
   val onPlaybackUpdate = { index, posture, pitch, roll, accelMag, gyroMag, playing ->
       currentOnStateChange(currentState.copy(
           playbackIndex = index,
           currentPosture = posture,
           isPlaying = playing,
           // ...
       ))
   }
   ```

### Critical Implementation Notes

- **DO NOT** add a `LifecycleEventObserver` that calls `fragment.onResume()/onPause()`. The `FragmentManager` handles lifecycle automatically. Double-pausing causes LibGDX's `AndroidGraphics` to deadlock waiting for GL thread pause synchronization (SIGKILL).

- **DO NOT** call `view.setOnTouchListener()` on the LibGDX view. `initializeForView()` sets `AndroidInput` as the touch listener for camera gestures. Setting another listener overwrites it since `View.setOnTouchListener()` only supports one listener.

- **Readiness flag**: `SalahVisualization3D.isReady` is set after `create()` completes. The Compose wrapper suspends until ready before sending state updates.

---

## Camera Controls

### CameraInputController
- **Drag**: Orbit camera around target
- **Pinch zoom**: Custom dolly zoom (moves camera along view direction) instead of field-of-view change
- **Minimum distance**: 2f (prevents zooming through target)

```kotlin
override fun pinchZoom(amount: Float): Boolean {
    val delta = zoomDir.set(camera.direction).nor().scl(amount * translateUnits)
    camera.position.add(delta)
    if (camera.position.dst(target) < 2f) {
        camera.position.sub(delta)
    }
    camera.update()
    return true
}
```

---

## Thread Safety

All public API methods on `SalahVisualization3D` use `Gdx.app.postRunnable()` to marshal calls to the GL thread:

```kotlin
private fun safePostRunnable(runnable: Runnable) {
    try {
        Gdx.app?.postRunnable(runnable)
    } catch (_: Exception) {
        // GL context not ready yet
    }
}
```

This ensures no concurrent modification of GL resources.

---

## Rendering Pipeline

### Per-frame (`render()`):
1. Update camera controller
2. Update playback (if playing) - advance at 50Hz intervals
3. Clear screen (warm charcoal: 0.11, 0.11, 0.13)
4. Enable depth testing
5. Render mode-specific content (scatter/phone/gravity)
6. Render axes (RGB lines for X/Y/Z, 15 units long with arrowheads)
7. Render grid (40x40 grid, 2-unit spacing, subtle gray)

### Environment Lighting
- Ambient: 50% gray
- Key light: 85% white, direction (-1, -0.8, -0.2)
- Fill light: 30% white, direction (1, 0.5, 0.5)

---

## Posture Color Map

| Posture | Color | Hex |
|---------|-------|-----|
| QIYAM | Deep Sky Blue | `#00BFFF` |
| QIYAM_RISING | Dark Turquoise | `#00CED1` |
| RUKU | Dark Orange | `#FF8C00` |
| GOING_TO_SUJUD | Deep Pink | `#FF1493` |
| SUJUD | Lime Green | `#32CD32` |
| JALSA | Medium Purple | `#9370DB` |
| TASHAHHUD | Orange Red | `#FF4500` |

---

## Controls Panel (VisualizationControls.kt)

### Sections
1. **Mode Selector** - Segmented control: Scatter / Phone / Gravity
2. **Posture Filter** - Color-coded chips to show/hide postures
3. **Scatter Options** (scatter mode only):
   - Axis dropdowns (X/Y/Z mapping with color-coded labels)
   - Point size slider
4. **Playback Bar**:
   - Play/Pause button
   - Step backward/forward buttons
   - Progress slider (sample index)
   - Speed control (1-50x)
   - Sample counter
5. **Current Sample Card** - Shows posture, pitch, roll, accel, gyro for current playback position
6. **Data Quality Summary**:
   - Per-posture sample counts with progress bars
   - Balance percentage (min/max ratio)
   - Balance quality indicator (Balanced/Moderate/Imbalanced)
   - Total samples, sessions, postures counts

---

## Resource Management

### Disposal
All LibGDX resources are properly disposed in `dispose()`:
- `ModelBatch`, `ShapeRenderer`
- All `Model` objects (phone, ground, highlight, scatter point, humanoid parts, gravity arrows, prayer mat)

### Fragment Cleanup
```kotlin
onRelease = {
    visualizationRef = null
    fragmentRef?.let { frag ->
        val fm = (frag.activity as? FragmentActivity)?.supportFragmentManager
        fm?.beginTransaction()?.remove(frag)?.commitAllowingStateLoss()
    }
    fragmentRef = null
}
```
