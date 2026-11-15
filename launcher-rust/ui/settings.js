"use strict";
(() => {
const form=$('#launcher-settings-form'),save=$('#settings-save'),error=$('#settings-error');
const fields={defaultRam:'setting-ram',downloadThreads:'setting-downloads',gameDir:'setting-game-dir',javaPath:'setting-java-path',extraJvmArgs:'setting-jvm',windowWidth:'setting-width',windowHeight:'setting-height',fullscreen:'setting-fullscreen'};
const groups=[
['Performance',[
 ['jvmPreset','Java preset','balanced',[['balanced','Balanced'],['low','Low memory'],['high','High memory'],['custom','Custom']]],['autoOptimize','Automatic game optimization',true],['cullLeaves','Leaf culling',true],['fastMath','Fast math',true]]],
['Appearance', [['gameWindowTitle','Game window title','']]],
['Launcher', [['language','Language','en',[['en','English'],['es','Español'],['fr','Français'],['de','Deutsch'],['pt','Português'],['ru','Русский'],['tr','Türkçe'],['ar','العربية']]],['hideOnLaunch','Hide when Minecraft starts',false],['keepLauncherOpen','Keep launcher open',false],['autoCloseOnGameExit','Close after Minecraft exits',false],['showConsole','Show game console',false],['closeToTray','Close to system tray',false],['startWithWindows','Start with Windows',false]]],
['Files & Network', []],
['Sound & Social', [['discordRPC','Discord activity',true],['launchSound','Launch sound',false],['notifications','Notifications',true],['crashReports','Save local launch error reports',false],['autoCheckUpdates','Automatic updates on startup',true]]],
['Advanced', [['publishSkinsToBatClient','Share equipped skins through Bat Client',true]]]];
const defaults={},extra=document.createElement('div');extra.className='settings-groups';const sections=[];
const notice=document.createElement('p');notice.className='settings-migration-note';notice.textContent='These preferences are shared with your existing Electron launcher. Older loader formats and some platform integrations still need validation.';extra.append(notice);
for(const [title,entries] of groups){const section=document.createElement('fieldset'),legend=document.createElement('legend');legend.textContent=title;section.append(legend);for(const [key,text,fallback,options] of entries){const row=document.createElement('label'),label=document.createElement('span'),input=document.createElement(options?'select':'input');label.textContent=text;input.id='setting-'+key;defaults[key]=fallback;fields[key]=input.id;if(options){for(const [value,text]of options){const option=document.createElement('option');option.value=value;option.textContent=text;input.append(option);}input.value=fallback;}else if(typeof fallback==='boolean'){input.type='checkbox';input.checked=fallback;input.setAttribute('role','switch');row.className='settings-toggle-row';}else{input.type='text';input.maxLength=256;input.placeholder='Minecraft default';}row.append(label,input);section.append(row);}sections.push(section);extra.append(section);}
const tabs=document.createElement('nav');tabs.className='settings-tabs';tabs.setAttribute('role','tablist');tabs.setAttribute('aria-label','Settings categories');tabs.setAttribute('aria-orientation','horizontal');
const panes=document.createElement('div');panes.className='settings-pages';
function chooseTab(index,focus=false){sections.forEach((pane,i)=>{pane.hidden=i!==index;const button=tabs.children[i];button.setAttribute('aria-selected',String(i===index));button.tabIndex=i===index?0:-1;});if(focus)tabs.children[index].focus();}
sections.forEach((section,i)=>{section.id='settings-pane-'+i;section.setAttribute('role','tabpanel');section.setAttribute('aria-labelledby','settings-tab-'+i);const tab=document.createElement('button');tab.type='button';tab.id='settings-tab-'+i;tab.setAttribute('role','tab');tab.setAttribute('aria-controls',section.id);const svg=icon(['settings','home','layers','box','user','spark'][i]);tab.append(svg,document.createTextNode(groups[i][0]));tab.addEventListener('click',()=>chooseTab(i));tab.addEventListener('keydown',event=>{if(['ArrowDown','ArrowUp','ArrowLeft','ArrowRight','Home','End'].includes(event.key)){event.preventDefault();chooseTab(event.key==='Home'?0:event.key==='End'?sections.length-1:(i+(['ArrowDown','ArrowRight'].includes(event.key)?1:-1)+sections.length)%sections.length,true);}});tabs.append(tab);panes.append(section);});
for(const [i,keys]of [[0,['defaultRam','javaPath','extraJvmArgs']],[1,['windowWidth','windowHeight','fullscreen']],[3,['gameDir','downloadThreads']]])for(const key of [...keys].reverse()){const row=document.getElementById(fields[key]).closest('label');sections[i].insertBefore(row,sections[i].children[1]||null);}
if(navigator.userAgent.includes('Windows')){
  const clear=document.createElement('button');
  clear.type='button';clear.className='mods-secondary';clear.textContent='Clear launcher web cache';clear.disabled=!invoke;
  clear.addEventListener('click',async()=>{
    clear.disabled=true;
    try{await invoke('clear_launcher_cache');notify('Launcher web cache cleared.');}
    catch(reason){notify(String(reason));}
    finally{clear.disabled=false;}
  });
  sections[3].append(clear);
}
const appearance=$('#page-settings .settings-panel'),java=$('#page-settings .java-status-panel');if(appearance)sections[1].append(appearance);if(java)sections[0].append(java);
form.querySelector('h2')?.remove();form.querySelectorAll('.editor-pair').forEach(pair=>{if(!pair.children.length)pair.remove();});$('#page-settings .settings-note')?.remove();
const layout=document.createElement('div');layout.className='settings-layout';layout.append(tabs,panes);extra.append(layout);form.insertBefore(extra,error);chooseTab(0);form.noValidate=true;
save.textContent='Save settings';let loaded=false,loading=false,busy=false;save.disabled=!!invoke;
async function load(){if(!invoke||loaded||loading)return;loading=true;error.textContent='';try{const settings=await invoke('launcher_settings');for(const [key,id]of Object.entries(fields)){const input=document.getElementById(id),value=settings[key]??defaults[key];if(input.type==='checkbox')input.checked=value===true;else if(value!==undefined)input.value=value;}window.BatLanguage?.set(settings.language);loaded=true;save.disabled=false;}catch(reason){error.textContent=String(reason)+' Reopen Settings to try again.';}finally{loading=false;}}
window.addEventListener('bat-page-changed',event=>{if(event.detail==='settings')load();});
form.addEventListener('submit',async event=>{event.preventDefault();if(!invoke)return notify('Open the native Rust app to save settings.');if(!loaded||busy)return;if(!form.checkValidity()){const invalid=form.querySelector(':invalid');const i=sections.indexOf(invalid?.closest('[role=tabpanel]'));if(i>=0)chooseTab(i);invalid?.reportValidity();return;}busy=true;save.disabled=true;error.textContent='';const settings={};for(const [key,id]of Object.entries(fields)){const input=document.getElementById(id);settings[key]=input.type==='checkbox'?input.checked:input.type==='number'?Number(input.value):input.value;}try{await invoke('save_launcher_settings',{settings});window.BatLanguage?.set(settings.language);notify('Settings saved.');try{state=await invoke('bootstrap');renderLibrary();renderMods();window.dispatchEvent(new Event('bat-library-ready'));}catch{error.textContent='Settings saved. Reopen the launcher to refresh the library.';}}catch(reason){error.textContent=String(reason);}finally{busy=false;save.disabled=false;}});
})();
