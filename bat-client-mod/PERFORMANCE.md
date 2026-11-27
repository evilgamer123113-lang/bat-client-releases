# Built-in performance

Performance scheduling is part of the client engine, not a module. The old Smooth Chunk Updates card is removed; old saved enable/budget values are retained as harmless legacy data and do not disable the engine. Existing visual-quality and optional module choices remain unchanged.

- Vanilla chunk uploads are processed in bounded time slices while preserving queue order and eventual progress. Nearby synchronous rebuilds use a smaller allowance; excess rebuilds go through Minecraft's existing asynchronous scheduler.
- Scheduling responds to observed world-render CPU work, excluding measured synchronous chunk work. Pressure reduces allowances quickly; recovery is gradual. Attack/local-hurt activity reduces the chunk-work allowance for three seconds.
- A running rebuild or GPU upload cannot be preempted; one expensive task may still exceed the soft allowance. Terrain generation, packet processing, disk I/O, GPU stalls, server tick lag and garbage collection can still cause hitches.
- Sodium, Embeddium, Rubidium and OptiFabric retain ownership of their chunk pipelines. The Bat chunk mixins yield rather than stacking an incompatible scheduler on top. Other Bat features remain available.
- Native HUD layouts are cached until configuration or viewport size changes. Editor state is separate, and previews/profile loads invalidate the cache.
- World entry no longer constructs a hidden software web menu. Explicit opening creates it, then reuses it. First opening can therefore take longer than a cached opening.
- Normal mouse arguments pass unchanged outside Zoom/Freelook. There is no new gameplay input queue, artificial delay, input thread, attack scheduling or packet manipulation. Hardware/OS/render-loop latency still applies.

Follow-up from a live flight recording on a four-core i5-4590:

- The experimental fixed background-worker reservation was removed after the user reported slow chunk delivery. Minecraft's normal worker parallelism and explicit `-Dmax.bg.threads` settings are preserved.
- Cubic sky/fog color sampling uses primitive accumulators for the same 216 sample positions and exact interpolation order, eliminating temporary multiply/add vectors. A test against the real unmodified Minecraft sampler verified bit-identical RGB output at 1,024 randomized positions and the same fetch order. With a fixed test fetcher, allocation measured 8,680 versus 40 bytes/call; this measures that function, not total game allocations or FPS. Renderer/ModernFix replacements retain control when present.

The implementation does not set JVM flags, select a garbage collector, call forced GC, alter thread priority, replace the cosmetics agent, force raw input/VSync/FPS settings, change render distance, or modify saved launcher arguments. The isolated compatibility fixture explicitly selects SerialGC and co-loads the existing agent; that fixture's arguments are not launcher defaults.

## 2026-10-28 profiling follow-up (validation in progress)

The companion agent now decodes local animated-cape files on one daemon worker, with at most16 pending players and disposal of unused completed frames. It returns the original skin/cape while decoding. Only render-thread callers create/register/upload the game texture. Real Fabric1.21.1 intermediary tests loaded57frames off-thread, registered the texture and passed the existing frame-update smoke. This targets a measured multi-second world-entry hitch; it is not a terrain-throughput claim.

The current mod candidate removes only the redundant `Thread.yield()` after Minecraft's existing frame limiter during active world gameplay. Menus retain the call; the separate world-loading wait loop is untouched. FPS/VSync settings, worker parallelism, thread priorities and chunk scheduling remain unchanged. It yields this hook to ModernFix/ThreadTweak/SmoothBoot. A bytecode contract checks the actual pinned1.21.1 callsite; native throughput/frame-time comparison is required before installing it.

Diagnostics run in disposable D: worlds, with both agents and the actual launcher mod/config/resource-pack set. The new route records settled terrain, then480blocks of travel at16blocks/sec, frame percentiles and received-chunk counts. `backgroundDiagnostic=true` explicitly disables DynamicFPS only in that private test; its numbers are not advertised foreground FPS. Process CPU samples reveal substantial external contention in several runs. The experimental biome-offset cache and extra Lithium dependency were rejected, and the priority7 test was rejected because smoother frames came with fewer delivered chunks. The biome experiment and mapping are preserved privately on D:, not shipped.

## Bundled renderer

The normal Fabric 1.21.1 build includes the unmodified, checksum-pinned **Sodium 0.5.11+mc1.21** renderer as a separately identifiable nested mod. It is active automatically and has no Bat module card/toggle. Bat yields its vanilla chunk hooks to Sodium instead of stacking schedulers. Fabric API remains required. The renderer does not expand Bat's Minecraft-version support.

The renderer is third-party software by CaffeineMC, licensed LGPL-3.0-only. Its original licenses, notice and exact corresponding upstream source/build scripts are included under `META-INF/batclient/third-party/sodium/`; these are public third-party sources, not Bat Client sources. Bat's Java obfuscation and encrypted in-memory UI remain intact. A developer can build the renderer-free variant with `-PwithoutBuiltinRenderer`; it is not the normal performance build.

Sodium 0.8.13 failed the project's Yarn development environment and is not bundled. Noisium was also tested separately but is not bundled because the short local flight diagnostic did not show a clear benefit. No extra optimizer is silently downloaded into a player's mods folder at runtime.

## Checks

```powershell
node scripts/verify-builtin-performance.cjs
.\gradlew.bat build --offline --console=plain
```

The first command tests adaptive pressure/recovery, combat limits, renderer ownership, queue progress/order, HUD cache invalidation and settings/profile persistence. The protected build retains Java obfuscation and AES-GCM memory-only UI assets.

Native integration fixtures live only under `build/builtin-performance-native*`, use disposable saves, and do not open or modify `run/saves`. Native results and logs must be checked separately from Gradle's process exit. The last-view native shutdown failure was reproduced outside Minecraft and is now avoided by one renderer-owned blank 1x1 lifetime view. It has no HTML/page, JS bridge, GL texture or input, is created once with the native renderer, and remains until natural process teardown because the binding has no renderer-dispose API. Ordinary menu views still release normally. Both the protected Fabric fixture and full-source Forge fixture passed gameplay checks and exited cleanly with this workaround; longer-session validation remains pending. Do not call a failed native exit clean, claim live visual cape verification, or advertise measured FPS gains without a controlled comparison.

The current game implementation is Fabric 1.21.1. The wider version-port matrix is resumed; Forge 1.21.1 full-source integration is internal and still needs protected distribution/renderer packaging and broader gameplay checks. Other versions remain unported.

The protected Fabric artifact explicitly nests Kotlin 2.3.10 alongside the native bindings. The protected-runtime fixture stages the same non-Minecraft nested libraries from the verified distributable and removes loose Kotlin/Ultralight libraries from its developer classpath, so missing player dependencies cannot be masked. Settings writes tolerate brief real Windows sharing locks with bounded retries (at most190ms scheduled backoff) while retaining old and pending data if replacement fails; these retries are only used by explicit configuration persistence.
