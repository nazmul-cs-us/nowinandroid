package com.starception.submission.dua;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

import com.starception.submission.sensor.ActivityDetectionService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * DUA MANAGER: Plays appropriate duas (prayers) based on detected user activity
 * 
 * This class integrates with ActivityDetectionService to automatically play
 * relevant Islamic duas when user activity changes. Each activity type has
 * associated duas that are contextually appropriate.
 * 
 * ACTIVITY-DUA MAPPINGS:
 * - STATIONARY: General remembrance and contemplation duas
 * - WALKING: Travel duas and protection prayers
 * - RUNNING: Strength and endurance duas
 * - DRIVING: Travel safety and protection duas
 * - UNKNOWN: General supplications
 */
public class DuaManager implements ActivityDetectionService.ActivityChangeCallback {
    
    private static final String TAG = "DuaManager";
    
    private Context context;
    private MediaPlayer mediaPlayer;
    private ActivityDetectionService activityDetectionService;
    
    // Dua resources mapping - you can add actual audio files to res/raw/
    private Map<ActivityDetectionService.ActivityType, DuaInfo> duaMap;
    
    // Current state
    private ActivityDetectionService.ActivityType lastPlayedActivity;
    private long lastPlayTime = 0;
    private static final long MIN_PLAY_INTERVAL = 300000; // 5 minutes minimum between plays
    
    public DuaManager(Context context) {
        this.context = context.getApplicationContext();
        this.activityDetectionService = new ActivityDetectionService(context);
        
        initializeDuaMap();
    }
    
    /**
     * Initialize the mapping between activities and appropriate duas
     */
    private void initializeDuaMap() {
        duaMap = new HashMap<>();
        
        // Stationary - General remembrance
        duaMap.put(ActivityDetectionService.ActivityType.STATIONARY, 
            new DuaInfo(
                "SubhanAllah", // Text for display
                null, // Audio resource - add actual file to res/raw/
                "Glory be to Allah - for contemplation and stillness"
            )
        );
        
        // Walking - Travel duas
        duaMap.put(ActivityDetectionService.ActivityType.WALKING,
            new DuaInfo(
                "Bismillahi tawakkaltu 'alallah", // Travel dua
                null, // Audio resource
                "In the name of Allah, I trust in Allah - for safe walking"
            )
        );
        
        // Running - Strength duas
        duaMap.put(ActivityDetectionService.ActivityType.RUNNING,
            new DuaInfo(
                "Allahumma qawwini ala dhikrika wa shukrika wa husni 'ibadatik",
                null, // Audio resource
                "O Allah, strengthen me in remembering You, thanking You, and worshipping You well"
            )
        );
        
        // Driving - Travel safety duas
        duaMap.put(ActivityDetectionService.ActivityType.DRIVING,
            new DuaInfo(
                "Subhanal-ladhi sakh-khara lana hadha wa ma kunna lahu muqrineen wa inna ila rabbina lamunqaliboon",
                null, // Audio resource
                "Glory to Him Who has subjected this to us, and we would never have it under control"
            )
        );
        
        // Unknown - General supplication
        duaMap.put(ActivityDetectionService.ActivityType.UNKNOWN,
            new DuaInfo(
                "Rabbi zidni ilman wa warzuqni fahman",
                null, // Audio resource
                "O my Lord, increase me in knowledge and grant me understanding"
            )
        );
    }
    
    /**
     * Start activity detection and dua playing
     */
    public void start() {
        if (activityDetectionService.hasRequiredPermissions()) {
            activityDetectionService.startDetection(this);
            Log.i(TAG, "DuaManager started with activity detection");
        } else {
            Log.w(TAG, "Required permissions not available for activity detection");
        }
    }
    
    /**
     * Stop activity detection and dua playing
     */
    public void stop() {
        activityDetectionService.stopDetection();
        stopCurrentDua();
        Log.i(TAG, "DuaManager stopped");
    }
    
    /**
     * Called when user activity changes
     */
    @Override
    public void onActivityChanged(ActivityDetectionService.ActivityType newActivity, 
                                ActivityDetectionService.ActivityType previousActivity) {
        
        Log.i(TAG, "Activity changed: " + previousActivity + " -> " + newActivity);
        
        // Play dua if enough time has passed since last play
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPlayTime > MIN_PLAY_INTERVAL || 
            lastPlayedActivity != newActivity) {
            
            playDuaForActivity(newActivity);
            lastPlayedActivity = newActivity;
            lastPlayTime = currentTime;
        }
    }
    
    /**
     * Play dua appropriate for the given activity
     */
    private void playDuaForActivity(ActivityDetectionService.ActivityType activity) {
        DuaInfo duaInfo = duaMap.get(activity);
        if (duaInfo == null) {
            Log.w(TAG, "No dua found for activity: " + activity);
            return;
        }
        
        Log.i(TAG, "Playing dua for " + activity + ": " + duaInfo.text);
        
        // For now, just log the dua text and meaning
        // In a full implementation, you would play the audio file here
        logDuaInfo(duaInfo);
        
        // TODO: Implement audio playback when you have audio files
        // playAudioDua(duaInfo.audioResource);
    }
    
    /**
     * Log dua information (placeholder for actual audio playback)
     */
    private void logDuaInfo(DuaInfo duaInfo) {
        Log.i(TAG, "🕌 DUA: " + duaInfo.text);
        Log.i(TAG, "📖 Meaning: " + duaInfo.meaning);
        
        // You could also show this as a notification or toast
        // NotificationManager.showDuaNotification(duaInfo);
    }
    
    /**
     * Play audio dua from resource (to be implemented when audio files are added)
     */
    private void playAudioDua(Integer audioResource) {
        if (audioResource == null) {
            Log.d(TAG, "No audio resource available for this dua");
            return;
        }
        
        try {
            stopCurrentDua(); // Stop any currently playing dua
            
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(context, Uri.parse("android.resource://" + 
                context.getPackageName() + "/" + audioResource));
            
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                    Log.i(TAG, "Dua audio started playing");
                }
            });
            
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Log.i(TAG, "Dua audio finished playing");
                    releaseMediaPlayer();
                }
            });
            
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e(TAG, "Error playing dua audio: " + what + ", " + extra);
                    releaseMediaPlayer();
                    return true;
                }
            });
            
            mediaPlayer.prepareAsync();
            
        } catch (IOException e) {
            Log.e(TAG, "Error setting up dua audio", e);
            releaseMediaPlayer();
        }
    }
    
    /**
     * Stop currently playing dua
     */
    private void stopCurrentDua() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            releaseMediaPlayer();
        }
    }
    
    /**
     * Release MediaPlayer resources
     */
    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
    
    /**
     * Get current detected activity
     */
    public ActivityDetectionService.ActivityType getCurrentActivity() {
        return activityDetectionService.getCurrentActivity();
    }
    
    /**
     * Check if dua manager is running
     */
    public boolean isRunning() {
        return activityDetectionService.isRunning();
    }
    
    /**
     * Manually play a specific dua (for testing or manual triggers)
     */
    public void playDuaManually(ActivityDetectionService.ActivityType activity) {
        playDuaForActivity(activity);
    }
    
    /**
     * Data class to hold dua information
     */
    private static class DuaInfo {
        final String text;           // Arabic text of the dua
        final Integer audioResource; // Resource ID for audio file (null if not available)
        final String meaning;        // English meaning/translation
        
        DuaInfo(String text, Integer audioResource, String meaning) {
            this.text = text;
            this.audioResource = audioResource;
            this.meaning = meaning;
        }
    }
    
    /**
     * Clean up resources when done
     */
    public void cleanup() {
        stop();
        releaseMediaPlayer();
    }
}
