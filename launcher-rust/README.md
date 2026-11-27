# Bat Client Next — Rust launcher

This is the separate Rust/Tauri launcher migration, not the Minecraft mod. The existing Electron launcher and production releases are retained.

**It is not yet a complete replacement for Electron.** Read [PARITY-AUDIT.md](PARITY-AUDIT.md) for the current feature-by-feature audit and remaining release blockers. Canonical session/build details are in [RUST-LAUNCHER-AGENT.md](../RUST-LAUNCHER-AGENT.md); historical checkpoints in MIGRATION.md are not current status.

## Current implementation

- Rust/Tauri2 backend with static HTML/CSS/JS and WebView2 on Windows. No production Node/Electron sidecar. Electron is used only for isolated development UI harnesses.
- Approved black/white Home, artwork, loadout/account menus, official changelog news, window controls, animations/reduced-motion, transparent Windows icons and generated macOS ICNS.
- Core profile metadata CRUD/copy/favorite/select, offline account management and saved local skin heads. Native Microsoft OAuth with backend-only OS credential storage; user confirmed sign-in. Live refresh/premium gameplay and premium account deletion parity remain incomplete.
- Official Minecraft version catalog with release/snapshot/beta/alpha filters and search. Listing a version does not install it.
- Core version/loader-filtered Modrinth search/install with required dependency resolution/checksums; installed artwork, remove/disable/enable, recoverable removed-file backups and top-level Sodium/add-on opt-outs. Project details/version picker/sort, installed updates, general conflicts, presets/import/export and older protected nested-renderer removal still need migration.
- Local PNG skin/cape/model changes and saved animated-frame preview. Registry/Microsoft uploads implemented but live acceptance unverified. GIF imports, cosmetic slots and full premium-custom cosmetic parity remain unfinished.
-26saved preferences with validation, atomic shared-config writes/backups and preservation of private/opaque fields.16preferences currently lack runtime behavior; see the audit. Config persistence is not feature parity.
- Installed Vanilla/Fabric launching with existing Bat/authlib agents, Java discovery, preserved custom JVM arguments, native libraries, signed offline loopback profiles and base-client checksum repair. Actual cached1.21/1.21.1Fabric renderer/agent initialization was recorded; not a world-gameplay/FPS or complete platform/version test.
- Loading-screen automatic Rust update check/download/signature verification/install/restart, respecting saved opt-out. Windows package signatures/tampering/version substitution verified; loading flow covered by mocked tests. Live channel remains unpublished/disabled and Mac/Linux packaging/install/restart need native release validation. See [UPDATER.md](UPDATER.md).

## Main remaining work

Fresh game/assets/library/loader/Java/agent installation; Forge/NeoForge/Quilt launch; background prepare/quick-join; full cosmetics/accounts parity;16settings consumers; mod maintenance/import/export/presets; folder dialogs and onboarding; Discord/tray/startup/localization/sounds; launch history, server/screenshot/crash/health/benchmark tools; signed native platform releases and actual update replacement tests. Both current Electron and Rust use a shared game/mod directory: do not count full instance isolation as an established Electron feature that was lost.

## Local build and verification

Windows Build Tools2022 are installed at D:/BuildTools. Rust/Cargo/toolchains, targets and temporary build data belong on D: because C: is constrained. The helper scripts set those build environments.

```powershell
./launcher-rust/scripts/check-native.ps1
./launcher-rust/scripts/dev.ps1
./launcher-rust/scripts/build.ps1
python launcher-rust/scripts/audit-parity.py
```

The build helper creates an EXE. Signed NSIS/update packages use the instructions in UPDATER.md; do not generate a replacement release key. No publish or production deployment occurs through these local commands. Exact current artifacts/hashes are in output/native-package-hashes.json.

Audit regression run:51Rust tests passed,7opt-in ignored,0failed. UI/native evidence and its limits are recorded in the canonical handoff and output evidence files. Public DTOs exclude credentials; tests use isolated fixtures. Do not test by editing user worlds/accounts/mods or overwriting running JARs.

## Shared data and scope

Profiles/accounts/settings/cosmetics retain the existing bat-client data folder. Config edits preserve unknown fields, lock/conflict-check and back up original bytes. Close Electron before editing shared configuration from Rust. Private config backups, token stores, signing keys and protected mod mapping files must not be published.

The Rust migration targets launcher responsiveness/overhead. Changing the launcher language does not itself increase Java Minecraft FPS. Minecraft mod performance and Fabric/Forge port work remain a separate paused scope.


Current migration checkpoint (2026-10-31): official game/assets/library installation and managed Java provisioning now exist, with fresh Fabric1.21.1 install/resolve and real Java21 download/probe evidence. GIF cape import saves decoded frames/timings locally; online sharing remains first-frame only. Tools page was removed at the user’s request. See RUST-LAUNCHER-AGENT.md and PARITY-AUDIT.md for exact remaining gaps; this is not full Electron parity or a public release.


2026-11-04: deep-black theme packaged; Mods now supports sorting and project details/gallery with compatible version/file selection. Wardrobe supports named skin/cape collection slots (save equipped, equip, remove), including stored cape frame timing. See canonical notes for test scope and remaining full-migration work.


Further 2026-11-04 checkpoint: pure-black surfaces include selected controls and Home’s formerly bright image background. Installed now has explicit update checks/per-mod updates with preserved old JARs and rollback, and Bat/Electron .batpack import/export that preserves disable choices and excludes accounts from exports. Native fixtures, live disposable mod-update helper and UI contracts passed; see canonical notes for remaining limits.
