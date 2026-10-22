#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
cat > "$work_dir/check.c" <<'C'
#include "vorbis_audio.h"
int main(int argc, char **argv) {
    int error = 0;
    stb_vorbis *file = stb_vorbis_open_filename(argv[1], &error, NULL);
    if (!file) return argc == 3 ? 0 : 1;
    if (argc == 3) { stb_vorbis_close(file); return 2; }
    stb_vorbis_info info = stb_vorbis_get_info(file);
    if (info.channels < 1 || info.channels > 2 || !info.sample_rate) return 3;
    short chunk[4096 * 2];
    unsigned long long frames = 0, energy = 0;
    int count;
    while ((count = stb_vorbis_get_samples_short_interleaved(file, info.channels, chunk, 4096 * info.channels)) > 0) {
        frames += count;
        for (int i = 0; i < count * info.channels; ++i) energy += abs(chunk[i]);
    }
    error = stb_vorbis_get_error(file);
    unsigned int expected = stb_vorbis_stream_length_in_samples(file);
    stb_vorbis_close(file);
    if (error || !energy || frames != expected) return 4;
    printf("Decoded %llu frames (%d channels, %u Hz) with an 8192-sample buffer\n", frames, info.channels, info.sample_rate);
    return 0;
}
C
cc -std=c99 -O2 -I "$repo_root/shared/src/nativeInterop/cinterop/vorbis" "$work_dir/check.c" -lm -o "$work_dir/check"
for channels in 1 2; do
    ffmpeg -nostdin -v error -f lavfi -i 'sine=frequency=440:duration=3' -ar 44100 -ac "$channels" -c:a libvorbis "$work_dir/audio-$channels.ogg"
    "$work_dir/check" "$work_dir/audio-$channels.ogg"
done
printf 'not an audio file' > "$work_dir/invalid.ogg"
"$work_dir/check" "$work_dir/invalid.ogg" expect-invalid
