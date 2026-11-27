'use strict';
// For legacy patch versions lacking any mapping config (1.8.1-1.8.7, 1.9.1,
// 1.9.3, 1.10.1, 1.11.1, 1.12.1), download the obfuscated client jar and find
// the tab-list entry class by scanning class files for a <init> whose first
// parameter is com.mojang.authlib.GameProfile AND that also references a
// PlayerSkin/ResourceLocation-like skin fields - i.e. NetworkPlayerInfo.
// Prints candidate obfuscated class names + ctor descriptors for manual review.

const fs = require('fs');
const path = require('path');
const https = require('https');
const { execSync } = require('child_process');

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

// ---- minimal classfile parser: read constant pool utfs + methods ----
function parseClass(buf) {
  let off = 8; // magic(4)+minor(2)+major(2)
  const cpc = buf.readUInt16BE(off); off += 2;
  const cp = new Array(cpc).fill(null);
  for (let i = 1; i < cpc; i++) {
    const tag = buf[off++];
    if (tag === 1) {
      const len = buf.readUInt16BE(off); off += 2;
      cp[i] = buf.toString('utf8', off, off + len); off += len;
    } else if (tag === 7 || tag === 8 || tag === 16 || tag === 19 || tag === 20) {
      if (tag === 7) { const ni = buf.readUInt16BE(off); cp[i] = { class: ni }; }
      off += 2;
    } else if (tag === 15) {
      off += 3;
    } else if (tag === 3 || tag === 4 || tag === 9 || tag === 10 || tag === 11 || tag === 12 || tag === 17 || tag === 18) {
      off += 4;
    } else if (tag === 5 || tag === 6) {
      off += 8; i++;
    }
  }
  off += 2; // access
  const thisClsIdx = buf.readUInt16BE(off); off += 2;
  const superIdx = buf.readUInt16BE(off); off += 2;
  const nameOf = (idx) => { const c = cp[idx]; return c && c.class ? cp[c.class] : null; };
  const thisName = nameOf(thisClsIdx);
  const superName = nameOf(superIdx);
  const ifaces = buf.readUInt16BE(off); off += 2;
  const ifNames = [];
  for (let i = 0; i < ifaces; i++) { off += 2; ifNames.push(null); }
  const methods = [];
  const fields = [];
  const readMembers = (arr) => {
    const n = buf.readUInt16BE(off); off += 2;
    for (let i = 0; i < n; i++) {
      off += 2; // access
      const ni = buf.readUInt16BE(off); off += 2;
      const di = buf.readUInt16BE(off); off += 2;
      const name = cp[ni], desc = cp[di];
      const attrs = buf.readUInt16BE(off); off += 2;
      for (let a = 0; a < attrs; a++) { const al = buf.readUInt32BE(off + 2); off += 6 + al; }
      arr.push({ name, desc });
    }
  };
  readMembers(fields);
  readMembers(methods);
  return { thisName, superName, methods, fields };
}

function scanJar(jarPath) {
  const dir = path.join(OUT, 'jar-ext', path.basename(jarPath, '.jar'));
  fs.mkdirSync(dir, { recursive: true });
  execSync('tar -xf "' + jarPath + '" -C "' + dir + '"', { stdio: 'ignore', shell: true });
  const results = [];
  const walk = (d) => {
    for (const e of fs.readdirSync(d, { withFileTypes: true })) {
      const p = path.join(d, e.name);
      if (e.isDirectory()) walk(p);
      else if (e.name.endsWith('.class')) {
        try {
          const c = parseClass(fs.readFileSync(p));
          const gpCtor = c.methods.filter(m => m.name === '<init>' && m.desc.includes('Lcom/mojang/authlib/GameProfile;'));
          if (gpCtor.length) {
            // NetworkPlayerInfo has skin-related fields (locationSkin etc.) of
            // ResourceLocation type. Heuristic: fields with desc Ljava/util/UUID;
            // plus the class is likely in net/minecraft client tree (package = obf).
            const uuids = c.fields.filter(f => f.desc === 'Ljava/util/UUID;').length;
            const skinFields = c.fields.filter(f => f.desc.includes('ResourceLocation')).length;
            results.push({ cls: c.thisName, super: c.superName, ctors: gpCtor.map(x => x.desc), uuidFields: uuids, skinFields });
          }
        } catch (e) {}
      }
    }
  };
  walk(dir);
  return results;
}

async function main() {
  const manifest = JSON.parse((await tryGet('https://launchermeta.mojang.com/mc/game/version_manifest_v2.json', 5 * 1024 * 1024)).toString('utf8'));
  const meta = new Map(manifest.versions.map(v => [v.id, v]));
  const missing = ['1.8.1', '1.8.2', '1.8.3', '1.8.4', '1.8.5', '1.8.6', '1.8.7', '1.9.1', '1.9.3', '1.10.1', '1.11.1', '1.12.1'];
  for (const v of missing) {
    const vm = meta.get(v);
    if (!vm) { console.log(v + ': no manifest'); continue; }
    const vj = JSON.parse((await tryGet(vm.url, 2 * 1024 * 1024)).toString('utf8'));
    const dl = vj.downloads && vj.downloads.client;
    if (!dl) { console.log(v + ': no client download'); continue; }
    const jar = path.join(OUT, 'client-' + v + '.jar');
    if (!fs.existsSync(jar)) {
      console.log('downloading client ' + v + ' (' + Math.round(dl.size / 1048576) + 'MB)...');
      const b = await tryGet(dl.url, 1024 * 1024 * 1024);
      if (!b) { console.log(v + ': download failed'); continue; }
      fs.writeFileSync(jar, b);
    }
    const cands = scanJar(jar);
    console.log('\n== ' + v + ' ==');
    for (const c of cands) {
      console.log('  ' + (c.cls || '?') + '  super=' + (c.super || '?') + '  ctors=' + JSON.stringify(c.ctors.slice(0, 3)) + ' uuidF=' + c.uuidFields + ' skinF=' + c.skinFields);
    }
  }
}
main().catch(e => { console.error(e); process.exit(1); });