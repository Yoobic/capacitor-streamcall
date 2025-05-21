package ee.forgr.capacitor.streamcall;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*
import android.widget.LinearLayout.LayoutParams
import android.widget.FrameLayout
import io.getstream.video.android.core.Call;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.launch;

class IncomingCallView(context: Context) : FrameLayout(context) {
    private val callerName: TextView = TextView(context)
    private val acceptButton: Button = Button(context)
    private val rejectButton: Button = Button(context)
    private var incomingCall: Call? = null

    init {
        setBackgroundColor(Color.BLACK)

        // Style components
        callerName.apply {
            textSize = 24f
            setTextColor(Color.WHITE)
            text = "Incoming Call"
            gravity = Gravity.CENTER
        }

        acceptButton.text = "Accept"
        rejectButton.text = "Reject"

        // Button listeners
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

        // Layout hierarchy
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(acceptButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = 32 })
            addView(rejectButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(callerName, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = 48 })
            addView(buttonRow, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }

        addView(container, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setIncomingCall(call: Call) {
        this.incomingCall = call
        callerName.text = "Incoming Call" // You can customize to show caller info
        visibility = VISIBLE
    }
}
