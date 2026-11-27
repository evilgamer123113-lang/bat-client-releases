# forge 1.18.2 - protected production native smoke PASS (r1 / a1)

- **Protected build (`r1`)**: `gradlew.bat build --rerun-tasks` (production) -> `BUILD SUCCESSFUL`, `PROTECTED_FORGE_ARTIFACT_OK`, `SHA256=0E8CC56CFF7609405F7F612792A1457FCB81F132318C292AA663F18D9E497C01`, size `22407621`.
- **Audits**: `audit-port-mixins.py` -> `47 targets; 0 issues`; `audit-constructor-callbacks.py` -> `0 callbacks; 0 issues`.
- **Visual-parity smoke (`a1`)**: `run-forge-production-smoke.py --version 1.18.2-forge-40.3.0 --runtime D:/BatClientBuildCache/mod-native/forge-1.18.2-visual ... --visual-parity` -> 69 PNGs captured cleanly (`main-menu.png` white-screen bug fixed via `adapter_1192.py` explicit `RenderSystem` state in `WebWindow.drawRegion`; process exceeded 240s only during overworld chunk-save at shutdown).
- **Fixes**: `ForgePortEntrypoint.java`, `ForgeFullSmoke.java`, `scripts/adapter_1182.py` (Forge `40.3.0` events/GUI), `scripts/adapter_1192.py` (`WebWindow.drawRegion` `RenderSystem` state), `scripts/run-forge-production-smoke.py` (pre-1.19 `natives-windows` classifier extraction).
- **Visual review**: `main-menu.png` (369,138 B), `game-overlay.png`, `pause-menu.png`, `hud-editor.png`, `world-hud.png` all crisp. See `../../../../BAT-CLIENT-MOD-AGENT.md` and `../VISUAL-PARITY.md`.
