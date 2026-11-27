// auth/xbox.js
// Xbox Live authentication: exchanges the Microsoft account access token for an
// Xbox Live user token (XBL 3.0 JWT) used to obtain an XSTS token next.
'use strict';

const XBOX_USER_AUTH = 'https://user.auth.xboxlive.com/user/authenticate';
const RELYING_PARTY = 'http://auth.xboxlive.com';

async function checkStatus(res) {
    if (!res.ok) {
        let text = '';
        try { text = await res.text(); } catch (e) {}
        const err = new Error(`Xbox Live authentication failed: HTTP ${res.status} ${text.slice(0, 300)}`);
        err.status = res.status;
        throw err;
    }
    return res;
}

async function authenticate(msaAccessToken) {
    const body = {
        RelyingParty: RELYING_PARTY,
        TokenType: 'JWT',
        Properties: {
            AuthMethod: 'RPS',
            SiteName: 'user.auth.xboxlive.com',
            RpsTicket: `d=${msaAccessToken}` // 'd=' prefix for delegated (Microsoft account) tickets
        }
    };
    const res = await fetch(XBOX_USER_AUTH, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
            'x-xbl-contract-version': '1'
        },
        body: JSON.stringify(body)
    });
    await checkStatus(res);
    const data = await res.json();
    const xui = data.DisplayClaims && data.DisplayClaims.xui && data.DisplayClaims.xui[0];
    return {
        token: data.Token,
        uhs: xui ? xui.uhs : null,
        xuid: xui ? xui.xid : null,
        notAfter: data.NotAfter || null
    };
}

module.exports = { authenticate, RELYING_PARTY, XBOX_USER_AUTH };