"use strict";
(() => {
  const make=(tag,text,cls)=>{const el=document.createElement(tag);if(text!==undefined)el.textContent=text;if(cls)el.className=cls;return el;};
  async function call(action,payload={}){if(!invoke)throw Error('Open the Rust desktop app to use launcher tools.');return invoke('tools_action',{action,payload});}
  async function reload(){state=await invoke('bootstrap');renderLibrary();renderMods();window.dispatchEvent(new Event('bat-library-ready'));}
  window.addEventListener('bat-page-changed',event=>{
    if(invoke&&state.native)void call('discord_page',{page:event.detail}).catch(()=>{});
  });
  function button(text,action,cls='mods-secondary'){const b=make('button',text,cls);b.type='button';b.addEventListener('click',async()=>{b.disabled=true;try{await action();}catch(error){notify(String(error));}finally{b.disabled=false;}});return b;}
  const toolbar=make('div',undefined,'loadout-transfer');toolbar.append(button('Import loadouts',async()=>{const data=await call('import_loadouts');if(data?.imported){await reload();notify(data.imported+' loadouts imported.');}}),button('Export loadouts',async()=>{const data=await call('export_loadouts');if(data?.exported)notify(data.exported+' loadouts exported.');}));$('#page-loadouts .page-intro').after(toolbar);
  async function openPresets(kind) {
    const launch=kind==='launch';
    const title=launch?'Launch presets':'Mod presets';
    const request=(operation,payload={})=>call('presets',{kind,operation,...payload});
    let presets=await request('list');
    const dialog=make('dialog',undefined,'launcher-welcome preset-dialog');
    dialog.setAttribute('aria-label',title);
    const heading=make('h2',title);
    const help=make('p',launch
      ? 'Save a shortcut to the selected loadout.'
      : 'Save the enabled mods. Applying a preset disables other mods in the shared folder.');
    const name=make('input');
    name.maxLength=80;
    name.placeholder='Preset name';
    name.setAttribute('aria-label','Preset name');
    const list=make('div',undefined,'preset-list');
    let busy=false;
    const operation=work=>async()=>{
      if(busy)return;
      busy=true;
      try{await work();}finally{busy=false;}
    };
    async function refresh(){
      presets=await request('list');
      render();
    }
    function save(id, existingName='') {
      return operation(async()=>{
        const title=name.value.trim()||existingName;
        if(!title)throw Error('Enter a preset name.');
        if(launch&&!selected)throw Error('Select a loadout first.');
        await request('save',{id,name:title,profileId:selected});
        name.value='';
        await refresh();
      });
    }
    function render(){
      list.replaceChildren();
      if(!presets.length)list.append(make('p','No presets saved.'));
      for(const preset of presets){
        const row=make('div',undefined,'preset-row');
        const label=make('strong',preset.name);
        row.append(label,button('Apply',operation(async()=>{
          await request('apply',{id:preset.id});
          await reload();
          notify('Applied '+preset.name);
        })),button('Update',save(preset.id,preset.name)),button('Remove',operation(async()=>{
          await request('delete',{id:preset.id});
          await refresh();
        }),'mod-remove'));
        list.append(row);
      }
    }
    const form=make('div',undefined,'preset-row');
    form.append(name,button('Save new',save(null),'mods-primary'));
    const close=button('Done',()=>dialog.close());
    dialog.append(heading,help,form,list,close);
    dialog.addEventListener('close',()=>dialog.remove(),{once:true});
    render();
    document.body.append(dialog);
    dialog.showModal();
  }
  toolbar.append(button('Launch presets',()=>openPresets('launch')));
  $('#mods-refresh').after(button('Mod presets',()=>openPresets('mods')));
  for(const [id,action]of [['setting-game-dir','pick_folder'],['setting-java-path','pick_java']]){const field=$('#'+id);field.after(button('Browse…',async()=>{const value=await call(action);if(value)field.value=value;}));}
  const gameFolder=make('div',undefined,'java-inventory');
  const effectiveFolder=make('p');
  effectiveFolder.setAttribute('role','status');
  gameFolder.append(button('Show active game folder',async()=>{
    effectiveFolder.textContent=await call('game_root');
  }),button('Open active game folder',()=>call('open_folder',{kind:'game'})),effectiveFolder);
  $('#setting-game-dir').closest('label').after(gameFolder);
  $('#java-check').after(button('Install required Java',async()=>{const java=await invoke('detect_java',{profileId:selected});$('#java-status').textContent='Downloading and verifying Java '+java.requiredMajor+'…';const path=await invoke('download_java',{major:java.requiredMajor});$('#java-status').textContent='Java '+java.requiredMajor+' installed: '+path;}));
  const javaInventory=make('div',undefined,'java-inventory');
  javaInventory.setAttribute('aria-label','Installed Java versions');
  const javaRows=make('div',undefined,'java-inventory-rows');
  const javaMessage=make('p');
  javaMessage.setAttribute('role','status');
  async function checkRuntimes(){
    javaMessage.textContent='Checking installed runtimes…';
    try {
      const statuses=await call('java_inventory');
      javaRows.replaceChildren();
      for(const status of statuses){
        const major=status.requiredMajor;
        const row=make('div',undefined,'java-inventory-row');
        const label=make('strong','Java '+major);
        const summary=make('span',status.found?(status.version||'Available'):'Not found');
        if(status.executable)summary.title=status.executable;
        row.append(label,summary);
        if(!status.found)row.append(button('Install',async()=>{
          const path=await invoke('download_java',{major});
          notify('Java '+major+' installed.');
          summary.textContent='Available';
          summary.title=path;
          row.querySelector('button').remove();
        }));
        javaRows.append(row);
      }
      javaMessage.textContent='';
    }catch(error){javaMessage.textContent=String(error);}
  }
  const systemInfo=make('p');
  systemInfo.setAttribute('role','status');
  javaInventory.append(button('Check all Java versions',checkRuntimes),button('Show system information',async()=>{
    const system=await call('system');
    systemInfo.textContent=[system.os+' '+system.arch,system.threads+' CPU threads',
      system.freeGB+' GB available of '+system.memoryGB+' GB',
      'Suggested Minecraft memory '+system.suggestedRam+' GB'].join(' · ');
  }),systemInfo,javaMessage,javaRows);
  $('.java-status-panel').after(javaInventory);
  function ordering(){for(const [selector,kind,values,key]of [['.loadout-card','profiles',state.profiles,'id'],['.saved-account-card','accounts',state.accounts,'username']])document.querySelectorAll(selector).forEach((card,index)=>{card.draggable=!!invoke;card.tabIndex=0;card.title='Drag to reorder, or use Alt + Arrow Up / Down';card.addEventListener('dragstart',event=>event.dataTransfer.setData('application/x-bat-order',JSON.stringify({kind,index})));card.addEventListener('dragover',event=>event.preventDefault());async function move(from,to){const ids=values.map(v=>v[key]);if(from<0||to<0||from>=ids.length||to>=ids.length)return;ids.splice(to,0,ids.splice(from,1)[0]);try{await call('reorder',{kind,ids});await reload();}catch(error){notify(String(error));}}card.addEventListener('drop',event=>{event.preventDefault();try{const drag=JSON.parse(event.dataTransfer.getData('application/x-bat-order'));if(drag.kind===kind)move(drag.index,index);}catch{}});card.addEventListener('keydown',event=>{if(event.altKey&&['ArrowUp','ArrowDown'].includes(event.key)){event.preventDefault();move(index,index+(event.key==='ArrowUp'?-1:1));}});});}
  window.addEventListener('bat-library-ready',ordering);
  let audio;
  document.addEventListener('pointerdown',()=>{if(!audio&&window.AudioContext)audio=new AudioContext();if(audio?.state==='suspended')audio.resume().catch(()=>{});},{once:true});
  window.BatLaunchSound=()=>{if(!audio||audio.state!=='running')return;const now=audio.currentTime;for(const [frequency,delay]of [[440,0],[660,.1]]){const oscillator=audio.createOscillator(),gain=audio.createGain();oscillator.frequency.value=frequency;gain.gain.setValueAtTime(0,now+delay);gain.gain.linearRampToValueAtTime(.035,now+delay+.015);gain.gain.exponentialRampToValueAtTime(.0001,now+delay+.16);oscillator.connect(gain).connect(audio.destination);oscillator.start(now+delay);oscillator.stop(now+delay+.18);}};
  let onboardingChecked=false;
  window.addEventListener('bat-library-ready',async()=>{if(!invoke||onboardingChecked)return;onboardingChecked=true;try{const data=await call('onboarding');if(!data.firstLaunch)return;const dialog=make('dialog',undefined,'launcher-welcome'),title=make('h2','Welcome to Bat Client'),copy=make('p','Your accounts and loadouts stay in your existing library. Choose an account, create or select a loadout, then launch Minecraft. Missing game files and Java download automatically.');dialog.append(title,copy);dialog.append(button('Choose an account',async()=>{await call('onboarding',{complete:true});dialog.close();dialog.remove();showPage('accounts');},'mods-primary'),button('Start exploring',async()=>{await call('onboarding',{complete:true});dialog.close();dialog.remove();},'mods-secondary'));dialog.setAttribute('aria-label','Welcome to Bat Client');document.body.append(dialog);dialog.showModal();}catch{onboardingChecked=false;}});
  async function openConsole(){
    let dialog=document.getElementById('game-console');
    if(!dialog){dialog=make('dialog',undefined,'game-console');dialog.id='game-console';dialog.setAttribute('aria-label','Minecraft console');const heading=make('h2','Minecraft console'),output=make('pre','','game-console-output');const refresh=button('Refresh',async()=>{const data=await invoke('launch_diagnostics');output.textContent=data.excerpt||'Waiting for Minecraft output…';});const close=button('Close',()=>dialog.close());dialog.append(heading,output,refresh,close);document.body.append(dialog);}
    if(!dialog.open)dialog.showModal();try{const data=await invoke('launch_diagnostics');dialog.querySelector('pre').textContent=data.excerpt||'Waiting for Minecraft output…';}catch(error){dialog.querySelector('pre').textContent=String(error);}
  }
  window.BatOpenConsole=openConsole;
  if(window.__TAURI__?.event?.listen)window.__TAURI__.event.listen('bat-game-event',async event=>{if(event.payload.kind==='started'){try{const settings=await invoke('launcher_settings');if(settings.showConsole)openConsole();if(settings.launchSound)window.BatLaunchSound?.();}catch{}}});
})();
