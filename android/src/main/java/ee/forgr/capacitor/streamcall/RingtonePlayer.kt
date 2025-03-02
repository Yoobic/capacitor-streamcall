package ee.forgr.capacitor.streamcall

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
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

    private fun isOurNotification(notification: android.service.notification.StatusBarNotification): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Check channel ID for Android O and above
            notification.notification.channelId == "incoming_calls_custom" &&
            notification.notification.category == android.app.Notification.CATEGORY_CALL &&
            notification.notification.actions?.size == 2
        } else {
            // For older devices, just check category and actions
            notification.notification.category == android.app.Notification.CATEGORY_CALL &&
            notification.notification.actions?.size == 2
        }
    }

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

            val notificationManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notifs = notificationManager.activeNotifications.toList()
            for (notification in notifs) {
                // First check if it's our notification
                val isOurs = isOurNotification(notification)
                Log.d("RingtonePlayer", """Notification details:
                    |Is Our Notification: $isOurs
                    |Tag: ${notification.tag}
                    |Key: ${notification.key}
                    |ID: ${notification.id}
                    |Channel ID: ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notification.notification.channelId else "N/A"}
                    |Group: ${notification.notification.group ?: "No group"}
                    |When: ${notification.notification.`when`}
                    |Post Time: ${notification.postTime}
                    |Creation Time: ${notification.notification.`when`}
                    |Package Name: ${notification.packageName}
                    |Title: ${notification.notification.extras.getString("android.title")}
                    |Text: ${notification.notification.extras.getString("android.text")}
                    |Sub Text: ${notification.notification.extras.getString("android.subText")}
                    |Info Text: ${notification.notification.extras.getString("android.infoText")}
                    |Category: ${notification.notification.category ?: "No category"}
                    |Priority: ${notification.notification.priority}
                    |Flags: ${notification.notification.flags}
                    |Flag Analysis: ${analyzeFlagsBinary(notification.notification.flags)}
                    |Is FLAG_NO_CLEAR Set: ${(notification.notification.flags and android.app.Notification.FLAG_NO_CLEAR) != 0}
                    |Is FLAG_ONGOING_EVENT Set: ${(notification.notification.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0}
                    |Is FLAG_FOREGROUND_SERVICE Set: ${(notification.notification.flags and android.app.Notification.FLAG_FOREGROUND_SERVICE) != 0}
                    |Number: ${notification.notification.number}
                    |Visibility: ${notification.notification.visibility}
                    |Sound URI: ${notification.notification.sound}
                    |Vibrate Pattern: ${notification.notification.vibrate?.contentToString() ?: "No vibration"}
                    |LED Color: ${notification.notification.ledARGB}
                    |LED On MS: ${notification.notification.ledOnMS}
                    |LED Off MS: ${notification.notification.ledOffMS}
                    |Defaults: ${notification.notification.defaults}
                    |Actions: ${notification.notification.actions?.size ?: 0} actions
                    |Ongoing: ${notification.isOngoing}
                    |Clearable: ${notification.isClearable}
                    |Group Key: ${notification.groupKey ?: "No group key"}
                    |Overflow: ${notification.isGroup}
                    |User ID: ${notification.userId}
                    |Badge Icon Type: ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notification.notification.badgeIconType else "N/A"}
                    |Settings Text: ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notification.notification.settingsText else "N/A"}
                    |Shortcut ID: ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notification.notification.shortcutId else "N/A"}
                    |Timeout After: ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notification.notification.timeoutAfter else "N/A"}
                    |All Extras: ${notification.notification.extras}
                    """.trimMargin())

                // Only proceed with ringtone if it's our notification
                if (!isOurs) {
                    Log.d("RingtonePlayer", "Skipping notification as it's not our incoming call notification")
                    continue
                }

                // Cancel our notification
                try {
                    Log.d("RingtonePlayer", "Cancelling our notification with ID: ${notification.id}")
                    // notificationManager.cancel(notification.id)

                } catch (e: Exception) {
                    Log.e("RingtonePlayer", "Error cancelling notification: ${e.message}")
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

    // Add helper function to analyze flags
    private fun analyzeFlagsBinary(flags: Int): String {
        val flagsList = mutableListOf<String>()
        if ((flags and android.app.Notification.FLAG_AUTO_CANCEL) != 0) flagsList.add("FLAG_AUTO_CANCEL")
        if ((flags and android.app.Notification.FLAG_NO_CLEAR) != 0) flagsList.add("FLAG_NO_CLEAR")
        if ((flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0) flagsList.add("FLAG_ONGOING_EVENT")
        if ((flags and android.app.Notification.FLAG_INSISTENT) != 0) flagsList.add("FLAG_INSISTENT")
        if ((flags and android.app.Notification.FLAG_ONLY_ALERT_ONCE) != 0) flagsList.add("FLAG_ONLY_ALERT_ONCE")
        if ((flags and android.app.Notification.FLAG_FOREGROUND_SERVICE) != 0) flagsList.add("FLAG_FOREGROUND_SERVICE")
        return flagsList.joinToString(", ")
    }
} 