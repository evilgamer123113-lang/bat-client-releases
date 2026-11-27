const fs = require('node:fs');
const path = require('node:path');
const {execFile} = require('node:child_process');
const {promisify} = require('node:util');
const run = promisify(execFile);

function javaMajor(output) {
    const match = String(output).match(/(?:java|openjdk)\s+(?:version\s+)?["']?(\d+)(?:\.(\d+))?/i);
    if (!match) return null;
    return Number(match[1]) === 1 ? Number(match[2]) : Number(match[1]);
}
async function probeJava(executable, runner = run) {
    try {
        const result = await runner(executable, ['-version'], {timeout:5000, windowsHide:true, maxBuffer:128*1024});
        const output = String(result.stderr || '') + '\n' + String(result.stdout || '');
        const major = javaMajor(output);
        return major ? {path:executable, major, version:output.trim().split(/\r?\n/)[0]} : null;
    } catch { return null; }
}
function javaCandidates({custom, bundled, env=process.env, platform=process.platform}={}) {
    const name = platform === 'win32' ? 'java.exe' : 'java';
    const candidates = [];
    const add = value => {
        if (!value) return;
        let candidate = String(value).trim().replace(/^"(.*)"$/, '$1');
        try { if (fs.statSync(candidate).isDirectory()) candidate = path.join(candidate,'bin',name); } catch { }
        if (platform === 'win32' && /javaw\.exe$/i.test(candidate)) candidate = candidate.replace(/javaw\.exe$/i,'java.exe');
        if (!candidates.includes(candidate)) candidates.push(candidate);
    };
    add(custom); add(bundled);
    for (const key of ['JAVA_HOME','JDK_HOME']) if (env[key]) add(path.join(env[key],'bin',name));
    add('java');
    const roots = platform === 'win32'
        ? [env.ProgramFiles,env['ProgramFiles(x86)']].filter(Boolean).flatMap(root=>['Java','Eclipse Adoptium','Microsoft','Zulu','BellSoft'].map(v=>path.join(root,v)))
        : platform === 'darwin' ? ['/Library/Java/JavaVirtualMachines',path.join(env.HOME || '', 'Library/Java/JavaVirtualMachines')]
        : ['/usr/lib/jvm','/usr/java','/opt/java'];
    for (const root of roots) {
        try { for (const entry of fs.readdirSync(root,{withFileTypes:true})) {
            if (!entry.isDirectory()&&!entry.isSymbolicLink()) continue;
            const home=path.join(root,entry.name,platform==='darwin'?'Contents/Home':'');
            const executable=path.join(home,'bin',name); if(fs.existsSync(executable))add(executable);
        } } catch { }
    }
    return candidates;
}
async function findJava(major, options={}, probe=probeJava) {
    for (const candidate of javaCandidates(options)) {const result=await probe(candidate);if(result?.major===Number(major))return result;}
    return null;
}
module.exports={javaMajor,probeJava,javaCandidates,findJava};
