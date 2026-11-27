'use strict';
const fs = require('node:fs');
const path = require('node:path');
const config = require('./targets.json');
const root = path.resolve(__dirname, '..');

function javaFor(version) {
  const [major, minor, patch = 0] = version.split('.').map(Number);
  if (major !== 1) throw Error('Unrecognized Minecraft version: ' + version);
  if (minor < 17) return 8;
  if (minor === 17) return 16;
  return minor > 20 || (minor === 20 && patch >= 5) ? 21 : 17;
}
function targets() {
  return config.minecraft.flatMap(minecraft => config.loaders.map(loader => {
    const id = `${loader}-${minecraft}`;
    const implemented = config.implemented[id];
    return {
      id, minecraft, loader,
      distribution: loader === 'fabric' && config.legacyFabric.includes(minecraft) ? 'legacy-fabric' : loader,
      java: implemented?.java ?? javaFor(minecraft),
      state: implemented ? 'implemented' : 'port-required',
      ...(implemented || {})
    };
  }));
}
function target(id) {
  const value = targets().find(entry => entry.id === id);
  if (!value) throw Error('Unknown target: ' + id);
  return value;
}
function buildPlan(id) {
  const value = target(id);
  if (value.state !== 'implemented') {
    throw Error(`${id}: game/render/input/network adapters are not ported yet. No compatible JAR can be produced from the 1.21.1 sources.`);
  }
  const projectArgs=value.project&&value.project!=='.'?['-p',value.project]:[];
  return { cwd: root, command: process.platform === 'win32' ? 'gradlew.bat' : './gradlew',
    args: [...projectArgs,'build', value.verificationTask||'verifyProtectedJar', '--offline', '--console=plain'], target: value };
}
// Loader availability is deliberately separate from Bat Client implementation status.
function releaseTarget(id) {
  const value = target(id);
  if (value.state !== 'implemented' || value.runtime !== 'verified-clean')
    throw Error(`${id} is not approved for a stable release; complete the port and clean runtime verification first.`);
  return value;
}
if (require.main === module) {
  const [action = 'list', id] = process.argv.slice(2);
  try {
    if (action === 'list') {
      for (const value of targets()) console.log(`${value.id.padEnd(16)} ${value.distribution.padEnd(14)} Java ${String(value.java).padEnd(2)} ${value.state}`);
    } else if (action === 'build') {
      const plan = buildPlan(id);
      const result = require('node:child_process').spawnSync(plan.command, plan.args, {cwd: plan.cwd, stdio: 'inherit', shell: process.platform === 'win32'});
      if (result.error) throw result.error;
      process.exitCode = result.status ?? 1;
    } else if (action === 'report') {
      const lock = JSON.parse(fs.readFileSync(path.join(__dirname, 'loaders.lock.json'), 'utf8'));
      for (const value of targets()) console.log(`${value.id}: ${value.state}; upstream loader=${lock.targets[value.id]?.loader?.version || 'unresolved'}`);
    } else throw Error('Usage: node ports/targets.cjs list|report|build <loader-version>');
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
module.exports = { targets, target, javaFor, buildPlan, releaseTarget };
