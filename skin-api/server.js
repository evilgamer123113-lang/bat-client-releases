/**
 * Bat Client — hosted Yggdrasil-compatible skin service.
 *
 * Serves custom skins/capes for cracked/offline players so that on servers
 * configured to use this authlib-injector API root, other players can see them.
 *
 * Deployed at: https://batclient.xyz/skinapi/api/yggdrasil
 *
 * Endpoints (authlib-injector compatible, mirrored after main.js local server):
 *   GET  /api/yggdrasil                                        metadata (skinDomains, signaturePublickey)
 *   GET  /api/yggdrasil/sessionserver/session/minecraft/profile/{uuid}
 *   GET  /api/yggdrasil/sessionserver/session/minecraft/hasJoined
 *   POST /api/yggdrasil/sessionserver/session/minecraft/join
 *   POST /api/yggdrasil/authserver/authenticate | refresh
 *   GET  /api/yggdrasil/textures/{id}
 *   POST /api/skins/upload      (authenticated: launcher publishes a player's skin/cape)
 *   GET  /api/skins/{username}  (admin: inspect a stored player)
 *
 * Auth for upload: X-Bat-Token header equal to BAT_SKIN_UPLOAD_TOKEN env var.
 */
"use strict";

const http = require("http");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const { createPresenceHandler } = require("./client-presence");

const HOST = process.env.BAT_SKIN_HOST || "127.0.0.1";
const PORT = parseInt(process.env.BAT_SKIN_PORT || "8090", 10);
const DATA_DIR = process.env.BAT_SKIN_DATA_DIR || path.join(__dirname, "data");
const KEY_DIR = process.env.BAT_SKIN_KEY_DIR || path.join(__dirname, "keys");
const KEY_PATH = path.join(KEY_DIR, "yggdrasil-key.pem");
const PUB_PATH = path.join(KEY_DIR, "yggdrasil-key.pub.pem");
const INDEX_PATH = path.join(DATA_DIR, "players.json");
const SKINS_DIR = path.join(DATA_DIR, "skins");
const UPLOAD_TOKEN = process.env.BAT_SKIN_UPLOAD_TOKEN || "change-me";
const BASE_URL = process.env.BAT_SKIN_BASE_URL || "https://batclient.xyz/skinapi";
const API_ROOT = process.env.BAT_SKIN_API_ROOT || `${BASE_URL}/api/yggdrasil`;

fs.mkdirSync(DATA_DIR, { recursive: true });
fs.mkdirSync(SKINS_DIR, { recursive: true });
fs.mkdirSync(KEY_DIR, { recursive: true });

function ensurePlayersIndex() {
  if (!fs.existsSync(INDEX_PATH)) fs.writeFileSync(INDEX_PATH, "{}");
}
ensurePlayersIndex();

function readPlayers() {
  try { return JSON.parse(fs.readFileSync(INDEX_PATH, "utf8")); }
  catch (e) { return {}; }
}
function writePlayers(players) {
  fs.writeFileSync(INDEX_PATH, JSON.stringify(players, null, 2));
}

function offlineUUID(username) {
  const hash = crypto.createHash("md5").update("OfflinePlayer:" + username, "utf8").digest();
  hash[6] = (hash[6] & 0x0f) | 0x30;
  hash[8] = (hash[8] & 0x3f) | 0x80;
  return hash.toString("hex");
}

// ---- Persistent RSA key (stable across restarts so signatures verify) ----
if (!fs.existsSync(KEY_PATH)) {
  const { publicKey, privateKey } = crypto.generateKeyPairSync("rsa", { modulusLength: 2048 });
  fs.writeFileSync(PRIVATE_KEY_PATH(), privateKey.export({ type: "pkcs8", format: "pem" }));
  fs.writeFileSync(PUB_PATH, publicKey.export({ type: "spki", format: "pem" }));
}
function PRIVATE_KEY_PATH() { return KEY_PATH; }
function getPrivateKey() { return fs.readFileSync(KEY_PATH); }
function getPublicKeyPem() { return fs.readFileSync(PUB_PATH, "utf8"); }

function signTextureValue(value) {
  const signer = crypto.createSign("RSA-SHA1");
  signer.update(value, "utf8");
  return signer.sign(getPrivateKey(), "base64");
}

function playerEntry(username) {
  const players = readPlayers();
  const key = username || null;
  if (!key) return null;
  return players[key] || null;
}

function buildTextures(username, entry) {
  const uuid = offlineUUID(username);
  const skinAbs = entry && entry.skin ? path.join(SKINS_DIR, entry.skin) : null;
  const capeAbs = entry && entry.cape ? path.join(SKINS_DIR, entry.cape) : null;
  const textures = {};
  if (skinAbs && fs.existsSync(skinAbs)) {
    const skinHash = crypto.createHash("sha256").update(fs.readFileSync(skinAbs)).digest("hex");
    textures.SKIN = { url: `${API_ROOT}/textures/skin-${skinHash}` };
    if (entry.model === "slim") textures.SKIN.metadata = { model: "slim" };
  }
  if (capeAbs && fs.existsSync(capeAbs)) {
    const capeHash = crypto.createHash("sha256").update(fs.readFileSync(capeAbs)).digest("hex");
    textures.CAPE = { url: `${API_ROOT}/textures/cape-${capeHash}` };
  }
  const texturesObj = { timestamp: Date.now(), profileId: uuid, profileName: username, textures };
  const value = Buffer.from(JSON.stringify(texturesObj)).toString("base64");
  return { value, signature: signTextureValue(value), textures, uuid };
}

function server() {
  const handlePresence = createPresenceHandler();
  return http.createServer((req, res) => {
    const url = new URL(req.url, `http://${HOST}:${PORT}`);
    const urlPath = url.pathname;
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Bat-Token",
      "Cache-Control": "no-store, no-cache, must-revalidate",
      "Pragma": "no-cache"
    };
    const sendJson = (obj, code = 200) => {
      res.writeHead(code, { "Content-Type": "application/json", ...cors });
      res.end(JSON.stringify(obj));
    };
    const sendPng = (buf, code = 200) => {
      res.writeHead(code, { "Content-Type": "image/png", ...cors });
      res.end(buf);
    };

    if (req.method === "OPTIONS") { res.writeHead(204, cors); return res.end(); }
    if (handlePresence(req, sendJson, urlPath)) return;

    // ---- Texture bytes ----
    if (req.method === "GET" && urlPath.startsWith("/api/yggdrasil/textures/")) {
      const id = urlPath.replace("/api/yggdrasil/textures/", "");
      const file = path.join(SKINS_DIR, id + ".png");
      if (!file.startsWith(SKINS_DIR) || !fs.existsSync(file)) return sendJson({ error: "not found" }, 404);
      return fs.createReadStream(file).on("error", () => sendJson({ error: "read error" }, 500)).pipe(res);
    }

    // ---- Metadata ----
    if (req.method === "GET" && (urlPath === "/api/yggdrasil" || urlPath === "/api/yggdrasil/")) {
      return sendJson({
        meta: { serverName: "batclient.xyz", implementationName: "bat-client-skins", implementationVersion: "1.0" },
        skinDomains: ["127.0.0.1", "localhost", "::1", "batclient.xyz"],
        signaturePublickey: getPublicKeyPem().trim()
      });
    }

    // ---- Profile lookup by UUID ----
    if (req.method === "GET" && urlPath.startsWith("/api/yggdrasil/sessionserver/session/minecraft/profile/")) {
      const uuid = urlPath.replace("/api/yggdrasil/sessionserver/session/minecraft/profile/", "").split("?")[0].toLowerCase().replace(/-/g, "");
      const players = readPlayers();
      const username = Object.keys(players).find(u => offlineUUID(u) === uuid);
      if (!username) return sendJson({ error: "Unknown profile" }, 204);
      const entry = players[username];
      const { value, signature } = buildTextures(username, entry);
      return sendJson({ id: uuid, name: username, properties: [{ name: "textures", value, signature }] });
    }

    // ---- Offline auth passthrough ----
    if (req.method === "GET" && urlPath.endsWith("/sessionserver/session/minecraft/hasJoined")) {
      const username = url.searchParams.get("username");
      const players = readPlayers();
      if (!players[username]) return sendJson({});
      const { value, signature, uuid } = buildTextures(username, players[username]);
      // IMPORTANT: include the textures property (name/value/signature). Servers that
      // run authlib-injector against this API build the profile they broadcast to other
      // clients from THIS response — with empty properties, other players see default
      // skins even though the player uploaded one. Mojang's real hasJoined does the same.
      return sendJson({ id: uuid, name: username, properties: [{ name: "textures", value, signature }] });
    }
    if (req.method === "POST" && urlPath.endsWith("/sessionserver/session/minecraft/join")) {
      res.writeHead(204, cors); return res.end();
    }

    if (req.method === "POST" && (urlPath.endsWith("/authserver/authenticate") || urlPath.endsWith("/authserver/refresh"))) {
      return sendJson({ error: "auth not used for stats", errorMessage: "Bat Client skin API does not authenticate accounts.", cause: "Use an offline/cracked launcher auth." }, 403);
    }

    // ---- Upload (launcher publishes a player's skin/cape) ----
    if (req.method === "POST" && urlPath === "/api/skins/upload") {
      if (req.headers["x-bat-token"] !== UPLOAD_TOKEN) return sendJson({ error: "unauthorized" }, 401);
      const chunks = [];
      let size = 0;
      req.on("data", (c) => { chunks.push(c); size += c.length; if (size > 4 * 1024 * 1024) { req.destroy(); } });
      req.on("end", () => {
        try {
          const body = JSON.parse(Buffer.concat(chunks).toString("utf8"));
          const username = String(body.username || "").trim();
          if (!username) return sendJson({ error: "username required" }, 400);
          const players = readPlayers();
          players[username] = players[username] || {};
          if (body.skin && body.skin.dataUrl) {
            const b64 = body.skin.dataUrl.replace(/^data:image\/\w+;base64,/, "");
            const buf = Buffer.from(b64, "base64");
            const file = `skin-${crypto.createHash("sha256").update(buf).digest("hex")}.png`;
            fs.writeFileSync(path.join(SKINS_DIR, file), buf);
            players[username].skin = file;
            players[username].model = body.skin.model === "slim" ? "slim" : "classic";
          }
          if (body.cape && body.cape.dataUrl) {
            const b64 = body.cape.dataUrl.replace(/^data:image\/\w+;base64,/, "");
            const buf = Buffer.from(b64, "base64");
            const file = `cape-${crypto.createHash("sha256").update(buf).digest("hex")}.png`;
            fs.writeFileSync(path.join(SKINS_DIR, file), buf);
            players[username].cape = file;
          }
          writePlayers(players);
          const { value, signature } = buildTextures(username, players[username]);
          return sendJson({ ok: true, username, uuid: offlineUUID(username), signature, value });
        } catch (e) {
          return sendJson({ error: "bad request: " + e.message }, 400);
        }
      });
      return;
    }

    // ---- Inspect a player ----
    if (req.method === "GET" && urlPath.startsWith("/api/skins/")) {
      const username = decodeURIComponent(urlPath.replace("/api/skins/", "")).replace(/\/?$/, "");
      const players = readPlayers();
      const entry = players[username];
      if (!entry) return sendJson({ error: "not found" }, 404);
      return sendJson({ username, uuid: offlineUUID(username), model: entry.model || "classic", hasSkin: !!entry.skin, hasCape: !!entry.cape });
    }

    // ---- Fetch a player's raw skin/cape data by offline UUID ----
    // (Used by the launcher's local server: fetchRemotePlayerData proxies remote
    // Bat Client players onto a vanilla/offline server by re-serving their textures
    // at 127.0.0.1. Shape mirrors the legacy render server: base64 data URLs.)
    if (req.method === "GET" && urlPath.startsWith("/api/player/")) {
      const uuid = decodeURIComponent(urlPath.replace("/api/player/", "")).replace(/\/?$/, "").toLowerCase().replace(/-/g, "");
      const players = readPlayers();
      const username = Object.keys(players).find(u => offlineUUID(u) === uuid);
      if (!username) return sendJson({ error: "not found" }, 404);
      const entry = players[username];
      const readData = (file) => {
        if (!file) return null;
        const abs = path.join(SKINS_DIR, file);
        if (!fs.existsSync(abs)) return null;
        return "data:image/png;base64," + fs.readFileSync(abs).toString("base64");
      };
      return sendJson({
        username,
        uuid,
        model: entry.model || "classic",
        skin: entry.skin ? { data: readData(entry.skin), model: entry.model || "classic" } : null,
        cape: entry.cape ? { data: readData(entry.cape), animated: false } : null
      });
    }

    return sendJson({ error: "not found" }, 404);
  });
}

if (require.main === module) {
  ensurePlayersIndex();
  const srv = server();
  srv.listen(PORT, HOST, () => {
    console.log(`[SkinAPI] listening on http://${HOST}:${PORT}`);
    console.log(`[SkinAPI] apiRoot=${API_ROOT}`);
    console.log(`[SkinAPI] dataDir=${DATA_DIR}`);
  });
}

module.exports = { server, offlineUUID, buildTextures, readPlayers, writePlayers };
