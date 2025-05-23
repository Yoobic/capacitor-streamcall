import UIKit
import os.log
import WebKit

class TouchInterceptView: UIView {
    private weak var webView: UIView?
    private weak var overlayView: UIView?
    private var touchStartPoint: CGPoint?
    
    func setupWithWebView(_ webView: UIView, overlayView: UIView) {
        self.webView = webView
        self.overlayView = overlayView
        
        // Ensure this view is transparent and doesn't interfere with display
        self.backgroundColor = .clear
        self.isOpaque = false
        os_log(.debug, "TouchInterceptView: setupWithWebView - webView: %{public}s, overlayView: %{public}s", String(describing: webView), String(describing: overlayView))
    }
    
    private func isInteractive(_ view: UIView) -> Bool {
        if view is UIControl { return true }
        if let grs = view.gestureRecognizers, !grs.isEmpty { return true }
        return false
    }

    private func nonGreedyInteractiveHitTest(in view: UIView, point: CGPoint, with event: UIEvent?) -> UIView? {
        // Traverse subviews in reverse order (frontmost first)
        for subview in view.subviews.reversed() where subview.isUserInteractionEnabled && !subview.isHidden && subview.alpha >= 0.01 {
            let converted = view.convert(point, to: subview)
            if subview.point(inside: converted, with: event) {
                if let hit = nonGreedyInteractiveHitTest(in: subview, point: converted, with: event) {
                    return hit
                }
            }
        }
        // If no subview handled, return view itself only if truly interactive
        return isInteractive(view) && view.point(inside: point, with: event) ? view : nil
    }

    private func forwardClickToWeb(at pointInSelf: CGPoint) {
        guard let wk = webView as? WKWebView else { return }
        let locationInWeb = self.convert(pointInSelf, to: wk)
        let x = Int(locationInWeb.x)
        let y = Int(locationInWeb.y)
        let js = """
        (() => {
            console.log('forwardClickToWeb', \(x), \(y));
            const x = \(x); const y = \(y);
            const el = document.elementFromPoint(x, y);
            if (!el) return 'NO_ELEM';
            const eventInit = { bubbles: true, cancelable: true, clientX: x, clientY: y };
            const touchInit = { bubbles: true, cancelable: true, touches: [{ clientX: x, clientY: y }], targetTouches: [], changedTouches: [], shiftKey: false };
            const seq = [];
            try {
                seq.push(new TouchEvent('touchstart', touchInit));
            } catch(e) { console.log('TouchEvent not supported', e); }
            seq.push(new PointerEvent('pointerdown', { ...eventInit, pointerType: 'touch' }));
            seq.push(new MouseEvent('mousedown', eventInit));
            try {
                seq.push(new TouchEvent('touchend', touchInit));
            } catch(e) { }
            seq.push(new PointerEvent('pointerup', { ...eventInit, pointerType: 'touch' }));
            seq.push(new MouseEvent('mouseup', eventInit));
            seq.push(new MouseEvent('click', eventInit));
            seq.forEach(evt => el.dispatchEvent(evt));
            console.log('SyntheticClick seq on', el);
            return el.tagName;
        })();
        """
        os_log(.debug, "TouchInterceptView: forwardClickToWeb - (%{public}d,%{public}d)", x, y)
        wk.evaluateJavaScript(js) { result, error in
            if let error = error {
                os_log(.error, "TouchInterceptView: JS error %{public}s", String(describing: error))
            } else {
                os_log(.debug, "TouchInterceptView: JS returned %{public}s", String(describing: result))
            }
        }
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        os_log(.debug, "TouchInterceptView: touchesBegan at %{public}s", String(describing: touches))
        if let touch = touches.first {
            touchStartPoint = touch.location(in: self)
            os_log(.debug, "TouchInterceptView: touchesBegan at %{public}s", String(describing: touchStartPoint))
        }
        super.touchesBegan(touches, with: event)
    }
    
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        os_log(.debug, "TouchInterceptView: touchesEnded at %{public}s", String(describing: touches))
        if let startPoint = touchStartPoint {
            os_log(.debug, "TouchInterceptView: touchesEnded - forwarding click to web at %{public}s", String(describing: startPoint))
            forwardClickToWeb(at: startPoint)
        }
        touchStartPoint = nil
        super.touchesEnded(touches, with: event)
    }
    
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        os_log(.debug, "TouchInterceptView: touchesCancelled at %{public}s", String(describing: touches))
        touchStartPoint = nil
        super.touchesCancelled(touches, with: event)
    }
} 
