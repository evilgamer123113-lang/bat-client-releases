'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {targets,target,buildPlan,releaseTarget}=require('../ports/targets.cjs');
const {versions,selectFabricApi,resolve}=require('./resolve-port-loaders.cjs');
test('all 15 requested versions have both loader targets, without duplicate IDs',()=>{
  const all=targets();assert.equal(all.length,30);assert.equal(new Set(all.map(t=>t.id)).size,30);
  for(const entry of all)assert.ok(all.some(t=>t.minecraft===entry.minecraft&&t.loader!==entry.loader));
});
test('1.8/1.8.9 use Legacy Fabric and Java 8; exact 1.21.1 stays on its tested pins',()=>{
  for(const version of ['1.8','1.8.9']) {assert.equal(target('fabric-'+version).distribution,'legacy-fabric');assert.equal(target('forge-'+version).java,8);}
  assert.equal(target('fabric-1.21.1').loaderVersion,'0.16.9');
  assert.equal(target('fabric-1.21.1').api,'0.102.1+1.21.1');
});
test('unported targets cannot build or ship a renamed incompatible JAR',()=>{
  for(const entry of targets())if(entry.state!=='implemented')assert.throws(()=>buildPlan(entry.id),/not ported/);
  assert.throws(()=>buildPlan('fabric-1.21'),/Unknown/);
  assert.ok(buildPlan('fabric-1.21.1').args.includes('verifyProtectedJar'));
  for(const entry of targets())assert.throws(()=>releaseTarget(entry.id),/not approved/);
});
test('Java baselines follow the version boundaries',()=>{
  for(const [version,java] of [['1.16.5',8],['1.17.1',16],['1.18.2',17],['1.20.4',17],['1.20.6',21],['1.21.11',21]])assert.equal(target('forge-'+version).java,java);
});
test('API resolver matches exact Minecraft suffix, never 1.21.1 against 1.21.11',()=>{
  assert.deepEqual(versions('<versions><version>a</version><version>b</version></versions>'),['a','b']);
  assert.equal(selectFabricApi(['0.9.0+1.21.1','0.10.0+1.21.1','0.141.6+1.21.11'],'1.21.1'),'0.10.0+1.21.1');
  assert.equal(selectFabricApi(['1+1.21.1'],'1.21.11'),null);
});
test('Forge resolver prefers verified recommended coordinate and rejects missing versions',async()=>{
  const entry=await resolve(target('forge-1.20.1'),['1.20.1-47.4.0','1.20.1-47.5.0','1.21.1-52.1.9'],{'1.20.1-recommended':'47.4.0'},[]);
  assert.equal(entry.loader.version,'1.20.1-47.4.0');
  await assert.rejects(resolve(target('forge-1.8'),['1.8.9-11.15.1.2318'],{},[]),/No official Forge/);
});
test('checked upstream lock covers exactly the requested targets; it does not mark ports implemented',()=>{
  const lock=require('../ports/loaders.lock.json');
  assert.deepEqual(Object.keys(lock.targets).sort(),targets().map(t=>t.id).sort());
  for(const entry of targets()){assert.ok(lock.targets[entry.id].loader.maven);assert.ok(lock.targets[entry.id].source.startsWith('https://'));}
});
test('packaged existing Fabric metadata is exact and service provider is retained under obfuscation',()=>{
  const root=path.resolve(__dirname,'..');
  const metadata=JSON.parse(fs.readFileSync(path.join(root,'src/main/resources/fabric.mod.json'),'utf8'));
  assert.equal(metadata.depends.minecraft,'1.21.1');
  assert.equal(fs.readFileSync(path.join(root,'src/main/resources/META-INF/services/com.batclient.platform.PlatformEnvironment'),'utf8').trim(),'com.batclient.platform.fabric.FabricEnvironment');
  const rules=fs.readFileSync(path.join(root,'proguard.pro'),'utf8');
  assert.match(rules,/-keep class com\.batclient\.platform\.fabric\.FabricEnvironment/);
});

test("Forge 1.21.1 uses its real protected project and retains the stable release gate",()=>{
  const plan=buildPlan("forge-1.21.1");
  assert.deepEqual(plan.args.slice(0,4),["-p","ports/forge-1.21.1-full","build","verifyProtectedForgeJar"]);
  assert.equal(plan.target.runtime,"clean-production-smoke");
  assert.throws(()=>releaseTarget("forge-1.21.1"),/not approved/);
});

test('paired 1.20.6 builds route to actual protected projects and remain internal',()=>{
  for(const loader of ['fabric','forge']){
    const plan=buildPlan(loader+'-1.20.6');
    assert.deepEqual(plan.args.slice(0,3),['-p','ports/'+loader+'-1.20.6','build']);
    assert.equal(plan.args[3],loader==='forge'?'verifyProtectedForgeJar':'verifyProtectedJar');
    assert.equal(plan.target.mappings,'1.20.6+build.3');
    assert.ok(plan.target.nativeEvidence.length>=2);
    assert.throws(()=>releaseTarget(loader+'-1.20.6'),/not approved/);
  }
});
