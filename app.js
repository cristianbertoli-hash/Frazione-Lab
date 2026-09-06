'use strict';
const $=id=>document.getElementById(id);
const MAX_FILE_BYTES=100*1024*1024;
let originalFile=null, fra1Packet=null, restored=null;
let originalUrl=null, restoredUrl=null, fra1Url=null;

function sizeText(n){ if(n<1024)return `${n} B`; if(n<1024*1024)return `${(n/1024).toFixed(1)} KB`; return `${(n/1024/1024).toFixed(2)} MB`; }
function setStatus(el,msg,error=false){ el.textContent=msg; el.className='status '+(error?'error':'ok'); }
function revoke(ref){ if(ref) URL.revokeObjectURL(ref); }
function previewFile(file,img,oldUrlSetter){ const url=URL.createObjectURL(file); img.src=url; img.style.display='block'; oldUrlSetter(url); }

$('encodeText').addEventListener('click',()=>{ try{ $('textFraction').value=FractionCodec.encodeText($('textInput').value); setStatus($('textStatus'),'Testo codificato.'); }catch(e){setStatus($('textStatus'),e.message,true);} });
$('decodeText').addEventListener('click',()=>{ try{ $('textInput').value=FractionCodec.decodeTextFraction($('textFraction').value); setStatus($('textStatus'),'Frazione decodificata nel testo originale.'); }catch(e){setStatus($('textStatus'),e.message,true);} });

$('originalInput').addEventListener('change',()=>{
  const file=$('originalInput').files?.[0]; if(!file)return;
  originalFile=file; fra1Packet=null; $('downloadFra1').disabled=true; $('makeFra1').disabled=file.size>MAX_FILE_BYTES;
  $('originalInfo').textContent=`${file.name} • ${sizeText(file.size)} • ${file.type||'tipo non dichiarato'}`;
  revoke(originalUrl); previewFile(file,$('originalPreview'),u=>originalUrl=u);
  $('fractionDescription').style.display='none';
  if(file.size>MAX_FILE_BYTES) setStatus($('encodeStatus'),'File oltre 100 MB: limite prudenziale del browser mobile.',true); else setStatus($('encodeStatus'),'Foto originale pronta. Nessun ridimensionamento verrà applicato.');
});

$('makeFra1').addEventListener('click',async()=>{
  if(!originalFile)return; const btn=$('makeFra1'); btn.disabled=true; btn.textContent='CALCOLO SHA-256…';
  try{
    const bytes=new Uint8Array(await originalFile.arrayBuffer());
    fra1Packet=await FractionCodec.packOriginalFile({name:originalFile.name,mime:originalFile.type||'application/octet-stream',bytes});
    const hash=await FractionCodec.sha256Hex(bytes); const d=FractionCodec.describeAsFraction(fra1Packet);
    revoke(fra1Url); fra1Url=URL.createObjectURL(new Blob([fra1Packet],{type:'application/octet-stream'}));
    $('downloadFra1').disabled=false;
    $('fractionDescription').style.display='block';
    $('fractionDescription').textContent=`N / 2^${d.denominatorExponent.toLocaleString('it-IT')}  •  pacchetto: ${sizeText(d.packetBytes)}  •  N (anteprima esadecimale): ${d.numeratorHexPreview}  •  SHA-256 originale: ${hash}`;
    setStatus($('encodeStatus'),'FRA1 creato: contiene il file originale completo byte-per-byte.');
  }catch(e){setStatus($('encodeStatus'),e.message,true);}finally{btn.disabled=false;btn.textContent='CREA FRA1 LOSSLESS';}
});

$('downloadFra1').addEventListener('click',()=>{
  if(!fra1Packet||!fra1Url||!originalFile)return; const a=document.createElement('a'); a.href=fra1Url; a.download=originalFile.name+'.fra1'; a.click();
});

$('fra1Input').addEventListener('change',()=>{
  const file=$('fra1Input').files?.[0]; if(!file)return; restored=null; $('downloadOriginal').disabled=true; $('restoreBtn').disabled=file.size>MAX_FILE_BYTES+1024*1024;
  $('fra1Info').textContent=`${file.name} • ${sizeText(file.size)}`; $('restoredPreview').style.display='none';
  if($('restoreBtn').disabled) setStatus($('restoreStatus'),'FRA1 oltre il limite prudenziale del browser mobile.',true); else setStatus($('restoreStatus'),'FRA1 pronto per la verifica e il recupero.');
});

$('restoreBtn').addEventListener('click',async()=>{
  const file=$('fra1Input').files?.[0]; if(!file)return; const btn=$('restoreBtn'); btn.disabled=true; btn.textContent='VERIFICA SHA-256…';
  try{
    const packet=new Uint8Array(await file.arrayBuffer()); restored=await FractionCodec.unpackOriginalFile(packet);
    revoke(restoredUrl); const blob=new Blob([restored.bytes],{type:restored.mime||'application/octet-stream'}); restoredUrl=URL.createObjectURL(blob);
    $('downloadOriginal').disabled=false;
    if((restored.mime||'').startsWith('image/')){ $('restoredPreview').src=restoredUrl; $('restoredPreview').style.display='block'; }
    setStatus($('restoreStatus'),`SHA-256 verificato ✓ • recuperato ${restored.name} • ${sizeText(restored.bytes.length)} • file identico byte-per-byte.`);
  }catch(e){ restored=null; setStatus($('restoreStatus'),e.message,true); }
  finally{btn.disabled=false;btn.textContent='RECUPERA ORIGINALE';}
});

$('downloadOriginal').addEventListener('click',()=>{ if(!restored||!restoredUrl)return; const a=document.createElement('a'); a.href=restoredUrl; a.download=restored.name; a.click(); });
