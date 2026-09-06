(function(root, factory){
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.FRA1QR = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function(){
  'use strict';

  const MAGIC = Uint8Array.from([0x46,0x51,0x31,0x30]); // FQ10
  const VERSION = 1;
  const HEADER_NO_CRC = 31;
  const HEADER = 35;
  const DEFAULT_PAYLOAD_BYTES = 1350;

  function asBytes(value){ return value instanceof Uint8Array ? value : Uint8Array.from(value || []); }
  function setU16BE(out,o,v){ out[o]=(v>>>8)&255; out[o+1]=v&255; }
  function getU16BE(b,o){ return (b[o]<<8)|b[o+1]; }
  function setU32BE(out,o,v){ out[o]=(v>>>24)&255; out[o+1]=(v>>>16)&255; out[o+2]=(v>>>8)&255; out[o+3]=v&255; }
  function getU32BE(b,o){ return (((b[o]<<24)>>>0)+(b[o+1]<<16)+(b[o+2]<<8)+b[o+3])>>>0; }
  function hex(bytes){ return Array.from(bytes,b=>b.toString(16).padStart(2,'0')).join(''); }
  function equalBytes(a,b){ a=asBytes(a); b=asBytes(b); if(a.length!==b.length)return false; for(let i=0;i<a.length;i++) if(a[i]!==b[i])return false; return true; }
  function concat(...parts){ const len=parts.reduce((n,p)=>n+p.length,0); const out=new Uint8Array(len); let o=0; for(const p of parts){ out.set(p,o); o+=p.length; } return out; }
  function startsWith(bytes,prefix){ if(bytes.length<prefix.length)return false; for(let i=0;i<prefix.length;i++) if(bytes[i]!==prefix[i])return false; return true; }

  function getCrypto(){
    const c=globalThis.crypto;
    if(!c || !c.subtle || !c.getRandomValues) throw new Error('Web Crypto non disponibile');
    return c;
  }

  async function sha256(bytes){
    const digest=await getCrypto().subtle.digest('SHA-256',asBytes(bytes));
    return new Uint8Array(digest);
  }

  function crc32(bytes){
    bytes=asBytes(bytes);
    let crc=0xffffffff;
    for(const byte of bytes){
      crc ^= byte;
      for(let j=0;j<8;j++) crc=(crc>>>1) ^ ((crc&1)?0xedb88320:0);
    }
    return (crc ^ 0xffffffff) >>> 0;
  }

  function toBase64(bytes){
    bytes=asBytes(bytes);
    if(typeof Buffer!=='undefined') return Buffer.from(bytes).toString('base64');
    let s='';
    const chunk=0x8000;
    for(let i=0;i<bytes.length;i+=chunk) s += String.fromCharCode(...bytes.subarray(i,i+chunk));
    return btoa(s);
  }

  function fromBase64(value){
    if(typeof Buffer!=='undefined') return Uint8Array.from(Buffer.from(value,'base64'));
    const s=atob(value); const out=new Uint8Array(s.length);
    for(let i=0;i<s.length;i++) out[i]=s.charCodeAt(i);
    return out;
  }

  function encodeFrameText(binaryFrame){
    const b64=toBase64(binaryFrame).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
    return 'FQ1:'+b64;
  }

  function decodeFrameText(text){
    text=String(text||'').trim();
    if(!text.startsWith('FQ1:')) throw new Error('Prefisso FRA1-QR non valido');
    let b64=text.slice(4).replace(/-/g,'+').replace(/_/g,'/');
    while(b64.length%4) b64+='=';
    try { return fromBase64(b64); }
    catch(e){ throw new Error('Base64URL FRA1-QR non valido'); }
  }

  function parseBinaryFrame(raw){
    raw=asBytes(raw);
    if(raw.length<HEADER) throw new Error('Frame FRA1-QR troppo corto');
    if(!startsWith(raw,MAGIC)) throw new Error('Magic FRA1-QR non valido');
    if(raw[4]!==VERSION) throw new Error('Versione FRA1-QR non supportata');
    const transferId=raw.slice(5,13);
    const index=getU16BE(raw,13);
    const frameCount=getU16BE(raw,15);
    const payloadLength=getU16BE(raw,17);
    const totalLength=getU32BE(raw,19);
    const hashPrefix=raw.slice(23,31);
    const storedCrc=getU32BE(raw,31);
    if(frameCount<1 || index>=frameCount) throw new Error('Indice/frame count FRA1-QR non valido');
    if(payloadLength!==raw.length-HEADER) throw new Error('Lunghezza payload FRA1-QR non valida');
    const payload=raw.slice(HEADER);
    const actualCrc=crc32(concat(raw.slice(0,HEADER_NO_CRC),payload));
    if(actualCrc!==storedCrc) throw new Error('CRC FRA1-QR non valido');
    return {
      transferId,
      transferIdHex:hex(transferId),
      index,
      frameCount,
      payloadLength,
      totalLength,
      hashPrefix,
      hashPrefixHex:hex(hashPrefix),
      crc32:storedCrc,
      payload,
      raw
    };
  }

  function parseFrameText(text){ return parseBinaryFrame(decodeFrameText(text)); }

  async function split(bytes, options={}){
    bytes=asBytes(bytes);
    const payloadBytes=Number(options.payloadBytes ?? DEFAULT_PAYLOAD_BYTES);
    if(!Number.isInteger(payloadBytes) || payloadBytes<256 || payloadBytes>1600) throw new Error('Dimensione chunk FRA1-QR non valida');
    if(bytes.length>0xffffffff) throw new Error('Payload FRA1E troppo grande');
    const frameCount=Math.max(1,Math.ceil(bytes.length/payloadBytes));
    if(frameCount>65535) throw new Error('Troppi frame FRA1-QR');

    let transferId;
    if(options.transferId!==undefined){
      transferId=asBytes(options.transferId);
      if(transferId.length!==8) throw new Error('Transfer ID FRA1-QR deve essere di 8 byte');
    }else{
      transferId=getCrypto().getRandomValues(new Uint8Array(8));
    }

    const hashPrefix=(await sha256(bytes)).slice(0,8);
    const frames=[];
    for(let index=0;index<frameCount;index++){
      const start=index*payloadBytes;
      const payload=bytes.slice(start,Math.min(start+payloadBytes,bytes.length));
      const raw=new Uint8Array(HEADER+payload.length);
      raw.set(MAGIC,0); raw[4]=VERSION; raw.set(transferId,5);
      setU16BE(raw,13,index); setU16BE(raw,15,frameCount); setU16BE(raw,17,payload.length);
      setU32BE(raw,19,bytes.length); raw.set(hashPrefix,23); raw.set(payload,HEADER);
      setU32BE(raw,31,crc32(concat(raw.slice(0,HEADER_NO_CRC),payload)));
      frames.push(encodeFrameText(raw));
    }
    return {transferIdHex:hex(transferId),frameCount,frames,totalLength:bytes.length,hashPrefixHex:hex(hashPrefix)};
  }

  function collect(frameTexts){
    if(!Array.isArray(frameTexts) || frameTexts.length===0) throw new Error('Nessun frame FRA1-QR');
    const parsed=frameTexts.map(parseFrameText);
    const first=parsed[0];
    const byIndex=new Map();
    for(const frame of parsed){
      if(frame.transferIdHex!==first.transferIdHex) throw new Error('Frame di transfer diversi');
      if(frame.frameCount!==first.frameCount || frame.totalLength!==first.totalLength || frame.hashPrefixHex!==first.hashPrefixHex) throw new Error('Metadati transfer FRA1-QR incoerenti');
      const previous=byIndex.get(frame.index);
      if(previous && !equalBytes(previous.raw,frame.raw)) throw new Error(`Frame duplicato ${frame.index} con contenuto diverso`);
      if(!previous) byIndex.set(frame.index,frame);
    }
    return {first,byIndex};
  }

  function missingIndexes(frameTexts){
    const {first,byIndex}=collect(frameTexts);
    const missing=[];
    for(let i=0;i<first.frameCount;i++) if(!byIndex.has(i)) missing.push(i);
    return missing;
  }

  function reassemble(frameTexts){
    const {first,byIndex}=collect(frameTexts);
    const missing=[];
    for(let i=0;i<first.frameCount;i++) if(!byIndex.has(i)) missing.push(i);
    if(missing.length) throw new Error('Frame mancanti: '+missing.join(', '));
    const parts=[];
    for(let i=0;i<first.frameCount;i++) parts.push(byIndex.get(i).payload);
    const out=concat(...parts);
    if(out.length!==first.totalLength) throw new Error('Lunghezza FRA1E ricostruita non valida');
    return out;
  }

  function parseFrameSetJson(text){
    let obj;
    try { obj=JSON.parse(String(text)); }
    catch(e){ throw new Error('JSON FRA1-QR non valido'); }
    if(!obj || obj.format!=='FRA1-QR-v0') throw new Error('Formato FRA1-QR JSON non valido');
    if(!Array.isArray(obj.frames) || obj.frames.length===0) throw new Error('Elenco frame FRA1-QR vuoto o non valido');
    if(!Number.isInteger(obj.frameCount) || obj.frameCount!==obj.frames.length) throw new Error('Conteggio frame FRA1-QR incoerente');
    if(obj.frames.some(frame=>typeof frame!=='string')) throw new Error('Ogni frame FRA1-QR deve essere testo');
    reassemble(obj.frames);
    return obj.frames.slice();
  }

  return {
    VERSION,
    DEFAULT_PAYLOAD_BYTES,
    split,
    parseFrameText,
    reassemble,
    missingIndexes,
    encodeFrameText,
    decodeFrameText,
    parseFrameSetJson,
    crc32
  };
});
