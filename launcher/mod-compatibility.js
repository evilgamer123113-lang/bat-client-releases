const fs=require('node:fs/promises');
const path=require('node:path');
const AdmZip=require('adm-zip');
const REGISTRY='net/fabricmc/fabric/api/networking/v1/PayloadTypeRegistry';

// Read class/interface identity and method-reference kinds without loading any
// untrusted mod classes into Java or executing their entrypoints.
function classInfo(bytes){
    if(bytes.readUInt32BE(0)!==0xCAFEBABE)throw Error('Invalid Java class');
    const cp=[],count=bytes.readUInt16BE(8);let at=10;
    for(let i=1;i<count;i++){
        const tag=bytes[at++];
        if(tag===1){const n=bytes.readUInt16BE(at);at+=2;cp[i]={tag,text:bytes.toString('utf8',at,at+n)};at+=n;}
        else if([7,8,16,19,20].includes(tag)){cp[i]={tag,a:bytes.readUInt16BE(at)};at+=2;}
        else if([9,10,11,12,17,18].includes(tag)){cp[i]={tag,a:bytes.readUInt16BE(at),b:bytes.readUInt16BE(at+2)};at+=4;}
        else if(tag===3||tag===4)at+=4;
        else if(tag===5||tag===6){at+=8;i++;}
        else if(tag===15)at+=3;
        else throw Error('Unknown class constant tag '+tag);
    }
    return {isInterface:!!(bytes.readUInt16BE(at)&0x200),refs:cp.filter(e=>e&&(e.tag===10||e.tag===11)).map(e=>({owner:cp[cp[e.a].a].text,isInterface:e.tag===11}))};
}
function metadata(bytes){const zip=new AdmZip(bytes),entry=zip.getEntry('fabric.mod.json');return {zip,mod:entry?JSON.parse(entry.getData().toString('utf8')):null};}
function apiShape(archives){
    const shapes=new Map();
    function scan(bytes,depth=0){
        const {zip,mod}=metadata(bytes);
        if(mod?.id==='fabric-networking-api-v1'){
            for(const name of [REGISTRY,REGISTRY+'$Registry']){const entry=zip.getEntry(name+'.class');if(entry)shapes.set(name,classInfo(entry.getData()).isInterface);}
        }
        if(depth<3)for(const child of mod?.jars||[]){const entry=zip.getEntry(child.file);if(entry)scan(entry.getData(),depth+1);}
    }
    archives.forEach(bytes=>scan(bytes));return shapes;
}
function incompatibility(bytes,gameVersion,shapes=new Map()){
    const {zip,mod}=metadata(bytes);if(!mod)return null;
    // Confirmed crash from the supplied 1.21.11 / Fabric API 0.141.6 logs.
    // Do not substitute another version just because its listing claims support.
    if(gameVersion==='1.21.11'&&mod.id==='kindscrystaloptimizer'&&mod.version==='1.6.3')return "Kind's Crystal Optimizer 1.6.3 is incompatible with the Fabric networking API on Minecraft 1.21.11. No verified replacement is bundled.";
    // Scope the binary check to the crashing mod: unrelated multi-version mods
    // may intentionally carry optional compatibility classes they never load.
    if(mod.id!=='kindscrystaloptimizer')return null;
    if(shapes.size)for(const entry of zip.getEntries())if(entry.entryName.endsWith('.class')){
        const refs=classInfo(entry.getData()).refs;
        for(const ref of refs)if(shapes.has(ref.owner)&&shapes.get(ref.owner)!==ref.isInterface)return (mod.name||mod.id)+' was compiled against an incompatible Fabric networking API ('+ref.owner.split('/').pop()+').';
    }
    return null;
}
async function checkFabricMods(directory,gameVersion){
    const jars=[];
    for(const name of await fs.readdir(directory))if(name.endsWith('.jar'))jars.push({name,bytes:await fs.readFile(path.join(directory,name))});
    // A malformed archive must produce a visible launch error, not a false pass.
    const shapes=apiShape(jars.map(j=>j.bytes)),disabled=[];
    for(const jar of jars){
        const reason=incompatibility(jar.bytes,gameVersion,shapes);if(!reason)continue;
        const source=path.join(directory,jar.name);let target=source+'.disabled';
        try{await fs.access(target);target=source+'.compat-'+Date.now()+'.disabled';}catch(error){if(error.code!=='ENOENT')throw error;}
        await fs.rename(source,target);disabled.push({file:jar.name,disabledAs:path.basename(target),reason});
    }
    return disabled;
}
module.exports={classInfo,apiShape,incompatibility,checkFabricMods};
