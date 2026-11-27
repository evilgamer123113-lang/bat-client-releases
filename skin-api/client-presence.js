"use strict";
const { isIP } = require("net");

// Short-lived, self-reported mod presence. No game-server address or skin data.
function createPresenceHandler({ now = Date.now, ttl = 60000 } = {}) {
  const players = new Map();
  const limits = new Map();
  let nextCleanup = 0;
  const uuid = value => typeof value === "string" && /^[a-f0-9]{32}$/.test(value.replace(/-/g, "").toLowerCase())
    ? value.replace(/-/g, "").toLowerCase() : null;
  return function handlePresence(req, send, pathname) {
    if (pathname !== "/api/client-presence" && pathname !== "/api/client-presence/leave") return false;
    if (req.method !== "POST") { send({ error: "POST required" }, 405); return true; }
    const time = now();
    if (time >= nextCleanup) {
      for (const [id, sessions] of players) {
        for (const [session, expires] of sessions) if (expires <= time) sessions.delete(session);
        if (!sessions.size) players.delete(id);
      }
      for (const [ip, rate] of limits) if (rate.until <= time) limits.delete(ip);
      nextCleanup = time + 30000;
    }
    // nginx overwrites this header; trust it only from our loopback proxy.
    const remote = req.socket.remoteAddress;
    const forwarded = req.headers['x-forwarded-for'];
    const ip = ['127.0.0.1', '::1', '::ffff:127.0.0.1'].includes(remote)
      && typeof forwarded === 'string' && isIP(forwarded) ? forwarded : remote;
    let rate = limits.get(ip);
    if (!rate || rate.until <= time) {
      if (limits.size >= 4096) { send({ error: "busy" }, 503); return true; }
      limits.set(ip, rate = { until: time + 60000, count: 0 });
    }
    if (++rate.count > 600) { send({ error: "rate limit" }, 429); return true; }
    let bytes = 0, chunks = [], rejected = false;
    req.on("data", chunk => {
      bytes += chunk.length;
      if (bytes > 8192) {
        if (!rejected) send({ error: "request too large" }, 413);
        rejected = true; chunks = [];
      } else if (!rejected) chunks.push(chunk);
    });
    req.on("end", () => {
      if (rejected) return;
      let body;
      try { body = JSON.parse(Buffer.concat(chunks).toString("utf8")); }
      catch { return send({ error: "invalid JSON" }, 400); }
      if (!body || typeof body !== "object") return send({ error: "invalid request" }, 400);
      const id = uuid(body.uuid);
      if (!id || typeof body.session !== "string" || !/^[a-f0-9]{64}$/.test(body.session))
        return send({ error: "invalid identity" }, 400);
      if (pathname.endsWith("/leave")) {
        const sessions = players.get(id);
        if (sessions) { sessions.delete(body.session); if (!sessions.size) players.delete(id); }
        return send({ ok: true });
      }
      if (typeof body.share !== "boolean" || !Array.isArray(body.peers) || body.peers.length > 128 || body.peers.some(peer => !uuid(peer)))
        return send({ error: "invalid peers" }, 400);
      let sessions = players.get(id);
      if (body.share) {
        if (!sessions) {
          if (players.size >= 10000) return send({ error: "busy" }, 503);
          players.set(id, sessions = new Map());
        }
        if (!sessions.has(body.session) && sessions.size >= 4) return send({ error: "too many sessions" }, 429);
        sessions.set(body.session, now() + ttl);
      } else if (sessions) {
        sessions.delete(body.session);
        if (!sessions.size) players.delete(id);
      }
      const active = {};
      for (const peer of body.peers) {
        const key = uuid(peer), leases = players.get(key);
        if (!leases) continue;
        const expires = Math.max(...leases.values());
        if (expires > now()) active[key] = expires - now();
      }
      send({ ok: true, active });
    });
    return true;
  };
}

module.exports = { createPresenceHandler };
