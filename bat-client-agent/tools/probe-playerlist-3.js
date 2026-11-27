'use strict';
// Scan every config in playerlist-data for PlayerListEntry / NetworkPlayerInfo
// class mappings (obfuscated name) and print a table.

const fs = require('fs');
const path = require('path');

const OUT = path.join(process.env.TEMP || '.', 'opencode', 'playerlist-data');

function parseSrg(text) {
  const obfOfNamed = new Map();
  for (const line of text.split(/\r?\n/)) {
    const m = line.match(/^CL: (\S+) (\S+)$/);
    if (m) obfOfNamed.set(m[2], m[1]);
  }
  return obfOfNamed;
}

function parseTsrg(text) {
  const obfOfNamed = new Map();
  for (const line of text.split(/\r?\n/)) {
    if (!line.trim()) continue;
    if (!line.startsWith('\t')) {
      const p = line.trim().split(/\s+/);
      if (p.length >= 2) obfOfNamed.set(p[1], p[0]);
    }
  }
  return obfOfNamed;
}

function scanFile(file, v, src) {
  const text = fs.readFileSync(file, 'utf8');
  const isSrg = file.endsWith('.srg');
  const map = isSrg ? parseSrg(text) : parseTsrg(text);
  const targets = [
    'net/minecraft/client/network/NetworkPlayerInfo',
    'net/minecraft/client/network/PlayerListEntry',
    'net/minecraft/client/multiplayer/PlayerListEntry',
    'net/minecraft/client/multiplayer/PlayerInfo',
    'net/minecraft/client/network/PlayerInfo',
  ];
  const found = [];
  for (const t of targets) {
    if (map.has(t)) found.push(t + ' -> ' + map.get(t));
  }
  if (found.length) console.log(src + '\t' + v + '\t' + found.join(' | '));
  else console.log(src + '\t' + v + '\t(no player-entry class found)');
}

function main() {
  const files = fs.readdirSync(OUT).filter(f => /\.(srg|tsrg)$/.test(f) && !('probes.json' === f || f === 'versions.json'));
  const versions = JSON.parse(fs.readFileSync(path.join(OUT, 'versions.json'), 'utf8'));
  console.log('Versions total:', versions.length);
  console.log('\n-- mcpbot srg --');
  for (const v of ['1.8', '1.8.8', '1.8.9', '1.9', '1.9.2', '1.9.4', '1.10', '1.10.2', '1.11']) {
    const f = path.join(OUT, 'mcpbot-srg-' + v + '.srg');
    if (fs.existsSync(f)) scanFile(f, v, 'mcpbot');
  }
  console.log('\n-- zffu --');
  for (const f of fs.readdirSync(OUT)) {
    if (f.startsWith('zffu-')) scanFile(path.join(OUT, f), f.replace(/^zffu-/, '').replace(/\.(srg|tsrg)$/, ''), 'zffu');
  }
  console.log('\n-- mcpconfig --');
  for (const f of fs.readdirSync(OUT)) {
    if (f.startsWith('mcpconfig-')) scanFile(path.join(OUT, f), f.replace(/^mcpconfig-/, '').replace(/\.tsrg$/, ''), 'mcpconfig');
  }
}

main();