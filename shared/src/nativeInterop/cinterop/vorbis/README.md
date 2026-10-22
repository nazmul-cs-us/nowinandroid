stb_vorbis v1.22, vendored from https://github.com/nothings/stb/blob/master/stb_vorbis.c

SHA-256: `4c7cb2ff1f7011e9d67950446b7eb9ca044f2e464d76bfbb0b84dd2e23e65636`

License: MIT (full notice at the end of stb_vorbis.c).

The iOS-only adapter streams Ogg Vorbis into a cached AAC/M4A file using AudioToolbox.
This preserves the existing AVAudioEngine player, seeking, pause/resume and playback bus.
Only a small PCM chunk is allocated, including for multi-hour surahs.
