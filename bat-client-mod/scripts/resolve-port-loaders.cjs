'use strict';
// Read-only upstream discovery. Never downloads/installs a mod or alters a game profile.
const fs = require('node:fs/promises');
const path = require('node:path');
const { targets } = require('../ports/targets.cjs');
const FABRIC = 'https://meta.fabricmc.net/v2/versions';
const LEGACY = 'https://meta.legacyfabric.net/v2/versions';
const FORGE = 'https://maven.minecraftforge.net/net/minecraftforge/forge';
const API = 'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml';

async function read(url, json = true) {
  const response = await fetch(url, {signal: AbortSignal.timeout(30000)});
  if (!response.ok) throw Error(`Upstream returned HTTP ${response.status}: ${url}`);
  return json ? response.json() : response.text();
}
function versions(xml) { return Array.from(xml.matchAll(/<version>([^<]+)<\/version>/g), match => match[1]); }
function selectFabricApi(all, minecraft) {
  return all.filter(value => value.endsWith('+' + minecraft)).sort((a, b) =>
    a.split('+')[0].localeCompare(b.split('+')[0], 'en', {numeric: true})).pop() || null;
}
async function resolve(value, forgeVersions, promos, apiVersions) {
  if (value.loader === 'forge') {
    const available = forgeVersions.filter(version => version.startsWith(value.minecraft + '-'));
    const preferred = promos[`${value.minecraft}-recommended`] || promos[`${value.minecraft}-latest`];
    const selected = preferred && available.find(version => version === `${value.minecraft}-${preferred}`)
      || available.sort((a,b) => a.localeCompare(b, 'en', {numeric:true})).pop();
    if (!selected) throw Error('No official Forge build for ' + value.minecraft);
    return { distribution: 'forge', loader: {version: selected, maven: 'net.minecraftforge:forge:' + selected},
      source: FORGE + '/maven-metadata.xml',
      download: `${FORGE}/${selected}/forge-${selected}-installer.jar` };
  }
  const base = value.distribution === 'legacy-fabric' ? LEGACY : FABRIC;
  const [loaders, mappings] = await Promise.all([read(`${base}/loader/${value.minecraft}`), read(`${base}/yarn/${value.minecraft}`)]);
  const selected = loaders.find(entry => entry.loader.stable) || loaders[0];
  const mapping = mappings.find(entry => entry.stable) || mappings[0];
  if (!selected || !mapping) throw Error('No loader/mappings for ' + value.id);
  return { distribution: value.distribution, loader: {version:selected.loader.version, maven:selected.loader.maven},
    intermediary: selected.intermediary.maven, mappings: {version:mapping.version, maven:mapping.maven},
    fabricApi: value.distribution === 'fabric' ? selectFabricApi(apiVersions, value.minecraft) : null,
    source: `${base}/loader/${value.minecraft}` };
}
async function main() {
  const [forgeXml, promotionData, apiXml] = await Promise.all([
    read(FORGE + '/maven-metadata.xml', false),
    read('https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json'),
    read(API, false)
  ]);
  const queue = targets(); const resolved = {}; const errors = [];
  // Bounded concurrency; a failed refresh never replaces a previously complete lock.
  await Promise.all(Array.from({length:4}, async () => {
    for (let value; (value = queue.shift()); ) {
      try {
        resolved[value.id] = await resolve(value, versions(forgeXml), promotionData.promos, versions(apiXml));
        console.log('UPSTREAM_OK ' + value.id);
      } catch(error) { errors.push(error.message); }
    }
  }));
  if (errors.length) throw Error(errors.join('\n'));
  const ordered = Object.fromEntries(targets().map(value => [value.id, resolved[value.id]]));
  const output = path.resolve(__dirname, '../ports/loaders.lock.json');
  const temp = output + '.tmp';
  await fs.writeFile(temp, JSON.stringify({schema:1, checkedAt:new Date().toISOString(), note:'Upstream loader availability only. Does not certify Bat Client compatibility. Existing 1.21.1 dependency pins stay unchanged.', targets:ordered}, null, 2) + '\n');
  await fs.rename(temp, output);
  console.log(`UPSTREAM_MATRIX_OK: ${Object.keys(ordered).length} combinations`);
}
if (require.main === module) main().catch(error => {console.error(error.message);process.exitCode=1;});
module.exports = { versions, selectFabricApi, resolve };
