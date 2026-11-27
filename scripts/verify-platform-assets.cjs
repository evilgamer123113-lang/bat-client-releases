const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const crypto=require('node:crypto');
const AdmZip=require('adm-zip');
const asar=require('@electron/asar');
const {validateMacZip}=require('../launcher/update-platform');
const version=require('../package.json').version;
const dir=path.resolve(process.argv[2]||'dist/platform-release');
const report=[];
function verifyPackagedAgent(buffer,label){
    assert.ok(buffer,label+' contains cosmetics agent');
    assert.equal(crypto.createHash('sha256').update(buffer).digest('hex'),crypto.createHash('sha256').update(fs.readFileSync(path.resolve('bat-client-agent/build/libs/bat-cosmetics.jar'))).digest('hex'),label+' includes exact corrected agent');
    const temp=path.join(dir,'.verify-agent-'+crypto.randomUUID()+'.jar');fs.writeFileSync(temp,buffer);
    try{require('../launcher/agent-package').verifyAgent(temp);}finally{fs.unlinkSync(temp);}
}
function verifyAsar(buffer,label) {
    const temp=path.join(dir,'.verify-'+crypto.randomUUID()+'.asar');
    fs.writeFileSync(temp,buffer);
    try {
        const pkg=JSON.parse(asar.extractFile(temp,'package.json'));
        assert.equal(pkg.version,version,label+' version');
        assert.equal(pkg.name,'bat-client');
        assert.ok(asar.extractFile(temp,'main.js').includes(Buffer.from("require('./launcher/update-platform')")),label+' native updater');
        assert.ok(asar.extractFile(temp,'launcher/platform.js').length>0);
        for(const file of ['launcher/java-runtime.js','launcher/jvm-arguments.js','launcher/agent-package.js','launcher/mod-compatibility.js'])assert.ok(asar.extractFile(temp,file).length>0,label+' includes '+file);
        assert.ok(asar.extractFile(temp,'main.js').includes(Buffer.from('composeJvmArgs([')),label+' uses collector composition');
        assert.ok(asar.extractFile(temp,'launcher/jvm-arguments.js').includes(Buffer.from('function composeJvmArgs')),label+' includes collector fix');
        assert.ok(asar.extractFile(temp,'launcher/mod-compatibility.js').includes(Buffer.from('kindscrystaloptimizer')),label+' includes optimizer guard');
        assert.ok(asar.extractFile(temp,'scripts/download-java.js').length>0);
        const files=asar.listPackage(temp);
        assert.ok(!files.some(file=>/bat-client-server[\\/]data[\\/]|[\\/]\.env/.test(file)),label+' excludes private backend data');
    } finally { fs.unlinkSync(temp); }
}
for(const [arch,cpu] of [['x64',0x1000007],['arm64',0x100000c]]) {
    const name=`BatClient-${version}-macOS-${arch}.zip`,zip=new AdmZip(path.join(dir,name));
    validateMacZip(zip);
    const binary=zip.readFile('Bat Client.app/Contents/MacOS/Bat Client');
    assert.ok(binary,'macOS executable');
    assert.equal(binary.readUInt32LE(4),cpu,'Mach-O processor matches '+arch);
    assert.match(zip.readAsText('Bat Client.app/Contents/Info.plist'),new RegExp('<string>'+version.replaceAll('.','\\.')+'</string>'));
    verifyAsar(zip.readFile('Bat Client.app/Contents/Resources/app.asar'),name);
    verifyPackagedAgent(zip.readFile('Bat Client.app/Contents/Resources/bundled/bat-cosmetics.jar'),name);
    report.push(name);
}
const portable=`BatClient-${version}-Portable.zip`,zip=new AdmZip(path.join(dir,portable));
for(const entry of zip.getEntries())assert.ok(!entry.entryName.includes('\\')&&!entry.entryName.startsWith('/')&&!entry.entryName.split('/').includes('..'),'safe Windows ZIP');
assert.equal(zip.readFile('Bat Client.exe').subarray(0,2).toString(),'MZ');
verifyAsar(zip.readFile('resources/app.asar'),portable);
verifyPackagedAgent(zip.readFile('resources/bundled/bat-cosmetics.jar'),portable);
report.push(portable);
const setup=`BatClient-${version}-Setup.exe`;
const exeHeader=Buffer.alloc(2),exe=fs.openSync(path.join(dir,setup),'r');
try {fs.readSync(exe,exeHeader,0,2,0);assert.equal(exeHeader.toString(),'MZ');} finally {fs.closeSync(exe);}
report.push(setup);
const linux=`BatClient-${version}-Linux-x86_64.AppImage`;
const header=Buffer.alloc(20),handle=fs.openSync(path.join(dir,linux),'r');
try {fs.readSync(handle,header,0,20,0);} finally {fs.closeSync(handle);}
assert.equal(header.subarray(0,4).toString('hex'),'7f454c46');
assert.equal(header.subarray(8,11).toString('hex'),'414902');
assert.equal(header.readUInt16LE(18),62,'Linux x86_64 architecture');
report.push(linux);
console.log('ALL_PLATFORM_ASSETS_VERIFIED',JSON.stringify({version,files:report}));
