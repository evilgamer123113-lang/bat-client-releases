# Forge 1.21.1 — protected production visual-parity smoke PASS (r2 / a2)

- **Protected build (`r2`)**: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gradle-secondary-cache.ps1 -p ports/forge-1.21.1 build --no-daemon --console=plain` -> `BUILD SUCCESSFUL`, `PROTECTED_FORGE_ARTIFACT_OK`, `SHA256=2410DAD429586EC5FAFE906764D4419404A4B15DA470BE6FC7A5165E56098534`, size `22397857`, log `D:/BatClientBuildCache/mod-ports-20261108/forge-1211-parity-r2-build.log`.
- **Audits**: `audit-constructor-callbacks.py` -> `0 callbacks; 0 issues`.
- **Visual-parity smoke (`a2`)**: `prepare-forge-production-smoke.py` (`52.1.20`) + `run-forge-production-smoke.py --runtime D:/BatClientBuildCache/mod-native/forge-1.21.1-20261113-a1 --version 1.21.1-forge-52.1.20 ... --visual-parity` -> `BAT_VISUAL_PARITY_CAPTURE_OK`, 69 PNGs, log `D:/BatClientBuildCache/mod-ports-20261108/forge-1211-parity-a2.log`.
- **Visual review**: All 6 screens (`main-menu.png`, `game-overlay.png`, `mod-manager.png`, `pause-menu.png`, `hud-editor.png`, `world-hud.png`) crisp. See `../../../../BAT-CLIENT-MOD-AGENT.md` and `../VISUAL-PARITY.md`.
