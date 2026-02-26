package com.whispertflite.engine;

import android.content.Context;
import android.util.Log;

//import com.google.android.gms.tflite.client.TfLiteInitializationOptions;
//import com.google.android.gms.tflite.gpu.support.TfLiteGpu;
//import com.google.android.gms.tflite.java.TfLite;
import com.whispertflite.utils.WaveUtil;
import com.whispertflite.utils.WhisperUtil;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
//import org.tensorflow.lite.nnapi.NnApiDelegate;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

public class WhisperEngineJava implements WhisperEngine {
    private final String TAG = "WhisperEngineJava";
    private final WhisperUtil mWhisperUtil = new WhisperUtil();

    private final Context mContext;
    private boolean mIsInitialized = false;
    private Interpreter mInterpreter = null;
    private GpuDelegate gpuDelegate = null;

    public WhisperEngineJava(Context context) {
        mContext = context;
    }

    @Override
    public boolean isInitialized() {
        return mIsInitialized;
    }

    @Override
    public boolean initialize(String modelPath, String vocabPath, boolean multilingual) throws IOException {
        // Load model
        loadModel(modelPath);
        Log.d(TAG, "Model is loaded..." + modelPath);

        // Load filters and vocab
        boolean ret = mWhisperUtil.loadFiltersAndVocab(multilingual, vocabPath);
        if (ret) {
            mIsInitialized = true;
            Log.d(TAG, "Filters and Vocab are loaded..." + vocabPath);
        } else {
            mIsInitialized = false;
            Log.d(TAG, "Failed to load Filters and Vocab...");
        }

        return mIsInitialized;
    }

    // Unload the model by closing the interpreter
    @Override
    public void deinitialize() {
        if (mInterpreter != null) {
            mInterpreter.close();
            mInterpreter = null;
        }
        if (gpuDelegate != null) {
            gpuDelegate.close();
            gpuDelegate = null;
        }
    }

    @Override
    public String transcribeFile(String wavePath) {
        // Calculate Mel spectrogram
        Log.d(TAG, "Calculating Mel spectrogram...");
        float[] melSpectrogram = getMelSpectrogram(wavePath);
        Log.d(TAG, "Mel spectrogram is calculated...!");

        // Perform inference
        String result = runInference(melSpectrogram);
        Log.d(TAG, "Inference is executed...!");

        return result;
    }

    @Override
    public String transcribeBuffer(float[] samples) {
        return null;
    }

    // Load TFLite model
    private void loadModel(String modelPath) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(modelPath);
        FileChannel fileChannel = fileInputStream.getChannel();
        long startOffset = 0;
        long declaredLength = fileChannel.size();
        ByteBuffer tfliteModel = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);

        // Set the number of threads for inference
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(Runtime.getRuntime().availableProcessors());
        // Enable XNNPACK for faster CPU inference
        options.setUseXNNPACK(true);

        // Try to use GPU delegate for faster inference
        try {
            CompatibilityList compatList = new CompatibilityList();
            if (compatList.isDelegateSupportedOnThisDevice()) {
                Log.d(TAG, "GPU delegate is supported, enabling...");
                GpuDelegate.Options gpuOptions = new GpuDelegate.Options();
                gpuDelegate = new GpuDelegate(gpuOptions);
                options.addDelegate(gpuDelegate);
            } else {
                Log.d(TAG, "GPU delegate not supported, using CPU with XNNPACK");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to initialize GPU delegate, using CPU: " + e.getMessage());
        }

        mInterpreter = new Interpreter(tfliteModel, options);
    }

    private float[] getMelSpectrogram(String wavePath) {
        // Get samples in PCM_FLOAT format
        float[] samples = WaveUtil.getSamples(wavePath);

        // Only process actual recorded audio, not fixed 30 seconds
        int actualSamples = samples.length;
        int maxSamples = WhisperUtil.WHISPER_SAMPLE_RATE * WhisperUtil.WHISPER_CHUNK_SIZE; // 480000
        int samplesToProcess = Math.min(actualSamples, maxSamples);

        Log.d(TAG, "Actual samples: " + actualSamples + ", processing: " + samplesToProcess +
              " (" + (samplesToProcess / (float)WhisperUtil.WHISPER_SAMPLE_RATE) + " seconds)");

        int cores = Runtime.getRuntime().availableProcessors();

        // Calculate mel for actual audio only
        float[] melActual = mWhisperUtil.getMelSpectrogram(samples, samplesToProcess, cores);

        // Pad to full 3000 frames if needed (model expects fixed input)
        int expectedMelLen = WhisperUtil.WHISPER_MEL_LEN; // 3000
        int actualMelLen = samplesToProcess / WhisperUtil.WHISPER_HOP_LENGTH;

        if (actualMelLen < expectedMelLen) {
            // Create padded output
            float[] melPadded = new float[WhisperUtil.WHISPER_N_MEL * expectedMelLen];
            // Copy actual mel data
            System.arraycopy(melActual, 0, melPadded, 0, Math.min(melActual.length, melPadded.length));
            // Rest is already zero-padded (Java default)
            Log.d(TAG, "Mel spectrogram padded from " + actualMelLen + " to " + expectedMelLen + " frames");
            return melPadded;
        }

        return melActual;
    }

    private String runInference(float[] inputData) {
        // Create input tensor
        Tensor inputTensor = mInterpreter.getInputTensor(0);
        TensorBuffer inputBuffer = TensorBuffer.createFixedSize(inputTensor.shape(), inputTensor.dataType());
//        printTensorDump("Input Tensor Dump ===>", inputTensor);

        // Create output tensor
        Tensor outputTensor = mInterpreter.getOutputTensor(0);
        TensorBuffer outputBuffer = TensorBuffer.createFixedSize(outputTensor.shape(), DataType.FLOAT32);
//        printTensorDump("Output Tensor Dump ===>", outputTensor);

        // Load input data
        int inputSize = inputTensor.shape()[0] * inputTensor.shape()[1] * inputTensor.shape()[2] * Float.BYTES;
        ByteBuffer inputBuf = ByteBuffer.allocateDirect(inputSize);
        inputBuf.order(ByteOrder.nativeOrder());
        for (float input : inputData) {
            inputBuf.putFloat(input);
        }

        // To test mel data as a input directly
//        try {
//            byte[] bytes = Files.readAllBytes(Paths.get("/data/user/0/com.example.tfliteaudio/files/mel_spectrogram.bin"));
//            inputBuf = ByteBuffer.wrap(bytes);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }

        inputBuffer.loadBuffer(inputBuf);

//        Log.d(TAG, "Before inference...");
        // Run inference
        mInterpreter.run(inputBuffer.getBuffer(), outputBuffer.getBuffer());
//        Log.d(TAG, "After inference...");

        // Retrieve the results
        int outputLen = outputBuffer.getIntArray().length;
        Log.d(TAG, "output_len: " + outputLen);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < outputLen; i++) {
            int token = outputBuffer.getBuffer().getInt();
            if (token == mWhisperUtil.getTokenEOT())
                break;

            // Get word for token and Skip additional token
            if (token < mWhisperUtil.getTokenEOT()) {
                String word = mWhisperUtil.getWordFromToken(token);
                //Log.d(TAG, "Adding token: " + token + ", word: " + word);
                result.append(word);
            } else {
                if (token == mWhisperUtil.getTokenTranscribe())
                    Log.d(TAG, "It is Transcription...");

                if (token == mWhisperUtil.getTokenTranslate())
                    Log.d(TAG, "It is Translation...");

                String word = mWhisperUtil.getWordFromToken(token);
                Log.d(TAG, "Skipping token: " + token + ", word: " + word);
            }
        }

        return result.toString();
    }

    private void printTensorDump(String message, Tensor tensor) {
        Log.d(TAG,"Output Tensor Dump ===>");
        Log.d(TAG, "  shape.length: " + tensor.shape().length);
        for (int i = 0; i < tensor.shape().length; i++)
            Log.d(TAG, "    shape[" + i + "]: " + tensor.shape()[i]);
        Log.d(TAG, "  dataType: " + tensor.dataType());
        Log.d(TAG, "  name: " + tensor.name());
        Log.d(TAG, "  numBytes: " + tensor.numBytes());
        Log.d(TAG, "  index: " + tensor.index());
        Log.d(TAG, "  numDimensions: " + tensor.numDimensions());
        Log.d(TAG, "  numElements: " + tensor.numElements());
        Log.d(TAG, "  shapeSignature.length: " + tensor.shapeSignature().length);
        Log.d(TAG, "  quantizationParams.getScale: " + tensor.quantizationParams().getScale());
        Log.d(TAG, "  quantizationParams.getZeroPoint: " + tensor.quantizationParams().getZeroPoint());
        Log.d(TAG, "==================================================================");
    }
}
