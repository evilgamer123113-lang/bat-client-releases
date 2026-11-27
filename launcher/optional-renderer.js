const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');
const AdmZip = require('adm-zip');
const sha = bytes => crypto.createHash('sha256').update(bytes).digest('hex');

// Migration for older Bat packages which nested Sodium. Never rewrite unrelated
// mods or protected class/UI bytes. New Bat builds leave renderer installation
// to the launcher and its persistent user preferences.
async function removeBundledSodium(file, backupDir) {
    const stat = await fs.lstat(file);
    if (!stat.isFile() || stat.isSymbolicLink()) return {changed:false};
    const original = await fs.readFile(file), zip = new AdmZip(original);
    const metadata = zip.getEntry('fabric.mod.json');
    if (!metadata) return {changed:false};
    const mod = JSON.parse(metadata.getData().toString('utf8'));
    if (mod.id !== 'batclient') return {changed:false};
    const removed = [];
    for (const entry of mod.jars || []) {
        if (!entry.file || !/^META-INF\/jars\/[^/]+\.jar$/.test(entry.file)) continue;
        const nested = zip.getEntry(entry.file);
        if (!nested) continue;
        const info = new AdmZip(nested.getData()).getEntry('fabric.mod.json');
        if (info && JSON.parse(info.getData().toString('utf8')).id === 'sodium') removed.push(entry.file);
    }
    if (!removed.length) return {changed:false};
    if (mod.depends?.sodium || zip.getEntries().some(e=>/^META-INF\/.*\.(?:SF|RSA|DSA|EC)$/i.test(e.entryName))) {
        throw Error('This Bat package cannot safely disable its nested renderer. Install the current renderer-optional Bat mod.');
    }
    mod.jars = mod.jars.filter(entry=>!removed.includes(entry.file));
    for (const name of removed) zip.deleteFile(name);
    zip.updateFile('fabric.mod.json', Buffer.from(JSON.stringify(mod,null,2)+'\n'));
    const output = zip.toBuffer(), verified = new AdmZip(output);
    for (const entry of new AdmZip(original).getEntries()) {
        if (entry.isDirectory || entry.entryName === 'fabric.mod.json' || removed.includes(entry.entryName)) continue;
        const replacement = verified.getEntry(entry.entryName);
        if (!replacement || !entry.getData().equals(replacement.getData())) throw Error('Protected mod contents changed; refusing renderer migration.');
    }
    await fs.mkdir(backupDir,{recursive:true});
    const backup = path.join(backupDir,sha(original)+'.jar.backup');
    try { await fs.writeFile(backup,original,{flag:'wx'}); }
    catch (error) { if (error.code !== 'EEXIST' || sha(await fs.readFile(backup)) !== sha(original)) throw error; }
    const temp = file+'.bat-choice-'+crypto.randomUUID()+'.tmp';
    try {
        await fs.writeFile(temp,output,{flag:'wx'});
        if (sha(await fs.readFile(file)) !== sha(original)) throw Error('Mod changed during renderer migration; original preserved.');
        await fs.rename(temp,file);
    } finally { await fs.rm(temp,{force:true}); }
    return {changed:true, removed, backup};
}
module.exports = {removeBundledSodium};
