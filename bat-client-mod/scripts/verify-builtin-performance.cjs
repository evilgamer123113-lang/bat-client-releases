'use strict';
const fs=require('node:fs');
const path=require('node:path');
const {spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),output=path.join(root,'build/builtin-performance/classes');
fs.mkdirSync(output,{recursive:true});
function run(command,args){const result=spawnSync(command,args,{cwd:root,stdio:'inherit'});if(result.error)throw result.error;if(result.status!==0)throw Error(command+' failed ('+result.status+')');}
try{
  run('javac',['--release','8','-Xlint:-options','-d',output,'src/main/java/com/batclient/performance/AdaptiveChunkBudget.java','src/main/java/com/batclient/performance/RendererCompatibility.java','src/main/java/com/batclient/hud/FrameWorkBudget.java','scripts/AdaptiveChunkBudgetSmoke.java']);
  run('java',['-cp',output,'AdaptiveChunkBudgetSmoke']);
  run('javac',['--release','8','-Xlint:-options','-d',output,'src/main/java/com/batclient/hud/FrameWorkBudget.java','scripts/FrameWorkBudgetSmoke.java']);
  run('java',['-cp',output,'FrameWorkBudgetSmoke']);
  run('javac',['--release','21','-cp',output,'-d',output,'src/main/java/com/batclient/hud/TerrainMapRaster.java','scripts/TerrainMapSmoke.java']);
  run('java',['-cp',output,'TerrainMapSmoke']);
  run('javac',['--release','8','-Xlint:-options','-d',output,'src/main/java/com/batclient/performance/NearestReadyBatch.java','scripts/NearestReadyBatchSmoke.java']);
  run('java',['-cp',output,'NearestReadyBatchSmoke']);
  run('javac',['--release','8','-Xlint:-options','-d',output,'src/main/java/com/batclient/performance/MeshWorkExecutor.java','scripts/MeshWorkExecutorSmoke.java']);
  run('java',['-cp',output,'MeshWorkExecutorSmoke']);
  run('javac',['--release','8','-Xlint:-options','-d',output,'src/main/java/com/batclient/hud/BiomeLabelCache.java','scripts/BiomeLabelCacheSmoke.java']);
  run('java',['-cp',output,'BiomeLabelCacheSmoke']);
  run('javac',['--release','8','-Xlint:-options','-d',output,'src/main/java/com/batclient/hud/SkinAlphaMask.java','src/main/java/com/batclient/hud/RenderPassCache.java','scripts/CustomRenderingSmoke.java']);
  run('java',['-cp',output,'CustomRenderingSmoke']);
  run('node',['--test','scripts/builtin-performance.test.cjs']);
  run('node',['scripts/verify-hud-persistence.cjs']);
}catch(error){console.error(error.message);process.exitCode=1;}
