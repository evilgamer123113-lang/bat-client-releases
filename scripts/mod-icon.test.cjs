const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises'),os=require('node:os'),path=require('node:path');
const AdmZip=require('adm-zip');
const {readModIcon}=require('../launcher/mod-icon');
const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jXioAAAAASUVORK5CYII=','base64');
const expected='data:image/png;base64,'+png.toString('base64');

test('installed mod logos: metadata formats, disabled JARs, cache and safe fallback',async t=>{
    const directory=await fs.mkdtemp(path.join(os.tmpdir(),'bat-mod-icons-'));
    const archive=async(name,entries)=>{const zip=new AdmZip();for(const [entry,data] of Object.entries(entries))zip.addFile(entry,Buffer.isBuffer(data)?data:Buffer.from(data));const file=path.join(directory,name);await fs.writeFile(file,zip.toBuffer());return file;};
    try{
        await t.test('Fabric icon remains available when disabled',async()=>{
            const file=await archive('fabric.disabled',{'fabric.mod.json':JSON.stringify({id:'test',icon:'assets/test/icon.png'}),'assets/test/icon.png':png});
            assert.equal(await readModIcon(file),expected);assert.equal(await readModIcon(file),expected);
        });
        await t.test('Fabric size map selects an available icon',async()=>{
            const file=await archive('mapped.jar',{'fabric.mod.json':JSON.stringify({icon:{16:'small.png',128:'large.png'}}),'large.png':png});assert.equal(await readModIcon(file),expected);
        });
        await t.test('Quilt, Forge and NeoForge metadata',async()=>{
            for(const [name,metadata] of [['quilt.mod.json',JSON.stringify({quilt_loader:{metadata:{icon:'logo.png'}}})],['META-INF/mods.toml','[[mods]]\nlogoFile="logo.png"'],['META-INF/neoforge.mods.toml',"[[mods]]\nlogoFile='logo.png'"]]){
                const file=await archive(name.replaceAll('/','-')+'.jar',{[name]:metadata,'logo.png':png});assert.equal(await readModIcon(file),expected);
            }
        });
        await t.test('legacy metadata and Bat companion logo',async()=>{
            const legacy=await archive('legacy.jar',{'mcmod.info':JSON.stringify([{logoFile:'legacy.png'}]),'legacy.png':png});assert.equal(await readModIcon(legacy),expected);
            const bat=await archive('bat.jar',{'fabric.mod.json':JSON.stringify({id:'batclient'}),'assets/batclient/web/bat-logo.png':png});assert.equal(await readModIcon(bat),expected);
        });
        await t.test('archive replacement invalidates the cache',async()=>{
            const file=await archive('changed.jar',{'logo.png':png});assert.equal(await readModIcon(file),expected);
            await archive('changed.jar',{'other.txt':'different archive without an icon'});assert.equal(await readModIcon(file),null);
        });
        await t.test('oversized images, unknown files and traversal paths fall back',async()=>{
            const huge=await archive('large.jar',{'logo.png':Buffer.alloc(1024*1024+1)});assert.equal(await readModIcon(huge),null);
            const invalid=await archive('invalid.jar',{'logo.png':'not an image'});assert.equal(await readModIcon(invalid),null);
            const traversal=await archive('path.jar',{'fabric.mod.json':JSON.stringify({icon:'../outside.png'})});assert.equal(await readModIcon(traversal),null);
            const broken=path.join(directory,'broken.jar');await fs.writeFile(broken,'not a zip');assert.equal(await readModIcon(broken),null);
            assert.equal(await readModIcon(path.join(directory,'missing.jar')),null);
        });
    }finally{assert.equal(path.dirname(path.resolve(directory)),path.resolve(os.tmpdir()));assert.match(path.basename(directory),/^bat-mod-icons-/);await fs.rm(directory,{recursive:true,force:true});}
});
