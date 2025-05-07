import UIKit

class TouchInterceptView: UIView {
    private weak var webView: UIView?
    private weak var overlayView: UIView?
    
    func setupWithWebView(_ webView: UIView, overlayView: UIView) {
        self.webView = webView
        self.overlayView = overlayView
        
        // Ensure this view is transparent and doesn't interfere with display
        self.backgroundColor = .clear
        self.isOpaque = false
        // print("TouchInterceptView setup with webView: \(webView) and overlayView: \(overlayView)")
    }
    
    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        guard let webView = webView, let overlayView = overlayView, overlayView.isHidden == false else {
            // print("hitTest: Conditions not met - webView: \(webView?.description ?? "nil"), overlayView: \(overlayView?.description ?? "nil"), overlayHidden: \(overlayView?.isHidden ?? true)")
            return super.hitTest(point, with: event)
        }
        
        // First, check if the webview has a specific subview to interact with at this point
        let webViewHit = webView.hitTest(point, with: event)
        if webViewHit != nil && webViewHit != webView {
            // Check if the hit view is a specific interactive element (like a button)
            if webViewHit is UIButton || webViewHit?.accessibilityTraits.contains(.button) == true {
                // print("hitTest: WebView interactive element hit at point \(point) - returning \(webViewHit?.description ?? "nil")")
                return webViewHit
            } else {
                // print("hitTest: WebView hit at point \(point) but not an interactive element - passing through")
            }
        } else {
            // print("hitTest: No WebView hit at point \(point) - passing through")
        }
        
        // If no specific interactive element in webview is hit, pass through to overlay (StreamCall UI)
        let overlayHit = overlayView.hitTest(point, with: event)
        if overlayHit != nil {
            // print("hitTest: Overlay hit at point \(point) - returning \(overlayHit?.description ?? "nil")")
            return overlayHit
        }
        
        // If neither webview nor overlay handles the touch, return nil to pass through
        // print("hitTest: No hit at point \(point) - returning nil")
        return nil
    }
    
    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        guard let webView = webView, let overlayView = overlayView, overlayView.isHidden == false else {
            // print("point(inside:with): Conditions not met - webView: \(webView?.description ?? "nil"), overlayView: \(overlayView?.description ?? "nil"), overlayHidden: \(overlayView?.isHidden ?? true)")
            return false
        }
        // Check if webview has content at this point
        if webView.point(inside: point, with: event) {
            // If webview claims the point, check for interactive elements in hitTest
            // print("point(inside:with): WebView claims point \(point) - will check for interactive elements")
            return true
        }
        // Otherwise, allow touches to pass through to the overlay if relevant
        if overlayView.point(inside: point, with: event) {
            // print("point(inside:with): Overlay claims point \(point)")
            return true
        }
        // print("point(inside:with): No view claims point \(point)")
        return false
    }
} 
