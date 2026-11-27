const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const os=require('node:os');
const path=require('node:path');
const {spawnSync}=require('node:child_process');
const AdmZip=require('adm-zip');
const {composeJvmArgs}=require('../launcher/jvm-arguments');
const {incompatibility,checkFabricMods,apiShape,classInfo}=require('../launcher/mod-compatibility');
const policy=require('../launcher/automatic-mod-policy');
const defaults=['-XX:+UseG1GC','-XX:G1HeapRegionSize=8M','-XX:+UseStringDeduplication','-Dkeep=true'];
test('custom collector replaces G1 and a loadout overrides the global policy',()=>{
    const args=composeJvmArgs(defaults,'-XX:+UseG1GC -Duser=1','-XX:+UseSerialGC -Xmn128m');
    assert.deepEqual(args,['-Dkeep=true','-XX:+UseSerialGC','-Duser=1','-Xmn128m']);
    const java=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java'):'java';
    const result=spawnSync(java,[...args,'-version'],{encoding:'utf8',windowsHide:true});
    assert.equal(result.status,0,result.stderr);assert.doesNotMatch(result.stderr,/Multiple garbage collectors/);
});
test('default G1 and valid custom agents/module flags survive; conflicting user selectors fail clearly',()=>{
    assert.deepEqual(composeJvmArgs(defaults,'',''),defaults);
    assert.ok(composeJvmArgs(defaults,'-javaagent:test.jar --add-opens java.base/java.lang=ALL-UNNAMED','').includes('-javaagent:test.jar'));
    assert.throws(()=>composeJvmArgs(defaults,'-XX:+UseZGC -XX:+UseSerialGC',''),/multiple garbage collectors/);
    assert.ok(!composeJvmArgs(defaults,'-XX:+UseSerialGC','-XX:-UseSerialGC').includes('-XX:+UseSerialGC'));
    assert.ok(!composeJvmArgs(defaults,'-XX:-UseG1GC','').some(a=>a.startsWith('-XX:G1')));
});
function jar(id,version,extras={}){const z=new AdmZip();z.addFile('fabric.mod.json',Buffer.from(JSON.stringify({id,version,...extras})));return z;}
function u2(n){const b=Buffer.alloc(2);b.writeUInt16BE(n);return b;}
function constant(tag,a,b){return Buffer.concat([Buffer.from([tag]),u2(a),...(b===undefined?[]:[u2(b)])]);}
function text(s){return Buffer.concat([Buffer.from([1]),u2(Buffer.byteLength(s)),Buffer.from(s)]);}
function fixture(isInterface,refTag){
    const owner='net/fabricmc/fabric/api/networking/v1/PayloadTypeRegistry';
    const constants=[text(owner),constant(7,1),text('playS2C'),text('()V'),constant(12,3,4),constant(refTag,2,5)];
    return Buffer.concat([Buffer.from('cafebabe00000034','hex'),u2(7),...constants,u2(isInterface?0x601:0x21),u2(2),u2(0),u2(0),u2(0),u2(0),u2(0)]);
}
test('nested Fabric API shape detects the exact Methodref/InterfaceMethodref mismatch',()=>{
    const owner='net/fabricmc/fabric/api/networking/v1/PayloadTypeRegistry';
    const api=jar('fabric-networking-api-v1','test');api.addFile(owner+'.class',fixture(true,11));
    const wrapper=jar('fabric-api','test',{jars:[{file:'META-INF/jars/network.jar'}]});wrapper.addFile('META-INF/jars/network.jar',api.toBuffer());
    const shapes=apiShape([wrapper.toBuffer()]);assert.equal(shapes.get(owner),true);
    const candidate=jar('kindscrystaloptimizer','future-fixture');candidate.addFile('Main.class',fixture(false,10));
    assert.match(incompatibility(candidate.toBuffer(),'1.21.11',shapes),/incompatible Fabric networking API/);
    candidate.updateFile('Main.class',fixture(false,11));assert.equal(incompatibility(candidate.toBuffer(),'1.21.11',shapes),null);
    assert.equal(classInfo(fixture(false,10)).refs[0].isInterface,false);
});
test('known incompatible optimizer is detected by metadata, unrelated versions are not blanket blocked',()=>{
    const bad=jar('kindscrystaloptimizer','1.6.3').toBuffer();assert.match(incompatibility(bad,'1.21.11'),/No verified replacement/);
    assert.equal(incompatibility(bad,'1.21.1'),null);
    assert.equal(incompatibility(jar('other','1.6.3').toBuffer(),'1.21.11'),null);
});
test('quarantine preserves bytes, disabled choice and other mods across repeated launch checks',async()=>{
    const root=await fs.mkdtemp(path.join(os.tmpdir(),'bat-compat-'));
    try{
        const bad=jar('kindscrystaloptimizer','1.6.3').toBuffer(),good=jar('other','1').toBuffer();
        await fs.writeFile(path.join(root,'renamed-by-user.jar'),bad);await fs.writeFile(path.join(root,'safe.jar'),good);
        const result=await checkFabricMods(root,'1.21.11');assert.equal(result.length,1);assert.equal(result[0].disabledAs,'renamed-by-user.jar.disabled');
        assert.deepEqual(await fs.readFile(path.join(root,result[0].disabledAs)),bad);assert.deepEqual(await fs.readFile(path.join(root,'safe.jar')),good);
        assert.deepEqual(await checkFabricMods(root,'1.21.11'),[]);
    }finally{await fs.rm(root,{recursive:true,force:true});}
});

test('Technical Utilities is excluded by project ID, slug, search result and actual renamed JAR identity',()=>{
    for(const id of ['hNoAJSm7','techutils','technical-utilities',{project_id:'hNoAJSm7',title:'Technical Utilities'}])assert.equal(policy.excludedAutomaticProject(id),true);
    assert.equal(policy.excludedAutomaticProject('AANobbMI'),false);
    assert.throws(()=>policy.assertAutomaticArtifactAllowed(jar('techutils','0.8.3').toBuffer()),/excluded/);
    assert.throws(()=>policy.assertAutomaticUrlAllowed('https://cdn.modrinth.com/data/hNoAJSm7/versions/test/mod.jar'),/excluded/);
    assert.doesNotThrow(()=>policy.assertAutomaticArtifactAllowed(jar('other','2').toBuffer(),jar('other','1').toBuffer()));
    assert.throws(()=>policy.assertAutomaticArtifactAllowed(jar('different','2').toBuffer(),jar('other','1').toBuffer()),/different mod/);
});
function ipcHandler(name,extra){
    const source=require('node:fs').readFileSync(path.resolve(__dirname,'../main.js'),'utf8');
    const start=source.indexOf("ipcMain.handle('"+name+"'");
    const end=name==='check-mod-updates'?source.indexOf("ipcMain.handle('update-mod'",start):source.indexOf('// 7. CRASH ANALYZER',start);
    let handler;require('node:vm').runInNewContext(source.slice(start,end),{ipcMain:{handle:(key,fn)=>{handler=fn;}},fs:require('fs-extra'),path,Buffer,...policy,...extra});return handler;
}
test('real update handler blocks a Technical Utilities payload before writing or deleting existing mods',async()=>{
    const root=await fs.mkdtemp(path.join(os.tmpdir(),'bat-no-techutils-'));
    try{
        const original=jar('original','1').toBuffer(),old=path.join(root,'original.jar');await fs.writeFile(old,original);
        let calls=0;const handler=ipcHandler('update-mod',{getModsDir:()=>root,stableFetch:async()=>{calls++;return {ok:true,headers:{get:()=> 'attachment; filename="replacement.jar"'},arrayBuffer:async()=>jar('techutils','0.8.3').toBuffer()};}});
        const result=await handler(null,{downloadUrl:'https://example.test/download',oldFilename:'original.jar'});assert.equal(result.ok,false);assert.match(result.error,/excluded/);
        assert.deepEqual(await fs.readFile(old),original);assert.deepEqual(await fs.readdir(root),['original.jar']);
        const blocked=await handler(null,{downloadUrl:'https://cdn.modrinth.com/data/hNoAJSm7/versions/test/mod.jar',oldFilename:'original.jar'});assert.equal(blocked.ok,false);assert.equal(calls,1,'known project URL blocked without a network request');
    }finally{await fs.rm(root,{recursive:true,force:true});}
});
test('real update checker skips installed Technical Utilities and Technical Utilities search suggestions',async()=>{
    const root=await fs.mkdtemp(path.join(os.tmpdir(),'bat-update-policy-'));
    try{
        await fs.writeFile(path.join(root,'generic-name.jar'),jar('techutils','0.8.3').toBuffer());let calls=0;
        const check=ipcHandler('check-mod-updates',{savedProfiles:[{id:'test',version:'1.21',engine:'fabric'}],activeProfileId:'test',getModsDir:()=>root,stableFetch:async()=>{calls++;return {json:async()=>({hits:[{project_id:'hNoAJSm7',title:'Technical Utilities'}]})};}});
        assert.equal((await check(null,'test')).length,0);assert.equal(calls,0,'installed excluded identity skips search');
        await fs.writeFile(path.join(root,'generic-name.jar'),jar('other','1').toBuffer());assert.equal((await check(null,'test')).length,0);assert.equal(calls,1,'excluded search project never reaches version/artifact downloads');
    }finally{await fs.rm(root,{recursive:true,force:true});}
});
test('automatic FPS downloader excludes the project before cache or dependency resolution',async()=>{
    const source=require('node:fs').readFileSync(path.resolve(__dirname,'../main.js'),'utf8'),start=source.indexOf('async function downloadTargetVersionFpsMod('),end=source.indexOf('const managedModsFilePath',start);
    const context={...policy,console:{log:()=>{}},getModsDir:()=>{throw Error('Excluded project reached automatic cache path');}};
    await require('node:vm').runInNewContext(source.slice(start,end)+"\ndownloadTargetVersionFpsMod('hNoAJSm7','1.21','fabric');",context);
});
test('update suggestions only accept the selected Minecraft version and loader',async()=>{
    const root=await fs.mkdtemp(path.join(os.tmpdir(),'bat-version-updates-'));
    try{
        await fs.writeFile(path.join(root,'other-1.jar'),jar('other','1').toBuffer());let query='';
        const check=ipcHandler('check-mod-updates',{savedProfiles:[{id:'test',version:'1.21',engine:'fabric'}],activeProfileId:'test',getModsDir:()=>root,
            versionMatches:(values,target)=>values.includes(target),loaderMatches:(values,target)=>values.includes(target),stableFetch:async url=>{
                if(url.includes('/search?'))return {json:async()=>({hits:[{project_id:'other-project',title:'Other mod'}]})};query=url;
                return {json:async()=>[
                    {version_number:'wrong-game',game_versions:['26.3'],loaders:['fabric'],files:[{filename:'wrong.jar',url:'https://example.test/wrong'}]},
                    {version_number:'wrong-loader',game_versions:['1.21'],loaders:['forge'],files:[{filename:'forge.jar',url:'https://example.test/forge'}]},
                    {version_number:'2',game_versions:['1.21'],loaders:['fabric'],files:[{filename:'other-2.jar',url:'https://example.test/right'}]}
                ]};
            }});
        const result=await check(null,'test');assert.equal(result.length,1);assert.equal(result[0].latestVersion,'2');assert.equal(new URL(query).searchParams.get('game_versions'),'["1.21"]');assert.equal(new URL(query).searchParams.get('loaders'),'["fabric"]');
    }finally{await fs.rm(root,{recursive:true,force:true});}
});
