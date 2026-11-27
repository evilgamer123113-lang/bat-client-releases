# Bat Client — `rust-v0.1.13` & `bat-mods-v6` Verified Public Release

**Date:** 2026-11-21
**Status:** **PUBLISHED & LIVE VERIFIED**

---

## 1. Bug Fixed in `0.1.13` / `bat-mods-v6`

### Black Screen After Loading Screen (`BatWebFileSystem.java`)
- **Root cause (confirmed from Linux user log `game-5dbcc3e7-fea4-496f-80d0-17dfbd318319.log`):**
  Ultralight's native `file:///` URL-to-path conversion strips all 3 slashes (`file:///`):
  - On Linux and macOS, `file:///home/crafter/.config/bat-client/minecraft_runtime/.batclient-ui/index.html` becomes `home/crafter/.config/bat-client/minecraft_runtime/.batclient-ui/index.html` (missing the leading `/`). `BatWebFileSystem.relative()` treated it as a relative path (`!candidate.isAbsolute()`), resolved it twice against `webRoot`, and returned `false` from `exists("index.html")`.
  - On Windows/Linux/macOS when the user's username or game directory contains spaces or special characters, `.toUri().toString()` percent-encodes spaces (`%20`), and when Ultralight passes the stripped path without `file:`, `Path.of(normalized)` failed to match `webRoot`.
  - As a result, `index.html` failed to load (`alpha0=0`, `upload diag #0` uploaded a blank surface, and `[bat] init ok` never fired), leaving a solid black screen after the loading screen.
- **Fix:**
  - Updated `bat-client-mod/src/main/java/com/batclient/web/BatWebFileSystem.java` to resolve POSIX paths missing a leading `/`, support `/.batclient-ui/` marker resolution (while strictly enforcing `candidate.startsWith(webRoot)` traversal confinement), decode percent-encoded characters while preserving literal `+` (`%2B`), and serve explicit MIME types (`text/html`, `text/css`, `application/javascript`, `image/png`, `image/svg+xml`, `application/json`).
  - Expanded `bat-client-mod/scripts/WebAssetMemorySmoke.java` and verified `WEB_ASSETS_IN_MEMORY_OK`.
  - Rebuilt, verified, and packed all 30 Fabric and Forge companion mod ports (`1.8` → `1.21.11`) into `bat-mods-v6`.

---

## 2. Published Releases

- **Companion Mod Bundles (`bat-mods-v6`, 30 assets):**
  `https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/bat-mods-v6`
- **Rust Launcher (`rust-v0.1.13`, 11 assets, `--prerelease --latest=false`, Classic `v2.2.97` preserved as `latest`):**
  `https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/rust-v0.1.13`
- **Git Commit:** `2957a1cf61bec62dd7268cde0a0fec67946abbf4` (`build/rust-native-0.1.13-release`)
- **Codemagic macOS Build:** `6b01de5f5f80e1d95a554557`
- **CircleCI Linux Pipeline:** `#27` (`e3d6ae56-3941-4158-bbbb-7ba4587d3b98`)

| Target | Updater Payload | Installer | Size (bytes) | SHA-256 |
|---|---|---|---|---|
| `windows-x86_64` | `Bat-Client-0.1.13-windows-x64-setup.exe` | `Bat-Client-0.1.13-windows-x64-setup.exe` | `10994515` | `7c29d6502cb1e38e6e2e607c69cfd6ee99fab3d019e02c49d07b92c6e84fcd9d` |
| `darwin-aarch64` | `Bat-Client-0.1.13-macos-apple-silicon.app.tar.gz` | `Bat-Client-0.1.13-macos-apple-silicon.dmg` | `13390223` | `44a5e2588def741414dccb47dba2e8c6ae0a42b462c08a28b4ce4ecc3adb8828` |
| `darwin-x86_64` | `Bat-Client-0.1.13-macos-intel.app.tar.gz` | `Bat-Client-0.1.13-macos-intel.dmg` | `14038025` | `0e7971d5ec8051546c828e7501e22986c261cad7ded74962aeefea4c4ae40f6d` |
| `linux-x86_64` | `Bat-Client-0.1.13-linux-x64.AppImage` | `Bat-Client-0.1.13-linux-x64.AppImage` | `93530616` | `9687c9b25a49a654877f6787b50727288fa471cac8b498675788a353ad61df8d` |
