const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const path=require('node:path');
const os=require('node:os');
const vm=require('node:vm');
const Zip=require('adm-zip');
const {removeBundledSodium}=require('../launcher/optional-renderer');
const prefs=require('../launcher/mod-preferences');
function nested(id){const z=new Zip();z.addFile('fabric.mod.json',Buffer.from(JSON.stringify({schemaVersion:1,id,version:'1'})));return z.toBuffer();}
function fixture(id='batclient',signed=false){const z=new Zip();z.addFile('fabric.mod.json',Buffer.from(JSON.stringify({schemaVersion:1,id,version:'1',jars:[{file:'META-INF/jars/renderer.jar'},{file:'META-INF/jars/kotlin.jar'}]})));z.addFile('META-INF/jars/renderer.jar',nested('sodium'));z.addFile('META-INF/jars/kotlin.jar',nested('kotlin'));z.addFile('com/batclient/protected.class',Buffer.from('protected-bytecode'));z.addFile('assets/batclient/web/ui.bin',Buffer.from('BAT1-encrypted-ui'));if(signed)z.addFile('META-INF/OWNER.SF',Buffer.from('signature'));return z.toBuffer();}
test('older protected Bat mod loses only optional nested Sodium, preserves bytes and original backup, and stays removed',async()=>{
 const root=await fs.mkdtemp(path.join(process.env.BAT_TEST_TMP||os.tmpdir(),'bat-optional-'));
 try{const file=path.join(root,'renamed.jar'),original=fixture();await fs.writeFile(file,original);
 const result=await removeBundledSodium(file,path.join(root,'backups'));assert.equal(result.changed,true);assert.deepEqual(await fs.readFile(result.backup),original);
 const after=new Zip(await fs.readFile(file));assert.equal(after.getEntry('META-INF/jars/renderer.jar'),null);assert.equal(after.readFile('com/batclient/protected.class').toString(),'protected-bytecode');assert.equal(after.readFile('assets/batclient/web/ui.bin').toString(),'BAT1-encrypted-ui');assert.equal(JSON.parse(after.readAsText('fabric.mod.json')).jars.length,1);
 const bytes=await fs.readFile(file);assert.equal((await removeBundledSodium(file,path.join(root,'backups'))).changed,false);assert.deepEqual(await fs.readFile(file),bytes);
 for(const [name,input] of [['unrelated.jar',fixture('other-client')],['signed.jar',fixture('batclient',true)]]){const f=path.join(root,name);await fs.writeFile(f,input);if(name==='signed.jar')await assert.rejects(removeBundledSodium(f,path.join(root,'backups')),/cannot safely/);else assert.equal((await removeBundledSodium(f,path.join(root,'backups'))).changed,false);assert.deepEqual(await fs.readFile(f),input);}
 }finally{await fs.rm(root,{recursive:true,force:true});}
});
test('actual automatic downloader drops an in-flight download after the user removes that project',async()=>{
 const source=await fs.readFile(path.join(__dirname,'..','main.js'),'utf8');let dismissed=[],writes=0,requests=0;const warnings=[];
 const context=vm.createContext({Buffer,path,Set,Map,...prefs,console:{log(){},warn(...a){warnings.push(a);}},excludedAutomaticProject:()=>false,getDismissedMods:async()=>dismissed,
 getModsDir:()=>'/fixture',inFlightModDownloads:new Map(),getFpsModCache:async()=>({}),getManagedMods:async()=>[],FPS_MOD_CACHE_TTL_MS:1,
 loaderMatches:()=>true,versionMatches:()=>true,assertAutomaticArtifactAllowed(){},incompatibility:()=>null,
 fs:{pathExists:async()=>false,readdir:async()=>[],writeFile:async()=>writes++},
 stableFetch:async()=>{requests++;return requests===1?{ok:true,json:async()=>[{loaders:['fabric'],game_versions:['1.21.1'],version_type:'release',dependencies:[],files:[{primary:true,filename:'sodium.jar',url:'https://fixture.invalid/sodium.jar'}]}]}:{ok:true,arrayBuffer:async()=>{dismissed=[prefs.SODIUM];return Buffer.from('fixture');}};}});
 vm.runInContext(source.slice(source.indexOf('async function downloadTargetVersionFpsMod('),source.indexOf('\nconst managedModsFilePath'))+'\nthis.download=downloadTargetVersionFpsMod;',context);
 await context.download(prefs.SODIUM,'1.21.1','fabric');assert.equal(requests,2);assert.equal(writes,0);assert.equal(warnings.length,0);
});
test('real Remove button invokes removal directly once and reports errors without a confirmation modal',async()=>{
 const html=await fs.readFile(path.join(__dirname,'..','index.html'),'utf8');assert.ok(!html.includes('id="remove-mod-modal"'));
 let click,calls=0,refreshes=0;const button={disabled:false,addEventListener:(name,fn)=>click=fn},status={textContent:''};let finish;
 const context=vm.createContext({removeBtn:button,statusText:status,mod:{filename:'fixture.jar',displayName:'Fixture'},t:s=>s,currentModUpdates:new Map(),refreshInstalledModCount:()=>refreshes++,loadInstalledMods:()=>{},ipcRenderer:{invoke:()=>{calls++;return new Promise(r=>finish=r);}}});
 const start=html.indexOf("                    removeBtn.addEventListener('click',async(e)=>{");const end=html.indexOf('                    const updateBtn=',start);vm.runInContext(html.slice(start,end),context);
 const pending=click({stopPropagation(){}});await click({stopPropagation(){}});assert.equal(calls,1);finish({ok:true});await pending;assert.equal(refreshes,1);assert.match(status.textContent,/Removed/);
 button.disabled=false;const failed=click({stopPropagation(){}});finish({ok:false,error:'File in use'});await failed;assert.equal(button.disabled,false);assert.match(status.textContent,/File in use/);
});
test('preference write failures preserve the previous file, recover the queue, and never erase corrupt choices',async()=>{
 const root=await fs.mkdtemp(path.join(process.env.BAT_TEST_TMP||os.tmpdir(),'bat-choice-store-'));
 try {
  const source=await fs.readFile(path.join(__dirname,'..','main.js'),'utf8'),file=path.join(root,'dismissed-mods.json');
  await fs.writeFile(file,'["keep"]');let fail=true;
  const io={...require('fs-extra'),rename:async(...args)=>{if(fail){fail=false;throw Error('simulated disk failure');}return fs.rename(...args);}};
  const context=vm.createContext({fs:io,path,crypto:require('node:crypto'),console,appDataRoot:root});
  vm.runInContext(source.slice(source.indexOf('const managedModsFilePath ='),source.indexOf('async function cleanModsFolder()'))+'\nthis.api={getDismissedMods,addDismissedMod};',context);
  await assert.rejects(context.api.addDismissedMod('new'),/simulated disk failure/);assert.equal(await fs.readFile(file,'utf8'),'["keep"]');
  await context.api.addDismissedMod('new');assert.deepEqual(JSON.parse(await fs.readFile(file,'utf8')),['keep','new']);
  await fs.writeFile(file,'broken preferences');await assert.rejects(context.api.getDismissedMods(),/Cannot read saved/);assert.equal(await fs.readFile(file,'utf8'),'broken preferences');
 }finally{await fs.rm(root,{recursive:true,force:true});}
});
test('Disable preserves both files when a disabled destination already exists',async()=>{
 const root=await fs.mkdtemp(path.join(process.env.BAT_TEST_TMP||os.tmpdir(),'bat-toggle-collision-'));
 try {
  const source=await fs.readFile(path.join(__dirname,'..','main.js'),'utf8'),handlers=new Map();
  await fs.writeFile(path.join(root,'sodium.jar'),'active');await fs.writeFile(path.join(root,'sodium.disabled'),'saved-disabled');
  const context=vm.createContext({fs:require('fs-extra'),path,getModsDir:()=>root,ipcMain:{handle:(name,fn)=>handlers.set(name,fn)}});
  vm.runInContext(source.slice(source.indexOf("ipcMain.handle('toggle-mod'"),source.indexOf("ipcMain.handle('check-mod-conflicts'")),context);
  const result=await handlers.get('toggle-mod')({},'sodium.jar');assert.equal(result.ok,false);assert.match(result.error,/both copies/);
  assert.equal(await fs.readFile(path.join(root,'sodium.jar'),'utf8'),'active');assert.equal(await fs.readFile(path.join(root,'sodium.disabled'),'utf8'),'saved-disabled');
 }finally{await fs.rm(root,{recursive:true,force:true});}
});
