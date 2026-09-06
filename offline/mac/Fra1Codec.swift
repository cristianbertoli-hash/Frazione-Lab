import Foundation
import CryptoKit
import Security

@_silgen_name("fra1_pbkdf2_sha256")
private func fra1_pbkdf2_sha256(
    _ password: UnsafePointer<UInt8>,
    _ passwordLen: Int,
    _ salt: UnsafePointer<UInt8>,
    _ saltLen: Int,
    _ rounds: UInt32,
    _ output: UnsafeMutablePointer<UInt8>,
    _ outputLen: Int
) -> Int32

enum Fra1Error: LocalizedError {
    case invalid(String)

    var errorDescription: String? {
        switch self {
        case .invalid(let message): return message
        }
    }
}

struct Fra1OriginalFile {
    let name: String
    let mime: String
    let bytes: Data
    let sha256: String
}

struct Fra1ContainerInfo {
    let kind: String
    let encrypted: Bool
    let keyBits: Int
    let iterations: Int
    let bytes: Int
}

enum Fra1Codec {
    static let defaultIterations = 600_000

    private static let magic = Data([0x46, 0x52, 0x41, 0x31]) // FRA1
    private static let magicE = Data([0x46, 0x52, 0x41, 0x31, 0x45]) // FRA1E
    private static let typeText: UInt8 = 1
    private static let typeOriginal: UInt8 = 3
    private static let headerSize = 9
    private static let metaSize = 40
    private static let encryptedHeaderSize = 20
    private static let encryptedVersion: UInt8 = 1
    private static let algorithmAESGCM: UInt8 = 1
    private static let kdfPBKDF2SHA256: UInt8 = 1
    private static let saltLength = 16
    private static let ivLength = 12
    private static let tagLength = 16

    static func encodeTextFraction(_ text: String) throws -> String {
        let packet = makePacket(type: typeText, payload: Data(text.utf8))
        let n = decimalString(from: packet)
        return "\(n) / 2^\(packet.count * 8)"
    }

    static func decodeTextFraction(_ fraction: String) throws -> String {
        let pattern = #"^\s*(\d+)\s*/\s*2\^(\d+)\s*$"#
        let regex = try NSRegularExpression(pattern: pattern)
        let range = NSRange(fraction.startIndex..<fraction.endIndex, in: fraction)
        guard let match = regex.firstMatch(in: fraction, range: range), match.numberOfRanges == 3,
              let nRange = Range(match.range(at: 1), in: fraction),
              let kRange = Range(match.range(at: 2), in: fraction),
              let exponent = Int(fraction[kRange]), exponent > 0, exponent % 8 == 0 else {
            throw Fra1Error.invalid("Frazione non valida. Usa il formato N / 2^K")
        }
        let packet = try dataFromDecimal(String(fraction[nRange]), exactLength: exponent / 8)
        try validateHeader(packet, expectedType: typeText)
        let payloadLength = try readUInt32(packet, at: 5)
        guard payloadLength == packet.count - headerSize else {
            throw Fra1Error.invalid("Lunghezza FRA1 non valida")
        }
        let payload = packet.subdata(in: headerSize..<packet.count)
        guard let text = String(data: payload, encoding: .utf8) else {
            throw Fra1Error.invalid("Testo UTF-8 non valido")
        }
        return text
    }

    static func packOriginalFile(name: String, mime: String, bytes: Data) throws -> Data {
        let safeName = name.isEmpty ? "file.bin" : name
        let safeMime = mime.isEmpty ? "application/octet-stream" : mime
        let nameData = Data(safeName.utf8)
        let mimeData = Data(safeMime.utf8)
        guard nameData.count <= 0xffff, mimeData.count <= 0xffff else {
            throw Fra1Error.invalid("Metadati troppo lunghi")
        }
        guard bytes.count <= Int(UInt32.max) else {
            throw Fra1Error.invalid("File troppo grande")
        }

        let hash = Data(SHA256.hash(data: bytes))
        let payloadLength = metaSize + nameData.count + mimeData.count + bytes.count
        guard payloadLength <= Int(UInt32.max) else {
            throw Fra1Error.invalid("File troppo grande")
        }

        var out = Data()
        out.append(magic)
        out.append(typeOriginal)
        appendUInt32(UInt32(payloadLength), to: &out)
        appendUInt16(UInt16(nameData.count), to: &out)
        appendUInt16(UInt16(mimeData.count), to: &out)
        appendUInt32(UInt32(bytes.count), to: &out)
        out.append(hash)
        out.append(nameData)
        out.append(mimeData)
        out.append(bytes)
        return out
    }

    static func unpackOriginalFile(_ packet: Data) throws -> Fra1OriginalFile {
        guard packet.count >= headerSize + metaSize else {
            throw Fra1Error.invalid("Pacchetto FRA1 troppo corto")
        }
        try validateHeader(packet, expectedType: typeOriginal)
        let payloadLength = try readUInt32(packet, at: 5)
        guard payloadLength == packet.count - headerSize else {
            throw Fra1Error.invalid("Lunghezza FRA1 non valida")
        }

        var offset = headerSize
        let nameLength = try readUInt16(packet, at: offset); offset += 2
        let mimeLength = try readUInt16(packet, at: offset); offset += 2
        let fileLength = try readUInt32(packet, at: offset); offset += 4
        guard offset + 32 <= packet.count else {
            throw Fra1Error.invalid("Metadati FRA1 incoerenti")
        }
        let storedHash = packet.subdata(in: offset..<(offset + 32)); offset += 32
        let expectedEnd = offset + nameLength + mimeLength + fileLength
        guard expectedEnd == packet.count else {
            throw Fra1Error.invalid("Metadati FRA1 incoerenti")
        }

        let nameData = packet.subdata(in: offset..<(offset + nameLength)); offset += nameLength
        let mimeData = packet.subdata(in: offset..<(offset + mimeLength)); offset += mimeLength
        guard let name = String(data: nameData, encoding: .utf8) else {
            throw Fra1Error.invalid("Nome file UTF-8 non valido")
        }
        guard let mime = String(data: mimeData, encoding: .utf8) else {
            throw Fra1Error.invalid("MIME UTF-8 non valido")
        }
        let bytes = packet.subdata(in: offset..<(offset + fileLength))
        let actualHash = Data(SHA256.hash(data: bytes))
        guard actualHash == storedHash else {
            throw Fra1Error.invalid("Verifica SHA-256 fallita: file FRA1 corrotto")
        }
        return Fra1OriginalFile(name: name, mime: mime, bytes: bytes, sha256: hexString(storedHash))
    }

    static func inspectContainer(_ packet: Data) throws -> Fra1ContainerInfo {
        if packet.starts(with: magicE) {
            let parsed = try parseEncrypted(packet)
            return Fra1ContainerInfo(kind: "fra1e", encrypted: true, keyBits: parsed.keyBits, iterations: parsed.iterations, bytes: packet.count)
        }
        if packet.starts(with: magic) {
            return Fra1ContainerInfo(kind: "fra1", encrypted: false, keyBits: 0, iterations: 0, bytes: packet.count)
        }
        return Fra1ContainerInfo(kind: "unknown", encrypted: false, keyBits: 0, iterations: 0, bytes: packet.count)
    }

    static func encryptFra1(_ fra1: Data, password: String, keyBits: Int) throws -> Data {
        let salt = try randomData(count: saltLength)
        let iv = try randomData(count: ivLength)
        return try encryptFra1(fra1, password: password, keyBits: keyBits, salt: salt, iv: iv, iterations: defaultIterations)
    }

    static func encryptFra1(_ fra1: Data, password: String, keyBits: Int, salt: Data, iv: Data, iterations: Int) throws -> Data {
        guard fra1.starts(with: magic) else {
            throw Fra1Error.invalid("Il contenuto da cifrare non è un FRA1 valido")
        }
        guard keyBits == 128 || keyBits == 256 else {
            throw Fra1Error.invalid("AES deve essere 128 o 256 bit")
        }
        guard !password.isEmpty else {
            throw Fra1Error.invalid("Password mancante")
        }
        guard salt.count == saltLength, iv.count == ivLength else {
            throw Fra1Error.invalid("Salt/IV FRA1E non validi")
        }
        guard iterations >= 10_000 && iterations <= 10_000_000 else {
            throw Fra1Error.invalid("Iterazioni PBKDF2 non valide")
        }

        let cipherLength = fra1.count + tagLength
        guard cipherLength <= Int(UInt32.max) else {
            throw Fra1Error.invalid("FRA1 troppo grande da cifrare")
        }

        var header = Data()
        header.append(magicE)
        header.append(encryptedVersion)
        header.append(algorithmAESGCM)
        appendUInt16(UInt16(keyBits), to: &header)
        header.append(kdfPBKDF2SHA256)
        appendUInt32(UInt32(iterations), to: &header)
        header.append(UInt8(salt.count))
        header.append(UInt8(iv.count))
        appendUInt32(UInt32(cipherLength), to: &header)
        guard header.count == encryptedHeaderSize else {
            throw Fra1Error.invalid("Header FRA1E interno non valido")
        }

        let aad = header + salt + iv
        let keyData = try deriveKey(password: password, salt: salt, iterations: iterations, keyBits: keyBits)
        let key = SymmetricKey(data: keyData)
        let nonce = try AES.GCM.Nonce(data: iv)
        let sealed = try AES.GCM.seal(fra1, using: key, nonce: nonce, authenticating: aad)
        let combinedCipher = Data(sealed.ciphertext) + Data(sealed.tag)
        guard combinedCipher.count == cipherLength else {
            throw Fra1Error.invalid("Lunghezza cifratura FRA1E inattesa")
        }
        return aad + combinedCipher
    }

    static func decryptFra1(_ fra1e: Data, password: String) throws -> Data {
        guard !password.isEmpty else {
            throw Fra1Error.invalid("Password mancante")
        }
        let parsed = try parseEncrypted(fra1e)
        let keyData = try deriveKey(password: password, salt: parsed.salt, iterations: parsed.iterations, keyBits: parsed.keyBits)
        let key = SymmetricKey(data: keyData)
        let nonce = try AES.GCM.Nonce(data: parsed.iv)
        guard parsed.ciphertext.count >= tagLength else {
            throw Fra1Error.invalid("FRA1E non valido")
        }
        let ciphertext = parsed.ciphertext.dropLast(tagLength)
        let tag = parsed.ciphertext.suffix(tagLength)
        do {
            let box = try AES.GCM.SealedBox(nonce: nonce, ciphertext: ciphertext, tag: tag)
            let plain = try AES.GCM.open(box, using: key, authenticating: parsed.aad)
            guard plain.starts(with: magic) else {
                throw Fra1Error.invalid("Contenuto decifrato non FRA1")
            }
            return plain
        } catch let error as Fra1Error {
            throw error
        } catch {
            throw Fra1Error.invalid("Password errata oppure file FRA1E alterato")
        }
    }

    static func sha256Hex(_ data: Data) -> String {
        hexString(Data(SHA256.hash(data: data)))
    }

    private struct ParsedEncrypted {
        let salt: Data
        let iv: Data
        let aad: Data
        let ciphertext: Data
        let keyBits: Int
        let iterations: Int
    }

    private static func parseEncrypted(_ packet: Data) throws -> ParsedEncrypted {
        guard packet.count >= encryptedHeaderSize + 8 + 12 + tagLength else {
            throw Fra1Error.invalid("Pacchetto FRA1E troppo corto")
        }
        guard packet.starts(with: magicE) else {
            throw Fra1Error.invalid("Firma FRA1E non valida")
        }
        let version = packet[5]
        let algorithm = packet[6]
        let keyBits = try readUInt16(packet, at: 7)
        let kdf = packet[9]
        let iterations = try readUInt32(packet, at: 10)
        let saltLen = Int(packet[14])
        let ivLen = Int(packet[15])
        let cipherLen = try readUInt32(packet, at: 16)

        guard version == encryptedVersion else { throw Fra1Error.invalid("Versione FRA1E non supportata") }
        guard algorithm == algorithmAESGCM else { throw Fra1Error.invalid("Algoritmo FRA1E non supportato") }
        guard keyBits == 128 || keyBits == 256 else { throw Fra1Error.invalid("Chiave AES FRA1E non valida") }
        guard kdf == kdfPBKDF2SHA256 else { throw Fra1Error.invalid("KDF FRA1E non supportato") }
        guard iterations >= 10_000 && iterations <= 10_000_000 else { throw Fra1Error.invalid("Iterazioni PBKDF2 FRA1E non valide") }
        guard saltLen >= 8 && saltLen <= 64 && ivLen >= 12 && ivLen <= 32 else { throw Fra1Error.invalid("Parametri FRA1E non validi") }

        let aadLength = encryptedHeaderSize + saltLen + ivLen
        guard cipherLen >= tagLength, aadLength + cipherLen == packet.count else {
            throw Fra1Error.invalid("Lunghezza FRA1E non valida")
        }
        let salt = packet.subdata(in: encryptedHeaderSize..<(encryptedHeaderSize + saltLen))
        let iv = packet.subdata(in: (encryptedHeaderSize + saltLen)..<aadLength)
        let aad = packet.subdata(in: 0..<aadLength)
        let ciphertext = packet.subdata(in: aadLength..<packet.count)
        return ParsedEncrypted(salt: salt, iv: iv, aad: aad, ciphertext: ciphertext, keyBits: keyBits, iterations: iterations)
    }

    private static func deriveKey(password: String, salt: Data, iterations: Int, keyBits: Int) throws -> Data {
        let passwordBytes = Array(password.utf8)
        let saltBytes = Array(salt)
        guard !passwordBytes.isEmpty else { throw Fra1Error.invalid("Password mancante") }
        var output = [UInt8](repeating: 0, count: keyBits / 8)
        let result: Int32 = passwordBytes.withUnsafeBufferPointer { p in
            saltBytes.withUnsafeBufferPointer { s in
                output.withUnsafeMutableBufferPointer { o in
                    fra1_pbkdf2_sha256(p.baseAddress!, p.count, s.baseAddress!, s.count, UInt32(iterations), o.baseAddress!, o.count)
                }
            }
        }
        guard result == 0 else {
            throw Fra1Error.invalid("PBKDF2-SHA-256 non disponibile")
        }
        return Data(output)
    }

    private static func randomData(count: Int) throws -> Data {
        var bytes = [UInt8](repeating: 0, count: count)
        let status = SecRandomCopyBytes(kSecRandomDefault, count, &bytes)
        guard status == errSecSuccess else {
            throw Fra1Error.invalid("Generatore casuale sicuro non disponibile")
        }
        return Data(bytes)
    }

    private static func makePacket(type: UInt8, payload: Data) -> Data {
        var out = Data()
        out.append(magic)
        out.append(type)
        appendUInt32(UInt32(payload.count), to: &out)
        out.append(payload)
        return out
    }

    private static func validateHeader(_ packet: Data, expectedType: UInt8) throws {
        guard packet.count >= headerSize, packet.starts(with: magic) else {
            throw Fra1Error.invalid("Firma FRA1 non valida")
        }
        guard packet[4] == expectedType else {
            throw Fra1Error.invalid("Tipo FRA1 non valido")
        }
    }

    private static func appendUInt16(_ value: UInt16, to data: inout Data) {
        data.append(UInt8((value >> 8) & 0xff))
        data.append(UInt8(value & 0xff))
    }

    private static func appendUInt32(_ value: UInt32, to data: inout Data) {
        data.append(UInt8((value >> 24) & 0xff))
        data.append(UInt8((value >> 16) & 0xff))
        data.append(UInt8((value >> 8) & 0xff))
        data.append(UInt8(value & 0xff))
    }

    private static func readUInt16(_ data: Data, at offset: Int) throws -> Int {
        guard offset >= 0, offset + 2 <= data.count else { throw Fra1Error.invalid("Lettura FRA1 fuori limite") }
        return (Int(data[offset]) << 8) | Int(data[offset + 1])
    }

    private static func readUInt32(_ data: Data, at offset: Int) throws -> Int {
        guard offset >= 0, offset + 4 <= data.count else { throw Fra1Error.invalid("Lettura FRA1 fuori limite") }
        let value = (UInt32(data[offset]) << 24) |
                    (UInt32(data[offset + 1]) << 16) |
                    (UInt32(data[offset + 2]) << 8) |
                    UInt32(data[offset + 3])
        return Int(value)
    }

    private static func hexString(_ data: Data) -> String {
        data.map { String(format: "%02x", $0) }.joined()
    }

    private static func decimalString(from data: Data) -> String {
        let base: UInt64 = 1_000_000_000
        var limbs: [UInt64] = [0]
        for byte in data {
            var carry = UInt64(byte)
            for i in 0..<limbs.count {
                let value = limbs[i] * 256 + carry
                limbs[i] = value % base
                carry = value / base
            }
            while carry > 0 {
                limbs.append(carry % base)
                carry /= base
            }
        }
        var result = String(limbs.last ?? 0)
        if limbs.count > 1 {
            for limb in limbs.dropLast().reversed() {
                result += String(format: "%09llu", limb)
            }
        }
        return result
    }

    private static func dataFromDecimal(_ decimal: String, exactLength: Int) throws -> Data {
        guard exactLength >= 0, !decimal.isEmpty, decimal.allSatisfy({ $0.isNumber }) else {
            throw Fra1Error.invalid("Numeratore decimale non valido")
        }
        let base: UInt64 = 1_000_000_000
        var limbs: [UInt64] = []
        var end = decimal.endIndex
        while end > decimal.startIndex {
            let remaining = decimal.distance(from: decimal.startIndex, to: end)
            let chunkLength = min(9, remaining)
            let start = decimal.index(end, offsetBy: -chunkLength)
            guard let value = UInt64(decimal[start..<end]) else {
                throw Fra1Error.invalid("Numeratore decimale non valido")
            }
            limbs.append(value)
            end = start
        }
        if limbs.isEmpty { limbs = [0] }
        while limbs.count > 1 && limbs.last == 0 { limbs.removeLast() }

        var reversedBytes: [UInt8] = []
        while !(limbs.count == 1 && limbs[0] == 0) {
            var remainder: UInt64 = 0
            for i in stride(from: limbs.count - 1, through: 0, by: -1) {
                let current = remainder * base + limbs[i]
                limbs[i] = current / 256
                remainder = current % 256
            }
            reversedBytes.append(UInt8(remainder))
            while limbs.count > 1 && limbs.last == 0 { limbs.removeLast() }
        }
        let bytes = Array(reversedBytes.reversed())
        guard bytes.count <= exactLength else {
            throw Fra1Error.invalid("Numeratore troppo grande per 2^K")
        }
        var out = Data(repeating: 0, count: exactLength - bytes.count)
        out.append(contentsOf: bytes)
        return out
    }
}
