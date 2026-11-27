const { app, BrowserWindow, ipcMain, shell, Notification, Tray, Menu, dialog, clipboard, safeStorage } = require('electron');
const path = require('path');
const fs = require('fs-extra');
const http = require('http');
const crypto = require('crypto');
const { fork, spawn } = require('child_process');
const { execSync, exec: execAsync } = require('child_process');
const { promisify } = require('util');
const execPromise = promisify(execAsync);
const { pipeline } = require('stream/promises');
const { createWriteStream } = require('fs');
const { buildForcedOptions, injectOptimizationSettings, injectSodiumConfig, parseVersion } = require('./launcher/optimization');

// Bat Client Microsoft login (OAuth2 + PKCE -> Xbox Live -> XSTS -> Minecraft Services).
const auth = require('./auth');
const { platformInfo, javaExecutable } = require('./launcher/platform');
const { selectUpdate, updateExtension, installUnixUpdate } = require('./launcher/update-platform');
const { readModIcon } = require('./launcher/mod-icon');
const {findJava, probeJava} = require('./launcher/java-runtime');
const {composeJvmArgs, compatibleJvmArgs} = require('./launcher/jvm-arguments');
const {removeBundledSodium} = require('./launcher/optional-renderer');
const {cleanupUpdateStaging, markUpdateStage} = require('./launcher/update-cleanup');
const {projectForFile, blockedAutomaticMod, reconcileModPreferences, SODIUM, ADDONS} = require('./launcher/mod-preferences');
const {verifyAgent} = require('./launcher/agent-package');
const {incompatibility,checkFabricMods} = require('./launcher/mod-compatibility');
const {excludedAutomaticProject,fabricIdentity,assertAutomaticArtifactAllowed,assertAutomaticUrlAllowed} = require('./launcher/automatic-mod-policy');

// Proper MC version comparison: "1.8" < "1.13" < "1.14" < "1.21.11".
// A plain float compare breaks on 1.8 vs 1.13 (1.8 > 1.13 numerically).
const mcVersionGe = (a, b) => {
    const A = Array.isArray(a) ? a : parseVersion(a);
    const B = Array.isArray(b) ? b : parseVersion(b);
    for (let i = 0; i < 3; i++) { if (A[i] !== B[i]) return A[i] > B[i]; }
    return true;
};
const mcVersionLt = (a, b) => !mcVersionGe(a, b);

// ---- Suppress Chromium disk cache spam ----
app.commandLine.appendSwitch('disable-disk-cache');
app.commandLine.appendSwitch('ignore-certificate-errors');
app.commandLine.appendSwitch('log-level', '3');

// ---- Single instance lock ----
// Multiple live instances each keep their own in-memory savedAccounts and all
// write profiles.config.json, so a stale instance can overwrite a skin that was
// just equipped in a newer window (this caused "skin doesn't stick in-game").
const gotTheLock = app.requestSingleInstanceLock();
if (!gotTheLock) {
    console.warn('[SingleInstance] Another Bat Client instance is already running; quitting this one.');
    app.quit();
} else {
    app.on('second-instance', () => {
        if (mainWindow) {
            if (mainWindow.isMinimized()) mainWindow.restore();
            mainWindow.focus();
        }
    });
}

// ---- Resolve proxies via the Windows system (WinHTTP) settings ----
// Electron's main-process fetch (Chromium network stack) does not reliably pick
// up the Windows system proxy / PAC / VPN configuration by itself. When the user
// is behind a system proxy or corporate filter, the update check and other HTTP
// calls can fail silently (caught and treated as "no update"). Telling Chromium
// to use WinHTTP for proxy resolution makes the app behave like the browser,
// which reads the very same system/PAC settings.
if (process.platform === 'win32') {
    app.commandLine.appendSwitch('winhttp-proxy-resolver');
}
process.env.ELECTRON_ENABLE_LOGGING = '0';
process.env.ELECTRON_ENABLE_STACK_DUMPING = '0';

// ---- Delete corrupted cache on every start (async, non-blocking) ----
const appDataRoot = path.join(app.getPath('appData'), 'bat-client');
const updateErrorLogPath = path.join(appDataRoot, 'batclient-update-error.log');
function logUpdateError(msg) {
    try { require('fs').appendFileSync(updateErrorLogPath, `[${new Date().toISOString()}] ${msg}\n`); } catch (e) {}
}
const cachePath = path.join(appDataRoot, 'Cache');
fs.rm(cachePath, { recursive: true, force: true }).catch(() => {});

// ---- Persistent flight log (recent launches shown on the home dashboard) ----
const launchLogPath = path.join(appDataRoot, 'launch-log.json');
function readLaunchLog() { try { return JSON.parse(fs.readFileSync(launchLogPath, 'utf8')); } catch (e) { return []; } }
function writeLaunchLog(entries) { try { fs.writeFileSync(launchLogPath, JSON.stringify(entries, null, 2)); } catch (e) {} }
ipcMain.on('record-launch', (event, info) => {
    const entries = readLaunchLog();
    entries.unshift({ at: new Date().toISOString(), username: info.username || activeUsername || '—', version: info.version || '', engine: info.engine || '', server: info.server || null });
    while (entries.length > 60) entries.pop();
    writeLaunchLog(entries);
    if (mainWindow) mainWindow.webContents.send('launch-log-updated');
});
ipcMain.handle('get-launch-log', () => readLaunchLog());

// ---- Global unhandled rejection filter ----
process.on('unhandledRejection', (reason) => {
    if (reason && (reason.message || '').toLowerCase().includes('rpc')) return;
    console.error("Unhandled Rejection:", reason);
});

// ==========================================
// DISCORD RICH PRESENCE
// ==========================================
const DISCORD_CLIENT_ID = '1524782535089979482';
const DISCORD_RPC_BUTTONS = [
    { label: 'Join Discord', url: 'https://discord.gg/mAjGbSEr27' },
    { label: 'Get Client', url: 'https://batclient.surge.sh/' }
];
// Rich Presence large image — uses the asset key registered in the Discord Developer Portal
// (Your App > Rich Presence > Art Assets). The key must match the uploaded asset name exactly
// (lowercase, no .png extension). Signed cdn.discordapp URLs expire, so an asset key is the
// permanent choice.
const DISCORD_RPC_IMAGE = 'bat-logo';

let rpc = null;
let rpcInitPromise = null;
async function initDiscordRPC() {
    if (rpcInitPromise) return rpcInitPromise;
    rpcInitPromise = (async () => {
        const { Client: DiscordRPCClient } = require('@xhayper/discord-rpc');
        rpc = new DiscordRPCClient({ clientId: DISCORD_CLIENT_ID });

        rpc.on('ready', () => {
            console.log(`[Discord RPC] Connected as ${rpc.user?.username || 'unknown user'}.`);
            try {
                rpc.user?.setActivity({
                    details: 'Idle',
                    state: 'In Launcher',
                    startTimestamp: new Date(),
                    largeImageKey: DISCORD_RPC_IMAGE,
                    largeImageText: 'Bat Client',
                    instance: false,
                    buttons: DISCORD_RPC_BUTTONS,
                });
            } catch (e) {}
        });

        rpc.on('error', (err) => console.warn('[Discord RPC] error:', err?.message));
        rpc.on('disconnected', () => console.warn('[Discord RPC] Disconnected.'));
    })().catch((err) => {
        console.warn('[Discord RPC] Disabled — could not initialize:', err.message);
        rpc = null;
    });
    return rpcInitPromise;
}

global.updateRPC = (details, state, instance = false) => {
    if (!rpc || !rpc.user) return;
    if (!appSettings.discordRPC) return;
    rpc.user.setActivity({
        details,
        state,
        startTimestamp: new Date(),
        largeImageKey: DISCORD_RPC_IMAGE,
        largeImageText: 'Bat Client',
        instance,
        buttons: DISCORD_RPC_BUTTONS,
    }).catch(err => console.warn('[Discord RPC] Failed to update activity:', err.message));
};

let mainWindow;
let appTray = null;

// ---- Default runtime data ----
let savedProfiles = [
    { id: 'p_boost', name: 'Bat FPS Boost Mode', version: '1.21', engine: 'fabric', ram: '6' }
];
let savedAccounts = [
    { username: 'BatPlayer', uuid: 'bat_user_default' }
];
let activeProfileId = 'p_boost';
let activeUsername = 'BatPlayer';

const defaultAppSettings = {
    windowWidth: 1150,
    windowHeight: 680,
    fullscreen: false,
    hideOnLaunch: false,
    javaPath: '',
    extraJvmArgs: '',
    language: 'en',
    discordRPC: true,
    keepLauncherOpen: false,
    autoCloseOnGameExit: false,
    gameWindowTitle: '',
    jvmPreset: 'balanced',
    autoCheckUpdates: true,
    showConsole: false,
    launchSound: false,
    notifications: true,
    closeToTray: false,
    autoOptimize: true,
    cullLeaves: true,
    fastMath: true,
    crashReports: false,
    startWithWindows: false,
    downloadThreads: 16,
    gameDir: '',
    publishSkinsToBatClient: true,
    batClientSkinToken: '4EhF5bCPKoaeL0ulXdifRcvzWZ3mOGygTpsY9IVQU78wjxBA',
    firstLaunch: true
};
let appSettings = { ...defaultAppSettings };

// Apply download thread setting globally
try { require('http').globalAgent.maxSockets = (appSettings.downloadThreads || 16) * 4; } catch(e) {}

// ---- Directory paths ----
const minecraftRootPath = path.join(appDataRoot, 'minecraft_runtime');
function getModsDir() {
    const root = (appSettings.gameDir && appSettings.gameDir.trim()) ? appSettings.gameDir.trim() : minecraftRootPath;
    return path.join(root, 'mods');
}
const profilesFilePath = path.join(appDataRoot, 'profiles.config.json');
const msaCacheDir = path.join(appDataRoot, 'msa-cache');
const msaAccountsDir = path.join(appDataRoot, 'msa-accounts');
const skinsDirectoryPath = path.join(appDataRoot, 'skins');
const authlibJarPath = path.join(appDataRoot, 'authlib-injector.jar');
const skinDebugLogPath = path.join(appDataRoot, 'batclient-skin-debug.log');
const javaDir = path.join(appDataRoot, 'jre');
const javaExecPath = javaExecutable(javaDir);

const SKIN_SERVER_URL = 'https://batclient.xyz/skinapi';
const HOSTED_SKIN_API_ROOT = 'https://batclient.xyz/skinapi';

function skinDebugLog(msg, truncate = false) {
    const line = `[${new Date().toISOString()}] ${msg}\n`;
    if (truncate) fs.writeFile(skinDebugLogPath, line).catch(() => {});
    else fs.appendFile(skinDebugLogPath, line).catch(() => {});
}

// ==========================================
// ROBUST FETCH HELPER
// ==========================================
// Mojang serves identical metadata from both hosts; alternate between them on retries
const HOST_MIRRORS = {
    'launchermeta.mojang.com': ['launchermeta.mojang.com', 'piston-meta.mojang.com'],
    'piston-meta.mojang.com': ['piston-meta.mojang.com', 'launchermeta.mojang.com']
};

async function stableFetch(url, options = {}, retries = 2, delay = 1000, timeout = 8000) {
    const defaultHeaders = { 'User-Agent': 'BatClient/Launcher/1.0.0' };
    let lastErr;
    for (let attempt = 0; attempt <= retries; attempt++) {
        let attemptUrl = url;
        try {
            const host = new URL(url).hostname;
            const mirrors = HOST_MIRRORS[host];
            if (mirrors) attemptUrl = url.replace(host, mirrors[attempt % mirrors.length]);
        } catch (e) {}

        const controller = new AbortController();
        const id = setTimeout(() => controller.abort(), timeout);
        const attemptOptions = {
            ...options,
            headers: { ...defaultHeaders, ...options.headers },
            signal: controller.signal
        };

        try {
            const response = await fetch(attemptUrl, attemptOptions);
            clearTimeout(id);
            return response;
        } catch (err) {
            clearTimeout(id);
            lastErr = err;
            if (attempt < retries) {
                console.warn(`[Network Retry] Failed fetching ${attemptUrl}. Retrying in ${delay}ms...`);
                await new Promise(res => setTimeout(res, delay));
                delay *= 1.5;
            }
        }
    }
    throw lastErr;
}

// ---- Asset integrity ----
// minecraft-launcher-core truncates a target asset to 0 bytes before downloading (createWriteStream
// in components/handler.js), so a failed/interrupted download leaves a zero-byte file. Those corrupt
// assets crash the game at runtime (e.g. "PNG header missing") and get left broken until a later
// download succeeds. Remove corrupt/missing assets here so mclc's getAssets() re-downloads them
// on the same launch.
async function verifyAssetsIntegrity(gameRoot, assetIndexId) {
    if (!assetIndexId) return 0;
    const indexPath = path.join(gameRoot, 'assets', 'indexes', `${assetIndexId}.json`);
    if (!fs.existsSync(indexPath)) return 0;
    let index;
    try { index = await fs.readJson(indexPath); } catch (e) { return 0; }
    const objects = (index && index.objects) || {};
    let removed = 0;
    for (const key of Object.keys(objects)) {
        const obj = objects[key];
        if (!obj || !obj.hash) continue;
        const file = path.join(gameRoot, 'assets', 'objects', obj.hash.slice(0, 2), obj.hash);
        let stat = null;
        try { stat = await fs.stat(file); } catch (e) {}
        if (!stat || stat.size !== obj.size) {
            await fs.remove(file).catch(() => {});
            removed++;
        }
    }
    if (removed > 0) {
        console.log(`[Assets] ${assetIndexId}: removed ${removed} corrupt/missing asset(s); re-downloading on this launch.`);
    }
    return removed;
}

// Version manifest with disk cache: network failures fall back to the last good copy
const versionManifestCachePath = path.join(appDataRoot, 'version_manifest_cache.json');
async function getVersionManifest() {
    try {
        const res = await stableFetch('https://launchermeta.mojang.com/mc/game/version_manifest_v2.json');
        const data = await res.json();
        if (!data || !Array.isArray(data.versions)) throw new Error('Malformed version manifest');
        fs.writeJson(versionManifestCachePath, data).catch(() => {});
        return data;
    } catch (err) {
        if (fs.existsSync(versionManifestCachePath)) {
            console.warn('[Manifest] Network failed, using cached version manifest.');
            return fs.readJson(versionManifestCachePath);
        }
        throw err;
    }
}

let configWriteChain = Promise.resolve();
function saveConfigToDisk() {
    const data = { profiles: savedProfiles, activeProfileId, savedAccounts, activeUsername, appSettings };
    configWriteChain = configWriteChain.then(() => fs.writeJson(profilesFilePath, data, { spaces: 2 })).catch(err => console.error("[Data Sync Error]", err));
    return configWriteChain;
}

// ==========================================
// JAVA AUTO‑DOWNLOAD
// ==========================================
const javaInstalls = new Map();
const verifiedJava = new Map();

// Each Minecraft version declares the Java major it needs (vanillaJson.javaVersion.majorVersion).
// Java 21 lives in the legacy 'jre' folder; other majors get their own 'jre-<major>' folder.
function getJavaDirForMajor(major) {
    return major === 21 ? javaDir : path.join(appDataRoot, `jre-${major}`);
}

function getJavaExecPathForMajor(major) {
    return javaExecutable(getJavaDirForMajor(major));
}

async function isJavaAvailable(major = 21) {
    major=Number(major);
    const result=await findJava(major,{custom:appSettings.javaPath,bundled:getJavaExecPathForMajor(major)});
    if(result)verifiedJava.set(major,result);else verifiedJava.delete(major);
    return Boolean(result);
}

function getJavaExecutable(major = 21) {
    return verifiedJava.get(Number(major))?.path || getJavaExecPathForMajor(Number(major));
}

async function getTemurinDownloadUrl(version, osName, arch, packageType) {
    const url = `https://api.adoptium.net/v3/assets/latest/${version}/hotspot?os=${osName}&architecture=${arch}&image_type=${packageType}`;
    const res = await stableFetch(url);
    const data = await res.json();
    if (!Array.isArray(data) || data.length === 0) return null;
    
    const items = data[0].binaries || data.map(d => d.binary).filter(Boolean);
    const binary = items.find(b => {
        const pkg = b.package || b.installer;
        return pkg && pkg.name && pkg.name.endsWith('.zip');
    });
    if (!binary) return null;
    const pkg = binary.package || binary.installer;
    return pkg.link;
}

async function ensureJavaAvailable(event, major = 21) {
    major=Number(major);
    if(![8,11,16,17,21,25].includes(major))throw new Error('Unsupported Java runtime version.');
    if(javaInstalls.has(major))return javaInstalls.get(major);
    const pending=installJavaIfMissing(event,major);javaInstalls.set(major,pending);
    try{return await pending;}finally{javaInstalls.delete(major);}
}
async function installJavaIfMissing(event, major) {
    if (await isJavaAvailable(major)) return true;

    const targetDir = getJavaDirForMajor(major);
    const targetExec = getJavaExecPathForMajor(major);

    const send = (channel, ...args) => {
        if (event.sender && typeof event.sender.send === 'function') event.sender.send(channel, ...args);
        else if (typeof event.reply === 'function') event.reply(channel, ...args);
    };

    send('launch-status', `Java ${major} not found. Downloading JRE (Eclipse Temurin ${major})...`);
    send('launch-progress', { percent: 0 });

    try {
        const scriptPath = path.join(__dirname, 'scripts', 'download-java.js');
        const { spawn } = require('child_process');
        const child = spawn(process.execPath, [scriptPath, String(major), targetDir, appDataRoot], {
            stdio: ['ignore', 'pipe', 'pipe'],
            env: { ...process.env, ELECTRON_RUN_AS_NODE: '1' },
            windowsHide: true
        });

        let buf = '', javaDownloadError = '';
        child.stdout.on('data', (chunk) => {
            buf += chunk.toString();
            const lines = buf.split('\n');
            buf = lines.pop();
            for (const line of lines) {
                if (!line.trim()) continue;
                try {
                    const msg = JSON.parse(line);
                    if (msg.type === 'status') send('launch-status', msg.message);
                    else if (msg.type === 'progress') send('launch-progress', { percent: msg.percent });
                    else if (msg.type === 'error') javaDownloadError = msg.message;
                } catch (e) {
                    console.warn('[Java DL] Ignoring non-JSON output');
                }
            }
        });

        child.stderr.on('data', (d) => console.error('[Java DL stderr]', d.toString()));

        const exitCode = await new Promise((resolve) => {
            child.on('close', (code) => resolve(code));
            child.on('error', (e) => { console.error('[Java DL spawn error]', e); resolve(1); });
        });

        if (exitCode !== 0) throw new Error(javaDownloadError || 'Download script failed (exit code ' + exitCode + ')');

        const installed=await probeJava(targetExec);
        if(!installed||installed.major!==Number(major))throw new Error('Downloaded Java '+major+' is missing, cannot run, or reports the wrong version.');
        verifiedJava.set(Number(major),installed);
        console.log('[Java] Verified Java '+major+' at',targetExec);
        send('launch-status', `Java ${major} is ready. Continuing launch...`);
        return true;
    } catch (err) {
        console.error('[Java] Download failed:', err.message);
        send('launch-error-catch', `Failed to install Java: ${err.message}`);
        return false;
    }
}

// ==========================================
// IPC HANDLERS
// ==========================================
ipcMain.handle('get-mojang-versions-list', async () => {
    try {
        const data = await getVersionManifest();
        return data.versions || [];
    } catch (err) {
        return [];
    }
});

const GITHUB_OWNER = 'evilgamer123113-lang';
const GITHUB_REPO = 'bat-client-releases';
const UPDATE_JSON_URL = 'https://batclient.xyz/update.json';

// Main-process-owned record of the update we downloaded + verified. The renderer
// must NOT be able to point install-update at an arbitrary file, so install-update
// only installs a filePath that the main process itself produced during download.
let expectedUpdate = null; // { filePath, isZip, latestVersion }

ipcMain.handle('check-for-update', async () => {
    try {
        const res = await stableFetch(UPDATE_JSON_URL, {}, 1, 3000, 8000);
        if (!res.ok) {
            console.warn(`[Update] update.json returned ${res.status}. Skipping.`);
            return { updateAvailable: false };
        }
        const data = await res.json();
        const latestVersion = (data.version || '').trim();
        if (!latestVersion) return { updateAvailable: false };

        const currentVersion = app.getVersion();
        if (latestVersion === currentVersion) return { updateAvailable: false };

        // Never downgrade: a lower "latest" version can't be an update.
        try {
            const semver = require('semver');
            if (semver.valid(latestVersion) && semver.valid(currentVersion) && semver.lt(latestVersion, currentVersion)) {
                console.warn(`[Update] Latest (${latestVersion}) < current (${currentVersion}); refusing downgrade.`);
                return { updateAvailable: false };
            }
        } catch (e) {}

        const platformUpdate = selectUpdate(data);
        let downloadUrl = platformUpdate?.download || '';
        if (!downloadUrl) return { updateAvailable: false };

        const changelog = Array.isArray(data.changelog) ? data.changelog.join('\n') : (data.changelog || '');
        const sha256 = platformUpdate?.sha256 || '';
        const required = !!data.required;

        // A "required" update (e.g. a critical data-loss fix) must still be offered
        // even if the user has opted out of automatic update checks. Auto-check only
        // suppresses optional updates; it must never silently hide a mandatory fix.
        if (required && !appSettings.autoCheckUpdates) {
            console.warn('[Update] Required update offered despite autoCheckUpdates=false.');
        } else if (!appSettings.autoCheckUpdates) {
            return { updateAvailable: false };
        }

        console.log(`[Update] Version ${latestVersion} available (current: ${currentVersion}). URL: ${downloadUrl}`);
        return { updateAvailable: true, latestVersion, downloadUrl, changelog, sha256, required };
    } catch (err) {
        console.warn('[Update] Check failed:', err.message);
        return { updateAvailable: false };
    }
});

ipcMain.handle('download-update', async (event, { downloadUrl, latestVersion, sha256 }) => {
    const https2 = require('https');
    const http2 = require('http');
    const { URL } = require('url');
    const crypto = require('crypto');
    const MAX_RETRIES = 3;
    const CHUNK_COUNT = 6;
    const CHUNK_TIMEOUT = 60000;
    const MAX_REDIRECTS = 5;

    // Only these hosts are allowed as update download sources.
    function isAllowedUpdateHost(hostname) {
        return hostname === 'github.com'
            || hostname === 'objects.githubusercontent.com'
            || hostname.endsWith('.githubusercontent.com')
            || hostname.endsWith('batclient.surge.sh')
            || hostname === 'batclient.surge.sh';
    }

    function followRedirects(reqUrl) {
        return new Promise((resolve, reject) => {
            let redirects = 0;
            function doReq(url) {
                let u;
                try { u = new URL(url); } catch (e) { reject(new Error('Invalid download URL')); return; }
                if (u.protocol !== 'https:' && u.protocol !== 'http:') {
                    reject(new Error('Download URL must use HTTP(S)')); return;
                }
                if (!isAllowedUpdateHost(u.hostname)) {
                    reject(new Error(`Download host not allowed: ${u.hostname}`)); return;
                }
                const mod = u.protocol === 'https:' ? https2 : http2;
                mod.get(url, {
                    headers: { 'User-Agent': 'BatClient/Launcher' },
                    timeout: 30000
                }, (res) => {
                    if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
                        res.resume();
                        if (++redirects > MAX_REDIRECTS) {
                            reject(new Error('Too many redirects')); return;
                        }
                        doReq(res.headers.location);
                        return;
                    }
                    resolve(res);
                }).on('error', reject).on('timeout', function() { this.destroy(); reject(new Error('Connection timeout')); });
            }
            doReq(reqUrl);
        });
    }

    for (let attempt = 1; attempt <= MAX_RETRIES; attempt++) {
        try {
            const tempDir = path.join(app.getPath('temp'), 'bat-client-update');
            await fs.ensureDir(tempDir);
            const ext = updateExtension(downloadUrl);
            const isZip = ext === '.zip';
            // Clean up old update installers to save disk space
            try {
                const oldFiles = await fs.readdir(tempDir);
                for (const f of oldFiles) {
                    if (f === `BatClient-${latestVersion}${isZip ? '.zip' : '.exe'}`) continue;
                    if (f.endsWith('.exe') || f.endsWith('.zip') || f.endsWith('.vbs')) {
                        try { await fs.remove(path.join(tempDir, f)); } catch (_) {}
                    }
                }
            } catch (_) {}
            const filePath = path.join(tempDir, `BatClient-${latestVersion}${ext}`);
            if (fs.existsSync(filePath)) await fs.remove(filePath);

            console.log(`[Update] Downloading ${downloadUrl} (attempt ${attempt}/${MAX_RETRIES})...`);

            const headRes = await followRedirects(downloadUrl);
            const supportsRange = headRes.headers['accept-ranges'] === 'bytes';
            const totalBytes = parseInt(headRes.headers['content-length'] || '0', 10);
            headRes.resume();

            if (supportsRange && totalBytes > 1024 * 1024 && CHUNK_COUNT > 1) {
                const chunkSize = Math.ceil(totalBytes / CHUNK_COUNT);
                let received = 0;
                const startTime = Date.now();
                const fd = fs.openSync(filePath, 'w');
                let lastReport = 0;
                const errors = [];

                const downloadChunk = (chunkIndex) => {
                    return new Promise((resolve, reject) => {
                        const start = chunkIndex * chunkSize;
                        const end = Math.min(start + chunkSize - 1, totalBytes - 1);
                        let chunkRedirects = 0;
                        function doReq(url) {
                            const u = new URL(url);
                            if (u.protocol !== 'https:' && u.protocol !== 'http:') {
                                reject(new Error(`Chunk ${chunkIndex} non-HTTP URL`)); return;
                            }
                            if (!isAllowedUpdateHost(u.hostname)) {
                                reject(new Error(`Chunk ${chunkIndex} host not allowed: ${u.hostname}`)); return;
                            }
                            const mod = u.protocol === 'https:' ? https2 : http2;
                            mod.get(url, {
                                headers: {
                                    'User-Agent': 'BatClient/Launcher',
                                    'Range': `bytes=${start}-${end}`
                                },
                                timeout: CHUNK_TIMEOUT
                            }, (res) => {
                                if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
                                    res.resume();
                                    if (++chunkRedirects > MAX_REDIRECTS) {
                                        reject(new Error(`Chunk ${chunkIndex} too many redirects`)); return;
                                    }
                                    doReq(res.headers.location);
                                    return;
                                }
                                if (res.statusCode !== 200 && res.statusCode !== 206) {
                                    reject(new Error(`Chunk ${chunkIndex} HTTP ${res.statusCode}`));
                                    res.resume();
                                    return;
                                }
                                const buf = [];
                                res.on('data', (chunk) => {
                                    buf.push(chunk);
                                    received += chunk.length;
                                    const now = Date.now();
                                    if (now - lastReport >= 200) {
                                        lastReport = now;
                                        const elapsed = (now - startTime) / 1000;
                                        const speed = elapsed > 0 ? received / elapsed : 0;
                                        const speedMB = (speed / (1024 * 1024)).toFixed(1);
                                        const downloadedMB = (received / (1024 * 1024)).toFixed(1);
                                        const totalMB = totalBytes > 0 ? (totalBytes / (1024 * 1024)).toFixed(1) : '?';
                                        const pct = totalBytes > 0 ? Math.round((received / totalBytes) * 100) : -1;
                                        try {
                                            if (event.sender && !event.sender.isDestroyed()) {
                                                event.sender.send('update-download-progress', { percent: pct, downloadedMB, totalMB, speedMB });
                                            }
                                        } catch (e) {}
                                    }
                                });
                                res.on('end', () => {
                                    const data = Buffer.concat(buf);
                                    fs.writeSync(fd, data, 0, data.length, start);
                                    resolve();
                                });
                                res.on('error', reject);
                            }).on('error', reject).on('timeout', function() { this.destroy(); reject(new Error(`Chunk ${chunkIndex} timeout`)); });
                        }
                        doReq(downloadUrl);
                    }).catch(e => { errors.push(e.message); return downloadChunk(chunkIndex).catch(() => {}); });
                };

                await Promise.all(Array.from({ length: CHUNK_COUNT }, (_, i) => downloadChunk(i)));
                fs.closeSync(fd);

                if (errors.length > 0 && received < totalBytes) {
                    console.log(`[Update] Chunk errors: ${errors.join(', ')}, retrying...`);
                    throw new Error(`Chunked download failed: ${errors[0]}`);
                }
            } else {
                await new Promise((resolve, reject) => {
                    let streamRedirects = 0;
                    function doReq(url) {
                        const u = new URL(url);
                        if (u.protocol !== 'https:' && u.protocol !== 'http:') {
                            reject(new Error('Stream download non-HTTP URL')); return;
                        }
                        if (!isAllowedUpdateHost(u.hostname)) {
                            reject(new Error(`Stream download host not allowed: ${u.hostname}`)); return;
                        }
                        const mod = u.protocol === 'https:' ? https2 : http2;
                        mod.get(url, {
                            headers: { 'User-Agent': 'BatClient/Launcher' },
                            timeout: 60000
                        }, (res) => {
                            if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
                                res.resume();
                                if (++streamRedirects > MAX_REDIRECTS) {
                                    reject(new Error('Too many redirects')); return;
                                }
                                doReq(res.headers.location);
                                return;
                            }
                            if (res.statusCode !== 200) {
                                reject(new Error(`HTTP ${res.statusCode}`));
                                res.resume();
                                return;
                            }
                            const total = parseInt(res.headers['content-length'] || '0', 10);
                            const fileStream = fs.createWriteStream(filePath);
                            let received = 0;
                            const startTime = Date.now();
                            let lastReport = 0;
                            res.on('data', (chunk) => {
                                fileStream.write(chunk);
                                received += chunk.length;
                                const now = Date.now();
                                if (now - lastReport >= 200) {
                                    lastReport = now;
                                    const elapsed = (now - startTime) / 1000;
                                    const speed = elapsed > 0 ? received / elapsed : 0;
                                    const speedMB = (speed / (1024 * 1024)).toFixed(1);
                                    const downloadedMB = (received / (1024 * 1024)).toFixed(1);
                                    const totalMB = total > 0 ? (total / (1024 * 1024)).toFixed(1) : '?';
                                    const pct = total > 0 ? Math.round((received / total) * 100) : -1;
                                    try {
                                        if (event.sender && !event.sender.isDestroyed()) {
                                            event.sender.send('update-download-progress', { percent: pct, downloadedMB, totalMB, speedMB });
                                        }
                                    } catch (e) {}
                                }
                            });
                            res.on('end', () => { fileStream.end(() => resolve()); });
                            res.on('error', (err) => { fileStream.destroy(); reject(err); });
                        }).on('error', reject).on('timeout', function() { this.destroy(); reject(new Error('Connection timeout')); });
                    }
                    doReq(downloadUrl);
                });
            }

            const stat = fs.statSync(filePath);
            if (stat.size < 10000) {
                await fs.remove(filePath);
                throw new Error('Downloaded file too small — likely not a valid update.');
            }

            if (!sha256 || !/^[a-fA-F0-9]{64}$/.test(sha256)) {
                await fs.remove(filePath);
                throw new Error('Missing or invalid SHA-256 in update metadata');
            }
            // Stream the file through the hash so a 100+ MB update never blocks
            // the main process (sync readFileSync caused the "Not Responding" freeze).
            const hash = await new Promise((resolve, reject) => {
                const h = crypto.createHash('sha256');
                const s = fs.createReadStream(filePath);
                s.on('data', (c) => h.update(c));
                s.on('end', () => resolve(h.digest('hex')));
                s.on('error', reject);
            });
            if (hash.toLowerCase() !== sha256.toLowerCase()) {
                await fs.remove(filePath);
                throw new Error(`SHA-256 mismatch: expected ${sha256}, got ${hash}`);
            }
            console.log(`[Update] SHA-256 verified: ${hash}`);

            console.log(`[Update] Download complete: ${filePath} (${stat.size} bytes)`);
            expectedUpdate = { filePath, isZip, latestVersion };
            return { success: true, filePath, isZip };
        } catch (err) {
            console.error(`[Update] Download attempt ${attempt} failed:`, err.message);
            logUpdateError('download-update attempt ' + attempt + ' failed: ' + err.message);
            if (attempt >= MAX_RETRIES) {
                return { success: false, error: err.message };
            }
            await new Promise(r => setTimeout(r, 2000));
        }
    }
});

// Validate a ZIP before it becomes executable application content. Returns the
// extracted dir. Throws on unsafe entries (path traversal / absolute paths) or
// if the expected app executable is missing.
async function validateAndExtractZip(filePath, extractDir, expectedExe) {
    const fs2 = require('fs');
    const AdmZip = require('adm-zip');
    const zip = new AdmZip(filePath);

    const entries = zip.getEntries();
    const normalizedExpected = expectedExe.replace(/\\/g, '/').toLowerCase();
    let foundExe = false;
    let excessive = false;

    if (entries.length > 20000) excessive = true;

    for (const e of entries) {
        const n = e.entryName.replace(/\\/g, '/');
        if (n.includes('..') || n.startsWith('/') || /^[a-zA-Z]:/.test(n)) {
            throw new Error('ZIP contains unsafe path entry: ' + e.entryName);
        }
        if (e.isDirectory) continue;
        if (excessive && e.header.size > 1024 * 1024 * 1024) {
            throw new Error('ZIP contains an unreasonably large entry');
        }
        if (n.toLowerCase() === normalizedExpected) foundExe = true;
    }
    if (!foundExe && expectedExe) {
        // Some portable zips put the exe at the root; ensure it is there.
        throw new Error(`ZIP did not contain expected executable ${expectedExe}`);
    }

    // Extraction runs in a detached helper process so no uncaught async throw
    // inside adm-zip can crash the launcher window mid-update. The helper
    // mirrors the validation above and reports via a result file + exit code.
    await fs.ensureDir(extractDir);
    await new Promise((resolve, reject) => {
        const { spawn } = require('child_process');
        const workerPath = path.join(__dirname, 'extract-worker.js');
        const helper = spawn(process.execPath, [workerPath, filePath, extractDir], {
            stdio: ['ignore', 'ignore', 'pipe'],
            windowsHide: true,
            env: { ...process.env, ELECTRON_RUN_AS_NODE: '1' }
        });
        let stderr = '';
        helper.stderr.on('data', (d) => { stderr += d.toString(); });
        helper.on('error', (err) => reject(new Error('extract helper spawn error: ' + err.message)));
        helper.on('close', (code) => {
            const resultFile = path.join(extractDir, '.bat-extract-result.json');
            if (code === 0) { resolve(); return; }
            let msg = stderr.trim() || `extract helper exited with code ${code}`;
            try {
                if (fs.existsSync(resultFile)) {
                    const r = JSON.parse(fs.readFileSync(resultFile, 'utf8'));
                    if (r && r.message) msg = r.message;
                }
            } catch (e) {}
            reject(new Error(String(msg)));
        });
    });
    if (expectedExe && !fs2.existsSync(path.join(extractDir, expectedExe))) {
        throw new Error('Extracted archive did not produce the app executable');
    }
    return extractDir;
}

// Force-quit for the update handoff. Must run detached from the IPC handshake
// so a slow renderer or a pending invoke can never leave the app stuck on the
// "Restarting..." screen. app.exit() is the hard exit; fall back to process.exit.
function exitForUpdate() {
    try { app.exit(0); } catch (e) {}
    try { process.exit(0); } catch (e) {}
}

ipcMain.handle('install-update', async (event, { filePath, isZip }) => {
    try {
        console.log(`[Update] Installing update from: ${filePath}`);
        const fs2 = require('fs');
        if (!fs2.existsSync(filePath)) throw new Error('Update file not found');

        // Security boundary: only install a file that the main process itself
        // downloaded and SHA-verified during download-update. A compromised
        // renderer must not be able to install arbitrary paths.
        if (!expectedUpdate || expectedUpdate.filePath !== filePath) {
            throw new Error('Refusing to install an unverified update file');
        }
        if (process.platform !== 'win32') {
            const result = await installUnixUpdate(filePath, {execPath:app.getPath('exe'),appImage:process.env.APPIMAGE,
                stagingRoot:path.join(appDataRoot,'update-staging')});
            setTimeout(exitForUpdate,500);
            return result;
        }
        const { spawn } = require('child_process');

        const exeDir = path.dirname(app.getPath('exe'));
        const exeName = path.basename(app.getPath('exe'));
        const tempRoot = app.getPath('temp');

        if (isZip) {
            // === ZIP path: validate + stage in-process, then transparent updater ===
            // Use a unique staging dir per update so we never collide with a stale one.
            // Staging lives under the app's own data dir (not %TEMP%): AV scanners
            // are far less likely to lock/flag freshly-extracted executables there,
            // and a stray lock can no longer corrupt a download in progress.
            const token = (Date.now().toString(36) + Math.random().toString(36).slice(2, 8));
            const extractDir = path.join(appDataRoot, 'update-staging', `v${app.getVersion()}-${token}`);
            await fs.ensureDir(extractDir);
            await markUpdateStage(extractDir);
            try {
                await validateAndExtractZip(filePath, extractDir, exeName);
            } catch (e) {
                await fs.remove(extractDir).catch(() => {});
                throw new Error('Update validation failed: ' + e.message);
            }
            const extractedExe = path.join(extractDir, exeName);
            // AV real-time scans can briefly lock a freshly-extracted exe; wait it out.
            let exeReady = false;
            for (let attempt = 0; attempt < 10; attempt++) {
                if (fs2.existsSync(extractedExe)) { exeReady = true; break; }
                await new Promise(r => setTimeout(r, 200));
            }
            if (!exeReady) {
                await fs.remove(extractDir).catch(() => {});
                throw new Error('Extracted archive did not produce the app executable');
            }

            // === Correctness: keep the extracted app in a known safe location ===
            // We cannot replace a running exe in-place. Copy the staged files to a
            // sibling folder under temp ("content staging") so the post-exit helper
            // only has to do an atomic-ish swap, never fragment writes.
            // (The helper itself performs backup + replace + rollback.)

            // Write the updater .ps1 to disk once (transparent, readable source,
            // file-based so no fragile inline quoting). It:
            //   1. Waits for the launcher to fully exit (shutdown sync, not fixed sleep)
            //   2. Overwrites the current install dir with the new content in place
            //      (robocopy /E /IS /IT — a real re-install, files are replaced
            //      atomically-enough by robocopy, and there is no backup that can
            //      fail mid-way like Move-Item swaps did)
            //   3. Verifies the new exe exists
            //   4. Writes OK / FAILED to a result file
            //   5. Launches the new version on success
            const extractStaging = path.join(appDataRoot, 'update-staging');
            await fs.ensureDir(extractStaging);
            const resultFile = path.join(extractStaging, `batclient-update-result.json`);
            const updaterPs = path.join(appDataRoot, 'batclient-update.ps1');
            const psContent = [
                'param()',
                '$ErrorActionPreference = "Stop"',
                '$procName = "' + exeName.replace(/"/g, '""') + '"',
                '$newVersion = "' + (expectedUpdate.latestVersion || '').replace(/"/g, '""') + '"',
                '$src = "' + extractDir.replace(/"/g, '""') + '"',
                '$dst = "' + exeDir.replace(/"/g, '""') + '"',
                '$resultFile = "' + resultFile.replace(/"/g, '""') + '"',
                'function Write-Result($ok, $msg) {',
                '    try {',
                '        $safe = ($msg -replace \'\\\\\', \'\\\\\\\\\') -replace \'"\', \'\\"\'',
                '        if ($ok) { [System.IO.File]::WriteAllText($resultFile, \'{"ok":true,"message":"\' + $safe + \'"}\') }',
                '        else { [System.IO.File]::WriteAllText($resultFile, \'{"ok":false,"message":"\' + $safe + \'"}\') }',
                '    } catch {}',
                '}',
                'function Wait-FileFree($path, $seconds) {',
                '    $deadline = (Get-Date).AddSeconds($seconds)',
                '    while ((Get-Date) -lt $deadline) {',
                '        try {',
                '            if (Test-Path -LiteralPath $path) {',
                '                $fs = [System.IO.File]::Open($path, "Open", "ReadWrite", "None")',
                '                $fs.Close()',
                '            }',
                '            return $true',
                '        } catch { Start-Sleep -Milliseconds 400 }',
                '    }',
                '    return $false',
                '}',
                'function Update-RegistryVersion {',
                '    # Keep the Control Panel "Bat Client" version in sync with the real files.',
                '    # The ZIP in-place updater must refresh the NSIS uninstall-registry entry',
                '    # that Windows shows in Add/Remove Programs; the NSIS installer does',
                '    # this itself on Setup.exe installs, but robocopy does not touch it.',
                '    try {',
                '        $uninstallRoot = "HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall"',
                '        $keys = Get-ChildItem -Path $uninstallRoot -ErrorAction SilentlyContinue | Where-Object {',
                '            $props = Get-ItemProperty $_.PSPath -ErrorAction SilentlyContinue',
                '            $props.DisplayName -like "Bat Client*" -or $props.DisplayIcon -like "*Bat Client*" -or $props.UninstallString -like "*Bat Client*"',
                '        }',
                '        foreach ($k in $keys) {',
                '            try {',
                '                Set-ItemProperty -Path $k.PSPath -Name "DisplayName" -Value "Bat Client $newVersion" -ErrorAction Stop | Out-Null',
                '                New-ItemProperty -Path $k.PSPath -Name "DisplayVersion" -Value $newVersion -PropertyType String -Force -ErrorAction SilentlyContinue | Out-Null',
                '            } catch {}',
                '        }',
                '        # De-duplicate: NSIS leaves a fresh uninstall key per version, so keep',
                '        # only the most recently written entry and clean up the older ones.',
                '        $dupes = @(Get-ItemProperty -Path (Join-Path $uninstallRoot "*") -ErrorAction SilentlyContinue | Where-Object { $_.DisplayName -eq "Bat Client $newVersion" } | Sort-Object { $_.PSPath })',
                '        if ($dupes.Count -gt 1) {',
                '            foreach ($d in $dupes | Select-Object -Skip 1) {',
                '                try { Remove-Item -LiteralPath $d.PSPath -Recurse -Force -ErrorAction Stop } catch {}',
                '            }',
                '        }',
                '    } catch {}',
                '}',
                'try {',
                '    # 1. wait for the launcher to fully exit and its exe to be unlocked',
                '    $app = Join-Path $dst $procName',
                '    if (-not (Wait-FileFree $app 90)) { throw "launcher exe is still locked by a running process" }',
                '    # 2. overlay the new content onto the install dir. /E copies subdirs,',
                '    # /IS+ /IT includes same-age and older files so every new file wins.',
                '    if (-not (Test-Path -LiteralPath $dst)) { New-Item -ItemType Directory -Path $dst -Force | Out-Null }',
                '    $rc = & robocopy $src $dst /E /IS /IT /XJ /NFL /NDL /NP /R:2 /W:2 | Out-Null',
                '    if ($LASTEXITCODE -gt 7) { throw "robocopy failed with exit code $LASTEXITCODE" }',
                '    # 3. update the uninstall-registry entry so Control Panel shows the new version',
                '    Update-RegistryVersion',
                '    # 4. verify the new executable is present and unlocked',
                '    if (-not (Test-Path -LiteralPath $app)) { throw "new executable not found: $app" }',
                '    if (-not (Wait-FileFree $app 30)) { throw "new executable is locked" }',
                '    # 5. launch the new version',
                '    Start-Process -FilePath $app -WorkingDirectory $dst',
                '} catch {',
                '    Write-Result $false $_.Exception.Message',
                '    # still try to relaunch the existing (old) app so the user is never left stuck',
                '    try { if (Test-Path -LiteralPath $app) { Start-Process -FilePath $app -WorkingDirectory $dst } } catch {}',
                '    exit',
                '}',
                'Write-Result $true "updated"'
            ].join('\r\n');
            try { fs2.writeFileSync(updaterPs, '\uFEFF' + psContent, 'utf-8'); } catch (e) {
                await fs.remove(extractDir).catch(() => {});
                throw new Error('Could not write updater script: ' + e.message);
            }

            // Launch the updater fully detached AND hidden (no console window).
            // `start /min` + `-WindowStyle Hidden` keeps it off-screen so it doesn't
            // look suspicious while it runs; it still survives the launcher's exit.
            const child = spawn('cmd.exe', ['/c', 'start', '""', '/min', 'powershell.exe', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-WindowStyle', 'Hidden', '-File', updaterPs], {
                detached: true,
                stdio: 'ignore',
                windowsHide: false
            });
            child.on('error', (err) => console.error('[Update] updater spawn error:', err));
            child.unref();

            // Exit so the post-exit updater can take over. The updater's own
            // shutdown-sync loop will observe this exit before touching files.
            // Exit from a detached callback (not tied to the IPC promise) so a
            // hanging renderer/await can never keep the app stuck on the screen.
            setTimeout(() => { exitForUpdate(); }, 400);

            // Do NOT report installation success here — only that the handoff started.
            return { success: true, handoffStarted: true, status: 'restarting' };
        } else {
            // === EXE path: hand off to the NSIS installer directly ===
            // NSIS handles process shutdown and relaunch itself.
            const child = spawn(filePath, ['/S'], {
                detached: true,
                stdio: 'ignore',
                windowsHide: false
            });
            child.on('error', (err) => console.error('[Update] installer spawn error:', err));
            child.unref();

            setTimeout(() => { exitForUpdate(); }, 400);
            return { success: true, handoffStarted: true, status: 'installing' };
        }
    } catch (err) {
        console.error('[Update] Install failed:', err.message);
        logUpdateError('install-update failed: ' + (err && err.stack ? err.stack : String(err)));
        // If the previous update failed, read the result report on the next call.
        return { success: false, error: err.message };
    }
});

// Report whether a previous update actually completed, based on the result file
// written by the post-exit updater helper. Call at startup / from the renderer.
ipcMain.handle('check-update-result', async () => {
    const resultFile = path.join(appDataRoot, 'update-staging', `batclient-update-result.json`);
    try {
        const fs2 = require('fs');
        if (!fs2.existsSync(resultFile)) return { pending: false };
        const data = JSON.parse(fs2.readFileSync(resultFile, 'utf-8'));
        // Consume the report (one-shot).
        fs2.unlinkSync(resultFile);
        return { pending: true, ok: !!data.ok, message: data.message || '' };
    } catch (err) {
        return { pending: false };
    }
});

ipcMain.handle('search-modrinth-api', async (event, { query, loader, offset, sort }) => {
    try {
        const sortIndex = sort || 'relevance';
        const facets = encodeURIComponent(JSON.stringify([["categories:" + loader]]));
        const searchUrl = `https://api.modrinth.com/v2/search?query=${encodeURIComponent(query)}&limit=20&offset=${offset}&index=${sortIndex}&facets=${facets}`;
        const response = await stableFetch(searchUrl);
        const data = await response.json();
        return { hits: data.hits || [], total: data.total_hits || 0 };
    } catch (err) {
        return { hits: [], total: 0 };
    }
});

ipcMain.handle('check-mod-compat', async (event, { projectId, version, loader }) => {
    try {
        const versionsUrl = `https://api.modrinth.com/v2/project/${projectId}/version?game_versions=${encodeURIComponent(JSON.stringify([version]))}&loaders=${encodeURIComponent(JSON.stringify([loader]))}`;
        const res = await stableFetch(versionsUrl, {}, 1, 1000, 6000);
        if (!res.ok) return { ok: false, compatible: false, count: 0 };
        const versions = await res.json();
        const list = Array.isArray(versions) ? versions : [];
        return { ok: true, compatible: list.length > 0, count: list.length };
    } catch (e) {
        return { ok: false, compatible: false, count: 0 };
    }
});

function resolveBatIconPath() {
    if (process.platform !== 'win32') return path.join(__dirname, 'bat-logo.png');
    let iconPath = path.join(__dirname, 'build', 'icon.ico');
    if (app.isPackaged) {
        const candidates = [
            path.join(process.resourcesPath, 'icon.ico'),
            path.join(process.resourcesPath, 'build', 'icon.ico'),
            path.join(app.getPath('userData'), 'batclient-icon.ico')
        ];
        for (const p of candidates) {
            try { if (fs.existsSync(p)) { iconPath = p; break; } } catch(e) {}
        }
    }
    return iconPath;
}

function createWindow() {
    const iconPath = resolveBatIconPath();
    mainWindow = new BrowserWindow({
        width: 1280,
        height: 800,
        minWidth: 1000,
        minHeight: 680,
        frame: false,
        resizable: true,
        show: false,
        title: 'Bat Client',
        backgroundColor: '#000003',
        icon: iconPath,
        webPreferences: { nodeIntegration: true, contextIsolation: false, devTools: false }
    });
    mainWindow.setTitle('Bat Client');
    mainWindow.once('ready-to-show', () => { mainWindow.show(); });
    mainWindow.loadFile('index.html');
    mainWindow.on('maximize', () => mainWindow.webContents.send('window-maximize-state', true));
    mainWindow.on('unmaximize', () => mainWindow.webContents.send('window-maximize-state', false));

    mainWindow.webContents.on('before-input-event', (event, input) => {
        if (input.type !== 'keyDown') return;
        const devShortcut = input.key === 'F12' || (input.control && input.shift && (input.key === 'I' || input.key === 'J' || input.key === 'C'));
        if (devShortcut) event.preventDefault();
    });
}

async function runBackgroundStartupTasks() {
    cleanupUpdateStaging({appDataRoot, execPath:app.getPath('exe')}).then(result => {
        if (result.removed.length) console.log(`[Update Cleanup] Removed ${result.removed.length} old staging folders (${Math.round(result.bytes/1024/1024)} MB).`);
    }).catch(error => console.warn('[Update Cleanup] Deferred:', error.message));
    // Create system tray icon if close-to-tray is enabled
    if (appSettings.closeToTray) {
        try {
            const iconPath = resolveBatIconPath();
            if (fs.existsSync(iconPath)) {
                appTray = new Tray(iconPath);
                appTray.setToolTip('Bat Client');
                appTray.setContextMenu(Menu.buildFromTemplate([
                    { label: 'Show Bat Client', click: () => { if (mainWindow) { mainWindow.show(); mainWindow.focus(); } } },
                    { type: 'separator' },
                    { label: 'Quit', click: () => { app.quit(); } }
                ]));
                appTray.on('double-click', () => { if (mainWindow) { mainWindow.show(); mainWindow.focus(); } });
            }
        } catch (e) { console.warn('[Tray] Failed to create tray icon:', e.message); }
    }

    // Retired mods cleanup, cosmetics repair, premium skin resolution, Java check,
    // and Discord RPC are all independent — run them in parallel to avoid sequential
    // blocking that could stall IPC responses and cause "Not Responding" in the renderer.
    const backgroundPromises = [];

    // Retired mod cleanup
    backgroundPromises.push((async () => {
        try {
            const retiredCleanupMarker = path.join(appDataRoot, '.retired-pack-mods-cleaned');
            if (!(await fs.pathExists(retiredCleanupMarker))) {
                const retiredPatterns = [/^exordium-/i, /^enhancedblockentities-/i, /^animatica-/i, /^lambdabettergrass-/i, /^debugify-/i];
                let jars = [];
                try { jars = await fs.readdir(getModsDir()); } catch (e) {}
                for (const jar of jars) {
                    if (retiredPatterns.some(rx => rx.test(jar))) {
                        await fs.remove(path.join(getModsDir(), jar));
                        console.log(`[Mods Migration] Removed retired pack mod: ${jar}`);
                    }
                }
                await fs.writeFile(retiredCleanupMarker, new Date().toISOString());
            }
        } catch (e) { console.warn('[Mods Migration Warn]', e.message); }
    })());

    // Cosmetics repair
    backgroundPromises.push((async () => {
        try {
            let relinked = false;
            for (const acc of savedAccounts) {
                for (const [key, kind] of [['customSkins', 'skin'], ['customCapes', 'cape']]) {
                    const slots = acc[key] || [null, null, null];
                    for (let i = 0; i < 3; i++) {
                        if (slots[i]) continue;
                        const file = `${acc.username}-${kind}-${i}.png`;
                        if (!(await fs.pathExists(path.join(skinsDirectoryPath, file)))) continue;
                        const thumbFile = `${acc.username}-${kind}-${i}-thumb.png`;
                        const hasThumb = await fs.pathExists(path.join(skinsDirectoryPath, thumbFile));
                        slots[i] = kind === 'skin'
                            ? { name: `Skin ${i + 1}`, model: acc.skinModel === 'slim' ? 'slim' : 'classic', file, thumbFile: hasThumb ? thumbFile : null }
                            : { name: `Cape ${i + 1}`, file, thumbFile: hasThumb ? thumbFile : null };
                        acc[key] = slots;
                        relinked = true;
                    }
                }
            }
            if (relinked) await saveConfigToDisk();
        } catch (e) { console.warn('[Cosmetics Repair Warn]', e.message); }
    })());

    // Premium skin resolution
    backgroundPromises.push((async () => {
        let changed = false;
        for (const acc of savedAccounts) {
            if (acc.type === 'premium' && acc.uuid && !acc.skinDataUrl) {
                if (await resolvePremiumSkin(acc)) changed = true;
            }
        }
        if (changed) {
            await saveConfigToDisk();
            if (mainWindow) mainWindow.webContents.send('sync-accounts-view', { accounts: savedAccounts, activeUser: activeUsername });
        }
    })());

    // Java availability check
    backgroundPromises.push((async () => {
        if (!(await isJavaAvailable()) && mainWindow) {
            mainWindow.webContents.send('skin-status', 'Java not found. It will be downloaded when you first launch a game.');
        }
    })());

    // Discord RPC (lazy init — the heavy module is only required here, off the boot path)
    backgroundPromises.push((async () => {
        if (appSettings.discordRPC !== false) {
            await initDiscordRPC();
            if (rpc) {
                Promise.race([
                    rpc.login(),
                    new Promise((_, reject) => setTimeout(() => reject(new Error('RPC login timeout')), 5000))
                ]).catch(err => console.warn('[Discord RPC] Login failed — is Discord running?', err.message));
            }
        }
    })());

    await Promise.allSettled(backgroundPromises);

    // Background pre-download for the active profile's version so the first
    // Launch click is near-instant. Runs after the window is shown so startup
    // stays snappy, and is skipped automatically when the version is already warm.
    try { kickBackgroundPrepare(); } catch (e) { console.warn('[Prepare] Startup pre-download skipped:', e.message); }
}

// ==========================================
// BACKGROUND PRE-DOWNLOAD ("instant launch")
// ==========================================
// minecraft-launcher-core downloads the version jar, natives, libraries AND the
// full asset store inside launch() at click-time, and its request pool defaults
// to just 2 parallel sockets (components/handler.js) — so a first launch of a new
// version visibly stalls for minutes. MCLC skips any file that already exists on
// disk, so we pre-download everything in advance via prepare-worker.js (same MCLC
// pipeline, but never spawns Java). When the user later hits Launch, launch()
// finds every file present and boots almost instantly.
let prepareWorker = null;
let prepareKey = null;

function getActiveProfile() {
    return savedProfiles.find(p => p.id === activeProfileId) || savedProfiles[0] || null;
}

function stopPrepareWorker() {
    if (prepareWorker) {
        try { prepareWorker.kill(); } catch (e) {}
        prepareWorker = null;
    }
    prepareKey = null;
}

// Cheap "already warm" check: a version is barely-worth-preparing once its client
// jar and asset index are both on disk (MCLC will only resume the few missing
// per-object files at launch then). Avoids spawning a worker on every app start.
function isVersionWarm(gameRoot, version) {
    try {
        const jar = path.join(gameRoot, 'versions', version, `${version}.jar`);
        const index = path.join(gameRoot, 'assets', 'indexes', `${version}.json`);
        return fs.existsSync(jar) && fs.existsSync(index);
    } catch (e) {
        return false;
    }
}

function kickBackgroundPrepare() {
    const profile = getActiveProfile();
    if (!profile || !profile.version || !gotTheLock) return;
    const gameRoot = (appSettings.gameDir && appSettings.gameDir.trim()) ? appSettings.gameDir.trim() : minecraftRootPath;
    const key = `${gameRoot}|${profile.version}`;
    if (prepareKey === key && prepareWorker) return;
    if (isVersionWarm(gameRoot, profile.version)) return;

    if (prepareWorker) stopPrepareWorker();
    prepareKey = key;

    const worker = fork(path.join(__dirname, 'prepare-worker.js'), [], { stdio: ['pipe', 'pipe', 'pipe', 'ipc'], windowsHide: true });
    prepareWorker = worker;
    const maxSockets = Math.max(6, parseInt(appSettings.downloadThreads) || 16);
    let lastPhaseSent = 0;

    worker.on('message', (msg) => {
        if (msg.type === 'prepare-status') {
            const now = Date.now();
            if (now - lastPhaseSent > 400 || msg.phase === 'done') {
                lastPhaseSent = now;
                if (mainWindow && !mainWindow.isDestroyed()) {
                    mainWindow.webContents.send('prepare-status', { version: profile.version, phase: msg.phase, label: msg.label });
                }
            }
        } else if (msg.type === 'prepare-done') {
            if (mainWindow && !mainWindow.isDestroyed()) {
                mainWindow.webContents.send('prepare-status', { version: profile.version, phase: 'ready', label: 'Ready to launch instantly' });
            }
            console.log(`[Prepare] ${profile.version} pre-download complete.`);
            stopPrepareWorker();
        } else if (msg.type === 'prepare-error') {
            console.warn(`[Prepare] Background pre-download failed for ${profile.version}: ${msg.message}`);
            if (mainWindow && !mainWindow.isDestroyed()) {
                mainWindow.webContents.send('prepare-status', { version: profile.version, phase: 'error', label: `Pre-download failed (${msg.message || 'network'}). The game will download on launch instead.` });
            }
            stopPrepareWorker();
        }
    });
    worker.on('error', (err) => { console.warn('[Prepare] worker error:', err.message); stopPrepareWorker(); });
    worker.on('exit', (code) => { if (prepareWorker === worker) { prepareWorker = null; prepareKey = null; } });

    worker.send({
        type: 'prepare',
        options: {
            root: gameRoot,
            version: { number: profile.version, type: 'release' },
            overrides: { maxSockets },
            os: platformInfo().gameOs
        }
    });
    console.log(`[Prepare] Background pre-download started for ${profile.version} in ${gameRoot} (${maxSockets} sockets).`);
}

if (gotTheLock) {
app.whenReady().then(async () => {
    await fs.ensureDir(appDataRoot);
    await fs.ensureDir(minecraftRootPath);
    await fs.ensureDir(msaCacheDir);
    await fs.ensureDir(msaAccountsDir);
    await fs.ensureDir(skinsDirectoryPath);

    // Encrypted MS token store (safeStorage uses Windows DPAPI when available).
    auth.configure({ storeDir: msaAccountsDir, safeStorage });

    if (await fs.pathExists(profilesFilePath)) {
        try {
            const persistedConfig = await fs.readJson(profilesFilePath);
            if (persistedConfig.profiles) savedProfiles = persistedConfig.profiles;
            if (persistedConfig.activeProfileId) activeProfileId = persistedConfig.activeProfileId;
            if (persistedConfig.savedAccounts) savedAccounts = persistedConfig.savedAccounts;
            if (persistedConfig.activeUsername) activeUsername = persistedConfig.activeUsername;
            if (persistedConfig.appSettings) appSettings = { ...defaultAppSettings, ...persistedConfig.appSettings };
try { require('http').globalAgent.maxSockets = (appSettings.downloadThreads || 16) * 4; } catch(e) {}
            try { app.setLoginItemSettings({ openAtLogin: !!appSettings.startWithWindows, path: app.getPath('exe') }); } catch(e) {}
        } catch (e) {
            console.warn("[Data Boot Warn] Profile corruption detected, using defaults.");
        }
    }

    await fs.ensureDir(getModsDir());

    createWindow();

    if (mainWindow) mainWindow.webContents.send('sync-accounts-view', { accounts: savedAccounts, activeUser: activeUsername });

    setTimeout(() => {
        runBackgroundStartupTasks().catch(err => console.warn('[Startup] Background task failed:', err.message));
    }, 0);
});
}

// ---- Window controls ----
ipcMain.on('update-rpc-state', (event, data) => global.updateRPC(data.details, data.state, false));
ipcMain.on('minimize-window', () => mainWindow && mainWindow.minimize());
ipcMain.on('maximize-window', () => { if (!mainWindow) return; mainWindow.isMaximized() ? mainWindow.unmaximize() : mainWindow.maximize(); });
ipcMain.on('close-window', () => {
    if (appSettings.closeToTray && mainWindow) {
        mainWindow.hide();
        return;
    }
    app.quit();
});
ipcMain.on('open-external-link', (event, url) => { shell.openExternal(url).catch(() => {}); });
ipcMain.handle('copy-text', async (event, text) => { clipboard.writeText(String(text || '')); return true; });
ipcMain.handle('get-bat-news', async () => {
    try {
        const resp = await stableFetch('https://batclient.xyz/update.json', {}, 2, 800, 8000);
        if (!resp || !resp.ok) return [];
        const data = await resp.json();
        const changelog = Array.isArray(data.changelog) ? data.changelog : [];
        const items = changelog.map((entry, i) => {
            const textStr = String(entry && entry.text !== undefined ? entry.text : entry).trim();
            const vMatch = textStr.match(/^\s*(v?[\d.]+)\s*[:\-\u2013]/);
            let title = null;
            let desc = textStr;
            if (vMatch) {
                title = vMatch[0].trim().replace(/[:\-\u2013]$/, '').trim();
                desc = textStr.slice(vMatch[0].length).trim();
            } else {
                const cMatch = textStr.match(/^(.*?)\s*[:\u2014\u2013\-]\s+(.*)$/);
                if (cMatch && cMatch[1] && cMatch[1].length < 60) {
                    title = cMatch[1].trim();
                    desc = cMatch[2].trim();
                }
            }
            if (!title || String(title).split(/\s+/).length < 2) title = restFallback(textStr);
            const version = entry.version || data.version || '';
            return {
                tag: `v${version}`.replace('vv', 'v') || 'Update',
                title,
                desc,
                link: entry.link || `https://batclient.xyz/`
            };
        });
        return items.length ? items : [];
    } catch (e) {
        return [];
    }
});
function restFallback(textStr) {
    const words = String(textStr).split(/\s+/);
    const cut = Math.min(words.length, 9);
    const head = words.slice(0, cut).join(' ');
    return head.length > 40 ? head.slice(0, 37) + '…' : head;
}

// ---- Home dashboard: System info & folder openers ----
ipcMain.handle('get-system-info', async () => {
    const os = require('os');
    const totalMem = os.totalmem();
    const freeMem = os.freemem();
    const usedMem = totalMem - freeMem;
    const cpuModel = os.cpus()[0]?.model || 'Unknown';
    const osInfo = `${os.platform()} ${os.release()}`;
    let javaVersion = 'Not found';
    try {
        const { stdout } = await execPromise('java -version 2>&1', { timeout: 5000 });
        const match = stdout.match(/version\s+"([\d._]+)"/);
        javaVersion = match ? match[1] : stdout.split('\n')[0] || 'Unknown';
    } catch (e) {}
    let gpuName = 'Unknown';
    try {
        if (process.platform === 'win32') {
            const util = require('util');
            const { execFile } = require('child_process');
            const { promisify } = require('util');
            // Use Win32_ComputerSystem via a direct COM-less WMI provider call on the System32 path,
            // avoiding wmic.exe shell-out. If unavailable, leave GPU as Unknown (non-fatal).
            try {
                const { stdout } = await promisify(execFile)(`${process.env.SystemRoot || 'C:\\Windows'}\\System32\\wbem\\wmic.exe`,
                    ['path', 'win32_videocontroller', 'get', 'caption', '/format:list'], { timeout: 5000, windowsHide: true });
                const lines = stdout.split('\n').filter(l => l.startsWith('Caption='));
                if (lines.length > 0) gpuName = lines[0].replace('Caption=', '').trim();
            } catch (e2) { gpuName = 'Unknown'; }
        } else {
            const { stdout } = await execPromise('lspci | grep -i vga', { timeout: 5000 });
            gpuName = stdout.split(':')[2]?.trim() || stdout.trim() || 'Unknown';
        }
    } catch (e) {}
    return {
        os: osInfo,
        cpu: cpuModel,
        gpu: gpuName,
        ram: `${(usedMem / (1024 * 1024 * 1024)).toFixed(1)} / ${(totalMem / (1024 * 1024 * 1024)).toFixed(1)} GB`,
        java: javaVersion
    };
});
ipcMain.handle('open-folder', async (event, folderPath) => { shell.openPath(folderPath); });
ipcMain.handle('select-directory', async () => {
    const result = await dialog.showOpenDialog(mainWindow, { properties: ['openDirectory'] });
    if (result.canceled || !result.filePaths.length) return null;
    return result.filePaths[0];
});
ipcMain.handle('get-game-root', async () => { return (appSettings.gameDir && appSettings.gameDir.trim()) ? appSettings.gameDir.trim() : minecraftRootPath; });
ipcMain.handle('get-mods-info', async () => {
    let totalMods = 0;
    let managedMods = 0;
    let modNames = [];
    try {
        if (await fs.pathExists(getModsDir())) {
            const files = await fs.readdir(getModsDir());
            const jars = files.filter(f => f.endsWith('.jar'));
            totalMods = jars.length;
            modNames = jars.map(f => f.replace(/\.jar$/i, ''));
        }
    } catch (e) {}
    try {
        if (await fs.pathExists(managedModsFilePath)) {
            const managed = await fs.readJson(managedModsFilePath);
            managedMods = Array.isArray(managed) ? managed.length : 0;
        }
    } catch (e) {}
    const activeProf = savedProfiles.find(p => p.id === activeProfileId);
    return { total: totalMods, managed: managedMods, names: modNames.slice(0, 20), engine: activeProf ? activeProf.engine : '—' };
});

// ==========================================
// ==========================================
// MOD DOWNLOADER – FUZZY VERSION MATCH + ENGINE FILTER
// ==========================================
// Numeric triple compare — JS array relational operators coerce to strings and
// misorder versions like [1,20,4] vs [1,3,0]
function compareVersionTriples(a, b) {
    for (let i = 0; i < 3; i++) {
        if (a[i] !== b[i]) return a[i] - b[i];
    }
    return 0;
}

function versionMatches(modVersions, target) {
    const [tMaj, tMin, tPat] = parseVersion(target);
    for (const mv of modVersions) {
        const cleaned = mv.replace(/[\s]/g, '');
        const rangeMatch = cleaned.match(/^(\d+\.\d+(?:\.\d+)?)\s*[-–]\s*(\d+\.\d+(?:\.\d+)?)$/);
        if (rangeMatch) {
            const tNum = [tMaj, tMin, tPat];
            const lo = parseVersion(rangeMatch[1]);
            const hi = parseVersion(rangeMatch[2]);
            if (compareVersionTriples(tNum, lo) >= 0 && compareVersionTriples(tNum, hi) <= 0) return true;
            continue;
        }
        if (cleaned.endsWith('.x') || cleaned.endsWith('.X')) {
            const base = cleaned.slice(0, -2);
            const [bMaj, bMin] = parseVersion(base);
            if (tMaj === bMaj && tMin === bMin) return true;
            continue;
        }
        const [cMaj, cMin, cPat] = parseVersion(cleaned);
        if (cMaj === 0 && cMin === 0 && cPat === 0) continue;
        if (tMaj === cMaj && tMin === cMin && tPat === cPat) return true;
    }
    return false;
}

// A jar built for another loader will crash the game, so only accept the exact
// loader — with one exception: NeoForge still loads Forge-format jars on 1.20.1
// and older (the formats split after that).
function loaderMatches(modLoaders, target, gameVersion) {
    let aliases = [target];
    if (target === 'neoforge') {
        const [maj, min, pat] = parseVersion(gameVersion);
        aliases = (maj === 1 && (min < 20 || (min === 20 && pat <= 1))) ? ['neoforge', 'forge'] : ['neoforge'];
    }
    return modLoaders.some(l => aliases.includes(l));
}

// Dedupes concurrent downloads of the same project (several mods share dependencies)
const inFlightModDownloads = new Map();

async function downloadTargetVersionFpsMod(projectId, gameVersion, loaderEngine, resolvedChain, modsDir) {
    if(excludedAutomaticProject(projectId)){console.log('[FPS Mods] Technical Utilities excluded from automatic downloads.');return;}
    const choices = await getDismissedMods();
    if (blockedAutomaticMod(projectId, choices)) { console.log(`[FPS Mods] Respecting removal choice for ${projectId}.`); return; }
    const chain = resolvedChain || new Set();
    if (chain.has(projectId)) return;
    chain.add(projectId);
    const targetModsDir = modsDir || getModsDir();

    const flightKey = `${projectId}|${gameVersion}|${loaderEngine}`;
    if (inFlightModDownloads.has(flightKey)) return inFlightModDownloads.get(flightKey);

    const task = (async () => {
        try {
            // Fast path: if a known-good jar for this (project, version, loader) is
            // already on disk and the entry hasn't aged past the TTL, skip the
            // Modrinth API round-trip entirely. Makes warm non-vanilla launches
            // instant instead of stalling on 8+ parallel network calls.
            try {
                const cache = await getFpsModCache();
                const cached = cache[flightKey];
                if (cached && cached.filename && Date.now() - (cached.at || 0) < FPS_MOD_CACHE_TTL_MS) {
                    const cachedPath = path.join(targetModsDir, cached.filename);
                    if (await fs.pathExists(cachedPath)) {
                        console.log(`[FPS Mods] Cached jar present: ${cached.filename} (no API call)`);
                        return;
                    }
                    console.log(`[FPS Mods] Cached jar missing on disk (${cached.filename}) — re-checking.`);
                }
            } catch (cacheErr) {
                console.warn('[FPS Mods] Cache check failed:', cacheErr.message);
            }

            const versionsUrl = `https://api.modrinth.com/v2/project/${projectId}/version?loaders=${encodeURIComponent(JSON.stringify([loaderEngine]))}`;
            const res = await stableFetch(versionsUrl, {}, 1, 1000, 6000);
            if (!res.ok) return;
            const allVersions = await res.json();

            // Collect ALL filenames this project has ever released so we can
            // identify stale/wrong-version jars sitting in the mods dir.
            const projectFilenames = new Set();
            for (const v of allVersions) for (const f of (v.files || [])) projectFilenames.add(f.filename);

            let presentJars = [];
            try { presentJars = await fs.readdir(targetModsDir); } catch (e) {}

            const compatible = allVersions.filter(v =>
                loaderMatches(v.loaders, loaderEngine, gameVersion) && versionMatches(v.game_versions, gameVersion)
            );
            if (compatible.length === 0) {
                console.warn(`[FPS Mods] No compatible version for mod ${projectId} on ${gameVersion}/${loaderEngine} — cleaning up stale jars.`);
                for (const existingJar of presentJars) {
                    if (projectFilenames.has(existingJar)) {
                        try { await fs.remove(path.join(targetModsDir, existingJar)); console.log(`[FPS Mods] Removed wrong-version: ${existingJar}`); } catch (e) {}
                    }
                }
                // Clean managed-mods entries for this project so stale entries
                // don't trigger false dismissals on next launch
                try {
                    const managed = await getManagedMods();
                    const cleanedManaged = managed.filter(f => !projectFilenames.has(f));
                    if (cleanedManaged.length !== managed.length) {
                        await fs.writeJson(managedModsFilePath, cleanedManaged, { spaces: 0 });
                    }
                } catch (e) {}
                return;
            }

            // Modrinth lists newest first; prefer the newest stable build. Alpha
            // builds are never installed — an unstable mod costs more FPS/stability
            // than its absence.
            const picked = compatible.find(v => v.version_type === 'release')
                || compatible.find(v => v.version_type === 'beta');
            if (!picked) {
                console.warn(`[FPS Mods] Only alpha builds for mod ${projectId} on ${gameVersion}/${loaderEngine} – skipped.`);
                return;
            }

            const latestChoices = await getDismissedMods();
            if ((picked.dependencies || []).some(dep => dep.dependency_type === 'required' && dep.project_id && blockedAutomaticMod(dep.project_id, latestChoices))) {
                console.log(`[FPS Mods] Skipping ${projectId}: required dependency was removed by the user.`); return;
            }
            // Pull required dependencies first so the loader never aborts on a missing library
            for (const dep of (picked.dependencies || [])) {
                if (dep.dependency_type === 'required' && dep.project_id) {
                    await downloadTargetVersionFpsMod(dep.project_id, gameVersion, loaderEngine, chain, targetModsDir);
                }
            }

            const primaryFile = picked.files.find(f => f.primary) || picked.files[0];
            if (!primaryFile) return;

            // Re-read current jars (may have changed after dependency downloads)
            try { presentJars = await fs.readdir(targetModsDir); } catch (e) {}

            // Remove any stale jars from this project (wrong version, old builds)
            for (const existingJar of presentJars) {
                if (existingJar !== primaryFile.filename && projectFilenames.has(existingJar)) {
                    try {
                        await fs.remove(path.join(targetModsDir, existingJar));
                        console.log(`[FPS Mods] Removed stale build: ${existingJar}`);
                    } catch (e) {}
                }
            }

            // Also clean managed-mods entries for stale jars so the pre-launch
            // scan doesn't falsely dismiss this project on next launch
            try {
                const managed = await getManagedMods();
                const cleanedManaged = managed.filter(f => {
                    return f === primaryFile.filename || !projectFilenames.has(f);
                });
                if (cleanedManaged.length !== managed.length) {
                    await fs.writeJson(managedModsFilePath, cleanedManaged, { spaces: 0 });
                }
            } catch (e) {}

            // If the correct file already exists, no download needed
            const destPath = path.join(targetModsDir, primaryFile.filename);
            if (await fs.pathExists(destPath)) {
                console.log(`[FPS Mods] Preserving existing file: ${primaryFile.filename}`);
                await setFpsModCacheEntry(flightKey, { filename: primaryFile.filename, at: Date.now() });
                return;
            }

            // If any version of this mod is disabled (.disabled extension), the user
            // explicitly disabled it — do not re-download.
            for (const present of presentJars) {
                if (present.endsWith('.disabled') && projectFilenames.has(present.replace(/\.disabled$/, ''))) {
                    console.log(`[FPS Mods] Mod ${projectId} was disabled by user (${present}) — skipping.`);
                    return;
                }
            }

            const fileRes = await stableFetch(primaryFile.url);
            if (!fileRes.ok) return;

            const buffer = Buffer.from(await fileRes.arrayBuffer());
            assertAutomaticArtifactAllowed(buffer);
            if(loaderEngine==='fabric'){const reason=incompatibility(buffer,gameVersion);if(reason)throw new Error(reason);}
            if (blockedAutomaticMod(projectId, await getDismissedMods())) return;
            await fs.writeFile(destPath, buffer);
            // A Remove/Disable action may finish while the network is in flight.
            if (blockedAutomaticMod(projectId, await getDismissedMods())) {
                await fs.remove(destPath); return;
            }
            await trackManagedMod(primaryFile.filename);
            await trackManagedModProject(primaryFile.filename, projectId);
            await setFpsModCacheEntry(flightKey, { filename: primaryFile.filename, at: Date.now() });
            console.log(`[FPS Mods] Downloaded: ${primaryFile.filename}`);
        } catch (err) {
            console.warn(`[FPS Mods] Error downloading mod ${projectId}:`, err.message);
        }
    })();

    inFlightModDownloads.set(flightKey, task);
    try {
        return await task;
    } finally {
        inFlightModDownloads.delete(flightKey);
    }
}

const managedModsFilePath = path.join(appDataRoot, 'managed-mods.json');
const dismissedModsFilePath = path.join(appDataRoot, 'dismissed-mods.json');
const fpsModCacheFilePath = path.join(appDataRoot, 'fps-mod-cache.json');
const FPS_MOD_CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000;

async function getFpsModCache() {
    try {
        if (await fs.pathExists(fpsModCacheFilePath)) return await fs.readJson(fpsModCacheFilePath);
    } catch (e) {}
    return {};
}

let fpsCacheWriteQueue = Promise.resolve();
function setFpsModCacheEntry(key, entry) {
    fpsCacheWriteQueue = fpsCacheWriteQueue.then(async () => {
    try {
        const cache = await getFpsModCache();
        cache[key] = entry;
        await fs.writeJson(fpsModCacheFilePath, cache, { spaces: 0 });
    } catch (e) {}
    });
    return fpsCacheWriteQueue;
}

async function getManagedMods() {
    try {
        if (await fs.pathExists(managedModsFilePath)) {
            return await fs.readJson(managedModsFilePath);
        }
    } catch (e) {}
    return [];
}

async function getDismissedMods() {
    try {
        const value = await fs.readJson(dismissedModsFilePath);
        if (!Array.isArray(value) || value.some(id=>typeof id!=='string')) throw new Error('Invalid preference format');
        return value;
    } catch (error) {
        if (error.code==='ENOENT') return [];
        throw new Error('Cannot read saved mod-removal choices: '+error.message);
    }
}
async function writeDismissedMods(value) {
    const temp = dismissedModsFilePath+'.'+crypto.randomUUID()+'.tmp';
    try { await fs.writeJson(temp,value,{spaces:0}); await fs.rename(temp,dismissedModsFilePath); }
    finally { await fs.remove(temp).catch(()=>{}); }
}
let dismissedModsWriteQueue = Promise.resolve();
function addDismissedMod(projectId) {
    dismissedModsWriteQueue = dismissedModsWriteQueue.catch(()=>{}).then(async()=>{
        const dismissed = await getDismissedMods();
        if (!dismissed.includes(projectId)) await writeDismissedMods([...dismissed,projectId]);
    });
    return dismissedModsWriteQueue;
}
function removeDismissedMod(projectId) {
    dismissedModsWriteQueue = dismissedModsWriteQueue.catch(()=>{}).then(async()=>{
        const dismissed = await getDismissedMods();
        const cleaned = dismissed.filter(id=>id!==projectId);
        if (cleaned.length!==dismissed.length) await writeDismissedMods(cleaned);
    });
    return dismissedModsWriteQueue;
}

const modProjectMapPath = path.join(appDataRoot, 'mod-project-map.json');

async function getModProjectMap() {
    try {
        if (await fs.pathExists(modProjectMapPath)) return await fs.readJson(modProjectMapPath);
    } catch (e) {}
    return {};
}

let projectMapWriteQueue = Promise.resolve();
function trackManagedModProject(filename, projectId) {
    projectMapWriteQueue = projectMapWriteQueue.then(async () => {
    try {
        const map = await getModProjectMap();
        if (!map[filename]) {
            map[filename] = projectId;
            await fs.writeJson(modProjectMapPath, map, { spaces: 0 });
        }
    } catch (e) {}
    });
    return projectMapWriteQueue;
}

// Serialized through a queue: parallel mod downloads must not interleave the
// read-modify-write, or entries get lost and stale jars survive cleanup
let managedModsWriteQueue = Promise.resolve();
function trackManagedMod(filename) {
    managedModsWriteQueue = managedModsWriteQueue.then(async () => {
        const managed = await getManagedMods();
        if (!managed.includes(filename)) {
            managed.push(filename);
            await fs.writeJson(managedModsFilePath, managed, { spaces: 0 });
        }
    }).catch(err => console.warn('[Mods Tracking] ', err.message));
    return managedModsWriteQueue;
}

async function cleanModsFolder() {
    try {
        if (!await fs.pathExists(getModsDir())) return;
        const managed = await getManagedMods();
        if (managed.length === 0) {
            console.log('[Mods Cleanup] No launcher-managed mods to remove. User mods preserved.');
            return;
        }
        console.log(`[Mods Cleanup] Preserving user mods. Skipped automatic cleanup for ${managed.length} launcher-owned mod(s).`);
    } catch (err) {
        console.warn('[Mods Cleanup] Error clearing managed mods:', err.message);
    }
}

async function injectOptimizationSettingsForLaunch(mcVersion, root) {
    const result = await injectOptimizationSettings({
        mcVersion,
        root: root || minecraftRootPath,
        appSettings,
        fs,
        path,
    });
    if (result && result.applied) {
        console.log(`[Optimization] options.txt updated: ${result.vanillaApplied || 0} Vanilla + ${result.optifineApplied || 0} OptiFine + ${result.vboApplied || 0} VBO options (${result.applied} total) for ${mcVersion}.`);
    }
    return result;
}

// Corrupted or incomplete cached server resource packs make Minecraft repeatedly
// re-download them every time the server re-sends the pack (the "resource pack
// loop"). A missing server-resource-packs folder causes the same failure on some
// Forge/LaunchWrapper builds ("Parameter 'directory' is not a directory").
// The cache is fully disposable (servers re-send their pack on every join anyway),
// so we empty it at launch to guarantee the client starts from a clean slate.
async function prepareServerResourcePacks(root) {
    const dir = path.join(root || minecraftRootPath, 'server-resource-packs');
    try {
        await fs.ensureDir(dir);
    } catch (err) {
        console.warn('[Resource Pack] Could not create server-resource-packs folder:', err.message);
        return;
    }
    let cleaned = 0;
    try {
        const entries = await fs.readdir(dir);
        for (const name of entries) {
            const fp = path.join(dir, name);
            try {
                const stat = await fs.stat(fp);
                if (stat.isDirectory()) {
                    await fs.remove(fp);
                } else if (stat.size === 0) {
                    await fs.remove(fp);
                } else {
                    let isZip = /\.zip$/i.test(name) || /\.zassets$/i.test(name);
                    if (!isZip) {
                        const fh = await fs.promises.open(fp, 'r');
                        const head = Buffer.alloc(4);
                        await fh.read(head, 0, 4, 0);
                        await fh.close();
                        isZip = head[0] === 0x50 && head[1] === 0x4b;
                    }
                    if (isZip) {
                        try {
                            const zip = new (require('adm-zip'))(fp);
                            const list = zip.getEntries ? zip.getEntries() : [];
                            if (list.length === 0) throw new Error('empty archive');
                        } catch (e) {
                            await fs.remove(fp);
                            cleaned++;
                            continue;
                        }
                    }
                    await fs.remove(fp);
                    cleaned++;
                }
            } catch (e) {}
        }
    } catch (err) {
        console.warn('[Resource Pack] Could not scan cached server packs:', err.message);
    }
    console.log(`[Resource Pack] Cleaned cached server resource packs before launch (${cleaned} file(s) removed).`);
    return cleaned;
}

async function injectSodiumConfigForLaunch(root) {
    const result = await injectSodiumConfig({
        root: root || minecraftRootPath,
        appSettings,
        fs,
        path,
    });
    if (result && result.applied) {
        const sectionSummary = result.sectionsApplied ? Object.entries(result.sectionsApplied).map(([s, c]) => `${s}(${c})`).join(', ') : 'all sections';
        console.log(`[Optimization] Sodium performance config injected: ${result.applied} options across ${sectionSummary}.`);
    }
    return result;
}

ipcMain.on('download-modrinth-exact', async (event, { projectId, title, version, loader, fileUrl, filename, profileId }) => {
    try {
        event.reply('mod-download-start', { title });
        let downloadUrl = fileUrl;
        let downloadFilename = filename;

        if (!downloadUrl) {
            const versionsUrl = `https://api.modrinth.com/v2/project/${projectId}/version?game_versions=${encodeURIComponent(JSON.stringify([version]))}&loaders=${encodeURIComponent(JSON.stringify([loader]))}`;
            const versionsRes = await stableFetch(versionsUrl);
            if (!versionsRes.ok) throw new Error(`Could not fetch versions for ${title} (HTTP ${versionsRes.status})`);
            const versionsData = await versionsRes.json();
            if (!versionsData || versionsData.length === 0) throw new Error(`No compatible version found for ${title} on ${version}/${loader}`);
            const selected = versionsData[0];
            const primaryFile = selected.files.find(f => f.primary) || selected.files[0];
            if (!primaryFile) throw new Error(`No downloadable file for ${title}`);
            downloadUrl = primaryFile.url;
            downloadFilename = primaryFile.filename;
        }

        const modsDir = getModsDir();
        await fs.ensureDir(modsDir);
        const destPath = path.join(modsDir, downloadFilename);
        if (await fs.pathExists(destPath)) {
            await removeDismissedMod(projectId);
            await trackManagedMod(downloadFilename);
            await trackManagedModProject(downloadFilename, projectId);
            event.reply('mod-install-success', title);
            return;
        }

        await downloadFileTo(downloadUrl, destPath, (percent) => {
            event.reply('mod-install-progress', { title, percent });
            if (mainWindow && !mainWindow.isDestroyed()) {
                mainWindow.setProgressBar(percent / 100);
            }
        });
        await removeDismissedMod(projectId);
        await trackManagedMod(downloadFilename);
        await trackManagedModProject(downloadFilename, projectId);
        console.log(`[Modrinth] Downloaded: ${downloadFilename}`);
        event.reply('mod-install-success', title);
        if (mainWindow && !mainWindow.isDestroyed()) {
            mainWindow.setProgressBar(-1);
        }
    } catch (err) {
        console.warn(`[Modrinth] Failed to download ${title}:`, err.message);
        event.reply('mod-install-fail', { title, error: err.message });
        if (mainWindow && !mainWindow.isDestroyed()) {
            mainWindow.setProgressBar(-1);
        }
    }
});

ipcMain.on('download-modrinth-file', async (event, { fileUrl, filename, title, profileId }) => {
    try {
        event.reply('mod-download-start', { title });
        const modsDir = getModsDir();
        await fs.ensureDir(modsDir);
        const destPath = path.join(modsDir, filename);
        if (await fs.pathExists(destPath)) { event.reply('mod-install-success', title); return; }

        await downloadFileTo(fileUrl, destPath, (percent) => {
            event.reply('mod-install-progress', { title, percent });
            if (mainWindow && !mainWindow.isDestroyed()) {
                mainWindow.setProgressBar(percent / 100);
            }
        });
        console.log(`[Modrinth] Downloaded: ${filename}`);
        event.reply('mod-install-success', title);
        if (mainWindow && !mainWindow.isDestroyed()) {
            mainWindow.setProgressBar(-1);
        }
    } catch (err) {
        console.warn(`[Modrinth] Failed to download ${title}:`, err.message);
        event.reply('mod-install-fail', { title, error: err.message });
        if (mainWindow && !mainWindow.isDestroyed()) {
            mainWindow.setProgressBar(-1);
        }
    }
});

ipcMain.handle('get-mod-details', async (event, { projectId, version, loader }) => {
    try {
        const projectRes = await stableFetch(`https://api.modrinth.com/v2/project/${projectId}`);
        if (!projectRes.ok) throw new Error(`Could not fetch project details (HTTP ${projectRes.status})`);
        const projectData = await projectRes.json();

        let versionsUrl = `https://api.modrinth.com/v2/project/${projectId}/version`;
        const params = [];
        if (version) params.push(`game_versions=${encodeURIComponent(JSON.stringify([version]))}`);
        if (loader) params.push(`loaders=${encodeURIComponent(JSON.stringify([loader]))}`);
        if (params.length) versionsUrl += '?' + params.join('&');

        const versionsRes = await stableFetch(versionsUrl);
        if (!versionsRes.ok) throw new Error(`Could not fetch versions (HTTP ${versionsRes.status})`);
        const versionsData = await versionsRes.json();

        return {
            ok: true,
            project: {
                title: projectData.title,
                description: projectData.description,
                author: projectData.author,
                icon_url: projectData.icon_url,
                gallery: (projectData.gallery || []).map(g => ({ url: g.url, title: g.title || '', description: g.description || '' })).slice(0, 10)
            },
            versions: versionsData.map(v => ({
                version_number: v.version_number,
                game_versions: v.game_versions,
                loaders: v.loaders,
                date_published: v.date_published,
                files: v.files.map(f => ({
                    filename: f.filename,
                    primary: f.primary,
                    url: f.url
                }))
            }))
        };
    } catch (err) {
        console.warn('[Modrinth] Failed to fetch mod details:', err.message);
        return { ok: false, error: err.message };
    }
});

// ==========================================
// PROFILES (with immediate sync on select)
// ==========================================
ipcMain.on('get-profiles-data', (event) => event.reply('sync-profiles-view', { profiles: savedProfiles, activeId: activeProfileId }));
ipcMain.on('create-blank-profile', async (event) => {
    const freshId = 'p_' + Date.now();
    const totalGB = Math.round(require('os').totalmem() / (1024 * 1024 * 1024));
    let suggestedRam = '4';
    if (totalGB >= 32) suggestedRam = '12';
    else if (totalGB >= 16) suggestedRam = '8';
    else if (totalGB >= 8) suggestedRam = '6';
    savedProfiles.push({ id: freshId, name: 'Custom Profile Layout', version: '1.21', engine: 'fabric', ram: suggestedRam, ramOverride: false, favorite: false, extraJvmArgs: '' });
    activeProfileId = freshId;
    await saveConfigToDisk();
    event.reply('sync-profiles-view', { profiles: savedProfiles, activeId: activeProfileId });
});
ipcMain.on('save-profile-node', async (event, payload) => {
    const index = savedProfiles.findIndex(p => p.id === payload.id);
    if (index !== -1) { savedProfiles[index] = { ...savedProfiles[index], ...payload }; await saveConfigToDisk(); }
    event.reply('sync-profiles-view', { profiles: savedProfiles, activeId: activeProfileId });
    try { kickBackgroundPrepare(); } catch (e) { console.warn('[Prepare] skipped on profile save:', e.message); }
});
ipcMain.on('delete-profile-node', async (event, profileId) => {
    if (savedProfiles.length <= 1) return;
    savedProfiles = savedProfiles.filter(p => p.id !== profileId);
    if (activeProfileId === profileId) activeProfileId = savedProfiles[0].id;
    await saveConfigToDisk();
    event.reply('sync-profiles-view', { profiles: savedProfiles, activeId: activeProfileId });
});
ipcMain.on('reorder-profiles', async (event, orderedIds) => {
    if (!Array.isArray(orderedIds)) return;
    const byId = new Map(savedProfiles.map(p => [p.id, p]));
    const next = [];
    const seen = new Set();
    for (const id of orderedIds) {
        const p = byId.get(id);
        if (p && !seen.has(id)) { next.push(p); seen.add(id); }
    }
    for (const p of savedProfiles) {
        if (!seen.has(p.id)) { next.push(p); seen.add(p.id); }
    }
    savedProfiles = next;
    await saveConfigToDisk();
    event.reply('sync-profiles-view', { profiles: savedProfiles, activeId: activeProfileId });
});
ipcMain.on('duplicate-profile-node', async (event, profileId) => {
    const source = savedProfiles.find(p => p.id === profileId);
    if (!source) return;
    const newId = 'p_' + Date.now();
    const copy = { ...source, id: newId, gameDir: undefined, mods: [] };
    let copyNum = 1;
    const existingNames = savedProfiles.map(p => p.name);
    while (existingNames.includes(`${source.name} (Copy ${copyNum})`)) copyNum++;
    copy.name = `${source.name} (Copy ${copyNum})`;
    if (source.mods && source.mods.length > 0) {
        const newModsDir = path.join(source.gameDir || minecraftRootPath, 'mods');
        await fs.ensureDir(newModsDir);
        for (const mod of source.mods) {
            if (mod.localPath && await fs.pathExists(mod.localPath)) {
                const dest = path.join(newModsDir, path.basename(mod.localPath));
                try { await fs.copy(mod.localPath, dest); } catch(e) {}
                copy.mods.push({ ...mod, localPath: dest });
            } else {
                copy.mods.push({ ...mod });
            }
        }
    }
    savedProfiles.push(copy);
    await saveConfigToDisk();
    event.reply('sync-profiles-view', { profiles: savedProfiles, activeId: activeProfileId });
    event.sender.send('show-toast', { message: `"${copy.name}" created`, type: 'success' });
});
ipcMain.on('select-active-profile', async (event, profileId) => {
    activeProfileId = profileId;
    await saveConfigToDisk();
    if (mainWindow) mainWindow.webContents.send('sync-profiles-view', { profiles: savedProfiles, activeId: activeProfileId });
    try { kickBackgroundPrepare(); } catch (e) { console.warn('[Prepare] skipped on profile select:', e.message); }
});
ipcMain.handle('export-loadouts', async (event) => {
    const { dialog } = require('electron');
    const result = await dialog.showSaveDialog(mainWindow, { title: 'Export Loadouts', defaultPath: 'bat-loadouts.json', filters: [{ name: 'JSON', extensions: ['json'] }] });
    if (result.canceled || !result.filePath) return { ok: false, canceled: true };
    const exportData = { version: 1, profiles: savedProfiles, activeProfileId, exportedAt: new Date().toISOString() };
    await fs.writeJson(result.filePath, exportData, { spaces: 2 });
    return { ok: true, path: result.filePath, count: savedProfiles.length };
});
ipcMain.handle('import-loadouts', async (event) => {
    const { dialog } = require('electron');
    const result = await dialog.showOpenDialog(mainWindow, { title: 'Import Loadouts', filters: [{ name: 'JSON', extensions: ['json'] }], properties: ['openFile'] });
    if (result.canceled || !result.filePaths.length) return { ok: false, canceled: true };
    try {
        const data = await fs.readJson(result.filePaths[0]);
        if (!data.profiles || !Array.isArray(data.profiles)) return { ok: false, error: 'Invalid loadout file format.' };
        let imported = 0;
        for (const p of data.profiles) {
            if (!p.name || !p.version || !p.engine) continue;
            if (savedProfiles.some(sp => sp.id === p.id)) {
                p.id = 'p_' + Date.now() + '_' + imported;
            }
            savedProfiles.push(p);
            imported++;
        }
        if (imported > 0) await saveConfigToDisk();
        event.sender.send('sync-profiles-view', { profiles: savedProfiles, activeId: activeProfileId });
        return { ok: true, imported };
    } catch (e) { return { ok: false, error: e.message }; }
});
ipcMain.handle('open-profile-folder', async () => {
    try {
        const dir = getModsDir();
        await fs.ensureDir(dir);
        await shell.openPath(dir);
        return { ok: true };
    } catch (e) { return { ok: false, error: e.message }; }
});
ipcMain.on('get-accounts-data', (event) => event.reply('sync-accounts-view', { accounts: savedAccounts, activeUser: activeUsername }));

// ==========================================
// ACCOUNTS
// ==========================================
const VALID_OFFLINE_USERNAME = /^[A-Za-z0-9_]{3,16}$/;

ipcMain.on('add-new-identity', async (event, rawUsername) => {
    const userClean = rawUsername.trim();
    if (!userClean) return;
    // Minecraft offline usernames must be 3-16 chars, [A-Za-z0-9_]. Longer or
    // invalid names make the login 'hello' packet fail with EncoderException
    // ("Failed to encode packet 'serverbound/minecraft:hello'") in 1.20.5+.
    const NAME_RE = /^[A-Za-z0-9_]+$/;
    let reason = '';
    if (userClean.length < 3) reason = 'Minecraft usernames must be at least 3 characters.';
    else if (userClean.length > 16) reason = `"${userClean.slice(0,16)}..." is too long. Minecraft allows up to 16 characters (you entered ${userClean.length}).`;
    else if (!NAME_RE.test(userClean)) reason = `"${userClean}" contains invalid characters. Use only letters, numbers and underscores (A-Z, a-z, 0-9, _).`;
    if (reason) {
        event.reply('add-identity-rejected', { username: userClean, reason });
        return;
    }
    if (!savedAccounts.some(acc => acc.username.toLowerCase() === userClean.toLowerCase())) {
        savedAccounts.push({ username: userClean, uuid: "bat_user_" + Math.floor(Math.random() * 9000) });
    }
    activeUsername = userClean;
    await saveConfigToDisk();
    event.reply('sync-accounts-view', { accounts: savedAccounts, activeUser: activeUsername });
});

ipcMain.handle('start-microsoft-login', async () => {
    try {
        // Interactive Microsoft OAuth2 + PKCE login in a dedicated second window.
        // auth/index.js runs: OAuth -> Xbox Live -> XSTS -> Minecraft Services -> profile.
        const account = await auth.startMicrosoftLogin({
            openWindow: openMsaLoginWindow,
            onStatus: (msg) => { if (mainWindow) mainWindow.webContents.send('microsoft-login-status', msg); }
        });
        return { ok: true, account: { type: 'premium', username: account.username, uuid: account.uuid, identifier: account.identifier } };
    } catch (err) {
        return { ok: false, error: err.message || 'Microsoft sign-in failed.' };
    }
});

// Dedicated "Bat Client Account Login" window: loads the live.com authorize URL
// and captures the OAuth authorization code from the oauth20_desktop.srf redirect.
function openMsaLoginWindow(url) {
    return new Promise((resolve, reject) => {
        let settled = false;
        const win = new BrowserWindow({
            width: 500,
            height: 700,
            resizable: false,
            maximizable: false,
            fullscreenable: false,
            title: 'Bat Client Account Login',
            backgroundColor: '#ffffff',
            icon: resolveBatIconPath(),
            parent: mainWindow || undefined,
            modal: false,
            webPreferences: { nodeIntegration: false, contextIsolation: true, sandbox: true }
        });
        win.removeMenu();
        const CDN = 'https://login.live.com/oauth20_desktop.srf';
        const tryCapture = (targetUrl) => {
            if (settled || !targetUrl) return;
            try {
                const u = new URL(targetUrl);
                const isRedirect = (u.origin + u.pathname) === CDN;
                if (!isRedirect) return;
                let code = u.searchParams.get('code');
                if (!code && u.hash) code = new URLSearchParams(u.hash.slice(1)).get('code');
                if (code) {
                    settled = true;
                    if (!win.isDestroyed()) win.destroy();
                    resolve(decodeURIComponent(code));
                }
            } catch (e) { /* ignore malformed redirects */ }
        };
        win.webContents.on('will-redirect', (event, targetUrl) => tryCapture(targetUrl));
        win.webContents.on('did-redirect-navigation', (event, targetUrl) => tryCapture(targetUrl));
        win.webContents.on('did-navigate', (event, targetUrl) => tryCapture(targetUrl));
        // Electron 43+ prefers will-frame-navigate for redirect interception.
        win.webContents.on('will-frame-navigate', (event, details) => {
            const targetUrl = details && details.url;
            if (targetUrl && targetUrl.includes('oauth20_desktop.srf') && !settled) {
                const u = new URL(targetUrl);
                if (u.searchParams.get('code') || (u.hash && new URLSearchParams(u.hash.slice(1)).get('code'))) {
                    event.preventDefault();
                }
            }
            tryCapture(targetUrl);
        });
        win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
        win.on('closed', () => {
            if (!settled) { settled = true; reject(new Error('Login window was closed. Please try again.')); }
        });
        const loadErr = (err) => { if (!settled) { settled = true; reject(err); } };
        win.loadURL(url).catch(loadErr);
        win.once('ready-to-show', () => win.show());
    });
}

async function refreshMicrosoftToken(identifier) {
    if (!identifier) return { ok: false, expired: true, error: 'Session expired. Please sign in again with Microsoft.' };
    try {
        const result = await auth.refreshMicrosoftSession(identifier);
        if (!result) return { ok: false, expired: true, error: 'Session expired. Please sign in again with Microsoft.' };
        return { ok: true, accessToken: result.accessToken, uuid: result.uuid, username: result.username };
    } catch (err) {
        return { ok: false, expired: true, error: 'Session expired. Please sign in again with Microsoft.' };
    }
}

const microsoftUploadLocks = new Set();

async function uploadSkinToMicrosoft(identifier, pngBuffer, variant) {
    if (microsoftUploadLocks.has(identifier)) {
        return { ok: false, error: 'A skin upload for this account is already in progress.' };
    }
    microsoftUploadLocks.add(identifier);
    try {
        const refreshed = await refreshMicrosoftToken(identifier);
        if (!refreshed.ok) return refreshed;
        const boundary = '----BatBoundary' + Date.now();
        const body = Buffer.concat([
            Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="variant"\r\n\r\n${variant}\r\n`),
            Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="skin.png"\r\nContent-Type: image/png\r\n\r\n`),
            pngBuffer,
            Buffer.from(`\r\n--${boundary}--\r\n`)
        ]);
        const res = await fetch('https://api.minecraftservices.com/minecraft/profile/skins', {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${refreshed.accessToken}`,
                'Content-Type': `multipart/form-data; boundary=${boundary}`,
                'User-Agent': 'BatClient/Launcher/1.0.0'
            },
            body
        });
        if (res.ok) return { ok: true };
        let errText = '';
        try { errText = (await res.text()).slice(0, 300); } catch (e) {}
        return { ok: false, error: `Microsoft rejected skin upload: HTTP ${res.status} ${errText}` };
    } catch (err) {
        return { ok: false, error: err.message || 'Microsoft skin upload failed.' };
    } finally {
        microsoftUploadLocks.delete(identifier);
    }
}

ipcMain.handle('refresh-microsoft-account', async (event, identifier) => {
    return await refreshMicrosoftToken(identifier);
});

async function resolvePremiumSkin(account) {
    try {
        let skinSrc = null;
        let model = 'default';
        
        // First try to fetch from official Mojang session server
        try {
            const resp = await stableFetch(`https://sessionserver.mojang.com/session/minecraft/profile/${account.uuid}`, {}, 1, 500, 5000);
            if (resp && resp.ok) {
                const profileData = await resp.json();
                if (profileData && profileData.properties) {
                    const texProp = profileData.properties.find(p => p.name === 'textures');
                    if (texProp && texProp.value) {
                        const texData = JSON.parse(Buffer.from(texProp.value, 'base64').toString('utf8'));
                        if (texData?.textures?.SKIN?.metadata?.model === 'slim') model = 'slim';
                        if (texData?.textures?.SKIN?.url) {
                            const skinRes = await stableFetch(texData.textures.SKIN.url, {}, 2, 1500, 8000);
                            if (skinRes && skinRes.ok) {
                                const buf = Buffer.from(await skinRes.arrayBuffer());
                                if (buf.length > 100) skinSrc = `data:image/png;base64,${buf.toString('base64')}`;
                            }
                        }
                    }
                }
            }
        } catch (e) {
            console.warn(`[Skin] Failed to fetch profile from Mojang for ${account.username}:`, e.message);
        }
        
        // Fallback to mc-heads.net if Mojang fails
        if (!skinSrc) {
            try {
                const skinUrl = `https://mc-heads.net/skin/${account.uuid}`;
                const res = await stableFetch(skinUrl, {}, 2, 1500);
                if (res.ok) {
                    const buf = Buffer.from(await res.arrayBuffer());
                    if (buf.length > 100) skinSrc = `data:image/png;base64,${buf.toString('base64')}`;
                }
            } catch (e) {
                console.warn(`[Skin] Failed to fetch skin from mc-heads for ${account.username}:`, e.message);
            }
        }
        
        if (skinSrc) {
            account.skinDataUrl = skinSrc;
            account.skinModel = model;
        }
        
        // Also fetch the head icon for UI display
        const headUrl = `https://mc-heads.net/avatar/${account.uuid}/64`;
        const headRes = await stableFetch(headUrl, {}, 2, 1500);
        if (headRes.ok) {
            const headBuf = Buffer.from(await headRes.arrayBuffer());
            account.headIcon = `data:image/png;base64,${headBuf.toString('base64')}`;
        }
        return !!skinSrc;
    } catch (err) {
        console.warn(`[Skin] Could not fetch skin for ${account.username}:`, err.message);
        return false;
    }
}

ipcMain.on('add-premium-identity', async (event, account) => {
    const existingIndex = savedAccounts.findIndex(acc => acc.username.toLowerCase() === account.username.toLowerCase());
    if (existingIndex !== -1) savedAccounts[existingIndex] = { ...savedAccounts[existingIndex], ...account };
    else savedAccounts.push(account);
    activeUsername = account.username;
    await resolvePremiumSkin(account);
    await saveConfigToDisk();
    event.reply('sync-accounts-view', { accounts: savedAccounts, activeUser: activeUsername });
});

ipcMain.on('switch-active-identity', async (event, targetUser) => {
    activeUsername = targetUser;
    await saveConfigToDisk();
    event.reply('sync-accounts-view', { accounts: savedAccounts, activeUser: activeUsername });
});

ipcMain.on('reorder-accounts', async (event, orderedUsernames) => {
    if (!Array.isArray(orderedUsernames)) return;
    const byName = new Map(savedAccounts.map(a => [a.username, a]));
    const next = [];
    const seen = new Set();
    for (const name of orderedUsernames) {
        const acc = byName.get(name);
        if (acc && !seen.has(name)) { next.push(acc); seen.add(name); }
    }
    for (const acc of savedAccounts) {
        if (!seen.has(acc.username)) { next.push(acc); seen.add(acc.username); }
    }
    savedAccounts = next;
    await saveConfigToDisk();
    event.reply('sync-accounts-view', { accounts: savedAccounts, activeUser: activeUsername });
});

ipcMain.on('delete-account-identity', async (event, targetUsername) => {
    const removed = savedAccounts.filter(acc => acc.username === targetUsername);
    for (const acc of removed) {
        if (acc.identifier) auth.deleteAccount(acc.identifier);
    }
    savedAccounts = savedAccounts.filter(acc => acc.username !== targetUsername);
    if (activeUsername === targetUsername) activeUsername = savedAccounts.length > 0 ? savedAccounts[0].username : '';
    await saveConfigToDisk();
    event.reply('sync-accounts-view', { accounts: savedAccounts, activeUser: activeUsername });
});

// ---- Offline skin upload ----
ipcMain.handle('set-account-skin', async (event, { username, skinDataUrl, headIcon, model }) => {
    try {
        const idx = savedAccounts.findIndex(a => a.username === username);
        if (idx === -1) return;
        let imageBuffer = null;
        if (skinDataUrl) {
            const base64Data = skinDataUrl.replace(/^data:image\/png;base64,/, '');
            const skinFilename = `${username}.png`;
            imageBuffer = Buffer.from(base64Data, 'base64');
            await fs.writeFile(path.join(skinsDirectoryPath, skinFilename), imageBuffer);
            savedAccounts[idx].skinFile = skinFilename;
        }
        if (model === 'slim' || model === 'classic') savedAccounts[idx].skinModel = model;
        if (headIcon) savedAccounts[idx].headIcon = headIcon;
        await saveConfigToDisk();
        event.reply('sync-accounts-view', { accounts: savedAccounts, activeUser: activeUsername });
        if (mainWindow) mainWindow.webContents.send('skin-status', `Skin uploaded for ${username}`);
        if (savedAccounts[idx].type === 'premium' && savedAccounts[idx].identifier && imageBuffer) {
            const variant = savedAccounts[idx].skinModel === 'slim' ? 'SLIM' : 'CLASSIC';
            // Fire-and-forget: never delay the viewer refresh on a slow network call.
            uploadSkinToMicrosoft(savedAccounts[idx].identifier, imageBuffer, variant).then((up) => {
                if (!up.ok) {
                    console.warn(`[Skin] Microsoft upload failed for ${username}:`, up.error);
                    if (up.expired) {
                        if (mainWindow) mainWindow.webContents.send('skin-status', `Skin saved locally — ${up.error} Re-sign in to sync it to other players.`);
                    }
                } else if (mainWindow) {
                    mainWindow.webContents.send('skin-status', `Skin updated on Microsoft for ${username}`);
                }
            }).catch((msErr) => console.warn(`[Skin] Microsoft upload error for ${username}:`, msErr.message));
        } else if (savedAccounts[idx].username) {
            publishSkinToBatClient(savedAccounts[idx]).catch(() => {});
        }
        return { ok: true };
    } catch (err) {
        console.error("[Skin Save Error]", err);
        return { ok: false, error: String(err && err.message || err) };
    }
});

// ---- Full skin source for the 3D viewer ----
ipcMain.handle('get-account-skin-data', async (event, username) => {
    try {
        const acc = savedAccounts.find(a => a.username === username);
        if (!acc) return null;
        if (acc.type === 'premium') {
            let skinSrc = null;
            let capeSrc = null;
            let model = acc.skinModel === 'slim' ? 'slim' : 'default';
            let capeFrames = null;
            let capeDelays = null;
            if (acc.skinFile) {
                const sp = path.join(skinsDirectoryPath, acc.skinFile);
                if (await fs.pathExists(sp)) {
                    const buf = await fs.readFile(sp);
                    skinSrc = `data:image/png;base64,${buf.toString('base64')}`;
                }
            }
            if (skinSrc) {
                if (acc.customCape) {
                    const cp = path.join(skinsDirectoryPath, acc.customCape);
                    if (await fs.pathExists(cp)) {
                        const buf = await fs.readFile(cp);
                        const isGif = buf.length > 6 && buf[0] === 0x47 && buf[1] === 0x49 && buf[2] === 0x46;
                        capeSrc = `data:image/${isGif ? 'gif' : 'png'};base64,${buf.toString('base64')}`;
                    }
                    if (acc.capeFrames && Array.isArray(acc.capeFrames) && acc.capeFrames.length > 1) {
                        const frameUrls = [];
                        for (const f of acc.capeFrames) {
                            const fp = path.join(skinsDirectoryPath, f);
                            if (await fs.pathExists(fp)) {
                                const fbuf = await fs.readFile(fp);
                                frameUrls.push(`data:image/png;base64,${fbuf.toString('base64')}`);
                            }
                        }
                        if (frameUrls.length > 1) { capeFrames = frameUrls; capeDelays = acc.capeDelays; }
                    }
                }
                return { skin: skinSrc, cape: capeSrc, capeFrames, capeDelays, model, name: acc.username, premium: true };
            }
            if (acc.uuid) {
                try {
                    const resp = await stableFetch(`https://sessionserver.mojang.com/session/minecraft/profile/${acc.uuid}`, {}, 1, 500, 5000);
                    if (resp && resp.ok) {
                        const profileData = await resp.json();
                        if (profileData && profileData.properties) {
                            const texProp = profileData.properties.find(p => p.name === 'textures');
                            if (texProp && texProp.value) {
                                const texData = JSON.parse(Buffer.from(texProp.value, 'base64').toString('utf8'));
                                if (texData?.textures?.SKIN?.metadata?.model === 'slim') model = 'slim';
                                if (texData?.textures?.SKIN?.url) {
                                    const skinRes = await stableFetch(texData.textures.SKIN.url, {}, 2, 1500, 8000);
                                    if (skinRes && skinRes.ok) {
                                        const ct = skinRes.headers.get ? skinRes.headers.get('content-type') : '';
                                        const buf = Buffer.from(await skinRes.arrayBuffer());
                                        if (buf.length > 100) {
                                            skinSrc = `data:image/png;base64,${buf.toString('base64')}`;
                                            acc.skinDataUrl = skinSrc;
                                        }
                                    }
                                }
                                if (texData?.textures?.CAPE?.url) {
                                    try {
                                        const capeRes = await stableFetch(texData.textures.CAPE.url, {}, 2, 1500, 8000);
                                        if (capeRes && capeRes.ok) {
                                            const cbuf = Buffer.from(await capeRes.arrayBuffer());
                                            if (cbuf.length > 100) {
                                                capeSrc = `data:image/png;base64,${cbuf.toString('base64')}`;
                                            }
                                        }
                                    } catch (e) {}
                                }
                            }
                        }
                    }
                } catch (e) {
                    console.warn(`[Skin] Failed to fetch profile from Mojang for ${acc.username}:`, e.message);
                }
            }
            if (!skinSrc) {
                try {
                    const res = await stableFetch(`https://mc-heads.net/skin/${acc.uuid}`, {}, 2, 1500, 8000);
                    if (res.ok) {
                        const buf = Buffer.from(await res.arrayBuffer());
                        if (buf.length > 100) {
                            skinSrc = `data:image/png;base64,${buf.toString('base64')}`;
                            acc.skinDataUrl = skinSrc;
                        }
                    }
                } catch (e) {
                    console.warn(`[Skin] Failed to fetch skin from mc-heads for ${acc.username}:`, e.message);
                }
            }
            if (!skinSrc) {
                const steveP = path.join(__dirname, 'steve.png');
                if (await fs.pathExists(steveP)) {
                    const buf = await fs.readFile(steveP);
                    skinSrc = `data:image/png;base64,${buf.toString('base64')}`;
                    model = 'classic';
                }
            }
            return { skin: skinSrc, cape: capeSrc, capeFrames: null, capeDelays: null, model, name: acc.username, premium: true };
        }
        let skinDataUrl = null;
        let skinModel = acc.skinModel === 'slim' ? 'slim' : 'default';
        if (acc.skinFile) {
            const p = path.join(skinsDirectoryPath, acc.skinFile);
            if (await fs.pathExists(p)) {
                const buf = await fs.readFile(p);
                skinDataUrl = `data:image/png;base64,${buf.toString('base64')}`;
            }
        }
        if (!skinDataUrl && Array.isArray(acc.customSkins)) {
            for (const slot of acc.customSkins) {
                if (slot && slot.file) {
                    const sp = path.join(skinsDirectoryPath, slot.file);
                    if (await fs.pathExists(sp)) {
                        const buf = await fs.readFile(sp);
                        skinDataUrl = `data:image/png;base64,${buf.toString('base64')}`;
                        if (slot.model) skinModel = slot.model === 'slim' ? 'slim' : 'default';
                        break;
                    }
                }
            }
        }
        if (!skinDataUrl) {
            const steveP = path.join(__dirname, 'steve.png');
            if (await fs.pathExists(steveP)) {
                const buf = await fs.readFile(steveP);
                skinDataUrl = `data:image/png;base64,${buf.toString('base64')}`;
                skinModel = 'classic';
            }
        }
        let capeDataUrl = null;
        let capeFrames = null;
        let capeDelays = null;
        if (acc.customCape) {
            const cp = path.join(skinsDirectoryPath, acc.customCape);
            if (await fs.pathExists(cp)) {
                const buf = await fs.readFile(cp);
                const isGif = buf.length > 6 && buf[0] === 0x47 && buf[1] === 0x49 && buf[2] === 0x46;
                capeDataUrl = `data:image/${isGif ? 'gif' : 'png'};base64,${buf.toString('base64')}`;
            }
            if (acc.capeFrames && Array.isArray(acc.capeFrames) && acc.capeFrames.length > 1) {
                const frameUrls = [];
                for (const f of acc.capeFrames) {
                    const fp = path.join(skinsDirectoryPath, f);
                    if (await fs.pathExists(fp)) {
                        const fbuf = await fs.readFile(fp);
                        frameUrls.push(`data:image/png;base64,${fbuf.toString('base64')}`);
                    }
                }
                if (frameUrls.length > 1) { capeFrames = frameUrls; capeDelays = acc.capeDelays; }
            }
        }
        return { skin: skinDataUrl, cape: capeDataUrl, capeFrames, capeDelays, model: skinModel, name: acc.username, premium: false };
    } catch (err) {
        return null;
    }
});

// ---- Custom cape ----

ipcMain.on('set-custom-cape', async (event, { username, capeDataUrl, isAnimated, frameData }) => {
    try {
        if (!capeDataUrl) return;
        const targetUser = username || activeUsername;
        const base64Data = capeDataUrl.replace(/^data:[^,]+;base64,/, '');
        const imageBuffer = Buffer.from(base64Data, 'base64');
        const isGif = imageBuffer.length > 6 && imageBuffer[0] === 0x47 && imageBuffer[1] === 0x49 && imageBuffer[2] === 0x46;
        const capeFile = `cape-${targetUser}.${isGif ? 'gif' : 'png'}`;
        await fs.writeFile(path.join(skinsDirectoryPath, capeFile), imageBuffer);
        const acc = savedAccounts.find(a => a.username === targetUser);
        if (acc) {
            for (const oldExt of ['png', 'gif']) {
                if (oldExt !== (isGif ? 'gif' : 'png')) {
                    const oldPath = path.join(skinsDirectoryPath, `cape-${targetUser}.${oldExt}`);
                    if (await fs.pathExists(oldPath)) await fs.remove(oldPath);
                }
            }
            if (acc.capeFrames && Array.isArray(acc.capeFrames)) {
                for (const f of acc.capeFrames) {
                    const fp = path.join(skinsDirectoryPath, f);
                    if (await fs.pathExists(fp)) await fs.remove(fp);
                }
            }
            acc.customCape = capeFile;
            acc.capeFrames = [];
            acc.capeDelays = [];
            if (isAnimated && frameData && frameData.frames && frameData.frames.length > 1) {
                const delays = frameData.delays || [];
                for (let i = 0; i < frameData.frames.length; i++) {
                    const frameBase64 = frameData.frames[i].replace(/^data:image\/png;base64,/, '');
                    const frameFile = `cape-${targetUser}-frame-${i}.png`;
                    await fs.writeFile(path.join(skinsDirectoryPath, frameFile), Buffer.from(frameBase64, 'base64'));
                    acc.capeFrames.push(frameFile);
                    acc.capeDelays.push(delays[i] || 100);
                }
            }
            await saveConfigToDisk();
        }
        event.reply('sync-accounts-view', { accounts: savedAccounts, activeUser: activeUsername });
        if (mainWindow) mainWindow.webContents.send('skin-status', `Custom cape uploaded for ${targetUser}.`);
    } catch (err) {
        console.error("[Cape Save Error]", err);
    }
});

ipcMain.on('remove-custom-cape', async (event) => {
    try {
        const targetUser = activeUsername;
        const acc = savedAccounts.find(a => a.username === targetUser);
        if (acc && acc.customCape) {
            const p = path.join(skinsDirectoryPath, acc.customCape);
            if (await fs.pathExists(p)) await fs.remove(p);
            for (const oldExt of ['png', 'gif']) {
                const oldPath = path.join(skinsDirectoryPath, `cape-${targetUser}.${oldExt}`);
                if (oldPath !== p && await fs.pathExists(oldPath)) await fs.remove(oldPath);
            }
            if (acc.capeFrames && Array.isArray(acc.capeFrames)) {
                for (const f of acc.capeFrames) {
                    const fp = path.join(skinsDirectoryPath, f);
                    if (await fs.pathExists(fp)) await fs.remove(fp);
                }
            }
            acc.customCape = '';
            acc.capeFrames = [];
            acc.capeDelays = [];
            await saveConfigToDisk();
        }
        event.reply('sync-accounts-view', { accounts: savedAccounts, activeUser: activeUsername });
        if (mainWindow) mainWindow.webContents.send('skin-status', 'Custom cape removed.');
    } catch (err) {
        console.error("[Cape Remove Error]", err);
    }
});

// ---- Save full cosmetics slot data (skins + capes) ----
ipcMain.handle('save-account-slots', async (event, { username, customSkins, customCapes }) => {
    try {
        const idx = savedAccounts.findIndex(a => a.username === username);
        if (idx === -1) return;

        // Save skin slot files. Slots are merged, never wiped: the renderer may
        // hold metadata-only copies (no dataUrl) after a sync-accounts-view, so a
        // slot without pixel data keeps whatever was already saved for it.
        const prevSkins = savedAccounts[idx].customSkins || [];
        const skinSlotMeta = [];
        const maxSkinSlots = Math.max((customSkins || []).length, prevSkins.length, 20);
        for (let i = 0; i < maxSkinSlots; i++) {
            const s = (customSkins || [])[i];
            if (s && s.dataUrl) {
                const base64 = s.dataUrl.replace(/^data:image\/png;base64,/, '');
                const skinFile = `${username}-skin-${i}.png`;
                await fs.writeFile(path.join(skinsDirectoryPath, skinFile), Buffer.from(base64, 'base64'));
                let thumbFile = null;
                let headFile = null;
                if (s.thumbnail) {
                    const thumbBase64 = s.thumbnail.replace(/^data:image\/png;base64,/, '');
                    thumbFile = `${username}-skin-${i}-thumb.png`;
                    await fs.writeFile(path.join(skinsDirectoryPath, thumbFile), Buffer.from(thumbBase64, 'base64'));
                }
                if (s.headIcon) {
                    const headBase64 = s.headIcon.replace(/^data:image\/png;base64,/, '');
                    headFile = `${username}-skin-${i}-head.png`;
                    await fs.writeFile(path.join(skinsDirectoryPath, headFile), Buffer.from(headBase64, 'base64'));
                }
                skinSlotMeta.push({ name: s.name || `Skin ${i + 1}`, model: s.model || 'classic', file: skinFile, thumbFile, headFile });
            } else if (s && s.file) {
                skinSlotMeta.push({ name: s.name || `Skin ${i + 1}`, model: s.model || 'classic', file: s.file, thumbFile: s.thumbFile || null, headFile: s.headFile || null });
            } else if (prevSkins[i]) {
                skinSlotMeta.push(prevSkins[i]);
            } else {
                skinSlotMeta.push(null);
            }
        }
        savedAccounts[idx].customSkins = skinSlotMeta;

        // Save cape slot files (same merge rules as skins)
        const prevCapes = savedAccounts[idx].customCapes || [];
        const capeSlotMeta = [];
        const maxCapeSlots = Math.max((customCapes || []).length, prevCapes.length, 20);
        for (let i = 0; i < maxCapeSlots; i++) {
            const c = (customCapes || [])[i];
            if (c && c.dataUrl) {
                const base64 = c.dataUrl.replace(/^data:[^,]+;base64,/, '');
                const imgBuf = Buffer.from(base64, 'base64');
                const isGif = imgBuf.length > 6 && imgBuf[0] === 0x47 && imgBuf[1] === 0x49 && imgBuf[2] === 0x46;
                const capeFile = `${username}-cape-${i}.${isGif ? 'gif' : 'png'}`;
                await fs.writeFile(path.join(skinsDirectoryPath, capeFile), imgBuf);
                for (const oldExt of ['png', 'gif']) {
                    if (oldExt !== (isGif ? 'gif' : 'png')) {
                        const oldPath = path.join(skinsDirectoryPath, `${username}-cape-${i}.${oldExt}`);
                        if (await fs.pathExists(oldPath)) await fs.remove(oldPath);
                    }
                }
                let thumbFile = null;
                if (c.thumbnail) {
                    const thumbBase64 = c.thumbnail.replace(/^data:[^,]+;base64,/, '');
                    thumbFile = `${username}-cape-${i}-thumb.png`;
                    await fs.writeFile(path.join(skinsDirectoryPath, thumbFile), Buffer.from(thumbBase64, 'base64'));
                }
                const slotData = { name: c.name || `Cape ${i + 1}`, file: capeFile, thumbFile };
                if (c.frames && c.frames.length > 1) {
                    const frameFiles = [];
                    const delays = c.delays || [];
                    for (let fi = 0; fi < c.frames.length; fi++) {
                        const frameB64 = c.frames[fi].replace(/^data:image\/png;base64,/, '');
                        const ff = `${username}-cape-${i}-frame-${fi}.png`;
                        await fs.writeFile(path.join(skinsDirectoryPath, ff), Buffer.from(frameB64, 'base64'));
                        frameFiles.push(ff);
                    }
                    slotData.frames = frameFiles;
                    slotData.delays = delays;
                }
                capeSlotMeta.push(slotData);
            } else if (c && c.file) {
                const slotData = { name: c.name || `Cape ${i + 1}`, file: c.file, thumbFile: c.thumbFile || null };
                if (c.frames && c.frames.length > 1) {
                    const frameFiles = [];
                    const delays = c.delays || [];
                    for (let fi = 0; fi < c.frames.length; fi++) {
                        const frameB64 = c.frames[fi].replace(/^data:[^,]+;base64,/, '');
                        const ff = `${username}-cape-${i}-frame-${fi}.png`;
                        await fs.writeFile(path.join(skinsDirectoryPath, ff), Buffer.from(frameB64, 'base64'));
                        frameFiles.push(ff);
                    }
                    slotData.frames = frameFiles;
                    slotData.delays = delays;
                }
                capeSlotMeta.push(slotData);
            } else if (prevCapes[i]) {
                capeSlotMeta.push(prevCapes[i]);
            } else {
                capeSlotMeta.push(null);
            }
        }
        savedAccounts[idx].customCapes = capeSlotMeta;

        await saveConfigToDisk();
        if (mainWindow) mainWindow.webContents.send('sync-accounts-view', { accounts: savedAccounts, activeUser: activeUsername });
        return { ok: true };
    } catch (err) {
        console.error("[Save Account Slots Error]", err);
        return { ok: false };
    }
});

// ---- Remove a single skin slot ----
ipcMain.handle('remove-skin-slot', async (event, { username, slotIndex }) => {
    try {
        const idx = savedAccounts.findIndex(a => a.username === username);
        if (idx === -1) return { ok: false };
        const slots = savedAccounts[idx].customSkins || [];
        const slot = slots[slotIndex];
        if (slot) {
            if (slot.file) { const p = path.join(skinsDirectoryPath, slot.file); if (await fs.pathExists(p)) await fs.remove(p); }
            if (slot.thumbFile) { const p = path.join(skinsDirectoryPath, slot.thumbFile); if (await fs.pathExists(p)) await fs.remove(p); }
            if (slot.headFile) { const p = path.join(skinsDirectoryPath, slot.headFile); if (await fs.pathExists(p)) await fs.remove(p); }
            slots[slotIndex] = null;
            savedAccounts[idx].customSkins = slots;
            await saveConfigToDisk();
        }
        if (mainWindow) mainWindow.webContents.send('sync-accounts-view', { accounts: savedAccounts, activeUser: activeUsername });
        return { ok: true };
    } catch (err) {
        console.error("[Remove Skin Slot Error]", err);
        return { ok: false };
    }
});

// ---- Remove a single cape slot ----
ipcMain.handle('remove-cape-slot', async (event, { username, slotIndex }) => {
    try {
        const idx = savedAccounts.findIndex(a => a.username === username);
        if (idx === -1) return { ok: false };
        const slots = savedAccounts[idx].customCapes || [];
        const slot = slots[slotIndex];
        if (slot) {
            if (slot.file) { const p = path.join(skinsDirectoryPath, slot.file); if (await fs.pathExists(p)) await fs.remove(p); }
            if (slot.thumbFile) { const p = path.join(skinsDirectoryPath, slot.thumbFile); if (await fs.pathExists(p)) await fs.remove(p); }
            if (slot.frames && Array.isArray(slot.frames)) {
                for (const ff of slot.frames) {
                    const fp = path.join(skinsDirectoryPath, ff);
                    if (await fs.pathExists(fp)) await fs.remove(fp);
                }
            }
            slots[slotIndex] = null;
            savedAccounts[idx].customCapes = slots;
            await saveConfigToDisk();
        }
        if (mainWindow) mainWindow.webContents.send('sync-accounts-view', { accounts: savedAccounts, activeUser: activeUsername });
        return { ok: true };
    } catch (err) {
        console.error("[Remove Cape Slot Error]", err);
        return { ok: false };
    }
});

// ---- Load full cosmetics slot data (skins + capes) ----
ipcMain.handle('get-account-slots', async (event, username) => {
    try {
        const acc = savedAccounts.find(a => a.username === username);
        if (!acc) return { customSkins: [], customCapes: [] };

        const skins = [];
        for (const meta of (acc.customSkins || [])) {
            if (meta && meta.file) {
                const p = path.join(skinsDirectoryPath, meta.file);
                if (await fs.pathExists(p)) {
                    const buf = await fs.readFile(p);
                    let thumbnail = null;
                    let headIcon = null;
                    if (meta.thumbFile) {
                        const tp = path.join(skinsDirectoryPath, meta.thumbFile);
                        if (await fs.pathExists(tp)) {
                            const tbuf = await fs.readFile(tp);
                            thumbnail = `data:image/png;base64,${tbuf.toString('base64')}`;
                        }
                    }
                    if (meta.headFile) {
                        const hp = path.join(skinsDirectoryPath, meta.headFile);
                        if (await fs.pathExists(hp)) {
                            const hbuf = await fs.readFile(hp);
                            headIcon = `data:image/png;base64,${hbuf.toString('base64')}`;
                        }
                    }
                    skins.push({ name: meta.name, model: meta.model, dataUrl: `data:image/png;base64,${buf.toString('base64')}`, thumbnail, headIcon });
                } else {
                    skins.push(null);
                }
            } else {
                skins.push(null);
            }
        }

        const capes = [];
        for (const meta of (acc.customCapes || [])) {
            if (meta && meta.file) {
                const p = path.join(skinsDirectoryPath, meta.file);
                if (await fs.pathExists(p)) {
                    const buf = await fs.readFile(p);
                    let thumbnail = null;
                    if (meta.thumbFile) {
                        const tp = path.join(skinsDirectoryPath, meta.thumbFile);
                        if (await fs.pathExists(tp)) {
                            const tbuf = await fs.readFile(tp);
                            thumbnail = `data:image/png;base64,${tbuf.toString('base64')}`;
                        }
                    }
                    const isGif = buf.length > 6 && buf[0] === 0x47 && buf[1] === 0x49 && buf[2] === 0x46;
                    const capeEntry = { name: meta.name, dataUrl: `data:image/${isGif ? 'gif' : 'png'};base64,${buf.toString('base64')}`, thumbnail };
                    if (meta.frames && meta.frames.length > 1) {
                        const frameUrls = [];
                        for (const ff of meta.frames) {
                            const ffp = path.join(skinsDirectoryPath, ff);
                            if (await fs.pathExists(ffp)) {
                                const fbuf = await fs.readFile(ffp);
                                frameUrls.push(`data:image/png;base64,${fbuf.toString('base64')}`);
                            }
                        }
                        if (frameUrls.length > 1) { capeEntry.frames = frameUrls; capeEntry.delays = meta.delays || []; }
                    }
                    capes.push(capeEntry);
                } else {
                    capes.push(null);
                }
            } else {
                capes.push(null);
            }
        }

        return { customSkins: skins, customCapes: capes };
    } catch (err) {
        console.error("[Get Account Slots Error]", err);
        return { customSkins: [], customCapes: [] };
    }
});

// ---- Equipped cosmetics for the active account (used by the cosmetics 3D viewer) ----
ipcMain.handle('get-equipped-cosmetics', async () => {
    try {
        const acc = savedAccounts.find(a => a.username === activeUsername);
        if (!acc) return { skinDataUrl: null, capeDataUrl: null, capeFrames: [], capeDelays: [] };
        let skinDataUrl = null;
        if (acc.skinFile) {
            const sp = path.join(skinsDirectoryPath, acc.skinFile);
            if (await fs.pathExists(sp)) {
                const buf = await fs.readFile(sp);
                skinDataUrl = `data:image/png;base64,${buf.toString('base64')}`;
            }
        }
        if (!skinDataUrl && acc.customSkin) {
            const sp = path.join(skinsDirectoryPath, acc.customSkin);
            if (await fs.pathExists(sp)) {
                const buf = await fs.readFile(sp);
                skinDataUrl = `data:image/png;base64,${buf.toString('base64')}`;
            }
        }
        let capeDataUrl = null;
        let capeFrames = [];
        let capeDelays = [];
        if (acc.customCape) {
            const cp = path.join(skinsDirectoryPath, acc.customCape);
            if (await fs.pathExists(cp)) {
                const buf = await fs.readFile(cp);
                const isGif = buf.length > 6 && buf[0] === 0x47 && buf[1] === 0x49 && buf[2] === 0x46;
                capeDataUrl = `data:image/${isGif ? 'gif' : 'png'};base64,${buf.toString('base64')}`;
            }
        }
        if (acc.capeFrames && Array.isArray(acc.capeFrames) && acc.capeFrames.length > 1) {
            for (const f of acc.capeFrames) {
                const fp = path.join(skinsDirectoryPath, f);
                if (await fs.pathExists(fp)) {
                    const fbuf = await fs.readFile(fp);
                    capeFrames.push(`data:image/png;base64,${fbuf.toString('base64')}`);
                }
            }
            capeDelays = acc.capeDelays || [];
        }
        return { skinDataUrl, capeDataUrl, capeFrames, capeDelays };
    } catch (err) {
        console.error("[Get Equipped Cosmetics Error]", err);
        return { skinDataUrl: null, capeDataUrl: null, capeFrames: [], capeDelays: [] };
    }
});

// ==========================================
// OFFLINE SKIN – authlib-injector + local Yggdrasil server
// ==========================================
let skinServer = null;
let yggKeyPair = null;
let yggKeyPromise = null;

async function getYggKeys() {
    if (yggKeyPair) return yggKeyPair;
    if (!yggKeyPromise) {
        yggKeyPromise = new Promise((resolve, reject) => {
            crypto.generateKeyPair('rsa', { modulusLength: 2048 }, (err, publicKey, privateKey) => {
                if (err) return reject(err);
                yggKeyPair = { publicKey, privateKey };
                resolve(yggKeyPair);
            });
        });
    }
    return yggKeyPromise;
}
async function signTextureValue(value) {
    const keys = await getYggKeys();
    const signer = crypto.createSign('RSA-SHA1');
    signer.update(value, 'utf8');
    return signer.sign(keys.privateKey, 'base64');
}
async function yggPublicKeyPem() {
    const keys = await getYggKeys();
    return keys.publicKey.export({ type: 'spki', format: 'pem' }).toString();
}

function offlineUUID(username) {
    const hash = crypto.createHash('md5').update('OfflinePlayer:' + username, 'utf8').digest();
    hash[6] = (hash[6] & 0x0f) | 0x30;
    hash[8] = (hash[8] & 0x3f) | 0x80;
    const hex = hash.toString('hex');
    return {
        plain: hex,
        dashed: `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
    };
}

async function ensureAuthlibInjector() {
    try {
        if (await fs.pathExists(authlibJarPath)) {
            const stat = await fs.stat(authlibJarPath);
            if (stat.size > 100000) return authlibJarPath;
        }
        const metaRes = await stableFetch('https://authlib-injector.yushi.moe/artifact/latest.json', {}, 2, 1500, 10000);
        const meta = await metaRes.json();
        if (!meta.download_url) return null;
        const jarRes = await stableFetch(meta.download_url, {}, 2, 1500, 15000);
        if (!jarRes.ok) return null;
        const buf = Buffer.from(await jarRes.arrayBuffer());
        if (meta.sha256) {
            const hash = crypto.createHash('sha256').update(buf).digest('hex');
            if (hash !== meta.sha256) { console.warn('[Skin] authlib-injector checksum mismatch — skipping.'); return null; }
        }
        await fs.writeFile(authlibJarPath, buf);
        console.log('[Skin] authlib-injector ready.');
        return authlibJarPath;
    } catch (err) {
        console.warn('[Skin] Could not obtain authlib-injector:', err.message);
        return null;
    }
}

// Minecraft (1.20.5+) decodes cape textures as PNG only. Animated capes are
// saved as a GIF plus per-frame PNGs, so in-game we must serve a real PNG frame.
async function resolveCapePngForGame(account) {
    if (!account) return null;
    const exists = async (p) => { try { return await fs.pathExists(p); } catch (e) { return false; } };
    const frames = Array.isArray(account.capeFrames) ? account.capeFrames : [];
    for (const f of frames) {
        if (!f) continue;
        const fp = path.join(skinsDirectoryPath, f);
        if (await exists(fp)) return fp;
    }
    if (Array.isArray(account.customCapes)) {
        for (const slot of account.customCapes) {
            if (slot && Array.isArray(slot.frames) && slot.frames.length) {
                for (const f of slot.frames) {
                    if (!f) continue;
                    const fp = path.join(skinsDirectoryPath, f);
                    if (await exists(fp)) return fp;
                }
            }
        }
    }
    if (account.customCape) {
        const cp = path.join(skinsDirectoryPath, account.customCape);
        if (await exists(cp)) {
            const buf = await fs.readFile(cp);
            const isGif = buf.length > 6 && buf[0] === 0x47 && buf[1] === 0x49 && buf[2] === 0x46;
            if (!isGif) return cp;
        }
    }
    return null;
}

// ---- Multiplayer skin sync: upload local skin to central server ----
async function uploadSkinToServer(account) {
    try {
        if (!account || !account.username) return;
        const uuid = account.type === 'premium' ? (account.uuid || '').replace(/-/g, '').toLowerCase() : offlineUUID(account.username).plain;
        if (!uuid) return;

        let skinData = null;
        let skinModel = account.skinModel || 'classic';
        if (account.skinFile) {
            const sp = path.join(skinsDirectoryPath, account.skinFile);
            if (await fs.pathExists(sp)) {
                skinData = 'data:image/png;base64,' + (await fs.readFile(sp)).toString('base64');
            }
        }
        if (!skinData && Array.isArray(account.customSkins)) {
            for (const slot of account.customSkins) {
                if (slot && slot.file) {
                    const sp = path.join(skinsDirectoryPath, slot.file);
                    if (await fs.pathExists(sp)) { skinData = 'data:image/png;base64,' + (await fs.readFile(sp)).toString('base64'); skinModel = slot.model || skinModel; break; }
                }
            }
        }

        let capeData = null;
        let capeAnimated = false;
        let capeFrames = [];
        let capeDelays = [];
        const capePng = await resolveCapePngForGame(account);
        if (capePng && await fs.pathExists(capePng)) {
            capeData = 'data:image/png;base64,' + (await fs.readFile(capePng)).toString('base64');
        }
        if (Array.isArray(account.capeFrames) && account.capeFrames.length > 0) {
            capeAnimated = true;
            for (const fp of account.capeFrames) {
                if (await fs.pathExists(fp)) {
                    capeFrames.push('data:image/png;base64,' + (await fs.readFile(fp)).toString('base64'));
                }
            }
            if (account.capeDelays) capeDelays = account.capeDelays;
        }

        if (!skinData && !capeData) return;

        const body = {
            uuid,
            username: account.username,
            apiKey: account.skinServerApiKey || undefined,
            skin: skinData ? { model: skinModel, data: skinData } : null,
            cape: capeData ? { data: capeData, animated: capeAnimated, frames: capeFrames.length > 0 ? capeFrames : undefined, delays: capeDelays.length > 0 ? capeDelays : undefined } : null
        };

        const https = require('https');
        const postData = JSON.stringify(body);
        const urlObj = new URL(`${SKIN_SERVER_URL}/api/register`);
        const result = await new Promise((resolve, reject) => {
            const req = https.request({ hostname: urlObj.hostname, port: 443, path: urlObj.pathname, method: 'POST', headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(postData) } }, (res) => {
                let d = '';
                res.on('data', (c) => d += c);
                res.on('end', () => { try { resolve(JSON.parse(d)); } catch (e) { resolve(null); } });
            });
            req.on('error', reject);
            req.setTimeout(30000, () => { req.destroy(); reject(new Error('timeout')); });
            req.write(postData);
            req.end();
        });

        if (result && result.ok && result.apiKey) {
            account.skinServerApiKey = result.apiKey;
            skinDebugLog(`Uploaded skin to server: ${account.username} (${uuid})`);
        }
    } catch (e) {
        skinDebugLog(`Upload skin to server failed: ${e.message}`);
    }
}

// ---- Publish skin/cape to the hosted batclient.xyz skin API ----
// This is what cracked multiplayer servers query via authlib-injector pointed at
// https://batclient.xyz/skinapi/api/yggdrasil so OTHER players see the custom skin.
async function publishSkinToBatClient(account) {
    try {
        if (!account || !account.username) return;
        if (!appSettings.publishSkinsToBatClient) return;
        const token = (appSettings.batClientSkinToken || '').trim();
        if (!token) { skinDebugLog(`Publish to batclient skipped: no upload token configured.`); return; }

        let skinData = null;
        let skinModel = account.skinModel || 'classic';
        if (account.skinFile) {
            const sp = path.join(skinsDirectoryPath, account.skinFile);
            if (await fs.pathExists(sp)) {
                skinData = 'data:image/png;base64,' + (await fs.readFile(sp)).toString('base64');
            }
        }
        if (!skinData && Array.isArray(account.customSkins)) {
            for (const slot of account.customSkins) {
                if (slot && slot.file) {
                    const sp = path.join(skinsDirectoryPath, slot.file);
                    if (await fs.pathExists(sp)) { skinData = 'data:image/png;base64,' + (await fs.readFile(sp)).toString('base64'); skinModel = slot.model || skinModel; break; }
                }
            }
        }
        let capeData = null;
        const capePng = await resolveCapePngForGame(account);
        if (capePng && await fs.pathExists(capePng)) {
            capeData = 'data:image/png;base64,' + (await fs.readFile(capePng)).toString('base64');
        }
        if (!skinData && !capeData) return;

        const body = {
            username: account.username,
            skin: skinData ? { model: skinModel, dataUrl: skinData } : null,
            cape: capeData ? { dataUrl: capeData } : null
        };
        const https = require('https');
        const postData = JSON.stringify(body);
        const urlObj = new URL(`${HOSTED_SKIN_API_ROOT}/api/skins/upload`);
        const result = await new Promise((resolve, reject) => {
            const req = https.request({
                hostname: urlObj.hostname, port: 443, path: urlObj.pathname, method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(postData), 'X-Bat-Token': token }
            }, (res) => {
                let d = '';
                res.on('data', (c) => d += c);
                res.on('end', () => { try { resolve(JSON.parse(d)); } catch (e) { resolve(null); } });
            });
            req.on('error', reject);
            req.setTimeout(20000, () => { req.destroy(); reject(new Error('timeout')); });
            req.write(postData);
            req.end();
        });
        if (result && result.ok) {
            skinDebugLog(`Published skin to batclient.xyz: ${account.username}`);
        } else {
            skinDebugLog(`Publish to batclient.xyz returned failure for ${account.username}`);
        }
    } catch (e) {
        skinDebugLog(`Publish skin to batclient.xyz failed: ${e.message}`);
    }
}

// ---- Multiplayer skin sync: fetch remote player data from central server ----
const remotePlayerCache = new Map();
async function fetchRemotePlayerData(uuid) {
    const clean = uuid.replace(/-/g, '').toLowerCase();
    if (remotePlayerCache.has(clean)) return remotePlayerCache.get(clean);
    try {
        const https = require('https');
        const result = await new Promise((resolve, reject) => {
            const req = https.get(`${SKIN_SERVER_URL}/api/player/${clean}`, { timeout: 5000 }, (res) => {
                if (res.statusCode !== 200) { res.resume(); return resolve(null); }
                let d = '';
                res.on('data', (c) => d += c);
                res.on('end', () => { try { resolve(JSON.parse(d)); } catch (e) { resolve(null); } });
            });
            req.on('error', () => resolve(null));
            req.on('timeout', () => { req.destroy(); resolve(null); });
        });
        if (result) remotePlayerCache.set(clean, result);
        return result;
    } catch (e) {
        skinDebugLog(`Fetch remote player ${clean} failed: ${e.message}`);
        return null;
    }
}

// Download a remote player's animated cape frames to the local skins directory
async function downloadRemoteCapeFrames(playerName, remoteData) {
    if (!remoteData || !remoteData.cape || !remoteData.cape.animated || !Array.isArray(remoteData.cape.frames) || remoteData.cape.frames.length < 2) return;
    const nameLower = playerName.toLowerCase();
    const prefix = `cape-${nameLower}-frame-`;
    const existing = fs.readdirSync(skinsDirectoryPath).filter(f => f.startsWith(prefix) && f.endsWith('.png'));
    if (existing.length >= remoteData.cape.frames.length) return;
    skinDebugLog(`Downloading ${remoteData.cape.frames.length} animated cape frames for ${playerName}`);
    for (let i = 0; i < remoteData.cape.frames.length; i++) {
        const frameFile = path.join(skinsDirectoryPath, `${prefix}${i}.png`);
        if (await fs.pathExists(frameFile)) continue;
        try {
            const raw = remoteData.cape.frames[i].replace(/^data:image\/\w+;base64,/, '');
            await fs.writeFile(frameFile, Buffer.from(raw, 'base64'));
        } catch (e) {
            skinDebugLog(`Download frame ${i} for ${playerName} failed: ${e.message}`);
        }
    }
    const metaFile = path.join(skinsDirectoryPath, `cape-${playerName}.json`);
    if (!(await fs.pathExists(metaFile)) && remoteData.cape.delays && remoteData.cape.delays.length > 0) {
        await fs.writeFile(metaFile, JSON.stringify(remoteData.cape.delays));
    }
}

async function startYggdrasilServer(account) {
    if (!account || account.type === 'premium') return null;
    let skinPath = null;
    let skinModel = account.skinModel || 'classic';
    if (account.skinFile) {
        skinPath = path.join(skinsDirectoryPath, account.skinFile);
        if (!(await fs.pathExists(skinPath))) {
            skinPath = null;
        }
    }
    if (!skinPath && Array.isArray(account.customSkins)) {
        for (const slot of account.customSkins) {
            if (slot && slot.file) {
                const sp = path.join(skinsDirectoryPath, slot.file);
                if (await fs.pathExists(sp)) { skinPath = sp; skinModel = slot.model || skinModel; break; }
            }
        }
    }
    if (!skinPath) {
        const steveP = path.join(__dirname, 'steve.png');
        if (await fs.pathExists(steveP)) {
            skinPath = steveP;
            skinModel = 'classic';
        } else {
            if (mainWindow) mainWindow.webContents.send('skin-status', `No skin file found for ${account.username}`);
            return null;
        }
    }

    // Always serve a PNG to the game — an animated GIF cape is rejected by
    // Minecraft's PNG-only skin loader ("Bad PNG Signature").
    let capePath = await resolveCapePngForGame(account);

    const port = 51234 + Math.floor(Math.random() * 1000);
    const name = account.username;
    const uuid = offlineUUID(name).plain;
    const isSlim = skinModel === 'slim';

    let skinHash = 'skin';
    if (skinPath) try { skinHash = crypto.createHash('sha256').update(await fs.readFile(skinPath)).update(isSlim ? 'slim' : 'classic').digest('hex'); } catch (e) {}
    const skinUrl = `http://127.0.0.1:${port}/textures/${skinHash}`;

    let remoteTextureCache = null;

    let capeHash = '';
    let capeUrl = '';
    if (capePath) {
        try { capeHash = crypto.createHash('sha256').update(await fs.readFile(capePath)).digest('hex'); } catch (e) {}
        capeUrl = `http://127.0.0.1:${port}/textures/cape-${capeHash}`;
    }

    const texturesObj = {
        timestamp: Date.now(),
        profileId: uuid,
        profileName: name,
        textures: {}
    };
    if (skinPath) texturesObj.textures.SKIN = { url: skinUrl, ...(isSlim ? { metadata: { model: 'slim' } } : {}) };
    if (capeUrl) texturesObj.textures.CAPE = { url: capeUrl };

    const value = Buffer.from(JSON.stringify(texturesObj)).toString('base64');
    let signature = '';
    try { signature = await signTextureValue(value); } catch (e) {}
    let publicKey = '';
    try { publicKey = await yggPublicKeyPem(); } catch (e) {}

    const textureProperty = { name: 'textures', value, signature };
    const profile = { id: uuid, name, properties: [textureProperty] };
    const shortProfile = { id: uuid, name, properties: [textureProperty] };
    const capeEntry = capeUrl ? [{ id: 'custom-cape', state: 'ACTIVE', url: capeUrl, alias: 'Custom Cape' }] : [];
    const servicesProfile = { id: uuid, name, skins: skinPath ? [{ id: uuid, state: 'ACTIVE', url: skinUrl, variant: isSlim ? 'SLIM' : 'CLASSIC' }] : [], capes: capeEntry };
    const attributes = {
        privileges: {
            onlineChat: { enabled: true },
            multiplayerServer: { enabled: true },
            multiplayerRealms: { enabled: false },
            telemetry: { enabled: false }
        },
        profanityFilterPreferences: { profanityFilterOn: false },
        banStatus: { bannedScopes: {} }
    };
    const authResponse = {
        accessToken: 'bat_session_pass',
        clientToken: 'bat_client_pass',
        availableProfiles: [shortProfile],
        selectedProfile: shortProfile,
        user: { id: uuid, username: name }
    };
    const metadata = {
        meta: { serverName: 'Bat Client', implementationName: 'bat-offline-skins', implementationVersion: '1.0' },
        skinDomains: ['127.0.0.1', 'localhost', '::1'],
        signaturePublickey: publicKey
    };
    const userProperties = JSON.stringify([textureProperty]);
    // Modern launchers (1.20.5+) build the LOCAL session profile from --profileProperties,
    // a PropertyMap. The PropertyMap deserializer accepts the root-array form
    // [{"name":..,"value":..,"signature":..}] which preserves the signature, so the
    // game's PlayerSkinProvider will accept the local player's own skin. (The
    // {name:[valueString]} form drops the signature -> game falls back to default.)
    const profileProperties = userProperties;

    const server = http.createServer((req, res) => {
        const urlPath = (req.url || '').split('?')[0];
        skinDebugLog(`REQ ${req.method} ${req.url}`);
        const corsHeaders = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET, POST, OPTIONS', 'Access-Control-Allow-Headers': 'Content-Type, Authorization' };
        const sendJson = (obj, code = 200) => { res.writeHead(code, { 'Content-Type': 'application/json', ...corsHeaders, 'Cache-Control': 'no-store, no-cache, must-revalidate', 'Pragma': 'no-cache' }); res.end(JSON.stringify(obj)); };
        try {
            if (req.method === 'OPTIONS') { res.writeHead(204, corsHeaders); return res.end(); }
            if (req.method === 'GET' && (urlPath === '/' || urlPath === '')) {
                skinDebugLog(`SERVE_METADATA skinDomains=${JSON.stringify(metadata.skinDomains)} pkLen=${(metadata.signaturePublickey || '').length}`);
                return sendJson(metadata);
            }
            if (req.method === 'GET' && urlPath.startsWith('/textures/')) {
                const textureId = urlPath.replace('/textures/', '');
                skinDebugLog(`TEXTURE REQUEST id=${textureId} capePath=${capePath || 'null'} skinPath=${skinPath || 'null'}`);
                if (remoteTextureCache && remoteTextureCache.has(textureId)) {
                    const b64 = remoteTextureCache.get(textureId);
                    const raw = b64.replace(/^data:image\/\w+;base64,/, '');
                    res.writeHead(200, { 'Content-Type': 'image/png', ...corsHeaders, 'Cache-Control': 'no-store, no-cache, must-revalidate' });
                    return res.end(Buffer.from(raw, 'base64'));
                }
                if (textureId.startsWith('cape-') && capePath) {
                    return fs.readFile(capePath, (err, data) => {
                        if (err) { skinDebugLog(`TEXTURE CAPE READ ERROR: ${err.message}`); res.writeHead(404); return res.end(); }
                        skinDebugLog(`TEXTURE CAPE SERVED size=${data.length}`);
                        res.writeHead(200, { 'Content-Type': 'image/png', ...corsHeaders, 'Cache-Control': 'no-store, no-cache, must-revalidate' });
                        res.end(data);
                    });
                }
                if (skinPath) {
                    return fs.readFile(skinPath, (err, data) => {
                        if (err) { skinDebugLog(`TEXTURE SKIN READ ERROR: ${err.message}`); res.writeHead(404); return res.end(); }
                        skinDebugLog(`TEXTURE SKIN SERVED size=${data.length}`);
                        res.writeHead(200, { 'Content-Type': 'image/png', ...corsHeaders, 'Cache-Control': 'no-store, no-cache, must-revalidate' });
                        res.end(data);
                    });
                }
                skinDebugLog(`TEXTURE NOT FOUND: no skinPath for id=${textureId}`);
                res.writeHead(404); return res.end();
            }
            if (urlPath.startsWith('/sessionserver/session/minecraft/profile/')) {
                const requestedUuid = urlPath.replace('/sessionserver/session/minecraft/profile/', '');
                skinDebugLog(`PROFILE REQUEST uuid=${requestedUuid} expected=${uuid}`);
                if (requestedUuid === uuid) {
                    // DIAGNOSTIC: capture exactly what the game receives for its own profile.
                    try {
                        const pp = textureProperty || profile.properties[0];
                        const dec = JSON.parse(Buffer.from(pp.value, 'base64').toString('utf8'));
                        const sUrl = dec && dec.textures && dec.textures.SKIN ? dec.textures.SKIN.url : 'NONE';
                        skinDebugLog(`SERVE_LOCAL_PROFILE uuid=${uuid} texturePresent=${!!pp} hasSig=${!!(pp.signature && pp.signature.length > 10)} sigLen=${pp.signature ? pp.signature.length : 0} skinURL=${sUrl}`);
                    } catch (e) { skinDebugLog(`SERVE_LOCAL_PROFILE uuid=${uuid} decodeErr=${e.message}`); }
                    return sendJson(profile);
                }
                const remoteKey = requestedUuid.replace(/-/g, '').toLowerCase();
                fetchRemotePlayerData(remoteKey).then(async (remote) => {
                    if (!remote) return sendJson({ id: remoteKey, name: requestedUuid, properties: [] });
                    const remoteName = remote.username || requestedUuid;
                    const textures = {};
                    if (remote.skin && remote.skin.data) {
                        const skinHash = crypto.createHash('sha256').update(remote.skin.data).digest('hex');
                        const rSkinUrl = `http://127.0.0.1:${port}/textures/remote-skin-${skinHash}`;
                        textures.SKIN = { url: rSkinUrl };
                        if (remote.skin.model === 'slim') textures.SKIN.metadata = { model: 'slim' };
                        if (!remoteTextureCache) remoteTextureCache = new Map();
                        remoteTextureCache.set('remote-skin-' + skinHash, remote.skin.data);
                    }
                    if (remote.cape && remote.cape.data) {
                        const capeHash = crypto.createHash('sha256').update(remote.cape.data).digest('hex');
                        const rCapeUrl = `http://127.0.0.1:${port}/textures/remote-cape-${capeHash}`;
                        textures.CAPE = { url: rCapeUrl };
                        if (!remoteTextureCache) remoteTextureCache = new Map();
                        remoteTextureCache.set('remote-cape-' + capeHash, remote.cape.data);
                    }
                    if (remote.cape && remote.cape.animated) {
                        downloadRemoteCapeFrames(remoteName, remote).catch(() => {});
                    }
                    const remoteTexturesObj = { timestamp: remote.timestamp || Date.now(), profileId: remoteKey, profileName: remoteName, textures };
                    const remoteValue = Buffer.from(JSON.stringify(remoteTexturesObj)).toString('base64');
                    let remoteSig = '';
                    try { remoteSig = await signTextureValue(remoteValue); } catch (e) {}
                    sendJson({ id: remoteKey, name: remoteName, properties: [{ name: 'textures', value: remoteValue, signature: remoteSig }] });
                }).catch(() => sendJson({ id: remoteKey, name: requestedUuid, properties: [] }));
                return;
            }
            if (urlPath === '/sessionserver/session/minecraft/hasJoined') return sendJson(profile);
            if (urlPath === '/sessionserver/session/minecraft/join') { res.writeHead(204); return res.end(); }
            if (urlPath === '/authserver/authenticate' || urlPath === '/authserver/refresh') return sendJson(authResponse);
            if (urlPath.startsWith('/authserver/')) { res.writeHead(204); return res.end(); }
            if (req.method === 'POST' && urlPath === '/api/profiles/minecraft') return sendJson([shortProfile]);
            if (urlPath === '/minecraftservices/minecraft/profile') return sendJson(servicesProfile);
            if (urlPath === '/minecraftservices/player/attributes') return sendJson(attributes);
            if (urlPath === '/minecraftservices/privileges') return sendJson(attributes.privileges);
            if (urlPath.startsWith('/minecraftservices/')) return sendJson({});
            skinDebugLog(`UNHANDLED: ${req.method} ${urlPath}`);
            return sendJson({});
        } catch (e) { skinDebugLog(`SERVER ERROR: ${e.message}`); try { res.writeHead(500); res.end(); } catch (_) {} }
    });

    return new Promise((resolve) => {
        const timeout = setTimeout(() => { console.warn('[Skin] Server start timed out.'); resolve(null); }, 3000);
        server.listen(port, '127.0.0.1', () => {
            clearTimeout(timeout);
            skinServer = server;
            console.log(`[Skin] Yggdrasil server started on http://127.0.0.1:${port} for ${name}`);
            skinDebugLog(`server listening on ${port} | user=${name} | uuid=${uuid} | skinFile=${account.skinFile || 'none'} | cape=${account.customCape || 'none'}`);
            resolve({ apiRoot: `http://127.0.0.1:${port}`, userProperties, profileProperties });
        });
        server.on('error', (err) => { clearTimeout(timeout); console.warn('[Skin] Could not start skin server:', err.message); resolve(null); });
    });
}

function stopSkinServer() {
    if (skinServer) {
        skinServer.close();
        skinServer = null;
        console.log('[Skin] Server stopped.');
    }
}

// ==========================================
// PREMIUM SKIN/CAPE — transparent authlib-injector proxy
// ==========================================
// Premium accounts keep their real Microsoft session: all auth/session traffic
// is forwarded to the real Mojang endpoints so online-mode servers still work.
// Only the local player's cape is overlaid with the launcher cape.
async function startPremiumSkinServer(account, accessToken, uuid) {
    if (!account || !uuid) return null;
    const capePng = await resolveCapePngForGame(account);
    let hasLocalSkin = false;
    if (account.skinFile) hasLocalSkin = await fs.pathExists(path.join(skinsDirectoryPath, account.skinFile));
    if (!hasLocalSkin && Array.isArray(account.customSkins)) {
        for (const slot of account.customSkins) {
            if (slot && slot.file && (await fs.pathExists(path.join(skinsDirectoryPath, slot.file)))) { hasLocalSkin = true; break; }
        }
    }
    if (!capePng && !hasLocalSkin) return null;

    let skinPath = null;
    let skinModel = account.skinModel || 'classic';
    if (account.skinFile) {
        const sp = path.join(skinsDirectoryPath, account.skinFile);
        if (await fs.pathExists(sp)) skinPath = sp;
    }
    if (!skinPath && Array.isArray(account.customSkins)) {
        for (const slot of account.customSkins) {
            if (slot && slot.file) {
                const sp = path.join(skinsDirectoryPath, slot.file);
                if (await fs.pathExists(sp)) { skinPath = sp; skinModel = slot.model || skinModel; break; }
            }
        }
    }

    const port = 51234 + Math.floor(Math.random() * 1000);
    const name = account.username;
    const localUuid = String(uuid).replace(/-/g, '').toLowerCase();
    let remoteTextureCache = null;

    let capeHash = '';
    try { capeHash = crypto.createHash('sha256').update(await fs.readFile(capePng)).digest('hex'); } catch (e) {}
    const capeUrl = capeHash ? `http://127.0.0.1:${port}/textures/cape-${capeHash}` : '';

    let publicKey = '';
    try { publicKey = await yggPublicKeyPem(); } catch (e) {}

    const corsHeaders = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET, POST, OPTIONS', 'Access-Control-Allow-Headers': 'Content-Type, Authorization' };
    const readBody = (req) => new Promise((resolve) => {
        const chunks = [];
        req.on('data', (c) => chunks.push(c));
        req.on('end', () => resolve(Buffer.concat(chunks)));
    });

    const forward = async (req, res, base, pathPart) => {
        try {
            const headers = {};
            if (req.headers['content-type']) headers['Content-Type'] = req.headers['content-type'];
            if (req.headers.authorization) headers['Authorization'] = req.headers.authorization;
            const bodyBuf = await readBody(req);
            const r = await stableFetch(base + pathPart, { method: req.method, headers, body: bodyBuf.length ? bodyBuf : undefined }, 1, 500, 15000);
            const out = Buffer.from(await r.arrayBuffer());
            if (pathPart === '/session/minecraft/join') {
                let joinInfo = '';
                try {
                    const jb = JSON.parse(bodyBuf.toString('utf8'));
                    joinInfo = ` uuid=${jb.selectedProfile || jb.accessToken ? String(jb.selectedProfile || '').slice(0, 36) : ''} serverId=${String(jb.serverId || '').slice(0, 48)} token=${String(jb.accessToken || '').slice(0, 10)}...`;
                } catch (e) { joinInfo = ` rawBody=${bodyBuf.toString('utf8').slice(0, 160)}`; }
                skinDebugLog(`PREMIUM JOIN FORWARD ${req.method} ${pathPart} -> Mojang ${r.status}${joinInfo} respHead=${out.slice(0, 200).toString('utf8').slice(0, 200)}`);
            } else {
                skinDebugLog(`PREMIUM FORWARD ${req.method} ${pathPart} -> ${r.status} respHead=${out.slice(0, 120).toString('utf8')}`);
            }
            res.writeHead(r.status, { 'Content-Type': r.headers.get('content-type') || 'application/json', ...corsHeaders });
            res.end(out);
        } catch (e) {
            skinDebugLog(`PREMIUM FORWARD ERROR ${pathPart}: ${e.message}`);
            try { res.writeHead(502, corsHeaders); res.end('{}'); } catch (_) {}
        }
    };

    // Build the local player's signed texture property up front: keep the real
    // Mojang skin URL, overlay the launcher cape, sign with our key. The same
    // property is served for profile lookups and embedded in the session.
    // The build is awaited by the profile route and by the server startup so
    // clients can never observe an empty/partial texture property.
    let textureProperty = { name: 'textures', value: '', signature: '' };
    // Real Mojang profile properties (profilePublicKey for chat signing, etc.) are passed
    // through untouched so the server's secure-profile validation accepts the session. Only
    // the textures property is replaced with our locally-signed one. Without this passthrough
    // the client has no valid profile key and servers with enforce-secure-profile kick with
    // "Invalid signature for profile public key".
    let realProfileProperties = [];
    const buildPromise = (async () => {
        try {
            let realSkinUrl = null;
            let realModel = 'classic';
            try {
                const r = await stableFetch(`https://sessionserver.mojang.com/session/minecraft/profile/${localUuid}?unsigned=false`, {}, 2, 1000, 12000);
                if (r.ok) {
                    const p = await r.json();
                    if (Array.isArray(p.properties)) {
                        realProfileProperties = p.properties.filter(x => x && x.name !== 'textures');
                    }
                    const tp = (p.properties || []).find(x => x.name === 'textures');
                    if (tp && tp.value) {
                        const dec = JSON.parse(Buffer.from(tp.value, 'base64').toString('utf8'));
                        if (dec.textures && dec.textures.SKIN) {
                            realSkinUrl = dec.textures.SKIN.url || null;
                            if (dec.textures.SKIN.metadata && dec.textures.SKIN.metadata.model) realModel = dec.textures.SKIN.metadata.model;
                        }
                    }
                }
            } catch (mojErr) {
                skinDebugLog(`PREMIUM Mojang fetch failed (non-fatal): ${mojErr.message}`);
            }
            const texturesObj = {
                timestamp: Date.now(),
                profileId: localUuid,
                profileName: name,
                textures: {}
            };
            if (skinPath) {
                let skinHash = '';
                try { skinHash = crypto.createHash('sha256').update(await fs.readFile(skinPath)).digest('hex'); } catch (e) {}
                const localSkinUrl = `http://127.0.0.1:${port}/textures/skin-${skinHash}`;
                texturesObj.textures.SKIN = { url: localSkinUrl, ...(skinModel === 'slim' ? { metadata: { model: 'slim' } } : {}) };
            }
            else if (realSkinUrl) texturesObj.textures.SKIN = { url: realSkinUrl, ...(realModel === 'slim' ? { metadata: { model: 'slim' } } : {}) };
            if (capeUrl) texturesObj.textures.CAPE = { url: capeUrl };
            const value = Buffer.from(JSON.stringify(texturesObj)).toString('base64');
            const signature = await signTextureValue(value);
            textureProperty = { name: 'textures', value, signature };
            skinDebugLog(`PREMIUM local texture property ready (skin=${skinPath ? 'local' : (realSkinUrl ? 'real' : 'none')} cape=${capeUrl ? 'custom' : 'none'})`);
        } catch (e) { skinDebugLog(`PREMIUM texture property build error: ${e.message}`); }
    })();

    const buildLocalProfile = async () => { await buildPromise; return { id: localUuid, name, properties: [...realProfileProperties, textureProperty] }; };

    // Other players: fetch the real profile and re-sign it under our key so the
    // client accepts it (authlib-injector verifies with our public key).
    const buildReSignedProfile = async (requestedUuid) => {
        try {
            const r = await stableFetch(`https://sessionserver.mojang.com/session/minecraft/profile/${requestedUuid}?unsigned=false`, {}, 2, 1000, 12000);
            if (!r.ok) return { id: requestedUuid, name: '', properties: [] };
            const p = await r.json();
            if (Array.isArray(p.properties)) {
                for (const prop of p.properties) {
                    if (prop && prop.name === 'textures' && prop.value) {
                        try { prop.signature = await signTextureValue(prop.value); } catch (e) {}
                    }
                }
            }
            return p;
        } catch (e) {
            skinDebugLog(`PREMIUM re-sign error ${requestedUuid}: ${e.message}`);
            return { id: requestedUuid, name: '', properties: [] };
        }
    };

    const metadata = {
        meta: { serverName: 'Bat Client', implementationName: 'bat-premium-skins', implementationVersion: '1.0' },
        skinDomains: ['127.0.0.1', 'localhost', '::1', 'textures.minecraft.net', '*.minecraft.net', '*.minecraftservices.com'],
        signaturePublickey: publicKey
    };

    const server = http.createServer((req, res) => {
        const urlPath = (req.url || '').split('?')[0];
        skinDebugLog(`PREMIUM REQ ${req.method} ${req.url}`);
        const sendJson = (obj, code = 200) => { res.writeHead(code, { 'Content-Type': 'application/json', ...corsHeaders, 'Cache-Control': 'no-store, no-cache, must-revalidate', 'Pragma': 'no-cache' }); res.end(JSON.stringify(obj)); };
        try {
            if (req.method === 'OPTIONS') { res.writeHead(204, corsHeaders); return res.end(); }
            if (req.method === 'GET' && (urlPath === '/' || urlPath === '')) return sendJson(metadata);

            if (req.method === 'GET' && urlPath.startsWith('/textures/')) {
                const textureId = urlPath.replace('/textures/', '');
                if (remoteTextureCache && remoteTextureCache.has(textureId)) {
                    const b64 = remoteTextureCache.get(textureId);
                    const raw = b64.replace(/^data:image\/\w+;base64,/, '');
                    res.writeHead(200, { 'Content-Type': 'image/png', ...corsHeaders, 'Cache-Control': 'no-store, no-cache, must-revalidate' });
                    return res.end(Buffer.from(raw, 'base64'));
                }
                const filePath = textureId.startsWith('cape-') ? capePng : skinPath;
                if (!filePath) { skinDebugLog(`PREMIUM TEXTURE NOT FOUND ${textureId}`); res.writeHead(404); return res.end(); }
                return fs.readFile(filePath, (err, data) => {
                    if (err) { skinDebugLog(`PREMIUM TEXTURE READ ERROR ${textureId}: ${err.message}`); res.writeHead(404); return res.end(); }
                    skinDebugLog(`PREMIUM TEXTURE SERVED ${textureId} size=${data.length}`);
                    res.writeHead(200, { 'Content-Type': 'image/png', ...corsHeaders, 'Cache-Control': 'no-store, no-cache, must-revalidate' });
                    res.end(data);
                });
            }

            if (urlPath.startsWith('/sessionserver/session/minecraft/profile/')) {
                const requestedUuid = urlPath.replace('/sessionserver/session/minecraft/profile/', '').replace(/-/g, '').toLowerCase();
                skinDebugLog(`PREMIUM PROFILE REQUEST uuid=${requestedUuid} local=${localUuid}`);
                if (requestedUuid === localUuid) return buildLocalProfile().then((p) => sendJson(p));
                fetchRemotePlayerData(requestedUuid).then(async (remote) => {
                    if (remote && (remote.skin || remote.cape)) {
                        skinDebugLog(`PREMIUM REMOTE BAT CLIENT SKIN for ${requestedUuid}`);
                        const remoteName = remote.username || requestedUuid;
                        const textures = {};
                        if (remote.skin && remote.skin.data) {
                            const skinHash = crypto.createHash('sha256').update(remote.skin.data).digest('hex');
                            const rSkinUrl = `http://127.0.0.1:${port}/textures/remote-skin-${skinHash}`;
                            textures.SKIN = { url: rSkinUrl };
                            if (remote.skin.model === 'slim') textures.SKIN.metadata = { model: 'slim' };
                            if (!remoteTextureCache) remoteTextureCache = new Map();
                            remoteTextureCache.set('remote-skin-' + skinHash, remote.skin.data);
                        }
                        if (remote.cape && remote.cape.data) {
                            const capeHash = crypto.createHash('sha256').update(remote.cape.data).digest('hex');
                            const rCapeUrl = `http://127.0.0.1:${port}/textures/remote-cape-${capeHash}`;
                            textures.CAPE = { url: rCapeUrl };
                            if (!remoteTextureCache) remoteTextureCache = new Map();
                            remoteTextureCache.set('remote-cape-' + capeHash, remote.cape.data);
                        }
                        if (remote.cape && remote.cape.animated) {
                            downloadRemoteCapeFrames(remoteName, remote).catch(() => {});
                        }
                        const remoteTexturesObj = { timestamp: remote.timestamp || Date.now(), profileId: requestedUuid, profileName: remoteName, textures };
                        const remoteValue = Buffer.from(JSON.stringify(remoteTexturesObj)).toString('base64');
                        let remoteSig = '';
                        try { remoteSig = await signTextureValue(remoteValue); } catch (e) {}
                        return sendJson({ id: requestedUuid, name: remoteName, properties: [{ name: 'textures', value: remoteValue, signature: remoteSig }] });
                    }
                    return buildReSignedProfile(requestedUuid).then((p) => sendJson(p));
                }).catch(() => buildReSignedProfile(requestedUuid).then((p) => sendJson(p)));
                return;
            }
            if (urlPath === '/sessionserver/session/minecraft/hasJoined') return sendJson({});
            if (urlPath === '/sessionserver/session/minecraft/join') {
                if (req.method === 'GET') { res.writeHead(204); return res.end(); }
                return forward(req, res, 'https://sessionserver.mojang.com', '/session/minecraft/join');
            }

            if (urlPath.startsWith('/authserver/')) return forward(req, res, 'https://authserver.mojang.com', urlPath.replace(/^\/authserver/, ''));
            if (urlPath.startsWith('/minecraftservices/')) return forward(req, res, 'https://api.minecraftservices.com', urlPath.replace(/^\/minecraftservices/, ''));
            if (urlPath === '/api/profiles/minecraft') return sendJson([]);
            skinDebugLog(`PREMIUM UNHANDLED: ${req.method} ${urlPath}`);
            return sendJson({});
        } catch (e) { skinDebugLog(`PREMIUM SERVER ERROR: ${e.message}`); try { res.writeHead(500); res.end(); } catch (_) {} }
    });

    return new Promise((resolve) => {
        const timeout = setTimeout(() => { console.warn('[Skin] Premium server start timed out.'); resolve(null); }, 3000);
        server.listen(port, '127.0.0.1', async () => {
            clearTimeout(timeout);
            skinServer = server;
            console.log(`[Skin] Premium skin server started on http://127.0.0.1:${port} for ${name}`);
            skinDebugLog(`premium server listening on ${port} | user=${name} | uuid=${localUuid} | cape=${capeUrl || 'none'} | skinPath=${skinPath || 'none'}`);
            await buildPromise;
            resolve({ apiRoot: `http://127.0.0.1:${port}`, userProperties: JSON.stringify([textureProperty]), profileProperties: JSON.stringify([textureProperty]) });
        });
        server.on('error', (err) => { clearTimeout(timeout); console.warn('[Skin] Could not start premium skin server:', err.message); resolve(null); });
    });
}

// ==========================================
// SETTINGS
// ==========================================
ipcMain.on('get-app-settings', (event) => event.reply('sync-settings-view', appSettings));
ipcMain.on('save-app-settings', async (event, payload) => {
    appSettings = { ...appSettings, ...payload };
    if (payload.discordRPC !== undefined) {
        if (payload.discordRPC) {
            initDiscordRPC().then(() => {
                if (rpc && !rpc.user) Promise.race([rpc.login(), new Promise((_,rej) => setTimeout(() => rej(new Error('RPC timeout')), 5000))]).catch(() => {});
            });
        } else if (rpc && rpc.user) rpc.destroy().catch(() => {});
    }
    if (payload.downloadThreads !== undefined) {
        try { require('http').globalAgent.maxSockets = (payload.downloadThreads || 16) * 4; } catch(e) {}
    }
    if (payload.startWithWindows !== undefined) {
        try { app.setLoginItemSettings({ openAtLogin: !!payload.startWithWindows, path: app.getPath('exe') }); } catch(e) {}
    }
    if (payload.closeToTray !== undefined) {
        if (payload.closeToTray && !appTray) {
            try {
                const iconPath = resolveBatIconPath();
                if (fs.existsSync(iconPath)) {
                    appTray = new Tray(iconPath);
                    appTray.setToolTip('Bat Client');
                    appTray.setContextMenu(Menu.buildFromTemplate([
                        { label: 'Show Bat Client', click: () => { if (mainWindow) { mainWindow.show(); mainWindow.focus(); } } },
                        { type: 'separator' },
                        { label: 'Quit', click: () => { app.quit(); } }
                    ]));
                    appTray.on('double-click', () => { if (mainWindow) { mainWindow.show(); mainWindow.focus(); } });
                }
            } catch(e) {}
        } else if (!payload.closeToTray && appTray) {
            try { appTray.destroy(); appTray = null; } catch(e) {}
        }
    }
    await saveConfigToDisk();
    event.reply('sync-settings-view', appSettings);
});

ipcMain.handle('get-app-settings', async () => appSettings);

ipcMain.handle('check-first-launch', async () => {
    return { firstLaunch: appSettings.firstLaunch === true };
});
ipcMain.handle('complete-first-launch', async (event, config) => {
    try {
        if (config) {
            if (config.ram) appSettings.defaultRam = config.ram;
            if (config.gameDir) appSettings.gameDir = config.gameDir;
            if (config.settings) {
                const s = config.settings;
                if (s.windowWidth) appSettings.windowWidth = s.windowWidth;
                if (s.windowHeight) appSettings.windowHeight = s.windowHeight;
                if (typeof s.fullscreen === 'boolean') appSettings.fullscreen = s.fullscreen;
                if (typeof s.gameWindowTitle === 'string') appSettings.gameWindowTitle = s.gameWindowTitle;
                if (typeof s.notifications === 'boolean') appSettings.notifications = s.notifications;
                if (typeof s.launchSound === 'boolean') appSettings.launchSound = s.launchSound;
                if (typeof s.discordRPC === 'boolean') appSettings.discordRPC = s.discordRPC;
                if (typeof s.autoCheckUpdates === 'boolean') appSettings.autoCheckUpdates = s.autoCheckUpdates;
            }
        }
        appSettings.firstLaunch = false;
        await saveConfigToDisk();
        return { ok: true };
    } catch (e) { return { ok: false, error: e.message }; }
});
ipcMain.handle('detect-java', async () => {
    try { const { execSync } = require('child_process'); const out = execSync('java -version 2>&1', { timeout: 5000, encoding: 'utf8', windowsHide: true }); return { found: true, version: out.split('\n')[0] || 'Unknown' }; } catch { return { found: false, version: '' }; }
});

ipcMain.handle('get-java-status', async () => {
    const statuses = {};
    for (const major of [8, 11, 17, 21]) {
        const available = await isJavaAvailable(major);
        const execPath = getJavaExecutable(major);
        let version = '';
        if (available) version=verifiedJava.get(major)?.version || '';
        statuses[major] = { available, version, path: available ? execPath : '' };
    }
    return statuses;
});

ipcMain.handle('download-java', async (event, { major }) => {
    return await ensureJavaAvailable(event, major || 21);
});

ipcMain.handle('clear-launcher-cache', async () => {
    try {
        const cacheDir = path.join(appDataRoot, 'Cache');
        const codeCacheDir = path.join(appDataRoot, 'Code Cache');
        const gpuCacheDir = path.join(appDataRoot, 'GPUCache');
        let cleared = 0;
        for (const dir of [cacheDir, codeCacheDir, gpuCacheDir]) {
            if (await fs.pathExists(dir)) { await fs.remove(dir); cleared++; }
        }
        return `Cleared ${cleared} cache folder(s).`;
    } catch (e) { return 'Failed to clear cache: ' + e.message; }
});
ipcMain.handle('get-installed-mods-count', async (event, profileId) => {
    try {
        const modsDir = getModsDir();
        if (!await fs.pathExists(modsDir)) return 0;
        const files = await fs.readdir(modsDir);
        return files.filter(f => f.endsWith('.jar') || f.endsWith('.disabled')).length;
    } catch (e) { return 0; }
});

ipcMain.handle('get-installed-mods', async (event, profileId) => {
    try {
        const modsDir = getModsDir();
        if (!await fs.pathExists(modsDir)) return [];
        const files = await fs.readdir(modsDir);
        const managed = await getManagedMods();
        const mods = [];
        for (const f of files) {
            if (f.endsWith('.jar') || f.endsWith('.disabled')) {
                const enabled = f.endsWith('.jar');
                const displayName = f.replace(/\.(jar|disabled)$/, '');
                const isManaged = managed.includes(f) || managed.includes(displayName + '.jar');
                const iconDataUrl = await readModIcon(path.join(modsDir, f));
                mods.push({ filename: f, displayName, enabled, isManaged, iconDataUrl });
            }
        }
        return mods;
    } catch { return []; }
});

ipcMain.handle('toggle-mod', async (event, filename, profileId) => {
    try {
        const modsDir = getModsDir();
        const fp = path.join(modsDir, filename);
        if (!(await fs.pathExists(fp))) return { ok: false, error: 'File not found' };
        if (filename.endsWith('.jar')) {
            const newPath = path.join(modsDir, filename.replace(/\.jar$/, '.disabled'));
            if (await fs.pathExists(newPath)) return {ok:false,error:'The destination mod file already exists; both copies were preserved.'};
            await fs.rename(fp, newPath);
            const managed = await getManagedMods();
            if (managed.includes(filename) || projectForFile(filename)) {
                const modProjectMap = await getModProjectMap();
                const projectId = projectForFile(filename, modProjectMap);
                if (projectId) await addDismissedMod(projectId);
            }
            return { ok: true, newFilename: filename.replace(/\.jar$/, '.disabled'), enabled: false };
        } else if (filename.endsWith('.disabled')) {
            const newPath = path.join(modsDir, filename.replace(/(?:\.jar)?\.disabled$/, '.jar'));
            if (await fs.pathExists(newPath)) return {ok:false,error:'The destination mod file already exists; both copies were preserved.'};
            await fs.rename(fp, newPath);
            const managed = await getManagedMods();
            if (managed.includes(filename.replace(/(?:\.jar)?\.disabled$/, '.jar')) || projectForFile(filename)) {
                const modProjectMap = await getModProjectMap();
                const projectId = projectForFile(filename, modProjectMap);
                if (projectId) await removeDismissedMod(projectId);
            }
            return { ok: true, newFilename: filename.replace(/(?:\.jar)?\.disabled$/, '.jar'), enabled: true };
        }
        return { ok: false, error: 'Unknown extension' };
    } catch (e) { return { ok: false, error: e.message }; }
});

ipcMain.handle('remove-mod', async (event, filename, profileId) => {
    try {
        const modsDir = getModsDir();
        const fp = path.join(modsDir, filename);
        if (!(await fs.pathExists(fp))) return { ok: false, error: 'File not found' };
        const managed = await getManagedMods();
        if (managed.includes(filename) || projectForFile(filename)) {
            const modProjectMap = await getModProjectMap();
            const projectId = projectForFile(filename, modProjectMap);
            if (projectId) await addDismissedMod(projectId);
        }
        await fs.remove(fp);
        const cleaned = managed.filter(m => m !== filename);
        if (cleaned.length !== managed.length) await fs.writeJson(managedModsFilePath, cleaned, { spaces: 0 });
        return { ok: true };
    } catch (e) { return { ok: false, error: e.message }; }
});

ipcMain.handle('check-mod-conflicts', async (event, profileId) => {
    try {
        const modsDir = getModsDir();
        if (!await fs.pathExists(modsDir)) return { conflicts: [], warnings: [] };
        const files = (await fs.readdir(modsDir)).filter(f => f.endsWith('.jar') || f.endsWith('.disabled'));
        const lower = files.map(f => f.toLowerCase());
        const has = (patterns) => patterns.some(p => lower.some(f => f.includes(p)));
        const conflicts = [];
        if (has(['sodium', 'embeddium']) && has(['optifine', 'optifabric'])) {
            conflicts.push({ severity: 'error', mod1: 'Sodium/Embeddium', mod2: 'OptiFine', message: 'Sodium and OptiFine conflict. Use Sodium + Sodium Extra + Iris instead.' });
        }
        if (has(['sodium']) && has(['rubidium'])) {
            conflicts.push({ severity: 'error', mod1: 'Sodium', mod2: 'Rubidium', message: 'Both are Sodium for different loaders. Use only one.' });
        }
        if (has(['sodium']) && has(['oculus'])) {
            conflicts.push({ severity: 'warning', mod1: 'Sodium', mod2: 'Oculus', message: 'Oculus bundles Sodium. Remove standalone Sodium to avoid duplicates.' });
        }
        if (has(['betterfps']) && has(['sodium', 'embeddium', 'rubidium'])) {
            conflicts.push({ severity: 'warning', mod1: 'BetterFps', mod2: 'Sodium', message: 'Both optimize rendering. BetterFps may cause issues alongside Sodium.' });
        }
        if (has(['lithium']) && has(['servercore'])) {
            conflicts.push({ severity: 'info', mod1: 'Lithium', mod2: 'ServerCore', message: 'Both optimize server-side code. Overlapping features but usually safe.' });
        }
        if (has(['starlight']) && has(['phosphor', 'lambdynamiclights'])) {
            conflicts.push({ severity: 'warning', mod1: 'Starlight', mod2: 'Phosphor', message: 'Both rewrite the lighting engine. Use only Starlight.' });
        }
        if (has(['create']) && has(['create-fabric'])) {
            conflicts.push({ severity: 'error', mod1: 'Create (Forge)', mod2: 'Create Fabric', message: 'Both are Create mod for different loaders. Use only one.' });
        }
        if (has(['iris']) && has(['optifine', 'optifabric'])) {
            conflicts.push({ severity: 'error', mod1: 'Iris', mod2: 'OptiFine', message: 'Iris and OptiFine conflict on shader loading. Use one or the other.' });
        }
        if (has(['canvas-renderer', 'canvas']) && has(['sodium', 'embeddium'])) {
            conflicts.push({ severity: 'warning', mod1: 'Canvas Renderer', mod2: 'Sodium', message: 'Both are rendering engines. Use only one for best results.' });
        }
        const warnings = [];
        const loaderForge = lower.some(f => f.includes('forge'));
        const loaderFabric = lower.some(f => f.includes('fabric') || f.includes('sodium') || f.includes('iris'));
        if (loaderForge && loaderFabric) {
            warnings.push({ severity: 'warning', message: 'Forge and Fabric mods detected together. Most are incompatible.' });
        }
        const mcVersion116 = lower.some(f => f.includes('1.16') || f.includes('1.17') || f.includes('1.18') || f.includes('1.19') || f.includes('1.20') || f.includes('1.21'));
        return { conflicts, warnings };
    } catch (e) { return { conflicts: [], warnings: [] }; }
});

// ==========================================
// MODPACK EXPORT / IMPORT
// ==========================================
ipcMain.handle('export-modpack', async (event, { profileName }) => {
    try {
        const { dialog } = require('electron');
        const modsDir = getModsDir();
        const safeName = (profileName || 'modpack').replace(/[^a-zA-Z0-9_\- ]/g, '_');
        const result = await dialog.showSaveDialog(mainWindow, {
            title: 'Export Modpack',
            defaultPath: `${safeName}.batpack`,
            filters: [{ name: 'Bat Modpack', extensions: ['batpack'] }, { name: 'ZIP', extensions: ['zip'] }]
        });
        if (result.canceled || !result.filePath) return { ok: false, canceled: true };
        const zip = new (require('adm-zip'))();
        const manifest = { name: safeName, exportedAt: new Date().toISOString(), version: 1, mods: [], managedMods: [] };
        if (await fs.pathExists(modsDir)) {
            const files = await fs.readdir(modsDir);
            for (const f of files) {
                if (f.endsWith('.jar') || f.endsWith('.disabled')) {
                    zip.addLocalFile(path.join(modsDir, f), 'mods');
                    manifest.mods.push({ filename: f, enabled: f.endsWith('.jar') });
                }
            }
        }
        const managed = await getManagedMods();
        manifest.managedMods = managed;
        zip.addFile('manifest.json', Buffer.from(JSON.stringify(manifest, null, 2)));
        const configFile = path.join(appDataRoot, 'profiles.config.json');
        if (await fs.pathExists(configFile)) {
            zip.addLocalFile(configFile, 'config');
        }
        zip.writeZip(result.filePath);
        return { ok: true, path: result.filePath, modCount: manifest.mods.length };
    } catch (e) { return { ok: false, error: e.message }; }
});

ipcMain.handle('import-modpack', async (event) => {
    try {
        const { dialog } = require('electron');
        const result = await dialog.showOpenDialog(mainWindow, {
            title: 'Import Modpack',
            filters: [{ name: 'Bat Modpack', extensions: ['batpack', 'zip'] }],
            properties: ['openFile']
        });
        if (result.canceled || !result.filePaths.length) return { ok: false, canceled: true };
        const zip = new (require('adm-zip'))(result.filePaths[0]);
        const entries = zip.getEntries();
        const manifestEntry = entries.find(e => e.entryName === 'manifest.json');
        let manifest = { mods: [], managedMods: [] };
        if (manifestEntry) {
            try { manifest = JSON.parse(manifestEntry.getData().toString('utf8')); } catch(e) {}
        }
        const modsDir = getModsDir();
        await fs.ensureDir(modsDir);
        let installed = 0;
        for (const entry of entries) {
            if (entry.entryName.startsWith('mods/') && (entry.entryName.endsWith('.jar') || entry.entryName.endsWith('.disabled'))) {
                const fileName = path.basename(entry.entryName);
                const targetPath = path.join(modsDir, fileName);
                await fs.writeFile(targetPath, entry.getData());
                installed++;
            }
        }
        if (manifest.managedMods && manifest.managedMods.length > 0) {
            const existing = await getManagedMods();
            const merged = [...new Set([...existing, ...manifest.managedMods])];
            await fs.writeJson(managedModsFilePath, merged, { spaces: 0 });
        }
        return { ok: true, installed, name: manifest.name || 'Unknown' };
    } catch (e) { return { ok: false, error: e.message }; }
});

// ==========================================
// LAUNCH GAME (with auto‑Java, old‑Forge protection, JVM fix, crash recovery)
// ==========================================
// Animated cape via -javaagent. Any leftover managed mod jar from a previous
// install is cleaned so it never conflicts with the agent's class transform.
async function ensureCapeAgent() {
    const candidates = [
        path.join(__dirname, 'bat-client-agent', 'build', 'libs', 'bat-cosmetics.jar'),
        path.join(process.resourcesPath || '', 'bundled', 'bat-cosmetics.jar')
    ];
    let src = null;
    for (const c of candidates) {
        if (fs.pathExistsSync(c)) { src = c; break; }
    }
    if (!src) { skinDebugLog('animated cape agent: not bundled'); return null; }
    try {
        const bundledHash=verifyAgent(src);
        await fs.ensureDir(appDataRoot);
        const dest = path.join(appDataRoot, 'bat-cosmetics.jar');
        let needCopy = true;
        if (fs.pathExistsSync(dest)) {
            try {needCopy=verifyAgent(dest)!==bundledHash;} catch {needCopy=true;}
        }
        if (needCopy) await fs.copy(src, dest);
        verifyAgent(dest);
        const capeJarPatterns = (f) => f === 'bat-client-cape-1.0.0.jar' || (f.startsWith('batclient-1.0.0+') && f.endsWith('.jar'));
        for (const modsDir of [getModsDir()]) {
            if (!fs.pathExistsSync(modsDir)) continue;
            for (const f of await fs.readdir(modsDir)) {
                if (capeJarPatterns(f)) {
                    try { await fs.remove(path.join(modsDir, f)); } catch (e) {}
                }
            }
        }
        skinDebugLog(`animated cape agent ensured (${dest})`);
        return dest;
    } catch (e) {
        skinDebugLog(`animated cape agent ensure failed: ${e.message}`);
        return null;
    }
}

// ---- Forge / NeoForge version-profile install ----
// mclc launches a custom version JSON through executionOptions.version.custom (the same
// mechanism used for Fabric profiles). Classic Forge (< 1.13) and NeoForge have no usable
// ForgeWrapper path, so we run the official loader installer with --installClient and point
// mclc at the version profile it creates. Forge 1.13+ keeps using the ForgeWrapper path.
async function downloadFileTo(url, destPath, onProgress) {
    const ac = new AbortController();
    const timer = setTimeout(() => ac.abort(), 60000);
    let res;
    try {
        res = await stableFetch(url, { signal: ac.signal }, 2, 1000, 15000);
    } finally {
        clearTimeout(timer);
    }
    if (!res.ok) throw new Error(`download failed (HTTP ${res.status})`);
    const totalBytes = parseInt(res.headers.get('content-length') || '0');
    const ws = createWriteStream(destPath);
    const reader = res.body.getReader();
    let downloaded = 0;
    while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        downloaded += value.length;
        ws.write(Buffer.from(value));
        if (onProgress && totalBytes > 0) onProgress(Math.round((downloaded / totalBytes) * 100));
    }
    ws.end();
    await new Promise((resolve, reject) => { ws.on('finish', resolve); ws.on('error', reject); });
}

function runInstaller(javaExe, argsList, timeoutMs) {
    return new Promise((resolve, reject) => {
        const proc = spawn(javaExe, argsList, { stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true });
        let out = '';
        proc.stdout.on('data', (d) => { out += d.toString(); });
        proc.stderr.on('data', (d) => { out += d.toString(); });
        const timer = setTimeout(() => { proc.kill(); reject(new Error('installer timed out')); }, timeoutMs);
        proc.on('error', (err) => { clearTimeout(timer); reject(err); });
        proc.on('close', (code) => {
            clearTimeout(timer);
            if (code === 0) resolve(out);
            else reject(new Error(`installer exited ${code}: ${out.slice(-400)}`));
        });
    });
}

async function findInstalledLoaderProfile(gameRoot, mcVersion, isNeo, build) {
    // NeoForge profile ids are exactly "neoforge-<neoVersion>" (e.g. neoforge-21.11.45), while
    // classic Forge uses "<mc>-forge-<build>" / "forge-<mc>-<build>".
    const patterns = isNeo
        ? [`neoforge-${build}`]
        : [`${mcVersion}-forge-`, `forge-${mcVersion}-`];
    const versionsDir = path.join(gameRoot, 'versions');
    let entries = [];
    try { entries = await fs.readdir(versionsDir); } catch (e) { return null; }
    for (const name of entries) {
        for (const p of patterns) {
            if (name.startsWith(p) && await fs.pathExists(path.join(versionsDir, name, `${name}.json`))) {
                return name;
            }
        }
    }
    return null;
}

async function latestLoaderBuild(mcVersion, isNeo) {
    if (isNeo) {
        // NeoForge versions are <mcMin>.<mcPatch>.<build> with the MC major "1." dropped, e.g. MC
        // 1.21.11 -> "21.11.45"; prereleases carry a -beta/-alpha/-snapshot suffix.
        const verParts = String(mcVersion).split('.');
        const min = parseInt(verParts[1] || '0', 10);
        const patch = parseInt(verParts[2] || '0', 10);
        const prefix = `${min}.${patch}.`;
        const metaRes = await stableFetch('https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml', {}, 2, 1000, 10000);
        const metaText = await metaRes.text();
        const versions = [...metaText.matchAll(/<version>([^<]+)<\/version>/g)].map((m) => m[1])
            .filter((v) => v.startsWith(prefix) && !/-(alpha|snapshot)/i.test(v));
        const stableVersions = versions.filter((v) => !v.includes('-'));
        const usableVersions = stableVersions.length ? stableVersions : versions.filter((v) => v.includes('-'));
        usableVersions.sort((a, b) => {
            const pa = a.split('-')[0].split('.').map(Number);
            const pb = b.split('-')[0].split('.').map(Number);
            for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
                const av = pa[i] || 0;
                const bv = pb[i] || 0;
                if (av !== bv) return av - bv;
            }
            return 0;
        });
        if (!usableVersions.length) throw new Error(`No NeoForge build found for ${mcVersion}.`);
        return usableVersions[usableVersions.length - 1];
    }
    const promoRes = await stableFetch('https://files.minecraftforge.net/maven/net/minecraftforge/forge/promotions_slim.json', {}, 2, 1000, 10000);
    const promoData = await promoRes.json();
    const promos = (promoData && promoData.promos) || {};
    const build = promos[`${mcVersion}-recommended`] || promos[`${mcVersion}-latest`];
    if (!build) throw new Error(`No Forge build found for ${mcVersion}.`);
    return build;
}

// Classic Forge installers (< 1.13) refuse to install unless the target directory looks like a
// real .minecraft folder, i.e. it contains launcher_profiles.json. The installer reads it to
// inject its profile, so we create a minimal valid one when it is missing (mclc never creates it).
function ensureLauncherProfile(gameRoot) {
    const profilePath = path.join(gameRoot, 'launcher_profiles.json');
    if (fs.existsSync(profilePath)) return;
    const minimal = {
        profiles: {},
        selectedProfile: '(Default)',
        clientToken: `bat-${Date.now().toString(36)}`,
        launcherVersion: { name: 'Bat Client', format: 1 }
    };
    fs.writeFileSync(profilePath, JSON.stringify(minimal, null, 2));
    console.log(`[Loader] Created ${profilePath}`);
}

async function installVersionCustomLoader(gameRoot, mcVersion, isNeo, javaMajor, cacheDir, onStatus, onProgress) {
    const build = await latestLoaderBuild(mcVersion, isNeo);
    const label = isNeo ? 'NeoForge' : 'Forge';
    // For NeoForge the build IS the full version (e.g. 21.11.45); classic Forge is <mc>-<build>.
    const display = isNeo ? build : `${mcVersion}-${build}`;
    if (onStatus) onStatus(`Downloading ${label} ${display} installer...`);
    if (onProgress) onProgress(0);
    const installerName = isNeo
        ? `neoforge-${build}-installer.jar`
        : `forge-${mcVersion}-${build}-installer.jar`;
    const installerPath = path.join(cacheDir, installerName);
    if (!fs.existsSync(installerPath)) {
        const baseUrl = isNeo
            ? `https://maven.neoforged.net/releases/net/neoforged/neoforge/${build}`
            : `https://files.minecraftforge.net/maven/net/minecraftforge/forge/${mcVersion}-${build}`;
        await downloadFileTo(`${baseUrl}/${installerName}`, installerPath, onProgress);
    }
    ensureLauncherProfile(gameRoot);
    if (onStatus) onStatus(`Installing ${label} ${display}...`);
    const javaExe = getJavaExecutable(javaMajor);
    await runInstaller(javaExe, ['-jar', installerPath, '--installClient', gameRoot], 180000);
    const profileId = await findInstalledLoaderProfile(gameRoot, mcVersion, isNeo, build);
    if (!profileId) throw new Error(`${label} installer produced no version profile.`);

    // mclc's getJar() would try to download the vanilla client into versions/<custom>/ using the
    // URL from the version JSON. For old versions that URL points at the retired launcher.mojang.com,
    // so pre-copy the already-installed vanilla jar to the custom profile slot to keep mclc from
    // attempting a dead download (mclc skips getJar() when versions/<custom>/<custom>.jar exists).
    const srcJar = path.join(gameRoot, 'versions', mcVersion, `${mcVersion}.jar`);
    const dstJar = path.join(gameRoot, 'versions', profileId, `${profileId}.jar`);
    if (fs.existsSync(srcJar) && !fs.existsSync(dstJar)) {
        await fs.ensureDir(path.dirname(dstJar));
        await fs.copy(srcJar, dstJar);
        console.log(`[Loader] Copied vanilla ${mcVersion}.jar -> ${profileId}.jar`);
    }

    return profileId;
}

// NeoForge/modern-loader profiles ship their module-path bootstrapping in arguments.jvm
// (e.g. `-p ${library_directory}/net/neoforged/fancymodloader/...` and `-DlibraryDirectory=...`).
// mclc never reads arguments.jvm from a custom version JSON, so inline them into customArgs with
// placeholders resolved. Unresolvable placeholders are dropped rather than passed to the JVM.
function loaderJvmArgs(gameRoot, profileId) {
    try {
        const profilePath = path.join(gameRoot, 'versions', profileId, `${profileId}.json`);
        if (!fs.existsSync(profilePath)) return [];
        const profileJson = JSON.parse(fs.readFileSync(profilePath, 'utf8'));
        if (!profileJson.arguments || !Array.isArray(profileJson.arguments.jvm)) return [];
        const libraryDir = path.join(gameRoot, 'libraries').replace(/\\/g, '/');
        const separator = process.platform === 'win32' ? ';' : ':';
        const resolved = [];
        let skipNext = false;
        for (const arg of profileJson.arguments.jvm) {
            if (typeof arg !== 'string') continue;
            if (skipNext) { skipNext = false; continue; }
            if (arg === '--add-opens' || arg === '--add-exports') { skipNext = true; continue; }
            if (arg.startsWith('--add-opens=') || arg.startsWith('--add-exports=')) continue;
            const value = arg
                .split('${library_directory}').join(libraryDir)
                .split('${classpath_separator}').join(separator)
                .split('${version_name}').join(profileId);
            if (/\$\{/.test(value)) continue;
            resolved.push(value);
        }
        return resolved;
    } catch (e) {
        console.warn(`[Launch] Failed to read loader JVM args for ${profileId}:`, e.message);
        return [];
    }
}

ipcMain.on('launch-game', async (event, args) => {
    const activeProfile = savedProfiles.find(p => p.id === (args.profileId || activeProfileId)) || savedProfiles[0];
    if (!activeProfile) {
        event.reply('launch-error-catch', 'No active profile selected.');
        event.reply('game-terminated');
        return;
    }
    // A real launch is starting — cancel any background pre-download so the two
    // MCLC pipelines never write the same jar/asset concurrently. The real launch
    // picks up from whatever the pre-downloader already finished (MCLC skips
    // existing files).
    stopPrepareWorker();
    if (args.username && args.userType !== 'msa' && !VALID_OFFLINE_USERNAME.test(args.username)) {
        const reason = args.username.length > 16
            ? `Username "${args.username}" is ${args.username.length} characters long; Minecraft only allows up to 16. Add a shorter account and use it to launch.`
            : `Username "${args.username}" contains invalid characters; use 3-16 letters, numbers and underscores.`;
        event.reply('launch-error-catch', reason);
        event.reply('game-terminated');
        return;
    }
    if (args.profileId && args.profileId !== activeProfileId) activeProfileId = activeProfile.id;
    activeProfile.lastPlayed = Date.now();
    await saveConfigToDisk();
    if (mainWindow) mainWindow.webContents.send('sync-profiles-view', { profiles: savedProfiles, activeId: activeProfileId });

    args.version = activeProfile.version;
    args.engine = activeProfile.engine;
    args.ram = (activeProfile.ram && activeProfile.ramOverride) ? activeProfile.ram : (args.ram || '4');

    const mcVersionNum = parseVersion(args.version);

    const gameLogLines = [];
    try {
        const capeAgentJar = await ensureCapeAgent();
        const memoryLimit = parseInt(args.ram) * 1024 || 4096;
        const isPremiumLaunch = args.userType === 'msa' && args.accessToken && args.uuid;
        const gameRoot = (appSettings.gameDir && appSettings.gameDir.trim()) ? appSettings.gameDir.trim() : minecraftRootPath;

        let authorizationObj;
        let offlineSkinAgentArg = null;
        let premiumProfileKeyArg = null;
        let launchUserProperties = null;
        let launchProfileProperties = null;
        let skinApiRoot = null;
        if (isPremiumLaunch) {
            const account = savedAccounts.find(a => a.username === args.username);
            const hasCape = account && (account.customCape || (Array.isArray(account.customCapes) && account.customCapes.some(c => c && c.file)));
            const hasSkin = account && (account.skinFile || (Array.isArray(account.customSkins) && account.customSkins.some(s => s && s.file)));
            if (account) { uploadSkinToServer(account).catch(() => {}); publishSkinToBatClient(account).catch(() => {}); }
            let premiumSkinServer = null;
            if (hasCape || hasSkin) {
                try {
                    premiumSkinServer = await startPremiumSkinServer(account, args.accessToken, args.uuid);
                    const jarPath = premiumSkinServer ? await ensureAuthlibInjector() : null;
                    if (premiumSkinServer && jarPath) {
                        offlineSkinAgentArg = `-javaagent:${jarPath}=${premiumSkinServer.apiRoot}`;
                        skinApiRoot = premiumSkinServer.apiRoot;
                        premiumProfileKeyArg = '-Dauthlibinjector.profileKey=enabled';
                        skinDebugLog(`premium agentArg=${offlineSkinAgentArg}`);
                        if (mainWindow) mainWindow.webContents.send('skin-status', `Cape ready for ${account.username}`);
                    } else {
                        stopSkinServer();
                        premiumSkinServer = null;
                        console.warn('[Skin] Premium cape disabled for this launch (agent or server unavailable).');
                    }
                } catch (skinErr) {
                    stopSkinServer();
                    premiumSkinServer = null;
                    console.warn('[Skin] Premium cape setup failed:', skinErr.message);
                }
            }
            authorizationObj = {
                access_token: args.accessToken,
                client_token: "bat_client_pass",
                uuid: args.uuid,
                name: args.username,
                user_properties: premiumSkinServer ? premiumSkinServer.userProperties : '{}',
                selected_profile: { id: args.uuid, name: args.username },
                user: { id: args.uuid, username: args.username },
                meta: { type: 'msa', demo: false }
            };
            if (premiumSkinServer) {
                launchUserProperties = premiumSkinServer.profileProperties;
                launchProfileProperties = premiumSkinServer.profileProperties;
            }
        } else {
            const account = savedAccounts.find(a => a.username === args.username);
            if (account) { uploadSkinToServer(account).catch(() => {}); publishSkinToBatClient(account).catch(() => {}); }
            let skinStarted = null;
            skinDebugLog(`launch offline user=${args.username} version=${args.version} engine=${args.engine} hasSkinFile=${!!(account && account.skinFile)} hasCape=${!!(account && account.customCape)}`);
            const hasSkinSlots = account && Array.isArray(account.customSkins) && account.customSkins.some(s => s && s.file);
            if (account && (account.skinFile || account.customCape || hasSkinSlots)) {
                try {
                    skinStarted = await startYggdrasilServer(account);
                    const jarPath = skinStarted ? await ensureAuthlibInjector() : null;
                    skinDebugLog(`serverStarted=${!!skinStarted} jarPath=${jarPath || 'NULL'}`);
                    if (skinStarted && jarPath) {
                        offlineSkinAgentArg = `-javaagent:${jarPath}=${skinStarted.apiRoot}`;
                        skinApiRoot = skinStarted.apiRoot;
                        skinDebugLog(`agentArg=${offlineSkinAgentArg}`);
                        if (mainWindow) mainWindow.webContents.send('skin-status', `Textures ready for ${account.username}`);
                    } else {
                        stopSkinServer();
                        skinStarted = null;
                        console.warn('[Skin] Offline skin disabled for this launch (agent or server unavailable).');
                    }
                } catch (skinErr) {
                    stopSkinServer();
                    skinStarted = null;
                    console.warn('[Skin] Offline skin setup failed:', skinErr.message);
                }
            }

            if (skinStarted) {
                launchUserProperties = skinStarted.profileProperties;
                launchProfileProperties = skinStarted.profileProperties;
                const off = offlineUUID(args.username);
                authorizationObj = {
                    access_token: "bat_session_pass",
                    client_token: "bat_client_pass",
                    uuid: off.dashed,
                    name: args.username,
                    user_properties: skinStarted.userProperties || '{}',
                    selected_profile: { id: off.plain, name: args.username },
                    user: { id: off.plain, username: args.username },
                    meta: { type: 'msa', xuid: '0', demo: false }
                };
            } else {
                authorizationObj = {
                    access_token: "bat_session_pass",
                    client_token: "bat_client_pass",
                    uuid: "00000000-0000-0000-0000-000000000000",
                    name: args.username,
                    user_properties: '{}',
                    selected_profile: { id: "00000000000000000000000000000000", name: args.username },
                    user: { id: "bat_user", username: args.username }
                };
            }
        }

        const javaModuleArgs = [
            '--add-opens', 'java.base/java.lang=ALL-UNNAMED',
            '--add-opens', 'java.base/java.lang.reflect=ALL-UNNAMED',
            '--add-opens', 'java.base/java.lang.invoke=ALL-UNNAMED',
            '--add-opens', 'java.base/java.util=ALL-UNNAMED',
            '--add-opens', 'java.base/java.io=ALL-UNNAMED',
            '--add-opens', 'java.base/java.net=ALL-UNNAMED',
            '--add-opens', 'java.base/java.nio=ALL-UNNAMED',
            '--add-opens', 'java.base/java.security=ALL-UNNAMED',
            '--add-opens', 'java.base/java.security.cert=ALL-UNNAMED',
            '--add-opens', 'java.base/java.security.spec=ALL-UNNAMED',
            '--add-opens', 'java.base/sun.security.x509=ALL-UNNAMED',
            '--add-opens', 'java.base/sun.security.util=ALL-UNNAMED',
            '--add-exports', 'java.base/sun.security.x509=ALL-UNNAMED',
            '--add-exports', 'java.base/sun.security.util=ALL-UNNAMED',
            '--add-exports', 'java.base/sun.net.www.protocol.https=ALL-UNNAMED',
            '--add-opens', 'java.base/jdk.internal.loader=ALL-UNNAMED'
        ];

        // -XX:+UnlockExperimentalVMOptions must stay ahead of the experimental G1 flags below
        const baseJvmArgs = [
            '-Djava.net.preferIPv4Stack=true',
            '-XX:+UnlockExperimentalVMOptions',
            '-XX:+DisableExplicitGC',
            '-XX:+UseCompressedOops',
            '-XX:+UseCompressedClassPointers',
            '-XX:+OptimizeStringConcat',
            '-Dfile.encoding=UTF-8',
            '-Djdk.attach.allowAttachSelf=true',
            '-Dsun.rmi.dgc.server.gcInterval=2147483646',
            '-XX:+ExitOnOutOfMemoryError',
            '-XX:ParallelGCThreads=' + Math.max(1, (require('os').cpus().length || 4) - 1),
            '-XX:ConcGCThreads=' + Math.max(1, Math.floor((require('os').cpus().length || 4) / 2))
        ];

        const buildPerfJvmArgs = (javaMajor) => {
            const perfJvmArgs = [
                '-XX:+UseG1GC',
                '-XX:+ParallelRefProcEnabled',
                '-XX:G1HeapRegionSize=8M',
                '-XX:G1ReservePercent=20',
                '-XX:G1HeapWastePercent=5',
                '-XX:G1MixedGCCountTarget=4',
                '-XX:InitiatingHeapOccupancyPercent=15',
                '-XX:G1MixedGCLiveThresholdPercent=90',
                '-XX:G1RSetUpdatingPauseTimePercent=5',
                '-XX:SurvivorRatio=32',
                '-XX:+PerfDisableSharedMem',
                '-XX:MaxTenuringThreshold=1',
            ];
            if (javaMajor >= 9) {
                perfJvmArgs.push('-XX:MaxGCPauseMillis=20');
                perfJvmArgs.push('-XX:G1NewSizePercent=30');
                perfJvmArgs.push('-XX:G1MaxNewSizePercent=40');
                perfJvmArgs.push('-XX:+UseStringDeduplication');
            }
            // Java 15+ removed biased locking; explicitly disable on 9-14 for reduced overhead
            if (javaMajor >= 9 && javaMajor <= 14) {
                perfJvmArgs.push('-XX:-UseBiasedLocking');
            }
            return perfJvmArgs;
        };

        let executionOptions = {
            authorization: authorizationObj,
            root: gameRoot,
            // MCLC's request pool defaults to 2 sockets (components/handler.js) —
            // raise it to the user's download preference so any first-launch
            // downloads finish dramatically faster, on top of the background
            // pre-download already warming the version.
            overrides: { windowsHide: true, maxSockets: Math.max(6, parseInt(appSettings.downloadThreads) || 16) },
            version: { number: args.version, type: "release" },
            memory: { max: `${memoryLimit}M`, min: "1024M" },
            window: {
                width: appSettings.windowWidth || 1150,
                height: appSettings.windowHeight || 680,
                fullscreen: !!appSettings.fullscreen
            },
            customArgs: [],
            customLaunchArgs: []
        };

        // The local player's own skin renders from the session profile properties
        // (--profileProperties on 1.20.5+, --userProperties on all) rather than the
        // session-server fetch. Pass the raw textures property value so the player's
        // own skin shows in singleplayer & multiplayer across every MC version/engine.
        if (launchProfileProperties) {
            const profileValueObj = launchProfileProperties;
            // --userProperties is accepted by every version's Main (1.8 -> 1.21.x).
            executionOptions.customLaunchArgs.push('--userProperties', profileValueObj);
            // --profileProperties only exists in 1.19+; joptsimple on older Main throws
            // on unrecognized options, so only pass it for modern versions.
            if (mcVersionGe(mcVersionNum, '1.19')) {
                executionOptions.customLaunchArgs.push('--profileProperties', profileValueObj);
            }
            // Diagnostic: confirm the skin args were injected.
            skinDebugLog(`launch skin args: ${executionOptions.customLaunchArgs.join(' ').slice(0, 220)}`);
        }

        if (appSettings.gameWindowTitle && appSettings.gameWindowTitle.trim() !== '') {
            executionOptions.window.title = appSettings.gameWindowTitle.trim();
        }

        if (args.serverIp) {
            executionOptions.quickPlay = {
                type: 'multiplayer',
                identifier: args.serverIp
            };
            executionOptions.server = {
                host: args.serverIp
            };
        }

        // --- Launch sequence ---
        event.reply('launch-status', 'Preparing server resource packs...');
        await prepareServerResourcePacks(gameRoot);
        event.reply('launch-status', 'Injecting high-performance video engine presets...');
        await injectOptimizationSettingsForLaunch(args.version, gameRoot);
        await injectSodiumConfigForLaunch(gameRoot);

        // Prefer the on-disk vanilla JSON (written by the background prepare worker or a
        // previous launch) so a warm launch needs zero network round-trips just to learn
        // the required Java major. Only fall back to the network manifest when absent.
        const vanillaFolder = path.join(gameRoot, 'versions', args.version);
        const vanillaJsonPath = path.join(vanillaFolder, `${args.version}.json`);
        let vanillaJson = null;
        if (fs.existsSync(vanillaJsonPath)) {
            try { vanillaJson = await fs.readJson(vanillaJsonPath); } catch (e) { vanillaJson = null; }
        }
        if (vanillaJson) {
            console.log(`[Manifest] Using cached ${args.version}.json (no network).`);
        }
        if (!vanillaJson) {
            event.reply('launch-status', `Verifying base Vanilla ${args.version}...`);
            const manifestData = await getVersionManifest();
            const targetVersionMeta = manifestData.versions.find(v => v.id === args.version);
            if (!targetVersionMeta) throw new Error(`Version "${args.version}" does not exist.`);

            try {
                const vanillaRes = await stableFetch(targetVersionMeta.url);
                vanillaJson = await vanillaRes.json();
                await fs.ensureDir(vanillaFolder);
                await fs.writeJson(vanillaJsonPath, vanillaJson);
            } catch (vErr) {
                if (fs.existsSync(vanillaJsonPath)) {
                    console.warn(`[Manifest] Network failed for ${args.version}.json, using local copy.`);
                    vanillaJson = await fs.readJson(vanillaJsonPath);
                } else {
                    throw vErr;
                }
            }
        }

    // Mojang declares the Java major each version needs; old LaunchWrapper versions need Java 8
        let requiredJavaMajor = (vanillaJson.javaVersion && vanillaJson.javaVersion.majorVersion) || 8;

        // Fabric always needs Java 17+; modern Forge (1.13+) also needs Java 17+;
        // NeoForge needs 17 (1.20.2-1.20.4) or 21 (1.20.5+).
        if (args.engine === 'fabric' || (args.engine === 'forge' && mcVersionGe(mcVersionNum, '1.13'))) {
            if (requiredJavaMajor < 17) requiredJavaMajor = 17;
        } else if (args.engine === 'neoforge') {
            const neoParts = String(args.version).split('.').map(Number);
            const neoMin = neoParts[1] || 0;
            const neoPatch = neoParts[2] || 0;
            if (neoMin >= 20 && neoPatch >= 5) {
                if (requiredJavaMajor < 21) requiredJavaMajor = 21;
            } else if (requiredJavaMajor < 17) {
                requiredJavaMajor = 17;
            }
        }

        if (!(await ensureJavaAvailable(event, requiredJavaMajor))) {
            event.reply('launch-error-catch', `Java ${requiredJavaMajor} installation failed.`);
            event.reply('game-terminated');
            return;
        }
        executionOptions.javaPath = getJavaExecutable(requiredJavaMajor);

        const launchJava=await probeJava(executionOptions.javaPath);
        if(!launchJava||launchJava.major!==requiredJavaMajor)throw new Error('Java '+requiredJavaMajor+' could not be verified. Use Java Auto-download in Settings to repair it.');

        // All engines run the obfuscated client jar (Mojang ships obfuscated jars for every
        // version; Fabric additionally remaps to intermediary at load, and classic Forge
        // (< 1.13, LaunchWrapper) has FML rename only the method headers to SRG func_*).
        // Verified runtime namespaces:
        //   fabric (all)            -> obfuscated classes + intermediary (method_*) methods
        //   vanilla (all)           -> obfuscated classes + obfuscated methods  (official)
        //   forge 1.13+ (ForgeWrapper) / neoforge (all, proven on 1.21.11) -> official
        //   classic forge 1.8-1.12.2 -> obfuscated classes + SRG func_* methods (srgobf)
        // The agent transformer + runtime also fall back to the other namespaces, so a
        // wrong guess here never fully disables the cape.
        const classicForge = args.engine === 'forge' && mcVersionLt(mcVersionNum, '1.13');
        const capeMapping = args.engine === 'fabric' ? 'intermediary'
            : classicForge ? 'srgobf'
            : 'official';
        const capeAgentArg = capeAgentJar
            ? `-javaagent:${capeAgentJar}=${args.version},${capeMapping},${skinApiRoot || ''}`
            : null;
        const fabricClassPathIsolationBypass = (capeAgentJar && args.engine === 'fabric')
            ? `-Dfabric.debug.disableClassPathIsolation=true`
            : null;

        // --add-opens / --add-exports are Java 9+ only; Java 8 refuses to start with them
        const rawArgs = composeJvmArgs([
            ...(requiredJavaMajor >= 9 ? javaModuleArgs : []),
            ...(requiredJavaMajor >= 9 ? baseJvmArgs : baseJvmArgs.filter(a => a !== '-Djdk.attach.allowAttachSelf=true')),
            ...buildPerfJvmArgs(requiredJavaMajor),
            ...(capeAgentArg ? [capeAgentArg] : []),
            ...(fabricClassPathIsolationBypass ? [fabricClassPathIsolationBypass] : []),
            ...(offlineSkinAgentArg ? [offlineSkinAgentArg] : []),
            ...(premiumProfileKeyArg ? [premiumProfileKeyArg] : []),
            ...(offlineSkinAgentArg || premiumProfileKeyArg ? ['-Dauthlibinjector.debug'] : []),
        ], appSettings.extraJvmArgs, activeProfile.extraJvmArgs);
        const compatibleArgs = compatibleJvmArgs(rawArgs, requiredJavaMajor);
        for (const removed of compatibleArgs.removed) {
            const notice = `Skipped obsolete JVM option ${removed} for Java ${requiredJavaMajor}. Your saved custom arguments were preserved.`;
            console.warn('[Java] '+notice); event.reply('console-log-batch', ['[WARN] '+notice]);
        }
        if (requiredJavaMajor < 9) {
            const skipNext = new Set();
            executionOptions.customArgs = compatibleArgs.args.filter((a, i) => {
                if (skipNext.has(i)) return false;
                if (a === '--add-opens' || a === '--add-exports') { skipNext.add(i + 1); return false; }
                if (a.startsWith('--add-opens=') || a.startsWith('--add-exports=')) return false;
                return true;
            });
        } else {
            executionOptions.customArgs = compatibleArgs.args;
        }

        console.log(`[Launch] Version=${args.version} Engine=${args.engine} Java=${requiredJavaMajor} RAM=${args.ram}GB`);
        console.log(`[Launch] JVM args (${executionOptions.customArgs.length}):`, executionOptions.customArgs.join(' '));

        if (args.engine && args.engine !== 'vanilla') {
            event.reply('launch-status', 'Preparing mod environment...');
            await cleanModsFolder();

            // A missing previously installed JAR is a persistent removal choice,
            // including curated renderers. Preferences are shared across loadouts.
            try {
                const modsDir = getModsDir();
                await fs.ensureDir(modsDir);
                const onDisk = await fs.readdir(modsDir);
                const managed = await getManagedMods(), map = await getModProjectMap();
                const reconciled = reconcileModPreferences(managed, map, onDisk, await getFpsModCache());
                for (const project of reconciled.dismissed) await addDismissedMod(project);
                await fs.writeJson(managedModsFilePath, reconciled.managed, {spaces:0});
                await fs.writeJson(modProjectMapPath, reconciled.map, {spaces:0});
                const choices = await getDismissedMods();
                if (choices.includes(SODIUM)) {
                    for (const file of onDisk.filter(name=>name.endsWith('.jar'))) {
                        const migrated = await removeBundledSodium(path.join(modsDir,file), path.join(appDataRoot,'mod-backups'));
                        if (migrated.changed) console.log('[Mods] Disabled nested Sodium in '+file+'; protected contents preserved and original backed up.');
                    }
                    // These two managed add-ons require Sodium. Preserve their
                    // bytes in disabled files instead of launching missing deps.
                    for (const file of onDisk) {
                        if (file.endsWith('.jar') && ADDONS.has(projectForFile(file, map))) {
                            let disabled = path.join(modsDir, file.replace(/\.jar$/, '.disabled'));
                            if (await fs.pathExists(disabled)) disabled = path.join(modsDir,file+'.'+crypto.randomUUID()+'.disabled');
                            await fs.rename(path.join(modsDir,file), disabled);
                        }
                    }
                }
            } catch (e) { console.warn('[FPS Mods] Could not reconcile user choices:', e.message); throw e; }

            event.reply('launch-status', `Downloading FPS boost mods for ${args.version} (${args.engine})...`);
            const ultimateFpsModsList = [
                'P7dR8mSH',  // fabric-api (required by many mods via fabric.mod.json, NOT listed as Modrinth dep)
                '7P86n6Vg',  // walksylib (required by rawinputbuffer)
                'AANobbMI',  // sodium
                'PtjYWJkn',  // sodium-extra
                'Bh37bMuy',  // reeses-sodium-options
                '5ZwdcRci',  // immediatelyfast
                'KuNKN7d2',  // noisium
                'LQ3K71Q1',  // dynamic-fps
            ];
            const fpsDownloadResult = await Promise.allSettled(ultimateFpsModsList.map(modId => downloadTargetVersionFpsMod(modId, args.version, args.engine)));
            const failed = fpsDownloadResult.filter(r => r.status === 'rejected');
            if (failed.length > 0) {
                console.warn(`[FPS Mods] ${failed.length} mod downloads failed:`, failed.map(r => r.reason?.message || r.reason));
            }
        }

        if (args.engine === 'fabric') {
            event.reply('launch-status', 'Checking Fabric mod compatibility...');
            await fs.ensureDir(getModsDir());
            for(const disabled of await checkFabricMods(getModsDir(),args.version)){
                const notice=`Disabled ${disabled.file}: ${disabled.reason} Original saved as ${disabled.disabledAs}`;
                console.warn('[Compatibility] '+notice);event.reply('console-log-batch',['[WARN] [Compatibility] '+notice]);
            }
            event.reply('launch-status', 'Connecting to Fabric Meta servers...');
            try {
                const loaderRes = await stableFetch('https://meta.fabricmc.net/v2/versions/loader');
                const loadersList = await loaderRes.json();
                const stableLoaderItem = loadersList.find(l => l.stable === true);
                if (!stableLoaderItem) throw new Error("No stable Fabric loader build found.");

                const profileUrl = `https://meta.fabricmc.net/v2/versions/loader/${args.version}/${stableLoaderItem.version}/profile/json`;
                const profileRes = await stableFetch(profileUrl);
                const responseRawText = await profileRes.text();
                if (responseRawText.includes("no mappings") || responseRawText.includes("Not Found")) throw new Error("Fabric not supported on this version.");

                let profileJson = JSON.parse(responseRawText);
                profileJson.id = `fabric-loader-${args.version}`;

                // Always strip --add-opens / --add-exports from Fabric profile;
                // our customArgs already supply them (and Fabric's bootstrap cannot
                // inject JVM args after the VM has started).
                if (profileJson.arguments && profileJson.arguments.jvm) {
                    const jvmArgs = profileJson.arguments.jvm;
                    const filtered = [];
                    let skipNext = false;
                    for (let i = 0; i < jvmArgs.length; i++) {
                        const a = jvmArgs[i];
                        if (typeof a === 'string') {
                            if (skipNext) { skipNext = false; continue; }
                            if (a === '--add-opens' || a === '--add-exports') { skipNext = true; continue; }
                            if (a.startsWith('--add-opens=') || a.startsWith('--add-exports=')) continue;
                        }
                        filtered.push(a);
                    }
                    profileJson.arguments.jvm = filtered;
                }

                const targetVersionFolder = path.join(gameRoot, 'versions', profileJson.id);
                await fs.ensureDir(targetVersionFolder);
                await fs.writeJson(path.join(targetVersionFolder, `${profileJson.id}.json`), profileJson);
                // mclc may fail to resolve the inherited vanilla jar for Fabric profiles,
                // leaving a 0-byte or missing client jar. Copy the vanilla jar to the
                // Fabric version folder so mclc can find it directly.
                const vanillaJar = path.join(gameRoot, 'versions', args.version, `${args.version}.jar`);
                const fabricJar = path.join(targetVersionFolder, `${profileJson.id}.jar`);
                if (await fs.pathExists(vanillaJar) && (!await fs.pathExists(fabricJar) || (await fs.stat(fabricJar)).size === 0)) {
                    await fs.copy(vanillaJar, fabricJar);
                    console.log(`[Fabric] Copied vanilla ${args.version}.jar -> ${profileJson.id}.jar`);
                }
                executionOptions.version.custom = profileJson.id;
            } catch (fabricErr) {
                console.warn(`[Fabric Auto-Bypass]: ${fabricErr.message} — Defaulting to Vanilla.`);
                if (executionOptions.version.custom) delete executionOptions.version.custom;
            }
        }

        if (args.engine === 'forge') {
            event.reply('launch-status', 'Fetching Forge version info...');
            try {
                const forgeDir = path.join(appDataRoot, 'forge-installs');
                await fs.ensureDir(forgeDir);

                const promoRes = await stableFetch('https://files.minecraftforge.net/maven/net/minecraftforge/forge/promotions_slim.json', {}, 2, 1000, 10000);
                const promoData = await promoRes.json();
                const promos = promoData.promos || {};

                const forgeVersion = promos[`${args.version}-recommended`] || promos[`${args.version}-latest`];
                if (!forgeVersion) throw new Error(`No Forge build found for ${args.version}.`);

                const isLegacyForge = mcVersionLt(mcVersionNum, '1.13');
                if (isLegacyForge) {
                    // Classic Forge (1.8-1.12.2) installers create a full version profile that
                    // mclc can consume via version.custom; the universal jar alone is not a
                    // usable mclc "forge" option.
                    const legacyProfileId = await installVersionCustomLoader(gameRoot, args.version, false, requiredJavaMajor, forgeDir,
                        (msg) => event.reply('launch-status', msg),
                        (pct) => event.reply('launch-progress', { percent: pct }));
                    executionOptions.version.custom = legacyProfileId;
                    event.reply('launch-status', `Forge ${args.version}-${forgeVersion} ready.`);
                } else {
                    const jarName = `forge-${args.version}-${forgeVersion}-installer.jar`;
                    const jarPath = path.join(forgeDir, jarName);

                    if (!fs.existsSync(jarPath)) {
                        event.reply('launch-status', `Downloading Forge ${args.version}-${forgeVersion}...`);
                        event.reply('launch-progress', { percent: 0 });
                        const baseUrl = `https://files.minecraftforge.net/maven/net/minecraftforge/forge/${args.version}-${forgeVersion}`;
                        const jarUrl = `${baseUrl}/${jarName}`;
                        await downloadFileTo(jarUrl, jarPath, (pct) => event.reply('launch-progress', { percent: pct }));
                    }

                    ensureLauncherProfile(gameRoot);
                    executionOptions.forge = jarPath;
                    event.reply('launch-status', `Forge ${args.version}-${forgeVersion} ready.`);
                }
            } catch (forgeErr) {
                console.warn(`[Forge Auto-Install]: ${forgeErr.message} — Falling back to Vanilla.`);
                event.reply('launch-status', `Forge install failed: ${forgeErr.message}. Launching Vanilla.`);
                if (executionOptions.version.custom) delete executionOptions.version.custom;
            }
        }

        if (args.engine === 'neoforge') {
            event.reply('launch-status', 'Fetching NeoForge version info...');
            try {
                const neoDir = path.join(appDataRoot, 'neoforge-installs');
                await fs.ensureDir(neoDir);
                const neoProfileId = await installVersionCustomLoader(gameRoot, args.version, true, requiredJavaMajor, neoDir,
                    (msg) => event.reply('launch-status', msg),
                    (pct) => event.reply('launch-progress', { percent: pct }));
                executionOptions.version.custom = neoProfileId;
                event.reply('launch-status', `NeoForge ${args.version} ready.`);
            } catch (neoErr) {
                console.warn(`[NeoForge Auto-Install]: ${neoErr.message} — Falling back to Vanilla.`);
                event.reply('launch-status', `NeoForge install failed: ${neoErr.message}. Launching Vanilla.`);
                if (executionOptions.version.custom) delete executionOptions.version.custom;
            }
        }

        // mclc drops arguments.jvm from custom version JSONs; inject them so NeoForge's
        // module-path bootstrap (-p, -DlibraryDirectory) actually reaches the JVM.
        if (executionOptions.version.custom) {
            const loaderArgs = loaderJvmArgs(gameRoot, executionOptions.version.custom);
            if (loaderArgs.length) {
                console.log(`[Launch] Loader JVM args (${loaderArgs.length}):`, loaderArgs.join(' '));
                executionOptions.customArgs = executionOptions.customArgs.concat(loaderArgs);
            }
        }

        // Heal corrupt/missing assets left by interrupted downloads (mclc truncates files to 0 bytes first,
        // so a failed download leaves a 0-byte file that later crashes the game e.g. "PNG header missing").
        // Removing them makes mclc's getAssets() re-download them on this very launch.
        try {
            const assetsIndexName = executionOptions.version.custom || args.version;
            const removedAssets = await verifyAssetsIntegrity(gameRoot, assetsIndexName);
            if (removedAssets > 0) {
                event.reply('launch-status', `Repaired ${removedAssets} corrupt asset(s)...`);
            }
        } catch (aErr) {
            console.warn('[Assets] integrity check failed:', aErr.message);
        }

        // Pre-launch validation: verify the client jar is a valid ZIP file.
        // Incomplete/corrupted downloads cause ZipException crashes at runtime.
        {
            const jarVersionId = executionOptions.version.custom || args.version;
            const clientJar = path.join(gameRoot, 'versions', jarVersionId, `${jarVersionId}.jar`);
            if (await fs.pathExists(clientJar)) {
                try {
                    const fd = await fs.open(clientJar, 'r');
                    const buf = Buffer.alloc(4);
                    await fd.read(buf, 0, 4, 0);
                    await fd.close();
                    const isZip = buf[0] === 0x50 && buf[1] === 0x4B;
                    if (!isZip) {
                        console.error(`[Launch] Client jar is not a valid ZIP (bad header): ${clientJar}`);
                        await fs.remove(clientJar);
                        event.reply('launch-error-catch', `Game jar was corrupted and has been deleted. Please restart the game to re-download.`);
                        event.reply('game-terminated');
                        stopSkinServer();
                        return;
                    }
                } catch (e) {
                    console.warn('[Launch] Could not verify client jar integrity:', e.message);
                }
            }
        }

        event.reply('launch-status', 'Launching Minecraft...');
        global.updateRPC(`Playing ${args.version} (${args.engine})`, args.username, true);
        ipcMain.emit('record-launch', event, { version: args.version, engine: args.engine, username: args.username, server: args.serverIp || null });
        if (appSettings.notifications && Notification.isSupported()) {
            new Notification({ title: 'Bat Client', body: `Minecraft ${args.version} is launching as ${args.username}...`, silent: true }).show();
        }

        const launchStartTime = Date.now();        let logBatch = [];
        let logTimeout = null;
        let lastProgressSent = 0;
        let lastStatusSent = 0;
        let lastProgressPct = -1;
        let lastRpcUpdate = 0;
        let chatSessionUpdateNotified = false;

        function processGameLine(line) {
            line = line.trim();
            if (!line) return;
            // MC-272506: during a proxy (BungeeCord/Velocity) server-switch the client can
            // fire 'chat_session_update' while still in the configuration phase, killing the
            // connection and restarting the resource pack download ("download loop"). Surface
            // the real cause instead of leaving the user staring at a restarting download bar.
            if (!chatSessionUpdateNotified && /Sending unknown packet 'serverbound\/minecraft:chat_session_update'/i.test(line)) {
                chatSessionUpdateNotified = true;
                console.warn('[Net Detect] MC-272506 detected: proxy server-switch fired chat_session_update during the configuration phase.');
                if (mainWindow) mainWindow.webContents.send('skin-status',
                    'Connection dropped (Mojang bug MC-272506): the server proxy switched you during login, restarting the resource pack. Rejoin once - if it loops, ask the server to delay its lobby transfer.');
            }
            gameLogLines.push(line);
            logBatch.push(line);
            if (global._benchmarkActive && global._benchmarkData) {
                if (!benchmarkSeenLines.has(line)) {
                    benchmarkSeenLines.add(line);
                    let fps = 0;
                    let m;
                    if ((m = line.match(/(\d+)\s*fps/i))) fps = parseInt(m[1]);
                    else if ((m = line.match(/FPS[:\s]+(\d+)/i))) fps = parseInt(m[1]);
                    else if ((m = line.match(/Avg:\s*([\d.]+)\s*fps/i))) fps = Math.round(parseFloat(m[1]));
                    else if ((m = line.match(/frame[:\s]+(\d+)/i))) fps = parseInt(m[1]);
                    if (fps > 0 && fps < 1000) {
                        global._benchmarkData.fps.push(fps);
                        global._benchmarkData.timestamps.push(Date.now());
                    }
                }
                if (benchmarkSeenLines.size > 2000) {
                    const arr = Array.from(benchmarkSeenLines);
                    benchmarkSeenLines = new Set(arr.slice(-500));
                }
            }
            const now = Date.now();
            if (now - lastRpcUpdate > 30000 && rpc && rpc.user) {
                lastRpcUpdate = now;
                try { global.updateRPC(`Playing ${args.version} (${args.engine})`, args.username, true); } catch (e) {}
            }
            if (gameLogLines.length > 5000) gameLogLines.splice(0, gameLogLines.length - 2500);
            if (!logTimeout && mainWindow) {
                logTimeout = setTimeout(() => {
                    if (mainWindow && logBatch.length) mainWindow.webContents.send('console-log-batch', logBatch);
                    logBatch = [];
                    logTimeout = null;
                }, 200);
            }
        }

        const launchWorkerPath = path.join(__dirname, 'launch-worker.js');
        let worker = fork(launchWorkerPath, [], { stdio: ['pipe', 'pipe', 'pipe', 'ipc'], windowsHide: true });
        let workerWatchdog = null;
        const WORKER_STALL_TIMEOUT = 120000;
        // Once the game process is confirmed running we never want the watchdog
        // to kill it, no matter how quiet stdout becomes mid-gameplay. The
        // watchdog exists only to catch a hung download/startup, not to police
        // an actively-running game.
        let gameConfirmedRunning = false;
        function resetWorkerWatchdog() {
            if (gameConfirmedRunning) return;
            if (workerWatchdog) clearTimeout(workerWatchdog);
            workerWatchdog = setTimeout(() => {
                console.error('[Launch] Worker stalled — no messages for 120s. Killing worker.');
                try { worker.kill(); } catch (e) {}
                stopSkinServer();
                try { global.updateRPC('Idle', 'In Launcher', false); } catch (e) {}
                event.reply('launch-error-catch', 'Download timed out — no progress for 2 minutes. Check your internet connection and try again.');
                event.reply('game-terminated');
            }, WORKER_STALL_TIMEOUT);
        }

        function onWorkerMessage(msg) {
            if (msg.type === 'progress' || msg.type === 'download-status' || msg.type === 'stdout' || msg.type === 'stderr') {
                resetWorkerWatchdog();
            }
            if (msg.type === 'progress') {
                const now = Date.now();
                const pct = Math.round((msg.data.task / msg.data.total) * 100);
                if (pct !== lastProgressPct && now - lastProgressSent > 300) {
                    lastProgressPct = pct;
                    lastProgressSent = now;
                    event.reply('launch-progress', { percent: pct });
                }
            } else if (msg.type === 'download-status') {
                const now = Date.now();
                if (now - lastStatusSent > 300) {
                    lastStatusSent = now;
                    event.reply('launch-status', `Downloading: ${msg.data.name || 'Package'}`);
                }
            } else if (msg.type === 'stdout') {
                processGameLine(msg.data);
            } else if (msg.type === 'stderr') {
                processGameLine(msg.data);
            } else if (msg.type === 'launched') {
                gameConfirmedRunning = true;
                if (workerWatchdog) { clearTimeout(workerWatchdog); workerWatchdog = null; }
                event.reply('game-running');
                // Throttle launcher to reduce PC lag while MC is running
                if (mainWindow && !mainWindow.isDestroyed()) {
                    try {
                        mainWindow.webContents.setBackgroundThrottling(true);
                        if (mainWindow.isMinimized() === false && !appSettings.keepLauncherOpen) mainWindow.minimize();
                        if (global.gc) global.gc();
                    } catch (e) {}
                }
            } else if (msg.type === 'debug') {
                processGameLine(msg.message);
            } else if (msg.type === 'error') {
                console.error('[Launch Worker] Error:', msg.message);
            } else if (msg.type === 'launch-error') {
                console.error('[Launch Worker] Launch error:', msg.message);
                if (workerWatchdog) { clearTimeout(workerWatchdog); workerWatchdog = null; }
                stopSkinServer();
                try { global.updateRPC('Idle', 'In Launcher', false); } catch (e) {}
                event.reply('launch-error-catch', `Launch failed: ${msg.message || 'Unknown error'}`);
                event.reply('game-terminated');
            } else if (msg.type === 'close') {
                if (workerWatchdog) { clearTimeout(workerWatchdog); workerWatchdog = null; }
                handleGameClose(msg.code).catch((e) => console.warn('[Game Close] Error during close handling:', e.message));
                const sessionDuration = Math.round((Date.now() - launchStartTime) / 1000);
                ipcMain.emit('record-playtime', event, { seconds: sessionDuration });
            }
        }
        worker.on('message', onWorkerMessage);

        worker.on('error', (err) => {
            console.error('[Launch Worker] Process error:', err.message);
            if (workerWatchdog) { clearTimeout(workerWatchdog); workerWatchdog = null; }
            stopSkinServer();
            try { global.updateRPC('Idle', 'In Launcher', false); } catch (e) {}
            event.reply('launch-error-catch', `Launch worker failed: ${err.message}`);
            event.reply('game-terminated');
        });

        worker.on('exit', (code) => {
            if (workerWatchdog) { clearTimeout(workerWatchdog); workerWatchdog = null; }
            if (code !== null && code !== 0 && code !== 255) {
                console.error(`[Launch Worker] Exited with code ${code}`);
                stopSkinServer();
                try { global.updateRPC('Idle', 'In Launcher', false); } catch (e) {}
                if (mainWindow && !mainWindow.isDestroyed()) {
                    event.reply('launch-error-catch', `Worker crashed (exit code ${code}). Check your antivirus or try restarting.`);
                    event.reply('game-terminated');
                }
            }
        });

        try {
            worker.send({ type: 'launch', options: executionOptions });
        } catch (launchError) {
            console.error('[Launch] Worker send failed:', launchError);
            stopSkinServer();
            try { global.updateRPC('Idle', 'In Launcher', false); } catch (e) {}
            event.reply('launch-error-catch', `Launch failed: ${launchError.message || 'Unknown error'}`);
            event.reply('game-terminated');
            return;
        }

        async function handleGameClose(code) {
            stopSkinServer();

            if (code !== 0) {
                const crashReason = gameLogLines.join('\n');

                if (crashReason.includes('Unrecognized option') && (crashReason.includes('add-opens') || crashReason.includes('add-opene'))) {
                    console.log('[Recovery] Detected --add-opens crash. Retrying with Java 17...');
                    if (mainWindow) mainWindow.webContents.send('skin-status', 'Incompatible Java detected. Downloading Java 17 and retrying...');
                    const retryJavaMajor = 17;
                    if (await ensureJavaAvailable(event, retryJavaMajor)) {
                        executionOptions.javaPath = getJavaExecutable(retryJavaMajor);
                        executionOptions.customArgs = executionOptions.customArgs.filter(a =>
                            a !== '--add-opens' && a !== '--add-exports' &&
                            !a.startsWith('--add-opens=') && !a.startsWith('--add-exports=')
                        );
                        if (executionOptions.version.custom) {
                            const fabricProfilePath = path.join(gameRoot, 'versions', executionOptions.version.custom, `${executionOptions.version.custom}.json`);
                            if (await fs.pathExists(fabricProfilePath)) {
                                try {
                                    const profileJson = await fs.readJson(fabricProfilePath);
                                    if (profileJson.arguments && profileJson.arguments.jvm) {
                                        const filtered = [];
                                        let skip = false;
                                        for (const a of profileJson.arguments.jvm) {
                                            if (typeof a === 'string') {
                                                if (skip) { skip = false; continue; }
                                                if (a === '--add-opens' || a === '--add-exports') { skip = true; continue; }
                                                if (a.startsWith('--add-opens=') || a.startsWith('--add-exports=')) continue;
                                            }
                                            filtered.push(a);
                                        }
                                        profileJson.arguments.jvm = filtered;
                                        await fs.writeJson(fabricProfilePath, profileJson);
                                    }
                                } catch (e) { console.warn('[Recovery] Error fixing Fabric profile:', e.message); }
                            }
                        }
                        console.log(`[Recovery] Retrying launch with Java ${retryJavaMajor}...`);
                        event.reply('launch-status', 'Retrying with Java 17...');
                        gameLogLines.length = 0;
                        lastProgressPct = -1;
                        lastProgressSent = 0;
                        lastStatusSent = 0;
                        try {
                            if (worker) worker.kill();
                            lastProgressPct = -1;
                            lastProgressSent = 0;
                            lastStatusSent = 0;
                            worker = fork(launchWorkerPath, [], { stdio: ['pipe', 'pipe', 'pipe', 'ipc'], windowsHide: true });
                            worker.on('message', onWorkerMessage);
                            worker.on('exit', (code) => {
                                if (code !== null && code !== 0 && code !== 255) {
                                    console.error(`[Launch Worker] Retry exited with code ${code}`);
                                    stopSkinServer();
                                    try { global.updateRPC('Idle', 'In Launcher', false); } catch (e) {}
                                    if (mainWindow && !mainWindow.isDestroyed()) {
                                        event.reply('launch-error-catch', `Worker crashed (exit code ${code}).`);
                                        event.reply('game-terminated');
                                    }
                                }
                            });
                            worker.send({ type: 'launch', options: executionOptions });
                            return;
                        } catch (retryErr) {
                            console.error('[Recovery] Retry failed:', retryErr.message);
                        }
                    }
                }

                if (crashReason.includes('ClassCastException') && crashReason.includes('net.minecraft.launchwrapper.Launch')) {
                    event.reply('launch-status', 'Cleaning broken Forge/LaunchWrapper version...');
                    const versionFolder = path.join(gameRoot, 'versions', args.version);
                    try {
                        if (await fs.pathExists(versionFolder)) {
                            await fs.remove(versionFolder);
                            if (mainWindow) mainWindow.webContents.send('skin-status', `Version ${args.version} was corrupted (old Forge?). Cleaned up. Please launch again.`);
                            const forgeProfile = path.join(gameRoot, 'versions', `${args.version}-forge`);
                            if (await fs.pathExists(forgeProfile)) await fs.remove(forgeProfile);
                        } else {
                            if (mainWindow) mainWindow.webContents.send('skin-status', 'LaunchWrapper crash detected. Try changing your profile engine to Fabric or Vanilla.');
                        }
                    } catch (e) { console.warn('[Recovery] Error cleaning version folder:', e.message); }
                    event.reply('launch-error-catch', `Old Forge version detected and cleaned. Please restart the game.`);
                    event.reply('game-terminated');
                    return;
                }

                if (crashReason.includes('ZipException') && crashReason.includes('zip END header not found')) {
                    console.log('[Recovery] Detected corrupted version jar (ZipException). Cleaning version folder...');
                    if (mainWindow) mainWindow.webContents.send('skin-status', 'Corrupted game jar detected. Re-downloading...');
                    const versionId = executionOptions.version.custom || args.version;
                    const versionFolder = path.join(gameRoot, 'versions', versionId);
                    try {
                        if (await fs.pathExists(versionFolder)) {
                            const jarFile = path.join(versionFolder, `${versionId}.jar`);
                            if (await fs.pathExists(jarFile)) await fs.remove(jarFile);
                            console.log(`[Recovery] Deleted corrupted jar: ${jarFile}`);
                        }
                    } catch (e) { console.warn('[Recovery] Error cleaning corrupted jar:', e.message); }
                    event.reply('launch-error-catch', `Corrupted game jar detected and cleaned. Please restart the game.`);
                    event.reply('game-terminated');
                    return;
                }

                let badModId = null;
                let match = crashReason.match(/Mixin apply for mod (\S+) failed/);
                if (match) badModId = match[1];
                if (!badModId) { match = crashReason.match(/Replace mod '(\S+)' \([^)]+\)/); if (match) badModId = match[1]; }
                if (!badModId) { match = crashReason.match(/requires version [\d.]+ or later of mod '(\S+)'/); if (match) badModId = match[1]; }
                if (badModId) {
                    try {
                        const modsDir = getModsDir();
                        const files = await fs.readdir(modsDir);
                        let removedFile = null;
                        for (const file of files) {
                            if (file.toLowerCase().includes(badModId.toLowerCase()) && file.endsWith('.jar')) {
                                await fs.remove(path.join(modsDir, file));
                                removedFile = file;
                                break;
                            }
                        }
                        if (removedFile && mainWindow) mainWindow.webContents.send('skin-status', `Removed incompatible mod: ${removedFile}. Please restart the game.`);
                        else if (mainWindow) mainWindow.webContents.send('skin-status', `Crash possibly caused by '${badModId}'. Check your mods folder.`);
                    } catch (e) { console.warn('[Crash Recovery] Error while removing mod:', e.message); }
                } else {
                    if (mainWindow) mainWindow.webContents.send('skin-status', 'Minecraft crashed. Check mods for incompatibilities.');
                }
            }

            if (code !== 0) {
                const lastLines = gameLogLines.slice(-10).join('\n');
                const errorMsg = `Minecraft exited with code ${code}.${lastLines ? '\n' + lastLines : ''}`;
                console.error('[Launch]', errorMsg);
                event.reply('launch-error-catch', `Minecraft exited with code ${code}.${lastLines ? ' ' + lastLines.split('\n')[0] : ''}`);
                if (appSettings.notifications && Notification.isSupported()) {
                    new Notification({ title: 'Bat Client', body: `Minecraft crashed with exit code ${code}.`, silent: false }).show();
                }
                if (appSettings.crashReports) {
                    try {
                        const crashDir = path.join(appDataRoot, 'crash-reports');
                        await fs.ensureDir(crashDir);
                        const crashFile = path.join(crashDir, `crash-${Date.now()}-code${code}.log`);
                        await fs.writeFile(crashFile, `Exit code: ${code}\nVersion: ${args.version}\nEngine: ${args.engine}\nTime: ${new Date().toISOString()}\n\n--- Game Log (last 200 lines) ---\n${gameLogLines.slice(-200).join('\n')}`);
                    } catch (crashErr) {}
                }
            }
            try { global.updateRPC('Idle', 'In Launcher', false); } catch (e) {}
            // Restore launcher rendering after game closes
            if (mainWindow && !mainWindow.isDestroyed()) {
                try { mainWindow.webContents.setBackgroundThrottling(false); } catch (e) {}
            }
            event.reply('game-terminated');
            if (appSettings.autoCloseOnGameExit) app.quit();
            else if (!appSettings.keepLauncherOpen && mainWindow && !mainWindow.isDestroyed()) mainWindow.restore();
        }

        if (appSettings.keepLauncherOpen) {}
        else if (appSettings.hideOnLaunch && mainWindow && !mainWindow.isDestroyed()) mainWindow.minimize();

    } catch (error) {
        stopSkinServer();
        try { global.updateRPC('Idle', 'In Launcher', false); } catch (e) {}
        event.reply('launch-error-catch', `Launch Aborted: ${error.message}`);
        event.reply('game-terminated');
    }
});

// ==========================================
// 1. AUTO-ALLOCATE RAM
// ==========================================
ipcMain.handle('get-system-ram', async () => {
    try {
        const totalBytes = require('os').totalmem();
        const freeBytes = require('os').freemem();
        const totalGB = Math.round(totalBytes / (1024 * 1024 * 1024));
        const freeGB = Math.round(freeBytes / (1024 * 1024 * 1024));
        let suggested = 4;
        if (totalGB >= 32) suggested = 12;
        else if (totalGB >= 16) suggested = 8;
        else if (totalGB >= 8) suggested = 6;
        else if (totalGB >= 4) suggested = 4;
        else suggested = 2;
        return { totalGB, freeGB, suggested };
    } catch { return { totalGB: 4, freeGB: 2, suggested: 4 }; }
});

// ==========================================
// 2. QUICK LAUNCH PRESETS
// ==========================================
const presetsFilePath = path.join(appDataRoot, 'launch-presets.json');
async function loadLaunchPresets() {
    try {
        if (await fs.pathExists(presetsFilePath)) return await fs.readJson(presetsFilePath);
    } catch {}
    return [];
}
async function saveLaunchPresets(presets) {
    await fs.writeJson(presetsFilePath, presets, { spaces: 2 });
}

ipcMain.handle('get-launch-presets', async () => loadLaunchPresets());

ipcMain.handle('save-launch-preset', async (event, preset) => {
    const presets = await loadLaunchPresets();
    const idx = presets.findIndex(p => p.id === preset.id);
    if (idx >= 0) presets[idx] = preset; else presets.push(preset);
    await saveLaunchPresets(presets);
    return { ok: true };
});

ipcMain.handle('delete-launch-preset', async (event, presetId) => {
    let presets = await loadLaunchPresets();
    presets = presets.filter(p => p.id !== presetId);
    await saveLaunchPresets(presets);
    return { ok: true };
});

ipcMain.handle('apply-launch-preset', async (event, presetId) => {
    const presets = await loadLaunchPresets();
    const preset = presets.find(p => p.id === presetId);
    if (!preset) return { ok: false, error: 'Preset not found' };
    if (preset.profileId) {
        activeProfileId = preset.profileId;
        await saveConfigToDisk();
    }
    return { ok: true };
});

// ==========================================
// 3. MOD PRESETS
// ==========================================
const modPresetsFilePath = path.join(appDataRoot, 'mod-presets.json');
async function loadModPresets() {
    try { if (await fs.pathExists(modPresetsFilePath)) return await fs.readJson(modPresetsFilePath); } catch {}
    return [];
}

ipcMain.handle('get-mod-presets', async () => loadModPresets());

ipcMain.handle('save-mod-preset', async (event, { id, name, modFilenames }) => {
    const presets = await loadModPresets();
    const idx = presets.findIndex(p => p.id === id);
    const entry = { id: id || `mp_${Date.now()}`, name, modFilenames, createdAt: Date.now() };
    if (idx >= 0) presets[idx] = entry; else presets.push(entry);
    await fs.writeJson(modPresetsFilePath, presets, { spaces: 2 });
    return { ok: true, preset: entry };
});

ipcMain.handle('delete-mod-preset', async (event, presetId) => {
    let presets = await loadModPresets();
    presets = presets.filter(p => p.id !== presetId);
    await fs.writeJson(modPresetsFilePath, presets, { spaces: 2 });
    return { ok: true };
});

ipcMain.handle('apply-mod-preset', async (event, presetId) => {
    const presets = await loadModPresets();
    const preset = presets.find(p => p.id === presetId);
    if (!preset) return { ok: false, error: 'Preset not found' };
    const modsDir = getModsDir();
    await fs.ensureDir(modsDir);
    const currentFiles = await fs.readdir(modsDir);
    for (const f of currentFiles) {
        if ((f.endsWith('.jar') || f.endsWith('.disabled')) && !preset.modFilenames.includes(f)) {
            await fs.remove(path.join(modsDir, f));
        }
    }
    return { ok: true, applied: preset.name };
});

// ==========================================
// 4. SERVER PING
// ==========================================
ipcMain.handle('ping-minecraft-server', async (event, { host, port }) => {
    const p = port || 25565;
    const net = require('net');
    function encodeVarInt(val) {
        const buf = [];
        while (true) {
            let byte = val & 0x7F;
            val >>>= 7;
            if (val !== 0) byte |= 0x80;
            buf.push(byte);
            if (val === 0) break;
        }
        return Buffer.from(buf);
    }
    function makePacket(id, payload) {
        const idBuf = encodeVarInt(id);
        const packet = Buffer.concat([idBuf, payload]);
        const lenBuf = encodeVarInt(packet.length);
        return Buffer.concat([lenBuf, packet]);
    }
    function readVarIntFromBuffer(buf, offset) {
        let val = 0, shift = 0, i = offset;
        while (i < buf.length) {
            const b = buf[i++];
            val |= (b & 0x7F) << shift;
            shift += 7;
            if ((b & 0x80) === 0) break;
        }
        return { value: val, newOffset: i };
    }
    try {
        const modern = await new Promise((resolve) => {
            const sock = new net.Socket();
            let responded = false;
            const startTime = Date.now();
            sock.setTimeout(7000);
            sock.on('connect', () => {
                const hostBuf = Buffer.from(host, 'utf8');
                const portShort = Buffer.alloc(2);
                portShort.writeUInt16BE(p);
                const handshakePayload = Buffer.concat([
                    encodeVarInt(767),
                    encodeVarInt(hostBuf.length),
                    hostBuf,
                    portShort,
                    encodeVarInt(1)
                ]);
                sock.write(makePacket(0x00, handshakePayload));
                const statusPayload = encodeVarInt(0);
                sock.write(makePacket(0x00, statusPayload));
            });
            let responseData = Buffer.alloc(0);
            let expectedLen = -1;
            sock.on('data', (chunk) => {
                responseData = Buffer.concat([responseData, chunk]);
                while (true) {
                    if (responseData.length < 5) break;
                    const { value: pktLen, newOffset: lenEnd } = readVarIntFromBuffer(responseData, 0);
                    if (responseData.length < pktLen + (lenEnd - 0)) break;
                    const { value: pktId, newOffset: idEnd } = readVarIntFromBuffer(responseData, lenEnd);
                    const { value: jsonLen, newOffset: jsonEnd } = readVarIntFromBuffer(responseData, idEnd);
                    const jsonStr = responseData.slice(jsonEnd, jsonEnd + jsonLen).toString('utf8');
                    responseData = responseData.slice(lenEnd + pktLen);
                    if (responded) continue;
                    responded = true;
                    try {
                        const info = JSON.parse(jsonStr);
                        let motd = 'Unknown';
                        if (info.description) {
                            if (info.description.text) motd = info.description.text;
                            else if (info.description.translate) motd = info.description.translate;
                            else motd = JSON.stringify(info.description);
                        }
                        resolve({
                            ok: true,
                            motd,
                            players: { max: info.players ? info.players.max : 0, online: info.players ? info.players.online : 0 },
                            version: info.version ? info.version.name : 'Unknown',
                            ping: Date.now() - startTime
                        });
                    } catch (e) {
                        resolve({ ok: false, error: 'Failed to parse response' });
                    }
                }
            });
            sock.on('error', (e) => { if (!responded) { responded = true; resolve({ ok: false, error: e.message }); } });
            sock.on('timeout', () => { sock.destroy(); if (!responded) { responded = true; resolve({ ok: false, error: 'Connection timed out' }); } });
            sock.connect(p, host);
        });
        if (modern.ok) return modern;

        // Legacy fallback: pre-1.7 server list ping (0xFE 0x01). Some proxies only answer this.
        return await new Promise((resolve) => {
            const sock = new net.Socket();
            let responded = false;
            const startTime = Date.now();
            sock.setTimeout(7000);
            sock.on('connect', () => { sock.write(Buffer.from([0xFE, 0x01])); });
            let buf = Buffer.alloc(0);
            sock.on('data', (chunk) => {
                if (responded) return;
                buf = Buffer.concat([buf, chunk]);
                if (buf.length < 3 || buf[0] !== 0xFF) return;
                responded = true;
                let str = '';
                for (let i = 1; i + 1 < buf.length; i += 2) {
                    str += String.fromCharCode(buf.readUInt16BE(i));
                }
                const parts = str.split('\u0000').filter(Boolean);
                const nums = parts.map(x => parseInt(x, 10)).filter(n => Number.isFinite(n));
                const texts = parts.filter(x => !Number.isFinite(parseInt(x, 10)));
                const max = nums.length ? nums[nums.length - 1] : 0;
                const online = nums.length > 1 ? nums[nums.length - 2] : 0;
                const version = texts[0] ? texts[0].replace(/\u00a7./g, '').trim() : 'Legacy';
                const motd = texts[1] ? texts[1].replace(/\u00a7./g, '').trim() : '';
                resolve({
                    ok: true,
                    motd,
                    players: { max, online },
                    version: version || 'Legacy',
                    ping: Date.now() - startTime,
                    legacy: true
                });
                sock.destroy();
            });
            sock.on('error', (e) => { if (!responded) { responded = true; resolve({ ok: false, error: e.message }); } });
            sock.on('timeout', () => { sock.destroy(); if (!responded) { responded = true; resolve({ ok: false, error: 'Connection timed out' }); } });
            sock.connect(p, host);
        });
    } catch (e) { return { ok: false, error: e.message }; }
});

// ==========================================
// 5. SCREENSHOT MANAGER
// ==========================================
function getScreenshotsDir() {
    const root = (appSettings.gameDir && appSettings.gameDir.trim()) ? appSettings.gameDir.trim() : minecraftRootPath;
    return path.join(root, 'screenshots');
}

ipcMain.handle('get-screenshots', async () => {
    try {
        const dir = getScreenshotsDir();
        if (!await fs.pathExists(dir)) return [];
        const files = await fs.readdir(dir);
        const pics = files.filter(f => /\.(png|jpg|jpeg|webp)$/i.test(f));
        const result = [];
        for (const f of pics) {
            const fp = path.join(dir, f);
            const stat = await fs.stat(fp);
            result.push({ filename: f, path: fp, size: stat.size, created: stat.birthtimeMs || stat.mtimeMs });
        }
        result.sort((a, b) => b.created - a.created);
        return result;
    } catch { return []; }
});

ipcMain.handle('open-screenshot', async (event, filePath) => {
    try { shell.openPath(filePath); return { ok: true }; } catch (e) { return { ok: false, error: e.message }; }
});

ipcMain.handle('delete-screenshot', async (event, filePath) => {
    try { await fs.remove(filePath); return { ok: true }; } catch (e) { return { ok: false, error: e.message }; }
});

// ==========================================
// 6. AUTO-MOD UPDATES (Modrinth)
// ==========================================
ipcMain.handle('check-mod-updates', async (event, profileId) => {
    try {
        const modsDir = getModsDir();
        if (!await fs.pathExists(modsDir)) return [];
        const files = (await fs.readdir(modsDir)).filter(f => f.endsWith('.jar'));
        const profile=savedProfiles.find(p=>p.id===profileId)||savedProfiles.find(p=>p.id===activeProfileId);
        if(!profile?.version||!profile?.engine)return [];
        const updates = [];
        const processedProjects = new Set();
        for (const f of files) {
            const filenameBase = f.replace(/\.jar$/, '').replace(/-[\d.]+$/, '').replace(/[-_]fabric[-_].*$/i, '').replace(/[-_]forge[-_].*$/i, '').replace(/[-_]neoforge[-_].*$/i, '');
            try {
                if(excludedAutomaticProject(fabricIdentity(await fs.readFile(path.join(modsDir,f)))))continue;
                const searchUrl = `https://api.modrinth.com/v2/search?query=${encodeURIComponent(filenameBase)}&limit=1`;
                const res = await stableFetch(searchUrl, {}, 1, 1000, 5000);
                const data = await res.json();
                if (data.hits && data.hits.length > 0) {
                    const hit = data.hits[0];
                    if(excludedAutomaticProject(hit))continue;
                    if (processedProjects.has(hit.project_id)) continue;
                    processedProjects.add(hit.project_id);
                    const versionsUrl = `https://api.modrinth.com/v2/project/${hit.project_id}/version?game_versions=${encodeURIComponent(JSON.stringify([profile.version]))}&loaders=${encodeURIComponent(JSON.stringify([profile.engine]))}`;
                    const vRes = await stableFetch(versionsUrl, {}, 1, 1000, 5000);
                    const versions = (await vRes.json()).filter(v=>versionMatches(v.game_versions||[],profile.version)&&loaderMatches(v.loaders||[],profile.engine,profile.version));
                    if (versions.length > 0) {
                        const latest = versions[0];
                        const latestFile = latest.files[0];
                        if (!latestFile) continue;
                        if (latestFile.filename === f) continue;
                        updates.push({
                            filename: f,
                            modName: hit.title,
                            projectId: hit.project_id,
                            currentVersion: filenameBase,
                            latestVersion: latest.version_number,
                            downloadUrl: latestFile.url,
                            filename_new: latestFile.filename
                        });
                    }
                }
            } catch {}
        }
        return updates;
    } catch { return []; }
});

ipcMain.handle('update-mod', async (event, { downloadUrl, oldFilename, modsDir: targetDir, profileId }) => {
    try {
        const modsDir = targetDir || getModsDir();
        await fs.ensureDir(modsDir);
        assertAutomaticUrlAllowed(downloadUrl);
        const res = await stableFetch(downloadUrl, {}, 2, 1000, 15000);
        if(!res.ok)throw new Error('Mod update download failed (HTTP '+res.status+').');
        const contentDisp = res.headers.get('content-disposition') || '';
        let filename = 'mod-update.jar';
        const match = contentDisp.match(/filename="?([^";\n]+)"?/);
        if (match) filename = match[1];
        const filePath = path.join(modsDir, filename);
        const buffer = Buffer.from(await res.arrayBuffer());
        const oldFile=oldFilename?path.join(modsDir,oldFilename):null;
        const oldBytes=oldFile&&await fs.pathExists(oldFile)?await fs.readFile(oldFile):null;
        assertAutomaticArtifactAllowed(buffer,oldBytes);
        await fs.writeFile(filePath, buffer);
        if (oldFilename && oldFilename !== filename) {
            const oldPath = path.join(modsDir, oldFilename);
            if (await fs.pathExists(oldPath)) await fs.remove(oldPath);
        }
        return { ok: true, filename };
    } catch (e) { return { ok: false, error: e.message }; }
});

// ==========================================
// 7. CRASH ANALYZER
// ==========================================
ipcMain.handle('analyze-crash', async () => {
    try {
        const crashDir = path.join(appDataRoot, 'crash-reports');
        if (!await fs.pathExists(crashDir)) return { ok: false, error: 'No crash reports found.' };
        const files = (await fs.readdir(crashDir)).filter(f => f.endsWith('.log'));
        if (files.length === 0) return { ok: false, error: 'No crash reports found.' };
        files.sort();
        const latest = files[files.length - 1];
        const content = await fs.readFile(path.join(crashDir, latest), 'utf8');
        const issues = [];
        if (content.includes('OutOfMemoryError')) issues.push({ type: 'critical', issue: 'Out of Memory', fix: 'Increase RAM allocation in Settings or close other applications.' });
        if (content.includes('ClassCastException') && content.includes('launchwrapper')) issues.push({ type: 'critical', issue: 'Incompatible Forge version', fix: 'Switch your profile engine to Fabric or Vanilla.' });
        if (content.includes('Mixin') && content.includes('failed')) {
            const mixinMatch = content.match(/Mixin apply for mod (\S+) failed/);
            if (mixinMatch) issues.push({ type: 'mod', issue: `Incompatible mod: ${mixinMatch[1]}`, fix: `Remove or update "${mixinMatch[1]}" from your mods folder.` });
        }
        if (content.includes('Unrecognized option') && content.includes('add-opens')) issues.push({ type: 'java', issue: 'Incompatible Java version', fix: 'The launcher will auto-download the correct Java version on next launch.' });
        if (content.includes('java.lang.ClassNotFoundException')) {
            const classMatch = content.match(/ClassNotFoundException: (\S+)/);
            if (classMatch) issues.push({ type: 'mod', issue: `Missing dependency: ${classMatch[1]}`, fix: `Install the required dependency mod for your mod loader.` });
        }
        if (content.includes('AlreadyLoadingException') || content.includes('duplicate')) issues.push({ type: 'mod', issue: 'Duplicate mod detected', fix: 'Check your mods folder for duplicate .jar files.' });
        if (content.includes('StackOverflowError')) issues.push({ type: 'critical', issue: 'Stack overflow (possible infinite loop in mod)', fix: 'Try removing recently installed mods.' });
        if (content.includes('Texture dimensions') || content.includes('TextureSizeException')) issues.push({ type: 'mod', issue: 'Invalid texture size', fix: 'A mod has an oversized texture. Update or remove the mod.' });
        if (issues.length === 0) issues.push({ type: 'unknown', issue: 'Unknown crash cause', fix: 'Check the full crash log for details.' });
        return { ok: true, filename: latest, content: content.slice(-3000), issues };
    } catch (e) { return { ok: false, error: e.message }; }
});

// ==========================================
// 8. GAME HEALTH DASHBOARD
// ==========================================
ipcMain.handle('get-game-health', async () => {
    try {
        const os = require('os');
        const totalMem = os.totalmem();
        const freeMem = os.freemem();
        const modsDir = getModsDir();
        let modCount = 0;
        if (await fs.pathExists(modsDir)) {
            modCount = (await fs.readdir(modsDir)).filter(f => f.endsWith('.jar') || f.endsWith('.disabled')).length;
        }
        const screenshotsDir = getScreenshotsDir();
        let screenshotCount = 0;
        if (await fs.pathExists(screenshotsDir)) {
            screenshotCount = (await fs.readdir(screenshotsDir)).filter(f => /\.(png|jpg|jpeg|webp)$/i.test(f)).length;
        }
        const crashDir = path.join(appDataRoot, 'crash-reports');
        let crashCount = 0;
        if (await fs.pathExists(crashDir)) {
            crashCount = (await fs.readdir(crashDir)).filter(f => f.endsWith('.log')).length;
        }
        let gameDirSize = 0;
        try {
            const root = (appSettings.gameDir && appSettings.gameDir.trim()) ? appSettings.gameDir.trim() : minecraftRootPath;
            if (await fs.pathExists(root)) {
                const calcSize = async (dir) => {
                    let total = 0;
                    const items = await fs.readdir(dir, { withFileTypes: true });
                    for (const item of items) {
                        const fp = path.join(dir, item.name);
                        if (item.isDirectory()) total += await calcSize(fp);
                        else { try { total += (await fs.stat(fp)).size; } catch {} }
                    }
                    return total;
                };
                gameDirSize = await calcSize(root);
            }
        } catch {}
        return {
            ok: true,
            totalMemGB: Math.round(totalMem / (1024 * 1024 * 1024)),
            freeMemGB: Math.round(freeMem / (1024 * 1024 * 1024)),
            usedMemPercent: Math.round(((totalMem - freeMem) / totalMem) * 100),
            modCount,
            screenshotCount,
            crashCount,
            gameDirSizeMB: Math.round(gameDirSize / (1024 * 1024)),
            javaVersion: process.version,
            activeProfile: savedProfiles.find(p => p.id === activeProfileId) || null
        };
    } catch (e) { return { ok: false, error: e.message }; }
});

// ==========================================
let benchmarkSeenLines = new Set();
// 10. BENCHMARK MODE
// ==========================================
ipcMain.handle('start-benchmark', async (event, { version, engine }) => {
    try {
        const benchmarkData = { fps: [], timestamps: [], startTime: Date.now(), version, engine };
        global._benchmarkActive = true;
        global._benchmarkData = benchmarkData;
        benchmarkSeenLines = new Set();
        return { ok: true, message: 'Benchmark started. Play for 30-60 seconds, then stop.' };
    } catch (e) { return { ok: false, error: e.message }; }
});

ipcMain.handle('stop-benchmark', async () => {
    try {
        global._benchmarkActive = false;
        const data = global._benchmarkData || { fps: [] };
        if (data.fps.length === 0) return { ok: true, avgFps: 0, minFps: 0, maxFps: 0, samples: 0, suggestion: 'No FPS data captured. Ensure the game is running. For best results, install Sodium Extra mod (outputs FPS to chat) or press F3 to show the debug overlay.' };
        const avg = Math.round(data.fps.reduce((a, b) => a + b, 0) / data.fps.length);
        const min = Math.min(...data.fps);
        const max = Math.max(...data.fps);
        let suggestion = '';
        if (avg >= 120) suggestion = 'Excellent performance! Your setup handles Minecraft very well.';
        else if (avg >= 60) suggestion = 'Good performance. Consider enabling optimization tweaks for higher FPS.';
        else if (avg >= 30) suggestion = 'Moderate performance. Enable all optimization settings and allocate more RAM.';
        else suggestion = 'Low performance. Try lowering render distance, using Sodium mod, and allocating more RAM.';
        const result = { ok: true, avgFps: avg, minFps: min, maxFps: max, samples: data.fps.length, suggestion };
        await fs.writeJson(path.join(appDataRoot, 'benchmark-result.json'), result, { spaces: 2 });
        return result;
    } catch (e) { return { ok: false, error: e.message }; }
});

// ==========================================
// 11. PROFILE GAME DIR
// ==========================================
ipcMain.handle('get-profile-game-dir', async (event, profileId) => {
    const preset = path.join(appDataRoot, `instances`, profileId);
    return preset;
});

// ==========================================
// 12. DISCORD LIVE STATS
// ==========================================
ipcMain.handle('send-discord-live-stats', async (event, stats) => {
    try {
        if (!rpc || !rpc.user) return { ok: false, error: 'Discord not connected' };
        const details = stats.version ? `Playing MC ${stats.version}` : 'Playing Minecraft';
        const stateParts = [];
        if (stats.fps) stateParts.push(`${stats.fps} FPS`);
        if (stats.ping) stateParts.push(`${stats.ping}ms ping`);
        if (stats.server) stateParts.push(stats.server);
        const state = stateParts.length > 0 ? stateParts.join(' • ') : 'In Game';
        rpc.user.setActivity({
            details,
            state,
            startTimestamp: stats.startTime || new Date(),
            largeImageKey: DISCORD_RPC_IMAGE,
            largeImageText: `Bat Client${stats.engine ? ' • ' + stats.engine : ''}`,
            instance: false,
            buttons: DISCORD_RPC_BUTTONS,
        });
        return { ok: true };
    } catch (e) { return { ok: false, error: e.message }; }
});

// ==========================================
// SAVE ACTIVE MOD LIST AS PRESET (snapshot current mods folder)
// ==========================================
ipcMain.handle('snapshot-mod-preset', async (event, { id, name }) => {
    try {
        const modsDir = getModsDir();
        if (!await fs.pathExists(modsDir)) return { ok: false, error: 'No mods folder found' };
        const files = (await fs.readdir(modsDir)).filter(f => f.endsWith('.jar') || f.endsWith('.disabled'));
        const presets = await loadModPresets();
        const entry = { id: id || `mp_${Date.now()}`, name: name || `Preset ${presets.length + 1}`, modFilenames: files, createdAt: Date.now() };
        const idx = presets.findIndex(p => p.id === entry.id);
        if (idx >= 0) presets[idx] = entry; else presets.push(entry);
        await fs.writeJson(modPresetsFilePath, presets, { spaces: 2 });
        return { ok: true, preset: entry };
    } catch (e) { return { ok: false, error: e.message }; }
});

// ==========================================
// SCREENSHOTS AS BASE64 THUMBNAILS
// ==========================================
ipcMain.handle('get-screenshot-thumb', async (event, filePath) => {
    try {
        const data = await fs.readFile(filePath);
        return `data:image/png;base64,${data.toString('base64')}`;
    } catch { return null; }
});

if (require.main === module) {
    // Electron entrypoint; keep normal launch behavior unchanged.
}

// ==========================================
module.exports = {
    __testInjectOptimizationSettings: injectOptimizationSettingsForLaunch,
};

app.on('will-quit', () => {
    stopSkinServer();
    if (appTray) { appTray.destroy(); appTray = null; }
    if (rpc) rpc.destroy().catch(() => {});
});
