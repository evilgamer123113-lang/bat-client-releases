# Bat Client — `rust-v0.1.12` & `bat-mods-v5` Verified Public Release

**Date:** 2026-11-21
**Status:** **PUBLISHED & LIVE VERIFIED**

---

## 1. Bugs Fixed in `0.1.12` / `bat-mods-v5`

### A. Linux & macOS HUD / Right Shift Menu Not Opening
- **Root cause:** `bat-client-mod` previously bundled only `io.github.nilsen84:ultralight-bindings:1.0.0-SNAPSHOT:windows-x64`. On Linux, `UltralightLoader.load` failed with:
  `java.lang.IllegalStateException: Missing native library: /io/github/nilsen84/ultralight/natives/linux-x64/libSph0.so`
  causing every Right Shift / HUD keybind press to log `Ultralight engine not ready, cannot open web UI`.
- **Fix:** Added `linux-x64`, `mac-x64`, and `mac-arm64` native classifiers alongside `windows-x64` across:
  - `bat-client-mod/build.gradle` (Fabric `1.21.1`)
  - All 14 Fabric port `build.gradle` files (`bat-client-mod/ports/fabric-*/build.gradle`)
  - `bat-client-mod/ports/fabric-protection.gradle` (now enforces all 4 OS JNI libraries in `verifyProtectedJar`)
  - `bat-client-mod/ports/forge-web-runtime/build.gradle` & `bindings.lock.json` (shared `:web-runtime` used by all 15 Forge ports)
- **Companion bundles (`bat-mods-v5`):** Rebuilt, 4-OS-verified, packed, round-trip verified, and published all 30 Fabric/Forge `BATMODS1` `.dat` files (`companion_mod.rs` download timeout also raised `300s` → `600s`).

### B. False-Positive `"Save and fully close Minecraft before changing mods"` Launch Failure (Windows & Linux)
- **Root cause:** `ensure_game_closed()` in `launcher-rust/src-tauri/src/library.rs` matched ANY `java`/`java.exe`/`javaw.exe` process on the system containing `net.minecraft` (blocking Bat Client whenever a local Minecraft server, another launcher's Minecraft, or a Linux zombie `java` process existed).
- **Fix:** Updated `ensure_game_closed()` (and `ensure_legacy_launcher_closed()`) in `launcher-rust/src-tauri/src/library.rs` to ignore `Zombie`/`Dead` processes, narrow matching to `net.minecraft.client`, and scope the check strictly to Bat Client's own `minecraft_runtime` / `bat-client` / `bat-cosmetics` process.

---

## 2. Published Releases & Live Site

- **Rust Launcher Release:** `https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/rust-v0.1.12` (`--prerelease --latest=false`, preserving Classic `v2.2.97` as `latest`, 11 assets)
- **Companion Mod Bundles:** `https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/bat-mods-v5` (`--prerelease --latest=false`, 30 assets)
- **Live Manifest:** `https://batclient.xyz/update.json` (BOM-free, verified Classic `2.2.97` preserved + Rust `0.1.12` enabled)
- **Live Changelog:** `https://batclient.xyz/changelog.html` (updated with `0.1.12`)
- **CI Build IDs:**
  - Windows x64 NSIS: built locally (`D:\BatClientBuildCache\rust-release-0.1.12\Bat-Client-0.1.12-windows-x64-setup.exe`)
  - macOS Apple Silicon + Intel: Codemagic `rust-macos` build `6b01ac9f17a83bd73150048a`
  - Linux x64 AppImage + `.deb`: CircleCI `linux-release` pipeline `#25` (`aae7ebe5-f5fa-447c-bbda-f1c1a36c0378`)

---

## 3. Rust `0.1.12` Signed Updater Payloads

| Platform | Updater Payload | Installer | Size (bytes) | SHA-256 |
|---|---|---|---|---|
| `windows-x86_64` | `Bat-Client-0.1.12-windows-x64-setup.exe` | `Bat-Client-0.1.12-windows-x64-setup.exe` | `10994838` | `25b6bfe10c4dd340214a2a94b2d3d618a8c61035401777405523f77b746a5dac` |
| `darwin-aarch64` | `Bat-Client-0.1.12-macos-apple-silicon.app.tar.gz` | `Bat-Client-0.1.12-macos-apple-silicon.dmg` | `13390362` | `df6e54be0f6a84df5975d0ccd439bdd60957b81a3164a9b76bf662c0a2bcac94` |
| `darwin-x86_64` | `Bat-Client-0.1.12-macos-intel.app.tar.gz` | `Bat-Client-0.1.12-macos-intel.dmg` | `14039038` | `0b5dc8a8d4daefdf1be727ba7aa3fe6d12fa49fc9d81ff8c7cff919e264efca2` |
| `linux-x86_64` | `Bat-Client-0.1.12-linux-x64.AppImage` | `Bat-Client-0.1.12-linux-x64.AppImage` (+ `.deb` `15881370` B) | `93530616` | `b99f772c8c0f2053f3a6caa31c8de676635e187b974bcbff3ba515bedb284622` |

---

## 4. `bat-mods-v5` Companion Mod Bundles (30 Targets)

| Target | Bundle File | Size (bytes) | SHA-256 |
|---|---|---|---|
| `fabric-1.8` | `bat-mods-fabric-1.8.dat` | `95873690` | `ac500b5521854011913ba9f61c00bfacfaff3d36e7d4900ef444f368f537be29` |
| `forge-1.8` | `bat-mods-forge-1.8.dat` | `97497766` | `e62309579ec3c9f6946c50a4be7414d4377a418ddae1383e5247ac8c5374202e` |
| `fabric-1.8.9` | `bat-mods-fabric-1.8.9.dat` | `95874512` | `ad3b0e27a33e377d96589ade2beb5d28926c18e16381085df61ea999228dba2e` |
| `forge-1.8.9` | `bat-mods-forge-1.8.9.dat` | `97498323` | `e67815a7af0b93597c81e15f48cccab4b32083d12d7e16d8fa62e3ed97126e0a` |
| `fabric-1.16.5` | `bat-mods-fabric-1.16.5.dat` | `95927397` | `747f5e52329006d76fad7d01c5ae83bb5e608d5e905488b21c5c8899e046ec1f` |
| `forge-1.16.5` | `bat-mods-forge-1.16.5.dat` | `191608341` | `001cf0eb908bd787b8ab5f86a62ed32f94d79e49e3551c4bc50cb1ab6c554e66` |
| `fabric-1.17.1` | `bat-mods-fabric-1.17.1.dat` | `95925139` | `7db1b12a65cd4a463d1eb3dd085559cb4ea98b445af4ed7661b6a0097c54d457` |
| `forge-1.17.1` | `bat-mods-forge-1.17.1.dat` | `191598295` | `229d5b957f6ad3f8616ce5bf753359ae89b0e2f49ed10c14f3f36e83d7f0e623` |
| `fabric-1.18.2` | `bat-mods-fabric-1.18.2.dat` | `95909993` | `f5dea90f981a97984ddab533f9edfd392651250b2ebebc15c6ad6af6965aa0b8` |
| `forge-1.18.2` | `bat-mods-forge-1.18.2.dat` | `95924948` | `e04d09e39ad83cdcd7fdd9c92320e11e7d0f405a9acababfbaebf78640bdea31` |
| `fabric-1.19.2` | `bat-mods-fabric-1.19.2.dat` | `95911867` | `e8732f4deac0ab060003593039c478fc3af4679a56461fce5d58a67443725898` |
| `forge-1.19.2` | `bat-mods-forge-1.19.2.dat` | `95927239` | `7f0e9a486f97794a0035dd69854f55006dcf88d0307df41f1058bb0fc9f4628e` |
| `fabric-1.19.4` | `bat-mods-fabric-1.19.4.dat` | `95911388` | `9614d7d32cef3e32a7072ae04c98d90fb42c9c1fe79bcd87fd1f86279d01aa4a` |
| `forge-1.19.4` | `bat-mods-forge-1.19.4.dat` | `95926749` | `17ee625ee5922a14a84c6f468e9b023083b5a1b98fdc4d13aac1a9148d9682ea` |
| `fabric-1.20.1` | `bat-mods-fabric-1.20.1.dat` | `95909103` | `43c4cd95859be43295227d93c187b35047fd0c04d2bbcfa154e251d59925e2d2` |
| `forge-1.20.1` | `bat-mods-forge-1.20.1.dat` | `95923263` | `f7e0acba40a5313d8e3e2c80856bb2a366b101448ecbeaec551598e51f39a2d5` |
| `fabric-1.20.4` | `bat-mods-fabric-1.20.4.dat` | `95905070` | `edd77d44b6e3999871fcc25df5fb16511d18b8b1d82f86e2c2ce2634bf01debb` |
| `forge-1.20.4` | `bat-mods-forge-1.20.4.dat` | `95918790` | `f008d84da1a716698b577164cabe8913caa71ba59ed36d788125190847eb27f9` |
| `fabric-1.20.6` | `bat-mods-fabric-1.20.6.dat` | `95901193` | `429847fc772815a1fd16ddf5c534881883022df13ea990caa817d2f082cea556` |
| `forge-1.20.6` | `bat-mods-forge-1.20.6.dat` | `95915380` | `13a07580c5c940b59e5c4f8179bf19734b6403b39af5e04065997266f64630e4` |
| `fabric-1.21.1` | `bat-mods-fabric-1.21.1.dat` | `95888207` | `603b103696739d6fc769e82c4d16da2483bcf17f6b6cf436735abbe7506d646f` |
| `forge-1.21.1` | `bat-mods-forge-1.21.1.dat` | `95915000` | `8e8106b519f39a43d3a4aeccd1f1f65f208f768bb2b5db3fac98d88344b36d86` |
| `fabric-1.21.4` | `bat-mods-fabric-1.21.4.dat` | `95906406` | `17f42a4489d7cc26b9c5b695172dc0e16e106b9119def940b22b27691fc3b938` |
| `forge-1.21.4` | `bat-mods-forge-1.21.4.dat` | `95922146` | `5ff4b0689c8d21f7e68963937adf88040e85a5e5a8d2e8e56da4f5e8d0476772` |
| `fabric-1.21.5` | `bat-mods-fabric-1.21.5.dat` | `95916363` | `aea750b94413e58a60d0179d6d3ca147d98c9a972b2a5a47c9b5d22408d06f68` |
| `forge-1.21.5` | `bat-mods-forge-1.21.5.dat` | `95931731` | `09656db8a6631abf67ba6c96b77e8581300f9c09a0851a3880970ca5aa3215d1` |
| `fabric-1.21.8` | `bat-mods-fabric-1.21.8.dat` | `95913345` | `f70ba103d57e27a8d5409ea63d27d05dd82eed3a47b0d620bd24832ca90827cc` |
| `forge-1.21.8` | `bat-mods-forge-1.21.8.dat` | `95928859` | `d0e4523ccd9b09e210d13c41f5a3ede9a1916af1c0e7b87211f9830839a468f9` |
| `fabric-1.21.11` | `bat-mods-fabric-1.21.11.dat` | `95916506` | `e125a192ca2be2ffedb59c39e5a84803e8b07d472577b58e77b0041f25537afb` |
| `forge-1.21.11` | `bat-mods-forge-1.21.11.dat` | `95931928` | `93db43e9232a9279f1173b3a34b373b338e2b01d104619b68b08a439d1c4d5bb` |

---

## 5. Verification Results
- **30/30 companion mod JARs:** Verified valid ZIP, exact mod metadata, and all 4 OS Ultralight natives (`windows-x64/ultralight-jni.dll`, `linux-x64/libultralight-jni.so` + `libSph0.so`, `mac-x64/libultralight-jni.dylib`, `mac-arm64/libultralight-jni.dylib`).
- **`BATMODS1` round-trip check:** `fabric-1.21.11` and `forge-1.21.11` unmasked back to exact original JAR SHA-256.
- **Rust unit tests:** `cargo test --locked --features custom-protocol` → `117 passed; 0 failed; 17 ignored`.
- **Updater signature smoke check:** `bat-client-next.exe --updater-verify-smoke` on signed `Bat-Client-0.1.12-windows-x64-setup.exe` → `exit 0`.
- **Live `https://batclient.xyz/update.json`:** Verified BOM-free (`bytes[0] == 0x7B`), Classic `2.2.97` intact, Rust `0.1.12` enabled with matching SHA-256 & sizes for all 4 platforms.
