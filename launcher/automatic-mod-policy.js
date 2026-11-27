const AdmZip=require('adm-zip');
// Official Modrinth project identity, plus its slug and Fabric mod identity.
const EXCLUDED=new Set(['hnoajsm7','techutils','technical-utilities','technical utilities']);
function excludedAutomaticProject(project){
    if(typeof project==='object'&&project)return [project.project_id,project.id,project.slug,project.title].some(excludedAutomaticProject);
    return EXCLUDED.has(String(project||'').trim().toLowerCase());
}
function fabricIdentity(bytes){
    const zip=new AdmZip(bytes),entry=zip.getEntry('fabric.mod.json');
    return entry?JSON.parse(entry.getData().toString('utf8')).id:null;
}
function assertAutomaticArtifactAllowed(bytes,oldBytes){
    const id=fabricIdentity(bytes);
    if(excludedAutomaticProject(id))throw Error('Technical Utilities is excluded from automatic downloads and updates.');
    if(oldBytes){const oldId=fabricIdentity(oldBytes);if(oldId&&oldId!==id)throw Error('The proposed update belongs to a different mod. Your existing JAR was preserved.');}
}
function assertAutomaticUrlAllowed(url){
    const parsed=new URL(url);
    const project=parsed.hostname==='cdn.modrinth.com'&&parsed.pathname.match(/^\/data\/([^/]+)\/versions\//)?.[1];
    if(excludedAutomaticProject(project))throw Error('Technical Utilities is excluded from automatic downloads and updates.');
}
module.exports={excludedAutomaticProject,fabricIdentity,assertAutomaticArtifactAllowed,assertAutomaticUrlAllowed};
