// Bat Client web UI main menu
// Buttons call into Java via the `batAction(...)` bridge (Ultralight bindFunction).

// ---- bridge to the Minecraft/Java side ----
// NOTE: these wrappers must NOT be named batAction/batGetPlayer — a script
// function declaration of the same name replaces window.batAction (the native
// binding) and clicking then recurses until RangeError, doing nothing.
function callBat(action) {
    if (typeof window.batAction === "function") {
        try { window.batAction(action); } catch (e) { console.log("[bat] bridge threw: " + e); }
    } else {
        console.log("[bat] bridge missing: window.batAction");
    }
}

function getSessionName() {
    if (typeof window.batGetPlayer === "function") {
        try { return window.batGetPlayer(); } catch (e) { /* ignore */ }
    }
    return null;
}

let hudEditorBeforePanel = false;
let nativeHud = typeof window.batUsesNativeHud === "function" && window.batUsesNativeHud() === "true";
if (nativeHud) document.body.classList.add("native-hud");
let nativeHudSizes = {};

// ---- main menu interactions ----
document.querySelectorAll("[data-action]").forEach(btn => {
    btn.addEventListener("click", () => {
        const action = btn.dataset.action;
        console.log("[bat] click " + action);
        switch (action) {
            case "singleplayer":
            case "multiplayer":
            case "settings":
            case "quit":
            case "website":
            case "resume":
            // ---- pause screen actions (handled in Java, WebScreen.handleAction) ----
            case "savequit":
            case "serverlist":
            case "originalpause":
            case "advancements":
            case "statistics":
            case "openworlds":
            case "opentolan":
            case "mcfolder":
            case "openshotsfolder":
                callBat(action);
                break;
            case "cosmetics":
                openPanel("cosmetics");
                break;
            case "mods":
                openPanel("mods");
                break;
            case "client-settings":
                // Mod menu only — HUD editor stays off so widgets don't float on top.
                openPanel("mods");
                break;
            case "hudtoggle":
                toggleHudEditor();
                break;
            case "back":
                closePanel();
                break;
            // ---- pause page: in-page navigation (no Java round-trip) ----
            case "screenshots":
                openShots();
                break;
            case "shotsclose":
                closeShots();
                break;
            case "shotviewerclose":
                closeShotView();
                break;
            case "shotprev":
                stepShot(-1);
                break;
            case "shotnext":
                stepShot(1);
                break;
            case "shotopenextern":
                if (window.__shotNames && window.__shotNames.length) {
                    callBat("openshot:" + window.__shotNames[window.__shotIndex || 0]);
                }
                break;
            case "resourcepacks":
                window.location.href = "respacks.html";
                break;
            case "respacksclose":
                window.location.href = "pause.html";
                break;
            default:
                callBat(action);
                break;
        }
    });
});

document.querySelectorAll(".pill-btn").forEach(btn => {
    btn.addEventListener("click", () => {
        const action = btn.dataset.action;
        if (action === "cosmetics") openPanel("cosmetics");
        else if (action === "settings") callBat("settings");
    });
});

// ---- settings panel view switching ----
const views = ["settings", "cosmetics", "mods"];
const headlines = {
    settings: "SETTINGS",
    cosmetics: "COSMETICS",
    mods: "MOD MENU"
};
let curView = "mods";

function openPanel(view) {
    if (!document.body.classList.contains("panel-open")) {
        hudEditorBeforePanel = document.body.classList.contains("hud-edit");
        if (hudEditorBeforePanel) toggleHudEditor(false);
    }
    callBat("hudpanel:on");
    document.getElementById("panel-layer").classList.remove("hidden");
    document.body.classList.add("panel-open");
    switchView(view);
    closeModSettings();
    batAnimateEntrance(document.querySelector(".mm-win"));
}

function closePanel(restoreEditor = true) {
    document.body.classList.remove('wp-manager');
    callBat('keybind-cancel'); window.__batManagerKeyCaptured = null;
    document.getElementById("panel-layer").classList.add("hidden");
    document.body.classList.remove("panel-open");
    callBat("hudpanel:off");
    if (restoreEditor && hudEditorBeforePanel) toggleHudEditor(true);
    hudEditorBeforePanel = false;
    clearNativePreview();
    batMenuEnter();
    // the in-game overlay page has no #main-brand header
    const brand = document.getElementById("main-brand");
    if (brand) brand.classList.add("active");
}

function switchView(target) {
    if (target !== 'keybinds') { callBat('keybind-cancel'); window.__batManagerKeyCaptured = null; }
    curView = target;
    document.body.classList.toggle('wp-manager',target==='waypoints');
    views.forEach(v => {
        const el = document.getElementById("view-" + v);
        if (el) el.classList.toggle("hidden", v !== target);
    });
    document.querySelectorAll(".nav-item").forEach(n => {
        n.classList.toggle("active", n.dataset.view === target);
    });
    const headline = document.getElementById("headline");
    if (headline) headline.textContent = headlines[target];
    batAnimateEntrance(document.getElementById("view-" + target));
    if (target === "keybinds") renderKeybindManager();
    if (target === "profiles") renderClientProfiles();
    if (target === "waypoints" && typeof renderWaypointManager==='function')renderWaypointManager();
}

document.querySelectorAll(".nav-item").forEach(item => {
    item.addEventListener("click", () => {
        switchView(item.dataset.view);
        closeModSettings();
    });
});

// ---- toggles (cosmetic placeholders for now) ----
document.querySelectorAll(".switch input").forEach(toggle => {
    toggle.addEventListener("change", () => {
        const status = document.getElementById("status");
        if (status) status.textContent = toggle.checked ? "\u2713 " + toggle.id + " enabled" : "\u2717 " + toggle.id + " disabled";
    });
});

// Native skin face + hat PNGs; integer canvas scaling stays crisp in Ultralight.
const accountHeadImages = new Map();
function drawAvatar(name, canvas = document.getElementById("user-avatar"), pixelScale = 5, head = '') {
    if(!canvas)return;
    if(canvas.id==='user-avatar'&&!head&&typeof window.batPlayerHead==='function') {
        try{head=window.batPlayerHead()||'';}catch(e){}
    }
    const SIZE = 8 * pixelScale;
    canvas.width = SIZE;
    canvas.height = SIZE;
    const ctx = canvas.getContext("2d");
    ctx.imageSmoothingEnabled = false;
    ctx.fillStyle='#131313';ctx.fillRect(0,0,SIZE,SIZE);
    canvas.style.width = SIZE + "px";
    canvas.style.height = SIZE + "px";
    canvas.dataset.skinName=name;canvas.dataset.skinUrl=head;
    if(typeof head!=='string'||!head.startsWith('data:image/png;base64,')||head.length>40000)return;
    let image=accountHeadImages.get(head);
    if(!image){image=new Image();if(accountHeadImages.size>=128)accountHeadImages.delete(accountHeadImages.keys().next().value);accountHeadImages.set(head,image);image.src=head;}
    const paint=()=>{
        if(canvas.dataset.skinUrl!==head||canvas.dataset.skinName!==name)return;
        // Ultralight may smooth drawImage enlargement even with smoothing disabled.
        // Decode at native 8x8, then paint each pixel as an integer-sized rectangle.
        const source=document.createElement('canvas');source.width=8;source.height=8;
        const sourceContext=source.getContext('2d');sourceContext.drawImage(image,0,0);
        const pixels=sourceContext.getImageData(0,0,8,8).data;ctx.clearRect(0,0,SIZE,SIZE);
        for(let y=0;y<8;y++)for(let x=0;x<8;x++) {
            const offset=(y*8+x)*4;if(!pixels[offset+3])continue;
            ctx.fillStyle='rgba('+pixels[offset]+','+pixels[offset+1]+','+pixels[offset+2]+','+(pixels[offset+3]/255)+')';
            ctx.fillRect(x*pixelScale,y*pixelScale,pixelScale,pixelScale);
        }
    };
    if(image.complete&&image.naturalWidth)paint();else image.addEventListener('load',paint,{once:true});
}
if(document.getElementById('user-avatar'))setInterval(()=>{
    if(document.hidden||typeof window.batPlayerHead!=='function')return;
    try {
        const head=window.batPlayerHead()||'',name=getSessionName()||document.getElementById('user-name').textContent;
        const canvas=document.getElementById('user-avatar');
        if(canvas.dataset.skinUrl!==head||canvas.dataset.skinName!==name)drawAvatar(name,canvas,5,head);
    }catch(e){}
},1000);

// Account credentials never enter localStorage or the page's account list.
let accountPoll = null, accountRenderKey = '';
function closeAccounts() {
    const panel=document.getElementById('account-panel');
    if(!panel || panel.classList.contains('hidden'))return false;
    panel.classList.add('hidden');document.getElementById('account-open').setAttribute('aria-expanded','false');
    document.getElementById('account-layer').classList.add('hidden');
    document.getElementById('account-redirect').value='';
    clearInterval(accountPoll);accountPoll=null;
    document.getElementById('account-open').focus();return true;
}
function accountRequest(action,value='') {
    if(typeof window.batAccounts!=='function') {
        document.getElementById('account-message').textContent='Account switching is available inside Minecraft.';return;
    }
    try {renderAccounts(JSON.parse(window.batAccounts(action,value)));}
    catch(e){document.getElementById('account-message').textContent='Could not contact the account manager. Please reopen this panel.';}
}
function renderAccounts(state) {
    const root=document.getElementById('account-list');if(!root)return;
    const key=JSON.stringify([state.accounts.map(a=>[a.id,a.name,a.kind,a.expired]),state.active,state.busy,state.connected]);
    if(key!==accountRenderKey) {
        const focusedInList=root.contains(document.activeElement);
        const focusedRow=document.activeElement.closest('.account-row');
        const focusedId=focusedRow&&focusedRow.dataset.account;
        accountRenderKey=key;root.textContent='';
        state.accounts.forEach((account,index)=>{
            const row=document.createElement('div');row.className='account-row'+(account.id===state.active?' active':'');
            row.dataset.account=account.id;
            row.style.setProperty('--account-delay',Math.min(index,5)*30+'ms');
            const pick=document.createElement('button');pick.type='button';pick.className='account-pick';
            pick.setAttribute('aria-label',(account.id===state.active?'Playing as ':'Switch to ')+account.name);
            pick.setAttribute('aria-pressed',String(account.id===state.active));
            pick.disabled=state.busy||state.connected||account.expired||account.id===state.active;
            row.classList.toggle('available',!pick.disabled);
            const avatar=document.createElement('canvas');avatar.className='account-face';avatar.setAttribute('aria-hidden','true');drawAvatar(account.name,avatar,8,account.head||'');
            const person=document.createElement('div');person.className='account-person';
            const name=document.createElement('strong');name.textContent=account.name;
            const type=document.createElement('small');type.textContent=account.kind+(account.expired?' · Sign in again':'');
            person.append(name,type);pick.append(avatar,person);
            const status=document.createElement('span');status.className='account-card-status';status.textContent=account.id===state.active?'✓ Playing':account.expired?'Sign in again':'Click to switch';pick.append(status);row.append(pick);
            row.addEventListener('click',event=>{if(!event.target.closest('.account-remove')&&!pick.disabled)accountRequest('switch',account.id);});
            const remove=document.createElement('button');remove.type='button';remove.className='account-remove';remove.innerHTML='<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v5m4-5v5"/></svg><span>Remove</span>';remove.setAttribute('aria-label','Remove '+account.name);
            remove.disabled=state.busy||account.id==='launch'||(state.connected&&account.id===state.active);
            remove.title=account.id==='launch'?'Your launcher account stays available':account.id===state.active?'Remove and return to your launcher account':'Remove this account';
            remove.addEventListener('click',event=>{event.stopPropagation();accountRequest('remove',account.id);});row.append(remove);
            root.append(row);
        });
        const add=document.createElement('button');add.type='button';add.className='account-add-tile';add.innerHTML='<span aria-hidden="true">+</span><strong>Add account</strong><small>Microsoft or offline</small>';
        add.addEventListener('click',()=>{document.getElementById('account-panel').classList.add('account-adding');document.getElementById('account-tab-microsoft').focus();});
        root.append(add);
        if(focusedInList) {
            const matching=Array.from(root.querySelectorAll('.account-row')).find(row=>row.dataset.account===focusedId);
            const target=matching&&matching.querySelector('button:not(:disabled)');
            (target||add).focus();
        }
    }
    root.querySelectorAll('.account-row').forEach(row=>{
        const account=state.accounts.find(a=>a.id===row.dataset.account),canvas=row.querySelector('.account-face');
        if(account&&canvas.dataset.skinUrl!==(account.head||''))drawAvatar(account.name,canvas,8,account.head||'');
    });
    document.getElementById('account-count').textContent=state.accounts.length+' ACCOUNT'+(state.accounts.length===1?'':'S');
    document.getElementById('account-message').textContent=state.connected?'Return to the main menu before switching accounts.':state.message||'Select an account to switch.';
    document.querySelector('#account-offline button').disabled=state.busy;
    document.getElementById('account-microsoft').disabled=state.busy;
    document.getElementById('account-finish').classList.toggle('hidden',state.phase!=='browser'&&state.phase!=='working');
    document.querySelector('#account-finish button[type="submit"]').disabled=state.busy;
    if(state.current && (document.getElementById('user-name').textContent!==state.current||document.getElementById('user-avatar').dataset.skinUrl!==(state.currentHead||''))){document.getElementById('user-name').textContent=state.current;drawAvatar(state.current,document.getElementById('user-avatar'),5,state.currentHead||'');}
}
function setAccountTab(tab) {
    document.querySelectorAll('[data-account-tab]').forEach(button=>{
        const selected=button.dataset.accountTab===tab;button.setAttribute('aria-selected',String(selected));button.tabIndex=selected?0:-1;
    });
    document.getElementById('account-ms-pane').classList.toggle('hidden',tab!=='microsoft');
    document.getElementById('account-offline-pane').classList.toggle('hidden',tab!=='offline');
}
if(document.getElementById('account-open')) {
    document.getElementById('account-open').addEventListener('click',()=>{
        if(closeAccounts())return;
        document.getElementById('account-panel').classList.remove('hidden');
        document.getElementById('account-panel').classList.remove('account-adding');
        document.getElementById('account-layer').classList.remove('hidden');
        document.getElementById('account-open').setAttribute('aria-expanded','true');accountRenderKey='';accountRequest('list');
        accountPoll=setInterval(()=>{if(!document.hidden)accountRequest('list');},750);
        document.getElementById('account-close').focus();
    });
    document.getElementById('account-close').addEventListener('click',closeAccounts);
    document.getElementById('account-back').addEventListener('click',()=>{document.getElementById('account-panel').classList.remove('account-adding');document.querySelector('.account-add-tile').focus();});
    document.getElementById('account-offline').addEventListener('submit',event=>{
        event.preventDefault();const input=document.getElementById('account-name');accountRequest('offline',input.value.trim());input.value='';document.getElementById('account-panel').classList.remove('account-adding');
    });
    document.getElementById('account-microsoft').addEventListener('click',()=>accountRequest('microsoft'));
    document.getElementById('account-finish').addEventListener('submit',event=>{
        event.preventDefault();const input=document.getElementById('account-redirect');const value=input.value;input.value='';accountRequest('finish',value);
    });
    document.getElementById('account-cancel').addEventListener('click',()=>accountRequest('cancel'));
    document.getElementById('account-layer').addEventListener('click',event=>{if(event.target.id==='account-layer')closeAccounts();});
    document.querySelectorAll('[data-account-tab]').forEach(button=>{
        button.addEventListener('click',()=>setAccountTab(button.dataset.accountTab));
        button.addEventListener('keydown',event=>{
            if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;
            event.preventDefault();const target=event.key==='Home'?'microsoft':event.key==='End'?'offline':button.dataset.accountTab==='microsoft'?'offline':'microsoft';
            setAccountTab(target);document.getElementById('account-tab-'+target).focus();
        });
    });
    document.getElementById('account-panel').addEventListener('keydown',event=>{
        if(event.key!=='Tab')return;
        const items=Array.from(document.querySelectorAll('#account-panel button:not(:disabled),#account-panel input')).filter(el=>el.offsetParent!==null);
        const first=items[0],last=items[items.length-1];
        if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus();}
        else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}
    });
}

// ---- pause screen: screenshots browser (page layer over pause.html) ----
function openShots() {
    const layer = document.getElementById("shots-layer");
    if (!layer) return;
    const grid = document.getElementById("shots-grid");
    grid.innerHTML = "";
    let names = [];
    if (typeof window.batScreenshots === "function") {
        try { names = JSON.parse(window.batScreenshots() || "[]"); } catch (e) { names = []; }
    }
    window.__shotNames = names;
    window.__shotIndex = 0;
    if (!names.length) {
        const empty = document.createElement("div");
        empty.className = "shots-empty";
        empty.textContent = "No Screenshots :(";
        grid.appendChild(empty);
    } else {
        names.forEach((name, i) => {
            const cell = document.createElement("div");
            cell.className = "shot";
            const img = document.createElement("img");
            img.src = "screenshots/" + name;
            img.alt = name;
            cell.appendChild(img);
            cell.addEventListener("click", () => openShotView(i));
            grid.appendChild(cell);
        });
    }
    layer.classList.remove("hidden");
    tickClock(document.getElementById("shots-clock"));
}

function closeShots() {
    closeShotView();
    const layer = document.getElementById("shots-layer");
    if (layer) layer.classList.add("hidden");
}

// ---- in-page screenshot viewer (lightbox) ----
function openShotView(i) {
    const viewer = document.getElementById("shot-viewer");
    const names = window.__shotNames || [];
    if (!viewer || !names.length) return;
    window.__shotIndex = ((i % names.length) + names.length) % names.length;
    const name = names[window.__shotIndex];
    document.getElementById("shot-view-img").src = "screenshots/" + name;
    document.getElementById("shot-viewer-name").textContent = name;
    document.getElementById("shot-viewer-count").textContent =
        (window.__shotIndex + 1) + " / " + names.length;
    viewer.classList.remove("hidden");
}

function closeShotView() {
    const viewer = document.getElementById("shot-viewer");
    if (viewer) viewer.classList.add("hidden");
}

function shotViewOpen() {
    const viewer = document.getElementById("shot-viewer");
    return !!viewer && !viewer.classList.contains("hidden");
}

function stepShot(delta) {
    if (!shotViewOpen()) return;
    openShotView((window.__shotIndex || 0) + delta);
}

// ---- time pill (screenshots + resource packs headers), Feather format ----
function tickClock(el) {
    if (!el) return;
    const d = new Date();
    const p = n => String(n).padStart(2, "0");
    const raw = d.getHours();
    const ampm = raw >= 12 ? "PM" : "AM";
    let h = raw % 12;
    if (h === 0) h = 12;
    const text = (raw >= 18 || raw < 6 ? "\u263E " : "\u2600 ") +
        d.getFullYear() + "/" + p(d.getMonth() + 1) + "/" + p(d.getDate()) +
        " " + p(h) + ":" + p(d.getMinutes()) + " " + ampm;
    if (el.textContent !== text) el.textContent = text;
}
tickClock(document.getElementById("shots-clock"));
tickClock(document.getElementById("rp-clock"));
tickClock(document.getElementById("mm-clock"));
setInterval(() => {
    tickClock(document.getElementById("shots-clock"));
    tickClock(document.getElementById("rp-clock"));
    tickClock(document.getElementById("mm-clock"));
}, 15000);

// ---- ESC ladder (Java forwards ESC to the page in pause mode) ----
// viewer -> close viewer; screenshots layer -> close layer;
// resource browser -> back to pause; pause -> game.
function dismissGameOverlay() {
    closeColorPicker();
    closeModSettings();
    closePanel(false);
    toggleHudEditor(false);
    hideHudGuides();
    cfgSave();
}

document.addEventListener("keydown", e => {
    if (e.key !== "Escape") return;
    if (closeAccounts()) return;
    if (document.body.classList.contains("overlay-page")) {
        dismissGameOverlay();
        callBat("resume");
        return;
    }
    if (closeColorPicker()) return;          // color popup -> close it
    if (shotViewOpen()) {
        closeShotView();
        return;
    }
    const layer = document.getElementById("shots-layer");
    if (layer && !layer.classList.contains("hidden")) {
        closeShots();
        return;
    }
    if (document.body.classList.contains("respacks-page")) {
        window.location.href = "pause.html";
        return;
    }
    if (document.body.classList.contains("pause-page")) {
        callBat("resume");
    }
});

// ============ MOD MENU — essential module catalog (each with its own settings) ============
const GEAR_SVG = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33h.01a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51h.01a1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82v.01a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>';

const MODULES = [
    {
        id: "fps", name: "FPS", cat: "hud", widget: true, def: true, defPos: { x: 0, y: 1 },
        desc: "Show your framerate on screen",
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3.5" y="4.5" width="17" height="12" rx="2"/><path d="M9 20.5h6M12 16.5v4"/><path d="M7 12l2.5-3 2 2 3.5-4 2 5"/></svg>',
        preview: '<span class="hp">144 FPS</span>',
        defaults: { scale: 1, shadow: true, background: true, bgColor: "#0000006B", color: "#FFFFFFFF" },
        settings: [{ title: "General", rows: [
            { label: "Text Scale", type: "slider", key: "scale", min: 0.5, max: 3, step: 0.1 },
            { label: "Text Shadow", type: "toggle", key: "shadow" },
            { label: "Background", type: "toggle", key: "background" },
            { label: "Background Color", type: "color", key: "bgColor" },
            { label: "Text Color", type: "color", key: "color" }
        ]}]
    },
    {
        id: "coords", name: "Coordinates", cat: "hud", widget: true, def: true, defPos: { x: 89, y: 18 },
        desc: "Show your coordinates, facing direction and biome",
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 21s7-6.1 7-11a7 7 0 1 0-14 0c0 4.9 7 11 7 11z"/><circle cx="12" cy="10" r="2.6"/></svg>',
        preview: '<span class="hp">X: 125  Y: 64  Z: -340</span>',
        defaults: { scale: 1, shadow: true, background: true, bgColor: "#0000006B", color: "#FFFFFFFF", labels: true },
        settings: [{ title: "General", rows: [
            { label: "Text Scale", type: "slider", key: "scale", min: 0.5, max: 3, step: 0.1 },
            { label: "Text Shadow", type: "toggle", key: "shadow" },
            { label: "Background", type: "toggle", key: "background" },
            { label: "Background Color", type: "color", key: "bgColor" },
            { label: "Axis Labels", type: "toggle", key: "labels" },
            { label: "Text Color", type: "color", key: "color" }
        ]}]
    },
    {
        id: "ping", name: "Ping", cat: "hud", widget: true, def: true, defPos: { x: 93, y: 1 },
        desc: "Show your server latency",
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M5 11.5a11 11 0 0 1 14 0"/><path d="M8 15a6.5 6.5 0 0 1 8 0"/><circle cx="12" cy="18.5" r="1.6"/></svg>',
        preview: '<span class="hp">888 ms</span>',
        defaults: { scale: 1, shadow: true, background: true, bgColor: "#0000006B", color: "#FFFFFFFF" },
        settings: [{ title: "General", rows: [
            { label: "Text Scale", type: "slider", key: "scale", min: 0.5, max: 3, step: 0.1 },
            { label: "Text Shadow", type: "toggle", key: "shadow" },
            { label: "Background", type: "toggle", key: "background" },
            { label: "Background Color", type: "color", key: "bgColor" },
            { label: "Text Color", type: "color", key: "color" }
        ]}]
    },
    {
        id: "cps", name: "CPS", cat: "hud", widget: true, def: false, defPos: { x: 0, y: 9 },
        desc: "Show your clicks per second",
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="6.5" y="4" width="11" height="16" rx="5.5"/><path d="M12 7.5v3.5"/></svg>',
        preview: '<span class="hp">LMB 5 CPS   RMB 2 CPS</span>',
        defaults: { scale: 1, shadow: true, background: true, bgColor: "#0000006B", color: "#FFFFFFFF" },
        settings: [{ title: "General", rows: [
            { label: "Text Scale", type: "slider", key: "scale", min: 0.5, max: 3, step: 0.1 },
            { label: "Text Shadow", type: "toggle", key: "shadow" },
            { label: "Background", type: "toggle", key: "background" },
            { label: "Background Color", type: "color", key: "bgColor" },
            { label: "Text Color", type: "color", key: "color" }
        ]}]
    },
    {
        id: "keystrokes", name: "Keystrokes", cat: "hud", widget: true, def: false, defPos: { x: 7, y: 78 },
        desc: "Display your key presses",
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="2.5" y="6" width="19" height="12" rx="2"/><path d="M6 10h.01M10 10h.01M14 10h.01M18 10h.01M8 14h8"/></svg>',
        preview: '<span class="keys"><span class="krow kr-c"><b data-k="w">W</b></span><span class="krow"><i data-k="a">A</i><i data-k="s">S</i><i data-k="d">D</i></span><span class="krow"><u class="km" data-k="lmb"><span>LMB</span><span>5</span></u><u class="km" data-k="rmb"><span>RMB</span><span>2</span></u></span><span class="krow"><u class="ks" data-k="space"></u></span></span>',
        defaults: { scale: 1, shadow: true, color: "#FFFFFFFF", background: true, bgColor: "#0000006B", border: true, showCps: true, pressedColor: "#FFFFFF55" },
        settings: [
            { title: "Style", rows: [
                { label: "Background", type: "toggle", key: "background" },
            { label: "Background Color", type: "color", key: "bgColor" },
                { label: "Border", type: "toggle", key: "border" },
                { label: "Show CPS", type: "toggle", key: "showCps" },
                { label: "Pressed Color", type: "color", key: "pressedColor" }
            ]},
            { title: "General", rows: [
                { label: "Text Scale", type: "slider", key: "scale", min: 0.5, max: 3, step: 0.1 },
                { label: "Text Shadow", type: "toggle", key: "shadow" },
                { label: "Text Color", type: "color", key: "color" }
            ]}
        ]
    },
    {
        id: "potions", name: "Potion Effects", cat: "hud", widget: true, def: false, defPos: { x: 0, y: 44 },
        desc: "Show your active potion effects",
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M9.5 3.5h5M10.5 3.5v5.5l-4.4 7.7A2.4 2.4 0 0 0 8.2 20.5h7.6a2.4 2.4 0 0 0 2.1-3.8L13.5 9V3.5"/><path d="M8 15.5h8"/></svg>',
        preview: '<span class="hp hp-potions"><u>Speed II<i>1:24</i></u><u>Strength<i>0:45</i></u></span>',
        defaults: { scale: 1, shadow: true, background: true, bgColor: "#0000006B", color: "#FFFFFFFF", format: "Shorthand", expiryWarning: true, warningSeconds:10, warningColor:"#FF5555FF" },
        settings: [{ title: "General", rows: [
            { label: "Time Format", type: "choice", key: "format", options: ["Shorthand", "Full"] },
            { label: "Expiry Warning", type: "toggle", key: "expiryWarning" },
            { label: "Warn Below (seconds)", type: "slider", key: "warningSeconds", min:1, max:60, step:1 },
            { label: "Expiry Timer Color", type: "color", key: "warningColor" },
            { label: "Text Scale", type: "slider", key: "scale", min: 0.5, max: 3, step: 0.1 },
            { label: "Text Shadow", type: "toggle", key: "shadow" },
            { label: "Background", type: "toggle", key: "background" },
            { label: "Background Color", type: "color", key: "bgColor" },
            { label: "Text Color", type: "color", key: "color" }
        ]}]
    },
    {
        id: "armor", name: "Armor HUD", cat: "hud", widget: true, def: false, defPos: { x: 94, y: 82 },
        desc: "Show equipped armor with durability numbers",
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l7 3v5c0 5-3.4 8.6-7 10-3.6-1.4-7-5-7-10V6l7-3z"/></svg>',
        preview: '<span class="hp hp-armor"><u><b class="pit"></b><span>363</span></u><u><b class="pit"></b><span>528</span></u><u><b class="pit"></b><span>495</span></u><u><b class="pit"></b><span>429</span></u></span>',
        defaults: { scale: 1, shadow: true, numbers: true, lowWarning: true, warningPercent: 15, warningFlash: true, warningColor: "#FF5555FF", background: true, bgColor: "#0000006B", color: "#FFFFFFFF" },
        settings: [{ title: "General", rows: [
            { label: "Durability Numbers", type: "toggle", key: "numbers" },
            { label: "Low Durability Warning", type: "toggle", key: "lowWarning" },
            { label: "Warning Threshold (%)", type: "slider", key: "warningPercent", min: 1, max: 50, step: 1 },
            { label: "Flash Warning", type: "toggle", key: "warningFlash" },
            { label: "Warning Color", type: "color", key: "warningColor" },
            { label: "Text Scale", type: "slider", key: "scale", min: 0.5, max: 3, step: 0.1 },
            { label: "Text Shadow", type: "toggle", key: "shadow" },
            { label: "Background", type: "toggle", key: "background" },
            { label: "Background Color", type: "color", key: "bgColor" },
            { label: "Text Color", type: "color", key: "color" }
        ]}]
    },
    {
        id: "durability", name: "Armor Durability", cat: "hud", widget: true, def: false, defPos: { x: 82, y: 76 },
        desc: "Horizontal armor icons with durability percentage",
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l7 3v5c0 5-3.4 8.6-7 10-3.6-1.4-7-5-7-10V6l7-3z"/><path d="M9 11.5l2 2 4-4"/></svg>',
        preview: '<span class="hp hp-dura"><u><b class="pit"></b><span>98%</span></u><u><b class="pit"></b><span>87%</span></u><u><b class="pit"></b><span>92%</span></u><u><b class="pit"></b><span>45%</span></u></span>',
        defaults: { scale: 1, shadow: true, numbers: true, lowWarning: true, warningPercent: 15, warningFlash: true, warningColor: "#FF5555FF", background: true, bgColor: "#0000006B", color: "#FFFFFFFF" },
        settings: [{ title: "General", rows: [
            { label: "Durability Numbers", type: "toggle", key: "numbers" },
            { label: "Low Durability Warning", type: "toggle", key: "lowWarning" },
            { label: "Warning Threshold (%)", type: "slider", key: "warningPercent", min: 1, max: 50, step: 1 },
            { label: "Flash Warning", type: "toggle", key: "warningFlash" },
            { label: "Warning Color", type: "color", key: "warningColor" },
            { label: "Text Scale", type: "slider", key: "scale", min: 0.5, max: 3, step: 0.1 },
            { label: "Text Shadow", type: "toggle", key: "shadow" },
            { label: "Background", type: "toggle", key: "background" },
            { label: "Background Color", type: "color", key: "bgColor" },
            { label: "Text Color", type: "color", key: "color" }
        ]}]
    },
    {
        id: "crosshair", name: "Custom Crosshair", cat: "hud", widget: false, nativePreview: true, def: false,
        desc: "Replace the vanilla crosshair at the centre of the screen, without a background",
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="4.5"/><path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3"/></svg>',
        preview: '<span class="hp hp-cross">&#10010;</span>',
        defaults: { scale: 1, color: "#FFFFFFFF", style: "Cross" },
        settings: [{ title: "General", rows: [
            { label: "Style", type: "choice", key: "style", options: ["Cross", "Cross + Dot", "Dot"] },
            { label: "Size", type: "slider", key: "scale", min: 0.5, max: 3, step: 0.1 },
            { label: "Color", type: "color", key: "color" }
        ]}]
    },
    {
        id: "blockindicator", name: "Block Indicator", cat: "hud", widget: true, def: false, defPos: { x: 62, y: 35 },
        desc: "Show the block you are looking at, its ID and breaking percentage",
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 2 22 7v10l-10 5L2 17V7zM2 7l10 5 10-5M12 12v10"/></svg>',
        preview: '<span class="hp">▧ Dirt<br><span style="color:#aaa">minecraft:dirt</span></span>',
        defaults: { scale: 1, range: 12, showBreaking: true, showId: true, shadow: true, background: true, bgColor: "#0000006B", color: "#FFFFFFFF", idColor: "#AAAAAAFF", border: false, borderColor: "#FFFFFFFF", borderThickness: 1 },
        settings: [{ title: "General", rows: [
            { label: "Indicator Range", type: "slider", key: "range", min: 1, max: 32, step: 1 },
            { label: "Show Block Breaking Percentage", type: "toggle", key: "showBreaking" },
            { label: "Show Block ID", type: "toggle", key: "showId" },
            { label: "Size", type: "slider", key: "scale", min: 0.5, max: 3, step: 0.1 },
            { label: "Text Shadow", type: "toggle", key: "shadow" }
        ]}, { title: "Style", rows: [
            { label: "Background", type: "toggle", key: "background" },
            { label: "Background Color", type: "color", key: "bgColor" },
            { label: "Text Color", type: "color", key: "color" },
            { label: "Block ID Color", type: "color", key: "idColor" },
            { label: "Border", type: "toggle", key: "border" },
            { label: "Border Color", type: "color", key: "borderColor" },
            { label: "Border Thickness", type: "slider", key: "borderThickness", min: 1, max: 4, step: 1 }
        ]}]
    },
    {
        id: "target", name: "Target HUD", cat: "hud", widget: true, def: false, defPos: { x: 35, y: 72 },
        desc: "Live target health, smooth health bar, player portrait, distance and armor",
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 8V5.5A1.5 1.5 0 0 1 5.5 4H8M16 4h2.5A1.5 1.5 0 0 1 20 5.5V8M20 16v2.5a1.5 1.5 0 0 1-1.5 1.5H16M8 20H5.5A1.5 1.5 0 0 1 4 18.5V16"/><circle cx="12" cy="12" r="2.6"/></svg>',
        preview: '<span class="hp">Zombie<br>14.0 / 20.0</span>',
        defaults: { scale: 1, shadow: true, background: true, bgColor: "#0000006B", color: "#FFFFFFFF", width:140, portrait:true, distance:true, armor:true, playersOnly:false, holdTime:.8, smooth:true, healthFormat:"Hearts", barColor:"#FFFFFFFF", lowWarning:true },
        settings: [{ title: "General", rows: [
            { label: "Players Only", type: "toggle", key: "playersOnly" },
            { label: "Portrait / Mob Icon", type: "toggle", key: "portrait" },
            { label: "Distance", type: "toggle", key: "distance" },
            { label: "Armor and Absorption", type: "toggle", key: "armor" },
            { label: "Health Format", type: "choice", key: "healthFormat", options:["Hearts","Health","Percent"] },
            { label: "Hold After Looking Away (seconds)", type: "slider", key: "holdTime", min:0, max:3, step:.1 },
            { label: "Smooth Health Bar", type: "toggle", key: "smooth" },
            { label: "Low Health Warning", type: "toggle", key: "lowWarning" },
            { label: "Width", type: "slider", key: "width", min:110, max:240, step:5 },
            { label: "Health Bar Color", type: "color", key: "barColor" },
            { label: "Text Scale", type: "slider", key: "scale", min: 0.5, max: 3, step: 0.1 },
            { label: "Text Shadow", type: "toggle", key: "shadow" },
            { label: "Background", type: "toggle", key: "background" },
            { label: "Background Color", type: "color", key: "bgColor" },
            { label: "Text Color", type: "color", key: "color" }
        ]}]
    },
    {
        id: "scoreboard", name: "Scoreboard", cat: "hud", widget: true, def: true, defPos: { x: 90, y: 44 },
        desc: "Move and resize the server scoreboard",
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="4" y="3" width="16" height="18" rx="3"/><path d="M8 8h8M8 12h8M8 16h5"/></svg>',
        preview: '<span class="hp">Bat Client<br>Player: BatPlayer<br>batclient.xyz</span>',
        defaults: { scale: 1, shadow: true, background: true, bgColor: "#0000006B", color: "#FFFFFFFF", showTitle: true, showScores: false },
        settings: [{ title: "Scoreboard", rows: [
            { label: "Show Title", type: "toggle", key: "showTitle" },
            { label: "Show Scores", type: "toggle", key: "showScores" },
            { label: "Text Scale", type: "slider", key: "scale", min: 0.5, max: 3, step: 0.1 },
            { label: "Text Shadow", type: "toggle", key: "shadow" },
            { label: "Background", type: "toggle", key: "background" },
            { label: "Background Color", type: "color", key: "bgColor" },
            { label: "Text Color", type: "color", key: "color" }
        ]}]
    },
    {
        id: "bossbar", name: "Boss Bar", cat: "hud", widget: true, def: true, defPos: { x: 39, y: 1 },
        desc: "Move and resize live boss bars with Minecraft's native bar textures",
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="2" y="10" width="20" height="5" rx="2"/><path d="M5 6h14M5 12.5h11"/></svg>',
        preview: '<span class="hp">Bat Client<br>━━━━━━━━━━━━━━━━</span>',
        defaults: { scale: 1, shadow: true, background: true, bgColor: "#0000006B", color: "#FFFFFFFF", showName: true, maxBars: 5 },
        settings: [{ title: "Boss Bar", rows: [
            { label: "Show Name", type: "toggle", key: "showName" },
            { label: "Maximum Bars", type: "slider", key: "maxBars", min: 1, max: 5, step: 1 },
            { label: "Scale", type: "slider", key: "scale", min: 0.5, max: 3, step: 0.1 },
            { label: "Text Shadow", type: "toggle", key: "shadow" },
            { label: "Background", type: "toggle", key: "background" },
            { label: "Background Color", type: "color", key: "bgColor" },
            { label: "Text Color", type: "color", key: "color" }
        ]}]
    },
    {
        id: "zoom", name: "Zoom", cat: "qol", def: true,
        desc: "Hold your zoom key and scroll up to zoom in further, down to zoom out. Releasing the key resets to your selected level.",
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="10.5" cy="10.5" r="6.5"/><path d="M20 20l-4.5-4.5"/><circle cx="10.5" cy="10.5" r="1.6"/></svg>',
        preview: "",
        defaults: { key: "Minecraft Controls", level: 4, smooth: true, speed: 18, sensitivity: 75, scroll: true, scrollSpeed: 1, maxZoom: 20 },
        settings: [{ title: "Zoom", rows: [
            { label: "Zoom Key", type: "keybind", key: "key", options: ["Minecraft Controls", "C", "V", "Z", "B"] },
            { label: "Zoom Level", type: "slider", key: "level", min: 2, max: 10, step: 1 },
            { label: "Smooth Zoom", type: "toggle", key: "smooth" },
            { label: "Animation Speed", type: "slider", key: "speed", min: 5, max: 30, step: 1 },
            { label: "Zoom Mouse Sensitivity (%)", type: "slider", key: "sensitivity", min: 10, max: 100, step: 5 },
            { label: "Scroll to Zoom", type: "toggle", key: "scroll" },
            { label: "Scroll Sensitivity", type: "slider", key: "scrollSpeed", min: 0.25, max: 2, step: 0.25 },
            { label: "Maximum Zoom", type: "slider", key: "maxZoom", min: 10, max: 40, step: 1 }
        ]}]
    },
    {
        id: "perspective", name: "Perspective Toggle", cat: "qol", def: false,
        desc: "Press P to cycle first / third person views",
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M2.5 12S6 6 12 6s9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6z"/><circle cx="12" cy="12" r="2.8"/></svg>',
        preview: "",
        defaults: {},
        settings: []
    },
    {
        id: "fullbright", name: "Fullbright", cat: "qol", def: false,
        desc: "See clearly in the dark — night vision while enabled",
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="4"/><path d="M12 3v2.5M12 18.5V21M3 12h2.5M18.5 12H21M5.6 5.6l1.8 1.8M16.6 16.6l1.8 1.8M18.4 5.6l-1.8 1.8M7.4 16.6l-1.8 1.8"/></svg>',
        preview: "",
        defaults: {},
        settings: []
    },
    {
        id: "lowfire", name: "Low Fire", cat: "qol", def: false,
        desc: "Lower the first-person fire overlay so it blocks less of the screen",
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3.5c2 3 5 4.5 5 8.5a5 5 0 0 1-10 0c0-2 1-3.5 2.5-4.5.3 1.4 1 2.3 2 2.8.5-2.5-.3-4.8.5-6.8z"/></svg>',
        preview: "",
        defaults: { height: 0.5 },
        settings: [{ title: "Low Fire", rows: [
            { label: "Fire Height", type: "slider", key: "height", min: 0.1, max: 1, step: 0.1 }
        ]}]
    },
    {
        id: "animations", name: "Custom Animations", cat: "qol", def: false,
        desc: "Custom item swing and attack animations",
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="8.5"/><path d="M10 8.5l5.5 3.5L10 15.5z"/></svg>',
        preview: "",
        defaults: { swing: "Smooth" },
        settings: [{ title: "Animations", rows: [
            { label: "Item Swing", type: "choice", key: "swing", options: ["Smooth", "Slide", "Spin"] }
        ]}]
    },
    {
        id: "togglesprint", name: "Toggle Sprint", cat: "qol", widget: true, def: false, defPos: { x: 0, y: 18 },
        desc: "Show walking, sprinting, flying and other movement states, with optional automatic sprint",
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="15" cy="5" r="2"/><path d="M13.5 21l1.5-6-3-2.5 1-4.5 3.5 2.5 3 .5"/><path d="M10 12l-2 4M14.5 15l2.5 6"/></svg>',
        preview: '<span class="hp">Sprinting (Toggled)</span>',
        defaults: { scale: 1, shadow: true, background: true, bgColor: "#0000006B", color: "#FFFFFFFF", autoSprint: true, flightSpeed: true },
        settings: [{ title: "General", rows: [
            { label: "Auto Sprint", type: "toggle", key: "autoSprint" },
            { label: "Show Flight Multiplier", type: "toggle", key: "flightSpeed" },
            { label: "Text Scale", type: "slider", key: "scale", min: 0.5, max: 3, step: 0.1 },
            { label: "Text Shadow", type: "toggle", key: "shadow" },
            { label: "Background", type: "toggle", key: "background" },
            { label: "Background Color", type: "color", key: "bgColor" },
            { label: "Text Color", type: "color", key: "color" }
        ]}]
    },
    {
        id: "nametags", name: "Nametags", cat: "qol", def: true, nativePreview: true,
        desc: "Native Minecraft nametags with badges for other Bat Client players",
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="2" y="5" width="20" height="14" rx="2"/><path d="M6 10h12M8 14h8"/></svg>',
        preview: '<span class="hp nametag-preview"><img src="bat-logo.png" alt="">BatPlayer</span>',
        defaults: { showOwn: true, logo: true, share: true, scale: 1, shadow: true, background: true },
        settings: [{ title: "General", rows: [
            { label: "Show Own Nametag", type: "toggle", key: "showOwn" },
            { label: "Bat Logo", type: "toggle", key: "logo" },
            { label: "Show Bat Badge to Others", type: "toggle", key: "share" },
            { label: "Text Scale", type: "slider", key: "scale", min: 0.5, max: 2, step: 0.1 },
            { label: "Text Shadow", type: "toggle", key: "shadow" },
            { label: "Background", type: "toggle", key: "background" }
        ]}]
    },
    {
        id: "entityculling", name: "Entity Culling", cat: "perf", def: true,
        desc: "Skip rendering entities hidden behind walls",
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3.5l7.5 4v9l-7.5 4-7.5-4v-9z"/><path d="M12 3.5v9M4.5 7.5l7.5 5M19.5 7.5l-7.5 5"/></svg>',
        preview: "",
        defaults: {},
        settings: []
    },
    {
        id: "particlereduce", name: "Particle Reduction", cat: "perf", def: false,
        desc: "Reduce particle count for higher FPS",
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="8" cy="8" r="2.2"/><circle cx="16" cy="7" r="1.6"/><circle cx="15.5" cy="15.5" r="2.6"/><circle cx="7" cy="16.5" r="1.4"/></svg>',
        preview: "",
        defaults: { amount: 50 },
        settings: [{ title: "Particles", rows: [
            { label: "Reduce Amount", type: "slider", key: "amount", min: 0, max: 100, step: 10 }
        ]}]
    },
    {
        id: "dynamicfps", name: "Dynamic FPS", cat: "perf", def: true,
        desc: "Drop FPS when the Minecraft window is unfocused",
        icon: '<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><path d="M13.5 2.5L5 13.5h5l-1.5 8 8.5-11h-5z"/></svg>',
        preview: "",
        defaults: { target: 30 },
        settings: [{ title: "Dynamic FPS", rows: [
            { label: "FPS When Unfocused", type: "slider", key: "target", min: 10, max: 60, step: 5 }
        ]}]
    },

];

MODULES.push({
    id: "inventoryanimations", name: "Inventory Animations", cat: "qol", def: true,
    desc: "Smooth opening animation for survival and creative inventory screens.",
    icon: '<svg viewBox="0 0 64 48" fill="none" stroke="currentColor" stroke-width="3" stroke-linejoin="round"><rect x="17" y="6" width="42" height="36" rx="3"/><path d="M17 18h42M17 30h42M31 6v36M45 6v36M2 12h9M2 24h9M2 36h9"/></svg>',
    preview: '<span class="hp">Inventory Animations</span>',
    defaults: {}, settings: []
});

MODULES.push({
    id:"healthindicator",name:"Health Indicator",cat:"qol",def:false,nativePreview:true,
    desc:"Minecraft hearts above player and mob names, with faint visibility behind blocks",
    icon:'<svg viewBox="0 0 64 48" fill="currentColor"><path d="M32 29 14 14C3 2 20-6 32 7 44-6 61 2 50 14z"/><rect x="12" y="36" width="40" height="6" rx="3"/></svg>',
    preview:'<span class="hp">♥ 7.0 / 10.0<br>Player</span>',
    defaults:{players:true,mobs:true,showOwn:false,mobNames:true,range:24,showValue:false,occluded:true,showMax:true,absorption:true,scale:1,shadow:true,background:true,color:"#FFFFFFFF",lowWarning:true},
    settings:[{title:"General",rows:[
        {label:"Players",type:"toggle",key:"players"},{label:"Mobs",type:"toggle",key:"mobs"},
        {label:"Own Health in Third Person",type:"toggle",key:"showOwn"},{label:"Show Mob Names",type:"toggle",key:"mobNames"},
        {label:"Range",type:"slider",key:"range",min:4,max:64,step:1},
        {label:"Show Health Number",type:"toggle",key:"showValue"},{label:"Faint Visibility Behind Blocks",type:"toggle",key:"occluded"},
        {label:"Show Maximum Health",type:"toggle",key:"showMax"},{label:"Absorption Hearts",type:"toggle",key:"absorption"},
        {label:"Low Health Warning",type:"toggle",key:"lowWarning"},
        {label:"Size",type:"slider",key:"scale",min:.5,max:2,step:.1},
        {label:"Text Shadow",type:"toggle",key:"shadow"},{label:"Background",type:"toggle",key:"background"},
        {label:"Text Color",type:"color",key:"color"}
    ]}]
});

MODULES.push({
    id: "motionblur", name: "Motion Blur", cat: "qol", def: false,
    desc: "Blend world frames for smoother-looking movement. Higher strength adds longer trails and uses extra GPU time.",
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M2 7h7M2 12h5M2 17h7"/><circle cx="15" cy="12" r="6"/></svg>',
    preview: '<span class="hp">Motion Blur</span>',
    defaults: { strength: 40 },
    settings: [{title:"General",rows:[
        {label:"Blur Strength",type:"slider",key:"strength",min:0,max:85,step:5}
    ]}]
});

function extraHudModule(id, name, pos, icon, preview, options, rows) {
    return { id, name, cat: "hud", widget: true, def: false, defPos: pos,
        desc: { iteminfo: "Held item icon, name, stack count, durability and enchantments", compass: "Direction and a compass bar that follows your view", clock: "Your local time or Minecraft world time", speedometer: "Your movement speed, measured in blocks per second or km/h", resourcepacks: "The top active pack: its icon, name and description with original text colors", combo:"Consecutive server-confirmed melee hits on the same target", reach:"Live distance between you and the mob or player you are looking at. Does not extend attack reach.", inventory:"Your main inventory slots with native items and stack counts" }[id],
        icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">' + icon + '</svg>',
        preview: '<span class="hp">' + preview + '</span>',
        defaults: Object.assign({scale:1,shadow:true,background:true,bgColor:"#0000006B",color:"#FFFFFFFF"},options),
        settings: [{title:"General",rows:rows.concat([
            {label:"Size",type:"slider",key:"scale",min:.5,max:3,step:.1},
            {label:"Text Shadow",type:"toggle",key:"shadow"}
        ])},{title:"Style",rows:[
            {label:"Background",type:"toggle",key:"background"},
            {label:"Background Color",type:"color",key:"bgColor"},
            {label:"Text Color",type:"color",key:"color"}
        ]}]
    };
}
MODULES.push(
    ...["totems","arrows"].map(id => {
        const totem=id==="totems";
        const module=extraHudModule(id,totem?"Totem Counter":"Arrow Counter",{x:2,y:totem?48:54},
            totem?'<circle cx="12" cy="6" r="3"/><path d="M12 9v12M4 11l8 3 8-3M8 21l4-5 4 5"/>':'<path d="m4 20 16-16M12 4h8v8M4 14v6h6"/>',
            totem?'Totems: 3':'Arrows: 64',
            {scope:"Inventory",offhand:true,icon:true,label:true,hideZero:false,specialArrows:true,lowWarning:true,warningCount:totem?1:16,warningFlash:false,warningColor:"#FF5555FF"},[
                {label:"Count From",type:"choice",key:"scope",options:["Inventory","Hotbar"]},
                {label:"Include Offhand",type:"toggle",key:"offhand"},
                ...(!totem?[{label:"Include Tipped / Spectral Arrows",type:"toggle",key:"specialArrows"}]:[]),
                {label:"Item Icon",type:"toggle",key:"icon"},
                {label:"Show Label",type:"toggle",key:"label"},
                {label:"Hide at Zero",type:"toggle",key:"hideZero"},
                {label:"Low Count Warning",type:"toggle",key:"lowWarning"},
                {label:"Warning at Count",type:"slider",key:"warningCount",min:0,max:totem?36:256,step:1},
                {label:"Flash Warning",type:"toggle",key:"warningFlash"},
                {label:"Warning Color",type:"color",key:"warningColor"}
            ]);
        module.desc=totem?"Count totems in your inventory or hotbar, with an optional offhand and low-count warning":"Count regular, tipped and spectral arrows, with an optional low-count warning";
        return module;
    }),
    extraHudModule("combo","Combo Counter",{x:2,y:38},'<path d="m4 20 6-6m4-4 6-6M4 4l16 16M4 4l1 5 4-4zM20 20l-1-5-4 4z"/>','Combo: 3',
        {timeout:3,playersOnly:false,hideInactive:false},[
            {label:"Combo Timeout (seconds)",type:"slider",key:"timeout",min:1,max:10,step:.5},
            {label:"Players Only",type:"toggle",key:"playersOnly"},
            {label:"Hide at Zero",type:"toggle",key:"hideInactive"}]),
    extraHudModule("reach","Reach Display",{x:2,y:42},'<path d="M3 12h18M3 8v8M21 8v8m-5-8 5 4-5 4"/>','Reach: 2.85 blocks',
        {range:32,decimals:2,playersOnly:false,hideInactive:false},[
            {label:"Target Detection Range",type:"slider",key:"range",min:4,max:64,step:1},
            {label:"Decimal Places",type:"slider",key:"decimals",min:1,max:3,step:1},
            {label:"Players Only",type:"toggle",key:"playersOnly"},
            {label:"Hide Without a Target",type:"toggle",key:"hideInactive"}]),
    extraHudModule("inventory","Inventory HUD",{x:40,y:60},'<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 9h18M3 14h18M9 4v16M15 4v16"/>','Inventory<br>▦ ▦ ▦ ▦ ▦ ▦ ▦ ▦ ▦',
        {hotbar:false,title:true,slotBackground:true,counts:true,slotGap:2},[
            {label:"Include Hotbar",type:"toggle",key:"hotbar"},
            {label:"Show Title",type:"toggle",key:"title"},
            {label:"Slot Backgrounds",type:"toggle",key:"slotBackground"},
            {label:"Stack Counts",type:"toggle",key:"counts"},
            {label:"Slot Spacing",type:"slider",key:"slotGap",min:0,max:6,step:1}]),
    extraHudModule("iteminfo","Item Info",{x:40,y:70},'<path d="M5 3h14v18H5zM8 7h8M8 11h8M8 15h5"/>','Diamond Sword x1<br>Durability: 1500/1561',
        {hand:"Main hand",count:true,durability:true,enchantments:true},[
            {label:"Hand",type:"choice",key:"hand",options:["Main hand","Offhand"]},
            {label:"Stack Count",type:"toggle",key:"count"},
            {label:"Durability",type:"toggle",key:"durability"},
            {label:"Enchantments",type:"toggle",key:"enchantments"}]),
    extraHudModule("compass","Compass",{x:40,y:3},'<circle cx="12" cy="12" r="9"/><path d="m16 8-2 6-6 2 2-6z"/>','NW&nbsp;&nbsp; N&nbsp;&nbsp; NE',
        {style:"Bar",degrees:true},[
            {label:"Style",type:"choice",key:"style",options:["Bar","Simple"]},
            {label:"Show Degrees",type:"toggle",key:"degrees"}]),
    extraHudModule("clock","Clock",{x:88,y:10},'<circle cx="12" cy="12" r="9"/><path d="M12 6v6l4 2"/>','12:30',
        {source:"Real time",format:"24-hour",seconds:false},[
            {label:"Time Source",type:"choice",key:"source",options:["Real time","Minecraft"]},
            {label:"Format",type:"choice",key:"format",options:["24-hour","12-hour"]},
            {label:"Show Seconds",type:"toggle",key:"seconds"}]),
    extraHudModule("speedometer","Speedometer",{x:2,y:25},'<path d="M3 18a9 9 0 1 1 18 0M12 14l5-6M7 19h10"/>','4.32 blocks/s',
        {unit:"blocks/s",vertical:false,decimals:2},[
            {label:"Unit",type:"choice",key:"unit",options:["blocks/s","km/h"]},
            {label:"Include Vertical Movement",type:"toggle",key:"vertical"},
            {label:"Decimal Places",type:"slider",key:"decimals",min:0,max:2,step:1}]),
    extraHudModule("resourcepacks","Resource Pack Display",{x:2,y:32},'<path d="m12 3 9 5-9 5-9-5zM3 12l9 5 9-5M3 16l9 5 9-5"/>','Default<br>The default look of Minecraft',
        {textWidth:200,showIcon:true,showName:true,showDescription:true,originalColors:true,iconSize:32,iconPosition:"Left",gap:6,textScale:1,lineSpacing:1,nameLines:2,descriptionLines:4,alignment:"Left",descriptionColor:"#AAAAAAFF"},[
            {label:"Show Pack Icon",type:"toggle",key:"showIcon"},
            {label:"Show Pack Name",type:"toggle",key:"showName"},
            {label:"Show MOTD",type:"toggle",key:"showDescription"},
            {label:"Keep Pack Text Colors",type:"toggle",key:"originalColors"},
            {label:"MOTD Color",type:"color",key:"descriptionColor"},
            {label:"Icon Position",type:"choice",key:"iconPosition",options:["Left","Right","Above"]},
            {label:"Icon Size",type:"slider",key:"iconSize",min:16,max:64,step:2},
            {label:"Icon / Text Gap",type:"slider",key:"gap",min:0,max:20,step:1},
            {label:"Text Alignment",type:"choice",key:"alignment",options:["Left","Center","Right"]},
            {label:"Font Size",type:"slider",key:"textScale",min:.75,max:2,step:.05},
            {label:"Text Width",type:"slider",key:"textWidth",min:100,max:320,step:10},
            {label:"Line Spacing",type:"slider",key:"lineSpacing",min:0,max:6,step:1},
            {label:"Maximum Name Lines",type:"slider",key:"nameLines",min:1,max:4,step:1},
            {label:"Maximum MOTD Lines",type:"slider",key:"descriptionLines",min:1,max:8,step:1}])
);

MODULES.push(
    {id:"hitcolor",name:"Hit Color",cat:"qol",def:false,desc:"Customize the damage flash on players and mobs. Keeps normal textures and white overlays.",preview:'<span class="hp">Damage flash color</span>',
        defaults:{color:"#FF0000FF",strength:.35},settings:[{title:"Style",rows:[{label:"Hit Color",type:"color",key:"color"},{label:"Intensity",type:"slider",key:"strength",min:0,max:1,step:.05}]}]},
    Object.assign(extraHudModule("minimap","Minimap",{x:80,y:5},'',"N<br>Surface map",
        {size:128,range:48,coords:true,directions:true},[
            {label:"Map Size",type:"slider",key:"size",min:80,max:224,step:8},{label:"Radius (blocks)",type:"slider",key:"range",min:16,max:96,step:8},
            {label:"Cardinal Directions",type:"toggle",key:"directions"},{label:"Coordinates",type:"toggle",key:"coords"}]),{desc:"North-up surface map from loaded chunks, with your direction and optional coordinates. Updates in bounded steps."}),
    {id:"itemphysics",name:"Item Physics",cat:"qol",def:false,desc:"Dropped items tumble in the air and rest flat on the ground. Visual only.",preview:'<span class="hp">Dropped item physics</span>',
        defaults:{speed:1,water:true},settings:[{title:"General",rows:[{label:"Rotation Speed",type:"slider",key:"speed",min:0,max:3,step:.1},{label:"Vanilla Floating in Water",type:"toggle",key:"water"}]}]},
    {id:"blockoutline",name:"Block Outline",cat:"qol",def:false,desc:"Customize the selected block outline, color gradient and optional translucent fill.",preview:'<span class="hp">Block outline</span>',
        defaults:{color:"#FFFFFFFF",width:2,gradient:false,gradientColor:"#55AAFFFF",fill:false,fillColor:"#FFFFFF33"},settings:[{title:"Style",rows:[{label:"Outline Color",type:"color",key:"color"},{label:"Line Width",type:"slider",key:"width",min:1,max:4,step:.5},{label:"Color Gradient",type:"toggle",key:"gradient"},{label:"Top Gradient Color",type:"color",key:"gradientColor"},{label:"Block Fill",type:"toggle",key:"fill"},{label:"Fill Color",type:"color",key:"fillColor"}]}]},
    {id:"hitboxes",name:"Hitboxes",cat:"qol",def:false,desc:"Draw customizable, depth-tested entity outlines. Actual collision and attack range stay unchanged.",preview:'<span class="hp">Player / mob / projectile</span>',
        defaults:{players:true,mobs:false,projectiles:true,range:32,width:1.5,playerColor:"#FFFFFFFF",mobColor:"#55FF55FF",projectileColor:"#FFAA00FF"},settings:[{title:"General",rows:[{label:"Players",type:"toggle",key:"players"},{label:"Mobs",type:"toggle",key:"mobs"},{label:"Projectiles",type:"toggle",key:"projectiles"},{label:"Range",type:"slider",key:"range",min:4,max:64,step:1}]},{title:"Style",rows:[{label:"Line Width",type:"slider",key:"width",min:1,max:4,step:.5},{label:"Player Color",type:"color",key:"playerColor"},{label:"Mob Color",type:"color",key:"mobColor"},{label:"Projectile Color",type:"color",key:"projectileColor"}]}]},
    {id:"hurtcamera",name:"Hurt Camera",cat:"qol",def:false,desc:"Adjust the camera roll from taking damage; zero removes it. Keeps the death animation.",preview:'<span class="hp">Damage tilt control</span>',
        defaults:{intensity:0},settings:[{title:"General",rows:[{label:"Damage Tilt (%)",type:"slider",key:"intensity",min:0,max:100,step:5}]}]},
    {id:"glintcolor",name:"Enchantment Glint Color",cat:"qol",def:false,desc:"Recolor the shine on enchanted items and armor using a native shader.",preview:'<span class="hp">Enchantment shine</span>',
        defaults:{color:"#8B5CFFFF",strength:1},settings:[{title:"Style",rows:[{label:"Glint Color",type:"color",key:"color"},{label:"Brightness",type:"slider",key:"strength",min:0,max:2,step:.1}]}]},
    Object.assign(extraHudModule("itemtracker","Item Pickup Tracker",{x:75,y:60},'',"+32 Cobblestone<br>−1 Diamond",
        {duration:4,rows:4,width:180,icons:true,gains:true,losses:true,fade:true,gainColor:"#55FF55FF",lossColor:"#FF5555FF"},[
            {label:"Show Gains",type:"toggle",key:"gains"},{label:"Show Losses",type:"toggle",key:"losses"},{label:"Item Icons",type:"toggle",key:"icons"},{label:"Fade Text",type:"toggle",key:"fade"},
            {label:"Display Time (seconds)",type:"slider",key:"duration",min:1,max:10,step:.5},{label:"Maximum Rows",type:"slider",key:"rows",min:1,max:8,step:1},{label:"Width",type:"slider",key:"width",min:100,max:280,step:10},
            {label:"Gain Color",type:"color",key:"gainColor"},{label:"Loss Color",type:"color",key:"lossColor"}]),{desc:"Recent net inventory gains and losses with native item icons. Hidden when nothing changes."})
);

MODULES.push(
    {id:"skinlayers3d",name:"3D Skin Layers",cat:"qol",def:false,desc:"Extrudes visible skin-overlay pixels on hats, jackets and sleeves. Nearby players only; distant skins keep normal overlays.",preview:'<span class="hp">3D skin overlays</span>',defaults:{range:12,head:true,body:true},settings:[{title:"General",rows:[{label:"Render Distance",type:"slider",key:"range",min:4,max:32,step:1},{label:"Head Layer",type:"toggle",key:"head"},{label:"Body, Sleeves and Pants",type:"toggle",key:"body"}]}]},
    {id:"tntcountdown",name:"TNT Countdown",cat:"qol",def:false,desc:"Shows the client-observed fuse above primed TNT. Server lag can affect actual explosion timing.",preview:'<span class="hp">TNT · 2.5s</span>',defaults:{range:32,scale:1,color:"#FFFFFFFF"},settings:[{title:"General",rows:[{label:"Render Distance",type:"slider",key:"range",min:4,max:64,step:1},{label:"Text Size",type:"slider",key:"scale",min:.5,max:2,step:.1},{label:"Timer Color",type:"color",key:"color"}]}]},
    Object.assign(extraHudModule("foodpreview","Food Preview",{x:60,y:75},'',"Steak<br>Hunger: +8 (8 usable)<br>Saturation: +12.8",{offhand:true,saturation:true},[{label:"Check Offhand",type:"toggle",key:"offhand"},{label:"Show Saturation",type:"toggle",key:"saturation"}]),{desc:"Held food's hunger restoration, usable hunger and saturation. Hidden when you are not holding food."}),
    Object.assign(extraHudModule("bowcharge","Bow Charge Indicator",{x:53,y:53},'',"Bow: 75%",{chargeColor:"#FFCC55FF"},[{label:"Charge Bar Color",type:"color",key:"chargeColor"}]),{desc:"Native bow draw-strength bar beside the crosshair. Green at full charge; hidden when not drawing a bow."}),
    Object.assign(extraHudModule("deathcoords","Death Coordinates",{x:2,y:65},'',"Last death<br>120, 64, -240<br>overworld",{dimension:true,waypoint:true},[{label:"Show Dimension",type:"toggle",key:"dimension"},{label:"Death Waypoint",type:"toggle",key:"waypoint"}]),{desc:"Saves the last death separately for each world or server. Optional waypoint appears in the matching dimension."}),
    Object.assign(extraHudModule("mounthud","Mount HUD",{x:70,y:70},'',"Horse<br>Health: 24 / 30<br>Speed: 7.50 blocks/s<br>Jump: ~3.00 blocks",{health:true,speed:true,jump:true},[{label:"Health",type:"toggle",key:"health"},{label:"Base Speed",type:"toggle",key:"speed"},{label:"Estimated Jump Height",type:"toggle",key:"jump"}]),{desc:"Your ridden horse, donkey, mule or camel's health, speed attribute and estimated full-charge jump. Hidden when not riding."})
);

// Native improvements share the normal saved module settings and profile system.
{
    const map=MODULES.find(m=>m.id==='minimap');
    map.desc='Detailed 128×128 surface map with biome tint, hill relief, water depth and a rotating Bat marker. Loaded chunks only.';
    Object.assign(map.defaults,{biomeColors:true,relief:true,contours:false,grid:false,biome:false,updates:'Balanced'});
    map.settings[0].rows.unshift(
        {label:'Update Budget',type:'choice',key:'updates',options:['Low','Balanced','Fast']},
        {label:'Biome Terrain Colors',type:'toggle',key:'biomeColors'},{label:'Terrain Relief',type:'toggle',key:'relief'},
        {label:'Height Contours',type:'toggle',key:'contours'},{label:'Map Grid',type:'toggle',key:'grid'},{label:'Biome Name',type:'toggle',key:'biome'});
    for(const id of ['foodpreview','bowcharge','deathcoords','mounthud']){
        const m=MODULES.find(m=>m.id===id);m.defaults.icons=true;m.settings[0].rows.unshift({label:'Item Icon',type:'toggle',key:'icons'});
    }
    const extend=(id,defaults,rows)=>{const m=MODULES.find(m=>m.id===id);Object.assign(m.defaults,defaults);m.settings[0].rows.unshift(...rows);};
    extend('foodpreview',{hungerBar:true},[{label:'Hunger Restoration Preview',type:'toggle',key:'hungerBar'}]);
    extend('bowcharge',{ready:true,drawTime:false,barWidth:110},[{label:'Show Ready at Full Draw',type:'toggle',key:'ready'},{label:'Draw Time',type:'toggle',key:'drawTime'},{label:'Bar Width',type:'slider',key:'barWidth',min:90,max:180,step:5}]);
    extend('deathcoords',{distance:true},[{label:'Distance and Direction',type:'toggle',key:'distance'}]);
    extend('mounthud',{currentSpeed:true,healthBar:true,barWidth:110},[{label:'Current Movement Speed',type:'toggle',key:'currentSpeed'},{label:'Health Bar',type:'toggle',key:'healthBar'},{label:'Bar Width',type:'slider',key:'barWidth',min:90,max:180,step:5}]);
    extend('skinlayers3d',{sleeves:true,pants:true,thickness:.45},[{label:'Sleeve Layers',type:'toggle',key:'sleeves'},{label:'Pants Layers',type:'toggle',key:'pants'},{label:'Pixel Depth',type:'slider',key:'thickness',min:.2,max:.8,step:.05}]);
    extend('tntcountdown',{background:true,shadow:true,ticks:false,throughWalls:false,urgentColor:'#FF5555FF'},[{label:'Timer Background',type:'toggle',key:'background'},{label:'Text Shadow',type:'toggle',key:'shadow'},{label:'Show Ticks Instead of Seconds',type:'toggle',key:'ticks'},{label:'Faint Timer Behind Blocks',type:'toggle',key:'throughWalls'},{label:'Last-Second Color',type:'color',key:'urgentColor'}]);
    MODULES.find(m=>m.id==='tntcountdown').nativePreview=true;
}

// Purpose-drawn monochrome module artwork. Static, bundled vectors; no network or animation.

MODULES.push(
 {id:'shulkerpreview',name:'Shulker Preview',cat:'qol',def:false,desc:'Native 27-slot preview of shulker contents. Hold Shift by default.',preview:'Shulker contents',defaults:{shift:true},settings:[{title:'General',rows:[{label:'Require Shift',type:'toggle',key:'shift'}]}]},
 {id:'equipmentcomparison',name:'Equipment Comparison',cat:'qol',def:false,desc:'Compare armor with equipped armor or weapons/tools with your main hand. Base attributes and durability; enchantments excluded.',preview:'Armor: +2',defaults:{shift:true,durability:true},settings:[{title:'General',rows:[{label:'Require Shift',type:'toggle',key:'shift'},{label:'Compare Durability',type:'toggle',key:'durability'}]}]},
 {id:'raincustomizer',name:'Rain Customizer',cat:'qol',def:false,desc:'Adjust rain visibility without changing weather or sounds.',preview:'Rain opacity',defaults:{opacity:40,snow:false},settings:[{title:'General',rows:[{label:'Rain Opacity',type:'slider',key:'opacity',min:0,max:100,step:5},{label:'Apply to Snow',type:'toggle',key:'snow'}]}]},
 Object.assign(extraHudModule('elytrahud','Elytra HUD',{x:70,y:45},'', 'Elytra: Gliding<br>Speed: 28.4 blocks/s',{equipped:false,speed:true,altitude:true,rockets:true,warning:30},[{label:'Show While Equipped',type:'toggle',key:'equipped'},{label:'Show Speed',type:'toggle',key:'speed'},{label:'Show Altitude',type:'toggle',key:'altitude'},{label:'Show Rocket Count',type:'toggle',key:'rockets'},{label:'Low Durability Warning',type:'slider',key:'warning',min:1,max:100,step:1}]),{desc:'Flight speed, altitude, durability and rockets. Hidden unless gliding by default.'}),
 Object.assign(extraHudModule('shieldindicator','Shield Indicator',{x:70,y:60},'', 'Shield: Disabled<br>Cooldown: 50%',{},[]),{desc:'Shield readiness, raising, blocking and remaining cooldown percentage. Hidden without a held shield.'})
);
function matchingSettings(m,q){return (m.settings||[]).flatMap(sec=>sec.rows.filter(r=>(sec.title+' '+r.label).toLowerCase().includes(q)).map(r=>({key:r.key,label:r.label})));}
function moduleSearchMatches(m,q){return !q||(m.name+' '+(m.desc||'')).toLowerCase().includes(q)||matchingSettings(m,q).length>0;}
function detectedModuleNotice(id){try{if(typeof window.batGetModuleConflicts==='function')return JSON.parse(window.batGetModuleConflicts()||'{}')[id]||'';}catch(e){}return '';}

for(const [id,defaults,rows] of [
 ['elytrahud',{durabilityBar:true,flightTime:true,rocketWarning:8},[{label:'Durability Bar',type:'toggle',key:'durabilityBar'},{label:'Flight Time',type:'toggle',key:'flightTime'},{label:'Low Rocket Warning',type:'slider',key:'rocketWarning',min:0,max:64,step:1}]],
 ['shieldindicator',{cooldownRing:true,readyPulse:true},[{label:'Cooldown Ring',type:'toggle',key:'cooldownRing'},{label:'Ready Pulse',type:'toggle',key:'readyPulse'}]]
]){const m=MODULES.find(m=>m.id===id);Object.assign(m.defaults,defaults);m.settings[0].rows.unshift(...rows);}
MODULES.push({id:'waypoints',name:'Waypoints',cat:'qol',def:false,desc:'Save named locations separately for each world/server and dimension. Press N to create one; manage groups, colors and tracking in the Waypoints tab.',preview:'Base · 128 m',defaults:{scale:1,range:10000,markers:12,ring:true,glow:true,beam:false,beamHeight:128,border:true,padding:4,death:true,chat:true,compass:true,serverMarkers:true,throughWalls:true,coordinates:false,background:true,shadow:true,navigation:true,navigationY:14,minimap:true,arrival:true,arrivalRadius:5},settings:[{title:'Markers',rows:[{label:'Marker Scale',type:'slider',key:'scale',min:.6,max:1.8,step:.1},{label:'Maximum Visible Markers',type:'slider',key:'markers',min:1,max:24,step:1},{label:'Marker Range',type:'slider',key:'range',min:64,max:30000,step:1},{label:'Destination Ring',type:'toggle',key:'ring'},{label:'Marker Glow',type:'toggle',key:'glow'},{label:'Beacon Beams',type:'toggle',key:'beam'},{label:'Beam Height',type:'slider',key:'beamHeight',min:16,max:256,step:16},{label:'Label Border',type:'toggle',key:'border'},{label:'Label Padding',type:'slider',key:'padding',min:0,max:10,step:1},{label:'Visible Behind Terrain',type:'toggle',key:'throughWalls'},{label:'Show Coordinates',type:'toggle',key:'coordinates'},{label:'Background',type:'toggle',key:'background'},{label:'Text Shadow',type:'toggle',key:'shadow'}]},{title:'Navigation',rows:[{label:'Tracked Waypoint Guide',type:'toggle',key:'navigation'},{label:'Guide Vertical Position',type:'slider',key:'navigationY',min:3,max:90,step:1},{label:'Show on Minimap',type:'toggle',key:'minimap'},{label:'Show on Compass',type:'toggle',key:'compass'},{label:'Save Death Waypoints',type:'toggle',key:'death'},{label:'Add From Chat',type:'toggle',key:'chat'},{label:'Show Server Waypoints',type:'toggle',key:'serverMarkers'},{label:'Arrival Notification',type:'toggle',key:'arrival'},{label:'Arrival Radius',type:'slider',key:'arrivalRadius',min:1,max:32,step:1}]}]});
MODULES.push(
 {id:'freelook',name:'Freelook',cat:'qol',def:false,desc:'Hold Left Alt to orbit the camera without turning your player. Rebind Freelook in Keybinds or Minecraft Controls. Releasing restores your previous perspective.',preview:'Camera orbit · Left Alt',defaults:{mode:'Hold',sensitivity:100},settings:[{title:'Camera',rows:[{label:'Freelook Key',type:'binding',key:'binding',default:'key.keyboard.left.alt'},{label:'Activation',type:'choice',key:'mode',options:['Hold','Toggle']},{label:'Look Sensitivity (%)',type:'slider',key:'sensitivity',min:10,max:200,step:5}]}]},
 {id:'lootbeams',name:'Loot Beams',cat:'qol',def:false,desc:'Colored beams above nearby dropped items. Only loaded items, normal terrain visibility, and a bounded number of beams.',preview:'Dropped item beams',defaults:{filter:'All',range:24,limit:32,minimum:1,height:2,width:.12,opacity:65,rarityColors:true,color:'#75CFFFFF',groundGlow:true,pulse:true,labels:false},settings:[{title:'Drops',rows:[{label:'Item Filter',type:'choice',key:'filter',options:['All','Uncommon+','Valuables']},{label:'Minimum Stack Count',type:'slider',key:'minimum',min:1,max:64,step:1},{label:'Render Range',type:'slider',key:'range',min:8,max:64,step:1},{label:'Maximum Beams',type:'slider',key:'limit',min:8,max:64,step:1}]},{title:'Appearance',rows:[{label:'Rarity Colors',type:'toggle',key:'rarityColors'},{label:'Custom Color (Rarity Colors off)',type:'color',key:'color'},{label:'Beam Height',type:'slider',key:'height',min:.5,max:6,step:.25},{label:'Beam Width',type:'slider',key:'width',min:.06,max:.4,step:.02},{label:'Opacity (%)',type:'slider',key:'opacity',min:10,max:100,step:5},{label:'Ground Glow',type:'toggle',key:'groundGlow'},{label:'Soft Pulse',type:'toggle',key:'pulse'},{label:'Item Names and Counts',type:'toggle',key:'labels'}]}]}
);

MODULES.push({id:'returntrail',name:'Return Trail',cat:'qol',def:false,desc:'Temporary breadcrumbs of your own route. Arrows point back along the path you actually traveled; trails stay local to the session and dimension.',preview:'Your path back',defaults:{minutes:10,points:512,spacing:1,range:48,width:2,color:'#75CFFFFF',opacity:80,style:'Both',groundOnly:false,clearOnDeath:true,guide:true},settings:[{title:'Route',rows:[{label:'Trail Lifetime (minutes)',type:'slider',key:'minutes',min:1,max:30,step:1},{label:'Maximum Breadcrumbs',type:'slider',key:'points',min:64,max:1024,step:32},{label:'Sample Spacing (blocks)',type:'slider',key:'spacing',min:.5,max:4,step:.25},{label:'Only Record On Ground',type:'toggle',key:'groundOnly'},{label:'Clear Current Trail On Death',type:'toggle',key:'clearOnDeath'}]},{title:'Appearance',rows:[{label:'Trail Style',type:'choice',key:'style',options:['Arrows','Line','Both']},{label:'Trail Color',type:'color',key:'color'},{label:'Opacity (%)',type:'slider',key:'opacity',min:10,max:100,step:5},{label:'Line Width',type:'slider',key:'width',min:1,max:4,step:.5},{label:'Visible Range',type:'slider',key:'range',min:8,max:128,step:4},{label:'Retrace Direction Guide',type:'toggle',key:'guide'}]}]});

const MODULE_ART = (() => {
    const text=(value,x=32,y=31,size=22)=>'<text x="'+x+'" y="'+y+'" text-anchor="middle" fill="currentColor" font-family="Arial,sans-serif" font-size="'+size+'" font-weight="800" letter-spacing="-1">'+value+'</text>';
    const line=paths=>'<g fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">'+paths+'</g>';
    const shirt='<path d="M20 8h8l4 6 4-6h8l12 10-7 9-7-5v19H22V22l-7 5-7-9z" fill="currentColor"/><path d="M27 23v12m6-16v16m6-12v12" stroke="#000" stroke-opacity=".22" stroke-width="2"/>';
    const cube='<path d="m32 5 23 12-23 13L9 17z" fill="currentColor"/><path d="m9 20 21 12v13L9 33z" fill="currentColor" opacity=".55"/><path d="m34 32 21-12v13L34 45z" fill="currentColor" opacity=".8"/><path d="m20 11 23 12m-1-12L20 23" stroke="#000" stroke-width="2" stroke-opacity=".18"/>';
    const arrow='<path d="m13 39 33-30" stroke="currentColor" stroke-width="5"/><path d="m38 5 19-1-3 18-6-8zM8 31l10 2 2 10-11 1 1-5-5-1z" fill="currentColor"/><path d="m20 32 9-8" stroke="#000" stroke-width="1.5" opacity=".35"/>';
    const sword='<path d="M44 2h14v14L30 36l-8-8zM12 24l21 21-5 3L8 29zM17 35l7 7-9 6-6-6z" fill="currentColor"/><path d="m27 29 24-21" stroke="#000" stroke-width="2" opacity=".3"/>';
    return {
        returntrail:line('<path d="M7 51c28 2 1-28 26-24s28-20 17-22M8 40l-1 11 11 1M28 18l5 9-9 5"/><circle cx="50" cy="5" r="3"/>'),
        freelook:line('<circle cx="32" cy="25" r="9"/><path d="M5 25c8-20 46-20 54 0-8 20-46 20-54 0M11 48h42m-35-6-7 6 7 6m28-12 7 6-7 6"/>'),
        lootbeams:line('<path d="M23 5h18l5 34-14 7-14-7zM12 48l20 10 20-10M23 5l9 6 9-6M32 11v35"/>'),
        waypoints:line('<path d="M32 46S13 28 13 18a19 19 0 1 1 38 0c0 10-19 28-19 28z"/><circle cx="32" cy="18" r="7"/><path d="M8 37 3 45l16-3m27 0 15 3-5-8"/>'),
        shulkerpreview:cube+line('<path d="M5 35h16m-8-8v16"/>'),
        equipmentcomparison:sword+line('<path d="m3 10 8-7 8 7M11 3v18"/>'),
        raincustomizer:line('<path d="M16 25a10 10 0 1 1 2-20 14 14 0 0 1 26 4 9 9 0 0 1 3 18H16m2 6-4 9m18-9-4 9m18-9-4 9"/>'),
        elytrahud:line('<path d="M30 8 8 3l-3 26 21 15 4-36m4 0 22-5 3 26-21 15-4-36M12 10l11 22m29-22L41 32"/>'),
        shieldindicator:line('<path d="m32 4 23 8v14c0 11-23 20-23 20S9 37 9 26V12zM22 25l7 7 15-16"/>'),
        skinlayers3d:cube+line('<path d="m5 32 23 14 30-17M5 39l23 14 30-17"/>'),
        foodpreview:line('<path d="M22 13c-18-9-24 11-13 24 7 10 14 10 20 5 7 6 16 2 22-10 9-18-7-28-20-19M30 15V6m0 3c8-8 17-6 17-6-1 9-8 11-17 9"/>'),
        tntcountdown:line('<rect x="8" y="13" width="46" height="34" rx="3"/><path d="M8 24h46M8 37h46M32 13V7l7-4"/>')+text('TNT',31,34,15),
        bowcharge:line('<path d="M16 4c33 8 33 32 0 43l10-22zM26 25h29m-8-7 9 7-9 7"/>'),
        deathcoords:line('<path d="M32 49S10 29 10 19a22 22 0 0 1 44 0c0 10-22 30-22 30z"/><circle cx="32" cy="20" r="9"/><path d="m28 16 8 8m0-8-8 8"/>'),
        mounthud:line('<path d="m17 45 1-19-8-5 6-14 12 2 8-5 3 15 15 9-3 17M20 22l7 5 11-8M27 27l-1 18M16 7l8 8"/>')+'<circle cx="23" cy="18" r="2" fill="currentColor"/>',
        minimap:line('<path d="m3 10 19-6 20 8 19-6v33l-19 6-20-8-19 6zM22 4v33M42 12v33"/>')+'<path d="m32 16-9 18 9-4 9 4z" fill="currentColor"/>',
        hitcolor:line('<path d="m33 2-5 14L13 7l6 15-16 5 17 5-7 13 17-8 6 10 4-14 18 4-11-12 12-11-18 2z"/>')+'<circle cx="33" cy="25" r="7" fill="currentColor"/>',
        itemphysics:line('<path d="m16 27 19-9 16 11-19 10zM16 27v10l16 9 19-9V29M32 39v7M9 13l8-5M5 23l8-4M31 5l5 7M46 7l-1 9"/>'),
        itemtracker:line('<rect x="9" y="9" width="30" height="30" rx="4"/><path d="M16 18h16M16 26h10M44 13h16M52 5v16M44 35h16"/>'),
        blockoutline:line('<path d="M10 12 32 2l22 10v25L32 47 10 37zM10 12l22 11 22-11M32 23v24"/>'),
        hitboxes:line('<rect x="17" y="3" width="30" height="43"/><circle cx="32" cy="13" r="6"/><path d="M32 19v15M22 25h20M32 34l-8 9m8-9 8 9"/>'),
        hurtcamera:line('<rect x="7" y="8" width="50" height="32" rx="4"/><path d="m16 25 8-8 9 15 8-12 8 5M27 45h10"/>'),
        glintcolor:line('<path d="m31 3 7 14 16 7-16 7-7 14-7-14-16-7 16-7zM51 3v10m-5-5h10"/>'),
        fps:text('FPS',32,29,23)+'<path d="M7 39h9v-6H7zm14 0h9V29h-9zm14 0h9V24h-9zm14 0h9V17h-9z" fill="currentColor" opacity=".55"/>',
        coords:text('XYZ',32,28,23)+line('<path d="M12 37h40m-40-3v6m20-6v6m20-6v6"/>'),
        ping:line('<path d="M8 14a37 37 0 0 1 48 0M16 24a25 25 0 0 1 32 0M24 33a13 13 0 0 1 16 0"/>')+'<circle cx="32" cy="41" r="4" fill="currentColor"/>',
        cps:'<rect x="18" y="3" width="28" height="42" rx="13" fill="currentColor"/><path d="M19 19h26M32 4v15" stroke="#000" stroke-width="2"/><rect x="30" y="8" width="4" height="8" rx="2" fill="#000"/>'+text('2',32,37,16),
        keystrokes:line('<rect x="24" y="2" width="17" height="17" rx="3"/><rect x="4" y="22" width="17" height="17" rx="3"/><rect x="24" y="22" width="17" height="17" rx="3"/><rect x="44" y="22" width="17" height="17" rx="3"/><path d="M7 46h51"/>')+text('W',32,15,11)+text('A',12,35,11)+text('S',32,35,11)+text('D',52,35,11),
        potions:'<path d="M24 3h16v6h-3v10l12 17a6 6 0 0 1-5 9H20a6 6 0 0 1-5-9l12-17V9h-3z" fill="currentColor" opacity=".55"/><path d="M21 30h22l5 9-4 5H20l-4-5z" fill="currentColor"/><path d="M28 12v8l-7 10" fill="none" stroke="currentColor" stroke-width="3"/><circle cx="29" cy="36" r="2" fill="#000" opacity=".4"/><circle cx="37" cy="40" r="1.5" fill="#000" opacity=".4"/>',
        armor:shirt,
        durability:shirt+'<rect x="20" y="36" width="25" height="11" rx="3" fill="#000"/>'+text('15%',32,45,10),
        crosshair:line('<path d="M32 3v13m0 16v13M10 24h13m18 0h13"/>')+'<rect x="29" y="21" width="6" height="6" fill="currentColor"/>',
        blockindicator:cube,
        target:line('<rect x="2" y="5" width="60" height="38" rx="4"/>')+'<circle cx="15" cy="17" r="5" fill="currentColor"/><path d="M7 31v-4a8 8 0 0 1 16 0v4z" fill="currentColor"/><path d="M30 13h23v4H30zm0 9h17v3H30zM8 35h47v3H8z" fill="currentColor"/>',
        healthindicator:'<path d="M32 29 15 15C4 4 19-5 32 8 45-5 60 4 49 15z" fill="currentColor"/>'+line('<path d="M17 37h30M23 44h18"/>'),
        scoreboard:line('<path d="M12 6h40v36H12zM19 16h4m6 0h16M19 25h4m6 0h16M19 34h4m6 0h11"/>'),
        bossbar:text('BOSS',32,20,17)+'<rect x="4" y="29" width="56" height="9" rx="3" fill="currentColor" opacity=".25"/><rect x="4" y="29" width="42" height="9" rx="3" fill="currentColor"/>',
        zoom:line('<circle cx="27" cy="20" r="16"/><path d="m39 32 14 13M19 20h16m-8-8v16"/>'),
        perspective:line('<path d="M3 24S14 9 32 9s29 15 29 15-11 15-29 15S3 24 3 24z"/>')+'<circle cx="32" cy="24" r="9" fill="currentColor"/><circle cx="35" cy="21" r="3" fill="#000"/>',
        fullbright:'<circle cx="32" cy="24" r="11" fill="currentColor"/>'+line('<path d="M32 2v5m0 34v5M10 24H5m49 0h5M16 8l4 4m24 24 4 4M16 40l4-4M44 12l4-4"/>'),
        lowfire:'<path d="M33 2c4 13 17 15 17 29a18 18 0 0 1-36 0c0-8 4-14 10-19-1 9 2 13 6 15 5-7 2-15 3-25z" fill="currentColor"/><path d="M32 27c4 5 8 7 8 12a8 8 0 0 1-16 0c0-4 4-7 8-12z" fill="#000" opacity=".55"/>',
        animations:'<path d="m15 7 35 17-35 17z" fill="currentColor"/>'+line('<path d="M5 14v20M57 14v20"/>'),
        togglesprint:'<circle cx="37" cy="7" r="6" fill="currentColor"/>'+line('<path d="m23 26 7-12 12 8 12 1M30 17l-6-3-8 10m16 0 9 9 5 12M31 29l-7 10-12 5M3 12h12M2 20h9"/>'),
        nametags:line('<rect x="2" y="10" width="60" height="28" rx="4"/>')+'<path d="m10 20 5-5 5 5-2 9H12z" fill="currentColor"/>'+text('BAT',40,29,17),
        entityculling:cube+'<path d="M8 44 56 4" stroke="#000" stroke-width="7"/><path d="M8 44 56 4" stroke="currentColor" stroke-width="3"/>',
        particlereduce:'<path d="m18 2 3 9 9 3-9 3-3 9-3-9-9-3 9-3zm28 17 3 7 7 3-7 3-3 7-3-7-7-3 7-3zM20 34h8v8h-8zM48 5h5v5h-5z" fill="currentColor"/>',
        dynamicfps:'<path d="M35 1 12 28h17l-5 19 28-29H35z" fill="currentColor"/>',
        motionblur:line('<path d="M3 10h17M2 24h12M3 38h17"/><circle cx="40" cy="24" r="17"/>')+'<path d="M40 7a17 17 0 0 0 0 34z" fill="currentColor" opacity=".5"/>',
        totems:'<path d="M23 2h18v5h5v15h-6v7h4v17H20V29h4v-7h-6V7h5z" fill="currentColor"/><path d="M6 17h12v8l9 5-4 7L9 29H3V19h3zm52 0H46v8l-9 5 4 7 14-8h6V19h-3z" fill="currentColor" opacity=".6"/><path d="M25 11h5v5h-5zm10 0h5v5h-5zM29 20h7v3h-7zM29 31h6v10h-6z" fill="#000" opacity=".65"/>',
        arrows:arrow,
        combo:line('<path d="m12 38 32-32 9 9-32 32zM8 29l18 18M38 3l19 19"/>')+text('3',14,20,18),
        reach:line('<path d="M5 24h54M5 15v18m54-18v18M5 24l8-6m-8 6 8 6m46-6-8-6m8 6-8 6"/>')+text('2.85',32,13,13),
        inventoryanimations:'<rect x="15" y="9" width="44" height="34" rx="4" fill="currentColor" opacity=".12"/>'+line('<rect x="15" y="9" width="44" height="34" rx="4"/><path d="M15 20h44M15 31h44M26 9v34M37 9v34M48 9v34M3 17h6M1 27h8M3 37h6"/>')+'<path d="M29 5h15l-7.5-5z" fill="currentColor"/><rect x="29" y="23" width="5" height="5" rx="1" fill="currentColor"/><rect x="40" y="34" width="5" height="5" rx="1" fill="currentColor" opacity=".6"/>',
        inventory:line('<rect x="3" y="5" width="58" height="38" rx="3"/><path d="M3 18h58M3 30h58M18 5v38M32 5v38M46 5v38"/>')+'<rect x="6" y="8" width="9" height="7" fill="currentColor" opacity=".55"/><rect x="35" y="21" width="8" height="6" fill="currentColor" opacity=".7"/>',
        iteminfo:sword+'<rect x="33" y="29" width="29" height="17" rx="3" fill="#000"/>'+text('x1',47,42,12),
        compass:line('<circle cx="32" cy="25" r="19"/>')+'<path d="m39 13-3 17-13 8 4-17z" fill="currentColor"/><path d="m32 25-9 13 4-17z" fill="#000" opacity=".45"/>'+text('N',32,10,9),
        clock:line('<circle cx="32" cy="24" r="20"/><path d="M32 10v15l11 6"/>')+'<circle cx="32" cy="24" r="3" fill="currentColor"/>',
        speedometer:line('<path d="M9 39a25 25 0 1 1 46 0M32 29l15-16M13 24l5 2m5-15 2 5m17-5-2 5M51 24l-5 2"/>')+'<circle cx="32" cy="29" r="4" fill="currentColor"/>'+text('4.32',32,45,12),
        resourcepacks:'<path d="M6 10h21l6 6h25v27H6z" fill="currentColor" opacity=".45"/>'+cube,
    };
})();
const MOD_PNG_MAP={fps:[0,0],coords:[1,0],ping:[2,0],cps:[3,0],keystrokes:[4,0],potions:[5,0],armor:[6,0],durability:[7,0],crosshair:[0,1],blockindicator:[1,1],target:[2,1],scoreboard:[3,1],bossbar:[4,1],zoom:[5,1],perspective:[6,1],fullbright:[7,1],lowfire:[0,2],animations:[1,2],togglesprint:[2,2],nametags:[3,2],entityculling:[4,2],particlereduce:[5,2],dynamicfps:[6,2],inventoryanimations:[7,2],healthindicator:[0,3],motionblur:[1,3],hitcolor:[2,3],itemphysics:[3,3],blockoutline:[4,3],hitboxes:[5,3],hurtcamera:[6,3],glintcolor:[7,3],skinlayers3d:[0,4],tntcountdown:[1,4],shulkerpreview:[2,4],equipmentcomparison:[3,4],raincustomizer:[4,4],waypoints:[5,4],freelook:[6,4],lootbeams:[7,4],returntrail:[0,5],combo:[1,5],reach:[2,5],inventory:[3,5],iteminfo:[4,5],compass:[5,5],clock:[6,5],speedometer:[7,5],resourcepacks:[0,6],minimap:[1,6],itemtracker:[2,6],foodpreview:[3,6],bowcharge:[4,6],deathcoords:[5,6],mounthud:[6,6],elytrahud:[7,6],shieldindicator:[0,7],totems:[1,7],arrows:[2,7],_default:[3,7]};
MODULES.forEach(module => {
    const pos = MOD_PNG_MAP[module.id] || MOD_PNG_MAP._default;
    const bx = (pos[0] / 7 * 100).toFixed(4) + '%';
    const by = (pos[1] / 7 * 100).toFixed(4) + '%';
    module.icon = '<span class="mod-png-icon" style="background-position:' + bx + ' ' + by + '" aria-hidden="true"></span>';
});

// ---- persistent module store: config/batclient-hud.json via the Java bridge ----
// Single source of truth shared with the in-game HudRenderer/ClientMods.
// localStorage only as a fallback (main menu without a world still has the
// bridge, but keep the data readable if the binding is ever missing).
let CFG = null;
function cfgLoad() {
    let raw = null;
    if (typeof window.batGetHudConfig === "function") {
        try { raw = window.batGetHudConfig(); } catch (e) { raw = null; }
    }
    if (!raw) {
        try { raw = localStorage.getItem("bat.hudcfg"); } catch (e) { raw = null; }
    }
    try { CFG = raw ? JSON.parse(raw) : null; } catch (e) { CFG = null; }
    if (!CFG || typeof CFG !== "object") CFG = {};
    if (!CFG.modules || typeof CFG.modules !== "object") CFG.modules = {};
}
function cfgSave(moduleId, patch) {
    if(moduleId&&typeof hudRecordLayout==='function'&&document.body.classList.contains('hud-edit'))hudRecordLayout();
    let raw = JSON.stringify(CFG);
    if (typeof window.batSaveHudModule === "function") {
        try {
            if (moduleId) {
                const update = patch ? Object.assign({ _initial: CFG.modules[moduleId] }, patch) : CFG.modules[moduleId];
                const stored = window.batSaveHudModule(moduleId, JSON.stringify(update));
                if (!stored) throw Error("Module save failed");
                CFG = JSON.parse(stored); raw = stored;
            } else {
                // Flush the current native state, not a cached page's stale snapshot.
                raw = window.batGetHudConfig(); CFG = JSON.parse(raw);
                window.batSaveHudConfig(raw);
            }
            localStorage.setItem("bat.hudcfg", raw);
            return;
        } catch (e) { console.warn("[bat] could not save module settings: " + e); return; }
    }
    if (typeof window.batSaveHudConfig === "function") {
        try { window.batSaveHudConfig(raw); } catch (e) { /* ignore */ }
    }
    try { localStorage.setItem("bat.hudcfg", raw); } catch (e) { /* ignore */ }
}
cfgLoad();

function modDef(id) {
    return MODULES.find(m => m.id === id);
}
function modState(id) {
    const m = modDef(id);
    if (!m) return { en: false, x: 4, y: 10, scale: 1, set: {} };
    let st = CFG.modules[id];
    if (!st || typeof st !== "object") {
        st = {};
        CFG.modules[id] = st;
    }
    if (typeof st.en !== "boolean") st.en = !!m.def;
    if (!st.set || typeof st.set !== "object") st.set = {};
    const d = m.defaults || {};
    for (const k in d) if (st.set[k] === undefined) st.set[k] = d[k];
    if (/^#(?:08080BC7|000000A6)$/i.test(st.set.bgColor || '')) st.set.bgColor = '#0000006B';
    if (!Number.isFinite(st.scale)) {
        st.scale = (typeof st.set.scale === "number") ? st.set.scale : 1;
    }
    st.scale = Math.max(0.5, Math.min(3, st.scale));
    st.set.scale = st.scale;
    const dp = m.defPos || { x: 4, y: 10 };
    const oldPositions = { fps: [2,6], cps: [2,16], coords: [2,26], ping: [86,6],
        keystrokes: [3,64], potions: [2,44], armor: [82,58], togglesprint: [2,36] };
    const old = oldPositions[id];
    if (!st.anchor && old && st.x === old[0] && st.y === old[1]) {
        st.x = dp.x;
        st.y = dp.y;
    }
    if (typeof st.x !== "number") st.x = dp.x;
    if (typeof st.y !== "number") st.y = dp.y;
    if (st.anchor || st.offset_x !== undefined || st.offset_y !== undefined) {
        const anchors = { TOP_LEFT: [0, 0], TOP_CENTER: [0.5, 0], TOP_RIGHT: [1, 0],
            CENTER_LEFT: [0, 0.5], CENTER: [0.5, 0.5], CENTER_RIGHT: [1, 0.5],
            BOTTOM_LEFT: [0, 1], BOTTOM_CENTER: [0.5, 1], BOTTOM_RIGHT: [1, 1] };
        const a = anchors[st.anchor] || anchors.TOP_LEFT;
        st.x = a[0] * 100 + (Number(st.offset_x) || 0) / window.innerWidth * 100;
        st.y = a[1] * 100 + (Number(st.offset_y) || 0) / window.innerHeight * 100;
        delete st.anchor;
        delete st.offset_x;
        delete st.offset_y;
    }
    return st;
}
function saveModState(id, st) {
    st.set.scale = st.scale;
    // Web moves use screen percentages. Remove stale native-editor offsets.
    delete st.anchor;
    delete st.offset_x;
    delete st.offset_y;
    CFG.modules[id] = st;
    cfgSave(id);
}
function saveHudLayout(id, st) {
    st.set.scale = st.scale;
    CFG.modules[id] = st;
    cfgSave(id, { x: st.x, y: st.y, scale: st.scale, locked:!!st.locked, set: { scale: st.scale } });
}
function setModEnabled(id, on) {
    const st = modState(id);
    st.en = !!on;
    cfgSave(id, { en: !!on });
    const grid = document.getElementById("mod-grid");
    const card = grid && grid.querySelector('.mod-card[data-id="' + id + '"]');
    if (card && !mmQuery && !mmFavOnly) {
        card.classList.toggle("off", !st.en);
        const btn = card.querySelector(".mod-toggle:not(.act)");
        if (btn) {
            btn.classList.toggle("on", st.en);
            btn.textContent = st.en ? "Enabled" : "Disabled";
        }
    } else {
        renderModGrid();
    }
    const def = modDef(id);
    if (def && def.widget) renderHudWidgets();
}

// ---- module grid (essential set only) ----
let mmCat = "all";
let mmQuery = "";
let mmFavOnly = false;

const HEART_SVG = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 21s-7.5-4.7-9.5-9A5.4 5.4 0 0 1 12 6.6 5.4 5.4 0 0 1 21.5 12c-2 4.3-9.5 9-9.5 9z"/></svg>';
const HEART_FILL_SVG = '<svg viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"><path d="M12 21s-7.5-4.7-9.5-9A5.4 5.4 0 0 1 12 6.6 5.4 5.4 0 0 1 21.5 12c-2 4.3-9.5 9-9.5 9z"/></svg>';

function renderModGrid() {
    const grid = document.getElementById("mod-grid");
    if (!grid) return;
    grid.innerHTML = "";
    const frag = document.createDocumentFragment();
    MODULES
        .filter(m => (mmCat === "all" || m.cat === mmCat) &&
            moduleSearchMatches(m,mmQuery) &&
            (!mmFavOnly || modState(m.id).fav))
        .sort((a, b) => a.name.localeCompare(b.name))
        .forEach(m => {
            const st = modState(m.id);
            const card = document.createElement("div");
            card.className = "mod-card" + (st.en ? "" : " off") + (st.fav ? " fav" : "");
            card.dataset.id = m.id;
            card.innerHTML =
                '<div class="mod-card-top">' +
                '<div class="mod-card-name">' + m.name + '</div>' +
                '<button class="mod-fav" title="Favorite">' + (st.fav ? HEART_FILL_SVG : HEART_SVG) + '</button>' +
                '</div>' +
                '<div class="mod-card-icon">' + m.icon + '</div>' +
                '<div class="mod-card-foot">' +
                (m.settings && m.settings.length
                    ? '<button class="mod-gear" title="Settings">' + GEAR_SVG + '</button>'
                    : '<span class="mod-card-pad"></span>') +
                (m.action
                    ? '<button class="mod-toggle act">Open Editor</button>'
                    : '<button class="mod-toggle' + (st.en ? " on" : "") + '">' + (st.en ? "Enabled" : "Disabled") + '</button>') +
                '</div>';
            card.querySelector(".mod-fav").addEventListener("click", e => {
                e.stopPropagation();
                const s2 = modState(m.id);
                s2.fav = !s2.fav;
                saveModState(m.id, s2);
                if (!mmFavOnly) {
                    card.classList.toggle("fav", s2.fav);
                    card.querySelector(".mod-fav").innerHTML = s2.fav ? HEART_FILL_SVG : HEART_SVG;
                } else {
                    renderModGrid();
                }
            });
            const toggleBtn = card.querySelector(".mod-toggle");
            if (m.action) {
                toggleBtn.addEventListener("click", () => {
                    if (m.action === "hudeditor") { closePanel(); toggleHudEditor(true); }
                });
            } else {
                toggleBtn.addEventListener("click", () => {
                    setModEnabled(m.id, !modState(m.id).en);
                });
            }
            const gear = card.querySelector(".mod-gear");
            if (gear) gear.addEventListener("click", () => openModSettings(m.id));
            if(mmQuery)matchingSettings(m,mmQuery).slice(0,3).forEach(match=>{
                const result=document.createElement('button');result.className='setting-search-result';result.textContent=match.label;
                result.addEventListener('click',()=>{openModSettings(m.id);const ctl=document.querySelector('#ms-sections .ms-ctl[data-key="'+match.key+'"]');if(ctl){const row=ctl.closest('.ms-row');row.classList.add('search-highlight');row.scrollIntoView({block:'center',behavior:'smooth'});}});card.appendChild(result);
            });
            frag.appendChild(card);
        });
    grid.appendChild(frag);
}

document.querySelectorAll(".mm-subtab").forEach(tab => {
    tab.addEventListener("click", () => {
        mmCat = tab.dataset.cat;
        document.querySelectorAll(".mm-subtab").forEach(x => x.classList.toggle("active", x === tab));
        renderModGrid();
    });
});
const mmSearchEl = document.getElementById("mm-search");
if (mmSearchEl) {
    mmSearchEl.placeholder="Search modules or settings";
    mmSearchEl.addEventListener("input", () => {
        mmQuery = mmSearchEl.value.trim().toLowerCase();
        renderModGrid();
    });
}
const mmFavEl = document.getElementById("mm-fav");
if (mmFavEl) {
    mmFavEl.addEventListener("click", () => {
        mmFavOnly = !mmFavOnly;
        mmFavEl.classList.toggle("active", mmFavOnly);
        renderModGrid();
    });
}
const mmGridBtnEl = document.getElementById("mm-gridbtn");
if (mmGridBtnEl) {
    const applyDense = on => {
        document.getElementById("mod-grid").classList.toggle("dense", on);
        mmGridBtnEl.classList.toggle("active", on);
    };
    let dense = false;
    try { dense = localStorage.getItem("bat.mmdense") === "1"; } catch (e) { /* ignore */ }
    applyDense(dense);
    mmGridBtnEl.addEventListener("click", () => {
        dense = !dense;
        try { localStorage.setItem("bat.mmdense", dense ? "1" : "0"); } catch (e) { /* ignore */ }
        applyDense(dense);
    });
}

// ---- per-module settings page ----
let msMod = null;

function openModSettings(id) {
    const m = modDef(id);
    if (!m || !m.settings || !m.settings.length) return;
    openPanel("mods");
    msMod = m;
    settingsChangedOnly=false;
    const sub = document.querySelector("#view-mods .mm-sub");
    const grid = document.getElementById("mod-grid");
    const ms = document.getElementById("mod-settings");
    if (!ms) return;
    if (sub) sub.classList.add("hidden");
    if (grid) grid.classList.add("hidden");
    ms.classList.remove("hidden");
    document.getElementById("headline").textContent = m.name.toUpperCase();
    document.getElementById("ms-desc").textContent = m.desc;
    const notice=detectedModuleNotice(id);if(notice){const n=document.createElement('div');n.className='module-conflict-notice';n.textContent=notice;document.getElementById('ms-desc').appendChild(n);}
    if(id==='returntrail'){
        const tools=document.createElement('div');tools.className='trail-actions';const status=document.createElement('span');status.className='trail-status';tools.appendChild(status);
        const request=action=>{try{if(typeof window.batReturnTrail!=='function')throw Error('Trail actions are available inside Minecraft.');const value=JSON.parse(window.batReturnTrail(action));if(value.error)throw Error(value.error);status.textContent=value.returning?'Retracing · '+value.remaining+' points left':value.points+' breadcrumbs · '+(value.recording?'Recording':'Module off');}catch(e){status.textContent=e.message;}};
        [['Retrace route','retrace'],['Resume recording','resume'],['Clear current trail','clear']].forEach(([label,action])=>{const button=document.createElement('button');button.className='bat-tool-button';button.textContent=label;button.onclick=()=>request(action);tools.appendChild(button);});document.getElementById('ms-desc').appendChild(tools);request('status');
    }
    if(id==='waypoints'&&typeof openWaypointManager==='function'){const manage=document.createElement('button');manage.className='setting-search-result';manage.textContent='Manage Waypoints';manage.onclick=()=>openWaypointManager(false);document.getElementById('ms-desc').appendChild(manage);}
    // reset the Move button (only widget modules can be dragged) + show preview again
    const mvBtn = document.getElementById("ms-move");
    if (mvBtn) mvBtn.classList.toggle("nope", !m.widget);
    const pvBtn = document.getElementById("ms-pv");
    const pvWrap = document.getElementById("ms-preview-wrap");
    if (pvBtn) pvBtn.classList.add("active");
    if (pvWrap) pvWrap.classList.remove("hidden");
    renderModSettings();
    batAnimateEntrance(ms);
}

function closeModSettings() {
    if(window.__batInlineKeyCapture){const action=window.__batInlineKeyCapture;if(window.__batManagerKeyCaptured)window.__batManagerKeyCaptured(action,null);callBat('keybind-cancel');window.__batInlineKeyCapture=null;}

    if (window.__batKeyCaptured) {
        window.__batKeyCaptured(null);
        callBat("zoom-bind-cancel");
    }
    const ms = document.getElementById("mod-settings");
    if (!ms || ms.classList.contains("hidden")) return false;
    ms.classList.add("hidden");
    msMod = null;
    clearNativePreview();
    const sub = document.querySelector("#view-mods .mm-sub");
    if (sub) sub.classList.remove("hidden");
    const grid = document.getElementById("mod-grid");
    if (grid) grid.classList.remove("hidden");
    const headline = document.getElementById("headline");
    if (headline) headline.textContent = headlines[curView] || "MOD MENU";
    return true;
}

let settingsChangedOnly=false;
function actionBinding(id){try{return JSON.parse(window.batGetKeybinds()).bindings[id]||'key.keyboard.unknown';}catch(e){return CFG.bindings&&CFG.bindings[id]||'key.keyboard.left.alt';}}
function settingDefault(m,row){if(row.type==='binding')return row.default;return m.defaults&&m.defaults[row.key]!==undefined?m.defaults[row.key]:row.type==='toggle'?false:row.type==='slider'?row.min:row.type==='color'?'#ffffffff':row.options[0];}
function settingChanged(m,row){if(row.type==='binding')return actionBinding(m.id)!==row.default;return JSON.stringify(modState(m.id).set[row.key])!==JSON.stringify(settingDefault(m,row));}
function refreshChangedSettings(){
 if(!msMod)return;let changed=0;
 msMod.settings.forEach(sec=>sec.rows.forEach(row=>{const ctl=document.querySelector('#ms-sections .ms-ctl[data-key="'+row.key+'"]');if(!ctl)return;const line=ctl.closest('.ms-row'),different=settingChanged(msMod,row);line.classList.toggle('setting-modified',different);if(different)changed++;}));
 const summary=document.getElementById('settings-changed-summary');if(summary)summary.textContent=changed+' changed settings';
}
function renderModSettings() {
    if (!msMod) return;
    const st = modState(msMod.id);
    const wrap = document.getElementById("ms-sections");
    wrap.innerHTML = "";
    const tools=document.createElement('div');tools.className='changed-settings-tools';
    const summary=document.createElement('span');summary.id='settings-changed-summary';tools.appendChild(summary);
    const filter=document.createElement('button');filter.className='setting-search-result';filter.textContent=settingsChangedOnly?'Show all settings':'Show changed only';filter.setAttribute('aria-pressed',String(settingsChangedOnly));filter.onclick=()=>{settingsChangedOnly=!settingsChangedOnly;renderModSettings();};tools.appendChild(filter);wrap.appendChild(tools);
    msMod.settings.forEach(sec => {
        const s = document.createElement("div");
        s.className = "ms-sec";
        let html = '<div class="ms-sec-title"><span class="ms-chev">&#9662;</span>' + sec.title + '</div>';
        sec.rows.filter(r=>!settingsChangedOnly||settingChanged(msMod,r)).forEach(r => {
            html += '<div class="ms-row"><span class="ms-label">' + r.label +
                '</span><span class="ms-ctl" data-key="' + r.key + '"></span></div>';
        });
        s.innerHTML = html;
        // collapse a section on its title click (Feather-style)
        s.querySelector(".ms-sec-title").addEventListener("click", () => s.classList.toggle("closed"));
        wrap.appendChild(s);
        sec.rows.forEach(r => {const ctl=s.querySelector('.ms-ctl[data-key="'+r.key+'"]');if(!ctl)return;mountModControl(ctl,r,st);});
        if(!s.querySelector('.ms-row'))s.remove();
    });
    refreshChangedSettings();
    renderMsPreview();
}

function mountModControl(el, row, st) {
    if (!el) return;
    const moduleId = msMod.id;
    if(row.type==='binding'){
        const button=document.createElement('button');button.className='bat-tool-button ms-action-bind';button.setAttribute('aria-label','Change '+row.label);
        const caption=()=>button.textContent=actionBinding(moduleId).replace(/^key\.(keyboard|mouse)\./,'').replace(/\./g,' ').toUpperCase();caption();
        button.onclick=()=>{button.textContent='Press a key or mouse button · Esc cancels';window.__batInlineKeyCapture=moduleId;window.__batManagerKeyCaptured=(action,key)=>{window.__batInlineKeyCapture=null;window.__batManagerKeyCaptured=null;try{if(key){cfgSave(moduleId,{en:modState(moduleId).en});const raw=window.batSetKeybind(action,key);if(!raw)throw Error('Keybind could not be saved.');CFG=JSON.parse(raw);localStorage.setItem('bat.hudcfg',raw);}caption();refreshChangedSettings();}catch(e){button.textContent=e.message;}};callBat('keybind-capture:'+moduleId);};el.appendChild(button);return;
    }
    const val = (st.set[row.key] !== undefined)
        ? st.set[row.key]
        : (row.type === "toggle" ? false
            : row.type === "slider" ? row.min
            : row.type === "color" ? ((msMod.defaults && msMod.defaults[row.key]) || "#ffffffff")
            : row.options[0]);
    const commit = (v, persist = true) => {
        st = modState(moduleId);
        st.set[row.key] = v;
        if (row.key === "scale") st.scale = v;   // top-level scale is what HudRenderer reads
        const patch = { set: { [row.key]: v } };
        if (row.key === "scale") patch.scale = v;
        if (!persist && nativeHud && typeof window.batPreviewHudModule === "function") {
            window.batPreviewHudModule(moduleId, JSON.stringify(Object.assign({ _initial: st }, patch)));
        } else {
            cfgSave(moduleId, patch);
        }
        refreshChangedSettings();
        renderMsPreview();
        if (persist || !nativeHud) renderHudWidgets();
    };
    if (row.type === "toggle") {
        el.innerHTML = '<label class="switch"><input type="checkbox"' + (val ? " checked" : "") + '><span></span></label>';
        el.querySelector("input").addEventListener("change", e => commit(e.target.checked, true));
    } else if (row.type === "slider") {
        el.innerHTML = '<span class="ms-num">' + val + '</span><input type="range" min="' + row.min +
            '" max="' + row.max + '" step="' + row.step + '" value="' + val + '">';
        const num = el.querySelector(".ms-num");
        const input = el.querySelector("input");
        input.addEventListener("input", e => {
            num.textContent = e.target.value;
            commit(parseFloat(e.target.value), false);
        });
        input.addEventListener("change", e => {
            num.textContent = e.target.value;
            commit(parseFloat(e.target.value), true);
        });
    } else if (row.type === "choice" || row.type === "keybind") {
        el.innerHTML = '<div class="seg">' + row.options.map(o =>
            '<button class="' + (o === val ? "on" : "") + '" data-v="' + o + '">' + o + '</button>').join("") + '</div>';
        el.querySelectorAll(".seg button").forEach(b => {
            b.addEventListener("click", () => {
                el.querySelectorAll(".seg button").forEach(x => x.classList.toggle("on", x === b));
                commit(b.dataset.v);
            });
        });
        if (row.type === "keybind") {
            el.classList.add("keybind-ctl");
            const bind = document.createElement("button");
            bind.className = "zoom-custom-bind";
            const caption = () => {
                const key = st.set[row.key];
                bind.textContent = key && key.startsWith("key.") ? key.replace(/^key\.(keyboard|mouse)\./, '').replace(/\./g, ' ').toUpperCase() : "Custom key…";
            };
            caption();
            bind.addEventListener("click", () => {
                bind.textContent = "Press a key or mouse button (Esc cancels)";
                window.__batKeyCaptured = key => {
                    window.__batKeyCaptured = null;
                    if (key) {
                        commit(key);
                        el.querySelectorAll('.seg button').forEach(b => b.classList.remove('on'));
                    }
                    caption();
                };
                callBat("zoom-bind");
            });
            el.appendChild(bind);
        }
    } else if (row.type === "color") {
        const def = hpColor((msMod.defaults && msMod.defaults[row.key]) || "#ffffffff");
        el.innerHTML = '<button class="swatch" aria-label="Pick color"></button>';
        const sw = el.querySelector(".swatch");
        sw.style.backgroundColor = hpColor(val);
        sw.addEventListener("click", () => {
            openColorPicker({
                title: row.label,
                hint: moduleId === 'resourcepacks' && (row.key === 'color' || row.key === 'descriptionColor') ? 'Apply uses custom text colors instead of the pack\'s original colors.' : '',
                value: hpColor(st.set[row.key] !== undefined ? st.set[row.key] : val),
                def: def,
                onChange: v => {
                    st = modState(moduleId);
                    st.set[row.key] = v;
                    if (typeof window.batPreviewHudModule === 'function') window.batPreviewHudModule(moduleId,
                        JSON.stringify({ _initial: st, set: { [row.key]: v } }));
                    sw.style.backgroundColor = v;
                    renderMsPreview();
                    if(!nativeHud)renderHudWidgets();
                },
                onCommit: v => {
                    const settings = { [row.key]: v };
                    if (moduleId === 'resourcepacks' && (row.key === 'color' || row.key === 'descriptionColor')) {
                        settings.originalColors = false;
                        const toggle = document.querySelector('.ms-ctl[data-key="originalColors"] input');
                        if (toggle) toggle.checked = false;
                    }
                    cfgSave(moduleId, { set: settings });
                    sw.style.backgroundColor = v;
                    renderMsPreview(); renderHudWidgets();
                }
            });
        });
    }
}

function renderMsPreview() {
    if (!msMod) return;
    const box = document.getElementById("ms-preview");
    if (!box) return;
    if ((msMod.widget || msMod.nativePreview) && typeof window.batSetHudPreview === "function" && nativeHud) {
        box.innerHTML = "";
        box.classList.add("native-preview");
        reportNativePreview();
        return;
    }
    box.classList.remove("native-preview");
    const st = modState(msMod.id);
    const scale = st.scale || 1;
    const s = st.set || {};
    if (msMod.preview) {
        const pv = widgetPreview(msMod, s);
        // same plate + classes as the real widget, so the preview matches the game
        const cls = [];
        if (s.background === false) cls.push("no-bg");
        if (s.shadow === false) cls.push("no-shadow");
        if (s.numbers === false) cls.push("no-num");
        if (s.showCps === false) cls.push("nocps");
        if (s.border === false) cls.push("noborder");
        box.innerHTML = '<div class="hud-mock hud-wbox ' + cls.join(" ") +
            '" style="transform:scale(' + scale + ')">' + pv + '</div>';
        const mock = box.querySelector(".hud-mock");
        if (mock) {
            mock.style.setProperty("--hpc", hpColor(s.color));
            if (s.bgColor) mock.style.setProperty("--hbg", s.bgColor);
            if (msMod.id === "keystrokes") mock.style.setProperty("--kflash", kflashColor(s.pressedColor));
        }
    } else {
        box.innerHTML = '<div class="hud-mock client-mock">' + msMod.icon + '<span>' + msMod.name + '</span></div>';
    }
}

const msBackEl = document.getElementById("ms-back");
if (msBackEl) msBackEl.addEventListener("click", closeModSettings);
const msMoveEl = document.getElementById("ms-move");
if (msMoveEl) {
    msMoveEl.addEventListener("click", () => {
        if (!msMod || !msMod.widget) return;
        closeModSettings();
        closePanel();
        toggleHudEditor(true);   // HUD editor opens — drag the widget, ESC returns to game
    });
}
const msPvEl = document.getElementById("ms-pv");
if (msPvEl) {
    msPvEl.addEventListener("click", () => {
        const w = document.getElementById("ms-preview-wrap");
        if (!w) return;
        const hidden = w.classList.toggle("hidden");
        msPvEl.classList.toggle("active", !hidden);
        scheduleNativePreview();
    });
}

// ============ Feather-style COLOR PICKER (SV square + hue + alpha + hex) ====
// openColorPicker({title, value, def, onChange, onCommit}) — value formats:
// "#RRGGBBAA" (legacy names like "White" pass through hpColor first).
let cpOnChange = null;
let cpOnCommit = null;
let cpOnCancel = null, cpRequiresApply = false, cpDrag = null, cpPaintFrame = 0;
function cpQueuePaint(){if(!cpPaintFrame)cpPaintFrame=requestAnimationFrame(()=>{cpPaintFrame=0;cpPaint(true);});}
function cpFlushPaint(){if(cpPaintFrame){cancelAnimationFrame(cpPaintFrame);cpPaintFrame=0;}cpPaint(true);}
function cpEndDrag(){if(cpDrag&&cpDrag.el.releasePointerCapture&&cpDrag.id!==undefined){try{cpDrag.el.releasePointerCapture(cpDrag.id);}catch(e){}}cpDrag=null;}
let cpDef = "#ffffffff";
let cpHSV = { h: 0, s: 1, v: 1, a: 1 };

function hexToHsva(hex) {
    hex = hpColor(hex);
    const r = parseInt(hex.substr(1, 2), 16) / 255;
    const g = parseInt(hex.substr(3, 2), 16) / 255;
    const b = parseInt(hex.substr(5, 2), 16) / 255;
    const a = parseInt(hex.substr(7, 2), 16) / 255;
    const mx = Math.max(r, g, b), mn = Math.min(r, g, b), d = mx - mn;
    let h = 0;
    if (d) {
        if (mx === r) h = 60 * (((g - b) / d) % 6);
        else if (mx === g) h = 60 * ((b - r) / d + 2);
        else h = 60 * ((r - g) / d + 4);
    }
    if (h < 0) h += 360;
    return { h: h, s: mx ? d / mx : 0, v: mx, a: a };
}
function hsvaToHex(c) {
    const f = n => {
        const k = (n + c.h / 60) % 6;
        return Math.round(255 * (c.v - c.v * c.s * Math.max(0, Math.min(k, 4 - k, 1))));
    };
    const p = n => n.toString(16).padStart(2, "0").toUpperCase();
    return "#" + p(f(5)) + p(f(3)) + p(f(1)) + p(Math.round(c.a * 255));
}
function cpColorRecents() {
    try { return JSON.parse(localStorage.getItem("bat.colorrecents") || "[]"); } catch (e) { return []; }
}
function cpPushRecent(hex) {
    try {
        let arr = cpColorRecents().filter(x => x !== hex);
        arr.unshift(hex);
        localStorage.setItem("bat.colorrecents", JSON.stringify(arr.slice(0, 10)));
    } catch (e) { /* ignore */ }
}
const CP_FAVS = ["#FFFFFFFF", "#0000006B", "#4DA3FFFF", "#2FD06FFF", "#E5484DFF",
    "#FFCC4DFF", "#55FFFFFF", "#9A9AA8FF", "#000000FF", "#FFFFFF55"];

function cpEnsureDom() {
    if (document.getElementById("cp-veil")) return;
    const veil = document.createElement("div");
    veil.id = "cp-veil";
    veil.className = "cp-veil hidden";
    veil.innerHTML =
        '<div class="cp-pop">' +
        '<div class="cp-title" id="cp-title">COLOR</div>' +
        '<div class="cp-main">' +
        '<div class="cp-sv" id="cp-sv"><span class="cp-circ" id="cp-svc"></span></div>' +
        '<div class="cp-strip cp-hue" id="cp-hue"><span class="cp-mark" id="cp-huem"></span></div>' +
        '<div class="cp-strip cp-alpha" id="cp-alpha"><span class="cp-mark" id="cp-alpham"></span></div>' +
        '<div class="cp-right">' +
        '<div class="cp-lab">Selected color</div>' +
        '<div class="cp-sel"><span id="cp-selbox"></span></div>' +
        '<input class="cp-hex" id="cp-hex" spellcheck="false" maxlength="9" value="#FFFFFFFF">' +
        '<div class="cp-hexa">HEXA</div>' +
        '<button type="button" class="cp-apply" id="cp-apply">Apply color</button>' +
        '<div class="cp-error" id="cp-error" role="status"></div>' +
        '</div>' +
        '</div>' +
        '<div class="cp-lab cp-lab2">Favorite Colors</div>' +
        '<div class="cp-swatches" id="cp-favs"></div>' +
        '<div class="cp-lab cp-lab2">Recent Colors</div>' +
        '<div class="cp-swatches" id="cp-recents"></div>' +
        '<div class="cp-note" id="cp-note"></div>' +
        '</div>';
    document.body.appendChild(veil);
    veil.addEventListener("mousedown", e => { if (e.target === veil) closeColorPicker(); });
    document.getElementById("cp-apply").addEventListener("click", applyColorPicker);

    // Some embedded WebKit builds omit capture/buttons on move events.
    // Track an explicit gesture, with document-level mouse fallback as well.
    const bindDrag=(el,update)=>{
        const begin=e=>{
            if(e.button!==undefined&&e.button!==0)return;
            if(cpDrag)return;
            cpDrag={el,update,kind:e.type==='pointerdown'?'pointer':'mouse',id:e.pointerId};
            if(e.pointerId!==undefined&&el.setPointerCapture){try{el.setPointerCapture(e.pointerId);}catch(ignored){}}
            update(e);cpPaint(true);e.preventDefault();
        };
        el.addEventListener('pointerdown',begin);el.addEventListener('mousedown',begin);
    };
    const normalized=(value,start,size)=>Math.max(0,Math.min(1,(value-start)/Math.max(1,size)));
    bindDrag(document.getElementById('cp-sv'),e=>{const r=document.getElementById('cp-sv').getBoundingClientRect();cpHSV.s=normalized(e.clientX,r.left,r.width);cpHSV.v=1-normalized(e.clientY,r.top,r.height);});
    bindDrag(document.getElementById('cp-hue'),e=>{const r=document.getElementById('cp-hue').getBoundingClientRect();cpHSV.h=normalized(e.clientY,r.top,r.height)*360;});
    bindDrag(document.getElementById('cp-alpha'),e=>{const r=document.getElementById('cp-alpha').getBoundingClientRect();cpHSV.a=1-normalized(e.clientY,r.top,r.height);});
    const matching=e=>cpDrag&&(cpDrag.kind==='pointer'?e.type.indexOf('pointer')===0&&(e.pointerId===undefined||e.pointerId===cpDrag.id):e.type.indexOf('mouse')===0);
    const move=e=>{if(!matching(e))return;cpDrag.update(e);cpQueuePaint();e.preventDefault();};
    const end=e=>{if(!matching(e)||(e.button!==undefined&&e.button!==0))return;cpDrag.update(e);cpFlushPaint();cpEndDrag();};
    document.addEventListener('pointermove',move,true);document.addEventListener('mousemove',move,true);
    document.addEventListener('pointerup',end,true);document.addEventListener('mouseup',end,true);
    document.addEventListener('pointercancel',()=>cpEndDrag(),true);window.addEventListener('blur',()=>cpEndDrag());document.addEventListener('mouseout',e=>{if(!e.relatedTarget)cpEndDrag();});

    // hex input
    const hx = document.getElementById("cp-hex");
    hx.addEventListener("change", () => {
        let v = hx.value.trim();
        if (v.charAt(0) !== "#") v = "#" + v;
        if (v.length === 7) v += "FF";
        if (/^#[0-9a-fA-F]{8}$/.test(v)) cpApply(hexToHsva(v), false);
        else cpPaint(false);   // repaint the input with the real value
    });
    hx.addEventListener("keydown", e => { if (e.key === "Enter") { e.preventDefault(); e.stopPropagation(); applyColorPicker(); } });

    // favorite swatches
    document.getElementById("cp-favs").innerHTML = CP_FAVS.map(c =>
        '<button class="cp-sw" data-c="' + c + '" style="background:' + c + '"></button>').join("");
    document.getElementById("cp-favs").addEventListener("click", e => {
        const b = e.target.closest(".cp-sw");
        if (b) cpApply(hexToHsva(b.dataset.c), false);
    });
    document.getElementById("cp-recents").addEventListener("click", e => {
        const b = e.target.closest(".cp-sw");
        if (b) cpApply(hexToHsva(b.dataset.c), false);
    });
}

function cpPaint(live) {
    const hex = hsvaToHex(cpHSV);
    // SV square: hue base + white/black gradients; cursor position
    const sv = document.getElementById("cp-sv");
    sv.style.background =
        "linear-gradient(to top, #000, transparent), linear-gradient(to right, #fff, hsl(" +
        Math.round(cpHSV.h) + ",100%,50%))";
    const c = document.getElementById("cp-svc");
    c.style.left = (cpHSV.s * 100) + "%";
    c.style.top = ((1 - cpHSV.v) * 100) + "%";
    c.style.background = hex;
    // strips
    document.getElementById("cp-huem").style.top = ((cpHSV.h / 360) * 100) + "%";
    const al = document.getElementById("cp-alpha");
    al.style.setProperty("--cp-rgb", hsvaToHex({ h: cpHSV.h, s: cpHSV.s, v: cpHSV.v, a: 1 }).substr(0, 7));
    document.getElementById("cp-alpham").style.top = ((1 - cpHSV.a) * 100) + "%";
    // selected + hex input
    document.getElementById("cp-selbox").style.background = hex;
    const hx = document.getElementById("cp-hex");
    hx.value = hex;
    if (cpOnChange && live) cpOnChange(hex);
}
function cpApply(c) {
    cpHSV = c;
    cpPaint(true);
}

function applyColorPicker() {
    const input = document.getElementById('cp-hex');
    let value = input.value.trim();
    if (!value.startsWith('#')) value = '#' + value;
    if (value.length === 7) value += 'FF';
    if (!/^#[0-9a-fA-F]{8}$/.test(value)) {
        document.getElementById('cp-error').textContent = 'Enter a 6- or 8-digit hex color.';
        input.focus(); return;
    }
    cpApply(hexToHsva(value));
    closeColorPicker(true);
}

function openColorPicker(opts) {
    cpEnsureDom();
    const veil = document.getElementById("cp-veil");
    veil.classList.remove("hidden");
    if (nativeHud && typeof window.batSetHudPreview === 'function') reportNativePreview();
    document.getElementById("cp-title").textContent = (opts.title || "COLOR").toUpperCase();
    document.getElementById('cp-note').textContent = opts.hint || '';
    document.getElementById('cp-error').textContent = '';
    cpDef = hpColor(opts.def || "#ffffffff");
    cpOnChange = opts.onChange || null;
    cpOnCommit = opts.onCommit || null;
    cpOnCancel = opts.onCancel || null;cpRequiresApply=!!opts.applyOnly;cpEndDrag();
    cpHSV = hexToHsva(opts.value || cpDef);
    // recents
    const rec = cpColorRecents();
    document.getElementById("cp-recents").innerHTML = rec.length
        ? rec.map(c => '<button class="cp-sw" data-c="' + c + '" style="background:' + c + '"></button>').join("")
        : '<span class="cp-empty">None yet</span>';
    cpPaint(false);
}
// closes the popup; returns true when one was open (for the ESC ladder)
function closeColorPicker(applied = false) {
    const veil = document.getElementById("cp-veil");
    if (!veil || veil.classList.contains("hidden")) return false;
    cpEndDrag();if(cpPaintFrame){cancelAnimationFrame(cpPaintFrame);cpPaintFrame=0;}
    veil.classList.add("hidden");
    const hex = hsvaToHex(cpHSV);
    if(!cpRequiresApply||applied){cpPushRecent(hex);if(cpOnCommit)cpOnCommit(hex);}else if(cpOnCancel)cpOnCancel();
    cpOnCancel=null;cpRequiresApply=false;
    cpOnChange = null;
    cpOnCommit = null;
    scheduleNativePreview();
    return true;
}

// ============ HUD WIDGETS (overlay page) — the SAME DOM is the in-game HUD =
// Always rendered (W2): Java HudRenderer draws this page's texture under the
// game and feeds live values via window.__batHud. Edit chrome (gear/hide/
// label/handles) is gated to body.hud-edit by CSS; positions are % of the
// window so editor and game are literally the same pixels.
function hpColor(n) {
    if (typeof n === "string" && n.charAt(0) === "#") return n.length === 7 ? n + "FF" : n;
    switch (n) {
        case "Gray": return "#9a9aa8";
        case "Green": return "#57d180";
        case "Yellow": return "#ffcc4d";
        case "Aqua": return "#55ffff";
        case "Black": return "#000000";
        case "Red": return "#e5484d";
        case "Blue": return "#4da3ff";
        default: return "#ffffffff";
    }
}
function kflashColor(n) {
    if (typeof n === "string" && n.charAt(0) === "#") return n.length === 7 ? n + "55" : n;
    const h = hpColor(n);
    const r = parseInt(h.substr(1, 2), 16);
    const g = parseInt(h.substr(3, 2), 16);
    const b = parseInt(h.substr(5, 2), 16);
    return "rgba(" + r + "," + g + "," + b + ",0.33)";   // matches Java pressFlash (0x55 alpha)
}
function widgetPreview(m, s) {
    if (m.id === "nametags") return '<span class="hp nametag-preview">' +
        (s.logo === false ? '' : '<img src="bat-logo.png" alt="">') + 'BatPlayer</span>';
    if (m.id === "crosshair") {
        const kind = s.style === "Dot" ? "dot" : s.style === "Cross + Dot" ? "cross-dot" : "cross";
        return '<span class="hp hp-cross crosshair-' + kind + '"><i></i></span>';
    }
    if (m.id === "coords") return '<span class="hp hp-coords">' + hudCoordsMarkup(s.labels === false ? '125 64 -340' : 'X: 125 Y: 64 Z: -340') + '</span>';
    if (m.id === "cps") return '<span class="hp">0 | 0</span>';
    if (m.id === "potions" && s.format === "Full") return m.preview.replace("1:24", "1m 24s").replace("0:45", "0m 45s");
    return m.preview;
}

function hudClampPosition(el, pos) {
    const r = el.getBoundingClientRect();
    return {
        x: Math.max(0, Math.min(Math.max(0, 100 - r.width / window.innerWidth * 100), pos.x)),
        y: Math.max(0, Math.min(Math.max(0, 100 - r.height / window.innerHeight * 100), pos.y))
    };
}

function hudKeepOnScreen(el, st) {
    const pos = hudClampPosition(el, st);
    st.x = pos.x;
    st.y = pos.y;
    el.style.left = st.x + "%";
    el.style.top = st.y + "%";
    hudLabelPlacement(el, st);
}

function hudLabelPlacement(el, st) {
    el.dataset.side = st.x >= 65 ? "right" : "left";
    el.dataset.bottom = st.y >= 65 ? "true" : "false";
}

function hudCoordsMarkup(value) {
    const text = String(value || '');
    const match = text.match(/X:\s*(-?\d+)\s+Y:\s*(-?\d+)\s+Z:\s*(-?\d+)/);
    return match ? ['X', 'Y', 'Z'].map((axis, i) => '<span><b>' + axis + ':</b> ' + match[i + 1] + '</span>').join('') : hudEsc(text);
}

function applyNativeSize(el) {
    const size = nativeHudSizes[el.dataset.id];
    if (!nativeHud || !size || el.classList.contains("dragging")) return;
    if (el.style.width === size[0] + "px" && el.style.height === size[1] + "px") return;
    el.style.width = size[0] + "px";
    el.style.height = size[1] + "px";
    hudKeepOnScreen(el, modState(el.dataset.id));
}

// Update the Java renderer during pointer motion; disk writes happen on release.
function previewNativePosition(el, id) {
    if (!nativeHud || (typeof window.batPreviewHudModule !== "function" && typeof window.batPreviewHudConfig !== "function")) return;
    const st = modState(id);
    st.x = parseFloat(el.style.left);
    st.y = parseFloat(el.style.top);
    st.scale = Number((el.style.transform.match(/scale\(([^)]+)\)/) || [0, 1])[1]);
    st.set.scale = st.scale;
    delete st.anchor; delete st.offset_x; delete st.offset_y;
    try {
        if (typeof window.batPreviewHudModule === "function") window.batPreviewHudModule(id,
            JSON.stringify({ _initial: st, x: st.x, y: st.y, scale: st.scale, set: { scale: st.scale } }));
        else window.batPreviewHudConfig(JSON.stringify(CFG));
    } catch (e) { console.log(e); }
}

function renderHudWidgets() {
    const layer = document.getElementById("hud-layer");
    if (!layer) return;
    layer.innerHTML = "";
    MODULES.filter(m => m.widget && modState(m.id).en).forEach(m => {
        const st = modState(m.id);
        const s = st.set || {};
        const el = document.createElement("div");
        el.className = "hud-widget"+(st.locked?" locked":"");
        el.dataset.id = m.id;
        el.dataset.live = "1";
        hudLabelPlacement(el, st);
        el.style.left = st.x + "%";
        el.style.top = st.y + "%";
        el.style.transform = "scale(" + st.scale + ")";
        // appearance settings -> CSS (text formatting settings like labels/
        // potion format are applied by the Java feed instead)
        const cls = [];
        if (s.background === false) cls.push("no-bg");
        if (s.shadow === false) cls.push("no-shadow");
        if (s.numbers === false) cls.push("no-num");
        if (s.showCps === false) cls.push("nocps");
        if (s.border === false) cls.push("noborder");
        el.style.setProperty("--hpc", hpColor(s.color));
        if (s.bgColor) el.style.setProperty("--hbg", s.bgColor);
        if (m.id === "keystrokes") el.style.setProperty("--kflash", kflashColor(s.pressedColor));
        el.innerHTML =
            '<div class="hud-wbox ' + cls.join(" ") + '">' + widgetPreview(m, s) +
            '<button class="hud-btn hud-gear" title="Settings">' + GEAR_SVG + '</button>' +
            '<button class="hud-btn hud-hide" title="Hide module">&minus;</button>' +
            '</div>' +
            '<div class="hud-wlabel">' + m.name + '</div>' +
            '<div class="hud-rz hud-rz-nw"></div><div class="hud-rz hud-rz-ne"></div>' +
            '<div class="hud-rz hud-rz-sw"></div><div class="hud-rz hud-rz-se"></div>';
        el.querySelector(".hud-gear").addEventListener("click", () => openModSettings(m.id));
        el.querySelector(".hud-hide").addEventListener("click", () => setModEnabled(m.id, false));
        makeHudDraggable(el, m.id);
        makeHudResizable(el, m.id);
        layer.appendChild(el);
        applyNativeSize(el);
        hudKeepOnScreen(el, st);
    });
    hudSelect(hudSelId);
}

function makeHudDraggable(el, id) {
    el.addEventListener("pointerdown", e => {
        if (!document.body.classList.contains("hud-edit")) return;
        if (e.target.closest(".hud-btn") || e.target.closest(".hud-rz")) return;
        if (document.body.classList.contains("hud-edit")) hudSelect(id,e.ctrlKey||e.metaKey);
        if(e.ctrlKey||e.metaKey||modState(id).locked){e.preventDefault();return;}
        const rect = el.getBoundingClientRect();
        const offX = e.clientX - rect.left;
        const offY = e.clientY - rect.top;
        let pos = { x: parseFloat(el.style.left) || 0, y: parseFloat(el.style.top) || 0 };
        el.classList.add("dragging");
        try { el.setPointerCapture(e.pointerId); } catch (err) { /* ignore */ }
        const move = ev => {
            let x = ((ev.clientX - offX) / window.innerWidth) * 100;
            let y = ((ev.clientY - offY) / window.innerHeight) * 100;
            pos = hudClampPosition(el, { x, y });
            if (hudSnapOn && !ev.shiftKey) pos = hudApplySnap(el, id, pos);
            else hideHudGuides();
            el.style.left = pos.x + "%";
            el.style.top = pos.y + "%";
            hudLabelPlacement(el, pos);
            previewNativePosition(el, id);
        };
        const up = () => {
            el.classList.remove("dragging");
            el.removeEventListener("pointermove", move);
            el.removeEventListener("pointerup", up);
            el.removeEventListener("pointercancel", up);
            hideHudGuides();
            const st = modState(id);
            st.x = pos.x;
            st.y = pos.y;
            saveHudLayout(id, st);
        };
        el.addEventListener("pointermove", move);
        el.addEventListener("pointerup", up);
        el.addEventListener("pointercancel", up);
        e.preventDefault();
    });
}

// ---- Feather/Lunar-style snap guides (screen edges/center + other widgets) ----
let hudUndoStack=[],hudRedoStack=[],hudHistoryBase=null,hudHistoryApplying=false;
function hudLayoutSnapshot(){const value={};MODULES.filter(m=>m.widget).forEach(m=>{const st=modState(m.id);value[m.id]={x:st.x,y:st.y,scale:st.scale,locked:!!st.locked,en:st.en};});return value;}
function hudBeginHistory(){hudUndoStack=[];hudRedoStack=[];hudHistoryBase=hudLayoutSnapshot();updateHudTools();}
function hudRecordLayout(){if(hudHistoryApplying)return;const after=hudLayoutSnapshot();if(hudHistoryBase&&JSON.stringify(after)!==JSON.stringify(hudHistoryBase)){hudUndoStack.push({before:hudHistoryBase,after});if(hudUndoStack.length>40)hudUndoStack.shift();hudRedoStack=[];}hudHistoryBase=after;updateHudTools();}
function hudRestoreLayout(value){hudHistoryApplying=true;try{Object.keys(value).forEach(id=>{const st=modState(id);Object.assign(st,value[id]);saveHudLayout(id,st);cfgSave(id,{en:st.en});});}finally{hudHistoryApplying=false;}hudHistoryBase=hudLayoutSnapshot();renderHudWidgets();updateHudTools();}
function hudUndo(){const entry=hudUndoStack.pop();if(!entry)return;hudRedoStack.push(entry);hudRestoreLayout(entry.before);}
function hudRedo(){const entry=hudRedoStack.pop();if(!entry)return;hudUndoStack.push(entry);hudRestoreLayout(entry.after);}
function updateHudTools(){
 const undo=document.getElementById('hud-undo'),redo=document.getElementById('hud-redo');if(undo)undo.disabled=!hudUndoStack.length;if(redo)redo.disabled=!hudRedoStack.length;
 const lock=document.getElementById('hud-lock');if(lock){lock.disabled=!hudSelId;lock.textContent=hudSelId&&modState(hudSelId).locked?'Unlock':'Lock';}
 for(const axis of ['x','y']){const button=document.getElementById('hud-space-'+axis);if(button)button.disabled=hudSelectedIds.size<3||Array.from(hudSelectedIds).some(id=>modState(id).locked);}
}
function hudToggleLock(){if(!hudSelId)return;const st=modState(hudSelId);st.locked=!st.locked;saveHudLayout(hudSelId,st);renderHudWidgets();}
function hudDistribute(axis){
 const widgets=Array.from(hudSelectedIds).map(hudEl).filter(Boolean);if(widgets.length<3||widgets.some(el=>modState(el.dataset.id).locked))return false;
 const horizontal=axis==='x',start=horizontal?'left':'top',end=horizontal?'right':'bottom',size=horizontal?'width':'height',viewport=horizontal?window.innerWidth:window.innerHeight;
 const list=widgets.map(el=>({el,rect:el.getBoundingClientRect()})).sort((a,b)=>a.rect[start]-b.rect[start]);
 const gap=(list[list.length-1].rect[end]-list[0].rect[start]-list.reduce((n,w)=>n+w.rect[size],0))/(list.length-1);if(gap<0)return false;
 let position=list[0].rect[start];hudHistoryApplying=true;
 try{list.forEach(w=>{const st=modState(w.el.dataset.id);st[axis]=position/viewport*100;w.el.style[horizontal?'left':'top']=st[axis]+'%';saveHudLayout(w.el.dataset.id,st);position+=w.rect[size]+gap;});}finally{hudHistoryApplying=false;}
 hudRecordLayout();renderHudWidgets();return true;
}
function installHudTools(){const bar=document.querySelector('#hud-toolbar .hud-tb-btns');if(!bar||document.getElementById('hud-undo'))return;
 [['hud-undo','Undo','Undo layout change (Ctrl+Z)',hudUndo],['hud-redo','Redo','Redo layout change (Ctrl+Y)',hudRedo],['hud-lock','Lock','Lock or unlock selected widget',hudToggleLock],['hud-space-x','Space X','Equal horizontal gaps (Ctrl-click at least 3 widgets)',()=>hudDistribute('x')],['hud-space-y','Space Y','Equal vertical gaps (Ctrl-click at least 3 widgets)',()=>hudDistribute('y')]].forEach(([id,label,title,action])=>{const button=document.createElement('button');button.id=id;button.className='hud-tb-btn hud-tb-text';button.textContent=label;button.title=title;button.onclick=action;bar.appendChild(button);});updateHudTools();
 const hint=document.createElement('div');hint.className='hud-selection-hint';hint.textContent='Ctrl-click: select several · Ctrl+Z/Y: undo/redo · L: lock · Shift: free move';hint.classList.add('hidden');document.body.appendChild(hint);
}
document.addEventListener('keydown',event=>{
 if(!document.body.classList.contains('hud-edit')||event.target.closest('input,textarea,[contenteditable=true]'))return;
 const key=event.key.toLowerCase();if((event.ctrlKey||event.metaKey)&&['z','y','a'].includes(key)){event.preventDefault();event.stopImmediatePropagation();if(key==='a'){hudSelectedIds.clear();hudWidgetIds().forEach(id=>hudSelectedIds.add(id));hudSelId=hudWidgetIds()[0]||null;hudSelect(hudSelId);}else if(key==='y'||event.shiftKey)hudRedo();else hudUndo();}
 else if(key==='l'){event.preventDefault();hudToggleLock();}
},true);

let hudSelId = null;
const hudSelectedIds=new Set();
let hudSnapOn = true;
try { hudSnapOn = localStorage.getItem("bat.hud.snap") !== "off"; } catch (e) { /* default on */ }
const HUD_SNAP_PX = 6;

function hudSelect(id,additive=false){
 hudSelId=id;
 if(!id)hudSelectedIds.clear();else if(additive){if(hudSelectedIds.has(id))hudSelectedIds.delete(id);else hudSelectedIds.add(id);}else if(!hudSelectedIds.has(id)){hudSelectedIds.clear();hudSelectedIds.add(id);}
 if(hudSelId&&!hudSelectedIds.has(hudSelId))hudSelId=Array.from(hudSelectedIds).pop()||null;
 document.querySelectorAll('#hud-layer .hud-widget').forEach(n=>n.classList.toggle('selected',hudSelectedIds.has(n.dataset.id)));
 updateHudTools();
}

function hideHudGuides() {
    const v = document.getElementById("guide-v");
    const h = document.getElementById("guide-h");
    if (v) v.classList.add("hidden");
    if (h) h.classList.add("hidden");
}

function showHudGuide(axis, posPct) {
    const g = document.getElementById(axis === "v" ? "guide-v" : "guide-h");
    if (!g) return;
    g.classList.remove("hidden");
    if (axis === "v") g.style.left = posPct + "%";
    else g.style.top = posPct + "%";
}

function hudSnapTargets(excludeId) {
    const lines = [];
    const addV = p => lines.push({ axis: "v", posPct: p });
    const addH = p => lines.push({ axis: "h", posPct: p });
    [0, 50, 100].forEach(addV);
    [0, 50, 100].forEach(addH);
    document.querySelectorAll("#hud-layer .hud-widget").forEach(w => {
        if (w.dataset.id === excludeId) return;
        const r = w.getBoundingClientRect();
        if (!r.width || !r.height || getComputedStyle(w).display === "none") return;
        const cx = ((r.left + r.right) / 2 / window.innerWidth) * 100;
        const cy = ((r.top + r.bottom) / 2 / window.innerHeight) * 100;
        addV((r.left / window.innerWidth) * 100);
        addV((r.right / window.innerWidth) * 100);
        addV(cx);
        addH((r.top / window.innerHeight) * 100);
        addH((r.bottom / window.innerHeight) * 100);
        addH(cy);
    });
    return lines;
}

function hudApplySnap(el, id, pos) {
    hideHudGuides();
    const r = el.getBoundingClientRect();
    const wPct = (r.width / window.innerWidth) * 100;
    const hPct = (r.height / window.innerHeight) * 100;
    const leftPct = pos.x;
    const rightPct = pos.x + wPct;
    const midXPct = pos.x + wPct / 2;
    const topPct = pos.y;
    const bottomPct = pos.y + hPct;
    const midYPct = pos.y + hPct / 2;
    const snapPxXPct = (HUD_SNAP_PX / window.innerWidth) * 100;
    const snapPxYPct = (HUD_SNAP_PX / window.innerHeight) * 100;
    let bestV = null;
    let bestH = null;
    const targets = hudSnapTargets(id);
    for (let i = 0; i < targets.length; i++) {
        const t = targets[i];
        if (t.axis === "v") {
            const cands = [
                { anchor: leftPct, delta: leftPct - t.posPct },
                { anchor: midXPct, delta: midXPct - t.posPct },
                { anchor: rightPct, delta: rightPct - t.posPct }
            ];
            for (let c = 0; c < cands.length; c++) {
                const d = Math.abs(cands[c].delta);
                if (d <= snapPxXPct && (!bestV || d < bestV.d)) {
                    bestV = { d, posPct: t.posPct, anchor: cands[c].anchor };
                }
            }
        } else {
            const cands = [
                { anchor: topPct, delta: topPct - t.posPct },
                { anchor: midYPct, delta: midYPct - t.posPct },
                { anchor: bottomPct, delta: bottomPct - t.posPct }
            ];
            for (let c = 0; c < cands.length; c++) {
                const d = Math.abs(cands[c].delta);
                if (d <= snapPxYPct && (!bestH || d < bestH.d)) {
                    bestH = { d, posPct: t.posPct, anchor: cands[c].anchor };
                }
            }
        }
    }
    let x = pos.x;
    let y = pos.y;
    if (bestV) {
        x = pos.x - (bestV.anchor - bestV.posPct);
        showHudGuide("v", bestV.posPct);
    }
    if (bestH) {
        y = pos.y - (bestH.anchor - bestH.posPct);
        showHudGuide("h", bestH.posPct);
    }
    return hudClampPosition(el, { x, y });
}

function hudWidgetIds() {
    return Array.from(document.querySelectorAll("#hud-layer .hud-widget"))
        .map(n => n.dataset.id)
        .filter(Boolean);
}

function hudCycleSelect(dir) {
    const ids = hudWidgetIds();
    if (!ids.length) return;
    let i = ids.indexOf(hudSelId);
    i = i < 0 ? (dir > 0 ? 0 : ids.length - 1) : (i + dir + ids.length) % ids.length;
    hudSelect(ids[i]);
}

function hudNudgeSelected(dx, dy) {
    if(hudSelId&&modState(hudSelId).locked)return;
    if (!hudSelId) return;
    const el = hudEl(hudSelId);
    if (!el) return;
    let x = parseFloat(el.style.left) || 0;
    let y = parseFloat(el.style.top) || 0;
    ({ x, y } = hudClampPosition(el, { x: x + dx, y: y + dy }));
    el.style.left = x + "%";
    el.style.top = y + "%";
    const st = modState(hudSelId);
    st.x = x;
    st.y = y;
    saveHudLayout(hudSelId, st);
}

function hudScaleById(id, delta) {
    if(modState(id).locked)return;
    const el = hudEl(id);
    if (!el) return;
    const st = modState(id);
    let s = (st.scale || 1) + delta;
    s = Math.max(0.5, Math.min(3, Math.round(s * 10) / 10));
    st.scale = s;
    el.style.transform = "scale(" + s + ")";
    hudKeepOnScreen(el, st);
    saveHudLayout(id, st);
}

function hudResetScale() {
    if(hudSelId&&modState(hudSelId).locked)return;
    if (!hudSelId) return;
    const el = hudEl(hudSelId);
    if (!el) return;
    const st = modState(hudSelId);
    st.scale = 1;
    el.style.transform = "scale(1)";
    hudKeepOnScreen(el, st);
    saveHudLayout(hudSelId, st);
}

function hudCenterSelected(axis) {
    if(hudSelId&&modState(hudSelId).locked)return;
    if (!hudSelId) return;
    const el = hudEl(hudSelId);
    if (!el) return;
    const r = el.getBoundingClientRect();
    let x = parseFloat(el.style.left) || 0;
    let y = parseFloat(el.style.top) || 0;
    if (axis === "h") {
        x = 50 - ((r.width / window.innerWidth) * 50);
    } else {
        y = 50 - ((r.height / window.innerHeight) * 50);
    }
    ({ x, y } = hudClampPosition(el, { x, y }));
    el.style.left = x + "%";
    el.style.top = y + "%";
    const st = modState(hudSelId);
    st.x = x;
    st.y = y;
    saveHudLayout(hudSelId, st);
}

function setHudToolbar(on) {
    if(on)hudBeginHistory();
    const selectionHint=document.querySelector('.hud-selection-hint');if(selectionHint)selectionHint.classList.toggle('hidden',!on);
    const tb = document.getElementById("hud-toolbar");
    if (tb) tb.classList.toggle("hidden", !on);
    if (!on) hideHudGuides();
}

document.addEventListener("keydown", e => {
    if (!document.body.classList.contains("hud-edit")) return;
    const t = e.target;
    if (t && (t.tagName === "INPUT" || t.tagName === "TEXTAREA" || t.isContentEditable)) return;
    if (e.key === "Tab") {
        e.preventDefault();
        e.stopPropagation();
        hudCycleSelect(e.shiftKey ? -1 : 1);
        return;
    }
    if (e.key === " " || e.key === "Spacebar") {
        e.preventDefault();
        e.stopPropagation();
        hudResetScale();
        return;
    }
    const step = e.shiftKey ? 2 : 0.3;
    switch (e.key) {
        case "ArrowLeft": e.preventDefault(); e.stopPropagation(); hudNudgeSelected(-step, 0); break;
        case "ArrowRight": e.preventDefault(); e.stopPropagation(); hudNudgeSelected(step, 0); break;
        case "ArrowUp": e.preventDefault(); e.stopPropagation(); hudNudgeSelected(0, -step); break;
        case "ArrowDown": e.preventDefault(); e.stopPropagation(); hudNudgeSelected(0, step); break;
        default: break;
    }
}, true);

document.addEventListener("wheel", e => {
    if (!document.body.classList.contains("hud-edit")) return;
    const w = e.target && e.target.closest ? e.target.closest(".hud-widget") : null;
    if (!w) return;
    e.preventDefault();
    e.stopPropagation();
    if (w.dataset.id) {
        hudSelect(w.dataset.id);
        hudScaleById(w.dataset.id, e.deltaY < 0 ? 0.1 : -0.1);
    }
}, { passive: false, capture: true });

document.addEventListener("pointerdown", e => {
    if (!document.body.classList.contains("hud-edit")) return;
    if (e.target.closest && e.target.closest(".hud-widget")) return;
    if (e.target.closest && e.target.closest(".hud-toolbar")) return;
    if (e.target.closest && e.target.closest(".pause-tool")) return;
    if (e.target.closest && e.target.closest(".mm-layer")) return;
    hudSelect(null);
}, true);

function wireHudToolbar() {
    installHudTools();
    const ch = document.getElementById("hud-center-h");
    const cv = document.getElementById("hud-center-v");
    const sn = document.getElementById("hud-snap-toggle");
    if (ch) ch.addEventListener("click", () => hudCenterSelected("h"));
    if (cv) cv.addEventListener("click", () => hudCenterSelected("v"));
    if (sn) {
        sn.classList.toggle("off", !hudSnapOn);
        sn.setAttribute("aria-pressed", String(hudSnapOn));
        sn.title = "Snap to edges and centres · hold Shift to move freely";
        sn.addEventListener("click", () => {
            hudSnapOn = !hudSnapOn;
            sn.classList.toggle("off", !hudSnapOn);
            sn.setAttribute("aria-pressed", String(hudSnapOn));
            try { localStorage.setItem("bat.hud.snap", hudSnapOn ? "on" : "off"); } catch (e) { /* optional fallback */ }
            if (!hudSnapOn) hideHudGuides();
        });
    }
}
wireHudToolbar();

// Resize: drag any of the 4 blue corner handles — persists top-level st.scale
// (what HudRenderer scales by, 0.5 - 3.0). The corner opposite the handle
// stays pinned, Feather-style.
function makeHudResizable(el, id) {
    el.querySelectorAll(".hud-rz").forEach(handle => {
        handle.addEventListener("pointerdown", e => {
            if (!document.body.classList.contains("hud-edit")) return;
            hudSelect(id);
            if(modState(id).locked)return;
            e.preventDefault();
            e.stopPropagation();
            const corner = handle.classList.contains("hud-rz-se") ? "se"
                : handle.classList.contains("hud-rz-sw") ? "sw"
                    : handle.classList.contains("hud-rz-ne") ? "ne" : "nw";
            const st = modState(id);
            const rect = el.getBoundingClientRect();
            const startX = e.clientX;
            const startY = e.clientY;
            const startS = st.scale || 1;
            const w0 = rect.width / startS;
            const h0 = rect.height / startS;
            const keepL = corner === "se" || corner === "ne";
            const keepT = corner === "se" || corner === "sw";
            const baseL = rect.left;
            const baseT = rect.top;
            const right = rect.right;
            const bottom = rect.bottom;
            el.classList.add("dragging");
            try { handle.setPointerCapture(e.pointerId); } catch (err) { /* ignore */ }
            const move = ev => {
                // Project the pointer onto this corner's diagonal. All four
                // handles grow outwards and keep the opposite corner fixed.
                const dx = (ev.clientX - startX) * (keepL ? 1 : -1);
                const dy = (ev.clientY - startY) * (keepT ? 1 : -1);
                let s = startS + (dx * w0 + dy * h0) / Math.max(1, w0 * w0 + h0 * h0);
                s = Math.max(0.5, Math.min(3, Math.round(s * 10) / 10));
                st.scale = s;
                el.style.transform = "scale(" + s + ")";
                let L = keepL ? baseL : right - w0 * s;
                let T = keepT ? baseT : bottom - h0 * s;
                const pos = hudClampPosition(el, { x: L / window.innerWidth * 100, y: T / window.innerHeight * 100 });
                el.style.left = pos.x + "%";
                el.style.top = pos.y + "%";
                previewNativePosition(el, id);
            };
            const up = () => {
                el.classList.remove("dragging");
                handle.removeEventListener("pointermove", move);
                handle.removeEventListener("pointerup", up);
                handle.removeEventListener("pointercancel", up);
                st.x = parseFloat(el.style.left);
                st.y = parseFloat(el.style.top);
                saveHudLayout(id, st);
            };
            handle.addEventListener("pointermove", move);
            handle.addEventListener("pointerup", up);
            handle.addEventListener("pointercancel", up);
        });
    });
}

// force: true/false toggles explicitly; omitted = flip. Called from Java ESC ladder too.
function toggleHudEditor(force) {
    const layer = document.getElementById("hud-layer");
    if (!layer) {
        if (force !== false) callBat("hudeditor:open");
        return;
    }
    const on = (typeof force === "boolean") ? force : !document.body.classList.contains("hud-edit");
    document.body.classList.toggle("hud-edit", on);
    document.querySelectorAll('[data-action="hudtoggle"]').forEach(b => b.classList.toggle("active", on));
    setHudToolbar(on);
    if (!on) hudSelect(null);
    renderHudWidgets();
    // Tell Java the flag; the HudRenderer feed echoes it back as the body
    // classes (hud-edit / hud-idle) every frame, so Java stays the source of
    // truth and the page state self-heals after screen transitions.
    callBat(on ? "hudedit:on" : "hudedit:off");
}

// ============ live HUD feed (Java HudRenderer pushes this every frame) ============
function hudEl(id) {
    const layer = document.getElementById("hud-layer");
    return layer ? layer.querySelector('[data-id="' + id + '"]') : null;
}
function hudText(id, val) {
    const el = hudEl(id);
    if (!el) return;
    const box = el.querySelector(".hp");
    if (!box) return;
    if (val == null || val === "") {
        el.dataset.live = "0";
        return;
    }
    el.dataset.live = "1";
    if (box.textContent !== val) box.textContent = val;
}
function hudRows(id, arr, rowHtml) {
    const el = hudEl(id);
    if (!el) return;
    const box = el.querySelector(".hp");
    if (!box) return;
    if (!arr || !arr.length) {
        el.dataset.live = "0";
        return;
    }
    el.dataset.live = "1";
    let sig = "";
    for (let i = 0; i < arr.length; i++) sig += JSON.stringify(arr[i]) + "|";
    if (box.dataset.sig !== sig) {
        box.dataset.sig = sig;
        let html = "";
        for (let i = 0; i < arr.length; i++) html += rowHtml(arr[i]);
        box.innerHTML = html;
    }
}
function hudEsc(s) {
    return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;")
        .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}
window.__batHud = function (o) {
    try {
        if (!o || typeof o !== "object") return;
        if (!document.getElementById("hud-layer")) return;
        if (o.native) {
            nativeHud = true;
            nativeHudSizes = o.sizes || nativeHudSizes;
            if (!document.body.classList.contains("native-hud")) document.body.classList.add("native-hud");
            document.querySelectorAll("#hud-layer .hud-widget").forEach(applyNativeSize);
        }
        // Another cached page (e.g. pause settings) may have saved a change.
        if (o.config && !document.querySelector(".hud-widget.dragging") && JSON.stringify(o.config) !== JSON.stringify(CFG)) {
            CFG = o.config;
            renderHudWidgets();
        }
        // mode: "edit" = editor chrome, "idle" = live, "menu" = settings screen
        if (o.mode) {
            const edit = o.mode === "edit";
            const idle = o.mode === "idle";
            const menu = o.mode === "menu";
            if (edit !== document.body.classList.contains("hud-edit")) {
                document.body.classList.toggle("hud-edit", edit);
                document.querySelectorAll('[data-action="hudtoggle"]').forEach(b => b.classList.toggle("active", edit));
            }
            document.body.classList.toggle("hud-idle", idle);
            document.body.classList.toggle("hud-menu", menu);
            setHudToolbar(edit);
        }
        if (o.native) return; // Java owns all module text, plates, and item icons.
        hudText("fps", o.fps);
        const ce = hudEl("coords");
        if (ce && o.coords) {
            ce.dataset.live = "1";
            const cb = ce.querySelector('.hp');
            const html = hudCoordsMarkup(o.coords);
            if (cb && cb.innerHTML !== html) cb.innerHTML = html;
        } else if (ce) ce.dataset.live = "0";
        hudText("ping", o.ping);
        const cpsMatch = typeof o.cps === 'string' && o.cps.match(/LMB\s+(\d+)\s+CPS\s+RMB\s+(\d+)/);
        hudText("cps", cpsMatch ? cpsMatch[1] + ' | ' + cpsMatch[2] : o.cps);
        hudText("togglesprint", typeof o.movement === 'string' ? o.movement : o.sprint ? "Sprinting (Toggled)" : "Standing");
        hudRows("potions", o.potions, it =>
            "<u>" + hudEsc(it.n) + "<i>" + hudEsc(it.t) + "</i></u>");
        hudRows("armor", o.armor, n =>
            '<u><b class="pit"></b>' + (n ? "<span>" + hudEsc(n) + "</span>" : "") + "</u>");
        hudRows("durability", o.dur, p =>
            '<u><b class="pit"></b>' + (p ? "<span>" + hudEsc(p) + "</span>" : "") + "</u>");
        // target HUD
        const te = hudEl("target");
        if (te) {
            const tb = te.querySelector(".hp");
            if (o.target && o.target.name && tb) {
                te.dataset.live = "1";
                const sig = o.target.name + "|" + o.target.hp;
                if (tb.dataset.sig !== sig) {
                    tb.dataset.sig = sig;
                    tb.innerHTML = hudEsc(o.target.name) + "<br>" + hudEsc(o.target.hp);
                }
            } else {
                te.dataset.live = "0";
            }
        }
        // keystrokes: pressed classes + CPS counters
        const ke = hudEl("keystrokes");
        if (ke && o.keys) {
            ke.dataset.live = "1";
            ["w", "a", "s", "d", "lmb", "rmb", "space"].forEach(k => {
                const b = ke.querySelector('[data-k="' + k + '"]');
                if (b) b.classList.toggle("on", !!o.keys[k]);
            });
            const lc = ke.querySelector('[data-k="lmb"] > span:last-child');
            if (lc && o.keys.lc != null && lc.textContent !== String(o.keys.lc)) {
                lc.textContent = String(o.keys.lc);
            }
            const rc = ke.querySelector('[data-k="rmb"] > span:last-child');
            if (rc && o.keys.rc != null && rc.textContent !== String(o.keys.rc)) {
                rc.textContent = String(o.keys.rc);
            }
        }
    } catch (e) {
        console.warn("[bat] hud feed error: " + e);
    }
};

// ---- init ----
// The main menu has the user pill; overlay/pause/respacks pages do not.
const userNameEl = document.getElementById("user-name");
if (userNameEl) {
    const playerName = getSessionName() || "BatPlayer";
    userNameEl.textContent = playerName;
    drawAvatar(playerName);
    console.log("[bat] init ok, player=" + playerName);
} else if (document.body.classList.contains("pause-page")) {
    console.log("[bat] init ok, pause, player=" + (getSessionName() || "BatPlayer"));
} else if (document.body.classList.contains("respacks-page")) {
    console.log("[bat] init ok, respacks");
} else {
    console.log("[bat] init ok, overlay, player=" + (getSessionName() || "BatPlayer"));
}

// Hide "Open to LAN" unless we are the host of a singleplayer world.
const lanBtn = document.getElementById("pause-lan");
if (lanBtn && typeof window.batIsSingleplayer === "function") {
    try {
        if (window.batIsSingleplayer() !== "true") lanBtn.classList.add("hidden");
    } catch (e) { /* keep the button if the probe fails */ }
}

// ---- MOD MENU + HUD editor init ----
if (document.getElementById("mod-grid")) renderModGrid();
// Build widgets once; Java sets body mode (hud-idle / hud-edit) via the feed.
if (document.getElementById("hud-layer")) {
    document.body.classList.add("hud-idle");
    renderHudWidgets();
}

// Ease the card grid wheel movement without delaying HUD wheel scaling.
document.querySelectorAll('.mm-body').forEach(scroller => {
    let target = 0, frame = 0, previousTime = 0;
    const tick = time => {
        if (!document.body.classList.contains('panel-open')) { frame = 0; return; }
        const elapsed = previousTime ? Math.max(1, Math.min(100, time - previousTime)) : 16;
        previousTime = time;
        target = Math.max(0, Math.min(scroller.scrollHeight - scroller.clientHeight, target));
        const distance = target - scroller.scrollTop;
        if (Math.abs(distance) < 1) { scroller.scrollTop = target; frame = 0; return; }
        scroller.scrollTop += distance * (1 - Math.exp(-elapsed / 42));
        frame = requestAnimationFrame(tick);
    };
    scroller.addEventListener('wheel', event => {
        if (event.ctrlKey || event.target.closest('input,textarea,select,.ms-picker')) return;
        if (scroller.scrollHeight <= scroller.clientHeight) return;
        if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
        event.preventDefault();
        if (!frame) { target = scroller.scrollTop; previousTime = 0; }
        const unit = event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? scroller.clientHeight : 1;
        target = Math.max(0, Math.min(scroller.scrollHeight - scroller.clientHeight, target + Math.max(-scroller.clientHeight*.8,Math.min(scroller.clientHeight*.8,event.deltaY * unit))));
        if (!frame) frame = requestAnimationFrame(tick);
    }, { passive: false });
    scroller.addEventListener('pointerdown', () => {
        if (frame) cancelAnimationFrame(frame);
        frame = 0;
    });
    scroller.addEventListener('keydown', () => {
        if (frame) cancelAnimationFrame(frame);
        frame = 0;
    });
});

// Drive entrance motion explicitly: cached Ultralight pages must animate on each activation.
let batMotionSerial = 0;
function batAnimateEntrance(element, duration = 320) {
    if (!element || element.classList.contains('hidden')) return;
    if (element.id === 'view-mods' || element.classList.contains('mm-win')) {
        element.style.opacity = '';
        delete element.dataset.batMotion;
        return;
    }
    if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const serial = ++batMotionSerial;
    element.dataset.batMotion = String(serial);
    element.style.opacity = '0';
    let started = null;
    const step = time => {
        if (element.dataset.batMotion !== String(serial)) return;
        if (started === null) started = time;
        const progress = Math.min(1, (time - started) / duration);
        const eased = 1 - Math.pow(1 - progress, 3);
        element.style.opacity = String(eased);
        if (progress < 1) requestAnimationFrame(step);
        else { element.style.opacity = ''; delete element.dataset.batMotion; }
    };
    requestAnimationFrame(step);
}
function batMenuEnter() {
    if (document.body.classList.contains('panel-open')) return;
    batAnimateEntrance(document.querySelector('.overlay-center'));
    batAnimateEntrance(document.querySelector('.pause-center'));
    batAnimateEntrance(document.querySelector('.main-menu'));
}
requestAnimationFrame(batMenuEnter);

let nativePreviewFrame = 0, nativePreviewSignature = '', nativePreviewConfig = '';
function clearNativePreview() {
    if (nativePreviewFrame) cancelAnimationFrame(nativePreviewFrame);
    nativePreviewFrame = 0;
    nativePreviewSignature = '';
    nativePreviewConfig = '';
    if (typeof window.batSetHudPreview === 'function') window.batSetHudPreview('null');
}
function reportNativePreview() {
    if (nativePreviewFrame) cancelAnimationFrame(nativePreviewFrame);
    nativePreviewFrame = 0;
    if (!msMod || !(msMod.widget || msMod.nativePreview) || !document.body.classList.contains('panel-open')) { clearNativePreview(); return; }
    const box = document.getElementById('ms-preview');
    const wrap = document.getElementById('ms-preview-wrap');
    const picker = document.getElementById('cp-veil');
    let payload = 'null';
    if (box && wrap && !wrap.classList.contains('hidden') && (!picker || picker.classList.contains('hidden'))) {
        const rect = box.getBoundingClientRect();
        const clip = document.querySelector('.mm-body').getBoundingClientRect();
        payload = JSON.stringify({ id: msMod.id, left: Math.max(rect.left, clip.left) / innerWidth,
            top: Math.max(rect.top, clip.top) / innerHeight, right: Math.min(rect.right, clip.right) / innerWidth,
            bottom: Math.min(rect.bottom, clip.bottom) / innerHeight });
    }
    if (payload !== nativePreviewSignature) {
        window.batSetHudPreview(payload);
        nativePreviewSignature = payload;
    }
    // Preview geometry must never replace saved settings from another cached page.
    // Track changing preview geometry during entrance fades, then stop scheduling idle frames.
    if (document.querySelector('[data-bat-motion]')) nativePreviewFrame = requestAnimationFrame(reportNativePreview);
}

function scheduleNativePreview() {
    if (!nativeHud || !msMod || typeof window.batSetHudPreview !== 'function' || nativePreviewFrame) return;
    nativePreviewFrame = requestAnimationFrame(reportNativePreview);
}
document.querySelectorAll('.mm-body').forEach(el => el.addEventListener('scroll', scheduleNativePreview));
window.addEventListener('resize', scheduleNativePreview);
