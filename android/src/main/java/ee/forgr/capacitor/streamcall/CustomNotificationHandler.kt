package ee.forgr.capacitor.streamcall
 
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import io.getstream.video.android.core.RingingState
import io.getstream.video.android.core.notifications.DefaultNotificationHandler
import io.getstream.video.android.core.notifications.NotificationHandler
import io.getstream.video.android.model.StreamCallId
import io.getstream.video.android.model.streamCallId

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
        Log.d("CustomNotificationHandler", "getRingingCallNotification called: ringingState=$ringingState, callId=$callId, callDisplayName=$callDisplayName, shouldHaveContentIntent=$shouldHaveContentIntent")
        return if (ringingState is RingingState.Incoming) {
            // Note: we create our own fullScreenPendingIntent later based on acceptCallPendingIntent

            // Get the main launch intent for the application
            val launchIntent = application.packageManager.getLaunchIntentForPackage(application.packageName)
            var targetComponent: android.content.ComponentName? = null
            if (launchIntent != null) {
                targetComponent = launchIntent.component
                Log.d("CustomNotificationHandler", "Derived launch component: ${targetComponent?.flattenToString()}")
            } else {
                Log.e("CustomNotificationHandler", "Could not get launch intent for package: ${application.packageName}. This is problematic for creating explicit intents.")
            }

            // Intent to simply bring the app to foreground and show incoming-call UI (no auto accept)
            val incomingIntentAction = "io.getstream.video.android.action.INCOMING_CALL"
            val incomingCallIntent = Intent(incomingIntentAction)
                .putExtra(NotificationHandler.INTENT_EXTRA_CALL_CID, callId)
                .setPackage(application.packageName)
            if (targetComponent != null) incomingCallIntent.component = targetComponent
            incomingCallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

            // Use the app's MainActivity intent so webview loads; user sees app UI
            val requestCodeFull = callId.cid.hashCode()
            val fullScreenPendingIntent = PendingIntent.getActivity(
                application,
                requestCodeFull,
                incomingCallIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val acceptCallAction = NotificationHandler.ACTION_ACCEPT_CALL
            val acceptCallIntent = Intent(acceptCallAction)
                // Pass full Parcelable so both new and old handlers succeed
                .putExtra(NotificationHandler.INTENT_EXTRA_CALL_CID, callId)
                .setPackage(application.packageName)

            if (targetComponent != null) {
                acceptCallIntent.component = targetComponent
            }
            acceptCallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

            Log.d("CustomNotificationHandler", "Constructed Accept Call Intent for PI: action=${acceptCallIntent.action}, cid=${acceptCallIntent.streamCallId(NotificationHandler.INTENT_EXTRA_CALL_CID)}, package=${acceptCallIntent.getPackage()}, component=${acceptCallIntent.component?.flattenToString()}, flags=${acceptCallIntent.flags}")

            // Create PendingIntent for Accept action using getActivity to launch the app
            val requestCodeAccept = callId.cid.hashCode() + 1 // Unique request code for the PendingIntent with offset to avoid collisions
            val acceptCallPendingIntent = PendingIntent.getActivity(
                application,
                requestCodeAccept,
                acceptCallIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            Log.d("CustomNotificationHandler", "Created Accept Call PendingIntent with requestCode: $requestCodeAccept")

            val rejectCallPendingIntent = intentResolver.searchRejectCallPendingIntent(callId) // Keep using resolver for reject for now, or change it too if needed

            Log.d("CustomNotificationHandler", "Full Screen PI: $fullScreenPendingIntent")
            Log.d("CustomNotificationHandler", "Custom Accept Call PI: $acceptCallPendingIntent")
            Log.d("CustomNotificationHandler", "Resolver Reject Call PI: $rejectCallPendingIntent")
            
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
        Log.d("CustomNotificationHandler", "customGetIncomingCallNotification called: callerName=$callerName, callId=$callId")
        customCreateIncomingCallChannel()
        // Always use the provided acceptCallPendingIntent (created with getActivity) so that
        // the app process is started and MainActivity receives the ACCEPT_CALL action even
        // when the app has been killed.
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
 
    private fun buildNotification(
        fullScreenPendingIntent: PendingIntent,
        acceptCallPendingIntent: PendingIntent,
        rejectCallPendingIntent: PendingIntent,
        callerName: String?,
        shouldHaveContentIntent: Boolean,
        channelId: String,
        includeSound: Boolean
    ): Notification {
        Log.d("CustomNotificationHandler", "buildNotification called: callerName=$callerName, channelId=$channelId, includeSound=$includeSound")
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
        Log.d("CustomNotificationHandler", "onMissedCall called: callId=$callId, callDisplayName=$callDisplayName")
        endCall(callId)
        super.onMissedCall(callId, callDisplayName)
    }
 
    private fun customCreateIncomingCallChannel() {
        Log.d("CustomNotificationHandler", "customCreateIncomingCallChannel called")
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
        Log.d("CustomNotificationHandler", "clone called")
        return CustomNotificationHandler(this.application, this.endCall, this.incomingCall)
    }
}
 