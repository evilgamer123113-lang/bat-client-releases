'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'..');
const read=name=>fs.readFileSync(path.join(root,name),'utf8');
test('chunk scheduler is built in and ignores old module enable flags and fixed budgets',()=>{
  const source=read('src/main/java/com/batclient/ChunkSmoothing.java');
  assert.doesNotMatch(source,/HudConfig|JsonObject|snapshot\(/);
  assert.match(source,/enabled\(\)\{return true;/);
  assert.doesNotMatch(read('src/main/resources/assets/batclient/web/app.js'),/id:\s*"smoothchunks"/);
});
test('performance implementation has no JVM/GC/priority/options/packet writers or input queue',()=>{
  for(const file of ['src/main/java/com/batclient/performance/BuiltinPerformance.java','src/main/java/com/batclient/performance/AdaptiveChunkBudget.java']){
    const source=read(file);
    assert.doesNotMatch(source,/System\.setProperty|System\.gc\(|Runtime\.getRuntime|setPriority\(|setValue\(|Thread\.sleep|sendPacket\(|Executor|BlockingQueue/);
  }
});
test('fixed global worker cap is removed; normal chunk-generation parallelism is preserved',()=>{
  const metadata=JSON.parse(read('src/main/resources/batclient.mixins.json'));
  assert.ok(!metadata.client.includes('BackgroundWorkerBudgetMixin'));
});
test('renderer alternatives own their scheduler; unrelated Bat features still apply',()=>{
  const source=read('src/main/java/com/batclient/performance/PerformanceMixinPlugin.java');
  const compatibility=read('src/main/java/com/batclient/performance/RendererCompatibility.java');
  for(const id of ['sodium','embeddium','rubidium','optifabric'])assert.ok(compatibility.includes('"'+id+'"'));
  assert.match(source,/SmoothChunkRebuildMixin/);assert.match(source,/SmoothChunkUploadMixin/);
  const metadata=JSON.parse(read('src/main/resources/batclient.mixins.json'));
  assert.equal(metadata.plugin,'com.batclient.performance.PerformanceMixinPlugin');
  assert.match(read('proguard.pro'),/-keep class com\.batclient\.performance\.PerformanceMixinPlugin/);
});
test('native HUD caches layout and invalidates on persisted/preview/profile replacements and resize',()=>{
  assert.match(read('src/main/java/com/batclient/HudRenderer.java'),/HudLayoutEngine\.loadRenderStates\(sw, sh\)/);
  assert.match(read('src/main/java/com/batclient/hud/HudLayoutEngine.java'),/revision!=renderRevision\|\|width!=renderWidth\|\|height!=renderHeight/);
  const source=read('src/main/java/com/batclient/HudConfig.java');
  assert.match(source,/setMemory\(JsonObject next\) \{memory=next;revision\+\+;/);
  assert.doesNotMatch(source,/setMemory\(JsonObject next\) \{setMemory/);
});
test('gameplay does not automatically create a hidden web overlay',()=>{
  const source=read('src/main/java/com/batclient/BatClientMod.java');
  assert.doesNotMatch(source,/client\.player\.age >= 60/);
  assert.match(source,/Gameplay HUD is native/);
});
test('ordinary mouse arguments pass through unchanged and inactive zoom skips config lookup',()=>{
  const source=read('src/main/java/com/batclient/mixin/MouseZoomMixin.java');
  assert.match(source,/if\(scale==1\)return;/);
  assert.match(read('src/main/java/com/batclient/ClientMods.java'),/zoomMouseScale\(\) \{\s*if \(zoomFactor == 1\) return 1;/);
});
test('performance ownership handshake only sets the private agent coordination marker',()=>{
  assert.match(read('src/main/java/com/batclient/performance/BuiltinPerformance.java'),/PerformanceOwnership\.claim\(\)/);
  const source=read('src/main/java/com/batclient/performance/PerformanceOwnership.java');
  assert.equal((source.match(/System\.setProperty\(/g)||[]).length,1);
  assert.match(source,/System\.setProperty\("batclient\.performance\.owner", "mod"\)/);
  assert.doesNotMatch(source,/setPriority|Runtime\.|System\.gc|setValue/);
});
