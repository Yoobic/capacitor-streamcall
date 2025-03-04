import Foundation
import Capacitor
import StreamVideo
import StreamVideoSwiftUI
import SwiftUI
import Combine
import WebKit

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(StreamCallPlugin)
public class StreamCallPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "StreamCallPlugin"
    public let jsName = "StreamCall"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "loginMagicToken", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "login", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "logout", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "call", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "endCall", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setMicrophoneEnabled", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setCameraEnabled", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "acceptCall", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isCameraEnabled", returnType: CAPPluginReturnPromise)
    ]
    
    private enum State {
        case notInitialized
        case initializing
        case initialized
    }
    private var apiKey: String?
    private var state: State = .notInitialized
    private static let tokenRefreshQueue = DispatchQueue(label: "stream.call.token.refresh")
    private static let tokenRefreshSemaphore = DispatchSemaphore(value: 1)
    private var currentToken: String?
    private var tokenWaitSemaphore: DispatchSemaphore?
    
    private var overlayView: UIView?
    private var hostingController: UIHostingController<CallOverlayView>?
    private var overlayViewModel: CallOverlayViewModel?
    private var tokenSubscription: AnyCancellable?
    private var activeCallSubscription: AnyCancellable?
    private var lastVoIPToken: String?
    private var touchInterceptView: TouchInterceptView?
    
    private var streamVideo: StreamVideo?
    
    @Injected(\.callKitAdapter) var callKitAdapter
    @Injected(\.callKitPushNotificationAdapter) var callKitPushNotificationAdapter
    private var webviewDelegate: WebviewNavigationDelegate?
    
    override public func load() {
        // Read API key from Info.plist
        if let apiKey = Bundle.main.object(forInfoDictionaryKey: "CAPACITOR_STREAM_VIDEO_APIKEY") as? String {
            self.apiKey = apiKey
        }
        if self.apiKey == nil {
            fatalError("Cannot get apikey")
        }
        
        // Check if we have a logged in user for handling incoming calls
        if let credentials = SecureUserRepository.shared.loadCurrentUser() {
            print("Loading user for StreamCallPlugin: \(credentials.user.name)")
            DispatchQueue.global(qos: .userInitiated).async {
                self.initializeStreamVideo()
            }
        }
        
        // Create and set the navigation delegate
        self.webviewDelegate = WebviewNavigationDelegate(
            wrappedDelegate: self.webView?.navigationDelegate,
            onSetupOverlay: { [weak self] in
                guard let self = self else { return }
                print("Attempting to setup call view")
                
                self.setupViews()
            }
        )
        
        self.webView?.navigationDelegate = self.webviewDelegate
    }
    
    //    private func cleanupStreamVideo() {
    //        // Cancel subscriptions
    //        tokenSubscription?.cancel()
    //        tokenSubscription = nil
    //        activeCallSubscription?.cancel()
    //        activeCallSubscription = nil
    //        lastVoIPToken = nil
    //
    //        // Cleanup UI
    //        Task { @MainActor in
    //            self.overlayViewModel?.updateCall(nil)
    //            self.overlayViewModel?.updateStreamVideo(nil)
    //            self.overlayView?.removeFromSuperview()
    //            self.overlayView = nil
    //            self.hostingController = nil
    //            self.overlayViewModel = nil
    //
    //            // Reset webview
    //            self.webView?.isOpaque = true
    //            self.webView?.backgroundColor = .white
    //            self.webView?.scrollView.backgroundColor = .white
    //        }
    //
    //        state = .notInitialized
    //    }
    
    private func requireInitialized() throws {
        guard state == .initialized else {
            throw NSError(domain: "StreamCallPlugin", code: -1, userInfo: [NSLocalizedDescriptionKey: "StreamVideo not initialized"])
        }
    }
    @objc func loginMagicToken(_ call: CAPPluginCall) {
        guard let token = call.getString("token") else {
            call.reject("Missing token parameter")
            return
        }
        
        print("loginMagicToken received token")
        currentToken = token
        tokenWaitSemaphore?.signal()
        call.resolve()
    }
    
    private func setupTokenSubscription() {
        // Cancel existing subscription if any
        tokenSubscription?.cancel()
        
        // Create new subscription
        tokenSubscription = callKitPushNotificationAdapter.$deviceToken.sink { [weak self] (updatedDeviceToken: String) in
            guard let self = self else { return }
            Task {
                do {
                    print("Setting up token subscription")
                    try self.requireInitialized()
                    if let lastVoIPToken = self.lastVoIPToken, !lastVoIPToken.isEmpty {
                        print("Deleting device: \(lastVoIPToken)")
                        try await self.streamVideo?.deleteDevice(id: lastVoIPToken)
                    }
                    if !updatedDeviceToken.isEmpty {
                        print("Setting voip device: \(updatedDeviceToken)")
                        try await self.streamVideo?.setVoipDevice(id: updatedDeviceToken)
                        // Save the token to our secure storage
                        print("Saving voip token: \(updatedDeviceToken)")
                        SecureUserRepository.shared.save(voipPushToken: updatedDeviceToken)
                    }
                    self.lastVoIPToken = updatedDeviceToken
                } catch {
                    log.error("Error updating VOIP token: \(String(describing: error))")
                }
            }
        }
    }
    
    private func setupActiveCallSubscription() {
        if let streamVideo = streamVideo {
            Task {
                for await event in streamVideo.subscribe() {
                    print("Event", event)
                    if let ringingEvent = event.rawValue as? CallRingEvent {
                        notifyListeners("callEvent", data: [
                            "callId": ringingEvent.callCid,
                            "state": "ringing"
                        ])
                        return
                    }
                    notifyListeners("callEvent", data: [
                        "callId": streamVideo.state.activeCall?.callId ?? "",
                        "state": event.type
                    ])
                }
            }
        }
        // Cancel existing subscription if any
        activeCallSubscription?.cancel()
        // Create new subscription
        activeCallSubscription = streamVideo?.state.$activeCall.sink { [weak self] newState in
            guard let self = self else { return }
            Task { @MainActor in
                do {
                    try self.requireInitialized()
                    print("Call State Update:")
                    print("- Call is nil: \(newState == nil)")
                    if let state = newState?.state {
                        print("- state: \(state)")
                        print("- Session ID: \(state.sessionId)")
                        print("- All participants: \(String(describing: state.participants))")
                        print("- Remote participants: \(String(describing: state.remoteParticipants))")
                        
                        // Update overlay and make visible when there's an active call
                        self.overlayViewModel?.updateCall(newState)
                        self.overlayView?.isHidden = false
                        self.webView?.isOpaque = false
                        
                        // Notify that a call has started
                        self.notifyListeners("callEvent", data: [
                            "callId": newState?.cId ?? "",
                            "state": "joined"
                        ])
                    } else {
                        // If newState is nil, hide overlay and clear call
                        self.overlayViewModel?.updateCall(nil)
                        self.overlayView?.isHidden = true
                        self.webView?.isOpaque = true
                        
                        // Notify that call has ended
                        self.notifyListeners("callEvent", data: [
                            "callId": newState?.cId ?? "",
                            "state": "left"
                        ])
                    }
                } catch {
                    log.error("Error handling call state update: \(String(describing: error))")
                }
            }
        }
    }
    
    @objc func login(_ call: CAPPluginCall) {
        guard let token = call.getString("token"),
              let userId = call.getString("userId"),
              let name = call.getString("name"),
              let apiKey = call.getString("apiKey") else {
            call.reject("Missing required parameters")
            return
        }
        
        let imageURL = call.getString("imageURL")
        let user = User(
            id: userId,
            name: name,
            imageURL: imageURL.flatMap { URL(string: $0) },
            customData: [:]
        )
        
        let credentials = UserCredentials(user: user, tokenValue: token)
        SecureUserRepository.shared.save(user: credentials)
        // Initialize Stream Video with new credentials
        initializeStreamVideo()
        
        if state != .initialized {
            call.reject("Failed to initialize StreamVideo")
            return
        }
        
        // Update the CallOverlayView with new StreamVideo instance
        Task { @MainActor in
            self.overlayViewModel?.updateStreamVideo(self.streamVideo)
        }
        
        call.resolve([
            "success": true
        ])
    }
    
    @objc func logout(_ call: CAPPluginCall) {
        // Remove VOIP token from repository
        SecureUserRepository.shared.save(voipPushToken: nil)
        
        // Try to delete the device from Stream if we have the last token
        if let lastVoIPToken = lastVoIPToken {
            Task {
                do {
                    try await streamVideo?.deleteDevice(id: lastVoIPToken)
                } catch {
                    log.error("Error deleting device during logout: \(String(describing: error))")
                }
            }
        }
        
        // Cancel subscriptions
        tokenSubscription?.cancel()
        tokenSubscription = nil
        activeCallSubscription?.cancel()
        activeCallSubscription = nil
        lastVoIPToken = nil
        
        SecureUserRepository.shared.removeCurrentUser()
        
        // Update the CallOverlayView with nil StreamVideo instance
        Task { @MainActor in
            self.overlayViewModel?.updateCall(nil)
            self.overlayViewModel?.updateStreamVideo(nil)
            self.overlayView?.isHidden = true
            self.webView?.isOpaque = true
        }
        
        call.resolve([
            "success": true
        ])
    }
    
    @objc func isCameraEnabled(_ call: CAPPluginCall) {
        do {
            try requireInitialized()
        } catch {
            call.reject("SDK not initialized")
        }
        
        if let activeCall = streamVideo?.state.activeCall {
            call.resolve([
                "enabled": activeCall.camera.status == .enabled
            ])
        } else {
            call.reject("No active call")
        }
    }

    @objc func call(_ call: CAPPluginCall) {
        guard let members = call.getArray("userIds", String.self) else {
            call.reject("Missing required parameter: userIds (array of user IDs)")
            return
        }

        if members.isEmpty {
            call.reject("userIds array cannot be empty")
            return
        }

        // Initialize if needed
        if state == .notInitialized {
            guard let credentials = SecureUserRepository.shared.loadCurrentUser() else {
                call.reject("No user credentials found")
                return
            }
            initializeStreamVideo()
            if state != .initialized {
                call.reject("Failed to initialize StreamVideo")
                return
            }
        }

        do {
            try requireInitialized()

            let callType = call.getString("type") ?? "default"
            let shouldRing = call.getBool("ring") ?? true

            // Generate a unique call ID
            let callId = UUID().uuidString

            Task {
                do {
                    print("Creating call:")
                    print("- Call ID: \(callId)")
                    print("- Call Type: \(callType)")
                    print("- Users: \(members)")
                    print("- Should Ring: \(shouldRing)")

                    // Create the call object
                    let streamCall = streamVideo?.call(callType: callType, callId: callId)

                    // Start the call with the members
                    print("Creating call with members...")
                    try await streamCall?.create(
                        memberIds: members,
                        custom: [:],
                        ring: shouldRing
                    )

                    // Track participants responses
                    var participantResponses: [String: String] = [:]
                    let totalParticipants = members.count

                    func handleParticipantResponse(userId: String, response: String, eventType: String) async {
                        guard let streamCall = streamCall else { return }
                        participantResponses[userId] = response
                        print("Call was \(response) by user: \(userId) in call: \(callId) with event type: \(eventType)")
                        await MainActor.run {
                            self.notifyListeners("callEvent", data: [
                                "callId": callId,
                                "state": eventType,
                                "userId": userId
                            ])
                        }

                        // Check if all participants have rejected or missed
                        let allResponded = participantResponses.count == totalParticipants
                        let allRejectedOrMissed = participantResponses.values.allSatisfy { $0 == "rejected" || $0 == "missed" }
                        
                        if allResponded && allRejectedOrMissed {
                            print("All participants have rejected or missed the call")
                            streamCall.leave()
                            await MainActor.run {
                                self.overlayViewModel?.updateCall(nil)
                                self.overlayView?.isHidden = true
                                self.webView?.isOpaque = true
                                self.notifyListeners("callEvent", data: [
                                    "callId": callId,
                                    "state": "ended",
                                    "reason": "all_rejected_or_missed"
                                ])
                            }
                        }
                    }

                    // Set up event subscription
                    let eventTask = Task { [weak self] in
                        guard let self = self else { return }
                        guard let streamCall = streamCall else { return }
                        for await event in streamCall.subscribe() {
                            print("Received event:", event)
                            if let rejectedEvent = event.rawValue as? CallRejectedEvent {
                                await handleParticipantResponse(userId: rejectedEvent.user.id, response: "rejected", eventType: "rejected")
                            }
                        }
                    }

                    // Join the call
                    print("Joining call...")
                    try await streamCall?.join(create: false)
                    print("Successfully joined call")

                    // Cancel the event task since we successfully joined
                    eventTask.cancel()

                    // Update the CallOverlayView with the active call
                    await MainActor.run {
                        self.overlayViewModel?.updateCall(streamCall)
                        self.overlayView?.isHidden = false
                        self.webView?.isOpaque = false
                    }

                    call.resolve([
                        "success": true
                    ])
                } catch {
                    log.error("Error making call: \(String(describing: error))")
                    call.reject("Failed to make call: \(error.localizedDescription)")
                }
            }
        } catch {
            call.reject("StreamVideo not initialized")
        }
    }

    @objc func endCall(_ call: CAPPluginCall) {
        do {
            try requireInitialized()

            Task {
                do {
                    if let activeCall = streamVideo?.state.activeCall {
                        try await activeCall.leave()

                        // Update view state instead of cleaning up
                        await MainActor.run {
                            self.overlayViewModel?.updateCall(nil)
                            self.overlayView?.isHidden = true
                            self.webView?.isOpaque = true
                        }

                        call.resolve([
                            "success": true
                        ])
                    } else {
                        call.reject("No active call to end")
                    }
                } catch {
                    log.error("Error ending call: \(String(describing: error))")
                    call.reject("Failed to end call: \(error.localizedDescription)")
                }
            }
        } catch {
            call.reject("StreamVideo not initialized")
        }
    }

    @objc func setMicrophoneEnabled(_ call: CAPPluginCall) {
        guard let enabled = call.getBool("enabled") else {
            call.reject("Missing required parameter: enabled")
            return
        }

        do {
            try requireInitialized()

            Task {
                do {
                    if let activeCall = streamVideo?.state.activeCall {
                        if enabled {
                            try await activeCall.microphone.enable()
                        } else {
                            try await activeCall.microphone.disable()
                        }
                        call.resolve([
                            "success": true
                        ])
                    } else {
                        call.reject("No active call")
                    }
                } catch {
                    log.error("Error setting microphone: \(String(describing: error))")
                    call.reject("Failed to set microphone: \(error.localizedDescription)")
                }
            }
        } catch {
            call.reject("StreamVideo not initialized")
        }
    }

    @objc func setCameraEnabled(_ call: CAPPluginCall) {
        guard let enabled = call.getBool("enabled") else {
            call.reject("Missing required parameter: enabled")
            return
        }

        do {
            try requireInitialized()

            Task {
                do {
                    if let activeCall = streamVideo?.state.activeCall {
                        if enabled {
                            try await activeCall.camera.enable()
                        } else {
                            try await activeCall.camera.disable()
                        }
                        call.resolve([
                            "success": true
                        ])
                    } else {
                        call.reject("No active call")
                    }
                } catch {
                    log.error("Error setting camera: \(String(describing: error))")
                    call.reject("Failed to set camera: \(error.localizedDescription)")
                }
            }
        } catch {
            call.reject("StreamVideo not initialized")
        }
    }

    @objc func acceptCall(_ call: CAPPluginCall) {
        do {
            try requireInitialized()

            Task {
                do {

                    // Get the call object for the given ID
                    let streamCall = streamVideo?.state.ringingCall
                    if streamCall == nil {
                        call.reject("Failed to accept call as there is no ringing call")
                        return
                    }

                    // Join the call
                    print("Accepting and joining call \(streamCall!.cId)...")
                    try await streamCall?.accept()
                    try await streamCall?.join(create: false)
                    print("Successfully joined call")

                    // Update the CallOverlayView with the active call
                    await MainActor.run {
                        self.overlayViewModel?.updateCall(streamCall)
                        self.overlayView?.isHidden = false
                        self.webView?.isOpaque = false
                    }

                    call.resolve([
                        "success": true
                    ])
                } catch {
                    log.error("Error accepting call: \(String(describing: error))")
                    call.reject("Failed to accept call: \(error.localizedDescription)")
                }
            }
        } catch {
            call.reject("StreamVideo not initialized")
        }
    }

    private func initializeStreamVideo() {
        state = .initializing

        // Try to get user credentials from repository
        guard let savedCredentials = SecureUserRepository.shared.loadCurrentUser(),
              let apiKey = self.apiKey else {
            print("No saved credentials or API key found, skipping initialization")
            state = .notInitialized
            return
        }
        print("Initializing with saved credentials for user: \(savedCredentials.user.name)")

        self.streamVideo = StreamVideo(
            apiKey: apiKey,
            user: savedCredentials.user,
            token: UserToken(stringLiteral: savedCredentials.tokenValue)
        )

        state = .initialized
        callKitAdapter.streamVideo = self.streamVideo
        callKitAdapter.availabilityPolicy = .always

        // Setup subscriptions for new StreamVideo instance
        setupActiveCallSubscription()
        setupTokenSubscription()

        // Register for incoming calls
        callKitAdapter.registerForIncomingCalls()
    }

    private func setupViews() {
        guard let webView = self.webView,
              let parent = webView.superview else { return }

        // Create TouchInterceptView
        let touchInterceptView = TouchInterceptView(frame: parent.bounds)
        touchInterceptView.translatesAutoresizingMaskIntoConstraints = false
        self.touchInterceptView = touchInterceptView

        // Remove webView from its parent
        webView.removeFromSuperview()

        // Add TouchInterceptView to the parent
        parent.addSubview(touchInterceptView)

        // Setup TouchInterceptView constraints
        NSLayoutConstraint.activate([
            touchInterceptView.topAnchor.constraint(equalTo: parent.topAnchor),
            touchInterceptView.bottomAnchor.constraint(equalTo: parent.bottomAnchor),
            touchInterceptView.leadingAnchor.constraint(equalTo: parent.leadingAnchor),
            touchInterceptView.trailingAnchor.constraint(equalTo: parent.trailingAnchor)
        ])

        // Configure webview for transparency
        webView.isOpaque = true
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
        webView.translatesAutoresizingMaskIntoConstraints = false

        // Create SwiftUI view with view model
        let (hostingController, viewModel) = CallOverlayView.create(streamVideo: self.streamVideo)
        hostingController.view.backgroundColor = .clear
        hostingController.view.translatesAutoresizingMaskIntoConstraints = false

        self.hostingController = hostingController
        self.overlayViewModel = viewModel
        self.overlayView = hostingController.view

        if let overlayView = self.overlayView {
            // Setup the views in TouchInterceptView
            touchInterceptView.setupWithWebView(webView, overlayView: overlayView)

            // Setup constraints for webView
            NSLayoutConstraint.activate([
                webView.topAnchor.constraint(equalTo: touchInterceptView.topAnchor),
                webView.bottomAnchor.constraint(equalTo: touchInterceptView.bottomAnchor),
                webView.leadingAnchor.constraint(equalTo: touchInterceptView.leadingAnchor),
                webView.trailingAnchor.constraint(equalTo: touchInterceptView.trailingAnchor)
            ])

            // Setup constraints for overlayView
            let safeGuide = touchInterceptView.safeAreaLayoutGuide
            NSLayoutConstraint.activate([
                overlayView.topAnchor.constraint(equalTo: safeGuide.topAnchor),
                overlayView.bottomAnchor.constraint(equalTo: safeGuide.bottomAnchor),
                overlayView.leadingAnchor.constraint(equalTo: safeGuide.leadingAnchor),
                overlayView.trailingAnchor.constraint(equalTo: safeGuide.trailingAnchor)
            ])
        }
    }
}
