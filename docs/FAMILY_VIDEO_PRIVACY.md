# Family video face privacy (Android and iOS)

Every new Android or iOS family video call starts with face blur enabled. The caller
can change it in the outgoing preview or during the call using the **Face blur
on/off** chip. The choice survives camera toggles, camera switching, and the
transition from ringing to connected; it resets when the call ends.

## Android local model

The app depends on `com.google.mlkit:face-detection:16.1.7`, the **bundled** model
variant. Its model files are packaged in `assets/models_bundled/` by the Android
asset merger. Face detection runs on the device and does not require a model
download or upload camera frames for inference. This does not remove the network
requirement for the video call itself.

Reference: https://developers.google.com/ml-kit/vision/face-detection/android

## Android media processing

`FamilyFacePrivacyProcessor` sits between the camera's `CapturerObserver` and the
WebRTC `VideoSource`. Both the local preview and outgoing track receive the same
processed frames. This is not a Compose blur overlay.

While enabled, only one frame is processed at a time on a dedicated worker.
Other frames are dropped. Each accepted frame is rotated upright, detected, then
blurred in its Y, U, and V planes before delivery. All detected face rectangles
are expanded by one third on each edge. Three box blur passes approximate a strong
Gaussian blur. Detection uses the current frame rather than cached face boxes.

Detection initialization failure, timeout (500 ms), no faces, or invalid face
bounds trigger whole-frame blur. Conversion/rendering failures drop the frame.
The raw frame path is available only after explicitly disabling privacy. A
synchronized gate prevents an in-flight processing result from being delivered
after privacy changes or the processor closes.

Face detection can miss a second face even when another face is detected. Blur
reduces visible facial detail; it is not a guarantee of anonymity. Live testing
is required for lighting, rapid movement, profiles, multiple faces, and sustained
frame rate/battery use. Both Android and iOS filter the outgoing camera track.

## Android verification

```sh
./gradlew :app:compileDemoDebugKotlin
./gradlew :app:testDemoDebugUnitTest \
  --tests '*FamilyPrivacyBlurTest' \
  --tests '*FamilyFacePrivacyProcessorTest' \
  --tests '*FamilyOutgoingVideoUiTest'
```

Tests exercise the actual processor using in-memory WebRTC buffers and a test
face-detector function; they do not validate native ML Kit inference accuracy.

On a connected Pixel, verify two callers see the same blur, including the first
camera frame, incoming acceptance, outgoing preview, rotation, switching cameras,
and camera off/on. Toggle blur off and on during both ringing and connected
states, then start another call and verify it defaults on again. Verify the
first preview after a fresh install works without internet to confirm the bundled
model path. Do not use an offline call failure as a model test.

## iOS implementation

`FamilyFacePrivacyProcessor.swift` uses Apple's Vision
`VNDetectFaceRectanglesRequest`. The model is provided locally by iOS; no extra
model download, third-party SDK, or cloud inference is required. Core Image
applies a strong Gaussian blur before the frame reaches `RTCVideoSource`. Physical devices use hardware
acceleration; the simulator uses CPU inference because its GPU inference context
may be unavailable.
Every accepted frame is rotated upright and detected individually. Expanded
face regions use the same one-third padding as Android. No faces, invalid
bounds, and detection errors use whole-frame blur. Unsupported frame buffers
and failed output allocations are dropped. Processing uses one worker with no
frame backlog, a bounded pixel-buffer pool, and a gate that discards results
when privacy changes or the call closes.

The iOS store resets privacy on a new call, call end, session loss, and sign-out.
A call's existing choice survives camera recreation, camera switching, and
ringing-to-active transitions. The control appears in outgoing preview and
both compact and regular active call layouts.

Generate the project and run the independent simulator tests:

```sh
cd iosApp
xcodegen generate
xcodebuild -project iosApp.xcodeproj -scheme FamilyVideoPrivacyTests \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro,OS=27.2' \
  CODE_SIGNING_ALLOWED=NO test
```

These tests use actual Core Image rendering and WebRTC pixel buffers. They
cover fallback blur, face-region masking, rotations, explicit opt-out, busy
frame dropping, close, and privacy changes while detection is running. A Vision
smoke test also exercises the OS detector locally with a generated frame. A
physical iPhone and two-party call are still needed to assess face accuracy,
thermal behavior, camera switching, and live transmission.

Apple reference: https://developer.apple.com/documentation/vision/vndetectfacerectanglesrequest
