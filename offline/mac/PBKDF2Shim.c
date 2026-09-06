#include <CommonCrypto/CommonKeyDerivation.h>
#include <stdint.h>
#include <stddef.h>

int32_t fra1_pbkdf2_sha256(
    const uint8_t *password,
    size_t password_len,
    const uint8_t *salt,
    size_t salt_len,
    uint32_t rounds,
    uint8_t *output,
    size_t output_len
) {
    return (int32_t)CCKeyDerivationPBKDF(
        kCCPBKDF2,
        (const char *)password,
        password_len,
        salt,
        salt_len,
        kCCPRFHmacAlgSHA256,
        rounds,
        output,
        output_len
    );
}
