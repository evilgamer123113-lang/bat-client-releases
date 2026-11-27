// Creates a portable ZIP from dist/win-unpacked for fast auto-updates.
// Usage: node scripts/build-portable.js
// NOTE: Uses adm-zip, NOT PowerShell Compress-Archive. Compress-Archive writes
// backslash entry names which break adm-zip extraction in installed clients
// (chmod ENOENT on resources\app.asar).
const path = require('path');
const fs = require('fs');
const AdmZip = require('adm-zip');

const distDir = path.join(__dirname, '..', 'dist');
const unpacked = path.join(distDir, 'win-unpacked');
const version = require('../package.json').version;
const zipName = `BatClient-${version}-Portable.zip`;
const zipPath = path.join(distDir, zipName);

if (!fs.existsSync(unpacked)) {
    console.error('dist/win-unpacked not found. Run "npm run dist" first.');
    process.exit(1);
}

if (fs.existsSync(zipPath)) fs.rmSync(zipPath);

console.log(`Creating portable ZIP: ${zipName}`);
const zip = new AdmZip();
zip.addLocalFolder(unpacked);
zip.writeZip(zipPath);

const firstEntry = zip.getEntries()[0] && zip.getEntries()[0].entryName;
console.log('First entry:', firstEntry);
if (firstEntry.includes('\\')) {
    console.error('FATAL: zip contains backslash entry names; extraction will fail in installed clients.');
    process.exit(1);
}

const sizeMB = (fs.statSync(zipPath).size / 1024 / 1024).toFixed(1);
console.log(`Done: ${zipPath} (${sizeMB} MB)`);
