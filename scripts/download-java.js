const fs = require('node:fs');
const path = require('node:path');
const https = require('node:https');
const crypto = require('node:crypto');
const {pipeline} = require('node:stream/promises');
const {promisify} = require('node:util');
const {execFile} = require('node:child_process');
const {platformInfo,javaExecutable} = require('../launcher/platform');
function send(message) { process.stdout.write(JSON.stringify(message)+'\n'); }
async function request(url) {
    for (let i=0;i<6;i++) {
        const res = await new Promise((resolve,reject)=>{ const req=https.get(url,{headers:{'User-Agent':'BatClient/Launcher'}},resolve); req.on('error',reject); req.setTimeout(60000,()=>req.destroy(new Error('Java download timed out'))); });
        if (res.statusCode>=300 && res.statusCode<400 && res.headers.location) {url=new URL(res.headers.location,url).href; res.resume(); continue;}
        if (res.statusCode !== 200) {res.resume();throw new Error('Java download HTTP '+res.statusCode);}
        return res;
    }
    throw new Error('Too many Java download redirects');
}
function findJava(dir, name) {
    for (const entry of fs.readdirSync(dir,{withFileTypes:true})) {
        const full=path.join(dir,entry.name);
        if (entry.isDirectory()) { const found=findJava(full,name); if(found)return found; }
        else if (entry.name===name && path.basename(dir)==='bin') return full;
    }
    return null;
}
async function installJava(major,target,cache) {
    const info=platformInfo();
    const url=`https://api.adoptium.net/v3/assets/latest/${major}/hotspot?os=${info.javaOs}&architecture=${info.javaArch}&image_type=jre`;
    send({type:'status',message:`Fetching Java ${major} for ${info.javaOs}/${info.javaArch}...`});
    const metadata = await request(url); let body=''; for await(const chunk of metadata)body+=chunk;
    const assets=JSON.parse(body);
    const pkg=assets.map(item=>item.binary?.package).find(item=>item?.name?.endsWith(info.archive));
    if (!pkg || !/^[a-f0-9]{64}$/i.test(pkg.checksum || '')) throw new Error('No verified Java package available for this OS/architecture');
    fs.mkdirSync(cache,{recursive:true});
    const archive=path.join(cache,`jre_temp_${major}_${process.arch}${info.archive}`);
    const res=await request(pkg.link), hash=crypto.createHash('sha256'); let downloaded=0;
    const total=Number(res.headers['content-length']);
    res.on('data',chunk=>{hash.update(chunk);downloaded+=chunk.length;if(total)send({type:'progress',percent:Math.round(downloaded*100/total)});});
    await pipeline(res,fs.createWriteStream(archive));
    if(hash.digest('hex').toLowerCase()!==pkg.checksum.toLowerCase())throw new Error('Java archive checksum mismatch');
    const resolved=path.resolve(target), scratch=resolved+'.extract-'+crypto.randomUUID();
    fs.mkdirSync(scratch,{recursive:true});
    try {
        if (info.archive==='.zip') {
            const zip=new (require('adm-zip'))(archive);
            for(const entry of zip.getEntries())if(entry.entryName.startsWith('/') || entry.entryName.replace(/\\/g,'/').split('/').includes('..'))throw new Error('Unsafe Java ZIP');
            zip.extractAllTo(scratch,true);
        } else await promisify(execFile)('tar',['-xzf',archive,'-C',scratch],{timeout:120000});
        const executable=findJava(scratch,info.javaName);
        if (!executable)throw new Error('Java executable missing after extraction');
        const javaRoot=path.dirname(path.dirname(executable));
        // Replace only the explicitly resolved Java runtime directory, never the cache/game root.
        if (resolved===path.parse(resolved).root || resolved===path.resolve(cache))throw new Error('Unsafe Java destination');
        fs.mkdirSync(resolved,{recursive:true});
        fs.cpSync(javaRoot,resolved,{recursive:true,verbatimSymlinks:true});
        if(process.platform!=='win32')fs.chmodSync(javaExecutable(resolved),0o755);
        await promisify(execFile)(javaExecutable(resolved),['-version'],{timeout:10000});
    } finally {fs.rmSync(scratch,{recursive:true,force:true});fs.rmSync(archive,{force:true});}
    send({type:'done',message:`Java ${major} ready.`});
}
if(require.main===module)installJava(parseInt(process.argv[2])||21,process.argv[3],process.argv[4]).catch(error=>{send({type:'error',message:error.message});process.exitCode=1;});
module.exports={findJava,installJava};
