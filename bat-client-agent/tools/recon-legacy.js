'use strict';

// Reconnaissance: dump, for every covered legacy version, the obfuscated names
// of the symbols the RavenCape agent needs. Reads the cached ground-truth
// configs (mcpbot srg / Zffu srg+tsrg / MCPConfig tsrg / fabric intermediary tiny).
// Prints a table so we can see which names drift between versions.

const fs = require('fs');
const path = require('path');

const CACHE = process.env.RAVEN_LEGACY_CACHE ||
  path.join(process.env.TEMP || process.env.TMP || '.', 'opencode', 'legacy-data');

const TARGET_CLASSES = [
  'net/minecraft/client/Minecraft',
  'net/minecraft/client/entity/AbstractClientPlayer',
  'net/minecraft/client/player/AbstractClientPlayer',
  'net/minecraft/util/ResourceLocation',
  'net/minecraft/client/renderer/texture/DynamicTexture',
  'net/minecraft/client/renderer/texture/TextureManager',
  'net/minecraft/client/renderer/texture/NativeImage',
  'com/mojang/blaze3d/platform/NativeImage',
];
const TARGET_METHODS = {
  'net/minecraft/client/Minecraft': ['func_71407_l'],              // tick
  'AbstractClientPlayer': ['func_110306_p', 'func_110303_q'],      // cape, skin getters
};

function parseSrg(text) {
  // CL: <obf> <named>
  // FD: <obfCls>/<obfF> <srgCls>/<srgF>
  // MD: <obfCls>/<obfM> <desc> <srgCls>/<srgM> <desc>
  const classes = new Map();   // obf -> named
  const methods = new Map();   // namedOwner + "/" + srgName + "#" + desc -> obfOwner/obfName
  const classMethods = new Map(); // namedOwner -> [{obfName, srgName, desc}]
  for (const line of text.split(/\r?\n/)) {
    if (line.startsWith('CL: ')) {
      const m = line.match(/^CL: (\S+) (\S+)$/);
      if (m) classes.set(m[1], m[2]);
    } else if (line.startsWith('MD: ')) {
      const m = line.match(/^MD: (\S+)\/(\S+) (\S+) (\S+)\/(\S+) \S+$/);
      if (!m) continue;
      const [ , obfOwner, obfName, desc, srgOwner, srgName ] = m;
      const namedOwner = srgOwner;
      if (!namedOwner) continue;
      if (!classMethods.has(namedOwner)) classMethods.set(namedOwner, []);
      classMethods.get(namedOwner).push({ obfOwner, obfName, srgName, desc });
      methods.set(namedOwner + '/' + srgName + '#' + desc, obfOwner + '/' + obfName);
    }
  }
  return { classes, classMethods, methods };
}

function parseTsrg(text) {
  const classes = new Map();   // obf -> named
  const classMethods = new Map();
  let cur = null;
  for (const line of text.split(/\r?\n/)) {
    if (!line.trim()) continue;
    if (!line.startsWith('\t')) {
      const p = line.trim().split(/\s+/);
      if (p.length >= 2) { classes.set(p[0], p[1]); cur = p[1]; if (!classMethods.has(cur)) classMethods.set(cur, []); }
    } else if (cur) {
      const p = line.trim().split(/\s+/);
      if (p.length === 3) { // obfName desc srgName
        classMethods.get(cur).push({ obfName: p[0], desc: p[1], srgName: p[2] });
      }
    }
  }
  return { classes, classMethods, methods: null };
}

function parseTiny(text) {
  // tiny 2 0 official intermediary  (and possibly named)
  const classes = new Map(); // official -> intermediary
  const classMethods = new Map(); // official -> [{obfName, intermediaryName, desc}]
  let cur = null;
  for (const line of text.split(/\r?\n/)) {
    if (!line || line.startsWith('tiny')) continue;
    if (line.startsWith('c\t')) {
      const p = line.split('\t');
      cur = p[1]; classes.set(p[1], p[2]);
      if (!classMethods.has(cur)) classMethods.set(cur, []);
    } else if (line.startsWith('\tm\t') && cur) {
      const p = line.split('\t');
      classMethods.get(cur).push({ obfName: p[2], intermediaryName: p[3], desc: p[1] });
    }
  }
  return { classes, classMethods, methods: null, tiny: true };
}

function ownerOf(named) {
  return named.endsWith('AbstractClientPlayer') ? 'AbstractClientPlayer' : named;
}

function dump(label, data, isTiny, namedByOfficial) {
  const out = {};
  for (const t of TARGET_CLASSES) {
    const key = t.split('/').pop();
    if (out[key]) continue;
    let found = null;
    if (isTiny) {
      for (const [official, intermediary] of data.classes) {
        if (namedByOfficial.get(official) === t) { found = official; break; }
      }
    } else {
      for (const [obf, named] of data.classes) {
        if (named === t) { found = obf; break; }
      }
    }
    out[key] = found;
  }
  // methods
  const mc = out['Minecraft'];
  const acp = out['AbstractClientPlayer'];
  if (isTiny) {
    if (acp) {
      const m = data.classMethods.get(acp) || [];
      out['acpRLocGetters'] = m.filter(x => x.desc.includes('ResourceLocation')).map(x => x.obfName + '/' + x.intermediaryName);
      out['mcZeroArgVoid'] = (data.classMethods.get(mc) || []).filter(x => x.desc === '()V').map(x => x.obfName + '/' + x.intermediaryName).join(',');
    }
  } else {
    for (const [owner, list] of data.classMethods) {
      const ownerShort = owner.split('/').pop();
      for (const mm of list) {
        if (ownerShort === 'Minecraft' && mm.srgName === 'func_71407_l') out['tick'] = mm.obfName;
        if (ownerShort === 'AbstractClientPlayer' && mm.srgName === 'func_110306_p') out['capeGetter'] = mm.obfName;
        if (ownerShort === 'AbstractClientPlayer' && mm.srgName === 'func_110303_q') out['skinGetter'] = mm.obfName;
      }
    }
  }
  return out;
}

function main() {
  const rows = [];
  const list = [];
  const cfg = path.join(CACHE, 'configs');

  const mcpbotVersions = ['1.8','1.8.8','1.8.9','1.9','1.9.2','1.9.4','1.10','1.10.2','1.11'];
  for (const v of mcpbotVersions) {
    const f = path.join(cfg, 'mcpbot', v, 'joined.srg');
    if (fs.existsSync(f)) {
      const d = parseSrg(fs.readFileSync(f, 'utf8'));
      rows.push(['mcpbot', v, dump('mcpbot', d, false)]);
    }
  }

  const zffuFiles = ['1.8.8','1.8.9','1.9.4','1.10','1.11.2','1.12','1.12.2','1.13','1.13.2','1.14','1.14.1','1.14.2','1.14.3'];
  for (const v of zffuFiles) {
    const ts = path.join(cfg, 'zffu', v + '.tsrg');
    const sr = path.join(cfg, 'zffu', v + '.srg');
    if (fs.existsSync(ts)) {
      const d = parseTsrg(fs.readFileSync(ts, 'utf8'));
      rows.push(['zffu', v, dump('zffu', d, false)]);
    } else if (fs.existsSync(sr)) {
      const d = parseSrg(fs.readFileSync(sr, 'utf8'));
      rows.push(['zffu', v, dump('zffu', d, false)]);
    }
  }

  const mcpVersions = ['1.13.1','1.13.2','1.14','1.14.1','1.14.2','1.14.3'];
  for (const v of mcpVersions) {
    const f = path.join(cfg, 'mcpconfig', v + '.tsrg');
    if (fs.existsSync(f)) {
      const d = parseTsrg(fs.readFileSync(f, 'utf8'));
      rows.push(['mcpconfig', v, dump('mcpconfig', d, false)]);
    }
  }

  const tinyVersions = ['1.14','1.14.1','1.14.2','1.14.3'];
  for (const v of tinyVersions) {
    const f = path.join(cfg, 'intermediary', v, 'mappings', 'mappings.tiny');
    const mcf = path.join(cfg, 'mcpconfig', v + '.tsrg');
    if (fs.existsSync(f) && fs.existsSync(mcf)) {
      const d = parseTiny(fs.readFileSync(f, 'utf8'));
      const namedByOfficial = new Map();
      const mc = parseTsrg(fs.readFileSync(mcf, 'utf8'));
      for (const [obf, named] of mc.classes) namedByOfficial.set(obf, named);
      rows.push(['intermediary', v, dump('intermediary', d, true, namedByOfficial)]);
    }
  }

  console.log('source\tversion\tMinecraft\tAbstractClientPlayer\tResourceLocation\tDynamicTexture\tTextureManager\tNativeImage\ttick\tcapeGetter\tskinGetter\tacpRLocGetters');
  for (const [src, v, d] of rows) {
    console.log([src, v, d.Minecraft, d.AbstractClientPlayer, d.ResourceLocation, d.DynamicTexture, d.TextureManager, d.NativeImage, d.tick, d.capeGetter, d.skinGetter, (d.acpRLocGetters || []).join(' '), (d.mcZeroArgVoid || '').slice(0, 60)].join('\t'));
  }
}

main();
