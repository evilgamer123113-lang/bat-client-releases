"""One isolated, actual intermediary1.21.1 launcher comparison. No account data copied."""
import argparse,hashlib,http.server,json,os,pathlib,re,shutil,subprocess,threading,time,urllib.request,uuid,zipfile
P=pathlib.Path
parser=argparse.ArgumentParser();parser.add_argument('--case',choices=['with-mod','without-mod'],required=True);parser.add_argument('--runtime',required=True);parser.add_argument('--world-template');parser.add_argument('--modules-off',action='store_true');parser.add_argument('--disable-crosshair',action='store_true');parser.add_argument('--jvm-profile',choices=['minimal','launcher','launcher-no-isolation','rust-launcher'],default='minimal');parser.add_argument('--background-diagnostic',action='store_true');parser.add_argument('--authlib-injector',action='store_true');parser.add_argument('--terrain-flight',action='store_true');a=parser.parse_args()
root=P(__file__).resolve().parent.parent
original=P(os.environ['APPDATA'])/'bat-client/minecraft_runtime'
runtime=P(a.runtime).resolve();allowed=P('D:/BatClientBuildCache').resolve()
if not runtime.is_relative_to(allowed) or runtime==allowed or runtime.exists():raise SystemExit('Require a new dedicated D: comparison directory')
running=subprocess.check_output(['powershell.exe','-NoProfile','-Command',"@(Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | Where-Object {$_.CommandLine -match 'KnotClient|BootstrapLauncher|net.minecraft.client.main.Main'}).Count"],text=True).strip()
if running!='0':raise SystemExit('A Minecraft JVM is still active; no comparison started')
base=json.loads((original/'versions/1.21.1/1.21.1.json').read_text());profile=json.loads((original/'versions/fabric-loader-1.21.1/fabric-loader-1.21.1.json').read_text())
if base['id']!='1.21.1' or profile['inheritsFrom']!='1.21.1' or profile['mainClass']!='net.fabricmc.loader.impl.launch.knot.KnotClient':raise SystemExit('Unexpected installed version')
runtime.mkdir();(runtime/'mods').mkdir();(runtime/'config').mkdir();(runtime/'natives').mkdir();(runtime/'resourcepacks').mkdir()
if a.world_template:
 template=P(a.world_template).resolve()
 if not template.is_relative_to(allowed) or not (template/'comparison-result.json').is_file():raise SystemExit('Only a completed private comparison world may be reused')
 if not (template/'saves/Comparison/level.dat').is_file():raise SystemExit('Private saved world missing')
 shutil.copytree(template/'saves/Comparison',runtime/'saves/Comparison')
def sha(file,algorithm='sha256'):return hashlib.new(algorithm,file.read_bytes()).hexdigest()

# Private provenance only. Exclude the runtime lock; record exact starting world bytes
# before the game has a chance to mutate the copied save.
world_files={p.relative_to(runtime/'saves/Comparison').as_posix():sha(p) for p in sorted((runtime/'saves/Comparison').rglob('*')) if p.is_file() and p.name!='session.lock'}
(runtime/'world-start-manifest.json').write_text(json.dumps(world_files,sort_keys=True,indent=2),encoding='utf-8')
def rules(items):
 result=not items
 for rule in items:
  system=rule.get('os',{})
  if system.get('name','windows')!='windows' or system.get('arch','amd64') not in ('amd64','x86_64'):continue
  if any(rule.get('features',{}).values()):continue
  if system.get('version') and not re.search(system['version'],'10.0'):continue
  result=rule['action']=='allow'
 return result
libraries={}
for lib in base['libraries']+profile['libraries']:
 if not rules(lib.get('rules',[])):continue
 parts=lib['name'].split(':');libraries[(parts[0],parts[1],parts[3] if len(parts)>3 else '')]=lib
cp=[]
for lib in libraries.values():
 item=lib.get('downloads',{}).get('artifact',{})
 parts=lib['name'].split(':');group,name,version=parts[:3];suffix='-'+parts[3] if len(parts)>3 else ''
 relative=item.get('path',group.replace('.','/')+'/'+name+'/'+version+'/'+name+'-'+version+suffix+'.jar')
 file=(original/'libraries'/relative).resolve()
 if not file.is_relative_to(original/'libraries') or not file.is_file():raise SystemExit('Missing installed library '+lib['name'])
 expected=item.get('sha1',lib.get('sha1'))
 if not expected and lib.get('url','').startswith('https://maven.fabricmc.net/'):
  checksum_url=lib['url'].rstrip('/')+'/'+relative+'.sha1'
  checksum_cache=allowed/'verified-fabric-sha1.json'
  known=json.loads(checksum_cache.read_text()) if checksum_cache.exists() else {}
  expected=known.get(checksum_url)
  if not expected:
   request=urllib.request.Request(checksum_url,headers={'User-Agent':'BatClient-private-validation'})
   expected=urllib.request.urlopen(request,timeout=20).read().decode().strip().split()[0]
 if not expected or sha(file,'sha1')!=expected:raise SystemExit('Library checksum unavailable/mismatch '+lib['name'])
 cp.append(file)
 if 'natives-windows' in file.name:
  with zipfile.ZipFile(file) as z:
   for entry in z.namelist():
    if entry.lower().endswith('.dll'):(runtime/'natives'/P(entry).name).write_bytes(z.read(entry))
client=original/'versions/1.21.1/1.21.1.jar'
if sha(client,'sha1')!=base['downloads']['client']['sha1']:raise SystemExit('Client checksum mismatch')
cp.append(client)
manifest=[]
for source in sorted((original/'mods').glob('*.jar')):
 destination_name=source.name
 if source.name==os.environ.get('BAT_BENCH_SKIP_MOD') or source.name in os.environ.get('BAT_BENCH_SKIP_MODS','').split(','):continue
 if source.name=='bat-client-mod-0.1.0.jar' and a.case=='without-mod':continue
 if source.name=='bat-client-mod-0.1.0.jar' and os.environ.get('BAT_BENCH_MOD'):
  source=P(os.environ['BAT_BENCH_MOD']).resolve()
  if not source.is_relative_to(allowed) or not source.is_file():raise SystemExit('Candidate must be a verified private D: file')
 shutil.copy2(source,runtime/'mods'/destination_name);manifest.append({'name':destination_name,'sha256':sha(source)})
fixture=root/'build/launcher-comparison/comparison-intermediary.jar'
if a.case=='with-mod' and not any(item['name']=='bat-client-mod-0.1.0.jar' for item in manifest):
 candidate=P(os.environ.get('BAT_BENCH_MOD','')).resolve()
 if not candidate.is_relative_to(allowed) or not candidate.is_file():raise SystemExit('With-mod comparison requires an explicit verified private candidate')
 destination=runtime/'mods/bat-client-mod-0.1.0.jar'
 shutil.copy2(candidate,destination);manifest.append({'name':destination.name,'sha256':sha(candidate)})
if not fixture.is_file():raise SystemExit('Compile actual remapped fixture first')
shutil.copy2(fixture,runtime/'mods'/fixture.name)
for name in ['batclient-hud.json','sodium-options.json','sodium-extra-options.json','dynamic_fps.json','immediatelyfast.json']:
 source=original/'config'/name
 if source.is_file():shutil.copy2(source,runtime/'config'/name)
if a.modules_off:
 if a.case!='with-mod':raise SystemExit('Module isolation requires Bat mod')
 config=runtime/'config/batclient-hud.json';data=json.loads(config.read_text(encoding='utf-8-sig'))
 for module in data.get('modules',{}).values():module['en']=False
 config.write_text(json.dumps(data),encoding='utf-8')
if a.case=='with-mod' and os.environ.get('BAT_BENCH_ENABLE_MINIMAP')=='1':
 config=runtime/'config/batclient-hud.json';data=json.loads(config.read_text(encoding='utf-8-sig')) if config.exists() else {}
 data.setdefault('modules',{}).setdefault('minimap',{}).update({'en':True,'updates':'Fast','range':96})
 config.write_text(json.dumps(data),encoding='utf-8')
if a.background_diagnostic:
 # Both diagnostic cases deliberately exclude background throttling. No user config is edited.
 config=runtime/'config/dynamic_fps.json';data=json.loads(config.read_text(encoding='utf-8-sig')) if config.exists() else {}
 data['enabled']=False;config.write_text(json.dumps(data),encoding='utf-8')
if a.disable_crosshair:
 config=runtime/'config/batclient-hud.json';data=json.loads(config.read_text(encoding='utf-8-sig'))
 data.setdefault('modules',{}).setdefault('crosshair',{})['en']=False;config.write_text(json.dumps(data),encoding='utf-8')
options=(original/'options.txt').read_text(encoding='utf-8')
options=re.sub(r'^fullscreen:.*$','fullscreen:false',options,flags=re.M)
(runtime/'options.txt').write_text(options,encoding='utf-8')
for line in options.splitlines():
 if line.startswith('resourcePacks:'):
  for entry in json.loads(line.split(':',1)[1]):
   if entry.startswith('file/'):
    source=(original/'resourcepacks'/entry[5:]).resolve()
    if not source.is_relative_to(original/'resourcepacks') or not source.is_file():raise SystemExit('Selected pack is not a safe local ZIP')
    shutil.copy2(source,runtime/'resourcepacks'/source.name)
agent=P(os.environ.get('BAT_BENCH_AGENT',str(P(os.environ['APPDATA'])/'bat-client/bat-cosmetics.jar')));shutil.copy2(agent,runtime/'bat-cosmetics.jar')
extra_mod=os.environ.get('BAT_BENCH_EXTRA_MOD')
if extra_mod:
 extra=P(extra_mod).resolve()
 if not extra.is_relative_to(allowed) or not extra.is_file():raise SystemExit('Extra diagnostic mod must be a verified private D: file')
 shutil.copy2(extra,runtime/'mods'/extra.name);manifest.append({'name':extra.name,'sha256':sha(extra)})
# Match the launcher: premain appends its agent to the system loader, NOT the
# game classpath. Adding it here lets Knot define an uninitialized second copy.
assets=original/'assets'
asset_candidates=[p for p in (assets/'indexes').glob('*.json') if sha(p,'sha1')==base['assetIndex']['sha1']]
if not asset_candidates:raise SystemExit('No asset index matching official checksum')
asset_index=asset_candidates[0].stem
username='BatComparison';identity=uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:'+username).encode()).digest(),version=3).hex
java=P(r'C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot/bin/java.exe')
cmd=[str(java),'-Xms1024M','-Xmx4096M','-XX:+UseG1GC','-XX:ParallelGCThreads=3','-XX:ConcGCThreads=2','-XX:MaxGCPauseMillis=200','-Djava.library.path='+str(runtime/'natives'),'-Dbatclient.test.noPresence=true','-javaagent:'+str(runtime/'bat-cosmetics.jar')+'=1.21.1,intermediary,http://127.0.0.1:9','-cp',os.pathsep.join(map(str,cp)),profile['mainClass'],'--username',username,'--version',profile['id'],'--gameDir',str(runtime),'--assetsDir',str(assets),'--assetIndex',asset_index,'--uuid',identity,'--accessToken','0','--userType','legacy','--versionType','release','--width','1680','--height','987']
if a.jvm_profile.startswith('launcher'):
 defaults=json.loads(subprocess.check_output(['node',str(root/'scripts/extract-launcher-jvm.cjs')],cwd=root))
 if a.jvm_profile=='launcher-no-isolation':defaults=[flag for flag in defaults if flag!='-Dfabric.debug.disableClassPathIsolation=true']
 cmd[3:7]=defaults+['-XX:MaxGCPauseMillis=200'] # Match the known effective custom override, preserve order.
if a.jvm_profile=='rust-launcher':
 # Rust balanced preset with a formerly failing custom G1 option, using 0.1.1 ordering.
 # This fixture tests compatibility; it does not edit or copy user JVM settings.
 cmd[1:7]=['-Xms512M','-Xmx4096M','-Dfile.encoding=UTF-8','-XX:+UseG1GC','-XX:+ParallelRefProcEnabled','-XX:+UnlockExperimentalVMOptions','-XX:G1NewSizePercent=30','-XX:G1MaxNewSizePercent=60','-Dfabric.debug.disableClassPathIsolation=true']
cosmetic_agent_enabled=os.environ.get('BAT_BENCH_NO_COSMETIC_AGENT')!='1'
if not cosmetic_agent_enabled:
 # Isolate agent/mod interaction without changing the game, authlib injector,
 # renderer, module settings, or any installed launcher files.
 cmd=[arg for arg in cmd if arg!='-javaagent:'+str(runtime/'bat-cosmetics.jar')+'=1.21.1,intermediary,http://127.0.0.1:9']
if a.terrain_flight:cmd.insert(1,'-Dbat.bench.terrainFlight=true')
if os.environ.get('BAT_BENCH_SAFEPOINT_DIAGNOSTIC')=='1':
 # Private diagnostics only. Warn about delayed safepoint arrival without
 # enabling AbortVMOnSafepointTimeout or changing production JVM settings.
 cmd[1:1]=['-XX:+SafepointTimeout','-XX:SafepointTimeoutDelay=500','-Xlog:safepoint=debug:file=safepoint.log:uptime,level,tags']
if os.environ.get('BAT_BENCH_PREPARE_WORLD')=='1':cmd.insert(1,'-Dbat.bench.prepareWorld=true')
if os.environ.get('BAT_BENCH_EXPECT_LOCAL_BATCH')=='1':cmd.insert(1,'-Dbat.bench.expectLocalBatch=true')
if os.environ.get('BAT_BENCH_EXPECT_MESH_EXECUTOR')=='1':cmd.insert(1,'-Dbat.bench.expectMeshExecutor=true')
if os.environ.get('BAT_BENCH_RENDERER_OPT_OUT')=='1':cmd.insert(1,'-Dbat.bench.rendererOptOut=true')
if os.environ.get('BAT_BENCH_RENDER_CHECKS')=='1':cmd[1:1]=['-Dbat.bench.renderChecks=true','-Dbat.bench.mapping='+os.environ['BAT_BENCH_MAPPING']]
if os.environ.get('BAT_BENCH_PRIORITY')=='7':cmd.insert(1,'-Dbat.bench.renderPriority=7')
if a.background_diagnostic:cmd.insert(1,'-Dbat.bench.backgroundDiagnostic=true')
if os.environ.get('BAT_BENCH_REQUIRE_FOCUS')=='1':cmd.insert(1,'-Dbat.bench.requireFocus=true')
if a.authlib_injector:
 # Exercise the real authlib transformer against a local synthetic service, without real accounts.
 public_key=subprocess.check_output(['node','-e',"console.log(require('node:crypto').generateKeyPairSync('rsa',{modulusLength:2048,publicKeyEncoding:{type:'spki',format:'pem'},privateKeyEncoding:{type:'pkcs8',format:'pem'}}).publicKey)"],text=True).strip()
 metadata={'meta':{'serverName':'Bat private comparison','implementationName':'private-benchmark','implementationVersion':'1'},'skinDomains':['127.0.0.1','localhost'],'signaturePublickey':public_key}
 class LocalMetadata(http.server.BaseHTTPRequestHandler):
  def log_message(self,*args):pass
  def do_GET(self):
   body=json.dumps(metadata).encode() if self.path=='/' else b'{}'
   self.send_response(200 if self.path=='/' else 404);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
  def do_POST(self):
   self.rfile.read(int(self.headers.get('Content-Length','0')));body=b'[]'
   self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
 service=http.server.ThreadingHTTPServer(('127.0.0.1',0),LocalMetadata)
 threading.Thread(target=service.serve_forever,daemon=True).start()
 api='http://127.0.0.1:'+str(service.server_port)+'/'
 authlib=P(os.environ['APPDATA'])/'bat-client/authlib-injector.jar';shutil.copy2(authlib,runtime/'authlib-injector.jar')
 cmd=[item.replace('1.21.1,intermediary,http://127.0.0.1:9','1.21.1,intermediary,'+api) for item in cmd]
 cmd[1:1]=['-javaagent:'+str(runtime/'authlib-injector.jar')+'='+api,'-Dauthlibinjector.debug']
cmd.insert(1,'-XX:StartFlightRecording=filename='+str(runtime/'benchmark.jfr')+',settings=profile,dumponexit=true')
(runtime/'comparison-inputs.json').write_text(json.dumps({'case':a.case,'authlibInjector':a.authlib_injector,'jvmProfile':a.jvm_profile,'backgroundDiagnostic':a.background_diagnostic,'modulesOff':a.modules_off,'mods':manifest,'fixture':sha(fixture),'agent':sha(agent),'cosmeticAgentEnabled':cosmetic_agent_enabled,'config':{p.name:sha(p) for p in (runtime/'config').glob('*') if p.is_file()},'packs':{p.name:sha(p) for p in (runtime/'resourcepacks').glob('*') if p.is_file()},'optionsSha256':hashlib.sha256(options.encode()).hexdigest(),'worldSeed':12345,'warmupSeconds':25,'measureSeconds':20},indent=2))
environment=os.environ.copy();environment['ALSOFT_DRIVERS']='null'
if os.environ.get('BAT_BENCH_CAPE')=='1':
 skins=P(os.environ['APPDATA'])/'bat-client/skins';groups={}
 for frame in skins.glob('cape-*-frame-*.png'):groups.setdefault(frame.name.rsplit('-frame-',1)[0],[]).append(frame)
 frames=max(groups.values(),key=len) if groups else []
 if len(frames)<2:raise SystemExit('No existing local animated cape for private native decode check')
 private_appdata=runtime/'private-appdata';private_skins=private_appdata/'bat-client/skins';private_skins.mkdir(parents=True)
 for frame in frames:shutil.copy2(frame,private_skins/('cape-batcomparison-frame-'+frame.name.rsplit('-frame-',1)[1]))
 environment['APPDATA']=str(private_appdata)
 cmd[1:1]=['-Dbatclient.cape.directory='+str(private_skins),'-Dbatclient.cape.player=BatComparison']
(runtime/'diagnostic-flags.json').write_text(json.dumps({
 'terrainFlight':a.terrain_flight,
 'requireFocus':not a.background_diagnostic or os.environ.get('BAT_BENCH_REQUIRE_FOCUS')=='1',
 'prepareWorld':os.environ.get('BAT_BENCH_PREPARE_WORLD')=='1',
 'safepointDiagnostic':os.environ.get('BAT_BENCH_SAFEPOINT_DIAGNOSTIC')=='1',
 'cosmeticAgentEnabled':cosmetic_agent_enabled,
 'expectLocalBatch':os.environ.get('BAT_BENCH_EXPECT_LOCAL_BATCH')=='1',
 'expectMeshExecutor':os.environ.get('BAT_BENCH_EXPECT_MESH_EXECUTOR')=='1',
 'requestedRenderPriority':7 if os.environ.get('BAT_BENCH_PRIORITY')=='7' else None,
 'capeFrameFiles':{p.name:sha(p) for p in sorted((runtime/'private-appdata/bat-client/skins').glob('*.png'))}
},indent=2),encoding='utf-8')
with (runtime/'native.log').open('w',encoding='utf-8') as log:
 process=subprocess.Popen(cmd,cwd=runtime,env=environment,stdout=log,stderr=subprocess.STDOUT)
 print('PRIVATE_COMPARISON_PID',process.pid,'CASE',a.case,flush=True)
 gpu_monitor=None
 if os.environ.get('BAT_BENCH_GPU')=='1':
  gpu_monitor=subprocess.Popen(['typeperf.exe',r'\GPU Engine(*)\Utilization Percentage','-si','1','-sc','300','-f','CSV','-o',str(runtime/'gpu-counter.csv'),'-y'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=subprocess.CREATE_NO_WINDOW)
 if a.terrain_flight:
  def monitor():
   import psutil
   tracked={}
   with (runtime/'process-cpu.jsonl').open('w') as samples:
    while process.poll() is None:
     readings=[]
     for entry in psutil.process_iter(['pid','name']):
      try:
       previous=tracked.setdefault(entry.pid,entry)
       cpu=previous.cpu_percent()
       if cpu>1:readings.append({'pid':entry.pid,'name':entry.info['name'],'cpuOneCorePercent':cpu,'priorityClass':int(previous.nice())})
      except (psutil.NoSuchProcess,psutil.AccessDenied):pass
     samples.write(json.dumps({'time':time.time(),'machinePercent':psutil.cpu_percent(),'processes':sorted(readings,key=lambda p:p['cpuOneCorePercent'],reverse=True)[:12]})+'\n');samples.flush();time.sleep(5)
  threading.Thread(target=monitor,daemon=True).start()
 # Allow JVM/mod startup in addition to the fixture's bounded 360-second test.
 # The old 300-second process deadline could preempt its 60-second settle phase.
 try:code=process.wait(timeout=480)
 except subprocess.TimeoutExpired:
  # The run is invalid now. Preserve diagnostics from this owned test JVM before
  # terminating it; an abrupt Windows exit otherwise loses the JFR recording.
  jcmd=java.with_name('jcmd.exe')
  if jcmd.is_file():
   for filename,arguments in [
    ('timeout-threads.txt',['Thread.print']),
    ('timeout-recording.txt',['JFR.dump','filename='+str(runtime/'timeout.jfr')]),
   ]:
    try:
     report=subprocess.run([str(jcmd),str(process.pid),*arguments],capture_output=True,text=True,timeout=15)
     (runtime/filename).write_text(report.stdout+report.stderr,encoding='utf-8')
    except (OSError,subprocess.TimeoutExpired) as error:
     (runtime/filename).write_text(type(error).__name__,encoding='utf-8')
  process.terminate();process.wait();raise SystemExit('Private comparison timed out; no pass')
 finally:
  if gpu_monitor is not None and gpu_monitor.poll() is None:
   gpu_monitor.terminate();gpu_monitor.wait(timeout=10)
result=runtime/'comparison-result.json'
if os.environ.get('BAT_BENCH_RENDER_CHECKS')=='1':result=runtime/'rendering-result.txt'
if os.environ.get('BAT_BENCH_RENDERER_OPT_OUT')=='1':result=runtime/'renderer-choice-result.txt'
if code or not result.exists():raise SystemExit('Comparison failed; inspect private native.log, exit='+str(code))
print('COMPARISON_RESULT',result.read_text(),flush=True)
