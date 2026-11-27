"""Seed an isolated official Forge installer using hash-verified public cache files.

Never edits the user's Minecraft installation or uses launcher accounts.
The official installer still performs and verifies its own processors afterwards.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import zipfile
import urllib.request
import re

parser=argparse.ArgumentParser()
parser.add_argument('--minecraft',choices=['1.8','1.8.9','1.16.5','1.17.1','1.18.2','1.19.2','1.19.4','1.20.1','1.20.4','1.20.6','1.21.1','1.21.4','1.21.5','1.21.8','1.21.11'],required=True)
parser.add_argument('--forge',required=True)
parser.add_argument('--runtime',required=True)
parser.add_argument('--cache',default=str(Path.home()/'.gradle/caches'))
args=parser.parse_args()
root=Path(__file__).resolve().parent.parent
runtime=Path(args.runtime).resolve()
private_root=Path('D:/BatClientBuildCache/mod-native')
if not ((runtime.is_relative_to(root/'build') and runtime!=root/'build') or (runtime.is_relative_to(private_root) and runtime!=private_root)):
    raise SystemExit('Only a dedicated project build or D: private native-test directory is allowed')
if runtime.exists():
    raise SystemExit('Refusing to overwrite an existing runtime')
cache=Path(args.cache).resolve()
coordinate=args.minecraft+'-'+args.forge
installers=list((cache/'modules-2/files-2.1/net.minecraftforge/forge'/coordinate).rglob('*-installer.jar'))
if len(installers)!=1:
    raise SystemExit('Expected one already cached official Forge installer')
installer=installers[0]
if hashlib.sha1(installer.read_bytes()).hexdigest()!=installer.parent.name.zfill(40):
    raise SystemExit('Installer differs from content-addressed Gradle cache')
official_url='https://maven.minecraftforge.net/net/minecraftforge/forge/'+coordinate+'/forge-'+coordinate+'-installer.jar.sha1'
request=urllib.request.Request(official_url,headers={'User-Agent':'BatClient-internal-compatibility-test'})
official_sha=urllib.request.urlopen(request,timeout=45).read().decode('ascii').strip().split()[0].lower()
if not re.fullmatch('[0-9a-f]{40}',official_sha) or hashlib.sha1(installer.read_bytes()).hexdigest()!=official_sha:
    raise SystemExit('Official Forge installer checksum mismatch')
print('OFFICIAL_FORGE_INSTALLER_SHA1_OK:',official_sha)
with zipfile.ZipFile(installer) as archive:
    profile=json.loads(archive.read('install_profile.json'))
    legacy='version.json' not in archive.namelist()
    forge=profile['versionInfo'] if legacy else json.loads(archive.read('version.json'))
    universal_name=profile.get('install',{}).get('filePath') if legacy else None
    universal_bytes=archive.read(universal_name) if legacy and universal_name else None
if forge['inheritsFrom']!=args.minecraft or (not legacy and forge['id']!=args.minecraft+'-forge-'+args.forge) or (legacy and args.forge not in forge['id']):
    raise SystemExit('Installer metadata does not match requested target')
base=json.loads((cache/'fabric-loom'/args.minecraft/'mojang_minecraft_info.json').read_text())
client=cache/'fabric-loom'/args.minecraft/'minecraft-client.jar'
if hashlib.sha1(client.read_bytes()).hexdigest()!=base['downloads']['client']['sha1']:
    raise SystemExit('Original Mojang client hash mismatch')
runtime.mkdir(parents=True)
version=runtime/'versions'/args.minecraft
version.mkdir(parents=True)
(version/(args.minecraft+'.json')).write_text(json.dumps(base),encoding='utf-8')
shutil.copy2(client,version/(args.minecraft+'.jar'))
if legacy:
    fver=runtime/'versions'/forge['id']
    fver.mkdir(parents=True,exist_ok=True)
    (fver/(forge['id']+'.json')).write_text(json.dumps(forge),encoding='utf-8')
    if universal_bytes is not None:
        parts=profile['install']['path'].split(':')
        u_rel=Path(parts[0].replace('.','/'))/parts[1]/parts[2]/(parts[1]+'-'+parts[2]+'.jar')
        u_dst=runtime/'libraries'/u_rel
        u_dst.parent.mkdir(parents=True,exist_ok=True)
        u_dst.write_bytes(universal_bytes)
(runtime/'launcher_profiles.json').write_text('{"profiles":{}}',encoding='utf-8')
(runtime/'installer-path.txt').write_text(str(installer),encoding='utf-8')
seeded=0
for library in base['libraries']+profile.get('libraries',[])+forge['libraries']:
    downloads=library.get('downloads',{})
    items=([downloads['artifact']] if 'artifact' in downloads else [])+list(downloads.get('classifiers',{}).values())
    for item in items:
        expected=item.get('sha1')
        path=item.get('path')
        if not expected or not path:
            continue
        destination=(runtime/'libraries'/path).resolve()
        if not destination.is_relative_to(runtime/'libraries'):
            raise RuntimeError('Unsafe public library path')
        if destination.exists():
            continue
        name=library['name'].split(':')
        version_cache=cache/'modules-2/files-2.1'/name[0]/name[1]/name[2]
        folder=version_cache/expected
        if not folder.is_dir():
            folder=version_cache/expected.lstrip('0')
        candidates=list(folder.glob('*')) if folder.is_dir() else []
        candidate=next((p for p in candidates if p.is_file() and hashlib.sha1(p.read_bytes()).hexdigest()==expected),None)
        if candidate:
            destination.parent.mkdir(parents=True,exist_ok=True)
            shutil.copy2(candidate,destination)
            seeded+=1
print('FORGE_INSTALL_INPUTS_VERIFIED:',coordinate,'Mojang SHA1',base['downloads']['client']['sha1'],'cached libraries',seeded)
print('INSTALLER:',installer)
