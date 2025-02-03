import Foundation

@objc public class StreamCall: NSObject {
    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }
}
