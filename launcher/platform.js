const path = require('node:path');
function platformInfo(platform = process.platform, arch = process.arch) {
    if (!['win32', 'darwin', 'linux'].includes(platform) || !['x64', 'arm64'].includes(arch)) throw new Error('Unsupported launcher platform: ' + platform + '/' + arch);
    return { javaName: platform === 'win32' ? 'java.exe' : 'java',
        javaOs: {win32:'windows',darwin:'mac',linux:'linux'}[platform],
        gameOs: {win32:'windows',darwin:'osx',linux:'linux'}[platform],
        javaArch: arch === 'arm64' ? 'aarch64' : 'x64', archive: platform === 'win32' ? '.zip' : '.tar.gz' };
}
function javaExecutable(root, platform = process.platform) { return path.join(root, 'bin', platform === 'win32' ? 'java.exe' : 'java'); }
module.exports = { platformInfo, javaExecutable };
