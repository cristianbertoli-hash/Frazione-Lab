'use strict';
const $=id=>document.getElementById(id);
const MAX_QR_FILE_BYTES=100*1024;
let sourceFile=null;
let fra1eBytes=null;
let frames=[];
let frameIndex=0;
let timer=null;
let restored=null;
let restoredUrl=null;
let exportUrl=null;

function sizeText(n){ if(n<1024)return `${n} B`; if(n<1024*1024)return `${(n/1024).toFixed(1)} KB`; return `${(n/1024/1024).toFixed(2)} MB`; }
function setStatus(msg,error=false){ const el=$('qrStatus'); el.textContent=msg; el.className='status '+(error?'error':'ok'); }
function passwordLength(s){ return Array.from(String(s||'')).length; }
function stop(){ if(timer!==null){ clearInterval(timer); timer=null; } }
function revoke(url){ if(url) URL.revokeObjectURL(url); }

function renderFrame(){
  if(!frames.length)return;
  if(typeof QRCode!=='function')throw new Error('Renderer QR non disponibile');
  frameIndex=((frameIndex%frames.length)+frames.length)%frames.length;
  const box=$('qrCanvas');
  box.innerHTML='';
  new QRCode(box,{text:frames[frameIndex],width:800,height:800,correctLevel:QRCode.CorrectLevel.M});
  $('qrFrameLabel').textContent=`Frame ${frameIndex+1} / ${frames.length}`;
  $('qrStage').style.display='block';
}

function play(){
  if(!frames.length)return;
  stop();
  renderFrame();
  const interval=Number($('qrInterval').value)||350;
  timer=setInterval(()=>{ frameIndex=(frameIndex+1)%frames.length; try{renderFrame();}catch(e){stop();setStatus(e.message,true);} },interval);
}

function resetGenerated(){
  stop(); fra1eBytes=null; frames=[]; frameIndex=0;
  $('qrCanvas').innerHTML=''; $('qrStage').style.display='none'; $('qrFrameLabel').textContent='Nessun frame';
  $('qrPlay').disabled=true; $('qrPause').disabled=true; $('qrExport').disabled=true;
  revoke(exportUrl); exportUrl=null;
}

$('qrInput').addEventListener('change',()=>{
  sourceFile=$('qrInput').files?.[0]||null;
  resetGenerated();
  if(!sourceFile){ $('qrInputInfo').textContent='Nessun file scelto'; $('qrGenerate').disabled=true; return; }
  $('qrInputInfo').textContent=`${sourceFile.name} • ${sizeText(sourceFile.size)} • ${sourceFile.type||'tipo non dichiarato'}`;
  if(sourceFile.size>MAX_QR_FILE_BYTES){ $('qrGenerate').disabled=true; setStatus('FRA1-QR v0 accetta al massimo 100 KB.',true); }
  else { $('qrGenerate').disabled=false; setStatus('File pronto. Inserisci una password e genera la sequenza QR cifrata.'); }
});

$('qrGenerate').addEventListener('click',async()=>{
  if(!sourceFile)return;
  const password=$('qrPassword').value;
  if(passwordLength(password)<12){ setStatus('Password troppo corta: minimo 12 caratteri, consigliati 16 o più.',true); return; }
  const btn=$('qrGenerate'); btn.disabled=true; btn.textContent='CIFRO E GENERO…';
  stop();
  try{
    const original=new Uint8Array(await sourceFile.arrayBuffer());
    const fra1=await FractionCodec.packOriginalFile({name:sourceFile.name,mime:sourceFile.type||'application/octet-stream',bytes:original});
    fra1eBytes=await FractionCodec.encryptFra1(fra1,password,256);
    const split=await FRA1QR.split(fra1eBytes,{payloadBytes:1350});
    frames=split.frames; frameIndex=0;
    renderFrame();
    $('qrPlay').disabled=false; $('qrPause').disabled=false; $('qrExport').disabled=false;
    setStatus(`Creati ${frames.length} QR • originale ${sizeText(original.length)} • FRA1E ${sizeText(fra1eBytes.length)} • AES-256. I QR contengono solo dati cifrati.`);
  }catch(e){ resetGenerated(); setStatus(e.message,true); }
  finally{ btn.disabled=false; btn.textContent='GENERA QR CIFRATI'; }
});

$('qrPlay').addEventListener('click',()=>{ try{play(); setStatus(`Animazione avviata: ${frames.length} frame in ciclo.`);}catch(e){setStatus(e.message,true);} });
$('qrPause').addEventListener('click',()=>{ stop(); if(frames.length)setStatus(`Animazione in pausa al frame ${frameIndex+1}/${frames.length}.`); });
$('qrInterval').addEventListener('change',()=>{ if(timer!==null)play(); });

$('qrExport').addEventListener('click',()=>{
  if(!frames.length)return;
  const data={format:'FRA1-QR-v0',createdAt:new Date().toISOString(),frameCount:frames.length,frames};
  const blob=new Blob([JSON.stringify(data,null,2)],{type:'application/json'});
  revoke(exportUrl); exportUrl=URL.createObjectURL(blob);
  const a=document.createElement('a'); a.href=exportUrl; a.download=(sourceFile?.name||'frazione-lab')+'.fra1qr.json'; a.click();
  setStatus(`Esportato set di ${frames.length} frame. Il contenuto resta cifrato AES-256.`);
});

$('qrImport').addEventListener('click',()=>$('qrImportInput').click());
$('qrImportInput').addEventListener('change',async()=>{
  const file=$('qrImportInput').files?.[0]; if(!file)return;
  restored=null; $('qrDownload').disabled=true; revoke(restoredUrl); restoredUrl=null;
  try{
    const imported=FRA1QR.parseFrameSetJson(await file.text());
    const rebuilt=FRA1QR.reassemble(imported);
    const info=FractionCodec.inspectContainer(rebuilt);
    if(info.kind!=='fra1e' || info.keyBits!==256)throw new Error('Il set QR non ricostruisce un FRA1E AES-256 valido');
    frames=imported; fra1eBytes=rebuilt; frameIndex=0; renderFrame();
    $('qrPlay').disabled=false; $('qrPause').disabled=false; $('qrExport').disabled=false; $('qrRestore').disabled=false;
    setStatus(`Set importato: ${frames.length} frame • FRA1E AES-256 ricostruito. Inserisci la password per recuperare l'originale.`);
  }catch(e){ fra1eBytes=null; frames=[]; $('qrRestore').disabled=true; setStatus(e.message,true); }
});

$('qrRestore').addEventListener('click',async()=>{
  if(!fra1eBytes)return;
  const password=$('qrRestorePassword').value;
  if(!password){ setStatus('Inserisci la password del FRA1E.',true); return; }
  const btn=$('qrRestore'); btn.disabled=true; btn.textContent='DECIFRO E VERIFICO…';
  try{
    const fra1=await FractionCodec.decryptFra1(fra1eBytes,password);
    restored=await FractionCodec.unpackOriginalFile(fra1);
    revoke(restoredUrl);
    restoredUrl=URL.createObjectURL(new Blob([restored.bytes],{type:restored.mime||'application/octet-stream'}));
    $('qrDownload').disabled=false;
    setStatus(`SHA-256 verificato ✓ • recuperato ${restored.name} • ${sizeText(restored.bytes.length)} • identico byte-per-byte.`);
  }catch(e){ restored=null; $('qrDownload').disabled=true; setStatus(e.message,true); }
  finally{ btn.disabled=false; btn.textContent='RECUPERA ORIGINALE'; }
});

$('qrDownload').addEventListener('click',()=>{
  if(!restored||!restoredUrl)return;
  const a=document.createElement('a'); a.href=restoredUrl; a.download=restored.name; a.click();
});
