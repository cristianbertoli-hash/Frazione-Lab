'use strict';
const assert = require('assert');
const fs = require('fs');
const path = require('path');
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

  const html = fs.readFileSync(path.join(__dirname, '..', 'index.html'), 'utf8');
  const ids = [
    'qrInput','qrPassword','qrGenerate','qrCanvas','qrPlay','qrPause',
    'qrFrameLabel','qrInterval','qrExport','qrImport','qrImportInput',
    'qrRestorePassword','qrRestore','qrStatus'
  ];
  for (const id of ids) {
    assert.ok(html.includes(`id="${id}"`), `index.html must contain #${id}`);
  }
  const fra1qrScript = html.indexOf('<script src="fra1qr.js"></script>');
  const appScript = html.indexOf('<script src="app.js"></script>');
  assert.ok(fra1qrScript >= 0, 'index.html must load fra1qr.js');
  assert.ok(appScript >= 0, 'index.html must load app.js');
  assert.ok(fra1qrScript < appScript, 'fra1qr.js must load before app.js');

  console.log('PASS FRA1-QR transport, FRA1E round-trip and web DOM tests');
})().catch(err => {
  console.error(err);
  process.exit(1);
});
