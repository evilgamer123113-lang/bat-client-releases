const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');

// Generated sources/plain minified fixtures stay private under build/.
async function main() {
    const build=path.resolve(process.env.BATCLIENT_BUILD_DIR || path.resolve(__dirname,'../build'));
    const source=path.join(build,'packaged-web/assets/batclient/web');
    const output=path.join(build,'protected-web/assets/batclient/web');
    const generated=path.join(build,'generated/protected-ui/com/batclient/web');
    await fs.mkdir(output,{recursive:true});await fs.mkdir(generated,{recursive:true});
    const key=crypto.randomBytes(32),mask=crypto.randomBytes(32);
    const other=Buffer.from(key.map((value,index)=>value^mask[index]));
    const signed=bytes=>Array.from(bytes,value=>value>127?value-256:value).join(',');
    const keySource=`package com.batclient.web;\n// Generated per build. Do not distribute source.\nfinal class UiAssetKey {\n private UiAssetKey() {}\n static byte[] bytes() {\n byte[] a={${signed(mask)}};\n byte[] b={${signed(other)}};\n for(int i=0;i<a.length;i++)a[i]^=b[i];\n java.util.Arrays.fill(b,(byte)0);\n return a;\n }\n}\n`;
    const names=(await fs.readdir(source)).sort();
    for(const name of names) {
        if(!(await fs.stat(path.join(source,name))).isFile()||name.endsWith('.map'))continue;
        const nonce=crypto.randomBytes(12);
        const cipher=crypto.createCipheriv('aes-256-gcm',key,nonce);
        cipher.setAAD(Buffer.from(name,'utf8'));
        const plain=await fs.readFile(path.join(source,name));
        const payload=Buffer.concat([Buffer.from('BAT2'),nonce,cipher.update(plain),cipher.final(),cipher.getAuthTag()]);
        await fs.writeFile(path.join(output,name+'.bin'),payload);
    }
    // Write last so a partial run cannot look like a successful new key generation.
    await fs.writeFile(path.join(generated,'UiAssetKey.java'),keySource,'utf8');
    key.fill(0);mask.fill(0);other.fill(0);
    console.log('PROTECTED_WEB_OK (AES-256-GCM; runtime decryption only in memory)');
}
main().catch(()=>{console.error('UI encryption failed. No tester build should be distributed.');process.exitCode=1;});
