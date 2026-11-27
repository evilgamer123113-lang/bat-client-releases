'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('fs-extra'),os=require('node:os'),path=require('node:path');
const {injectOptimizationSettings}=require('../launcher/optimization');

test('launcher writes real Unlimited sentinels and disables VSync for selected Minecraft generations',async()=>{
    const directory=await fs.mkdtemp(path.join(os.tmpdir(),'bat-unlimited-fps-'));
    try {
        for(const version of ['1.8','1.8.9','1.16.5','1.20.6','1.21.1','1.21.11']){
            const root=path.join(directory,version);await fs.ensureDir(root);
            await fs.writeFile(path.join(root,'options.txt'),'maxFps:60\nenableVsync:true\ncustomUnrelatedOption:preserve\n');
            const result=await injectOptimizationSettings({mcVersion:version,root,appSettings:{autoOptimize:true},fs,path});
            assert.ok(result && !result.skipped);
            const actual=await fs.readFile(path.join(root,'options.txt'),'utf8');
            assert.match(actual,new RegExp('^maxFps:'+(version.startsWith('1.8')?'999':'260')+'$','m'));
            assert.match(actual,/^enableVsync:false$/m);
            assert.match(actual,/^customUnrelatedOption:preserve$/m);
            assert.doesNotMatch(actual,/Infinity|NaN/);
        }
    } finally {
        const target=path.resolve(directory);
        assert.equal(path.dirname(target),path.resolve(os.tmpdir()));
        assert.ok(path.basename(target).startsWith('bat-unlimited-fps-'));
        assert.equal((await fs.lstat(target)).isSymbolicLink(),false);
        await fs.remove(target);
    }
});
