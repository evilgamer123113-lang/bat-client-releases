"""Prepare a disabled Rust channel in shared update.json; never deploys or publishes."""
import argparse
import hashlib
import json
import re
from pathlib import Path
from urllib.parse import quote

TARGETS = {'windows-x86_64', 'windows-aarch64', 'darwin-x86_64', 'darwin-aarch64', 'linux-x86_64', 'linux-aarch64'}

def prepare(original, version, artifacts):
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?", version):
        raise ValueError("Use a semantic Rust release version")
    result = json.loads(json.dumps(original))
    platforms = {}
    for target, filename in artifacts:
        if target not in TARGETS or target in platforms:
            raise ValueError('Unsupported or duplicate Rust target')
        path = Path(filename)
        if not path.is_file() or not 0 < path.stat().st_size <= 512 * 1024 * 1024:
            raise ValueError('Missing or oversized update package')
        signature = Path(str(path) + '.sig').read_text(encoding='utf-8').strip()
        if not signature or len(signature) >= 16384:
            raise ValueError('Missing package signature')
        platforms[target] = {
            'url': f'https://github.com/evilgamer123113-lang/bat-client-releases/releases/download/rust-v{quote(version, safe="")}/{quote(path.name, safe="")}',
            'signature': signature,
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
            'size': path.stat().st_size,
        }
    result['rust'] = {'schema': 1, 'product': 'bat-client-rust', 'enabled': False, 'version': version,
                      'notes': 'Rust launcher update. Activate only after signed assets are published and verified.', 'platforms': platforms}
    assert {k: v for k, v in result.items() if k != 'rust'} == {k: v for k, v in original.items() if k != 'rust'}
    return result

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--artifact', action='append', default=[], help='target=local-package-path (requires adjacent .sig)')
    args = parser.parse_args()
    if args.source.resolve() == args.output.resolve():
        parser.error('Use a separate reviewable output file; source manifest is preserved')
    result = prepare(json.loads(args.source.read_text(encoding='utf-8-sig')), args.version,
                     [value.split('=', 1) for value in args.artifact])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
