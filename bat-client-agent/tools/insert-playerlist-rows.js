'use strict';
// Insert 70 PlayerListEntry rows into MappingsData.java.
// Skips versions that already have a PlayerListEntry row (currently only 1.21).
// Preserves existing rows exactly; adds new rows before the closing }}.

const fs = require('fs');
const path = require('path');
const OUT = path.join(process.env.TEMP || '.', 'opencode', 'playerlist-data');
const FILE = 'bat-client-agent/src/main/java/com/batclient/cosmetics/MappingsData.java';

const file = fs.readFileSync(FILE, 'utf8');

// Parse existing rows: {"C", "PlayerListEntry", "VER", ...}
const existingPLE = new Set(
  [...file.matchAll(/\{"([CMX])", "PlayerListEntry", "([^"]+)"/g)].map(m => m[2])
);
console.log('existing PlayerListEntry versions:', [...existingPLE].join(' ') || '(none)');

const rows = JSON.parse(fs.readFileSync(path.join(OUT, 'playerlist-rows.json'), 'utf8'));
const toAdd = rows.filter(r => !existingPLE.has(r[2]));
console.log('to add: ' + toAdd.length + ' rows');

// Format rows exactly matching existing convention: {"C", "PlayerListEntry", "VER", "OB", "INTER", "NAMED"}
const formatted = toAdd.map(r => `{"${r[0]}", "${r[1]}", "${r[2]}", "${r[3]}", "${r[4]}", "${r[5]}"}`);

// Find the insertion point: the last }} before the semicolon on the DATA line.
// The DATA line ends with }};  - we insert before the final }}
const endMarker = /\}\};(\r?\n|$)/;
if (!endMarker.test(file)) { console.error('cannot find DATA end marker'); process.exit(1); }

const insert = ', ' + formatted.join(', ');
const newFile = file.replace(endMarker, insert + '};$1');

// Verify new file has correct total count
const totalPLE = [...newFile.matchAll(/\{"([CMX])", "PlayerListEntry"/g)].length;
console.log('total PlayerListEntry rows after insert: ' + totalPLE);
if (totalPLE !== 70) { console.error('expected 70, got ' + totalPLE); process.exit(1); }

fs.writeFileSync(FILE, newFile, 'utf8');
console.log('MappingsData.java updated (' + newFile.length + ' bytes)');