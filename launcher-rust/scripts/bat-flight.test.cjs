const assert = require('node:assert/strict');
const test = require('node:test');
const {create} = require('../ui/bat-flight.js');
const bounds = {left:100,right:640,top:24,bottom:165};
const settings = {speed:1,react:true,hang:true};

test('keeps similar trajectories at 30 and 120 FPS',()=>{
  function at(rate){
    const flight=create(bounds,()=>.45);
    for(let n=0;n<rate*2;n++)flight.step(1/rate,settings);
    return flight.bat;
  }
  const a=at(30),b=at(120);
  assert.ok(Math.hypot(a.x-b.x,a.y-b.y)<3);
});

test('approaches the top, hangs, and takes off again',()=>{
  const flight=create(bounds,()=>.1);
  let hung=false,tookOff=false;
  for(let n=0;n<1500;n++){
    const bat=flight.step(1/60,settings);
    assert.ok(bat.x>=bounds.left&&bat.x<=bounds.right);
    assert.ok(bat.y>=bounds.top&&bat.y<=bounds.bottom);
    if(bat.state==='Hanging'){hung=true;assert.equal(bat.y,bounds.top);}
    if(hung&&bat.state==='TakingOff'){tookOff=true;break;}
  }
  assert.ok(hung&&tookOff);
});

test('follows a drag and stays within resized bounds',()=>{
  const flight=create(bounds,()=>.4);
  for(let n=0;n<60;n++)flight.step(1/60,settings);
  flight.hold();
  flight.drag(250,95);
  assert.equal(flight.bat.state,'Held');
  for(let n=0;n<15;n++)flight.step(1/60,settings);
  assert.equal(flight.bat.x,250);
  assert.equal(flight.bat.y,95);
  flight.release();
  assert.equal(flight.bat.state,'Flying');
  const small={left:55,right:240,top:24,bottom:76};
  flight.resize(small);
  for(let n=0;n<450;n++){
    const bat=flight.step(1/60,{...settings,speed:1.35,hang:false});
    assert.ok(bat.x>=small.left&&bat.x<=small.right);
    assert.ok(bat.y>=small.top&&bat.y<=small.bottom);
    assert.notEqual(bat.state,'Hanging');
  }
});

test('hangs beneath a button and leaves when that button disappears',()=>{
  const flight=create(bounds,()=>.1);
  const perch={id:'button',x:530,y:95};
  flight.setPerches([perch]);
  for(let n=0;n<900&&flight.bat.state!=='Hanging';n++)flight.step(1/60,settings);
  assert.equal(flight.bat.state,'Hanging');
  assert.equal(flight.bat.x,perch.x);
  assert.equal(flight.bat.y,perch.y);
  flight.setPerches([{...perch,y:102}]);
  assert.equal(flight.bat.y,102);
  flight.setPerches([]);
  assert.equal(flight.bat.state,'TakingOff');
});

test('swoops, circles and glides without skipping across the screen',()=>{
  const area={left:46,right:1274,top:32,bottom:868};
  for(const [choice,expected] of [[.32,'Gliding'],[.49,'Hovering'],[.64,'Circling'],[.82,'Swooping']]){
    const flight=create(area,()=>choice);
    let sawState=false;
    const positions=[];
    for(let n=0;n<600;n++){
      const {x,y}=flight.bat;
      const bat=flight.step(1/60,{...settings,hang:false});
      assert.ok(Math.hypot(x-bat.x,y-bat.y)<4);
      assert.ok(bat.x>=area.left&&bat.x<=area.right&&bat.y>=area.top&&bat.y<=area.bottom);
      if(bat.state===expected){
        sawState=true;
        positions.push({x:bat.x,y:bat.y});
      }
    }
    assert.ok(sawState,expected);
    if(expected==='Circling'||expected==='Swooping'){
      const y=positions.map(point=>point.y);
      assert.ok(Math.max(...y)-Math.min(...y)>10);
    }
  }
});

test('petting closes the eyes briefly without interrupting flight',()=>{
  const flight=create(bounds,()=>.45);
  flight.pet();
  assert.equal(flight.step(1/60,settings).blink,true);
  assert.equal(flight.bat.state,'Flying');
  for(let n=0;n<45;n++)flight.step(1/60,settings);
  assert.equal(flight.bat.blink,false);
});
