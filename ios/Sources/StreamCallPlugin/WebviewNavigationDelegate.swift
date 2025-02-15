import Foundation
import WebKit

class WebviewNavigationDelegate: NSObject, WKNavigationDelegate {
    private let wrappedDelegate: WKNavigationDelegate?
    private let onSetupOverlay: () -> Void
    
    init(wrappedDelegate: WKNavigationDelegate?, onSetupOverlay: @escaping () -> Void) {
        self.wrappedDelegate = wrappedDelegate
        self.onSetupOverlay = onSetupOverlay
        super.init()
    }
    
    public func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        // Then forward to the original delegate
        wrappedDelegate?.webView?(webView, didFinish: navigation)
        
        // Call our custom setup
        onSetupOverlay()
    }
    
    // Forward all other WKNavigationDelegate methods
    
    public func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        if let result = wrappedDelegate?.webView?(webView, decidePolicyFor: navigationAction, decisionHandler: decisionHandler) {
            return result
        }
        decisionHandler(.allow)
    }
    
    public func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse, decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
        if let result = wrappedDelegate?.webView?(webView, decidePolicyFor: navigationResponse, decisionHandler: decisionHandler) {
            return result
        }
        decisionHandler(.allow)
    }
    
    public func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        wrappedDelegate?.webView?(webView, didStartProvisionalNavigation: navigation)
    }
    
    public func webView(_ webView: WKWebView, didReceiveServerRedirectForProvisionalNavigation navigation: WKNavigation!) {
        wrappedDelegate?.webView?(webView, didReceiveServerRedirectForProvisionalNavigation: navigation)
    }
    
    public func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        wrappedDelegate?.webView?(webView, didFailProvisionalNavigation: navigation, withError: error)
    }
    
    public func webView(_ webView: WKWebView, didCommit navigation: WKNavigation!) {
        wrappedDelegate?.webView?(webView, didCommit: navigation)
    }
    
    public func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        wrappedDelegate?.webView?(webView, didFail: navigation, withError: error)
    }
    
    public func webView(_ webView: WKWebView, didReceive challenge: URLAuthenticationChallenge, completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        if let result = wrappedDelegate?.webView?(webView, didReceive: challenge, completionHandler: completionHandler) {
            return result
        }
        completionHandler(.performDefaultHandling, nil)
    }
    
    public func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        wrappedDelegate?.webViewWebContentProcessDidTerminate?(webView)
    }
}
