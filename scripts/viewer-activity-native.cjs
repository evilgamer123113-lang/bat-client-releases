'use strict';
const {app,BrowserWindow}=require('electron');
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),privateRoot='D:/BatClientBuildCache/viewer-activity-native-20261027';
fs.mkdirSync(privateRoot,{recursive:true});app.setPath('userData',privateRoot);
app.whenReady().then(async()=>{
 let window;
 try{
  window=new BrowserWindow({show:false,width:500,height:500,webPreferences:{nodeIntegration:true,contextIsolation:false,backgroundThrottling:false,offscreen:true}});
  window.webContents.on('console-message',(_event,_level,message)=>console.log('TEST_RENDERER',message));
  await window.loadURL('data:text/html,<html><body><div id="panel"><canvas id="avatar"></canvas></div></body></html>');
  const result=await window.webContents.executeJavaScript(`(async()=>{
   const assert=require('node:assert/strict');
   const {SkinViewer}=require(${JSON.stringify(path.join(root,'libs/skinview3d.bundle.js'))});
   const {createViewerActivity}=require(${JSON.stringify(path.join(root,'launcher/viewer-activity.js'))});
   const {applyViewerActivity}=require(${JSON.stringify(path.join(root,'scripts/apply-viewer-activity.cjs'))});
   const html=applyViewerActivity(require('node:fs').readFileSync(${JSON.stringify(path.join(root,'index.html'))},'utf8'));
   const start=html.indexOf('        function attachAnimatedCape('),end=html.indexOf(String.fromCharCode(10)+'        }',start)+10;
   const attach=eval('('+html.slice(start,end).trim()+')');
   globalThis.safeLoadCape=()=>{};globalThis.capeCanvasSizeFor=im=>({w:im.width,h:im.height});
   let foreground=false;Object.defineProperty(document,'hidden',{configurable:true,get:()=>false});document.hasFocus=()=>foreground;
   const viewer=new SkinViewer({canvas:document.querySelector('#avatar'),width:300,height:340});
   let draws=0,uploads=0;const draw=viewer.draw.bind(viewer);viewer.draw=()=>{draws++;return draw();};
   const upload=viewer.recreateCapeTexture?.bind(viewer);viewer.recreateCapeTexture=()=>{uploads++;upload?.();};
   const wait=ms=>new Promise(r=>setTimeout(r,ms));await wait(700);const legacyDraws=draws;assert.ok(legacyDraws>0,'legacy hidden renderer was exercised');
   const manager=createViewerActivity({window,document});manager.track(viewer);assert.equal(viewer.renderPaused,true);assert.equal(viewer.animationID,null);
   const pixels=document.createElement('canvas');pixels.width=64;pixels.height=32;const brush=pixels.getContext('2d');brush.fillStyle='red';brush.fillRect(0,0,64,32);const red=pixels.toDataURL();brush.fillStyle='blue';brush.fillRect(0,0,64,32);const blue=pixels.toDataURL();
   attach(viewer,null,[red,blue],[50,50]);await wait(350);const stopped=draws;assert.equal(uploads,0);await wait(250);assert.equal(draws,stopped);
   foreground=true;manager.refresh();await wait(1200);assert.ok(draws>stopped,'visible rendering resumes '+JSON.stringify({paused:viewer.renderPaused,rect:viewer.canvas.getBoundingClientRect().toJSON(),width:innerWidth,height:innerHeight,focus:document.hasFocus(),hidden:document.hidden}));assert.ok(uploads>1);
   foreground=false;manager.refresh();const pausedDraws=draws,pausedUploads=uploads;await wait(300);assert.equal(draws,pausedDraws);assert.equal(uploads,pausedUploads);
   foreground=true;document.querySelector('#panel').style.display='none';manager.refresh();assert.equal(viewer.renderPaused,true);
   document.querySelector('#panel').style.display='block';manager.refresh();assert.equal(viewer.renderPaused,false);await wait(150);
   manager.dispose();viewer.dispose();return {legacyHiddenDraws:legacyDraws,totalDraws:draws,totalCapeUploads:uploads,backgroundDrawDelta:0,backgroundUploadDelta:0,capeResumed:true,hiddenPanelPaused:true};
  })().catch(error=>({error:String(error),stack:error.stack}))`);
  if(result.error)throw Error(result.error+' '+result.stack);
  fs.writeFileSync(path.join(privateRoot,'result.json'),JSON.stringify(result,null,2));console.log('VIEWER_ACTIVITY_NATIVE_OK',JSON.stringify(result));
  window.destroy();app.quit();
 }catch(error){console.error('VIEWER_ACTIVITY_NATIVE_FAILED',String(error));if(window&&!window.isDestroyed())window.destroy();app.exit(1);}
});
