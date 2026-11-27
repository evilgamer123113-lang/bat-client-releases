'use strict';
// Rebuild MappingsData.java cleanly from the rows present after insertion.
// The DATA line is one giant line; only the tail got mangled, rows are intact.

const fs = require('fs');
const FILE = 'bat-client-agent/src/main/java/com/batclient/cosmetics/MappingsData.java';

const file = fs.readFileSync(FILE, 'utf8');

// Extract all row literals: {"C", "a", "b", "c", "d", "e"} or {"X", "a", "b", "c"}
const rowRe = /\{"[CMX]", "[^"]*"(?:, "[^"]*"){1,4}\}/g;
const rows = file.match(rowRe);
if (!rows || rows.length === 0) { console.error('no rows found'); process.exit(1); }
console.log('extracted rows: ' + rows.length);

// Count duplicates + versions
const seen = new Set();
const dupes = [];
for (const r of rows) { if (seen.has(r)) dupes.push(r); seen.add(r); }
if (dupes.length) { console.error('duplicate rows: ' + dupes.length); process.exit(1); }

const header = `/*
 * Decompiled with CFR 0.152.
 */
package com.batclient.cosmetics;

final class MappingsData {
    static final String[][] DATA = new String[][]{`;

const tail = `};

    MappingsData() {
    }
}
`;

const out = header + '\n' + rows.map((r, i) => (i === 0 ? '' : '') + r).join(', ') + tail;
fs.writeFileSync(FILE, out, 'utf8');
console.log('wrote ' + out.length + ' bytes');

// verify
const check = fs.readFileSync(FILE, 'utf8');
const ple = [...check.matchAll(/\{"C", "PlayerListEntry", "([^"]+)"/g)].map(m => m[1]);
console.log('PLE rows: ' + ple.length);
console.log('ends correctly: ' + check.endsWith('\n    MappingsData() {\n    }\n}\n'));
// brace balance on DATA line
const dataLine = check.split('\n').find(l => l.includes('new String[][]'));
const open = (dataLine.match(/\{/g) || []).length;
const close = (dataLine.match(/\}/g) || []).length;
console.log('DATA braces open=' + open + ' close=' + close);