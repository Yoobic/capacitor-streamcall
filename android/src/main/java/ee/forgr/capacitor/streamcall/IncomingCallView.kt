package ee.forgr.capacitor.streamcall;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import io.getstream.video.android.core.Call;
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class IncomingCallView(context: Context) : View(context) {
    private var callerName: TextView
    private var acceptButton: Button
    private var rejectButton: Button
    private var incomingCall: Call? = null

    init {
        // Initialize UI components immediately
        callerName = TextView(context)
        acceptButton = Button(context)
        rejectButton = Button(context)

        acceptButton.text = "Accept"
        rejectButton.text = "Reject"

        acceptButton.setOnClickListener {
            incomingCall?.let { call ->
                CoroutineScope(Dispatchers.Main).launch {
                    call.join()
                    visibility = GONE
                }
            }
        }

        rejectButton.setOnClickListener {
            incomingCall?.let { call ->
                CoroutineScope(Dispatchers.Main).launch {
                    call.leave()
                    visibility = GONE
                }
            }
        }

        // Placeholder for layout setup, adjust based on your actual layout
    }

    fun setIncomingCall(call: Call) {
        this.incomingCall = call
        // Update UI with call details
        // For example, set caller name from call.state or metadata
        callerName.text = "Incoming Call" // Placeholder, update with actual data
        visibility = VISIBLE
    }
}
