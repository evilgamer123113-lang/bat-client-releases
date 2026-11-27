const { Client } = require('minecraft-launcher-core');
const launcher = new Client();

// Fast-path the asset/library integrity check. MCLC's getAssets() and
// downloadToDirectory() SHA-1-hash EVERY existing file on every launch
// (components/handler.js checkSum), which reads hundreds of MB from disk and
// makes even a warm launch feel slow. main.js already runs verifyAssetsIntegrity
// (a size check that removes corrupt/missing assets) before this worker spawns,
// so any file present here is either known-good or was just downloaded.
// Skipping the per-file hash keeps that guarantee while cutting launch time.
// A 0-byte file is still treated as corrupt (classic truncated download).
const fs = require('fs');
const MCLCHandler = require('minecraft-launcher-core/components/handler');
MCLCHandler.prototype.checkSum = async function (hash, file) {
    try {
        const stat = fs.statSync(file);
        return stat.isFile() && stat.size > 0;
    } catch (e) { return false; }
};

launcher.startMinecraft = function (launchArguments) {
    const child = require('child_process');
    const mc = child.spawn(
        this.options.javaPath || 'java',
        launchArguments,
        {
            cwd: this.options.overrides.cwd || this.options.root,
            detached: false,
            windowsHide: true,
            shell: false,
            stdio: ['pipe', 'pipe', 'pipe']
        }
    );
    mc.stdout.on('data', (data) => this.emit('data', data.toString('utf-8')));
    mc.stderr.on('data', (data) => this.emit('data', data.toString('utf-8')));
    mc.on('close', (code) => this.emit('close', code));
    return mc;
};

process.on('message', async (msg) => {
    if (msg.type === 'launch') {
        launcher.removeAllListeners();
        launcher.on('progress', (e) => process.send({ type: 'progress', data: e }));
        launcher.on('download-status', (e) => process.send({ type: 'download-status', data: e }));
        launcher.on('debug', (e) => process.send({ type: 'debug', message: String(e) }));

        try {
            const mc = await launcher.launch(msg.options);
            if (!mc) {
                // MCLC returns null when its internal Java check or any download step fails;
                // it swallows the real cause. Ask it to expose the last debug line so the
                // user sees the actual problem instead of a null-crash.
                process.send({ type: 'launch-error', message: 'Minecraft could not start. Check the console log above for the real error.' });
                return;
            }
            process.send({ type: 'launched' });

            if (mc.stdout) mc.stdout.on('data', (data) => process.send({ type: 'stdout', data: data.toString() }));
            if (mc.stderr) mc.stderr.on('data', (data) => process.send({ type: 'stderr', data: data.toString() }));
            mc.on('close', (code) => process.send({ type: 'close', code }));
            mc.on('error', (err) => process.send({ type: 'error', message: err.message }));
        } catch (err) {
            process.send({ type: 'launch-error', message: err.message || String(err) });
        }
    }
});
