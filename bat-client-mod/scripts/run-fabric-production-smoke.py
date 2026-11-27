"""Actual protected Fabric production launch; no user accounts or worlds."""
import argparse, concurrent.futures, hashlib, http.client, json, os, re, shutil, subprocess, time, urllib.request, uuid, zipfile
from urllib.error import HTTPError, URLError
from pathlib import Path

def java_path(path):
    value=str(path)
    if value.startswith('\\\\?\\UNC\\'):return '\\\\'+value[8:]
    return value[4:] if value.startswith('\\\\?\\') else value

def canonical(path):
    return Path(java_path(path.resolve()))
parser=argparse.ArgumentParser()
parser.add_argument('--runtime',required=True)
parser.add_argument('--mod',required=True)
parser.add_argument('--minecraft',choices=['1.8','1.8.9','1.16.5','1.17.1','1.18.2','1.19.2','1.19.4','1.20.1','1.20.4','1.20.6','1.21.1','1.21.4','1.21.5','1.21.8','1.21.11'],default='1.20.6')
parser.add_argument('--agent')
parser.add_argument('--java',default='java')
parser.add_argument('--resume-preparation',action='store_true')
parser.add_argument('--interactive',action='store_true')
parser.add_argument('--visual-parity',action='store_true')
args=parser.parse_args()
version=args.minecraft
label=version.replace('.','')
api_version={'1.21.11':'0.141.6+1.21.11','1.21.8':'0.136.1+1.21.8','1.21.5':'0.128.2+1.21.5','1.21.4':'0.119.4+1.21.4','1.21.1':'0.102.1+1.21.1','1.20.6':'0.100.8+1.20.6','1.20.4':'0.97.3+1.20.4','1.20.1':'0.92.12+1.20.1','1.19.4':'0.87.2+1.19.4','1.19.2':'0.77.0+1.19.2','1.18.2':'0.77.0+1.18.2'}.get(version)
root=Path(__file__).resolve().parent.parent
running=subprocess.check_output(['powershell.exe','-NoProfile','-Command',"@(Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | Where-Object {$_.CommandLine -match 'KnotClient|BootstrapLauncher|net.minecraft.client.main.Main|devlaunchinjector'}).Count"],text=True).strip()
if running!='0':raise SystemExit('Minecraft is already running; no second native game started')
runtime=Path(args.runtime).resolve()
source=Path(args.mod).resolve()
cache=Path(r'D:\BatClientBuildCache\gradle-home\caches')
private_root=Path('D:/BatClientBuildCache/mod-native')
if not ((runtime.is_relative_to(root/'build') and runtime!=root/'build') or (runtime.is_relative_to(private_root) and runtime!=private_root)):
    raise SystemExit('Use a new dedicated private build runtime')
if args.interactive:
    if not (runtime/'result.txt').is_file() or not (runtime/'result.txt').read_text().startswith('BAT_FABRIC_'+label+'_INTERNAL_WORLD_OK:'):
        raise SystemExit('Interactive inspection requires an already tested isolated runtime')
elif runtime.exists() and (not args.resume_preparation or (runtime/'production-smoke.log').exists() or (runtime/'saves').exists()):
    raise SystemExit('Existing runtime is not a failed preparation that can be safely resumed')
expected_libs=root/'build/libs' if version=='1.21.1' else root/'ports'/('fabric-'+version)/'build/libs'
if not source.is_relative_to(expected_libs) or not source.is_file():
    raise SystemExit('Expected actual internal Fabric'+version+' distribution')
with zipfile.ZipFile(source) as archive:
    mod=json.loads(archive.read('fabric.mod.json'))
    if mod['id']!='batclient' or mod['depends']['minecraft']!=version:raise SystemExit('Game identity mismatch')
    if archive.getinfo('assets/batclient/web/app.js.bin').file_size<1:raise SystemExit('Protected UI absent')
def fetch(url):
    if not url.startswith('https://'):raise RuntimeError('HTTPS dependencies only')
    request=urllib.request.Request(url,headers={'User-Agent':'BatClient-internal-test'})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request,timeout=45) as response:
                return response.read()
        except HTTPError as error:
            if attempt==2 or error.code not in {429,500,502,503,504}:raise
        except (URLError,TimeoutError,ConnectionError,http.client.HTTPException):
            if attempt==2:raise
            time.sleep(2*(attempt+1))
base=json.loads((cache/'fabric-loom'/version/'mojang_minecraft_info.json').read_text())
meta_host='https://meta.legacyfabric.net' if version in ('1.8','1.8.9') else 'https://meta.fabricmc.net'
profile_bytes=fetch(meta_host+'/v2/versions/loader/'+version+'/0.20.1/profile/json')
profile=json.loads(profile_bytes)
if profile['inheritsFrom']!=version or profile['mainClass']!='net.fabricmc.loader.impl.launch.knot.KnotClient':
    raise SystemExit('Unexpected official Fabric launch profile')
runtime.mkdir(parents=True,exist_ok=args.resume_preparation or args.interactive)
if (runtime/'official-fabric-profile.json').exists() and (runtime/'official-fabric-profile.json').read_bytes()!=profile_bytes:
    raise SystemExit('Official profile changed; preserve preparation and use a new runtime')
(runtime/'official-fabric-profile.json').write_bytes(profile_bytes)
(runtime/'mojang-version.json').write_text(json.dumps(base),encoding='utf-8')
def allowed(rules):
    if not rules:return True
    result=False
    for rule in rules:
        system=rule.get('os',{})
        if system.get('name','windows')!='windows':continue
        if system.get('arch') and not re.fullmatch(system['arch'],'amd64'):continue
        if system.get('version') and not re.search(system['version'],'10.0'):continue
        if any(rule.get('features',{}).values()):continue
        result=rule['action']=='allow'
    return result
def coordinate(name):
    parts=name.split(':');group,artifact,version=parts[:3]
    suffix='-'+parts[3] if len(parts)>3 else ''
    return '/'.join([group.replace('.','/'),artifact,version,artifact+'-'+version+suffix+'.jar'])
def artifact(library):
    item=library.get('downloads',{}).get('artifact')
    if item is None:
        path=coordinate(library['name'])
        item={'path':path,'url':library['url'].rstrip('/')+'/'+path,'sha1':library.get('sha1')}
    expected=item.get('sha1')
    allow_unchecked_legacy=False
    if not expected and item.get('url','').startswith('https://maven.fabricmc.net/'):
        # Some official profile entries omit hashes (notably intermediary).
        # Require the repository checksum rather than accepting unchecked bytes.
        expected=fetch(item['url']+'.sha1').decode('ascii').strip().split()[0]
    elif not expected and (item.get('url','').startswith('https://repo.legacyfabric.net/') or item.get('url','').startswith('https://maven.legacyfabric.net/')):
        try:
            expected=fetch(item['url']+'.sha1').decode('ascii').strip().split()[0]
        except HTTPError:
            allow_unchecked_legacy=True
    if not allow_unchecked_legacy and (not expected or not re.fullmatch('[a-fA-F0-9]{40}',expected)):raise RuntimeError('Missing official SHA1: '+library['name'])
    destination=(runtime/'libraries'/item['path']).resolve()
    if not canonical(destination).is_relative_to(canonical(runtime/'libraries')):raise RuntimeError('Unsafe library path: '+library['name']+' -> '+str(destination))
    if destination.exists():
        if not allow_unchecked_legacy and hashlib.sha1(destination.read_bytes()).hexdigest()!=expected:raise RuntimeError('Existing prepared artifact differs; preserve it')
        return destination
    parts=library['name'].split(':');directory=cache/'modules-2/files-2.1'/parts[0]/parts[1]/parts[2]
    candidates=(list((directory/expected).glob('*'))+list((directory/expected.lstrip('0')).glob('*'))) if expected else [p for p in directory.rglob('*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar'))]
    candidates.append(root/'build/forge-production-1.20.6-20261027/libraries'/item['path'])
    original=next((p for p in candidates if p.is_file() and (not expected or hashlib.sha1(p.read_bytes()).hexdigest()==expected)),None)
    body=original.read_bytes() if original else fetch(item['url'])
    if not allow_unchecked_legacy and hashlib.sha1(body).hexdigest()!=expected:raise RuntimeError('SHA1 mismatch: '+library['name'])
    destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(body)
    return destination
libraries={}
for library in base['libraries']+profile['libraries']:
    if allowed(library.get('rules')):
        parts=library['name'].split(':')
        classifier=parts[3] if len(parts)>3 else ''
        # This native fixture is Windows x64. Do not flatten ARM/x86 DLLs over
        # the x64 libraries when a Mojang manifest lists all Windows variants.
        if classifier.startswith('natives-windows-') and classifier not in ('natives-windows-x64','natives-windows-x86_64'):continue
        has_natives = 'natives' in library and isinstance(library['natives'],dict)
        if not has_natives or library.get('downloads',{}).get('artifact') is not None:
            libraries[(parts[0],parts[1],classifier)]=library
        if has_natives and 'windows' in library['natives']:
            native_key=library['natives']['windows'].replace('${arch}','64')
            native_item=library.get('downloads',{}).get('classifiers',{}).get(native_key)
            if native_item:
                libraries[(parts[0],parts[1],native_key)]={'name':library['name']+':'+native_key,'downloads':{'artifact':native_item}}
            elif library.get('url'):
                libraries[(parts[0],parts[1],native_key)]={'name':library['name']+':'+native_key,'url':library['url']}
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:classpath=list(pool.map(artifact,libraries.values()))
client=cache/'fabric-loom'/version/'minecraft-client.jar'
if hashlib.sha1(client.read_bytes()).hexdigest()!=base['downloads']['client']['sha1']:raise SystemExit('Original Mojang checksum mismatch')
shutil.copy2(client,runtime/'minecraft-client.jar');classpath.append(runtime/'minecraft-client.jar')
mods=runtime/'mods';mods.mkdir(exist_ok=args.resume_preparation or args.interactive)
if (mods/'bat-client-internal.jar').exists() and (mods/'bat-client-internal.jar').read_bytes()!=source.read_bytes():
    raise SystemExit('Prepared mod differs from requested artifact; use a new runtime')
shutil.copy2(source,mods/'bat-client-internal.jar')
if api_version is not None:
    api_candidates=list((cache/'modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api'/api_version).rglob('fabric-api-'+api_version+'.jar'))
    if len(api_candidates)!=1:raise RuntimeError('Expected exact cached Fabric API distribution')
    api=api_candidates[0]
    if hashlib.sha1(api.read_bytes()).hexdigest()!=api.parent.name.zfill(40):raise RuntimeError('Fabric API hash mismatch')
    with zipfile.ZipFile(api) as archive:
        if json.loads(archive.read('fabric.mod.json'))['version']!=api_version:raise RuntimeError('Wrong Fabric API')
    if api.stat().st_size<100_000 and api_version=='0.77.0+1.18.2':
        expected_fat='d312b2846af795ee5a99b1ec290f0e023fa48338'
        fat_dest=mods/'fabric-api.jar'
        if not fat_dest.is_file() or hashlib.sha1(fat_dest.read_bytes()).hexdigest()!=expected_fat:
            body=fetch('https://cdn.modrinth.com/data/P7dR8mSH/versions/qk28POfr/fabric-api-0.77.0%2B1.18.2.jar')
            if hashlib.sha1(body).hexdigest()!=expected_fat:raise RuntimeError('Fabric API fat jar hash mismatch')
            fat_dest.write_bytes(body)
    else:
        shutil.copy2(api,mods/'fabric-api.jar')
natives=runtime/'natives';natives.mkdir(exist_ok=args.interactive or args.resume_preparation)
for jar in classpath:
    if 'natives-windows' not in jar.name:continue
    with zipfile.ZipFile(jar) as archive:
        for entry in archive.namelist():
            if entry.lower().endswith('.dll'):(natives/Path(entry).name).write_bytes(archive.read(entry))
username='BatFabricSmoke'
offline_uuid=uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:'+username).encode()).digest(),version=3).hex
asset_index=cache/'fabric-loom/assets/indexes'/(version+'-'+base['assetIndex']['id']+'.json')
if not asset_index.is_file() or hashlib.sha1(asset_index.read_bytes()).hexdigest()!=base['assetIndex']['sha1']:
    body=fetch(base['assetIndex']['url'])
    if hashlib.sha1(body).hexdigest()!=base['assetIndex']['sha1']:raise SystemExit('Official asset index download checksum failed')
    asset_index.parent.mkdir(parents=True,exist_ok=True);asset_index.write_bytes(body)
if hashlib.sha1(asset_index.read_bytes()).hexdigest()!=base['assetIndex']['sha1']:
    raise SystemExit('Asset index does not match official Mojang metadata')
objects=json.loads(asset_index.read_text())['objects']
def ensure_asset(item):
    digest=item['hash']
    if not re.fullmatch('[0-9a-f]{40}',digest):raise RuntimeError('Unsafe asset identity')
    destination=cache/'fabric-loom/assets/objects'/digest[:2]/digest
    if destination.is_file() and destination.stat().st_size==item['size'] and hashlib.sha1(destination.read_bytes()).hexdigest()==digest:return
    body=fetch('https://resources.download.minecraft.net/'+digest[:2]+'/'+digest)
    if len(body)!=item['size'] or hashlib.sha1(body).hexdigest()!=digest:raise RuntimeError('Official asset object checksum failed')
    destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(body)
unique_objects=list({item['hash']:item for item in objects.values()}.values())
pool=concurrent.futures.ThreadPoolExecutor(max_workers=8)
try:
    futures=[pool.submit(ensure_asset,item) for item in unique_objects]
    for completed,future in enumerate(concurrent.futures.as_completed(futures),1):
        future.result()
        if completed%250==0:print('OFFICIAL_ASSET_PROGRESS:',completed,'/',len(unique_objects),flush=True)
except BaseException:
    pool.shutdown(wait=True,cancel_futures=True);raise
else:pool.shutdown()

print('OFFICIAL_ASSET_OBJECTS_VERIFIED:',len(objects),flush=True)
command=[args.java,'-Xmx2G','-Djava.library.path='+java_path(natives),'-Dbatclient.internalSmoke='+str(not args.interactive).lower(),
    '-Dbatclient.productionSmoke=true','-Dbatclient.test.noPresence=true',
    '-cp',os.pathsep.join(java_path(p) for p in classpath),profile['mainClass'],'--username',username,'--version',profile['id'],
    '--gameDir',java_path(runtime),'--assetsDir',java_path(cache/'fabric-loom/assets'),'--assetIndex',asset_index.stem,
    '--uuid',offline_uuid,'--accessToken','0','--userType','legacy','--versionType','release']
if version == '1.8':
    command += ['--userProperties', '{}']
if args.visual_parity:
    command.insert(1, '-Dbatclient.visualParity=true')
if args.agent:
    agent=Path(args.agent).resolve()
    if not agent.is_file():raise SystemExit('Actual cosmetic agent missing')
    with zipfile.ZipFile(agent) as archive:
        if b'Premain-Class: com.batclient.cosmetics.AgentMain' not in archive.read('META-INF/MANIFEST.MF'):raise SystemExit('Unexpected agent entrypoint')
    command[1:1]=['-XX:+UseSerialGC','-Dbatclient.compatAgent=true',
        '-Dfabric.debug.disableClassPathIsolation=true',
        '-javaagent:'+java_path(agent)+'='+version+',intermediary,http://127.0.0.1:9']
    (runtime/'agent-sha256.txt').write_text(hashlib.sha256(agent.read_bytes()).hexdigest(),encoding='utf-8')
environment=os.environ.copy();environment['ALSOFT_DRIVERS']='null'
log=runtime/('interactive-visual.log' if args.interactive else 'production-smoke.log')
print('FABRIC_PRODUCTION_START: SHA256='+hashlib.sha256((mods/'bat-client-internal.jar').read_bytes()).hexdigest(),flush=True)
with log.open('w',encoding='utf-8') as output:
    try:result=subprocess.run(command,cwd=runtime,env=environment,stdout=output,stderr=subprocess.STDOUT,timeout=None if args.interactive else 240)
    except subprocess.TimeoutExpired:raise SystemExit('Own isolated process exceeded240s; inspect '+str(log))
if args.interactive:
    print('VISUAL_SESSION_EXIT:',result.returncode,flush=True)
    raise SystemExit(result.returncode)
content=(runtime/'result.txt').read_text() if (runtime/'result.txt').exists() else 'NO FIXTURE RESULT'
print('Process exit:',result.returncode,'Result:',content,flush=True)
if args.visual_parity:
    raise SystemExit(0 if result.returncode == 0 and content == 'BAT_VISUAL_PARITY_CAPTURE_OK' else 1)
if result.returncode or not content.startswith('BAT_FABRIC_'+label+'_INTERNAL_WORLD_OK:'):raise SystemExit('Fabric production failed; inspect '+str(log))
if 'BAT_FABRIC_'+label+'_PRODUCTION_NAMESPACE_OK' not in log.read_text(encoding='utf-8'):raise SystemExit('Production namespace assertion missing')
print('FABRIC_PRODUCTION_SMOKE_OK: actual remapped protected JAR, native gameplay and natural clean exit')
