// auth/minecraft.js
// Minecraft Services authentication: exchanges the XSTS identity token for a
// Minecraft access token, then fetches the profile and ownership entitlement.
'use strict';

const LOGIN_WITH_XBOX = 'https://api.minecraftservices.com/authentication/login_with_xbox';
const PROFILE_URL = 'https://api.minecraftservices.com/minecraft/profile';
const ENTITLEMENTS_URL = 'https://api.minecraftservices.com/entitlements/mcstore';

const UA = 'MinecraftLauncher/3.2.3';

// POST https://api.minecraftservices.com/authentication/login_with_xbox
async function loginWithXbox(identityToken) {
    const res = await fetch(LOGIN_WITH_XBOX, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Accept': 'application/json', 'User-Agent': UA },
        body: JSON.stringify({ identityToken })
    });
    let data = {};
    try { data = await res.json(); } catch (e) {}
    if (!res.ok || !data.access_token) {
        const err = new Error(data.errorMessage || data.error || `Minecraft login failed: HTTP ${res.status}`);
        err.status = res.status;
        throw err;
    }
    return {
        accessToken: data.access_token,
        username: data.username || '', // anonymous game id when the account has no profile yet
        expiresIn: data.expires_in || 86400,
        tokenType: data.token_type || 'Bearer'
    };
}

// GET https://api.minecraftservices.com/minecraft/profile
async function fetchProfile(accessToken) {
    const res = await fetch(PROFILE_URL, {
        headers: { 'Accept': 'application/json', 'Authorization': `Bearer ${accessToken}`, 'User-Agent': UA }
    });
    let data = {};
    try { data = await res.json(); } catch (e) {}
    if (!res.ok) {
        // 404 = demo account with no purchased Java profile
        const err = new Error(data.errorMessage || data.error || `Profile fetch failed: HTTP ${res.status}`);
        err.status = res.status;
        err.noProfile = res.status === 404;
        throw err;
    }
    return {
        id: data.id,
        name: data.name,
        skins: data.skins || [],
        capes: data.capes || [],
        model: data.skins && data.skins[0] && data.skins[0].variant === 'SLIM' ? 'slim' : 'classic'
    };
}

// GET https://api.minecraftservices.com/entitlements/mcstore — the Minecraft account owns Java Edition
async function ownsMinecraftJava(accessToken) {
    try {
        const res = await fetch(ENTITLEMENTS_URL, {
            headers: { 'Accept': 'application/json', 'Authorization': `Bearer ${accessToken}`, 'User-Agent': UA }
        });
        if (!res.ok) return false;
        const data = await res.json();
        if (!data || !Array.isArray(data.items)) return false;
        return data.items.some(item => item && ['product_minecraft', 'game_minecraft'].includes((item.name || '').toLowerCase()));
    } catch (e) {
        return false;
    }
}

module.exports = { loginWithXbox, fetchProfile, ownsMinecraftJava, LOGIN_WITH_XBOX, PROFILE_URL, ENTITLEMENTS_URL };