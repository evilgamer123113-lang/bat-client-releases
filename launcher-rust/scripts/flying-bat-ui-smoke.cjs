const {app,BrowserWindow}=require('electron');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {createPreviewServer}=require('./preview-server.cjs');
app.setPath('userData','D:/BatClientBuildCache/flying-bat-ui-smoke');
app.commandLine.appendSwitch('disable-gpu');

app.whenReady().then(async()=>{
  const server=createPreviewServer({getChangelog:async()=>''});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const win=new BrowserWindow({width:1320,height:900,show:false,
    webPreferences:{offscreen:true,backgroundThrottling:false,contextIsolation:true,sandbox:true}});
  const js=code=>win.webContents.executeJavaScript(code);
  const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  const transform=()=>js('document.querySelector(".flying-bat-layer canvas").style.transform');
  async function until(code){
    for(let i=0;i<90;i++){if(await js(code))return;await sleep(100);}
    throw Error('Timed out: '+code);
  }
  const url='http://127.0.0.1:'+server.address().port+'/';
  try{
    await win.loadURL(url);
    await js('localStorage.removeItem("bat-flying-preview")');
    await win.loadURL(url);
    await until('document.querySelector("#startup-screen").hidden && !document.querySelector(".flying-bat-layer").hidden');
    await js('window.dispatchEvent(new Event("focus"))');
    assert.equal(await js(`(()=>{
      const frame=BatSprite.create(()=>document.createElement('canvas'));
      const up=frame(-1,0,false), down=frame(1,0,false), folded=frame(0,1,false);
      const open=frame(0,0,false);
      const pixels=canvas=>canvas.getContext('2d').getImageData(0,0,canvas.width,canvas.height).data;
      const opaque=canvas=>pixels(canvas).filter((value,index)=>index%4===3&&value===255).length;
      return [up,down,folded,open].every(canvas=>canvas.width===44&&canvas.height===30&&
        pixels(canvas).every((value,index)=>index%4!==3||value===0||value===255))&&
        up.toDataURL()!==down.toDataURL()&&opaque(folded)<opaque(open)&&
        folded.toDataURL()!==frame(0,1,true).toDataURL();
    })()`),true);
    const start=await transform();
    await sleep(420);
    assert.notEqual(await transform(),start);
    assert.equal(await js('document.querySelector(".titlebar").textContent.includes("DEVELOPMENT BUILD")'),false);
    assert.equal(await js('getComputedStyle(document.querySelector(".flying-bat-layer canvas")).pointerEvents'),'none');

    assert.equal(await js(`(()=>{
      const canvas=document.querySelector('.flying-bat-layer canvas');
      const hit=canvas.getBoundingClientRect(), x=(hit.left+hit.right)/2, y=(hit.top+hit.bottom)/2;
      const target=document.elementFromPoint(x,y);
      const mouse=(type,button,clientX,clientY)=>new PointerEvent(type,{button,pointerId:7,clientX,clientY,bubbles:true,cancelable:true});
      if(getComputedStyle(canvas).opacity!=='1')return false;
      if(target.dispatchEvent(mouse('pointerdown',2,x,y)))return false;
      if(!document.querySelector('.flying-bat-heart').getAnimations().length)return false;
      if(target.dispatchEvent(mouse('pointerdown',0,x,y)))return false;
      target.dispatchEvent(mouse('pointermove',-1,x-165,y+80));
      const box=canvas.getBoundingClientRect();
      target.dispatchEvent(mouse('pointerup',0,x-165,y+80));
      return Math.abs((box.left+box.right)/2-x+165)<2&&Math.abs((box.top+box.bottom)/2-y-80)<2;
    })()`),true);
    await js('window.dispatchEvent(new Event("blur"))');
    const paused=await transform();
    await sleep(240);
    assert.equal(await transform(),paused);
    await js('Object.defineProperty(document,"hidden",{configurable:true,value:true});document.dispatchEvent(new Event("visibilitychange"))');
    assert.equal(await js('document.querySelector(".flying-bat-layer").hidden'),true);
    await js('delete document.hidden;document.dispatchEvent(new Event("visibilitychange"));window.dispatchEvent(new Event("focus"))');
    win.setSize(940,700);
    await sleep(250);
    assert.equal(await js(`(()=>{
      const bat=document.querySelector('.flying-bat-layer canvas').getBoundingClientRect();
      return bat.left>=-1&&bat.top>=-1&&bat.right<=innerWidth+1&&bat.bottom<=innerHeight+1;
    })()`),true);
    const beforeNav=await transform();
    await js('document.querySelector(".quick-card[data-goto=loadouts]").click()');
    assert.equal(await js('document.querySelector("#page-loadouts").classList.contains("active")&&!document.querySelector(".flying-bat-layer").hidden'),true);
    await sleep(420);
    assert.notEqual(await transform(),beforeNav);
    await js('document.querySelector("[data-page=settings]").click();document.querySelector("#settings-tab-1").click();document.querySelector(".flying-bat-settings input").click()');
    await until('JSON.parse(localStorage.getItem("bat-flying-preview"))?.enabled===false');
    await win.loadURL(url);
    await until('document.querySelector("#startup-screen").hidden');
    assert.equal(await js('document.querySelector(".flying-bat-layer").hidden'),true);
    assert.equal(await js('document.querySelector(".flying-bat-settings input").checked'),false);
    await js('document.querySelector(".flying-bat-settings input").click();document.body.classList.add("reduce-motion");window.dispatchEvent(new Event("focus"))');
    const reduced=await transform();
    await sleep(250);
    assert.equal(await transform(),reduced);
    const output=path.join(__dirname,'../output/launcher-screenshots');
    fs.mkdirSync(output,{recursive:true});
    win.setSize(1320,900);
    await sleep(450);
    fs.writeFileSync(path.join(output,'08-flying-bat.png'),(await win.webContents.capturePage()).toPNG());
    console.log('FLYING_BAT_UI_PASS');
  }finally{
    win.destroy();
    server.closeAllConnections();
    await new Promise(resolve=>server.close(resolve));
  }
}).then(()=>app.exit(0)).catch(error=>{console.error(error);app.exit(1);});
