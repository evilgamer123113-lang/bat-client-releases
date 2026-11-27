'use strict';
// Probe MCPConfig/ForgeGradle repos for the legacy patch versions that had no
// mcpbot/zffu/mcpconfig entry, plus check fabric intermediary for 1.14-1.14.3.
const fs = require('fs');
const path = require('path');
const https = require('https');

const OUT = path.join(process.env.TEMP || '.', 'opencode', 'playerlist-data');

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
  try { const b = await get(url); if (maxBytes && b.length > maxBytes) return null; return b; }
  catch (e) { return null; }
}

async function main() {
  const missing = ['1.8.1', '1.8.2', '1.8.3', '1.8.4', '1.8.5', '1.8.6', '1.8.7', '1.9.1', '1.9.3', '1.10.1', '1.11.1', '1.12.1'];
  console.log('== MCPConfig base mappings (tsrg2) ==');
  for (const v of missing) {
    const urls = [
      'https://raw.githubusercontent.com/MinecraftForge/MCPConfig/master/versions/release/' + v + '/joined.tsrg',
      'https://raw.githubusercontent.com/MinecraftForge/MCPConfig/master/versions/release/' + v + '/joined.tsrg2',
      'https://raw.githubusercontent.com/MinecraftForge/MCPConfig/master/versions/release/' + v + '/joined.srg',
    ];
    let ok = null;
    for (const u of urls) {
      const b = await tryGet(u, 30 * 1024 * 1024);
      if (b) { ok = { u, b }; break; }
    }
    if (ok) {
      const name = 'mcpconfig-base-' + v + (ok.u.endsWith('.tsrg2') ? '.tsrg2' : ok.u.endsWith('.srg') ? '.srg' : '.tsrg');
      fs.writeFileSync(path.join(OUT, name), ok.b);
      console.log('  OK ' + v + ' <- ' + ok.u);
    } else {
      console.log('  MISS ' + v);
    }
  }

  // Try forge's mcp 1.8.1-1.8.9 configs via mcpbot alternate naming
  console.log('\n== mcpbot alt names ==');
  for (const v of ['1.8.1', '1.8.2', '1.8.3', '1.8.4', '1.8.5', '1.8.6', '1.8.7']) {
    const u = 'https://github.com/ModCoderPack/mcpbot/raw/master/mcp-' + v + '-srg.zip';
    const b = await tryGet(u, 60 * 1024 * 1024);
    if (b) { fs.writeFileSync(path.join(OUT, 'mcpbot-alt-' + v + '.zip'), b); console.log('  OK ' + v); }
    else console.log('  MISS ' + v);
  }

  // fabric intermediary for 1.14-1.14.3
  console.log('\n== fabric intermediary 1.14-1.14.3 ==');
  for (const v of ['1.14', '1.14.1', '1.14.2', '1.14.3']) {
    const u = 'https://maven.fabricmc.net/net/fabricmc/intermediary/' + v + '/intermediary-' + v + '-v2.jar';
    const b = await tryGet(u, 30 * 1024 * 1024);
    if (b) {
      fs.writeFileSync(path.join(OUT, 'intermediary-' + v + '-v2.jar'), b);
      const { execSync } = require('child_process');
      const ex = path.join(OUT, 'intermediary-ext', v);
      fs.mkdirSync(ex, { recursive: true });
      try { execSync('tar -xf "' + path.join(OUT, 'intermediary-' + v + '-v2.jar') + '" -C "' + ex + '" mappings/mappings.tiny', { stdio: 'ignore', shell: true }); } catch (e) {}
      const tiny = fs.readFileSync(path.join(ex, 'mappings', 'mappings.tiny'), 'utf8');
      let class640 = null, class640alt = null, mcpName = null;
      for (const line of tiny.split(/\r?\n/)) {
        if (line.startsWith('c\t')) {
          const p = line.split('\t');
          if (p[1] === 'dhm') class640 = p[2];
          if (/NetworkPlayerInfo/.test(p[1])) class640alt = p[1] + '->' + p[2];
        }
      }
      console.log('  ' + v + ' dhm->' + (class640 || '-') + ' netplayerinfo: ' + (class640alt || '-'));
    }
  }
}
main().catch(e => { console.error(e); process.exit(1); });