"""Full-source 1.21.4 port generator. Not a compatibility certification."""
import argparse,json
from adapter_1214 import adapt
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--loader',choices=['fabric','forge'],required=True);p.add_argument('--project',required=True);a=p.parse_args()
root=Path(__file__).resolve().parent.parent;project=Path(a.project).resolve()
if project.parent!=root/'ports' or project.name!=a.loader+'-1.21.4':raise SystemExit('Only exact internal 1.21.4 target paths supported')
roots=[root/'src/main/java',project/'src/main/java',root/'ports/mc1.21.4/src']
if a.loader=='forge':roots.insert(1,root/'ports/forge/src/main/java')
seen=set();report=[]
for source_root in roots:
 for source in source_root.rglob('*.java'):
  relative=source.relative_to(source_root)
  if a.loader=='forge' and (str(relative).replace('\\','/').startswith('com/batclient/platform/fabric/') or source.name=='PerformanceMixinPlugin.java'):continue
  if relative in seen:raise RuntimeError('Ambiguous source override: '+str(relative))
  seen.add(relative);value=source.read_text(encoding='utf-8')
  if source_root!=root/'ports/mc1.21.4/src':value=adapt(relative.as_posix(),value)
  target=project/'build/version-sources'/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(value,encoding='utf-8')
visual = (root/'scripts/VisualParitySmoke.java').read_text(encoding='utf-8')
visual = adapt('Fabric1214Smoke.java', visual)
if a.loader == 'forge':
 visual = visual.replace('package com.batclient.platform.fabric;', 'package com.batclient.platform.forge;')
 visual = visual.replace('import net.fabricmc.api.ClientModInitializer;', '').replace('import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;', '')
 visual = visual.replace(' implements ClientModInitializer', '').replace('@Override public void onInitializeClient()', 'public void onInitializeClient()')
 visual = visual.replace('ClientTickEvents.END_CLIENT_TICK.register(this::tick);', 'com.batclient.platform.ClientHookServices.get().endTick(this::tick);')
fixture = project/'build/version-sources/com/batclient/platform'/a.loader
(fixture/'VisualParitySmoke.java').write_text(visual, encoding='utf-8')
name = 'Fabric1214Smoke.java' if a.loader == 'fabric' else 'ForgeFullSmoke.java'
entry = fixture/name
code = entry.read_text(encoding='utf-8')
method = 'public void onInitializeClient(){' if a.loader == 'fabric' else 'static void install(){'
assert code.count(method) == 1
entry.write_text(code.replace(method, method+'\n        if (Boolean.getBoolean("batclient.visualParity")) { new VisualParitySmoke().onInitializeClient(); return; }'),encoding='utf-8')
(project/'build/api-adaptation-report.json').write_text(json.dumps({'sources':len(seen),'note':'Compilation/native gates required'}))
print('MC1214_FULL_SOURCES_GENERATED:',len(seen),'sources; NOT verified compatibility')
