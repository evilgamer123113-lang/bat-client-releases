"""Check explicit constructor Inject callback descriptors in actual compiled port classes.

Narrow preflight, not a substitute for native Mixin application. Local-capture
constructors are intentionally excluded because their contracts differ.
"""
import argparse,re,subprocess
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument('--project',required=True)
parser.add_argument('--javap',default='javap')
args=parser.parse_args()
project=Path(args.project).resolve()
ci='Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;'
issues=[];checked=0
for source in (project/'build/version-sources').rglob('*.java'):
    value=source.read_text(encoding='utf-8')
    matches=list(re.finditer(r'@Inject\(method="<init>(\([^"]+)"([^;]*?)\)\s*(?:private|protected|public)\s+void\s+([\w$]+)\(',value,re.S))
    if not matches:continue
    package=re.search(r'package\s+([\w.]+)\s*;',value)[1]
    result=subprocess.run([args.javap,'-p','-s','-classpath',str(project/'build/classes/java/main'),package+'.'+source.stem],text=True,capture_output=True,check=True)
    for match in matches:
        if 'locals=' in match[2] or 'locals =' in match[2]:continue
        name=match[3]
        descriptor=re.search(r'\b'+re.escape(name)+r'\([^;]*;\s*descriptor:\s*(\S+)',result.stdout)
        checked+=1
        expected=match[1].split(')')[0]+ci+')V'
        actual=descriptor[1] if descriptor else 'MISSING_COMPILED_CALLBACK'
        if actual not in (expected,'('+ci+')V'):issues.append((source.stem,name,expected,actual))
print('COMPILED_CONSTRUCTOR_CALLBACK_AUDIT:',checked,'callbacks;',len(issues),'issues')
for issue in issues:print(' | '.join(issue))
raise SystemExit(bool(issues))
