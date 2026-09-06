'use strict';
const assert = require('assert');
const fs = require('fs');
const path = require('path');
const { webcrypto } = require('crypto');
if (!globalThis.crypto) globalThis.crypto = webcrypto;

const rootIndex = fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
assert.ok(!rootIndex.includes('id="fra1QrCard"'), 'La app principale non deve contenere FRA1-QR');
assert.ok(!rootIndex.includes('id="qrGenerate"'), 'La app principale non deve contenere controlli FRA1-QR');

const qrIndexPath = path.join(__dirname,'..','qr','index.html');
const qrAppPath = path.join(__dirname,'..','qr','app.js');
const qrCodecPath = path.join(__dirname,'..','qr','fra1qr.js');
assert.ok(fs.existsSync(qrIndexPath), 'Manca qr/index.html');
assert.ok(fs.existsSync(qrAppPath), 'Manca qr/app.js');
assert.ok(fs.existsSync(qrCodecPath), 'Manca qr/fra1qr.js');

const html = fs.readFileSync(qrIndexPath,'utf8');
for (const id of ['qrInput','qrPassword','qrGenerate','qrCanvas','qrPlay','qrPause','qrFrameLabel','qrInterval','qrExport','qrImport','qrImportInput','qrRestorePassword','qrRestore','qrDownload','qrStatus']) {
  assert.ok(html.includes(`id="${id}"`), `Manca controllo ${id}`);
}
assert.ok(html.includes('../codec.js'), 'La seconda app deve riusare il codec FRA1E principale');
assert.ok(html.includes('fra1qr.js'), 'La seconda app deve caricare il codec FRA1-QR locale');
assert.ok(html.includes('app.js'), 'La seconda app deve caricare la propria UI');

const Q = require('../qr/fra1qr.js');
const C = require('../codec.js');
function bytes(n){ return Uint8Array.from({length:n},(_,i)=>(i*73+19)&255); }

(async()=>{
  const original = bytes(100*1024);
  const fra1 = await C.packOriginalFile({name:'prova_è_✓.bin',mime:'application/octet-stream',bytes:original});
  const fra1e = await C.encryptFra1(fra1,'Password QR molto sicura 2026!',256);
  const split = await Q.split(fra1e,{payloadBytes:1350,transferId:Uint8Array.from([0,1,2,3,4,5,6,7])});
  assert.ok(split.frameCount > 70 && split.frameCount < 90);
  const reversed = split.frames.slice().reverse();
  const rebuilt = Q.reassemble(reversed);
  assert.deepEqual(Buffer.from(rebuilt),Buffer.from(fra1e));
  await assert.rejects(()=>C.decryptFra1(rebuilt,'password sbagliata'));
  const decrypted = await C.decryptFra1(rebuilt,'Password QR molto sicura 2026!');
  const restored = await C.unpackOriginalFile(decrypted);
  assert.equal(restored.name,'prova_è_✓.bin');
  assert.deepEqual(Buffer.from(restored.bytes),Buffer.from(original));

  const json = JSON.stringify({format:'FRA1-QR-v0',frameCount:split.frames.length,frames:split.frames});
  assert.equal(Q.parseFrameSetJson(json).length,split.frames.length);
  console.log('PASS separate FRA1-QR site and AES-256 round-trip tests');
})().catch(err=>{ console.error(err); process.exit(1); });
