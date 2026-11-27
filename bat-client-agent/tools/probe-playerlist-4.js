'use strict';
// For modern versions (1.14.4 - 1.21.11), download per-version:
//   a) Mojang client_mappings.txt (official deobf <-> obfuscated)
//   b) fabric intermediary-<v>-v2.jar (obfuscated <-> intermediary)
// and resolve the PlayerListEntry/PlayerInfo class: official obf name + intermediary.
// Named = yarn 'net/minecraft/client/network/PlayerListEntry'.

const fs = require('fs');
const path = require('path');
const https = require('https');
const http = require('http');
const { execSync } = require('child_process');

const OUT = path.join(process.env.TEMP || '.', 'opencode', 'playerlist-data');
fs.mkdirSync(OUT, { recursive: true });

function getRaw(url) {
  const lib = url.startsWith('https') ? https : http;
  return new Promise((resolve, reject) => {
    lib.get(url, { headers: { 'User-Agent': 'bat-client' } }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        res.resume();
        getRaw(res.headers.location).then(resolve, reject);
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
    const buf = await getRaw(url);
    if (maxBytes && buf.length > maxBytes) return null;
    return buf;
  } catch (e) { return null; }
}

function parseClientMappings(text) {
  // Two formats:
  //  - old ProGuard style:  net.minecraft.client.Minecraft -> bfr:   (with trailing ':')
  //  - new style:           net.minecraft.client.Minecraft -> bfr     (no trailing ':')
  // Left = deobfuscated readable name, right = runtime obfuscated class name (OFFICIAL column).
  const obfOfNamed = new Map();
  for (const line of text.split(/\r?\n/)) {
    let m = line.match(/^([A-Za-z_$][A-Za-z0-9_$\/.]*) -> ([A-Za-z_$][A-Za-z0-9_$]*):\s*$/);
    if (!m) m = line.match(/^([A-Za-z_$][A-Za-z0-9_$\/.]*) -> ([A-Za-z_$][A-Za-z0-9_$]*)$/);
    if (m) obfOfNamed.set(m[1], m[2]);
  }
  return obfOfNamed;
}

async function main() {
  const versions = JSON.parse(fs.readFileSync(path.join(OUT, 'versions.json'), 'utf8'));
  const modern = versions.filter(v => {
    const p = v.split('.').map(n => parseInt(n, 10) || 0);
    return p[0] === 1 && p[1] >= 14 && (p[1] > 14 || p[2] >= 4);
  });
  console.log('modern versions:', modern.length);

  const manifest = JSON.parse((await tryGet('https://launchermeta.mojang.com/mc/game/version_manifest_v2.json', 5 * 1024 * 1024)).toString('utf8'));
  const meta = new Map(manifest.versions.map(v => [v.id, v]));
  const metaDir = path.join(OUT, 'version-jsons');
  fs.mkdirSync(metaDir, { recursive: true });

  const outData = {};

  for (const v of modern) {
    const vm = meta.get(v);
    if (!vm) { console.log('  WARN no manifest for ' + v); continue; }
    let vj = null;
    const pj = path.join(metaDir, v + '.json');
    try {
      vj = fs.existsSync(pj) ? JSON.parse(fs.readFileSync(pj, 'utf8')) : JSON.parse((await tryGet(vm.url, 2 * 1024 * 1024)).toString('utf8'));
      if (!fs.existsSync(pj)) fs.writeFileSync(pj, JSON.stringify(vj));
    } catch (e) { console.log('  WARN version json ' + v + ': ' + e.message); continue; }

    const cm = vj.downloads && vj.downloads.client_mappings;
    const cmPath = path.join(OUT, 'client-mappings-' + v + '.txt');
    if (cm) {
      if (!fs.existsSync(cmPath)) {
        const buf = await tryGet(cm.url, 60 * 1024 * 1024);
        if (buf) fs.writeFileSync(cmPath, buf);
      }
    }

    const interJar = path.join(OUT, 'intermediary-' + v + '-v2.jar');
    if (!fs.existsSync(interJar)) {
      const buf = await tryGet('https://maven.fabricmc.net/net/fabricmc/intermediary/' + v + '/intermediary-' + v + '-v2.jar', 30 * 1024 * 1024);
      if (buf) fs.writeFileSync(interJar, buf);
    }

    // parse client mappings -> official obf name for PlayerListEntry / PlayerInfo
    let official = null;
    if (fs.existsSync(cmPath)) {
      const map = parseClientMappings(fs.readFileSync(cmPath, 'utf8'));
      for (const name of [
        'net.minecraft.client.multiplayer.PlayerInfo',
        'net.minecraft.client.network.PlayerListEntry',
        'net.minecraft.client.network.play.NetworkPlayerInfo',
      ]) {
        if (map.has(name)) { official = map.get(name); break; }
      }
    }
    if (!official) {
      console.log('  [MISS-OFFICIAL] ' + v);
    }

    // parse intermediary jar -> find class with official===official, get intermediary
    let intermediary = null;
    if (fs.existsSync(interJar)) {
      const ex = path.join(OUT, 'intermediary-ext', v);
      fs.mkdirSync(ex, { recursive: true });
      try {
        execSync('tar -xf "' + interJar + '" -C "' + ex + '" mappings/mappings.tiny', { stdio: 'ignore', shell: true });
      } catch (e) {}
      const tinyPath = path.join(ex, 'mappings', 'mappings.tiny');
      if (fs.existsSync(tinyPath)) {
        const tiny = fs.readFileSync(tinyPath, 'utf8');
        for (const line of tiny.split(/\r?\n/)) {
          if (line.startsWith('c\t')) {
            const p = line.split('\t'); // c, official, intermediary
            if (official && p[1] === official) { intermediary = p[2]; break; }
          }
        }
      } else {
        console.log('    [no tiny in intermediary jar] ' + v);
      }
    }

    outData[v] = { official, intermediary, named: 'net/minecraft/client/network/PlayerListEntry' };
    console.log('  ' + v + '\toff=' + (official || '-') + '\tinter=' + (intermediary || '-'));
  }

  fs.writeFileSync(path.join(OUT, 'modern-playerlist.json'), JSON.stringify(outData, null, 2));
  console.log('\nwrote modern-playerlist.json with ' + Object.keys(outData).length + ' versions');
}

main().catch(e => { console.error(e); process.exit(1); });