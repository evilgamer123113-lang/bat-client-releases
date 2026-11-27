const {spawnSync} = require('node:child_process');
const path = require('node:path');
const result = spawnSync(process.platform === 'win32' ? 'gradlew.bat' : 'sh', process.platform === 'win32'
    ? ['relocateJar', '--console=plain'] : ['./gradlew', 'relocateJar', '--console=plain'],
    {cwd:path.resolve(__dirname,'../bat-client-agent'),stdio:'inherit',shell:process.platform === 'win32'});
if (result.error) throw result.error;
if(result.status!==0)process.exit(result.status ?? 1);
require('../launcher/agent-package').verifyAgent(path.resolve(__dirname,'../bat-client-agent/build/libs/bat-cosmetics.jar'));
console.log('AGENT_PACKAGE_OK: self-contained relocated ASM');
const java=process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java') : 'java';
const smoke=spawnSync(java,['-javaagent:'+path.resolve(__dirname,'../bat-client-agent/build/libs/bat-cosmetics.jar')+'=1.21.11,intermediary','-version'],{encoding:'utf8',windowsHide:true,timeout:15000});
const output=String(smoke.stdout || '')+String(smoke.stderr || '');
if(smoke.error||smoke.status!==0||output.includes('agent init failed')||!output.includes('installed for MC 1.21.11'))throw new Error('Cosmetics agent startup check failed: '+output);
console.log('AGENT_STARTUP_OK: premain installed without external ASM');
