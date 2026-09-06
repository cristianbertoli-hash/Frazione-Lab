'use strict';
const assert = require('assert');
const {webcrypto} = require('crypto');
if (!globalThis.crypto) globalThis.crypto = webcrypto;
const C = require('../codec.js');

function hex(s){ return Uint8Array.from(Buffer.from(s,'hex')); }

(async()=>{
  const plain = hex('46524131030000003a0005000a00000003ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad612e6a7067696d6167652f6a706567616263');
  const salt = Uint8Array.from([...Array(16).keys()]);
  const iv = Uint8Array.from([...Array(12).keys()].map(x=>x+0xa0));
  const password = 'Test password 123!';

  const aes128 = await C.encryptFra1(plain,password,128,{salt,iv,iterations:600000});
  assert.equal(Buffer.from(aes128).toString('hex'),'46524131450101008001000927c0100c00000053000102030405060708090a0b0c0d0e0fa0a1a2a3a4a5a6a7a8a9aaab4fb07977fc1d567a0ea8126548b35b58095df69848196630a1aefa63988f53682726e33d56f84ebb075a35eaa552132c71d1db7e5a1a25cf496131a1e7f229410929546b0a7846f4c02e252629c9b40a47875b');
  assert.equal(C.inspectContainer(aes128).keyBits,128);
  assert.deepEqual(Buffer.from(await C.decryptFra1(aes128,password)),Buffer.from(plain));

  const aes256 = await C.encryptFra1(plain,password,256,{salt,iv,iterations:600000});
  assert.equal(Buffer.from(aes256).toString('hex'),'46524131450101010001000927c0100c00000053000102030405060708090a0b0c0d0e0fa0a1a2a3a4a5a6a7a8a9aaab5f37567eeaa243d7e69eced3f9145dace37dbe7d7191ba586bf3aa031fa2fa85b53900cfce244e1f2c52392d35ffdbf0e682c6f881cab8a97e8d22b8d331e6a178abd08353230613b167ea824664f14e9ed7c8');
  const info = C.inspectContainer(aes256);
  assert.equal(info.kind,'fra1e');
  assert.equal(info.keyBits,256);
  assert.equal(info.iterations,600000);
  assert.equal(aes256.length,plain.length+64);
  assert.deepEqual(Buffer.from(await C.decryptFra1(aes256,password)),Buffer.from(plain));

  let failed=false;
  try { await C.decryptFra1(aes256,'password sbagliata'); } catch(e){ failed=true; }
  assert.ok(failed,'wrong password must fail');
  console.log('PASS FRA1E Web Crypto tests');
})().catch(e=>{ console.error(e); process.exit(1); });
