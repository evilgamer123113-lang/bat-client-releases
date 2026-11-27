// auth/index.js
// Bat Client Microsoft Minecraft login system.
//
// Full flow: Microsoft OAuth2 + PKCE (interactive, in a dedicated login window)
// -> Xbox Live -> XSTS -> Minecraft Services -> Minecraft profile.
//
// Only non-sensitive data (username / uuid / login status) is ever exposed to the
// renderer. Tokens live in the main process and persist (encrypted with Electron
// safeStorage when available) under <userData>/msa-accounts/.
'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const microsoft = require('./microsoft');
const xbox = require('./xbox');
const xsts = require('./xsts');
const minecraft = require('./minecraft');

// ---- Secure token store ----

let storeDir = null;
let safeStorage = null;

function configure(options = {}) {
    if (options.storeDir) storeDir = options.storeDir;
    if (options.safeStorage) safeStorage = options.safeStorage;
    else {
        try {
            // Electron main process: safeStorage encrypts with the OS DPAPI on Windows.
            const { safeStorage: ss } = require('electron');
            safeStorage = ss;
        } catch (e) { safeStorage = null; }
    }
    if (storeDir) fs.mkdirSync(storeDir, { recursive: true });
}

function accountFilePath(identifier) {
    const safe = crypto.createHash('sha256').update(identifier).digest('hex').slice(0, 24);
    return path.join(storeDir || '', `${safe}.json`);
}

function encryptSecret(plaintext) {
    if (safeStorage && safeStorage.isEncryptionAvailable()) {
        return { enc: 'safe', data: safeStorage.encryptString(plaintext).toString('base64') };
    }
    return { enc: 'b64', data: Buffer.from(plaintext, 'utf8').toString('base64') };
}

function decryptSecret(record) {
    if (!record) return null;
    try {
        if (record.enc === 'safe' && safeStorage && safeStorage.isEncryptionAvailable()) {
            return safeStorage.decryptString(Buffer.from(record.data, 'base64'));
        }
        return Buffer.from(record.data, 'base64').toString('utf8');
    } catch (e) {
        return null;
    }
}

function saveAccount(account) {
    if (!storeDir) return;
    const f = accountFilePath(account.identifier);
    const record = {
        version: 1,
        identifier: account.identifier,
        username: account.username,
        uuid: account.uuid,
        createdAt: account.createdAt || Date.now(),
        msaRefresh: encryptSecret(account.refreshToken || ''),
        mcaToken: encryptSecret(account.accessToken || ''),
        mcaExpiresAt: account.expiresAt || 0
    };
    try {
        fs.writeFileSync(f, JSON.stringify(record, null, 2));
    } catch (e) {
        console.warn('[Auth] Failed to persist account', e.message);
    }
}

function loadAccountRaw(identifier) {
    const f = accountFilePath(identifier);
    try {
        if (!fs.existsSync(f)) return null;
        return JSON.parse(fs.readFileSync(f, 'utf8'));
    } catch (e) {
        return null;
    }
}

function getAccount(identifier) {
    const raw = loadAccountRaw(identifier);
    if (!raw) return null;
    return {
        identifier: raw.identifier,
        username: raw.username,
        uuid: raw.uuid,
        msaRefreshToken: decryptSecret(raw.msaRefresh),
        mcaAccessToken: decryptSecret(raw.mcaToken),
        mcaExpiresAt: raw.mcaExpiresAt || 0
    };
}

function listAccounts() {
    if (!storeDir || !fs.existsSync(storeDir)) return [];
    let files = [];
    try { files = fs.readdirSync(storeDir).filter(f => f.endsWith('.json')); } catch (e) {}
    const accounts = [];
    for (const f of files) {
        try {
            const raw = JSON.parse(fs.readFileSync(path.join(storeDir, f), 'utf8'));
            if (raw && raw.identifier) accounts.push({
                identifier: raw.identifier,
                username: raw.username,
                uuid: raw.uuid
            });
        } catch (e) {}
    }
    return accounts;
}

function deleteAccount(identifier) {
    const f = accountFilePath(identifier);
    try { if (fs.existsSync(f)) fs.unlinkSync(f); } catch (e) {}
}

// ---- Auth chain ----

// Full interactive login: generate the PKCE session, ask the caller to open the
// login window with `url` (a dedicated second BrowserWindow), wait for the code.
// Calls finishMicrosoftLogin(code) once the window captures the redirect.
async function startMicrosoftLogin({ openWindow, onStatus }) {
    const identifier = `msa-${crypto.randomBytes(6).toString('hex')}`;
    const verifier = microsoft.generateCodeVerifier();
    const challenge = microsoft.generateCodeChallenge(verifier);
    const url = microsoft.buildAuthorizeUrl({ challenge });

    if (!openWindow) throw new Error('openWindow is required to present the Microsoft login page.');
    if (onStatus) onStatus('Opening Microsoft login page…');

    const code = await openWindow(url, { identifier });
    if (!code) {
        const err = new Error('Microsoft login was cancelled or the window was closed.');
        err.code = 'AUTH_CANCELLED';
        throw err;
    }

    return finishMicrosoftLogin({ code, verifier, identifier, onStatus });
}

// Exchange the authorization code, run the Xbox -> XSTS -> Minecraft chain, and
// return the account. Never leaks tokens to the renderer at the IPC boundary.
async function finishMicrosoftLogin({ code, verifier, identifier, onStatus }) {
    if (onStatus) onStatus('Exchanging authentication code…');
    const msTokens = await microsoft.exchangeCode({ code, verifier });

    if (onStatus) onStatus('Signing in to Xbox Live…');
    const xbl = await xbox.authenticate(msTokens.access_token);

    if (onStatus) onStatus('Verifying with Xbox Live…');
    const xstsToken = await xsts.authenticate(xbl.token);

    if (onStatus) onStatus('Logging in to Minecraft…');
    const mcLogin = await minecraft.loginWithXbox(xstsToken.identityToken);

    const owns = await minecraft.ownsMinecraftJava(mcLogin.accessToken);
    if (!owns) {
        const err = new Error('This Microsoft account does not own Minecraft: Java Edition.');
        err.code = 'NO_OWNERSHIP';
        throw err;
    }

    let profile;
    try {
        profile = await minecraft.fetchProfile(mcLogin.accessToken);
    } catch (e) {
        // Some accounts return 404 for /minecraft/profile until first play; keep going off the login name.
        profile = { id: '', name: mcLogin.username || '' };
    }

    const account = {
        identifier,
        username: profile.name || mcLogin.username || '',
        uuid: profile.id || '',
        accessToken: mcLogin.accessToken,
        refreshToken: msTokens.refresh_token || '',
        expiresAt: Date.now() + ((mcLogin.expiresIn || 86400) * 1000)
    };
    saveAccount(account);
    return account;
}

// Silent refresh of a stored account using its Microsoft refresh token. Does not
// open a window; returns null when the refresh token is invalid/expired.
async function refreshMicrosoftSession(identifier) {
    const stored = getAccount(identifier);
    if (!stored) return null;
    if (stored.mcaAccessToken && stored.mcaExpiresAt > Date.now() + 60000) {
        return { ok: true, accessToken: stored.mcaAccessToken, uuid: stored.uuid, username: stored.username };
    }
    if (!stored.msaRefreshToken) return null;
    try {
        const msTokens = await microsoft.refreshTokens({ refreshToken: stored.msaRefreshToken });
        const xbl = await xbox.authenticate(msTokens.access_token);
        const xstsToken = await xsts.authenticate(xbl.token);
        const mcLogin = await minecraft.loginWithXbox(xstsToken.identityToken);
        let profile;
        try { profile = await minecraft.fetchProfile(mcLogin.accessToken); } catch (e) { profile = null; }
        const next = {
            identifier,
            username: (profile && profile.name) || stored.username,
            uuid: (profile && profile.id) || stored.uuid,
            accessToken: mcLogin.accessToken,
            refreshToken: msTokens.refresh_token || stored.msaRefreshToken,
            expiresAt: Date.now() + ((mcLogin.expiresIn || 86400) * 1000)
        };
        saveAccount(next);
        return { ok: true, accessToken: next.accessToken, uuid: next.uuid, username: next.username };
    } catch (e) {
        return null;
    }
}

module.exports = {
    configure,
    startMicrosoftLogin,
    finishMicrosoftLogin,
    refreshMicrosoftSession,
    getAccount,
    listAccounts,
    deleteAccount,
    microsoft,
    xbox,
    xsts,
    minecraft
};