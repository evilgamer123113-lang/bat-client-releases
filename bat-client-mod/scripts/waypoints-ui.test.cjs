const {app,BrowserWindow}=require('electron');
const path=require('node:path');
const fs=require('node:fs');
app.disableHardwareAcceleration();
app.whenReady().then(async()=>{
 const win=new BrowserWindow({show:false,width:1280,height:800,webPreferences:{backgroundThrottling:false,offscreen:true}});
 win.webContents.on('console-message',(_e,level,message)=>{if(level===3)console.error(message);});
 try{
  await win.loadFile(path.join(process.env.BAT_WEB_TEST_ROOT||path.join(__dirname,'../src/main/resources/assets/batclient/web'),'overlay.html'));
  const result=await win.webContents.executeJavaScript(`(${function(){
   let count=0;const check=(value,message)=>{if(!value)throw Error(message);count++;};
   Object.defineProperty(HTMLFormElement.prototype,'reportValidity',{configurable:true,value:undefined});Object.defineProperty(HTMLFormElement.prototype,'checkValidity',{configurable:true,value:undefined});
   let points=[],serial=0,readOnly=false;const calls=[];
   window.batWaypoints=(action,raw)=>{const data=JSON.parse(raw||'{}');calls.push(action);if(action==='save'){if(data.name==='reject')return JSON.stringify({error:'Save failed; preserved.'});let item=points.find(e=>e.id===data.id);if(!item){item={id:'id-'+(++serial),created:serial};points.push(item);}if(data.tracked)points.forEach(e=>e.tracked=false);Object.assign(item,data,{id:item.id});}
    else if(action==='delete')points=points.filter(e=>e.id!==data.id);
    else if(['visible','favorite','tracked'].includes(action)){if(action==='tracked'&&data.value)points.forEach(e=>e.tracked=false);points.find(e=>e.id===data.id)[action]=data.value;}
    else if(action==='group')points.filter(e=>e.group===data.group).forEach(e=>e.visible=data.value);
    return JSON.stringify({error:'',notice:'',readOnly,world:'Test World',dimension:'minecraft:overworld',position:{x:12,y:64,z:-30},waypoints:points.map(e=>Object.assign({},e)),moduleEnabled:false,createdId:action==='save'?points.find(e=>e.name===data.name)?.id:''});
   };
   document.body.classList.add('hud-idle');openWaypointManager(true);
   check(!document.body.classList.contains('hud-idle')&&getComputedStyle(document.querySelector('.mm-layer')).display!=='none','cold quick-create unhides idle overlay before native first feed');
   check(modDef('waypoints')&&!modDef('waypoints').def,'module starts off');check(!modDef('waypoints').widget,'world feature is not a draggable marker widget');check(MODULE_ART.waypoints.length>50,'dedicated vector icon');
   check(document.querySelector('[data-view="waypoints"] svg'),'waypoint tab exists');check(document.getElementById('waypoint-editor'),'quick create opens editor');check(document.querySelector('[name=x]').value==='12','current position prefilled');
   function fill(name,dimension='minecraft:overworld'){const form=document.getElementById('waypoint-form');form.elements.name.value=name;form.elements.dimension.value=dimension;form.querySelector('.wp-save').click();}
   fill('');check(points.length===0&&document.querySelector('.wp-form-error').textContent.includes('Name'),'empty name has visible validation error without browser validation APIs');check(document.querySelector('[name=name]').getAttribute('aria-invalid')==='true','invalid field marked accessibly');
   document.querySelector('[name=x]').value='1.5';fill('Invalid coordinate');check(points.length===0&&document.querySelector('.wp-form-error').textContent.includes('whole number'),'fractional coordinate cannot reach native bridge');document.querySelector('[name=x]').value='12';
   document.querySelector('[name=group]').value='';fill('Missing group');check(document.querySelector('.wp-advanced').open&&document.querySelector('[name=group]').getAttribute('aria-invalid')==='true','invalid advanced field expands before focusing');document.querySelector('[name=group]').value='General';
   fill('<img src=x onerror=alert(1)>');check(points.length===1,'create saves');check(document.querySelector('.wp-name').textContent.includes('<img'),'names render literally');check(!document.querySelector('#waypoint-list img'),'names cannot inject markup');
   check(document.querySelector('.wp-badge').textContent==='TRACKED','tracked target indicated');
   document.querySelector('.wp-name').click();document.querySelector('[name=x]').value='42';fill('Home');check(points.length===1&&points[0].x===42,'editing preserves ID');
   document.querySelector('.wp-actions button').click();check(points[0].favorite,'favorite saves');
   Array.from(document.querySelectorAll('.wp-actions button')).find(b=>b.textContent==='Hide').click();check(!points[0].visible&&document.querySelector('.wp-hidden'),'visibility saves');
   Array.from(document.querySelectorAll('.wp-actions button')).find(b=>b.textContent==='Copy').click();check(calls.includes('copy'),'coordinate copy delegates native clipboard');
   wpEdit(null);fill('Portal','minecraft:the_nether');check(points.length===2,'second dimension saved');check(document.querySelectorAll('.wp-card').length===1,'current dimension filter hides nether');
   const dimension=document.querySelector('[aria-label="Filter waypoint dimension"]');dimension.value='*';dimension.dispatchEvent(new Event('change'));check(document.querySelectorAll('.wp-card').length===2,'all dimensions filter');
   const search=document.querySelector('[aria-label="Search waypoints"]');search.value='portal';search.dispatchEvent(new Event('input'));check(document.querySelectorAll('.wp-card').length===1&&document.querySelector('.wp-name').textContent==='Portal','search matches waypoint names');search.value='';search.dispatchEvent(new Event('input'));
   const group=document.querySelector('[aria-label="Filter waypoint group"]');group.value='General';group.dispatchEvent(new Event('change'));Array.from(document.querySelectorAll('.wp-list-tools button')).find(b=>b.textContent==='Hide group').click();check(points.every(e=>!e.visible),'group hide saves');
   const remove=Array.from(document.querySelectorAll('.wp-actions .wp-delete'))[0];remove.click();check(points.length===2,'first delete requires confirmation');document.querySelector('.wp-delete').click();check(points.length===1,'second delete removes only selected location');
   wpEdit(null);fill('reject');check(points.length===1&&document.getElementById('waypoints-message').textContent.includes('Save failed'),'native save error visible without deleting locations');
   readOnly=true;renderWaypointManager();check(document.querySelector('.wp-save').disabled,'corrupt/read-only store disables creation');
   readOnly=false;renderWaypointManager();check(!document.querySelector('#view-waypoints .setting-reset,#view-waypoints #ms-reset'),'no unwanted reset controls');
   wpOpenDraft({x:-120,y:72,z:48});check(document.querySelector('[name=x]').value==='-120'&&document.querySelector('[name=name]').value==='Shared location','chat opens reviewable coordinate draft without saving');
   check(points.length===1,'chat draft does not auto-save');const f=document.getElementById('waypoint-form');f.elements.chroma.checked=true;f.elements.beam.checked=false;f.elements.opacity.value='50';f.elements.duration.value='60';fill('Temporary rainbow');const added=points.find(e=>e.name==='Temporary rainbow');check(added.chroma&&!added.beam&&added.opacity===50,'per-waypoint chroma beam and alpha save');check(added.expiresAt>Date.now()&&added.expiresAt<Date.now()+61000,'temporary waypoint receives expiry');
   points.push({id:'server:test',name:'Server spawn',group:'Server',dimension:'minecraft:overworld',x:0,y:64,z:0,color:'#85e6a3',visible:true,server:true,created:5});WP.group='*';WP.dimension='current';renderWaypointManager();const remote=document.querySelector('[data-waypoint-id="server:test"]');check(remote&&!remote.querySelector('.wp-delete'),'server markers are labeled and have no delete button');remote.querySelector('.wp-name').click();check(!document.querySelector('#waypoint-editor form')&&document.querySelector('#waypoint-editor').textContent.includes('Only the server'),'server waypoint editor explains ownership');check(remote.querySelectorAll('.wp-actions button').length===3,'server controls limited to track hide copy');
   document.querySelector('[data-source=server]').click();check(document.querySelectorAll('.wp-card').length===1&&document.querySelector('.wp-name').textContent==='Server spawn','server tab isolates remote markers');document.querySelector('[data-source=personal]').click();check(!document.querySelector('[data-waypoint-id="server:test"]'),'personal tab excludes server markers');document.querySelector('[data-source=favorites]').click();check(Array.from(document.querySelectorAll('.wp-card')).every(e=>WP.data.waypoints.find(p=>p.id===e.dataset.waypointId).favorite),'favorites tab respects favorite state');document.querySelector('[data-source=all]').click();
   document.querySelector('.wp-editor-dismiss').click();check(document.querySelector('.wp-layout').classList.contains('wp-browse-only'),'hiding editor expands destination library');wpEdit(null);check(!document.querySelector('.wp-layout').classList.contains('wp-browse-only'),'new waypoint reopens editor');document.querySelector('[name=name]').value='Unsaved draft';Array.from(document.querySelectorAll('.wp-heading button')).find(b=>b.textContent==='Refresh').click();check(document.querySelector('[name=name]').value==='Unsaved draft','refresh preserves unsaved editor draft');
   check(!modDef('waypoints').defaults.beam&&modDef('waypoints').defaults.ring&&modDef('waypoints').defaults.glow&&modDef('waypoints').defaults.chat&&modDef('waypoints').defaults.death&&modDef('waypoints').defaults.compass,'reference-inspired feature defaults defined while module stays off');
   wpEdit(null);const color=document.querySelector('[name=color]'),originalColor=color.value;const savesBefore=calls.filter(x=>x==='save').length;color.click();check(!document.getElementById('cp-veil').classList.contains('hidden'),'waypoint opens full shared picker');
   const sv=document.getElementById('cp-sv'),sr=sv.getBoundingClientRect();sv.setPointerCapture=()=>{throw Error('Capture unavailable');};
   sv.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,cancelable:true,button:0,buttons:1,pointerId:5,clientX:sr.left+sr.width*.2,clientY:sr.top+sr.height*.2}));
   document.dispatchEvent(new PointerEvent('pointermove',{bubbles:true,buttons:0,pointerId:5,clientX:sr.left+sr.width*.7,clientY:sr.top+sr.height*.3}));check(Math.abs(cpHSV.s-.7)<.01&&Math.abs(cpHSV.v-.7)<.01,'gliding updates SV when capture fails and move buttons is zero');
   document.dispatchEvent(new PointerEvent('pointerup',{bubbles:true,pointerId:5,clientX:sr.right+20,clientY:sr.top-20}));check(cpHSV.s===1&&cpHSV.v===1,'drag beyond color surface clamps to edges');check(color.value===originalColor&&calls.filter(x=>x==='save').length===savesBefore,'drag only previews without changing draft or saving waypoint');
   const beforeIdle=cpHSV.s;document.dispatchEvent(new PointerEvent('pointermove',{bubbles:true,pointerId:5,clientX:sr.left,clientY:sr.top}));check(cpHSV.s===beforeIdle,'released drag stops changing selection');
   const hue=document.getElementById('cp-hue'),hr=hue.getBoundingClientRect();hue.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,cancelable:true,button:0,clientX:hr.left,clientY:hr.top}));document.dispatchEvent(new MouseEvent('mousemove',{bubbles:true,buttons:0,clientX:hr.left,clientY:hr.top+hr.height/3}));document.dispatchEvent(new MouseEvent('mouseup',{bubbles:true,button:0,clientX:hr.left,clientY:hr.top+hr.height/3}));check(Math.abs(cpHSV.h-120)<1,'mouse-only hue glide works without pointer events');
   const alpha=document.getElementById('cp-alpha'),ar=alpha.getBoundingClientRect();alpha.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,cancelable:true,button:0,clientX:ar.left,clientY:ar.top}));document.dispatchEvent(new MouseEvent('mousemove',{bubbles:true,buttons:0,clientX:ar.left,clientY:ar.top+ar.height/2}));document.dispatchEvent(new MouseEvent('mouseup',{bubbles:true,button:0,clientX:ar.left,clientY:ar.top+ar.height/2}));check(Math.abs(cpHSV.a-.5)<.01,'opacity glides continuously');
   document.getElementById('cp-apply').click();check(/^#[0-9a-f]{6}$/i.test(color.value)&&color.value!==originalColor&&document.querySelector('[name=opacity]').value==='50','Apply color transfers RGB and opacity into waypoint draft');check(calls.filter(x=>x==='save').length===savesBefore,'Apply color does not save the waypoint prematurely');
   const appliedColor=color.value;color.click();cpApply(hexToHsva('#FF0000FF'));closeColorPicker();check(color.value===appliedColor,'closing waypoint picker without Apply leaves draft unchanged');
   switchView('mods');check(!document.body.classList.contains('wp-manager'),'manager layout does not leak into other module screens');openWaypointManager(false);
   return {status:'WAYPOINT_MANAGER_UI_OK',checks:count};
  }.toString()})()`);
  console.log(JSON.stringify(result));
  await win.webContents.executeJavaScript(`document.body.style.background='#050505';openWaypointManager(false);document.getAnimations().forEach(a=>{if(Number.isFinite(a.effect.getComputedTiming().endTime))a.finish();else a.pause();});document.querySelectorAll('[data-bat-motion]').forEach(el=>{delete el.dataset.batMotion;el.style.opacity='1';})`);
  await new Promise(resolve=>setTimeout(resolve,350));
  const visible=await win.webContents.executeJavaScript(`['#panel-layer','.mm-win','#view-waypoints'].every(s=>{const e=document.querySelector(s),c=getComputedStyle(e),r=e.getBoundingClientRect();return c.display!=='none'&&c.visibility==='visible'&&Number(c.opacity)>0&&r.width>0&&r.height>0})`);
  if(!visible)throw Error('Waypoint manager must be visibly rendered');
  fs.writeFileSync(path.join(__dirname,'../build/waypoint-manager-preview.png'),(await win.webContents.capturePage()).toPNG());
  win.setContentSize(854,480);await new Promise(resolve=>setTimeout(resolve,150));
  const reachable=await win.webContents.executeJavaScript(`(()=>{const b=document.querySelector('.wp-save'),r=b.getBoundingClientRect(),p=document.querySelector('.mm-body').getBoundingClientRect();return r.top>=p.top&&r.bottom<=p.bottom&&r.right<=innerWidth&&r.height>20&&b.closest('.wp-form-footer')!==null&&document.querySelector('.wp-form-body').getBoundingClientRect().height>100})()`);
  if(!reachable){console.log(await win.webContents.executeJavaScript(`JSON.stringify(['.wp-save','.mm-body','.wp-form-body','.wp-layout'].map(s=>({s,r:document.querySelector(s).getBoundingClientRect().toJSON()})))`));throw Error('Create waypoint must remain reachable in a small game window');}
  fs.writeFileSync(path.join(__dirname,'../build/waypoint-manager-small-preview.png'),(await win.webContents.capturePage()).toPNG());
  await win.webContents.executeJavaScript(`document.querySelector('[name=color]').click()`);await new Promise(resolve=>setTimeout(resolve,100));
  fs.writeFileSync(path.join(__dirname,'../build/waypoint-color-picker-preview.png'),(await win.webContents.capturePage()).toPNG());
  console.log('WAYPOINT_SMALL_WINDOW_SAVE_OK');
  app.exit(0);
 }catch(error){console.error(error.stack);app.exit(1);}
});
