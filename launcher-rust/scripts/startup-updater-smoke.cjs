const {app,BrowserWindow}=require('electron');
const path=require('node:path'),fs=require('node:fs');
const {createPreviewServer}=require('./preview-server.cjs');
app.setPath('userData','D:/BatClientBuildCache/startup-updater-smoke');app.commandLine.appendSwitch('disable-gpu');
app.on('window-all-closed',()=>{});
app.whenReady().then(async()=>{
  const server=createPreviewServer();await new Promise(r=>server.listen(0,'127.0.0.1',r));
  const wait=ms=>new Promise(r=>setTimeout(r,ms));
  try{
    for(const scenario of ['normal','current','unpublished','disabled','offline','invalid','installing']){
      const win=new BrowserWindow({show:false,width:1320,height:850,webPreferences:{preload:path.join(__dirname,'mods-smoke-preload.cjs'),contextIsolation:true,sandbox:true,nodeIntegration:false,offscreen:true,partition:'startup-'+scenario,additionalArguments:['--startup-case='+scenario]}});
      const run=s=>win.webContents.executeJavaScript(s);
      try{
        await win.loadURL(`http://127.0.0.1:${server.address().port}/`);
        if(scenario==='normal'){
          await wait(650);
          if(!await run(`!document.querySelector('#startup-screen').hidden&&document.body.classList.contains('starting')&&!document.querySelector('#startup-update-progress').hidden&&document.querySelector('#startup-update-progress').value===.5`))throw Error('Splash did not remain visible with actual update progress');
          fs.writeFileSync(path.join(__dirname,'../output/startup-update-preview.png'),(await win.webContents.capturePage()).toPNG());
        }
        if(scenario==='installing'){
          await wait(2000);
          if(!await run(`!document.querySelector('#startup-screen').hidden&&document.body.classList.contains('starting')&&document.querySelector('#startup-status').textContent.includes('restarting')`))throw Error('Home opened while native installer was restarting');
        }else{
          for(let i=0;i<40;i++){if(await run(`document.querySelector('#startup-screen').hidden`))break;await wait(150);}
          if(!await run(`document.querySelector('#startup-screen').hidden&&!document.body.classList.contains('starting')`))throw Error('Splash did not finish for '+scenario);
        }
        const calls=await run(`window.modsSmokeCalls()`),checked=calls.filter(c=>c.command==='check_launcher_update').length,installed=calls.filter(c=>c.command==='install_launcher_update').length;
        if(checked!==(scenario==='disabled'?0:1))throw Error('Duplicate/unwanted startup update check for '+scenario);
        if(installed!==(['normal','invalid','installing'].includes(scenario)?1:0))throw Error('Wrong automatic install choice for '+scenario);
        console.log('STARTUP_UPDATER_PASS '+scenario);
      }finally{win.destroy();}
    }
  }finally{server.closeAllConnections();await new Promise(r=>server.close(r));app.quit();}
}).catch(error=>{console.error(error);app.exit(1)});
