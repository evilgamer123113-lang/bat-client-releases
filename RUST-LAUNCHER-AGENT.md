# VERIFIED PUBLIC RUST RELEASE: 0.1.13 (2026-11-21)

- **Release:** `https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/rust-v0.1.13` (`--prerelease --latest=false`, preserving Classic `v2.2.97` as `latest`).
- **Companion mod bundles (`bat-mods-v6`):** `https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/bat-mods-v6` — 30 per-target XOR-masked `BATMODS1` `.dat` files rebuilt with cross-platform `BatWebFileSystem` path resolution across all 30 Fabric/Forge ports (1.8–1.21.11), fixing the black screen after the loading screen on Linux, macOS, and Windows paths containing spaces (`file:///` leading-slash stripping in Ultralight + percent-encoded segment decoding + explicit MIME types).
- **Builds & verification:** Windows x64 NSIS (`D:\BatClientBuildCache\rust-release-0.1.13\Bat-Client-0.1.13-windows-x64-setup.exe`, `10994515` B, sha256 `7c29d6502cb1e38e6e2e607c69cfd6ee99fab3d019e02c49d07b92c6e84fcd9d`), macOS Apple Silicon + Intel (Codemagic `6b01de5f5f80e1d95a554557`), and Linux x64 AppImage + `.deb` (CircleCI `#27` `e3d6ae56-3941-4158-bbbb-7ba4587d3b98`). All 4 updater payloads signed with `--app-version 0.1.13`. `companion_mod::tests`: `5 passed; 0 failed`. `WebAssetMemorySmoke`: `WEB_ASSETS_IN_MEMORY_OK`.
- **Live site:** Deployed BOM-free `/var/www/html/update.json` (Classic `2.2.97`, Rust `0.1.13`) and `/var/www/html/changelog.html`. Full manifest saved in `releases/rust-v0.1.13-bat-mods-v6.md`.

# VERIFIED PUBLIC RUST RELEASE: 0.1.12 (2026-11-21)

- **Release:** `https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/rust-v0.1.12` (`--prerelease --latest=false`, preserving Classic `v2.2.97` as `latest`).
- **Companion mod bundles (`bat-mods-v5`):** `https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/bat-mods-v5` — 30 per-target XOR-masked `BATMODS1` `.dat` files rebuilt with all 4 desktop Ultralight native classifiers (`windows-x64`, `linux-x64`, `mac-x64`, `mac-arm64`) across all 30 Fabric/Forge ports (1.8–1.21.11), fixing Linux/macOS HUD and Right Shift menu opening (`Missing native library: .../linux-x64/libSph0.so`).
- **Launcher fix (`library.rs`):** Scoped `ensure_game_closed()` to Bat Client's own non-zombie `net.minecraft.client` process (`minecraft_runtime` / `bat-client` / `bat-cosmetics`), fixing false-positive `"Save and fully close Minecraft before changing mods"` launch preparation failures on Windows and Linux; raised companion mod download timeout to `600s`.
- **Builds & verification:** Windows x64 NSIS (`D:\BatClientBuildCache\rust-release-0.1.12\Bat-Client-0.1.12-windows-x64-setup.exe`, `10994838` B), macOS Apple Silicon + Intel (Codemagic `6b01ac9f17a83bd73150048a`), and Linux x64 AppImage + `.deb` (CircleCI `#25` `aae7ebe5-f5fa-447c-bbda-f1c1a36c0378`). All 4 updater payloads signed with `--app-version 0.1.12`; Windows verified via `--updater-verify-smoke` (`exit 0`). `cargo test --locked --features custom-protocol`: `117 passed; 0 failed; 17 ignored`.
- **Live site:** Deployed BOM-free `/var/www/html/update.json` (Classic `2.2.97`, Rust `0.1.12`) and `/var/www/html/changelog.html`. Full manifest saved in `releases/rust-v0.1.12-bat-mods-v5.md`.

# VERIFIED PUBLIC RUST RELEASE: 0.1.11 (2026-11-16)

- **Release:** `https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/rust-v0.1.11` (`--prerelease --latest=false`, preserving Classic `v2.2.97` as `latest`).
- **Companion mod bundles (`bat-mods-v4`):** `https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/bat-mods-v4` — 30 per-target XOR-masked `BATMODS1` `.dat` files rebuilt with `"mod-icons.png"` registered in `WebAssetManager.WEB_FILES` (`WEB_VERSION = "121"`), serving the 512×512 (60-icon, 17.1 KB) pure-white PNG sprite sheet in memory across all 30 Fabric/Forge ports.
- **Builds & verification:** Windows x64 NSIS (`D:\BatClientBuildCache\rust-release-0.1.11\Bat-Client-0.1.11-windows-x64-setup.exe`), macOS ARM64 (`6afaf466f436e85f3eeefe17`), macOS Intel, and Linux x64 (CircleCI `#19` `734a1d35-61ee-4647-bf5c-120c63fcc67b`). All 4 updater payloads signed with `--app-version 0.1.11` and verified via `--updater-verify-smoke` (`exit 0`). `companion_mod::tests`: `5 passed; 0 failed`.
- **Live site:** Deployed BOM-free `/var/www/html/update.json` (Classic `2.2.97`, Rust `0.1.11`) and `/var/www/html/changelog.html`.

# VERIFIED PUBLIC RUST RELEASE: 0.1.10 (2026-11-16)

- **Release:** `https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/rust-v0.1.10` (`--prerelease --latest=false`, preserving Classic `v2.2.97` as `latest`).
- **Companion mod bundles (`bat-mods-v3`):** `https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/bat-mods-v3` — 30 per-target XOR-masked `BATMODS1` `.dat` files rebuilt with an optimized 512×512 (60-icon, 17.1 KB) pure-white PNG sprite sheet (`bat-client-mod/src/main/resources/assets/batclient/web/mod-icons.png`), replacing 59 inline SVGs in `app.js` / `style.css` / `hud-theme.css` so Ultralight decodes one texture without SVG lag.
- **Builds & verification:** Windows x64 NSIS (`10972962` bytes, sha256 `6cad21ee2670dea447955c4efb0cbeb026679794c9bfd47055e5469c2bae46cd`), macOS ARM64 (`6afadfa5479473484fded3f8`), macOS Intel, and Linux x64 (CircleCI `#11` `87f24d18-3261-46c0-9043-f5a305eab6e7`). All 4 updater payloads signed with `--app-version 0.1.10` and verified via `--updater-verify-smoke` (`exit 0`). `companion_mod::tests`: `5 passed; 0 failed`.
- **Live site:** Deployed BOM-free `/var/www/html/update.json` (Classic `2.2.97`, Rust `0.1.10`) and `/var/www/html/changelog.html`.

# VERIFIED PUBLIC RUST RELEASE: 0.1.9 (2026-11-15)

- **Release:** `https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/rust-v0.1.9` (`--prerelease --latest=false`, preserving Classic `v2.2.97` as `latest`).
- **Companion mod bundles (`bat-mods-v2`):** `https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/bat-mods-v2` — 30 per-target XOR-masked `BATMODS1` `.dat` files (`bat-mods-<engine>-<version>.dat`, ~22 MB each, ~44 MB for `forge-1.16.5`/`forge-1.17.1`, no raw `.jar` files exposed). `companion_mod.rs` downloads and SHA-256-verifies only the single launched `(engine, version)` bundle into `%APPDATA%\bat-client\companion-bundles\bat-mods-<key>.dat`; startup 684.5 MB prefetch removed from `main.rs`.
- **Companion mod fixes (all 30 ports rebuilt cleanly with ProGuard + encrypted web UI):**
  - `bat-client-mod/src/main/java/com/batclient/BatClientMod.java`: `RSHIFT` menu hotkey now checks `(client.currentScreen == null || client.currentScreen instanceof WebScreen)` so typing in chat or other menus never opens the mod menu.
  - `bat-client-mod/scripts/adapter_12111.py`: `ChatHudMixin.bat$visible` now cancels vanilla `addVisibleMessage` unconditionally, fixing duplicate chat messages in `1.21.11`.
- **CI / Builds:**
  - Windows x64 NSIS (`Bat-Client-0.1.9-windows-x64-setup.exe`, `10967258` bytes, sha256 `a324ebdce22f5d05c4aa63064862b71ffb6fb90678043e2a00b184886cb28863`) built locally.
  - macOS ARM64 (`Bat-Client-0.1.9-macos-apple-silicon.app.tar.gz`, `13386743` bytes, sha256 `24115e9efb4e41d7c18ef31a4ce05e9577badfac44efca7d8a9ad554220aa2d1`, + `.dmg`) and macOS Intel (`Bat-Client-0.1.9-macos-intel.app.tar.gz`, `14033301` bytes, sha256 `61fc3878c7f1d65ef0972789d3a4a5d3b2121fe20bf79c5c14962f09f974ccbe`, + `.dmg`) built on Codemagic (`codemagic.yaml` `rust-macos` on `mac_mini_m2`, build `6af9c60ca89bf643d668c5c0`).
  - Linux x64 (`Bat-Client-0.1.9-linux-x64.AppImage`, `93522424` bytes, sha256 `e416df3394fb8c1e9969e371236a00a1b7a97e10997c246e84e0915180b082d2`, + `.deb`) built on CircleCI (`.circleci/config.yml` `linux-release` on `ubuntu-2204:current`, pipeline `#6` `cd398c9c-ad52-49a2-a849-06ceb196b154`).
  - All 4 updater payloads signed with `--app-version 0.1.9` and verified with `bat-client-next.exe --updater-verify-smoke` (`exit 0`).
- **Tests:** `cargo test --locked --features custom-protocol --manifest-path launcher-rust/src-tauri/Cargo.toml`: `117 passed; 0 failed; 17 ignored`.
- **Live site:** Deployed BOM-free `/var/www/html/update.json` (preserving Classic `v2.2.97`, updating `rust` to `0.1.9` for all 4 platforms) and `/var/www/html/changelog.html`.

# Local launcher access integration — 2026-11-09

The normal Java spawn now supplies BAT_CLIENT_LAUNCHER_PID as a child-only environment value. The companion mod checks the actual direct parent PID and the Rust binary name before resource initialization. JVM options and cosmetic-agent arguments are supplied as before. The check is offline and does not authenticate the publisher cryptographically. This source change requires a matching mod and updated Rust launcher. The currently published 0.1.4 and Classic lack this handshake.

Windows optimized Rust tests finished with 109 passed, 0 failed and 17 ignored after closing the private game. The concurrent test run initially hit two expected game-open guards in modpack tests. Logs are `launcher-gate-rust-r2.log` and `launcher-gate-rust-final.log` under `D:/BatClientBuildCache/mod-ports-20261108`. Windows Fabric and Forge 1.21.4 native smoke used a separate Rust launcher-parent fixture. That establishes process-contract behavior, with packaged launcher integration and Linux/macOS runtime checks still open. No release.

# VERIFIED PUBLIC RUST RELEASE: 0.1.4

## 2026-11-08 — Rust 0.1.4 published, updater and changelog live

User explicitly authorized publication and changelog catch-up. Public Rust preview release https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/rust-v0.1.4 contains all12 verified Windowsx64Setup/Linuxx64AppImage+Debian/macOSIntel+AppleSiliconDMG+updaterarchive/signature/checksum assets. Private source3f142d4cbd9f999f63997f1b7ef5987af9840071; native Actions42458796567 all3runnersPASS; local WindowsNSISPASS2m54s. Four updater payloads signed with original protected key; native signature/tamper/versionbinding verificationPASS. Both Mac plist0.1.4/MachOarchitecturePASS; Linux ELF64x86_64 and Debian0.1.4/amd64PASS. All12 PUBLIC files downloaded/streamed and matched staged SHA256+sizes before activation. ClassicLatest2.2.97 and every legacy updater field preserved. Windows updater signing is separate from Authenticode; Mac ad hoc signed/notnotarized. No live per-platformMinecraftvisual acceptance claim.

Release includes ALL unreleased Wardrobe work since0.1.3: black/white Bat slot-grid redesign, search, crisp heads and selected state, scroll-only model zoom, direct PNG/GIF upload into chosen empty card, automaticSkinN/CapeN, removal of optional-name/Savecurrentlook/Apply&save confirmation rows, upload retry, sparse20slot handling, duplicate chosen cards sharing immutable files, occupied-slot protection, restart/account/model persistence and asynchronous online sync. Select saved card to equip. Classic/Slim saves automatically and is supplied on NEXT Minecraft launch; running game is not hot-swapped. GIF quality/frames/timing preserved; hosted cape sharing remains still-frame only. Native107PASS/0FAIL/17ignored/2unrelatedactive-game-modpackfiltered; UI auto-upload/retry/restart/model+rollback/search/account/scroll/layoutPASS. Exact development tests and limits in canonical RUST-LAUNCHER-AGENT.md.

Production deployed ONLY changelog.html then update.json via previously selected backed-up SSH method with baseline/new SHA guards and rollback backup /var/backups/batclient/release-rust-0.1.4. nginx and skinAPIhealthy. Both bare and www HTTPS manifest/changelog bytes match staged files exactly. Live rust.version0.1.4/enabledtrue; Classic2.2.97 unchanged. Changelog adds0.1.4 covering current and earlier unreleased iterations; existing0.1.0–0.1.3 and Classic history retained. Desktop/mobile browser assertions and captures PASS/reviewed. No other HTML/layout/theme/assets/ads/consent/download-gate/backend changes. Primary website local mirror synchronized. Raven root and website/update.json source manifests received Rust channel only while all their legacy fields remain intact, preventing future Classic release from discarding Rust metadata. Installed user's app/game not forcibly replaced. Automatic updates occur on launcher restart when enabled and game closed.

Evidence/scripts/packages/notes/public checks D:/BatClientBuildCache/rust-public-0.1.4, rust-public-0.1.4-packages, rust-public-0.1.4-site. completed.json records exact deployed hashes; changelogSHA256432aae44458ebfc924f2c0f14f690263829ff6dab807b8e134b61afde8dbe15d; manifestSHA256a4205c22649adbcea395303f91c712313bcf7505bdd7f797fd86ce18c7cede5a. Supersedes prior in-progress notes. Companion mod not released; performance/ports stay paused.

# Rust 0.1.4 release in progress — 2026-11-08

User authorizes publication, updater and public changelog, using the existing backed-up SSH method. Source3f142d4cbd9f999f63997f1b7ef5987af9840071; private native Actions42458796567. Windows NSIS and AppleSilicon packages built, updater signatures/tamper/version verification PASS. Mac ARM64 plist/version/architecture PASS. Linux/IntelMac still packaging. Changelog0.1.4 staged and desktop/mobile browser PASS, all0.1.0–0.1.3 and Classic history preserved. All unreleased Wardrobe iterations are consolidated in0.1.4, not invented old versions. Native107PASS /0FAIL/17ignored/2active-game-modpackfiltered; UI auto-upload/model/rollback/restart/scroll checks PASS. Build artifacts/scripts/evidence D:/BatClientBuildCache/rust-public-0.1.4[-site|-packages].

Public remains0.1.3 until all packages signed, published and public stream hashes match. Then deploy ONLY changelog.html and update.json, manifest last, backup /var/backups/batclient/release-rust-0.1.4, exact baseline/new hash guards and rollback. Classic top-level fields/download remain unchanged. No backend/theme/ads/consent/download-gate changes. Do not publish companion mod. No actual per-platform Minecraft visual acceptance claim. Existing installed app/game intact; local preview closed only for packaging. Finish release and update canonical root/site/Rust notes with exact verification before final response.

# Direct-card uploads + Classic/Slim completed and opened — 2026-11-08

Final optimized Windows local build PASSED2m21s, seven existing dead-code warnings, zero errors. Binary D:/BatClientBuildCache/rust-launcher-target/release/bat-client-next.exe; SHA256 7A34AB1E74E10B820FB99989BD07D919E5C4C8AAAB90116E85C59A32D148E2C3. Opened as PID 18464, native Bat Client window confirmed. No old installed launcher/game terminated. No website/GitHub/updater publication; public remains0.1.3.

Final behavior: optional name/Save current look bars and New preview/Apply & save/Cancel rows REMOVED. Upload from an empty card immediately validates and saves PNG/GIF into that exact sparse slot, automatic Skin N/Cape N name; no equip until saved card selected. Repeated identical upload to another chosen card fills it too while sharing immutable files. Occupied different-look cards never overwritten. Failed upload can Retry without file repick. Classic/Slim selector saves automatically and supplies correct Minecraft skin metadata on next launch; failed save restores previous selector. Model variant saved library maintained. Scroll-only bounded zoom and approved Bat grid/card theme preserved.

Final UI fixture passed including direct selected-card upload, no removed controls, errors/retry, repeated chosen cards, restart, explicit equip, model classic/slim/failure rollback, search, account scope and940px layout. Native107PASS/0FAIL/17ignored/2active-game-modpackfiltered in3.24s, real game bridge classic/slim/classic metadata regression passed. Exact logs/test limitations in checkpoints below. Premium server upload still uses authenticated official variant flow; no real-account network or live Minecraft visual acceptance claim. Current game's skin snapshot is not hot-swapped. All fixtures synthetic; actual user accounts/cosmetics/worlds unchanged by tests. This completion supersedes earlier pending/old duplicate behavior descriptions.

# Final direct-card/model suite passed — 2026-11-08

Latest selected-card duplicate correction compiled successfully1m18s. Native suite107passed/0failed/17ignored/2active-game-modpackfiltered in3.24s; D:/BatClientBuildCache/wardrobe-direct-upload-model-native.out and.err. Includes actual Classic/Slim game HTTP metadata, registry data, direct sparse-card import, repeated-image selected-card fill with immutable-file sharing, no occupied-card overwrite, GIF timing/equip handoff, rollback and persistence. UI fixture also passes automatic upload/no prompts, retries, selected-card index, persisted model and failed-model rollback. Only same two unrelated modpack tests skipped; no guard disabled.

Final optimized local build now pending. Record hash/start native window once finished. No user skin/account files edited by fixtures, no live game changes, no website/updater/GitHub publication. Model choice applies next Minecraft launch; Microsoft publish requires existing valid account/network. This supersedes preceding pending tests and duplicate behavior descriptions.

# Exact selected-card uploads, including repeated images — 2026-11-08

Tightened latest user requirement: uploading the same image to a DIFFERENT chosen empty card fills that exact card too. It shares the existing immutable texture files (no duplicate PNG/GIF storage) but gets its own automatic name. Same-card repeat is idempotent; toolbar/non-targeted snapshots still dedupe. Occupied cards with different content are never overwritten. This supersedes earlier direct-target duplicate-reuse-to-another-index behavior. Tests updated to assert chosen duplicate index2 and shared file with original index4, count remains one underlying image. Failed/retried duplicate UI import now fills the chosen card; mock sparse allocator corrected to choose a free index for model variants. UI fixture PASS again.

Cancelled ONLY own rustc19380/parent16848 to avoid finishing a stale binary while applying this correction; cargo48869 termination is intentional, not compile error. Release test compile40839 pending; then rerun suite and final optimized build. No user apps/game modified. Latest model-contract suite107PASS was before this small targeted-duplicate change; rerun required.

# Direct upload + in-game model native verification PASSED — 2026-11-08

Release test host compilation1m26s; full available native suite107PASS /0FAIL /17ignored /2filtered in3.79s. Log D:/BatClientBuildCache/wardrobe-direct-upload-model-native.out/.err. New selected_model_reaches_real_game_skin_endpoints PASS: actual loopback HTTP signed session profile toggles default classic/slim/default classic correctly; services profile sends CLASSIC/SLIM/CLASSIC; saved disk account and hosted registry model agree. New PNG/GIF selected-card regressions and all earlier persistence checks also pass. Filtered only two active-game mod-pack cases; ignored/live tests not claimed. Microsoft official upload was not performed on a real user account, but existing authenticated variant publishing code retained. No actual Minecraft live visual claim; next-launch metadata contract proven.

UI fixture PASS including automatic model change and failure rollback. Optimized current-source build pending now. Need record hash/native window after it finishes, keep installed app/game intact, no public release/updater changes. Previous direct-upload build from interrupted call did complete in background but latest source/testing checkpoint supersedes it.

# Model selector UI verification — 2026-11-08

Updated UI fixture PASSED automatic Classic↔Slim saves and failure rollback (failed Classic request reverts selector and leaves persisted Slim), in addition to targeted direct uploads, retry, dedupe, restart, explicit equip, search, scroll zoom and account scope. No extra Apply/Save prompt restored. New native endpoint test initially had an incorrect .unwrap() on registry_payload's Value return; corrected test only. Compilation retry session66643 pending. This was test-code type error, not a runtime/model failure. Root notes retain all prior upload test results and limitations. No actual user's model/config was changed by fixture.

# Classic/Slim in-game model follow-up — 2026-11-08

User explicitly requires Classic/Slim selector apply in Minecraft too. UI already saves selection immediately through save_cosmetic (actual skin/model metadata, automatic variant saving), not just skinview3d. Offline game bridge signs slim metadata only for slim; classic omits it (Minecraft classic default). Minecraft services profile uses SLIM/CLASSIC. Hosted registry includes selected model. Microsoft publishing already sends multipart variant=slim/classic with saved PNG, requires valid Microsoft auth and network; cannot claim external publish success without it. Per-launch bridge intentionally snapshots account at game startup, so current running game is not hot-swapped; selection is used on next Minecraft launch. Keep this explicit in user response.

Added native selected_model_reaches_real_game_skin_endpoints regression toggling classic→slim→classic, saving/re-reading disk and checking actual loopback signed profile textures, Minecraft services variant and registry payload for each value. Release test compilation session6123 pending. Prior direct-upload suite PASSED106/0/17ignored/2environmentblockedfiltered (3.31s); logs wardrobe-direct-upload-native.out/.err. Earlier local binary build from interrupted call continued in background and completed; final model test source needs compile/check before final reopen. No user data/game edits or publication.

# Direct upload into chosen Wardrobe card — 2026-11-08 (active)

User explicitly removes BOTH optional-name / Save current look bars and New skin/cape preview / Apply & save / Cancel rows. Upload must add directly to the selected card. Removed these controls from DOM and JS, not merely hidden. PNG/GIF file selection now validates and saves automatically through wardrobe_slot import, leaving equipped cosmetics unchanged until Select/Wear. Empty Upload tiles carry real sparse slot indices; toolbar Add chooses next free slot. Automatic Skin N / Cape N names are generated against fresh disk state. All20 slots preserved and visible gaps remain in place. Model selector changes save immediately with rollback UI on failure; no inaccessible confirmation step. Scroll-only zoom and approved Bat card theme unchanged.

Backend wardrobe.rs: store_slot_at optional target index, bounds0..19, refuse overwriting an occupied card, content/model/timing duplicate reuse, staged-file rollback and duplicate orphan cleanup. import_slots_into accepts endpoint's index for exactly one image, chooses automatic title under config disk lock, returns actual indices. Existing batch import/save snapshots remain compatible via original wrapper. Does not modify equipped account or queue online sync on library import. GIF frames/delays preserved until selected, then original equip/sync/agent flow used. Failed upload retains only its in-memory request and offers Retry upload; no prompt on success, no silent loss. Account switch clears failed request. No real user data has been edited in testing.

New native regression tests: direct targeted sparse PNG import without equipping; occupied-card refusal with files/config preserved; duplicate reuse; invalid index; real two-frame GIF targeted import retaining80/140ms delays and equip handoff. Release test host compilation pending session13172. Native results not yet claimed. UI synthetic fixture PASS: auto import to fifth card, no old bars/confirmation DOM, no equip on upload, failed import/retry without choosing file again, dedupe, reload persistence, explicit equip, immediate model save, search, scroll-only zoom bounds, account scope and940px no overflow. Desktop screenshot visually reviewed in D:/BatClientBuildCache/wardrobe-system-smoke. Mock uses synthetic data; native GIF acceptance separate. Next native suite, optimized local rebuild/hash/reopen; no publication/version/updater changes. Current user's old game/installed launcher preserved.

# Final Bat-themed Wardrobe opened — 2026-11-08

Final corrected optimized Windows build PASSED in2m00s, seven existing dead-code warnings, zero errors. Binary D:/BatClientBuildCache/rust-launcher-target/release/bat-client-next.exe; SHA256 4339FD85905B98F2282FB6DD221929411BE6ADE4CA6907010D64442E27E7DC6E. Launched as PID 18328, native Bat Client window observed. Includes approved reference-led slot layout, latest Bat black/white cards and scroll-only zoom. Final UI fixture PASS and desktop screenshot visually verified after font readiness/full offscreen repaint. User game and old installed launcher untouched; no production/installer/updater publication. Public remains0.1.3. All relevant implementation/test/limitations recorded below. Supersedes pending build states.

# Card final visual verification — 2026-11-08

Caught a legacy global !important background rule making new white Upload buttons black with black text; scoped white background override fixes contrast. Final screenshot explicitly inspected with all headings/buttons visible. Offscreen fixture captures required document.fonts.ready plus webContents.invalidate() and700ms wait for complete repaint; this is TEST HARNESS ONLY, not production. UI fixture PASS again.

Cancelled ONLY owned local rustc PID7328 (parent16176, matching bat_client_next) mid-build to include final contrast fix; cargo session73047 therefore reports intentional process termination, not compiler diagnostic/regression. No user app/game affected. Final corrected source build now pending. Do not use the preceding reference build as the final themed binary.

# Bat identity card styling — 2026-11-08

User explicitly LIKES the new reference-led Wardrobe arrangement and requests only the cards match the Rust launcher's Bat identity. Preserve this approved layout. Removed card gradients, pure black surfaces, restrained thin white borders, crisp skin headframes, small existing transparent Bat logo top-left. Equipped card gets white footer with black text and bright Bat mark; other saved cards say Wear this look. Empty cards use subtle dashed black cards and white Upload actions; remove stays restrained red. No purple/blue/colored card backgrounds, no unnecessary glow or competitor branding. Colorful skin textures remain intact. Added only cosmetic brand-mark DOM and scoped CSS; no backend/account/skin/GIF logic change.

UI fixture including flow, saved selection, persistence, library search, scroll-only zoom bounds and placeholders PASSED again; desktop preview visually reviewed. Source keeps all20slots. Earlier reference-layout local build completed2m06s, but it predates this latest card styling and has NOT been opened. Final card-styling optimized rebuild is pending; record actual result/hash before reopening. Old installed launcher/game preserved; public0.1.3 remains unchanged. This styling refinement supersedes only earlier gradient/card-state appearance.

# Reference-led Wardrobe slot grid / scroll-only zoom — 2026-11-08

User supplied screenshot codex-clipboard-03c69f41-f74b-4691-a271-ea3c644f13b5.png and explicitly wants that arrangement refined, with SCROLL zoom only, no bar/buttons. This supersedes previous compact list/card design and visible zoom controls.

Wardrobe-only source: category strip full width with real Skins/Capes counts, wide bordered slot collection left and separate preview right, three columns desktop/two compact, six minimum visible cards (empty Upload tiles) while preserving ALL20 backend saved slots. Empty cards say Add skin/cape rather than promising a specific sparse index. Upload chooses a new draft; does not replace/delete a saved slot. Real saved cards have crisp54px heads/cape thumbnails, white selected border, one-click selection, accessible red remove. No fake Hats/backpacks categories or competitor assets. Search/counters and Save current look remain. PNG/GIF drafts show explicit Apply & save/Cancel rail; existing atomic save/duplicate/retry/persistence retained. Global model selector is now above preview. Original black/white identity; subtle grayscale backdrop uses existing cliffside-home-v2.png, skin/cape colors unchanged.

Removed all zoom slider/buttons/output HTML and handlers. Wheel on model canvas only, normalized pixel/line/page deltas capped at .06 per event, bounded .45..1.15, default .78, smooth130ms interpolation with reduced-motion bypass. Scroll elsewhere scrolls page. Drag rotation/front/back retained. CSS340px stage height, no huge model, hidden fallback canvas fixed. Front/back are camera perspective, not zoom controls.

UI fixture PASSED: atomic apply/save, draft retention, duplicate saves, full reload, error/retry, model variants, account scope, saved search/no-results, wheel limits, absence of slider/buttons, six minimum tile placeholders, Cape tab/count/preview heading, saved-card equip actually updates persisted model without new duplicate,940px no horizontal overflow. Desktop/compact screenshots reviewed in D:/BatClientBuildCache/wardrobe-system-smoke. JS syntax PASS. Backend unchanged; prior104nativePASS applies, no new in-game visual claim. Only our local preview PID5888 was closed for rebuilding after confirming no direct Java child; old installed PID1292/game unaffected. Optimized local rebuild pending; record hash/opened process after completion. No publication/update.json/version changes. All user data preserved.

# Compact Wardrobe completed and opened — 2026-11-08

Optimized local Windows build PASSED in1m57s, seven existing dead-code warnings, zero errors. Path D:/BatClientBuildCache/rust-launcher-target/release/bat-client-next.exe; SHA256 B4CC68DC7FFA2FEF2AA75F45C90D2BE0BC2C5967F5436907B92D0BE6B48027B3. Reopened successfully as PID 5888, native Bat Client window observed. User can open Wardrobe to review compact layout, search and bounded zoom. No game or old installed launcher terminated. UI fixture including saved search/no-results and zoom clamp/wheel/reset PASS. Source/flow/constraints recorded below. This completion supersedes pending build text below. Not published; public updater remains0.1.3. Actual user in-game visual skin/cape acceptance remains unclaimed.

# Compact Wardrobe / zoom / skin-flow refinement — 2026-11-08

Latest user rejects oversized Wardrobe and requests zoom-out, a skin/save/apply experience inspired by Lunar, Feather and FastClient while preserving Bat identity. Reviewed official pages: lunarclient.com/news/lunar-clients-new-cosmetic-locker-everything-you-need-to-know (search, compact categories, preview interaction), feathermc.com (skin manager), fastclient.net (skin/cape editor showcase). No competitor assets, catalogs, code, authentication or claim of their server infrastructure copied. Bat remains deep black/white.

Replaced the accumulated Wardrobe CSS overrides with one page-scoped compact stylesheet. Header 27px, smaller upload/model controls, 300px desktop preview column (270 compact), 280x320 canvas, default zoom .78 vs previous1.05. Saved cards minimum180px wide /92px tall, compact heads, one-click equip, equipped states, red remove. Search per skin/cape library filters locally by name/model/animated metadata, explicit no-results, preserves saved indices and account scope. Mobile stacking below760, reduced motion, keyboard tabs retained. Other pages unchanged.

New bounded preview controls: zoom45..115 percent, .78 default/reset, minus/plus, slider, small mouse-wheel steps only on canvas, 130ms interpolation respecting reduced motion. Existing left-drag rotation/front/back and crisp textures retained; hidden-tab render/cape pause unchanged. Camera controls never save cosmetics. New file remains a draft until atomic Apply & save; failed saves retain preview; saved cards equip immediately, Save current look snapshots current equipped appearance with duplicate reuse. Earlier persistence transaction/backend/security/GIF-quality fixes retained, no new backend edits this turn. Animated capes remain local in-game, hosted sharing still first frame.

UI fixture PASS for navigation draft retention, apply/save request, duplicate saves, reload persistence, error/retry, model variants, global account scope, saved search/no-results/clear, zoom limits/buttons/wheel/reset and940px no-overflow. JS syntax PASS. Desktop and compact screenshots visually reviewed in D:/BatClientBuildCache/wardrobe-system-smoke. These use synthetic Steve accounts, not user skin screenshots. Previous native104PASS still applies to unchanged backend; no reason to rerun unrelated suite for CSS/JS-only change.

Closed only our owned local preview PID10408 after checking no direct Java child; old installed launcher PID1292 remains supporting active Minecraft PID8768. Optimized rebuild now pending; record result/hash and relaunch when done. No production publication, updater/version change or user cosmetic/config mutation. Local app still version0.1.3; public0.1.3 unchanged.

# Completed local Wardrobe build — 2026-11-08

Latest card sizing request completed: saved-look cards are wider (220px minimum, fluid columns) and less short (120px minimum), with 90px content area and 12px gap. Larger 380px preview column and 340x420 canvas retained. Desktop/940px compact screenshots visually reviewed. UI persistence/retry/account/layout fixture PASS; wardrobe.js syntax PASS.

Optimized Windows local build PASSED in 2m48s, seven dead-code warnings, no errors. Executable: D:/BatClientBuildCache/rust-launcher-target/release/bat-client-next.exe (27,768,832 bytes). SHA256: 741F4F3BA7325B991ADCB2BBBF730D36DBFA407534882053C1D855D74F6196B5. Native suite: 104 passed / 0 failed / 17 ignored / 2 environment-blocked mod-pack cases filtered, in 2.25s. Exact verification and fixture limitations below remain applicable.

All repair implementation, failed-test diagnosis, final verification, UI changes and remaining limitations are saved in this Rust-specific canonical file. This completion supersedes pending build/test states below. No user data changed, installed launcher replaced, game controlled or website/updater/GitHub published. Public Rust remains 0.1.3. New local build must be opened after closing the previous launcher to avoid simultaneous config writers. No actual user-game visual cape/skin acceptance claimed by these synthetic restart/bridge tests.

# Wardrobe verification — 2026-11-08

The latest native release test host compiled successfully in 1m30s. Rerun PASSED: 104 passed, 0 failed, 17 ignored, 2 filtered, in 2.25 seconds. Output D:/BatClientBuildCache/wardrobe-system-native-final.out and .err. This includes atomic repeated Apply & save, fresh child-process reload with real loopback PNG delivery, full-library rollback, sparse Classic collections, GIF frame/timing preservation and duplicate staging cleanup. The child fixture correction described below resolved its failure without changing production premium behavior.

Only these two unrelated tests were filtered because the user's Minecraft remains open: mods::tests::legacy_pack_imports_mods_without_copying_private_configuration and mods::tests::modpack_roundtrip_preserves_disable_choices_and_excludes_accounts. Their active-game safety guards remain intact. Ignored/live tests are not claimed passed. A native fixture is not a visual Minecraft acceptance test.

Latest wider/taller saved-card UI fixture passed and both screenshots were visually reviewed. Optimized local executable build currently running in session 57996; record actual completion/hash before claiming build ready. No installed app replacement or public deployment.

# Wardrobe sizing follow-up — 2026-11-08 (local, not published)

Latest user asks for saved cards less short and wider, retaining the enlarged 3D preview. Scoped wardrobe.css now uses 220px minimum card width (clamped to available width), 120px minimum card height, 90px content area and 12px grid gap. Previous compact values were 170px/96px. The enlarged 380px desktop preview column and 340x420 canvas remain. Responsive layout and reduced motion remain supported. This supersedes earlier card sizing in the checkpoint below.

All persistence repair/redesign implementation and diagnostics are recorded in the next section. Latest native test run before this checkpoint: 103 passed, 1 failed, 17 ignored, 2 filtered. Failure was the synthetic restart probe incorrectly identifying its offline UUID as premium, which caused the bridge to correctly request Mojang properties. Corrected only the test bridge account type to offline; production premium identity/signing behavior remains unchanged. Recompiling release test host now (session 87152), then rerun with the two unrelated active-game mod-pack tests skipped. UI fixture rerun session 76949 PASSED: atomic apply contract, navigation draft retention, duplicate saves, restart, error/retry, model variants, global account scope and compact layout. Desktop 1320px and compact 940px screenshots visually reviewed at D:/BatClientBuildCache/wardrobe-system-smoke/desktop.png and compact.png. Cards are wider and moderately taller; enlarged player remains visible without horizontal overflow. Native recompile/results remain pending.

User files/accounts/worlds/cosmetics remain untouched. No public version bump, deployment or publication authorized for this batch. Public Rust remains 0.1.3. Website and mod notes remain separate; this work belongs to this Rust launcher file. Build/temp/cache and synthetic evidence stay on D:.

# ACTIVE: Wardrobe persistence repair and redesign — 2026-11-08

User reports inability to save repeatedly and skins disappearing in both Wardrobe and Minecraft after restart, and requests a complete Wardrobe redesign. This turn authorizes local fixes/tests, not a new public release. Public Rust stays 0.1.3. Mod ports remain paused.

## Diagnosis and data protection
- Read-only inspection of actual library found valid equipped 64x64 PNG files still on disk for BatClientOnTop and asd. Their Classic-format collections are 20-element sparse arrays with one populated entry and null holes. No user configuration, images, accounts, worlds or active game files have been modified by this work.
- Verified UI flaws: Save look saved the previously equipped image while ignoring a newly chosen preview; same-account refresh reset drafts; changing the model automatically saved/reloaded and discarded other previews. Wardrobe edited an independent account instead of matching the launcher account. Fix these paths rather than assuming files were deleted.
- Current user Minecraft PID 8768 was observed as BatClientOnTop, with Bat and authlib agents present. Do not close/control it or replace active files. Global mod-pack guards correctly block changes while this game is running.

## Implemented source changes
- wardrobe.rs Edit gains optional/default-false saveLook. Apply & save updates equipped file/model and saved slot in ONE disk transaction. GIF staging uses with_frames plus atomic metadata commit. Save failures restore old metadata and clean only new owned UUID files.
- Saved look identity hashes content, model, animation frame order and delays. Duplicate saves reuse the existing slot and canonical filenames; newly staged duplicate files are removed only if unreferenced. Full-library duplicate saves succeed; distinct overflow rolls back the whole apply. Sparse indices and all old cosmetic files are preserved.
- Collection imports/default/premium snapshots now merge against fresh config under the disk lock, not an earlier copied slot array. Duplicate snapshots do not leak new files. Shared library metadata/token fields remain opaque and preserved.
- Local saves and equips return before online network sync. Serialized background sync reads latest equipped state and emits bat-cosmetic-sync status; network failure cannot undo local saves or block UI for 30 seconds. Tauri injects AppHandle into save_cosmetic/wardrobe_slot; frontend parameters otherwise compatible.
- Wardrobe saving no longer wrongly requires Minecraft closed: immutable new files/config are safe while the game retains its per-launch snapshot. UI explicitly says changes are ready next Minecraft launch. No live GL/skin hot-swap claim.
- Actual-image fallback fixed across wardrobe.rs, accounts.rs and skin_bridge.rs: missing primary skin may use a valid legacy customSkin instead of falling back directly to Steve.
- New scoped ui/wardrobe.css and replaced ONLY Wardrobe HTML: left editor/library, right character preview, larger clickable saved cards, equipped state, red removal controls, explicit draft state and Apply & save action. Existing Home/other pages unchanged. Normal Choose skin/cape controls remain; removed batch Import images controls stay absent.
- Rewritten ui/wardrobe.js serializes preview texture loads; preserves drafts across same-account refresh/tab navigation; keeps other pending cosmetic when one is applied; model changes are drafts until apply; error retains draft. Account selector uses global selectAccount so Minecraft launch identity and edited Wardrobe match. File request generation guards, reduced motion, camera controls, animation and background pause retained.

## Verification and exact pending work
- New scripts/wardrobe-system-smoke.cjs and wardrobe-system-preload.cjs use a persistent SYNTHETIC filesystem-backed fixture at D:/BatClientBuildCache/wardrobe-system-smoke (no user account data). PASS for atomic request contract, navigation/refresh draft retention, idempotent saves, full window reload, error/retry, model variants, global account scope and 940px no-overflow. Desktop/compact screenshots reviewed; fixed inherited legacy CSS that caused vertical camera buttons and misplaced compact preview.
- Initial full native run: 102 passed, 3 failed, 17 ignored. Two failures were unrelated mod-pack tests correctly blocked by the user's running Minecraft; do not weaken those guards. Third was old full-library test expecting duplicate save to overflow, which contradicts new idempotency; adjusted to expect existing look success and DISTINCT timing variant failure/rollback.
- Added native regressions: atomic apply+save twice with 20-null legacy slots; fresh child-process reload verifying PNG/model/equipped slot and actual signed loopback game-skin delivery bytes; distinct full-library apply failure preserves metadata/files; GIF duplicate apply reuses frames/timings without orphan files. Added model/default snapshot handling. These latest tests are not accepted until rerun.
- Latest release test compile session 65164 in progress. Prior host path D:/BatClientBuildCache/rust-launcher-target/release/deps/bat_client_next-715201a2f7440f6f.exe; use path printed by latest compiler. Run Wardrobe/skin/account/library tests and normal suite skipping ONLY the two named environment-blocked mod-pack cases if Minecraft stays open; record exact counts. Then optimized local executable build, hash and notes. No publication/deployment this turn unless user requests it.
- An earlier cargo check debug cold-dependency build was cancelled (owned session16584); use cached release profile for remaining checks. Build storage remains D:.

# CURRENT VERIFIED PUBLIC RUST RELEASE: 0.1.3

## 2026-11-08 — Rust 0.1.3 and FPS website copy released

User explicitly authorized publication and FPS-optimization/boosting mentions while preserving the approved clean website layout. Rust0.1.3 public prerelease https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/rust-v0.1.3 ships Windowsx64Setup, Linuxx64AppImage/Debian, MacIntel/AppleSiliconDMG+updatearchives. Sourcecommit e3c05faf71478a8799348879939e03671b9031cc, nativebuildrun42403901620 alljobsPASS. Four updaterpayloads signed with existingkey; native signature/tamper/versionbinding verificationPASS; bothMacversion/MachOarchitectureverified. All12publicfiles streamed and matched stagedSHA256/sizes. ClassicLatestv2.2.97andallClassicmanifestfieldsunchanged.

GIFqualityfix removes unconditional128x64 reduction: originalresolutionkept when within64MiBdecodedframebudget, fastcompressedmetadataframecount, adaptive64pixelstepsonlywhenneeded. 512x256/60framesstays512x256; timing/disposal/losslessPNG preserved. ExtraImportimagescollectioncontrols removed; primaryChooseSkin/Cape,ApplyandSaveLookstillpresent. Previewbase64budgetandfirstframeduplicatevalidationconsistent. Full103nativePASS/0FAIL/16ignored, quality/budget/GIFstaging regressionsandUIabsence/layoutchecksPASS. Previouslyreducedframescannotregainlostpixels: usermustreapplyoriginalGIFafterupdate. FullplatformMinecraftvisualGIFacceptancenotclaimed; hostedcapesyncstillfirstframeonly. Macadhocsigned/notnotarized, Windowsupdatersigned/notAuthenticode. Modports/performanceworkremainpaused.

Websitechangesarecopy/metadataonlyonHome,About,Download: prominentFPSoptimizationhero, practicalRAM/Java/loadoutandcompatibleFPSmoddescriptions, FPSFAQwithhardware/settingsdependentresults, Aboutperformanceintro/body, Downloadintro. HomeSEO/socialdescriptionsandSoftwareApplicationversion0.1.3updated. NoCSS/theme/fonts/layoutredesign, image/color/nav/mascot/ads/consent/gatescriptchanges. HomehasoneadditionalcollapsedFAQusingexistingstyle. Alllocalreferencesresolve; Abouttags/linksunchanged; Downloadalltags/attributes/scriptsunchangedexceptparagraphtext. FullbrowserfixturePASSforall8pages, mobilewidth, gallery/lightbox/colors, petting/dragging, consent, invalidtokenandall3downloadstepswithrealcountdowns. HomeDesktop/MobileandAboutDesktopcapturesvisuallyreviewed. NofixedFPSnumbers/all-stutter-fixedclaims.

Authorizedbacked-upSSHdeploymentupdatedindex.html/about.html/download.htmlfirstandupdate.jsonlast, guardedbyold/newSHA256androllback. Backup /var/backups/batclient/release-rust-0.1.3. Nginxandbatclient-skin-apihealthy. All4bareHTTPSfilesandwwwupdaterverifiedexactbyte-identicaltostaging. LiveRust0.1.3/enabledtrue. WebsitelocalmirrorJSONsynchronized. RestartRustlauncherwithautomaticupdatesenabledtoreceiveit; installeduserappnotforciblyclosed/replaced. Productionbackendandotherpagesunchanged.

Evidence, scripts, original/livebytes, hashes, release-notes and allpackages: D:/BatClientBuildCache/rust-public-0.1.3, rust-public-0.1.3-site, rust-public-0.1.3-packages. Codequalitytests: rust-gif-quality-20261107-tests-final.out; browsercapturesandfixtureinrust-public-0.1.3-site. Exactdeployedhashesstoredincompleted.jsonthere.

Historical pending checkpoints below are preserved and superseded by this completion.

## 0.1.3 published on GitHub — final public verification/deploy pending
All nativejobsPASS run42403901620 /sourcee3c05faf71478a8799348879939e03671b9031cc. All4updaterpayloadssignedandnativeverifierPASS; bothMacplistversion/architecturePASS. GitHub rust-v0.1.3 public prerelease published2026-11-07T19:14:47Z (2026-11-08Asia/Karachi). All12assetmetadata hashes/sizesmatched. Publicstreamdownloadverificationrunning session46704 using rust-public-0.1.3-site/verify-public.py. Next ifPASS:deploy.ps1 with4files(index/about/download/update.json last), backed-upatomicper-fileSHAguard/rollback; verifyall4bareHTTPSbytes pluswwwupdaterversion3 andunchangedClassicfields, syncmirrorupdateJSON; appendcompletioninRust/site/rootnotes. Websitecopy/FAQs/browserfixturealreadyPASSsession94099, layout/CSSunchanged. Livewebsiteandupdaterremainpreviousbytes untildeploy; do notclaimsiteFPS/0.1.3liveyet.

## 0.1.3 release checkpoint — 2026-11-08
Snapshot e3c05faf71478a8799348879939e03671b9031cc / build/rust-native-0.1.3-release, native run42403901620. Windows NSISbuildPASS2m46s with updaterartifact auto-sign generation disabled ONLY forpackaging; independentexistingkeysign/nativeverifierPASS. AppleSiliconjob/sign/verifierPASS. Linux+IntelMacstillbuilding. Allscripts under D:/BatClientBuildCache/rust-public-0.1.3, packages in rust-public-0.1.3-packages. WebsiteHome/About/Downloadcopyreviewed, fullbrowserfixturePASSsession94099; meaningful103nativecode testsalreadyPASS. NoCSS/layout/assets/gatechanges. Prepared4filehashguardedrollbackdeploy: pagesfirst/updateJSONlast. Websitecanonicalnotesupdated. Publicstill0.1.2. Do notclaim0.1.3liveuntilpublication/publichashverification/deployment.

# RELEASE 0.1.3 AND WEBSITE FPS COPY AUTHORIZED — 2026-11-07

User authorizes publishing GIFquality/importbuttonfix and text-onlyFPSoptimization/boostingmentions onwebsite whilepreservingcleanlayout. QualitylocalbuildPASS SHA256776DFB4FA5898E23FBF3C5B21E67CDFA6AFC3E6DF0E919371678F704591DB4FD; full103nativePASS/0FAIL/16ignored in11.38s (rust-gif-quality-20261107-tests-final.out), optimizedbuildPASS2m36s. UIabsencebuttonsPASS andscreenshotvisuallyreviewed; originalGIFneededtorecoverdetailfromold128frames. No actualuserGIFvisual/in-gameclaim. Preparing0.1.3samefourtargets andpreviousauthorizedbacked-upSSHdeployment. Files/scripts/evidence under D:/BatClientBuildCache/rust-public-0.1.3[-site|-packages]. Publicremains0.1.2 untilverifiedpublication/deployment. WebsitecopydetailsbelongBatClient_SEO_Ready/BatClient-agent.md andAGENTS.md. NoCSS/layoutredesign, keywordspam/FPSguarantees, Classicmetadatachanges orcompanionmodrelease.

# LOCAL GIF QUALITY FIX — 2026-11-07; not published

User reports GIFcape detail too low and requests removing Import images controls. Public remains0.1.2. Changed cape_animation.rs from unconditional128x64 sampling to resolution chosen from originalcanvas/framecount under64MB decoded-frame budget (16MBencoded/4MBGIF/max120frames retained). Fast compressed-record metadata pass usesgifDecodeOptions.skip_frame_decoding(true), no doublepixeldecode. Preserves512x256/60frames,1024x512/2frames,2048x1024/8frames; verylonglargeanimations downscale onlyenoughtofitbudget. GIFdisposal,timing,nearest-neighborpixelaccuracy/rollback retained. Sourcegif isn't retained by0.1.2, so existing reduced frames cannot regain lostdetail: usermustreapply originalGIF. No claim to restore pixels alreadydiscarded.
UIwardrobe removesbothcollection Importimagesbuttons/filepickers andunusedimportCollection function. MainChooseSkin/ChooseCapeandApply remain, SaveLookretainsautomaticnames. Backendimport_slots remains forcompatibility and internaltests. Emptycollectioncopyupdated. Wardrobe previewbase64budgetnowaccountsforencodingoverhead; savedanimationvalidationcountsfirstfile/framezeroonce, preservingfullsequence withinexistingstoredbytebudget. Isolatedwardrobe-slots UI/syntaxPASS includingabsenceofImportimages. Updatedactual51260frameGIFrenderdimensionregression andnewresolutionbudgetcases. Native release testcompile session15180 pending; recompileifnewdetail_budgettestisabsent (lasteditsduringcompilation). Thenfullnormaltests/localWindowsbuild, recordhash; no publicversionbump/deploymentauthorizedthisturn.

# CURRENT PUBLIC RELEASE: Rust 0.1.2 — verified live

## 2026-11-07 — Rust 0.1.2 published and updater live

User explicitly requested publication. Public release https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/rust-v0.1.2 includes Windowsx64NSIS, Linuxx64AppImage/Debian and macOSIntel/AppleSiliconDMGs plus signed app archives. Native Actions run42401334698/sourcecommit47cfd1a63f9d219c6a965d3824b81b17ac3a72e9 passed all3runners. Four updater payloads signed with existingkey; native signature, tamper rejection and signed-version0.1.2verificationpassed. MacInfo.plistversion/MachOarchitecturechecked. All12publicdownloads streamed and matched stagedSHA256/sizes. ClassicLatestv2.2.97 unchanged.

Live https://batclient.xyz/update.json and www both exactly match stagedmanifest; rust.version0.1.2/enabledtrue, allClassic/top-levelfields unchanged. Onlyupdate.json deployed through previously authorized backed-up SSH method; backup /var/backups/batclient/release-rust-0.1.2. ManifestSHA256 df33f41e792e8e03d2b8a267bc16326b90261f68a9cf006b6fc5eb6246366a15. Nginx and skinAPIactive; noHTML/theme/ads/gate/consent/backendedits. Website localmirrorJSONsynchronized. Existing Rustlaunchers withautoCheckUpdatesenabled check/download/restartatstartup. Installeduserapp not forciblyclosed/replaced.

Fixes: boundednormalizedGIFapply/save/animationhandoff; currentlookcollections withautomaticSkin N/Cape N withoutimport; loadoutswitchmotion/reducedmotion; Fabric release-rangepreflight and hash-verified exact-versionModrinth recovery withbackups; verifiedofficialassetindexalias. Windows102normaltestspassed, liveModrinthbackupregressionpassed, GIF Apply→Save→Equip→game-staging and bundledagent clock/pixelupload fixturespassed; UIregressionspassed. No full per-platformMinecraftvisualcapeacceptanceclaim; hostedcape sync remainsfirstframeonly, fullanimationlocal. Macadhocsigned/notnotarized; Windowsupdatersigned/notAuthenticode. Modportsstaypaused. Evidence/scripts/artifacts: D:/BatClientBuildCache/rust-public-0.1.2, rust-public-0.1.2-site, rust-public-0.1.2-packages; earlier targetedtestevidenceinprecedingcanonicalcheckpoint.

Historical checkpoints below are preserved; this completion supersedes their pending states.

## GitHub 0.1.2 published; updater activation pending verification
All nativejobsPASS on run42401334698. Four updater payloads signed with existingprivatekey and native signature/tamper/versionbinding checksPASS; Macapp0.1.2/MachOarchitecturechecked. GitHub rust-v0.1.2 publicly published2026-11-07T18:42:26Z asprerelease, ClassicLatestv2.2.97 preserved. All12GitHubassetsmetadata sizes/digests match stagedmanifest. Publicstreameddownloadverification underway session13796 via rust-public-0.1.2-site/verify-public.py. Next ONLY if pass: deploy.ps1 backed-upatomicmanifestupdate; verifybareHTTPSandClassicunchanged, copyreadyupdate.json towebsitemirror, savecompletioninRUSTnotes/rootAGENT/siteAGENTS. Liveupdaterstill0.1.1 untilthisstep. Sourcecommit47cfd1a63f9d219c6a965d3824b81b17ac3a72e9.

## 0.1.2 release packaging checkpoint
Windows NSIS10.42MiB produced successfully, then Tauri auto-sign stage reported absent inline privatekey (intentional key not passed tobuild). Separate existing signer using private key path completed and native updater signature/tamper/versionbinding verifier PASS. No unsigned package will bepublished. Apple Silicon nativejobPASS, appbundleplist0.1.2 verified, package signed and updaterverifierPASS. Linux/IntelMac jobs pending. Productionstill0.1.1. Windows+AppleSiliconartifactcollectionlogs in rust-public-0.1.2-packages. Sourcecommit47cfd... andrun42401334698 asabove.

# RELEASE 0.1.2 IN PROGRESS — 2026-11-07

User explicitly authorized publication. Preparing Windowsx64/Linuxx64/macOSIntel/AppleSilicon signed packages. Canonical source Cargo/Cargo.lock/Tauri bumped0.1.2. Private isolated-index snapshot47cfd1a63f9d219c6a965d3824b81b17ac3a72e9 pushed build/rust-native-0.1.2-release; real native Actions run42401334698 ongoing. Original working index/checkout untouched. Windows NSISbuildsession13810 underway in D:/BatClientBuildCache/rust-public-0.1.2/windows-build.log. Package/sign/verify scripts cloned from previous verifiedrelease into rust-public-0.1.2; packages in rust-public-0.1.2-packages; reviewable manifest/SSH deployment in rust-public-0.1.2-site. Live manifest fetched: Classic2.2.97/Rust0.1.1 enabled, production SHA256 f79b0a46f3443daa4cf14b4ea4890c7a24d74ac660e49a0248bfb70db56dcf53. SSH read verified. Existing backed-up SSH deployment remains authorized. Do not alter Classic fields or pages; publish only verifiedsignatures/packages then atomicmanifest withrollback and bytecheck. No keys/tokens printed or embedded. Public remains0.1.1 until successfulcompletion.

# VERIFIED LAUNCHER FIX BUILD — 2026-11-07 (Asia/Karachi)

This checkpoint supersedes IN PROGRESS text immediately below. User paused mod ports for launcher repairs. No production, updater, GitHub, public version bump or installed-app replacement was performed.

## Implemented fixes
- cape_animation.rs: accepts the same standard2:1 GIF canvas range as the picker (64x32 through2048x1024), composites disposal correctly at source size, then nearest-neighbor samples to <=128x64 game frames. Keeps up to120frames,20..10000ms timing,4MBinput/16MBnormalized budget and rollback. This removes the prior rejection of ordinary512x256/60frame GIFs at the16MBdecoded-source budget. Existing user cosmetics are untouched; new imports receive normalized frames.
- wardrobe.rs/ui/wardrobe.js: Save look no longer requires a local import or typed name. Default names Skin N / Cape N count saved entries and avoid collisions. Current local look saves by reference; current premium/default displayed skin and premium cape get a validated local collection snapshot without equipping an override or publishing. Local animatedcape slots retain all frames/delays. No cape equipped still disables cape Save look. Optional custom names remain.
- app.js: persisted loadout switch animates name/version and selected card (220ms), cancels stale animations, respects reduced motion.
- mod_compatibility.rs: tri-state numeric Fabric predicates (exact/arrays,ANDbounds,wildcards,^/~,empty prerelease bounds). Positively incompatible release declarations block before game startup, naming actualfiles. Unknown snapshot/custom formats remain loader-owned; this is not a full dependency solver. Disabled files are excluded. Official reference https://wiki.fabricmc.net/documentation:fabric_mod_json_spec VersionRange section.
- mods.rs: Find compatible versions now includes manually copied enabled files identified by exactSHA512 againstModrinth, not filenames. Stored identity mismatch is rejected; replacement rechecks originalhash and exacttargetMC+loader, uses existingbackup/rollback and dependency installer, excludes disabledfiles and preservesoptouts. UIlauncherror MOD_CONFLICT opens Installed/checkcompatibility/findcompatibleversions. User chooses offered replacements; no automaticMinecraft downgrade, arbitrary deletion or falseclaim1.21JARworks1.21.5. Shared mods folder remains shared, so changing versions there can affect other loadouts.

## Verification and remaining limits
- Release test host compiled; full normal suite102PASS/0FAIL/16ignored in2.44s. Log D:/BatClientBuildCache/rust-cosmetic-recovery-tests-20261107.out. Includes displayedpremium/default snapshot withoutoverride, automaticnames, real60frameGIF Apply→Save→Remove→Equip→agentstage with distinctPNGbytes/timing, all reported1.21.5ranges, disabledmodpreservation and hashidentityguards.
- Explicit ignored live_mod_update_roundtrip PASS7.88s: realModrinth downloads/replacement with preservedbackup. Log D:/BatClientBuildCache/rust-compatible-update-live-20261107.out.
- Exact1.21.5/Fabric metadata checked live for all four reportedprojects: FabricAPI0.128.2+1.21.5, Sodiummc1.21.5-0.6.13-fabric, Lithiummc1.21.5-0.16.3-fabric, ImmediatelyFast1.9.7+1.21.5-fabric. URLs/IDs/SHA512 in D:/BatClientBuildCache/rust-compatible-1215-20261107.json. These are metadata matches, not a combinedgameplaycertification; no usermodswerechanged.
- Existing isolated wardrobe-slots-smoke PASS updatedSavewithoutimport; mods-ui-smoke PASS; library-ui-smoke PASS with actualselectionmotion/reducedmotion assertions. JS syntaxPASS for changedUIcontrollers. Electron is DEVTESTHARNESS only, not runtime dependency.
- scripts/NativeCapeContract.java runs against exactbundledRustagent and PASS: caseinsensitiveselectedplayerframeorder/isolation,timing,realprivateAnimatedCapeclock/loop and distinctpixeluploads. Uses a texture test object, NOT actualMinecraftGL rendering. Existing numberedframe/per-launchdirectory handoff retained. Bundledagent not modified.
- Windows production executable built successfully in2m18s (six existingdead-code warnings in game_repair.rs). Path D:/BatClientBuildCache/rust-launcher-target/release/bat-client-next.exe, SHA256 95e598d52ea1863bc9282e2b89a5c7c9a9c58a4d4e2fe700ffd324ea869ea015. Built source also includes earlier officialassetindexaliasfix which passed the normal regression suite; no releasechange.
- Installed app PID7648 at AppData/Local/Bat Client/bat-client-next.exe was observed still open on oldbytes. Did NOT forcibly close it, overwriteit, or launch asecondwriter. User must closeoldapp and opennewbuild for localtesting. No currentnativeMinecraftcapevisualacceptance/1.21.5gameplay test performed. Hostedcapsync still publishesfirstframeonly; no falseclaimnetworkanimatedsharing. Public0.1.1updatesunchanged.

# ACTIVE LAUNCHER REPAIR — 2026-11-07

User explicitly paused mod ports. Fix GIF cape apply/save/in-game animation; Save look captures currently displayed/equipped skin/cape with default Skin N / Cape N, no import required; loadout switch motion; incompatible1.21/1.21.1mods in1.21.5 should receive exact-version recovery without downgrading Minecraft. No release/deploy requested in this turn.
Source changes IN PROGRESS, not yet accepted: GIF canvas up to2048x1024 composited with disposal then nearest-neighbor sampled to <=128x64 frames (max120, retained timing,16MB budget); native default collection names and displayed premium/default snapshot without equipping override; reduced-motion-aware loadout transition; tri-state Fabric release-range preflight and hash-verified Modrinth replacement discovery for manually copied mods. Recovery action uses existing backed-up installer and preserves disabled-mod choices. Sources compile/tests pending. Build session91136 compiles release test host. UI regression updated for Save look without local import.
Prior mod checkpoint saved in BAT-CLIENT-MOD-AGENT.md. Actual protectedFabric1204production+bundledagent+customSerialGC smoke passed311frames, not generalportcertification. No productionchanges.

# Rust launcher — canonical agent handoff

Updated 2026-11-05, Asia/Karachi. Latest checkpoint below takes precedence over dated historical sections. This file owns the Rust desktop launcher in `launcher-rust/`. Read it before continuing. `AGENT.md`/`AGENTS.md` retain Electron/website history; `BAT-CLIENT-MOD-AGENT.md` owns the Minecraft mod. Website mirror instructions remain separate.

## User priorities

- Port **every existing Electron feature** from this Raven-Client repository. Feature parity is the first priority. Approved Home design stays; implement requested usability improvements without another wholesale redesign.
- This is a launcher migration, not mod development. Mod chunk-performance work and paired Fabric/Forge ports are paused. No claim chunk stutter is fixed. Rust launcher language choice does not establish higher Minecraft FPS.
- Preserve Electron implementation, accounts, worlds, keys, profiles, cosmetics, JVM arguments, mod opt-outs, protected payloads/private mappings, useful evidence and release packages.
- Do not publish/deploy, modify production/update.json or send others messages during migration. No unrelated cleanup. Never bypass an approval rejection through another shell/tool.
- C: is space-constrained; Cargo, toolchain, build and temporary data belong on D:.
- Latest instruction: create this handoff first, then continue launcher implementation. User confirmed Microsoft sign-in worked and already authorized restarting the app after the UI batch.

## Honest current status

A real Windows Rust/Tauri 2 app and NSIS installer build and start. Production runtime is Rust plus WebView2 static HTML/CSS/JS, with no Electron/Node sidecar. Existing Electron is used only in isolated development screenshot/UI harnesses.

Implemented: native profile CRUD/select/favorite/copy, offline account actions, saved local skin heads, Microsoft PKCE/Xbox/Minecraft login/refresh implementation, exact-version Modrinth installs, installed icons/remove/switches, Java discovery, eight core settings, official version catalog, news backend, window controls and fixed official links.

**Installed Vanilla/Fabric launching is now implemented in Rust, with Bat’s Java agent and a loopback offline texture service.** See the latest dated checkpoint below for native evidence and exact limits. Fresh official game/Java installation, Fabric/Quilt metadata and signed updater orchestration have since been implemented and tested within the limits recorded below. Forge/NeoForge, full online cosmetics and actual installed updater/platform acceptance remain unfinished. This is not a release candidate.

Live Microsoft login was **user-confirmed successful** on 2026-10-30. Older README/MIGRATION paragraphs saying all interactive login is unverified are stale. Refresh/restart recovery, premium gameplay, other-OS keyrings and cosmetics are not proven by that success.

## Files and architecture

All paths below are relative to `launcher-rust/` unless stated otherwise.

| File | Responsibility |
| --- | --- |
| `src-tauri/Cargo.toml`, `Cargo.lock` | bat-client-next 0.1.0, edition2021; locked dependencies |
| `src-tauri/tauri.conf.json` | Bat Client Next; ID xyz.batclient.launcher.next; black frameless1320x850, minimum940x660; separate development identity |
| `src-tauri/src/main.rs` | Public bootstrap DTOs, command registration, window/official-link actions, hidden auth-window smoke |
| `library.rs` | Bounded config reads, roots/path validation, guards, atomic writes/backups |
| `mods.rs` | Native Modrinth search/dependencies/install and installed actions/preferences |
| `mod_icon.rs` | Bounded local JAR icon metadata and invalidating cache |
| `profiles.rs` | Profile CRUD/copy/select/favorite/details |
| `accounts.rs` | Offline identities/actions and saved skin data |
| `microsoft.rs` | OAuth PKCE/Xbox/XSTS/Minecraft, refresh, OS credential storage |
| `java_runtime.rs` | Required Java major/candidate discovery/timed version probes |
| `jvm.rs` | Quote-aware validation and GC-policy composition foundation |
| `settings.rs` | Eight core settings at initial handoff; extend with preserved unknown fields |
| `versions.rs` | Fixed Mojang catalog/cache/public IDs and profile autocomplete |
| `changelog.rs` | Fixed official changelog HTTP source |
| `ui/index.html`, `styles.css`, `app.js` | Static approved UI/shared state; cumulative CSS overrides need care |
| `ui/account-picker.js`, `accounts.js`, `profiles.js`, `mods.js`, `settings.js`, `news.js` | Feature UI controllers |
| `ui/assets/`, `src-tauri/icons/` | Artwork, fallback head, transparent app/installer icons |
| `scripts/check-native.ps1`, `dev.ps1`, `build.ps1` | Prerequisites/run/EXE helpers |
| `scripts/preview-server.cjs` | Loopback preview127.0.0.1:4173 |
| `scripts/render-preview.cjs`, `library-ui-smoke.cjs`, `mods-ui-smoke.cjs`, `mods-smoke-preload.cjs` | Isolated dev-only UI harnesses |
| `output/` | Test evidence/screenshots/hash manifest; timestamps matter |
| `README.md`, `MIGRATION.md`, `ART-DIRECTION.md` | Supporting docs and complete90-channel Electron IPC parity inventory; some status/checkmarks lag current implementation |

Original feature references: root `main.js`, `index.html`, `auth/`, `launch-worker.js`, `prepare-worker.js`, `optimization.js`, `launcher/`, `lang/translations.js`, updater/scripts and Java-agent contracts. Never delete the old source while porting.

Dependencies include Tauri2/tauri-build2/opener2, serde/json, dirs6, reqwest0.12 Rustls/blocking/json, sha2, fs2, base64, zip6 deflate, toml, UUIDv4, tempfile, sysinfo; target-specific keyring3.6 Windows-native/Apple-native/Linux secret-service. Other-OS feature flags do not prove packaged platform support.

## Source versus packaged checkpoint

Last recorded native package built 2026-10-30 in1m17s; NSIS about7.12MiB. Built, not installed/published.

- EXE: `D:/BatClientBuildCache/rust-launcher-target/release/bat-client-next.exe`
- SHA256: `988313A174FE9BAF9D7448D4F1A6CC8584BB968CFCB8AFDA183254E79597CBF0`
- Installer: `D:/BatClientBuildCache/rust-launcher-target/release/bundle/nsis/Bat Client Next_0.1.0_x64-setup.exe`
- SHA256: `DEF82480C2B093353DAB7547545DAD6DAF0DA7A4AE7E81833DB0442CDAD62772`
- Manifest: `output/native-package-hashes.json`. Rehash after rebuilding; do not reuse stale evidence.

Newer **source-only** changes: official version catalog/autocomplete; earlier Electron-running guard at Microsoft login; generated monochrome Home artwork v3; brighter/bordered loadout actions; red Remove styling; card/editor/press animations; Microsoft/offline SVG provider helper and restoring sign-in button icons. Catalog tests passed. Latest visual changes only had JS syntax checks, not fresh screenshots/native packaging. Old output screenshots are stale for those edits.

User finished their sign-in test and authorized restart. Recheck actual running process/path before replacing an executable; old PID16616 is historical, not a permanent target.

## Toolchain/build/run/test

Build Tools2022 17.14.44 installed manually at `D:/BuildTools`; WebView2 detected. Rust stable previously1.99.1. Earlier automatic installer restriction was not bypassed; do not reinstall prerequisites unnecessarily.

Set process environment before Cargo (helpers do this):

```powershell
$env:RUSTUP_HOME = 'D:/BatClientBuildCache/rustup'
$env:CARGO_HOME = 'D:/BatClientBuildCache/cargo'
$env:CARGO_TARGET_DIR = 'D:/BatClientBuildCache/rust-launcher-target'
$env:TEMP = 'D:/BatClientBuildCache/rust-temp'
$env:TMP = $env:TEMP
$env:PATH = "$env:CARGO_HOME/bin;$env:PATH"
```

From Raven-Client root:

```powershell
./launcher-rust/scripts/check-native.ps1
./launcher-rust/scripts/dev.ps1
./launcher-rust/scripts/build.ps1
cargo test --locked --release --features custom-protocol --manifest-path launcher-rust/src-tauri/Cargo.toml
```

build.ps1 builds the EXE only. NSIS from `launcher-rust/` with the D: environment:

```powershell
  npm exec --cache D:/BatClientBuildCache/npm-rust-tools --yes --package @tauri-apps/cli@2 -- tauri build --bundles nsis
```

NSIS is currentUser, startMenuFolder Bat Client Next, local tools. Transparent ICO/PNG resources verified previously in EXE/installer; actual installed shortcuts not tested. No macOS/Linux native package validation yet.

## Config, privacy and security

Default library `dirs::config_dir()/bat-client`, Windows `%APPDATA%/bat-client`. `profiles.config.json` contains private accounts/cosmetics/token refs/settings. Never dump it or paste actual values into docs/tests/frontend. Preserve skins/, game folders, jre/jre-major, authlib-injector.jar, bat-cosmetics.jar, dismissed-mods.json, mod-project-map.json, msa-accounts and older credential state.

Writes use WRITE_LOCK, fs2 `.rust-config.lock`, original-byte SHA256 backups `rust-migration-backups/profiles-<hash>.json`, conflict recheck and atomic replacement. Preserve unknown fields. Backups are private. Legacy Electron process guard avoids normal simultaneous writes; Electron does not honor Rust lock, so no claim of perfect external-writer exclusion. Tests use temporary fixtures only. Settings/mod/profile mutation checks game/legacy processes as applicable.

Public DTOs exclude raw accounts/tokens/custom launch args. Main capability is restricted to main-window core actions and start dragging. CSP scripts/styles self; images self/data/Modrinth CDN; IPC connections only, no arbitrary remote scripts. Dedicated Microsoft webview has different label and no main IPC capability. External links are allowlisted official destinations. Never return existing shared skin-service token to frontend, log OAuth bodies, collect passwords or silently migrate encrypted Electron tokens unsafely.

## Feature contracts

### Modrinth / installed mods

Exact selected game version/loader Fabric/Forge/NeoForge/Quilt. Vanilla unsupported result is explicit. Search20/page, pagination/manual refresh, stale response suppression. Required dependency graph max40; declared compatibility, SHA512 and ZIP signature; HTTPS CDN/no redirects;100MB/file and300MB/batch. Stage all, no-clobber hardlink commits, rollback new files on failure. No automatic background mod downloads. Previously removed/disabled dependencies block a parent until explicit user restoration.

Installed regular `.jar`/`.disabled` only, reject traversal/symlink/collision paths. Remove is immediate and recoverable in game `.bat-removed-mods/<timestamp>`; it is not space cleanup. Switch preserves bytes; busy/error/focus recovery. Sodium disable/remove cascades recognized Sodium Extra/Reese add-ons; enabling requires Sodium. Never re-add removed Sodium on launch/new profile. Protected nested Bat renderer migration still pending; do not rewrite protected JARs casually.

Icons read local Fabric/Quilt/Forge/NeoForge/mcmod.info metadata/fallback paths, including disabled mods. Raster PNG/JPEG/GIF/WebP signature checks, no SVG;128MB archive/256KB metadata/1MB icon/40000 entries;128entry/8MB cache invalidates on path/size/timestamps. Subtle upward entrance explicitly requested; reduced-motion honored.

### Profiles / accounts / skins

Native validated profile create/edit/copy/favorite/delete/select/details; unknown opaque fields retained. Copies clear original legacy gameDir/mods exclusions. Removal metadata only, last profile cannot be deleted. Selection writes only on user action. Custom JVM field returned only by explicit details. Official catalog datalist does not install loaders.

Offline usernames3–16 ASCII alphanumeric/underscore, case-insensitive duplicates rejected. Add/select/remove persist. Premium selection preserves old credential references; premium deletion blocked until secure cleanup parity. Local PNG skin getter constrained to skins directory, size/dimension caps; canvas face+hat unsmoothed, Steve fallback. Hosted skin fetch/upload, official premium remote skin lookup, slots/equip/removal/animated capes/3D wardrobe remain pending.

### Microsoft

PKCE S256/random verifier+state, exact HTTPS callback origin/path/port/state/duplicate checks. Worker-thread webview creation avoids main-thread deadlock.300s interaction/30s HTTP/1MB body bounds, sanitized errors. OAuth->Xbox->XSTS->Minecraft->Java entitlement/profile; refresh code exists. Tokens Rust-only/no Debug/plaintext fallback; keyring2000byte chunks<=32 plus metadata for Windows limits. New random rustAuthId preserves legacy IDs/token stores/cosmetics. Roll back secure writes if config commit fails; offline username conflicts never silently convert.

Synthetic Windows credential-store roundtrip+cleanup and RFC PKCE/callback rejection tests pass. `--auth-window-smoke` verifies hidden secondary WebView creation/close only; exit0 with nonfatal Chromium unregister1412 stderr. User-confirmed actual login succeeded; refresh/recovery/premium game/otherOS remain unproven.

### Java / arguments

Manifest major authoritative; known fallback8/16/17/21. Probe custom/javaw-normalized/bundled/environment/PATH/vendor candidates for exact major with hidden5s timeout. No downloader yet. Preserve quoted Windows paths/custom agents; reject managed classpath/main/exit/argfile options,8k limit. Profile GC overrides global, remove G1 residue when using another collector; repair AggressiveOpts on newer Java. Composition is not yet wired to a native launch; no global JVM/renderer policy change implied.

### Settings / news

Initial core settings: defaultRam, javaPath, extraJvmArgs, gameDir, downloadThreads, windowWidth, windowHeight, fullscreen. Unknown secret/unported fields preserved. Electron settings still needing surface/consumers: jvmPreset, autoOptimize, cullLeaves, fastMath, gameWindowTitle, hideOnLaunch, keepLauncherOpen, autoCloseOnGameExit, autoCheckUpdates, showConsole, closeToTray, startWithWindows, language, discordRPC, launchSound, notifications, crashReports, publishSkinsToBatClient. Saving preferences is not runtime integration; label unfinished consumers truthfully.

Skin-service token requires masked/write-only treatment, blank preserves, explicit clear; never return actual value. Not implemented initially. firstLaunch is internal onboarding. Languages en/es/fr/de/pt/ru/tr/ar in root lang/translations.js not wired. Native directory dialog plugin not yet added. Java installation/reset/cache/folder operations pending. Reset must keep accounts/profiles; cache cleanup never removes worlds/skins/tokens.

News fixed source `https://batclient.xyz/changelog.html`, Rust HTTP, downloaded HTML parsed inertly/plain text extracted only. Manual refresh/labeled offline cache. file:// preview cannot fetch news, loopback dev server can. Native end-to-end news still requires QA.

## Approved design / latest unfinished requests

Pure black/white with neutral borders, no blue/gray panel redesign. SVG tabs/icons: earlier PNG request reversed. Keep approved Home. Loadout bottom-left and Launch bottom-right directly on artwork. Hero270/250/230 responsive heights; CTA width218/196 with reduced height64/60 (user meant less tall, not narrower). Account dropdown compact250px; loadout top-layer dropdown downwards avoids clipping. Quick access3cards plus Settings. Installed toggles are switches, remove separate/red.

Latest source uses `ui/assets/home-widget-generated-v3.png`: original black/white pixel scene from supplied house/trees/dueling-block-figures reference,2079x756, about767KB. CSS center58%/cover/pixelated; fresh fit QA required. Prior user246x138 `home-widget-user.png` retained and used in last binary. Prior valley/cliffside assets inactive. Bat logo500x500 transparent. Generated original source is under user .codex/generated_images; no need regenerate merely to continue.

Source-only brighter action buttons/background/borders, red Remove labels/classes, loadout/editor entrance/hover/press CSS and providerIcon(kind) SVG Microsoft/offline helper exist. Sign-in button restores SVG after completion. Need fresh UI/native build evidence; JS syntax alone passed previously. User asks all Electron settings, clear visible controls and animations; don't claim those consumers exist prematurely.

## Verification checkpoint and limits

Before this handoff:31 Rust tests,27pass/4 opt-in ignored. Explicit actual Mojang catalog passed1.71s. Prior actual installed-Java, live Modrinth download/toggle/remove fixture and synthetic Windows secure-store probes passed. Tests use temp libraries, not user mods/worlds. UI harness uses isolated temporary D:userData, sandbox/contextIsolation, no frontend Node; remove ELECTRON_RUN_AS_NODE in helper environment. Fake IPC proves UI only. Native startup/auth-window lifecycle pass. Installer not executed; latest art/control edits not QAed. No native game/FPS/cosmetic/update/crossplatform pass.

## Next work

1. Finish pending UI/art fit/settings migration with isolated verification and updated honest statuses.
2. Native Java installation/game pipeline: rules/inheritance/integrity/downloads/assets/libraries/natives/arguments/classpath/loaders, cancellation/logging and process lifecycle. Preserve opt-outs and custom JVM/agents.
3. Premium refresh/recovery and offline authlib, skins/static+animated capes/agent/wardrobe/slots, keeping secrets backend-only.
4. Remaining90-channel IPC parity from MIGRATION: import/export/reorder, mod conflicts/updates/presets, screenshots, system integrations/RPC/dialogs/tray/startup, crash/benchmark features.
5. Secure native updater/version+checksum trust/staging cleanup/recovery and Windows/macOS/Linux packaging. No production change without explicit release authorization.
6. Actual native game/cosmetics/updater tests before replacing Electron; only return to paused mod work when scope changes.

## Maintenance

Update this file after meaningful Rust work with exact files, tests/failures, remaining work and source-versus-built status. Keep README/MIGRATION aligned. Never paste private configs/credentials. Compilation/UI evidence does not prove native gameplay or FPS improvement.

## Current native commands

- `bootstrap`
- `versions::minecraft_versions`
- `settings::launcher_settings`
- `settings::save_launcher_settings`
- `microsoft::start_microsoft_login`
- `microsoft::refresh_microsoft_account`
- `accounts::account_action`
- `accounts::account_skin`
- `profiles::save_profile`
- `profiles::profile_details`
- `profiles::profile_action`
- `java_runtime::detect_java`
- `mods::installed_mods`
- `mods::change_mod`
- `mods::search_modrinth`
- `mods::install_mod`
- `changelog::fetch_changelog`
- `window_action`
- `open_link`

## Electron IPC inventory

These names are a parity checklist, not completion claims. Use MIGRATION.md and actual implementation/tests to assess each.

- `record-launch`
- `get-launch-log`
- `get-mojang-versions-list`
- `check-for-update`
- `download-update`
- `install-update`
- `check-update-result`
- `search-modrinth-api`
- `check-mod-compat`
- `update-rpc-state`
- `minimize-window`
- `maximize-window`
- `close-window`
- `open-external-link`
- `copy-text`
- `get-bat-news`
- `get-system-info`
- `open-folder`
- `select-directory`
- `get-game-root`
- `get-mods-info`
- `download-modrinth-exact`
- `download-modrinth-file`
- `get-mod-details`
- `get-profiles-data`
- `create-blank-profile`
- `save-profile-node`
- `delete-profile-node`
- `reorder-profiles`
- `duplicate-profile-node`
- `select-active-profile`
- `export-loadouts`
- `import-loadouts`
- `open-profile-folder`
- `get-accounts-data`
- `add-new-identity`
- `start-microsoft-login`
- `refresh-microsoft-account`
- `add-premium-identity`
- `switch-active-identity`
- `reorder-accounts`
- `delete-account-identity`
- `set-account-skin`
- `get-account-skin-data`
- `set-custom-cape`
- `remove-custom-cape`
- `save-account-slots`
- `remove-skin-slot`
- `remove-cape-slot`
- `get-account-slots`
- `get-equipped-cosmetics`
- `get-app-settings`
- `save-app-settings`
- `check-first-launch`
- `complete-first-launch`
- `detect-java`
- `get-java-status`
- `download-java`
- `clear-launcher-cache`
- `get-installed-mods-count`
- `get-installed-mods`
- `toggle-mod`
- `remove-mod`
- `check-mod-conflicts`
- `export-modpack`
- `import-modpack`
- `launch-game`
- `get-system-ram`
- `get-launch-presets`
- `save-launch-preset`
- `delete-launch-preset`
- `apply-launch-preset`
- `get-mod-presets`
- `save-mod-preset`
- `delete-mod-preset`
- `apply-mod-preset`
- `ping-minecraft-server`
- `get-screenshots`
- `open-screenshot`
- `delete-screenshot`
- `check-mod-updates`
- `update-mod`
- `analyze-crash`
- `get-game-health`
- `start-benchmark`
- `stop-benchmark`
- `get-profile-game-dir`
- `send-discord-live-stats`
- `snapshot-mod-preset`
- `get-screenshot-thumb`

## 2026-10-31 continuation checkpoint (source; package verification pending)

Created this canonical handoff before implementation. Extended settings.rs and ui/settings.js from8core fields to26 preferences, preserving Electron discordRPC acronym and16-thread default. Added grouped black preference controls with switches and explicit notice that unported Rust consumers remain inactive; failed settings read cannot save defaults, duplicate submits blocked, dirty form retained when returning to Settings, successful commit not misreported as failed by later bootstrap refresh error.

Skin service credential control is write-only: frontend receives configured boolean only, replacement field skipped from serialization, blank preserves, explicit clear removes, mutually exclusive clear+replacement and whitespace/size validation. Existing firstLaunch/unknown fields/cosmetics remain untouched. Token input clears after successful save. Never use a real secret in automated fixtures.

New tests cover full preference roundtrip, legacy acronym/private-field preservation, invalid language/preset/title with byte-identical config on failure, injected unknown secret rejection, write-only replacement/blank/clear. Initial token test exposed a TEST DTO mismatch: serializing the output-only configured flag then deserializing strict input is rightly rejected; fixture now removes output-only flag before input validation. Await final rerun before marking tests passed.

Isolated library UI smoke passed with new language/preset/RPC/title save payload checks; preview/news checks passed including live official changelog and labeled offline cache. Inspected fresh home-preview.png and settings-preferences-preview.png. Home art fits hero with visible play/loadout overlays; black grouped settings and switches visible. These are dev-harness visual checks, not native game-launch evidence. No user config/world/mod changes. Pending native build/package and app restart; core game/cosmetics/updater parity still incomplete.

### Final settings test result (2026-10-31)

Corrected DTO fixture rerun:34total,30passed,0failed,4 opt-in ignored,0.90s after28.44s optimized test compile. No implementation failure remains from that test. Isolated UI fixture and preview/news checks PASS; no real user settings/credentials modified by tests. Native NSIS rebuild now in progress; update artifact hashes and package status after completion.

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

### Final compatibility/lifecycle refinements

Preserve original Electron offline identity policy: accounts with equipped skin/cape/skin slots use Java OfflinePlayer MD5 UUID; unequipped legacy accounts use the original all-zero UUID. Do not change identity solely due to launcher migration, which can separate existing player data. Regression covers both paths. No saved account UUID/player files edited.

Native launch also holds fs2 .rust-game.lock for the full game lifetime across Rust processes, alongside in-process ACTIVE and existing Minecraft/legacy process checks. A second native app cannot race to start another JVM into the shared game directory. Lock is released after child wait/bridge/native cleanup or any preparation error. No foreign JVM termination. App restores its window on preparation error even if it was hidden during startup. Native queue/lock checks do not claim to control a foreign launcher.

Latest tests before packaging:42offlinePASS/5opt-in ignored after offline-identity addition, then added cross-instance game-lock regression (final suite/package result recorded next). Source includes new UI/launch/lifecycle code; no additional mod performance/port/production change. Installer packaging is in progress, not a published release.

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


## 2026-10-31 — damaged 1.21 cache, progress, wardrobe camera and startup

Latest user screenshot reported `Installed file checksum mismatch: 1.21.jar`. Actual installed archive was truncated: 12,680,644 bytes, SHA1 `0c663b25849abe0ebebd70c3fc08066d26545c49`, expected official SHA1 `0e9a07b9bb3390602f977073aa12884a4ce12431`.

- Added `game_repair.rs`, registered and called before preparing installed Vanilla/Fabric. It downloads only the affected base client, using the fixed Mojang catalog, verified manifest SHA1, allowlisted HTTPS hosts with no redirects, bounded metadata/client sizes, streaming byte count and SHA1 verification, valid JAR check, staged atomic replacement. The damaged original is preserved under appData `bat-client/rust-migration-backups/game-files/client-<oldSHA1>.jar`. It does not reinstall mods, change opt-outs, touch worlds/options or retag loaders. Missing full installation remains separate unfinished work.
- Explicit authorized repair test ran against the actual 1.21 cache and passed in 17.85s; repaired=true and official client SHA1 verified. No check was bypassed.
- Actual native Minecraft **1.21 Fabric** test after repair: disposable `D:/BatClientBuildCache/native-rust-game-6RuILv`, synthetic BatNativeTest, separate game directory, existing agents copied, only `fabric-api-0.116.17+1.21.1.jar` copied as external mod. Renderer texture initialization + Bat premain confirmed at 39,710ms (41.49s whole test). Test stopped only its own menu-only JVM. This proves this cached startup path, not gameplay/FPS, full Bat mod compatibility or visual interaction. No user world was loaded. User's real mod collection was not copied.
- Added native Status.progress optional fraction. Home launch bar is indeterminate during verification/Java opening, determinate from actual repair byte counts. New `starting` state remains until bounded tail polling sees renderer initialization in the private log; process creation alone no longer claims game ready. No fake percentages. Failed/closed state restores the launch action; existing hide-on-launch preference remains.
- Branded black startup screen in `startup.js` waits for library readiness and local logo/fonts, minimum450ms +220ms fade; explicit retry on failure/slow read. app.js tracks bootstrap lifecycle so an early resolved bootstrap cannot strand splash. Reduced-motion supported.
- Wardrobe camera root cause: OrbitControls saved its initial pose before viewer zoom was set. `controls.reset()` restored that unsuitable pose inside the character. Front/Back now use resetCameraPose, centered target, update/saveState. Zoom/pan/right/middle actions disabled, left drag rotates; browser contextmenu prevented in app capture listener.
- Wardrobe Apply now saves locally then attempts existing-config online publishing from native Rust. No token is exposed to UI, logs or hardcoded in new code. Registry fixed endpoint, header credential from saved appSettings, no redirects,30s timeout,4MB body and64KB response bound, explicit JSON ok confirmation. Microsoft skin multipart upload uses refreshed native keyring session. No real user cosmetic was uploaded by our tests. Registry payload unit-tested and frontend mocked. Live upload success is **not yet verified**.
- Registry's existing API does not delete hosted cosmetics when given null. Removal honestly stays local. Saved animated capes preserve local animation; remote upload uses first PNG frame and says so, not a false full-animation sync claim. GIF import migration still pending. Errors retain successful local saves.
- Updater question: Rust Windows/Linux/macOS signed update channel is **still pending**, Electron production updater untouched. Do not claim other-OS binaries/updater tested; do not modify production update.json or publish without authorization. Original migration/parity work is still incomplete.
- Regression: release Rust tests 53 total,47 passed,6 opt-in ignored,0 failed (0.90s); actual authorized repair and native1.21 tests separately passed. JS syntax and isolated wardrobe/settings/versions/shared picker/camera/contextmenu/startup/launch progress harness passed. Camera regression asserts stable radius Front/Back, no pan/zoom; progress test confirms45% fixture fraction, normal button width, hidden after start. Visual artifacts `output/wardrobe-preview.png`, `launch-progress-preview.png` regenerated and reviewed. Native package hashes/checkpoint below supersede older hashes once built.


Final packaged checkpoint for the above changes (2026-10-31):

- Final Tauri NSIS build succeeded in1m30s; installer8.40MiB. EXE `D:/BatClientBuildCache/rust-launcher-target/release/bat-client-next.exe`, SHA256 `A5757009E39D1106CAE35EF3A4A29F864CCB6B8B8D5B8C5B198F60F7C855E5A4`.
- NSIS `D:/BatClientBuildCache/rust-launcher-target/release/bundle/nsis/Bat Client Next_0.1.0_x64-setup.exe`, SHA256 `C59D87915A6833C765657E626CC0F030BD0AAAD1B1EA0825365C77554F99D140`.
- `output/native-package-hashes.json` refreshed; `output/launch-repair-evidence.json` records actual repair/native evidence and limits. Native app reopened PID17924, responding, title Bat Client Next · Development. PID is historical; recheck before next close/rebuild. Installer not installed or published.
- Final UI regression re-run passed after the progress layout and splash hidden-display fix. Splash uses explicit display:none when hidden, avoiding an invisible animation left running. Progress is absolutely positioned inside the approved CTA so it cannot shrink the button. No existing user JVM remains from our disposable test.


## 2026-10-31 — signed Rust updater using the existing update.json

User explicitly asked to add Windows/Linux/macOS updating and use Bat Client's existing update.json. Implemented new `src-tauri/src/updater.rs` and `ui/updater.js`. It reads the fixed HTTPS endpoint `https://batclient.xyz/update.json`, extracts **only** a versioned `rust` channel (`schema:1`, `product:bat-client-rust`, `enabled:true` required), and never falls back to Electron root fields or legacy platforms. Existing live/legacy Electron2.2.97 packages are not Rust update artifacts. Target keys windows-x86_64/darwin-x86_64/darwin-aarch64/linux-x86_64; mapping also understands Windows/Linux aarch64 but no corresponding CI packages promised.

Rust check/update public IPCs: check_launcher_update, launcher_update_status, install_launcher_update. Settings → Launcher update panel has check, actual download progress and explicit Install update & restart. Startup checks honor autoCheckUpdates; checks alone do not install. Background checking must not block launching Minecraft (INSTALLING is separate from check BUSY). Installing holds the same cross-instance game lock, rechecks no game/legacy process and holds WRITE_LOCK during installation; game launch also refuses during update. No user JVM is killed.

Manifest HTTP limited1MiB/25s/no redirects. Asset URL constrained to existing Bat Client GitHub releases repo; redirects only HTTPS GitHub asset hosts, at most5. Exact advertised length/512MiB ceiling, SHA256 then Tauri signature verification required. `tauri-plugin-updater`2.14.1 is locked; official `restore_update` turns the nested manifest into a native installer update and enforces signature-bound version. Production uses default newer-only semver comparison; no downgrade override. Windows NSIS restart delegated to Tauri; macOS .app.tar.gz and Linux AppImage native install then app restart. Native replacement/restart still requires release validation, especially Mac/Linux.

Tauri config now createUpdaterArtifacts=true, updater requireSignedVersion=true, pubkey and endpoint configured; platform configs choose Linux appimage and Mac app/dmg. Endpoint configuration is required even when using restore_update: initial native verification failed because endpoints were absent; that was corrected, and the final native verification result is recorded next. Our code fetches/parses the shared manifest itself rather than interpreting Electron's root as a Tauri manifest. No direct updater plugin JS permissions added; frontend uses bounded native wrappers.

Persistent new private signing key is `D:/BatClientPrivate/rust-updater/bat-client-next.key`, directory ACL restricted to current user, outside source/build caches. Public key `launcher-rust/src-tauri/updater.pub` is safe to ship and mirrored in config. Private key has no password and relies on ACL; never print/copy into repo/public site/packages. Preserve/back up securely; future signed updates require the same key. Do not regenerate on continuation. Only public key is packaged. GitHub secrets BAT_RUST_UPDATER_PRIVATE_KEY / BAT_RUST_UPDATER_PASSWORD not remotely configured by this task.

`.github/workflows/rust-packages.yml` is a new manual artifact-only matrix for Windows x64, macOS Intel/Apple Silicon, Linux x64, without publishing/deploying. No remote GitHub changes or runs yet. Workflow is not proof of native Mac/Linux correctness. Local `scripts/prepare-update-manifest.py` calculates actual asset hashes/sizes and reads adjacent signatures, preserves every unrelated legacy field, writes a separate reviewable output, always disabled until signed packages are published and verified. `UPDATER.md` contains exact schema/build/deployment/testing instructions and limitations.

Validation so far:58Rust tests total,51PASS/7opt-in ignored/0failed,1.45s; explicit live_shared_manifest read-only HTTPS test PASS0.65s;2Python manifest generator tests PASS; JS syntax PASS; updater UI availability/install/progress mocked harness PASS; existing wardrobe/settings/version/launch UI regression PASS. Updated updater-settings-preview.png inspected. No real updater installation, cosmetic POST, production deployment, release publication or user data change was performed. Native signature verification and final package status follow below.


### Final updater package and exact remaining release work

- Final signed Windows build succeeded1m14s; NSIS8.75MiB plus adjacent `.sig`. EXE SHA256 `21EE1F70046BDD6188AF3EECF1D16881DAF6D61859576872B31FF1D2B073C719`; NSIS SHA256 `98AC89F8006593650AB631E66764B4FE4AF82AF0A67A53C2DC9B0D034C0B2D73`; `.sig` SHA256 `D17619AB02ED3F92930F61EB875F7D562B1FE3C72E421B6198A4782EC3E85303`. Paths remain D:/BatClientBuildCache/rust-launcher-target/release/bat-client-next.exe and release/bundle/nsis/Bat Client Next_0.1.0_x64-setup.exe(.sig). Hash manifest refreshed.
- Native `--updater-verify-smoke` PASS after endpoint fix: actual signed package accepted, modified bytes rejected, same signed bytes with version999.0.0 rejected. No update installed. Hidden WebView teardown emitted Chrome_WidgetWin_0 unregister1412 warning on exit; test exit0 and signature assertions passed. New regular native app reopened PID14668, responding. Installer not executed/published.
- `output/update.rust-draft.json` prepared from the actual local website mirror using the actual signed Windows package. Local mirror `C:/Users/Noor-ul-Islam/Desktop/BatClient_SEO_Ready/update.json` now contains this disabled Rust channel. JSON equality assertion verified all other fields, including protected Electron download/checksum/platforms, unchanged. UTF8 without BOM. Original manifest snapshot preserved at output/update.before-rust-channel.json. No production/SSH/GitHub changes.
- Current Rust channel deliberately disabled: no Rust assets published at the generated rust-v0.1.0 URL. No fabricated macOS/Linux artifact entries; run configured native CI, supply the preserved signing key via repository secret, verify platform startup/update replacement, upload signed assets and only then activate/deploy reviewed manifest. Current development launcher is still not complete clean-PC feature parity. Mac/Linux native builds/installation and true two-version replacement/restart have **not** passed by implication from Windows tests.
- Native format configs tauri.linux.conf.json (appimage), tauri.macos.conf.json (app/dmg and generated transparent icon.icns). ICNS generated from existing transparent Bat icon through Tauri icon tooling, original Windows icons preserved. Other generated icon exports remain D:/BatClientBuildCache/rust-icon-export; no unrelated cleanup.


## 2026-10-31 — automatic updating on the loading screen

Latest user clarified the required behavior: opening Bat Client must check on its loading screen, automatically download/install an available update, then restart itself. This supersedes the previous startup-check-only behavior. Default autoCheckUpdates=true remains shared with Electron; explicit false is respected. Settings label is now Automatic updates on startup.

`ui/startup.js` now gates Home on BOTH library readiness and completion of the startup update operation. It exposes a narrow BatStartup update/complete UI bridge; determinate update download progress replaces the startup animation and shows an actual percentage, verification/installation uses indeterminate status. `ui/updater.js` owns exactly one startup check after native bootstrap, reads the saved preference, calls the existing native signed install command immediately for an available update, and keeps the loading screen visible while the installer is replacing/restarting the process. No confirmation/click required. Manual Settings check/install remain retry controls. No extra bootstrap-triggered rechecks after account/profile/settings edits.

Unpublished/current channel and disabled preference proceed to Home. Offline check or install/signature failure shows a short failure message, then opens the unchanged launcher with the error notification instead of trapping startup. Native game/legacy-process and cross-instance game locks still prevent update installation while a game is using the app; no game force-close. The existing Windows installer restart mechanism and Mac/Linux app restart path are reused, with signature/size/SHA256/version-bound verification unchanged. Added explicit native verifying status before package verification.

Source/regression:51Rust tests PASS/7opt-in ignored/0failed,0.90s. New startup-updater-smoke.cjs runs7 isolated mocked IPC cases: available auto-install with50% splash progress and hidden Home, current, unpublished, disabled (zero remote checks), offline (zero install), invalid signature (opens preserved app), installing (splash remains visible awaiting restart). All7PASS; image startup-update-preview.png inspected. Updater settings UI and full library UI regressions PASS; Wardrobe/version/launch regression result recorded with final packaging. These prove UI orchestration and existing crypto tests, NOT an actual two-version installed replacement or Mac/Linux native release. Live Rust channel remains unpublished/disabled; no production/GitHub/deploy changes authorized or made. Documentation UPDATER.md updated. Signed rebuild and refreshed local disabled manifest hashes follow below.


Final automatic-startup updater checkpoint: signed Windows EXE/NSIS rebuilt SUCCESS2m04s,8.75MiB; native authentic/tampered/version-substitution verification re-run PASS (same benign WebView unregister1412 warning on hidden smoke teardown; exit0). Full Wardrobe/settings/versions/launch UI regression also PASS, as did updater settings and library regressions. New regular native app reopened PID19684, responding; recheck PID before next rebuild. Actual installed two-version replacement and Mac/Linux native tests remain pending, not claimed.

Current EXE SHA256 `770A2F1647102145A3E73010AB64886463AEB64F38D848F4A5FCF3475E426266`; NSIS SHA256 `200AD55D4A1B8A609DC2525EFC88740A7D24B45EB0AC683D452842165B388DAC`; signature-file SHA256 `63B85D94ABEFDAB473B50CBF6B42B172910D499AE4BF16108787430AE8A6EFC4`. D: artifact paths unchanged. output/native-package-hashes.json refreshed. Disabled local Rust manifest metadata refreshed against the new signed package, all legacy fields asserted unchanged. Nothing published/deployed or installed on the user's system. Keep same persistent signing key.


## 2026-10-31 — complete source parity audit (user asked verify remaining Electron migration)

Added launcher-rust/PARITY-AUDIT.md, output/parity-audit.json and scripts/audit-parity.py. Reviewed all90unique Electron IPC channels/91registrations (get-app-settings has both on/handle) against current native implementation, all26registered Rust commands,25literal frontend calls, launch installer/agent requirements, cosmetics and actual preference consumers. No orphan literal frontend invoke found; backend-only refresh command is also used internally before launch. Manual coverage classification:17Implemented,24Partial,48Missing,1Unverified. These are command/workflow scopes, **not a feature-completion percentage**. Full explicit evidence/limits recorded per row; script validates complete inventory coverage and evidence paths and records current source SHA256 values without credentials/user configs.

Confirmed release blockers: clean-PC game/assets/libraries/Java/loader/agent provisioning; Forge/NeoForge/Quilt; premium lifecycle/cosmetics/GIF slots/imports and live upload/refresh/gameplay acceptance; mod updates/details/version/file/sort/import/export/presets/general-conflict reports/older protected nested Sodium handling;16saved-only settings; public signed platform releases and actual installed two-version update replacement. Additional missing folder/onboarding/history/server/screenshot/crash/health/benchmark/RPC/tray/localization/workflow details in report. Top-level mod rename/remove, opt-outs and Sodium-dependent guards do work; do not confuse them with old protected-JAR nested renderer rewriting.

Verified preference consumers: defaultRam,javaPath,extraJvmArgs,gameDir,windowWidth,windowHeight,fullscreen,hideOnLaunch,autoCheckUpdates,publishSkinsToBatClient (last one live sync unverified). Remaining16save only:downloadThreads,keepLauncherOpen,autoCloseOnGameExit,showConsole,closeToTray,startWithWindows,discordRPC,launchSound,notifications,autoOptimize,cullLeaves,fastMath,crashReports,jvmPreset,language,gameWindowTitle. Reused agent source does not consume those flags; no claim this disables the agent's independent existing tuning. Save-only toggles must not be called migrated runtime features.

Correction from deeper Electron inspection: main.js launch-game uses global gameRoot; getModsDir also global. Electron's get-profile-game-dir merely returns an instances path. Therefore full per-loadout isolation is **not** an established existing feature Rust lost; folder/path helper UI is missing. Do not falsely present it as a regression or silently migrate worlds into instance folders.

Fresh verification:51RustPASS/7opt-in ignored/0fail,1.01s execution; startup updater7mockedcases PASS; library UI PASS; Mods UI PASS. Existing EXE/NSIS/signature hashes match the stored package manifest, unchanged by audit. No app/game control, user config/world/account/mod edits, production/network cosmetic writes, release/deployment or package rebuild performed for audit. Existing native1.21/1.21.1startup and signature evidence remains limited as previously documented; no new gameplay, premium refresh/upload, installed updater replacement or other-OS native pass claimed.

MIGRATION.md's stale checkbox inventory replaced with current audit link/summary, historical dated evidence retained. README refreshed to avoid obsolete Launch-disabled/online-upload-absent/updater-absent claims. Audit is current reference; do not replace Electron yet. User asked verification, not another implementation batch; app code remains unchanged.


## 2026-10-31 — active full-migration implementation (user: add them all)

Implementation is IN PROGRESS; audit statuses from previous section are historical and not proof current batch is finished. Do not replace Electron or publish. New modules downloads/game_install/java_install/agents/tools/runtime/optimization; tools.js UI. Source added official verified Minecraft game/assets/libraries/natives and Fabric/Quilt/Legacy Fabric metadata installation, bounded concurrent downloads honoring downloadThreads, managed Adoptium Java installation, bundled existing Bat cosmetics agent (resource byte copy) and verified official authlib provisioning. No optional mod installer/reinstallation introduced. Forge/NeoForge still explicit unsupported, not retagged. Managed Java rollback preserves/restores old runtime on failed commit. Downloader permits only explicit HTTPS official hosts, checks hashes/size, keeps replaced originals, rejects traversal/symlinks. Runtime install archives bounded and reject unsafe links/paths.

Real native evidence: managed Java21 download/extract/probe PASS27.54s in D:/BatClientBuildCache/rust-java-install-hlkQPL (java-install-native.out/.err). Fresh official1.21.1 Fabric install fixture D:/BatClientBuildCache/rust-clean-install-ZsWfX3 created in this batch; correctly waited verification rerun PASS5.22s, CLEAN_FABRIC_INSTALL_VERIFIED in clean-install-native.out. Initial installation was launched without waiting; no initial elapsed time claimed. This validates installer/checksums/resolution, NOT game renderer/gameplay with new installation. Test accepts existing fixture only after canonical direct-child D: cache and rust-clean-install- prefix validation. Test executable before disk-feature rebuild559d9bb6142452b0. Prior full regression70total61PASS9ignored0failed1.17s; further changes added since then and pending rerun.

Tools backend/UI: fixed folder opening + native game/Java picker; JSON loadout import/export whitelists profile fields, validates all before atomic commit and preserves private opaque config; drag/Alt-arrow account/loadout ordering; bounded launch history and lastPlayed; actual bounded TCP Minecraft status and selected-loadout join (modern quickPlay, legacy server args, IPv6 brackets); screenshots preview with bounded PNG/JPEG and removal preserves bytes in owned .bat-removed-screenshots; bounded/redacted log/crash tails plus local hints; named launch/mod presets (mod apply toggles rather than deletes and refuses missing files); first-run welcome; system/RAM suggestions; installation health/free space; owned-game CPU/memory sampler explicitly has no FPS probe and no FPS claim. Need dedicated tools UI smoke; existing library UI PASS after tools.js loaded, before welcome addition (mock now firstLaunch:false). JS mocks extend tools contracts. Native dialogs/server/live presets/OS tray effects still need native acceptance.

Settings connected in source: downloadThreads, keepLauncherOpen/minimize, hideOnLaunch, showConsole/Tools logs, autoCloseOnGameExit, notifications, closeToTray, startWithWindows; native tray Open/Quit (quit does not terminate active Minecraft/bridge), autostart/notification plugins. Runtime setup skipped for hidden *-smoke probes. JVM balanced/low/high/custom presets now supply defaults through existing custom-GC composition; explicit global/profile GC and agents preserved. launchSound small WebAudio chime on started event (no audio before user gesture). Automatic optimization ports existing Electron options policy, honors autoOptimize:false with zero writes, version-specific Unlimited sentinel260/999 and VSyncfalse, preserves unknown settings/resource packs and content-addressed original options backup; OptiFine-only leaves/math switches not claimed to tune vanilla. Sodium config preset not yet ported. New health compile required enabling sysinfo disk feature; rerun pending (session68948 at checkpoint).

Still remaining: Forge/NeoForge installer processing and native version/platform matrix; mod maintenance/details/updates/modpacks/conflicts/older protected nested Sodium; cosmetic GIF/slots/premium cleanup/remote heads and live acceptance; DiscordRPC/language/windowTitle/crashReports runtime, native cache maintenance; installed two-version updater and Mac/Linux signed native evidence. Existing previous signed EXE/NSIS and disabled local update.json channel are OLD relative to this source batch. No current-batch package/reopen yet. Prior native app19684 may still run old binary; recheck gracefully before replacing. Private signing key unchanged outside workspace. No production or user worlds/accounts/mod files changed by tests. All generated cache/toolchain artifacts remain D:.


### User correction: remove Tools (2026-10-31)
Removed the newly added Tools sidebar tab and entire Tools page including Overview/Servers/Screenshots/diagnostics/presets UI. Do not re-add it during parity migration. Kept loadout transfer/order, account ordering, Settings folder/Java browsing/install, welcome and launch sound in renamed ui/library-extras.js. showConsole opens a dedicated Minecraft console dialog, not the removed page. Native tools_action helpers remain because those existing sections call them; backend-only diagnostics/presets are not exposed as a Tools screen. Replaced obsolete tools-ui-smoke with library-extras-smoke, asserting Tools tab/page absent and retained controls present. Source-only correction pending current batch packaging; do not claim the previously running old executable changed.


## 2026-10-31 — packaged migration foundation and GIF capes; Tools remains removed

User said continue after removing Tools. Added native cape_animation.rs using gif0.13.3 (Cargo.lock updated), RGBA compositing with transparent pixels and Background/Previous disposal, complete predecode before writes, 4MB encoded input, 64x32 integer-scaled canvas up to512x256,120frame/16MB expanded and PNG limits. Saves uniquely named PNG frames and clamped20..10000ms timings in existing customCape/capeFrames/capeDelays contract; atomic account commit; rolls back only new files on failure, preserves existing cosmetics. Does not claim online animated sharing: registry payload currently first frame, existing bridge/agent frame consumer reused and gameplay acceptance still pending. Wardrobe accepts PNG/GIF cape files, previews first GIF frame, native applies full animation. Existing skin PNG path unchanged.

Verification:74Rust tests total65PASS9opt-in ignored0failed1.86s. New tests decode genuine two-frame GIF, check timing/file signatures/private fields and invalid-input no-write behavior. Wardrobe/settings/version/launch UI regression PASS; initial run failed a fixed500ms preview wait during simultaneous compile, changed harness to bounded readiness polling with actual status errors; rerun PASS. library-extras-smoke PASS with assertion Tools tab/page absent, retained loadout transfer and Settings Browse present. Startup updater7casesPASS. Signed Windows build SUCCESS1m51s, NSIS9.42MiB. Native actual-package updater verification PASS: authentic accepted, tamper and version-substitution rejected; hidden WebView teardown still reports benign unregister1412, exit0. No actual installed replacement or Mac/Linux proof, and no gameplay/FPS test in this turn.

Audit regenerated from explicit reviewed mappings:90unique Electron channels,91registrations;28Rust registered commands,27literal frontend commands,no orphans.24Implemented24Partial41Missing1Unverified are workflow scopes not percentages. Updated fresh installer, Java, reorder/import/export, welcome, GIF scope and preference consumers. Four visible saved-only preferences remain DiscordRPC/language/gameWindowTitle/crashReports; tray/autostart/notification/native dialogs still need desktop acceptance. No Tools UI should return. Backend-only server/screenshots/health/CPU/preset helpers do not count as exposed equivalent features. Mod details/updates/modpacks/general conflicts/older protected nested Sodium and Forge/NeoForge remain next major work; do not claim full migration.

Packaged current paths D:/BatClientBuildCache/rust-launcher-target/release/bat-client-next.exe and bundle/nsis/Bat Client Next_0.1.0_x64-setup.exe(.sig). Exact current hashes in launcher-rust/output/native-package-hashes.json. Persistent signing key unchanged outside repo. New separate disabled output/update.rust-draft.json regenerated against these artifacts; website local/production manifest not changed in this turn, so its old disabled artifact metadata remains historical. Nothing installed, published, pushed or deployed. No player worlds/accounts/mods changed by tests. Older source-only checkpoint packaging warnings above are superseded by this build; remaining validation limits are not.

New native Windows app launched PID20776 after signed rebuild. PID is historical; recheck before replacement. This is an app startup observation, not visual/gameplay certification.


## 2026-10-31 — requested fresh verification of remaining Electron migration
Read current source/canonical notes and reran explicit90channel/91registration coverage. Corrected stale claims in generator: history/lastPlayed recording, Settings browse, native system/RAM, preset/status/screenshot/diagnostic helper implementations now distinguished from missing UI. Tools page remains intentionally removed, not a release requirement to recreate. Current reviewed scopes26Implemented46Partial17Missing1Unverified; counts are IPC workflows, not percent completion.28registered native commands,27literal frontend calls,no orphan literal invoke. Full PARITY-AUDIT.md and output/parity-audit.json regenerated with source hashes.

Fresh checks:65RustPASS9opt-inignored0fail1.76s; library-extrasUI PASS including Tools absent; ModsUI PASS. All local index.html src/href asset paths resolve. Current EXE/NSIS/signature SHA256 still match native-package-hashes.json. Local mirror Rust channel remains disabled with Windows-only metadata; published Rust assets and real installed replacement are not verified. No new game/native network tests, production changes, package rebuild, player account/world/mod edits or application implementation changes for this audit.

Outstanding feature groups verified directly: Forge/NeoForge rejected by installer/UI; Modrinth sort/details/gallery/exact-file/version chooser and updates/modpack/general-conflict/nested protected renderer workflows; cosmetic slots + premium removal/token cleanup + remote premium heads; DiscordRPC/language/gameWindowTitle/crashReports runtime; background Instant Launch, remaining Sodium-config optimization parity, safe cache/update-stage maintenance and post-update result receipt; helper/profile-folder and Home partner-server UI parity. Actual native Quilt/Legacy Fabric, new clean-install game startup/gameplay, animated multiplayer cosmetics, premium refresh/upload, tray/autostart/notifications and Windows two-version update/macOS/Linux releases need acceptance. Do not claim implemented upload APIs are live validated or first-frame registry sharing is animated online parity. User-requested Tools removal remains respected.


## 2026-11-04 — user explicitly chose dark black
User corrected the graphite/color direction: use Dark black. Replaced new graphite/mint/amber surface overrides with deep-black #030303 main, #050505 sidebar and #080808 panels/dialogs, white primary/selected controls and neutral borders. Destructive controls retain existing red semantics. Do not restore graphite theme or colorful palette. Mods sorting/details/version/file work from preceding interrupted turn remains source-in-progress; test/build evidence follows, no additional cosmetic-slot changes were made.

Dark-black Mods details/sort/exact-file UI smoke PASS, including computed #080808 dialog surface, explicit selected filename/version IPC and cleanup. Harness now waits for the asynchronous dialog close event before checking removal. Source theme changed; packaged Windows binary from previous checkpoint has not been rebuilt for this correction.


## 2026-11-04 — active remaining-feature migration, dark-black theme
Added Modrinth sort (relevance/downloads/follows/newest/updated), bounded project details/gallery and compatible version/file selection in Mods. Remote descriptions are text-only, never injected HTML; gallery restricted to HTTPS cdn.modrinth.com. Exact download resolves version from native API, requires project and Minecraft/loader compatibility, chosen filename must actually exist (no fallback), reuses checksum/dependency/staging/opt-out guards. Different existing/disabled mod versions still require explicit user management; automatic installed-mod update workflow remains unfinished. New native tests verify wrong filename rejection, compatibility and gallery filtering. Actual live read-only metadata acceptance test added opt-in; result follows. Mod details/sort/exact-file UI fixture PASS. Theme deep black as user corrected, white selected/primary controls, existing red delete semantics; no graphite/mint palette.

Added Wardrobe skin/cape collections (20 slots each) in existing panels: save equipped, name, equip, remove, skin head previews and cape/animation labels. Keeps Electron customSkins/customCapes file/model/frames/delays format and sparse slot indices. Saves references to already applied local cosmetics; no duplicate uploads or copies for snapshots. Equipping validates confined regular existing files and bounded animation files before atomic metadata edit. Removing collection entries leaves equipped cosmetic/files intact. Full-slot failure and invalid paths preserve config bytes. Native mutations reject running game/legacy launcher. Equip then uses existing sync_saved path with honest locally-equipped fallback; live network publishing not tested. UI ignores stale account-load responses; slot controls honor busy state. Native tests cover private fields/file preservation, stable indices, path rejection, animation timing restore and full-slot atomicity. Wardrobe slots UI fixture PASS, image inspected; broad regressions and final package results follow. Tools remains removed. No cosmetic uploads, player accounts/worlds/mod edits, production or publication during tests.


### Final 2026-11-04 package and acceptance checkpoint
Native regression80total70PASS10opt-inignored0failed2.03s. Explicit read-only live_mod_details PASS1.53s, Sodium1.21.1Fabric metadata returned22compatible versions. This is metadata acceptance, not a download/install/gameplay pass. Wardrobe slots UI PASS; Wardrobe/settings/version/launch regression PASS; Mod details/sort/explicit-file UI PASS; library-extras/noTools UI PASS; local HTML assets resolve. New screenshots output/wardrobe-slots-preview.png and mod-details-preview.png inspected. All actual user data unchanged by fixtures.

Signed Windows NSIS build SUCCESS1m39s,9.44MiB. New EXE/NSIS/signature hashes recorded in output/native-package-hashes.json. Exact paths D:/BatClientBuildCache/rust-launcher-target/release/bat-client-next.exe and release/bundle/nsis/Bat Client Next_0.1.0_x64-setup.exe(.sig). Native actual signed-package verification PASS: valid package accepted, tampered bytes and version substitution rejected; same benign hidden WebView unregister1412 at smoke teardown, exit0. Startup updater7mockedcasesPASS. Generated disabled output/update.rust-draft.json with current package metadata; no website mirror/live manifest edit, no publication/install/SSH/GitHub changes. Preserve private signing key outside repo; version remains0.1.0 development, not a live update release.

PARITY-AUDIT.md regenerated for2026-11-04 after reviewing new paths:90unique Electron channels/91registrations,31Rust commands,30literal frontend calls,no orphans.27Implemented51Partial11Missing1Unverified are workflow classifications, not a completeness percentage. Mod details/version/file/sort and collection slots no longer called missing; exact remaining limits stated. Major work still Forge/NeoForge official installer + version/native platform matrix, installed-mod updates/modpack/general conflicts/older protected nested Sodium, premium cleanup/remote heads/live cosmetic acceptance, four saved-only integrations, background prepare/cache maintenance/update result receipt and actual installed update/macOS/Linux releases. User removed Tools; keep removed. No performance/FPS claim or new game test in this batch.

New built native Windows app opened PID15840 after packaging; recheck PID before future replacement. Startup/process observation only, no claim of visual/gameplay verification in native app.


## 2026-11-04 — user says still grey; insists on complete migration
Set actual major surfaces and controls to #000!important, including selected navigation/tabs, native-select options, popup IDs and dialogs. White primary/Play buttons and red destructive labels retain contrast. Found Home image itself contains a bright white/grey sky, so render existing asset inverted with low-opacity silhouette and bottom mask over pure black, keeping original asset file unchanged. Do not restore grey card fills. Updated UI assertion requires exact rgb(0,0,0) on main/sidebar/active nav/installed row/sort. New mod-maintenance UI test PASS; original pre-inversion Home screenshot showed why perceived grey persisted. Revised artwork screenshot follows.

Added Installed Check updates + per-mod explicit Update. Native checks enabled Modrinth-linked files only, SHA512 verifies original against Modrinth hash registry/project; replacement version must match game/loader. Disabled/removed files are skipped. Explicit update rechecks current hash, rejects duplicates and stale choices, resolves required dependencies through existing opt-out guards. All files staged/hash-verified before changes. New transaction preserves old JAR plus metadata in .bat-mod-update-backups/<uuid>, restores old file/map/preferences on failed commit, never overwrites unrelated existing files and keeps unrelated opt-outs. Checks bounded to100linked mods, metadata20s timeout,60s overall budget (in-flight request can extend it); no silent background auto-update. General conflict/old protected nested renderer features still pending.

Added native export/import modpack file dialogs and controls in Installed. New .batpack ZIP includes checksummed bat-modpack.json plus Electron-compatible manifest.json and enabled/disabled mod files. No accounts/credentials/config copied in exports. Imports support old Electron manifest.json packs and intentionally ignore embedded config/profiles.config.json; never import accounts from those archives. Strict filename/entry whitelist, duplicate detection, ZIP/JAR validation, CRC and new-format SHA512,200file/512MB expanded limits, exact new-format Minecraft/loader match. Legacy format lacks version/hash metadata, so cannot claim those checks; user-selected local JARs still validated and selected loadout used. Different installed files cause rejection before commit; identical existing files and disable choices retained. Previously dismissed project imports disabled; Sodium add-ons disabled if Sodium absent; no auto-download. Native tests cover roundtrip/opt-outs/private-field exclusion, legacy import preserving local accounts, unexpected-path rejection and failed-update rollback. Initial full85tests75PASS10ignored0failed2.09s; final timeout/theme code rerun follows. New UI fixture tests update/pack command wiring and true-black surfaces. No player data changed by fixtures, no production/publication or live cosmetic writes.


Pure-black/update/modpack final verification before package: full85tests75PASS10opt-inignored0failed2.60s. Added one opt-in live_mod_update_roundtrip afterward; actual FerriteCore compatible older and latest Modrinth artifacts downloaded in disposable D: fixture, original hash registry verified, update committed, latest SHA512 matched, original bytes found in backup and unrelated Sodium opt-out retained: PASS3.87s. Current test inventory86 including11opt-in. No actual user mods changed and neither artifact was loaded into Minecraft; no gameplay compatibility claim.

MOD_UPDATE_PACK_AND_TRUE_BLACK_UI_PASS checks native update/pack IPC wiring and exact rgb(0,0,0) body/main/sidebar/selected-nav/installed-row/sort surfaces. New Home screenshot after inverted/masked image inspected: bright grey sky removed, black background with subtle silhouette. Existing Mods/details, Wardrobe slots and library/noTools regressions all PASS. Pure-black UI source now includes actual selected tabs/controls/dropdown option backgrounds, not just panel tokens. The full native signed Windows rebuild follows; old launcher closed only after confirming no Minecraft JVM active and verifying exact owned build executable path. No other app or JVM stopped.


### Final pure-black/update/modpack package (2026-11-04)
Signed Windows rebuild SUCCESS1m47s, NSIS9.53MiB; EXE/NSIS/signature exact SHA256 refreshed in output/native-package-hashes.json. Native actual-package updater verification PASS (authentic accepted, tamper/version-substitution rejected; same harmless hidden WebView unregister1412 on exit0). Separate disabled output/update.rust-draft.json refreshed only; site local/live update.json and GitHub/production untouched. Persistent signing key unchanged outside repo. Native app reopen follows; no installer executed and no live update channel activation.

Audit regenerated from current source:34native commands/33literal frontend calls/no orphans; reviewed90Electron workflow scopes now27Implemented55Partial7Missing1Unverified, NOT completion percentages. Core mod updates and pack import/export no longer marked absent; full OS dialog/gameplay acceptance remains pending. Last full test run75PASS10ignored2.60s; subsequently added opt-in live update test passed3.87s; new current inventory86/11opt-in. UI regressions Mods/details/slots/noTools and new update/pack/true-black control checks PASS. New Home screenshot visually inspected after eliminating white/grey image sky. Generated build artifact path remains D:/BatClientBuildCache/rust-launcher-target/release/bat-client-next.exe plus bundle/nsis installer/signature. All user worlds/accounts/mod files preserved.

Still NOT full Electron migration: Forge/NeoForge processing and broad native game/OS matrix; old protected nested renderer opt-out handling and general dependency/conflict reports; premium account token cleanup/remote heads/live cosmetic/animated multiplayer acceptance; DiscordRPC/language/custom window-title/crashReports runtime; background prepare/cache maintenance/update-result receipt and actual installed updater/macOS/Linux release validation. No Tools page should be reintroduced. Next work should target these remaining concrete gaps, not repeatedly call implemented mod details/slots/pack/update features absent. Native app startup observation must not be called visual/gameplay acceptance.

Reopened new Windows build PID1532; process responded with Bat Client Next development title. Historical PID: recheck before any later replacement. No native gameplay/visual-control session performed.


## 2026-11-04 — Wardrobe redesign and animated mod details
User explicitly disliked Wardrobe and asked for animation in mod details. Replaced Wardrobe markup while preserving all account/upload/model/apply/remove/tab/canvas/status IDs and native commands. New pure-black two-column layout: framed/sticky larger character stage with centred player label and Front/Back pressed-state controls; right-side underline Skins/Capes tabs, compact import target, model selector and apply/remove row, followed by named saved-look collection. Removed tall marketing drawer and verbose migration footer; cape still explains online sharing uses a still frame. Preview dimensions360x420 with stable existing zoom/camera policy; CSS keeps intrinsic aspect ratio. Responsive breakpoint at990px stacks stage/editor and uses two-column stage internals; no horizontal overflow at940px. PNG/GIF selection shows selected filename, reset on account/outfit load. Existing per-account saved slots/data/sync behaviour preserved, no backend changes.

Mod details:220ms CSS fade/translate/scale entrance, staggered content reveal, subtle gallery hover,140ms WAAPI close from actual current transform/opacity. Close button, Escape/cancel and outside-dialog clicks share close path; ignores duplicate close and late metadata response during closing, removes dialog on native close event. Reduced-motion OS preference or saved reduce-motion class disables new motion, including imperative close animation. Wardrobe panel/slot entrances and hover remain subtle and respect reduced motion. Pure #000 backgrounds retained, Tools remains removed.

UI regression Wardrobe/settings/version/launch PASS; saved-slot UI PASS; mod details/sort/exact-file UI PASS. New wardrobe-redesign-smoke: wide side-by-side black layout, camera pressed states, capes tab,940px no-overflow/stacking, animated details entry, Escape cleanup and reduced-motion no-animation all PASS. Images output/wardrobe-redesign-preview.png and wardrobe-cape-redesign-preview.png visually inspected; character fully framed and editor/collection controls visible. No native account, cosmetic, world or mod changes during tests. Backend unchanged: no unnecessary full Rust regression repeated; prior native verification remains scoped as recorded. Signed Windows package rebuild and signature/reopen results follow.


Wardrobe/details redesign final package: signed Windows rebuild SUCCESS1m58s, NSIS9.54MiB. Native actual-package signature/tamper/version-substitution smoke PASS exit0, same benign hidden WebView unregister1412 warning. Fresh hashes in output/native-package-hashes.json, current EXE FC790E343DD0B8181661403A2665B17277D128E1F248E76043A841CEBE49EAC2; NSIS BD5DED6FD520F4D9422E871484D2EA4DA5B48DE100B25B12BA92630688C36FBE. Separate disabled Rust draft refreshed only; no website/live update.json/GitHub/publish/install changes. Package paths unchanged on D:. Markup duplicate IDs, SVG references and local assets PASS. Native app reopen follows. Remaining full-migration limitations unchanged; do not mistake this UI-only task for completion of Forge/platform/integration work.

New redesign build reopened PID2176, responding with development window title. Recheck historical PID before future replacement. Native startup observation only; visual QA was done on isolated UI previews.


## 2026-11-04 — CURRENT RESUME CHECKPOINT: screenshot viewer, launch controls and startup errors

User interrupted implementation to require updating this canonical Rust document FIRST. This checkpoint records the current source, what really passed, and work that must resume. Do not restart migration from scratch or treat older initial-handoff paragraphs as current completeness claims. No additional implementation or packaging was performed for this documentation request; a previously started Cargo run was collected to record its actual result.

### Active request and agreed appearance

Add a mod screenshot viewer, actual accessible Launch buttons, better failure handling when Minecraft does not open, animations, and continue every remaining Electron feature. Last completed prior UI task was redesigned Wardrobe and animated mod details. Keep the approved Home layout and pure black surfaces, including selected controls, dropdowns and dialogs. Tools page is intentionally removed; retain ordinary backend helpers used by Settings/Loadouts. Use SVG UI icons. Installed mods use enable/disable switches, red removal and subtle upward entrances. Preserve user removal/disable choices; no silent renderer reinstallation. Broad parity is still unfinished; never say all Electron features moved merely because source compiles.

### Current source-only changes (not yet in packaged app)

- `launcher-rust/ui/mods.js`: new modal screenshot viewer from mod details gallery. Accessible thumbnail buttons, title/count, previous/next buttons, Left/Right keyboard navigation, actual-size versus fit-image zoom, loading/error feedback, no image drag ghost, reduced-motion-aware entrance/image/close motion. Escape closes viewer; parent-details close cleans up child viewer/listeners/animations. Native gallery remains restricted to official HTTPS Modrinth CDN URLs, max8. No screenshots gives an explicit empty state. Runtime UI acceptance still pending.
- `launcher-rust/ui/app.js`: each loadout card gets a separate Launch button calling `BatLaunchFor(id)`; persists selected loadout before launch, does not nest a button or silently change loader/version. Existing Edit/Copy/Favorite/Remove preserved.
- `launcher-rust/ui/game-launch.js`: shared real launch IPC from Home, loadout cards and a bottom dock on other tabs; dock also remains visible during startup/running/failure on Home. Progress comes from native preparation/startup state, with animated busy indicator and no fabricated running completion. Prevent concurrent requests; unsupported Forge/NeoForge explicitly unavailable rather than retagged. `BatLaunchFor` selects first then calls `BatLaunch`. Dock View launch log opens normal console. Structured error modal has actionable remedies, Retry, Settings, Mods, Open logs folder, Copy details and expandable bounded redacted log. Failure IDs deduplicate poll/catch dialogs. Last requested server preserved for retry. JS syntax checked, UI fixture execution still pending. Initial replacement-character text in this new file was corrected; inspect final visible punctuation during UI QA.
- `launcher-rust/ui/styles.css`: new pure-black viewer/dock/failure UI and loadout Launch styles, responsive dock positioning, spinner/entrance motion and reduced-motion overrides. Needs screenshot/overflow QA, including minimum940px window.
- `launcher-rust/ui/library-extras.js`: console now reads `launch_diagnostics` for current owned launch instead of arbitrary generic log; exposes `BatOpenConsole` for dock.
- `launcher-rust/src-tauri/src/launch_errors.rs` NEW: structured camelCase Failure DTO (id/code/title/detail/remedies/logName/excerpt/exitCode). Classifies integrity, mod conflict, Java version, JVM arguments, memory, graphics, missing libraries, account, network, unsupported loader or generic startup failure. Memory classification precedes generic Java-VM creation errors. Bounded last64KiB log, drops partial first line before redaction; credential/token/Authorization redaction before UI/copy/local report. Real JVM invalid-option opt-in test exists but not yet executed. Uses Windows CREATE_NO_WINDOW for that test child.
- `launcher-rust/src-tauri/src/game_launch.rs`: status includes optional Failure; owns current log path in mutex. Registered async `launch_diagnostics` returns current redacted excerpt/failure only, no renderer-provided arbitrary file paths. Preparation errors produce structured status. Child monitor captures exit code, treats early clean exit before renderer readiness as failure, restores launcher on failure, keeps it open despite auto-close-on-exit preference. Auto-close applies only after a successfully opened session. Slow startup after60s shows a taking-longer/log message, without killing game or claiming failure. Actual renderer readiness policy remains log-based; this is not gameplay/visual proof. ACTIVE/child/game-lock lifetime preserved.
- `launcher-rust/src-tauri/src/main.rs`: module + command registration for launch diagnostics.
- `launcher-rust/src-tauri/src/tools.rs`: log tail delegates to shared bounded/redacted reader; unused Seek imports removed.
- `launcher-rust/ui/settings.js`: crashReports label is now “Save local launch error reports”. Native saved preference writes atomic local JSON under `rust-launch-reports/<failure-id>.json` on failure only when enabled. No report upload. This preference is no longer merely saved-only; parity audit still needs updating.
- `launcher-rust/scripts/mods-smoke-preload.cjs`: fixture mod now includes2 screenshot images from existing embedded fixture icon; `launch_diagnostics` fixture added. `--launch-failure` produces a structured JVM_ARGUMENTS failure after preparation; normal fixture still progresses to running. This preload has a VERY LARGE embedded base64 PNG: do not dump whole file into tool output.

### Verification actually completed

Latest `cargo fmt` completed. Latest locked optimized test command:
`cargo test --locked --release --features custom-protocol --manifest-path launcher-rust/src-tauri/Cargo.toml`
Build51.47s;90 tests total, **78 passed,0 failed,12 opt-in ignored**, execution4.53s. Collected successfully after the user interrupted the preceding turn. Current test exe `D:/BatClientBuildCache/rust-launcher-target/release/deps/bat_client_next-6238b80cc0e8b117.exe`. Includes classifier examples, memory precedence, credential redaction, bounded partial-line log tail; existing preservation/opt-out/transaction/updater/game-lock/JVM tests passed. No ignored native Java/game/live-network tests ran in this batch. New mods/game-launch/library-extras JavaScript syntax checks exited successfully. Previous initial90-test run78PASS12ignored3.60s was superseded by this4.53s result.

New screenshot-viewer and launch-error UI fixture tests have NOT run yet. No actual new Minecraft launch, gameplay, visual native test, package rebuild, installation or publication for this batch. User worlds/accounts/credentials/mods untouched by these tests.

### Last valid package versus working source

Current working source changes above are ahead of last signed package. Last signed Wardrobe/details package: EXE SHA256 `FC790E343DD0B8181661403A2665B17277D128E1F248E76043A841CEBE49EAC2`; NSIS `BD5DED6FD520F4D9422E871484D2EA4DA5B48DE100B25B12BA92630688C36FBE`; signature `632A4236F1505691A127651F6320A6F6BA93D87ACE10868C7E29EEF17AB0593B`. Hash file `launcher-rust/output/native-package-hashes.json` last observed2026-11-04 20:47:45. Package EXE `D:/BatClientBuildCache/rust-launcher-target/release/bat-client-next.exe`; installer under `release/bundle/nsis/Bat Client Next_0.1.0_x64-setup.exe` plus `.sig`. Last opened package PID2176 is HISTORICAL: verify exact executable/current status before replacement. Cargo test exe is not the main packaged launcher.

Persistent private signing key stays outside repo at D:/BatClientPrivate/rust-updater/bat-client-next.key; never print/read/regenerate it. Existing public-key signature/tamper/version-substitution check passed for prior package. Actual installed update/download/restart not yet accepted; macOS/Linux native bundles not tested. Rust channel remains disabled/unpublished, separate `output/update.rust-draft.json` only. Do not modify production/site update.json or claim legacy Electron assets are Rust packages.

### Exact next work after documentation

1. Add/run isolated Electron development UI fixtures for screenshot open/nav/keyboard/zoom/Escape/parent cleanup/reduced motion; loadout-card selection + single native launch/progress; structured failure retry/actions/log/deduplication; dock on non-Home pages. Existing Electron harness is development-only, no runtime sidecar. Run relevant existing mod details/Wardrobe/launch regression fixtures. Use Start-Process -Wait -PassThru for Windows GUI test runner with redirected stdout/stderr; plain invocation can return before completion.
2. Explicitly run ignored real-Java invalid-option diagnosis test; scoped failure only, no user settings/JVM mutation. If a native Minecraft test is appropriate, ensure no existing game and use a disposable D: world/game directory. Existing native_cached_launch_smoke copies current user mod JAR bytes into its fixture, so it is not a controlled minimal-mod baseline. Log renderer/agent readiness is not visual/gameplay acceptance. No PC/game-control tools per user preference.
3. Update `scripts/audit-parity.py`, PARITY-AUDIT.md and generated JSON: old “four saved-only” setting statement must become three (Discord RPC/localization/window title); crashReports now local launch-report consumer. New command launch_diagnostics changes source command counts; regenerate, do not invent counts. Prior90Electron workflow categories27Implemented55Partial7Missing1Unverified are historical and NOT completion percentages.
4. After UI/native checks and source freeze, rebuild signed Windows NSIS using existing D: toolchain/key, refresh hashes, verify actual package signature/tamper/version substitution, refresh only disabled Rust draft and reopen exact owned app. Stop only owned exact build executable after confirming no Minecraft JVM. Never terminate other apps/user games.
5. Continue real parity gaps: Forge/NeoForge official installer processing/native acceptance; protected nested renderer opt-outs and general dependency/conflict diagnostics; premium account cleanup/remote heads/live online cosmetics/animated multiplayer acceptance; Discord RPC/localization/custom window title; background preparation/cache maintenance/post-update receipt; real updater installation and Windows/macOS/Linux platform/game matrix. Full fresh game/Java installs and core Fabric/Quilt source are no longer absent, but scoped tests do not prove every version/platform.

### Operating constraints retained

D: for Cargo/npm/TEMP/build fixtures because C: is constrained. Never clean unfamiliar directories, user worlds, configs/accounts/keys, protected payloads, packages or other projects. No production/site/GitHub/SSH changes or messages to others authorized. No proactive subagents. No CUA/PC-control/game-control tools for this work. Native launching allowed, but report testing limits honestly. Do not bypass prior approval rejection by changing shell/tool. Read exact paths and native process ownership before any future move/delete/replacement. Keep this document current at each material checkpoint.


## 2026-11-04 — screenshot viewer and launch-error acceptance update

Completed isolated UI fixture `scripts/launch-gallery-smoke.cjs`: gallery thumbnails open viewer;2image counter/Right-arrow/actual-size zoom; Escape returns to parent; reduced-motion disables viewer entrance; closing parent cleans viewer. Loadout card calls actual shared native command once, persisted selected loadout, shows preparation progress/busy state, then disables launch while running. Separate --launch-failure fixture verifies structured JVM error/log/remedies/Retry visibility, deduped modal and Settings recovery action. Both PASS. Screenshots `output/mod-screenshot-viewer-preview.png` and `output/launch-error-preview.png` inspected: pure black, controls visible and no visible overlap at1320x900. Fixture uses Bat logo as image; it proves viewer behavior, not live screenshot CDN delivery. Existing mod-details/sort/exact-file, Wardrobe redesign/responsive/motion and Wardrobe/settings/version/launch UI regressions PASS.

Explicit actual-Java invalid JVM option test PASS0.15s (real Java21 child hidden, no player config writes). Full suite remains78PASS12ignored4.53s; opt-in test separately run. Native cached1.21.1 test FAILED38.66s: copied Fabric API0.102.0+1.21 correctly rejects1.21.1. Evidence preserved D:/BatClientBuildCache/native-rust-game-4Yllqf/game.log. No user mod changed or incompatible JAR retagged. Matching version1.21 disposable run then PASS42.28s, renderer/agent initialization detected40752ms. Evidence D:/BatClientBuildCache/native-rust-game-mPzSJP/game.log. Only own disposable menu-only child stopped; no player world opened. This is native startup/log evidence, not gameplay/visual/performance certification.

Audit generator updated crashReports consumer and latest evidence; regenerated35native commands34literalfrontend calls/no unregistered calls;90Electron workflows27Implemented55Partial7Missing1Unverified unchanged.3visible saved-only settings remain (DiscordRPC/localization/custom title). These are scoped workflow categories, not completion percentages.

Signed Windows rebuild started after confirming no Minecraft JVM and closing only exact owned D: build launcher PID2176. No source modifications after build start except handoff notes. Build/signature/hash/reopen results follow. Do not claim package includes this batch until confirmed.


Screenshot viewer/launch-errors final Windows package: signed rebuild SUCCESS (optimized native2m38s, NSIS9.54MiB); actual package signature/tamper/version-substitution native check PASS exit0. Same benign hidden WebView unregister1412 teardown warning. No installer executed and no public channel enabled. Fresh package hashes in output/native-package-hashes.json: EXE 6E97C26E074A2D956F52FA78C3B0FB04D25C54BB6344EB4DD2B31FA6146EFE2E; NSIS BFB79E080716B81B0247CBCF5C7E914C18F24F256BC8C255D76EBB76C66C3FB4; signature 9DD76E5B1D9E7DFD2E2A84154559B621491CEF1B9E1823612EF28814FADC37AB. Separate disabled draft refresh and owned app reopen follow; production/site/GitHub untouched. Local HTML IDs/references checked; current CSP allows only self/data and official Modrinth CDN images, preserving native gallery allowlist. Source changes in screenshot viewer/launch controls/diagnostics now included in this package. Full Electron migration limitations remain as above.

Disabled draft refreshed successfully; native package reopened after signature check. Historical PID should always be rechecked before future replacement. No native visual/gameplay control performed; UI screenshots and scoped native renderer/agent test are the evidence.


## 2026-11-04 — User correction: launch controls ONLY on Home
This overrides the prior extra launch controls. Removed bottom launch/loadout dock from all pages and per-loadout-card Launch buttons. Home retains selected-loadout dropdown, Launch Minecraft button, true progress/spinner and structured failure recovery. Loadouts retain select/edit/copy/favorite/remove. Removed BatLaunchFor/dock page listeners/dock CSS and spacing. Updated launch-gallery fixture to assert no dock/card Launch and invoke Home button. Screenshot/Home-only normal launch fixture PASS; failure/recovery fixture PASS. Reduced-motion CSS cleaned after dock removal. Backend unchanged. Full migration remains broader active task; do not reintroduce these controls during parity work. Package rebuild underway; initial note/CSS command had wrong relative workdir, immediately corrected with absolute paths. Ensure packaging includes final corrected CSS.

Home-only launch correction final: UI source/smoke/CSS balance PASS; native signed Windows build SUCCESS2m14s, NSIS9.55MiB; actual package updater crypto smoke PASS. Fresh hashes in output/native-package-hashes.json; EXE AAA12A9D16A0CB48C4CD7B190E74163F3F2502FE9D287943A30F00E79EAF54AC. Disabled draft refreshed only; no production changes or installer execution. Native app reopening. No backend changes or new native gameplay test needed for this removal. Audit regenerated35native34frontend calls/no orphans.


## 2026-11-04 — Full migration continuation: official Forge-family installers, account cleanup, localization (SOURCE ONLY)

Home-only launch user correction remains mandatory. No new dock/card Launch controls. Current source is ahead of packaged EXE, last package still Home-only batch. Main active work is full Electron migration; do not call it complete.

NEW forge_install.rs + main module/game_install dispatch: official Forge/NeoForge Maven metadata, exact MC prefix and stable/numeric release selection (not metadata-order guessing), SHA1-verified installer JAR, embedded install_profile.json and version JSON target validation. Official Java installer --installClient runs in own .bat-forge-install-* tempdir inside game root, skeleton launcher_profiles.json only, verified vanilla files/libs/index copied into stage. Does NOT run installer against player launcher profiles/mods.600s own child timeout, bounded redacted installer diagnostic saved under game/bat-launch-logs. Validates full staged resolver before commit; plans all libraries/versions only, rejects symlink/reparse/different existing artifact, create_new additions only. Player profiles/mods/accounts never copied by installer; same existing bytes preserved. Map published only after output resolve. Legacy install-profile-v1 (no json) and NeoForge1.20.1 still explicitly unsupported; do not claim all old versions. Modern Forge/NeoForge frontend NOT yet enabled until native checks done. Resolver now has resolve_selected and supports Forge/NeoForge with loader-coordinate validation, no retagging.

Native actual official Forge1.21.1 installer52.1.21 install+resolve PASS302.48s in D:/BatClientBuildCache/rust-forge-install-i8scqq. Fresh game files downloaded;1520 immutable asset objects hardlinked from existing own D: clean-install cache to avoid redundant network traffic, hash verification still executed. No user files altered. Unit tests for exact stable numeric metadata selection and conflict/player-profile preservation passed. Initial module inserted above Rust inner attribute caused compile error; fixed before tests. Full interim94tests80PASS14ignored0failed3.28s (before final Java-path fix).

Added opt-in native_forge_launch test restricted to direct D:/BatClientBuildCache/rust-forge-install-* fixtures. Adds only fixture offline identity and own copied agent JARs; own run-UUID folder, no user mods/worlds; prepare_for actual pipeline + Bat/authlib agents. Forge1.21.1 renderer initialization PASS22429ms (test23.98s), log run-ae45dc38-9e29-4280-a8ce-3e89beb334c9/game.log inside fixture. Stops only own child. Native logs, NOT gameplay/visual acceptance. NeoForge installer initial run FAILED9.43s: Java could not load installer main class because canonical Windows \?\ path passed to -jar. Corrected Java path normalization for installer/staged-game args and game classpath/native/assets/library/run paths; rerun compilation/test currently active (exec session61191). Must collect actual outcome and inspect preserved diagnostics before enabling frontend/claiming support.

Premium account removal: removed temporary native+UI restriction. Metadata action atomically removes selected account with active fallback, keeps skins/capes and unrelated account fields. IPC snapshots native rustAuthId, removes own OS namespace credentials only after successful metadata removal and only if no remaining account references same ID. Legacy stores untouched. OS failure explicitly reports account removed but credential cleanup incomplete (no false complete-cleanup claim). remove_credentials checks NoEntry as success. Extended real native secure-storage roundtrip to exercise new removal: PASS0.03s synthetic random disposable ID; no actual user token removed. UI remove now enabled for Microsoft cards. Need ensure UI reload on cleanup-warning path and finish broader premium live acceptance.

Localization: copied existing lang/translations.js verbatim to Rust UI; new localization.js loaded after controllers/dictionaries. Applies selected saved language en/es/fr/de/pt/ru/tr/ar to owned marked navigation/settings labels/page headings only, original text WeakMap allows reversible switching; fallback English for unmapped new Rust text. Does not translate account/loadout names, remote mod content or logs. Attributes limited to marked owned labels, no HTML injection. MutationObserver processes newly recreated marked nodes; setters avoid repeated DOM mutation. Settings load/save invokes language setter; bootstrap reads saved preference. This is source-language consumer, NOT full new Rust string coverage or full RTL layout parity.

New isolated localization-accounts-smoke PASS: Spanish Home label, revert English, Arabic document language without changing Test loadout, Microsoft card Remove enabled and correct account_action IPC. No real account deleted. Native full test rerun, NeoForge actual launch, regression checks, audit regeneration and signed package still pending. Keep all staged/test logs, user files/private keys/packages. No deployment, GitHub, live update.json or production modifications. No PC/game-control tools used. Continue current task instead of repeating UI redesign or saying all parity achieved.


## 2026-11-05 — Migration verification and package checkpoint

- Modern Forge and NeoForge are enabled in the normal Home launch path. Forge 52.1.21 and NeoForge 21.1.265 both passed actual official install, resolution and renderer startup with Bat/authlib agents on Windows with Minecraft 1.21.1. NeoForge needed normalized Java paths, FancyModLoader metadata recognition and the raw client filename in the bootstrap ignore list to prevent duplicate `minecraft` modules. Official NeoForge install/resolve passed 83.97s after the metadata fix. Corrected renderer startup passed 17.35s. Final title/readback run reached renderer and title verification in 16.66s, evidence `D:/BatClientBuildCache/rust-forge-install-i8scqq/run-fac6d844-aa8f-478f-98ab-49826cc15875/game.log`. Forge also passed a second staged install with reusable artifact caches. Legacy Forge installer format and NeoForge 1.20.1 still need separate handling. Native tests were startup-only in disposable fixtures. An earlier title assertion ran before cleanup and left one test JVM. Verified its exact fixture command, stopped only that process, then moved assertion after child cleanup and added a bounded title wait.
- Windows game title is applied to the owned visible GLFW/LWJGL child by PID from the game monitor. Bounded WM_SETTEXT call and title readback succeeded. Empty setting preserves the Minecraft title. macOS/Linux title support remains pending.
- Native Discord Rich Presence uses discord-rich-presence 1.1.0 with the existing app/asset. Background worker reads saved preference and launch stage, reconnects with a delay and clears on disable. Uses official Bat Client website/Discord redirect. Fixed activity states avoid sending usernames, servers or log details. Live FPS/ping/server telemetry and actual Discord client acceptance remain.
- Sodium opt-out for old Fabric/Quilt Bat packages is honored before launch. Only Bat archives with embedded Sodium are changed. Original checksum-named backup is retained and other payload entries are verified by digest. Signed archives and hard dependencies fail with a clear replacement message. Sodium Extra and Reese's settings are moved to disabled files with a collision guard. Fixture tests verified payload bytes, original backup, idempotence, refusal cases and add-on behavior. No player package was modified during verification.
- Added Microsoft removal and verified native credential cleanup with a synthetic keyring entry. Language preference uses reused Electron dictionaries for marked owned labels. New strings can fall back to English and the full RTL layout remains pending.
- Update result persists from/to/stage/time after verification and before installation. On next startup the running binary version determines applied versus incomplete. Failed same-version automatic updates wait for manual retry. Settings displays last result. Unit tests verify result mapping. Real installed update/restart has yet to be exercised.
- Loadout editor opens the validated shared game folder. Mods retains its folder action. Windows Settings → Files & Network clears only WebView2 disk cache via profile API and completion callback. This avoids clearing storage and cookies. Windows API compiled, and fixture checked button wiring. Real cache effects and other OS behavior remain pending.
- Home remains the only place with the loadout selector and Launch Minecraft control. Screenshot, launch progress, launch errors, Wardrobe and localization/account/folder/update/cache fixture checks passed. Updated an obsolete UI regression assertion to test a genuinely unsupported loader after enabling Forge.

Latest native suite: 100 tests, 86 passed, 14 opt-in skipped, zero failures, 3.61s. Test binary `D:/BatClientBuildCache/rust-launcher-target/release/deps/bat_client_next-fb5a366a65ed36e4.exe`. Final signed Windows package build underway, tool session 17628, persistent build output `D:/BatClientBuildCache/rust-migration-package.log`. Collect result, verify package signature, refresh local hashes and disabled draft, then reopen the owned launcher. Keep the release channel unpublished.

Remaining migration work is tracked in PARITY-AUDIT.md. Prioritize background pre-download with safe cancellation, broad mod compatibility checks, older loader formats and version matrix, remaining premium/legacy wardrobe and live Discord behavior, real updater installation and native macOS/Linux acceptance. Review existing supported workflows before implementing any replacement. The Tools tab stays removed and launch controls stay on Home.

Signed package finished 2026-11-05. Optimized native build 1m34s and NSIS 9.66MiB. Actual signature, tamper and version-substitution verification passed. Latest executable hash CA3703DE18717B76FC6E2683AE1B6E9442092FE4D5EEB836871B1288385577F0. Installer hash D3D55DC18F7879F123E4F82B5168D50C91D74D13BC3EE07C8D2A660218C1A53C. Audit now 37 native commands and 36 literal frontend calls with zero unregistered calls. Scoped ledger 27 implemented, 61 partial, 1 unverified and 1 missing unused legacy instance path helper. These counts are not completion percentages.
Disabled local draft refreshed. The new launcher reopened and responded as PID 14604. This PID is historical. Actual updater replacement and release publication remain pending.

 
## 2026-11-05 — Background preparation and mod compatibility

The Home launch controller queues preparation after the startup screen clears and when the selected loadout changes. A short debounce limits rapid selection requests. Native preparation downloads and verifies shared vanilla client, libraries, native archives and assets with the existing downloader. It uses at most two workers and a 12-second timeout per request. Loader setup and Java provisioning remain in the foreground launch path. Preparation uses the existing game-file lock. Foreground launch and update cancel the worker and let in-flight I/O finish before taking that lock. Verified completed files are reusable. Staged incomplete downloads are discarded by the existing temp-file guard. The Home caption reports preparation or foreground fallback and the Launch button stays available.

Installed Mods now has a read-only compatibility check. It reports enabled-JAR loader metadata mismatches and common conflicts. The confirmed Kind's Crystal Optimizer 1.6.3 / Minecraft 1.21.11 Fabric networking mismatch blocks launch with a disable instruction. The scanner does not automatically disable or replace mods. Full dependency ranges, nested mod discovery and binary class compatibility analysis remain partial.

102 native tests ran with 88 passed, 14 opt-in skipped and zero failed in 4.09s. New coverage verifies foreground cancellation precedes lock acquisition and that the known mod guard only blocks the matching enabled mod. The combined isolated UI fixture passed preparation, compatibility, account, language, folder, update result and cache wiring. Live cold pre-download timing remains unmeasured. Signed Windows package build underway. Persistent build log at D:/BatClientBuildCache/rust-preparation-package.log. Full migration still needs legacy loader support, deeper compatibility diagnostics, remaining localization and live integration/platform acceptance.

Signed preparation and compatibility package built successfully. Native compilation 2m32s. Actual updater signature verification passed. Executable hash 73F2B2769B534856D0C23187E01A0732500496FF4D7E3C198DE2CF688D9CCC9B and NSIS hash 1033E5825EC47A59CEAB6ECAA12300D0F685D0CD68D588C9DD9629D28DDA16DF. Parity audit regenerated with 39 registered commands and 38 literal frontend invokes, all registered.
Disabled local updater draft refreshed. Reopened owned build as PID 4664. Recheck process ownership before later replacement.

 
## 2026-11-05 — Legacy Forge client installation

Rust recognizes Forge install-profile v1, verifies the Minecraft and loader identities, extracts the embedded universal JAR and checks library downloads against the installer checksums or repository sidecars. It writes verified artifact hashes to the staged manifest and uses the existing guarded installation commit. Legacy installation does not need the old graphical Java installer. Modern installation keeps its official Java installer path. The shared downloader and resolver now handle native-only classifier libraries without requesting nonexistent ordinary JARs.

Windows test fixture D:/BatClientBuildCache/rust-forge-install-KOhTuQ verified official Forge 1.8-11.14.4.1577 and 1.8.9-11.15.1.2318-1.8.9. Install and resolve passed in 25.49s and 58.77s respectively, with shared cache files reused. Java 8 was installed via managed Adoptium provisioning. Renderer initialization and owned game title readback passed with Bat and authlib agents in both tests. Startup-and-title timing 12.896s for 1.8 and 9.177s for 1.8.9. Logs are in run-a213f3f6-3fd9-4bce-a096-9a10433ac42c and run-c39cb70d-fb4f-4f46-bfe2-51f71540123b. Both tests closed their own children. This is launcher startup coverage. Gameplay, companion mod ports, other legacy releases and other platforms still need independent validation.

Rust regression passed with 88 successful tests and 14 opt-in skips. Latest package build log D:/BatClientBuildCache/rust-legacy-forge-package.log. Keep the signed updater draft disabled. Next scope includes NeoForge 1.20.1, legacy Fabric launch acceptance and the gaps in the parity audit.

Signed legacy Forge package built successfully and updater signature verified. EXE AE41021C5053B4B56C37AE68696FCBAB9A2560612881BD6B6F829649F0F5CE7E. NSIS 270D77BEA020407E1052C5966CA3AB60879664E684AD847A3B7E8AF093B689FC. Both FML logs emitted a nonfatal missing-signature warning. Jarsigner reports the official 1.8.9 embedded JAR uses a disabled weak signing algorithm. Its installer checksum was verified and the JAR was extracted unchanged.
Disabled local draft refreshed. Reopened signed legacy Forge build as PID 14192. Recheck ownership before any replacement.


## 2026-11-05 — NeoForge 1.20.1 and Legacy Fabric 1.8/1.8.9

- Added NeoForge 1.20.1's official transitional Maven coordinate and separated its installed profile ID from Forge. Official install/resolve succeeded in 189.07s in `D:/BatClientBuildCache/rust-forge-install-KOhTuQ`; actual Windows renderer startup and title readback with Bat/authlib agents passed in 25.334s. Game log: `D:/BatClientBuildCache/rust-forge-install-KOhTuQ/run-4287a661-4878-4f60-bfac-26983faa0375/game.log`. This is client startup evidence, not general modpack or gameplay compatibility.
- Legacy Fabric 1.8 and 1.8.9 install/resolve and renderer startup with Bat/authlib agents now pass. The official Maven `maven.legacyfabric.net` redirects checksum sidecars to `repo.legacyfabric.net`; added only this exact official repository host to the HTTPS allowlist. Both versions installed in the isolated D: fixture `D:/BatClientBuildCache/rust-forge-install-legacy-fabric`, reused verified immutable artifacts, used managed Java 8, and reached the renderer with title readback. Startup test times: 10.230s (1.8) and 10.957s (1.8.9). Logs: `D:/BatClientBuildCache/legacy-fabric-1.8-verified-install.log`, `legacy-fabric-1.8-verified-launch.log`, `legacy-fabric-1.8.9-verified-install.log`, `legacy-fabric-1.8.9-verified-launch.log`; exact game logs are under the fixture's `run-*` directories. An earlier missing-checksum failure was fixed by accepting the official redirect host, not by skipping verification.
- Latest full native suite after these changes: 88 passed, 14 ignored opt-in tests, 0 failures; log `D:/BatClientBuildCache/rust-full-parity-regression.log`.
- `launcher-rust/scripts/audit-parity.py` and generated `PARITY-AUDIT.md` now include these validations. Electron-to-Rust feature migration remains **incomplete**: platform updater replacement/recovery acceptance, full cosmetic lifecycle/gameplay, remaining mod details/dependency checks, full localization/RTL and notification/Discord acceptance, broader game-version/loader/platform matrix and native macOS/Linux release validation are still open. Keep Electron available; do not publish or claim full parity.
- Signed Windows package rebuilt successfully after the source/audit changes: EXE SHA256 `AA2993D6B9EFAE80DE57F195F175D9FE1CE00D42956AE557E879E03A369F95E9`; NSIS SHA256 `0E3BC78080B790309C6EE590DB6D8E64BB9273334521A0F829E5A949AF336FA1`; signature-file SHA256 `EA36F67EED1A53385D1C8B598C2996A6D0C7A1B5339F15FDFEDA9D17EFF0D6EA`. Native package signature/tamper/version-substitution smoke passed. Hashes: `launcher-rust/output/native-package-hashes.json`; package log `D:/BatClientBuildCache/rust-neo1201-legacyfabric-package.log`; local updater draft only, `launcher-rust/output/update.rust-draft.json`, refreshed and still unpublished. Reopened exact owned build PID 2616, title `Bat Client Next · Development`, responding. No Java/Minecraft process, user game, account or world was modified.

## 2026-11-05 — Home and premium texture migration

- Restored the Electron server list on Home using the original bundled logos and addresses. Each card copies its address or joins through the current loadout using the existing native quick-join path. Check status issues bounded TCP status requests on demand. Preview mode and unavailable launch states disable joining.
- The Home changelog offers previous and next navigation for up to 12 releases. It validates entries, renders text only, supports the earlier single-entry cache, and retains the selected release if a refresh fails.
- Recent launches uses the existing shared history and reads both Electron ISO timestamps and Rust timestamps. Rust records a destination for server launches and validates the destination before starting installation.
- Missing premium skins and capes can be fetched using a saved UUID from Mojang's public profile and texture services. Responses are bounded, redirects are disabled, and only official HTTPS texture URLs are fetched. Saved cosmetics take priority. Wardrobe labels the public cape and keeps local remove and collection actions tied to local files.
- Full Rust regression: 88 passed, 14 opt-in skipped, 0 failed. Existing Wardrobe, settings, versions and launch UI fixture passed. A separate Home fixture verified older/newer release controls, offline cache behavior, five server cards, disabled preview joins and the history panel. Live partner connections, clipboard and premium lookup remain unverified. See the parity audit for the rest of the migration.
- Windows NSIS package rebuilt with the Home and cosmetic changes (10.36 MiB). Native signature verification smoke passed. Updated `launcher-rust/output/native-package-hashes.json` and the disabled local updater draft. Development window reopened and responded as historical PID 13568. The previous package is preserved in `D:/BatClientBuildCache/rust-package-before-home-7e3dd85f6e5c4e8f9da22f32fd817ff9`.

## 2026-11-05 — Partner cards, presets and legacy GIF capes

- Home partner cards separate server identity, address, on-demand status and Join/Copy actions. Recent launches was removed from Home as requested. Shared launch history recording remains available to the backend.
- Launch presets live in Loadouts and mod presets in Installed Mods. Both can read, add, update, apply and remove entries in the shared Electron preset files. Mod presets recognize legacy disabled filenames, validate missing files and known Sodium add-on requirements before moving anything, and roll back completed filename changes if a move or opt-out write fails.
- Equipping a legacy GIF-only cape slot now generates bounded PNG frames and stores their timing without overwriting the source GIF. The same staged file handling used for direct GIF imports cleans up generated frames if the config commit fails.
- The local Home fixture passed at 940x660 with all five partner cards, no card overflow, no Recent launches, and preset entry points in the expected tabs. The Rust regression includes focused tests for legacy GIF slots and disabled preset entries.
- Final Rust suite: 90 passed, 14 opt-in skipped, zero failures. Isolated preset UI fixture passed create, update, apply and delete for both preset types. The signed Windows installer (10.37 MiB) passed native updater verification. The local updater draft and package hashes were refreshed, and the new development launcher reopened as historical PID 10284. Full Electron parity remains open in PARITY-AUDIT.md.

## 2026-11-05 — Java inventory and legacy launcher metadata

- Settings checks Java 8, 11, 16, 17 and 21 on demand, reusing executable probe results within each scan. Missing versions can be installed through the existing verified runtime installer. Settings also shows system memory and the effective shared game folder.
- The loadout editor can open an existing Electron instance folder after validating the loadout, path and containment. It does not create a legacy folder or change the launch location.
- Installed Mods shows legacy managed flags from managed-mods.json. Mod details show a compatible release count for the selected loadout.
- The isolated UI fixture passed Java inventory and install command routing, effective folder display, existing-instance open routing and managed-mod label rendering. Native legacy folder coverage checks present, missing and invalid profile IDs.
- Final native regression: 91 passed, 14 opt-in skipped, zero failures. Signed Windows NSIS package rebuilt at 10.38 MiB and passed native updater signature smoke. Local hashes and disabled updater draft refreshed. Development launcher reopened as historical PID 14216. Previous package kept in D:/BatClientBuildCache/rust-package-before-java-instances-39f54bafe279449b9e8c5d7c09fb2e2d.

## 2026-11-05 — Settings scope

The user removed the Minecraft screenshot gallery from Files & Network and the crash analysis, game health and FPS benchmark controls from Performance. Keep those controls out of Settings during migration. The existing launch error dialog and optional game console remain available.

## 2026-11-05 — Collection import, account refresh and launcher parity

- Wardrobe imports batches of PNG skins or PNG/GIF capes into free shared collection slots. It uses bounded image decoding and stages all assets before committing the collection. A failed batch cleans up staged assets. Equipped cosmetics, old slots and account fields are preserved.
- Accounts exposes manual Microsoft session refresh. Native refresh verifies UUID and credential reference under the library write lock. Refreshing another account keeps the active account selection. Mocked UI routing passed. Live credential refresh still needs an authorized account run.
- Mod compatibility now reports duplicate top-level Fabric IDs and exact Minecraft release mismatches declared in Fabric metadata. General dependency predicates and nested binary validation remain separate.
- Arabic now sets document RTL direction. Additional owned interface labels reuse the existing Electron dictionaries. Discord idle presence reflects allowlisted launcher tabs. The user’s Settings exclusions are preserved.
- Rust suite: 92 passed, 14 opt-in skipped, zero failures. Focused Wardrobe and localization/account UI fixtures passed batch import wiring, manual refresh wiring and Arabic direction.
- Built the signed 10.41 MiB Windows installer in D:/BatClientBuildCache/rust-launcher-target-collection/release/bundle/nsis. The packaged updater verification smoke passed. Updated local hashes and the disabled updater draft. The existing launcher build and active Minecraft session were left open.

## 2026-11-05 — Launcher visual polish and screenshots

- Home omits the Partner servers section and its subtitle per user request. Standard launcher labels and cards cannot be drag-selected. Form inputs, logs and long mod descriptions remain selectable.
- Loadout actions use accessible Edit, Copy, Favorite and Remove icons with compact spacing. Saved skins and capes use rows with pixel previews, slot counts and an Equipped state. The cape thumbnail crops the visible cape surface. Page and row transitions respect reduced motion.
- Captured seven 1440×1000 PNGs from the actual launcher frontend with isolated sample data in launcher-rust/output/launcher-screenshots. The capture checked selection behavior and button overflow at 940px width.
- The revised Windows NSIS installer was built in the staging target at 10.40 MiB and passed native updater signature verification. Local hashes and the disabled update draft were refreshed.

## 2026-11-05 — Flying Bat and private native packages

- Flying Bat is an original procedural pixel sprite on the Home artwork. The renderer caches eight tiny canvas frames and animates one CSS transform. No asset network calls or new dependencies. The layer is click-through and Home-only. Its flight zone is calculated from the title, hero and launch control bounds.
- The state model lives in ui/bat-flight.js. Adjust travel speeds in step(), transition timing and chance in enter()/step(), and CSS sprite dimensions in ui/styles.css. Render scale, pointer radius and the optional six-particle pool live in ui/flying-bat.js. Keep measured padding in sync with size changes.
- Settings → Appearance → Flying Bat applies controls immediately and writes appSettings.flyingBat via the existing native settings lock. Defaults are enabled, mouse reactions enabled, hanging enabled, particles disabled and normal speed. Reduced motion uses a still sprite. Hidden, off-page and unfocused windows stop the animation frame loop.
- Node movement tests passed for 30/120 FPS, hang/takeoff, pointer avoidance and resize. The isolated UI fixture verified animation, blur/visibility pause, reduced motion, responsive bounds, normal navigation and preference reload. Existing Wardrobe/Settings/version/launch and startup updater UI fixtures passed. Rust regression: 92 passed, 14 opt-in skipped.
- Native CI snapshot branch build/rust-native-20261105-bat in the private launcher repository, Actions run 42069842859. The CI workflow packages Linux x64 and Mac Intel/Apple Silicon with public source configuration and no production signing key. Version-bound updater signatures are created locally after download.
- All native runner jobs succeeded. Installers and updater assets are in D:/BatClientBuildCache/rust-native-20261105. The Windows NSIS, Linux AppImage, and both Mac app archives passed native updater signature, tamper and version-substitution checks. The disabled draft now covers four targets and preserves the Electron manifest fields. Mac bundles are ad hoc signed and not notarized. CI verified the Mac bundles and DMGs and inspected the Linux packages. Live Minecraft and Microsoft auth were not retested for the decorative feature. The website was not changed.


## 2026-11-06 — Roaming bat and Wardrobe polish

- Flying Bat uses a fixed, click-through viewport layer across launcher tabs. It hangs upside down from eligible visible buttons or links, the title bar or the top edge. Cached perch positions refresh after layout and navigation changes. An anchor that disappears or moves too far releases the bat into flight.
- Faster flight, a smoother landing roll and a small sprite highlight improve the pixel animation. Focus, visibility and reduced-motion guards continue to apply.
- Removed the Development Build label from the title bar and native window title. Saved Wardrobe skin heads fill the 46-pixel thumbnail with a nearest-neighbor head and hat crop. Front and Back animate a short character turn when motion is enabled.
- All four movement tests and the isolated flight and Wardrobe UI fixtures passed. Screenshots are in launcher-rust/output/launcher-screenshots/08-flying-bat.png and launcher-rust/output/wardrobe-slots-preview.png. The Wardrobe fixture uses a sample skin.
- The Windows development executable rebuilt and reopened with the title Bat Client Next. SHA256 9D6E76155DECEDC15E4CF9EDA08A39C4F43232522514ACBFA0A786E9B191D540. Build log D:/BatClientBuildCache/rust-roaming-bat-build.log. Signed installers and the disabled updater draft still refer to the prior cross-platform build.


## 2026-11-06 — Charcoal pixel mascot and cosmetics verification

- The launcher mascot uses a transparent 44×30 original charcoal sprite, enlarged with pixel sampling. ui/bat-sprite.js prepares 17 stroke poses, five folding poses and one resting blink. The renderer only redraws on a pose change, eases position and tilt, dims near key controls and remains click-through. It uses a short pointer activity window and respects focus and reduced motion.
- ui/bat-flight.js drives curved flight, short glides and hovers, a small orbit, swoops, upside-down resting, quick takeoff and a higher flap rate when startled. Steering, pose and flap rates ease between states. The launcher keeps eligible button and title-bar perches.
- Six movement tests passed. Isolated UI checks passed for fixed-size transparent sprite frames, up/down stroke, folding and blinking, navigation, pause behavior, settings persistence and Wardrobe layout/collections. Screenshot at launcher-rust/output/launcher-screenshots/08-flying-bat.png.
- Re-ran 13 existing Windows native wardrobe, cape_animation and skin_bridge fixture tests. All passed. Local PNG skins and static capes, GIF frame decoding and timing, legacy saved GIF recovery, collection import/equip and signed local profile serving are covered. Wardrobe can play equipped GIF frames locally. Full live cosmetic acceptance in Minecraft and Microsoft publishing remain unverified. Hosted cape sync currently publishes the first frame.
- The Windows development build completed in 3m14s and reopened as PID 6108 with a responding Bat Client Next window. SHA256 5209BE45743083D6F11ABB2C7259582E6171B8BF03CF1577050944EA84A81106. Build log D:/BatClientBuildCache/rust-mascot-build.log. Existing installers still contain the earlier build.


## 2026-11-06 — Interactive mascot and in-game cape handoff

- Flying Bat stays fully opaque and ignores pointer proximity. Left drag uses the painted sprite hit area and pointer capture. A held bat flaps and wiggles. Releasing it resumes ambient flight. Right-click closes its eyes and floats a small pixel heart. Normal controls, modals and popovers retain their click behavior. The obsolete React to Mouse setting is hidden while its saved field remains compatible.
- The sprite now uses 25 lift levels with stronger tip flex, more span change and a shaded downstroke. Movement and isolated UI tests passed for dragging, petting, full opacity, navigation, resting and reduced motion.
- Rust GIF frames had unique saved filenames, while the Java agent expected numbered player-specific files. Rust now stages only the equipped animation and its timing in the per-launch temporary directory, and uses the matching bundled Java agent. The Java override is scoped to the selected player. This keeps stale legacy frames from replacing a static or unequipped cape and preserves the shared Electron agent and cosmetic files.
- Gradle built the relocated Java agent and passed its async frame-loader validation. A fixture against the bundled JAR verified case-insensitive selected-player matching, frame order, frame timing and isolation from other players. Hosted cape sync still sends the first frame. Live animation rendering and online skin/cape visibility require separate Minecraft verification.
- Windows development executable rebuilt and reopened, SHA256 B8C05804DFBC6896236F132FE58EFD6B4A8CE2F5D5018303A57257E658C80805. All three Rust cape tests passed, including the numbered-file staging regression. A disposable Minecraft 1.21.1 Forge fixture reached the renderer in 27.811s with the new launch path. This was a startup test without an animated cape equipped. Evidence D:/BatClientBuildCache/rust-bat-petting-game-test.out. The signed installers still use the prior build.

## 2026-11-06 — First public Rust launcher release

- Public branding is **Bat Client**. The release title distinguishes the Rust preview from Classic. Tauri's window, product, installer, Start Menu and Mac bundle display names use Bat Client. The internal binary, bundle identifier, app data and updater identity retain their established identifiers for compatibility.
- Final private packaging snapshot `ff9c9066acb5da4059bede5800f5c3197215d66a`, Actions run `42249327480`. Targets are Windows x64 NSIS from the local Windows toolchain, Linux x64 AppImage and Debian package, macOS Intel and Apple Silicon DMGs and updater archives. Run `42246544011` was superseded after the public name correction. Its packages must stay unpublished.
- Publication is authorized for the Rust launcher on the public binary repository and on the production website via the existing backed-up SSH deployment. The companion mod release remains on hold. Preserve Classic `v2.2.97` as the GitHub Latest release and all top-level updater fields.
- Release staging is under `D:/BatClientBuildCache/rust-public-0.1.0-branded-packages` and `D:/BatClientBuildCache/rust-public-0.1.0-site`. Only release binaries, updater signatures and the checksum list are public assets. Updater signing stays local. Windows and Apple Silicon passed the rebuilt package's signature, tamper and version-substitution checks. Final publication and remaining validation are recorded below when complete.

### Release completion

- Published **Bat Client 0.1.0 (Rust Preview)** at https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/rust-v0.1.0. Classic `v2.2.97` remains the GitHub Latest release. All 12 public assets were fetched back from GitHub and matched local SHA-256 and byte counts. The release includes the installer files, two Mac updater archives, four version-bound updater signatures and SHA256SUMS.txt.
- Both macOS targets passed native packaging, app signature and DMG verification in run `42249327480`. That run's Linux dependency step stalled. The Linux-only retry `42251194702`, source snapshot `f662bf2173f1afb9d334302ea417a209861e8cb3`, changed only the CI workflow and passed. The Linux native runner inspected the AppImage and Debian package. The Windows x64 NSIS package was built locally. The native Windows updater verifier checked genuine signatures, tampered content and version substitution for all four updater payloads.
- The Windows package lacks Authenticode. Mac apps use ad hoc signing and lack Apple notarization. Mac users keeping Classic should install the Rust app in a separate folder because both app bundles use the Bat Client name. Linux and Mac gameplay and a real two-version update/restart remain unverified.
- Live https://batclient.xyz/update.json enables the Rust channel at 0.1.0 for Windows x64, Linux x64, Intel Mac and Apple Silicon. `installer` selects the first-install file on the website. All Classic fields, including the protected download URL and checksum, match the pre-release manifest.
- The approved SSH release deployment is backed up under `/var/backups/batclient/release-rust-0.1.0`. The user then requested the full redesigned website from `C:/Users/Noor-ul-Islam/Desktop/BatClient_SEO_Ready`. The 26-file redesign deployment was backed up under `/var/backups/batclient/redesign-20261106` and all 26 public HTTPS responses matched the staged bytes. Build evidence and checksums are in the staging folders above and `D:/BatClientBuildCache/site-redesign-20261106`.

## 2026-11-07 — JVM startup hotfix 0.1.1 in progress

- User reported Java rejecting G1NewSizePercent because UnlockExperimentalVMOptions was absent or appeared later. The final JVM argument vector now runs through jvm::unlock_experimental_options immediately before the Minecraft main class is appended. It inserts the unlock only when a gated G1NewSizePercent, G1MaxNewSizePercent or enabled Epsilon collector needs it. Existing argument order, custom values, agents and explicit lock directives remain in the command. A later gated option gets another unlock if an earlier directive relocked experimental flags.
- Standalone Rust regressions passed, including mixed global/loadout flags, an already valid unlock, a later unlock, relocking, unchanged ordinary presets, custom collectors and agent arguments. A real Java 21 test reproduced the original failure and started successfully after normalization.
- Cargo, Cargo.lock and Tauri version are 0.1.1. Private native build branch build/rust-native-0.1.1-release, snapshot 561aeeacf58d76f4f0f48e78ef699849cc0eb2b9, run 42362119413. This run uses the bounded Ubuntu mirror requests learned during 0.1.0. Windows signed installer build log D:/BatClientBuildCache/rust-public-0.1.1/windows-build.log. Fresh package collection and release scripts are under D:/BatClientBuildCache/rust-public-0.1.1 and rust-public-0.1.1-site, with packages under rust-public-0.1.1-packages.
- A disposable existing Forge 1.21.1 fixture is configured with both experimental G1 percentages and no user-supplied unlock for a native startup regression. Its pre-test config is backed up under rust-public-0.1.1/game-fixture-before.json and native build/test log is native-game-test.log. Existing user profiles were not edited.
- Release and updater deployment are explicitly authorized by the user's request. Publish only verified signed 0.1.1 artifacts, then update the enabled Rust manifest and website using the already selected backed-up SSH deployment. Keep Classic metadata intact. Prior Rust0.1.0 remains the live version until this release completes.

## 2026-11-07 — Rust 0.1.1 released

Published https://github.com/evilgamer123113-lang/bat-client-releases/releases/tag/rust-v0.1.1 for Windows x64, Linux x64 and both Mac architectures. All 12 public downloads matched their staged hashes and sizes. Updater signatures, tamper rejection and version binding passed for all four targets. The six JVM regressions, five offline updater regressions and actual Java 21 reproduction passed. The disposable Forge 1.21.1 startup with G1NewSizePercent and G1MaxNewSizePercent reached renderer initialization with Bat agents in 26.641s. The live rust.version is 0.1.1, rust.enabled is true, and Classic fields match their previous values. Production deployment changed only update.json after the user's final scope clarification. The prepared HTML release edits were reverted locally and were not deployed. Production backup /var/backups/batclient/release-rust-0.1.1. Evidence and all artifacts are under D:/BatClientBuildCache/rust-public-0.1.1, rust-public-0.1.1-site and rust-public-0.1.1-packages. Restarting a Rust launcher with autoCheckUpdates enabled checks and installs the update. The installed user's 0.1.0 launcher was observed with that preference true; its actual replacement was left for its next startup.


## 2026-11-07 — private combined-mod interactive launch and asset-index alias source fix

Mod FPS investigation requested user-controlled1.21.1 session. Added cfg(test)-only ignored native_combined_interactive in game_launch.rs; compiled release test host6e91f943acc52564 PASS3m26s, real prepare_for/JVM compose/agents/bridge path. No production command, UI, version bump, publish or release change. Open private gamePID13068/host15168/session96516, Xmx8G/Xms512M,twoagents,protected437Dmod,synthetic57frame512x256cape/nativeadvancePASS, copied private Comparison world. User manually tests/moves/exits; no PCcontrol/auto-flight/autoclose. Full Rust UI not launched. Details and exact private runtime in BAT-CLIENT-MOD-AGENT.md. Do not run another game/build/replace active JARs. Private cache contains directory junctions back to user's installed cache; never recursively traverse/delete targets during cleanup.

Found actual Rust startup obstruction: canonical asset17.json has differentSHA1 while existing MCLC1.21.1.json alias matches same manifest's official SHA1. Source game_files.rs now checks primary bytes, falls back to version alias ONLY if same official hash passes; wrong/missing both stillfail and validprimary wins. Extended existing mclc_asset_index_alias_requires_identical_official_hash regression. Source fix unbuilt/untested after edit because game is running; run focused Rust regression after it exits before distribution. Current interactive host is previous compile using private verified17.json copy, shared index unchanged. Current public release remains0.1.1 and no new publication authorized for these edits.

## 2026-11-08 — Public changelog caught up through Rust 0.1.3

User asked whether all update news was on the website before going to sleep. Live check showed onlyRust0.1.0; candidly reported missingentries and completed the implied update. Added datedRust0.1.1(JavaG1argumentordering),0.1.2(savedlooks/GIFhandoff/loadoutmotion/exact-versionmodhelp/assetchecksumalias),0.1.3(GIFdetail/memorybudget/Importimagesremoval/originalGIFreapply/FPSwebsitecopy). Latestjumpbadgeandanchorpoint0.1.3; old0.1.0andentireClassichistorypreserved. NoCSS/layout/scripts/ads/consent/updateJSONchanges. Desktop/mobilebrowserassertionsPASS anddesktopcapturevisuallyreviewed. Deployedonlychangelog.html withold/newhashguardsandrollbackbackup /var/backups/batclient/changelog-rust-20261108. BareHTTPSbytesverifiedSHA2562758fa8ccd3ce53fe395c5490655facc452dc2957c29669687ccab13906ddb30. UpdaterstillRust0.1.3/Classic2.2.97. Evidence/script/originalbytes/screenshots D:/BatClientBuildCache/site-changelog-rust-20261108. PublicURL https://batclient.xyz/changelog.html.

## 2026-11-08 — Rust 0.1.4 publication authorized, in progress

User explicitly requests publication and all missing changelog news. Existing backed-up SSH deployment method remains selected. Preparing Rust0.1.4 Windowsx64/Linuxx64/MacIntel/AppleSilicon, signed with existing updater key. Private isolated release source3f142d4cbd9f999f63997f1b7ef5987af9840071, branch build/rust-native-0.1.4-release, native Actions42458796567. Original working checkout/index preserved; snapshot diff only9Rust files. Native107PASS and UI upload/model/retry/restart/zoom/layout PASS recorded in RUST-LAUNCHER-AGENT.md. Windows installer packaging in progress; other targets CI. No assets/updater activated yet. Public remainsRust0.1.3/Classic2.2.97.

Staged changelog0.1.4 includes entire Wardrobe redesign/persistence/direct chosen-card upload/automatic names/removal of confirmation bars/retry/sparse collections/ClassicSlim next-launch model/scroll zoom/GIF timing and online-sync limits. Existing0.1.0–0.1.3 and Classic history preserved. No website layout/theme/ads/consent/gate/backend changes. Publication requires signed-package verification, public stream hashes and only then backed-up atomic website manifest/changelog deployment. Evidence D:/BatClientBuildCache/rust-public-0.1.4[-site|-packages]. Mod release/ports remain paused.

## Rust 0.1.4 package checkpoint — 2026-11-08

Windows NSIS packaging PASS2m54s/10.45MiB. Windows and AppleSilicon updater payloads signed with original protected key and native signature/tamper/versionbinding verifier PASS. AppleSilicon0.1.4 Info.plist and ARM64 Mach-O checked. Linux/IntelMac Actions still building, run42458796567. Original source3f142d4cbd9f999f63997f1b7ef5987af9840071. Changelog browser desktop/mobile checks PASS and captures reviewed; previous0.1.0–0.1.3/Classic history retained. SSH read-only health: nginx/skinAPIactive; production manifest/changelog baseline hashes captured. Live still0.1.3, no deployment/publication yet. Pending final packages, public asset verification, backed-up2-file manifest/changelog deploy. Evidence rust-public-0.1.4 directories on D:.

## Rust 0.1.4 all native packages verified — 2026-11-08

Native Actions42458796567 all3runnersPASS fromsource3f142d4cbd9f999f63997f1b7ef5987af9840071. Windows NSISPASS. Four updaterpayloads signed with originalkey; native signature/tamper/versionbinding verifierPASS. Mac0.1.4 plist+MachO ARM64/x86_64PASS, Linux AppImage ELF x64 and Debian0.1.4/amd64PASS. Prepared12assets plus manifest preserving allClassicfields. Publication/upload now running; public stream checksum verification and backed-up manifest/changelog deployment still pending. Do notclaim updaterlive yet. Details/packages/evidence D:/BatClientBuildCache/rust-public-0.1.4[-site|-packages].

## 2026-11-08 — Website Wardrobe screenshot refreshed

User requested replacing the website Wardrobe image with current design. Captured actual current0.1.4 HTML/UI at1320x900 using isolated demo-only account/Steve skins, two populated cards and four upload cards. No user account/config/skin files used or changed. Capture uses launcher-supported pet-disabled preference only in synthetic fixture so model is unobstructed; shared WEBSITE bat still enabled unchanged. No image repaint/generation or fabricated UI. Hidden normal renderer avoided intermittent offscreen missing-glyph capture; final image visually reviewed.

New file screenshots/launcher-wardrobe-0.1.4.png, Home Wardrobe href/src point to it with updated descriptive alt. Old screenshots/launcher-wardrobe.png remains live byte-identical. No layout/CSS/fonts/scripts/other images/ads/consent/gate/updater/backend changes. Home gallery retains6shots; new image natural1320px/colorfilter-none, full-size lightbox and390px no-overflow browser checks PASS, page view reviewed. Native launcher release not changed; live Rust0.1.4 updater hash unchanged.

Deployed only new PNG thenindex.html using existing authorized backed-up SSH method; baseline/staged SHA guards and rollback index backup /var/backups/batclient/wardrobe-shot-014-20261108 (added-paths recorded). First attempt stopped before mutation due CRLF shell lines; normalized script to LF and retried same SSH method successfully. nginx/skinAPIactive. Bare+www index and PNG HTTPS bytes exactly match staging; old PNG and update.json verified unchanged. Local website mirror synchronized. ScreenshotSHA2560b2378d801e716c8fa73b55bdd1d9c10089ec654a8efd6aaaa2579deb14dee20, indexSHA256d9c9e6c9bb9b3aab24ee435e7d55b40d710bd5ad830fd4ff38eec1b5f82e866f. Evidence/capture/browser/deploy scripts D:/BatClientBuildCache/site-wardrobe-shot-014-20261108/completed.json and siblingfiles.


## 2026-11-14 — Companion mod staging, LegacyFabric checksum fallback & mod compatibility

- **`mod_compatibility.rs` & `game_launch.rs`:**
  - Verified `auto_fix_for_launch` + `companion_mod::cleanup_stale_at` (`cargo test --locked --release --features custom-protocol`: `114 passed; 0 failed; 17 ignored`) and rebuilt `bat-client-next.exe` (`0.1.5`).
- **`companion_mod.rs` (1.8.9 Fabric companion mod not loading):**
  - Removed `mark_hidden` (`FILE_ATTRIBUTE_HIDDEN`) from staged `.jar` files in `mods/` because Fabric Loader's `DirectoryModCandidateFinder` skips hidden files. Verified `4 passed; 0 failed` in `companion_mod` tests and confirmed `batclient 0.1.0-fabric-internal` loads in `1.8.9`.
- **`game_install.rs` (`intermediary-1.8.9.jar` checksum unavailable):**
  - Added a 2-attempt retry in `checksum()` and an `existing_file_sha1()` fallback in `artifact()` when remote checksum lookup fails (e.g., transient `repo.legacyfabric.net` DNS failure) and a regular non-empty file already exists locally. Verified `2 passed; 0 failed; 1 ignored` in `game_install` tests.


## 2026-11-15 — Rust 0.1.5, 0.1.6 & 0.1.7 Releases + Website Changelog

### `rust-v0.1.5` & `rust-v0.1.6`
- **Cross-platform companion mod pack lookup (`launcher-rust/src-tauri/src/companion_mod.rs`):**
  - Updated `find_pack_file` to search Windows, macOS (`.app/Contents/Resources/`), Linux (`lib/bat-client-next/resources/`, `share/bat-client-next/`), and `lib::root()/bat-mods.dat`.
- **Automatic Fabric API setup (`launcher-rust/src-tauri/src/mods.rs`, `game_launch.rs`):**
  - Added `mods::ensure_fabric_api(root, profile_id, version)` and wired it into `game_launch.rs` before Fabric `1.18.2+` launches so matching `fabric-api` (`P7dR8mSH`) is automatically installed and verified in `mods/`, and `install_selected_at` replaces disabled stale `fabric-api` jars cleanly.
- **Releases & Manifest:**
  - Built Windows x64 NSIS locally + macOS Apple Silicon, macOS Intel, and Linux x64 via GitHub Actions (`43686834278` for `0.1.5`, `43690331124` for `0.1.6`), signed with `D:\BatClientPrivate\rust-updater\bat-client-next.key`, verified with `--updater-verify-smoke`, published prereleases `rust-v0.1.5` and `rust-v0.1.6` on `evilgamer123113-lang/bat-client-releases` (keeping Classic `v2.2.97` as `latest`), and deployed `update.json` to `/var/www/html/update.json`.
  - Updated `https://batclient.xyz/changelog.html` with `0.1.5` and `0.1.6` entries (`sha256: d76be6a0aa6902aad4940df3903947bc1a4ef8dc4cb52e85c3c33a1280ae3a32`).

### `rust-v0.1.7` — Dual Microsoft Login (Bat Client Window + Browser)
- **Backend (`launcher-rust/src-tauri/src/microsoft.rs`, `main.rs`):**
  - Kept **Sign in in Bat Client** (`start_microsoft_login`, built-in WebView OAuth2 + PKCE).
  - Added **Sign in in browser** (`start_microsoft_browser_login` and `cancel_microsoft_login`) using Microsoft Live device-code flow (`https://login.live.com/oauth20_connect.srf` → opens `https://www.microsoft.com/link?otc=<code>` in the default browser via `app.opener().open_url`, emits `bat-microsoft-device-code`, and polls `https://login.live.com/oauth20_token.srf` with `Set-Cookie` forwarding and 250ms cancellation responsiveness).
  - Added unit tests `device_link_url_appends_otc_and_rejects_non_https` and `device_poll_errors_classify_pending_slowdown_and_terminal` (`4 passed; 0 failed; 1 ignored` in `microsoft::tests`).
- **UI (`launcher-rust/ui/index.html`, `accounts.js`, `styles.css`):**
  - Added two action buttons on the Accounts page: **Sign in in Bat Client** (`#microsoft-add`) and **Sign in in browser** (`#microsoft-browser-add`), plus `#microsoft-browser-panel` showing the selectable code, clickable link, **Copy code**, and **Cancel** buttons.
- **Release & Live Verification:**
  - CI run `43701722490` (`build/rust-native-0.1.7-release`, commit `1166a94e9c9ab676f642816a2acbfd269658a81b`) built macOS ARM64, macOS Intel, and Linux x64; Windows x64 NSIS built locally (`10,988,365` bytes).
  - Signed all 4 updater payloads with `--app-version 0.1.7` (required by `requireSignedVersion`) and verified all 4 via `--updater-verify-smoke` with isolated `WEBVIEW2_USER_DATA_FOLDER` (`NATIVE_UPDATER_VERIFY_SMOKE PASS`).
  - Published `rust-v0.1.7` (12 assets, `v2.2.97` preserved as `latest`), updated `https://batclient.xyz/changelog.html` (`sha256: 0fa27bc9c14fda1b1eec9afea0552b9f767872f6177e53c0ad240812201fafa6`), and deployed `https://batclient.xyz/update.json` with matching `url`, `signature`, `sha256`, `size`, and `installer` for all 4 platforms (`sha256: 4dc5bb4b38539a4ffd86c1d52fe73c32eee3087230e303e9034ee79fdb0ad759`).
  - Confirmed live end-to-end auto-update succeeded on user's machine.

### `rust-v0.1.8` — Automatic Companion Mod Bundle Provisioning
- **Root cause fixed:** `bat-mods.dat` (`717,785,348` bytes) was previously only on the dev machine (`D:/BatClientBuildCache/bat-mods.dat`), causing other users to see `"Bundled mod package not found."` in Loadout details.
- **Automatic download & verification (`launcher-rust/src-tauri/src/companion_mod.rs`, `main.rs`):**
  - Uploaded the opaque `BATMODS1` XOR-masked `bat-mods.dat` (no raw `.jar` files) to `bat-mods-v1` (`SHA-256: ec113c9f6748149b799d34adfa7bfa2eec41f61062ba40bf900e4e7286b78eed`, `717,785,348` bytes).
  - Added `ensure_pack_downloaded` (using `downloads::file` with exact SHA-256/size verification and `PACK_DOWNLOAD_LOCK`) and `spawn_background_prefetch` on launcher startup (`main.rs`), plus on-launch fallback in `stage_for_launch`.
  - Updated `check_availability` to report `"Supported · Bat Client mod for {engine} {version} downloads automatically on launch."` when not yet cached locally.
  - Bumped `updater::LIMIT` to `1024 * 1024 * 1024` (1 GiB) while keeping `0.1.8` installers ~10 MB so `0.1.7` users auto-update cleanly.
- **Release & Live Verification:**
  - Verified `companion_mod::tests` (`4 passed; 0 failed`) and `updater::tests` (`5 passed; 0 failed; 1 ignored`).
  - CI run `43711499582` (`build/rust-native-0.1.8-release`, commit `4761c5cba060729eadc9ad9dd0da17ff0064d549`) + local Windows x64 NSIS (`10,984,420` bytes); all 4 updater payloads signed with `--app-version 0.1.8` and verified via `--updater-verify-smoke` (`NATIVE_UPDATER_VERIFY_SMOKE PASS`).
  - Published `rust-v0.1.8` (12 assets, `v2.2.97` preserved as `latest`), deployed `update.json` (`sha256: b6f76add2f95ad743a54d89c04b9138622200cf734fdb04e7af10299bb603db9`) and `changelog.html` (`sha256: 141fa000484a0f3d596fa12001a63a263ce3ce945fedc8e1fa08032e9fe2ff24`).
