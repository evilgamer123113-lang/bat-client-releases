'use strict';
// Continuation: extract PlayerListEntry/NetworkPlayerInfo obfuscated class names
//  from the CONFIGS already downloaded to %TEMP%\opencode\playerlist-data.
//  Also re-download mcpbot zips to files and extract joined.srg properly.

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

function listZip(zipFile) {
  const out = execSync('tar -tf "' + zipFile + '"', { encoding: 'utf8' }).split(/\r?\n/).filter(Boolean);
  return out;
}

function extractAll(zipFile, outDir) {
  fs.mkdirSync(outDir, { recursive: true });
  execSync('tar -xf "' + zipFile + '" -C "' + outDir + '"', { stdio: 'ignore' });
}

async function main() {
  // Re-download mcpbot zips to FILES (fix extraction)
  const mcpbotVersions = ['1.8', '1.8.8', '1.8.9', '1.9', '1.9.2', '1.9.4', '1.10', '1.10.2', '1.11'];
  console.log('== mcpbot zips ==');
  for (const v of mcpbotVersions) {
    const url = 'https://github.com/ModCoderPack/mcpbot/raw/master/mcp-' + v + '-srg.zip';
    const zipPath = path.join(OUT, 'mcpbot-' + v + '.zip');
    const buf = await tryGet(url, 100 * 1024 * 1024);
    if (!buf) { console.log('  MISS ' + v); continue; }
    fs.writeFileSync(zipPath, buf);
    let names;
    try { names = listZip(zipPath); }
    catch (e) { console.log('  LZFAIL ' + v + ' ' + e.message); continue; }
    const srgEntry = names.find(n => n.endsWith('joined.srg')) || names.find(n => n.endsWith('.srg'));
    if (srgEntry) {
      extractAll(zipPath, path.join(OUT, 'mcpbot-ext', v));
      console.log('  OK ' + v + ' entry=' + srgEntry);
    } else {
      console.log('  NO-SRG ' + v + ' entries=' + names.slice(0, 5).join(','));
    }
  }

  // ---- Find joined.srg wherever mcpbot put it and copy to flat name ----
  console.log('\n== flatten mcpbot srg ==');
  const extRoot = path.join(OUT, 'mcpbot-ext');
  if (fs.existsSync(extRoot)) {
    const walk = (dir, acc) => {
      for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
        const p = path.join(dir, e.name);
        if (e.isDirectory()) walk(p, acc);
        else if (e.name.endsWith('.srg')) acc.push(p);
      }
      return acc;
    };
    const srgs = walk(extRoot, []);
    for (const f of srgs) {
      const m = f.match(/mcpbot-ext[\\/]([^\\/]+)[\\/]/.exec(f) ? f : null);
      const vPart = /mcpbot-ext[\\/]([^\\/]+)/.exec(f);
      if (vPart) {
        const dest = path.join(OUT, 'mcpbot-srg-' + vPart[1] + '.srg');
        fs.copyFileSync(f, dest);
        console.log('  ' + dest + ' (' + fs.statSync(dest).size + ' bytes)');
      }
    }
  }
}

main().catch(e => { console.error(e); process.exit(1); });