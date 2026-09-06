'use strict';
const $=id=>document.getElementById(id);
const MAX_FILE_BYTES=100*1024*1024;
const MAX_QR_FILE_BYTES=100*1024;
let originalFile=null, fra1Packet=null, fra1ePacket=null, restored=null;
let selectedContainer=null, selectedInfo=null;
let originalUrl=null, restoredUrl=null, fra1Url=null, fra1eUrl=null;
let qrOriginalFile=null, qrFra1e=null, qrFrames=[], qrIndex=0, qrTimer=null, qrExportUrl=null;

function sizeText(n){ if(n<1024)return `${n} B`; if(n<1024*1024)return `${(n/1024).toFixed(1)} KB`; return `${(n/1024/1024).toFixed(2)} MB`; }
function setStatus(el,msg,error=false){ el.textContent=msg; el.className='status '+(error?'error':'ok'); }
function revoke(ref){ if(ref) URL.revokeObjectURL(ref); }
function previewFile(file,img,oldUrlSetter){ const url=URL.createObjectURL(file); img.src=url; img.style.display='block'; oldUrlSetter(url); }
function passwordLength(s){ return Array.from(String(s||'')).length; }

async function ensureFra1Packet(){
  if(fra1Packet)return fra1Packet;
  if(!originalFile)throw new Error('Scegli prima una foto originale');
  const bytes=new Uint8Array(await originalFile.arrayBuffer());
  fra1Packet=await FractionCodec.packOriginalFile({name:originalFile.name,mime:originalFile.type||'application/octet-stream',bytes});
  return fra1Packet;
}

function prepareRestored(decoded){
  restored=decoded;
  revoke(restoredUrl);
  const blob=new Blob([restored.bytes],{type:restored.mime||'application/octet-stream'});
  restoredUrl=URL.createObjectURL(blob);
  $('downloadOriginal').disabled=false;
  if((restored.mime||'').startsWith('image/')){ $('restoredPreview').src=restoredUrl; $('restoredPreview').style.display='block'; }
  setStatus($('restoreStatus'),`SHA-256 verificato ✓ • recuperato ${restored.name} • ${sizeText(restored.bytes.length)} • file identico byte-per-byte.`);
}

function stopQrAnimation(){
  if(qrTimer!==null){ clearInterval(qrTimer); qrTimer=null; }
}

function renderQrFrame(){
  if(!qrFrames.length)return;
  if(typeof QRCode!=='function')throw new Error('Renderer QR non disponibile');
  qrIndex=((qrIndex%qrFrames.length)+qrFrames.length)%qrFrames.length;
  const target=$('qrCanvas');
  target.innerHTML='';
  new QRCode(target,{
    text:qrFrames[qrIndex],
    width:800,
    height:800,
    correctLevel:QRCode.CorrectLevel.M
  });
  $('qrStage').style.display='flex';
  $('qrFrameLabel').textContent=`Frame ${qrIndex+1} / ${qrFrames.length}`;
}

function startQrAnimation(){
  if(!qrFrames.length)return;
  stopQrAnimation();
  renderQrFrame();
  const interval=Number($('qrInterval').value)||350;
  qrTimer=setInterval(()=>{
    qrIndex=(qrIndex+1)%qrFrames.length;
    try{ renderQrFrame(); }
    catch(e){ stopQrAnimation(); setStatus($('qrStatus'),e.message,true); }
  },interval);
}

function resetQrGenerated(){
  stopQrAnimation();
  qrFra1e=null; qrFrames=[]; qrIndex=0;
  $('qrCanvas').innerHTML=''; $('qrStage').style.display='none'; $('qrFrameLabel').textContent='Nessun frame';
  $('qrPlay').disabled=true; $('qrPause').disabled=true; $('qrExport').disabled=true;
  revoke(qrExportUrl); qrExportUrl=null;
}

$('encodeText').addEventListener('click',()=>{ try{ $('textFraction').value=FractionCodec.encodeText($('textInput').value); setStatus($('textStatus'),'Testo codificato.'); }catch(e){setStatus($('textStatus'),e.message,true);} });
$('decodeText').addEventListener('click',()=>{ try{ $('textInput').value=FractionCodec.decodeTextFraction($('textFraction').value); setStatus($('textStatus'),'Frazione decodificata nel testo originale.'); }catch(e){setStatus($('textStatus'),e.message,true);} });

$('originalInput').addEventListener('change',()=>{
  const file=$('originalInput').files?.[0]; if(!file)return;
  originalFile=file; fra1Packet=null; fra1ePacket=null;
  revoke(fra1Url); fra1Url=null; revoke(fra1eUrl); fra1eUrl=null;
  $('downloadFra1').disabled=true; $('downloadFra1e').disabled=true;
  const tooLarge=file.size>MAX_FILE_BYTES;
  $('makeFra1').disabled=tooLarge; $('makeFra1e').disabled=tooLarge;
  $('originalInfo').textContent=`${file.name} • ${sizeText(file.size)} • ${file.type||'tipo non dichiarato'}`;
  revoke(originalUrl); previewFile(file,$('originalPreview'),u=>originalUrl=u);
  $('fractionDescription').style.display='none'; $('protectedDescription').style.display='none';
  if(tooLarge){
    setStatus($('encodeStatus'),'File oltre 100 MB: limite prudenziale del browser mobile.',true);
    setStatus($('protectStatus'),'File oltre 100 MB: limite prudenziale del browser mobile.',true);
  }else{
    setStatus($('encodeStatus'),'Foto originale pronta. Nessun ridimensionamento verrà applicato.');
    setStatus($('protectStatus'),'Pronta per FRA1E. Inserisci una password di almeno 12 caratteri.');
  }
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
    $('fractionDescription').textContent=`N / 2^${d.denominatorExponent.toLocaleString('it-IT')}  •  FRA1: ${sizeText(d.packetBytes)}  •  originale: ${sizeText(originalFile.size)}  •  N (anteprima): ${d.numeratorHexPreview}  •  SHA-256 originale: ${hash}`;
    setStatus($('encodeStatus'),'FRA1 creato: contiene il file originale completo byte-per-byte.');
  }catch(e){setStatus($('encodeStatus'),e.message,true);}finally{btn.disabled=false;btn.textContent='CREA FRA1 LOSSLESS';}
});

$('downloadFra1').addEventListener('click',()=>{
  if(!fra1Packet||!fra1Url||!originalFile)return; const a=document.createElement('a'); a.href=fra1Url; a.download=originalFile.name+'.fra1'; a.click();
});

$('makeFra1e').addEventListener('click',async()=>{
  if(!originalFile)return;
  const password=$('protectPassword').value;
  if(passwordLength(password)<12){ setStatus($('protectStatus'),'Password troppo corta: usa almeno 12 caratteri; consigliati 16 o più.',true); return; }
  const keyBits=Number($('aesBits').value);
  const btn=$('makeFra1e'); btn.disabled=true; btn.textContent='CIFRATURA IN CORSO…';
  try{
    const plain=await ensureFra1Packet();
    fra1ePacket=await FractionCodec.encryptFra1(plain,password,keyBits);
    revoke(fra1eUrl); fra1eUrl=URL.createObjectURL(new Blob([fra1ePacket],{type:'application/octet-stream'}));
    $('downloadFra1e').disabled=false;
    const overhead=fra1ePacket.length-plain.length;
    $('protectedDescription').style.display='block';
    $('protectedDescription').textContent=`AES-${keyBits} • originale ${sizeText(originalFile.size)} • FRA1 ${sizeText(plain.length)} • FRA1E ${sizeText(fra1ePacket.length)} • sovraccarico cifratura +${overhead} B • PBKDF2 ${FractionCodec.DEFAULT_PBKDF2_ITERATIONS.toLocaleString('it-IT')} iterazioni`;
    setStatus($('protectStatus'),`FRA1E AES-${keyBits} creato. Password e foto sono rimaste in questo browser.`);
  }catch(e){ setStatus($('protectStatus'),e.message,true); }
  finally{ btn.disabled=false; btn.textContent='CREA FRA1E PROTETTO'; }
});

$('downloadFra1e').addEventListener('click',()=>{
  if(!fra1ePacket||!fra1eUrl||!originalFile)return; const a=document.createElement('a'); a.href=fra1eUrl; a.download=originalFile.name+'.fra1e'; a.click();
});

$('qrInput').addEventListener('change',()=>{
  const file=$('qrInput').files?.[0];
  qrOriginalFile=file||null;
  resetQrGenerated();
  $('qrRestore').disabled=true; $('qrDownload').disabled=true;
  if(!file){ $('qrInputInfo').textContent='Nessun file scelto'; $('qrGenerate').disabled=true; return; }
  $('qrInputInfo').textContent=`${file.name} • ${sizeText(file.size)} • ${file.type||'tipo non dichiarato'}`;
  if(file.size>MAX_QR_FILE_BYTES){
    $('qrGenerate').disabled=true;
    setStatus($('qrStatus'),'FRA1-QR v0 accetta al massimo 100 KB.',true);
  }else{
    $('qrGenerate').disabled=false;
    setStatus($('qrStatus'),'File pronto. Inserisci una password e genera la sequenza FRA1E AES-256 → QR.');
  }
});

$('qrGenerate').addEventListener('click',async()=>{
  if(!qrOriginalFile)return;
  const password=$('qrPassword').value;
  if(passwordLength(password)<12){ setStatus($('qrStatus'),'Password troppo corta: usa almeno 12 caratteri; consigliati 16 o più.',true); return; }
  if(qrOriginalFile.size>MAX_QR_FILE_BYTES){ setStatus($('qrStatus'),'File oltre 100 KB.',true); return; }
  const btn=$('qrGenerate');
  btn.disabled=true; btn.textContent='CIFRO E GENERO…'; stopQrAnimation();
  try{
    const bytes=new Uint8Array(await qrOriginalFile.arrayBuffer());
    const fra1=await FractionCodec.packOriginalFile({name:qrOriginalFile.name,mime:qrOriginalFile.type||'application/octet-stream',bytes});
    qrFra1e=await FractionCodec.encryptFra1(fra1,password,256);
    const split=await FRA1QR.split(qrFra1e,{payloadBytes:1350});
    qrFrames=split.frames; qrIndex=0;
    renderQrFrame();
    $('qrPlay').disabled=false; $('qrPause').disabled=false; $('qrExport').disabled=false;
    setStatus($('qrStatus'),`Creati ${qrFrames.length} QR • originale ${sizeText(bytes.length)} • FRA1E ${sizeText(qrFra1e.length)} • AES-256. I QR contengono solo dati cifrati.`);
  }catch(e){ resetQrGenerated(); setStatus($('qrStatus'),e.message,true); }
  finally{ btn.disabled=false; btn.textContent='GENERA QR CIFRATI'; }
});

$('qrPlay').addEventListener('click',()=>{
  try{ startQrAnimation(); setStatus($('qrStatus'),`Animazione avviata: ${qrFrames.length} frame in ciclo.`); }
  catch(e){ setStatus($('qrStatus'),e.message,true); }
});
$('qrPause').addEventListener('click',()=>{ stopQrAnimation(); if(qrFrames.length)setStatus($('qrStatus'),`Animazione in pausa al frame ${qrIndex+1}/${qrFrames.length}.`); });
$('qrInterval').addEventListener('change',()=>{ if(qrTimer!==null) startQrAnimation(); });

$('qrExport').addEventListener('click',()=>{
  if(!qrFrames.length)return;
  const data={format:'FRA1-QR-v0',createdAt:new Date().toISOString(),frameCount:qrFrames.length,frames:qrFrames};
  const blob=new Blob([JSON.stringify(data,null,2)],{type:'application/json'});
  revoke(qrExportUrl); qrExportUrl=URL.createObjectURL(blob);
  const a=document.createElement('a'); a.href=qrExportUrl; a.download=(qrOriginalFile?.name||'frazione-lab')+'.fra1qr.json'; a.click();
  setStatus($('qrStatus'),`Esportato set di ${qrFrames.length} frame JSON. Il contenuto resta FRA1E AES-256 cifrato.`);
});

$('fra1Input').addEventListener('change',async()=>{
  const file=$('fra1Input').files?.[0]; if(!file)return;
  restored=null; selectedContainer=null; selectedInfo=null;
  $('downloadOriginal').disabled=true; $('restoreBtn').disabled=true; $('restoredPreview').style.display='none';
  $('containerBadge').style.display='none'; $('restorePasswordRow').style.display='none'; $('restorePassword').value='';
  $('fra1Info').textContent=`${file.name} • ${sizeText(file.size)}`;
  if(file.size>MAX_FILE_BYTES+2*1024*1024){ setStatus($('restoreStatus'),'File oltre il limite prudenziale del browser mobile.',true); return; }
  setStatus($('restoreStatus'),'Leggo l’intestazione del contenitore…');
  try{
    selectedContainer=new Uint8Array(await file.arrayBuffer());
    selectedInfo=FractionCodec.inspectContainer(selectedContainer);
    if(selectedInfo.kind==='unknown')throw new Error('Il file non è un contenitore FRA1/FRA1E riconosciuto');
    const badge=$('containerBadge'); badge.style.display='inline-block';
    if(selectedInfo.encrypted){
      badge.textContent=`FRA1E • AES-${selectedInfo.keyBits}`;
      $('restorePasswordRow').style.display='flex';
      setStatus($('restoreStatus'),`FRA1E riconosciuto automaticamente: AES-${selectedInfo.keyBits}, PBKDF2 ${selectedInfo.iterations.toLocaleString('it-IT')} iterazioni. Inserisci la password.`);
    }else{
      badge.textContent='FRA1 • non cifrato';
      setStatus($('restoreStatus'),'FRA1 normale riconosciuto. Pronto per verifica SHA-256 e recupero.');
    }
    $('restoreBtn').disabled=false;
  }catch(e){ selectedContainer=null; selectedInfo=null; setStatus($('restoreStatus'),e.message,true); }
});

$('restoreBtn').addEventListener('click',async()=>{
  if(!selectedContainer||!selectedInfo)return;
  const btn=$('restoreBtn'); btn.disabled=true;
  try{
    let packet=selectedContainer;
    if(selectedInfo.encrypted){
      const password=$('restorePassword').value;
      if(!password)throw new Error('Inserisci la password del FRA1E');
      btn.textContent=`DECIFRO AES-${selectedInfo.keyBits}…`;
      packet=await FractionCodec.decryptFra1(selectedContainer,password);
    }else btn.textContent='VERIFICA SHA-256…';
    const decoded=await FractionCodec.unpackOriginalFile(packet);
    prepareRestored(decoded);
  }catch(e){ restored=null; $('downloadOriginal').disabled=true; setStatus($('restoreStatus'),e.message,true); }
  finally{btn.disabled=false;btn.textContent='RECUPERA ORIGINALE';}
});

$('downloadOriginal').addEventListener('click',()=>{ if(!restored||!restoredUrl)return; const a=document.createElement('a'); a.href=restoredUrl; a.download=restored.name; a.click(); });
