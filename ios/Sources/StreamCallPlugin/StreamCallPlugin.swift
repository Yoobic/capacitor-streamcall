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
        CAPPluginMethod(name: "echo", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "initialize", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "login", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "logout", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "call", returnType: CAPPluginReturnPromise)
    ]
    
    private var overlayView: UIView?
    private var hostingController: UIHostingController<CallOverlayView>?
    private var overlayViewModel: CallOverlayViewModel?
    private var tokenSubscription: AnyCancellable?
    private var activeCallSubscription: AnyCancellable?
    private var lastVoIPToken: String?
    
    @Injected(\.streamVideo) var streamVideo
    @Injected(\.callKitAdapter) var callKitAdapter
    @Injected(\.callKitPushNotificationAdapter) var callKitPushNotificationAdapter
    
    override public func load() {
        // Check if we have a logged in user
        if let credentials = SecureUserRepository.shared.loadCurrentUser() {
            print("Loading user for StreamCallPlugin: \(credentials.user.name)")
            // Initialize Stream Video client with stored credentials
            initializeStreamVideo(with: credentials)
        }
        
    }
    
    private func setupTokenSubscription() {
        // Cancel existing subscription if any
        tokenSubscription?.cancel()
        
        // Create new subscription
        tokenSubscription = callKitPushNotificationAdapter.$deviceToken.sink { [weak self] (updatedDeviceToken: String) in
            guard let self = self else { return }
            Task {
                do {
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
                print("Call State Update:")
                print("- Call is nil: \(newState == nil)")
                if let state = newState?.state {
                    // print("- Call ID: \(newState?.id ?? "unknown")")
                    // print("- Call Type: \(state.callType)")
                    // print("- Call Status: \(state.status)")
                    print("- state: \(state)")
                    print("- Session ID: \(state.sessionId)")
                    print("- All participants: \(String(describing: state.participants))")
                    print("- Remote participants: \(String(describing: state.remoteParticipants))")
                }
                self.overlayViewModel?.updateCall(newState)
                self.overlayView?.isHidden = newState == nil
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
            if let credentials = SecureUserRepository.shared.loadCurrentUser() {
                // Initialize Stream Video client with stored credentials
                self.initializeStreamVideo(with: credentials)
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
    
    private func initializeStreamVideo(with credentials: UserCredentials) {
        streamVideo = StreamVideo(
            apiKey: "n8wv8vjmucdw",
            user: credentials.user,
            token: .init(stringLiteral: credentials.tokenValue)
        )
        
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
        initializeStreamVideo(with: credentials)
        
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
    }
}
