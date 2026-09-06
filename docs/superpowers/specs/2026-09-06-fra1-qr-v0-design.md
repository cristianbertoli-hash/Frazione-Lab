# FRA1-QR v0 — Design

Date: 2026-09-06
Status: approved concept, design checkpoint before implementation
Branch: `feature/fra1-qr-v0`
Base: `feature/fra1e-offline-completion`

## Goal

Create an experimental transport format for small files, initially limited to about 100 KB, where the file is first converted to FRA1E using the existing authenticated encryption stack and is then split across a sequence of standard black/white QR codes. The QR layer transports ciphertext only; it does not replace encryption and does not compress the file.

The first version is intentionally conservative: standard QR codes, no grayscale/color coding, no custom visual symbols, and no claim that the output is smaller than the source.

## Chosen approach

The project considered three payload choices:

1. QR transports the original file directly.
2. QR transports the plain FRA1 container.
3. QR transports FRA1E ciphertext.

Version 0 uses option 3. This preserves confidentiality if all QR frames are copied: without the password, the reconstructed payload is still FRA1E ciphertext.

For the QR transport itself, the recommended v0 approach is a sequence of independent standard QR frames rather than a custom multi-color code. Standard QR is easier to decode with existing cameras/libraries and already includes error correction. A custom color code can be explored later only after the binary transport is proven reliable.

## Data flow

### Encoding

1. User selects a file up to 100 KB.
2. Existing FRA1 logic stores the original filename, MIME type, bytes and SHA-256.
3. Existing FRA1E logic encrypts the FRA1 packet with AES-GCM 256 bit.
4. Existing password derivation remains PBKDF2-HMAC-SHA256, 600,000 iterations, with random salt and nonce/IV.
5. FRA1E bytes are split into QR transport chunks.
6. Each chunk is wrapped in a small FRA1-QR frame header.
7. Each frame becomes one standard QR code.
8. Frames are displayed as an animation and can also be exported as an ordered image set in a later phase.

### Decoding

1. Scanner reads QR frames in any order.
2. Frame header identifies transfer ID, frame index and total frame count.
3. Duplicate frames are ignored.
4. Per-frame integrity is checked.
5. Once all frames are present, chunks are concatenated into the original FRA1E byte stream.
6. FRA1E header is inspected automatically.
7. User enters the password.
8. AES-GCM authentication verifies the ciphertext before release of plaintext.
9. FRA1 is unpacked and SHA-256 verifies the recovered original file byte-for-byte.

## FRA1-QR frame format

Version 0 uses a compact binary frame before QR encoding.

Fields:

- Magic: `FQ10` (4 bytes)
- Version: 1 byte
- Transfer ID: 8 random bytes
- Frame index: 2 bytes unsigned
- Frame count: 2 bytes unsigned
- Payload length: 2 bytes unsigned
- Total FRA1E length: 4 bytes unsigned
- Whole-transfer SHA-256 prefix: 8 bytes
- Frame CRC32: 4 bytes
- Payload: variable length

The transfer ID prevents accidental mixing of two simultaneous transfers. The whole-transfer SHA-256 prefix catches wrong-set assembly early; the complete FRA1E is still authenticated by AES-GCM after reconstruction.

## QR capacity and chunk sizing

Version 0 will target a conservative payload size rather than maximum theoretical QR capacity. The initial encoder should use approximately 1,200–1,600 payload bytes per frame and QR error correction level M or Q, chosen by benchmark.

For a 100 KB FRA1E payload, this implies roughly 65–90 frames depending on the selected chunk size and QR overhead. The UI must calculate and display the exact number before generation.

The design deliberately avoids promising 10–20 frames for 100 KB because that would require much denser symbols and reduce camera reliability.

## Animation

Default display target:

- 800 × 800 logical canvas or equivalent responsive square
- one QR frame at a time
- frame number shown outside the QR symbol, never inside it
- configurable frame interval, initial default around 250–400 ms
- repeated loop until stopped
- first frame repeated more often or held longer to improve acquisition

The QR symbol itself remains standard black and white.

## Error handling

The decoder must distinguish:

- QR could not be decoded
- frame belongs to another transfer
- duplicate frame
- CRC32 mismatch
- unsupported FRA1-QR version
- incomplete transfer with missing frame numbers
- reconstructed FRA1E length mismatch
- wrong password or altered FRA1E authenticated ciphertext
- corrupted FRA1 after decryption

No partial plaintext file is released before AES-GCM and FRA1 SHA-256 checks pass.

## Security model

FRA1-QR adds transport, not cryptographic strength. Security remains provided by FRA1E.

Threats handled:

- photographed/copied QR frames reveal only ciphertext
- frame reordering is harmless because indices are explicit
- frame alteration is detected by CRC32 and ultimately AES-GCM
- wrong password fails authenticated decryption

Non-goals:

- hiding the fact that data is being transferred
- preventing an observer from copying all frames
- compressing arbitrary files
- resisting compromise of the endpoint before encryption or after decryption

## First implementation target

Phase 1 should be web-first inside Frazione Lab because the existing JavaScript FRA1/FRA1E codec is already cross-platform-tested and a browser can generate/display QR frames quickly.

The first usable prototype should provide:

- select file up to 100 KB
- create FRA1E AES-256
- generate QR frame sequence
- animated playback
- import a saved set of decoded frame payloads for deterministic reconstruction testing
- reconstruct FRA1E
- decrypt with password
- restore original file byte-for-byte

Live camera scanning is phase 2. Android and Mac camera/scanner integration comes after the frame format is frozen by phase-1 tests.

## Testing

Required tests before any public integration:

1. Empty/small file round trip.
2. 100 KB binary file round trip.
3. Non-ASCII filename and MIME preservation.
4. Frames shuffled randomly before reconstruction.
5. Duplicate frames.
6. One missing frame: decoder reports exact missing indexes.
7. One corrupted frame: CRC failure.
8. Two transfers mixed: rejected by transfer ID.
9. Wrong password: authenticated failure.
10. Correct password: recovered bytes match original SHA-256 exactly.
11. Existing FRA1E compatibility vector remains unchanged.

## Future experiments explicitly deferred

- four-color symbols
- grayscale ternary symbols
- animated GIF/video export
- printable multi-page PDF
- fountain/erasure coding for frame loss
- compression before FRA1E
- custom non-QR visual matrices

These are deliberately excluded from v0 so reliability can be measured before increasing information density.
