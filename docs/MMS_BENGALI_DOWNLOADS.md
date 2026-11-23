# Bengali Tafsir voice downloads

MMS Bengali weights are **CC BY-NC 4.0**, not covered by the app's source-code license.
Commercial use requires separate applicable permission. The owner confirmed strictly
non-commercial distribution before CDN publication on 2026-11-20.

In an explicitly enabled non-commercial build, enable MMS in the app's Bengali voice
settings. If playback is requested without the model, a missing-content sheet opens.
It displays the license warning and requires acknowledgement before showing the standard
download card. Playback must be requested again after downloading; it never auto-starts.

The published manifest category is `model_tts_mms_bengali`. The model, matching tokens,
attribution and complete license were uploaded and SHA-256 verified. If the category
cannot be found, the sheet reports unavailable rather than claiming a successful download.
Following the owner's non-commercial distribution confirmation, debug builds enable
MMS by default, without requiring bundled assets. Pass `-PallowNonCommercialTts=false`
to disable it. Release builds keep MMS disabled. Bundling still requires explicit
`-PallowNonCommercialTts=true` acknowledgement.

An approved manifest must install the model and matching tokens under:

```
models/tts/vits-mms-ben/model.onnx
models/tts/vits-mms-ben/tokens.txt
```

Retain the model's license and attribution alongside those files. Do not remove the
commercial-release gate without separate applicable permission.

Local asset builds use an absolute asset directory, for example:

```sh
./gradlew :app:assembleDemoDebug \
  -PbengaliMmsAssetsDir="$PWD/work/tts/mms-bengali/assets" \
  -PallowNonCommercialTts=true
```

Before installing, verify the APK contains `assets/tts/vits-mms-ben/model.onnx`
and `tokens.txt`. The opt-in acknowledgement does not itself grant commercial rights.
