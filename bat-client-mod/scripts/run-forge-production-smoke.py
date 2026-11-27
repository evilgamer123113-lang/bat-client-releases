"""Run the real remapped internal mod in an isolated official Forge installation.

No user account, real saves, launcher settings or host installation are used.
The installer must have completed first. Downloads use version-JSON URLs/SHA-1s.
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import urllib.request
import uuid
import zipfile
import tomllib

parser=argparse.ArgumentParser()
parser.add_argument('--runtime',required=True)
parser.add_argument('--version',required=True)
parser.add_argument('--mod',required=True)
parser.add_argument('--cache',default=str(Path.home()/'.gradle/caches'))
parser.add_argument('--agent')
parser.add_argument('--java',default='java')
parser.add_argument('--visual-parity',action='store_true')
args=parser.parse_args()
workspace=Path(__file__).resolve().parent.parent
runtime=Path(args.runtime).resolve()
private_root=Path('D:/BatClientBuildCache/mod-native')
if not (runtime.is_relative_to(workspace/'build') or runtime.is_relative_to(private_root)) or runtime in (workspace/'build',private_root):
    raise SystemExit('Production smoke must stay inside this mod build directory')
running=subprocess.check_output(['powershell.exe','-NoProfile','-Command',"@(Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | Where-Object {$_.CommandLine -match 'KnotClient|BootstrapLauncher|net.minecraft.client.main.Main|devlaunchinjector'}).Count"],text=True).strip()
if running!='0':raise SystemExit('Minecraft already running; no second game started')
if (runtime/'production-smoke.log').exists() or (runtime/'saves').exists():raise SystemExit('Use a fresh prepared private runtime, not an earlier game')
cache=Path(args.cache).resolve()
metadata=json.loads((runtime/'versions'/args.version/(args.version+'.json')).read_text())
parent=metadata['inheritsFrom']
base=json.loads((runtime/'versions'/parent/(parent+'.json')).read_text())

def allowed(rules):
    if not rules:
        return True
    result=False
    for rule in rules:
        platform=rule.get('os',{})
        if platform.get('name','windows')!='windows':
            continue
        if platform.get('arch') and not re.fullmatch(platform['arch'],'amd64'):
            continue
        if platform.get('version') and not re.search(platform['version'],'10.0'):
            continue
        if any(expected for expected in rule.get('features',{}).values()):
            continue
        result=rule['action']=='allow'
    return result

libraries={}
for library in base['libraries']+metadata['libraries']:
    if library.get('clientreq') is False or not allowed(library.get('rules')):
        continue
    name=library['name'].split(':')
    if library.get('downloads',{}).get('artifact',{}).get('path'):
        libraries[(name[0],name[1],name[3] if len(name)>3 else '')]=library
    elif 'downloads' not in library and len(name)>=3:
        cls=name[3] if len(name)>3 else ''
        rel=f"{name[0].replace('.','/')}/{name[1]}/{name[2]}/{name[1]}-{name[2]}{'-'+cls if cls else ''}.jar"
        base_url=library.get('url') or 'https://libraries.minecraft.net/'
        if not base_url.endswith('/'):
            base_url+='/'
        checksums=library.get('checksums') or []
        libraries[(name[0],name[1],cls)]={'name':library['name'],'downloads':{'artifact':{'path':rel,'url':base_url+rel,'sha1':checksums[0] if len(checksums)==1 else None,'legacy':True}}}
    if 'natives' in library and isinstance(library['natives'],dict) and 'windows' in library['natives']:
        native_key=library['natives']['windows'].replace('${arch}','64')
        native_item=library.get('downloads',{}).get('classifiers',{}).get(native_key)
        if native_item and native_item.get('path'):
            libraries[(name[0],name[1],native_key)]={'name':library['name']+':'+native_key,'downloads':{'artifact':native_item}}

def artifact(library):
    item=library.get('downloads',{}).get('artifact',{})
    if not item.get('path'):
        raise RuntimeError('Version metadata lacks artifact path: '+library['name'])
    destination=runtime/'libraries'/item['path']
    expected=item.get('sha1')
    def valid():
        return destination.is_file() and (not expected or hashlib.sha1(destination.read_bytes()).hexdigest()==expected)
    if not valid():
        if not item.get('url') or (not expected and not item.get('legacy')):
            raise RuntimeError('Installer output missing and cannot be fetched safely: '+str(destination))
        urls=[item['url']]
        if item.get('legacy'):
            urls+=['https://repo1.maven.org/maven2/'+item['path'],'https://libraries.minecraft.net/'+item['path']]
        body=None
        last_err=None
        for url in urls:
            try:
                request=urllib.request.Request(url,headers={'User-Agent':'BatClient-internal-compatibility-test'})
                body=urllib.request.urlopen(request,timeout=45).read()
                break
            except Exception as exc:
                last_err=exc
        if body is None:
            raise RuntimeError('Failed to download '+item['path']+': '+str(last_err))
        if expected and hashlib.sha1(body).hexdigest()!=expected:
            raise RuntimeError('Artifact checksum failed: '+item['path'])
        destination.parent.mkdir(parents=True,exist_ok=True)
        destination.write_bytes(body)
    return destination

with concurrent.futures.ThreadPoolExecutor(max_workers=4) as workers:
    classpath=list(workers.map(artifact,libraries.values()))

natives=runtime/'natives'
natives.mkdir(exist_ok=True)
for library in classpath:
    if 'natives-windows' not in library.name or 'arm64' in library.name or 'x86' in library.name:
        continue
    with zipfile.ZipFile(library) as archive:
        for entry in archive.namelist():
            if entry.lower().endswith('.dll'):
                (natives/Path(entry).name).write_bytes(archive.read(entry))
classpath.append(runtime/'versions'/parent/(parent+'.jar'))

mods=runtime/'mods'
mods.mkdir(exist_ok=True)
source=Path(args.mod).resolve()
if not source.is_relative_to(workspace/'ports') or not source.is_file():
    raise SystemExit('Expected an internal port artifact inside this project')
with zipfile.ZipFile(source) as mod:
    names=set(mod.namelist())
    if 'META-INF/mods.toml' in names:
        declaration=tomllib.loads(mod.read('META-INF/mods.toml').decode())
        declared=next(d for d in declaration['dependencies']['batclient'] if d['modId']=='minecraft')
        if declared['versionRange']!='['+parent+']':raise SystemExit('Protected Forge mod Minecraft version mismatch')
        if not mod.read('assets/batclient/web/app.js.bin'):raise SystemExit('Encrypted UI missing')
        if not mod.getinfo('META-INF/jarjar/metadata.json').file_size:raise SystemExit('Nested Forge runtime absent')
    elif 'mcmod.info' in names:
        mcmod=json.loads(mod.read('mcmod.info').decode())
        if mcmod[0]['mcversion']!=parent:raise SystemExit('Protected Forge mod Minecraft version mismatch')
        if not mod.read('assets/batclient/web/app.js.bin'):raise SystemExit('Encrypted UI missing')
    else:
        raise SystemExit('Unrecognized Forge mod metadata')
# Loom may prefix the official asset index filename with the game version.
index=base['assetIndex'];asset_indexes=cache/'fabric-loom/assets/indexes'
index_path=next((p for p in (asset_indexes/(index['id']+'.json'),asset_indexes/(parent+'-'+index['id']+'.json')) if p.is_file() and hashlib.sha1(p.read_bytes()).hexdigest()==index['sha1']),None)
if not index_path:
    request=urllib.request.Request(index['url'],headers={'User-Agent':'BatClient-internal-compatibility-test'})
    body=urllib.request.urlopen(request,timeout=45).read()
    if hashlib.sha1(body).hexdigest()!=index['sha1']:raise SystemExit('Official asset index download checksum failed')
    index_path=asset_indexes/(parent+'-'+index['id']+'.json')
    index_path.parent.mkdir(parents=True,exist_ok=True);index_path.write_bytes(body)
values_index=index_path.stem
destination=mods/'bat-client-internal-smoke.jar'
shutil.copyfile(source,destination)
username='BatForgeSmoke'
offline_uuid=uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:'+username).encode()).digest(),version=3).hex
values={
    'natives_directory':str(natives),'launcher_name':'Bat-internal-test','launcher_version':'1',
    'classpath':os.pathsep.join(map(str,classpath)),'classpath_separator':os.pathsep,
    'library_directory':str(runtime/'libraries'),'auth_player_name':username,
    'version_name':args.version,'game_directory':str(runtime),
    'assets_root':str(cache/'fabric-loom/assets'),
    'assets_index_name':values_index,'auth_uuid':offline_uuid,
    'auth_access_token':'0','clientid':'','auth_xuid':'','user_type':'legacy','version_type':'release',
    'user_properties':'{}',
}

# Legacy Forge's generated ignoreList uses ${version_name}.jar to exclude the
# inherited vanilla client from module discovery. Match its real file basename;
# Forge's own fml.mcVersion/forgeVersion arguments still come from its profile.
if metadata['mainClass']=='cpw.mods.bootstraplauncher.BootstrapLauncher':
    values['version_name']=parent

def expand(items):
    result=[]
    for entry in items:
        if isinstance(entry,dict):
            if not allowed(entry.get('rules')):
                continue
            entry=entry['value']
        for item in entry if isinstance(entry,list) else [entry]:
            text=re.sub(r'\$\{([^}]+)\}',lambda match:values[match.group(1)],item)
            result.append(text)
    return result

if 'arguments' in base and 'arguments' in metadata:
    jvm=expand(base.get('arguments',{}).get('jvm',[]))+expand(metadata.get('arguments',{}).get('jvm',[]))
    game=expand(base.get('arguments',{}).get('game',[]))+expand(metadata.get('arguments',{}).get('game',[]))
else:
    jvm=['-Djava.library.path='+values['natives_directory'],'-cp',values['classpath']]
    game=expand((metadata.get('minecraftArguments') or base.get('minecraftArguments') or '').split())
extra=['-Dbatclient.productionSmoke=true']
if args.visual_parity:
    extra.append('-Dbatclient.visualParity=true')
if args.agent:
    agent=Path(args.agent).resolve()
    with zipfile.ZipFile(agent) as z:
        if 'Premain-Class: com.batclient.cosmetics.AgentMain' not in z.read('META-INF/MANIFEST.MF').decode():raise SystemExit('Expected actual Bat cosmetics premain')
    extra+=['-XX:+UseSerialGC','-Dbatclient.compatAgent=true','-javaagent:'+str(agent)+'='+parent+',official,http://127.0.0.1:9']
    print('Actual agent SHA256='+hashlib.sha256(agent.read_bytes()).hexdigest(),flush=True)
command=[args.java,'-Xmx2G','-Dbatclient.internalSmoke=true','-Dbatclient.test.noPresence=true']+extra+jvm+[metadata['mainClass']]+game
result_file=runtime/'result.txt'
result_file.unlink(missing_ok=True)
log=runtime/'production-smoke.log'
environment=os.environ.copy()
environment['ALSOFT_DRIVERS']='null'
print('Starting actual Forge production namespace; artifact SHA256='+hashlib.sha256(destination.read_bytes()).hexdigest(),flush=True)
with log.open('w',encoding='utf-8') as output:
    try:
        result=subprocess.run(command,cwd=runtime,env=environment,stdout=output,stderr=subprocess.STDOUT,timeout=240)
    except subprocess.TimeoutExpired:
        raise SystemExit('Production test exceeded240s; own isolated process terminated; inspect '+str(log))
content=result_file.read_text() if result_file.exists() else 'NO FIXTURE RESULT'
print('Process exit:',result.returncode,'Result:',content,flush=True)
if args.visual_parity:
    raise SystemExit(0 if result.returncode == 0 and content == 'BAT_VISUAL_PARITY_CAPTURE_OK' else 1)
if result.returncode or not content.startswith('BAT_FORGE_FULL_INTERNAL_WORLD_OK:'):
    raise SystemExit('Production Forge smoke failed; inspect '+str(log))
print('FORGE_PRODUCTION_SMOKE_OK: actual remapped protected JAR, nested libraries, menu/world/HUD/waypoints and natural clean exit')
