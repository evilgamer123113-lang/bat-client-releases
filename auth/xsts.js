// auth/xsts.js
// XSTS token exchange: turns the Xbox Live user token into an XSTS JWT scoped to
// api.minecraftservices.com, which is what the Minecraft services login consumes.
'use strict';

const XSTS_AUTHORIZE = 'https://xsts.auth.xboxlive.com/xsts/authorize';
const MC_RELYING_PARTY = 'rp://api.minecraftservices.com/';

// https://wiki.vg/Microsoft_Authentication_Scheme#Authenticate_with_XSTS
const XSTS_ERRORS = {
    2148916227: 'Your account was banned by Xbox for violating Community Standards.',
    2148916228: 'Your account is currently unavailable.',
    2148916229: 'Your account is restricted and your guardian has not given permission to play online.',
    2148916232: 'Your account has not accepted Xbox Terms of Service.',
    2148916233: 'Your account does not have an Xbox profile. Create one at https://xbox.com.',
    2148916234: 'Your account has not accepted the Xbox Terms of Service.',
    2148916235: 'Your account resides in a region Xbox has not authorized.',
    2148916236: 'Your account requires proof of age.',
    2148916237: 'Your account has reached its playtime limit.',
    2148916238: 'Your account must be added to a family by an adult to play online.'
};

async function authenticate(xblToken, relyingParty = MC_RELYING_PARTY) {
    const body = {
        RelyingParty: relyingParty,
        TokenType: 'JWT',
        Properties: {
            SandboxId: 'RETAIL',
            UserTokens: [xblToken]
        }
    };
    const res = await fetch(XSTS_AUTHORIZE, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
            'x-xbl-contract-version': '1'
        },
        body: JSON.stringify(body)
    });
    let data = {};
    try { data = await res.json(); } catch (e) {}
    if (data.XErr) {
        const err = new Error(XSTS_ERRORS[data.XErr] || `Xbox Live XSTS error XErr=${data.XErr}. ${data.Message || ''}`.trim());
        err.code = 'XSTS_ERROR';
        err.xerr = data.XErr;
        throw err;
    }
    if (!res.ok || !data.Token) {
        const err = new Error(`XSTS token exchange failed: HTTP ${res.status}`);
        err.status = res.status;
        throw err;
    }
    const xui = data.DisplayClaims && data.DisplayClaims.xui && data.DisplayClaims.xui[0];
    return {
        token: data.Token,
        uhs: xui ? xui.uhs : null,
        xuid: xui ? xui.xid : null,
        notAfter: data.NotAfter || null,
        identityToken: `XBL3.0 x=${xui ? xui.uhs : ''};${data.Token}`
    };
}

module.exports = { authenticate, MC_RELYING_PARTY, XSTS_ERRORS, XSTS_AUTHORIZE };