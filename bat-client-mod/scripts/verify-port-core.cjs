'use strict';
const fs = require('node:fs');
const path = require('node:path');
const {spawnSync} = require('node:child_process');
const root = path.resolve(__dirname,'..');
const classes = path.join(root,'build/port-core/classes');
fs.mkdirSync(classes,{recursive:true});
const names = ['ArmorWarning','PotionExpiry','FrameWorkBudget','ItemCounter','HealthValues','CombatTracker','ZoomInterpolation'];
const sources = names.map(name => `src/main/java/com/batclient/hud/${name}.java`).concat([
  'src/main/java/com/batclient/platform/PlatformEnvironment.java',
  'src/main/java/com/batclient/platform/Platforms.java',
  'scripts/PortCoreSmoke.java'
]);
function run(command,args) {
  const result = spawnSync(command,args,{cwd:root,stdio:'inherit'});
  if(result.error)throw result.error;
  if(result.status!==0)throw Error(command+' failed: '+result.status);
}
try {
  run('javac',['--release','8','-encoding','UTF-8','-d',classes,...sources]);
  for(const file of names.map(name=>`com/batclient/hud/${name}.class`).concat(['com/batclient/platform/PlatformEnvironment.class','com/batclient/platform/Platforms.class'])) {
    const bytes=fs.readFileSync(path.join(classes,file));
    if(bytes.readUInt16BE(6)!==52)throw Error('Not Java 8 bytecode: '+file);
  }
  run('java',['-cp',classes,'PortCoreSmoke']);
  run('node',['--test','scripts/port-targets.test.cjs']);
} catch(error) {console.error(error.message);process.exitCode=1;}
