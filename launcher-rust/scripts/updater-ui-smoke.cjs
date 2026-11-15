const {app,BrowserWindow}=require('electron');
const path=require('node:path'),fs=require('node:fs');
const {createPreviewServer}=require('./preview-server.cjs');
app.setPath('userData','D:/BatClientBuildCache/updater-ui-smoke');app.commandLine.appendSwitch('disable-gpu');
app.whenReady().then(async()=>{
  const server=createPreviewServer();await new Promise(r=>server.listen(0,'127.0.0.1',r));
  const win=new BrowserWindow({show:false,width:1320,height:850,webPreferences:{preload:path.join(__dirname,'mods-smoke-preload.cjs'),contextIsolation:true,sandbox:true,nodeIntegration:false,offscreen:true,partition:'updater-smoke'}});
  const run=code=>win.webContents.executeJavaScript(code),wait=ms=>new Promise(r=>setTimeout(r,ms));
  try{
    await win.loadURL(`http://127.0.0.1:${server.address().port}/`);
    for(let i=0;i<30;i++){if(await run(`document.querySelector('#startup-screen').hidden`))break;await wait(250);}
    await run(`document.querySelector('[data-page="settings"]').click();document.querySelector('#settings-tab-2').click();document.querySelector('.launcher-update-panel button').click()`);await wait(250);
    if(!await run(`(()=>{const p=document.querySelector('.launcher-update-panel');return p.textContent.includes('Rust update fixture')&&!p.querySelector('.mods-primary').hidden;})()`))throw Error('Available update controls missing');
    fs.writeFileSync(path.join(__dirname,'../output/updater-settings-preview.png'),(await win.webContents.capturePage()).toPNG());
    await run(`document.querySelector('.launcher-update-panel .mods-primary').click()`);await wait(800);
    if(!await run(`(()=>{const bar=document.querySelector('.launcher-update-panel progress');return !bar.hidden&&bar.value===.5;})()`))throw Error('Update progress missing');
    await wait(1000);
    if(!await run(`document.querySelector('.launcher-update-panel').textContent.includes('Fixture updated')&&document.querySelector('.launcher-update-panel progress').hidden&&window.modsSmokeCalls().some(c=>c.command==='install_launcher_update')`))throw Error('Update finish state missing');
    console.log('UPDATER_UI_PASS');
  }finally{win.destroy();server.closeAllConnections();await new Promise(r=>server.close(r));app.quit();}
}).catch(error=>{console.error(error);app.exit(1)});
