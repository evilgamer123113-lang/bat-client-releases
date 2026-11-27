# Forge 1.21.8 internal port

Forge 58.1.26 with Yarn 1.21.8 build 1 and the shared 1.21.8 renderer adapter.

- Genuine patched game compiled and produced a protected internal JAR after migrating the lifecycle, resource, tick, screen, tooltip, and cancellable outline subscriptions to Forge EventBus 7. The world-render hook uses the actual projection matrix passed to the world renderer.
- The first compile found 12 Forge API errors. The protected rebuild passed in 52 seconds. Both logs are in `D:/BatClientBuildCache/mod-ports-20261108` as `forge-1218-first-compile.log` and `forge-1218-protected-a2.log`.
- The official installer completed in `D:/BatClientBuildCache/mod-native/forge-1.21.8-20261109-a1`. Installer SHA-1 `0b6c40f8a434391511df081bdc9def4451223a83` verified against Forge Maven. The original client and 126 cached libraries were verified, and the installer validated patched client SHA-1 `37543bb22046d962044f4ae26cd7b122b3639413`. The protected build with the shared pause blur fix is in progress. Native acceptance remains open.
- Native a1 passed with exit 0 and `BAT_VISUAL_PARITY_CAPTURE_OK`. Protected JAR SHA-256 `93f4ab4c36cf375af8f80d3bf2bc2686da6de86866dcabf682b94ef9bf6fc7cd`. Reviewed main menu, gameplay overlay, real Mod Manager, pause, HUD editor and live HUD from 69 screenshots in the runtime's `visual-parity` directory. Overlay text still appears dimmer than 1.21.1. This run uses the actual cosmetic agent and the launcher-parent fixture, with packaged launcher and full module behavior still open.
