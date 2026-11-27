"""Full-source 1.21.5 port generator layered over 1.21.4. Not a compatibility certification."""
import argparse,json
from adapter_1214 import adapt as adapt_base
from adapter_1215 import adapt
from adapter_1218 import adapt as adapt_1218
from adapter_12111 import adapt as adapt_12111
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--loader',choices=['fabric','forge'],required=True);p.add_argument('--project',required=True);a=p.parse_args()
root=Path(__file__).resolve().parent.parent;project=Path(a.project).resolve()
if project.parent!=root/'ports' or project.name not in {a.loader+'-1.21.5',a.loader+'-1.21.8',a.loader+'-1.21.11'}:raise SystemExit('Expected an exact internal GPU port target path')
roots=[root/'src/main/java',project/'src/main/java',root/'ports/mc1.21.4/src',root/'ports/mc1.21.5/src']
if a.loader=='forge':roots.insert(1,root/'ports/forge/src/main/java')
seen=set();report=[]
for source_root in roots:
 for source in source_root.rglob('*.java'):
  if project.name.endswith('-1.21.11') and source.name in {'CubicColorAllocationMixin.java','FastCubicSampler.java'}:continue
  relative=source.relative_to(source_root)
  if a.loader=='forge' and (str(relative).replace('\\','/').startswith('com/batclient/platform/fabric/') or source.name=='PerformanceMixinPlugin.java'):continue
  if relative in seen:raise RuntimeError('Ambiguous source override: '+str(relative))
  seen.add(relative);value=source.read_text(encoding='utf-8')
  if source_root!=root/'ports/mc1.21.4/src' and source_root!=root/'ports/mc1.21.5/src':value=adapt_base(relative.as_posix(),value)
  value=adapt(relative.as_posix(),value)
  if project.name.endswith(('-1.21.8', '-1.21.11')):value=adapt_1218(relative.as_posix(),value)
  if project.name.endswith('-1.21.11'):value=adapt_12111(relative.as_posix(),value)
  target=project/'build/version-sources'/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(value,encoding='utf-8')
capture = root/'scripts/VisualParitySmoke.java'
fixture = project/'build/version-sources/com/batclient/platform'/a.loader
visual = adapt(capture.name, capture.read_text(encoding='utf-8')).replace(
 'save(client, file, ScreenshotRecorder.takeScreenshot(client.getFramebuffer()));',
 'ScreenshotRecorder.takeScreenshot(client.getFramebuffer(), image -> client.execute(() -> save(client, file, image)));'
)
if project.name.endswith('-1.21.11'):
 visual = adapt_12111(capture.name, visual)
if a.loader == 'forge':
 visual = visual.replace('package com.batclient.platform.fabric;', 'package com.batclient.platform.forge;')
 visual = visual.replace('import net.fabricmc.api.ClientModInitializer;', '').replace('import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;', '')
 visual = visual.replace(' implements ClientModInitializer', '').replace('@Override public void onInitializeClient()', 'public void onInitializeClient()')
 visual = visual.replace('ClientTickEvents.END_CLIENT_TICK.register(this::tick);', 'com.batclient.platform.ClientHookServices.get().endTick(this::tick);')
 entry = fixture/'ForgeFullSmoke.java'
 code = entry.read_text(encoding='utf-8')
 method = 'static void install(){'
 assert code.count(method) == 1
 entry.write_text(code.replace(method, method+'\n        if (Boolean.getBoolean("batclient.visualParity")) { new VisualParitySmoke().onInitializeClient(); return; }'),encoding='utf-8')
(fixture/capture.name).write_text(visual,encoding='utf-8')
(project/'build/api-adaptation-report.json').write_text(json.dumps({'sources':len(seen),'note':'Compilation/native gates required'}))
print('FULL_SOURCES_GENERATED:',project.name,len(seen),'sources; native gates pending')
