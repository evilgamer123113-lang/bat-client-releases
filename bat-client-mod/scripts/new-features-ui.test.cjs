const {app,BrowserWindow}=require('electron');
const path=require('node:path');
const fs=require('node:fs');
app.disableHardwareAcceleration();
app.whenReady().then(async()=>{
    const win=new BrowserWindow({show:false,width:1280,height:800,webPreferences:{partition:'bat-new-features-test',backgroundThrottling:false}});
    win.webContents.on('console-message',(_event,level,message)=>{if(level>=2)console.error(message);});
    try {
        await win.loadFile(path.join(process.env.BAT_WEB_TEST_ROOT || path.join(__dirname,'../src/main/resources/assets/batclient/web'),'overlay.html'));
        const result=await win.webContents.executeJavaScript(`(${function(){
            let checks=0;const assert=(ok,message)=>{if(!ok)throw Error(message);checks++;};
            const backend={modules:{},bindings:{},activeProfile:'Default',profiles:{Default:{modules:{}},'<PvP & Recording>':{modules:{}}}};
            const calls=[];
            window.batGetHudConfig=()=>JSON.stringify(backend);
            window.batSaveHudModule=(id,raw)=>{
                const patch=JSON.parse(raw),mod=backend.modules[id] || patch._initial || {};
                for(const k in patch)if(k!=='_initial')mod[k]=k==='set'?Object.assign(mod.set||{},patch.set):patch[k];
                backend.modules[id]=mod;return JSON.stringify(backend);
            };
            window.batGetKeybinds=()=>JSON.stringify({bindings:Object.assign({menu:'key.keyboard.right.shift',zoom:'key.keyboard.c',freelook:'key.keyboard.left.alt',perspective:'key.keyboard.p'},backend.bindings),controls:[{name:'Save hotbar',key:'key.keyboard.c'}]});
            window.batSetKeybind=(id,key)=>{if(key)backend.bindings[id]=key;else delete backend.bindings[id];return JSON.stringify(backend);};
            window.batProfiles=(action,name)=>{
                calls.push({action,name});
                if(action==='load')backend.activeProfile=name;
                if(action==='create'){if(backend.profiles[name])return JSON.stringify({error:'That profile already exists.'});backend.profiles[name]={modules:{}};backend.activeProfile=name;}
                if(action==='delete')delete backend.profiles[name];
                return JSON.stringify({error:'',config:JSON.stringify(backend)});
            };
            document.body.classList.remove('hud-idle');document.body.classList.add('hud-menu');
            cfgLoad();openPanel('mods');
            for(const id of ['shulkerpreview','equipmentcomparison','raincustomizer','elytrahud','shieldindicator','freelook','lootbeams','returntrail']){
                const m=modDef(id);assert(m&&!m.def,id+' defaults off');assert(Boolean(m.widget)===['elytrahud','shieldindicator'].includes(id),id+' correct placement');
                assert(MODULE_ART[id]&&MODULE_ART[id].length>40,id+' artwork');openModSettings(id);
                const control=document.querySelector('.ms-ctl input');assert(control,id+' settings');const key=control.closest('.ms-ctl').dataset.key;
                const value=control.type==='checkbox'?!control.checked:Number(control.max);if(control.type==='checkbox')control.click();else{control.value=value;control.dispatchEvent(new Event('input',{bubbles:true}));}
                closeModSettings();cfgLoad();assert(modState(id).set[key]===value,id+' setting saved');
            }
            openModSettings('freelook');Array.from(document.querySelectorAll('.ms-ctl[data-key="mode"] button')).find(b=>b.textContent==='Toggle').click();closeModSettings();cfgLoad();assert(modState('freelook').set.mode==='Toggle','freelook activation mode persists');renderKeybindManager();assert(document.querySelector('[data-action="freelook"]').textContent.startsWith('Freelook'),'freelook key action controls orbit instead of generic module toggle');
            openModSettings('freelook');assert(document.querySelector('.ms-action-bind').textContent==='LEFT ALT','inline bind displays native default');
            document.querySelector('.ms-action-bind').click();assert(window.__batInlineKeyCapture==='freelook','inline starts native Freelook capture');window.__batManagerKeyCaptured('freelook','key.mouse.4');closeModSettings();cfgLoad();openModSettings('freelook');assert(document.querySelector('.ms-action-bind').textContent==='4'&&backend.bindings.freelook==='key.mouse.4','mouse binding persists on reopen');assert(!backend.bindings.zoom&&!modState('freelook').set.key,'binding uses shared action without altering zoom or legacy module key');
            document.querySelector('.ms-action-bind').click();window.__batManagerKeyCaptured('freelook',null);assert(backend.bindings.freelook==='key.mouse.4','Escape capture preserves binding');document.querySelector('.ms-action-bind').click();closeModSettings();assert(!window.__batInlineKeyCapture&&!window.__batManagerKeyCaptured,'closing settings cancels capture');
            let routeStatus={points:12,remaining:8,returning:false,recording:true};window.batReturnTrail=action=>{if(action==='retrace')routeStatus.returning=true;if(action==='resume')routeStatus.returning=false;if(action==='clear')routeStatus.points=0;return JSON.stringify(routeStatus);};openModSettings('returntrail');assert(document.querySelector('.trail-status').textContent.includes('12 breadcrumbs'),'trail status visible');Array.from(document.querySelectorAll('.trail-actions button')).find(b=>b.textContent==='Retrace route').click();assert(document.querySelector('.trail-status').textContent.includes('Retracing'),'retrace action feedback');Array.from(document.querySelectorAll('.trail-actions button')).find(b=>b.textContent==='Resume recording').click();assert(document.querySelector('.trail-status').textContent.includes('Recording'),'resume action feedback');Array.from(document.querySelectorAll('.trail-actions button')).find(b=>b.textContent==='Clear current trail').click();assert(document.querySelector('.trail-status').textContent.includes('0 breadcrumbs'),'clear action feedback');assert(!document.querySelector('.setting-reset,#ms-reset'),'trail does not add setting reset controls');
            assert(moduleSearchMatches(modDef('raincustomizer'),'opacity'),'settings label search matches module');
            assert(!moduleSearchMatches(modDef('raincustomizer'),'qzx-nonexistent'),'unknown search has no match');
            closeModSettings();mmQuery='rain opacity';mmCat='all';renderModGrid();
            const settingResult=document.querySelector('.setting-search-result');assert(settingResult&&settingResult.textContent==='Rain Opacity','search renders direct setting result');
            settingResult.click();assert(msMod.id==='raincustomizer'&&document.querySelector('.ms-row.search-highlight'),'search opens and highlights setting');
            window.batGetModuleConflicts=()=>JSON.stringify({shulkerpreview:'Shulker Box Tooltip detected. Preview overlap.'});openModSettings('shulkerpreview');assert(document.querySelector('.module-conflict-notice').textContent.includes('Tooltip detected'),'detected conflict shown');
            openModSettings('raincustomizer');assert(!document.querySelector('.module-conflict-notice'),'no invented conflict for unrelated module');
            delete window.batGetModuleConflicts;closeModSettings();mmQuery='';renderModGrid();
            for(const id of ['skinlayers3d','foodpreview','tntcountdown','bowcharge','deathcoords','mounthud']){
                const def=modDef(id);assert(def&&!def.def,id+' starts off');
                assert(!!def.widget===['foodpreview','bowcharge','deathcoords','mounthud'].includes(id),id+' correct HUD placement');
                assert(MODULE_ART[id]&&MODULE_ART[id].length>40,id+' has dedicated vector artwork');
                openModSettings(id);assert(document.querySelectorAll('.ms-ctl').length>0,id+' settings open');
                const toggle=document.querySelector('.ms-ctl input[type="checkbox"]');
                const slider=document.querySelector('.ms-ctl input[type="range"]');
                const control=toggle||slider,key=control.closest('.ms-ctl').dataset.key;
                let expected;
                if(toggle){expected=!toggle.checked;toggle.click();}else{slider.value=slider.max;expected=Number(slider.max);slider.dispatchEvent(new Event('input',{bubbles:true}));}
                closeModSettings();cfgLoad();assert(modState(id).set[key]===expected,id+' settings persist');
            }
            for(const id of ['minimap','hitcolor']){
                const definition=modDef(id);assert(definition&&!definition.def,id+' defaults off');
                assert(Boolean(definition.widget)===(id==='minimap'),id+' placement is correct');
                openModSettings(id);const slider=document.querySelector('.ms-ctl input[type="range"]');assert(slider,id+' exposes settings');
                const key=slider.closest('.ms-ctl').dataset.key;slider.value=slider.max;slider.dispatchEvent(new Event('input',{bubbles:true}));const expected=Number(slider.max);
                closeModSettings();cfgLoad();assert(Number(modState(id).set[key])===expected,id+' setting persists');
            }
            assert(!modDef('chatcopy'),'chat copying is built in, not a module');
            for(const id of ['itemphysics','itemtracker','blockoutline','hitboxes','hurtcamera','glintcolor']){
                const definition=modDef(id);assert(definition&&!definition.def,id+' is opt-in');
                assert(Boolean(definition.widget)===(id==='itemtracker'),id+' has correct native versus HUD placement');
                openModSettings(id);
                const slider=document.querySelector('.ms-ctl input[type="range"]');assert(slider,id+' has adjustable settings');
                const key=slider.closest('.ms-ctl').dataset.key;slider.value=slider.max;slider.dispatchEvent(new Event('input',{bubbles:true}));
                const expected=Number(slider.max);closeModSettings();cfgLoad();assert(Number(modState(id).set[key])===expected,id+' settings persist');
            }
            assert(!MODULES.some(module=>/System Resources/i.test(module.name)),'System Resources HUD was excluded');
            openModSettings('blockoutline');assert(document.querySelector('.ms-ctl[data-key="gradient"] input')&&document.querySelector('.ms-ctl[data-key="gradientColor"]'),'block outline offers two-color gradients');closeModSettings();
            assert(MODULES.every(module=>module.icon.includes('class="module-art"')),'every module has purpose-drawn artwork');
            assert(document.querySelectorAll('#mod-grid svg.module-art').length===MODULES.length,'module artwork renders throughout catalog');
            assert([...document.querySelectorAll('#mod-grid svg.module-art')].every(svg=>svg.getAttribute('viewBox')==='0 0 64 48'&&svg.getAttribute('aria-hidden')==='true'),'artwork shares consistent proportions and remains decorative');
            for(const id of ['iteminfo','compass','clock','speedometer','resourcepacks','combo','reach','inventory','totems','arrows']){
                const module=modDef(id);assert(module&&module.widget&&!module.def,id+' is an opt-in HUD module');
                openModSettings(id);
                assert(document.querySelector('.ms-ctl[data-key="shadow"] input'),id+' has native text-shadow setting');
                const slider=document.querySelector('.ms-ctl[data-key="scale"] input');slider.value='1.8';slider.dispatchEvent(new Event('input',{bubbles:true}));
                closeModSettings();cfgLoad();openModSettings(id);assert(modState(id).scale===1.8,id+' scale persists');closeModSettings();
            }
            openModSettings('iteminfo');assert(document.querySelector('.ms-ctl[data-key="enchantments"] input'),'item enchantment option');closeModSettings();
            for(const id of ['totems','arrows']){
                openModSettings(id);
                assert(document.querySelector('.ms-ctl[data-key="offhand"] input').checked,id+' includes offhand by default');
                assert(document.querySelector('.ms-ctl[data-key="warningColor"]'),id+' has warning color');
                const limit=document.querySelector('.ms-ctl[data-key="warningCount"] input');limit.value=id==='totems'?'2':'32';limit.dispatchEvent(new Event('input',{bubbles:true}));
                document.querySelector('.ms-ctl[data-key="hideZero"] input').click();
                document.querySelector('.ms-ctl[data-key="offhand"] input').click();
                closeModSettings();cfgLoad();
                assert(modState(id).set.warningCount===(id==='totems'?2:32)&&modState(id).set.hideZero&&!modState(id).set.offhand,id+' count settings persist');
            }
            openModSettings('arrows');assert(document.querySelector('.ms-ctl[data-key="specialArrows"] input').checked,'special arrow counting can be configured');closeModSettings();
            for(const id of ['chatanimations','chatheads','chatrepeats'])assert(!modDef(id),id+' is built in, not a module');
            assert(!modDef('smoothchunks'),'chunk scheduling is built in, not a module');
            for(const id of ['armor','durability']){
                openModSettings(id);
                assert(document.querySelector('.ms-ctl[data-key="lowWarning"] input').checked,id+' warning enabled');
                assert(document.querySelector('.ms-ctl[data-key="warningColor"]'),id+' warning color');
                const threshold=document.querySelector('.ms-ctl[data-key="warningPercent"] input');threshold.value='20';threshold.dispatchEvent(new Event('input',{bubbles:true}));
                document.querySelector('.ms-ctl[data-key="warningFlash"] input').click();
                closeModSettings();cfgLoad();
                assert(modState(id).set.warningPercent===20&&!modState(id).set.warningFlash,id+' warning settings persist');
            }
            const snapButton=document.getElementById('hud-snap-toggle');
            const initialSnap=hudSnapOn;snapButton.click();
            assert(hudSnapOn!==initialSnap&&snapButton.getAttribute('aria-pressed')===String(hudSnapOn),'snap toolbar reflects toggle');
            assert(localStorage.getItem('bat.hud.snap')===(hudSnapOn?'on':'off'),'snap preference saved');snapButton.click();
            openModSettings('potions');
            assert(document.querySelector('.ms-ctl[data-key="expiryWarning"] input').checked,'expiry warning default on in existing potion module');
            assert(document.querySelector('.ms-ctl[data-key="warningColor"]'),'warning color picker');
            const warning=document.querySelector('.ms-ctl[data-key="warningSeconds"] input');warning.value='15';warning.dispatchEvent(new Event('input',{bubbles:true}));closeModSettings();cfgLoad();
            assert(modState('potions').set.warningSeconds===15,'potion warning threshold persists');
            openModSettings('inventory');document.querySelector('.ms-ctl[data-key="hotbar"] input').click();closeModSettings();cfgLoad();assert(modState('inventory').set.hotbar,'inventory hotbar setting persists');
            openModSettings('combo');const timeout=document.querySelector('.ms-ctl[data-key="timeout"] input');timeout.value='4.5';timeout.dispatchEvent(new Event('input',{bubbles:true}));closeModSettings();cfgLoad();assert(modState('combo').set.timeout===4.5,'combo timeout persists');
            openModSettings('reach');assert(document.querySelector('.ms-ctl[data-key="playersOnly"] input'),'reach target filtering available');
            const range=document.querySelector('.ms-ctl[data-key="range"] input');assert(range&&!document.querySelector('.ms-ctl[data-key="timeout"]'),'live reach uses target detection instead of last-hit duration');
            range.value='40';range.dispatchEvent(new Event('input',{bubbles:true}));closeModSettings();cfgLoad();assert(modState('reach').set.range===40,'live reach detection range persists');
            openModSettings('resourcepacks');
            const packWidth=document.querySelector('.ms-ctl[data-key="textWidth"] input');
            assert(packWidth&&packWidth.max==='320'&&!document.querySelector('.ms-ctl[data-key="maxPacks"]'),'single pack has width control instead of list settings');
            packWidth.value='240';packWidth.dispatchEvent(new Event('input',{bubbles:true}));closeModSettings();cfgLoad();
            assert(modState('resourcepacks').set.textWidth===240,'resource pack settings persist');
            openModSettings('resourcepacks');
            for(const key of ['showIcon','showName','showDescription','originalColors']) {
                const input=document.querySelector('.ms-ctl[data-key="'+key+'"] input');
                assert(input&&input.checked,'pack setting '+key+' defaults on');input.click();
            }
            for(const [key,value] of [['iconSize','48'],['gap','12'],['textScale','1.5'],['lineSpacing','3'],['nameLines','1'],['descriptionLines','6']]) {
                const input=document.querySelector('.ms-ctl[data-key="'+key+'"] input');assert(input,'pack control '+key);
                input.value=value;input.dispatchEvent(new Event('input',{bubbles:true}));
            }
            document.querySelector('.ms-ctl[data-key="iconPosition"] button[data-v="Above"]').click();
            document.querySelector('.ms-ctl[data-key="alignment"] button[data-v="Center"]').click();
            assert(document.querySelector('.ms-ctl[data-key="descriptionColor"]'),'MOTD color picker present');
            closeModSettings();cfgLoad();const packSettings=modState('resourcepacks').set;
            assert(!packSettings.showIcon&&!packSettings.showName&&!packSettings.showDescription&&!packSettings.originalColors,'pack visibility/color overrides persist');
            assert(packSettings.iconSize===48&&packSettings.gap===12&&packSettings.textScale===1.5&&packSettings.lineSpacing===3&&packSettings.nameLines===1&&packSettings.descriptionLines===6,'pack typography/spacing limits persist');
            assert(packSettings.iconPosition==='Above'&&packSettings.alignment==='Center','pack layout choices persist');
            openModSettings('togglesprint');
            for(const key of ['autoSprint','flightSpeed']) {
                const input=document.querySelector('.ms-ctl[data-key="'+key+'"] input');
                assert(input&&input.checked,'movement setting '+key+' defaults on');input.click();
            }
            closeModSettings();cfgLoad();assert(!modState('togglesprint').set.autoSprint&&!modState('togglesprint').set.flightSpeed,'status-only and plain flying settings persist');
            assert(modDef('inventoryanimations')&&modDef('inventoryanimations').def&&!modDef('inventoryanimations').widget,'inventory animation defaults on and is not a HUD widget');
            assert(modDef('healthindicator')&&!modDef('healthindicator').def&&!modDef('healthindicator').widget&&modDef('healthindicator').nativePreview,'health indicator is opt-in native world module');
            openModSettings('healthindicator');
            assert(modDef('healthindicator').defaults.occluded&&!modDef('healthindicator').defaults.showValue,'native hearts default on with faint occluded visibility and optional numbers');
            assert(document.querySelector('.ms-ctl[data-key="occluded"] input')&&document.querySelector('.ms-ctl[data-key="showValue"] input'),'heart visibility and number settings are exposed');
            assert(document.querySelector('.ms-ctl[data-key="players"] input')&&document.querySelector('.ms-ctl[data-key="mobs"] input'),'world health supports player and mob filters');
            const healthRange=document.querySelector('.ms-ctl[data-key="range"] input');healthRange.value='32';healthRange.dispatchEvent(new Event('input',{bubbles:true}));
            closeModSettings();cfgLoad();assert(modState('healthindicator').set.range===32,'health indicator range persists');
            openModSettings('target');
            assert(document.querySelector('.ms-ctl[data-key="portrait"] input')&&document.querySelector('.ms-ctl[data-key="smooth"] input'),'target HUD has portrait and smooth bar settings');
            const hold=document.querySelector('.ms-ctl[data-key="holdTime"] input');hold.value='1.5';hold.dispatchEvent(new Event('input',{bubbles:true}));
            closeModSettings();cfgLoad();assert(modState('target').set.holdTime===1.5,'target hold duration persists');
            assert(modDef('motionblur')&&!modDef('motionblur').def&&!modDef('motionblur').widget,'motion blur is an opt-in world effect');
            openModSettings('motionblur');
            const blur=document.querySelector('.ms-ctl[data-key="strength"] input');
            assert(blur&&blur.min==='0'&&blur.max==='85','bounded motion blur strength control');
            blur.value='65';blur.dispatchEvent(new Event('input',{bubbles:true}));closeModSettings();cfgLoad();
            assert(modState('motionblur').set.strength===65,'motion blur strength persists through native bridge');
            switchView('keybinds');
            assert(!document.querySelector('.bat-binding-row[data-action="chatheads"],.bat-binding-row[data-action="chatanimations"],.bat-binding-row[data-action="chatrepeats"]'),'built-in chat has no toggle keybind rows');
            assert(document.querySelector('[data-view="keybinds"]').classList.contains('active'),'keybind nav active');
            assert(document.querySelector('[data-action="zoom"]').textContent.includes('Save hotbar'),'Minecraft key conflict shown');
            let row=document.querySelector('.bat-binding-row[data-action="iteminfo"]');
            backend.modules.iteminfo.anchor='BOTTOM_RIGHT';backend.modules.iteminfo.offset_x=-20;backend.modules.iteminfo.offset_y=-30;cfgLoad();
            row.querySelector('button').click();window.__batManagerKeyCaptured('iteminfo','key.mouse.4');
            assert(backend.bindings.iteminfo==='key.mouse.4','mouse capture persisted through native bridge');
            assert(backend.modules.iteminfo.anchor==='BOTTOM_RIGHT'&&backend.modules.iteminfo.offset_x===-20,'binding edits preserve native HUD anchors');
            row=document.querySelector('.bat-binding-row[data-action="iteminfo"]');assert(row.textContent.includes('MOUSE 4'),'mouse caption updated');
            row.querySelectorAll('button')[1].click();assert(backend.bindings.iteminfo==='key.keyboard.unknown','clear unbinds action');
            row=document.querySelector('.bat-binding-row[data-action="iteminfo"]');row.querySelectorAll('button')[2].click();assert(!backend.bindings.iteminfo,'reset restores default');
            row=document.querySelector('.bat-binding-row[data-action="iteminfo"]');row.querySelector('button').click();window.__batManagerKeyCaptured('iteminfo',null);assert(!backend.bindings.iteminfo,'cancel leaves binding unchanged');
            switchView('profiles');
            assert(document.querySelector('[data-view="keybinds"] svg')&&document.querySelector('[data-view="profiles"] svg'),'both manager tabs have native SVG icons');
            assert(document.querySelector('#view-profiles h2 svg'),'profile heading uses matching SVG');
            assert(document.querySelectorAll('.bat-profile-card')[1].querySelector('strong').textContent==='<PvP & Recording>','profile names displayed as text');
            assert(!document.querySelector('#view-profiles img'),'profile names cannot inject markup');
            assert(document.querySelector('.bat-profile-card .bat-profile-badge').textContent==='ACTIVE','active profile badge shown');
            assert(document.querySelector('[data-profile-action="delete"]').disabled,'active profile protected from deletion');
            assert(document.querySelector('[data-profile-action="create"]').disabled,'empty profile name cannot be submitted');
            document.querySelectorAll('.bat-profile-card')[1].click();
            assert(document.querySelectorAll('.bat-profile-card')[1].getAttribute('aria-pressed')==='true','selected card is accessible');
            assert(!document.querySelector('[data-profile-action="delete"]').disabled,'inactive profile can be deleted');
            assert(backend.activeProfile==='Default','selecting a card does not silently change active profile');
            document.querySelector('[data-profile-action="load"]').click();assert(calls.some(c=>c.action==='load'&&c.name==='<PvP & Recording>'),'switch sends exact profile name');
            const fillName=value=>{const input=document.querySelector('#bat-profile-name');input.value=value;input.dispatchEvent(new Event('input',{bubbles:true}));};
            fillName('Survival');document.querySelector('[data-profile-action="create"]').click();assert(backend.activeProfile==='Survival','create uses current profile');
            fillName('Survival');document.querySelector('[data-profile-action="create"]').click();assert(document.getElementById('profiles-message').textContent.includes('already exists'),'native save error displayed');
            document.querySelector('.bat-profile-card').click();const deleteCalls=calls.filter(c=>c.action==='delete').length;
            document.querySelector('[data-profile-action="delete"]').click();assert(calls.filter(c=>c.action==='delete').length===deleteCalls,'first delete click only asks for confirmation');
            document.querySelector('[data-profile-action="delete"]').click();assert(!backend.profiles.Default,'confirmed inactive profile deleted');
            openModSettings('elytrahud');modState('elytrahud').set.flightTime=true;cfgSave('elytrahud',{set:{flightTime:true}});renderModSettings();
            let flight=document.querySelector('.ms-ctl[data-key="flightTime"] input');flight.click();assert(document.querySelector('.ms-ctl[data-key="flightTime"]').closest('.ms-row').classList.contains('setting-modified'),'changed setting marked');
            assert(!document.querySelector('.setting-reset,#ms-reset,#cp-reset'),'module and color settings have no reset buttons');document.querySelector('.ms-ctl[data-key="flightTime"] input').click();assert(modState('elytrahud').set.flightTime===true,'setting can still be changed and saved normally');
            document.querySelector('.ms-ctl[data-key="flightTime"] input').click();document.querySelector('.changed-settings-tools button').click();assert(!document.querySelector('.ms-ctl[data-key="altitude"]'),'changed filter hides unchanged controls');
            assert(document.querySelector('.ms-ctl[data-key="flightTime"]'),'changed filter retains changed control');closeModSettings();
            document.body.classList.add('hud-edit');document.body.classList.remove('panel-open');
            for(const [id,x] of [['fps',5],['coords',35],['ping',75]]){const st=modState(id);st.en=true;st.x=x;st.y=20;st.scale=1;st.locked=false;cfgSave(id);}
            renderHudWidgets();for(const id of ['fps','coords','ping']){const el=hudEl(id);el.style.width='100px';el.style.height='24px';}
            hudSelect('fps');hudBeginHistory();const initialX=modState('fps').x;hudNudgeSelected(2,0);assert(modState('fps').x>initialX,'nudge updates layout');hudUndo();assert(modState('fps').x===initialX,'undo restores layout');hudRedo();assert(modState('fps').x===initialX+2,'redo reapplies layout');
            hudToggleLock();const lockedX=modState('fps').x;hudNudgeSelected(3,0);hudScaleById('fps',.2);assert(modState('fps').x===lockedX&&modState('fps').scale===1,'locked widget refuses move and resize');cfgLoad();assert(modState('fps').locked===true,'lock persists');hudUndo();assert(!modState('fps').locked,'lock undo works');
            hudSelect(null);hudSelect('fps',true);hudSelect('coords',true);hudSelect('ping',true);assert(hudSelectedIds.size===3,'multi selection retains three widgets');
            assert(hudDistribute('x'),'distribution accepts three widgets');
            const a=hudEl('fps').getBoundingClientRect(),b=hudEl('coords').getBoundingClientRect(),c=hudEl('ping').getBoundingClientRect();assert(Math.abs((b.left-a.right)-(c.left-b.right))<2,'distribution produces equal edge gaps');
            hudUndo();assert(modState('coords').x===35,'group spacing is one undo transaction');document.body.classList.remove('hud-edit');
            let serverManual=false;window.batServerProfiles=(action,name)=>{if(action==='override')serverManual=true;if(action==='resume')serverManual=false;backend.serverProfiles=backend.serverProfiles||{};if(action==='bind')backend.serverProfiles['play.example.test']=name;if(action==='clear')delete backend.serverProfiles['play.example.test'];return JSON.stringify({key:'play.example.test',override:serverManual,error:'',config:JSON.stringify(backend)});};
            openPanel('profiles');assert(document.getElementById('server-profile-settings').textContent.includes('play.example.test'),'server profile menu identifies connection');
            document.querySelector('#server-profile-settings button').click();assert(backend.serverProfiles['play.example.test']===backend.activeProfile,'server assignment persists through bridge');
            document.querySelectorAll('#server-profile-settings button')[1].click();assert(!backend.serverProfiles['play.example.test'],'server assignment can be removed');
            document.querySelectorAll('#server-profile-settings button')[2].click();assert(document.getElementById('server-profile-settings').textContent.includes('Manual override'),'manual override shown');delete window.batServerProfiles;
            return {checks,status:'NEW_MODULES_KEYBINDS_PROFILES_UI_OK'};
        }.toString()})()`);
        console.log(JSON.stringify(result));
        const scrolling=await win.webContents.executeJavaScript(`(async()=>{
            closeModSettings();document.body.classList.remove('hud-edit');openPanel('mods');mmQuery='';mmCat='all';renderModGrid();
            const scroller=document.querySelector('.mm-body');scroller.scrollTop=0;const before=scroller.scrollTop;
            scroller.dispatchEvent(new WheelEvent('wheel',{deltaY:100000,bubbles:true,cancelable:true}));
            const times=[];let previous=performance.now();for(let i=0;i<6;i++)await new Promise(resolve=>requestAnimationFrame(now=>{times.push(now-previous);previous=now;resolve();}));
            if(scroller.scrollTop<=before||scroller.scrollTop>scroller.clientHeight*.81)throw Error('wheel burst should advance smoothly within one capped burst');
            scroller.dispatchEvent(new WheelEvent('wheel',{deltaY:-100000,bubbles:true,cancelable:true}));
            await new Promise(resolve=>setTimeout(resolve,450));if(scroller.scrollTop<0||!Number.isFinite(scroller.scrollTop))throw Error('scroll cannot overshoot bounds');
            times.sort((a,b)=>a-b);return {status:'MENU_SCROLL_DIAGNOSTIC_OK',frames:times.length,p95ms:times[Math.floor(times.length*.95)]};
        })()`);
        console.log(JSON.stringify(scrolling));

        for(const view of ['keybinds','profiles']){
            await win.webContents.executeJavaScript(`openPanel('${view}');document.body.style.background='#090909'`);
            await win.webContents.executeJavaScript(`document.getAnimations().forEach(a=>a.finish())`);
            await win.webContents.executeJavaScript(`document.querySelectorAll('[data-bat-motion]').forEach(el=>{delete el.dataset.batMotion;el.style.opacity='1';})`);
            await new Promise(resolve=>setTimeout(resolve,750));
            fs.writeFileSync(path.join(__dirname,'../build/'+view+'-preview.png'),(await win.webContents.capturePage()).toPNG());
        }
        app.exit(0);
    }catch(error){console.error(error.stack);app.exit(1);}
});
