"use strict";
(() => {
  const panel=document.createElement('section');panel.className='launcher-update-panel';
  const title=document.createElement('h3');title.textContent='Launcher updates';
  const message=document.createElement('p');message.setAttribute('role','status');message.textContent='Signed Rust updates from batclient.xyz/update.json.';
  const check=document.createElement('button');check.type='button';check.className='mods-secondary';check.textContent='Check for updates';
  const install=document.createElement('button');install.type='button';install.className='mods-primary';install.textContent='Install update & restart';install.hidden=true;
  const bar=document.createElement('progress');bar.max=1;bar.hidden=true;bar.setAttribute('aria-label','Launcher update progress');
  const lastResult=document.createElement('p');lastResult.hidden=true;lastResult.setAttribute('role','status');
  panel.append(title,message,lastResult,bar,check,install);$('#settings-pane-2').append(panel);
  let busy=false,timer,automatic=false,startup=false;
  function show(value){message.textContent=value.message;busy=['checking','downloading','verifying','installing'].includes(value.stage);check.disabled=!invoke||busy;install.disabled=busy;install.hidden=value.stage!=='available';bar.hidden=!['downloading','verifying','installing'].includes(value.stage);if(Number.isFinite(value.progress))bar.value=value.progress;else bar.removeAttribute('value');if(startup)window.BatStartup?.update(value);}
  async function poll(){clearTimeout(timer);try{show(await invoke('launcher_update_status'));}catch{}if(busy)timer=setTimeout(poll,1000);}
  async function checkNow(){if(!invoke||busy)return;show({stage:'checking',message:'Checking for updates…'});try{const value=await invoke('check_launcher_update');show(value);return value;}catch(error){show({stage:'failed',message:String(error)});throw error;}}
  check.disabled=!invoke;check.addEventListener('click',()=>checkNow().catch(()=>{}));
  async function installNow(){show({stage:'downloading',message:'Downloading update…'});timer=setTimeout(poll,250);try{await invoke('install_launcher_update');await poll();}catch(error){show({stage:'failed',message:String(error)});throw error;}}
  install.addEventListener('click',()=>{if(invoke&&!busy)installNow().catch(()=>{});});
  async function auto(){
    if(automatic||!invoke||!state.native)return;automatic=true;startup=true;
    let delay=0;
    try{
      const result=await invoke('launcher_update_result').catch(()=>null);
      if(result){lastResult.textContent=result.message;lastResult.hidden=false;}
      const settings=await invoke('launcher_settings');
      if(settings.autoCheckUpdates===false)return;
      const update=await checkNow();
      const needsManualRetry=result?.version===update?.version&&['failed','incomplete'].includes(result?.stage);
      if(update?.stage==='available'&&!needsManualRetry){
        await installNow();
        // Native installation normally exits/restarts this process. A return is safe to surface.
        const final=await invoke('launcher_update_status');
        if(final.stage==='installing'){show(final);return;}
      }
    }catch(error){show({stage:'failed',message:'Update could not finish. Opening your launcher…'});notify('Automatic update: '+String(error));delay=900;}
    finally{
      // Keep the splash visible while the native installer is replacing/restarting the app.
      let installing=false;try{installing=(await invoke('launcher_update_status')).stage==='installing';}catch{}
      if(!installing){window.BatStartup?.complete(delay);startup=false;}
    }
  }
  window.addEventListener('bat-library-ready',auto);window.addEventListener('beforeunload',()=>clearTimeout(timer));auto();
})();
