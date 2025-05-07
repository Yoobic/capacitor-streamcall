package ee.forgr.capacitor.streamcall
 
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import io.getstream.log.taggedLogger
import io.getstream.video.android.core.RingingState
import io.getstream.video.android.core.notifications.DefaultNotificationHandler
import io.getstream.video.android.core.notifications.NotificationHandler
import io.getstream.video.android.model.StreamCallId
 
// declare "incoming_calls_custom" as a constant
const val INCOMING_CALLS_CUSTOM = "incoming_calls_custom"
 
class CustomNotificationHandler(
    val application: Application,
    private val endCall: (callId: StreamCallId) -> Unit = {},
    private val incomingCall: () -> Unit = {}
) : DefaultNotificationHandler(application, hideRingingNotificationInForeground = false) {
    companion object {
        private const val PREFS_NAME = "StreamCallPrefs"
        private const val KEY_NOTIFICATION_TIME = "notification_creation_time"
    }
    private var allowSound = true;
 
    override fun getRingingCallNotification(
        ringingState: RingingState,
        callId: StreamCallId,
        callDisplayName: String?,
        shouldHaveContentIntent: Boolean,
    ): Notification? {
        return if (ringingState is RingingState.Incoming) {
            val fullScreenPendingIntent = intentResolver.searchIncomingCallPendingIntent(callId)
            val acceptCallPendingIntent = intentResolver.searchAcceptCallPendingIntent(callId)
            val rejectCallPendingIntent = intentResolver.searchRejectCallPendingIntent(callId)
 
            if (fullScreenPendingIntent != null && acceptCallPendingIntent != null && rejectCallPendingIntent != null) {
                customGetIncomingCallNotification(
                    fullScreenPendingIntent,
                    acceptCallPendingIntent,
                    rejectCallPendingIntent,
                    callDisplayName,
                    shouldHaveContentIntent,
                    callId
                )
            } else {
                Log.e("CustomNotificationHandler", "Ringing call notification not shown, one of the intents is null.")
                null
            }
        } else if (ringingState is RingingState.Outgoing) {
            val outgoingCallPendingIntent = intentResolver.searchOutgoingCallPendingIntent(callId)
            val endCallPendingIntent = intentResolver.searchEndCallPendingIntent(callId)
 
            if (outgoingCallPendingIntent != null && endCallPendingIntent != null) {
                getOngoingCallNotification(
                    callId,
                    callDisplayName,
                    isOutgoingCall = true,
                )
            } else {
                Log.e("CustomNotificationHandler", "Ringing call notification not shown, one of the intents is null.")
                null
            }
        } else {
            null
        }
    }
 
    fun customGetIncomingCallNotification(
        fullScreenPendingIntent: PendingIntent,
        acceptCallPendingIntent: PendingIntent,
        rejectCallPendingIntent: PendingIntent,
        callerName: String?,
        shouldHaveContentIntent: Boolean,
        callId: StreamCallId
    ): Notification {
 
        // customCreateIncomingCallChannel()
        val manufacturer = Build.MANUFACTURER.lowercase()
        if (manufacturer.contains("xiaomi") || manufacturer.contains("mi")) {
            // val serviceIntent = Intent(application, CallForegroundService::class.java)
            // serviceIntent.action = CallForegroundService.ACTION_START_FOREGROUND_SERVICE
            // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //     application.startForegroundService(serviceIntent)
            // } else {
            //     application.startService(serviceIntent)
            // }
            // Adjust PendingIntent for Xiaomi to avoid permission denial
            val xiaomiAcceptIntent = PendingIntent.getActivity(
                application,
                0,
                launchIntent ?: Intent("io.getstream.video.android.action.ACCEPT_CALL").setPackage(application.packageName).putExtra(NotificationHandler.INTENT_EXTRA_CALL_CID, callId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return buildNotification(
                fullScreenPendingIntent,
                xiaomiAcceptIntent,
                rejectCallPendingIntent,
                callerName,
                shouldHaveContentIntent,
                INCOMING_CALLS_CUSTOM,
                true // Include sound
            )
        }
 
        return buildNotification(
            fullScreenPendingIntent,
            acceptCallPendingIntent,
            rejectCallPendingIntent,
            callerName,
            shouldHaveContentIntent,
            INCOMING_CALLS_CUSTOM,
            true // Include sound
        )
    }
 
    fun buildNotification(
        fullScreenPendingIntent: PendingIntent,
        acceptCallPendingIntent: PendingIntent,
        rejectCallPendingIntent: PendingIntent,
        callerName: String?,
        shouldHaveContentIntent: Boolean,
        channelId: String,
        includeSound: Boolean
    ): Notification {
        return getNotification {
            priority = NotificationCompat.PRIORITY_HIGH
            setContentTitle(callerName)
            setContentText("Incoming call")
            setChannelId(channelId)
            setOngoing(true)
            setAutoCancel(false)
            setCategory(NotificationCompat.CATEGORY_CALL)
 
            // Clear all defaults first
            setDefaults(0)
 
            if (includeSound && allowSound) {
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
            } else {
                setSound(null)
                setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
            }
 
            // setVibrate(longArrayOf(0, 1000, 500, 1000))
            setLights(0xFF0000FF.toInt(), 1000, 1000)
            setFullScreenIntent(fullScreenPendingIntent, true)
            if (shouldHaveContentIntent) {
                setContentIntent(fullScreenPendingIntent)
            } else {
                val emptyIntent = PendingIntent.getActivity(
                    application,
                    0,
                    Intent(),
                    PendingIntent.FLAG_IMMUTABLE,
                )
                setContentIntent(emptyIntent)
            }
            addCallActions(acceptCallPendingIntent, rejectCallPendingIntent, callerName)
        }.apply {
            // flags = flags or NotificationCompat.FLAG_ONGOING_EVENT
        }
    }
 
    override fun onMissedCall(callId: StreamCallId, callDisplayName: String) {
        endCall(callId)
        super.onMissedCall(callId, callDisplayName)
    }
 
    private fun customCreateIncomingCallChannel() {
        maybeCreateChannel(
            channelId = INCOMING_CALLS_CUSTOM,
            context = application,
            configure = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    name = application.getString(
                        R.string.stream_video_incoming_call_notification_channel_title,
                    )
                    description = application.getString(R.string.stream_video_incoming_call_notification_channel_description)
                    importance = NotificationManager.IMPORTANCE_HIGH
                    this.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    this.setShowBadge(true)
 
                    // Set the channel to be silent since we handle sound via RingtonePlayer
                    setSound(null, null)
                    enableVibration(true)
                    enableLights(true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    this.setAllowBubbles(true)
                }
            },
        )
    }
 
    public fun clone(): CustomNotificationHandler {
        return CustomNotificationHandler(this.application, this.endCall, this.incomingCall)
    }
}
 