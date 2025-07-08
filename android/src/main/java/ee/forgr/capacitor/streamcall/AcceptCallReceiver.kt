package ee.forgr.capacitor.streamcall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.getstream.video.android.core.notifications.NotificationHandler
import io.getstream.video.android.model.streamCallId

class AcceptCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("AcceptCallReceiver", "onReceive called with action: ${intent?.action}")
        if (intent?.action == NotificationHandler.ACTION_ACCEPT_CALL) {
            val cid = intent.streamCallId(NotificationHandler.INTENT_EXTRA_CALL_CID)
            if (cid == null) {
                Log.e("AcceptCallReceiver", "Call CID is null, cannot accept call.")
                return
            }

            Log.d("AcceptCallReceiver", "Accepting call with CID: $cid")

            // Create an intent to launch the main activity
            val launchIntent = context?.packageManager?.getLaunchIntentForPackage(context.packageName)
            if (launchIntent != null) {
                launchIntent.action = NotificationHandler.ACTION_ACCEPT_CALL
                launchIntent.putExtra(NotificationHandler.INTENT_EXTRA_CALL_CID, cid)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(launchIntent)
                Log.d("AcceptCallReceiver", "Started MainActivity to handle ACCEPT_CALL action.")
            } else {
                Log.e("AcceptCallReceiver", "Could not get launch intent for package.")
            }
        }
    }
} 
