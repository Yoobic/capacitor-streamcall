import Foundation
import Capacitor
import StreamVideo
import StreamVideoSwiftUI
import SwiftUI
import Combine

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
        CAPPluginMethod(name: "initialize", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "login", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "logout", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "call", returnType: CAPPluginReturnPromise)
    ]
    
    private enum State {
        case notInitialized
        case initializing
        case initialized
    }
    
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
    
    @Injected(\.streamVideo) var streamVideo {
        willSet {
            // Clean up existing subscriptions when streamVideo changes
            tokenSubscription?.cancel()
            activeCallSubscription?.cancel()
        }
    }
    
    @Injected(\.callKitAdapter) var callKitAdapter
    @Injected(\.callKitPushNotificationAdapter) var callKitPushNotificationAdapter
    
    override public func load() {
        // Check if we have a logged in user
        if let credentials = SecureUserRepository.shared.loadCurrentUser() {
            print("Loading user for StreamCallPlugin: \(credentials.user.name)")
            // Initialize Stream Video client with stored credentials
        }
        DispatchQueue.global(qos: .userInitiated).async {
            self.initializeStreamVideo()
        }
        
        // self.webView?.evaluateJavaScript("console.log('abca');")
    }
    
    private func requireInitialized() throws {
        guard state == .initialized else {
            throw NSError(domain: "StreamCallPlugin", code: -1, userInfo: [NSLocalizedDescriptionKey: "StreamVideo not initialized"])
        }
    }
    
    private func refreshToken() throws -> UserToken {
        // Acquire the semaphore in a thread-safe way
        StreamCallPlugin.tokenRefreshQueue.sync {
            StreamCallPlugin.tokenRefreshSemaphore.wait()
        }
        
        defer {
            // Always release the semaphore when we're done, even if we throw an error
            StreamCallPlugin.tokenRefreshSemaphore.signal()
        }
        
        // Clear current token
        currentToken = nil
        
        // Create a local semaphore for waiting on the token
        let localSemaphore = DispatchSemaphore(value: 0)
        tokenWaitSemaphore = localSemaphore
        
        // Capture webView before async context
        guard let webView = self.webView else {
            print("WebView not available")
            tokenWaitSemaphore = nil
            throw NSError(domain: "StreamCallPlugin", code: -1, userInfo: [NSLocalizedDescriptionKey: "WebView not available"])
        }
        
        let script = """
            (function() {
                console.log('Starting token refresh...');
                fetch('https://magic-login-srv-35.localcan.dev/user?user_id=user1', {
                    headers: {
                        'magic-shit': 'yes'
                    }
                })
                .then(response => {
                    console.log('Got response:', response.status);
                    return response.json();
                })
                .then(data => {
                    console.log('Got data, token length:', data.token?.length);
                    const tokenA = data.token;
                    window.Capacitor.Plugins.StreamCall.loginMagicToken({
                        token: tokenA
                    });
                })
                .catch(error => {
                    console.error('Token refresh error:', error);
                });
            })();
        """
        
        if Thread.isMainThread {
            print("Executing script on main thread")
            webView.evaluateJavaScript(script) { result, error in
                if let error = error {
                    print("JavaScript evaluation error: \(error)")
                } else {
                    print("JavaScript executed successfully: \(String(describing: result))")
                }
            }
        } else {
            print("Executing script from background thread")
            DispatchQueue.main.sync {
                webView.evaluateJavaScript(script) { result, error in
                    if let error = error {
                        print("JavaScript evaluation error: \(error)")
                    } else {
                        print("JavaScript executed successfully: \(String(describing: result))")
                    }
                }
            }
        }
        
        // Set up a timeout
        let timeoutQueue = DispatchQueue.global()
        let timeoutWork = DispatchWorkItem {
            print("Token refresh timed out")
            self.tokenWaitSemaphore?.signal()
            self.tokenWaitSemaphore = nil
        }
        timeoutQueue.asyncAfter(deadline: .now() + 10.0, execute: timeoutWork) // 10 second timeout
        
        // Wait for token to be set via loginMagicToken or timeout
        localSemaphore.wait()
        timeoutWork.cancel()
        tokenWaitSemaphore = nil
        
        guard let token = currentToken else {
            print("Failed to get token")
            throw NSError(domain: "StreamCallPlugin", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to get token or timeout occurred"])
        }
        
        // Save the token
        SecureUserRepository.shared.save(token: token)
        
        print("Got the token!!!")
        return UserToken(stringLiteral: token)
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
                    try self.requireInitialized()
                    if let lastVoIPToken = self.lastVoIPToken, !lastVoIPToken.isEmpty {
                        try await self.streamVideo.deleteDevice(id: lastVoIPToken)
                    }
                    if !updatedDeviceToken.isEmpty {
                        try await self.streamVideo.setVoipDevice(id: updatedDeviceToken)
                        // Save the token to our secure storage
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
        // Cancel existing subscription if any
        activeCallSubscription?.cancel()
        
        // Create new subscription
        activeCallSubscription = streamVideo.state.$activeCall.sink { [weak self] newState in
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
                        
                        // Notify that a call has started
                        self.notifyListeners("callStarted", data: [
                            "callId": newState?.cId ?? ""
                        ])
                    } else {
                        // If newState is nil, it means the call has ended
                        self.notifyListeners("callEnded", data: [:])
                    }
                    
                    self.overlayViewModel?.updateCall(newState)
                    self.overlayView?.isHidden = newState == nil
                } catch {
                    log.error("Error handling call state update: \(String(describing: error))")
                }
            }
        }
    }
    
    @objc func initialize(_ call: CAPPluginCall) {
        Task { @MainActor in
            // Make webview transparent
            self.webView?.isOpaque = false
            self.webView?.backgroundColor = .clear
            self.webView?.scrollView.backgroundColor = .clear
            
            // Check if we have a logged in user
            self.initializeStreamVideo()
            
            if (self.state != .initialized) {
                call.reject("The SDK is not initialized")
                return
            }

            
            // Create SwiftUI view with view model
            let (hostingController, viewModel) = CallOverlayView.create(streamVideo: self.streamVideo)
            hostingController.view.backgroundColor = .clear
            
            self.hostingController = hostingController
            self.overlayViewModel = viewModel
            self.overlayView = hostingController.view
            
            if let overlayView = self.overlayView {
                // Add to view hierarchy below webview but keep it hidden
                overlayView.isHidden = true
                self.webView?.superview?.addSubview(overlayView)
                self.webView?.superview?.bringSubviewToFront(self.webView!)
                
                // Setup constraints
                overlayView.translatesAutoresizingMaskIntoConstraints = false
                NSLayoutConstraint.activate([
                    overlayView.topAnchor.constraint(equalTo: overlayView.superview!.topAnchor),
                    overlayView.bottomAnchor.constraint(equalTo: overlayView.superview!.bottomAnchor),
                    overlayView.leadingAnchor.constraint(equalTo: overlayView.superview!.leadingAnchor),
                    overlayView.trailingAnchor.constraint(equalTo: overlayView.superview!.trailingAnchor)
                ])
            }
            
            call.resolve()
        }
    }
    
    private func initializeStreamVideo() {
//        if (state == .initializing) {
//            return
//        }
        state = .initializing
        
        // Try to get user credentials from repository
        guard let savedCredentials = SecureUserRepository.shared.loadCurrentUser() else {
            print("No saved credentials found, skipping initialization")
            state = .notInitialized
            return
        }
        print("Initializing with saved credentials for user: \(savedCredentials.user.name)")
        
        // Create a local reference to refreshToken to avoid capturing self
        let refreshTokenFn = self.refreshToken
        
        self.streamVideo = StreamVideo(
            apiKey: "n8wv8vjmucdw",
            user: savedCredentials.user,
            token: UserToken(stringLiteral: savedCredentials.tokenValue),
            tokenProvider: { result in
                print("attempt to refresh")
                DispatchQueue.global().async {
                    do {
                        let newToken = try refreshTokenFn()
                        print("Refresh successful")
                        result(.success(newToken))
                    } catch {
                        print("Refresh fail")
                        result(.failure(error))
                    }
                }
            }
        )
        
        state = .initialized
        callKitAdapter.streamVideo = self.streamVideo
        
        // Setup subscriptions for new StreamVideo instance
        setupActiveCallSubscription()
        setupTokenSubscription()
        
        // Register for incoming calls
        callKitAdapter.registerForIncomingCalls()
    }
    
    @objc func login(_ call: CAPPluginCall) {
        guard let token = call.getString("token"),
              let userId = call.getString("userId"),
              let name = call.getString("name") else {
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
        // this setsup callkit
        initializeStreamVideo()
        
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
                    try await streamVideo.deleteDevice(id: lastVoIPToken)
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
            self.overlayViewModel?.updateStreamVideo(nil)
        }
        
        call.resolve([
            "success": true
        ])
    }
    
    @objc func call(_ call: CAPPluginCall) {
        guard let userId = call.getString("userId") else {
            call.reject("Missing required parameter: userId")
            return
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
                    print("- User ID: \(userId)")
                    print("- Should Ring: \(shouldRing)")
                    
                    // Create the call object
                    let streamCall = streamVideo.call(callType: callType, callId: callId)
                    
                    // Start the call with the member
                    print("Creating call with member...")
                    try await streamCall.create(
                        memberIds: [userId],
                        custom: [:],
                        ring: shouldRing
                    )
                    
                    // Join the call
                    print("Joining call...")
                    try await streamCall.join(create: false)
                    print("Successfully joined call")
                    
                    // Update the CallOverlayView with the active call
                    await MainActor.run {
                        self.overlayViewModel?.updateCall(streamCall)
                        self.overlayView?.isHidden = false
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
}
