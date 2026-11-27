const fs = require('node:fs/promises');
const path = require('node:path');
const STAGE_NAME = /^(?:v\d+\.\d+\.\d+-[a-z0-9]+|[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12})$/i;
function inside(root, file) {
    const relative = path.relative(root, file);
    return relative === '' || (!relative.startsWith('..' + path.sep) && relative !== '..' && !path.isAbsolute(relative));
}
async function plainTree(root) {
    let bytes = 0;
    for (const entry of await fs.readdir(root, {withFileTypes:true})) {
        const file = path.join(root, entry.name), stat = await fs.lstat(file);
        if (stat.isSymbolicLink()) throw Error('Linked staging entry; preserved');
        if (stat.isDirectory()) bytes += await plainTree(file);
        else if (stat.isFile()) bytes += stat.size;
        else throw Error('Unknown staging entry; preserved');
    }
    return bytes;
}
async function regular(file) {
    try { const stat = await fs.lstat(file); return stat.isFile() && !stat.isSymbolicLink(); } catch { return false; }
}
// Only launcher-shaped, stale extraction directories. Reports, backups, unrelated
// folders, linked paths and an executable running from staging are preserved.
async function cleanupUpdateStaging({appDataRoot, execPath = process.execPath, now = Date.now(), minAgeMs = 24*60*60*1000}) {
    const result = {removed:[], skipped:[], bytes:0};
    let root, executable;
    try {
        // Resolve the approved app-data parent first: Windows short-name aliases
        // can differ between synchronous and native realpath implementations.
        root = path.join(await fs.realpath(path.resolve(appDataRoot)), 'update-staging');
        executable = await fs.realpath(execPath).catch(() => path.resolve(execPath));
        const stat = await fs.lstat(root);
        // Windows realpath can normalize drive/directory letter casing. Use the
        // platform's path comparison while still rejecting redirected ancestors.
        if (!stat.isDirectory() || stat.isSymbolicLink() || path.relative(root, await fs.realpath(root)) !== '') return result;
    } catch { return result; }
    for (const entry of await fs.readdir(root, {withFileTypes:true})) {
        if (!STAGE_NAME.test(entry.name)) continue;
        const stage = path.join(root, entry.name);
        try {
            const stat = await fs.lstat(stage);
            if (!stat.isDirectory() || stat.isSymbolicLink() || inside(stage, executable) || now-stat.mtimeMs < minAgeMs) { result.skipped.push(entry.name); continue; }
            const lease = path.join(stage, '.bat-update-owner.json');
            if (await regular(lease)) {
                const owner = JSON.parse(await fs.readFile(lease, 'utf8'));
                if (!Number.isSafeInteger(owner.pid) || owner.pid <= 0) throw Error('Invalid lease');
                try { process.kill(owner.pid, 0); result.skipped.push(entry.name); continue; }
                catch (e) { if (e.code !== 'ESRCH') throw e; }
            }
            const packaged = await regular(path.join(stage, 'Bat Client.exe')) && await regular(path.join(stage, 'resources', 'app.asar'));
            const mac = await regular(path.join(stage, 'Bat Client.app', 'Contents', 'Info.plist')) && await regular(path.join(stage, 'Bat Client.app', 'Contents', 'MacOS', 'Bat Client'));
            const linux = await regular(path.join(stage, 'Bat Client.AppImage'));
            if (!packaged && !mac && !linux) { result.skipped.push(entry.name); continue; }
            const bytes = await plainTree(stage);
            await fs.rm(stage, {recursive:true, force:false});
            result.bytes += bytes; result.removed.push(entry.name);
        } catch { result.skipped.push(entry.name); }
    }
    return result;
}
async function markUpdateStage(stage) {
    await fs.writeFile(path.join(stage, '.bat-update-owner.json'), JSON.stringify({pid:process.pid, created:Date.now()}));
}
module.exports = {cleanupUpdateStaging, markUpdateStage};
