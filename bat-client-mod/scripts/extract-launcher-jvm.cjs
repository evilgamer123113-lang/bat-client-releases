'use strict';
// Read the installed launcher's static defaults without starting Electron or loading accounts.
const asar=require('../../node_modules/@electron/asar');
const path=require('node:path'),vm=require('node:vm');
const source=asar.extractFile(path.join(process.env.LOCALAPPDATA,'Programs/Bat Client/resources/app.asar'),'main.js').toString('utf8');
function array(name){const start=source.indexOf('const '+name+' = [');if(start<0)throw Error('Missing installed '+name);const end=source.indexOf('];',start);if(end<0)throw Error('Incomplete '+name);return source.slice(start,end+2);}
const start=source.indexOf('const buildPerfJvmArgs =');
const finish=source.indexOf('\n        };',source.indexOf('return perfJvmArgs;',start));
if(start<0||finish<0)throw Error('Installed JVM factory not found');
const factory=source.slice(start,finish+12);
const flags=vm.runInNewContext('(()=>{'+array('javaModuleArgs')+array('baseJvmArgs')+factory+';return [...javaModuleArgs,...baseJvmArgs,...buildPerfJvmArgs(21),"-Dfabric.debug.disableClassPathIsolation=true"];})()',{
 require(name){if(name!=='os')throw Error('Only CPU count is needed');return require('node:os');}
},{timeout:1000});
if(!Array.isArray(flags)||flags.some(x=>typeof x!=='string'||/[\r\n]/.test(x)))throw Error('Invalid JVM defaults');
process.stdout.write(JSON.stringify(flags));
