const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const vm = require('node:vm');
const {spawnSync} = require('node:child_process');
const {cleanupUpdateStaging, markUpdateStage} = require('../launcher/update-cleanup');
const preferences = require('../launcher/mod-preferences');
const {compatibleJvmArgs, composeJvmArgs} = require('../launcher/jvm-arguments');
const fixtureBase = require('node:fs').realpathSync(process.env.BAT_TEST_TMP || os.tmpdir());
const now = Date.now(), old = new Date(now-3*86400000);
async function exists(file) { try { await fs.access(file); return true; } catch { return false; } }
async function stage(root, name) {
    const dir = path.join(root,'update-staging',name);
    await fs.mkdir(path.join(dir,'resources'),{recursive:true});
    await fs.writeFile(path.join(dir,'Bat Client.exe'),'fixture executable');
    await fs.writeFile(path.join(dir,'resources','app.asar'),'fixture archive');
    await fs.utimes(dir,old,old); return dir;
}
test('old completed extracts are reclaimed; recent stages, current executable, reports and unknown data survive',async()=>{
    const root = await fs.mkdtemp(path.join(fixtureBase,'bat-maintenance-'));
    try {
        const obsolete = await stage(root,'v2.2.83-old123');
        const active = await stage(root,'v2.2.94-current');
        const recent = await stage(root,'v2.2.95-recent'); await fs.utimes(recent,new Date(now),new Date(now));
        const unknown = path.join(root,'update-staging','personal'); await fs.mkdir(unknown);
        await fs.writeFile(path.join(unknown,'notes.txt'),'keep');
        const report = path.join(root,'update-staging','batclient-update-result.json'); await fs.writeFile(report,'{"ok":true}');
        const result = await cleanupUpdateStaging({appDataRoot:root,execPath:path.join(active,'Bat Client.exe'),now});
        assert.deepEqual(result.removed,['v2.2.83-old123']); assert.ok(result.bytes>0);
        assert.equal(await exists(obsolete),false);
        for (const item of [active,recent,report,unknown]) assert.equal(await exists(item),true,item);
        assert.equal((await cleanupUpdateStaging({appDataRoot:root,execPath:path.join(active,'Bat Client.exe'),now})).removed.length,0);
    } finally { await fs.rm(root,{recursive:true,force:true}); }
});
test('live leases, linked trees and incomplete/unrecognized packages are preserved',async()=>{
    const root = await fs.mkdtemp(path.join(fixtureBase,'bat-maintenance-'));
    try {
        const live = await stage(root,'v2.2.95-live'); await markUpdateStage(live); await fs.utimes(live,old,old);
        const linked = await stage(root,'v2.2.84-linked');
        const external = path.join(root,'protected-world'); await fs.mkdir(external); await fs.writeFile(path.join(external,'level.dat'),'world');
        await fs.symlink(external,path.join(linked,'linked'),process.platform==='win32'?'junction':'dir'); await fs.utimes(linked,old,old);
        const incomplete = path.join(root,'update-staging','v2.2.85-incomplete'); await fs.mkdir(incomplete); await fs.utimes(incomplete,old,old);
        const result = await cleanupUpdateStaging({appDataRoot:root,now});
        assert.equal(result.removed.length,0); assert.equal(await fs.readFile(path.join(external,'level.dat'),'utf8'),'world');
        assert.equal(await exists(live),true); assert.equal(await exists(incomplete),true);
    } finally { await fs.rm(root,{recursive:true,force:true}); }
});
test('Windows cleanup accepts case-only path normalization', {skip:process.platform!=='win32'}, async()=>{
    const root=await fs.mkdtemp(path.join(fixtureBase,'bat-case-'));
    try {
        await stage(root,'v2.2.83-old123');
        const result=await cleanupUpdateStaging({appDataRoot:root.toLowerCase(),now});
        assert.deepEqual(result.removed,['v2.2.83-old123']);
    } finally {await fs.rm(root,{recursive:true,force:true});}
});
test('manual deletion and both disabled filename conventions persist; replacements do not count as removal',()=>{
    const map = {'sodium-old.jar':preferences.SODIUM,'sodium-new.jar':preferences.SODIUM,'lithium.jar':'lithium'};
    assert.deepEqual(preferences.reconcileModPreferences(['sodium-old.jar'],map,[]).dismissed,[preferences.SODIUM]);
    assert.deepEqual(preferences.reconcileModPreferences(['sodium-old.jar'],map,['sodium-new.jar']).dismissed,[]);
    for (const file of ['sodium-old.disabled','sodium-old.jar.disabled']) assert.deepEqual(preferences.reconcileModPreferences(['sodium-old.jar'],map,[file]).dismissed,[preferences.SODIUM]);
    const recovered = preferences.reconcileModPreferences([],{},[],{[preferences.SODIUM+'|1.21.11|fabric']:{filename:'sodium-old.jar'}});
    assert.deepEqual(recovered.dismissed,[preferences.SODIUM]);
    assert.equal(preferences.blockedAutomaticMod('PtjYWJkn',[preferences.SODIUM]),true);
    assert.equal(preferences.blockedAutomaticMod('Bh37bMuy',[preferences.SODIUM]),true);
    assert.equal(preferences.blockedAutomaticMod('P7dR8mSH',[preferences.SODIUM]),false);
});
test('actual automatic downloader never fetches opted-out Sodium/add-ons, even with a warm cache or a new version',async()=>{
    const source = await fs.readFile(path.join(__dirname,'..','main.js'),'utf8');
    const start = source.indexOf('async function downloadTargetVersionFpsMod('), end = source.indexOf('\nconst managedModsFilePath',start);
    let fetches = 0;
    const context = vm.createContext({console:{log(){},warn(){}},...preferences,
        excludedAutomaticProject:()=>false,getDismissedMods:async()=>[preferences.SODIUM],
        getModsDir:()=>'/fixture',getFpsModCache:async()=>({}),inFlightModDownloads:new Map(),
        stableFetch:async()=>{fetches++;throw Error('Unexpected network request');},Set,Map});
    vm.runInContext(source.slice(start,end)+'\nthis.download=downloadTargetVersionFpsMod;',context);
    for (const version of ['1.21.1','1.21.11']) for (const id of [preferences.SODIUM,'PtjYWJkn','Bh37bMuy']) await context.download(id,version,'fabric');
    assert.equal(fetches,0);
});
test('Java 21 repair removes only obsolete AggressiveOpts, preserves custom settings and launches the real JVM',()=>{
    const saved='-XX:+AggressiveOpts -Dplayer.setting=retained';
    const composed=composeJvmArgs(['-XX:+UseG1GC'],saved,'');
    const filtered=compatibleJvmArgs(composed,21);
    assert.deepEqual(filtered.removed,['-XX:+AggressiveOpts']); assert.ok(filtered.args.includes('-Dplayer.setting=retained'));
    assert.ok(saved.includes('AggressiveOpts')); assert.ok(compatibleJvmArgs(composed,8).args.includes('-XX:+AggressiveOpts'));
    const java=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java'):'java';
    const result=spawnSync(java,[...filtered.args,'-version'],{encoding:'utf8',windowsHide:true});
    assert.equal(result.status,0,result.stderr);
});
test('real tracking writers retain parallel installs and removal choices survive a fresh launcher context',async()=>{
    const root=await fs.mkdtemp(path.join(fixtureBase,'bat-preferences-'));
    try {
        const source=await fs.readFile(path.join(__dirname,'..','main.js'),'utf8');
        const storage=source.slice(source.indexOf('const managedModsFilePath ='),source.indexOf('async function cleanModsFolder()'));
        function fresh() {
            const context=vm.createContext({fs:require('fs-extra'),crypto:require('node:crypto'),path,appDataRoot:root,console});
            vm.runInContext(storage+'\nthis.api={getManagedMods,getModProjectMap,getFpsModCache,getDismissedMods,trackManagedMod,trackManagedModProject,setFpsModCacheEntry,addDismissedMod,removeDismissedMod};',context);
            return context.api;
        }
        const api=fresh();
        await Promise.all(Array.from({length:12},(_,i)=>Promise.all([
            api.trackManagedMod('mod-'+i+'.jar'),api.trackManagedModProject('mod-'+i+'.jar','project-'+i),
            api.setFpsModCacheEntry('project-'+i+'|1.21.1|fabric',{filename:'mod-'+i+'.jar'}),api.addDismissedMod('removed-'+i)
        ])));
        assert.equal((await api.getManagedMods()).length,12);
        assert.equal(Object.keys(await api.getModProjectMap()).length,12);
        assert.equal(Object.keys(await api.getFpsModCache()).length,12);
        assert.equal((await api.getDismissedMods()).length,12);
        await api.addDismissedMod(preferences.SODIUM);
        assert.equal(preferences.blockedAutomaticMod(preferences.SODIUM,await fresh().getDismissedMods()),true);
        await fresh().removeDismissedMod(preferences.SODIUM);
        assert.equal(preferences.blockedAutomaticMod(preferences.SODIUM,await fresh().getDismissedMods()),false);
    } finally {await fs.rm(root,{recursive:true,force:true});}
});
test('real remove/toggle handlers honor manually installed Sodium and restore disabled archives without double .jar',async()=>{
    const root=await fs.mkdtemp(path.join(fixtureBase,'bat-mod-handlers-'));
    try {
        const source=await fs.readFile(path.join(__dirname,'..','main.js'),'utf8');
        const handlers=new Map(),choices=new Set();
        const context=vm.createContext({fs:require('fs-extra'),path,...preferences,
            getModsDir:()=>root,getManagedMods:async()=>[],getModProjectMap:async()=>({}),
            addDismissedMod:async id=>choices.add(id),removeDismissedMod:async id=>choices.delete(id),
            ipcMain:{handle:(name,callback)=>handlers.set(name,callback)}});
        vm.runInContext(source.slice(source.indexOf("ipcMain.handle('toggle-mod'"),source.indexOf("ipcMain.handle('check-mod-conflicts'")),context);
        await fs.writeFile(path.join(root,'sodium-1.jar'),'fixture');
        assert.equal((await handlers.get('remove-mod')({},'sodium-1.jar')).ok,true); assert.ok(choices.has(preferences.SODIUM));
        await fs.writeFile(path.join(root,'sodium-1.jar.disabled'),'fixture');
        const restored=await handlers.get('toggle-mod')({},'sodium-1.jar.disabled');
        assert.equal(restored.newFilename,'sodium-1.jar'); assert.equal(choices.has(preferences.SODIUM),false);
        assert.equal(await exists(path.join(root,'sodium-1.jar.jar')),false);
    } finally {await fs.rm(root,{recursive:true,force:true});}
});
