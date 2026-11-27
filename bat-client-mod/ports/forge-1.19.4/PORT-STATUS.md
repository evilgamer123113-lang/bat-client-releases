# Forge 1.19.4 port status

Target: Minecraft 1.19.4, Forge `1.19.4-45.4.0`, Yarn `1.19.4+build.2:v2`, Java 17 bytecode.

## Verified
- Protected build `r3` (2026-11-12): `BUILD SUCCESSFUL`, `FORGE_WEB_MODULE_OK`, `FORGE_47_MIXIN_BYTECODE_OK`, `PROTECTED_FORGE_ARTIFACT_OK`.
  - Command: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gradle-secondary-cache.ps1 -p ports/forge-1.19.4 build --no-daemon --console=plain`
  - Log: `D:/BatClientBuildCache/mod-ports-20261108/forge-1194-r3.log`
  - JAR: `ports/forge-1.19.4/build/libs/bat-client-forge-1.19.4-INTERNAL-UNVERIFIED-0.1.0-forge-internal.jar`
  - JAR SHA256: `26B004E567C76A0DC9F15F71DBE2E0C27DF42FC8FFDB529C01ED6BB34AC6E51F`
- `scripts/audit-port-mixins.py`: 47 targets, 0 issues. `scripts/audit-constructor-callbacks.py`: 0 callbacks, 0 issues.
- Adapters: `scripts/adapter_1194.py` (`EntityRenderersEvent.AddLayers.getSkin(skin)` in `ForgeClientResources.java`) + `scripts/adapter_1204.py` (`ForgeHudMixin` targets `{InGameHud.class, net.minecraftforge.client.gui.overlay.ForgeGui.class}` so live HUD renders when `ForgeGui.render` overrides `InGameHud.render`).
- Isolated native visual-parity smoke `a2` (2026-11-12): exited `0` with `BAT_VISUAL_PARITY_CAPTURE_OK` using `bat-cosmetics.jar`, `-XX:+UseSerialGC`, and launcher-parent fixture `D:/BatClientBuildCache/mod-ports-20261108/launcher-parent-fixture/bat-client-next.exe`.
  - Runtime: `D:/BatClientBuildCache/mod-native/forge-1.19.4-20261112-a2` (after `a1` exposed missing `ForgeGui.render` hook on `world-hud.png`)
  - Log: `D:/BatClientBuildCache/mod-ports-20261108/forge-1194-native-a2.log`
  - Captures: 69 screenshots in `D:/BatClientBuildCache/mod-native/forge-1.19.4-20261112-a2/visual-parity`
  - Six-screen review completed (`main-menu.png`, `game-overlay.png`, `mod-manager.png`, `pause-menu.png`, `hud-editor.png`, `world-hud.png`); rounded HUD widget plates, live in-world HUD widgets, and custom crosshair render cleanly; module card text in `mod-manager.png` has the same dimmer grey tint tracked on 1.21.4–1.21.11.

## Not yet verified
- Dedicated gameplay behavior, persistence, cosmetics, packaged-launcher, and sustained performance checks.
