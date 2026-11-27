// Generates src/main/java/com/batclient/cosmetics/MappingsData.java from the Fabric loom
// mapping cache. Reads each version's layered mappings.tiny (official ->
// intermediary -> named/mojmap) and emits the exact class/method names the
// BatCape javaagent needs to patch at class-load time.
//
// Usage: node tools/generate-mappings.js
"use strict";
const fs = require("fs");
const path = require("path");

const LOOM_CACHE = path.join(process.env.USERPROFILE || "C:/Users/Noor-ul-Islam", ".gradle", "caches", "fabric-loom");

// Legacy versions (1.8 - 1.14.3) are mapped from cached ground-truth configs
// (mcpbot SRG / Zffu SRG+TSRG / MCPConfig TSRG / fabric intermediary tiny).
const LEGACY_CACHE = process.env.RAVEN_LEGACY_CACHE ||
  path.join(process.env.TEMP || process.env.TMP || ".", "opencode", "legacy-data");

const LEGACY_SOURCE = {
  "1.8": "mcpbot/1.8/joined.srg",
  "1.8.8": "mcpbot/1.8.8/joined.srg",
  "1.8.9": "mcpbot/1.8.9/joined.srg",
  "1.9": "mcpbot/1.9/joined.srg",
  "1.9.2": "mcpbot/1.9.2/joined.srg",
  "1.9.4": "mcpbot/1.9.4/joined.srg",
  "1.10": "mcpbot/1.10/joined.srg",
  "1.10.2": "mcpbot/1.10.2/joined.srg",
  "1.11": "mcpbot/1.11/joined.srg",
  "1.11.2": "zffu/1.11.2.srg",
  "1.12": "zffu/1.12.srg",
  "1.12.2": "zffu/1.12.2.tsrg",
  "1.13": "zffu/1.13.tsrg",
  "1.13.1": "mcpconfig/1.13.1.tsrg",
  "1.13.2": "zffu/1.13.2.tsrg",
  "1.14": "mcpconfig/1.14.tsrg",
  "1.14.1": "mcpconfig/1.14.1.tsrg",
  "1.14.2": "mcpconfig/1.14.2.tsrg",
  "1.14.3": "mcpconfig/1.14.3.tsrg",
};
// Fabric intermediary exists for 1.14 - 1.14.3 (tiny v2: official -> intermediary).
const TINY_SOURCE = {
  "1.14": "intermediary/1.14/mappings/mappings.tiny",
  "1.14.1": "intermediary/1.14.1/mappings/mappings.tiny",
  "1.14.2": "intermediary/1.14.2/mappings/mappings.tiny",
  "1.14.3": "intermediary/1.14.3/mappings/mappings.tiny",
};
const LEGACY_VERSIONS = Object.keys(LEGACY_SOURCE);

const VERSIONS = [
  "1.14.4","1.15","1.15.1","1.15.2","1.16","1.16.1","1.16.2","1.16.3","1.16.4","1.16.5",
  "1.17","1.17.1","1.18","1.18.1","1.18.2","1.19","1.19.1","1.19.2","1.19.3","1.19.4",
  "1.20","1.20.1","1.20.2","1.20.3","1.20.4","1.20.5","1.20.6","1.21","1.21.1","1.21.2",
  "1.21.3","1.21.4","1.21.5","1.21.6","1.21.7","1.21.8","1.21.9","1.21.10","1.21.11"
];

// ---- legacy mapping parsers (SRG/TSRG/TSRG tiny). Method descriptors are in
// the obfuscated ("official") namespace in every format. ----

function parseSrgLegacy(text) {
  const obfOfNamed = new Map();
  const methodObf = new Map(); // <srgOwner>/<srgName>#<obfDesc> -> obfName
  for (const line of text.split(/\r?\n/)) {
    let m;
    if ((m = line.match(/^CL: (\S+) (\S+)$/))) {
      obfOfNamed.set(m[2], m[1]);
    } else if ((m = line.match(/^MD: (\S+)\/(\S+) (\S+) (\S+)\/(\S+) (\S+)$/))) {
      const [, obfOwner, obfName, obfDesc, srgOwner, srgName] = m;
      methodObf.set(srgOwner + "/" + srgName + "#" + obfDesc, obfName);
    }
  }
  return { obfOfNamed, methodObf };
}

function parseTsrgLegacy(text) {
  const obfOfNamed = new Map();
  const methodObf = new Map();
  let cur = null;
  for (const line of text.split(/\r?\n/)) {
    if (!line.trim()) continue;
    if (!line.startsWith("\t")) {
      const p = line.trim().split(/\s+/);
      if (p.length >= 2) { obfOfNamed.set(p[1], p[0]); cur = p[1]; }
    } else if (cur) {
      const p = line.trim().split(/\s+/);
      if (p.length === 3) methodObf.set(cur + "/" + p[2] + "#" + p[1], p[0]);
    }
  }
  return { obfOfNamed, methodObf };
}

function parseTinyLegacy(text) {
  const interOfOfficial = new Map();
  const methodInter = new Map(); // <officialOwner>/<officialName>#<desc> -> intermediaryName
  let cur = null;
  for (const line of text.split(/\r?\n/)) {
    if (!line || line.startsWith("tiny")) continue;
    if (line.startsWith("c\t")) {
      const p = line.split("\t");
      cur = p[1];
      interOfOfficial.set(p[1], p[2]);
    } else if (line.startsWith("\tm\t") && cur) {
      const p = line.split("\t"); // "", "m", desc, officialName, intermediaryName
      methodInter.set(cur + "/" + p[3] + "#" + p[2], p[4]);
    }
  }
  return { interOfOfficial, methodInter };
}

function parseVersion(v) {
  return v.split(".").map((s) => parseInt(s, 10) || 0);
}
function lt(a, b) { const A = parseVersion(a), B = parseVersion(b); for (let i = 0; i < Math.max(A.length, B.length); i++) { const x = A[i] || 0, y = B[i] || 0; if (x !== y) return x < y; } return false; }
function ge(a, b) { return !lt(a, b); }

function readTiny(version) {
  const dir = path.join(LOOM_CACHE, version);
  const candidates = fs.readdirSync(dir).filter((n) => n.startsWith("loom.mappings.") && n.endsWith("-v2"));
  if (!candidates.length) throw new Error(`no layered mappings for ${version}`);
  const file = path.join(dir, candidates[0], "mappings.tiny");
  const lines = fs.readFileSync(file, "utf8").split(/\r?\n/);
  // <row: {official, intermediary, named, methods: []}>
  const classes = new Map(); // named -> row
  let cur = null;
  for (const raw of lines) {
    if (!raw || raw.startsWith("tiny")) continue;
    if (raw.startsWith("c\t")) {
      const p = raw.split("\t"); // c, official, intermediary, named
      cur = { official: p[1], intermediary: p[2], named: p[3], methods: [] };
      classes.set(cur.named, cur);
    } else if (raw.startsWith("\tm\t") && cur) {
      const p = raw.split("\t"); // "", m, desc, officialName, intermediaryName, namedName
      cur.methods.push({ desc: p[2], official: p[3], intermediary: p[4], named: p[5] });
    }
  }
  return classes;
}

function cls(classes, named) {
  const c = classes.get(named);
  if (!c) return null;
  return { official: c.official, intermediary: c.intermediary, named: c.named };
}
function mtd(classes, ownerNamed, named, descConstraint) {
  const c = classes.get(ownerNamed);
  if (!c) return null;
  for (const m of c.methods) {
    if (m.named !== named) continue;
    if (descConstraint && m.desc !== descConstraint) continue;
    return { official: m.official, intermediary: m.intermediary, named: m.named };
  }
  return null;
}

function officialDescCls(name) {
  // Build an official descriptor for a method that only references unremapped
  // types plus one remapped class given by its official name.
  return "L" + name + ";";
}

function main() {
  const out = [];
  out.push("package com.batclient.cosmetics;");
  out.push("");
  out.push("// GENERATED FILE — do not edit. Rebuild with: node tools/generate-mappings.js");
  out.push("final class MappingsData {");
  out.push("    // rows: <type>,<symbol>,<version>,<official>,<intermediary>,<named>");
  out.push("    // types: C=class, M=method; plus X meta rows: <metaKey>,<version>,<value>");
  out.push("    static final String[][] DATA = {");

  for (const v of VERSIONS) {
    const classes = readTiny(v);
    const row = (type, symbol, official, intermediary, named) => {
      if (!official || !intermediary || !named) return;
      out.push(`        {"${type}","${symbol}","${v}","${official}","${intermediary}","${named}"},`);
    };
    const meta = (key, value) => out.push(`        {"X","${key}","${v}","${value}"},`);

    const isPre1202 = lt(v, "1.20.2");
    const isPre21 = lt(v, "1.21");
    const isPre215 = lt(v, "1.21.5");
    const isPre219 = lt(v, "1.21.9");
    const isPre2111 = lt(v, "1.21.11");

    // ---- classes ----
    const rlocNamed = isPre2111 ? "net/minecraft/resources/ResourceLocation" : "net/minecraft/resources/Identifier";
    const playerskinNamed = isPre1202 ? null
      : (isPre219 ? "net/minecraft/client/resources/PlayerSkin" : "net/minecraft/world/entity/player/PlayerSkin");

    for (const [sym, named] of Object.entries({
      Minecraft: "net/minecraft/client/Minecraft",
      AbstractClientPlayer: "net/minecraft/client/player/AbstractClientPlayer",
      ResourceLocation: rlocNamed,
      DynamicTexture: "net/minecraft/client/renderer/texture/DynamicTexture",
      NativeImage: "com/mojang/blaze3d/platform/NativeImage",
      TextureManager: "net/minecraft/client/renderer/texture/TextureManager",
      PlayerSkin: playerskinNamed,
    })) {
      if (!named) continue;
      const c = cls(classes, named);
      if (!c) throw new Error(`${v}: missing class ${named}`);
      row("C", sym, c.official, c.intermediary, c.named);
    }

    // ---- methods ----
    // cape getter: getCloakTextureLocation (<1.20.2) or getSkin. Declared on
    // AbstractClientPlayer until 1.21.9 moved it to ClientAvatarEntity.
    const capeGetterNamed = isPre1202 ? "getCloakTextureLocation" : "getSkin";
    const capeGetterRet = isPre1202 ? "ResourceLocation" : "PlayerSkin";
    const retCls = isPre1202 ? cls(classes, rlocNamed) : (playerskinNamed ? cls(classes, playerskinNamed) : null);
    const capeDesc = retCls ? "()L" + retCls.official + ";" : null;
    let capeGetter = null;
    let capeOwner = null;
    for (const owner of [
      "net/minecraft/client/player/AbstractClientPlayer",
      "net/minecraft/client/entity/ClientAvatarEntity",
    ]) {
      const m = mtd(classes, owner, capeGetterNamed, capeDesc);
      if (m) { capeGetter = m; capeOwner = cls(classes, owner); break; }
    }
    if (!capeGetter || !capeOwner) throw new Error(`${v}: missing capeGetter ${capeGetterNamed} ${capeDesc}`);
    row("C", "CapeGetterOwner", capeOwner.official, capeOwner.intermediary, capeOwner.named);
    row("M", "capeGetter", capeGetter.official, capeGetter.intermediary, capeGetter.named);

    const tick = mtd(classes, "net/minecraft/client/Minecraft", "tick", "()V");
    if (!tick) throw new Error(`${v}: missing Minecraft.tick`);
    row("M", "tick", tick.official, tick.intermediary, tick.named);

    const nativeImage = cls(classes, "com/mojang/blaze3d/platform/NativeImage");
    const read = mtd(classes, "com/mojang/blaze3d/platform/NativeImage", "read", "(Ljava/io/InputStream;)L" + nativeImage.official + ";");
    if (!read) throw new Error(`${v}: missing NativeImage.read`);
    row("M", "read", read.official, read.intermediary, read.named);

    const copyFrom = mtd(classes, "com/mojang/blaze3d/platform/NativeImage", "copyFrom", "(L" + nativeImage.official + ";)V");
    if (!copyFrom) throw new Error(`${v}: missing NativeImage.copyFrom`);
    row("M", "copyFrom", copyFrom.official, copyFrom.intermediary, copyFrom.named);

    const dynTex = cls(classes, "net/minecraft/client/renderer/texture/DynamicTexture");
    const upload = mtd(classes, "net/minecraft/client/renderer/texture/DynamicTexture", "upload", "()V");
    if (!upload) throw new Error(`${v}: missing DynamicTexture.upload`);
    row("M", "upload", upload.official, upload.intermediary, upload.named);

    const getPixels = mtd(classes, "net/minecraft/client/renderer/texture/DynamicTexture", "getPixels", "()L" + nativeImage.official + ";");
    if (!getPixels) throw new Error(`${v}: missing DynamicTexture.getPixels`);
    row("M", "getPixels", getPixels.official, getPixels.intermediary, getPixels.named);

    // PlayerSkin record cape accessor (needed to rebuild the immutable PlayerSkin)
    if (!isPre1202) {
      const psClass = cls(classes, playerskinNamed);
      if (!psClass) throw new Error(`${v}: missing PlayerSkin class`);
      const capeAccessorNamed = isPre219 ? "capeTexture" : "cape";
      const acc = mtd(classes, playerskinNamed, capeAccessorNamed, null);
      if (!acc) throw new Error(`${v}: missing PlayerSkin.${capeAccessorNamed}`);
      row("M", "capeAccessor", acc.official, acc.intermediary, acc.named);
      meta("capeAccessorMethod", capeAccessorNamed);
      if (!isPre219) {
        const rt = cls(classes, "net/minecraft/core/ClientAsset$ResourceTexture");
        if (!rt) throw new Error(`${v}: missing ClientAsset$ResourceTexture`);
        row("C", "ResourceTexture", rt.official, rt.intermediary, rt.named);
      }
    }

    // ---- meta ----
    meta("capeGetterMethod", capeGetterNamed);
    meta("capeGetterReturns", capeGetterRet);
    meta("rlocCtor", isPre21 ? "ctor" : "fromNamespaceAndPath");
    meta("dynTexCtor", isPre215 ? "NativeImage" : "Supplier;NativeImage");
  }

  // ---- legacy versions (1.8 - 1.14.3) ----
  for (const v of LEGACY_VERSIONS) {
    const src = path.join(LEGACY_CACHE, "configs", LEGACY_SOURCE[v]);
    if (!fs.existsSync(src)) { console.error(`WARN missing legacy mapping ${src}`); continue; }
    const doc = src.endsWith(".srg")
      ? parseSrgLegacy(fs.readFileSync(src, "utf8"))
      : parseTsrgLegacy(fs.readFileSync(src, "utf8"));
    const tinyFile = TINY_SOURCE[v] ? path.join(LEGACY_CACHE, "configs", TINY_SOURCE[v]) : null;
    const tinyDoc = tinyFile && fs.existsSync(tinyFile)
      ? parseTinyLegacy(fs.readFileSync(tinyFile, "utf8")) : null;

    const row = (type, symbol, official, intermediary, named) => {
      if (!official || !intermediary || !named) return;
      out.push(`        {"${type}","${symbol}","${v}","${official}","${intermediary}","${named}"},`);
    };
    const meta = (key, value) => out.push(`        {"X","${key}","${v}","${value}"},`);

    const isNativeImage = ge(v, "1.13");
    const inter = (obf) => (tinyDoc && tinyDoc.interOfOfficial.get(obf)) || obf;
    const mInter = (ownerObf, nameObf, descObf) =>
      tinyDoc ? (tinyDoc.methodInter.get(ownerObf + "/" + nameObf + "#" + descObf) || nameObf) : nameObf;

    const acpNamed = doc.obfOfNamed.has("net/minecraft/client/entity/player/AbstractClientPlayerEntity")
      ? "net/minecraft/client/entity/player/AbstractClientPlayerEntity"
      : "net/minecraft/client/entity/AbstractClientPlayer";
    const niSrg = doc.obfOfNamed.has("com/mojang/blaze3d/platform/NativeImage")
      ? "com/mojang/blaze3d/platform/NativeImage"
      : "net/minecraft/client/renderer/texture/NativeImage";

    const obfCls = (named) => doc.obfOfNamed.get(named);
    const mc = obfCls("net/minecraft/client/Minecraft");
    const acp = obfCls(acpNamed);
    const rloc = obfCls("net/minecraft/util/ResourceLocation");
    const dynTex = obfCls("net/minecraft/client/renderer/texture/DynamicTexture");
    const tm = obfCls("net/minecraft/client/renderer/texture/TextureManager");
    const ni = obfCls(niSrg);

    const mth = (ownerNamed, srgName, obfDesc) => {
      const n = doc.methodObf.get(ownerNamed + "/" + srgName + "#" + obfDesc);
      if (!n) return null;
      const ownerObf = doc.obfOfNamed.get(ownerNamed);
      return { official: n, intermediary: mInter(ownerObf, n, obfDesc), named: srgName };
    };
    const need = (...xs) => { for (const x of xs) if (!x) throw new Error(`${v}: missing symbol`); };

    need(mc, acp, rloc, dynTex, tm);
    row("C", "Minecraft", mc, inter(mc), "net/minecraft/client/Minecraft");
    row("C", "AbstractClientPlayer", acp, inter(acp), acpNamed);
    row("C", "CapeGetterOwner", acp, inter(acp), acpNamed);
    row("C", "ResourceLocation", rloc, inter(rloc), "net/minecraft/util/ResourceLocation");
    row("C", "DynamicTexture", dynTex, inter(dynTex), "net/minecraft/client/renderer/texture/DynamicTexture");
    row("C", "TextureManager", tm, inter(tm), "net/minecraft/client/renderer/texture/TextureManager");

    const capeGetter = mth(acpNamed, "func_110303_q", "()L" + rloc + ";");
    const tick = mth("net/minecraft/client/Minecraft", "func_71407_l", "()V");
    const upload = mth("net/minecraft/client/renderer/texture/DynamicTexture", "func_110564_a", "()V");
    need(capeGetter, tick, upload);
    row("M", "capeGetter", capeGetter.official, capeGetter.intermediary, capeGetter.named);
    row("M", "tick", tick.official, tick.intermediary, tick.named);
    row("M", "upload", upload.official, upload.intermediary, upload.named);

    if (isNativeImage) {
      need(ni);
      row("C", "NativeImage", ni, inter(ni), niSrg);
      const getPixels = mth("net/minecraft/client/renderer/texture/DynamicTexture", "func_195414_e", "()L" + ni + ";");
      const read = mth(niSrg, "func_195713_a", "(Ljava/io/InputStream;)L" + ni + ";");
      const copyFrom = mth(niSrg, "func_195703_a", "(L" + ni + ";)V");
      need(getPixels, read, copyFrom);
      row("M", "getPixels", getPixels.official, getPixels.intermediary, getPixels.named);
      row("M", "read", read.official, read.intermediary, read.named);
      row("M", "copyFrom", copyFrom.official, copyFrom.intermediary, copyFrom.named);
    }

    meta("capeGetterReturns", "ResourceLocation");
    meta("capeGetterMethod", "func_110303_q");
    meta("dynTexCtor", isNativeImage ? "NativeImage" : "BufferedImage");
    meta("rlocCtor", "ctor");
  }

  out.push("    };");
  out.push("}");
  out.push("");

  const target = path.join(__dirname, "..", "src", "main", "java", "com", "batclient", "cosmetics", "MappingsData.java");
  fs.writeFileSync(target, out.join("\n"), "utf8");
  console.log(`wrote ${target} (${VERSIONS.length} + ${LEGACY_VERSIONS.length} versions)`);
}

main();
