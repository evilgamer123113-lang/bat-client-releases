# Bat Client — `rust-v0.1.14` & `bat-mods-v7` Verified Public Release

**Date:** 2026-11-22
**Status:** **PUBLISHED & LIVE VERIFIED**

---

## 1. In-Game Mod Menu Stutter Fixes (`0.1.14` / `bat-mods-v7`)

1. **Unthrottled Full-FPS Ultralight Menu Rendering (`WebController.java`):**
   - `WebController.updateAndRender()` previously throttled Ultralight's `renderer.update()` / `renderer.render()` to a 60 Hz `UiFrameClock` even while an interactive `WebScreen` (`Mod Menu`, `Pause Menu`, `Main Menu`) was open, causing beat-frequency stutter against high Minecraft FPS.
   - Added `boolean interactiveScreen = com.batclient.BatClientMod.getActiveScreen() != null;` to the `urgent` condition so Ultralight updates and renders at the full in-game render frame rate whenever a menu is open.
2. **Immediate Cursor Hover & Movement Wakeups (`WebWindow.java` & `ports/mc1.8.9/src/com/batclient/web/WebWindow.java`):**
   - `onCursorPos(double x, double y)` previously only called `WebController.INSTANCE.requestUiFrame()` when a mouse button was held (`button != 3`).
   - Updated `onCursorPos` to wake the UI frame whenever integer cursor coordinates change.
3. **In-Memory `HudConfig` Updates with Atomic Flush on Menu Close (`HudConfig.java`):**
   - `HudConfig.update()` and `HudConfig.updateModule()` previously performed synchronous disk writes (`Files.writeString` to `.tmp` + `AtomicFiles.replace` to `batclient-hud.json`) on Minecraft's Render Thread inside the Ultralight JNI callback on every module toggle, favorite click, or slider change.
   - Added `dirtyDisk` state so `update()` and `updateModule()` update in-memory state immediately and defer disk I/O until `HudConfig.flush()` runs on menu close (`WebScreen.removed()`) or game shutdown.
4. **In-Place DOM Toggles & Zero Software Alpha Fade Overhead (`app.js` & `style.css`):**
   - Tagged `.mod-card` elements with `data-id` and updated `setModEnabled(id, on)` and `.mod-fav` clicks to toggle classes/text in place without destroying and rebuilding all 59 cards via `grid.innerHTML = ""`.
   - Removed the 320 ms full-window software opacity fade on `#view-mods` and `.mm-win` and added `contain: layout style paint;` on `.mod-card`.

---

## 2. Published Releases

- **Companion Mod Bundles (`bat-mods-v7`, 30 assets):**
  `https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/bat-mods-v7`
- **Rust Launcher (`rust-v0.1.14`, 11 assets, `--prerelease --latest=false`, Classic `v2.2.97` preserved as `latest`):**
  `https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/rust-v0.1.14`
- **Git Commit:** `0c8cfffae296c8478a124a848d1ea8dc8bedba31` (`build/rust-native-0.1.14-release`)
- **Codemagic macOS Build:** `6b02a2f38dfd348e34759f56`
- **CircleCI Linux Pipeline:** `#30` (`098bd40c-da6b-422d-bbdb-31d35ea86c8b`)

| Target | Updater Payload | Installer | Size (bytes) | SHA-256 |
|---|---|---|---|---|
| `windows-x86_64` | `Bat-Client-0.1.14-windows-x64-setup.exe` | `Bat-Client-0.1.14-windows-x64-setup.exe` | `10988760` | `de759c2b4c0baea7be9f4e6d35be7a79c9913fb777e0e0bb723157c4a90539ab` |
| `darwin-aarch64` | `Bat-Client-0.1.14-macos-apple-silicon.app.tar.gz` | `Bat-Client-0.1.14-macos-apple-silicon.dmg` | `13390478` | `33a6a677df18224b50fdcad8c8421323aad2015467e09bab976a2d3d9f3b7f13` |
| `darwin-x86_64` | `Bat-Client-0.1.14-macos-intel.app.tar.gz` | `Bat-Client-0.1.14-macos-intel.dmg` | `14038908` | `fb37026a8492e05748ba51d1abfa11f4346d4c0f2c013c636e5ca7bffa7b5b1a` |
| `linux-x86_64` | `Bat-Client-0.1.14-linux-x64.AppImage` | `Bat-Client-0.1.14-linux-x64.AppImage` | `93522424` | `303134190bfdc4128ad735a7ef4d629a71a44436e7d1fe63057b5c51839108f5` |
