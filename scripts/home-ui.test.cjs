// Real launcher renderer with isolated userData and mocked main-process IPC.
// Never loads main.js, signs in, updates files or starts Minecraft.
const {app,BrowserWindow,ipcMain}=require('electron');
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),output=path.join(root,'build','home-ui-preview');
fs.mkdirSync(output,{recursive:true});app.setPath('userData',path.join(output,'test-user-data'));
app.disableHardwareAcceleration();
const checkMotion=process.env.BAT_UI_MOTION==='1';
const checkMods=process.env.BAT_UI_MODS==='1';
const checkWardrobe=process.env.BAT_UI_WARDROBE==='1';
let modDelay=0;
if(!checkMotion)app.commandLine.appendSwitch('force-prefers-reduced-motion');
const profiles=[{id:'survival',name:'Survival',version:'1.21.1',engine:'fabric',ram:'4',ramOverride:true},{id:'classic',name:'Classic PvP',version:'1.8.9',engine:'vanilla',ram:'3',ramOverride:true,favorite:true}];
const settings={language:'en',globalRam:'4',ram:'4',javaPath:'',enableSounds:false,clickSounds:false,autoCheckUpdates:false};
let active='survival',accounts=[{username:'Raven',type:'offline',uuid:'00000000-0000-0000-0000-000000000000'}];
const sent=[],copied=[],invoked=[];let win,checks=0;
const browseFixtures=[
    {project_id:'sodium',title:'Sodium',description:'A rendering engine that improves Minecraft performance.',author:'CaffeineMC',downloads:1200000,categories:['optimization']},
    {project_id:'lithium',title:'Lithium',description:'Game logic optimizations for your next world.',author:'CaffeineMC',downloads:900000,categories:['optimization']},
    {project_id:'modmenu',title:'Mod Menu',description:'Browse your installed Fabric mods in Minecraft.',author:'Prospector',downloads:500000,categories:['utility']}
];
let installedFixtures=[{filename:'sodium.jar',displayName:'Sodium',enabled:true,isManaged:true,modrinthId:'sodium',iconDataUrl:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jXioAAAAASUVORK5CYII='},{filename:'lithium.jar.disabled',displayName:'Lithium',enabled:false,isManaged:false,modrinthId:'lithium'}];
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function check(value,message){if(!value)throw Error(message);checks++;}
function send(channel,data){win.webContents.send(channel,data);}
function syncProfiles(){send('sync-profiles-view',{profiles,activeId:active});}
function syncAccounts(){send('sync-accounts-view',{accounts,activeUser:accounts[0]?.username||null});}
app.whenReady().then(async()=>{
    try {
        const source=fs.readFileSync(path.join(root,'index.html'),'utf8');
        const invokes=[...source.matchAll(/ipcRenderer\.invoke\(['"]([^'"]+)/g)].map(m=>m[1]);
        for(const channel of new Set(invokes))ipcMain.handle(channel,async(_event,...args)=>{
            invoked.push({channel,args});
            if(channel==='export-loadouts'||channel==='import-loadouts')return{canceled:true};
            if(channel==='get-app-settings')return settings;
            if(channel==='check-first-launch')return false;
            if(channel==='check-for-update')return{updateAvailable:false};
            if(channel==='check-update-result')return null;
            if(channel==='get-system-ram')return 16;
            if(channel==='get-installed-mods-count')return 12;
            if(channel==='get-mojang-versions-list')return[{id:'1.21.1',type:'release'},{id:'1.8.9',type:'release'}];
            if(channel==='get-bat-news')return[{tag:'2.2.93',title:'More worlds. One launcher.',desc:'Bat Client is available for Windows, macOS and Linux.',link:'https://batclient.xyz/changelog.html'}];
            if(channel==='get-account-skin-data')return null;
            if(channel==='get-account-slots')return{customSkins:[],customCapes:[]};
            if(channel==='get-equipped-cosmetics')return{};
            if(channel==='get-java-status')return{};
            if(checkMods&&channel==='search-modrinth-api'){if(modDelay)await pause(modDelay);if(args[0].query==='offline')throw Error('Fixture network error');return{hits:args[0].query==='empty'?[]:browseFixtures};}
            if(checkMods&&channel==='get-installed-mods')return installedFixtures;
            if(checkMods&&channel==='get-mod-details')return{ok:true,project:{title:'Sodium',author:'CaffeineMC',description:'Rendering performance.'},versions:[]};
            if(checkMods&&channel==='check-mod-conflicts')return{conflicts:[],warnings:[]};
            if(checkMods&&channel==='toggle-mod'){const mod=installedFixtures.find(mod=>mod.filename===args[0]);mod.enabled=!mod.enabled;mod.filename=mod.enabled?mod.filename.replace(/\.disabled$/,''):mod.filename+'.disabled';return{ok:true,enabled:mod.enabled,newFilename:mod.filename};}
            if(checkMods&&channel==='check-mod-updates')return[{filename:installedFixtures[0].filename,latestVersion:'1.1',downloadUrl:'https://example.com/test-only.jar'}];
            if(checkMods&&channel==='update-mod')return{ok:true};
            if(channel==='copy-text'){copied.push(args[0]);return true;}
            return[];
        });
        for(const channel of new Set([...source.matchAll(/ipcRenderer\.send\(['"]([^'"]+)/g)].map(m=>m[1])))ipcMain.on(channel,(_event,data)=>{
            sent.push({channel,data});
            if(channel==='get-profiles-data')syncProfiles();
            if(channel==='get-accounts-data')syncAccounts();
            if(channel==='get-app-settings')send('sync-settings-view',settings);
            if(channel==='select-active-profile'){active=data;syncProfiles();}
        });
        win=new BrowserWindow({show:false,width:1280,height:800,webPreferences:{nodeIntegration:true,contextIsolation:false,backgroundThrottling:false}});
        const errors=[];
        win.webContents.on('console-message',event=>{if(/Uncaught|ReferenceError|TypeError/.test(event.message))errors.push(event.message);});
        win.webContents.session.webRequest.onBeforeRequest({urls:['http://*/*','https://*/*']},(_details,callback)=>callback({cancel:true}));
        await win.loadFile(path.join(root,'index.html'));
        if(checkMotion){win.webContents.debugger.attach('1.3');await win.webContents.debugger.sendCommand('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'no-preference'}]});}
        await win.webContents.executeJavaScript(`new Promise(resolve=>{const timer=setInterval(()=>{if(document.getElementById('startup-loading-overlay')?.classList.contains('hidden')){clearInterval(timer);resolve();}},50);setTimeout(()=>{clearInterval(timer);resolve();},10000);})`);
        await pause(4000);
        const js=code=>win.webContents.executeJavaScript(code);
        if(checkMotion){
            check(await js(`matchMedia('(prefers-reduced-motion: no-preference)').matches`),'normal motion preference enabled');
            const verifyEntrance=async selector=>check(await js(`(()=>{const node=document.querySelector(${JSON.stringify(selector)}),motion=node.getAnimations()[0];if(!motion)return false;motion.pause();motion.currentTime=0;const start=Number(getComputedStyle(node).opacity);motion.currentTime=Number(motion.effect.getTiming().duration)+Number(motion.effect.getTiming().delay);const end=Number(getComputedStyle(node).opacity);return start<.1&&end>.99&&motion.effect.getTiming().iterations===1;})()`),'entrance visibly fades once: '+selector);
            await verifyEntrance('#view-home .home-launch-panel');
            await verifyEntrance('#view-home .home-news');
            await js(`document.querySelector('[data-target="view-profiles"]').click()`);await pause(50);
            await verifyEntrance('#view-profiles .profile-cards-grid');
            check(await js(`document.querySelector('#view-profiles .profile-card.active .profile-active-badge').getAnimations().length>0`),'selected badge has short feedback');
            await js(`document.querySelector('[data-target="view-home"]').click()`);await pause(50);
            check(await js(`document.querySelector('#view-home .home-launch-panel').getAnimations().some(motion=>motion.playState==='running')`),'Home entrance replays when returning to the tab');
            check(await js(`!document.getElementById('launch-trigger-action').getAnimations().some(motion=>motion.effect.getTiming().iterations===Infinity)`),'Play has no continuous idle animation');
            if(checkMods)modDelay=350;
            await js(`document.querySelector('[data-target="view-mods"]').click()`);await pause(40);
            if(checkMods)check(await js(`document.querySelector('#mods-query-render-target .mods-skeleton').getAnimations().some(motion=>motion.effect.getTiming().iterations===Infinity)`),'loading placeholders pulse while search is pending');
            await verifyEntrance('#view-mods .mods-heading');
            await verifyEntrance('#view-mods .mods-target-bar');
            await verifyEntrance('#view-mods .mods-tabbar');
            await verifyEntrance('#mods-browse-section');
            if(checkMods){await pause(400);modDelay=0;check(await js(`!document.querySelector('#mods-query-render-target .mods-loading')`),'loading animation removed when search completes');}
            await js(`document.getElementById('mods-sub-installed').click()`);await pause(40);
            await verifyEntrance('#mods-installed-section');
            await js(`document.getElementById('mods-sub-browse').click()`);await pause(40);
            check(await js(`document.getElementById('mods-browse-section').getAnimations().some(motion=>motion.playState==='running')`),'Mods subtab reveal replays on switch');
            console.log('HOME_LOADOUTS_MODS_MOTION_OK: '+checks+' normal-motion checks');app.exit(0);return;
        }
        if(checkWardrobe){
            await js(`document.querySelector('[data-target="view-cosmetics"]').click()`);await pause(250);
            check(await js(`document.querySelector('#view-cosmetics h1').textContent==='Wardrobe'&&document.getElementById('cosmetic-account-chip').textContent.includes('Raven')`),'Wardrobe shows title and active account');
            check(await js(`document.querySelectorAll('#cosmetic-slot-list .cosmetics-slot.empty').length===6`),'six upload slots remain available');
            for(const [width,height] of [[1280,800],[1000,680],[1600,900]]){
                win.setSize(width,height);await pause(120);
                check(await js(`(()=>{const frame=document.querySelector('.content-frame'),preview=document.querySelector('.cosmetic-viewer-card').getBoundingClientRect(),list=document.querySelector('.cosmetic-list-card').getBoundingClientRect();return frame.scrollWidth<=frame.clientWidth+1&&preview.right<=list.left&&[...document.querySelectorAll('#cosmetic-slot-list .cosmetics-slot')].every(slot=>slot.scrollWidth<=slot.clientWidth+1);})()`),'preview and slots fit '+width);
            }
            win.setSize(1280,800);await pause(150);
            await js(`document.getElementById('skin-file-input').addEventListener('click',event=>event.preventDefault());document.querySelector('#cosmetic-slot-list .cosmetics-slot-btn').click()`);
            check(await js(`document.getElementById('skin-file-input').dataset.targetUser==='Raven'&&document.getElementById('skin-file-input').dataset.slotIndex==='0'`),'skin upload retains account and slot target');
            await js(`document.getElementById('cosmetic-model-slim').click()`);await pause(50);
            check(invoked.some(item=>item.channel==='set-account-skin'&&item.args[0].username==='Raven'&&item.args[0].model==='slim'),'model choice reaches existing save handler');
            const skinData=require('electron').nativeImage.createFromBitmap(Buffer.alloc(64*64*4,255),{width:64,height:64}).toDataURL();
            accounts[0].customSkins=[{name:'Test skin',model:'slim',dataUrl:skinData,thumbnail:skinData}];syncAccounts();
            await js(`document.querySelector('[data-target="view-cosmetics"]').click()`);await pause(200);
            check(await js(`document.querySelector('#cosmetic-slot-list .cosmetics-slot strong').textContent==='Test skin'&&document.querySelectorAll('#cosmetic-slot-list .cosmetics-slot-btn').length===8`),'populated skin retains upload, select and remove controls');
            await js(`document.querySelectorAll('#cosmetic-slot-list .cosmetics-slot-btn')[1].click()`);await pause(200);
            check(await js(`document.querySelector('#cosmetic-slot-list .cosmetics-slot').classList.contains('equipped')&&getComputedStyle(document.querySelector('#cosmetic-slot-list .cosmetics-slot')).borderTopColor==='rgb(255, 255, 255)'`),'equipped skin uses white selection border');
            await js(`document.querySelector('[data-cat="capes"]').click()`);await pause(150);
            check(await js(`document.getElementById('cosmetic-list-title').textContent==='Cape slots'&&document.getElementById('cosmetic-model-tools').style.display==='none'&&document.querySelectorAll('#cosmetic-slot-list .cosmetics-slot').length===6`),'Capes switches slots and hides skin model controls');
            await js(`document.getElementById('cape-file-input').addEventListener('click',event=>event.preventDefault());document.querySelector('#cosmetic-slot-list .cosmetics-slot-btn').click()`);
            check(await js(`document.getElementById('cape-file-input').dataset.targetUser==='Raven'`),'cape upload retains target');
            await js(`document.querySelector('[data-cat="skins"]').click()`);await pause(150);
            fs.writeFileSync(path.join(output,'wardrobe-desktop.png'),(await win.webContents.capturePage()).toPNG());
            check(errors.length===0,'Wardrobe has no renderer exceptions');
            console.log('WARDROBE_UI_OK: '+checks+' isolated checks');app.exit(0);return;
        }
        if(checkMods){
            modDelay=350;
            await js(`document.querySelector('[data-target="view-mods"]').click()`);await pause(50);
            check(await js(`document.querySelectorAll('#mods-query-render-target .mods-skeleton').length===6&&document.querySelector('#mods-query-render-target [role="status"]').textContent.includes('Loading mods')`),'pending search shows accessible loading placeholders');
            check(await js(`getComputedStyle(document.querySelector('#mods-query-render-target .mods-skeleton')).animationName==='none'`),'loading placeholders respect reduced motion');
            await pause(400);modDelay=0;
            check(await js(`!document.querySelector('#mods-query-render-target .mods-loading')`),'loading placeholders removed after results');
            await js(`document.querySelector('[data-target="view-mods"]').click()`);await pause(150);
            check(await js(`document.querySelectorAll('#mods-query-render-target .list-item').length===3`),'Browse renders real fixture results');
            check(await js(`getComputedStyle(document.querySelector('#view-mods .mods-tabbar')).animationName==='none'&&getComputedStyle(document.getElementById('mods-browse-section')).animationName==='none'`),'Mods entrances respect reduced motion');
            check(await js(`document.getElementById('mods-browse-context').textContent.includes('Survival')&&document.getElementById('mods-browse-context').textContent.includes('1.21.1')&&document.getElementById('mods-browse-context').textContent.includes('FABRIC')`),'loadout, version and loader stay visible');
            for(const [width,height] of [[1280,800],[1000,680],[1600,900]]){win.setSize(width,height);await pause(100);check(await js(`(()=>{const frame=document.querySelector('.content-frame');return frame.scrollWidth<=frame.clientWidth+1&&[...document.querySelectorAll('#mods-query-render-target .list-item')].every(card=>{const a=card.querySelector('.mod-install-btn').getBoundingClientRect(),b=card.getBoundingClientRect();return a.right<=b.right&&a.bottom<=b.bottom;})})()`),'compact Browse cards and install buttons fit '+width);}
            check(await js(`(()=>{const node=document.getElementById('mods-query-render-target'),grid=node.getBoundingClientRect(),cards=[...node.querySelectorAll('.list-item')].map(card=>card.getBoundingClientRect()),firstRow=cards.filter(card=>Math.abs(card.top-cards[0].top)<1),right=grid.left+node.clientWidth-parseFloat(getComputedStyle(node).paddingRight);return Math.abs(firstRow.at(-1).right-right)<2;})()`),'Browse cards fill the available row width');
            win.setSize(1280,800);await pause(150);fs.writeFileSync(path.join(output,'mods-browse.png'),(await win.webContents.capturePage()).toPNG());
            await js(`document.getElementById('mod-search-input').value='Sodium';document.getElementById('mod-search-trigger').click()`);await pause(100);
            check(invoked.some(item=>item.channel==='search-modrinth-api'&&item.args[0].query==='Sodium'&&item.args[0].loader==='fabric'),'Search retains loader-aware API payload');
            await js(`document.getElementById('mod-sort-select').value='downloads';document.getElementById('mod-sort-select').dispatchEvent(new Event('change',{bubbles:true}))`);await pause(100);
            check(invoked.some(item=>item.channel==='search-modrinth-api'&&item.args[0].sort==='downloads'),'sort reaches existing search handler');
            const detailsBefore=invoked.filter(item=>item.channel==='get-mod-details').length;
            await js(`document.querySelector('#mods-query-render-target .mod-install-btn').click()`);await pause(80);
            check(await js(`document.getElementById('install-modal').style.display==='flex'&&document.getElementById('modal-mod-title').textContent==='Sodium'`),'Install opens confirmation for the chosen mod');
            check(invoked.filter(item=>item.channel==='get-mod-details').length===detailsBefore,'Install does not also open Details');
            await js(`document.getElementById('modal-confirm').click()`);await pause(80);
            check(sent.some(item=>item.channel==='download-modrinth-exact'&&item.data.projectId==='sodium'&&item.data.profileId==='survival'&&item.data.version==='1.21.1'&&item.data.loader==='fabric'),'confirmed install retains target loadout/version/loader');
            await js(`document.querySelector('#mods-query-render-target .mod-title').click()`);await pause(80);
            check(await js(`document.getElementById('mod-details-modal').style.display==='flex'&&document.getElementById('mod-details-title').textContent==='Sodium'`),'mod title opens Details');
            await js(`document.getElementById('mod-details-close').click();document.getElementById('mods-sub-installed').click()`);await pause(120);
            check(await js(`document.querySelectorAll('#installed-mods-render-target .list-item').length===2&&document.getElementById('mods-sub-installed').getAttribute('aria-selected')==='true'`),'Installed tab and accessible selected state');
            check(await js(`document.getElementById('mods-installed-summary').textContent.includes('1 enabled')&&document.getElementById('mods-installed-summary').textContent.includes('1 disabled')`),'installed enabled/disabled summary');
            check(await js(`(()=>{const tabs=document.querySelector('.mods-tabbar').getBoundingClientRect(),tools=document.getElementById('mods-installed-tools').getBoundingClientRect(),list=document.getElementById('installed-mods-render-target').getBoundingClientRect();return tools.top>=tabs.top&&tools.bottom<=tabs.bottom+1&&list.top-tabs.bottom<70;})()`),'Installed tools share the tab row and leave no blank toolbar row');
            check(await js(`document.querySelector('.installed-mod-logo')?.src.startsWith('data:image/png;base64,')&&document.querySelector('.installed-mod-logo').naturalWidth>0`),'installed logo data renders as a real image');
            check(await js(`document.querySelectorAll('.mod-icon-box svg').length===2`),'missing logos retain the fallback icon');
            await js(`document.querySelectorAll('.toast').forEach(node=>node.remove());new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))`);await pause(200);
            fs.writeFileSync(path.join(output,'mods-installed.png'),(await win.webContents.capturePage()).toPNG());
            win.setSize(1000,680);await pause(100);
            check(await js(`(()=>{const f=document.querySelector('.content-frame');return f.scrollWidth<=f.clientWidth+1&&[...document.querySelectorAll('#installed-mods-render-target .list-item')].every(row=>row.querySelector('.mod-row-actions').getBoundingClientRect().right<=row.getBoundingClientRect().right)})()`),'Installed actions fit minimum width');
            await js(`document.querySelector('#installed-mods-render-target input[type="checkbox"]').click()`);await pause(120);
            check(installedFixtures[0].enabled===false&&await js(`!document.querySelector('#installed-mods-render-target input[type="checkbox"]').checked`),'toggle updates the correct mod and UI state');
            await js(`document.querySelector('#installed-mods-render-target [data-remove-mod]').click()`);await pause(50);
            check(await js(`document.getElementById('remove-mod-modal').style.display==='flex'&&document.getElementById('remove-mod-name').textContent==='Sodium'`),'remove confirmation still identifies the selected mod');
            await js(`document.getElementById('remove-mod-cancel').click();document.getElementById('open-mods-folder-btn').click();document.getElementById('check-mod-updates-btn').click()`);await pause(120);
            check(invoked.some(item=>item.channel==='open-profile-folder')&&invoked.some(item=>item.channel==='check-mod-updates'),'folder and update check handlers retained');
            check(await js(`document.querySelector('#installed-mods-render-target [data-update-mod]')!==null`),'available updates expose the update action');
            await js(`document.querySelector('#installed-mods-render-target [data-update-mod]').click()`);await pause(100);
            check(invoked.some(item=>item.channel==='update-mod'&&item.args[0].oldFilename==='sodium.jar.disabled'),'update targets the selected installed file');
            await js(`document.getElementById('mods-sub-browse').click();document.getElementById('mod-search-input').value='empty';document.getElementById('mod-search-trigger').click()`);await pause(100);
            check(await js(`document.querySelector('#mods-query-render-target .mods-empty strong').textContent==='No mods found'`),'empty Browse results have useful feedback');
            await js(`document.getElementById('mod-search-input').value='offline';document.getElementById('mod-search-trigger').click()`);await pause(100);
            check(await js(`document.querySelector('#mods-query-render-target .mods-empty strong').textContent==='Something went wrong'`),'offline API failure renders without a secondary exception');
            await js(`document.getElementById('mod-search-input').value='';document.getElementById('mod-search-trigger').click()`);await pause(100);
            await js(`document.getElementById('mod-refresh-trigger').click()`);await pause(100);
            check(await js(`document.querySelectorAll('#mods-query-render-target .list-item').length===3`),'Refresh recovers from empty/error states');
            const ordinaryTitle=browseFixtures[0].title;browseFixtures[0].title='<img src=x onerror=alert(1)>';
            await js(`document.getElementById('mod-refresh-trigger').click()`);await pause(100);
            check(await js(`document.querySelector('#mods-query-render-target .mod-title').textContent==='<img src=x onerror=alert(1)>'&&document.querySelector('#mods-query-render-target .mod-title img')===null`),'API titles are rendered safely as text');
            browseFixtures[0].title=ordinaryTitle;
            await js(`document.getElementById('mods-manage-loadout').click()`);
            check(await js(`document.getElementById('view-profiles').classList.contains('active')`),'Manage loadouts opens the Loadouts tab');
            installedFixtures=[];await js(`document.querySelector('[data-target="view-mods"]').click();document.getElementById('mods-sub-installed').click()`);await pause(100);
            check(await js(`document.querySelector('#installed-mods-render-target .mods-empty strong').textContent==='No mods installed yet'`),'empty installed collection');
            check(errors.length===0,'no Mods renderer errors: '+errors.join('\n'));
            console.log('MODS_UI_OK: '+checks+' isolated Browse/Installed checks');app.exit(0);return;
        }
        check(await js(`document.querySelector('#view-home .home-launch-panel').getAnimations().length===0`),'reduced motion disables Home entrance');
        check(await js(`document.getElementById('home-account-name').textContent==='Raven'`),'account data reaches Home');
        check(await js(`document.getElementById('quick-profile-dropdown').value==='survival'`),'loadout selection retained');
        check(await js(`document.getElementById('home-profile-version').textContent==='1.21.1'`),'version metadata retained');
        check(await js(`document.querySelectorAll('#view-home [id]').length===new Set([...document.querySelectorAll('#view-home [id]')].map(el=>el.id)).size`),'no duplicate Home IDs');
        for(const [width,height] of [[1280,800],[1000,680],[1600,900]]){
            win.setSize(width,height);await pause(200);
            const metrics=await js(`(()=>{const frame=document.querySelector('.content-frame'),hero=document.querySelector('.home-hero').getBoundingClientRect(),rail=document.querySelector('.home-hero-launch').getBoundingClientRect(),panel=document.querySelector('.home-launch-panel').getBoundingClientRect(),button=document.getElementById('launch-trigger-action').getBoundingClientRect();return{overflow:frame.scrollWidth>frame.clientWidth+1,separate:hero.bottom<=rail.top+1,buttonInside:button.right<=panel.right&&button.left>=rail.left,panelVisible:button.bottom<=innerHeight,animation:getComputedStyle(document.getElementById('launch-trigger-action')).animationName}})()`);
            check(!metrics.overflow,'no horizontal overflow at '+width);
            check(metrics.separate&&metrics.buttonInside&&metrics.panelVisible,'account and launch controls fit '+width+': '+JSON.stringify(metrics));
            check(metrics.animation==='none','launch button has no idle animation');
            check(await js(`(()=>{const account=document.querySelector('.home-hero').getBoundingClientRect(),heading=document.querySelector('#view-home h1').getBoundingClientRect(),settings=document.querySelector('.home-page-heading button').getBoundingClientRect();return account.top>Math.max(heading.bottom,settings.bottom)})()`),'account and heading do not overlap at '+width);
            check(await js(`(()=>{const dock=document.querySelector('.home-launch-dock').getBoundingClientRect(),panel=document.querySelector('.home-launch-panel').getBoundingClientRect(),select=document.getElementById('quick-profile-dropdown').getBoundingClientRect(),facts=document.querySelector('.home-loadout-facts').getBoundingClientRect();return dock.bottom<=panel.bottom+1&&(select.bottom<=facts.top+1||select.right<=facts.left)&&select.left>=dock.left})()`),'loadout dock fits without overlapping metadata at '+width);
            check(await js(`(()=>{const track=document.getElementById('news-carousel-track').getBoundingClientRect(),card=document.querySelector('#view-home .news-card').getBoundingClientRect(),action=document.querySelector('.news-card-link').getBoundingClientRect();return card.left>=track.left-1&&card.right<=track.right+1&&action.right<card.right})()`),'editorial news panel fits at '+width);
            check(await js(`(()=>{const list=document.getElementById('partner-list').getBoundingClientRect(),tiles=[...document.querySelectorAll('.partner-row')].map(el=>el.getBoundingClientRect());return tiles.length===5&&tiles.every(rect=>rect.left>=list.left-1&&rect.right<=list.right+1&&rect.bottom<=list.bottom+1)})()`),'all server rows fit their panel at '+width);
        }
        win.setSize(1280,800);await pause(250);
        fs.writeFileSync(path.join(output,'home-desktop.png'),(await win.webContents.capturePage()).toPNG());
        await js(`document.querySelector('.home-page-heading button').click()`);
        check(await js(`document.getElementById('view-settings').classList.contains('active')`),'Home settings shortcut');
        await js(`document.querySelector('[data-target="view-home"]').click();document.querySelector('.home-dock-manage button').click()`);
        check(await js(`document.getElementById('view-profiles').classList.contains('active')`),'manage loadouts shortcut');
        await js(`document.querySelector('[data-target="view-home"]').click();document.querySelector('.home-hero-account button').click()`);
        check(await js(`document.getElementById('view-accounts').classList.contains('active')`),'switch account shortcut');
        await js(`document.querySelector('[data-target="view-home"]').click();document.getElementById('console-toggle-launch-btn').click()`);
        check(await js(`document.getElementById('console-overlay').style.display==='flex'`),'launch console still opens');
        await js(`document.getElementById('home-community-action').click()`);
        check(sent.some(item=>item.channel==='open-external-link'&&item.data==='https://batclient.xyz/discord'),'community opens the official Discord redirect');
        await js(`document.getElementById('close-console-btn').click();const select=document.getElementById('quick-profile-dropdown');select.value='classic';select.dispatchEvent(new Event('change',{bubbles:true}))`);await pause(100);
        check(active==='classic'&&await js(`document.getElementById('home-profile-version').textContent==='1.8.9'`),'profile changes still sync metadata');
        await js(`document.getElementById('launch-trigger-action').click()`);await pause(100);
        check(sent.some(item=>item.channel==='launch-game'&&item.data.profileId==='classic'&&item.data.username==='Raven'),'launch payload preserved');
        check(await js(`document.getElementById('launch-trigger-action').disabled`),'launch cannot be double clicked');
        send('launch-progress',{percent:64});send('launch-status','Downloading game files…');await pause(100);
        check(await js(`document.getElementById('global-progress-bar').style.width==='64%'&&document.getElementById('global-status-text').textContent.includes('Downloading')`),'progress and status remain live');
        fs.writeFileSync(path.join(output,'home-progress.png'),(await win.webContents.capturePage()).toPNG());
        send('game-terminated');await pause(80);
        const before=sent.filter(item=>item.channel==='launch-game').length;
        await js(`document.querySelector('.partner-row-copy').click()`);await pause(80);
        check(copied.length===1&&sent.filter(item=>item.channel==='launch-game').length===before,'Copy server address does not launch');
        await js(`document.querySelector('.partner-row-join').click()`);await pause(80);
        check(sent.some(item=>item.channel==='launch-game'&&item.data.serverIp==='play.flashmc.cyou:22217'),'server Join launches existing quick-play path');
        send('game-terminated');active='survival';syncProfiles();await pause(80);
        await js(`document.querySelector('[aria-label="Next news"]').click()`);await pause(450);
        check(await js(`document.getElementById('news-carousel-track').scrollLeft>0`),'news navigation works: '+JSON.stringify(await js(`(()=>{const t=document.getElementById('news-carousel-track');return {left:t.scrollLeft,width:t.clientWidth,scrollWidth:t.scrollWidth,offsets:[...t.children].map(c=>c.offsetLeft)}})()`)));
        accounts=[];syncAccounts();await pause(100);
        check(await js(`document.getElementById('home-account-name').textContent==='Choose an account'`),'empty account state is helpful');
        const noAccountCount=sent.filter(item=>item.channel==='launch-game').length;
        await js(`document.getElementById('launch-trigger-action').click()`);await pause(80);
        check(sent.filter(item=>item.channel==='launch-game').length===noAccountCount,'missing-account launch guard preserved');
        fs.writeFileSync(path.join(output,'home-empty-account.png'),(await win.webContents.capturePage()).toPNG());
        accounts=[{username:'Raven',type:'offline',uuid:'00000000-0000-0000-0000-000000000000'}];syncAccounts();await pause(100);
        send('launch-status','Choose a loadout, then play.');await pause(100);
        win.setSize(1000,680);await pause(200);fs.writeFileSync(path.join(output,'home-minimum.png'),(await win.webContents.capturePage()).toPNG());
        await js(`document.querySelector('[data-target="view-profiles"]').click()`);await pause(100);
        check(await js(`document.querySelector('#view-profiles .profile-cards-grid').getAnimations().length===0`),'reduced motion disables Loadouts entrance');
        check(await js(`document.querySelectorAll('#view-profiles .profile-card').length===2&&document.getElementById('loadouts-count').textContent==='2 loadouts'`),'loadout cards and saved count');
        check(await js(`document.querySelector('.profile-card').dataset.prof==='classic'&&document.querySelector('.profile-card.active').dataset.prof==='survival'`),'favorites sort first without changing selection');
        check(await js(`document.querySelector('[data-prof="classic"] .profile-specs').textContent.includes('Vanilla')&&document.querySelector('[data-prof="classic"] .profile-specs').textContent.includes('3 GB')`),'per-profile loader and memory metadata');
        for(const [width,height] of [[1280,800],[1000,680],[1600,900]]){
            win.setSize(width,height);await pause(100);
            check(await js(`(()=>{const frame=document.querySelector('.content-frame');return frame.scrollWidth<=frame.clientWidth+1&&[...document.querySelectorAll('#view-profiles .profile-card')].every(card=>[...card.querySelectorAll('.profile-card-actions button')].every(button=>button.getBoundingClientRect().right<=card.getBoundingClientRect().right))})()`),'loadout cards and actions fit '+width);
        }
        win.setSize(1280,800);await pause(100);fs.writeFileSync(path.join(output,'loadouts-desktop.png'),(await win.webContents.capturePage()).toPNG());
        await js(`(()=>{const s=document.getElementById('loadout-search-input');s.value='fabric';s.dispatchEvent(new Event('input',{bubbles:true}));})()`);
        check(await js(`document.querySelectorAll('.profile-card').length===1&&document.querySelector('.profile-card').dataset.prof==='survival'`),'search by loader');
        await js(`(()=>{const s=document.getElementById('loadout-search-input');s.value='1.8.9';s.dispatchEvent(new Event('input',{bubbles:true}));})()`);
        check(await js(`document.querySelectorAll('.profile-card').length===1&&document.querySelector('.profile-card').dataset.prof==='classic'`),'search by Minecraft version');
        await js(`(()=>{const s=document.getElementById('loadout-search-input');s.value='missing';s.dispatchEvent(new Event('input',{bubbles:true}));})()`);
        check(await js(`document.querySelector('.loadouts-empty strong').textContent==='No matching loadouts'`),'filtered empty state');
        await js(`document.querySelector('.loadouts-empty button').click();document.getElementById('loadout-fav-filter').click()`);
        check(await js(`document.querySelectorAll('.profile-card').length===1&&document.querySelector('.profile-card').dataset.prof==='classic'&&document.getElementById('loadout-fav-filter').getAttribute('aria-pressed')==='true'`),'favorite filter and accessible state');
        await js(`document.getElementById('loadout-fav-filter').click();document.querySelector('[data-prof="classic"] .profile-star').click()`);await pause(50);
        check(sent.some(item=>item.channel==='save-profile-node'&&item.data.id==='classic'&&item.data.favorite===false),'favorite saves the correct profile');
        const selectCount=sent.filter(item=>item.channel==='select-active-profile').length;
        await js(`document.querySelector('[data-prof="classic"] [data-action="duplicate"]').click();document.querySelector('[data-prof="classic"] [data-action="folder"]').click();document.querySelector('[data-prof="classic"] [data-action="delete"]').click()`);await pause(60);
        check(sent.some(item=>item.channel==='duplicate-profile-node'&&item.data==='classic')&&sent.some(item=>item.channel==='delete-profile-node'&&item.data==='classic'),'duplicate and delete target the chosen card');
        check(invoked.some(item=>item.channel==='open-profile-folder')&&sent.filter(item=>item.channel==='select-active-profile').length===selectCount,'tool buttons do not select another loadout');
        await js(`(()=>{const from=document.querySelector('[data-prof="classic"]'),to=document.querySelector('[data-prof="survival"]'),data=new DataTransfer();from.dispatchEvent(new DragEvent('dragstart',{bubbles:true,dataTransfer:data}));to.dispatchEvent(new DragEvent('drop',{bubbles:true,cancelable:true,dataTransfer:data}));from.dispatchEvent(new DragEvent('dragend',{bubbles:true,dataTransfer:data}));})()`);await pause(50);
        check(sent.some(item=>item.channel==='reorder-profiles'&&item.data.join(',')==='classic,survival'),'drag reorder preserves the profile order payload');
        await js(`document.querySelector('[data-prof="classic"] [data-action="edit"]').click()`);
        check(await js(`document.getElementById('profile-edit-modal').style.display==='flex'&&document.getElementById('edit-profile-id').value==='classic'&&document.getElementById('edit-profile-name').value==='Classic PvP'`),'Edit opens the correct profile');
        await js(`document.getElementById('edit-profile-name').value='Classic revised';document.getElementById('save-profile-action').click()`);await pause(50);
        check(sent.some(item=>item.channel==='save-profile-node'&&item.data.id==='classic'&&item.data.name==='Classic revised'&&item.data.ram==='3'&&item.data.ramOverride===true),'edit save preserves memory settings');
        await js(`document.querySelector('[data-prof="classic"] [data-action="select"]').click()`);await pause(80);
        check(active==='classic'&&await js(`document.querySelector('.profile-card.active').dataset.prof==='classic'&&document.getElementById('quick-profile-dropdown').value==='classic'`),'Select updates the card and Home selector');
        await js(`document.getElementById('import-loadouts-action').click();document.getElementById('export-loadouts-action').click()`);await pause(50);
        check(invoked.some(item=>item.channel==='import-loadouts')&&invoked.some(item=>item.channel==='export-loadouts'),'import and export still use their existing handlers');
        const ordinaryName=profiles[0].name;profiles[0].name='<img src=x onerror=alert(1)>';syncProfiles();await pause(80);
        check(await js(`document.querySelector('[data-prof="survival"] .profile-card-name').textContent==='<img src=x onerror=alert(1)>'&&document.querySelector('[data-prof="survival"] .profile-card-name img')===null`),'imported profile names are rendered as text');
        profiles[0].name=ordinaryName;syncProfiles();await pause(80);
        send('sync-profiles-view',{profiles:[],activeId:null});await pause(80);
        check(await js(`document.querySelector('.loadouts-empty strong').textContent==='Create your first loadout'`),'first-loadout empty state');
        fs.writeFileSync(path.join(output,'loadouts-empty.png'),(await win.webContents.capturePage()).toPNG());
        syncProfiles();await pause(80);win.setSize(1000,680);await pause(100);fs.writeFileSync(path.join(output,'loadouts-minimum.png'),(await win.webContents.capturePage()).toPNG());
        await js(`document.getElementById('create-profile-action').click()`);await pause(50);
        check(sent.some(item=>item.channel==='create-blank-profile'),'new loadout uses the existing creation flow');
        check(errors.length===0,'no renderer errors: '+errors.join('\n'));
        console.log('HOME_AND_LOADOUTS_UI_OK: '+checks+' launcher IPC, layout and empty-state checks; screenshots in '+output);
        app.exit(0);
    }catch(error){if(win){console.error(await win.webContents.executeJavaScript(`JSON.stringify([...document.querySelectorAll('.signature-content,.signature-brand,.home-widgets,.home-widget,.news-carousel,.news-carousel-track,.partner-list,.partner-row')].map(e=>({cls:e.className,height:e.getBoundingClientRect().height,display:getComputedStyle(e).display,flex:getComputedStyle(e).flex,min:getComputedStyle(e).minHeight})))`));fs.writeFileSync(path.join(output,'home-failure.png'),(await win.webContents.capturePage()).toPNG());}console.error(error.stack);app.exit(1);}
});
