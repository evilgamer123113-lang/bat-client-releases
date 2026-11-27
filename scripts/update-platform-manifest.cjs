const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const {pipeline}=require('node:stream/promises');
async function main() {
    const [baseFile,assetDir,outFile]=process.argv.slice(2);
    if(!outFile)throw new Error('Usage: node scripts/update-platform-manifest.cjs base-update.json dist output.json');
    const data=JSON.parse(fs.readFileSync(baseFile,'utf8'));
    const version=require('../package.json').version;
    async function sha(name) {
        const hash=crypto.createHash('sha256');
        await pipeline(fs.createReadStream(path.join(assetDir,name)),hash);
        return hash.digest('hex').toUpperCase();
    }
    if(data.version!==version) {
        const setup=`BatClient-${version}-Setup.exe`, portable=`BatClient-${version}-Portable.zip`;
        fs.accessSync(path.join(assetDir,setup));
        data.version=version;
        const base=`https://github.com/evilgamer123113-lang/bat-client-releases/releases/download/v${version}/`;
        data.website=base+setup; data.download=base+portable; data.sha256=await sha(portable);
        data.required=true;
        data.changelog=[
            'Native launcher packages for Windows, macOS Intel, macOS Apple Silicon and Linux x64',
            'Java installation now selects and verifies the correct operating system and processor package',
            'Platform-specific updates with SHA-256 checks, macOS app replacement and Linux AppImage replacement',
            'Native launcher startup and Java installation checks on dedicated build runners'
        ];
    }
    const definitions=[['darwin','x64',`BatClient-${version}-macOS-x64.zip`],['darwin','arm64',`BatClient-${version}-macOS-arm64.zip`],['linux','x64',`BatClient-${version}-Linux-x86_64.AppImage`]];
    data.platforms=data.platforms||{};
    data.platforms.win32={x64:{download:data.download,website:data.website,sha256:data.sha256}};
    for(const [os,arch,name] of definitions) {
        const url=`https://github.com/evilgamer123113-lang/bat-client-releases/releases/download/v${version}/${name}`;
        data.platforms[os]=data.platforms[os]||{};
        data.platforms[os][arch]={download:url,website:url,sha256:await sha(name)};
    }
    fs.writeFileSync(outFile,JSON.stringify(data,null,2)+'\n');
    console.log('ALL_PLATFORM_MANIFEST_OK (Windows legacy fields preserved)');
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
