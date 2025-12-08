package com.starception.submission.dua;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
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
 * TRAVEL DUA LOGIC:
 * - When user starts DRIVING, wait for 1 minute of continuous driving
 * - After 1 minute, play the travel dua so user can relax and recite
 * - If user stops (traffic) and resumes within 5 minutes, don't play again (same trip)
 * - If user stops for 5+ minutes then drives again, treat as new trip and play dua
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
    private Handler handler;

    // Dua resources mapping - you can add actual audio files to res/raw/
    private Map<ActivityDetectionService.ActivityType, DuaInfo> duaMap;

    // Current state for general duas
    private ActivityDetectionService.ActivityType lastPlayedActivity;
    private long lastPlayTime = 0;
    private static final long MIN_PLAY_INTERVAL = 300000; // 5 minutes minimum between plays

    // Travel dua specific state
    private static final long CONTINUOUS_DRIVING_THRESHOLD = 60000; // 1 minute of continuous driving
    private static final long NEW_TRIP_THRESHOLD = 300000; // 5 minutes = new trip

    private long drivingStartTime = 0;           // When current driving session started
    private long lastDrivingEndTime = 0;         // When last driving session ended
    private boolean travelDuaPlayedForTrip = false;  // Whether travel dua was played for current trip
    private boolean isDriving = false;           // Current driving state
    private Runnable travelDuaRunnable = null;   // Pending 1-minute timer callback
    
    public DuaManager(Context context) {
        this.context = context.getApplicationContext();
        this.activityDetectionService = new ActivityDetectionService(context);
        this.handler = new Handler(Looper.getMainLooper());

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
        
        // On Phone - Protection dua for safe usage
        duaMap.put(ActivityDetectionService.ActivityType.ON_PHONE, 
            new DuaInfo(
                "Allahumma baarik li fi hadha wa qini sharrahu", // Text for display
                null, // Audio resource - add actual file to res/raw/
                "O Allah, bless me in this and protect me from its harm - for mindful phone usage"
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
     *
     * TRAVEL DUA LOGIC FOR DRIVING:
     * 1. When DRIVING starts → check if this is a new trip (stopped for 5+ minutes)
     * 2. If new trip → start 1-minute timer
     * 3. After 1 minute of continuous driving → play travel dua
     * 4. If user stops (traffic) and resumes within 5 min → don't play again (same trip)
     * 5. If user stops for 5+ minutes → reset trip, next drive triggers new dua
     */
    @Override
    public void onActivityChanged(ActivityDetectionService.ActivityType newActivity,
                                  ActivityDetectionService.ActivityType previousActivity) {

        long currentTime = System.currentTimeMillis();
        Log.i(TAG, "🚗 Activity changed: " + previousActivity + " -> " + newActivity);

        // Handle DRIVING activity specially for travel dua
        if (newActivity == ActivityDetectionService.ActivityType.DRIVING) {
            handleDrivingStarted(currentTime);
        } else if (isDriving) {
            // User was driving but now stopped
            handleDrivingStopped(currentTime);
        }

        // Handle non-driving activities with general dua logic
        if (newActivity != ActivityDetectionService.ActivityType.DRIVING) {
            handleNonDrivingActivity(newActivity, currentTime);
        }
    }

    /**
     * Handle when user starts driving
     */
    private void handleDrivingStarted(long currentTime) {
        Log.i(TAG, "🚗 Driving detected");

        // Check if this is a NEW trip (stopped for 5+ minutes)
        boolean isNewTrip = false;
        if (!isDriving) {
            // Was not driving before
            if (lastDrivingEndTime == 0) {
                // First time driving ever
                isNewTrip = true;
                Log.i(TAG, "🆕 First driving session - new trip");
            } else {
                long stoppedDuration = currentTime - lastDrivingEndTime;
                if (stoppedDuration >= NEW_TRIP_THRESHOLD) {
                    // Stopped for 5+ minutes - this is a new trip
                    isNewTrip = true;
                    Log.i(TAG, "🆕 Stopped for " + (stoppedDuration / 1000) + "s (>= 5 min) - NEW TRIP");
                } else {
                    // Stopped for less than 5 minutes - same trip (traffic stop)
                    Log.i(TAG, "🚦 Stopped for " + (stoppedDuration / 1000) + "s (< 5 min) - SAME TRIP (traffic)");
                }
            }
        }

        isDriving = true;

        if (isNewTrip) {
            // Reset trip state
            travelDuaPlayedForTrip = false;
            drivingStartTime = currentTime;

            // Cancel any pending timer
            cancelTravelDuaTimer();

            // Start 1-minute timer for continuous driving
            Log.i(TAG, "⏱️ Starting 1-minute continuous driving timer");
            startTravelDuaTimer();
        } else if (!travelDuaPlayedForTrip && drivingStartTime == 0) {
            // Resuming from traffic stop, but dua not played yet
            // Continue the timer from where we left off (or restart if needed)
            drivingStartTime = currentTime;
            Log.i(TAG, "⏱️ Resuming driving - restarting timer");
            startTravelDuaTimer();
        }
    }

    /**
     * Handle when user stops driving (traffic, arrived, etc.)
     */
    private void handleDrivingStopped(long currentTime) {
        Log.i(TAG, "🛑 Driving stopped");
        isDriving = false;
        lastDrivingEndTime = currentTime;

        // Cancel the 1-minute timer since driving stopped
        cancelTravelDuaTimer();

        long drivingDuration = currentTime - drivingStartTime;
        Log.i(TAG, "📊 Drove for " + (drivingDuration / 1000) + " seconds");

        // Reset driving start time (will be set again when driving resumes)
        drivingStartTime = 0;
    }

    /**
     * Start the 1-minute timer for continuous driving
     */
    private void startTravelDuaTimer() {
        travelDuaRunnable = new Runnable() {
            @Override
            public void run() {
                // Check if still driving
                if (isDriving && !travelDuaPlayedForTrip) {
                    long drivingDuration = System.currentTimeMillis() - drivingStartTime;
                    Log.i(TAG, "⏱️ Timer fired! Drove continuously for " + (drivingDuration / 1000) + " seconds");

                    if (drivingDuration >= CONTINUOUS_DRIVING_THRESHOLD) {
                        // 1 minute of continuous driving achieved!
                        Log.i(TAG, "✅ 1 minute of continuous driving - playing travel dua!");
                        playTravelDua();
                        travelDuaPlayedForTrip = true;
                    }
                } else {
                    Log.i(TAG, "⏱️ Timer fired but not driving or already played");
                }
            }
        };

        // Schedule for 1 minute from now
        handler.postDelayed(travelDuaRunnable, CONTINUOUS_DRIVING_THRESHOLD);
    }

    /**
     * Cancel the pending travel dua timer
     */
    private void cancelTravelDuaTimer() {
        if (travelDuaRunnable != null) {
            handler.removeCallbacks(travelDuaRunnable);
            travelDuaRunnable = null;
            Log.i(TAG, "⏱️ Cancelled travel dua timer");
        }
    }

    /**
     * Play the travel dua for driving
     */
    private void playTravelDua() {
        DuaInfo duaInfo = duaMap.get(ActivityDetectionService.ActivityType.DRIVING);
        if (duaInfo != null) {
            Log.i(TAG, "🕌 TRAVEL DUA: " + duaInfo.text);
            Log.i(TAG, "📖 Meaning: " + duaInfo.meaning);
            logDuaInfo(duaInfo);
            lastPlayTime = System.currentTimeMillis();
            lastPlayedActivity = ActivityDetectionService.ActivityType.DRIVING;

            // TODO: Play audio when available
            // playAudioDua(duaInfo.audioResource);
        }
    }

    /**
     * Handle non-driving activities (walking, running, stationary, etc.)
     */
    private void handleNonDrivingActivity(ActivityDetectionService.ActivityType newActivity, long currentTime) {
        // Skip DRIVING - handled separately
        if (newActivity == ActivityDetectionService.ActivityType.DRIVING) {
            return;
        }

        // For other activities, use original logic with 5-minute interval
        if (currentTime - lastPlayTime > MIN_PLAY_INTERVAL ||
                lastPlayedActivity != newActivity) {

            // Don't spam duas for stationary/on_phone (common states)
            if (newActivity == ActivityDetectionService.ActivityType.STATIONARY ||
                    newActivity == ActivityDetectionService.ActivityType.ON_PHONE) {
                // Only play if activity changed AND enough time passed
                if (lastPlayedActivity != newActivity && currentTime - lastPlayTime > MIN_PLAY_INTERVAL) {
                    playDuaForActivity(newActivity);
                    lastPlayedActivity = newActivity;
                    lastPlayTime = currentTime;
                }
            } else {
                // For walking, running - play when activity changes
                playDuaForActivity(newActivity);
                lastPlayedActivity = newActivity;
                lastPlayTime = currentTime;
            }
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
        cancelTravelDuaTimer();
        releaseMediaPlayer();

        // Reset trip state
        isDriving = false;
        travelDuaPlayedForTrip = false;
        drivingStartTime = 0;
        lastDrivingEndTime = 0;
    }
}
