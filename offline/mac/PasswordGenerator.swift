import Foundation
import Security

enum PasswordGenerator {
    static let alphabet = Array("ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*+-_=?.")

    static func generate(length: Int) throws -> String {
        guard length >= 12 && length <= 128 else {
            throw Fra1Error.invalid("La password deve essere lunga tra 12 e 128 caratteri")
        }
        guard !alphabet.isEmpty && alphabet.count <= 256 else {
            throw Fra1Error.invalid("Alfabeto password non valido")
        }

        let limit = 256 - (256 % alphabet.count)
        var result = ""
        result.reserveCapacity(length)

        while result.count < length {
            var byte: UInt8 = 0
            let status = SecRandomCopyBytes(kSecRandomDefault, 1, &byte)
            guard status == errSecSuccess else {
                throw Fra1Error.invalid("Generatore casuale sicuro non disponibile")
            }
            if Int(byte) >= limit { continue }
            result.append(alphabet[Int(byte) % alphabet.count])
        }
        return result
    }
}
