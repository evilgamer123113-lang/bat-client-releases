# Bat Client — `rust-v0.1.15` & `bat-mods-v8` Complete Engineering & Release Record

**Date:** 2026-11-26  
**Status:** **PUBLISHED & LIVE VERIFIED**  
**Git Branch & Commit:** `build/rust-native-0.1.15-release` (`ebceea2ec15b0cacbf6569d51cfeb0bf259b9c16`, based on `0c8cfffae296c8478a124a848d1ea8dc8bedba31` / `0.1.14`)  
**Pushed Remotes:**
- `https://github.com/evilgamer123113-lang/bat-client-launcher.git` → `build/rust-native-0.1.15-release`
- `https://github.com/evilgamer123113-lang/bat-client-releases.git` → `build/rust-native-0.1.15-release` (required so Codemagic & CircleCI can check out the branch)

---

## 1. Verbatim User Prompts & Player Bug Reports Handled

1. **Bug #1 Report:**
   - User prompt: *"now fixing some bugs of players starting with 1st he is saying, 'i am not able to load in'"*
   - Launcher UI error shown to player: `A downloaded file could not be verified / Minecraft closed before startup completed. / Retry to download and verify the file again.`
   - Actual crash in log (Minecraft `1.21.11`, Fabric Loader `0.20.1`, 87 mods including `batclient 0.1.0-fabric-internal` and `fastclient-hud 1.0.72`):
     ```text
     @Redirect conflict. Skipping fastclient-hud.client.mixins.json:LoadingOverlayMixin from mod fastclient-hud->@Redirect::fastclient$omitVanillaLogo(...) with priority 1000, already redirected by batclient.mixins.json:LoadingBrandMixin from mod batclient->@Redirect::bat$brand(...) with priority 1000
     Caused by: org.spongepowered.asm.mixin.injection.throwables.InjectionError: Critical injection failure: Redirector fastclient$omitVanillaLogo(...) in fastclient-hud.client.mixins.json:LoadingOverlayMixin from mod fastclient-hud failed injection check, (0/1) succeeded.
     ```

2. **Bug #2 Report:**
   - User prompt: *"he is saying, 'MY VERSION IS 26.3 WHEN IM LAUNCHING MC MY BUT WHEN I CHANGE THE FPS IT SHOWS 120 BUT EVEN DOES I CLICK APPLY IT CHANGE BUT IT STILL SAYS 120 FPS NOT MAX I HV TO CHANGE IT ALL TIME'"*

3. **Bug #3 Report:**
   - User prompt: *"1 more bug fix it then continue i aaded 2 mods everything was right and perfect version these mods didnot loaded it said retry mods were bloodbathen 1.20.1 forge i guess then build all ports and build the launcher for all os and publish it and save every single detail in .md file"*

4. **Disk Space Alert:**
   - User prompt: *"BROoo Only 4.45 GB left in C: FIX ITT FASTT IT IS MAIN!! FASTT!! and then continue"*

---

## 2. Exact Root Causes & Every Code Change Made

### A. Bug #1A — `SplashOverlay` Mixin `@Redirect` Crash (`bat-client-mod`, shipped in `bat-mods-v8`)
- **Root Cause:** Both `batclient`'s `LoadingBrandMixin` and `fastclient-hud`'s `LoadingOverlayMixin` targeted `SplashOverlay` (`net.minecraft.class_425`) `drawTexture` with `@Redirect` at default priority `1000`. SpongePowered Mixin only allows one `@Redirect` per target instruction, so `batclient` consumed the target and `fastclient-hud` crashed with `InjectionError (0/1)`.
- **Files Changed:**
  1. `bat-client-mod/src/main/java/com/batclient/performance/PerformanceMixinPlugin.java`
     - Added explicit guard in `shouldApplyMixin`: skips `LoadingBrandMixin` completely whenever `fastclient-hud`, `dark-loading-screen`, `drippyloadingscreen`, or `fancymenu` is loaded via `FabricLoader.getInstance().isModLoaded(...)`.
  2. `bat-client-mod/src/main/java/com/batclient/mixin/LoadingBrandMixin.java`
     - Changed `@Mixin(SplashOverlay.class)` → `@Mixin(value=SplashOverlay.class,priority=1500)` so any third-party splash-screen mod (default priority `1000`) applies first.
     - Added `require=0` to `@Redirect` `bat$black`, `@Redirect` `bat$brand`, and `@Inject` `bat$progress` so even **unknown future loading-screen mods** will never crash on `SplashOverlay`.
  3. `bat-client-mod/scripts/adapter_1165.py` & `bat-client-mod/scripts/adapter_1194.py`
     - Updated exact string replacements on `LoadingBrandMixin.java` to match `,require=0)` so all legacy `1.16.5`–`1.19.4` ports adapt and compile cleanly.
- **Immediate Player Workaround (pre-update):** Disable `fastclient-hud` in Mods.

### B. Bug #1B — False `"A downloaded file could not be verified"` Crash Classification (`launcher-rust`)
- **Root Cause:** `launcher-rust/src-tauri/src/launch_errors.rs` checked `lower.contains("checksum") || lower.contains("signature")` BEFORE `MOD_CONFLICT`. Every offline/authlib-injector launch prints `"signature":"..."` inside `--profileProperties`, so **every offline crash** was misclassified as `INTEGRITY` (*"A downloaded file could not be verified"*). In addition, `MOD_CONFLICT` did not match `Mixin transformation`, `MixinTransformerError`, `InjectionError`, `@Redirect conflict`, or `Critical injection failure`.
- **File Changed:** `launcher-rust/src-tauri/src/launch_errors.rs`
  - Narrowed `INTEGRITY` match to: `checksum`, `sha256 mismatch`, `sha1 mismatch`, `hash mismatch`, `invalid signature`, `signature verification`, `signature mismatch`.
  - Expanded `MOD_CONFLICT` match to include: `mixin transformation`, `mixintransformererror`, `critical injection failure`, `@redirect conflict`, `injectionerror`.
  - Added regression test in `launch_errors::tests::classifies_real_startup_errors` verifying `--profileProperties ... "signature":"kXbq..."` + `Mixin transformation of net.minecraft.class_425 failed @Redirect conflict` classifies as `MOD_CONFLICT`.

### C. Bug #2 — Minecraft `26.3` FPS Slider Resetting to `120` Every Launch (`launcher-rust`)
- **Root Cause:** `launcher-rust/src-tauri/src/optimization.rs` computed `modern` via `version.split('.').nth(1) >= 13`. For year-based Minecraft **`26.3`**, `nth(1)` is `3` (`3 < 13`), so `modern` evaluated to `false` and `optimization.rs` forcibly wrote `maxFps:999` into `options.txt` on every launch. Modern Minecraft (`1.13+` and `26.x`) only allows `10..=260` (`260` = Unlimited), so `26.3` rejected `999` and reset the slider back to `120` every launch.
- **Files Changed:**
  1. `launcher-rust/src-tauri/src/optimization.rs`
     - Changed modern check to `major > 1 || (major == 1 && minor >= 13)` so `26.x` writes `maxFps:260` (Unlimited) and automatically heals any stale `maxFps:999`.
     - Added `26.3` regression test in `optimization::tests::preserves_unknown_options_and_has_version_specific_unlimited`.
  2. `launcher-rust/src-tauri/src/game_launch.rs`
     - Fixed the same `26.x` version check for `--profileProperties` (offline skins: `major > 1 || (major == 1 && (minor > 20 || (minor == 20 && patch >= 5)))`) and `--quickPlayMultiplayer` (partner servers: `major > 1 || (major == 1 && minor >= 20)`).
  3. `launcher-rust/src-tauri/src/mods.rs`
     - Fixed `ensure_fabric_api` (`if major == 1 && minor < 18 { return Ok(()); }`) so Fabric API is not skipped on `26.x`.

### D. Bug #3 — Forge `1.20.1` (`Bloodbathen`) Mods Failing with `Retry` (`launcher-rust`)
- **Investigation:** Verified `bloodbathen` on Modrinth (`project_id: oI3Gy6U8`, `bloodbathen-1.4.1-forge-1.20.1.jar`, `11,606,583` bytes, `META-INF/mods.toml` declaring `modId="minecraft", mandatory=true, versionRange="[1.20.1]"` and optional `geckolib`).
- **Root Cause:** `launcher-rust/src-tauri/src/mod_compatibility.rs` (`jar_compatible_with` called by `auto_fix_for_launch`) only validated Minecraft version ranges for Fabric/Quilt `fabric.mod.json`, never for Forge `META-INF/mods.toml` or NeoForge `META-INF/neoforge.mods.toml`, and treated legacy `mcmod.info` (`1.8.9`/`1.12.2`) JARs as compatible with modern Forge (`1.20.1`). Any mismatched Forge JAR from another Minecraft version left in `mods/` stayed active and broke Forge `1.20.1` startup.
- **File Changed:** `launcher-rust/src-tauri/src/mod_compatibility.rs`
  - Added `is_modern_mc`, `compare_ver`, `matches_maven_range` (evaluating Maven ranges `[1.20.1]`, `[1.20.1,1.21)`, `[1.20,)`, etc., returning `None` on unknown syntax so valid mods are never false-blocked), and `forge_toml_matches`.
  - Updated `jar_compatible_with`:
    - On modern Forge (`1.13+` / `26.x`): stashes `mcmod.info`-only legacy JARs and validates `META-INF/mods.toml` `minecraft` `versionRange`.
    - On legacy Forge (`1.8` / `1.8.9`): stashes `META-INF/mods.toml`-only modern JARs.
    - On NeoForge: validates `META-INF/neoforge.mods.toml` (or `META-INF/mods.toml`) `minecraft` `versionRange`.
  - Added unit test `auto_fix_stashes_mismatched_forge_versions_and_keeps_matching_1201` confirming `bloodbathen-1.4.1-forge-1.20.1.jar` is kept while `legacy-1.8.9.jar` (`mcmod.info`) and `other-1.21.1.jar` (`[1.21.1]`) are automatically stashed.

### E. Version Bump & Companion Mod Manifest (`launcher-rust`)
- `launcher-rust/src-tauri/Cargo.toml`: `version = "0.1.14"` → `"0.1.15"`
- `launcher-rust/src-tauri/Cargo.lock`: `bat-client-next` `version = "0.1.14"` → `"0.1.15"`
- `launcher-rust/src-tauri/tauri.conf.json`: `"version": "0.1.14"` → `"0.1.15"`
- `launcher-rust/src-tauri/src/companion_mod.rs`: `MOD_BUNDLE_BASE_URL` → `.../releases/download/bat-mods-v8`, updated all 30 `(key, sha256, size)` entries in `MOD_BUNDLES`.

---

## 3. Build, Test, Signing & Disk Hygiene Details

### Unit & Compile Verification
- **Java (`fabric-1.21.11`):** `gradlew.bat -p bat-client-mod\ports\fabric-1.21.11 compileJava --no-daemon` → `BUILD SUCCESSFUL`.
- **All 30 Companion Mod Ports (`1.8`–`1.21.11`, Fabric + Forge):** Built via `bat-client-mod/scripts/gradle-secondary-cache.ps1` using warm cache `D:\BatClientBuildCache\gradle-home` and packed into `D:\BatClientBuildCache\bat-mods-v8\bat-mods-<key>.dat` (`30/30 OK`, `0` failures).
- **Rust Test Suite (`cargo test --locked -- --skip native_rejected_jvm_option --skip live_`):** **`118 passed; 0 failed`** (`12` ignored hardware/network tests).

### `C:` Drive Cleanup & D-Drive Build Discipline
- Freed **`~9 GB`** on `C:` (`4.42 GB` → **`13.34 GB` free**):
  - Removed accidental `c:\Users\Noor-ul-Islam\Desktop\Raven-Client\launcher-rust\src-tauri\target` (`3.47 GB`)
  - Removed `C:\Users\Noor-ul-Islam\.gradle\caches` (`10.43 GB`) & `.gradle\daemon` (`0.04 GB`)
  - Removed `bat-client-mod\ports\*\build` (`5.42 GB`) after all 30 `.dat` bundles were packed to `D:` and uploaded to GitHub
- **Rule for all future Rust builds on this machine:** Always export these env vars before running `cargo` / `tauri build`:
  ```powershell
  $env:RUSTUP_HOME = 'D:/BatClientBuildCache/rustup'
  $env:CARGO_HOME = 'D:/BatClientBuildCache/cargo'
  $env:CARGO_TARGET_DIR = 'D:/BatClientBuildCache/rust-launcher-target'
  $env:TEMP = 'D:/BatClientBuildCache/rust-temp'
  $env:TMP = $env:TEMP
  ```

### Multi-OS Build & Signing Pipeline
1. **Windows x64 NSIS:** Built & signed locally via `npx --yes --package @tauri-apps/cli@2.12.4 tauri build --bundles nsis` on `D:\BatClientBuildCache\rust-launcher-target` (`10.48 MiB`).
2. **macOS Apple Silicon (`aarch64-apple-darwin`) + Intel (`x86_64-apple-darwin`):** Built on **Codemagic** (`appId: 6af9c498a9d62c4277b43fc8`, `workflowId: rust-macos`, `buildId: 6b0802437bdb78685b7958a0`, `mac_mini_m2`).
3. **Linux x64 (`x86_64-unknown-linux-gnu`):** Built on **CircleCI** (`slug: circleci/PEBmuUecNmMPajhVuHuPXX/PZkrqBLADnz8ePtK1B9KEZ`, `definition_id: 89008546-3ed0-46f8-94a0-47265cce8b69`, pipeline `#32`, `id: 74d22403-cc62-455d-b5a4-e220e1b561be`).
4. **CRITICAL Tauri Updater Signing Requirement (`requireSignedVersion: true`):**
   - Signing key: `D:\BatClientPrivate\rust-updater\bat-client-next.key` (empty password).
   - Must pass **`--app-version <VER>`** when signing macOS & Linux updater payloads (`tauri signer sign --app-version 0.1.15 <file>` with `$env:TAURI_SIGNING_PRIVATE_KEY_PATH` and `$env:TAURI_SIGNING_PRIVATE_KEY_PASSWORD = ''`, and do NOT pass `-f` alongside `TAURI_SIGNING_PRIVATE_KEY`). Without `--app-version`, Tauri updaters with `requireSignedVersion: true` reject the signature!
5. **Website Deployment (`https://batclient.xyz/`):**
   - Deployed via `scp`/`ssh` to `ubuntu@129.151.130.118` (`~/.ssh/bat-server.pem`) → `/var/www/html/update.json` & `/var/www/html/changelog.html` (`chown www-data:www-data`).
   - Live-verified: `update.json` byte `0` == `0x7B` (BOM-free), Classic `2.2.97` preserved verbatim, Rust `0.1.15` `enabled: true`, all 4 platform SHA-256 hashes & byte sizes match published GitHub assets.

---

## 4. Published Releases & Artifact Hashes

- **Companion Mod Bundles (`bat-mods-v8`, 30 assets, `--prerelease --latest=false`):**  
  https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/bat-mods-v8
- **Rust Launcher (`rust-v0.1.15`, 11 assets, `--prerelease --latest=false`, Classic `v2.2.97` preserved as `latest`):**  
  https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/rust-v0.1.15
- **Live Endpoints:** https://batclient.xyz/update.json · https://batclient.xyz/changelog.html

### Launcher Installers & Updater Payloads (`rust-v0.1.15`)

| Target | Updater Payload | Installer | Size (bytes) | SHA-256 |
|---|---|---|---|---|
| `windows-x86_64` | `Bat-Client-0.1.15-windows-x64-setup.exe` | `Bat-Client-0.1.15-windows-x64-setup.exe` | `10987389` | `381faaf848ba799b4ade1fb55b6fc44cb0d8a7ad3dfc14e2da1d62c81a1c495a` |
| `darwin-aarch64` | `Bat-Client-0.1.15-macos-apple-silicon.app.tar.gz` | `Bat-Client-0.1.15-macos-apple-silicon.dmg` | `13419581` | `9e7c25ee0e2e17f2a1d1376845d457ecfdb0a5f244efdd525756cb992e52b73b` |
| `darwin-x86_64` | `Bat-Client-0.1.15-macos-intel.app.tar.gz` | `Bat-Client-0.1.15-macos-intel.dmg` | `14067820` | `93b21828f4e502d66046a69bc15a8522ff80df8f30c585165da7341b9d431f28` |
| `linux-x86_64` | `Bat-Client-0.1.15-linux-x64.AppImage` | `Bat-Client-0.1.15-linux-x64.AppImage` | `93563384` | `5f198cc6abe8014e79461bad29a18571cbdd8f551da4e24b817ff8afa81533ef` |

*(Additional installer assets published in `rust-v0.1.15`: `Bat-Client-0.1.15-macos-apple-silicon.dmg` (`13687424` B), `Bat-Client-0.1.15-macos-intel.dmg` (`14326633` B), `Bat-Client-0.1.15-linux-x64.deb` (`15896610` B), plus all 4 `.sig` files.)*

### Companion Mod Bundles (`bat-mods-v8`, all 30 targets)

| Target | Size (bytes) | SHA-256 |
|---|---|---|
| `fabric-1.8` | `95875557` | `5dacbf022e41b9d9c2a86543566a417620b8a86b2e0a25fc0715ee307a35d14f` |
| `forge-1.8` | `97499558` | `9a36502adbd35be3cd3ebe809ca819f8690bbb27152c877fdb7b0da0e12a7135` |
| `fabric-1.8.9` | `95876378` | `422e70253208381c8af2fda87b1592c6b6919fb311cbc82970cdb2ba54e15868` |
| `forge-1.8.9` | `97500105` | `edef32a5666b85cef0f5fe6d165544154c124791c7e86c2f199cc95e4e14a1f4` |
| `fabric-1.16.5` | `95929305` | `ec2350eafc90e2bf3f2bfbca7b07c7dd82bb8adf5c8d1cb5b1b7ddc5c4fc0094` |
| `forge-1.16.5` | `191610170` | `c5ea9c33afcf47af0759f05a0ede738fb94c427c79d67ac12868fc186fc69e5b` |
| `fabric-1.17.1` | `95927028` | `1949b2c0600f764698700b0d5122eb55602f928b6b68f3dc2ad49c7726c8e173` |
| `forge-1.17.1` | `191600090` | `b4e963360052e6c1996d4e2b45f21c093635d8c7d9515cfe5cd96c7d21781761` |
| `fabric-1.18.2` | `95911880` | `c7e429dace7656120db3cf11ae4f927fb57997f7fa44b1cc84c6663fc0d8b0ff` |
| `forge-1.18.2` | `95926745` | `e43236c34e973299e6376e5412cb91a015043dffb8893d009da9252efaa41807` |
| `fabric-1.19.2` | `95913745` | `cd4d4f00e6a542268a85bed091d8fb1733afc2a7541bd02d0aa686d5456ce444` |
| `forge-1.19.2` | `95929050` | `bffb21d382b7085e5ff61db64071286b40107ff54ae282ad09806346de3ae3f8` |
| `fabric-1.19.4` | `95913276` | `630e0c25e5d384570158f794a6c1ea71d6e8a869987c3b908685981d03df0192` |
| `forge-1.19.4` | `95928556` | `4c9ab1c16b68c227a0ee442811afaf143c463628e8db211daaaddb5d9ea91cb0` |
| `fabric-1.20.1` | `95910991` | `51591e45df61d7b8e59c6675382733a2f7f7ec844117f8038ae08aad7a63c48f` |
| `forge-1.20.1` | `95925072` | `a7526967157afb822ce8af56bf70fdf1dae5359c470b541ef2ff14733d84e65d` |
| `fabric-1.20.4` | `95906958` | `c50a855f15e7003fd69a6846eb2f91b3341c6993d64316297dfa1b2e011fb3fa` |
| `forge-1.20.4` | `95920597` | `28a6fd83b9c15c64656909f717d507e1d2343186db5cbad3deffcc0610bbe4b2` |
| `fabric-1.20.6` | `95903084` | `4ec9323e9687a573a2893bdb880970d25665d6965a67ec4252da5e1237805c6f` |
| `forge-1.20.6` | `95917193` | `c561430a80b096e9e1d6fa3e4e05958bac3ae0129a266aea385f6614ae8ca690` |
| `fabric-1.21.1` | `95890097` | `29162c62d79a5dcda55ba8b46be601baf03ad6e79697b9c4f482b0e80039cf28` |
| `forge-1.21.1` | `95916803` | `efc956dac5f49ff6ff4e3fd8e0d4616fe74436ff35054b25845e27e1ae72739c` |
| `fabric-1.21.4` | `95908297` | `c069cce1c219a9fefcaa9e894cd14017c859e069926a902f79d81bc8b9eff2b6` |
| `forge-1.21.4` | `95923944` | `30736a1fd7a38ef3104e35963af73a5fd84ec9a977acb08e55af861fc786f8e5` |
| `fabric-1.21.5` | `95918249` | `cbc931d95564c708d88337d6c8a0466217ba1381b9785117f32860637d0498d7` |
| `forge-1.21.5` | `95933536` | `17234e28437c90bd622dd5e9077c0762bf66267d3a99ec0d42f86178648dc573` |
| `fabric-1.21.8` | `95915243` | `dccc83026ab238f08fcec628ee181af69a563f677f26f35d892dc855ad3e259e` |
| `forge-1.21.8` | `95930661` | `ef86f5c886a8d06afe304589c72562391af4f9aeab00d1c646266f2e0e73c71a` |
| `fabric-1.21.11` | `95918402` | `6ff4d5e0c682996209f9b2ae83d77bd149b42e96f9e0d801f9df9e149d4b7efd` |
| `forge-1.21.11` | `95933723` | `c1125135cd34486d9b4bc4f0afacac93c2cc7260f7fa6d49a374586ba7b38838` |
