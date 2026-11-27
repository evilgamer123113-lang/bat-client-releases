// Native-backed keybinds and local profiles. User names are always textContent.
function clientToolIcon(type) {
    const icon=document.createElement('span'); icon.className='bat-tool-icon'; icon.setAttribute('aria-hidden','true');
    const paths=type==='keybinds'
        ? '<rect x="2" y="5" width="20" height="14" rx="3"/><path d="M6 9h.01M10 9h.01M14 9h.01M18 9h.01M6 12h.01M10 12h.01M14 12h.01M18 12h.01M7 16h10"/>'
        : '<rect x="3" y="7" width="15" height="14" rx="3"/><path d="M7 3h11a3 3 0 0 1 3 3v11M7 12h7M7 16h5"/>';
    icon.innerHTML='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">'+paths+'</svg>';
    return icon;
}
function clientToolButton(label, action) {
    const button = document.createElement('button'); button.type = 'button';
    button.className = 'bat-tool-button'; button.textContent = label;
    button.addEventListener('click',action); return button;
}
function toolMessage(view, text) {
    const message = document.getElementById(view + '-message');
    if (message) message.textContent = text || '';
}
function readableKey(key) {
    if (!key || key === 'key.keyboard.unknown') return 'Unbound';
    return key.replace(/^key\.(keyboard|mouse)\./,key.startsWith('key.mouse.') ? 'Mouse ' : '').replace(/\./g,' ').toUpperCase();
}
function renderKeybindManager(message) {
    const view = document.getElementById('view-keybinds'); if (!view) return;
    view.textContent = '';
    const title = document.createElement('h2'); title.textContent = 'Keybind manager'; title.prepend(clientToolIcon('keybinds')); view.appendChild(title);
    const intro = document.createElement('p'); intro.textContent = 'Click a key to bind a keyboard or mouse button. Esc cancels capture. Other modules toggle; Zoom is held and Perspective cycles the camera.'; view.appendChild(intro);
    const status = document.createElement('p'); status.id = 'keybinds-message'; status.setAttribute('role','status'); status.textContent = message || ''; view.appendChild(status);
    let data;
    try { data = JSON.parse(window.batGetKeybinds()); }
    catch (_) { status.textContent = 'Keybinds are available when running Bat Client in Minecraft.'; return; }
    const search = document.createElement('input'); search.placeholder = 'Search actions or keys'; search.setAttribute('aria-label','Search keybinds'); search.className = 'bat-tool-input'; view.appendChild(search);
    const actions = [{id:'menu',name:'Bat Client menu'},{id:'waypoint_add',name:'Create waypoint'},{id:'waypoint_manager',name:'Waypoint manager'},{id:'trail_retrace',name:'Retrace / resume trail'},...['zoom','perspective','freelook'].map(id=>({id,name:modDef(id).name})),...MODULES.filter(m=>m.id!=='zoom'&&m.id!=='perspective'&&m.id!=='freelook').map(m=>({id:m.id,name:'Toggle ' + m.name}))];
    actions.forEach(action=>{
        const key = data.bindings[action.id] || 'key.keyboard.unknown';
        const row = document.createElement('div'); row.className = 'bat-binding-row'; row.dataset.action = action.id;
        const label = document.createElement('div'); label.textContent = action.name;
        const conflicts = key === 'key.keyboard.unknown' ? [] : actions.filter(other=>other.id!==action.id && data.bindings[other.id]===key).map(other=>other.name).concat((data.controls||[]).filter(other=>other.key===key).map(other=>other.name));
        if (conflicts.length) { const hint = document.createElement('small'); hint.textContent = 'Conflict: ' + conflicts.slice(0,4).join(', ') + (conflicts.length>4 ? '…' : ''); label.appendChild(hint); }
        row.appendChild(label);
        const bind = clientToolButton(readableKey(key),()=>{
            bind.textContent = 'Press a key…';
            window.__batManagerKeyCaptured = (id,captured)=>{
                window.__batManagerKeyCaptured = null;
                if (captured) saveManagedBinding(id,captured); else renderKeybindManager('Binding cancelled.');
            };
            callBat('keybind-capture:' + action.id);
        });
        bind.setAttribute('aria-label','Bind ' + action.name); row.appendChild(bind);
        row.appendChild(clientToolButton('Clear',()=>saveManagedBinding(action.id,'key.keyboard.unknown')));
        row.appendChild(clientToolButton('Reset',()=>saveManagedBinding(action.id,'')));
        view.appendChild(row);
    });
    search.addEventListener('input',()=>{
        const query = search.value.toLowerCase();
        view.querySelectorAll('.bat-binding-row').forEach(row=>row.style.display = row.textContent.toLowerCase().includes(query) ? '' : 'none');
    });
}
function saveManagedBinding(action, key) {
    try {
        // Materialize defaults without rewriting native anchor coordinates for a key-only edit.
        if (modDef(action)) cfgSave(action,{en:modState(action).en});
        const raw = window.batSetKeybind(action,key);
        if (!raw) throw Error('The keybind could not be saved.');
        CFG = JSON.parse(raw); localStorage.setItem('bat.hudcfg',raw);
        renderKeybindManager('Saved. Profiles keep these Bat keybinds with your layout.');
    } catch (error) { toolMessage('keybinds',error.message || 'The keybind could not be saved.'); }
}
function renderClientProfiles(message) {
    const view = document.getElementById('view-profiles'); if (!view) return;
    view.textContent = '';
    const title = document.createElement('h2'); title.textContent = 'Profiles'; title.prepend(clientToolIcon('profiles')); view.appendChild(title);
    const description = document.createElement('p'); description.textContent = 'Your setup, ready for every session. Layouts, module settings and Bat keybinds save automatically to the active profile.'; view.appendChild(description);
    const status = document.createElement('p'); status.id = 'profiles-message'; status.setAttribute('role','status'); status.textContent = message || ''; view.appendChild(status);
    try {
        const result = JSON.parse(window.batProfiles('list',''));
        if (result.error) throw Error(result.error);
        CFG = JSON.parse(result.config);
    } catch (error) { status.textContent = 'Profiles are available when running Bat Client in Minecraft.'; return; }
    const profiles=CFG.profiles||{}, names=Object.keys(profiles);
    const summary=document.createElement('div'); summary.className='bat-profile-summary';
    const active=document.createElement('strong');active.textContent='Active: '+CFG.activeProfile;summary.appendChild(active);
    const count=document.createElement('span');count.textContent=names.length+' / 16 profiles · Saved on this device';summary.appendChild(count);view.appendChild(summary);
    const layout=document.createElement('div');layout.className='bat-profile-layout';view.appendChild(layout);
    const grid=document.createElement('div');grid.className='bat-profile-grid';grid.setAttribute('role','group');grid.setAttribute('aria-label','Saved profiles');layout.appendChild(grid);
    const details=document.createElement('aside');details.className='bat-profile-details';layout.appendChild(details);
    const selectedTitle=document.createElement('h3');details.appendChild(selectedTitle);
    const selectedInfo=document.createElement('p');details.appendChild(selectedInfo);
    let selected=CFG.activeProfile, deleteArmed=false;
    const perform = (action,target)=>{
        try {
            const result=JSON.parse(window.batProfiles(action,target));
            if(result.error)throw Error(result.error);
            CFG=JSON.parse(result.config); localStorage.setItem('bat.hudcfg',result.config);
            const messages={load:'Now using ',create:'Created ',rename:'Renamed active profile to ',delete:'Deleted '};
            renderModGrid(); renderHudWidgets(); hudBeginHistory(); renderClientProfiles((messages[action]||'Saved ')+target+'.');
        } catch(error) { toolMessage('profiles',error.message || 'The profile could not be saved.'); }
    };
    const use=clientToolButton('Use profile',()=>perform('load',selected));use.dataset.profileAction='load';details.appendChild(use);
    const remove=clientToolButton('Delete selected',()=>{
        if(!deleteArmed){deleteArmed=true;remove.textContent='Confirm delete';toolMessage('profiles','Delete '+selected+'? Click Confirm delete to remove it.');return;}
        perform('delete',selected);
    });remove.dataset.profileAction='delete';details.appendChild(remove);
    const form=document.createElement('div');form.className='bat-profile-form';details.appendChild(form);
    const label=document.createElement('label');label.textContent='Create a copy or rename active';label.htmlFor='bat-profile-name';form.appendChild(label);
    const name=document.createElement('input');name.id='bat-profile-name';name.className='bat-tool-input';name.placeholder='e.g. Survival or PvP';name.maxLength=48;name.setAttribute('aria-label','Profile name');form.appendChild(name);
    const create=clientToolButton('Create from current',()=>perform('create',name.value));create.dataset.profileAction='create';form.appendChild(create);
    const rename=clientToolButton('Rename active',()=>perform('rename',name.value));rename.dataset.profileAction='rename';form.appendChild(rename);
    const hint=document.createElement('p');hint.className='bat-profile-hint';hint.textContent='A new profile copies your current setup. Rename always applies to the active profile.';form.appendChild(hint);
    const validate=()=>{create.disabled=!name.value.trim()||names.length>=16;rename.disabled=!name.value.trim();};name.addEventListener('input',validate);validate();
    name.addEventListener('keydown',event=>{if(event.key==='Enter'&&!create.disabled){event.preventDefault();create.click();}});
    const stats=profile=>{
        const saved=profile.modules||{},modules=Object.values(saved),enabled=MODULES.filter(mod=>saved[mod.id]?saved[mod.id].en:mod.def).length;
        const bindings=Object.values(profile.bindings||{}).filter(key=>key&&key!=='key.keyboard.unknown').length+['zoom','perspective'].filter(id=>saved[id]&&saved[id].set&&saved[id].set.key&&saved[id].set.key!=='Minecraft Controls'&&saved[id].set.key!=='key.keyboard.unknown').length;
        return enabled+' enabled · '+modules.length+' configured · '+bindings+' custom keys';
    };
    const select=profileName=>{
        selected=profileName;deleteArmed=false;remove.textContent='Delete selected';
        grid.querySelectorAll('.bat-profile-card').forEach(card=>card.setAttribute('aria-pressed',String(card.dataset.profileName===selected)));
        selectedTitle.textContent=selected;selectedInfo.textContent=stats(profiles[selected]||{});
        use.disabled=selected===CFG.activeProfile;use.textContent=use.disabled?'Currently active':'Use profile';remove.disabled=use.disabled;
        toolMessage('profiles',message||'');
    };
    names.forEach(profileName=>{
        const card=clientToolButton('',()=>select(profileName));card.classList.add('bat-profile-card');card.dataset.profileName=profileName;
        const top=document.createElement('div');top.className='bat-profile-card-top';top.appendChild(clientToolIcon('profiles'));
        const badge=document.createElement('span');badge.className='bat-profile-badge';badge.textContent=profileName===CFG.activeProfile?'ACTIVE':'SAVED';top.appendChild(badge);card.appendChild(top);
        const label=document.createElement('strong');label.textContent=profileName;card.appendChild(label);
        const meta=document.createElement('small');meta.textContent=stats(profiles[profileName]);card.appendChild(meta);grid.appendChild(card);
    });
    if(typeof window.batServerProfiles==='function'){
        const server=document.createElement('div');server.className='bat-profile-form';server.id='server-profile-settings';details.appendChild(server);
        try{const info=JSON.parse(window.batServerProfiles('list',''));const caption=document.createElement('p');caption.textContent=info.key?'This connection: '+info.key:'Join a world or server to assign a profile.';server.appendChild(caption);
            const assignment=document.createElement('p');assignment.textContent=info.key?'Assigned: '+((CFG.serverProfiles||{})[info.key]||'None')+(info.override?' · Manual override':''):'';server.appendChild(assignment);
            const invoke=action=>{const answer=JSON.parse(window.batServerProfiles(action,selected));if(answer.error){toolMessage('profiles',answer.error);return;}CFG=JSON.parse(answer.config);renderHudWidgets();renderModGrid();hudBeginHistory();renderClientProfiles('Server profile preference saved.');};
            const bind=clientToolButton('Assign selected to this server',()=>invoke('bind'));bind.disabled=!info.key;server.appendChild(bind);
            const clear=clientToolButton('Remove server assignment',()=>invoke('clear'));clear.disabled=!info.key||!(CFG.serverProfiles||{})[info.key];server.appendChild(clear);
            const manual=clientToolButton(info.override?'Resume automatic selection':'Keep manual choice this session',()=>invoke(info.override?'resume':'override'));manual.disabled=!info.key;server.appendChild(manual);
            const note=document.createElement('p');note.className='bat-profile-hint';note.textContent='Assignments apply on joining. A manual profile change stays active until you reconnect or resume automatic selection.';server.appendChild(note);
        }catch(error){server.textContent='Server profiles are unavailable: '+error.message;}
    }
    select(selected);
}
(function initClientTools(){
    const tabs=document.querySelector('.mm-tabs'), source=document.getElementById('view-mods');
    if(!tabs||!source)return;
    const style=document.createElement('style');
    style.textContent='.bat-tools-view{overflow:auto;height:100%;box-sizing:border-box;padding:24px!important;color:#fff;background:#000}.bat-tools-view h2{font-size:22px;margin:0 0 12px}.bat-tools-view p{font-size:14px;line-height:1.6;color:#bcbcbc;margin:0 0 16px}.bat-tool-input{display:block;width:100%;box-sizing:border-box;padding:12px;margin:12px 0;background:#111;color:#fff;border:1px solid #444;border-radius:8px;font:inherit}.bat-tool-button{padding:10px 14px;background:#111;color:#fff;border:1px solid #555;border-radius:8px;font:inherit;cursor:pointer;transition:background .16s,color .16s}.bat-tool-button:hover,.bat-tool-button:focus-visible{background:#fff;color:#000}.bat-tool-button:disabled{opacity:.4;cursor:default}.bat-binding-row{display:grid;grid-template-columns:minmax(120px,1fr) minmax(100px,180px) auto auto;gap:10px;align-items:center;padding:14px 0;border-bottom:1px solid #282828}.bat-binding-row small{display:block;font-size:12px;color:#aaa;margin-top:6px}.bat-profile-actions{display:flex;gap:10px;flex-wrap:wrap}.bat-tools-tab{min-width:0!important;padding:0 16px!important}.bat-tools-view [role=status]{min-height:18px;color:#fff}@media(max-width:700px){.bat-binding-row{grid-template-columns:1fr auto}.bat-tools-tab{font-size:11px!important;padding:0 10px!important}}';
    document.head.appendChild(style);
    const profileStyle=document.createElement('style');
    profileStyle.textContent='.bat-tool-icon{display:inline-flex;align-items:center;justify-content:center;flex-shrink:0;width:20px;height:20px}.bat-tool-icon svg{width:100%;height:100%;display:block}.bat-tools-tab{display:inline-flex;align-items:center;justify-content:center;gap:9px}.bat-tools-view h2{display:flex;align-items:center;gap:12px}.bat-tools-view h2 .bat-tool-icon{width:26px;height:26px}.bat-profile-summary{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:16px 18px;border:1px solid #333;border-radius:12px;margin-bottom:20px}.bat-profile-summary span{font-size:12px;color:#aaa}.bat-profile-summary strong{overflow-wrap:anywhere}.bat-profile-layout{display:grid;grid-template-columns:minmax(0,1fr) minmax(220px,270px);gap:20px;align-items:start}.bat-profile-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.bat-profile-card{text-align:left;padding:18px;display:flex;flex-direction:column;gap:16px;min-width:0;border-color:#333;transition:background .18s,border-color .18s}.bat-profile-card[aria-pressed=true]{background:#191919;border-color:#fff}.bat-profile-card:hover{background:#1b1b1b;color:#fff;border-color:#aaa}.bat-profile-card strong{font-size:16px;overflow-wrap:anywhere}.bat-profile-card small{font-size:11px;line-height:1.6;color:#aaa}.bat-profile-card-top{display:flex;align-items:center;justify-content:space-between;gap:10px}.bat-profile-badge{font-size:10px;font-weight:800;letter-spacing:.7px;border:1px solid #555;border-radius:5px;padding:4px 7px}.bat-profile-card[aria-pressed=true] .bat-profile-badge{background:#fff;color:#000;border-color:#fff}.bat-profile-details{padding:18px;border:1px solid #333;border-radius:12px;min-width:0}.bat-profile-details h3{margin:0 0 10px;font-size:17px;overflow-wrap:anywhere}.bat-profile-details>button,.bat-profile-form>button{width:100%;margin-bottom:8px}.bat-profile-form{margin-top:16px;padding-top:18px;border-top:1px solid #333}.bat-profile-form label{font-size:12px;color:#ccc;line-height:1.5}.bat-profile-form .bat-profile-hint{font-size:11px;margin:8px 0 0}.bat-profile-details p{font-size:12px}@media(max-width:900px){.bat-profile-layout{grid-template-columns:1fr}.bat-profile-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.bat-profile-summary{align-items:flex-start;flex-direction:column}}@media(max-width:480px){.bat-profile-grid{grid-template-columns:1fr}}@media(prefers-reduced-motion:reduce){.bat-profile-card{transition:none}}';
    document.head.appendChild(profileStyle);
    ['keybinds','profiles'].forEach(id=>{
        views.push(id); headlines[id]=id==='keybinds'?'KEYBINDS':'PROFILES';
        const section=document.createElement('section'); section.id='view-'+id; section.className='card hidden bat-tools-view'; source.parentNode.appendChild(section);
        const tab=document.createElement('button'); tab.className='nav-item mm-tab bat-tools-tab'; tab.dataset.view=id;
        tab.appendChild(clientToolIcon(id));const caption=document.createElement('span');caption.textContent=headlines[id];tab.appendChild(caption);tab.setAttribute('aria-label',headlines[id]);
        tab.addEventListener('click',()=>{closeModSettings(); switchView(id);});
        tabs.insertBefore(tab,tabs.querySelector('.mm-x'));
    });
})();
