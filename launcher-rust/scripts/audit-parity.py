"""Rebuild the source-backed Electron/Rust parity report; no application/user data writes."""
from pathlib import Path
import hashlib
import json
import re
from collections import Counter

ROOT = Path(__file__).resolve().parents[2]
RUST = ROOT / 'launcher-rust'
source = (ROOT / 'main.js').read_text(encoding='utf-8')
channels = {}
for match in re.finditer(r"ipcMain\.(handle|on)\(\s*['\"]([^'\"]+)['\"]", source):
    channels.setdefault(match[2], []).append({'kind': match[1], 'line': source[:match.start()].count('\n') + 1})

coverage = {}
def mark(names, status, evidence, note):
    for name in names.split():
        assert name in channels and name not in coverage, name
        coverage[name] = {'status': status, 'evidence': evidence, 'note': note}

mark('record-launch get-launch-log', 'Missing', 'src-tauri/src/game_launch.rs', 'Private JVM logs exist; no persistent recent-launch dashboard history or lastPlayed update.')
mark('get-mojang-versions-list', 'Implemented', 'src-tauri/src/versions.rs; ui/profiles.js', 'Official catalog, all four version-type filters and search. Catalog listing is not game installation.')
mark('check-for-update download-update install-update', 'Partial', 'src-tauri/src/updater.rs; ui/startup.js; ui/updater.js; UPDATER.md', 'Signed Rust channel and automatic splash flow implemented. Native signature verification passed; public channel disabled, Mac/Linux native packages built and local updater signatures verified. Real two-version replacement/restart remains unverified.')
mark('check-update-result', 'Missing', 'src-tauri/src/updater.rs', 'No persistent after-restart update result/rollback receipt equivalent.')
mark('search-modrinth-api', 'Partial', 'src-tauri/src/mods.rs; ui/mods.js', 'Version/loader-filtered search and pagination work; index fixed to relevance, no Electron sort control.')
mark('check-mod-compat', 'Implemented', 'src-tauri/src/mods.rs; ui/mods.js', 'Project details show the number of compatible releases for the selected Minecraft version and loader. Installation revalidates the selected file.')
mark('update-rpc-state send-discord-live-stats', 'Missing', 'src-tauri/src/settings.rs', 'Discord preference persists; no native Discord RPC/live-stat integration.')
mark('minimize-window maximize-window close-window', 'Implemented', 'src-tauri/src/main.rs', 'Native window actions and tray-close implementation exist; actual desktop acceptance remains pending.')
mark('open-external-link', 'Partial', 'src-tauri/src/main.rs', 'Allowlisted news/support/Discord links work; broad Electron URL/link workflows are not all present. Do not restore an unrestricted URL proxy.')
mark('copy-text', 'Missing', 'src-tauri/src/main.rs', 'No equivalent explicit clipboard action. Ordinary browser text selection/copy is a separate capability.')
mark('get-bat-news', 'Implemented', 'src-tauri/src/changelog.rs; ui/news.js', 'Official changelog text with navigation across up to 12 recent releases, manual refresh and offline cache. Home navigation and offline behavior passed an isolated fixture.')
mark('get-system-info get-system-ram', 'Missing', 'src-tauri/src/main.rs', 'No system-information/RAM reporting UI/command or hardware-based RAM recommendation.')
mark('open-folder select-directory open-profile-folder', 'Missing', 'src-tauri/src/main.rs', 'Folder open/browse controls absent; paths can currently be entered manually.')
mark('get-game-root', 'Partial', 'src-tauri/src/library.rs; src-tauri/src/settings.rs', 'Settings displays the effective saved game directory and opens it with the native folder action.')
mark('get-mods-info', 'Partial', 'src-tauri/src/mods.rs; ui/mods.js', 'Installed mod names, count, enabled state, and per-file legacy managed badges are visible. The combined Electron summary remains partial.')
mark('download-modrinth-exact', 'Partial', 'src-tauri/src/mods.rs; ui/mods.js', 'Compatible primary-file and required-dependency install/checksums work. User-selectable historical version/file and per-file download progress absent.')
mark('download-modrinth-file get-mod-details', 'Missing', 'ui/mods.js; src-tauri/src/mods.rs', 'No full project gallery/version/file browser or selected-file install workflow; do not add arbitrary download URLs as a shortcut.')
mark('get-profiles-data save-profile-node delete-profile-node duplicate-profile-node select-active-profile', 'Implemented', 'src-tauri/src/profiles.rs; src-tauri/src/main.rs; ui/profiles.js', 'Core metadata CRUD/select/copy/favorite and custom arguments covered by fixtures. Profile deletion preserves game files.')
mark('create-blank-profile', 'Partial', 'src-tauri/src/profiles.rs; ui/profiles.js', 'Create works; Electron hardware-based suggested RAM and background prepare trigger are absent.')
mark('reorder-profiles export-loadouts import-loadouts', 'Missing', 'src-tauri/src/profiles.rs; ui/profiles.js', 'No reorder or loadout import/export workflow.')
mark('get-accounts-data add-new-identity switch-active-identity', 'Implemented', 'src-tauri/src/accounts.rs; ui/accounts.js; ui/account-picker.js', 'Offline create/select and public account listing/switching covered by backend/UI fixtures.')
mark('start-microsoft-login add-premium-identity', 'Implemented', 'src-tauri/src/microsoft.rs; ui/accounts.js', 'Native OAuth validates/persists accounts rather than trusting raw renderer credentials; user confirmed actual sign-in. This does not prove premium gameplay.')
mark('refresh-microsoft-account', 'Unverified', 'src-tauri/src/microsoft.rs; src-tauri/src/game_launch.rs', 'Native refresh and automatic pre-launch expiry handling exist; no recorded real expired-token/restart/premium-gameplay acceptance test. Accounts offers manual refresh. Refresh verifies the saved profile UUID and keeps the active account selection. The UI command was checked with a fixture.')
mark('reorder-accounts', 'Missing', 'src-tauri/src/accounts.rs; ui/accounts.js', 'No account reorder workflow.')
mark('delete-account-identity', 'Partial', 'src-tauri/src/accounts.rs; ui/accounts.js', 'Offline removal works. Microsoft removal explicitly blocked until old/native credential cleanup parity is finished.')
mark('set-account-skin', 'Partial', 'src-tauri/src/wardrobe.rs; ui/wardrobe.js', 'Local PNG/model saves work; Microsoft/registry upload implementations exist but live uploads remain unverified; remote removal unsupported by current service.')
mark('get-account-skin-data', 'Partial', 'src-tauri/src/accounts.rs; src-tauri/src/wardrobe.rs; ui/app.js; ui/wardrobe.js', 'Uses saved local PNG/head first. Public Mojang skin/cape fallback is available for saved premium UUIDs and preserves local animated capes. Live account lookup is unverified.')
mark('set-custom-cape', 'Partial', 'src-tauri/src/wardrobe.rs; ui/wardrobe.js; src-tauri/src/skin_bridge.rs', 'Static PNG equip and existing saved-frame preview preserved. New GIF decoding/import missing; remote sharing sends first PNG frame only; premium custom cape/gameplay needs validation.')
mark('remove-custom-cape', 'Implemented', 'src-tauri/src/wardrobe.rs', 'Unequips the local cape and animation. Saved collection files are preserved for later use.')
mark('save-account-slots remove-skin-slot remove-cape-slot get-account-slots', 'Missing', 'src-tauri/src/wardrobe.rs; ui/wardrobe.js', 'No skin/cape slot collection CRUD/equip UI or native equivalent; unknown saved slot fields are preserved.')
mark('get-equipped-cosmetics', 'Partial', 'src-tauri/src/wardrobe.rs; src-tauri/src/skin_bridge.rs', 'Current local outfit DTO and loopback profile exist; complete slots/premium-custom override/multiplayer animated cosmetic acceptance still missing.')
mark('get-app-settings save-app-settings', 'Partial', 'src-tauri/src/settings.rs; ui/settings.js', '26 visible preferences persist and opaque fields survive; 16 have no current runtime consumer. See settings table.')
mark('check-first-launch complete-first-launch', 'Missing', 'src-tauri/src/main.rs; ui/app.js', 'No first-run setup/wizard/complete-firstLaunch workflow. Existing firstLaunch field preserved, not consumed.')
mark('detect-java', 'Implemented', 'src-tauri/src/java_runtime.rs; ui/app.js', 'Exact required-major/custom/bundled/PATH/vendor discovery with timed probes is active in installed launch; not a Java installer.')
mark('get-java-status', 'Partial', 'src-tauri/src/java_runtime.rs', 'Settings checks Java 8, 11, 16, 17 and 21 on demand and offers verified installation of missing runtimes. The selected-loadout check remains available.')
mark('download-java', 'Missing', 'src-tauri/src/java_runtime.rs; src-tauri/src/game_launch.rs', 'Missing Java produces an explicit error; no verified runtime download/install.')
mark('clear-launcher-cache', 'Missing', 'src-tauri/src/main.rs', 'No native user-facing cache maintenance action; Electron Chromium cache names must not be blindly deleted for WebView2.')
mark('get-installed-mods-count', 'Implemented', 'src-tauri/src/mods.rs; ui/mods.js', 'Installed list count includes disabled entries; fixture-tested UI.')
mark('get-installed-mods', 'Partial', 'src-tauri/src/mods.rs; src-tauri/src/mod_icon.rs', 'Global list, disabled artwork and project IDs work. Full legacy managed details not exposed; both launchers currently use a global mods directory.')
mark('toggle-mod remove-mod', 'Partial', 'src-tauri/src/mods.rs', 'Top-level remove/disable/enable, recoverable backup, opt-outs and Sodium-dependent guards work. No older protected Bat JAR nested-renderer rewriting parity; existing tests do not prove nested protected payload handling.')
mark('check-mod-conflicts', 'Partial', 'src-tauri/src/mods.rs', 'Sodium/add-on and exact install compatibility guards exist; broad installed conflict/dependency reports and pre-launch compatibility cleanup absent.')
mark('export-modpack import-modpack', 'Missing', 'src-tauri/src/mods.rs; ui/mods.js', 'No modpack archive import/export workflow.')
mark('launch-game', 'Partial', 'src-tauri/src/game_launch.rs; src-tauri/src/game_files.rs; src-tauri/src/game_repair.rs', 'Installed Vanilla/Fabric with existing agents only; actual 1.21/1.21.1 renderer/agent initialization recorded. No fresh game/assets/libs/loader/Java/agent provisioning, Forge/NeoForge/Quilt, background prepare or quick-join. No full gameplay/performance parity proof.')
mark('get-launch-presets save-launch-preset delete-launch-preset apply-launch-preset', 'Missing', 'src-tauri/src/settings.rs; src-tauri/src/jvm.rs', 'Named launch-preset CRUD/apply absent. jvmPreset value persists but default launch uses fixed defaults plus custom arguments.')
mark('get-mod-presets save-mod-preset delete-mod-preset apply-mod-preset snapshot-mod-preset', 'Missing', 'src-tauri/src/mods.rs', 'No named mod collection/preset CRUD/snapshot/apply workflow.')
mark('ping-minecraft-server', 'Missing', 'ui/index.html; src-tauri/src/main.rs', 'No server ping/status/partner server cards or server quick-join flow.')
mark('get-screenshots open-screenshot delete-screenshot get-screenshot-thumb', 'Missing', 'src-tauri/src/main.rs; ui/index.html', 'No screenshot gallery/thumbnail/open/delete workflow. Existing player screenshot files are untouched.')
mark('check-mod-updates update-mod', 'Missing', 'src-tauri/src/mods.rs; ui/mods.js', 'No installed mod update check/apply/version-change workflow.')
mark('analyze-crash get-game-health start-benchmark stop-benchmark', 'Missing', 'src-tauri/src/game_launch.rs; ui/index.html', 'JVM text logs exist; crash analysis, health panel, benchmark/session telemetry tools absent.')
mark('get-profile-game-dir', 'Implemented', 'src-tauri/src/library.rs; src-tauri/src/tools.rs; ui/profiles.js', 'Loadout editor opens an existing legacy instances/<id> folder when present. Paths are checked for links and containment. Launch continues to use the effective shared game directory.')
# Migration checkpoint overrides after the original audit. Removed Tools UI stays removed.
def revise(names, status, evidence, note):
    for name in names.split(): coverage.pop(name)
    mark(names, status, evidence, note)
revise('reorder-profiles export-loadouts import-loadouts', 'Implemented', 'src-tauri/src/tools.rs; ui/library-extras.js', 'Native reorder/import/export; validates before commit, exports public profile fields, preserves opaque account fields. Fixture and UI checks passed; native file dialogs still need acceptance.')
revise('reorder-accounts', 'Implemented', 'src-tauri/src/tools.rs; ui/library-extras.js', 'Drag and Alt-arrow ordering preserve complete account objects. Native fixture verifies private-field preservation.')
revise('check-first-launch complete-first-launch', 'Implemented', 'src-tauri/src/tools.rs; ui/library-extras.js', 'Native firstLaunch read/complete and compact welcome dialog. Existing false is respected; no Tools page.')
revise('download-java', 'Implemented', 'src-tauri/src/java_install.rs; ui/library-extras.js', 'Bounded SHA256-verified Adoptium runtime installation, archive traversal checks, actual Java21 install/probe passed in disposable D: fixture. Other OS/major native tests pending.')
revise('launch-game', 'Partial', 'src-tauri/src/game_launch.rs; src-tauri/src/game_install.rs; src-tauri/src/agents.rs', 'Fresh official Minecraft/assets/libraries/natives and Fabric/Quilt/Legacy Fabric metadata installer, managed Java and agent provisioning now exist. Fresh1.21.1 Fabric download/resolve passed; native Legacy Fabric 1.8/1.8.9 install and renderer startup passed on Windows; broader installed-game gameplay, OSes and matrix remain.')
revise('set-custom-cape', 'Partial', 'src-tauri/src/wardrobe.rs; src-tauri/src/cape_animation.rs; ui/wardrobe.js', 'PNG and bounded GIF import with disposal-aware RGBA-to-PNG frames and timing; native decode/save/private-field preservation tests pass. Local saved animation bridge reused. Online sharing still first frame; native animated gameplay/multiplayer acceptance pending.')
revise('get-app-settings save-app-settings', 'Partial', 'src-tauri/src/settings.rs; src-tauri/src/runtime.rs; ui/settings.js', 'Preferences persist; expanded runtime consumers listed below. Scoped runtime consumers now exist for all26visible settings; live/platform limitations remain.')
revise('record-launch get-launch-log', 'Implemented', 'src-tauri/src/game_launch.rs; src-tauri/src/tools.rs', 'Rust retains 60 shared launch records with the server destination and lastPlayed. The Home history panel was removed at the user’s request.')
revise('open-folder select-directory', 'Implemented', 'src-tauri/src/tools.rs; ui/library-extras.js', 'Native folder/Java browse wired in Settings; fixed allowlisted folder opener exists. Native OS-dialog acceptance still pending.')
revise('get-system-info get-system-ram', 'Partial', 'src-tauri/src/tools.rs', 'Settings shows OS, architecture, CPU thread count, available memory and suggested memory. New loadouts use that memory suggestion.')
revise('get-launch-presets save-launch-preset delete-launch-preset apply-launch-preset', 'Implemented', 'src-tauri/src/tools.rs; ui/library-extras.js', 'Loadouts supports named preset creation, update, apply and removal using the shared Electron preset file.')
revise('get-mod-presets save-mod-preset delete-mod-preset apply-mod-preset snapshot-mod-preset', 'Implemented', 'src-tauri/src/tools.rs; src-tauri/src/mods.rs; ui/library-extras.js', 'Installed Mods supports saving, updating, applying and deleting named snapshots. Apply respects legacy disabled entries, checks missing files and Sodium dependencies, and disables unselected mods with rollback on errors.')
revise('ping-minecraft-server', 'Partial', 'src-tauri/src/tools.rs; src-tauri/src/game_launch.rs', 'Native server status and quick join remain available. The user removed the Partner servers section from Home.')
revise('get-screenshots open-screenshot delete-screenshot get-screenshot-thumb', 'Partial', 'src-tauri/src/tools.rs', 'Bounded list/preview and recoverable removal helpers exist; gallery removed with Tools. No native external image opening or complete thumbnail-cache parity.')
revise('analyze-crash get-game-health start-benchmark stop-benchmark', 'Partial', 'src-tauri/src/tools.rs', 'Local redacted crash hints/health and owned-game CPU/RAM sampler exist; page removed with Tools. No game frame-time benchmark/session start-stop implementation.')
revise('search-modrinth-api', 'Implemented', 'src-tauri/src/mods.rs; ui/mods.js', 'Compatible search, pagination and relevance/downloads/follows/newest/updated sorting with native allowlist. UI contract passed.')
revise('download-modrinth-file get-mod-details', 'Partial', 'src-tauri/src/mods.rs; ui/mods.js', 'Bounded project details/text-only body, allowlisted gallery and compatible version/file chooser added. Native explicit selection never falls back, verifies compatibility/project/hash/dependencies; UI contract and live read-only metadata passed. Existing different/disabled versions require explicit management; live selected-file installation acceptance still pending.')
revise('download-modrinth-exact', 'Partial', 'src-tauri/src/mods.rs; ui/mods.js', 'Exact compatible version/file selection now active with required dependency/checksum staging. Live chosen-file install and detailed byte progress acceptance pending.')
revise('save-account-slots remove-skin-slot remove-cape-slot get-account-slots', 'Partial', 'src-tauri/src/wardrobe.rs; ui/wardrobe.js', 'Named20skin/20cape collection slots save/equip/remove/list in Wardrobe; legacy metadata and sparse indices preserved, removal keeps files/equipped cosmetic, animation timing restored. GIF-only legacy slots generate frames when equipped. Native/UI fixtures passed. Wardrobe imports multiple PNG skins or PNG/GIF capes in one operation. Sparse slots and equipped cosmetics are preserved and failed batches roll back. Live online/multiplayer acceptance remains.')
revise('check-mod-updates update-mod', 'Partial', 'src-tauri/src/mods.rs; ui/mods.js', 'Enabled-linked mod update check and explicit per-file update now exist; original file registry identity/current SHA512 and target game/loader/version are verified. Transaction stages downloads, preserves original JAR/metadata, restores on failed commit. Disabled/removed files are skipped and opt-outs retained. Rollback/native fixture and UI tests pass. Actual two-version Modrinth helper download/update/backup test passed; full gameplay and native OS-dialog acceptance remain.')
revise('export-modpack import-modpack', 'Partial', 'src-tauri/src/mods.rs; ui/mods.js', 'Native file dialogs and Installed import/export controls support checksummed .batpack ZIPs and old Electron packs. Sparse enable choices preserved, dismissed projects import disabled, accounts/config excluded from exports and legacy embedded config ignored. Bounded ZIP/path/JAR/CRC/hash/collision checks and roundtrip/legacy fixtures pass; native OS dialog acceptance and broader pack ecosystems remain.')
assert set(coverage) == set(channels), sorted(set(channels) - set(coverage))

main = (RUST / 'src-tauri/src/main.rs').read_text(encoding='utf-8')
registry = main.split('tauri::generate_handler![')[1].split('])')[0]
registered = {value.split('::')[-1] for value in re.findall(r'\b(?:\w+::)*\w+\b', registry)}
invokes = {match[2] for path in (RUST / 'ui').glob('*.js') for match in re.finditer(r"invoke\(\s*(['\"])([^'\"]+)\1", path.read_text(encoding='utf-8'))}
assert not invokes - registered, sorted(invokes - registered)
for item in coverage.values():
    for filename in item['evidence'].split('; '):
        assert (RUST / filename).is_file(), filename


revise('update-rpc-state send-discord-live-stats', 'Partial', 'src-tauri/src/discord_presence.rs; src-tauri/src/runtime.rs', 'Native gated launcher/starting/game presence exists and includes the selected launcher tab while idle. Live FPS/ping/server ingestion and live desktop/platform acceptance remain.')
revise('delete-account-identity', 'Partial', 'src-tauri/src/accounts.rs; src-tauri/src/microsoft.rs; ui/accounts.js', 'Offline/Microsoft removal exposed; own OS credential deletion tested with synthetic entry, shared references and cosmetic files kept. Legacy credential stores preserved; live account removal acceptance pending.')
revise('launch-game', 'Partial', 'src-tauri/src/game_launch.rs; src-tauri/src/forge_install.rs; ui/game-launch.js', 'Official modern Forge/NeoForge1.21.1 install/resolve/renderer-agent checks passed. Legacy formats and full version/OS/premium gameplay matrix remain.')
revise('copy-text', 'Partial', 'ui/game-launch.js', 'Copy launch details and server addresses are available in their respective workflows. Live system clipboard for the new server cards remains unverified.')

revise('check-update-result', 'Partial', 'src-tauri/src/updater.rs; ui/updater.js', 'Verified install attempt persists a local receipt. Running target version confirms success, previous version reports interrupted/failed and waits for manual retry of the same update. Real installed replacement/restart remains unverified.')
revise('open-profile-folder', 'Partial', 'src-tauri/src/tools.rs; ui/profiles.js; ui/mods.js', 'Loadout editor opens actual shared game directory after validating profile ID; Mods opens shared mods folder. Native OS folder-open acceptance pending.')
revise('clear-launcher-cache', 'Partial', 'src-tauri/src/web_cache.rs; ui/settings.js', 'Windows WebView2 disk-cache-only operation with completion callback. Cookies, storage and game data preserved. UI command fixture passed; real cache effect and macOS/Linux acceptance pending.')

revise('check-mod-conflicts', 'Partial', 'src-tauri/src/mod_compatibility.rs; ui/mods.js', 'Installed Mods scans enabled JARs for loader mismatches, common conflicts, duplicate Fabric IDs, exact declared Minecraft release mismatches and a confirmed Fabric networking incompatibility. Full dependency range and binary shape checks remain.')
revise('create-blank-profile', 'Partial', 'src-tauri/src/profiles.rs; ui/profiles.js; src-tauri/src/background_prepare.rs', 'Creation now suggests RAM from the existing native system-info policy without changing saved profiles; selecting a new profile queues cancellable background preparation. Native suggestion-policy and create/edit UI checks passed.')
revise('launch-game', 'Partial', 'src-tauri/src/game_launch.rs; src-tauri/src/forge_install.rs; src-tauri/src/background_prepare.rs; ui/game-launch.js', 'Forge 1.8/1.8.9, Forge/NeoForge 1.21.1, NeoForge 1.20.1, and Legacy Fabric 1.8/1.8.9 passed native install/resolve and renderer startup on Windows. Background preparation is cancellable. Broader version, gameplay and OS acceptance remains.')

active = {
    'defaultRam':'game_launch.rs — actual -Xmx selection', 'javaPath':'java_runtime.rs — /appSettings/javaPath candidate',
    'extraJvmArgs':'game_launch.rs + jvm.rs — global/profile composition', 'gameDir':'library.rs — effective global root',
    'windowWidth':'game_launch.rs — launch resolution argument', 'windowHeight':'game_launch.rs — launch resolution argument',
    'fullscreen':'game_launch.rs — fullscreen game argument', 'hideOnLaunch':'game_launch.rs — native window hide',
    'autoCheckUpdates':'ui/updater.js — automatic startup flow', 'publishSkinsToBatClient':'wardrobe.rs — upload gate; live sync unverified',
}
active.update({
 'discordRPC':'discord_presence.rs + runtime.rs — gated background IPC, reconnect/clear; live desktop acceptance pending',
 'language':'ui/localization.js + translations.js — owned labels; English fallback for new strings, full RTL pending',
 'gameWindowTitle':'window_title.rs + game_launch.rs — owned Windows child PID and bounded title message; other OS unsupported',

 'crashReports':'game_launch.rs + launch_errors.rs — opt-in atomic local redacted startup-failure reports; no upload',
 'downloadThreads':'game_install.rs — bounded actual concurrent asset/library downloads',
 'keepLauncherOpen':'game_launch.rs — keeps visible or minimizes after spawn',
 'autoCloseOnGameExit':'game_launch.rs — closes native launcher after child exit',
 'showConsole':'ui/library-extras.js — dedicated Minecraft console on started event',
 'closeToTray':'runtime.rs + main.rs — native tray and close handler; desktop acceptance pending',
 'startWithWindows':'runtime.rs — own autostart entry; desktop acceptance pending',
 'notifications':'runtime.rs — native notification gate',
 'launchSound':'ui/library-extras.js — user-gesture-unlocked WebAudio chime',
 'autoOptimize':'optimization.rs — options policy with original backup; false does no writes',
 'cullLeaves':'optimization.rs — OptiFine ofTrees only; no vanilla/Sodium performance claim',
 'fastMath':'optimization.rs — OptiFine ofFastMath only',
 'jvmPreset':'jvm.rs + game_launch.rs — balanced/low/high/custom defaults, custom collectors/agents preserved',
})
settings_src = (RUST / 'src-tauri/src/settings.rs').read_text(encoding='utf-8').split('fn number')[0]
settings = []
for snake in re.findall(r'pub (\w+):', settings_src):
    if snake.startswith('skin_token') or snake == 'clear_skin_token': continue
    words=snake.split('_'); key=words[0]+''.join(word.title() for word in words[1:]); key='discordRPC' if snake=='discord_rpc' else key
    settings.append({'key':key,'status':'Active' if key in active else 'Saved only','evidence':active.get(key,'No runtime consumer found in native/UI action code or reused agent source; persistence is not behavior.')})
assert len(settings)==26 and sum(item['status']=='Saved only' for item in settings)==0

evidence_files = [ROOT/'main.js', ROOT/'launch-worker.js', ROOT/'prepare-worker.js', *sorted((RUST/'src-tauri/src').glob('*.rs')), *sorted((RUST/'ui').glob('*.js'))]
data = {'date':'2026-11-05','scope':'Static source parity plus recorded native and fresh fixture tests; not complete end-to-end certification',
        'electronUniqueChannels':len(channels),'electronRegistrations':sum(map(len,channels.values())),
        'rustRegisteredCommands':len(registered),'literalFrontendCommands':len(invokes),'unregisteredFrontendCommands':sorted(invokes-registered),
        'statuses':dict(Counter(item['status'] for item in coverage.values())),
        'channels':[{'name':name,'electron':channels[name],**coverage[name]} for name in channels], 'settings':settings,
        'sourceSha256':{str(path.relative_to(ROOT)).replace('\\','/'):hashlib.sha256(path.read_bytes()).hexdigest() for path in evidence_files}}
(RUST/'output/parity-audit.json').write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')

intro = '''# Electron → Rust migration audit

Verified 2026-11-05. **Migration is incomplete; keep Electron available.** This report distinguishes source implementation, exposed UI and recorded test evidence. This audit includes the latest Home and premium cosmetic work, alongside the recorded Windows loader validation.

Compared all90 unique Electron IPC channels (91 registrations because get-app-settings has two registrations), native command handlers, frontend calls, settings consumers, launch/agent dependencies and cosmetic/update paths. Channel counts are not feature-completion percentages: commands have different scope and several Rust commands replace multiple Electron channels.

## Release-blocking work

1. **Fresh installation validation:** official Minecraft/Fabric download/resolve and managed Java21 install/probe passed. New installed-game renderer/gameplay acceptance, other Java majors/OSes still pending. Quilt and the remaining version/OS matrix still need native validation.
2. **Forge/NeoForge and version matrix:** Official modern and legacy Forge installer formats are supported. Windows native install and renderer startup passed for Forge 1.8, 1.8.9, Forge/NeoForge 1.21.1, NeoForge 1.20.1, and Legacy Fabric 1.8/1.8.9. Broader version, gameplay and OS validation remain.
3. **Cosmetics:** native bounded GIF decode/save is implemented. Collection slot save/equip/remove now exists. Native premium removal/credential cleanup exists; public Mojang textures now fill missing premium previews. Batch image import populates the shared legacy slot schema. Legacy token cleanup, live lookup, upload and refresh, and animated gameplay acceptance remain.
4. **Mod maintenance:** Installed Mods now reports known conflicts and loader mismatches. Full dependency and binary compatibility checks remain. Protected nested Sodium opt-out migration now preserves original backup/payload bytes; signed/hard-dependent packages rejected. Installed update and Bat/Electron modpack import/export now exist with rollback/opt-out fixtures; live delivery and native dialog acceptance remain. Details/gallery/version/file chooser and sorting exist.
5. **Settings/integrations:** All26visible preferences now have scoped runtime consumers. Arabic uses RTL document layout and additional launcher labels reuse the Electron translations. Remaining Rust strings, native Discord acceptance and non-Windows custom titles remain. crashReports saves local redacted launch reports. Tray/autostart/notification code needs native desktop acceptance; optimization is policy parity, not evidence of FPS improvement.
6. **Public updating/platform release validation:** startup auto-download/verification/install/restart is implemented and mocked. Native signature/tamper/version-substitution tests passed. Rust channel is still unpublished/disabled; Mac/Linux native packages now build successfully. Native installation and real two-version replacement/restart are unverified. Persistent result reporting and same-version manual retry now exist. Real installer replacement and recovery acceptance remain.

## Other missing workflows

Loadout/account ordering, loadout import/export, folder/Java browsing, automatic Java installation and welcome are present in their normal sections. User explicitly removed Tools: do not reintroduce its overview/server/screenshots/diagnostic/preset page. Launch and mod presets are available in Loadouts and Installed Mods. Keep the Minecraft screenshot gallery, crash analysis, game health and FPS benchmark controls out of Settings at the user’s request. Other retained helper implementations do not count as exposed UI parity. Windows cache maintenance is now exposed under Settings → Files & Network. macOS/Linux cache clearing remains.

**Shared-folder correction:** both current Electron launch and Rust launch use a global game root/mods folder. Electron has an instances-path helper, but its launch code does not establish full per-loadout isolation. Do not present isolation as an existing working feature Rust lost; The loadout editor now opens existing legacy instance folders separately.

## Working core and evidence limits

Profile metadata CRUD/select/copy/favorite, offline accounts/switching, official version catalog/filter/search, local PNG Wardrobe and saved-frame preview, core Modrinth install dependencies/checksums, installed icons/switches/remove, custom JVM composition, selected Java discovery, approved Home/news, native window controls and installed Vanilla/Fabric launch exist. See per-row scope; implemented does not mean every OS/live account case has passed.

Latest full regression:92 passed, 14 opt-in skipped, zero failures, 106 tests total. Separate Forge and Legacy Fabric 1.8/1.8.9 and NeoForge 1.20.1 install and renderer startup checks passed. Explicit real-Java rejected-option diagnosis passed0.15s. New screenshot viewer/loadout launch and structured startup-failure UI fixtures passed. Read-only live Modrinth details returned22compatible versions; Wardrobe slot and Mods chooser UI fixtures passed. Native Java21 install and fresh Fabric install/resolve passed in isolated D: fixtures. No full gameplay proof follows. Home release navigation, offline cache, removal of Partner servers and Recent launches from Home, and preset entry points passed a local UI fixture. Prior native startup/crypto and UI evidence is recorded in RUST-LAUNCHER-AGENT.md and output evidence files; startup update UI, premium refresh and release replacement must not be confused with one another.

Literal frontend IPC names are checked against the native registry by the generator. Counts in output/parity-audit.json are wiring checks, not correctness or platform acceptance. tools_action routes retained section helpers; the Tools page is removed.

## Settings: persistence versus behavior

`Active` means a consumer exists; it is not a claim of complete platform/live integration validation. Both keys and unknown config values are preserved. Skin-service credential infrastructure is separate from the26visible preferences and is not printed here.

| Preference | Current behavior | Evidence/limitation |
|---|---|---|
'''
rows = ''.join(f"| `{item['key']}` | {item['status']} | {item['evidence']} |\n" for item in settings)
appendix='''
## Complete IPC coverage ledger

Implemented = native equivalent and relevant fixture/user evidence within the stated scope. Partial = some behavior or release validation missing. Unverified = implementation exists but required live acceptance missing. Missing = no corresponding current workflow found. These are manually reviewed equivalences, not name matching.

| Electron channel (source line) | Rust coverage | Source evidence | Scope / remaining work |
|---|---|---|---|
'''
for name,item in coverage.items():
    links=', '.join(str(entry['line']) for entry in channels[name])
    appendix+=f"| `{name}` ({links}) | {item['status']} | {'; '.join('`'+v+'`' for v in item['evidence'].split('; '))} | {item['note']} |\n"
appendix+='\nRegenerate with `python launcher-rust/scripts/audit-parity.py` after reviewing the explicit mappings against changed implementations. `output/parity-audit.json` contains source hashes and machine-readable coverage; it contains no user configuration, tokens or signing-key contents.\n'
(RUST/'PARITY-AUDIT.md').write_text(intro+rows+appendix,encoding='utf-8')
print(json.dumps({key:data[key] for key in ['electronUniqueChannels','electronRegistrations','rustRegisteredCommands','literalFrontendCommands','unregisteredFrontendCommands','statuses']},indent=2))
