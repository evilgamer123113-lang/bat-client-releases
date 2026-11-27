# Bat Client — Architecture Report (Phase 1 Audit)

## 1. Overview

Bat Client is a Minecraft Java Edition launcher/client ecosystem: an **Electron** desktop
launcher (`main.js`, ~291 KB), a **Fabric/NeoForge** in-game agent (`bat-client-agent`), an
**Express** backend (`bat-client-server`), a **Yggdrasil-compatible skin service**
(`skin-api`), and a Microsoft-OAuth flow (`auth-microsoft.js`) using `prismarine-auth`
(device-code, no password collection).

## 2. Repository layout

| Path | Role |
|---|---|
| `main.js` | Electron main process: launch, update, accounts, skins servers, IPC |
| `index.html` | Renderer UI |
| `launch-worker.js`, `launcher-worker.js` | MCLC (minecraft-launcher-core) launch subprocesses |
| `extract-worker.js` | Pure-Node zip extractor used during updates |
| `auth-microsoft.js` | Microsoft -> Xbox -> XSTS -> Minecraft login (prismarine-auth) |
| `bat-client-agent/` | Java agent: animated capes + PerformanceTuner (via ASM) |
| `bat-client-server/` | Express backend (`index.js`, JSON-file storage) |
| `skin-api/` | Yggdrasil skin service (authlib-injector compatible) |
| `scripts/build-portable.js` | Portable zip builder (adm-zip, forward-slash entries) |
| `optimization.js`, `launcher/optimization.js` | Per-version graphics/FPS presets |

## 3. Authentication

- **Microsoft accounts**: `prismarine-auth` `Authflow` device-code flow. The user is shown a
  code + verification URI; no password is ever collected. Tokens are cached per account in
  `%APPDATA%\bat-client\msa-cache`.
- **Local profiles**: offline/development profiles (`meta: { type: 'msa', xuid: '0', demo }`
  or `type: ''`). These are NOT presented as premium. Singleplayer works normally.
- Session is forwarded to MCLC via `authorization` object; premium uses real tokens.

## 4. Session / skin / Yggdrasil

- For offline profiles, the launcher starts a local Yggdrasil server
  (`startYggdrasilServer`) and injects `authlib-injector` via `-javaagent`.
- For premium, `startPremiumSkinServer` proxies real Microsoft session requests and overlays
  the launcher cape/skin.
- The game fetches the local player's profile from our server; we serve a signed textures
  property pointing at `http://127.0.0.1:<port>/textures/...`.
- **Open issue (skin)**: MC 1.21.4's client fetches the profile and gets the signed skin
  property, but does not render it for the local player. Server side (signature, PNG, format)
  is verified correct; this is the main remaining defect.

## 5. Update/distribution

- `update.json` at `https://batclient.xyz/update.json` holds `version`, `website` (Setup.exe),
  `download` (Portable.zip), `sha256` (uppercase), `changelog`, `required`.
- Launcher compares versions, downloads, verifies SHA-256 (streamed), extracts via
  `extract-worker.js` (pure Node, `process.noAsar`), then a hidden PowerShell swap backs up +
  replaces the install and relaunches.
- GitHub Releases (`evilgamer123113-lang/bat-client-releases`) host the binaries.

## 6. Backend & skin API (current)

- `bat-client-server/index.js`: Express, JSON-file player registry under `data/`
  (`{uuid}.json`), `/api/register`, skin/cape upload with `apiKey`. Uses file storage, not a DB.
- `skin-api/server.js`: authlib-injector Yggdrasil service at
  `https://batclient.xyz/skinapi/api/yggdrasil`, persistent RSA key, `/api/skins/upload`
  guarded by `X-Bat-Token`.

## 7. Deployment (current)

- VM `51.170.86.154`, user `ubuntu`, nginx webroot `/var/www/html`, skin-api under
  `/var/www/skin-api` (systemd `batclient-skin-api`, nginx `/skinapi/`).
- Domain `batclient.xyz`, public `update.json` and skin Yggdrasil endpoint.

## 8. Version handling

- Many MC versions supported via fabric/neoforge/forge installers on demand.
- The agent uses per-version mapping tables (`MappingsData`) and Fabric intermediary namespaces.
- There is currently **no formal version-adapter registry** as specified in the target
  architecture; version-specific behavior is mostly inline/case-based in `main.js`.

## 9. Security posture (current)

- No passwords collected; Microsoft tokens never logged; SHA-256 enforced on updates.
- Skin upload guarded by a shared token (embedded in each build — a known limitation).
- Backend uses JSON files, not a DB; no rate limiting yet; no dedicated `/api/v1` versioned
  contract.

## 10. Gaps vs. target production architecture

- Dedicated `/api/v1/` versioned API (health/version/releases/config/minecraft versions).
- PostgreSQL and proper schema/migrations (currently JSON files).
- Public website (currently update.json + nginx static only).
- `api.batclient.xyz` DNS + nginx + systemd service for a real backend.
- Formal version-adapter interface + registry (isolate per-MC code).
- Rate limiting, structured logging, `.env` secrets, backups, monitoring.
- Fix the local-player skin rendering on 1.21.4 via the session/Yggdrasil path (not the agent).

## 11. Recommended priority

1. **Skin fix via session/Yggdrasil path** (user priority; verified server side, only the
   1.21.4 client consumption is broken).
2. Backend `/api/v1` + PostgreSQL migration (preserve `bat-client-server` where possible).
3. Website + `api.batclient.xyz` + nginx/systemd.
4. Formal version-adapter registry.
