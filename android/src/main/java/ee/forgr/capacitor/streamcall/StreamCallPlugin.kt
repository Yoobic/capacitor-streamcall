package ee.forgr.capacitor.streamcall

import TouchInterceptWrapper
import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.getcapacitor.BridgeActivity
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.google.firebase.messaging.FirebaseMessaging
import io.getstream.android.push.PushProvider
import io.getstream.android.push.firebase.FirebasePushDeviceGenerator
import io.getstream.android.push.permissions.ActivityLifecycleCallbacks
import io.getstream.android.video.generated.models.CallAcceptedEvent
import io.getstream.android.video.generated.models.CallCreatedEvent
import io.getstream.android.video.generated.models.CallEndedEvent
import io.getstream.android.video.generated.models.CallMissedEvent
import io.getstream.android.video.generated.models.CallRejectedEvent
import io.getstream.android.video.generated.models.CallRingEvent
import io.getstream.android.video.generated.models.CallSessionEndedEvent
import io.getstream.android.video.generated.models.CallSessionParticipantCountsUpdatedEvent
import io.getstream.android.video.generated.models.CallSessionParticipantLeftEvent
import io.getstream.android.video.generated.models.CallSessionStartedEvent
import io.getstream.android.video.generated.models.VideoEvent
import io.getstream.log.Priority
import io.getstream.video.android.compose.theme.VideoTheme
import io.getstream.video.android.compose.ui.components.call.activecall.CallContent
import io.getstream.video.android.compose.ui.components.call.renderer.FloatingParticipantVideo
import io.getstream.video.android.compose.ui.components.call.renderer.ParticipantVideo
import io.getstream.video.android.compose.ui.components.call.renderer.RegularVideoRendererStyle
import io.getstream.video.android.compose.ui.components.video.VideoScalingType
import io.getstream.video.android.core.Call
import io.getstream.video.android.core.CameraDirection
import io.getstream.video.android.core.GEO
import io.getstream.video.android.core.RealtimeConnection
import io.getstream.video.android.core.StreamVideo
import io.getstream.video.android.core.StreamVideoBuilder
import io.getstream.video.android.core.call.CallType
import io.getstream.video.android.core.events.ParticipantLeftEvent
import io.getstream.video.android.core.internal.InternalStreamVideoApi
import io.getstream.video.android.core.logging.LoggingLevel
import io.getstream.video.android.core.notifications.NotificationConfig
import io.getstream.video.android.core.notifications.NotificationHandler
import io.getstream.video.android.core.notifications.internal.service.CallServiceConfigRegistry
import io.getstream.video.android.core.notifications.internal.service.DefaultCallConfigurations
import io.getstream.video.android.core.sounds.RingingConfig
import io.getstream.video.android.core.sounds.toSounds
import io.getstream.video.android.model.Device
import io.getstream.video.android.model.StreamCallId
import io.getstream.video.android.model.User
import io.getstream.video.android.model.streamCallId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// I am not a religious pearson, but at this point, I am not sure even god himself would understand this code
// It's a spaghetti-like, tangled, unreadable mess and frankly, I am deeply sorry for the code crimes commited in the Android impl
@CapacitorPlugin(name = "StreamCall")
public class StreamCallPlugin : Plugin() {
    private var streamVideoClient: StreamVideo? = null
    private var state: State = State.NOT_INITIALIZED
    private var overlayView: ComposeView? = null
    private var barrierView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var savedContext: Context? = null
    private var bootedToHandleCall: Boolean = false
    private var initializationTime: Long = 0
    private var savedActivity: Activity? = null
    private var savedActivityPaused = false
    private var savedCallsToEndOnResume = mutableListOf<Call>()
    private val callStates: MutableMap<String, LocalCallState> = mutableMapOf()

    // Store current call info
    private var currentCallId: String = ""
    private var currentCallType: String = ""
    private var currentCallState: String = ""

    // Add a field for the fragment
    private var callFragment: StreamCallFragment? = null
    private var streamVideo: StreamVideo? = null
    private var touchInterceptWrapper: TouchInterceptWrapper? = null

    private enum class State {
        NOT_INITIALIZED,
        INITIALIZING,
        INITIALIZED
    }

    public fun incomingOnlyRingingConfig(): RingingConfig = object : RingingConfig {
        override val incomingCallSoundUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        override val outgoingCallSoundUri: Uri? = null
    }

    private fun runOnMainThread(action: () -> Unit) {
        mainHandler.post { action() }
    }

    override fun handleOnPause() {
        super.handleOnPause()
    }

    override fun handleOnResume() {
        super.handleOnResume()
    }

    override fun load() {
        // general init
        initializeStreamVideo()
        setupViews()
        super.load()
        checkPermissions()
        // Register broadcast receiver for ACCEPT_CALL action with high priority
        val filter = IntentFilter("io.getstream.video.android.action.ACCEPT_CALL")
        filter.priority = 999 // Set high priority to ensure it captures the intent
        androidx.core.content.ContextCompat.registerReceiver(activity, acceptCallReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        android.util.Log.d("StreamCallPlugin", "Registered broadcast receiver for ACCEPT_CALL action with high priority")

        // Start the background service to keep the app alive
        val serviceIntent = Intent(activity, StreamCallBackgroundService::class.java)
        activity.startService(serviceIntent)
        android.util.Log.d("StreamCallPlugin", "Started StreamCallBackgroundService to keep app alive")
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun handleOnNewIntent(intent: android.content.Intent) {
        android.util.Log.d("StreamCallPlugin", "handleOnNewIntent called: action=${intent.action}, data=${intent.data}, extras=${intent.extras}")
        super.handleOnNewIntent(intent)

        val action = intent.action
        val data = intent.data
        val extras = intent.extras
        android.util.Log.d("StreamCallPlugin", "handleOnNewIntent: Parsed action: $action")

        if (action === "io.getstream.video.android.action.INCOMING_CALL") {
            android.util.Log.d("StreamCallPlugin", "handleOnNewIntent: Matched INCOMING_CALL action")
            // We need to make sure the activity is visible on locked screen in such case
            changeActivityAsVisibleOnLockScreen(this@StreamCallPlugin.activity, true)
            activity?.runOnUiThread {
                val cid = intent.streamCallId(NotificationHandler.INTENT_EXTRA_CALL_CID)
                android.util.Log.d("StreamCallPlugin", "handleOnNewIntent: INCOMING_CALL - Extracted cid: $cid")
                if (cid != null) {
                    android.util.Log.d("StreamCallPlugin", "handleOnNewIntent: INCOMING_CALL - cid is not null, processing.")
                    val call = streamVideoClient?.call(id = cid.id, type = cid.type)
                    android.util.Log.d("StreamCallPlugin", "handleOnNewIntent: INCOMING_CALL - Got call object: ${call?.id}")

                    // Try to get caller information from the call
                    kotlinx.coroutines.GlobalScope.launch {
                        try {
                            val callInfo = call?.get()
                            val callerInfo = callInfo?.getOrNull()?.call?.createdBy
                            
                            val payload = com.getcapacitor.JSObject().apply {
                                put("cid", cid.cid)
                                put("type", "incoming")
                                if (callerInfo != null) {
                                    val caller = com.getcapacitor.JSObject().apply {
                                        put("userId", callerInfo.id)
                                        put("name", callerInfo.name ?: "")
                                        put("imageURL", callerInfo.image ?: "")
                                        put("role", callerInfo.role ?: "")
                                    }
                                    put("caller", caller)
                                }
                            }
                            
                            // Notify WebView/JS about incoming call so it can render its own UI
                            notifyListeners("incomingCall", payload, true)
                            
                            // Delay bringing app to foreground to allow the event to be processed first
                            kotlinx.coroutines.delay(500) // 500ms delay
                            bringAppToForeground()
                        } catch (e: Exception) {
                            android.util.Log.e("StreamCallPlugin", "Error getting call info for incoming call", e)
                            // Fallback to basic payload without caller info
                            val payload = com.getcapacitor.JSObject().apply {
                                put("cid", cid.cid)
                                put("type", "incoming")
                            }
                            notifyListeners("incomingCall", payload, true)
                            
                            // Delay bringing app to foreground to allow the event to be processed first
                            kotlinx.coroutines.delay(500) // 500ms delay
                            bringAppToForeground()
                        }
                    }
                } else {
                    android.util.Log.w("StreamCallPlugin", "handleOnNewIntent: INCOMING_CALL - cid is null. Cannot process.")
                }
            }
        } else if (action === "io.getstream.video.android.action.ACCEPT_CALL") {
            android.util.Log.d("StreamCallPlugin", "handleOnNewIntent: Matched ACCEPT_CALL action")
            val cid = intent.streamCallId(NotificationHandler.INTENT_EXTRA_CALL_CID)
            android.util.Log.d("StreamCallPlugin", "handleOnNewIntent: ACCEPT_CALL - Extracted cid: $cid")
            if (cid != null) {
                android.util.Log.d("StreamCallPlugin", "handleOnNewIntent: ACCEPT_CALL - Accepting call with cid: $cid")
                val call = streamVideoClient?.call(id = cid.id, type = cid.type)
                if (call != null) {
                    // Log the full stack trace to see exactly where this is called from
                    val stackTrace = Thread.currentThread().stackTrace
                    android.util.Log.d("StreamCallPlugin", "internalAcceptCall STACK TRACE:")
                    stackTrace.forEachIndexed { index, element ->
                        android.util.Log.d("StreamCallPlugin", "  [$index] ${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber})")
                    }
                    kotlinx.coroutines.GlobalScope.launch {
                        internalAcceptCall(call)
                    }
                    bringAppToForeground()
                } else {
                    android.util.Log.e("StreamCallPlugin", "handleOnNewIntent: ACCEPT_CALL - Call object is null for cid: $cid")
                }
            }
        }
        // Log the intent information
        android.util.Log.d("StreamCallPlugin", "New Intent - Action: $action")
        android.util.Log.d("StreamCallPlugin", "New Intent - Data: $data")
        android.util.Log.d("StreamCallPlugin", "New Intent - Extras: $extras")
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun declineCall(call: Call) {
        android.util.Log.d("StreamCallPlugin", "declineCall called for call: ${call.id}")
        kotlinx.coroutines.GlobalScope.launch {
            try {
                call.reject()
                changeActivityAsVisibleOnLockScreen(this@StreamCallPlugin.activity, false)

                // Notify that call has ended using our helper
                updateCallStatusAndNotify(call.id, "rejected")

                hideIncomingCall()
            } catch (e: Exception) {
                android.util.Log.e("StreamCallPlugin", "Error declining call: ${e.message}")
            }
        }
    }

    private fun hideIncomingCall() {
        activity?.runOnUiThread {
            // No dedicated incoming-call native view anymore; UI handled by web layer
        }
    }

    private fun showBarrier() {
        activity?.runOnUiThread {
            barrierView?.isVisible = true
        }
    }

    private fun hideBarrier() {
        activity?.runOnUiThread {
            barrierView?.isVisible = false
        }
    }

    @OptIn(InternalStreamVideoApi::class)
    private fun setupViews() {
        val context = context
        val originalParent = bridge?.webView?.parent as? ViewGroup ?: return

        // Wrap original parent with TouchInterceptWrapper to allow touch passthrough
        val rootParent = originalParent.parent as? ViewGroup
        val indexInRoot = rootParent?.indexOfChild(originalParent) ?: -1
        if (rootParent != null && indexInRoot >= 0) {
            rootParent.removeViewAt(indexInRoot)
            touchInterceptWrapper = TouchInterceptWrapper(originalParent).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
            rootParent.addView(touchInterceptWrapper, indexInRoot)
        }

        val parent: ViewGroup = touchInterceptWrapper ?: originalParent

        // Make WebView initially visible and opaque
        bridge?.webView?.setBackgroundColor(Color.WHITE) // Or whatever background color suits your app

        // Create and add overlay view below WebView for calls
        overlayView = ComposeView(context).apply {
            isVisible = false // Start invisible until a call starts
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        parent.addView(overlayView, 0)  // Add at index 0 to ensure it's below WebView

        // Initialize with active call content
        setOverlayContent()

        // Create barrier view (above webview for blocking interaction during call setup)
        barrierView = View(context).apply {
            isVisible = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#1a242c"))
        }
        parent.addView(barrierView, parent.indexOfChild(bridge?.webView) + 1) // Add above WebView
    }

    /**
     * Centralized function to set the overlay content with call UI.
     * This handles all the common Compose UI setup for video calls.
     */
    private fun setOverlayContent(call: Call? = null) {
        overlayView?.setContent {
            VideoTheme {
                val activeCall = call ?: streamVideoClient?.state?.activeCall?.collectAsState()?.value
                if (activeCall != null) {
                    val participants by activeCall.state.participants.collectAsStateWithLifecycle()
                    val sortedParticipants by activeCall.state.sortedParticipants.collectAsStateWithLifecycle(emptyList())
                    val callParticipants by remember(participants) {
                        derivedStateOf {
                            if (sortedParticipants.size > 6) {
                                sortedParticipants
                            } else {
                                participants
                            }
                        }
                    }

                    val currentLocal by activeCall.state.me.collectAsStateWithLifecycle()

                    CallContent(
                        call = activeCall,
                        onBackPressed = { /* Handle back press if needed */ },
                        controlsContent = { /* Empty to disable native controls */ },
                        appBarContent = { /* Empty to disable app bar with stop call button */ },
                        videoRenderer = { videoModifier, videoCall, videoParticipant, videoStyle ->
                            ParticipantVideo(
                                modifier = videoModifier,
                                call = videoCall,
                                participant = videoParticipant,
                                style = videoStyle,
                                actionsContent = {_, _, _ -> {}},
                                scalingType = VideoScalingType.SCALE_ASPECT_FIT
                            )
                        },
                        floatingVideoRenderer = { call, parentSize ->
                            currentLocal?.let {
                                FloatingParticipantVideo(
                                    call = call,
                                    participant = currentLocal!!,
                                    style = RegularVideoRendererStyle().copy(isShowingConnectionQualityIndicator = false),
                                    parentBounds = parentSize,
                                    videoRenderer = { _ ->
                                        ParticipantVideo(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(VideoTheme.shapes.dialog),
                                            call = call,
                                            participant = it,
                                            style = RegularVideoRendererStyle().copy(isShowingConnectionQualityIndicator = false),
                                            actionsContent = {_, _, _ -> {}},
                                        )
                                    }
                                )
                            }

                        }
                    )
                }
            }
        }
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

            val savedCredentials = SecureUserRepository.getInstance(this.context).loadCurrentUser()
            val hadSavedCredentials = savedCredentials != null

            // Create credentials and save them
            val credentials = UserCredentials(user, token)
            SecureUserRepository.getInstance(context).save(credentials)

            // Initialize Stream Video with new credentials
            if (!hadSavedCredentials || (savedCredentials!!.user.id != userId)) {
                initializeStreamVideo()
            }

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
            kotlinx.coroutines.GlobalScope.launch {
                streamVideoClient?.let {
                    magicDeviceDelete(it)
                    it.logOut()
                    StreamVideo.removeClient()
                }

                streamVideoClient = null
                state = State.NOT_INITIALIZED

                val ret = JSObject()
                ret.put("success", true)
                call.resolve(ret)
            }
        } catch (e: Exception) {
            call.reject("Failed to logout", e)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    public fun initializeStreamVideo(passedContext: Context? = null, passedApplication: Application? = null) {
        android.util.Log.d("StreamCallPlugin", "initializeStreamVideo called")
        if (state == State.INITIALIZING) {
            android.util.Log.v("StreamCallPlugin", "Returning, already in the process of initializing")
            return
        }
        state = State.INITIALIZING

        if (passedContext != null) {
            this.savedContext = passedContext
        }
        val contextToUse = passedContext ?: this.context

        // Try to get user credentials from repository
        val savedCredentials = SecureUserRepository.getInstance(contextToUse).loadCurrentUser()
        if (savedCredentials == null) {
            android.util.Log.v("StreamCallPlugin", "Saved credentials are null")
            state = State.NOT_INITIALIZED
            return
        }

        try {
            // Check if we can reuse existing StreamVideo singleton client
            if (StreamVideo.isInstalled) {
                android.util.Log.v("StreamCallPlugin", "Found existing StreamVideo singleton client")
                if (streamVideoClient == null) {
                    android.util.Log.v("StreamCallPlugin", "Plugin's streamVideoClient is null, reusing singleton and registering event handlers")
                    streamVideoClient = StreamVideo.instance()
                    // Register event handlers since streamVideoClient was null
                    registerEventHandlers()
                } else {
                    android.util.Log.v("StreamCallPlugin", "Plugin already has streamVideoClient, skipping event handler registration")
                }
                state = State.INITIALIZED
                initializationTime = System.currentTimeMillis()
                return
            }

            // If we reach here, we need to create a new client
            android.util.Log.v("StreamCallPlugin", "No existing StreamVideo singleton client, creating new one")

            // unsafe cast, add better handling
            val application = contextToUse.applicationContext as Application
            android.util.Log.d("StreamCallPlugin", "No existing StreamVideo singleton client, creating new one")
            val notificationHandler = CustomNotificationHandler(
                application = application,
                endCall = { callId ->
                    val activeCall = streamVideoClient?.call(callId.type, callId.id)

                    kotlinx.coroutines.GlobalScope.launch {
                        try {
                            android.util.Log.i(
                                "StreamCallPlugin",
                                "Attempt to endCallRaw, activeCall == null: ${activeCall == null}",
                            )
                            activeCall?.let { endCallRaw(it) }
                        } catch (e: Exception) {
                            android.util.Log.e(
                                "StreamCallPlugin",
                                "Error ending after missed call notif action",
                                e
                            )
                        }
                    }
                },
                incomingCall = {
                    if (this.savedContext != null && initializationTime != 0L) {
                        val contextCreatedAt = initializationTime
                        val now = System.currentTimeMillis()
                        val isWithinOneSecond = (now - contextCreatedAt) <= 1000L

                        android.util.Log.i(
                            "StreamCallPlugin",
                            "Time between context creation and activity created (incoming call notif): ${now - contextCreatedAt}"
                        )
                        if (isWithinOneSecond && !bootedToHandleCall) {
                            android.util.Log.i(
                                "StreamCallPlugin",
                                "Notification incomingCall received less than 1 second after the creation of streamVideoSDK. Booted FOR SURE in order to handle the notification"
                            )
                        }
                    }
                }
            )

            val notificationConfig = NotificationConfig(
                pushDeviceGenerators = listOf(
                    FirebasePushDeviceGenerator(
                        providerName = "firebase",
                        context = contextToUse
                    )
                ),
                requestPermissionOnAppLaunch = { true },
                notificationHandler = notificationHandler,
            )

            val soundsConfig = incomingOnlyRingingConfig()

            // Initialize StreamVideo client
            streamVideoClient = StreamVideoBuilder(
                context = contextToUse,
                apiKey = contextToUse.getString(R.string.CAPACITOR_STREAM_VIDEO_APIKEY),
                geo = GEO.GlobalEdgeNetwork,
                user = savedCredentials.user,
                token = savedCredentials.tokenValue,
                notificationConfig = notificationConfig,
                sounds = soundsConfig.toSounds(),
                // loggingLevel = LoggingLevel(priority = Priority.DEBUG)
            ).build()

            // don't do event handler registration when activity may be null
            if (passedContext != null) {
                android.util.Log.w("StreamCallPlugin", "Ignoring event listeners for initializeStreamVideo")
                passedApplication?.let {
                    registerActivityEventListener(it)
                }
                initializationTime = System.currentTimeMillis()
                this.state = State.INITIALIZED
                return
            }

            registerEventHandlers()

            android.util.Log.v("StreamCallPlugin", "Initialization finished")
            initializationTime = System.currentTimeMillis()
            state = State.INITIALIZED
        } catch (e: Exception) {
            state = State.NOT_INITIALIZED
            throw e
        }
    }

    private fun moveAllActivitiesToBackgroundOrKill(context: Context, allowKill: Boolean = false) {
        try {
            if (allowKill && bootedToHandleCall && savedActivity != null) {
                android.util.Log.d("StreamCallPlugin", "App was booted to handle call and allowKill is true, killing app")
                savedActivity?.let { act ->
                    try {
                        // Get the ActivityManager
                        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                        // Remove the task
                        val tasks = activityManager.appTasks
                        tasks.forEach { task ->
                            task.finishAndRemoveTask()
                        }
                        // Finish the activity
                        act.finish()
                        // Remove from recents
                        act.finishAndRemoveTask()
                        // Give a small delay for cleanup
                        Handler(Looper.getMainLooper()).postDelayed({
                            // Kill the process
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }, 100)
                    } catch (e: Exception) {
                        android.util.Log.e("StreamCallPlugin", "Error during aggressive cleanup", e)
                        // Fallback to direct process kill
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }
                return
            }

            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_HOME)
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            android.util.Log.d("StreamCallPlugin", "Moving app to background using HOME intent")
        } catch (e: Exception) {
            android.util.Log.e("StreamCallPlugin", "Failed to move app to background", e)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun registerEventHandlers() {
        // Subscribe to call events
        streamVideoClient?.let { client ->
            client.subscribe { event: VideoEvent ->
                android.util.Log.v("StreamCallPlugin", "Received an event ${event.getEventType()} $event")
                when (event) {
                    is CallRingEvent -> {
                        // Extract caller information from the ringing call
                        kotlinx.coroutines.GlobalScope.launch {
                            try {
                                val callCid = event.callCid
                                val callIdParts = callCid.split(":")
                                if (callIdParts.size >= 2) {
                                    val callType = callIdParts[0]
                                    val callId = callIdParts[1]
                                    val call = streamVideoClient?.call(type = callType, id = callId)
                                    val callInfo = call?.get()
                                    val callerInfo = callInfo?.getOrNull()?.call?.createdBy
                                    
                                    // Pass caller information to the ringing event
                                    if (callerInfo != null) {
                                        val caller = mapOf(
                                            "userId" to callerInfo.id,
                                            "name" to (callerInfo.name ?: ""),
                                            "imageURL" to (callerInfo.image ?: ""),
                                            "role" to (callerInfo.role ?: "")
                                        )
                                        updateCallStatusAndNotify(event.callCid, "ringing", null, null, null, caller)
                                    } else {
                                        updateCallStatusAndNotify(event.callCid, "ringing")
                                    }
                                } else {
                                    updateCallStatusAndNotify(event.callCid, "ringing")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("StreamCallPlugin", "Error getting caller info for ringing event", e)
                                updateCallStatusAndNotify(event.callCid, "ringing")
                            }
                        }
                    }
                    // Handle CallCreatedEvent differently - only log it but don't try to access members yet
                    is CallCreatedEvent -> {
                        val callCid = event.callCid
                        android.util.Log.d("StreamCallPlugin", "CallCreatedEvent: Received for $callCid")
                        android.util.Log.d("StreamCallPlugin", "CallCreatedEvent: All members from event: ${event.members.joinToString { it.user.id + " (role: " + it.user.role + ")" }}")
                        android.util.Log.d("StreamCallPlugin", "CallCreatedEvent: Self user ID from SDK: ${this@StreamCallPlugin.streamVideoClient?.userId}")

                        // Only send "created" event for outgoing calls (calls created by current user)
                        // For incoming calls, we'll only send "ringing" event in CallRingEvent handler
                        kotlinx.coroutines.GlobalScope.launch {
                            try {
                                val callIdParts = callCid.split(":")
                                if (callIdParts.size >= 2) {
                                    val callType = callIdParts[0]
                                    val callId = callIdParts[1]
                                    val call = streamVideoClient?.call(type = callType, id = callId)
                                    val callInfo = call?.get()
                                    val createdBy = callInfo?.getOrNull()?.call?.createdBy
                                    val currentUserId = streamVideoClient?.userId
                                    
                                    android.util.Log.d("StreamCallPlugin", "CallCreatedEvent: Call created by: ${createdBy?.id}, Current user: $currentUserId")
                                    
                                    // Only notify for outgoing calls (where current user is the creator)
                                    if (createdBy?.id == currentUserId) {
                                        android.util.Log.d("StreamCallPlugin", "CallCreatedEvent: This is an outgoing call, sending created event")
                                        
                                        val callParticipants = event.members.filter {
                                            val selfId = this@StreamCallPlugin.streamVideoClient?.userId
                                            val memberId = it.user.id
                                            val isSelf = memberId == selfId
                                            android.util.Log.d("StreamCallPlugin", "CallCreatedEvent: Filtering member $memberId. Self ID: $selfId. Is self: $isSelf")
                                            !isSelf
                                        }.map { it.user.id }

                                        android.util.Log.d("StreamCallPlugin", "Call created for $callCid with ${callParticipants.size} remote participants: ${callParticipants.joinToString()}.")

                                        // Start tracking this call now that we have the member list
                                        startCallTimeoutMonitor(callCid, callParticipants)

                                        // Extract all members information (including self) for UI display
                                        val allMembers = event.members.map { member ->
                                            mapOf(
                                                "userId" to member.user.id,
                                                "name" to (member.user.name ?: ""),
                                                "imageURL" to (member.user.image ?: ""),
                                                "role" to (member.user.role ?: "")
                                            )
                                        }

                                        updateCallStatusAndNotify(callCid, "created", null, null, allMembers)
                                    } else {
                                        android.util.Log.d("StreamCallPlugin", "CallCreatedEvent: This is an incoming call (created by ${createdBy?.id}), not sending created event")
                                    }
                                } else {
                                    android.util.Log.w("StreamCallPlugin", "CallCreatedEvent: Invalid call CID format: $callCid")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("StreamCallPlugin", "Error processing CallCreatedEvent", e)
                            }
                        }
                    }
                    // Add handler for CallSessionStartedEvent which contains participant information
                    is CallSessionStartedEvent -> {
                        val callCid = event.callCid
                        updateCallStatusAndNotify(callCid, "session_started")
                    }

                    is CallRejectedEvent -> {
                        val userId = event.user.id
                        val callCid = event.callCid

                        // Update call state
                        callStates[callCid]?.let { callState ->
                            callState.participantResponses[userId] = "rejected"
                        }

                        updateCallStatusAndNotify(callCid, "rejected", userId)

                        // Check if all participants have responded
                        checkAllParticipantsResponded(callCid)
                    }

                    is CallMissedEvent -> {
                        val userId = event.user.id
                        val callCid = event.callCid

                        // Update call state
                        callStates[callCid]?.let { callState ->
                            callState.participantResponses[userId] = "missed"
                        }

                        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                        if (keyguardManager.isKeyguardLocked) {
                            android.util.Log.d("StreamCallPlugin", "Stop ringing and move to background")
                            moveAllActivitiesToBackgroundOrKill(context)
                        }

                        updateCallStatusAndNotify(callCid, "missed", userId)

                        // Check if all participants have responded
                        checkAllParticipantsResponded(callCid)
                    }

                    is CallAcceptedEvent -> {
                        val userId = event.user.id
                        val callCid = event.callCid

                        // Update call state
                        callStates[callCid]?.let { callState ->
                            callState.participantResponses[userId] = "accepted"

                            // Since someone accepted, cancel the timeout timer
                            android.util.Log.d("StreamCallPlugin", "Call accepted by $userId, canceling timeout timer for $callCid")
                            callState.timer?.removeCallbacksAndMessages(null)
                            callState.timer = null
                        }

                        updateCallStatusAndNotify(callCid, "accepted", userId)
                    }

                    is CallEndedEvent -> {
                        runOnMainThread {
                            android.util.Log.d("StreamCallPlugin", "Setting overlay invisible due to CallEndedEvent for call ${event.callCid}")
                            // Clean up call resources
                            val callCid = event.callCid
                            cleanupCall(callCid)
                        }
                        updateCallStatusAndNotify(event.callCid, "left")
                    }

                    is CallSessionEndedEvent -> {
                        runOnMainThread {
                            android.util.Log.d("StreamCallPlugin", "Setting overlay invisible due to CallSessionEndedEvent for call ${event.callCid}. Test session: ${event.call.session?.endedAt}")
                            // Clean up call resources
                            val callCid = event.callCid
                            cleanupCall(callCid)
                        }
                        updateCallStatusAndNotify(event.callCid, "left")
                    }

                    is ParticipantLeftEvent, is CallSessionParticipantLeftEvent, is CallSessionParticipantCountsUpdatedEvent -> {
                        val activeCall = streamVideoClient?.state?.activeCall?.value

                        val callId = when (event) {
                            is ParticipantLeftEvent -> {
                                event.callCid
                            }
                            is CallSessionParticipantLeftEvent -> {
                                event.callCid
                            }
                            is CallSessionParticipantCountsUpdatedEvent -> {
                                event.callCid
                            }

                            else -> {
                                throw RuntimeException("Unreachable code reached when getting callId")
                            }
                        }

                        android.util.Log.d("StreamCallPlugin", "CallSessionParticipantLeftEvent: Received for call $callId. Active call: ${activeCall?.cid}")


                        if (activeCall != null && activeCall.cid == callId) {
                            val connectionState = activeCall.state.connection.value
                            if (connectionState != RealtimeConnection.Disconnected) {
                                val total = activeCall.state.participantCounts.value?.total
                                android.util.Log.d("StreamCallPlugin", "CallSessionParticipantLeftEvent: Participant left, remaining: $total");
                                if (total != null && total <= 2) {
                                    android.util.Log.d("StreamCallPlugin", "CallSessionParticipantLeftEvent: All remote participants have left call ${activeCall.cid}. Ending call.")
                                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                                        endCallRaw(activeCall)
                                    }
                                }
                            }
                        } else {
                            android.util.Log.d("StreamCallPlugin", "CallSessionParticipantLeftEvent: Conditions not met (activeCall null, or cid mismatch, or local user not joined). ActiveCall CID: ${activeCall?.cid}")
                        }
                    }

                    else -> {
                        updateCallStatusAndNotify(
                            streamVideoClient?.state?.activeCall?.value?.cid ?: "",
                            event.getEventType()
                        )
                    }
                }
            }

            // Add call state subscription using collect
            // used so that it follows the same patterns as iOS
            kotlinx.coroutines.GlobalScope.launch {
                client.state.activeCall.collect { call ->
                    android.util.Log.d("StreamCallPlugin", "Call State Update:")
                    android.util.Log.d("StreamCallPlugin", "- Call is null: ${call == null}")

                    call?.state?.let { state ->
                        android.util.Log.d("StreamCallPlugin", "- Session ID: ${state.session.value?.id}")
                        android.util.Log.d("StreamCallPlugin", "- All participants: ${state.participants}")
                        android.util.Log.d("StreamCallPlugin", "- Remote participants: ${state.remoteParticipants}")

                        // Notify that a call has started or state updated (e.g., participants changed but still active)
                        // The actual check for "last participant" is now handled by CallSessionParticipantLeftEvent
                        updateCallStatusAndNotify(call.cid, "joined")
                        // Make sure activity is visible on lock screen
                        changeActivityAsVisibleOnLockScreen(this@StreamCallPlugin.activity, true)
                    } ?: run {
                        // Notify that call has ended using our helper
                        updateCallStatusAndNotify("", "left")
                        changeActivityAsVisibleOnLockScreen(this@StreamCallPlugin.activity, false)
                    }
                }
            }
        }
    }

    private fun registerActivityEventListener(application: Application) {
        android.util.Log.i("StreamCallPlugin", "Registering activity event listener")
        application.registerActivityLifecycleCallbacks(object: ActivityLifecycleCallbacks() {
            override fun onActivityCreated(activity: Activity, bunlde: Bundle?) {
                android.util.Log.d("StreamCallPlugin", "onActivityCreated called")
                savedContext?.let {
                    if (this@StreamCallPlugin.savedActivity != null && activity is BridgeActivity) {
                        android.util.Log.d("StreamCallPlugin", "Activity created before, but got re-created. saving and returning")
                        this@StreamCallPlugin.savedActivity = activity;
                        return
                    }
                    if (initializationTime == 0L) {
                        android.util.Log.w("StreamCallPlugin", "initializationTime is zero. Not continuing with onActivityCreated")
                        return
                    }

                    val keyguardManager = application.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    val isLocked = keyguardManager.isKeyguardLocked

                    if (isLocked) {
                        this@StreamCallPlugin.bootedToHandleCall = true;
                        android.util.Log.d("StreamCallPlugin", "Detected that the app booted an activity while locked. We will kill after the call fails")
                    }

                    if (this@StreamCallPlugin.bridge == null && activity is BridgeActivity) {
                        this@StreamCallPlugin.savedActivity = activity
                    }
                }
                super.onActivityCreated(activity, bunlde)
            }

            override fun onActivityPaused(activity: Activity) {
                if (activity is BridgeActivity && activity == this@StreamCallPlugin.savedActivity) {
                    this@StreamCallPlugin.savedActivityPaused = true
                }
                super.onActivityPaused(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                if (activity is BridgeActivity && activity == this@StreamCallPlugin.savedActivity) {
                    this@StreamCallPlugin.savedActivityPaused = false
                }
                for (call in this@StreamCallPlugin.savedCallsToEndOnResume) {
                    android.util.Log.d("StreamCallPlugin", "Trying to end call with ID ${call.id} on resume")
                    transEndCallRaw(call)
                }
                super.onActivityResumed(activity)
            }
        })
    }

    @PluginMethod
    public fun acceptCall(call: PluginCall) {
        android.util.Log.d("StreamCallPlugin", "acceptCall called")
        try {
            val streamVideoCall = streamVideoClient?.state?.ringingCall?.value
            if (streamVideoCall == null) {
                call.reject("Ringing call is null")
                return
            }
            kotlinx.coroutines.GlobalScope.launch {
                internalAcceptCall(streamVideoCall)
            }
        } catch (t: Throwable) {
            android.util.Log.d("StreamCallPlugin", "JS -> acceptCall fail", t);
            call.reject("Cannot acceptCall")
        }
    }

    @PluginMethod
    public fun rejectCall(call: PluginCall) {
        android.util.Log.d("StreamCallPlugin", "rejectCall called")
        try {
            val streamVideoCall = streamVideoClient?.state?.ringingCall?.value
            if (streamVideoCall == null) {
                call.reject("Ringing call is null")
                return
            }
            kotlinx.coroutines.GlobalScope.launch {
                declineCall(streamVideoCall)
            }
        } catch (t: Throwable) {
            android.util.Log.d("StreamCallPlugin", "JS -> rejectCall fail", t);
            call.reject("Cannot rejectCall")
        }
    }

    @OptIn(DelicateCoroutinesApi::class, InternalStreamVideoApi::class)
    internal fun internalAcceptCall(call: Call) {
        android.util.Log.d("StreamCallPlugin", "internalAcceptCall: Entered for call: ${call.id}")

        kotlinx.coroutines.GlobalScope.launch {
            try {
                android.util.Log.d("StreamCallPlugin", "internalAcceptCall: Coroutine started for call ${call.id}")

                // Hide incoming call view first
                runOnMainThread {
                    android.util.Log.d("StreamCallPlugin", "internalAcceptCall: Hiding incoming call view for call ${call.id}")
                    // No dedicated incoming-call native view anymore; UI handled by web layer
                }
                android.util.Log.d("StreamCallPlugin", "internalAcceptCall: Incoming call view hidden for call ${call.id}")

                // Check and request permissions before joining the call
                val permissionsGranted = checkPermissions()
                android.util.Log.d("StreamCallPlugin", "internalAcceptCall: checkPermissions result for call ${call.id}: $permissionsGranted")
                if (!permissionsGranted) {
                    android.util.Log.d("StreamCallPlugin", "internalAcceptCall: Permissions not granted for call ${call.id}. Requesting permissions.")
                    requestPermissions()
                    // Do not proceed with joining until permissions are granted
                    runOnMainThread {
                        android.widget.Toast.makeText(
                            context,
                            "Permissions required for call. Please grant them.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    android.util.Log.w("StreamCallPlugin", "internalAcceptCall: Permissions not granted for call ${call.id}. Aborting accept process.")
                    return@launch
                }

                android.util.Log.d("StreamCallPlugin", "internalAcceptCall: Permissions are granted for call ${call.id}. Proceeding to accept.")
                // Join the call without affecting others
                call.accept()
                android.util.Log.d("StreamCallPlugin", "internalAcceptCall: call.accept() completed for call ${call.id}")
                call.join()
                android.util.Log.d("StreamCallPlugin", "internalAcceptCall: call.join() completed for call ${call.id}")
                streamVideoClient?.state?.setActiveCall(call)
                android.util.Log.d("StreamCallPlugin", "internalAcceptCall: setActiveCall completed for call ${call.id}")

                // Notify that call has started using helper
                updateCallStatusAndNotify(call.id, "joined")
                android.util.Log.d("StreamCallPlugin", "internalAcceptCall: updateCallStatusAndNotify(joined) called for ${call.id}")

                // Show overlay view with the active call and make webview transparent
                runOnMainThread {
                    android.util.Log.d("StreamCallPlugin", "internalAcceptCall: Updating UI for active call ${call.id} - setting overlay visible.")
                    bridge?.webView?.setBackgroundColor(Color.TRANSPARENT) // Make webview transparent
                    android.util.Log.d("StreamCallPlugin", "internalAcceptCall: WebView background set to transparent for call ${call.id}")
                    bridge?.webView?.bringToFront() // Ensure WebView is on top and transparent
                    android.util.Log.d("StreamCallPlugin", "internalAcceptCall: WebView brought to front for call ${call.id}")
                    // Reusing the initialization logic from call method
                    call.microphone?.setEnabled(true)
                    call.camera?.setEnabled(true)
                    android.util.Log.d("StreamCallPlugin", "internalAcceptCall: Microphone and camera enabled for call ${call.id}")
                    android.util.Log.d("StreamCallPlugin", "internalAcceptCall: Setting CallContent with active call ${call.id}")
                    setOverlayContent(call)
                    android.util.Log.d("StreamCallPlugin", "internalAcceptCall: Content set for overlayView for call ${call.id}")
                    overlayView?.isVisible = true
                    android.util.Log.d("StreamCallPlugin", "internalAcceptCall: OverlayView set to visible for call ${call.id}, isVisible: ${overlayView?.isVisible}")

                    // Ensure overlay is behind WebView by adjusting its position in the parent
                    val parent = overlayView?.parent as? ViewGroup
                    parent?.removeView(overlayView)
                    parent?.addView(overlayView, 0) // Add at index 0 to ensure it's behind other views
                    android.util.Log.d("StreamCallPlugin", "internalAcceptCall: OverlayView re-added to parent at index 0 for call ${call.id}")
                    // Add a small delay to ensure UI refresh
                    mainHandler.postDelayed({
                        android.util.Log.d("StreamCallPlugin", "internalAcceptCall: Delayed UI check, overlay visible: ${overlayView?.isVisible} for call ${call.id}")
                        if (overlayView?.isVisible == true) {
                            overlayView?.invalidate()
                            overlayView?.requestLayout()
                            android.util.Log.d("StreamCallPlugin", "internalAcceptCall: UI invalidated and layout requested for call ${call.id}")
                            // Force refresh with active call from client
                            val activeCall = streamVideoClient?.state?.activeCall?.value
                            if (activeCall != null) {
                                android.util.Log.d("StreamCallPlugin", "internalAcceptCall: Force refreshing CallContent with active call ${activeCall.id}")
                                setOverlayContent(activeCall)
                                android.util.Log.d("StreamCallPlugin", "internalAcceptCall: Content force refreshed for call ${activeCall.id}")
                            } else {
                                android.util.Log.w("StreamCallPlugin", "internalAcceptCall: Active call is null during force refresh for call ${call.id}")
                            }
                        } else {
                            android.util.Log.w("StreamCallPlugin", "internalAcceptCall: overlayView not visible after delay for call ${call.id}")
                        }
                    }, 1000) // Increased delay to ensure all events are processed
                }
            } catch (e: Exception) {
                android.util.Log.e("StreamCallPlugin", "internalAcceptCall: Error accepting call ${call.id}: ${e.message}", e)
                runOnMainThread {
                    android.widget.Toast.makeText(
                        context,
                        "Failed to join call: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Function to check required permissions
    private fun checkPermissions(): Boolean {
        android.util.Log.d("StreamCallPlugin", "checkPermissions: Entered")
        val audioPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        android.util.Log.d("StreamCallPlugin", "checkPermissions: RECORD_AUDIO permission status: $audioPermission (Granted=${PackageManager.PERMISSION_GRANTED})")
        val cameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        android.util.Log.d("StreamCallPlugin", "checkPermissions: CAMERA permission status: $cameraPermission (Granted=${PackageManager.PERMISSION_GRANTED})")
        val allGranted = audioPermission == PackageManager.PERMISSION_GRANTED && cameraPermission == PackageManager.PERMISSION_GRANTED
        android.util.Log.d("StreamCallPlugin", "checkPermissions: All permissions granted: $allGranted")
        return allGranted
    }

    // Function to request required permissions
    private fun requestPermissions() {
        android.util.Log.d("StreamCallPlugin", "requestPermissions: Requesting RECORD_AUDIO and CAMERA permissions.")
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA),
            1001 // Request code for permission result handling
        )
        android.util.Log.d("StreamCallPlugin", "requestPermissions: ActivityCompat.requestPermissions called.")
    }

    // Override to handle permission results
    override fun handleRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.handleRequestPermissionsResult(requestCode, permissions, grantResults)
        android.util.Log.d("StreamCallPlugin", "handleRequestPermissionsResult: Entered. RequestCode: $requestCode")
        if (requestCode == 1001) {
            android.util.Log.d("StreamCallPlugin", "handleRequestPermissionsResult: Matched requestCode 1001.")
            logPermissionResults(permissions, grantResults)
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                android.util.Log.i("StreamCallPlugin", "handleRequestPermissionsResult: All permissions GRANTED.")
                // Permissions granted, can attempt to join the call again if needed
                val ringingCall = streamVideoClient?.state?.ringingCall?.value
                android.util.Log.d("StreamCallPlugin", "handleRequestPermissionsResult: Ringing call object: ${ringingCall?.id}")
                if (ringingCall != null) {
                    android.util.Log.d("StreamCallPlugin", "handleRequestPermissionsResult: Ringing call found (${ringingCall.id}). Re-attempting internalAcceptCall.")
                    kotlinx.coroutines.GlobalScope.launch {
                        internalAcceptCall(ringingCall)
                    }
                } else {
                    android.util.Log.w("StreamCallPlugin", "handleRequestPermissionsResult: Permissions granted, but no ringing call found to accept.")
                }
            } else {
                android.util.Log.e("StreamCallPlugin", "handleRequestPermissionsResult: One or more permissions DENIED.")
                runOnMainThread {
                    android.widget.Toast.makeText(
                        context,
                        "Permissions not granted. Cannot join call.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            android.util.Log.w("StreamCallPlugin", "handleRequestPermissionsResult: Received unknown requestCode: $requestCode")
        }
    }

    private fun logPermissionResults(permissions: Array<out String>, grantResults: IntArray) {
        android.util.Log.d("StreamCallPlugin", "logPermissionResults: Logging permission results:")
        for (i in permissions.indices) {
            val permission = permissions[i]
            val grantResult = if (grantResults.size > i) grantResults[i] else -999 // -999 for safety if arrays mismatch
            val resultString = if (grantResult == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED ($grantResult)"
            android.util.Log.d("StreamCallPlugin", "  Permission: $permission, Result: $resultString")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
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

    @OptIn(DelicateCoroutinesApi::class)
    @PluginMethod
    fun isCameraEnabled(call: PluginCall) {
        try {
            val activeCall = streamVideoClient?.state?.activeCall
            if (activeCall == null) {
                call.reject("No active call")
                return
            }

            kotlinx.coroutines.GlobalScope.launch {
                try {
                    val enabled = activeCall.value?.camera?.isEnabled?.value
                    if (enabled == null) {
                        call.reject("Cannot figure out if camera is enabled or not")
                        return@launch
                    }
                    call.resolve(JSObject().apply {
                        put("enabled", enabled)
                    })
                } catch (e: Exception) {
                    android.util.Log.e("StreamCallPlugin", "Error checking the camera status: ${e.message}")
                    call.reject("Failed to check if camera is enabled: ${e.message}")
                }
            }
        } catch (e: Exception) {
            call.reject("StreamVideo not initialized")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
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

    @OptIn(InternalStreamVideoApi::class)
    private suspend fun endCallRaw(call: Call) {
        val callId = call.id
        android.util.Log.d("StreamCallPlugin", "Attempting to end call $callId")
        
        try {
            // Get call information to make the decision
            val callInfo = call.get()
            val callData = callInfo?.getOrNull()?.call
            val currentUserId = streamVideoClient?.userId
            val createdBy = callData?.createdBy?.id
            val isCreator = createdBy == currentUserId
            
            // Use call.state.totalParticipants to get participant count (as per StreamVideo Android SDK docs)
            val totalParticipants = call.state.totalParticipants.value ?: 0
            val shouldEndCall = isCreator || totalParticipants <= 2
            
            android.util.Log.d("StreamCallPlugin", "Call $callId - Creator: $createdBy, CurrentUser: $currentUserId, IsCreator: $isCreator, TotalParticipants: $totalParticipants, ShouldEnd: $shouldEndCall")
            
            if (shouldEndCall) {
                // End the call for everyone if I'm the creator or only 2 people
                android.util.Log.d("StreamCallPlugin", "Ending call $callId for all participants (creator: $isCreator, participants: $totalParticipants)")
                call.end()
            } else {
                // Just leave the call if there are more than 2 people and I'm not the creator
                android.util.Log.d("StreamCallPlugin", "Leaving call $callId (not creator, >2 participants)")
                call.leave()
            }

            // Here, we'll also mark the activity as not-visible on lock screen
            this@StreamCallPlugin.savedActivity?.let {
                changeActivityAsVisibleOnLockScreen(it, false)
            }

        } catch (e: Exception) {
            android.util.Log.e("StreamCallPlugin", "Error getting call info for $callId, defaulting to leave()", e)
            // Fallback to leave if we can't determine the call info
            call.leave()
        }

        // Capture context from the overlayView
        val currentContext = overlayView?.context ?: this.savedContext
        if (currentContext == null) {
            android.util.Log.w("StreamCallPlugin", "Cannot end call $callId because context is null")
            return
        }

        runOnMainThread {
            android.util.Log.d("StreamCallPlugin", "Setting overlay invisible after ending call $callId")


            currentContext.let { ctx ->
                val keyguardManager = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (keyguardManager.isKeyguardLocked) {
                    // we allow kill exclusively here
                    // the idea is that:
                    // the 'empty' instance of this plugin class gets created in application
                    // then, it handles a notification and setts the context (this.savedContext)
                    // if the context is new
                    moveAllActivitiesToBackgroundOrKill(ctx, true)
                }
            }

            val savedCapacitorActivity = savedActivity
            if (savedCapacitorActivity != null) {

                if (savedActivityPaused) {
                    android.util.Log.d("StreamCallPlugin", "Activity is paused. Adding call ${call.id} to savedCallsToEndOnResume")
                    savedCallsToEndOnResume.add(call)
                } else {
                    transEndCallRaw(call)
                }

                return@runOnMainThread
            }

            setOverlayContent(call)
            overlayView?.isVisible = false
            bridge?.webView?.setBackgroundColor(Color.WHITE) // Restore webview opacity

            // Also hide incoming call view if visible
            android.util.Log.d("StreamCallPlugin", "Hiding incoming call view for call $callId")
            // No dedicated incoming-call native view anymore; UI handled by web layer
        }

        // Notify that call has ended using helper
        updateCallStatusAndNotify(callId, "left")
    }

    private fun changeActivityAsVisibleOnLockScreen(activity: Activity, visible: Boolean) {
        if (visible) {
            // Ensure the activity is visible over the lock screen when launched via full-screen intent
            android.util.Log.d("StreamCallPlugin", "Mark the mainActivity as visible on the lockscreen")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            } else {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        } else {
            // Ensure the activity is NOT visible over the lock screen when launched via full-screen intent
            android.util.Log.d("StreamCallPlugin", "Clear the flag for the mainActivity for visible on the lockscreen")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(false)
                activity.setTurnScreenOn(false)
            } else {
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        }

    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun transEndCallRaw(call: Call) {
        val callId = call.id
        val savedCapacitorActivity = savedActivity
        if (savedCapacitorActivity == null) {
            android.util.Log.d("StreamCallPlugin", "Cannot perform transEndCallRaw for call $callId. savedCapacitorActivity is null")
            return
        }
        android.util.Log.d("StreamCallPlugin", "Performing a trans-instance call to end call with id $callId")
        if (savedCapacitorActivity !is BridgeActivity) {
            android.util.Log.e("StreamCallPlugin", "Saved activity is NOT a Capactor activity. Saved activity class: ${savedCapacitorActivity.javaClass.canonicalName}")
            return
        }
        val plugin = savedCapacitorActivity.bridge.getPlugin("StreamCall")
        if (plugin == null) {
            android.util.Log.e("StreamCallPlugin", "Plugin with name StreamCall not found?????")
            return
        }
        if (plugin.instance !is StreamCallPlugin) {
            android.util.Log.e("StreamCallPlugin", "Plugin found, but invalid instance")
            return
        }

        kotlinx.coroutines.GlobalScope.launch {
            try {
                (plugin.instance as StreamCallPlugin).endCallRaw(call)
            } catch (e: Exception) {
                android.util.Log.e("StreamCallPlugin", "Error ending call on remote instance", e)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @PluginMethod
    fun endCall(call: PluginCall) {
        try {
            val activeCall = streamVideoClient?.state?.activeCall?.value
            val ringingCall = streamVideoClient?.state?.ringingCall?.value
            
            val callToEnd = activeCall ?: ringingCall
            
            if (callToEnd == null) {
                android.util.Log.w("StreamCallPlugin", "Attempted to end call but no active or ringing call found")
                call.reject("No active call to end")
                return
            }

            android.util.Log.d("StreamCallPlugin", "Ending call: activeCall=${activeCall?.id}, ringingCall=${ringingCall?.id}, callToEnd=${callToEnd.id}")

            kotlinx.coroutines.GlobalScope.launch {
                try {
                    endCallRaw(callToEnd)
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

    @OptIn(DelicateCoroutinesApi::class, InternalStreamVideoApi::class)
    @PluginMethod
    fun call(call: PluginCall) {
        val userIds = call.getArray("userIds")?.toList<String>()
        if (userIds.isNullOrEmpty()) {
            call.reject("Missing required parameter: userIds (array of user IDs)")
            return
        }

        try {
            if (state != State.INITIALIZED) {
                call.reject("StreamVideo not initialized")
                return
            }

            val selfUserId = streamVideoClient?.userId
            if (selfUserId == null) {
                call.reject("No self-user id found. Are you not logged in?")
                return
            }

            val callType = call.getString("type") ?: "default"
            val shouldRing = call.getBoolean("ring") ?: true
            val callId = java.util.UUID.randomUUID().toString()
            val team = call.getString("team");

            android.util.Log.d("StreamCallPlugin", "Creating call:")
            android.util.Log.d("StreamCallPlugin", "- Call ID: $callId")
            android.util.Log.d("StreamCallPlugin", "- Call Type: $callType")
            android.util.Log.d("StreamCallPlugin", "- Users: $userIds")
            android.util.Log.d("StreamCallPlugin", "- Should Ring: $shouldRing")

            // Check permissions before creating the call
            if (!checkPermissions()) {
                requestPermissions()
                call.reject("Permissions required for call. Please grant them.")
                return
            }

            // Create and join call in a coroutine
            kotlinx.coroutines.GlobalScope.launch {
                try {
                    // Create the call object
                    val streamCall = streamVideoClient?.call(type = callType, id = callId)

                    // Note: We no longer start tracking here - we'll wait for CallSessionStartedEvent
                    // instead, which contains the actual participant list

                    android.util.Log.d("StreamCallPlugin", "Creating call with members...")
                    // Create the call with all members
                    val createResult = streamCall?.create(
                        memberIds = userIds + selfUserId,
                        custom = emptyMap(),
                        ring = shouldRing,
                        team = team,
                    )

                    if (createResult?.isFailure == true) {
                        throw (createResult.errorOrNull() ?: RuntimeException("Unknown error creating call")) as Throwable
                    }

                    android.util.Log.d("StreamCallPlugin", "Setting overlay visible for outgoing call $callId")
                    // Show overlay view
                    activity?.runOnUiThread {
                        streamCall?.microphone?.setEnabled(true)
                        streamCall?.camera?.setEnabled(true)

                        bridge?.webView?.setBackgroundColor(Color.TRANSPARENT) // Make webview transparent
                        bridge?.webView?.bringToFront() // Ensure WebView is on top and transparent
                        setOverlayContent(streamCall)
                        overlayView?.isVisible = true
                        // Ensure overlay is behind WebView by adjusting its position in the parent
                        val parent = overlayView?.parent as? ViewGroup
                        parent?.removeView(overlayView)
                        parent?.addView(overlayView, 0) // Add at index 0 to ensure it's behind other views
                    }

                    // Resolve the call with success
                    call.resolve(JSObject().apply {
                        put("success", true)
                    })
                } catch (e: Exception) {
                    android.util.Log.e("StreamCallPlugin", "Error making call: ${e.message}")
                    call.reject("Failed to make call: ${e.message}")
                }
            }
        } catch (e: Exception) {
            call.reject("Failed to make call: ${e.message}")
        }
    }

    private fun startCallTimeoutMonitor(callCid: String, memberIds: List<String>) {
        val callState = LocalCallState(members = memberIds)

        val handler = Handler(Looper.getMainLooper())
        val timeoutRunnable = object : Runnable {
            override fun run() {
                checkCallTimeout(callCid)
                handler.postDelayed(this, 1000)
            }
        }

        handler.postDelayed(timeoutRunnable, 1000)
        callState.timer = handler

        callStates[callCid] = callState

        android.util.Log.d("StreamCallPlugin", "Started timeout monitor for call $callCid with ${memberIds.size} members")
    }

    private fun checkCallTimeout(callCid: String) {
        val callState = callStates[callCid] ?: return

        val now = System.currentTimeMillis()
        val elapsedSeconds = (now - callState.createdAt) / 1000

        if (elapsedSeconds >= 30) {
            android.util.Log.d("StreamCallPlugin", "Call $callCid has timed out after $elapsedSeconds seconds")

            val hasAccepted = callState.participantResponses.values.any { it == "accepted" }

            if (!hasAccepted) {
                android.util.Log.d("StreamCallPlugin", "No one accepted call $callCid, marking all non-responders as missed")

                // First, remove the timer to prevent further callbacks
                callState.timer?.removeCallbacksAndMessages(null)
                callState.timer = null

                callState.members.forEach { memberId ->
                    if (memberId !in callState.participantResponses) {
                        callState.participantResponses[memberId] = "missed"

                        updateCallStatusAndNotify(callCid, "missed", memberId)
                    }
                }

                val callIdParts = callCid.split(":")
                if (callIdParts.size >= 2) {
                    val callType = callIdParts[0]
                    val callId = callIdParts[1]

                    streamVideoClient?.call(type = callType, id = callId)?.let { call ->
                        kotlinx.coroutines.GlobalScope.launch {
                            try {
                                // Use endCallRaw instead of manual cleanup
                                endCallRaw(call)

                                // Clean up state - we don't need to do this in endCallRaw because we already did it here
                                callStates.remove(callCid)

                                // Notify that call has ended using helper
                                updateCallStatusAndNotify(callCid, "ended", null, "timeout")
                            } catch (e: Exception) {
                                android.util.Log.e("StreamCallPlugin", "Error ending timed out call", e)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun cleanupCall(callCid: String) {
        // Get the call state
        val callState = callStates[callCid]

        if (callState != null) {
            // Ensure timer is properly canceled
            android.util.Log.d("StreamCallPlugin", "Stopping timer for call: $callCid")
            callState.timer?.removeCallbacksAndMessages(null)
            callState.timer = null
        }

        // Remove from callStates
        callStates.remove(callCid)

        // Hide UI elements directly without setting content
        runOnMainThread {
            android.util.Log.d("StreamCallPlugin", "Hiding UI elements for call $callCid (one-time cleanup)")
            overlayView?.isVisible = false
            // here we will also make sure we don't show on lock screen
            changeActivityAsVisibleOnLockScreen(this.activity, false)
        }

        android.util.Log.d("StreamCallPlugin", "Cleaned up resources for ended call: $callCid")
    }

    private fun checkAllParticipantsResponded(callCid: String) {
        val callState = callStates[callCid] ?: return

        val totalParticipants = callState.members.size
        val responseCount = callState.participantResponses.size

        android.util.Log.d("StreamCallPlugin", "Checking responses for call $callCid: $responseCount / $totalParticipants")

        val allResponded = responseCount >= totalParticipants
        val allRejectedOrMissed = allResponded &&
                callState.participantResponses.values.all { it == "rejected" || it == "missed" }

        if (allResponded && allRejectedOrMissed) {
            android.util.Log.d("StreamCallPlugin", "All participants have rejected or missed the call $callCid")

            // Cancel the timer immediately to prevent further callbacks
            callState.timer?.removeCallbacksAndMessages(null)
            callState.timer = null

            // End the call using endCallRaw
            val callIdParts = callCid.split(":")
            if (callIdParts.size >= 2) {
                val callType = callIdParts[0]
                val callId = callIdParts[1]

                streamVideoClient?.call(type = callType, id = callId)?.let { call ->
                    kotlinx.coroutines.GlobalScope.launch {
                        try {
                            // Use endCallRaw instead of manual cleanup
                            endCallRaw(call)

                            // Clean up state - we don't need to do this in endCallRaw because we already did it here
                            callStates.remove(callCid)

                            // Notify that call has ended using helper
                            updateCallStatusAndNotify(callCid, "ended", null, "all_rejected_or_missed")
                        } catch (e: Exception) {
                            android.util.Log.e("StreamCallPlugin", "Error ending call after all rejected/missed", e)
                        }
                    }
                }
            }
        }
    }

    private suspend fun magicDeviceDelete(streamVideoClient: StreamVideo) {
        try {
            android.util.Log.d("StreamCallPlugin", "Starting magicDeviceDelete operation")

            FirebaseMessaging.getInstance().token.await()?.let {
                android.util.Log.d("StreamCallPlugin", "Found firebase token")
                val device = Device(
                    id = it,
                    pushProvider = PushProvider.FIREBASE.key,
                    pushProviderName = "firebase",
                )

                streamVideoClient.deleteDevice(device)
            }
        } catch (e: Exception) {
            android.util.Log.e("StreamCallPlugin", "Error in magicDeviceDelete", e)
        }
    }

    @PluginMethod
    fun getCallStatus(call: PluginCall) {
        // If not in a call, reject
        if (currentCallId.isEmpty() || currentCallState == "left") {
            call.reject("Not in a call")
            return
        }

        val result = JSObject()
        result.put("callId", currentCallId)
        result.put("state", currentCallState)

        // No additional fields to ensure compatibility with CallEvent interface

        call.resolve(result)
    }

    @PluginMethod
    fun setSpeaker(call: PluginCall) {
        val name = call.getString("name") ?: "speaker"
        val activeCall = streamVideoClient?.state?.activeCall?.value
        if (activeCall != null) {
            if (name == "speaker")
                activeCall.speaker.setSpeakerPhone(enable = true)
            else
                activeCall.speaker.setSpeakerPhone(enable = false)
            call.resolve(JSObject().apply {
                put("success", true)
            })
        } else {
            call.reject("No active call")
        }
    }

    @PluginMethod
    fun switchCamera(call: PluginCall) {
        val camera = call.getString("camera") ?: "front"
        val activeCall = streamVideoClient?.state?.activeCall?.value
        if (activeCall != null) {
            if (camera == "front")
                activeCall.camera.setDirection(CameraDirection.Front)
            else
                activeCall.camera.setDirection(CameraDirection.Back)
            call.resolve(JSObject().apply {
                put("success", true)
            })
        } else {
            call.reject("No active call")
        }
    }
    
    // Helper method to update call status and notify listeners
    private fun updateCallStatusAndNotify(callId: String, state: String, userId: String? = null, reason: String? = null, members: List<Map<String, Any>>? = null, caller: Map<String, Any>? = null) {
        android.util.Log.d("StreamCallPlugin", "updateCallStatusAndNotify called: callId=$callId, state=$state, userId=$userId, reason=$reason")
        // Update stored call info
        currentCallId = callId
        currentCallState = state

        // Get call type from call ID if available
        if (callId.contains(":")) {
            currentCallType = callId.split(":").firstOrNull() ?: ""
        }

        // Create data object with only the fields in the CallEvent interface
        val data = JSObject().apply {
            put("callId", callId)
            put("state", state)
            userId?.let {
                put("userId", it)
            }
            reason?.let {
                put("reason", it)
            }
            members?.let { membersList ->
                val membersArray = JSArray()
                membersList.forEach { member ->
                    val memberObj = JSObject().apply {
                        member.forEach { (key, value) ->
                            put(key, value)
                        }
                    }
                    membersArray.put(memberObj)
                }
                put("members", membersArray)
            }
            caller?.let { callerInfo ->
                val callerObj = JSObject().apply {
                    callerInfo.forEach { (key, value) ->
                        put(key, value)
                    }
                }
                put("caller", callerObj)
            }
        }

        // Notify listeners
        notifyListeners("callEvent", data)
    }

    @PluginMethod
    fun joinCall(call: PluginCall) {
        val fragment = callFragment
        if (fragment != null && fragment.getCall() != null) {
            if (!checkPermissions()) {
                requestPermissions()
                call.reject("Permissions required for call. Please grant them.")
                return
            }
            CoroutineScope(Dispatchers.Main).launch {
                fragment.getCall()?.join()
                call.resolve()
            }
        } else {
            call.reject("No active call or fragment not initialized")
        }
    }

    @PluginMethod
    fun leaveCall(call: PluginCall) {
        val fragment = callFragment
        if (fragment != null && fragment.getCall() != null) {
            CoroutineScope(Dispatchers.Main).launch {
                fragment.getCall()?.leave()
                call.resolve()
            }
        } else {
            call.reject("No active call or fragment not initialized")
        }
    }

    data class LocalCallState(
        val members: List<String>,
        val participantResponses: MutableMap<String, String> = mutableMapOf(),
        val createdAt: Long = System.currentTimeMillis(),
        var timer: Handler? = null
    )

    private val acceptCallReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            android.util.Log.d("StreamCallPlugin", "BroadcastReceiver: Received broadcast with action: ${intent?.action}")
            if (intent?.action == "io.getstream.video.android.action.ACCEPT_CALL") {
                val cid = intent.streamCallId(NotificationHandler.INTENT_EXTRA_CALL_CID)
                android.util.Log.d("StreamCallPlugin", "BroadcastReceiver: ACCEPT_CALL broadcast received with cid: $cid")
                if (cid != null) {
                    android.util.Log.d("StreamCallPlugin", "BroadcastReceiver: Accepting call with cid: $cid")
                    val call = streamVideoClient?.call(id = cid.id, type = cid.type)
                    if (call != null) {
                        kotlinx.coroutines.GlobalScope.launch {
                            internalAcceptCall(call)
                        }
                        bringAppToForeground()
                    } else {
                        android.util.Log.e("StreamCallPlugin", "BroadcastReceiver: Call object is null for cid: $cid")
                    }
                }
            }
        }
    }

    private fun bringAppToForeground() {
        try {
            val ctx = savedContext ?: context
            val launchIntent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (launchIntent != null) {
                ctx.startActivity(launchIntent)
                android.util.Log.d("StreamCallPlugin", "bringAppToForeground: Launch intent executed to foreground app")
            } else {
                android.util.Log.w("StreamCallPlugin", "bringAppToForeground: launchIntent is null")
            }
        } catch (e: Exception) {
            android.util.Log.e("StreamCallPlugin", "bringAppToForeground error", e)
        }
    }

    companion object {
        @JvmStatic fun preLoadInit(ctx: Context, app: Application) {
            holder ?: run {
                val p = StreamCallPlugin()
                p.savedContext = ctx
                p.initializeStreamVideo(ctx, app)
                holder = p
            }
        }
        private var holder: StreamCallPlugin? = null
    }
}
