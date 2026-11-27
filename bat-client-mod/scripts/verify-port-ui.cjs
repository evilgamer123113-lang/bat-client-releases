'use strict';
const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const assert=require('node:assert/strict');
const roots=process.argv.slice(2).map(value=>path.resolve(value));
assert.ok(roots.length,'Pass the private build output directories to check');
const keys=[];
for(const root of roots){
  const source=fs.readFileSync(path.join(root,'generated/protected-ui/com/batclient/web/UiAssetKey.java'),'utf8');
  const arrays=['a','b'].map(name=>{
    const match=source.match(new RegExp('byte\\[\\] '+name+'=\\{([^}]+)\\}'));
    assert.ok(match,'Missing generated key array');
    return Buffer.from(match[1].split(',').map(Number));
  });
  const key=Buffer.from(arrays[0].map((value,index)=>value^arrays[1][index]));
  assert.equal(key.length,32);keys.push(key);
  for(const name of ['app.js','index.html','brand.html']){
    const packed=fs.readFileSync(path.join(root,'packaged-web/assets/batclient/web',name));
    const encrypted=fs.readFileSync(path.join(root,'protected-web/assets/batclient/web',name+'.bin'));
    assert.equal(encrypted.subarray(0,4).toString(),'BAT2');
    const decipher=crypto.createDecipheriv('aes-256-gcm',key,encrypted.subarray(4,16));
    decipher.setAAD(Buffer.from(name));decipher.setAuthTag(encrypted.subarray(-16));
    assert.deepEqual(Buffer.concat([decipher.update(encrypted.subarray(16,-16)),decipher.final()]),packed);
  }
  console.log('PORT_UI_KEY_ASSET_ROUNDTRIP_OK: '+path.relative(process.cwd(),root));
}
assert.equal(new Set(keys.map(key=>key.toString('hex'))).size,keys.length,'Targets must have separate UI key generation');
console.log('PORT_UI_ISOLATION_OK: '+roots.length+' independently authenticated output sets');
