"""Read actual mapped game members before native port tests; never substitutes for Mixin runtime."""
import argparse,json,re,struct,zipfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--project',required=True);p.add_argument('--game-jar',required=True);a=p.parse_args()
project=Path(a.project);z=zipfile.ZipFile(a.game_jar);cache={}
def members(name):
    if name in cache:return cache[name]
    try:data=z.read(name+'.class')
    except KeyError:return None
    offset=8
    def u2():
        nonlocal offset
        v=struct.unpack_from('>H',data,offset)[0];offset+=2;return v
    size=u2();pool=[None]*size;i=1
    while i<size:
        tag=data[offset];offset+=1
        if tag==1:
            length=u2();pool[i]=data[offset:offset+length].decode('utf-8',errors='replace');offset+=length
        elif tag in (7,8,16,19,20):pool[i]=u2()
        elif tag in (3,4,9,10,11,12,17,18):offset+=4
        elif tag in (5,6):offset+=8;i+=1
        elif tag==15:offset+=3
        else:raise RuntimeError('Unexpected constant pool tag '+str(tag))
        i+=1
    u2();u2();super_index=u2();parent=pool[pool[super_index]] if super_index else None
    for _ in range(u2()):u2()
    fields=set();methods=set()
    for collection in (fields,methods):
        for _ in range(u2()):
            u2();n=pool[u2()];d=pool[u2()];collection.add((n,d))
            for _ in range(u2()):
                u2();length=struct.unpack_from('>I',data,offset)[0];offset+=4+length
    cache[name]=(parent,fields,methods);return cache[name]
def has(name,method):
    seen=set()
    while name and name not in seen:
        seen.add(name);m=members(name)
        if m is None:return False
        if any(n+d==method if '(' in method else n==method for n,d in m[2]):return True
        name=m[0]
    return False
issues=[];checked=0
config=json.loads((project/'build/resources/main/batclient.mixins.json').read_text())
for mixin in config.get('mixins',[])+config.get('client',[]):
    src_dir='build/mcp-sources' if (project/'build/mcp-sources').is_dir() else 'build/version-sources'
    source=project/src_dir/Path(config['package'].replace('.','/'))/(mixin+'.java')
    value=source.read_text(encoding='utf-8');target=re.search(r'@Mixin\(\s*(?:value\s*=\s*)?([\w.$]+)\.class',value)
    if not target:continue
    name=target[1];imports=dict((x.rsplit('.',1)[1],x) for x in re.findall(r'import ([\w.]+);',value))
    parts=name.split('.')
    candidate=imports.get(parts[0],parts[0])+''.join('$'+part for part in parts[1:]) if parts[0] in imports else name
    options=[candidate]+[pkg+'.'+name for pkg in re.findall(r'import ([\w.]+)\.\*;',value)]
    resolved=None
    for option in options:
        pieces=option.split('.')
        for split in range(len(pieces),0,-1):
            entry='/'.join(pieces[:split])+''.join('$'+part for part in pieces[split:])
            if entry+'.class' in z.namelist():resolved=entry;break
        if resolved:break
    name=resolved or candidate.replace('.','/');checked+=1
    if members(name) is None:issues.append((mixin,'MISSING_TARGET',name));continue
    for field in re.findall(r'@Shadow(?:\s+@\w+)*\s+(?:private|public|protected)\s+(?:final\s+)?[^;\n{}()]+?\s+(\w+)\s*;',value):
        target_name=name;found=False;seen=set()
        while target_name and target_name not in seen:
            seen.add(target_name);m=members(target_name)
            if m is None:break
            if any(n==field for n,d in m[1]):found=True;break
            target_name=m[0]
        if not found:issues.append((mixin,'MISSING_SHADOW_FIELD',field))
    for method in set(re.findall(r'\bmethod\s*=\s*"([^"]+)"',value)):
        if not has(name,method):issues.append((mixin,'MISSING_METHOD',method))
print('ACTUAL_GAME_MIXIN_MEMBER_AUDIT:',checked,'targets;',len(issues),'issues')
for row in issues:print(' | '.join(row))
raise SystemExit(1 if issues else 0)
