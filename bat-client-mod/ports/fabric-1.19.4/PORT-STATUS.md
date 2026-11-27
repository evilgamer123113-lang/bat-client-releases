# Fabric 1.19.4 port status

Target: Minecraft 1.19.4, Fabric Loader 0.20.1, Yarn `1.19.4+build.2:v2`, Fabric API `0.87.2+1.19.4`, Java 17 bytecode.

## Verified
- Protected build `r5` (2026-11-12): `BUILD SUCCESSFUL`, `PROTECTED_WEB_OK`, `PROTECTED_FABRIC_PORT_OK`.
  - Command: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gradle-secondary-cache.ps1 -p ports/fabric-1.19.4 build --no-daemon --console=plain`
  - Log: `D:/BatClientBuildCache/mod-ports-20261108/fabric-1194-r5.log`
  - JAR: `ports/fabric-1.19.4/build/libs/bat-client-fabric-1.19.4-INTERNAL-UNVERIFIED-0.1.0-fabric-internal.jar`
  - JAR SHA256: `9D2D704CC269580591005E91554FCEA559B2443A6B1FDA1D415D0E00A0041E7A`
- `scripts/audit-port-mixins.py`: 47 targets, 0 issues. `scripts/audit-constructor-callbacks.py`: 0 issues.
- Adapters: `scripts/adapter_1194.py` + `ports/mc1.19.4/src/com/batclient/platform/mc1194/DrawContext.java` (javap-verified MatrixStack APIs, static `DrawableHelper.enableScissor` callback in `InventoryScissorAnimationMixin`, static `InGameHud.drawTexture` redirect in `CustomCrosshairMixin`).
- Isolated native visual-parity smoke `a3` (2026-11-12): exited `0` with `BAT_VISUAL_PARITY_CAPTURE_OK` using `bat-cosmetics.jar`, `-XX:+UseSerialGC`, and launcher-parent fixture `D:/BatClientBuildCache/mod-ports-20261108/launcher-parent-fixture/bat-client-next.exe`.
  - Runtime: `D:/BatClientBuildCache/mod-native/fabric-1.19.4-20261112-a3`
  - Log: `D:/BatClientBuildCache/mod-ports-20261108/fabric-1194-native-a3.log`
  - Captures: 69 screenshots in `D:/BatClientBuildCache/mod-native/fabric-1.19.4-20261112-a3/visual-parity`
  - Six-screen review completed (`main-menu.png`, `game-overlay.png`, `mod-manager.png`, `pause-menu.png`, `hud-editor.png`, `world-hud.png`); rounded HUD widget plates and custom crosshair render cleanly; module card text in `mod-manager.png` has the same dimmer grey tint tracked on 1.21.4–1.21.11.

## Not yet verified
- Dedicated gameplay behavior, persistence, cosmetics, packaged-launcher, and sustained performance checks.
