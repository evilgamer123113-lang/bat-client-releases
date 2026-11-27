'use strict';
// Build definitive PlayerListEntry dataset for all 70 release versions.
// Row format matches MappingsData.java: {"C", "PlayerListEntry", VER, OFFICIAL, INTERMEDIARY, NAMED}
// - Pre-1.14: no fabric intermediary -> intermediary column = obf name (same as official)
//   named = MCP name (existing legacy rows use MCP names)
// - 1.14-1.14.3: fabric exists, intermediary = net/minecraft/class_640,
//   named = yarn PlayerListEntry (existing 1.14-1.14.3 rows use yarn names)
// - 1.14.4+: official from client_mappings.txt, intermediary = class_640,
//   named = yarn PlayerListEntry (existing rows use yarn names)

const fs = require('fs');
const path = require('path');
const OUT = path.join(process.env.TEMP || '.', 'opencode', 'playerlist-data');

const legacyObf = {
  '1.8': 'ces', '1.8.1': 'cer', '1.8.2': 'bdd', '1.8.3': 'bdd',
  '1.8.4': 'bdc', '1.8.5': 'bdc', '1.8.6': 'bdc', '1.8.7': 'bdc',
  '1.8.8': 'bdc', '1.8.9': 'bdc',
  '1.9': 'bkv', '1.9.1': 'bks', '1.9.2': 'bks', '1.9.3': 'bkt', '1.9.4': 'bkt',
  '1.10': 'blo', '1.10.1': 'blo', '1.10.2': 'blo',
  '1.11': 'bnp', '1.11.1': 'bnr', '1.11.2': 'bnr',
  '1.12': 'bsa', '1.12.1': 'bsc', '1.12.2': 'bsc',
  '1.13': 'cqw', '1.13.1': 'crg', '1.13.2': 'crh'
};

const transition = { // 1.14-1.14.3: official obf (jar+config verified), intermediary class_640
  '1.14': 'dhm', '1.14.1': 'dho', '1.14.2': 'dho', '1.14.3': 'dhs'
};

const modern = JSON.parse(fs.readFileSync(path.join(OUT, 'modern-playerlist.json'), 'utf8'));

const NAMED_LEGACY = 'net/minecraft/client/network/NetworkPlayerInfo';
const NAMED_MODERN = 'net/minecraft/client/network/PlayerListEntry';
const INTER = 'net/minecraft/class_640';

const rows = [];
for (const [v, obf] of Object.entries(legacyObf)) {
  rows.push(['C', 'PlayerListEntry', v, obf, obf, NAMED_LEGACY]);
}
for (const [v, obf] of Object.entries(transition)) {
  rows.push(['C', 'PlayerListEntry', v, obf, INTER, NAMED_MODERN]);
}
for (const [v, d] of Object.entries(modern)) {
  rows.push(['C', 'PlayerListEntry', v, d.official, INTER, NAMED_MODERN]);
}

// sanity: 27 legacy + 4 transition + 39 modern = 70
const total = rows.length;
const missing = new Set([...Object.keys(legacyObf), ...Object.keys(transition), ...Object.keys(modern)]);
// version set must equal 70 releases
if (total !== 70) { console.error('expected 70 rows, got ' + total); process.exit(1); }
const versions = rows.map(r => r[2]).sort((a, b) => {
  const pa = a.split('.').map(Number), pb = b.split('.').map(Number);
  for (let i = 0; i < 3; i++) { if ((pa[i] || 0) !== (pb[i] || 0)) return (pa[i] || 0) - (pb[i] || 0); }
  return 0;
});
console.log('rows=' + total + ' unique versions=' + new Set(versions).size);
console.log('versions:');
console.log(versions.join(' '));

fs.writeFileSync(path.join(OUT, 'playerlist-rows.json'), JSON.stringify(rows, null, 0));
console.log('written playerlist-rows.json');