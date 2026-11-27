'use strict';
// Probe + gather PlayerListEntry class names for every release version 1.8 -> 1.21.11.
// Sources:
//  - mcpbot joined.srg (legacy 1.8-1.11)
//  - Zffu joined.tsrg / .srg (legacy 1.8.8-1.14.3)
//  - MCPConfig joined.tsrg (1.13.1-1.14.3)
//  - fabric intermediary v2 jar + yarn v2 jar (1.14.4+)
// Writes probes to %TEMP%\opencode\playerlist-data for inspection.

const fs = require('fs');
const path = require('path');
const https = require('https');
const { execSync } = require('child_process');

const OUT = path.join(process.env.TEMP || '.', 'opencode', 'playerlist-data');
fs.mkdirSync(OUT, { recursive: true });

function get(url) {
  return new Promise((resolve, reject) => {
    https.get(url, { headers: { 'User-Agent': 'bat-client' } }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        res.resume();
        get(res.headers.location).then(resolve, reject);
        return;
      }
      if (res.statusCode !== 200) { res.resume(); reject(new Error(url + ' -> ' + res.statusCode)); return; }
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolve(Buffer.concat(chunks)));
    }).on('error', reject);
  });
}

async function tryGet(url, maxBytes) {
  try {
    const buf = await get(url);
    if (maxBytes && buf.length > maxBytes) return null;
    return buf;
  } catch (e) { return null; }
}

async function main() {
  const manifestUrl = 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json';
  const manifest = JSON.parse((await tryGet(manifestUrl, 5 * 1024 * 1024)).toString('utf8'));

  const VERSIONS = [];
  for (const v of manifest.versions) {
    if (v.type === 'release' && !v.id.includes('-')) {
      const m = v.id.match(/^1\.(\d+)(?:\.(\d+))?$/);
      if (!m) continue;
      const mi = parseInt(m[1], 10);
      const pa = m[2] ? parseInt(m[2], 10) : 0;
      const maj = mi;
      if (maj < 8 || maj > 21) continue;
      if (maj === 21 && pa > 11) continue;
      VERSIONS.push(v.id);
    }
  }
  VERSIONS.sort((a, b) => {
    const A = a.split('.').map(n => parseInt(n, 10) || 0);
    const B = b.split('.').map(n => parseInt(n, 10) || 0);
    for (let i = 0; i < Math.max(A.length, B.length); i++) {
      const x = A[i] || 0, y = B[i] || 0;
      if (x !== y) return x - y;
    }
    return 0;
  });
  console.log('release versions:', VERSIONS.length);
  console.log(VERSIONS.join(', '));

  fs.writeFileSync(path.join(OUT, 'versions.json'), JSON.stringify(VERSIONS, null, 2));

  const probes = [];
  const src = {};

  const mcpbotVersions = ['1.8', '1.8.1', '1.8.2', '1.8.3', '1.8.4', '1.8.5', '1.8.6', '1.8.7', '1.8.8', '1.8.9', '1.9', '1.9.1', '1.9.2', '1.9.3', '1.9.4', '1.10', '1.10.1', '1.10.2', '1.11', '1.11.1', '1.11.2', '1.12', '1.12.1', '1.12.2', '1.13'];
  for (const v of mcpbotVersions) {
    const url = 'https://github.com/ModCoderPack/mcpbot/raw/master/mcp-' + v + '-srg.zip';
    const ok = await tryGet(url, 50 * 1024 * 1024);
    if (ok) {
      try {
        execSync('tar -xf - -C "' + OUT + '" joined.srg', { input: ok, stdio: 'ignore', shell: true });
        fs.renameSync(path.join(OUT, 'joined.srg'), path.join(OUT, 'mcpbot-' + v + '.srg'));
        src['mcpbot-' + v] = 'mcpbot/' + v + '.srg';
      } catch (e) {
        probes.push(['mcpbot', v, 'zip-ok-but-extract-fail']);
      }
    } else {
      probes.push(['mcpbot', v, '404']);
    }
  }

  const zffuVersions = ['1.8', '1.8.1', '1.8.2', '1.8.3', '1.8.4', '1.8.5', '1.8.6', '1.8.7', '1.8.8', '1.8.9', '1.9', '1.9.1', '1.9.2', '1.9.3', '1.9.4', '1.10', '1.10.1', '1.10.2', '1.11', '1.11.1', '1.11.2', '1.12', '1.12.1', '1.12.2', '1.13', '1.13.1', '1.13.2', '1.14', '1.14.1', '1.14.2', '1.14.3'];
  for (const v of zffuVersions) {
    const url = 'https://raw.githubusercontent.com/Zffu/mappings/main/versions/' + v + '/joined.tsrg';
    const buf = await tryGet(url, 20 * 1024 * 1024);
    if (buf) {
      fs.writeFileSync(path.join(OUT, 'zffu-' + v + '.tsrg'), buf);
      src['zffu-' + v] = 'zffu/' + v + '.tsrg';
    } else {
      const url2 = 'https://raw.githubusercontent.com/Zffu/mappings/main/versions/' + v + '/joined.srg';
      const buf2 = await tryGet(url2, 20 * 1024 * 1024);
      if (buf2) {
        fs.writeFileSync(path.join(OUT, 'zffu-' + v + '.srg'), buf2);
        src['zffu-' + v] = 'zffu/' + v + '.srg';
      } else {
        probes.push(['zffu', v, '404']);
      }
    }
  }

  const mcpVersions = ['1.13.1', '1.13.2', '1.14', '1.14.1', '1.14.2', '1.14.3'];
  for (const v of mcpVersions) {
    const url = 'https://raw.githubusercontent.com/MinecraftForge/MCPConfig/master/versions/release/' + v + '/joined.tsrg';
    const buf = await tryGet(url, 20 * 1024 * 1024);
    if (buf) {
      fs.writeFileSync(path.join(OUT, 'mcpconfig-' + v + '.tsrg'), buf);
      src['mcpconfig-' + v] = 'mcpconfig/' + v + '.tsrg';
    } else {
      probes.push(['mcpconfig', v, '404']);
    }
  }

  fs.writeFileSync(path.join(OUT, 'probes.json'), JSON.stringify({ probes, src }, null, 2));
  console.log('\n=== probe results ===');
  for (const [s, v, r] of probes) console.log(s + '\t' + v + '\t' + r);
  console.log('sources available:', Object.keys(src).length);
}

main().catch(e => { console.error(e); process.exit(1); });