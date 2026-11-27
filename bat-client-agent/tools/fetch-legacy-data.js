'use strict';

// Downloads, into a cache directory, everything needed to generate the
// 1.8.0 - 1.14.3 rows of MappingsData.java:
//   - client jars for all 31 versions (obfuscated runtime classes)
//   - ground-truth mapping configs: mcpbot srg zips, Zffu joined.tsrg,
//     MCPConfig joined.tsrg (master branch), Fabric intermediary v2 jars
//
// Idempotent: existing files with the correct size/sha1 are skipped.

const fs = require('fs');
const path = require('path');
const https = require('https');
const { execSync } = require('child_process');

const CACHE = process.env.RAVEN_LEGACY_CACHE ||
  path.join(process.env.TEMP || process.env.TMP || '.', 'opencode', 'legacy-data');

const VERSIONS = [
  '1.8', '1.8.1', '1.8.2', '1.8.3', '1.8.4', '1.8.5', '1.8.6', '1.8.7',
  '1.8.8', '1.8.9', '1.9', '1.9.1', '1.9.2', '1.9.3', '1.9.4',
  '1.10', '1.10.1', '1.10.2', '1.11', '1.11.1', '1.11.2',
  '1.12', '1.12.1', '1.12.2', '1.13', '1.13.1', '1.13.2',
  '1.14', '1.14.1', '1.14.2', '1.14.3'
];

const MCBOT = ['1.8', '1.8.8', '1.8.9', '1.9', '1.9.2', '1.9.4', '1.10', '1.10.2', '1.11'];
const ZFFU = ['1.8.8', '1.8.9', '1.9.4', '1.10', '1.11.2', '1.12', '1.12.2', '1.13', '1.13.2', '1.14', '1.14.1', '1.14.2', '1.14.3'];
const MCPCONFIG = ['1.13.1', '1.13.2', '1.14', '1.14.1', '1.14.2', '1.14.3'];
const INTERMEDIARY = ['1.14', '1.14.1', '1.14.2', '1.14.3'];

const HDR = { 'User-Agent': 'bat-client' };
let fetched = { jars: {}, configs: {} };

function download(url, dest, sha1Expected) {
  return new Promise((resolve, reject) => {
    https.get(url, { headers: HDR }, (res) => {
      if (res.statusCode === 301 || res.statusCode === 302 || res.statusCode === 307) {
        res.resume();
        download(res.headers.location, dest, sha1Expected).then(resolve, reject);
        return;
      }
      if (res.statusCode !== 200) {
        res.resume();
        reject(new Error(url + ' -> HTTP ' + res.statusCode));
        return;
      }
      const tmp = dest + '.part';
      fs.mkdirSync(path.dirname(dest), { recursive: true });
      const f = fs.createWriteStream(tmp);
      f.on('finish', () => {
        const ok = sha1Expected ? sha1(tmp) === sha1Expected : true;
        if (ok) {
          fs.renameSync(tmp, dest);
          resolve();
        } else {
          fs.unlinkSync(tmp);
          reject(new Error('sha1 mismatch: ' + dest));
        }
      });
      f.on('error', reject);
      res.pipe(f);
    }).on('error', reject);
  });
}

function sha1(file) {
  const crypto = require('crypto');
  return crypto.createHash('sha1').update(fs.readFileSync(file)).digest('hex');
}

function fileExists(dest, size, sha1Expected) {
  if (!fs.existsSync(dest)) return false;
  const st = fs.statSync(dest);
  if (size && st.size !== size) return false;
  if (sha1Expected && sha1(dest) !== sha1Expected) return false;
  return true;
}

function fetch(url, dest, size, sha1Expected) {
  if (fileExists(dest, size, sha1Expected)) {
    console.log('  [skip] ' + dest);
    return Promise.resolve();
  }
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  console.log('  [get ] ' + url);
  return download(url, dest, sha1Expected);
}

function unzip(zipPath, outDir) {
  fs.mkdirSync(outDir, { recursive: true });
  execSync('tar -xf "' + zipPath + '" -C "' + outDir + '"', { stdio: 'ignore' });
}

async function main() {
  fs.mkdirSync(CACHE, { recursive: true });

  const manifestPath = process.env.APPDATA ? path.join(process.env.APPDATA, 'bat-client', 'version_manifest_cache.json') : null;
  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  const meta = new Map(manifest.versions.map(v => [v.id, v]));

  const jarsDir = path.join(CACHE, 'jars');
  const configsDir = path.join(CACHE, 'configs');

  // 1. client jars
  console.log('== client jars ==');
  for (const v of VERSIONS) {
    const vm = meta.get(v);
    if (!vm) { console.log('  [WARN] no manifest entry for ' + v); continue; }
    const vjson = await (async () => {
      const p = path.join(CACHE, 'version-json', v + '.json');
      if (fs.existsSync(p)) return JSON.parse(fs.readFileSync(p, 'utf8'));
      await fetch(vm.url, p, null, null);
      return JSON.parse(fs.readFileSync(p, 'utf8'));
    })();
    const dl = vjson.downloads && vjson.downloads.client;
    if (!dl) { console.log('  [WARN] no client download for ' + v); continue; }
    const jar = path.join(jarsDir, v + '.jar');
    const ok = await fetch(dl.url, jar, dl.size, dl.sha1);
    if (fs.existsSync(jar)) {
      fetched.jars[v] = { size: fs.statSync(jar).size, sha1: sha1(jar) };
    }
  }

  // 2. mcpbot srg zips (obf -> srg/mcp)
  console.log('== mcpbot ==');
  for (const v of MCBOT) {
    const zip = path.join(configsDir, 'mcpbot', v + '.zip');
    await fetch('https://github.com/ModCoderPack/mcpbot/raw/master/mcp-' + v + '-srg.zip', zip, null, null);
    if (fs.existsSync(zip)) {
      unzip(zip, path.join(configsDir, 'mcpbot', v));
      fetched.configs['mcpbot-' + v] = true;
    }
  }

  // 3. Zffu joined.tsrg/srg (obf -> mcp-named, obf members -> srg)
  console.log('== zffu ==');
  for (const v of ZFFU) {
    const out = path.join(configsDir, 'zffu', v + '.tsrg');
    let ok = false;
    try {
      await fetch('https://raw.githubusercontent.com/Zffu/mappings/main/versions/' + v + '/joined.tsrg', out, null, null);
      ok = fs.existsSync(out);
    } catch (e) { ok = false; }
    if (!ok) {
      const out2 = path.join(configsDir, 'zffu', v + '.srg');
      await fetch('https://raw.githubusercontent.com/Zffu/mappings/main/versions/' + v + '/joined.srg', out2, null, null);
      if (fs.existsSync(out2)) fetched.configs['zffu-' + v] = true;
    } else {
      fetched.configs['zffu-' + v] = true;
    }
  }

  // 4. MCPConfig joined.tsrg (obf -> named, obf members -> srg) -- master branch
  console.log('== mcpconfig ==');
  for (const v of MCPCONFIG) {
    const out = path.join(configsDir, 'mcpconfig', v + '.tsrg');
    await fetch('https://raw.githubusercontent.com/MinecraftForge/MCPConfig/master/versions/release/' + v + '/joined.tsrg', out, null, null);
    if (fs.existsSync(out)) fetched.configs['mcpconfig-' + v] = true;
  }

  // 5. Fabric intermediary v2 (official + intermediary + yarn named)
  console.log('== fabric intermediary ==');
  for (const v of INTERMEDIARY) {
    const jar = path.join(configsDir, 'intermediary', v + '-v2.jar');
    await fetch('https://maven.fabricmc.net/net/fabricmc/intermediary/' + v + '/intermediary-' + v + '-v2.jar', jar, null, null);
    if (fs.existsSync(jar)) {
      unzip(jar, path.join(configsDir, 'intermediary', v));
      fetched.configs['intermediary-' + v] = true;
    }
  }

  fs.writeFileSync(path.join(CACHE, 'manifest.json'), JSON.stringify({ fetched, versions: VERSIONS }, null, 2));
  console.log('CACHE=' + CACHE);
  console.log('jars=' + Object.keys(fetched.jars).length + ' configs=' + Object.keys(fetched.configs).length);
}

main().catch(e => { console.error(e); process.exit(1); });
