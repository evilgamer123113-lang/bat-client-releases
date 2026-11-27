const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {javaMajor,probeJava,findJava}=require('../launcher/java-runtime');
const {validateJvmArgs}=require('../launcher/jvm-arguments');
const {verifyAgent}=require('../launcher/agent-package');

test('normal Java 8,17,21 version strings and vendor output',()=>{
    for(const [output,major] of [['java version "1.8.0_442"',8],['openjdk version "17.0.12" 2024-07-16',17],['openjdk 21.0.12 2026-07-21',21],['java version "21"',21],['not java',null]])assert.equal(javaMajor(output),major);
});
test('probe handles Java stderr, spaces in executable path and failed executables',async()=>{
    const executable='C:\\Program Files\\Java\\bin\\java.exe';
    const result=await probeJava(executable,async(file,args,options)=>{assert.equal(file,executable);assert.deepEqual(args,['-version']);assert.equal(options.windowsHide,true);return {stderr:'openjdk version "17.0.12"',stdout:''};});
    assert.equal(result.major,17);
    assert.equal(await probeJava('broken',async()=>{throw Error('ENOENT');}),null);
});
test('wrong custom Java cannot suppress correct bundled runtime',async()=>{
    const root=fs.mkdtempSync(path.join(os.tmpdir(),'bat-java-'));
    try{
        const custom=path.join(root,'custom-java'),bundled=path.join(root,'bundled-java'),seen=[];
        const found=await findJava(21,{custom,bundled,env:{},platform:'linux'},async candidate=>{seen.push(candidate);return {path:candidate,major:candidate===bundled?21:8};});
        assert.equal(found.path,bundled);assert.deepEqual(seen,[custom,bundled]);
        const missing=await findJava(17,{custom,bundled,env:{},platform:'linux'},async candidate=>({path:candidate,major:21}));assert.equal(missing,null);
    }finally{fs.rmSync(root,{recursive:true,force:true});}
});
test('rejects the attached log main-class injection and managed launch options',()=>{
    for(const raw of ['dgfddrdfg -DFabricMcEmu= net.minecraft.client.main.Main','-Dfoo=1 net.minecraft.client.main.Main','-cp something','--class-path=abc','-jar bad.jar','@options.txt','--version','--add-opens','-Dfoo="unfinished'])assert.throws(()=>validateJvmArgs(raw));
});
test('keeps valid quoted JVM values and paired module options as individual tokens',()=>{
    assert.deepEqual(validateJvmArgs('-Xmx4G -XX:+UseG1GC -Dfolder="C:\\Program Files\\Bat Client" --add-opens java.base/java.lang=ALL-UNNAMED'),['-Xmx4G','-XX:+UseG1GC','-Dfolder=C:\\Program Files\\Bat Client','--add-opens','java.base/java.lang=ALL-UNNAMED']);
    assert.deepEqual(validateJvmArgs(''),[]);
});
test('rejects incomplete/raw-ASM agents and verifies the bundled self-contained artifact',()=>{
    const root=fs.mkdtempSync(path.join(os.tmpdir(),'bat-agent-'));
    try{
        const AdmZip=require('adm-zip'),file=path.join(root,'bad.jar'),zip=new AdmZip();zip.addFile('com/batclient/cosmetics/AgentMain.class',Buffer.from('test'));zip.writeZip(file);
        assert.throws(()=>verifyAgent(file),/incomplete/);
        const good=path.resolve(__dirname,'../bat-client-agent/build/libs/bat-cosmetics.jar');
        assert.match(verifyAgent(good),/^[0-9a-f]{64}$/);
        // Reproduce the old bug: all shaded files exist, but executable bytecode still references raw ASM.
        const damaged=new AdmZip(good),entry='batclient/shaded/org/objectweb/asm/ClassWriter.class',bytes=damaged.readFile(entry);
        const shaded=Buffer.from('batclient/shaded/org/objectweb/asm/ClassVisitor'),raw=Buffer.from('org/objectweb/asm/ClassVisitor');
        const header=Buffer.alloc(3);header[0]=1;header.writeUInt16BE(shaded.length,1);const offset=bytes.indexOf(Buffer.concat([header,shaded]))+3;assert.ok(offset>3);
        const size=Buffer.alloc(2);size.writeUInt16BE(raw.length);
        damaged.deleteFile(entry);damaged.addFile(entry,Buffer.concat([bytes.subarray(0,offset-2),size,raw,bytes.subarray(offset+shaded.length)]));damaged.writeZip(file);
        assert.throws(()=>verifyAgent(file),/unrelocated ASM bytecode/);
    }finally{fs.rmSync(root,{recursive:true,force:true});}
});
