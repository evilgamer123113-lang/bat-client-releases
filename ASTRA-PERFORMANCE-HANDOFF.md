# Bat Client performance investigation — handoff prompt

Continue the actual Bat Client engineering work. The immediate goal is to reduce severe FPS drops, missing/slowly arriving terrain, and stutter while flying and entering worlds. Edit the implementation and verify meaningful results. Do not stop at suggestions, repeated readiness questions, or isolated microbenchmarks. Do not promise literally zero frame drops.

## Workspace and instructions

Windows workspace: `C:/Users/Noor-ul-Islam/Desktop/Raven-Client`.
First read `AGENTS.md`, `AGENT.md`, and the latest entries of `BAT-CLIENT-MOD-AGENT.md`; inspect the actual current diff and files before changing anything. These files contain historical, superseded states: newest evidence wins. Do not print secrets or entire account/launch configuration from notes or logs.

- `AGENT.md` documents the Electron launcher and website.
- `BAT-CLIENT-MOD-AGENT.md` is the canonical mod implementation, test, failure and handoff record.
- Mod: `bat-client-mod/`; cosmetics agent: `bat-client-agent/`.
- Current performance target is the actual protected Fabric Minecraft **1.21.1** client. Do not confuse 1.21.1 with 1.21.11.
- Fabric/Forge ports and the requested lighter Rust launcher come AFTER current performance work. A Rust launcher does not turn Java Minecraft into a Rust game or automatically multiply game FPS.

## User observations and constraints

The user historically reported roughly 500+ FPS with launcher+agent without Bat mod, 400+ in mod runClient, and around 150 combined. They explicitly said same spot, resolution and first-person view. A launcher hidden WebGL/viewer CPU issue was repaired and performance improved, but sustained chunk-loading stutter remains. The latest experimental local-batch build showed the user **55–77 FPS**, visibly missing terrain and loading hitches. Do not dismiss these reports as merely different resolution or assume every comparison looked at the sky.

The user permits launching Minecraft and running code/log/profiler tests, but **prohibits PC-control tools**: no Computer Use, SendInput, window activation, keyboard/mouse automation or substitute UI-control helpers. The user manually focuses the game. Windows+L/logouts prevent a valid foreground performance result; a background functional check is not an FPS pass. Do not keep cycling through identical focus timeouts.

Preserve worlds, accounts, profiles/settings, custom JVM arguments, cosmetics, skins/animated capes, protected packaging, backups and useful evidence. The user removed Sodium and does not want it silently reinstalled, bundled or enabled. Do not force renderer/GC/driver/security changes, cap FPS against their instruction, close unrelated apps or change their priorities. The user previously opted to keep background apps running; measure contention and report its limits. No publishing, release uploads, website/updater/production changes, deletion, or other-project modifications.

Use D: for caches/evidence because C: had a disk-space crisis. One Minecraft JVM at a time. Use dummy accounts and private disposable world copies, not the user's worlds. Never expose real JVM command lines/tokens or upload raw JFRs/private mappings.

## Current code and installed state

Installed mod remains EFB7D3B2A081F4F8B214396D394B161124928FB16A86E4770FC1533ECFC231B4 at `%APPDATA%/bat-client/minecraft_runtime/mods/bat-client-mod-0.1.0.jar`.

Installed agent is AEA4AC92F5C57BEA8B9548F2C95DA01CB8EB43B19CD238C5474DDE7C8989DE9B at BOTH `%APPDATA%/bat-client/bat-cosmetics.jar` and `%LOCALAPPDATA%/Programs/Bat Client/resources/bundled/bat-cosmetics.jar`. The launcher can restore the bundled copy, so inspect both if changing installation. Agent delegates legacy chunk-option/FPS-estimate work on `batclient.performance.owner=mod`, retains original one-time render-thread priority 7, and decodes cape images asynchronously while GPU upload remains on the render thread. Coordination and native cape animation checks pass. Earlier removal of the priority boost regressed performance and was rejected.

Latest SOURCE/`build/libs` protected candidate is **69F693914A0EC42BB4AF6D55B49B3E65E386B7E9898568B885C8775F3A84027A**, UNINSTALLED, at `D:/BatClientBuildCache/isolated-mesh-20261030/candidate-mod.jar`, with matching PRIVATE mapping. It is not a verified performance improvement.

Active candidate changes:

1. `AdaptiveChunkBudget` / `BuiltinPerformance`: render-time and focused start-to-start frame-pressure estimates; bounded recovery for genuinely deferred uploads when world rendering has spare time. Not a worker/FPS cap.
2. `FrameWorkBudget`: predicts recent task cost to avoid admitting a second costly job just before a slice expires; always permits first-job progress. Upload/rebuild hooks record task duration.
3. `SmoothChunkUploadMixin`: bypasses upload budgeting when ChunkBuilder is stopped so vanilla shutdown can drain queued uploads.
4. `ChunkMeshExecutorMixin` / `MeshWorkExecutor`: actual vanilla WorldRenderer supplies `Util.getMainWorkerExecutor()` to ChunkBuilder, which uses it for mailbox and mesh `CompletableFuture.supplyAsync`. New hook redirects ONLY mesh compilation supplyAsync into a per-builder pool: one worker on four cores, maximum two on larger CPUs, daemon/normal priority, actual mod class loader, idle timeout. Vanilla mailbox, buffer limits, world-generation pool and GL upload thread remain unchanged. Graceful close drains accepted work; late submissions fall back to original executor. This may add CPU contention or slow generation: prove the tradeoff. The mailbox is STILL shared.
5. `GameLoopYieldMixin` skips only the post-limiter yield in focused gameplay, Unlimited, VSync off. Keep other limiter/loading/background behavior. Minecraft's 260 setting is **Unlimited**, not a 260-FPS cap. Known alternative renderers retain ownership of relevant hooks.

`LocalChunkBatchMixin` was PARKED and removed from active registration after unproven results and negative feedback. Its source/tests and F2 checkpoint remain, but the latest candidate restores vanilla local packet-batch selection. Do not reactivate blindly. Earlier always-async rebuild, biome-offset cache, fixed global worker limit, and priority-removal experiments also failed acceptance; read notes rather than repeating them.

## Evidence and unanswered questions

Private evidence lives under `D:/BatClientBuildCache/`. All runs below used a private seed-12345 world, render distance 12, simulation 5, 1680x987, Unlimited, a 609-loaded-chunk settled start, and a cliff-safe 480-block creative flight. CPU/background workloads differed, so comparisons are observational, not causal proof.

| Run | Mean FPS | p95 / p99 ms | >50ms frames | Route / terrain settle |
|---|---:|---|---:|---|
| `presentation-budget-20261029/control-resumed-20261030` | 37.39 | 94.548 / 284.490 | 154 | 38.09s / 28.91s |
| `task-cost-budget-20261030/native-flight` | 105.56 | 25.790 / 53.646 | 53 | 41.45s / 48.83s |
| `backlog-budget-20261030/gameplay-test-2` | 81.28 | 27.134 / 51.695 | 36 | 37.66s / 37.25s |

IMPORTANT: the last run's once-per-second `terrain-progress.json` showed late flight/settling with **pC=0, pU=0, available buffers=4, renderer complete for AVAILABLE terrain**, while loaded chunks rose from about 230 to 609 and receipts from 950 to 1346. Therefore much of the remaining wait is generation/delivery, not a perpetually blocked upload queue. Earlier JFR samples show substantial worker noise/aquifer generation work; distinguish startup from flight before attribution. Main-thread wall gaps sometimes had almost no thread CPU and insufficient GC overlap to explain them: also investigate scheduler/GPU waits and contention. Do not attribute all pauses to GC without correlation.

Latest `isolated-mesh-20261030/native-functional` passed actual protected startup, actual agent/authlib/cape, 609 chunks/render completion, new mesh hook active and old local batch inactive, clean saved exit. JFR ThreadStart confirms `Bat chunk mesh-1` actually ran. This is functional proof, **not** a foreground FPS/throughput pass. Latest foreground mesh run timed out without focus and is INVALID. No owned test game remains at handoff; verify fresh.

Exact binary mesh control: `isolated-mesh-20261030/control-mod.jar`, SHA 87D358BC0B8AE939B2F06B05D6610E6F78639A1A22EBF2C63B1CFAEA35864C28. Only mesh-Mixin registration differs; every other uncompressed entry is identical. Both include shutdown cleanup and no local batch limiter.

## Build and tests

JDK: `C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot`.
From mod folder: `& ./scripts/gradle-secondary-cache.ps1 build --offline --console=plain`. Helper uses `D:/BatClientBuildCache/gradle-home`.

Full protected build passed. Meaningful checks include 1,200 actual executor jobs with shared-pool isolation, class loader/priority, shutdown and close-race/late-cleanup behavior; 129 adaptive budget checks; 16,081 parked nearest-selector checks; existing HUD/profile/file-lock persistence, actual Minecraft cubic math, exact-version bytecode contracts and packaging protection. These are not evidence of FPS gains or working version ports.

Private native fixture: `scripts/LauncherComparisonSmoke.java`; runner: `scripts/run-launcher-comparison.py`. Remap with `remapLauncherComparison -I scripts/launcher-comparison.init.gradle`. Read their current code and flags before use. Use NEW dedicated D: runtime directories. Same template: `D:/BatClientBuildCache/terrain-coordination-20261029/warmup-validation`.

Foreground comparisons require ten continuous focused seconds, settled starting terrain, 20s stationary then flight, and post-route terrain completion. Record p95/p99, long frames per second, receipts/loaded terrain, route/settle duration, queue state, CPU/GPU/GC and exact hashes. No simultaneous builds/JFR exports while measuring. Failure/interruption files invalidate the result; partial traces remain diagnostic only. Prepare-world functional mode bypasses focus for startup checks and must never be reported as performance success.

## Research and next action

Consult primary upstream sources and exact 1.21.1 behavior, not generic JVM-tuning lists:
- https://github.com/CaffeineMC/sodium/issues/691
- https://github.com/CaffeineMC/sodium/blob/dev/common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager.java
- https://github.com/RelativityMC/C2ME-fabric/discussions/292

These motivate bounded task/upload admission without starving terrain, and caution that too much parallelism can hurt rendering while too little hurts chunk throughput. Current dev code is not automatically compatible with 1.21.1; respect licenses and renderer opt-outs. Do not blindly install more optimization mods or copy an incompatible renderer.

Start by auditing the latest candidate and evidence. Form one testable bottleneck hypothesis, make a concrete implementation change where justified, and test the complete agent/mod/launcher path with exact provenance. Investigate generation/delivery, meshing, packet handling, GPU uploads, GC and scheduling separately. Retain changes only when frame behavior improves without unacceptable terrain debt or broken cleanup/cosmetics. Revert regressions. Do not optimize sky FPS or hide terrain to fake a win. Ask only for genuinely necessary user action; do not repeat unproductive focus-timeout loops. Save exact code changes, results, failures and remaining work to the canonical mod notes. Do not claim completion until the user's practical stutter/terrain problem is convincingly improved.
