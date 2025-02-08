//
//  UserRepository.swift
//  Pods
//
//  Created by Michał Tremblay on 07/02/2025.
//

import Security
import StreamVideo

struct UserCredentials: Identifiable, Codable {
    var id: String {
        user.id
    }
    let user: User
    let tokenValue: String
}

protocol UserRepository {
    
    func save(user: UserCredentials)
    
    func loadCurrentUser() -> UserCredentials?
    
    func removeCurrentUser()
    
    func save(token: String)
    
}

protocol VoipTokenHandler {
    
    func save(voipPushToken: String?)
    
    func currentVoipPushToken() -> String?
    
}

class SecureUserRepository: UserRepository, VoipTokenHandler {
    
    static let shared = SecureUserRepository()
    
    private let serviceIdentifier = "stream.video.keychain"
    private let userKey = "stream.video.user"
    private let tokenKey = "stream.video.token"
    private let voipPushTokenKey = "stream.video.voip.token"
    
    private init() {}
    
    private func save(data: Data, forKey key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceIdentifier,
            kSecAttrAccount as String: key,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock // More permissive access
        ]
        
        // First try to delete any existing item
        SecItemDelete(query as CFDictionary)
        
        // Then add the new item
        let status = SecItemAdd(query as CFDictionary, nil)
        if status != errSecSuccess {
            switch status {
            case errSecDuplicateItem:
                log.error("Keychain error: Item already exists")
            case errSecItemNotFound:
                log.error("Keychain error: Item not found")
            case errSecNotAvailable:
                log.error("Keychain error: Keychain not available")
            case errSecDenied:
                log.error("Keychain error: Access denied - check entitlements")
            default:
                log.error("Keychain error: Unhandled error \(status)")
            }
        }
    }
    
    private func loadData(forKey key: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceIdentifier,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
        ]
        
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        
        if status != errSecSuccess && status != errSecItemNotFound {
            switch status {
            case errSecNotAvailable:
                log.error("Keychain error: Keychain not available")
            case errSecDenied:
                log.error("Keychain error: Access denied - check entitlements")
            default:
                log.error("Keychain error: Unhandled error \(status)")
            }
        }
        
        return result as? Data
    }
    
    private func deleteData(forKey key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceIdentifier,
            kSecAttrAccount as String: key
        ]
        
        SecItemDelete(query as CFDictionary)
    }
    
    func save(user: UserCredentials) {
        let encoder = JSONEncoder()
        if let encoded = try? encoder.encode(user.user) {
            save(data: encoded, forKey: userKey)
            if let tokenData = user.tokenValue.data(using: .utf8) {
                save(data: tokenData, forKey: tokenKey)
            }
        }
    }
    
    func save(token: String) {
        if let tokenData = token.data(using: .utf8) {
            save(data: tokenData, forKey: tokenKey)
        }
    }
    
    func loadCurrentUser() -> UserCredentials? {
        guard let userData = loadData(forKey: userKey),
              let tokenData = loadData(forKey: tokenKey),
              let tokenString = String(data: tokenData, encoding: .utf8) else {
            return nil
        }
        
        let decoder = JSONDecoder()
        do {
            let loadedUser = try decoder.decode(User.self, from: userData)
            return UserCredentials(user: loadedUser, tokenValue: tokenString)
        } catch {
            log.error("Error while decoding user: \(String(describing: error))")
            return nil
        }
    }
    
    func save(voipPushToken: String?) {
        if let token = voipPushToken,
           let tokenData = token.data(using: .utf8) {
            save(data: tokenData, forKey: voipPushTokenKey)
        } else {
            deleteData(forKey: voipPushTokenKey)
        }
    }
    
    func currentVoipPushToken() -> String? {
        guard let tokenData = loadData(forKey: voipPushTokenKey) else {
            return nil
        }
        return String(data: tokenData, encoding: .utf8)
    }
    
    func removeCurrentUser() {
        deleteData(forKey: userKey)
        deleteData(forKey: tokenKey)
        deleteData(forKey: voipPushTokenKey)
    }
}

