const fs = require('fs');
const path = require('path');

exports.default = async function(context) {
  const appDir = context.appOutDir;
  const filesToRemove = [
    'dxcompiler.dll',
    'dxil.dll',
    'vk_swiftshader.dll',
    'vk_swiftshader_icd.json',
    'LICENSES.chromium.html'
  ];
  for (const f of filesToRemove) {
    const p = path.join(appDir, f);
    if (fs.existsSync(p)) {
      try { fs.unlinkSync(p); } catch(e) {}
    }
  }
};
