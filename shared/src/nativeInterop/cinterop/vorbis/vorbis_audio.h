/* iOS adapter for stb_vorbis v1.22 (MIT; license in stb_vorbis.c). */
#ifndef DEENLY_VORBIS_AUDIO_H
#define DEENLY_VORBIS_AUDIO_H
#include <stdbool.h>
#define STB_VORBIS_STATIC
#define STB_VORBIS_NO_PUSHDATA_API
#include "stb_vorbis.c"

#ifdef __APPLE__
#include <AudioToolbox/AudioToolbox.h>
#include <CoreFoundation/CoreFoundation.h>

/* Stream into AAC rather than keeping an entire long surah as PCM in memory/on disk. */
static inline int deenly_prepare_vorbis_m4a(const char *input, const char *output) {
    int error = 0;
    stb_vorbis *decoder = stb_vorbis_open_filename(input, &error, NULL);
    if (!decoder) return 0;
    stb_vorbis_info info = stb_vorbis_get_info(decoder);
    if (info.channels < 1 || info.channels > 2 || !info.sample_rate) {
        stb_vorbis_close(decoder);
        return 0;
    }
    AudioStreamBasicDescription encoded = {0};
    encoded.mSampleRate = info.sample_rate;
    encoded.mFormatID = kAudioFormatMPEG4AAC;
    encoded.mChannelsPerFrame = info.channels;
    UInt32 descriptionSize = sizeof(encoded);
    OSStatus status = AudioFormatGetProperty(kAudioFormatProperty_FormatInfo, 0, NULL,
                                             &descriptionSize, &encoded);
    CFURLRef url = CFURLCreateFromFileSystemRepresentation(NULL, (const UInt8 *)output,
                                                         strlen(output), false);
    ExtAudioFileRef file = NULL;
    if (status == noErr && url) {
        status = ExtAudioFileCreateWithURL(url, kAudioFileM4AType, &encoded, NULL,
                                          kAudioFileFlags_EraseFile, &file);
    }
    if (url) CFRelease(url);
    if (!file) {
        stb_vorbis_close(decoder);
        return 0;
    }
    AudioStreamBasicDescription pcm = {0};
    pcm.mSampleRate = info.sample_rate;
    pcm.mFormatID = kAudioFormatLinearPCM;
    pcm.mFormatFlags = kAudioFormatFlagIsSignedInteger | kAudioFormatFlagIsPacked;
    pcm.mChannelsPerFrame = info.channels;
    pcm.mBitsPerChannel = 16;
    pcm.mFramesPerPacket = 1;
    pcm.mBytesPerFrame = pcm.mBytesPerPacket = info.channels * sizeof(short);
    status = ExtAudioFileSetProperty(file, kExtAudioFileProperty_ClientDataFormat,
                                    sizeof(pcm), &pcm);
    short samples[4096 * 2];
    unsigned long long written = 0;
    while (status == noErr) {
        int frames = stb_vorbis_get_samples_short_interleaved(decoder, info.channels,
                                                              samples, 4096 * info.channels);
        if (!frames) break;
        AudioBufferList buffers = {0};
        buffers.mNumberBuffers = 1;
        buffers.mBuffers[0].mNumberChannels = info.channels;
        buffers.mBuffers[0].mDataByteSize = frames * pcm.mBytesPerFrame;
        buffers.mBuffers[0].mData = samples;
        status = ExtAudioFileWrite(file, frames, &buffers);
        written += frames;
    }
    error = stb_vorbis_get_error(decoder);
    stb_vorbis_close(decoder);
    OSStatus finish = ExtAudioFileDispose(file);
    if (status != noErr || finish != noErr || error || !written) {
        remove(output);
        return 0;
    }
    return 1;
}
#endif
#endif
