"use strict";
(()=>{let editing=null,versions=[],versionRequest=null;
const SUPPORTED_VERSIONS=new Set(['1.8','1.8.9','1.16.5','1.17.1','1.18.2','1.19.2','1.19.4','1.20.1','1.20.4','1.20.6','1.21.1','1.21.4','1.21.5','1.21.8','1.21.11']);
const SUPPORTED_ENGINES=new Set(['fabric','forge']);
const isSupportedVersion=(versionId,engine=($('#profile-engine')?.value||'').toLowerCase())=>SUPPORTED_VERSIONS.has(versionId)&&SUPPORTED_ENGINES.has(engine);
const versionBrowser=document.createElement('div');versionBrowser.className='version-browser';
const filters=document.createElement('div');filters.className='version-filters';filters.setAttribute('aria-label','Minecraft version types');
const kinds=new Set(['release']);for(const [kind,label]of [['release','Releases'],['snapshot','Snapshots'],['old_beta','Beta'],['old_alpha','Alpha']]){const row=document.createElement('label'),toggle=document.createElement('input');toggle.type='checkbox';toggle.checked=kinds.has(kind);toggle.setAttribute('role','switch');toggle.addEventListener('change',()=>{toggle.checked?kinds.add(kind):kinds.delete(kind);renderVersions();});row.append(toggle,document.createTextNode(label));filters.append(row);}
const versionList=document.createElement('div');versionList.id='minecraft-version-list';versionList.className='version-list';versionList.setAttribute('role','listbox');versionList.setAttribute('aria-label','Available Minecraft versions');
const versionStatus=document.createElement('p');versionStatus.setAttribute('role','status');
const retry=document.createElement('button');retry.type='button';retry.className='mods-secondary';retry.textContent='Retry version list';retry.hidden=true;retry.addEventListener('click',loadVersions);
const versionSearch=document.createElement('input');versionSearch.id='minecraft-version-search';versionSearch.type='search';versionSearch.placeholder='Search Minecraft versions';versionSearch.setAttribute('aria-label','Search Minecraft versions');versionSearch.maxLength=64;versionSearch.addEventListener('input',renderVersions);versionBrowser.append(filters,versionSearch,versionList,versionStatus,retry);
const versionField=$('#profile-game-version');versionField.type='hidden';versionField.removeAttribute('list');
const versionTrigger=document.createElement('button');versionTrigger.type='button';versionTrigger.id='version-trigger';versionTrigger.setAttribute('aria-haspopup','dialog');versionTrigger.setAttribute('aria-expanded','false');versionTrigger.setAttribute('aria-controls','version-popup');
versionField.after(versionTrigger);
versionBrowser.id='version-popup';versionBrowser.setAttribute('popover','manual');versionBrowser.setAttribute('role','dialog');versionBrowser.setAttribute('aria-label','Choose Minecraft version');$('#profile-editor').append(versionBrowser);

const modStatusNote=document.createElement('p');modStatusNote.id='profile-mod-status';modStatusNote.className='profile-mod-status';modStatusNote.setAttribute('role','status');
$('#profile-error').before(modStatusNote);

function createSupportTag(supported){
  const tag=document.createElement('span');
  tag.className='version-support-tag '+(supported?'supported':'unsupported');
  tag.textContent=supported?'Supported':'Not supported';
  return tag;
}
function updateVersionTrigger(versionId){
  const label=document.createElement('span');
  label.className='version-trigger-label';
  label.textContent=versionId||'Select version';
  const right=document.createElement('span');
  right.className='version-trigger-right';
  if(versionId)right.append(createSupportTag(isSupportedVersion(versionId)));
  const caret=document.createElement('span');caret.className='version-trigger-caret';caret.textContent='▾';
  right.append(caret);
  versionTrigger.replaceChildren(label,right);
}
let statusRequestSeq=0;
async function refreshCompanionStatus(){
  const version=$('#profile-game-version').value;
  const engine=$('#profile-engine').value;
  const supported=isSupportedVersion(version,engine);
  modStatusNote.className='profile-mod-status '+(supported?'is-supported':'is-unsupported');
  if(!invoke){
    modStatusNote.textContent=supported
      ?`Supported (${engine} ${version}) · Bat Client mod loads automatically on launch.`
      :`Not supported (${engine} ${version}) · Bat Client mod supports 30 Fabric/Forge versions.`;
    return;
  }
  const seq=++statusRequestSeq;
  try{
    const result=await invoke('prepare_companion_mod',{version,engine});
    if(seq!==statusRequestSeq)return;
    modStatusNote.className='profile-mod-status '+(result?.supported?'is-supported':'is-unsupported');
    modStatusNote.textContent=result?.message||'';
  }catch{
    if(seq!==statusRequestSeq)return;
    modStatusNote.textContent=supported
      ?`Supported (${engine} ${version}) · Bat Client mod loads automatically on launch.`
      :`Not supported (${engine} ${version}).`;
  }
}

function closeVersions(){if(versionBrowser.matches(':popover-open'))versionBrowser.hidePopover();versionTrigger.setAttribute('aria-expanded','false');}
function openVersions(){if(versionBrowser.matches(':popover-open'))return closeVersions();versionBrowser.showPopover();versionTrigger.setAttribute('aria-expanded','true');const rect=versionTrigger.getBoundingClientRect();versionBrowser.style.width=Math.min(440,innerWidth-32)+'px';versionBrowser.style.left=Math.max(16,Math.min(rect.left,innerWidth-versionBrowser.offsetWidth-16))+'px';const height=versionBrowser.offsetHeight;versionBrowser.style.top=Math.max(16,Math.min(rect.bottom+8,innerHeight-height-16))+'px';versionSearch.focus();loadVersions();}
versionTrigger.addEventListener('click',openVersions);
versionTrigger.addEventListener('keydown',event=>{if(event.key==='ArrowDown'){event.preventDefault();openVersions();}});
versionBrowser.addEventListener('keydown',event=>{if(event.key==='Escape'){event.preventDefault();event.stopPropagation();closeVersions();versionTrigger.focus();}});
document.addEventListener('pointerdown',event=>{if(!versionBrowser.contains(event.target)&&!versionTrigger.contains(event.target))closeVersions();});
window.addEventListener('resize',closeVersions);
$('#profile-editor').addEventListener('close',closeVersions);

function selectVersion(versionId,closeAfter=false){
  versionField.value=versionId;
  updateVersionTrigger(versionId);
  for(const item of versionList.querySelectorAll('.version-option')){
    const selected=item.dataset.version===versionId;
    item.setAttribute('aria-selected',String(selected));
    item.classList.toggle('selected',selected);
  }
  void refreshCompanionStatus();
  if(closeAfter){closeVersions();versionTrigger.focus();}
}

function renderVersions(){
  const query=versionSearch.value.trim().toLowerCase();
  const engine=$('#profile-engine').value;
  const selectedId=$('#profile-game-version').value;
  const matches=versions.filter(v=>kinds.has(v.kind)&&(!query||v.id.toLowerCase().includes(query)));
  versionList.replaceChildren();
  for(const v of matches){
    const supported=isSupportedVersion(v.id,engine);
    const option=document.createElement('button');
    option.type='button';
    option.className='version-option '+(supported?'is-supported':'is-unsupported');
    option.setAttribute('role','option');
    option.dataset.version=v.id;
    const selected=v.id===selectedId;
    option.setAttribute('aria-selected',String(selected));
    if(selected)option.classList.add('selected');
    const left=document.createElement('span');
    left.className='version-option-label';
    left.textContent=v.id+' · '+({'release':'Release','snapshot':'Snapshot','old_beta':'Beta','old_alpha':'Alpha'}[v.kind]||v.kind);
    option.append(left,createSupportTag(supported));
    option.addEventListener('click',()=>selectVersion(v.id,true));
    versionList.append(option);
  }
  const supportedCount=matches.filter(v=>isSupportedVersion(v.id,engine)).length;
  versionStatus.textContent=versions.length?`${matches.length} matching versions · ${supportedCount} supported on ${engine}`:'Loading the official Minecraft catalog…';
}

versionList.addEventListener('keydown',event=>{
  const options=[...versionList.querySelectorAll('.version-option')];
  if(!options.length)return;
  const activeIndex=options.indexOf(document.activeElement);
  if(event.key==='ArrowDown'){
    event.preventDefault();
    const next=options[Math.min(options.length-1,activeIndex<0?0:activeIndex+1)];
    next.focus();
    selectVersion(next.dataset.version,false);
  }else if(event.key==='ArrowUp'){
    event.preventDefault();
    const prev=options[Math.max(0,activeIndex<0?0:activeIndex-1)];
    prev.focus();
    selectVersion(prev.dataset.version,false);
  }else if(event.key==='Enter'&&document.activeElement?.classList.contains('version-option')){
    event.preventDefault();
    selectVersion(document.activeElement.dataset.version,true);
  }
});
versionSearch.addEventListener('keydown',event=>{
  if(event.key==='ArrowDown'){
    event.preventDefault();
    const first=versionList.querySelector('.version-option.selected')||versionList.querySelector('.version-option');
    if(first)first.focus();
  }
});
$('#profile-engine').addEventListener('change',()=>{
  updateVersionTrigger($('#profile-game-version').value);
  if(versions.length)renderVersions();
  void refreshCompanionStatus();
});

async function loadVersions(){if(!invoke)return;retry.hidden=true;if(versions.length){renderVersions();return;}if(versionRequest)return versionRequest;versionStatus.textContent='Loading the official Minecraft catalog…';versionRequest=(async()=>{try{versions=await invoke('minecraft_versions');renderVersions();}catch{versionStatus.textContent='The official catalog could not be loaded. You can still enter a version manually.';retry.hidden=false;}finally{versionRequest=null;}})();return versionRequest;}
const dialog=$('#profile-editor');
const openFolder=document.createElement('button');
openFolder.type='button';openFolder.className='mods-secondary';openFolder.textContent='Open game folder';openFolder.title='This folder is shared by your loadouts';openFolder.hidden=true;
dialog.querySelector('.editor-footer').prepend(openFolder);
const openInstance=document.createElement('button');
openInstance.type='button';
openInstance.className='mods-secondary';
openInstance.textContent='Open legacy instance';
openInstance.title='Open existing files from an older launcher loadout';
openInstance.hidden=true;
openFolder.after(openInstance);
openInstance.addEventListener('click',async()=>{
  if(!editing)return;
  openInstance.disabled=true;
  try{await invoke('tools_action',{action:'legacy_instance',payload:{profileId:editing,open:true}});}
  catch(error){$('#profile-error').textContent=String(error);}
  finally{openInstance.disabled=false;}
});
async function checkLegacyInstance(id){
  openInstance.hidden=true;
  if(!id)return;
  try{
    const result=await invoke('tools_action',{action:'legacy_instance',payload:{profileId:id}});
    if(editing===id&&dialog.open)openInstance.hidden=!result?.exists;
  }catch{}
}
openFolder.addEventListener('click',async()=>{
  if(!invoke||!editing)return;
  openFolder.disabled=true;
  try{await invoke('tools_action',{action:'open_folder',payload:{kind:'game',profileId:editing}});}
  catch(error){$('#profile-error').textContent=String(error);}
  finally{openFolder.disabled=false;}
});
async function reload(){const data=await invoke('bootstrap');state=data;renderLibrary();renderMods();window.dispatchEvent(new Event('bat-library-ready'));}
async function newLoadout(){
  const system=await invoke('tools_action',{action:'system'}).catch(()=>null);
  const suggested=system?.suggestedRam;
  const ram=Number.isInteger(suggested)&&suggested>=1&&suggested<=64?suggested:4;
  return {name:'My loadout',version:'1.21.1',engine:'fabric',ram,ramOverride:false,favorite:false,extraJvmArgs:''};
}
window.editLoadout=async(id=null)=>{if(!invoke)return notify('Open the native Rust app to manage loadouts.');$('#profile-error').textContent='';try{const value=id?await invoke('profile_details',{profileId:id}):await newLoadout();editing=id;openFolder.hidden=!id;versionSearch.value='';$('#profile-editor-title').textContent=id?'Edit loadout':'New loadout';$('#profile-name').value=value.name;$('#profile-game-version').value=value.version;$('#profile-engine').value=value.engine;updateVersionTrigger(value.version);$('#profile-ram').value=value.ram;$('#profile-ram-override').checked=value.ramOverride;$('#profile-favorite').checked=value.favorite;$('#profile-jvm').value=value.extraJvmArgs;dialog.showModal();void refreshCompanionStatus();void checkLegacyInstance(id);$('#profile-name').focus();loadVersions();}catch(error){notify(String(error));}};
window.runProfileAction=async(profileId,action)=>{if(!invoke)return;try{await invoke('profile_action',{profileId,action});await reload();notify(action==='delete'?'Loadout removed. Your worlds and files are kept.':'Loadout updated.');}catch(error){notify(String(error));}};
$('#create-loadout').addEventListener('click',()=>window.editLoadout());$('#profile-cancel').addEventListener('click',()=>dialog.close());$('#profile-form').addEventListener('submit',async event=>{event.preventDefault();const button=$('#profile-save');button.disabled=true;$('#profile-error').textContent='';try{const version=$('#profile-game-version').value,engine=$('#profile-engine').value;await invoke('save_profile',{edit:{id:editing,name:$('#profile-name').value,version,engine,ram:Number($('#profile-ram').value),ramOverride:$('#profile-ram-override').checked,favorite:$('#profile-favorite').checked,extraJvmArgs:$('#profile-jvm').value}});const modCheck=await invoke('prepare_companion_mod',{version,engine}).catch(()=>null);await reload();dialog.close();notify(modCheck?.supported&&modCheck?.available?`Loadout saved · Bat Client mod ready for ${engine} ${version}.`:'Loadout saved.');}catch(error){$('#profile-error').textContent=String(error);}finally{button.disabled=false;}});
})();
