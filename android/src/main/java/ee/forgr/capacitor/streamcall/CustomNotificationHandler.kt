package ee.forgr.capacitor.streamcall

import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.getstream.video.android.core.notifications.DefaultNotificationHandler
import io.getstream.video.android.core.notifications.NotificationHandler

class CustomNotificationHandler(val application: Application) : DefaultNotificationHandler(application, hideRingingNotificationInForeground = false) {
    override fun getIncomingCallNotification(
        fullScreenPendingIntent: PendingIntent,
        acceptCallPendingIntent: PendingIntent,
        rejectCallPendingIntent: PendingIntent,
        callerName: String?,
        shouldHaveContentIntent: Boolean,
    ): Notification {
        // if the app is in foreground then don't interrupt the user with a high priority
        // notification (popup). The application will display an incoming ringing call
        // screen instead - but this needs to be handled by the application.
        // The default behaviour is that all notification are high priority
        val showAsHighPriority = true //!hideRingingNotificationInForeground || !isInForeground()
        val channelId = "incoming_calls" // also hardcoded

        createIncomingCallChannel(channelId, showAsHighPriority)

        return getNotification {
            priority = NotificationCompat.PRIORITY_HIGH
            setContentTitle(callerName)
            setContentText("Incoming call") // Using a hardcoded string temporarily
            setChannelId(channelId)
            setOngoing(true)
            setCategory(NotificationCompat.CATEGORY_CALL)
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
                setAutoCancel(false)
            }
            addCallActions(acceptCallPendingIntent, rejectCallPendingIntent, callerName)
        }
    }
}