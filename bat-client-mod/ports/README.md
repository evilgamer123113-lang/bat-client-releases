# Version ports

The requested matrix is in `targets.json`: 15 Minecraft releases, each with Fabric and Forge (30 combinations). The 1.8 and 1.8.9 Fabric targets mean **Legacy Fabric**, not official modern Fabric.

`loaders.lock.json` records exact upstream loader/mapping coordinates found on 2026-10-26. This proves upstream availability only. It does not mark a Bat Client port complete or change dependency versions in the existing project.

## Current implementation

Four internal full-source builds now have native evidence: **Fabric/Forge 1.21.1 and Fabric/Forge 1.20.6**. Forge 1.21.1 and 1.20.6 pass real remapped protected production runtime tests; Fabric 1.20.6 passes protected named runtime with actual nested libraries and cosmetic premain/custom SerialGC. Broader feature coverage, production cosmetic visuals and long-session certification remain pending. Stable-release gates remain closed. The other 26 combinations are unported; no cross-version mod has been publicly published.

The user **resumed cross-version work on 2026-10-26**, after the renderer performance build. Completed foundation:

- Shared `PlatformEnvironment` service for game/config directories, loaded-mod discovery and development mode. The existing mod uses the Fabric provider; saved paths are preserved.
- Separate modern and 1.8-era Forge environment providers, compiled against actual upstream loader JARs for every requested version. These providers do not include mod entrypoints or game/render/input/network ports.
- Java 8-compatible shared armor/potion/item warnings, health formatting, combat tracking, zoom interpolation and work budgets.
- Exact existing JAR Minecraft dependency `1.21.1`; build guards reject attempts to retag these sources for other versions.

The `forge-1.21.1` probe is a foundation test, not the full feature set. `forge-1.21.1-full`, `forge-1.20.6` and `fabric-1.20.6` compile the actual shared feature sources with genuine loader/game adapters. Forge 50 needs real Java 17-compatible Mixin bytecode even though its game/toolchain require Java 21; it uses native tooltip factories and avoids generated argument classes that fail under that loader. Named protected Forge 50 testing still has duplicate development mod discovery, but the real production JAR loads both nested libraries and passes native gameplay checks. See the canonical mod notes for exact evidence and limitations.

Use `scripts/gradle-secondary-cache.ps1` for further cold ports after its D: cache preparation is verified. It only supplies an explicit Gradle user home on D:, without moving the workspace or changing global environment/JVM settings. Existing C: cache remains intact. This prevents new version downloads from filling C: again.

## Verification commands

From `bat-client-mod`:

```powershell
node ports/targets.cjs list
node scripts/resolve-port-loaders.cjs
node scripts/verify-port-environments.cjs
node scripts/verify-port-core.cjs
node scripts/verify-builtin-performance.cjs
node ports/targets.cjs build fabric-1.21.1
```

The network resolver refreshes only its checked lock, using official Fabric, Legacy Fabric and Forge metadata. The environment check verifies upstream SHA-1s, compiles actual providers against upstream APIs, and writes detailed evidence under `build/port-loader-api/results.json`. It does not launch or install anything in a player's game.

Unported build targets deliberately fail rather than generating renamed incompatible JARs. Stable-release approval requires separate runtime verification; broader gameplay, longer sessions and artifact checks remain required for stable-release certification.

## Remaining port work

Each game family needs its own mapped rendering, screens, item/block components, input/keybinding, player/session/authlib, networking and Mixin hooks. Pre-1.20.5 item NBT differs from modern data components; 1.8 uses LWJGL 2; newer render-state APIs differ from 1.21.1. UI binding bytecode, JNI dependencies, native uploads and protection also require validation on legacy Java. Sharing web assets or environment providers does not solve those differences. Do not copy the 1.21.1 Mixin list unchanged into another target.

Do not publish version support or add automatic launcher downloads until that target's protected artifact and native gameplay checks pass. Launcher/website work is separate from this mod port.
