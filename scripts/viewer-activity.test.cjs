'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {createViewerActivity}=require('../launcher/viewer-activity');
function fixture(){
 const events={},classes=new Set();let focused=true,callbacks=[];
 const doc={hidden:false,hasFocus:()=>focused,head:{appendChild(){}},createElement:()=>({}),documentElement:{classList:{contains:x=>classes.has(x),toggle:(x,on)=>on?classes.add(x):classes.delete(x)}},addEventListener:(n,f)=>events[n]=f,removeEventListener:(n)=>delete events[n]};
 const win={innerWidth:800,innerHeight:600,getComputedStyle:c=>({visibility:c.visibility||'visible'}),requestAnimationFrame:f=>{callbacks.push(f);return callbacks.length;},cancelAnimationFrame(){callbacks=[];},MutationObserver:class{constructor(f){events.mutation=f;}observe(){}disconnect(){}},addEventListener:(n,f)=>events[n]=f,removeEventListener:n=>delete events[n]};
 const canvas={isConnected:true,shown:true,getClientRects(){return this.shown?[{}]:[];},getBoundingClientRect:()=>({top:0,left:0,right:200,bottom:300,width:200,height:300})};
 const manager=createViewerActivity({window:win,document:doc});
 return {manager,doc,canvas,events,classes,focus:value=>{focused=value;events[value?'focus':'blur']();},flush:()=>{const queue=callbacks;callbacks=[];queue.forEach(f=>f());}};
}
test('hidden, offscreen and background viewers stop; visible focused viewers resume with cape frames intact',()=>{
 const f=fixture();let pauses=0,resumes=0;
 const viewer=f.manager.track({canvas:f.canvas,renderPaused:false,__pauseCape:()=>pauses++,__resumeCape:()=>resumes++});
 assert.equal(viewer.renderPaused,false);f.focus(false);assert.equal(viewer.renderPaused,true);assert.ok(pauses);assert.ok(f.classes.has('launcher-background'));
 f.focus(true);assert.equal(viewer.renderPaused,false);assert.ok(resumes);
 f.canvas.shown=false;f.events.mutation();f.flush();assert.equal(viewer.renderPaused,true);
 f.canvas.shown=true;f.manager.refresh();assert.equal(viewer.renderPaused,false);
 f.canvas.visibility='hidden';f.manager.refresh();assert.equal(viewer.renderPaused,true);
 f.canvas.visibility='visible';f.doc.hidden=true;f.events.visibilitychange();assert.equal(viewer.renderPaused,true);
 f.doc.hidden=false;f.events.visibilitychange();assert.equal(viewer.renderPaused,false);
 f.manager.dispose();assert.equal(viewer.renderPaused,true);
});
test('detached/disposed previews stay stopped and cannot leak rendering after teardown',()=>{
 const f=fixture(),viewer=f.manager.track({canvas:f.canvas,renderPaused:false});
 f.canvas.isConnected=false;f.manager.refresh();assert.equal(viewer.renderPaused,true);
 viewer.disposed=true;f.manager.refresh();f.manager.dispose();assert.throws(()=>f.manager.track(viewer),/closed/);
});
test('renderer patch is restricted to four constructors and cape scheduler, and is idempotent',()=>{
 const fs=require('node:fs'),path=require('node:path'),{applyViewerActivity}=require('./apply-viewer-activity.cjs');
 const html=fs.readFileSync(path.join(__dirname,'../index.html'),'utf8'),patched=applyViewerActivity(html);
 assert.equal(applyViewerActivity(patched),patched);
 assert.equal((patched.match(/viewerActivity\.track\(new skinview3d\.SkinViewer/g)||[]).length,4);
 assert.match(patched,/viewer\.disposed\|\|viewer\.renderPaused/);
 assert.throws(()=>applyViewerActivity('<html>unknown</html>'),/Unrecognized/);
});
