'use strict';
const assert = require('assert');
const { webcrypto } = require('crypto');
if (!globalThis.crypto) globalThis.crypto = webcrypto;
const Q = require('../fra1qr.js');
const C = require('../codec.js');

function bytes(n) {
  return Uint8Array.from({ length: n }, (_, i) => (i * 73 + 19) & 255);
}

(async () => {
  const payload = bytes(100 * 1024 + 64);
  const transferId = Uint8Array.from([0,1,2,3,4,5,6,7]);
  const split = await Q.split(payload, { payloadBytes: 1350, transferId });

  assert.ok(split.frameCount > 70 && split.frameCount < 90);
  assert.equal(split.frames.length, split.frameCount);
  assert.ok(split.frames.every(x => x.startsWith('FQ1:')));

  const shuffled = split.frames.slice().reverse();
  assert.deepEqual(Buffer.from(Q.reassemble(shuffled)), Buffer.from(payload));

  const withDuplicate = shuffled.concat(shuffled[0]);
  assert.deepEqual(Buffer.from(Q.reassemble(withDuplicate)), Buffer.from(payload));

  const missing = split.frames.filter((_, i) => i !== 3);
  assert.deepEqual(Q.missingIndexes(missing), [3]);
  assert.throws(() => Q.reassemble(missing), /mancanti/i);

  const corrupted = split.frames.slice();
  const raw = Q.decodeFrameText(corrupted[2]);
  raw[raw.length - 1] ^= 1;
  corrupted[2] = Q.encodeFrameText(raw);
  assert.throws(() => Q.reassemble(corrupted), /CRC/i);

  const other = await Q.split(payload, {
    payloadBytes: 1350,
    transferId: Uint8Array.from([8,9,10,11,12,13,14,15])
  });
  assert.throws(() => Q.reassemble([split.frames[0], other.frames[1]]), /transfer/i);

  const original = bytes(100 * 1024);
  const fra1 = await C.packOriginalFile({
    name: 'foto_è_✓.bin',
    mime: 'application/octet-stream',
    bytes: original
  });
  const password = 'Password QR molto sicura 2026!';
  const fra1e = await C.encryptFra1(fra1, password, 256);
  const qr = await Q.split(fra1e, { payloadBytes: 1350, transferId });
  const permuted = qr.frames.slice(13).concat(qr.frames.slice(0,13)).reverse();
  const reconstructed = Q.reassemble(permuted);
  assert.deepEqual(Buffer.from(reconstructed), Buffer.from(fra1e));

  let wrongPasswordFailed = false;
  try {
    await C.decryptFra1(reconstructed, 'password sbagliata');
  } catch (e) {
    wrongPasswordFailed = /Password errata|alterato/i.test(e.message);
  }
  assert.ok(wrongPasswordFailed, 'wrong password must fail after QR reconstruction');

  const decrypted = await C.decryptFra1(reconstructed, password);
  const restored = await C.unpackOriginalFile(decrypted);
  assert.equal(restored.name, 'foto_è_✓.bin');
  assert.equal(restored.mime, 'application/octet-stream');
  assert.deepEqual(Buffer.from(restored.bytes), Buffer.from(original));
  assert.equal(restored.hashVerified, true);

  console.log('PASS FRA1-QR transport and FRA1E round-trip tests');
})().catch(err => {
  console.error(err);
  process.exit(1);
});
