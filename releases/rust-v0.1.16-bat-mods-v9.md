# Release `rust-v0.1.16` + `bat-mods-v9`

- **Date:** 2026-11-27
- **GitHub Releases:**
  - `https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/rust-v0.1.16` (`--prerelease --latest=false`)
  - `https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/bat-mods-v9` (`--prerelease --latest=false`)
- **CI Builds (`build/rust-native-0.1.16-release`):**
  - Codemagic (`rust-macos`): `6b097d04f7de5618c7bd5fc5`
  - CircleCI (`linux`): pipeline `ff273e2b-2071-474e-bcaa-f8b724cb7fc2` / workflow `7341d6b2-f03b-44c5-bd3c-a80b42689853`

## Changes

1. **Sodium Extra auto-disable fix (`launcher-rust/src-tauri/src/optional_renderer.rs`):**
   - `apply()` now scans `mods/` for an enabled `.jar` with `fabric.mod.json` `id == "sodium"` and returns `Ok(0)` before checking `dismissed-mods.json`, preventing `sodium-extra` / `reeses-sodium-options` from auto-disabling when Sodium is enabled.
2. **Kill Launch / Kill Minecraft button (`game_launch.rs`, `main.rs`, `ui/game-launch.js`, `ui/styles.css`):**
   - Added `stop_running_game` command, `CANCEL_LAUNCH`, `ACTIVE_PID`, and red `is-killing` state (**Kill Launch** / **Kill Minecraft**) on `#play-button`.
3. **Companion mod Windows 11 / AV launcher-check fix (`bat-client-mod/src/main/java/com/batclient/platform/LauncherAccess.java`, `bat-mods-v9`):**
   - Updated `isBatLauncher(ProcessHandle parent)` so restricted `OpenProcess` (`parent.info().command()` empty under Windows 11 / `mc-sec-plugin-x64.dll`) falls back to `.orElse(true)` when `parent.pid() == BAT_CLIENT_LAUNCHER_PID`, and accepts `bat-client-next`, `bat-client`, and `bat client` executables.

## Artifacts (`rust-v0.1.16`)

| Platform | File | Size (bytes) | SHA-256 |
|---|---|---|---|
| `windows-x86_64` | `Bat-Client-0.1.16-windows-x64-setup.exe` | `10996618` | `e6f03976d0e62aa3121f1d8a101e61a2ed67800953a17bcfce1b3482434b0bd1` |
| `darwin-aarch64` (updater) | `Bat-Client-0.1.16-macos-apple-silicon.app.tar.gz` | `13429692` | `310eee6eb3b642e47fcc14c0f8e7b3a8a98c3f5b84ae2513331ec3011518b834` |
| `darwin-aarch64` (installer) | `Bat-Client-0.1.16-macos-apple-silicon.dmg` | `13692766` | `b3fa3a3d57637726144538b657424541f7d7e61924593555555fd585d4b3743b` |
| `darwin-x86_64` (updater) | `Bat-Client-0.1.16-macos-intel.app.tar.gz` | `14071600` | `df5a14aec70a2c6df08a86f68ab46af5237ad22a553cdacc89135cc93ab20fa1` |
| `darwin-x86_64` (installer) | `Bat-Client-0.1.16-macos-intel.dmg` | `14332579` | `57dfe8eb7c015d62b3be4a96b3e3d97b85ca65cc077fb22786fc8d829f013d53` |
| `linux-x86_64` (updater/installer) | `Bat-Client-0.1.16-linux-x64.AppImage` | `93542904` | `5338a74fd8137519a9924c9b9b50e2a44f02d30b0c5fb9980670f4c21e872bd` |
| `linux-x86_64` (.deb) | `Bat-Client-0.1.16-linux-x64.deb` | `15898206` | `6285281c85b7f06bcb721d3acbc3cfcbf87fb299dc5259ad6794f4af533a2d46` |
