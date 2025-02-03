import Foundation
import SwiftUI
import Capacitor

@objc public class StreamCall: NSObject {
    private weak var bridge: CAPBridgeProtocol?
    private var overlayWindow: UIWindow?
    
    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }
    
    @objc public func initialize() {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            
            // Make the webview transparent
            if let webView = bridge?.webView {
                webView.isOpaque = false
                webView.backgroundColor = .clear
                webView.scrollView.backgroundColor = .clear
                webView.superview?.backgroundColor = .clear
            }
            
            // Create window that will host our SwiftUI view
            let window = UIWindow(frame: UIScreen.main.bounds)
            window.windowLevel = .normal - 1 // Place it below the main window
            window.backgroundColor = .clear
            
            // Create and set the SwiftUI view
            let overlayView = CallOverlayView()
            let hostingController = UIHostingController(rootView: overlayView)
            hostingController.view.backgroundColor = .clear
            window.rootViewController = hostingController
            
            // Show the window
            window.isHidden = false
            self.overlayWindow = window
        }
    }
    
    @objc public func setBridge(_ bridge: CAPBridgeProtocol) {
        self.bridge = bridge
    }
}
