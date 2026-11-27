// Run from the repository root: node_modules/.bin/electron bat-client-mod/scripts/hud-ui.test.cjs
// Isolated, hidden browser test of the production HUD assets; no game/config changes.
const { app, BrowserWindow } = require('electron');
const path = require('path');
const fs = require('fs');
app.disableHardwareAcceleration();
app.whenReady().then(async () => {
    const win = new BrowserWindow({ show: false, width: Number(process.env.HUD_TEST_WIDTH) || 1280, height: Number(process.env.HUD_TEST_HEIGHT) || 800,
        webPreferences: { partition: 'hud-regression-test', backgroundThrottling: false, offscreen: true } });
    const errors = [];
    win.webContents.on('console-message', event => {
        if (event.level === 'error') errors.push(event.message);
    });
    try {
        const page = process.env.HUD_TEST_PAGE === 'pause' ? 'pause.html' : 'overlay.html';
        await win.loadFile(path.join(process.env.BAT_WEB_TEST_ROOT || path.join(__dirname, '../src/main/resources/assets/batclient/web'), page));
        const result = await win.webContents.executeJavaScript(`(${async function () {
            let checks = 0;
            const assert = (ok, message) => { if (!ok) throw Error(message); checks++; };
            const near = (a, b, message) => assert(Math.abs(a - b) < 0.2, message + ': ' + a + ' vs ' + b);
            const rect = el => { const r = el.getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, height: r.height, right: r.right, bottom: r.bottom }; };
            let saved = '{"modules":{}}';
            window.batGetHudConfig = () => saved;
            window.batSaveHudConfig = raw => { saved = raw; };
            window.batAction = () => {};
            cfgLoad();
            window.batSaveHudModule = (id, rawPatch) => {
                const current = JSON.parse(saved), patch = JSON.parse(rawPatch);
                const previous = current.modules[id] || patch._initial || {};
                delete patch._initial;
                current.modules[id] = Object.assign({}, previous, patch);
                if (patch.set) current.modules[id].set = Object.assign({}, previous.set, patch.set);
                saved = JSON.stringify(current); return saved;
            };
            openModSettings('zoom');
            const persistedLevel = document.querySelector('.ms-ctl[data-key="level"] input');
            persistedLevel.value = '7'; persistedLevel.dispatchEvent(new Event('input', { bubbles: true }));
            const concurrent = JSON.parse(saved);
            concurrent.modules.zoom.set.sensitivity = 40;
            concurrent.modules.fps = { en: true, set: { color: '#AABBCCFF' } };
            saved = JSON.stringify(concurrent);
            const persistedSpeed = document.querySelector('.ms-ctl[data-key="speed"] input');
            persistedSpeed.value = '22'; persistedSpeed.dispatchEvent(new Event('input', { bubbles: true }));
            assert(JSON.parse(saved).modules.zoom.set.sensitivity === 40, 'Zoom save preserves other newer settings');
            assert(JSON.parse(saved).modules.fps.set.color === '#AABBCCFF', 'Zoom save preserves other modules');
            cfgLoad(); closeModSettings(); openModSettings('zoom');
            assert(document.querySelector('.ms-ctl[data-key="level"] input').value === '7', 'Zoom level survives reload');
            assert(document.querySelector('.ms-ctl[data-key="sensitivity"] input').value === '40', 'Zoom sensitivity survives reload');
            closeModSettings();
            for (const module of MODULES.filter(m => m.widget)) {
                openModSettings(module.id);
                const scaleControl = document.querySelector('.ms-ctl[data-key="scale"] input');
                scaleControl.value = '1.7'; scaleControl.dispatchEvent(new Event('input', { bubbles: true }));
                closeModSettings(); cfgLoad(); openModSettings(module.id);
                assert(document.querySelector('.ms-ctl[data-key="scale"] input').value === '1.7', module.id + ' settings survive reopen/reload');
                closeModSettings();
            }
            for (const module of MODULES.filter(m => m.widget && m.id !== 'crosshair')) {
                for (const enabled of [false, true]) {
                    openModSettings(module.id);
                    const shadowControl = document.querySelector('.ms-ctl[data-key="shadow"] input');
                    assert(shadowControl, module.id + ' exposes Text Shadow');
                    shadowControl.checked = enabled;
                    shadowControl.dispatchEvent(new Event('change', { bubbles: true }));
                    closeModSettings(); cfgLoad(); openModSettings(module.id);
                    assert(document.querySelector('.ms-ctl[data-key="shadow"] input').checked === enabled,
                        module.id + ' Text Shadow survives reload: ' + enabled);
                    closeModSettings();
                }
            }
            const staleLayoutState = modState('fps');
            const newerSettings = JSON.parse(saved);
            newerSettings.modules.fps.set.color = '#2468ACFF';
            saved = JSON.stringify(newerSettings);
            Object.assign(staleLayoutState, { x: 23.5, y: 41.25, scale: 1.8 });
            saveHudLayout('fps', staleLayoutState);
            assert(JSON.parse(saved).modules.fps.set.color === '#2468ACFF', 'Saving a layout preserves newer module settings');
            cfgLoad();
            near(modState('fps').x, 23.5, 'HUD position survives reload');
            near(modState('fps').scale, 1.8, 'HUD size survives reload');
            let layoutPreviewPatch = null;
            window.batPreviewHudModule = (id, patch) => { layoutPreviewPatch = { id, patch: JSON.parse(patch) }; };
            nativeHud = true;
            const previewElement = document.createElement('div');
            previewElement.style.left = '29%'; previewElement.style.top = '37%'; previewElement.style.transform = 'scale(1.6)';
            previewNativePosition(previewElement, 'fps');
            assert(layoutPreviewPatch.id === 'fps' && layoutPreviewPatch.patch.x === 29 && layoutPreviewPatch.patch.scale === 1.6,
                'Live layout preview updates only the moved module');
            assert(Object.keys(layoutPreviewPatch.patch.set).join(',') === 'scale', 'Layout preview does not replace appearance settings');
            nativeHud = false; delete window.batPreviewHudModule;
            closePanel(false); delete window.batSaveHudModule;
            saved = '{"modules":{}}';
            cfgLoad();
            openModSettings('zoom');
            document.querySelector('.ms-ctl[data-key="key"] button[data-v="V"]').click();
            const zoomSpeed = document.querySelector('.ms-ctl[data-key="speed"] input');
            zoomSpeed.value = '25'; zoomSpeed.dispatchEvent(new Event('input', { bubbles: true }));
            assert(JSON.parse(saved).modules.zoom.set.key === 'V', 'Zoom key setting saves');
            assert(JSON.parse(saved).modules.zoom.set.speed === 25, 'Zoom animation speed saves');
            assert(document.querySelector('.ms-ctl[data-key="level"] input'), 'Zoom level setting visible');
            assert(document.querySelector('.ms-ctl[data-key="smooth"]'), 'Smooth Zoom setting visible');
            const customBind = document.querySelector('.zoom-custom-bind');
            customBind.click(); window.__batKeyCaptured('key.keyboard.g');
            assert(JSON.parse(saved).modules.zoom.set.key === 'key.keyboard.g', 'Custom keyboard bind saves');
            assert(customBind.textContent === 'G', 'Custom key label visible');
            customBind.click(); window.__batKeyCaptured(null);
            assert(JSON.parse(saved).modules.zoom.set.key === 'key.keyboard.g', 'Cancel preserves custom bind');
            customBind.click(); window.__batKeyCaptured('key.mouse.middle');
            assert(JSON.parse(saved).modules.zoom.set.key === 'key.mouse.middle', 'Custom mouse bind saves');
            assert(document.querySelector('.ms-ctl[data-key="scroll"]'), 'Wheel zoom toggle visible');
            const maximumZoom = document.querySelector('.ms-ctl[data-key="maxZoom"] input');
            maximumZoom.value = '30'; maximumZoom.dispatchEvent(new Event('input', { bubbles: true }));
            assert(JSON.parse(saved).modules.zoom.set.maxZoom === 30, 'Maximum wheel zoom saves');
            closeModSettings(); closePanel(false);
            openModSettings('nametags');
            assert(modDef('nametags').def && !modDef('nametags').widget, 'Nametags is a world-space module');
            for (const key of ['showOwn', 'logo', 'share', 'shadow', 'background']) {
                const control = document.querySelector('.ms-ctl[data-key="' + key + '"] input');
                assert(control, 'Nametags control: ' + key);
                control.checked = false; control.dispatchEvent(new Event('change', { bubbles: true }));
            }
            assert(!document.querySelector('#ms-preview .nametag-preview img'), 'Nametags logo toggle updates preview');
            closeModSettings(); cfgLoad(); openModSettings('nametags');
            assert(!document.querySelector('.ms-ctl[data-key="logo"] input').checked, 'Nametags logo preference persists');
            document.getElementById('ms-reset').click();
            assert(document.querySelector('#ms-preview .nametag-preview img'), 'Nametags reset restores logo');
            closeModSettings(); closePanel(false);
            if (!document.getElementById('hud-layer')) {
                for (const m of MODULES.filter(m => m.widget)) {
                    openModSettings(m.id);
                    const slider = document.querySelector('.ms-ctl[data-key="scale"] input');
                    slider.value = '1.4'; slider.dispatchEvent(new Event('input', { bubbles: true }));
                    near(JSON.parse(saved).modules[m.id].scale, 1.4, m.id + ' pause settings saved');
                    document.getElementById('ms-reset').click();
                    near(JSON.parse(saved).modules[m.id].scale, 1, m.id + ' pause reset saved');
                }
                let action = null;
                window.batAction = value => { action = value; };
                document.getElementById('ms-move').click();
                assert(action === 'hudeditor:open', 'Pause Move on HUD opens the actual overlay editor');
                batMenuEnter();
                await new Promise(resolve => setTimeout(resolve, 100));
                const pauseCenter = document.querySelector('.pause-center');
                const opacity = Number(getComputedStyle(pauseCenter).opacity);
                assert(opacity > 0 && opacity < 1, 'Pause menu visibly animates');
                await new Promise(resolve => setTimeout(resolve, 350));
                assert(pauseCenter.style.transform === '', 'Pause animation keeps original centering');
                return { checks, status: 'PAUSE_SETTINGS_OK' };
            }
            document.body.classList.remove('panel-open', 'hud-menu', 'hud-idle');
            document.body.classList.add('hud-edit');
            // Appearance combinations previously threw DOMTokenList InvalidCharacterError.
            for (const m of MODULES.filter(m => m.widget)) {
                const st = modState(m.id);
                st.en = true;
                Object.assign(st.set, { background: false, shadow: false, numbers: false, showCps: false, border: false });
            }
            renderHudWidgets();
            assert(document.querySelectorAll('.hud-widget').length === MODULES.filter(m => m.widget).length, 'All modules render with combined settings');
            for (const id of ['scoreboard', 'bossbar', 'blockindicator']) {
                assert(modDef(id).widget && modDef(id).cat === 'hud', id + ' is an editable HUD module');
                assert(hudEl(id), id + ' has an editor widget');
            }
            for (const el of document.querySelectorAll('.hud-widget')) {
                const box = el.querySelector('.hud-wbox');
                assert(getComputedStyle(box).backgroundColor === 'rgba(0, 0, 0, 0)', 'Background off: ' + el.dataset.id);
                const hp = box.querySelector('.hp');
                if (hp) assert(getComputedStyle(hp).textShadow === 'none', 'Shadow off: ' + el.dataset.id);
            }
            assert(getComputedStyle(hudEl('keystrokes').querySelector('.km span:last-child')).display === 'none', 'CPS hidden');
            assert(getComputedStyle(hudEl('armor').querySelector('.hp-armor span')).display === 'none', 'Armor numbers hidden');
            assert(getComputedStyle(hudEl('keystrokes').querySelector('[data-k="w"]')).boxShadow === 'none', 'Key borders hidden');
            for (const key of hudEl('keystrokes').querySelectorAll('[data-k]')) {
                assert(getComputedStyle(key).textShadow === 'none', 'Keystrokes shadow off: ' + key.dataset.k);
            }
            for (const id of ['armor', 'durability', 'potions', 'keystrokes']) {
                modState(id).set.color = '#FF0000FF';
            }
            renderHudWidgets();
            for (const id of ['armor', 'durability', 'potions', 'keystrokes']) {
                const selector = id === 'keystrokes' ? '[data-k="w"]' : '.hp u';
                assert(getComputedStyle(hudEl(id).querySelector(selector)).color === 'rgb(255, 0, 0)', id + ' text color');
            }
            // Editor and idle use the identical box and metrics at every supported scale.
            const feed = { fps: '144 FPS', coords: 'X: 125 Y: 64 Z: -340', ping: '22 ms', cps: 'LMB 5 CPS RMB 2 CPS', sprint: true,
                potions: [{ n: 'Speed II', t: '1:24' }], armor: ['300'], dur: ['80%'], target: { name: 'Zombie', hp: '14 / 20' } };
            for (const scale of [0.5, 1, 2, 3]) {
                for (const m of MODULES.filter(m => m.widget)) modState(m.id).scale = scale;
                renderHudWidgets();
                window.__batHud(Object.assign({ mode: 'edit' }, feed));
                const before = Array.from(document.querySelectorAll('.hud-widget')).map(el => [el.dataset.id, rect(el)]);
                window.__batHud(Object.assign({ mode: 'idle' }, feed));
                for (const [id, b] of before) {
                    const a = rect(hudEl(id));
                    for (const k of ['x', 'y', 'width', 'height']) near(a[k], b[k], id + ' mode parity ' + k);
                }
            }
            window.__batHud({ mode: 'idle' });
            assert(getComputedStyle(hudEl('potions')).display === 'none', 'Empty potions hidden in-game');
            window.__batHud({ mode: 'edit' });
            assert(getComputedStyle(hudEl('potions')).display !== 'none', 'Empty potions editable');
            // All corners grow outward while the opposite corner remains pinned.
            for (const corner of ['nw', 'ne', 'sw', 'se']) {
                const st = modState('fps'); Object.assign(st, { x: 30, y: 30, scale: 1 });
                renderHudWidgets();
                const el = hudEl('fps'), before = rect(el), handle = el.querySelector('.hud-rz-' + corner);
                const right = corner.includes('e'), bottom = corner.includes('s');
                const x = right ? before.right : before.x, y = bottom ? before.bottom : before.y;
                const event = (type, x, y) => handle.dispatchEvent(new PointerEvent(type, { bubbles: true, pointerId: 1, clientX: x, clientY: y }));
                event('pointerdown', x, y);
                event('pointermove', x + (right ? 1 : -1) * before.width * 0.5, y + (bottom ? 1 : -1) * before.height * 0.5);
                event('pointerup', x, y);
                const after = rect(el);
                near(st.scale, 1.5, corner + ' grows outward');
                near(right ? after.x : after.right, right ? before.x : before.right, corner + ' pinned X');
                near(bottom ? after.y : after.bottom, bottom ? before.y : before.bottom, corner + ' pinned Y');
                near(st.set.scale, 1.5, 'Settings scale synchronized');
            }
            // Scaled right edge snaps exactly to the screen boundary.
            const st = modState('fps'); Object.assign(st, { x: 20, y: 20, scale: 2 }); renderHudWidgets();
            const el = hudEl('fps'), b = rect(el);
            // Isolate screen-edge snapping from nearby module-edge targets.
            const otherWidgets = Array.from(document.querySelectorAll('.hud-widget')).filter(w => w !== el);
            otherWidgets.forEach(w => { w.style.display = 'none'; });
            const snapped = hudApplySnap(el, 'fps', { x: 100 - b.width / innerWidth * 100 - 0.2, y: 20 });
            near(snapped.x / 100 * innerWidth + b.width, innerWidth, 'Scaled edge snap');
            hudSnapOn = true;
            const start = rect(el);
            el.dispatchEvent(new PointerEvent('pointerdown', {bubbles:true,pointerId:7,clientX:start.x+5,clientY:start.y+5}));
            const nearCenterX = innerWidth/2 - start.width/2 + 2;
            el.dispatchEvent(new PointerEvent('pointermove', {bubbles:true,pointerId:7,clientX:nearCenterX+5,clientY:start.y+5,shiftKey:true}));
            near(rect(el).x, nearCenterX, 'Shift bypasses centre snapping');
            assert(document.getElementById('guide-v').classList.contains('hidden'), 'Free movement hides guides');
            el.dispatchEvent(new PointerEvent('pointermove', {bubbles:true,pointerId:7,clientX:nearCenterX+5,clientY:start.y+5}));
            near(rect(el).x + rect(el).width/2, innerWidth/2, 'Centre snapping resumes after Shift');
            el.dispatchEvent(new PointerEvent('pointerup', {bubbles:true,pointerId:7}));
            otherWidgets.forEach(w => { w.style.display = ''; });
            const clamped = hudClampPosition(el, { x: 100, y: 100 });
            near(clamped.x / 100 * innerWidth + b.width, innerWidth, 'Right boundary');
            near(clamped.y / 100 * innerHeight + b.height, innerHeight, 'Bottom boundary');
            hudSelect('fps'); hudCenterSelected('h');
            near(rect(el).x + rect(el).width / 2, innerWidth / 2, 'Horizontal centering');
            hudNudgeSelected(-100, -100); cfgLoad(); renderHudWidgets();
            near(rect(hudEl('fps')).x, 0, 'Zero X persists'); near(rect(hudEl('fps')).y, 0, 'Zero Y persists');
            // Native anchor import then web save must not retain stale offsets.
            CFG.modules.fps = { en: true, anchor: 'BOTTOM_RIGHT', offset_x: -200, offset_y: -100, scale: 1, set: {} };
            const anchored = modState('fps');
            near(anchored.x / 100 * innerWidth, innerWidth - 200, 'Anchor import X');
            near(anchored.y / 100 * innerHeight, innerHeight - 100, 'Anchor import Y');
            saveModState('fps', anchored);
            assert(!JSON.parse(saved).modules.fps.anchor, 'Legacy anchor removed on web save');
            // Settings page controls, reset, and selected color survive return to game.
            openModSettings('fps');
            const shadow = document.querySelector('.ms-ctl[data-key="shadow"] input');
            shadow.checked = false; shadow.dispatchEvent(new Event('change', { bubbles: true }));
            assert(getComputedStyle(hudEl('fps').querySelector('.hp')).textShadow === 'none', 'Settings shadow reaches HUD');
            document.getElementById('ms-reset').click();
            assert(!hudEl('fps').querySelector('.hud-wbox').classList.contains('no-shadow'), 'Reset refreshes live widget');
            closeModSettings(); closePanel();
            const fs = modState('fps'); fs.set.bgColor = '#12345680'; saveModState('fps', fs); renderHudWidgets();
            window.__batHud(Object.assign({ mode: 'idle' }, feed));
            await new Promise(resolve => setTimeout(resolve, 160));
            const bg = getComputedStyle(hudEl('fps').querySelector('.hud-wbox')).backgroundColor;
            assert(bg === 'rgba(18, 52, 86, 0.5)' || bg === 'rgba(18, 52, 86, 0.502)', 'Custom background alpha: ' + bg);
            assert(widgetPreview(modDef('crosshair'), { style: 'Cross' }) !== widgetPreview(modDef('crosshair'), { style: 'Cross + Dot' }), 'Distinct crosshair styles');
            assert(!modDef('crosshair').widget && modDef('crosshair').nativePreview, 'Crosshair uses native preview and has no draggable HUD box');
            assert(!modDef('crosshair').settings.flatMap(section => section.rows).some(row => row.key === 'background' || row.key === 'bgColor'), 'Crosshair has no background settings');
            assert(!widgetPreview(modDef('coords'), { labels: false }).includes('X:'), 'Coordinate preview labels');
            // A second cached page changing Java config must update the active HUD.
            const external = JSON.parse(saved); external.modules.fps.scale = 1.7;
            window.__batHud(Object.assign({ mode: 'idle', config: external }, feed));
            near(modState('fps').scale, 1.7, 'External page settings synchronized');
            for (const m of MODULES.filter(m => m.widget)) {
                openModSettings(m.id);
                const slider = document.querySelector('.ms-ctl[data-key="scale"] input');
                slider.value = '1.2'; slider.dispatchEvent(new Event('input', { bubbles: true }));
                near(modState(m.id).scale, 1.2, m.id + ' scale slider');
                document.getElementById('ms-reset').click();
                near(modState(m.id).scale, 1, m.id + ' reset scale');
            }
            closeModSettings(); closePanel();
            // Returning from the mod menu must preserve the editor and expose its button.
            toggleHudEditor(true);
            document.querySelector('[data-action="client-settings"]').click();
            assert(!document.body.classList.contains('hud-edit'), 'Editor suspended while mod menu is open');
            document.querySelector('[data-action="back"]').click();
            assert(document.body.classList.contains('hud-edit'), 'Editor restored after closing mod menu');
            assert(!document.querySelector('.overlay-editor'), 'No extra HUD editor button');
            assert(document.querySelectorAll('.overlay-center > button').length === 1, 'Only Bat Client Settings in center menu');
            openPanel('mods'); toggleHudEditor(false); closePanel(false);
            assert(!document.body.classList.contains('hud-edit'), 'Screen removal cannot reactivate editor');
            // Native measurements replace CSS text metrics; pointer motion previews without disk writes.
            let preview = null;
            window.batPreviewHudConfig = value => { preview = JSON.parse(value); };
            window.__batHud({ mode: 'edit', native: true, sizes: { fps: [110, 34] } });
            const nativeEl = hudEl('fps');
            const nativeScale = modState('fps').scale;
            near(rect(nativeEl).width, 110 * nativeScale, 'Native framebuffer width');
            near(rect(nativeEl).height, 34 * nativeScale, 'Native framebuffer height');
            const mutations = [];
            const observer = new MutationObserver(records => mutations.push(...records));
            observer.observe(nativeEl, { attributes: true, subtree: true });
            window.__batHud({ mode: 'edit', native: true, sizes: { fps: [110, 34] } });
            await Promise.resolve();
            observer.disconnect();
            assert(mutations.length === 0, 'Unchanged native measurements cause no widget layout mutations');
            assert(getComputedStyle(nativeEl.querySelector('.hp')).visibility === 'hidden', 'Java owns module text');
            assert(getComputedStyle(nativeEl.querySelector('.hud-wbox')).backgroundColor === 'rgba(0, 0, 0, 0)', 'Native plate remains visible through editor');
            nativeEl.style.left = '12%'; nativeEl.style.top = '18%';
            previewNativePosition(nativeEl, 'fps');
            near(preview.modules.fps.x, 12, 'Native drag preview X');
            near(preview.modules.fps.y, 18, 'Native drag preview Y');
            assert(parseFloat(getComputedStyle(document.querySelector('.overlay-primary')).transitionDuration) > 0, 'Smooth button transitions');
            nativeHud = false; nativeHudSizes = {}; document.body.classList.remove('native-hud');
            // Verify motion progresses over time and replays on a cached menu.
            closePanel(false);
            const center = document.querySelector('.overlay-center');
            const centerBefore = rect(center);
            // Drive the animation clock explicitly: a busy game/build can delay a 100ms
            // timer beyond the entire entrance, making a correct fade appear unanimated.
            const realAnimationFrame = window.requestAnimationFrame;
            let entranceFrames = [];
            window.requestAnimationFrame = callback => { entranceFrames.push(callback); return 0; };
            const advanceEntrance = time => { const frames = entranceFrames; entranceFrames = []; frames.forEach(callback => callback(time)); };
            batMenuEnter();
            advanceEntrance(0); advanceEntrance(100);
            const movingOpacity = Number(getComputedStyle(center).opacity);
            assert(movingOpacity > 0 && movingOpacity < 1, 'Menu animation advances between frames: ' + movingOpacity + ' ' + JSON.stringify({body:document.body.className, opacity:center.style.opacity, motion:center.dataset.batMotion, reduced:matchMedia('(prefers-reduced-motion: reduce)').matches}));
            near(rect(center).x, centerBefore.x, 'Fade does not move center X');
            near(rect(center).y, centerBefore.y, 'Fade does not move center Y');
            advanceEntrance(350);
            assert(!center.dataset.batMotion && center.style.transform === '', 'Menu animation restores normal layout');
            batMenuEnter();
            assert(center.style.opacity === '0', 'Cached menu entrance replays');
            advanceEntrance(400); advanceEntrance(750);
            window.requestAnimationFrame = realAnimationFrame;
            assert(getComputedStyle(document.querySelector('.overlay-primary')).fontFamily.includes('Segoe UI'), 'Original menu font retained');
            near(parseFloat(getComputedStyle(document.querySelector('.overlay-primary')).fontSize), 14, 'Settings button font size');
            assert(getComputedStyle(document.querySelector('.overlay-primary')).fontWeight === '800', 'Settings button uses heavy bold text');
            assert(getComputedStyle(document.querySelector('.overlay-primary')).whiteSpace === 'nowrap', 'Settings label stays on one line');
            const lighter = modState('fps');
            lighter.set.bgColor = '#000000A6';
            assert(modState('fps').set.bgColor === '#0000006B', 'Old heavy default becomes light translucent black');
            lighter.set.bgColor = '#12345680';
            assert(modState('fps').set.bgColor === '#12345680', 'Custom module background color preserved');
            let nativePreview = null;
            window.batSetHudPreview = value => { nativePreview = value; };
            nativeHud = true; document.body.classList.add('native-hud');
            openModSettings('armor');
            await new Promise(resolve => setTimeout(resolve, 350));
            // Busy build/game processes can delay hidden-window animation frames.
            const previewDeadline = performance.now() + 2000;
            while (nativePreviewFrame !== 0 && performance.now() < previewDeadline) {
                await new Promise(resolve => setTimeout(resolve, 50));
            }
            const previewRect = JSON.parse(nativePreview);
            assert(nativePreviewFrame === 0, 'Static native preview stops idle animation scheduling');
            assert(previewRect.id === 'armor' && previewRect.right > previewRect.left && previewRect.bottom > previewRect.top, 'Native settings preview has a clipped viewport');
            assert(document.getElementById('ms-preview').innerHTML === '', 'Settings preview contains no web module font');
            assert(getComputedStyle(hudEl('armor').querySelector('.hud-wlabel')).visibility === 'hidden', 'Editor labels use Java font');
            closeModSettings(); closePanel(false);
            assert(nativePreview === 'null', 'Closing module settings clears native preview');
            openModSettings('nametags');
            await new Promise(resolve => setTimeout(resolve, 350));
            assert(JSON.parse(nativePreview).id === 'nametags', 'Nametags settings use Java preview');
            assert(document.getElementById('ms-preview').innerHTML === '', 'Nametags preview contains no web text');
            closeModSettings(); closePanel(false);
            delete window.batSetHudPreview;
            const escapeActions = [];
            window.batAction = action => escapeActions.push(action);
            for (const nestedSettings of [false, true]) {
                toggleHudEditor(true); hudSelect('fps');
                if (nestedSettings) openModSettings('fps');
                escapeActions.length = 0;
                document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
                assert(escapeActions.filter(a => a === 'resume').length === 1, 'One Escape resumes from overlay');
                assert(!document.body.classList.contains('hud-edit'), 'Escape closes HUD editor');
                assert(!document.body.classList.contains('panel-open'), 'Escape closes settings panel');
                assert(document.getElementById('mod-settings').classList.contains('hidden'), 'Escape closes module settings');
                assert(hudSelId === null, 'Escape clears HUD selection and resize controls');
            }
            window.batAction = () => {};
            nativeHud = false; nativeHudSizes = {}; document.body.classList.remove('native-hud');
            // Leave a readable fixture for screenshot inspection (not a game screenshot).
            CFG = { modules: {} };
            renderHudWidgets();
            document.body.style.background = '#293445';
            window.__batHud(Object.assign({ mode: 'edit' }, feed));
            window.__hudFixtureFeed = feed;
            await new Promise(resolve => setTimeout(resolve, 160));
            return { checks, status: 'HUD_UI_OK' };
        }} )()`);
        console.log(JSON.stringify(result));
        if (errors.length) throw Error(errors.join('\n'));
        const output = path.join(__dirname, '../build/hud-ui-test.png');
        fs.mkdirSync(path.dirname(output), { recursive: true });
        fs.writeFileSync(output, (await win.webContents.capturePage()).toPNG());
        console.log('Screenshot: ' + output);
        if (page === 'overlay.html') {
            await win.webContents.executeJavaScript(`document.body.style.background='linear-gradient(rgba(0,0,0,.25),rgba(0,0,0,.25)),url("bg.png") center/cover'; window.__batHud(Object.assign({mode:'idle'},window.__hudFixtureFeed));`);
            await new Promise(resolve => setTimeout(resolve, 200));
            fs.writeFileSync(path.join(__dirname,'../build/hud-gameplay-preview.png'), (await win.webContents.capturePage()).toPNG());
            await win.webContents.executeJavaScript(`window.__batHud(Object.assign({mode:'edit'},window.__hudFixtureFeed));`);
            await new Promise(resolve => setTimeout(resolve, 200));
            fs.writeFileSync(path.join(__dirname,'../build/hud-editor-preview.png'), (await win.webContents.capturePage()).toPNG());
            const menu = await win.webContents.executeJavaScript(`openPanel('mods'); ({ columns: getComputedStyle(document.getElementById('mod-grid')).gridTemplateColumns.split(' ').length, active: getComputedStyle(document.querySelector('.mm-tab.active')).backgroundColor, scrollable: document.querySelector('.mm-body').scrollHeight > document.querySelector('.mm-body').clientHeight });`);
            if (menu.columns !== 4 || menu.active !== 'rgb(255, 255, 255)' || !menu.scrollable) throw Error('Mod menu layout failed: ' + JSON.stringify(menu));
            await new Promise(resolve => setTimeout(resolve, 200));
            fs.writeFileSync(path.join(__dirname,'../build/mod-menu-preview.png'), (await win.webContents.capturePage()).toPNG());
            console.log('MONOCHROME_MENU_OK');
        }
        app.exit(0);
    } catch (error) { console.error(error.stack); app.exit(1); }
});
