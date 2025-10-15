//
//  UserRepository.swift
//  Pods
//
//  Created by Michał Tremblay on 07/02/2025.
//

import Security
import StreamVideo
import Foundation

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

// NOTE: This is just for simplicity. User data shouldn't be kept in `UserDefaults`.
// NOTE: This is just for simplicity. User data shouldn't be kept in `UserDefaults`.
class SecureUserRepository: UserRepository, VoipTokenHandler {

    private let defaults = UserDefaults.standard
    private let userKey = "stream.video.user"
    private let tokenKey = "stream.video.token"
    private let chatTokenKey = "stream.chat.token"
    private let voipPushTokenKey = "stream.video.voip.token"

    static let shared = SecureUserRepository()

    private init() {}

    func save(user: UserCredentials) {
        print("SecureUserRepository: Saving user credentials for: \(user.user.id)")
        let encoder = JSONEncoder()
        if let encoded = try? encoder.encode(user.user) {
            defaults.set(encoded, forKey: userKey)
            defaults.set(user.tokenValue, forKey: tokenKey)
            print("SecureUserRepository: User credentials saved successfully for: \(user.user.id)")
        } else {
            print("SecureUserRepository: Failed to encode user data for: \(user.user.id)")
        }
    }

    func save(token: String) {
        defaults.set(token, forKey: tokenKey)
    }

    func loadCurrentUser() -> UserCredentials? {
        print("SecureUserRepository: Loading current user credentials")

        if let savedUser = defaults.object(forKey: userKey) as? Data {
            let decoder = JSONDecoder()
            do {
                let loadedUser = try decoder.decode(User.self, from: savedUser)
                guard let tokenValue = defaults.value(forKey: tokenKey) as? String else {
                    print("SecureUserRepository: User data found but no token")
                    throw ClientError.Unexpected()
                }
                let credentials = UserCredentials(user: loadedUser, tokenValue: tokenValue)
                print("SecureUserRepository: Successfully loaded credentials for user: \(loadedUser.id)")
                return credentials
            } catch {
                print("SecureUserRepository: Error while decoding user: \(String(describing: error))")
                log.error("Error while decoding user: \(String(describing: error))")
            }
        } else {
            print("SecureUserRepository: No stored user data found")
        }
        return nil
    }

    func save(voipPushToken: String?) {
        defaults.set(voipPushToken, forKey: voipPushTokenKey)
    }

    func currentVoipPushToken() -> String? {
        defaults.value(forKey: voipPushTokenKey) as? String
    }

    func removeCurrentUser() {
        print("SecureUserRepository: Removing current user credentials")
        defaults.set(nil, forKey: userKey)
        defaults.set(nil, forKey: tokenKey)
        defaults.set(nil, forKey: voipPushTokenKey)
        print("SecureUserRepository: User credentials removed successfully")
    }

}
