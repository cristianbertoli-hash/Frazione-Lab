'use strict';
const assert = require('assert');
const { webcrypto } = require('crypto');
if (!globalThis.crypto) globalThis.crypto = webcrypto;
const Q = require('../fra1qr.js');

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

  console.log('PASS FRA1-QR transport tests');
})().catch(err => {
  console.error(err);
  process.exit(1);
});
