const {app,BrowserWindow}=require('electron');
const path=require('node:path'),fs=require('node:fs');
const {createPreviewServer}=require('./preview-server.cjs');
app.setPath('userData','D:/BatClientBuildCache/launch-gallery-smoke'+(process.argv.includes('--launch-failure')?'-failure':''));
app.commandLine.appendSwitch('disable-gpu');
app.whenReady().then(async()=>{
 const server=createPreviewServer();await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const failure=process.argv.includes('--launch-failure');
 const win=new BrowserWindow({show:false,width:1320,height:900,webPreferences:{preload:path.join(__dirname,'mods-smoke-preload.cjs'),additionalArguments:failure?['--launch-failure']:[],contextIsolation:true,sandbox:true,nodeIntegration:false,offscreen:true}});
 win.webContents.on('console-message',(_e,_l,message)=>console.log('RENDERER',message));
 const run=async s=>{try{return await win.webContents.executeJavaScript(s)}catch(e){throw Error(s+' :: '+e)}},settle=()=>new Promise(r=>setTimeout(r,180));
 async function until(s){for(let i=0;i<65;i++){if(await run(s))return;await settle();}throw Error('Timed out: '+s);}
 async function check(s,label){if(!await run(s))throw Error(label);}
 try{
 await win.loadURL(`http://127.0.0.1:${server.address().port}/`);await until(`document.querySelector('#startup-screen').hidden`);
 await run(`document.querySelector('[data-page="mods"]').click()`);await until(`!!document.querySelector('.modrinth-card')`);
 await run(`[...document.querySelectorAll('.modrinth-card button')].find(b=>b.textContent==='Details').click()`);await until(`!!document.querySelector('.mod-gallery-open')`);
 await run(`document.querySelector('.mod-gallery-open').click()`);await until(`document.querySelector('.mod-screenshot-viewer')?.open`);
 await check(`document.querySelector('.screenshot-head span').textContent==='1 / 2'`,'Screenshot counter');
 await run(`document.querySelector('.mod-screenshot-viewer').dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true}))`);
 await check(`document.querySelector('.screenshot-head span').textContent==='2 / 2'`,'Screenshot keyboard navigation');
 await run(`[...document.querySelectorAll('.screenshot-controls button')].find(b=>b.textContent==='Actual size').click()`);
 await check(`document.querySelector('.screenshot-image-wrap').classList.contains('is-zoomed')`,'Screenshot zoom');
 await settle();fs.writeFileSync(path.join(__dirname,'../output/mod-screenshot-viewer-preview.png'),(await win.webContents.capturePage()).toPNG());
 await run(`document.querySelector('.mod-screenshot-viewer').dispatchEvent(new Event('cancel',{cancelable:true}))`);await until(`!document.querySelector('.mod-screenshot-viewer')`);
 await check(`document.querySelector('.mod-details').open`,'Escape should preserve parent');
 await run(`document.body.classList.add('reduce-motion');document.querySelector('.mod-gallery-open').click()`);
 await check(`getComputedStyle(document.querySelector('.mod-screenshot-viewer')).animationName==='none'`,'Viewer reduced motion');
 await run(`document.querySelector('.mod-details').close()`);await until(`!document.querySelector('.mod-screenshot-viewer')`);
 await run(`document.querySelector('[data-page="loadouts"]').click()`);await until(`!!document.querySelector('.loadout-card')`);
 await check(`!document.querySelector('.launch-dock')&&!document.querySelector('.loadout-launch')`,'Launch controls must be Home only');
 await run(`document.querySelector('[data-page="home"]').click()`);
 await run(`document.querySelector('#play-button').click()`);
 await until(`window.modsSmokeCalls().some(c=>c.command==='launch_installed_game')`);
 await check(`!document.querySelector('#launch-progress').hidden&&document.querySelector('.launch-busy-mark')`,'Actual startup progress');
 if(failure){
 await until(`document.querySelector('#launch-error')?.open`);
 await check(`document.querySelector('#launch-error h2').textContent==='Java rejected the launch settings'&&document.querySelector('#launch-error pre').textContent.includes('Unrecognized VM option')`,'Structured failure details');
 await check(`document.querySelectorAll('#launch-error').length===1&&[...document.querySelectorAll('#launch-error button')].some(b=>b.textContent==='Retry')`,'Retry and deduplication');
 await settle();fs.writeFileSync(path.join(__dirname,'../output/launch-error-preview.png'),(await win.webContents.capturePage()).toPNG());
 await run(`[...document.querySelectorAll('#launch-error button')].find(b=>b.textContent==='Settings').click()`);
 await until(`!document.querySelector('#launch-error')`);
 await check(`window.modsSmokeCalls().filter(c=>c.command==='launch_installed_game').length===1`,'Launch must not duplicate');
 }else{await until(`document.querySelector('#game-launch-status').textContent==='Fixture launch'`);await check(`document.querySelector('#play-button').disabled`,'Running launch guard');}
 console.log(failure?'LAUNCH_FAILURE_ACTIONS_UI_PASS':'SCREENSHOT_VIEWER_HOME_ONLY_LAUNCH_ANIMATION_UI_PASS');
 }catch(e){fs.writeFileSync(path.join(__dirname,'../output/launch-gallery-failure.png'),(await win.webContents.capturePage()).toPNG());throw e;}
 finally{win.destroy();server.closeAllConnections();await new Promise(r=>server.close(r));app.quit();}
}).catch(e=>{console.error(e);app.exit(1)});
