// Upload only explicit build input to the existing private, isolated build branch.
const {execFileSync}=require('node:child_process');
const AdmZip=require('adm-zip');
const crypto=require('node:crypto');
const repo='evilgamer123113-lang/bat-client-launcher',branch='codex/platform-packages';
const token=execFileSync('gh',['auth','token'],{encoding:'utf8'}).trim();
async function api(endpoint,data,method='POST'){
    for(let attempt=0;attempt<4;attempt++)try{
        const response=await fetch('https://api.github.com/'+endpoint,{method:data?method:'GET',headers:{Authorization:'Bearer '+token,Accept:'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','Content-Type':'application/json','User-Agent':'Bat-Client-release'},body:data?JSON.stringify(data):undefined,signal:AbortSignal.timeout(25000)});
        if(!response.ok){const error=Error('GitHub API '+response.status+' '+endpoint);error.status=response.status;throw error;}
        return await response.json();
    }catch(error){if(error.status&&error.status<500||attempt===3)throw error;console.log('Retrying GitHub build upload request',attempt+1);}
}
(async()=>{
 const metadata=await api('repos/'+repo);if(!metadata.private)throw Error('Refusing public source upload');
 const existing=(await api(`repos/${repo}/git/ref/heads/${encodeURIComponent(branch)}`)).object.sha;
 const base=(await api(`repos/${repo}/git/commits/${existing}`)).tree.sha;
 const oldTree=await api(`repos/${repo}/git/trees/${base}?recursive=1`),oldBlobs=new Map(oldTree.tree.map(e=>[e.path,e.sha])),tree=[];
 for(const entry of new AdmZip('build/platform-build-input.zip').getEntries()){
  if(entry.isDirectory)continue;const bytes=entry.getData(),sha=crypto.createHash('sha1').update(Buffer.from('blob '+bytes.length+'\0')).update(bytes).digest('hex');if(oldBlobs.get(entry.entryName)===sha)continue;
  const blob=await api(`repos/${repo}/git/blobs`,{content:bytes.toString('base64'),encoding:'base64'});tree.push({path:entry.entryName,mode:'100644',type:'blob',sha:blob.sha});console.log('Prepared',entry.entryName);
 }
 const next=(await api(`repos/${repo}/git/trees`,{base_tree:base,tree})).sha;
 const commit=(await api(`repos/${repo}/git/commits`,{message:'Release '+require('../package.json').version+': fix collector conflicts, Fabric compatibility and Authlib skins',tree:next,parents:[existing]})).sha;
 await api(`repos/${repo}/git/refs/heads/${encodeURIComponent(branch)}`,{sha:commit,force:false},'PATCH');
 console.log('PRIVATE_BUILD_BRANCH_READY',branch,commit);
})().catch(error=>{console.error(error.message);process.exitCode=1;});
