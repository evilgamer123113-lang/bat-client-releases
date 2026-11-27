const AdmZip=require('adm-zip');
const crypto=require('node:crypto');
const fs=require('node:fs');
function hasRawAsm(bytes) {
    if(bytes.readUInt32BE(0)!==0xCAFEBABE)throw new Error('Invalid agent class file.');
    const count=bytes.readUInt16BE(8);let offset=10;
    for(let index=1;index<count;index++){
        const tag=bytes[offset++];
        if(tag===1){const size=bytes.readUInt16BE(offset);offset+=2;const value=bytes.toString('utf8',offset,offset+size);offset+=size;
            if(/(^|[L[(])org\/objectweb\/asm\//.test(value))return true;
        }else if(tag===5||tag===6){offset+=8;index++;}
        else if(tag===3||tag===4||tag===9||tag===10||tag===11||tag===12||tag===17||tag===18)offset+=4;
        else if(tag===7||tag===8||tag===16||tag===19||tag===20)offset+=2;
        else if(tag===15)offset+=3;
        else throw new Error('Invalid agent constant-pool tag.');
    }return false;
}
function verifyAgent(file) {
    const bytes=fs.readFileSync(file),zip=new AdmZip(bytes);
    for(const entry of ['com/batclient/cosmetics/AgentMain.class','batclient/shaded/org/objectweb/asm/ClassVisitor.class','batclient/shaded/org/objectweb/asm/ClassReader.class','batclient/shaded/org/objectweb/asm/tree/ClassNode.class']) {
        if(!zip.getEntry(entry))throw new Error('Cosmetics agent is incomplete or not relocated. Rebuild with npm run build:agent.');
    }
    if(zip.getEntry('org/objectweb/asm/ClassVisitor.class'))throw new Error('Cosmetics agent contains unrelocated ASM. Rebuild with npm run build:agent.');
    for(const entry of zip.getEntries())if(entry.entryName.endsWith('.class')&&hasRawAsm(entry.getData()))throw new Error('Cosmetics agent still references unrelocated ASM bytecode in '+entry.entryName+'. Rebuild with npm run build:agent.');
    if(!/Premain-Class:\s*com\.batclient\.cosmetics\.AgentMain/.test(zip.readAsText('META-INF/MANIFEST.MF')))throw new Error('Cosmetics agent manifest is invalid.');
    return crypto.createHash('sha256').update(bytes).digest('hex');
}
module.exports={verifyAgent};
