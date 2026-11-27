// auth/microsoft.js
// Microsoft OAuth 2.0 authorization-code + PKCE flow against the live.com identity
// endpoint for the Minecraft Java client. Produces the Microsoft account access &
// refresh tokens that seed the Xbox Live -> XSTS -> Minecraft Services chain.
'use strict';

const crypto = require('crypto');

const CLIENT_ID = '00000000402b5328'; // Minecraft Java Edition Azure client id
const REDIRECT_URI = 'https://login.live.com/oauth20_desktop.srf';
const SCOPE = 'XboxLive.signin offline_access';
const AUTHORIZE_URL = 'https://login.live.com/oauth20_authorize.srf';
const TOKEN_URL = 'https://login.live.com/oauth20_token.srf';

// ---- PKCE helpers ----

function base64UrlEncode(buf) {
    return buf.toString('base64')
        .replace(/\+/g, '-')
        .replace(/\//g, '_')
        .replace(/=+$/, '');
}

// RFC 7636 code_verifier: 43-128 chars from the unreserved set.
function generateCodeVerifier() {
    return base64UrlEncode(crypto.randomBytes(48)).substring(0, 88);
}

function generateCodeChallenge(verifier) {
    return base64UrlEncode(crypto.createHash('sha256').update(verifier).digest());
}

// Builds the interactive authorize URL shown inside the dedicated login window.
function buildAuthorizeUrl({ clientId = CLIENT_ID, redirectUri = REDIRECT_URI, challenge, prompt = 'select_account' } = {}) {
    const params = new URLSearchParams({
        client_id: clientId,
        response_type: 'code',
        redirect_uri: redirectUri,
        scope: SCOPE,
        code_challenge: challenge,
        code_challenge_method: 'S256'
    });
    if (prompt) params.set('prompt', prompt);
    return `${AUTHORIZE_URL}?${params.toString()}`;
}

// ---- Token exchange ----

async function postTokenForm(form) {
    const res = await fetch(TOKEN_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'Accept': 'application/json' },
        body: new URLSearchParams(form).toString()
    });
    let json = {};
    try { json = await res.json(); } catch (e) { /* non-JSON error body */ }
    if (!res.ok || json.error) {
        const message = json.error_description || json.error || `HTTP ${res.status}`;
        const err = new Error(`Microsoft token request failed: ${message}`);
        err.code = json.error || `http_${res.status}`;
        err.status = res.status;
        throw err;
    }
    return json;
}

// Exchanges the authorization code (captured from the login window redirect) for tokens.
async function exchangeCode({ code, verifier, clientId = CLIENT_ID, redirectUri = REDIRECT_URI } = {}) {
    return postTokenForm({
        grant_type: 'authorization_code',
        client_id: clientId,
        code,
        redirect_uri: redirectUri,
        code_verifier: verifier,
        scope: SCOPE
    });
}

// Refreshes the Microsoft access + refresh token pair using a stored refresh token.
async function refreshTokens({ refreshToken, clientId = CLIENT_ID, redirectUri = REDIRECT_URI } = {}) {
    return postTokenForm({
        grant_type: 'refresh_token',
        client_id: clientId,
        refresh_token: refreshToken,
        redirect_uri: redirectUri,
        scope: SCOPE
    });
}

module.exports = {
    CLIENT_ID,
    REDIRECT_URI,
    SCOPE,
    generateCodeVerifier,
    generateCodeChallenge,
    buildAuthorizeUrl,
    exchangeCode,
    refreshTokens
};