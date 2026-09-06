(function(root, factory){
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.FractionCodec = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function(){
  'use strict';
  const MAGIC = Uint8Array.from([0x46,0x52,0x41,0x31]);
  const MAGIC_E = Uint8Array.from([0x46,0x52,0x41,0x31,0x45]);
  const TYPE_TEXT = 1;
  const TYPE_ORIGINAL_FILE = 3;
  const HEADER = 9;
  const META = 2+2+4+32;
  const E_VERSION = 1;
  const E_ALG_AES_GCM = 1;
  const E_KDF_PBKDF2_SHA256 = 1;
  const E_HEADER = 20;
  const E_SALT_LEN = 16;
  const E_IV_LEN = 12;
  const E_TAG_LEN = 16;
  const DEFAULT_PBKDF2_ITERATIONS = 600000;
  const enc = new TextEncoder();
  const dec = new TextDecoder('utf-8', {fatal:true});

  function setU16BE(out, o, v){ out[o]=(v>>>8)&255; out[o+1]=v&255; }
  function getU16BE(b,o){ return (b[o]<<8)|b[o+1]; }
  function setU32BE(out,o,v){ out[o]=(v>>>24)&255; out[o+1]=(v>>>16)&255; out[o+2]=(v>>>8)&255; out[o+3]=v&255; }
  function getU32BE(b,o){ return (((b[o]<<24)>>>0)+(b[o+1]<<16)+(b[o+2]<<8)+b[o+3])>>>0; }
  function hex(bytes){ return Array.from(bytes, b=>b.toString(16).padStart(2,'0')).join(''); }
  function bytesToBigInt(bytes){ if(bytes.length===0) return 0n; let h=''; for(const b of bytes) h += b.toString(16).padStart(2,'0'); return BigInt('0x'+h); }
  function bigIntToBytes(value,length){ if(value<0n) throw new Error('Numero negativo non valido'); let h=value.toString(16); if(h.length>length*2) throw new Error('Numeratore incompatibile'); h=h.padStart(length*2,'0'); const out=new Uint8Array(length); for(let i=0;i<length;i++) out[i]=parseInt(h.slice(i*2,i*2+2),16); return out; }
  function startsWith(bytes, magic){ if(bytes.length<magic.length)return false; for(let i=0;i<magic.length;i++) if(bytes[i]!==magic[i]) return false; return true; }
  function concat(...parts){ const len=parts.reduce((n,p)=>n+p.length,0); const out=new Uint8Array(len); let o=0; for(const p of parts){out.set(p,o);o+=p.length;} return out; }
  function asBytes(value){ return value instanceof Uint8Array ? value : Uint8Array.from(value || []); }
  function getCrypto(){ const c=globalThis.crypto; if(!c || !c.subtle || !c.getRandomValues) throw new Error('Web Crypto non disponibile in questo browser'); return c; }

  function makePacket(type,payload){ const out=new Uint8Array(HEADER+payload.length); out.set(MAGIC,0); out[4]=type; setU32BE(out,5,payload.length); out.set(payload,HEADER); return out; }
  function packetToDecimalFraction(packet){ return `${bytesToBigInt(packet).toString()} / 2^${packet.length*8}`; }
  function parseDecimalFraction(value){ const m=/^\s*(\d+)\s*\/\s*2\^(\d+)\s*$/.exec(String(value)); if(!m) throw new Error('Formato frazione non valido: usa N / 2^K'); const k=Number(m[2]); if(!Number.isSafeInteger(k)||k<=0||k%8!==0) throw new Error('Esponente K non valido'); if(k/8>2_000_000) throw new Error('Frazione decimale troppo grande per la casella di testo'); const n=BigInt(m[1]); if(n >= (1n<<BigInt(k))) throw new Error('Numeratore incompatibile'); return bigIntToBytes(n,k/8); }
  function encodeText(text){ return packetToDecimalFraction(makePacket(TYPE_TEXT, enc.encode(String(text)))); }
  function decodeTextFraction(fraction){ const packet=parseDecimalFraction(fraction); if(packet.length<HEADER) throw new Error('Pacchetto FRA1 troppo corto'); for(let i=0;i<4;i++) if(packet[i]!==MAGIC[i]) throw new Error('Firma FRA1 non valida'); if(packet[4]!==TYPE_TEXT) throw new Error('La frazione non contiene testo'); const len=getU32BE(packet,5); if(len!==packet.length-HEADER) throw new Error('Lunghezza FRA1 non valida'); try { return dec.decode(packet.slice(HEADER)); } catch(e){ throw new Error('Testo UTF-8 non valido'); } }

  async function sha256Bytes(bytes){ bytes=asBytes(bytes); const digest = await getCrypto().subtle.digest('SHA-256', bytes); return new Uint8Array(digest); }
  async function sha256Hex(bytes){ return hex(await sha256Bytes(bytes)); }

  async function packOriginalFile({name, mime, bytes}){
    bytes=asBytes(bytes); name = String(name || 'file.bin'); mime = String(mime || 'application/octet-stream');
    const nb = enc.encode(name), mb = enc.encode(mime);
    if (nb.length > 65535 || mb.length > 65535) throw new Error('Metadati troppo lunghi');
    if (bytes.length > 0xffffffff) throw new Error('File troppo grande');
    const hash = await sha256Bytes(bytes);
    const payloadLen = META + nb.length + mb.length + bytes.length;
    const out = new Uint8Array(HEADER + payloadLen);
    out.set(MAGIC,0); out[4]=TYPE_ORIGINAL_FILE; setU32BE(out,5,payloadLen);
    let o=HEADER; setU16BE(out,o,nb.length); o+=2; setU16BE(out,o,mb.length); o+=2; setU32BE(out,o,bytes.length); o+=4;
    out.set(hash,o); o+=32; out.set(nb,o); o+=nb.length; out.set(mb,o); o+=mb.length; out.set(bytes,o);
    return out;
  }

  async function unpackOriginalFile(packet){
    packet=asBytes(packet);
    if (packet.length < HEADER+META) throw new Error('Pacchetto FRA1 troppo corto');
    for(let i=0;i<4;i++) if(packet[i]!==MAGIC[i]) throw new Error('Firma FRA1 non valida');
    if(packet[4]!==TYPE_ORIGINAL_FILE) throw new Error('Tipo FRA1 non supportato');
    const payloadLen=getU32BE(packet,5); if(payloadLen!==packet.length-HEADER) throw new Error('Lunghezza FRA1 non valida');
    let o=HEADER; const nameLen=getU16BE(packet,o); o+=2; const mimeLen=getU16BE(packet,o); o+=2; const fileLen=getU32BE(packet,o); o+=4;
    const storedHash=packet.slice(o,o+32); o+=32;
    const expectedEnd=o+nameLen+mimeLen+fileLen; if(expectedEnd!==packet.length) throw new Error('Metadati FRA1 incoerenti');
    let name,mime; try { name=dec.decode(packet.slice(o,o+nameLen)); o+=nameLen; mime=dec.decode(packet.slice(o,o+mimeLen)); o+=mimeLen; } catch(e){ throw new Error('Metadati UTF-8 non validi'); }
    const bytes=packet.slice(o,o+fileLen); const actual=await sha256Bytes(bytes);
    const hashVerified = hex(actual)===hex(storedHash);
    if(!hashVerified) throw new Error('Verifica SHA-256 fallita: file FRA1 corrotto');
    return {type:'original-file', name, mime, bytes, sha256:hex(storedHash), hashVerified};
  }

  function parseEncrypted(packet){
    packet=asBytes(packet);
    if(packet.length<E_HEADER+E_SALT_LEN+E_IV_LEN+E_TAG_LEN) throw new Error('Pacchetto FRA1E troppo corto');
    if(!startsWith(packet,MAGIC_E)) throw new Error('Firma FRA1E non valida');
    const version=packet[5], algorithm=packet[6], keyBits=getU16BE(packet,7), kdf=packet[9], iterations=getU32BE(packet,10), saltLen=packet[14], ivLen=packet[15], cipherLen=getU32BE(packet,16);
    if(version!==E_VERSION) throw new Error('Versione FRA1E non supportata');
    if(algorithm!==E_ALG_AES_GCM) throw new Error('Algoritmo FRA1E non supportato');
    if(keyBits!==128 && keyBits!==256) throw new Error('Chiave AES FRA1E non valida');
    if(kdf!==E_KDF_PBKDF2_SHA256) throw new Error('KDF FRA1E non supportato');
    if(iterations<10000 || iterations>10_000_000) throw new Error('Iterazioni PBKDF2 FRA1E non valide');
    if(saltLen<8 || saltLen>64 || ivLen<12 || ivLen>32) throw new Error('Parametri FRA1E non validi');
    const aadLen=E_HEADER+saltLen+ivLen;
    if(cipherLen<E_TAG_LEN || aadLen+cipherLen!==packet.length) throw new Error('Lunghezza FRA1E non valida');
    return {packet, version, algorithm, keyBits, kdf, iterations, saltLen, ivLen, cipherLen, aadLen, salt:packet.slice(E_HEADER,E_HEADER+saltLen), iv:packet.slice(E_HEADER+saltLen,aadLen), aad:packet.slice(0,aadLen), ciphertext:packet.slice(aadLen)};
  }

  function inspectContainer(packet){
    packet=asBytes(packet);
    if(startsWith(packet,MAGIC_E)){
      const p=parseEncrypted(packet);
      return {kind:'fra1e', encrypted:true, version:p.version, keyBits:p.keyBits, iterations:p.iterations, bytes:packet.length, overhead:64};
    }
    if(startsWith(packet,MAGIC)) return {kind:'fra1', encrypted:false, bytes:packet.length};
    return {kind:'unknown', encrypted:false, bytes:packet.length};
  }

  async function deriveAesKey(password, salt, iterations, keyBits, usages){
    password=String(password ?? ''); if(!password) throw new Error('Password mancante');
    const subtle=getCrypto().subtle;
    const base=await subtle.importKey('raw', enc.encode(password), 'PBKDF2', false, ['deriveKey']);
    return subtle.deriveKey({name:'PBKDF2',salt,iterations,hash:'SHA-256'},base,{name:'AES-GCM',length:keyBits},false,usages);
  }

  async function encryptFra1(fra1Packet,password,keyBits=256,options={}){
    fra1Packet=asBytes(fra1Packet);
    if(!startsWith(fra1Packet,MAGIC)) throw new Error('Il contenuto da cifrare non è un FRA1 valido');
    if(keyBits!==128 && keyBits!==256) throw new Error('AES deve essere 128 o 256 bit');
    const iterations=Number(options.iterations ?? DEFAULT_PBKDF2_ITERATIONS);
    if(!Number.isInteger(iterations) || iterations<10000 || iterations>10_000_000) throw new Error('Iterazioni PBKDF2 non valide');
    const c=getCrypto();
    const salt=options.salt ? asBytes(options.salt) : c.getRandomValues(new Uint8Array(E_SALT_LEN));
    const iv=options.iv ? asBytes(options.iv) : c.getRandomValues(new Uint8Array(E_IV_LEN));
    if(salt.length!==E_SALT_LEN || iv.length!==E_IV_LEN) throw new Error('Salt/IV FRA1E non validi');
    const cipherLen=fra1Packet.length+E_TAG_LEN;
    const header=new Uint8Array(E_HEADER);
    header.set(MAGIC_E,0); header[5]=E_VERSION; header[6]=E_ALG_AES_GCM; setU16BE(header,7,keyBits); header[9]=E_KDF_PBKDF2_SHA256; setU32BE(header,10,iterations); header[14]=salt.length; header[15]=iv.length; setU32BE(header,16,cipherLen);
    const aad=concat(header,salt,iv);
    const key=await deriveAesKey(password,salt,iterations,keyBits,['encrypt']);
    const encrypted=await c.subtle.encrypt({name:'AES-GCM',iv,additionalData:aad,tagLength:128},key,fra1Packet);
    return concat(aad,new Uint8Array(encrypted));
  }

  async function decryptFra1(fra1ePacket,password){
    const p=parseEncrypted(fra1ePacket); const c=getCrypto();
    const key=await deriveAesKey(password,p.salt,p.iterations,p.keyBits,['decrypt']);
    try{
      const plain=new Uint8Array(await c.subtle.decrypt({name:'AES-GCM',iv:p.iv,additionalData:p.aad,tagLength:128},key,p.ciphertext));
      if(!startsWith(plain,MAGIC)) throw new Error('Contenuto decifrato non FRA1');
      return plain;
    }catch(e){
      if(e && e.message==='Contenuto decifrato non FRA1') throw e;
      throw new Error('Password errata oppure file FRA1E alterato');
    }
  }

  function describeAsFraction(packet){ packet=asBytes(packet); const preview=packet.slice(0, Math.min(16, packet.length)); return {packetBytes:packet.length, denominatorExponent:packet.length*8, numeratorHexPreview:'0x'+hex(preview)+(packet.length>16?'…':'')}; }

  return {TYPE_TEXT, TYPE_ORIGINAL_FILE, DEFAULT_PBKDF2_ITERATIONS, encodeText, decodeTextFraction, packOriginalFile, unpackOriginalFile, sha256Hex, describeAsFraction, inspectContainer, encryptFra1, decryptFra1};
});
