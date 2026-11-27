// Read bundled mod artwork without extracting files or contacting a service.
const fs = require('node:fs/promises');
const path = require('node:path');
const AdmZip = require('adm-zip');
const cache = new Map();
const MAX_ARCHIVE = 128 * 1024 * 1024;
const MAX_ICON = 1024 * 1024;

async function readEntry(zip, name, limit) {
    if (typeof name !== 'string') return null;
    const normalized = name.replace(/\\/g, '/').replace(/^\.\//, '');
    if (normalized.startsWith('/') || normalized.split('/').includes('..')) return null;
    const entry = zip.getEntry(normalized);
    if (!entry || entry.isDirectory || entry.header.size > limit || entry.header.compressedSize > limit) return null;
    return new Promise(resolve => {
        try { entry.getDataAsync((data, error) => resolve(!error && data && data.length <= limit ? data : null)); }
        catch { resolve(null); }
    });
}

function iconPath(icon) {
    if (typeof icon === 'string') return icon;
    if (!icon || typeof icon !== 'object') return null;
    return Object.keys(icon).filter(key => Number.isFinite(Number(key)))
        .sort((a, b) => Math.abs(Number(a) - 128) - Math.abs(Number(b) - 128))
        .map(key => icon[key]).find(value => typeof value === 'string') || null;
}

function mimeType(data) {
    if (data.subarray(0, 8).equals(Buffer.from([137,80,78,71,13,10,26,10]))) return 'image/png';
    if (data[0] === 255 && data[1] === 216 && data[2] === 255) return 'image/jpeg';
    if (/^GIF8[79]a$/.test(data.subarray(0, 6).toString('ascii'))) return 'image/gif';
    if (data.subarray(0, 4).toString('ascii') === 'RIFF' && data.subarray(8, 12).toString('ascii') === 'WEBP') return 'image/webp';
    return null;
}

async function loadIcon(file) {
    try {
        const zip = new AdmZip(await fs.readFile(file));
        const candidates = [];
        for (const metadataPath of ['fabric.mod.json', 'quilt.mod.json']) {
            const data = await readEntry(zip, metadataPath, 256 * 1024);
            if (!data) continue;
            try {
                const json = JSON.parse(data.toString('utf8'));
                const metadata = json.quilt_loader?.metadata || json;
                candidates.push(iconPath(metadata.icon));
                const id = json.id || json.quilt_loader?.id;
                if (/^[a-z0-9_-]+$/i.test(id || '')) candidates.push(`assets/${id}/icon.png`, `assets/${id}/logo.png`);
                if (id === 'batclient') candidates.push('assets/batclient/web/bat-logo.png');
            } catch { /* Try another supported metadata format. */ }
        }
        for (const metadataPath of ['META-INF/mods.toml', 'META-INF/neoforge.mods.toml']) {
            const data = await readEntry(zip, metadataPath, 256 * 1024);
            if (data) candidates.push(data.toString('utf8').match(/^\s*logoFile\s*=\s*["']([^"']+)["']/m)?.[1]);
        }
        const legacy = await readEntry(zip, 'mcmod.info', 256 * 1024);
        if (legacy) { try { const json = JSON.parse(legacy.toString('utf8')); candidates.push((Array.isArray(json) ? json[0] : json.modList?.[0] || json)?.logoFile); } catch {} }
        candidates.push('icon.png', 'logo.png');
        for (const candidate of new Set(candidates.filter(Boolean))) {
            const data = await readEntry(zip, candidate, MAX_ICON);
            const type = data && mimeType(data);
            if (type) return `data:${type};base64,${data.toString('base64')}`;
        }
    } catch { /* A missing or invalid logo must not hide the installed mod. */ }
    return null;
}

async function readModIcon(file) {
    try {
        const stat = await fs.stat(file);
        if (!stat.isFile() || stat.size > MAX_ARCHIVE) return null;
        const key = path.resolve(file);
        const signature = `${stat.size}:${stat.mtimeMs}:${stat.ctimeMs}`;
        const hit = cache.get(key);
        if (hit?.signature === signature) return hit.result;
        const result = loadIcon(file);
        cache.delete(key);
        cache.set(key, { signature, result });
        if (cache.size > 128) cache.delete(cache.keys().next().value);
        return result;
    } catch { return null; }
}

module.exports = { readModIcon };
