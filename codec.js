(function(root, factory){
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.FractionCodec = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function(){
  'use strict';
  const MAGIC = Uint8Array.from([0x46,0x52,0x41,0x31]);
  const TYPE_TEXT = 1;
  const TYPE_ORIGINAL_FILE = 3;
  const HEADER = 9;
  const META = 2+2+4+32;
  const enc = new TextEncoder();
  const dec = new TextDecoder('utf-8', {fatal:true});

  function setU16BE(out, o, v){ out[o]=(v>>>8)&255; out[o+1]=v&255; }
  function getU16BE(b,o){ return (b[o]<<8)|b[o+1]; }
  function setU32BE(out,o,v){ out[o]=(v>>>24)&255; out[o+1]=(v>>>16)&255; out[o+2]=(v>>>8)&255; out[o+3]=v&255; }
  function getU32BE(b,o){ return (((b[o]<<24)>>>0)+(b[o+1]<<16)+(b[o+2]<<8)+b[o+3])>>>0; }
  function hex(bytes){ return Array.from(bytes, b=>b.toString(16).padStart(2,'0')).join(''); }
  function bytesToBigInt(bytes){ if(bytes.length===0) return 0n; let h=''; for(const b of bytes) h += b.toString(16).padStart(2,'0'); return BigInt('0x'+h); }
  function bigIntToBytes(value,length){ if(value<0n) throw new Error('Numero negativo non valido'); let h=value.toString(16); if(h.length>length*2) throw new Error('Numeratore incompatibile'); h=h.padStart(length*2,'0'); const out=new Uint8Array(length); for(let i=0;i<length;i++) out[i]=parseInt(h.slice(i*2,i*2+2),16); return out; }
  function makePacket(type,payload){ const out=new Uint8Array(HEADER+payload.length); out.set(MAGIC,0); out[4]=type; setU32BE(out,5,payload.length); out.set(payload,HEADER); return out; }
  function packetToDecimalFraction(packet){ return `${bytesToBigInt(packet).toString()} / 2^${packet.length*8}`; }
  function parseDecimalFraction(value){ const m=/^\s*(\d+)\s*\/\s*2\^(\d+)\s*$/.exec(String(value)); if(!m) throw new Error('Formato frazione non valido: usa N / 2^K'); const k=Number(m[2]); if(!Number.isSafeInteger(k)||k<=0||k%8!==0) throw new Error('Esponente K non valido'); if(k/8>2_000_000) throw new Error('Frazione decimale troppo grande per la casella di testo'); const n=BigInt(m[1]); if(n >= (1n<<BigInt(k))) throw new Error('Numeratore incompatibile'); return bigIntToBytes(n,k/8); }
  function encodeText(text){ return packetToDecimalFraction(makePacket(TYPE_TEXT, enc.encode(String(text)))); }
  function decodeTextFraction(fraction){ const packet=parseDecimalFraction(fraction); if(packet.length<HEADER) throw new Error('Pacchetto FRA1 troppo corto'); for(let i=0;i<4;i++) if(packet[i]!==MAGIC[i]) throw new Error('Firma FRA1 non valida'); if(packet[4]!==TYPE_TEXT) throw new Error('La frazione non contiene testo'); const len=getU32BE(packet,5); if(len!==packet.length-HEADER) throw new Error('Lunghezza FRA1 non valida'); try { return dec.decode(packet.slice(HEADER)); } catch(e){ throw new Error('Testo UTF-8 non valido'); } }

  async function sha256Bytes(bytes){ if (!(bytes instanceof Uint8Array)) bytes = Uint8Array.from(bytes); const digest = await globalThis.crypto.subtle.digest('SHA-256', bytes); return new Uint8Array(digest); }
  async function sha256Hex(bytes){ return hex(await sha256Bytes(bytes)); }

  async function packOriginalFile({name, mime, bytes}){
    if (!(bytes instanceof Uint8Array)) bytes = Uint8Array.from(bytes || []);
    name = String(name || 'file.bin'); mime = String(mime || 'application/octet-stream');
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
    if (!(packet instanceof Uint8Array)) packet = Uint8Array.from(packet || []);
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

  function describeAsFraction(packet){ if (!(packet instanceof Uint8Array)) packet=Uint8Array.from(packet||[]); const preview=packet.slice(0, Math.min(16, packet.length)); return {packetBytes:packet.length, denominatorExponent:packet.length*8, numeratorHexPreview:'0x'+hex(preview)+(packet.length>16?'…':'')}; }

  return {TYPE_TEXT, TYPE_ORIGINAL_FILE, encodeText, decodeTextFraction, packOriginalFile, unpackOriginalFile, sha256Hex, describeAsFraction};
});
