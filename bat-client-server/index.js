const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const app = express();
const PORT = process.env.PORT || 3000;
const DATA_DIR = path.join(__dirname, 'data');

app.use(cors());
app.use(express.json({ limit: '20mb' }));

if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });

function playerPath(uuid) {
    return path.join(DATA_DIR, uuid.replace(/-/g, '').toLowerCase() + '.json');
}

function readPlayer(uuid) {
    const p = playerPath(uuid);
    if (!fs.existsSync(p)) return null;
    try { return JSON.parse(fs.readFileSync(p, 'utf8')); } catch (e) { return null; }
}

function writePlayer(uuid, data) {
    fs.writeFileSync(playerPath(uuid), JSON.stringify(data));
}

function stripKey(data) {
    if (!data) return null;
    const { apiKey, ...safe } = data;
    return safe;
}

// Register/update a player's skin+cape data
app.post('/api/register', (req, res) => {
    const { uuid, username, skin, cape, apiKey } = req.body;
    if (!uuid) return res.status(400).json({ error: 'uuid required' });
    const clean = uuid.replace(/-/g, '').toLowerCase();
    const existing = readPlayer(clean);
    if (existing && existing.apiKey && existing.apiKey !== apiKey) {
        return res.status(403).json({ error: 'wrong api key' });
    }
    const key = apiKey || (existing && existing.apiKey) || crypto.randomBytes(16).toString('hex');
    const data = {
        uuid: clean,
        username: username || (existing && existing.username) || 'Unknown',
        apiKey: key,
        skin: skin || (existing && existing.skin) || null,
        cape: cape || (existing && existing.cape) || null,
        timestamp: Date.now()
    };
    writePlayer(clean, data);
    console.log(`[register] ${data.username} (${clean}) skin=${!!data.skin} cape=${!!data.cape}`);
    res.json({ ok: true, apiKey: key });
});

// Get Yggdrasil-compatible profile
app.get('/api/profile/:uuid', (req, res) => {
    const clean = req.params.uuid.replace(/-/g, '').toLowerCase();
    const data = readPlayer(clean);
    if (!data) return res.status(404).json({ error: 'not found' });
    const base = `${req.protocol}://${req.get('host')}`;
    const textures = {};
    if (data.skin) {
        textures.SKIN = { url: `${base}/api/textures/skin/${clean}` };
        if (data.skin.model === 'slim') textures.SKIN.metadata = { model: 'slim' };
    }
    if (data.cape && data.cape.data) {
        textures.CAPE = { url: `${base}/api/textures/cape/${clean}` };
        if (data.cape.animated && Array.isArray(data.cape.frames) && data.cape.frames.length > 0) {
            textures.CAPE.metadata = {
                animated: true,
                frameCount: data.cape.frames.length,
                delays: data.cape.delays || [],
                frameBaseUrl: `${base}/api/textures/cape-frame/${clean}/`
            };
        }
    }
    const texturesJson = { timestamp: data.timestamp, profileId: clean, profileName: data.username, textures };
    const value = Buffer.from(JSON.stringify(texturesJson)).toString('base64');
    res.json({ id: clean, name: data.username, properties: [{ name: 'textures', value }] });
});

// Get raw player data (no apiKey)
app.get('/api/player/:uuid', (req, res) => {
    const clean = req.params.uuid.replace(/-/g, '').toLowerCase();
    const data = readPlayer(clean);
    if (!data) return res.status(404).json({ error: 'not found' });
    res.json(stripKey(data));
});

// Serve skin PNG
app.get('/api/textures/skin/:uuid', (req, res) => {
    const clean = req.params.uuid.replace(/-/g, '').toLowerCase();
    const data = readPlayer(clean);
    if (!data || !data.skin || !data.skin.data) return res.status(404).end();
    const raw = data.skin.data.replace(/^data:image\/\w+;base64,/, '');
    res.setHeader('Content-Type', 'image/png');
    res.setHeader('Cache-Control', 'public, max-age=60');
    res.send(Buffer.from(raw, 'base64'));
});

// Serve cape PNG
app.get('/api/textures/cape/:uuid', (req, res) => {
    const clean = req.params.uuid.replace(/-/g, '').toLowerCase();
    const data = readPlayer(clean);
    if (!data || !data.cape || !data.cape.data) return res.status(404).end();
    const raw = data.cape.data.replace(/^data:image\/\w+;base64,/, '');
    res.setHeader('Content-Type', 'image/png');
    res.setHeader('Cache-Control', 'public, max-age=60');
    res.send(Buffer.from(raw, 'base64'));
});

// Serve animated cape frame PNG
app.get('/api/textures/cape-frame/:uuid/:index', (req, res) => {
    const clean = req.params.uuid.replace(/-/g, '').toLowerCase();
    const data = readPlayer(clean);
    if (!data || !data.cape || !Array.isArray(data.cape.frames)) return res.status(404).end();
    const idx = parseInt(req.params.index, 10);
    if (isNaN(idx) || idx < 0 || idx >= data.cape.frames.length) return res.status(404).end();
    const raw = data.cape.frames[idx].replace(/^data:image\/\w+;base64,/, '');
    res.setHeader('Content-Type', 'image/png');
    res.setHeader('Cache-Control', 'public, max-age=300');
    res.send(Buffer.from(raw, 'base64'));
});

// Health check
app.get('/api/health', (req, res) => res.json({ ok: true }));

// List all registered UUIDs (admin)
app.get('/api/list', (req, res) => {
    const files = fs.readdirSync(DATA_DIR).filter(f => f.endsWith('.json'));
    const players = files.map(f => {
        try {
            const d = JSON.parse(fs.readFileSync(path.join(DATA_DIR, f), 'utf8'));
            return { uuid: d.uuid, username: d.username, hasSkin: !!(d.skin && d.skin.data), hasCape: !!(d.cape && d.cape.data), capeAnimated: !!(d.cape && d.cape.animated) };
        } catch (e) { return null; }
    }).filter(Boolean);
    res.json({ count: players.length, players });
});

app.listen(PORT, () => console.log(`Bat Client Skin Server on port ${PORT}`));
