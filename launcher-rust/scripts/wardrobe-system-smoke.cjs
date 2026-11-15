const {app,BrowserWindow}=require('electron');
const path=require('node:path'),fs=require('node:fs'),assert=require('node:assert/strict');
const {createPreviewServer}=require('./preview-server.cjs');
const base='D:/BatClientBuildCache/wardrobe-system-smoke';
fs.mkdirSync(base,{recursive:true});if(fs.existsSync(base+'/state.json'))fs.renameSync(base+'/state.json',base+'/state-'+Date.now()+'.json');
app.setPath('userData',base+'/browser');app.commandLine.appendSwitch('disable-gpu');app.commandLine.appendSwitch('enable-unsafe-swiftshader');
app.whenReady().then(async()=>{
  const server=createPreviewServer();await new Promise(r=>server.listen(0,'127.0.0.1',r));
  const win=new BrowserWindow({show:false,width:1320,height:960,webPreferences:{preload:path.join(__dirname,'wardrobe-system-preload.cjs'),contextIsolation:true,sandbox:false,offscreen:true}});
  const run=s=>win.webContents.executeJavaScript(s),sleep=ms=>new Promise(r=>setTimeout(r,ms));
  async function until(s){for(let i=0;i<100;i++){if(await run(s))return;await sleep(80);}throw Error('Timed out: '+s);}
  async function open(){await until('document.querySelector("#startup-screen").hidden');await run('document.querySelector("[data-page=wardrobe]").click()');await until('document.querySelectorAll("#wardrobe-skin-panel .cosmetic-empty-slot").length===6');}
  async function upload(index){await run(`(()=>{const input=document.querySelector('#wardrobe-skin-file');input.click=()=>{};document.querySelector('#wardrobe-skin-panel .cosmetic-empty-slot[data-index="${index}"] button').click();const bytes=Uint8Array.from(atob(window.wardrobeTestSkin().split(',')[1]),c=>c.charCodeAt(0));Object.defineProperty(input,'files',{value:[new File([bytes],'My-skin.png',{type:'image/png'})],configurable:true});input.dispatchEvent(new Event('change'));})()`);}
  try{
    await win.loadURL('http://127.0.0.1:'+server.address().port+'/');await open();
    assert.equal(await run(`document.querySelectorAll('#page-wardrobe .cosmetic-collection-bar,#page-wardrobe .wardrobe-draft,#wardrobe-apply-skin,#wardrobe-apply-cape').length`),0);
    // Upload to the fifth card; saving must be automatic and must not equip.
    await upload(4);await until(`document.querySelector('#wardrobe-skin-panel .cosmetic-slot[data-index="4"]')&&!document.querySelector('#wardrobe-account-trigger').disabled`);
    let db=JSON.parse(fs.readFileSync(base+'/state.json'));assert.equal(db.accounts.Player.skin,null);assert.equal(db.accounts.Player.skinSlots[0].index,4);assert.equal(db.accounts.Player.skinSlots[0].name,'Skin 1');
    assert.equal(await run(`window.modsSmokeCalls().some(c=>c.command==='wardrobe_slot'&&c.args.action==='import'&&c.args.index===4&&c.args.imports[0].name==='')`),true);
    // Failed import stays absent; Retry completes without choosing the file again.
    await run(`window.wardrobeFailNext()`);await upload(2);await until(`!document.querySelector('#wardrobe-upload-retry').hidden&&!document.querySelector('#wardrobe-upload-retry').disabled`);
    assert.equal(await run(`document.querySelectorAll('#wardrobe-skin-panel .cosmetic-slot').length`),1);
    await run(`document.querySelector('#wardrobe-upload-retry').click()`);await until(`document.querySelector('#wardrobe-upload-retry').hidden&&!document.querySelector('#wardrobe-account-trigger').disabled`);
    assert.equal(await run(`document.querySelectorAll('#wardrobe-skin-panel .cosmetic-slot').length`),2);assert.ok(await run(`document.querySelector('#wardrobe-skin-panel .cosmetic-slot[data-index="2"]')!==null`));
    await win.reload();await until('document.querySelector("#startup-screen").hidden');await run('document.querySelector("[data-page=wardrobe]").click()');await until(`document.querySelector('#wardrobe-skin-panel .cosmetic-slot[data-index="4"]')`);
    await run(`document.querySelector('#wardrobe-skin-panel .cosmetic-slot[data-index="4"] .slot-main').click()`);await until(`document.querySelector('#wardrobe-skin-panel .cosmetic-slot.is-equipped')&&!document.querySelector('#wardrobe-account-trigger').disabled`);
    assert.ok(JSON.parse(fs.readFileSync(base+'/state.json')).accounts.Player.skin);
    await run(`document.querySelector('#wardrobe-model').value='slim';document.querySelector('#wardrobe-model').dispatchEvent(new Event('change'))`);
    await until(`document.querySelectorAll('#wardrobe-skin-panel .cosmetic-slot').length===3&&!document.querySelector('#wardrobe-account-trigger').disabled`);
    assert.equal(JSON.parse(fs.readFileSync(base+'/state.json')).accounts.Player.model,'slim');
    await run(`window.wardrobeFailNext();document.querySelector('#wardrobe-model').value='classic';document.querySelector('#wardrobe-model').dispatchEvent(new Event('change'))`);
    await until(`document.querySelector('#wardrobe-status').dataset.error==='true'&&!document.querySelector('#wardrobe-account-trigger').disabled`);
    assert.equal(await run(`document.querySelector('#wardrobe-model').value`),'slim');
    assert.equal(JSON.parse(fs.readFileSync(base+'/state.json')).accounts.Player.model,'slim');
    await run(`document.querySelector('#wardrobe-model').value='classic';document.querySelector('#wardrobe-model').dispatchEvent(new Event('change'))`);
    await until(`!document.querySelector('#wardrobe-account-trigger').disabled&&document.querySelector('#wardrobe-model').value==='classic'`);
    assert.equal(JSON.parse(fs.readFileSync(base+'/state.json')).accounts.Player.model,'classic');
    await run(`document.querySelector('#wardrobe-model').value='slim';document.querySelector('#wardrobe-model').dispatchEvent(new Event('change'))`);
    await until(`!document.querySelector('#wardrobe-account-trigger').disabled&&document.querySelector('#wardrobe-model').value==='slim'`);

    await run(`(()=>{const s=document.querySelector('#wardrobe-skin-panel input[type=search]');s.value='nothing like this';s.dispatchEvent(new Event('input'));})()`);assert.equal(await run(`document.querySelector('#wardrobe-skin-panel .wardrobe-no-results').hidden`),false);
    await run(`(()=>{const s=document.querySelector('#wardrobe-skin-panel input[type=search]');s.value='';s.dispatchEvent(new Event('input'));for(let i=0;i<40;i++)document.querySelector('#wardrobe-canvas').dispatchEvent(new WheelEvent('wheel',{deltaY:100,cancelable:true}));})()`);assert.equal(await run(`document.querySelector('#wardrobe-canvas').dataset.zoom`),'45');
    await run(`for(let i=0;i<40;i++)document.querySelector('#wardrobe-canvas').dispatchEvent(new WheelEvent('wheel',{deltaY:-100,cancelable:true}))`);assert.equal(await run(`document.querySelector('#wardrobe-canvas').dataset.zoom`),'115');
    assert.equal(await run(`document.querySelectorAll('#wardrobe-zoom,.wardrobe-zoom-controls').length`),0);
    await run(`for(let i=0;i<6;i++)document.querySelector('#wardrobe-canvas').dispatchEvent(new WheelEvent('wheel',{deltaY:60,cancelable:true}))`);
    await run('document.fonts.ready');win.webContents.invalidate();await sleep(700);fs.writeFileSync(base+'/desktop.png',(await win.webContents.capturePage()).toPNG());
    win.setSize(940,760);win.webContents.invalidate();await sleep(350);assert.equal(await run('document.documentElement.scrollWidth===document.documentElement.clientWidth'),true);fs.writeFileSync(base+'/compact.png',(await win.webContents.capturePage()).toPNG());
    await run(`document.querySelector('#wardrobe-cape-tab').click()`);assert.equal(await run(`document.querySelectorAll('#wardrobe-cape-panel .cosmetic-empty-slot').length`),6);
    await run(`document.querySelector('#wardrobe-account-trigger').click()`);await until(`[...document.querySelectorAll('#account-options button')].some(b=>b.textContent.includes('Other'))`);await run(`[...document.querySelectorAll('#account-options button')].find(b=>b.textContent.includes('Other')).click()`);await until(`document.querySelector('#wardrobe-player-name').textContent==='Other'`);
    assert.equal(await run('state.username'),'Other');
    console.log('WARDROBE_SYSTEM_PASS: automatic targeted upload, no confirmation/name/save bars, failed import retry, duplicate reuse, restart persistence, explicit equip, auto model, search, scroll-only zoom, account scope and compact layout');
  }finally{win.destroy();server.closeAllConnections();await new Promise(r=>server.close(r));app.quit();}
}).catch(e=>{console.error(e);app.exit(1)});
