'use strict';
const fs=require('node:fs');
const path=require('node:path');
const {spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..');
const output=path.join(root,'build/performance-persistence',String(Date.now()));
const classes=path.join(output,'classes');fs.mkdirSync(classes,{recursive:true});
function jar(group,name){
  const cache=process.env.GRADLE_USER_HOME || path.join(require('node:os').homedir(),'.gradle');
  const base=path.join(cache,'caches/modules-2/files-2.1',group,name);
  const files=[];function scan(dir){for(const e of fs.readdirSync(dir,{withFileTypes:true})){const file=path.join(dir,e.name);if(e.isDirectory())scan(file);else if(e.name.endsWith('.jar')&&!e.name.includes('sources')&&!e.name.includes('javadoc'))files.push(file);}}
  scan(base);if(!files.length)throw Error('Missing cached '+name);return files.sort().pop();
}
function run(cmd,args){const r=spawnSync(cmd,args,{cwd:root,stdio:'inherit'});if(r.error)throw r.error;if(r.status!==0)throw Error(cmd+' failed '+r.status);}
try{
  const dependencies=[jar('com.google.code.gson','gson'),jar('org.slf4j','slf4j-api')].join(path.delimiter);
  const layouts=fs.readdirSync(path.join(root,'src/main/java/com/batclient/hud/layout')).filter(n=>n.endsWith('.java')).map(n=>'src/main/java/com/batclient/hud/layout/'+n);
  const sources=['scripts/support/BatClientMod.java','scripts/support/FabricLoader.java','scripts/support/ServerProfiles.java',
    'src/main/java/com/batclient/platform/PlatformEnvironment.java','src/main/java/com/batclient/platform/Platforms.java','src/main/java/com/batclient/platform/AtomicFiles.java','src/main/java/com/batclient/platform/fabric/FabricEnvironment.java',
    'src/main/java/com/batclient/HudConfig.java','src/main/java/com/batclient/hud/HudLayoutEngine.java',
    'scripts/AtomicFileLockSmoke.java','scripts/HudRenderCacheSmoke.java','scripts/HudConfigPersistenceSmoke.java','scripts/ProfilePersistenceSmoke.java',...layouts];
  run('javac',['--release','21','-encoding','UTF-8','-cp',dependencies,'-d',classes,...sources]);
  const services=path.join(classes,'META-INF/services');fs.mkdirSync(services,{recursive:true});
  fs.copyFileSync(path.join(root,'src/main/resources/META-INF/services/com.batclient.platform.PlatformEnvironment'),path.join(services,'com.batclient.platform.PlatformEnvironment'));
  for(const test of ['AtomicFileLockSmoke','HudRenderCacheSmoke','HudConfigPersistenceSmoke','ProfilePersistenceSmoke']){
    const config=path.join(output,test,'config');fs.mkdirSync(config,{recursive:true});
    run('java',['-Dbat.test.config='+config,'-cp',classes+path.delimiter+dependencies,test]);
  }
}catch(error){console.error(error.message);process.exitCode=1;}
