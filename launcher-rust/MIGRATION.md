# Rust launcher parity tracker

Source of truth: existing Raven-Client launcher. Never delete or replace the Electron implementation while migration is incomplete.

## First priority

User reconfirmed complete existing Raven-Client feature parity comes FIRST. No new redesign or optional feature expansion ahead of launch/auth/cosmetics/settings/updater migration. Existing approved layout is retained; only requested usability fixes accompany the port.

## Current audited checkpoint — 2026-10-31

**Migration is incomplete.** The current source-backed status is [PARITY-AUDIT.md](PARITY-AUDIT.md), generated from an explicit review of all90unique Electron channels, current native command wiring, settings consumers and implementation limits. Machine-readable coverage/source hashes: output/parity-audit.json. Do not use the historical checkpoints below as present status.

- Native installed Vanilla/Fabric launching exists, including cached client repair, existing agents, custom JVM composition and selected Java discovery. Fresh game/Java/loader/agent installation and Forge/NeoForge/Quilt launching are missing.
- Profile/offline-account core actions, catalog, local PNG cosmetics and core Modrinth install/manage exist. Microsoft login was user-confirmed; refresh/gameplay and live uploads remain unverified. Premium removal, new GIF imports and cosmetic slots are missing.
- All26visible settings persist;16still have no current runtime consumer. Saving a checkbox is not implementation of that feature.
- Startup signed Rust updating is implemented with verification tests and mocked automatic flow; live channel remains disabled/unpublished. Mac/Linux native packages and actual two-version installed replacement/restart remain release work.
- Source audit:90unique Electron IPC channels/91registrations,26Rust commands and25literal frontend calls; no unregistered frontend call found. Coverage ledger is in PARITY-AUDIT.md rather than the stale checkboxes previously here. Command counts are not feature-completion percentages.
- No production/release deployment, user-library modification or new game run performed for this audit. Keep Electron until the release blockers and real acceptance tests pass.

## Validation rules

- Native compilation, isolated fixture tests and actual lifecycle evidence required; browser screenshots do not prove native game launch.
- Test premium/offline auth, agent plus authlib compatibility, skins, static/animated capes, custom Java/JVM arguments and opt-outs before replacing Electron.
- Preserve updater host/checksum validation, atomic swaps, staging cleanup, native platform packages and recovery.
- Tests must use isolated fixture folders; no user mods or worlds changed to prove a test.

## 2026-10-30 delivered native mod slice

Files: src-tauri/src/library.rs, mods.rs; ui/mods.js plus Mods markup/styles; icons/icon.ico and icon.png; NSIS configuration. Eleven offline tests pass plus explicitly invoked live FerriteCore1.21.1Fabric download/disable/enable/remove fixture. UI smoke uses an isolated fake IPC bridge only in the test harness; it is not production data and does not prove native WebView visual behavior. Native Rust backend test downloads the actual API-selected artifact and checks SHA512. Dependency opt-outs are never silently cleared by installing a different parent mod. Sodium-dependent addons disable together; re-enable blocked until Sodium enabled. Remove is immediate and recoverable in .bat-removed-mods, not permanent disk-space cleanup. No user-mod action performed by automated validation.

Feature parity remains INCOMPLETE: current app is still a development build with Launch disabled. Do not relabel it as finished or replace Electron before launch/auth/cosmetics/updates are ported and verified. Game version metadata/rules/inheritance/Java install/args/agents/native extraction/authlib/premium tokens must be implemented together, not bypassed to demonstrate a superficial vanilla launch.

## Installed artwork, animation and Java discovery (2026-10-30)

Native ZIP metadata artwork support for Fabric/Quilt/Forge/NeoForge/mcmod.info, raster signature checks, bounded archive/entry reads and8MB/128entry invalidating cache. No extraction/networklookup; disabled mods retain images. User explicitly selected a subtle upward entrance animation for Installed, not removal. Reduced-motion honored. Java discovery now exposed in Settings with manifest-required major, custom/bundled/environment/vendor candidates and timed hidden -version probes; discovery only, not runtime download or complete launching. Existing settings and arguments untouched.

## 2026-10-30 profile/account/settings/auth implementation checkpoint

Native profile create/edit/copy/favorite/delete/select implemented. Metadata-only deletion keeps game files; copied fields/opaque values preserved except original legacy-copy exclusions (gameDir/mods). Profile editor supports name/version/loader/RAM override/favorite/custom JVM arguments. Settings supports existing RAM, Java path, game directory, custom JVM options, download threads and game-window dimensions/fullscreen. Other settings such as Discord RPC/tray/startup/preset behavior still pending, so settings IPC parity is PARTIAL.

Shared config writes use an OS file lock across native instances, checksum-named original-byte backups, final original-byte conflict check and atomic replacement. Legacy Electron running guard prevents normal cross-launcher overwrite. Full original configuration remains private; never publish backups. Unknown account/token/cosmetic/profile/settings fields preserved. Tests only edit temp fixtures, not user config.

Offline account add/select/remove persists; Microsoft selection persists but old credential references stay opaque. Existing saved skin PNG/head thumbnails are used for native account heads without smoothing. Microsoft deletion remains blocked until old and native credential cleanup parity is finished. Skin/cape upload/equip/remote sharing still pending.

Microsoft OAuth native implementation: existing public client ID/endpoints; PKCE S256, random state, exact callback-origin/path/state checks, duplicate callback rejection, separate remote webview with no main-window IPC capability, background-thread creation per Tauri guidance, timeouts/cancel cleanup. MSA→Xbox→XSTS→Minecraft profile+Java entitlement; refresh implemented. Tokens backend-only in OS credential store (Windows/macOS/Linux configured explicitly), chunked binary records to respect Windows size limits, no plaintext fallback. New random native credential reference rustAuthId preserves old Electron identifier/token store and cosmetic fields. Secure token write is rolled back if config commit fails. An offline username conflict is never silently converted. Live Microsoft login/refresh NOT verified yet; public service integration and account-family cases require interactive testing.

Windows native credential-store roundtrip with synthetic tokens PASS, cleaned its disposable entries. PKCE RFC vector/callback rejection tests PASS. Hidden native auth-window --auth-window-smoke mode checks WebView create/close only; does not prove sign-in. Product button exists, but do not mark authentication fully verified until actual user sign-in+refresh succeeds.

JVM quoting/managed-option validation and GC-policy composition ported/tested; custom agents and quoted Windows paths retained, profile GC overrides global without G1 residue. Settings/profile validators connected; native game pipeline not yet implemented, so GC composition is not active in game launches. Launch remains disabled.

User supplied monochrome 246x138 Home image, copied byte-for-byte to ui/assets/home-widget-user.png. CSS fits full scene on right at full hero height, pixel rendering; no new AI artwork. Existing selectors/Launch overlay kept.

## 2026-10-31 canonical handoff and settings surface

Read ../RUST-LAUNCHER-AGENT.md first; older status paragraphs above are historical. User confirmed actual Microsoft sign-in worked; refresh/gameplay/otherOS remain unverified. Native settings now cover26 preferences plus write-only skin token management, preserving unknown/private values and firstLaunch.18 newly exposed legacy choices have explicit pending-runtime notice; persistence is implemented, Rust tray/startup/translations/RPC/game/update consumers are not.30 offline Rust tests pass,4 opt-in ignored. Updated UI fixture verifies preset/language/RPC/title payload, preview/news checks pass and screenshots inspected. Current native build packaging in progress; no game-launch completion claim.

## 2026-10-31 packaged checkpoint — supersedes earlier source-only status

Native optimized app and NSIS rebuild SUCCESS1m19s; installer7.67MiB. Includes catalog/auth guard/generated hero/provider SVGs/red action buttons/animations plus26-preference settings and write-only skin token controls.30offline Rust tests PASS/4opt-in ignored; UI library/preview/news checks PASS. New app opened, PID2936 responding with title Bat Client Next · Development; startup stderr empty at3s. This is startup evidence, not native visual interaction or Minecraft gameplay. Installer built only, not executed/published. No production/user-world/mod/config writes by automated tests. Private credentials stay omitted from handoff; BOM check passed.

Current package hashes (output/native-package-hashes.json):
- `D:/BatClientBuildCache/rust-launcher-target/release/bat-client-next.exe` SHA256 `741F008027BFC1E9409E5836BE519E58F3A6E543550AF9382904F2E887601C26`
- `D:/BatClientBuildCache/rust-launcher-target/release/bundle/nsis/Bat Client Next_0.1.0_x64-setup.exe` SHA256 `611E0F880922864A51428ECD09F644F77710058A85DD4547684ACBD928C64003`

All current source changes are now in the native package. Historical hashes/source-only paragraphs above are retained as dated checkpoints, not the current binary. README/MIGRATION earlier live-auth-unverified wording is superseded by user-confirmed login. Native game launching/Java installer/loader/download pipeline/authlib+agent/cosmetics/updater and remaining90-feature parity still require implementation. Launch remains disabled. Continue from complete game pipeline next; do not replace Electron or claim this launcher finished.

## 2026-10-31 — version browser, settings categories, account motion and Wardrobe

Latest user steering: all official Minecraft versions in editor AND creator; release/snapshot/beta/alpha switches; account-switch animation; REMOVE the skin-service credentials panel shown in screenshot; Settings tabs matching Electron with all existing preferences and animations; replace empty Wardrobe. Credential UI removed without clearing/changing existing token. Backend write-only DTO support remains private infrastructure, not a visible user panel.

### Files/behavior

- ui/profiles.js: official catalog is now a real scrollable version browser with independent search and Release/Snapshot/Beta/Alpha switches. Selected version text remains separate, so prefilled1.21.1 no longer hides the rest. All filtered entries rendered, no artificial short list. Counts, native keyboard-select list, loading/error/retry/manual entry, safe text labels. Shared editor handles create/edit. Filters preserve current selection, never retag incompatible game/loader artifacts.
- versions.rs: regression fixture includes beta/alpha. Explicit REAL Mojang API test verifies all4types and1.21.1/1.8/1.8.9/b1.7.3/a1.2.6; PASS2.07s (2version tests total).
- ui/settings.js/styles.css: six keyboard-accessible tabs Performance, Display, Launcher, Files & Network, Sound & Social, Advanced. All26 native preference fields retained and saved across tabs. Appearance in Display, Java probe in Performance. Invalid hidden inputs reveal their owning tab before reportValidity. Reduced-motion honored for panes/switches/account transitions. Pending-runtime notice retained; no claim tray/RPC/game/update integrations implemented simply because their preferences persist.
- ui/accounts.js/account-picker.js/app.js: popup/identity/card transitions; committed account chip animates. Skin head drawing now rejects stale username loads, preventing old head flashes after quick switches. Cosmetic changes invalidate cached heads and refresh account/picker artwork. No account/token deletion policy changed.
- src-tauri/src/wardrobe.rs (NEW): wardrobe_outfit(username) returns explicit cosmetic-only DTO (skin/cape/frames/delays/model/premium). Read only bounded regular files under skins; no arbitrary paths/private account fields. Existing customSkin fallback/customCape/PNG-or-GIF/capeFrames/capeDelays contracts retained. Frames max120/16MBcombined encoded, delay20..10000ms. Individual file max4MB.
- save_cosmetic(edit): skin/cape/model/remove_skin/remove_cape, explicit account and classic/slim; actual PNG decoder validates/decompresses uploads, supported skin64square/64x32 integer multiples<=1024wide, cape64x32 multiples<=2048wide, max4MBcompressed/16MBdecoded, rejects APNG/truncated/wrong-size. png0.18.1 added from already cached registry; Cargo.lock updated offline. No GIF-import encoder yet.
- Local cosmetic writes use unique UUID image names, staged/synced persist_noclobber, then config atomic backup/commit. Roll back newly created image if config commit fails. Existing image files are retained for recovery; removes detach metadata only. Account private refs/unknown fields/other cape metadata preserved unless explicit cape replacement/removal. Legacy launcher and Minecraft-running guards apply. No upload network call, no token exposure.
- ui/wardrobe.js/index.html: nonempty Wardrobe with account selector, 3D drag preview, classic/slim, PNG choose/preview/Apply/local remove controls and saved animated-frame preview. Uses exact existing libs/skinview3d.bundle.js copied to ui/vendor (no new remote script). Background/hidden rendering paused, cape timer cleared, reduced-motion stops animations, viewer disposed on unload. WebGL failure shows texture fallback. Asynchronous account/image generations prevent applying late preview to another account. Static images/apply and local model changes now persist; user-owned settings/cosmetics are only modified on explicit UI actions.

### Honest Wardrobe limits

This is local shared-library cosmetic management, not full Electron online parity. Hosted skin publishing, official premium skin upload/fetch, cosmetic slots/equip API, animated-GIF import and agent/game integration still require migration. UI explicitly says local save/online sync pending. Existing animated cape metadata/files preserved; existing PNG frames can be previewed. Do not claim new GIF uploads or remote player cosmetics verified.

### Verification

37Rust tests:33PASS/4opt-in ignored/0failed; includes valid PNG skin/cape/model/remove lifecycle, invalid images leave config byte-identical/no skins directory, opaque premium refs untouched and old image files kept. Explicit strengthened live version test also PASS. Native tests edit TEMP fixtures only, never user accounts/worlds/mods.

Existing library UI harness PASS after tab changes. New scripts/wardrobe-ui-smoke.cjs uses isolated fake bridge/temporary D:userData/offscreen WebGL: release list, all type switches, alpha search,6tabs/credential-panel absence, correct tab visibility, account selector, real64x64PNG browser File/DataTransfer preview and Apply command payload, account animation PASS. New output/version-browser-preview.png, settings-display-preview.png, wardrobe-preview.png visually inspected. png fixture skin intentionally returns defaultSteve after mock save; not proof of actual native persistence (covered in Rust fixture tests). Native EXE packaging/restart pending at this checkpoint.

Full launcher still NOT finished; game launch remains disabled. No production/release/website/mod performance changes.

### Packaged and reopened — 2026-10-31

Native NSIS build SUCCESS1m25s,7.83MiB. All source from the version/settings/account/Wardrobe batch is included. App reopened PID2464, responding with expected development title; startup stderr empty at3s. Installer built only, not executed/published. Current hashes supersede earlier dated artifacts:

- `D:/BatClientBuildCache/rust-launcher-target/release/bat-client-next.exe` SHA256 `CA4A34BB0EA4BE900FEE565F4A73AED5D9CF9C4D10E54C23067D8E8348BBA9DF`
- `D:/BatClientBuildCache/rust-launcher-target/release/bundle/nsis/Bat Client Next_0.1.0_x64-setup.exe` SHA256 `67A7FC8D0C68E1812379A4644D4FF349B2C7E34B7D30ED465C534E0FB648CC39`

33offline Rust tests PASS/4opt-in skipped; additionally strengthened real Mojang catalog+parser tests PASS2.07s; both isolated library and version/settings/Wardrobe UI flows PASS. Screenshot inspection covers requested UI. Native game launch/online cosmetic publishing/Microsoft skin uploads/animated imports/updater remain unimplemented, not certified by startup. Current executable stays open for user review.

## 2026-10-31 — UI revision plus real installed-game launch (SOURCE, packaging pending)

Latest user asked to redesign Wardrobe again, horizontal SETTINGS NAVBAR (not sidebar), floating version dropdown separate from editor surface, working Minecraft launch with Java agent. Implemented new open character stage + compact Skin/Cape-tab studio, front/back camera controls, horizontal six-category settings navigation with left/right keys, floating top-layer version popover inside modal ancestry (avoids inert/clip), keyboard selection/search/type switches/click-away/Escape. All previous local cosmetic controls retained. Updated scripts/wardrobe-ui-smoke.cjs validates popover opening/bounds, tabs, Wardrobe, account motion, native Launch command payload and unsupported-loader disabled state. PASS; fresh output screenshots inspected. Settings integration notice updated for now-working installed game controls.

### Native launch architecture

NEW src-tauri/src/game_files.rs: installed Vanilla/Fabric resolution; bounded inheritance/cycle checks; child args/libraries merge (Maven classifiers retained); Maven path validation, platform/feature rules, SHA1 checks where manifest provides checksums; base client inherited correctly; classpath and placeholder substitution; modern/legacy argument forms; native classifier JAR selection/extraction with confined filenames, max512files/512MBtotal/128MBentry. Does not auto-download/reinstall any mod, change Sodium choice/options/JVM preferences, retag incompatible JARs or alter user worlds. Missing libraries/client/index/Java/loader/agent yield specific fail-closed errors.

NEW game_launch.rs + ui/game-launch.js: real Rust std::process::Command launching, Java discovery, quoted per-profile/global JVM composition/GC policy, memory/window/fullscreen args, real Bat agent and offline authlib-injector, Fabric classpath-isolation bypass, namespace intermediary/official, signed profile properties for modern offline clients. Rust-only tokens, no command/token DTO/log dump. Premium expiry refresh reuses native Microsoft chain before config write lock; persists opaque fields and credentials through existing secure store. Updated microsoft.rs extracts refresh_at/fresh_for_launch. Real premium refresh/gameplay NOT verified by offline test.

Launch button ENABLED for installed Vanilla/Fabric with account/loadout. Forge/NeoForge/Quilt native install/launch still pending and explained; new version/loader downloads and automatic Java install also pending. Existing expected appData bat-cosmetics.jar/authlib-injector.jar required; native installer does not yet bundle/install missing agent prerequisites. Existing Electron remains available/intact. Do not claim full fresh-PC or cross-platform parity.

Game lifecycle: single active/preparing gate, private rust-game-logs, stage polling, child wait/exit restoration, native TempDir retained until child exits then cleaned, skin bridge kept alive. Closing Rust main window while preparing/running hides it to keep game cosmetics service alive; returns on game exit or preparation error. No user JVM killed. Private command args never returned to frontend. Session refresh outside global WRITE_LOCK avoids self-deadlock.

NEW skin_bridge.rs: per-game127.0.0.1 ephemeral-port HTTP server with4workers, signed textures via ephemeral2048bitRSA/SHA1 (Yggdrasil protocol), metadata/public key, own profile/textures/offline join/auth/services routes. Native Minecraft offline UUID MD5 matches Java. Reads only bounded regular skins files; cache24MB/128assets+128profiles, bounded remote time/body. Remote fixed Bat skin API and official Mojang profiles only; no arbitrary URL or token forwarding. Premium clients without authlib retain ORIGINAL Mojang signatures/profiles, not locally re-signed properties; premium local skin override/hosted multiplayer parity still incomplete. Existing capes/frames read; no new uploads to production. Dependencies rsa0.9/rand0.8/md-5/sha1/tiny_http/regex added; Cargo.lock pinned. Local texture server has no account credential endpoint and never binds LAN. authlib emits expected HTTP warning for loopback; no TLS/WAN service exposed.

### Failures found by meaningful native tests and fixed

1. Native preflight initially rejected missing assets/indexes/17.json. Electron MCLC cache uses assets/indexes/1.21.1.json. Added alias ONLY after checking identical official SHA1, and pass actual filename to Minecraft. Actual alias hash exactly e61ea955de95de1f99d36616a8b46147e9f6f5d7. No cached file rewritten.
2. Actual JVM failed before premain: Java instrumentation cannot load agent JAR via Rust canonical Windows verbatim \\?\ path. Strip prefix (including UNC conversion) only for Java agent argument paths. Regression test covers Windows/UNC, saved user paths/arguments unchanged.
3. UI smoke first checked Wardrobe before async WebGL texture decode completed; replaced brittle short delay with bounded readiness wait. Latest test passes, no native claim from fake IPC.

### Actual native Minecraft evidence

Explicit `cargo test ... native_cached_launch_smoke -- --ignored --nocapture` now PASS. It creates D:/BatClientBuildCache/native-rust-game-*/appdata/bat-client and separate game folder. Reads installed game caches; copies existing agent/authlib and existing mods, uses synthetic offline BatNativeTest, own local Steve, and existing Java21; no real account credentials/config/worlds/options copied or mutated. Existing mod set in this cache was FABRIC API ONLY (54 nested Fabric API mods), NOT Bat Client mod. Do not claim mod+agent performance compatibility or Bat mod native UI from this test.

Passing fixture D:/BatClientBuildCache/native-rust-game-DeuOCr; PID7904 owned test JVM. Renderer+agent marker reached45312ms after spawn, total46.56s. Log verifies BatCape premain/mappings1.21.1/intermediary, authlib1.2.8 loaded/public-key transformations, Minecraft1.21.1+Fabric0.20.1, OpenAL and atlas creation. It then kills ONLY its own disposable menu-only test child, waits, closes bridge/native temp. No world entered, no PC/game UI control used, no screenshots/native visual/menu/gameplay/FPS proof. Prior failed fixtures retained as evidence. Production user JVMs untouched.

Default latest suite:46total/41PASS/5opt-in ignored (includes native game test). Source changes after last native pass add classifier-preservation/alias tests, premium original-signature handling and status/error restoration; offline tests PASS. Native public IPC child lifecycle/premium gameplay/full online cosmetics still need user end-to-end review; compile/unit/prepare test is not that proof. Documentation and package hashes MUST be updated after rebuilding; last distributed dev binary still older until packaging completes. No release/site/production changes.

### FINAL local package checkpoint — 2026-10-31

Final full suite48total:43PASS/0failed/5opt-in ignored,0.95s. Earlier explicit native Minecraft1.21.1Fabric test PASS at45.312s; tests do not prove world gameplay/premium cosmetics/Bat mod compatibility. Updated floating-version/settings-navbar/Wardrobe/launch UI smoke PASS and screenshots inspected. Final NSIS build SUCCESS1m30s,8.38MiB; installer built only, not executed/published. Native app reopened PID5444, responding with development title and empty startup stderr at3s.

All source in this UI/installed-launch batch is now packaged. Current hashes supersede previous dated artifact hashes:
- `D:/BatClientBuildCache/rust-launcher-target/release/bat-client-next.exe` SHA256 `E6A79F45992EB074D133A1222E2DE108F6572D7F65A6C350CD83302D3D15A2A9`
- `D:/BatClientBuildCache/rust-launcher-target/release/bundle/nsis/Bat Client Next_0.1.0_x64-setup.exe` SHA256 `2C3A55A831E1B357346AB70D25815BE02219330F1D775F5986612033BC6B15AD`

Current next steps: user can test an ALREADY INSTALLED Vanilla/Fabric loadout (native validated1.21.1Fabric, Java21, both agents, Fabric API). Native new game/Java/loader download/installation and Forge/NeoForge/Quilt startup remain pending. Missing prerequisites produce explicit messages; nothing silently falls back to another engine or reinstalls mods. The native package still expects existing appData agent/authlib JARs, not a full clean-PC distribution. Full premium refresh/gameplay, local-premium cosmetic override, hosted skin publishing/animated imports/slots, updater and other feature parity remain unverified/unfinished. Mod performance/ports remain paused; no FPS claim. Preserve existing Electron and production releases.

No current test JVM left running. Native game test used disposable D: directories and copied agents/Fabric API, not Bat mod or user worlds/accounts/options. Do not mistake its marker for a visual full-menu, actual-world, frame-time or chunk-throughput result. Frontend preview harness is not the native runtime. Existing worlds/settings/customJVM/cosmetics/opt-outs/release packages/production remain intact.

## 2026-10-31 — Wardrobe account picker, animated-cape wording, compact loadouts

User screenshot/request: replace Wardrobe native gray account select with the same Home account switcher; explicitly mention animated capes; make loadouts a little wider and less tall.

ui/account-picker.js now exposes shared BatAccountPicker.open/close with anchor, selection getter and optional onSelect. Same250px animated top-layer black menu, real pixel skin heads, provider SVGs, selected checkmark, keyboard/Escape/click-away/resize/scroll handling and account management shortcut. Home still persists the active launch identity. Wardrobe uses identical visual component while preserving its existing editing-account scope; changing whose cosmetics are edited does NOT silently switch the Home launch account. Menu subtitle makes this explicit. Native select remains hidden internal state only; no system gray dropdown. Wardrobe chip shows current editing account/head, disables during saving/no accounts, refreshes through existing artwork/cache guards.

Files: ui/account-picker.js, wardrobe.js, index.html, styles.css. Cape card heading now Static & animated capes; description explicitly supports choosing static PNG and previewing existing animated capes, frame status and stage metadata identify animation. This is wording plus existing-frame preview, NOT new GIF-import support. GIF imports/online publishing/premium uploads remain pending.

Loadout grid cards260..290px wide with reduced choice padding16px/18px, icon gap12px, subtitle gap12px, footer8px/10px and30px action buttons. Height content-driven and notably shorter, with slightly wider readable cards. Create/edit/functionality untouched. No Rust backend/game/agent/config changes this batch.

Verification: JS syntax PASS; existing library UI harness now selects Home account through actual picker (not only direct method), CRUD/settings PASS. Extended Wardrobe UI harness checks shared picker open/head/hidden native select, animated wording, multiple-account Wardrobe selection, and that Home launch identity remains unchanged; full existing version/settings/native-launch UI contracts also PASS. Fresh loadouts-preview.png and wardrobe-account-picker-preview.png inspected. Test fixture accounts/images only; no actual user configuration changed by tests. Prior43Rust test/native1.21.1agent startup evidence remains applicable; no needless game rerun for UI-only changes. Packaging/reopen pending at this checkpoint.

### Shared-picker UI batch packaged

Native EXE/NSIS rebuilt SUCCESS1m21s,8.38MiB; app reopened/responding, startup stderr empty at3s. Both UI harnesses PASS and screenshots inspected. No backend/agent/gameplay changes. Installer not installed/published. Current package hashes:

- `D:/BatClientBuildCache/rust-launcher-target/release/bat-client-next.exe` SHA256 `F388E9D735CE76C08492005B262AC6A715A01035354BA24C5B628CDCA04B78D5`
- `D:/BatClientBuildCache/rust-launcher-target/release/bundle/nsis/Bat Client Next_0.1.0_x64-setup.exe` SHA256 `96ECC6B79DB4D4E42E2CC7D68F2BC27E7B85CE3A73F3F491CE5AEE286CF04648`
