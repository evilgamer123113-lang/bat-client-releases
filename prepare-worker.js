const path = require('path');
const fs = require('fs');
const MCLCore = require('minecraft-launcher-core');
const Handler = require('minecraft-launcher-core/components/handler');

// Skip MCLC's per-file SHA-1 re-hash (components/handler.js checkSum) during the
// background pre-download too — main.js verifyAssetsIntegrity already size-checks
// assets before a real launch, and files that don't exist get downloaded anyway.
// A 0-byte file is still treated as corrupt (classic truncated download).
Handler.prototype.checkSum = async function (hash, file) {
    try {
        const stat = fs.statSync(file);
        return stat.isFile() && stat.size > 0;
    } catch (e) { return false; }
};

// Background pre-download worker.
//
// Drives MCLC's OWN download pipeline (version json, client jar, natives,
// libraries, assets) directly into the same gameRoot the real launch uses,
// but NEVER spawns Java. The point: when the user hits Launch, everything
// MCLC needs already exists, so launch() skips every download and the game
// starts almost instantly.
//
// This bypasses checkJava()/startMinecraft() which launch() would otherwise
// run — we only need the handler's download methods, so no Java binary is
// required here.

const DEFAULT_URLS = {
    meta: 'https://launchermeta.mojang.com',
    resource: 'https://resources.download.minecraft.net',
    mavenForge: 'https://files.minecraftforge.net/maven/',
    defaultRepoForge: 'https://libraries.minecraft.net/',
    fallbackMaven: 'https://search.maven.org/remotecontent?filepath='
};

const DEFAULT_FW = {
    baseUrl: 'https://github.com/ZekerZhayard/ForgeWrapper/releases/download/',
    version: '1.6.0',
    sh1: '035a51fe6439792a61507630d89382f621da0f1f',
    size: 28679
};

const launcher = new MCLCore.Client();

async function prepareDownload(userOptions) {
    launcher.removeAllListeners();
    launcher.on('progress', (e) => process.send({ type: 'progress', data: e }));
    launcher.on('download-status', (e) => process.send({ type: 'download-status', data: e }));
    launcher.on('debug', (e) => process.send({ type: 'debug', message: String(e) }));

    launcher.options = { ...userOptions };
    launcher.options.root = path.resolve(launcher.options.root);
    launcher.options.overrides = {
        detached: true,
        ...launcher.options.overrides,
        url: { ...DEFAULT_URLS, ...(launcher.options.overrides && launcher.options.overrides.url) },
        fw: { ...DEFAULT_FW, ...(launcher.options.overrides && launcher.options.overrides.fw) }
    };

    launcher.handler = new Handler(launcher);
    launcher.createRootDirectory();
    launcher.createGameDirectory();

    const versionId = launcher.options.version.custom || launcher.options.version.number;
    launcher.options.directory = path.join(launcher.options.root, 'versions', versionId);

    // getVersion() reads {directory}/{version.number}.json if present, else pulls
    // the manifest + version json from Mojang. For prepare we always target the
    // flattened Vanilla version so assets/libraries are shared by every engine.
    const versionFile = await launcher.handler.getVersion();
    const number = launcher.options.version.number;

    const mcPath = launcher.options.version.custom
        ? path.join(launcher.options.root, 'versions', launcher.options.version.custom, `${launcher.options.version.custom}.jar`)
        : path.join(launcher.options.directory, `${number}.jar`);
    launcher.options.mcPath = mcPath;

    // Jar FIRST (it's small and both the biggest single file and the most likely
    // to be left half-written if this worker is killed mid-run). Once it's on disk
    // the real launch's jar-integrity check treats any bad file as re-downloadable.
    if (!fs.existsSync(mcPath) || !fs.statSync(mcPath).size) {
        process.send({ type: 'prepare-status', phase: 'jar', label: 'Downloading Minecraft' });
        await launcher.handler.getJar();
    }

    process.send({ type: 'prepare-status', phase: 'natives', label: 'Preparing natives' });
    await launcher.handler.getNatives();

    process.send({ type: 'prepare-status', phase: 'classes', label: 'Pre-downloading libraries' });
    // classJson=null → downloads this.version.libraries (the flattened Vanilla
    // set, which every engine profile inherits).
    await launcher.handler.getClasses(null);

    process.send({ type: 'prepare-status', phase: 'assets', label: 'Pre-downloading assets' });
    await launcher.handler.getAssets();

    process.send({ type: 'prepare-status', phase: 'done', label: 'Ready', versionFile: versionFile.id });
}

process.on('message', async (msg) => {
    if (msg.type !== 'prepare') return;
    try {
        await prepareDownload(msg.options);
        process.send({ type: 'prepare-done', version: msg.options.version.number });
    } catch (err) {
        process.send({ type: 'prepare-error', message: err.message || String(err), stack: err.stack });
    }
});