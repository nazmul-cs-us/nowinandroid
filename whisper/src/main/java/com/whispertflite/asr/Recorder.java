package com.whispertflite.asr;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.whispertflite.utils.WaveUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Recorder {

    public interface RecorderListener {
        void onUpdateReceived(String message);

        void onDataReceived(float[] samples);
    }

    private static final String TAG = "Recorder";
    public static final String ACTION_STOP = "Stop";
    public static final String ACTION_RECORD = "Record";
    public static final String MSG_RECORDING = "Recording...";
    public static final String MSG_RECORDING_DONE = "Recording done...!";

    private final Context mContext;
    private final AtomicBoolean mInProgress = new AtomicBoolean(false);

    private String mWavFilePath;
    private RecorderListener mListener;
    private final Lock lock = new ReentrantLock();
    private final Condition hasTask = lock.newCondition();
    private final Object fileSavedLock = new Object(); // Lock object for wait/notify

    private volatile boolean shouldStartRecording = false;
    private volatile boolean isShutdown = false;

    // Persistent AudioRecord to prevent other services from grabbing the mic
    private AudioRecord mAudioRecord = null;
    private final Object audioRecordLock = new Object();

    private final Thread workerThread;

    public Recorder(Context context) {
        this.mContext = context;

        // Initialize and start the worker thread
        workerThread = new Thread(this::recordLoop);
        workerThread.start();

        // Initialize AudioRecord early to hold the mic
        initializeAudioRecord();
    }

    private void initializeAudioRecord() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "AudioRecord permission is not granted, will init later");
            return;
        }

        synchronized (audioRecordLock) {
            if (mAudioRecord != null) {
                return; // Already initialized
            }

            int sampleRateInHz = 16000;
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            int audioSource = MediaRecorder.AudioSource.MIC;

            int bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
            Log.d(TAG, "Initializing persistent AudioRecord, bufferSize: " + bufferSize);

            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size: " + bufferSize);
                return;
            }

            mAudioRecord = new AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSize);
            Log.d(TAG, "Persistent AudioRecord state: " + mAudioRecord.getState() + " (1=INITIALIZED)");

            if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize persistent AudioRecord");
                mAudioRecord.release();
                mAudioRecord = null;
            }
        }
    }

    public void release() {
        isShutdown = true;
        mInProgress.set(false);

        // Wake up the worker thread
        lock.lock();
        try {
            shouldStartRecording = true;
            hasTask.signal();
        } finally {
            lock.unlock();
        }

        // Release AudioRecord
        synchronized (audioRecordLock) {
            if (mAudioRecord != null) {
                try {
                    if (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        mAudioRecord.stop();
                    }
                    mAudioRecord.release();
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing AudioRecord", e);
                }
                mAudioRecord = null;
                Log.d(TAG, "Persistent AudioRecord released");
            }
        }
    }

    public void setListener(RecorderListener listener) {
        this.mListener = listener;
    }

    public void setFilePath(String wavFile) {
        this.mWavFilePath = wavFile;
    }

    public void start() {
        if (!mInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Recording is already in progress...");
            return;
        }
        lock.lock();
        try {
            shouldStartRecording = true;
            hasTask.signal();
        } finally {
            lock.unlock();
        }
    }

    public void stop() {
        mInProgress.set(false);

        // Wait for the recording thread to finish
        synchronized (fileSavedLock) {
            try {
                fileSavedLock.wait(); // Wait until notified by the recording thread
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupted status
            }
        }
    }

    public boolean isInProgress() {
        return mInProgress.get();
    }

    private void sendUpdate(String message) {
        if (mListener != null)
            mListener.onUpdateReceived(message);
    }

    private void sendData(float[] samples) {
        if (mListener != null)
            mListener.onDataReceived(samples);
    }

    private void recordLoop() {
        while (!isShutdown) {
            lock.lock();
            try {
                while (!shouldStartRecording && !isShutdown) {
                    hasTask.await();
                }
                if (isShutdown) {
                    break;
                }
                shouldStartRecording = false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                lock.unlock();
            }

            if (isShutdown) {
                break;
            }

            // Start recording process with retry on failure
            int maxRetries = 3;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                if (isShutdown) break;
                try {
                    int bytesRecorded = recordAudio();
                    if (bytesRecorded > 0) {
                        break; // Success, exit retry loop
                    } else if (attempt < maxRetries) {
                        Log.w(TAG, "Recording returned 0 bytes, retrying (attempt " + attempt + "/" + maxRetries + ")");
                        sendUpdate("Retrying...");
                        // Wait longer before retry to let audio system recover
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Recording error (attempt " + attempt + ")...", e);
                    if (attempt >= maxRetries) {
                        sendUpdate(e.getMessage());
                    }
                }
            }
            // Only set mInProgress to false if another recording wasn't already requested
            // This prevents race condition when user starts new recording during playback
            lock.lock();
            try {
                if (!shouldStartRecording) {
                    mInProgress.set(false);
                }
            } finally {
                lock.unlock();
            }
        }
        Log.d(TAG, "recordLoop exiting");
    }

    private int recordAudio() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "AudioRecord permission is not granted");
            sendUpdate("Permission not granted for recording");
            return -1;
        }

        sendUpdate(MSG_RECORDING);

        int channels = 1;
        int bytesPerSample = 2;
        int sampleRateInHz = 16000;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

        int bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
        Log.d(TAG, "AudioRecord bufferSize: " + bufferSize);

        // Ensure persistent AudioRecord is initialized
        synchronized (audioRecordLock) {
            if (mAudioRecord == null) {
                initializeAudioRecord();
            }
            if (mAudioRecord == null) {
                Log.e(TAG, "Failed to initialize AudioRecord");
                sendUpdate("Microphone initialization failed");
                return -1;
            }

            Log.d(TAG, "Using persistent AudioRecord, state: " + mAudioRecord.getState());

            // If already recording, stop first
            if (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                Log.d(TAG, "AudioRecord already recording, stopping first");
                mAudioRecord.stop();
            }

            // Wait for AudioRecord to fully reset before starting
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Start recording
            mAudioRecord.startRecording();
            Log.d(TAG, "AudioRecord recording state: " + mAudioRecord.getRecordingState() + " (3=RECORDING, 1=STOPPED)");

            // Verify recording actually started
            if (mAudioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "Failed to start recording, state: " + mAudioRecord.getRecordingState());
                sendUpdate("Failed to start recording");
                synchronized (fileSavedLock) {
                    fileSavedLock.notify();
                }
                return -1;
            }

            // Warmup: Wait for AudioRecord to actually start filling the buffer
            // This is critical on some devices where the mic takes time to "wake up"
            // Give the mic a moment to start up
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Calculate maximum byte counts for 30 seconds (for saving)
        int bytesForThirtySeconds = sampleRateInHz * bytesPerSample * channels * 30;
        int bytesForThreeSeconds = sampleRateInHz * bytesPerSample * channels * 3;

        ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream(); // Buffer for saving data in wave file
        ByteArrayOutputStream realtimeBuffer = new ByteArrayOutputStream(); // Buffer for real-time processing

        byte[] audioData = new byte[bufferSize];
        int totalBytesRead = 0;

        int readCount = 0;
        while (mInProgress.get() && totalBytesRead < bytesForThirtySeconds && !isShutdown) {
            int bytesRead;
            synchronized (audioRecordLock) {
                if (mAudioRecord == null) break;
                bytesRead = mAudioRecord.read(audioData, 0, bufferSize);
            }
            if (bytesRead > 0) {
                // Check if we're getting actual audio data (not all zeros)
                if (readCount == 0) {
                    int nonZeroCount = 0;
                    for (int i = 0; i < Math.min(bytesRead, 100); i++) {
                        if (audioData[i] != 0) nonZeroCount++;
                    }
                    Log.d(TAG, "First audio chunk: bytesRead=" + bytesRead + ", nonZeroSamples=" + nonZeroCount + "/100");
                }
                readCount++;
                outputBuffer.write(audioData, 0, bytesRead);  // Save all bytes read up to 30 seconds
                realtimeBuffer.write(audioData, 0, bytesRead); // Accumulate real-time audio data
                totalBytesRead += bytesRead;

                // Check if realtimeBuffer has more than 3 seconds of data
                if (realtimeBuffer.size() >= bytesForThreeSeconds) {
                    float[] samples = convertToFloatArray(ByteBuffer.wrap(realtimeBuffer.toByteArray()));
                    realtimeBuffer.reset(); // Clear the buffer for the next accumulation
                    sendData(samples); // Send real-time data for processing
                }
            } else {
                Log.w(TAG, "AudioRecord read error: " + bytesRead);
                // On error, try a short sleep and continue (might be temporary system interference)
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                // If we've read some data, keep going; only break if no data at all after some attempts
                if (readCount > 0 || totalBytesRead > 0) {
                    continue; // We have some data, try to continue
                }
                // No data yet, give it a few more tries
                if (readCount < 10) {
                    readCount++;
                    continue;
                }
                break; // Give up after 10 failed attempts with no data
            }
        }

        // Stop recording but keep AudioRecord initialized for next use
        synchronized (audioRecordLock) {
            if (mAudioRecord != null && mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                mAudioRecord.stop();
                Log.d(TAG, "Recording stopped, AudioRecord kept alive for reuse");
            }
        }

        Log.d(TAG, "Recording complete: totalBytesRead=" + totalBytesRead + ", readCount=" + readCount);

        // Check if recorded data has actual audio content (analyze 16-bit samples properly)
        byte[] recordedData = outputBuffer.toByteArray();
        int numSamples = recordedData.length / 2;
        int nonZeroTotal = 0;
        int maxAmplitude = 0;
        int firstNonZeroIndex = -1;
        for (int i = 0; i < numSamples; i++) {
            // Read 16-bit sample in little-endian format
            short sample = (short) ((recordedData[i * 2 + 1] << 8) | (recordedData[i * 2] & 0xFF));
            if (sample != 0) {
                nonZeroTotal++;
                if (firstNonZeroIndex == -1) firstNonZeroIndex = i;
            }
            if (Math.abs(sample) > maxAmplitude) maxAmplitude = Math.abs(sample);
        }
        Log.d(TAG, "Recorded audio: totalSamples=" + numSamples + ", nonZeroTotal=" + nonZeroTotal + ", maxAmp=" + maxAmplitude + ", firstNonZeroAt=" + firstNonZeroIndex);

        // Save recorded audio data to file (up to 30 seconds)
        WaveUtil.createWaveFile(mWavFilePath, recordedData, sampleRateInHz, channels, bytesPerSample);
        sendUpdate(MSG_RECORDING_DONE);

        // Play back immediately so user can hear if recording worked
        Log.d(TAG, "Playing back recorded audio...");
        sendUpdate("Playing back...");
        try {
            int playBufferSize = AudioTrack.getMinBufferSize(sampleRateInHz, AudioFormat.CHANNEL_OUT_MONO, audioFormat);
            AudioTrack audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRateInHz,
                AudioFormat.CHANNEL_OUT_MONO,
                audioFormat,
                playBufferSize,
                AudioTrack.MODE_STREAM
            );
            audioTrack.play();
            audioTrack.write(recordedData, 0, recordedData.length);
            Thread.sleep((long) (recordedData.length / (sampleRateInHz * 2.0) * 1000) + 500);
            audioTrack.stop();
            audioTrack.release();
            Log.d(TAG, "Playback complete");
            sendUpdate("Playback done");
        } catch (Exception e) {
            Log.e(TAG, "Playback error", e);
        }

        // Notify the waiting thread that recording is complete
        synchronized (fileSavedLock) {
            fileSavedLock.notify(); // Notify that recording is finished
        }

        return totalBytesRead;
    }

    private float[] convertToFloatArray(ByteBuffer buffer) {
        buffer.order(ByteOrder.nativeOrder());
        float[] samples = new float[buffer.remaining() / 2];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = buffer.getShort() / 32768.0f;
        }
        return samples;
    }

    // Move file from /data/user/0/com.whispertflite/files/MicInput.wav to
    // sdcard path /storage/emulated/0/Android/data/com.whispertflite/files/MicInput.wav
    // Copy and delete the original file
    private void moveFileToSdcard(String waveFilePath) {
        File sourceFile = new File(waveFilePath);
        File destinationFile = new File(this.mContext.getExternalFilesDir(null), sourceFile.getName());
        try (FileInputStream inputStream = new FileInputStream(sourceFile);
             FileOutputStream outputStream = new FileOutputStream(destinationFile)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            if (sourceFile.delete()) {
                Log.d("FileMove", "File moved successfully to " + destinationFile.getAbsolutePath());
            } else {
                Log.e("FileMove", "Failed to delete the original file.");
            }

        } catch (IOException e) {
            Log.e("FileMove", "File move failed", e);
        }
    }

    /**
     * Test method: Records 3 seconds of audio and immediately plays it back.
     * Use this to verify the microphone is working properly.
     */
    public void testRecordAndPlayback() {
        new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "TEST: Permission not granted");
                    sendUpdate("Permission not granted");
                    return;
                }

                int sampleRateInHz = 16000;
                int channelConfig = AudioFormat.CHANNEL_IN_MONO;
                int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
                int audioSource = MediaRecorder.AudioSource.MIC;

                int bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
                Log.d(TAG, "TEST: bufferSize=" + bufferSize);

                AudioRecord audioRecord = new AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSize);
                Log.d(TAG, "TEST: AudioRecord state=" + audioRecord.getState());

                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "TEST: AudioRecord failed to initialize");
                    sendUpdate("Mic init failed");
                    audioRecord.release();
                    return;
                }

                // Record for 3 seconds
                sendUpdate("Recording 3 seconds...");
                ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
                byte[] audioData = new byte[bufferSize];

                audioRecord.startRecording();
                Log.d(TAG, "TEST: Recording state=" + audioRecord.getRecordingState());

                long startTime = System.currentTimeMillis();
                int totalBytesRead = 0;

                while (System.currentTimeMillis() - startTime < 3000) {
                    int bytesRead = audioRecord.read(audioData, 0, bufferSize);
                    if (bytesRead > 0) {
                        outputBuffer.write(audioData, 0, bytesRead);
                        totalBytesRead += bytesRead;
                    }
                }

                audioRecord.stop();
                audioRecord.release();

                byte[] recordedData = outputBuffer.toByteArray();

                // Check for non-zero data
                int nonZeroCount = 0;
                int maxVal = 0;
                for (int i = 0; i < recordedData.length - 1; i += 2) {
                    short sample = (short) ((recordedData[i + 1] << 8) | (recordedData[i] & 0xff));
                    if (sample != 0) nonZeroCount++;
                    if (Math.abs(sample) > maxVal) maxVal = Math.abs(sample);
                }

                Log.d(TAG, "TEST: Recorded " + totalBytesRead + " bytes, nonZeroSamples=" + nonZeroCount + "/" + (recordedData.length / 2) + ", maxAmplitude=" + maxVal);
                sendUpdate("Recorded: nonZero=" + nonZeroCount + ", max=" + maxVal);

                // Playback
                sendUpdate("Playing back...");
                int playBufferSize = AudioTrack.getMinBufferSize(sampleRateInHz, AudioFormat.CHANNEL_OUT_MONO, audioFormat);
                AudioTrack audioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRateInHz,
                    AudioFormat.CHANNEL_OUT_MONO,
                    audioFormat,
                    playBufferSize,
                    AudioTrack.MODE_STREAM
                );

                audioTrack.play();
                audioTrack.write(recordedData, 0, recordedData.length);

                // Wait for playback to finish
                Thread.sleep(3500);
                audioTrack.stop();
                audioTrack.release();

                sendUpdate("Test complete");
                Log.d(TAG, "TEST: Playback complete");

            } catch (Exception e) {
                Log.e(TAG, "TEST: Error", e);
                sendUpdate("Error: " + e.getMessage());
            }
        }).start();
    }
}
