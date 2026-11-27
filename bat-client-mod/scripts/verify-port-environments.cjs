'use strict';
// Compile real environment providers against real upstream APIs for all requested targets.
// This is intentionally NOT a Minecraft boot/gameplay compatibility certification.
const fs=require('node:fs/promises');
const path=require('node:path');
const crypto=require('node:crypto');
const {spawnSync}=require('node:child_process');
const {targets}=require('../ports/targets.cjs');
const lock=require('../ports/loaders.lock.json');
const root=path.resolve(__dirname,'..');
const output=path.join(root,'build/port-loader-api');
async function artifact(base,coordinate,classifier='') {
  const [group,name,version]=coordinate.split(':');
  const filename=`${name}-${version}${classifier?'-'+classifier:''}.jar`;
  const url=`${base}/${group.replaceAll('.','/')}/${name}/${version}/${filename}`;
  const local=path.join(output,filename);
  let data;
  try {data=await fs.readFile(local);}catch(error){if(error.code!=='ENOENT')throw error;}
  const sumResponse=await fetch(url+'.sha1',{signal:AbortSignal.timeout(30000)});
  if(!sumResponse.ok)throw Error('No official checksum: '+url+' ('+sumResponse.status+')');
  const expected=(await sumResponse.text()).trim().split(/\s/)[0].toLowerCase();
  if(!/^[0-9a-f]{40}$/.test(expected))throw Error('Invalid upstream checksum: '+url);
  if(!data||crypto.createHash('sha1').update(data).digest('hex')!==expected) {
    const response=await fetch(url,{signal:AbortSignal.timeout(30000)});
    if(!response.ok)throw Error('Artifact download failed: '+url+' ('+response.status+')');
    data=Buffer.from(await response.arrayBuffer());
    if(crypto.createHash('sha1').update(data).digest('hex')!==expected)throw Error('Checksum mismatch: '+url);
    await fs.writeFile(local,data);
  }
  return {file:local,url,sha1:expected};
}
async function check(value) {
  const entry=lock.targets[value.id];
  let jars=[],provider;
  if(value.loader==='fabric') {
    jars=[await artifact('https://maven.fabricmc.net',entry.loader.maven)];
    provider='src/main/java/com/batclient/platform/fabric/FabricEnvironment.java';
  } else if(value.minecraft==='1.8'||value.minecraft==='1.8.9') {
    jars=await Promise.all([
      artifact('https://maven.minecraftforge.net',entry.loader.maven,'universal'),
      artifact('https://libraries.minecraft.net','net.minecraft:launchwrapper:1.12')
    ]);
    provider='ports/forge-legacy/src/main/java/com/batclient/platform/forge/LegacyForgeEnvironment.java';
  } else {
    const version=entry.loader.version;
    const names=value.minecraft==='1.16.5' ? [['forge','universal'],['forge','launcher']] : [['fmlcore',''],['fmlloader','']];
    jars=await Promise.all(names.map(([name,c])=>artifact('https://maven.minecraftforge.net',`net.minecraftforge:${name}:${version}`,c)));
    provider='ports/forge/src/main/java/com/batclient/platform/forge/ForgeEnvironment.java';
  }
  const classes=path.join(output,'classes',value.id);await fs.mkdir(classes,{recursive:true});
  const compile=spawnSync('javac',['--release','8','-Xlint:-options','-encoding','UTF-8','-cp',jars.map(j=>j.file).join(path.delimiter),'-d',classes,'src/main/java/com/batclient/platform/PlatformEnvironment.java',provider],{cwd:root,encoding:'utf8'});
  if(compile.error||compile.status!==0)throw Error(value.id+': '+(compile.error?.message||compile.stderr));
  return {target:value.id,kind:'environment-api-compilation-only',javaBytecode:8,dependencies:jars.map(({url,sha1})=>({url,sha1}))};
}
async function main() {
  await fs.mkdir(output,{recursive:true});
  const results=[],errors=[];
  // Serial checks share cached loader artifacts safely. No game processes are started.
  for(const value of targets()) {
    try {results.push(await check(value));console.log('ENVIRONMENT_API_OK '+value.id);}
    catch(error){errors.push(error.message);console.error(error.message);}
  }
  await fs.writeFile(path.join(output,'results.json'),JSON.stringify({checkedAt:new Date().toISOString(),results,errors},null,2)+'\n');
  if(errors.length)throw Error(`${errors.length} environment adapters failed API compilation`);
  console.log('ENVIRONMENT_MATRIX_OK: '+results.length+' targets (not full mod ports)');
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
