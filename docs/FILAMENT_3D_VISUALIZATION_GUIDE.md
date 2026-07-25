# Salah 3D visualization

The Salah training visualization is rendered by Google Filament through SceneView's
Compose API. Its implementation lives in
`app/src/main/kotlin/com/starception/submission/feature/salah/visualization/Visualization3DView.kt`.

## Architecture

`SalahDataCollectionScreen` owns `VisualizationState` and recorded sensor samples.
`Visualization3DView` translates that immutable state into a Compose scene and sends
playback progress back through the existing callback. SceneView owns the Filament
engine and Android surface lifecycle, so no Fragment or manual GL-thread teardown is
needed.

The view exposes the same four modes:

- Sensor scatter: normalized accelerometer samples, posture colors, outlier emphasis,
  confidence volumes, and the current playback point.
- Phone model: animated paired posture figures for recorded label versus model
  prediction, with incorrect predictions highlighted.
- Gravity vector: per-posture acceleration direction on a one-g reference shell.
- Feature PCA: normalized principal-component points using the scatter scene.

## Rendering choices

- Physically based metallic/roughness materials
- Filmic tone mapping, FXAA, MSAA, ambient occlusion, bloom, and dynamic lighting
- A transparent Filament surface over a Compose gradient
- Bounded scatter geometry (`MAX_SCATTER_POINTS`) to keep recomposition and GPU load
  predictable with large training sets
- A 180 ms pose transition so playback changes are readable without feeling sluggish

## Extending the renderer

Keep data calculations outside Filament nodes and preserve `VisualizationState` as the
public boundary. A future rigged glTF model can replace the procedural digital-twin
figure inside the phone-model scene without changing controls, playback, or analysis.
