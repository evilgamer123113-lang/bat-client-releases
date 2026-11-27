const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=[...fs.readFileSync('website/download.html','utf8').matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)].filter(match=>! /\bsrc\s*=/.test(match[1])).map(match=>match[2]).join('\n');
async function check(platform,manifest,expected) {
    const elements=new Map();
    function element(id) {
        if(!elements.has(id))elements.set(id,{textContent:'',style:{},attributes:{},classList:{add(){},remove(){}},setAttribute(key,value){this.attributes[key]=value;},addEventListener(){}});
        return elements.get(id);
    }
    const storage=new Map([['bat_dl_t3','test-token']]);
    let timer,resolveFetch;
    const context={URLSearchParams,navigator:{platform:'Win32'},location:{search:'?p='+platform+'&t=test-token',replace(){throw new Error('Unexpected redirect');}},sessionStorage:{getItem:key=>storage.get(key),setItem:(key,value)=>storage.set(key,value),removeItem:key=>storage.delete(key)},document:{getElementById:element},window:{},setInterval:fn=>{timer=fn;return 1;},clearInterval(){},fetch:()=>new Promise(resolve=>{resolveFetch=resolve;})};
    vm.runInNewContext(source,context);
    for(let i=0;i<7;i++)timer();
    assert.equal(element('dl-btn').attributes.href,undefined,'Cannot unlock before manifest arrives');
    resolveFetch({json:async()=>manifest});
    await new Promise(resolve=>setImmediate(resolve));
    assert.equal(element('dl-btn').attributes.href,expected,platform+' download URL');
}
(async()=>{
    const base='https://github.com/evilgamer123113-lang/bat-client-releases/';
    const manifest={website:base+'windows.exe',platforms:{darwin:{x64:{download:base+'intel.zip'},arm64:{download:base+'silicon.zip'}},linux:{x64:{download:base+'linux.AppImage'}}}};
    await check('windows',manifest,base+'windows.exe');
    await check('mac-intel',manifest,base+'intel.zip');
    await check('mac-arm',manifest,base+'silicon.zip');
    await check('linux',manifest,base+'linux.AppImage');
    await check('mac-arm',{website:base+'windows.exe'},undefined);
    console.log('DOWNLOAD_PLATFORM_SELECTION_AND_SLOW_MANIFEST_OK');
})().catch(error=>{console.error(error);process.exitCode=1;});
