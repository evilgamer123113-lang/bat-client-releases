'use strict';
// Identify NetworkPlayerInfo among the (GameProfile)V ctor candidates by
// cross-reference frequency: NPI is referenced as a type in many methods across
// the client (tab overlay getters etc). Calibrate against known-mapped versions
// (1.8->ces, 1.9.2->bks, 1.9.4->bkt, 1.10.2->blo, 1.11.2->bnr, 1.12.2->bsc).

const fs = require('fs');
const path = require('path');
const https = require('https');
const { execSync } = require('child_process');

const OUT = path.join(process.env.TEMP || '.', 'opencode', 'playerlist-data');

function parseClass(buf) {
  let off = 8;
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
  off += 2;
  const thisClsIdx = buf.readUInt16BE(off); off += 2;
  const superIdx = buf.readUInt16BE(off); off += 2;
  const nameOf = (idx) => { const c = cp[idx]; return c && c.class ? cp[c.class] : null; };
  const thisName = nameOf(thisClsIdx);
  const superName = nameOf(superIdx);
  const ifaces = buf.readUInt16BE(off); off += 2;
  for (let i = 0; i < ifaces; i++) off += 2;
  const methods = [];
  const fields = [];
  const readMembers = (arr) => {
    const n = buf.readUInt16BE(off); off += 2;
    for (let i = 0; i < n; i++) {
      off += 2;
      const ni = buf.readUInt16BE(off); off += 2;
      const di = buf.readUInt16BE(off); off += 2;
      arr.push({ name: cp[ni], desc: cp[di] });
      const attrs = buf.readUInt16BE(off); off += 2;
      for (let a = 0; a < attrs; a++) { const al = buf.readUInt32BE(off + 2); off += 6 + al; }
    }
  };
  readMembers(fields);
  readMembers(methods);
  return { thisName, superName, methods, fields };
}

function loadClasses(jarPath) {
  const dir = path.join(OUT, 'jar-ext', path.basename(jarPath, '.jar'));
  fs.mkdirSync(dir, { recursive: true });
  execSync('tar -xf "' + jarPath + '" -C "' + dir + '"', { stdio: 'ignore', shell: true });
  const classes = [];
  const walk = (d) => {
    for (const e of fs.readdirSync(d, { withFileTypes: true })) {
      const p = path.join(d, e.name);
      if (e.isDirectory()) walk(p);
      else if (e.name.endsWith('.class')) {
        try { classes.push(parseClass(fs.readFileSync(p))); } catch (e2) {}
      }
    }
  };
  walk(dir);
  return classes;
}

function analyze(classes) {
  const byName = new Map(classes.map(c => [c.thisName, c]));
  const candidates = classes.filter(c =>
    c.superName === 'java/lang/Object' &&
    c.methods.some(m => m.name === '<init>' && m.desc === '(Lcom/mojang/authlib/GameProfile;)V'));
  // reference counts: how many class files mention this type in a method descriptor
  const refs = new Map();
  for (const cn of classes) {
    for (const m of cn.methods) {
      for (const cand of candidates) {
        if (m.desc.includes(cand.thisName)) refs.set(cand.thisName, (refs.get(cand.thisName) || 0) + 1);
      }
      if (!m.name.startsWith('<')) {
        // method return/param mentions
      }
    }
  }
  // Structural fingerprint: NPI has a GameProfile field + TWO fields of the same
  // non-primitive type (locationSkin + locationCape ResourceLocations).
  const results = candidates.map(c => {
    const r = refs.get(c.thisName) || 0;
    const gpFields = c.fields.filter(f => f.desc === 'Lcom/mojang/authlib/GameProfile;').length;
    const hist = new Map();
    for (const f of c.fields) {
      if (f.desc.length < 3) continue;
      if (f.desc.startsWith('[L') || f.desc.startsWith('L')) hist.set(f.desc, (hist.get(f.desc) || 0) + 1);
    }
    const top = [...hist.entries()].sort((a, b) => b[1] - a[1]).slice(0, 4);
    const pair = [...hist.entries()].find(([, n]) => n >= 2);
    return { cls: c.thisName, refs: r, gpFields, pair: pair ? pair[0] + 'x' + pair[1] : '', top: top.map(([t, n]) => t + 'x' + n).join(' ') };
  }).sort((a, b) => b.refs - a.refs);
  return results;
}

async function main() {
  const versions = ['1.8', '1.8.1', '1.8.2', '1.8.3', '1.8.4', '1.8.5', '1.8.6', '1.8.7', '1.9', '1.9.1', '1.9.2', '1.9.3', '1.9.4', '1.10', '1.10.1', '1.10.2', '1.11', '1.11.1', '1.11.2', '1.12', '1.12.1', '1.12.2', '1.13', '1.13.1', '1.13.2', '1.14', '1.14.1', '1.14.2', '1.14.3'];
  const manifest = JSON.parse((await new Promise((res, rej) => {
    https.get('https://launchermeta.mojang.com/mc/game/version_manifest_v2.json', { headers: { 'User-Agent': 'bat-client' } }, (r) => {
      if (r.statusCode >= 300 && r.statusCode < 400 && r.headers.location) { r.resume(); return rej(new Error('redirect')); }
      const ch = []; r.on('data', c => ch.push(c)); r.on('end', () => res(Buffer.concat(ch)));
    }).on('error', rej);
  })).toString('utf8'));
  const meta = new Map(manifest.versions.map(v => [v.id, v]));

  const known = { '1.8': 'ces', '1.8.8': 'bdc', '1.9': 'bkv', '1.9.2': 'bks', '1.9.4': 'bkt', '1.10': 'blo', '1.10.2': 'blo', '1.11': 'bnp', '1.11.2': 'bnr', '1.12': 'bsa', '1.12.2': 'bsc' };
  const out = {};
  for (const v of versions) {
    let jar = path.join(OUT, 'client-' + v + '.jar');
    let need = false;
    if (v === '1.8') { jar = path.join(OUT, 'client-1.8.jar'); if (!fs.existsSync(jar)) need = true; }
    else if (!fs.existsSync(jar)) need = true;
    if (need) {
      const vm = meta.get(v);
      if (!vm) continue;
      const vj = JSON.parse((await new Promise((res, rej) => {
        https.get(vm.url, { headers: { 'User-Agent': 'bat-client' } }, (r) => { const ch = []; r.on('data', c => ch.push(c)); r.on('end', () => res(Buffer.concat(ch))); }).on('error', rej);
      })).toString('utf8'));
      const dl = vj.downloads && vj.downloads.client;
      if (!dl) continue;
      console.log('download ' + v + '...');
      const b = await new Promise((res, rej) => {
        https.get(dl.url, { headers: { 'User-Agent': 'bat-client' } }, (r) => {
          if (r.statusCode >= 300 && r.statusCode < 400) { r.resume(); return rej(new Error('redirect')); }
          const ch = []; r.on('data', c => ch.push(c)); r.on('end', () => res(Buffer.concat(ch)));
        }).on('error', rej);
      });
      fs.writeFileSync(jar, b);
    }
    const classes = loadClasses(jar);
    const res = analyze(classes);
    const top = res.slice(0, 6).map(r => r.cls + '(refs=' + r.refs + ',gp=' + r.gpFields + ',pair=' + (r.pair || '-') + ')').join('  ');
    const mark = known[v] ? ' EXPECT=' + known[v] : '';
    console.log(v + ': ' + top + mark);
    out[v] = res.filter(r => r.refs > 0).slice(0, 6);
  }
  fs.writeFileSync(path.join(OUT, 'refscan.json'), JSON.stringify(out, null, 2));
}
main().catch(e => { console.error(e); process.exit(1); });