import Foundation
import Security

enum FamilyCredentials {
    private static let service = "com.starception.family.login"
    static let deviceID: String = {
        if let existing = UserDefaults.standard.string(forKey: "familyDeviceID") { return existing }
        let value = UUID().uuidString
        UserDefaults.standard.set(value, forKey: "familyDeviceID")
        return value
    }()
    static func save(jid: String, password: String) {
        guard let data = try? JSONSerialization.data(withJSONObject: ["jid": jid, "password": password]) else { return }
        clear()
        SecItemAdd([kSecClass: kSecClassGenericPassword, kSecAttrService: service,
                    kSecAttrAccount: "current", kSecValueData: data,
                    kSecAttrAccessible: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly] as CFDictionary, nil)
    }
    static func load() -> (String, String)? {
        var item: CFTypeRef?
        let status = SecItemCopyMatching([kSecClass: kSecClassGenericPassword,
            kSecAttrService: service, kSecAttrAccount: "current", kSecReturnData: true,
            kSecMatchLimit: kSecMatchLimitOne] as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data,
              let value = try? JSONSerialization.jsonObject(with: data) as? [String: String],
              let jid = value["jid"], let password = value["password"] else { return nil }
        return (jid, password)
    }
    static func clear() {
        SecItemDelete([kSecClass: kSecClassGenericPassword, kSecAttrService: service,
                       kSecAttrAccount: "current"] as CFDictionary)
    }
}
