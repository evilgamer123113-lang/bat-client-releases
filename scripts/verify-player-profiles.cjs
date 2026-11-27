const fs=require('node:fs/promises');
const path=require('node:path');
const {execFileSync}=require('node:child_process');
async function main(){
    const root=path.resolve('build/profile-regression');await fs.mkdir(root,{recursive:true});
    const artifacts={
        'authlib-7.jar':'https://libraries.minecraft.net/com/mojang/authlib/7.0.61/authlib-7.0.61.jar',
        'authlib-6.jar':'https://libraries.minecraft.net/com/mojang/authlib/6.0.54/authlib-6.0.54.jar',
        'guava.jar':'https://libraries.minecraft.net/com/google/guava/guava/33.5.0-jre/guava-33.5.0-jre.jar',
        'gson.jar':'https://libraries.minecraft.net/com/google/code/gson/gson/2.13.2/gson-2.13.2.jar'
    };
    for(const [name,url] of Object.entries(artifacts)){
        const response=await fetch(url,{signal:AbortSignal.timeout(30000)});if(!response.ok)throw Error('Could not obtain '+name+': '+response.status);
        await fs.writeFile(path.join(root,name),Buffer.from(await response.arrayBuffer()));
    }
    const exe=name=>process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin',name+(process.platform==='win32'?'.exe':'')):name;
    const agent=path.resolve('bat-client-agent/build/libs/bat-cosmetics.jar');
    const shared=[root,agent,path.join(root,'guava.jar'),path.join(root,'gson.jar')];
    execFileSync(exe('javac'),['--release','8','-cp',agent,'-d',root,'scripts/PlayerProfileSmoke.java'],{stdio:'inherit',windowsHide:true});
    for(const name of ['authlib-6.jar','authlib-7.jar']){
        const output=execFileSync(exe('java'),['-cp',[...shared,path.join(root,name)].join(path.delimiter),'PlayerProfileSmoke'],{encoding:'utf8',windowsHide:true,timeout:20000});
        if(!output.includes('AUTHLIB_PROFILE_OK: 8 checks'))throw Error('Profile regression failed: '+output);
        console.log(output.trim());
    }
}
main().catch(e=>{console.error(e);process.exitCode=1;});
