const fs = require('node:fs');
const path = require('node:path');
const {spawn,execFileSync} = require('node:child_process');
const [platform, arch] = process.argv.slice(2);
const binary = platform === 'mac'
  ? path.resolve('dist', arch === 'arm64' ? 'mac-arm64' : 'mac', 'Bat Client.app/Contents/MacOS/Bat Client')
  : platform === 'win' ? path.resolve('dist/win-unpacked/Bat Client.exe') : path.resolve('dist/linux-unpacked/bat-client');
if (!fs.existsSync(binary)) throw new Error('Missing native executable: ' + binary);
const child = spawn(binary, ['--disable-gpu'], {stdio:['ignore','pipe','pipe'], detached:true});
let output = '', finished = false;
child.stdout.on('data', chunk => { output += chunk; });
child.stderr.on('data', chunk => { output += chunk; });
child.on('error', error => { console.error(error.message); process.exitCode = 1; });
child.on('exit', (code, signal) => {
  if (!finished) { console.error(output); console.error('Launcher exited during startup:', code, signal); process.exitCode = 1; }
});
setTimeout(() => {
  finished = true;
  if (child.exitCode === null && child.signalCode === null && !/A JavaScript error occurred|Error: Cannot find module|Unhandled Exception/i.test(output)) {
    console.log('NATIVE_LAUNCHER_STARTUP_OK', platform, arch);
  } else { console.error(output); process.exitCode = 1; }
  try {
    if (platform === 'win') execFileSync('taskkill',['/PID',String(child.pid),'/T','/F'],{stdio:'ignore'});
    else process.kill(-child.pid, 'SIGTERM');
  } catch {}
}, 12000);
