import Foundation

private var checks = 0

private func check(_ condition: @autoclosure () -> Bool, _ message: String) {
    checks += 1
    if !condition() {
        fputs("FAIL: \(message)\n", stderr)
        exit(1)
    }
}

private func checkEqual<T: Equatable>(_ expected: T, _ actual: T, _ message: String) {
    checks += 1
    if expected != actual {
        fputs("FAIL: \(message)\nexpected=\(expected)\nactual=\(actual)\n", stderr)
        exit(1)
    }
}

private func hex(_ string: String) -> Data {
    var out = Data(capacity: string.count / 2)
    var i = string.startIndex
    while i < string.endIndex {
        let j = string.index(i, offsetBy: 2)
        out.append(UInt8(string[i..<j], radix: 16)!)
        i = j
    }
    return out
}

private func hexString(_ data: Data) -> String {
    data.map { String(format: "%02x", $0) }.joined()
}

func runTests() throws {
    let fileBytes = Data("abc".utf8)
    let fra1 = try Fra1Codec.packOriginalFile(name: "a.jpg", mime: "image/jpeg", bytes: fileBytes)
    let expectedFra1 = "46524131030000003a0005000a00000003ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad612e6a7067696d6167652f6a706567616263"
    checkEqual(expectedFra1, hexString(fra1), "FRA1 binary compatibility vector")

    let restored = try Fra1Codec.unpackOriginalFile(fra1)
    checkEqual("a.jpg", restored.name, "FRA1 preserves file name")
    checkEqual("image/jpeg", restored.mime, "FRA1 preserves MIME")
    checkEqual(fileBytes, restored.bytes, "FRA1 restores bytes exactly")

    let salt = Data((0..<16).map(UInt8.init))
    let iv = Data((0..<12).map { UInt8(0xa0 + $0) })
    let password = "Test password 123!"

    let aes128 = try Fra1Codec.encryptFra1(
        fra1,
        password: password,
        keyBits: 128,
        salt: salt,
        iv: iv,
        iterations: 600_000
    )
    let expected128 = "46524131450101008001000927c0100c00000053000102030405060708090a0b0c0d0e0fa0a1a2a3a4a5a6a7a8a9aaab4fb07977fc1d567a0ea8126548b35b58095df69848196630a1aefa63988f53682726e33d56f84ebb075a35eaa552132c71d1db7e5a1a25cf496131a1e7f229410929546b0a7846f4c02e252629c9b40a47875b"
    checkEqual(expected128, hexString(aes128), "FRA1E AES-128 cross-platform vector")
    let info128 = try Fra1Codec.inspectContainer(aes128)
    check(info128.encrypted && info128.keyBits == 128, "AES-128 automatic detection")
    checkEqual(fra1, try Fra1Codec.decryptFra1(aes128, password: password), "AES-128 round trip")

    let aes256 = try Fra1Codec.encryptFra1(
        fra1,
        password: password,
        keyBits: 256,
        salt: salt,
        iv: iv,
        iterations: 600_000
    )
    let expected256 = "46524131450101010001000927c0100c00000053000102030405060708090a0b0c0d0e0fa0a1a2a3a4a5a6a7a8a9aaab5f37567eeaa243d7e69eced3f9145dace37dbe7d7191ba586bf3aa031fa2fa85b53900cfce244e1f2c52392d35ffdbf0e682c6f881cab8a97e8d22b8d331e6a178abd08353230613b167ea824664f14e9ed7c8"
    checkEqual(expected256, hexString(aes256), "FRA1E AES-256 cross-platform vector")
    let info256 = try Fra1Codec.inspectContainer(aes256)
    check(info256.encrypted && info256.keyBits == 256, "AES-256 automatic detection")
    checkEqual(600_000, info256.iterations, "PBKDF2 iteration detection")
    checkEqual(fra1.count + 64, aes256.count, "FRA1E overhead is exactly 64 bytes")
    checkEqual(fra1, try Fra1Codec.decryptFra1(aes256, password: password), "AES-256 round trip")

    var wrongPasswordFailed = false
    do {
        _ = try Fra1Codec.decryptFra1(aes256, password: "password sbagliata")
    } catch {
        wrongPasswordFailed = true
    }
    check(wrongPasswordFailed, "wrong password must fail authenticated decryption")

    let text = "Ciao Cris — FRA1 ✓ 🌱"
    let fraction = try Fra1Codec.encodeTextFraction(text)
    check(fraction.contains(" / 2^"), "text fraction syntax")
    checkEqual(text, try Fra1Codec.decodeTextFraction(fraction), "UTF-8 text fraction round trip")

    for length in [16, 24, 32] {
        let generated = try PasswordGenerator.generate(length: length)
        checkEqual(length, generated.count, "password generator length \(length)")
        check(generated.allSatisfy { PasswordGenerator.alphabet.contains($0) }, "password generator alphabet")
    }

    print("PASS \(checks) checks")
}

do {
    try runTests()
} catch {
    fputs("FAIL: \(error)\n", stderr)
    exit(1)
}
