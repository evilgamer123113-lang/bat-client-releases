"use strict";
(() => {
  const main=document.querySelector('#main');
  const titlebar=document.querySelector('.titlebar');
  const sidebar=document.querySelector('.sidebar');
  const perchControls='button:not(:disabled), a[href]';
  const settingsPanel=document.querySelector('#settings-pane-1');
  const reduce=matchMedia('(prefers-reduced-motion: reduce)');
  const defaults={enabled:true,react:true,hang:true,particles:false,speed:'normal'};
  let preferences={...defaults},confirmed=preferences,revision=0,saveQueue=Promise.resolve();
  const native=window.__TAURI__?.core?.invoke;
  const fieldset=document.createElement('section');
  fieldset.className='flying-bat-settings';
  const heading=document.createElement('h3');
  heading.textContent='Flying Bat';
  const description=document.createElement('p');
  description.textContent='Hold the left mouse button to carry the bat. Right-click it to pet it.';
  fieldset.append(heading,description);
  const controls={};
  for(const [key,label] of [['enabled','Enable Flying Bat'],['hang','Hanging Animation'],['particles','Particles'],['speed','Animation Speed']]){
    const row=document.createElement('label');
    const caption=document.createElement('span');
    caption.textContent=label;
    const control=document.createElement(key==='speed'?'select':'input');
    if(key==='speed'){
      for(const speed of ['slow','normal','fast'])control.add(new Option(speed[0].toUpperCase()+speed.slice(1),speed));
    }else{
      control.type='checkbox';
      control.setAttribute('role','switch');
      row.className='settings-toggle-row';
    }
    row.append(caption,control);
    fieldset.append(row);
    controls[key]=control;
    control.addEventListener('change',()=>{
      const version=++revision;
      const next={...preferences,[key]:key==='speed'?control.value:control.checked};
      apply(next);
      saveQueue=saveQueue.then(async()=>{
        try{
          if(native)await native('tools_action',{action:'flying_bat',payload:next});
          else localStorage.setItem('bat-flying-preview',JSON.stringify(next));
          confirmed=next;
        }catch(error){
          if(version===revision)apply(confirmed);
          notify(String(error));
        }
      });
    });
  }
  settingsPanel?.append(fieldset);
  const layer=document.createElement('div');
  layer.className='flying-bat-layer';
  layer.setAttribute('aria-hidden','true');
  const canvas=document.createElement('canvas');
  canvas.width=BatSprite.width;canvas.height=BatSprite.height;
  const heart=document.createElement('span');
  heart.className='flying-bat-heart';
  layer.append(canvas,heart);
  document.body.append(layer);
  const context=canvas.getContext('2d');
  if(!context){console.warn('Flying Bat: canvas rendering is unavailable.');return;}
  let frameFor;
  try{frameFor=BatSprite.create(()=>document.createElement('canvas'));}
  catch(error){console.warn('Flying Bat: could not prepare the pixel sprites.',error);return;}
  const dust=Array.from({length:6},()=>{
    const element=document.createElement('i');
    layer.append(element);
    return {element,life:0,x:0,y:0};
  });
  let flight=null,bounds=null,drag=null,animation=0,lastTime=0,shownFrame=null,trailTime=0,layoutDirty=true;
  let petEffect=null;
  const hitMasks=new WeakMap();
  let ready=!!document.getElementById('startup-screen')?.hidden;
  let focused=document.hasFocus();
  function apply(value){
    preferences={...defaults,...value};
    for(const [key,control] of Object.entries(controls)){
      if(key==='speed')control.value=preferences[key];
      else control.checked=preferences[key];
    }
    sync();
  }
  function measure(){
    const width=layer.clientWidth,height=layer.clientHeight;
    const left=46,right=width-46,top=32,bottom=height-32;
    if(right<=left||bottom<=top)return false;
    if(!bounds||bounds.right!==right||bounds.bottom!==bottom){
      bounds={left,right,top,bottom};
      if(flight)flight.resize(bounds);
      else flight=BatFlight.create(bounds);
    }
    return true;
  }
  function refreshLayout(){
    layoutDirty=false;
    if(!preferences.hang)return;
    const ledges=[titlebar];
    const active=document.querySelector('.page.active');
    if(sidebar)ledges.push(...sidebar.querySelectorAll(perchControls));
    if(active)ledges.push(...active.querySelectorAll(perchControls));
    const perches=[];
    for(const ledge of ledges){
      if(perches.length===24)break;
      if(!ledge)continue;
      const rect=ledge.getBoundingClientRect();
      const x=ledge===titlebar?rect.left+rect.width*.55:(rect.left+rect.right)/2;
      const y=rect.bottom+20;
      if(rect.width<26||rect.height<20||x<bounds.left||x>bounds.right||y<bounds.top||y>bounds.bottom)continue;
      const edge=document.elementFromPoint(x,rect.bottom-2);
      if(!ledge.contains(edge))continue;
      if(document.elementFromPoint(x,y)?.closest(perchControls))continue;
      perches.push({id:ledge,x,y});
    }
    const ceiling=perches.find(perch=>perch.id===titlebar);
    if(ceiling){
      perches.push({id:'ceiling',x:bounds.left+(bounds.right-bounds.left)*.76,y:ceiling.y});
    }
    flight.setPerches(perches);
  }
  const reduced=()=>reduce.matches||document.body.classList.contains('reduce-motion');
  function draw(frame){
    if(frame===shownFrame)return;
    shownFrame=frame;
    context.clearRect(0,0,canvas.width,canvas.height);
    context.drawImage(frame,0,0);
  }
  function position(bat){
    const angle=bat.flip+bat.bank;
    canvas.style.transform='translate3d('+bat.x.toFixed(1)+'px,'+bat.y.toFixed(1)+'px,0) translate(-50%,-50%) rotate('+angle+'rad) scaleX('+(1-Math.abs(bat.facing)*.12)+')';
  }
  function tick(now){
    animation=0;
    const delta=lastTime?Math.min((now-lastTime)/1000,.05):0;
    lastTime=now;
    if(layoutDirty)refreshLayout();
    const bat=flight.step(delta,{
      hang:preferences.hang,
      speed:preferences.speed==='slow'?.7:preferences.speed==='fast'?1.35:1
    });
    // The wing tips trail the shoulders through a fast downstroke and slower recovery.
    const flap=Math.sin(bat.phase-.4*Math.cos(bat.phase));
    draw(frameFor(flap*bat.stroke,bat.fold,bat.blink));
    position(bat);
    if(preferences.particles){
      trailTime+=delta;
      if(trailTime>.1&&bat.state!=='Hanging'){
        trailTime=0;
        const mote=dust.find(item=>item.life<=0);
        if(mote){mote.life=.42;mote.x=bat.x-bat.facing*14;mote.y=bat.y+5;}
      }
      for(const mote of dust){
        mote.life=Math.max(0,mote.life-delta);
        mote.element.style.opacity=String(mote.life*.7);
        if(mote.life)mote.element.style.transform='translate('+Math.round(mote.x)+'px,'+Math.round(mote.y+(1-mote.life)*8)+'px)';
      }
    }
    animation=requestAnimationFrame(tick);
  }
  function sync(){
    if(animation)cancelAnimationFrame(animation);
    animation=lastTime=0;
    finishDrag();
    for(const mote of dust){mote.life=0;mote.element.style.opacity='0';}
    layer.hidden=!ready||!preferences.enabled||document.hidden;
    if(layer.hidden)return;
    if(!measure()){layer.hidden=true;return;}
    layoutDirty=true;
    position(flight.bat);
    draw(reduced()||!shownFrame?frameFor(0,0,false):shownFrame);
    if(focused&&!reduced())animation=requestAnimationFrame(tick);
  }
  function hitBat(event){
    if(!shownFrame||layer.hidden||event.target.closest('button,input,a,select,textarea,label,[role="button"],[popover],dialog,[data-tauri-drag-region]'))return false;
    if(document.querySelector('dialog[open],:popover-open'))return false;
    const rect=canvas.getBoundingClientRect();
    if(event.clientX<rect.left||event.clientX>rect.right||event.clientY<rect.top||event.clientY>rect.bottom)return false;
    // Invert the pose around the sprite's center, then sample its transparent pixel mask.
    const pose=new DOMMatrix(getComputedStyle(canvas).transform);
    const vector=new DOMPoint(event.clientX-(rect.left+rect.right)/2,event.clientY-(rect.top+rect.bottom)/2,0,0).matrixTransform(pose.inverse());
    const x=Math.floor(canvas.width*(.5+vector.x/canvas.offsetWidth));
    const y=Math.floor(canvas.height*(.5+vector.y/canvas.offsetHeight));
    if(x<0||y<0||x>=canvas.width||y>=canvas.height)return false;
    let pixels=hitMasks.get(shownFrame);
    if(!pixels){
      pixels=shownFrame.getContext('2d').getImageData(0,0,canvas.width,canvas.height).data;
      hitMasks.set(shownFrame,pixels);
    }
    return pixels[(y*canvas.width+x)*4+3]===255;
  }
  function finishDrag(event){
    if(!drag||(event&&event.pointerId!==drag.id))return;
    const id=drag.id;
    drag=null;
    flight.release();
    if(layer.hasPointerCapture(id))layer.releasePointerCapture(id);
  }
  document.addEventListener('pointerdown',event=>{
    if(drag||(event.button!==0&&event.button!==2)||!hitBat(event))return;
    event.preventDefault();
    event.stopPropagation();
    const bat=flight.bat;
    if(event.button===2){
      flight.pet();
      petEffect?.cancel();
      heart.style.left=bat.x+'px';
      heart.style.top=(bat.y-22)+'px';
      petEffect=heart.animate(
        reduced()?[{opacity:1},{opacity:0}]:[{opacity:1,transform:'translate(-50%,0) scale(.7)'},{opacity:1,transform:'translate(-50%,-12px) scale(1)' ,offset:.35},{opacity:0,transform:'translate(-50%,-26px) scale(1)'}],
        {duration:650,easing:'ease-out'});
      if(reduced())draw(frameFor(0,0,true));
      petEffect.onfinish=()=>{if(reduced()&&!layer.hidden)draw(frameFor(0,0,false));};
      return;
    }
    flight.hold();
    drag={id:event.pointerId,offsetX:bat.x-event.clientX,offsetY:bat.y-event.clientY};
    if(event.isTrusted)layer.setPointerCapture(event.pointerId);
  },true);
  document.addEventListener('pointermove',event=>{
    if(!drag||event.pointerId!==drag.id)return;
    flight.drag(event.clientX+drag.offsetX,event.clientY+drag.offsetY);
    position(flight.bat);
  },true);
  for(const event of ['pointerup','pointercancel','lostpointercapture'])document.addEventListener(event,finishDrag,true);
  const markLayout=()=>{layoutDirty=true;};
  window.addEventListener('resize',sync);
  document.addEventListener('scroll',markLayout,{capture:true,passive:true});
  if(main){
    new MutationObserver(markLayout).observe(main,{childList:true,subtree:true});
    main.addEventListener('animationend',markLayout);
  }
  new MutationObserver(sync).observe(document.body,{attributes:true,attributeFilter:['class']});
  for(const event of ['visibilitychange'])document.addEventListener(event,sync);
  reduce.addEventListener('change',sync);
  window.addEventListener('blur',()=>{focused=false;sync();});
  window.addEventListener('focus',()=>{focused=true;sync();});
  window.addEventListener('bat-page-changed',markLayout);
  window.addEventListener('bat-startup-complete',()=>{ready=true;sync();});
  apply(defaults);
  if(native){
    native('tools_action',{action:'flying_bat'}).then(value=>{
      if(!revision&&value?.speed){confirmed={...defaults,...value};apply(confirmed);}
    }).catch(error=>console.warn('Flying Bat: could not read saved preferences.',error));
  }else{
    try{confirmed={...defaults,...JSON.parse(localStorage.getItem('bat-flying-preview')||'{}')};apply(confirmed);}catch{}
  }
})();
