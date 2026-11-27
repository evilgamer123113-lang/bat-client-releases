const {app,BrowserWindow}=require('electron');
const path=require('node:path');
app.disableHardwareAcceleration();
app.whenReady().then(async()=>{
    const win=new BrowserWindow({show:false,width:1280,height:800,webPreferences:{backgroundThrottling:false}});
    win.webContents.on('console-message',event=>{if(event.level==='error')console.error(event.message);});
    try {
        await win.loadFile(path.join(process.env.BAT_WEB_TEST_ROOT||path.join(__dirname,'../src/main/resources/assets/batclient/web'),'overlay.html'));
        const result=await win.webContents.executeJavaScript(`(${function(){
            let checks=0;const assert=(ok,message)=>{if(!ok)throw Error(message);checks++;};
            const saved={modules:{zoom:{en:true,set:{key:'V'}}}},previews=[];
            window.batGetHudConfig=()=>JSON.stringify(saved);
            window.batPreviewHudModule=(id,raw)=>previews.push({id,patch:JSON.parse(raw)});
            window.batSaveHudModule=(id,raw)=>{
                const patch=JSON.parse(raw),mod=saved.modules[id]||patch._initial||{};
                for(const key in patch)if(key!=='_initial')mod[key]=key==='set'?Object.assign(mod.set||{},patch.set):patch[key];
                saved.modules[id]=mod;return JSON.stringify(saved);
            };
            document.body.classList.remove('hud-idle');document.body.classList.add('hud-menu');cfgLoad();openPanel('mods');
            const open=(id,key)=>{openModSettings(id);document.querySelector('.ms-ctl[data-key="'+key+'"] .swatch').click();};
            open('fps','color');assert(document.getElementById('cp-apply').textContent==='Apply color','explicit Apply button');
            const square=document.getElementById('cp-sv'),bounds=square.getBoundingClientRect();
            square.dispatchEvent(new PointerEvent('pointermove',{bubbles:true,buttons:1,clientX:bounds.left+bounds.width*.65,clientY:bounds.top+bounds.height*.2}));
            const selected=document.getElementById('cp-hex').value;
            assert(previews.length>0&&previews.at(-1).patch.set.color===selected,'drag updates actual live native color');
            assert(!saved.modules.fps,'drag does not persist until applied/closed');
            document.getElementById('cp-apply').click();
            assert(saved.modules.fps.set.color===selected,'Apply saves selected drag value instead of stale color');
            assert(document.getElementById('cp-veil').classList.contains('hidden'),'Apply closes picker');
            closeModSettings();open('fps','color');assert(document.getElementById('cp-hex').value===selected,'applied color survives reopening');
            const input=document.getElementById('cp-hex');input.value='#12345680';document.getElementById('cp-apply').click();
            assert(saved.modules.fps.set.color==='#12345680','Apply parses pending typed RGBA without needing blur');
            closeModSettings();open('fps','color');input.value='24AB68';
            input.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));
            assert(saved.modules.fps.set.color==='#24AB68FF','Enter applies six-digit RGB');
            closeModSettings();open('fps','color');input.value='#badhex';document.getElementById('cp-apply').click();
            assert(!document.getElementById('cp-veil').classList.contains('hidden')&&document.getElementById('cp-error').textContent,'invalid typed color is explained and not applied');
            input.value='#FF0000FF';document.getElementById('cp-apply').click();
            closeModSettings();open('fps','bgColor');
            const alpha=document.getElementById('cp-alpha'),a=alpha.getBoundingClientRect();
            alpha.dispatchEvent(new PointerEvent('pointermove',{bubbles:true,buttons:1,clientY:a.top+a.height/2}));document.getElementById('cp-apply').click();
            const alphaByte=parseInt(saved.modules.fps.set.bgColor.slice(-2),16);
            assert(Math.abs(alphaByte-128)<=1,'alpha drag commits selected transparency: '+saved.modules.fps.set.bgColor);
            closeModSettings();open('resourcepacks','descriptionColor');input.value='#22CC88FF';document.getElementById('cp-apply').click();
            assert(saved.modules.resourcepacks.set.descriptionColor==='#22CC88FF'&&saved.modules.resourcepacks.set.originalColors===false,'custom MOTD color overrides original pack colors on Apply');
            assert(!document.querySelector('.ms-ctl[data-key="originalColors"] input').checked,'original color toggle reflects override');
            assert(saved.modules.zoom.set.key==='V','color saves preserve unrelated modules');
            return{checks,status:'COLOR_PICKER_APPLY_OK'};
        }.toString()})()`);
        console.log(JSON.stringify(result));app.exit(0);
    }catch(error){console.error(error.stack);app.exit(1);}
});
