package ee.forgr.capacitor.streamcall

import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.isVisible
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import io.getstream.video.android.core.GEO
import io.getstream.video.android.core.StreamVideo
import io.getstream.video.android.core.StreamVideoBuilder
import io.getstream.video.android.core.notifications.NotificationConfig
import io.getstream.video.android.model.User
import io.getstream.android.push.firebase.FirebasePushDeviceGenerator
import io.getstream.log.Priority
import io.getstream.video.android.core.Call
import io.getstream.video.android.core.logging.LoggingLevel
import io.getstream.video.android.core.model.RejectReason
import io.getstream.video.android.core.notifications.NotificationHandler
import io.getstream.video.android.model.streamCallId
import kotlinx.coroutines.launch

@CapacitorPlugin(name = "StreamCall")
public class StreamCallPlugin : Plugin() {
    private val implementation = StreamCall()
    private var composeView: ComposeView? = null
    private var streamVideoClient: StreamVideo? = null
    private var state: State = State.NOT_INITIALIZED
    private var overlayView: ComposeView? = null
    private var incomingCallView: ComposeView? = null

    private enum class State {
        NOT_INITIALIZED,
        INITIALIZING,
        INITIALIZED
    }

    override fun load() {
        // general init
        initializeStreamVideo()
        setupViews()
        super.load()

        // Check the launch intent
        val activity = activity
        val intent = activity?.intent

        if (intent != null) {
            val action = intent.action
            val data = intent.data
            val extras = intent.extras

            if (action === "io.getstream.video.android.action.INCOMING_CALL") {
                activity.runOnUiThread {
                    val cid = intent.streamCallId(NotificationHandler.INTENT_EXTRA_CALL_CID)
                    if (cid != null) {
                        val call = streamVideoClient?.call(id = cid.id, type = cid.type)
                        // Launch a coroutine to handle the suspend function
                        kotlinx.coroutines.GlobalScope.launch {
                            call?.get()
                            activity.runOnUiThread {
                                incomingCallView?.setContent {
                                    IncomingCallView(
                                        streamVideo = streamVideoClient,
                                        call = call,
                                        onDeclineCall = { declinedCall ->
                                            declineCall(declinedCall)
                                        },
                                        onAcceptCall = { acceptedCall ->
                                            acceptCall(acceptedCall)
                                        }
                                    )
                                }
                                incomingCallView?.isVisible = true
                            }
                        }
                    }
                }
            }
            // Log the intent information
            android.util.Log.d("StreamCallPlugin", "Launch Intent - Action: $action")
            android.util.Log.d("StreamCallPlugin", "Launch Intent - Data: $data")
            android.util.Log.d("StreamCallPlugin", "Launch Intent - Extras: $extras")
        }
    }

    private fun declineCall(call: Call) {
        kotlinx.coroutines.GlobalScope.launch {
            call.reject(RejectReason.Decline)
            
            // Notify that call has ended
            notifyListeners("callEnded", JSObject())
            
            activity?.runOnUiThread {
                incomingCallView?.isVisible = false
                // Check if device is locked using KeyguardManager
                val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (keyguardManager.isKeyguardLocked) {
                    activity.moveTaskToBack(true)
                }
            }
        }
    }

    private fun setupViews() {
        val context = context
        val parent = bridge?.webView?.parent as? ViewGroup ?: return

        // Make WebView transparent
        bridge?.webView?.setBackgroundColor(Color.TRANSPARENT)

        // Create and add overlay view below WebView
        overlayView = ComposeView(context).apply {
            isVisible = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setContent {
                CallOverlayView(
                    context = context,
                    streamVideo = streamVideoClient,
                    call = null
                )
            }
        }
        parent.addView(overlayView, 0)  // Add at index 0 to ensure it's below WebView

        // Create and add incoming call view
        incomingCallView = ComposeView(context).apply {
            isVisible = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setContent {
                IncomingCallView(streamVideoClient)
            }
        }
        parent.addView(incomingCallView, parent.indexOfChild(bridge?.webView) + 1)  // Add above WebView
    }

    @PluginMethod
    fun login(call: PluginCall) {
        val token = call.getString("token")
        val userId = call.getString("userId")
        val name = call.getString("name")

        if (token == null || userId == null || name == null) {
            call.reject("Missing required parameters: token, userId, or name")
            return
        }

        val imageURL = call.getString("imageURL")

        try {
            // Create user object
            val user = User(
                id = userId,
                name = name,
                image = imageURL,
                custom = emptyMap() // Initialize with empty map for custom data
            )

            // Create credentials and save them
            val credentials = UserCredentials(user, token)
            SecureUserRepository.getInstance(context).save(credentials)

            // Initialize Stream Video with new credentials
            initializeStreamVideo()

            val ret = JSObject()
            ret.put("success", true)
            call.resolve(ret)
        } catch (e: Exception) {
            call.reject("Failed to login", e)
        }
    }

    @PluginMethod
    fun logout(call: PluginCall) {
        try {
            // Clear stored credentials
            SecureUserRepository.getInstance(context).removeCurrentUser()
            
            // Properly cleanup the client
            streamVideoClient?.let {
                StreamVideo.removeClient()
            }
            streamVideoClient = null
            state = State.NOT_INITIALIZED

            val ret = JSObject()
            ret.put("success", true)
            call.resolve(ret)
        } catch (e: Exception) {
            call.reject("Failed to logout", e)
        }
    }

    public fun initializeStreamVideo(passedContext: Context? = null) {
        if (state == State.INITIALIZING) {
            return
        }
        state = State.INITIALIZING

        val contextToUse = passedContext ?: this.context

        // Try to get user credentials from repository
        val savedCredentials = SecureUserRepository.getInstance(contextToUse).loadCurrentUser()
        if (savedCredentials == null) {
            state = State.NOT_INITIALIZED
            return
        }

        try {
            // Cleanup existing client if any
            streamVideoClient?.let {
                StreamVideo.removeClient()
                streamVideoClient = null
            }

            if (StreamVideo.isInstalled) {
                StreamVideo.removeClient()
            }



            // unsafe cast, add better handling
            val application = contextToUse.applicationContext as Application

            val notificationConfig = NotificationConfig(
                pushDeviceGenerators = listOf(FirebasePushDeviceGenerator(
                    providerName = "firebase",
                    context = contextToUse
                )),
                requestPermissionOnAppLaunch = { true },
                notificationHandler = CustomNotificationHandler(application)
            )

            // Initialize StreamVideo client
            streamVideoClient = StreamVideoBuilder(
                context = contextToUse,
                apiKey = "n8wv8vjmucdw",
                geo = GEO.GlobalEdgeNetwork,
                user = savedCredentials.user,
                token = savedCredentials.tokenValue,
                notificationConfig = notificationConfig,
                loggingLevel = LoggingLevel(priority = Priority.VERBOSE),

            ).build()

            state = State.INITIALIZED
        } catch (e: Exception) {
            state = State.NOT_INITIALIZED
            throw e
        }
    }

    @PluginMethod
    fun initialize(call: PluginCall) {
        call.resolve()
        return
//        try {
//            initializeStreamVideo()
//
//            if (state != State.INITIALIZED) {
//                call.reject("The SDK is not initialized")
//                return
//            }
//
//            getBridge().executeOnMainThread {
//                // Get the main activity
//                val activity = activity as ComponentActivity
//                val webViewParent = getBridge().webView.parent as ViewGroup
//
//                // Create ComposeView
//                composeView = ComposeView(activity)
//
//                // Set content
//                composeView!!.setContent {
//                    CallOverlayView(context, streamVideoClient)
//                }
//
//                // Create layout params
//                val params = FrameLayout.LayoutParams(
//                    ViewGroup.LayoutParams.MATCH_PARENT,
//                    ViewGroup.LayoutParams.MATCH_PARENT
//                )
//
//                // Add ComposeView below WebView
//                webViewParent.addView(composeView, 0, params)
//
//                // Make WebView background transparent
//                getBridge().webView.setBackgroundColor(Color.TRANSPARENT)
//
//                val ret = JSObject()
//                ret.put("success", true)
//                call.resolve(ret)
//            }
//        } catch (e: Exception) {
//            call.reject("Failed to initialize", e)
//        }
    }

    private fun acceptCall(call: Call) {
        kotlinx.coroutines.GlobalScope.launch {
            try {
                // Hide incoming call view first
                activity?.runOnUiThread {
                    incomingCallView?.isVisible = false
                }

                // Accept the call
                call.accept()

                // Notify that call has started
                val data = JSObject().apply {
                    put("callId", call.id)
                }
                notifyListeners("callStarted", data)

                // Show overlay view with the active call
                activity?.runOnUiThread {
                    overlayView?.setContent {
                        CallOverlayView(
                            context = context,
                            streamVideo = streamVideoClient,
                            call = call
                        )
                    }
                    overlayView?.isVisible = true
                    // No need to bring to front as it should stay below WebView
                }
            } catch (e: Exception) {
                android.util.Log.e("StreamCallPlugin", "Error accepting call", e)
                activity?.runOnUiThread {
                    android.widget.Toast.makeText(
                        context,
                        "Failed to join call: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    @PluginMethod
    fun setMicrophoneEnabled(call: PluginCall) {
        val enabled = call.getBoolean("enabled") ?: run {
            call.reject("Missing required parameter: enabled")
            return
        }

        try {
            val activeCall = streamVideoClient?.state?.activeCall
            if (activeCall == null) {
                call.reject("No active call")
                return
            }

            kotlinx.coroutines.GlobalScope.launch {
                try {
                    activeCall.value?.microphone?.setEnabled(enabled)
                    call.resolve(JSObject().apply {
                        put("success", true)
                    })
                } catch (e: Exception) {
                    android.util.Log.e("StreamCallPlugin", "Error setting microphone: ${e.message}")
                    call.reject("Failed to set microphone: ${e.message}")
                }
            }
        } catch (e: Exception) {
            call.reject("StreamVideo not initialized")
        }
    }

    @PluginMethod
    fun setCameraEnabled(call: PluginCall) {
        val enabled = call.getBoolean("enabled") ?: run {
            call.reject("Missing required parameter: enabled")
            return
        }

        try {
            val activeCall = streamVideoClient?.state?.activeCall
            if (activeCall == null) {
                call.reject("No active call")
                return
            }

            kotlinx.coroutines.GlobalScope.launch {
                try {
                    activeCall.value?.camera?.setEnabled(enabled)
                    call.resolve(JSObject().apply {
                        put("success", true)
                    })
                } catch (e: Exception) {
                    android.util.Log.e("StreamCallPlugin", "Error setting camera: ${e.message}")
                    call.reject("Failed to set camera: ${e.message}")
                }
            }
        } catch (e: Exception) {
            call.reject("StreamVideo not initialized")
        }
    }

    @PluginMethod
    fun endCall(call: PluginCall) {
        try {
            val activeCall = streamVideoClient?.state?.activeCall
            if (activeCall == null) {
                call.reject("No active call to end")
                return
            }

            kotlinx.coroutines.GlobalScope.launch {
                try {
                    activeCall.value?.leave()
                    
                    activity?.runOnUiThread {
                        overlayView?.isVisible = false
                    }
                    
                    // Notify that call has ended
                    notifyListeners("callEnded", JSObject())
                    
                    call.resolve(JSObject().apply {
                        put("success", true)
                    })
                } catch (e: Exception) {
                    android.util.Log.e("StreamCallPlugin", "Error ending call: ${e.message}")
                    call.reject("Failed to end call: ${e.message}")
                }
            }
        } catch (e: Exception) {
            call.reject("StreamVideo not initialized")
        }
    }
}
