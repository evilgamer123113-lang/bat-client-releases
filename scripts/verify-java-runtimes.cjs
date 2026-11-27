const assert=require('node:assert/strict');
const path=require('node:path');
const {execFileSync}=require('node:child_process');
const {javaExecutable}=require('../launcher/platform');
const {probeJava,findJava}=require('../launcher/java-runtime');
async function main(){
    const [home17,home21]=process.argv.slice(2),java17=javaExecutable(home17),java21=javaExecutable(home21);
    assert.equal((await probeJava(java17))?.major,17,'native Java17 version');
    assert.equal((await probeJava(java21))?.major,21,'native Java21 version');
    assert.equal((await findJava(17,{custom:java21,bundled:java17}))?.path,java17,'wrong custom Java21 does not suppress Java17');
    assert.equal((await findJava(21,{custom:java17,bundled:java21}))?.path,java21,'wrong custom Java17 does not suppress Java21');
    const agent=path.resolve('bat-client-agent/build/libs/bat-cosmetics.jar');
    for(const java of [java17,java21]){
        const output=execFileSync(java,['-javaagent:'+agent+'=1.21.11,intermediary','-version'],{encoding:'utf8',windowsHide:true,timeout:15000});
        assert.ok(output.includes('installed for MC 1.21.11')&&!output.includes('agent init failed'),'native self-contained agent startup');
    }
    console.log('NATIVE_JAVA17_JAVA21_AND_AGENT_OK',process.platform,process.arch);
}
main().catch(error=>{console.error(error);process.exitCode=1;});
