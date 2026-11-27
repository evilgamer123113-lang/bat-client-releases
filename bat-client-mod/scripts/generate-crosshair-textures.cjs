// Small, crisp RGBA textures for Minecraft's native GUI renderer. No background.
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
function crc32(bytes) {
    let crc = 0xffffffff;
    for (const byte of bytes) {
        crc ^= byte;
        for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
    }
    return (crc ^ 0xffffffff) >>> 0;
}
function chunk(name, data) {
    const type = Buffer.from(name), size = Buffer.alloc(4), crc = Buffer.alloc(4);
    size.writeUInt32BE(data.length); crc.writeUInt32BE(crc32(Buffer.concat([type, data])));
    return Buffer.concat([size, type, data, crc]);
}
const root = path.resolve(__dirname, '../src/main/resources/assets/batclient/textures/gui/crosshair');
fs.mkdirSync(root, {recursive:true});
for (const style of ['cross', 'cross_dot', 'dot']) {
    const size = 9, raw = Buffer.alloc((size * 4 + 1) * size);
    for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
        const arm = (x === 4 && (y <= 2 || y >= 6)) || (y === 4 && (x <= 2 || x >= 6));
        const dot = x === 4 && y === 4;
        const on = (style !== 'dot' && arm) || (style !== 'cross' && dot);
        if (on) raw.fill(255, y * 37 + 1 + x * 4, y * 37 + 1 + x * 4 + 4);
    }
    const header = Buffer.alloc(13);
    header.writeUInt32BE(size, 0); header.writeUInt32BE(size, 4); header[8] = 8; header[9] = 6;
    fs.writeFileSync(path.join(root, style + '.png'), Buffer.concat([
        Buffer.from([137,80,78,71,13,10,26,10]), chunk('IHDR', header), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))
    ]));
}
