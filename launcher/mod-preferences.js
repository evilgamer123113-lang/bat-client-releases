const SODIUM = 'AANobbMI';
const ADDONS = new Set(['PtjYWJkn', 'Bh37bMuy']);
function projectForFile(filename, map = {}) {
    if (map[filename]) return map[filename];
    const enabledName = filename.replace(/(?:\.jar)?\.disabled$/, '.jar');
    if (map[enabledName]) return map[enabledName];
    const name = filename.replace(/\.disabled$/, '').replace(/\.jar$/, '').toLowerCase();
    if (/^sodium-extra(?:[-_\d]|$)/.test(name)) return 'PtjYWJkn';
    if (/^reeses[-_]sodium[-_]options(?:[-_\d]|$)/.test(name)) return 'Bh37bMuy';
    if (/^sodium(?:[-_\d]|$)/.test(name)) return SODIUM;
    return null;
}
function blockedAutomaticMod(project, dismissed) {
    return dismissed.includes(project) || (ADDONS.has(project) && dismissed.includes(SODIUM));
}
function reconcileModPreferences(managed, map, disk, cache = {}) {
    map = {...map};
    // Older releases could lose tracking during concurrent writes. Recover the
    // identity from the successful-download cache, not by guessing other mods.
    for (const [key, cached] of Object.entries(cache)) {
        if (cached?.filename && !map[cached.filename]) map[cached.filename] = key.split('|')[0];
    }
    const present = new Set(disk), tracked = new Set(managed), dismissed = new Set();
    for (const cached of Object.values(cache)) if (cached?.filename && map[cached.filename]) tracked.add(cached.filename);
    const exists = name => present.has(name) || present.has(name+'.disabled') || present.has(name.replace(/\.jar$/, '.disabled'));
    for (const filename of tracked) {
        if (!present.has(filename) && (present.has(filename+'.disabled') || present.has(filename.replace(/\.jar$/, '.disabled')))) {
            const project = projectForFile(filename, map); if (project) dismissed.add(project);
        }
        if (exists(filename)) continue;
        const project = projectForFile(filename, map);
        // A replacement version of the same project is not a removal choice.
        if (project && !disk.some(file => projectForFile(file, map) === project)) dismissed.add(project);
    }
    return {managed:managed.filter(exists), map:Object.fromEntries(Object.entries(map).filter(([file]) => exists(file))), dismissed:[...dismissed]};
}
module.exports = {projectForFile, blockedAutomaticMod, reconcileModPreferences, SODIUM, ADDONS};
