const path = require('node:path');
const fs = require('node:fs/promises');
const {spawn, execFile} = require('node:child_process');
const {promisify} = require('node:util');
const run = promisify(execFile);
function selectUpdate(data, platform = process.platform, arch = process.arch) {
    const entry = data.platforms?.[platform]?.[arch];
    return entry || (platform === 'win32' ? {download:data.download,sha256:data.sha256,website:data.website} : null);
}
function updateExtension(url, platform = process.platform) {
    const suffix = path.extname(new URL(url).pathname).toLowerCase();
    const allowed = {win32:['.zip','.exe'],darwin:['.zip'],linux:['.appimage']};
    if (!allowed[platform]?.includes(suffix)) throw new Error('Wrong update format for ' + platform);
    return suffix === '.appimage' ? '.AppImage' : suffix;
}
function validateMacZip(zip) {
    for (const entry of zip.getEntries()) {
        const name = entry.entryName;
        if (name.includes('\\') || name.startsWith('/') || name.split('/').includes('..')) throw new Error('Unsafe macOS archive entry');
        const mode = entry.header.attr >>> 16;
        if ((mode & 0xf000) === 0xa000) {
            const link = entry.getData().toString('utf8');
            const resolved = path.posix.normalize(path.posix.join(path.posix.dirname(name),link));
            if (path.posix.isAbsolute(link) || resolved === '..' || resolved.startsWith('../')) throw new Error('Unsafe macOS archive symlink');
        }
    }
    if (!zip.getEntry('Bat Client.app/Contents/Info.plist')) throw new Error('Missing Bat Client application');
}
// The main process supplies only a downloaded, SHA-verified file.
async function installUnixUpdate(file, {execPath, appImage, stagingRoot, pid = process.pid, platform = process.platform}) {
    const id = require('node:crypto').randomUUID();
    const stage = path.join(stagingRoot,id);
    await fs.mkdir(stage,{recursive:true});
    await require('./update-cleanup').markUpdateStage(stage);
    let source, target;
    if (platform === 'darwin') {
        const zip = new (require('adm-zip'))(file); validateMacZip(zip);
        await run('/usr/bin/ditto',['-x','-k',file,stage]);
        source = path.join(stage,'Bat Client.app');
        target = path.dirname(path.dirname(path.dirname(execPath)));
        if (!target.endsWith('.app')) throw new Error('macOS updater must run inside an installed application');
        await fs.access(path.join(source,'Contents','MacOS','Bat Client'));
    } else if (platform === 'linux') {
        if (!appImage || !path.isAbsolute(appImage)) throw new Error('Automatic Linux updates require the AppImage distribution. Download it from batclient.xyz.');
        target = appImage;
        source = path.join(stage,'Bat Client.AppImage');
        const handle = await fs.open(file,'r');
        try { const header = Buffer.alloc(11); await handle.read(header,0,11,0);
            if (header.subarray(0,4).toString('hex') !== '7f454c46' || header[8] !== 65 || header[9] !== 73 || header[10] !== 2) throw new Error('Invalid AppImage update');
        } finally { await handle.close(); }
        await fs.copyFile(file,source); await fs.chmod(source,0o755);
    } else throw new Error('Unsupported Unix update platform');
    // Stage alongside the destination so the final rename is on the same filesystem.
    const parent = path.dirname(target);
    await fs.access(parent,require('node:fs').constants.W_OK);
    const incoming = path.join(parent,'.batclient-incoming-'+id);
    await fs.cp(source,incoming,{recursive:true,dereference:false,verbatimSymlinks:true,preserveTimestamps:true});
    const helper = path.join(stage,'install.sh');
    const script = `#!/bin/sh
set -eu
pid="$1"; incoming="$2"; target="$3"; backup="$4"; platform="$5"; result="$6"
count=0
while kill -0 "$pid" 2>/dev/null; do
    count=$((count+1)); [ "$count" -lt 120 ] || exit 1
    sleep 1
done
[ ! -e "$backup" ] || exit 1
mv "$target" "$backup"
if mv "$incoming" "$target"; then printf '%s' '{"ok":true,"message":"updated"}' > "$result"
else mv "$backup" "$target"; printf '%s' '{"ok":false,"message":"Update replacement failed; restored previous version."}' > "$result"
fi
if [ "$platform" = darwin ]; then /usr/bin/open "$target" --args --updated
else "$target" --updated >/dev/null 2>&1 &
fi
`;
    await fs.writeFile(helper,script,{mode:0o700});
    const child = spawn('/bin/sh',[helper,String(pid),incoming,target,target+'.bat-backup-'+id,platform,path.join(stagingRoot,'batclient-update-result.json')],{detached:true,stdio:'ignore'});
    await new Promise((resolve,reject)=>{ child.once('spawn',resolve); child.once('error',reject); });
    child.unref();
    return {success:true,handoffStarted:true};
}
module.exports = {selectUpdate,updateExtension,validateMacZip,installUnixUpdate};
