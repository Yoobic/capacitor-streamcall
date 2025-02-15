package ee.forgr.capacitor.streamcall

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.util.Log

class RingtonePlayer(private val application: Application) {
    companion object {
        private const val PREFS_NAME = "StreamCallPrefs"
        private const val KEY_NOTIFICATION_TIME = "notification_creation_time"
        private const val DEFAULT_RINGTONE_DURATION = 30000L // 30 seconds in milliseconds
    }

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stopRingtoneRunnable: Runnable? = null

    fun startRinging() {
        try {
            // Get notification creation time
            val notificationTime = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_NOTIFICATION_TIME, 0)

            Log.i("RingtonePlayer", "notificationTime: $notificationTime")

            if (mediaPlayer == null) {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(application, uri)
                    isLooping = true
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    prepare()
                }
            }
            
            if (notificationTime > 0) {
                val currentTime = System.currentTimeMillis()
                val elapsedTime = currentTime - notificationTime

                // Only start playing if we're within the ringtone duration
                if (elapsedTime < DEFAULT_RINGTONE_DURATION) {
                    // Get the ringtone duration
                    val ringtoneDuration = mediaPlayer?.duration?.toLong() ?: DEFAULT_RINGTONE_DURATION
                    
                    // Calculate the position to seek to
                    val seekPosition = (elapsedTime % ringtoneDuration).toInt()
                    Log.d("RingtonePlayer", "Seeking to position: $seekPosition ms in ringtone")
                    
                    mediaPlayer?.seekTo(seekPosition)
                    mediaPlayer?.start()

                    // Schedule stop at the remaining duration
                    val remainingDuration = DEFAULT_RINGTONE_DURATION - elapsedTime
                    stopRingtoneRunnable = Runnable { stopRinging() }
                    handler.postDelayed(stopRingtoneRunnable!!, remainingDuration)
                    
                    Log.d("RingtonePlayer", "Starting ringtone with offset: $elapsedTime ms, will play for $remainingDuration ms")
                } else {
                    Log.d("RingtonePlayer", "Not starting ringtone as elapsed time ($elapsedTime ms) exceeds duration")
                }
            } else {
                // If no notification time found, just play normally
                mediaPlayer?.start()
                
                // Schedule stop at the default duration
                stopRingtoneRunnable = Runnable { stopRinging() }
                handler.postDelayed(stopRingtoneRunnable!!, DEFAULT_RINGTONE_DURATION)
                
                Log.d("RingtonePlayer", "Starting ringtone with default duration")
            }
        } catch (e: Exception) {
            Log.e("RingtonePlayer", "Error playing ringtone: ${e.message}")
        }
    }

    fun stopRinging() {
        try {
            stopRingtoneRunnable?.let { handler.removeCallbacks(it) }
            stopRingtoneRunnable = null
            
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("RingtonePlayer", "Error stopping ringtone: ${e.message}")
        }
    }
} 