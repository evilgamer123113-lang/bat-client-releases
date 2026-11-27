// Standalone extraction helper. Runs outside the main process so no uncaught
// async throw can ever crash the launcher window.
// Usage: (ELECTRON_RUN_AS_NODE=1) node extract-worker.js <zipPath> <outDir>
// When spawned via Electron's binary as plain node, its asar-aware fs rejects
// writing .asar files ("Invalid package"). Disable asar interception first.
process.noAsar = true;
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const zipPath = process.argv[2];
const outDir = process.argv[3];

function report(code, message) {
    try { fs.writeFileSync(path.join(outDir, '.bat-extract-result.json'), JSON.stringify({ code, message })); } catch (e) {}
    process.exit(code);
}
process.on('uncaughtException', (err) => report(1, String((err && err.stack) || err)));
process.on('unhandledRejection', (err) => report(1, String((err && err.stack) || err)));

// ---- Minimal ZIP reader (central directory + local headers + DEFLATE inflate) ----
// Handles the standard archives produced by adm-zip (no encryption, no zip64).
const SIG_EOCD = 0x06054b50;
const SIG_CEN = 0x02014b50;
const SIG_LOC = 0x04034b50;

function parseCentralDirectories(buf) {
    // Locate End Of Central Directory (EOCD) by scanning backward for the signature.
    let eocd = -1;
    for (let i = buf.length - 22; i >= 0; i--) {
        if (buf.readUInt32LE(i) === SIG_EOCD) { eocd = i; break; }
    }
    if (eocd < 0) throw new Error('ZIP: End Of Central Directory not found');
    const cenCount = buf.readUInt16LE(eocd + 10);
    const cenOffset = buf.readUInt32LE(eocd + 16);
    const entries = [];
    let p = cenOffset;
    for (let i = 0; i < cenCount; i++) {
        if (buf.readUInt32LE(p) !== SIG_CEN) throw new Error('ZIP: bad central directory entry');
        const method = buf.readUInt16LE(p + 10);
        const compSize = buf.readUInt32LE(p + 20);
        const uncompSize = buf.readUInt32LE(p + 24);
        const nameLen = buf.readUInt16LE(p + 28);
        const extraLen = buf.readUInt16LE(p + 30);
        const commentLen = buf.readUInt16LE(p + 32);
        const localOffset = buf.readUInt32LE(p + 42);
        const name = buf.toString('utf8', p + 46, p + 46 + nameLen);
        entries.push({ name, method, compSize, uncompSize, localOffset });
        p += 46 + nameLen + extraLen + commentLen;
    }
    return entries;
}

function readLocalData(buf, localOffset, compSize) {
    // Local header: sig(4) + ver(2) + flags(2) + method(2) + time(2) + date(2) + crc(4)
    //   + compSize(4) + uncompSize(4) + nameLen(2) + extraLen(2)
    const nameLen = buf.readUInt16LE(localOffset + 26);
    const extraLen = buf.readUInt16LE(localOffset + 28);
    const dataStart = localOffset + 30 + nameLen + extraLen;
    const slice = buf.slice(dataStart, dataStart + compSize);
    // Bit 3 of general purpose flag => data descriptor after data; compSize in EOCD is authoritative.
    return slice;
}

function safeJoin(outDir, entryName) {
    const n = entryName.replace(/\\/g, '/');
    if (n.includes('..') || n.startsWith('/') || /^[a-zA-Z]:/.test(n)) {
        throw new Error('unsafe path: ' + entryName);
    }
    return path.join(outDir, n);
}

function writeEntry(destPath, data) {
    fs.mkdirSync(path.dirname(destPath), { recursive: true });
    let lastErr;
    for (let attempt = 0; attempt < 6; attempt++) {
        let fd = null;
        try {
            fd = fs.openSync(destPath, 'w');
            fs.writeFileSync(fd, data);
            fs.chmodSync(destPath, 0o666);
            fs.closeSync(fd);
            return;
        } catch (e) {
            lastErr = e;
            if (fd !== null) { try { fs.closeSync(fd); } catch (e2) {} }
            if (attempt < 5) {
                const stop = Date.now() + 250 * (attempt + 1);
                while (Date.now() < stop) { /* backoff for AV locks */ }
            }
        }
    }
    throw lastErr;
}

try {
    const buf = fs.readFileSync(zipPath);
    const entries = parseCentralDirectories(buf);

    // Validation: must contain Bat Client.exe and no unsafe paths.
    let foundExe = false;
    for (const e of entries) {
        safeJoin(outDir, e.name); // throws on unsafe
        if (!e.name.endsWith('/') && e.name.replace(/\\/g, '/').toLowerCase() === 'bat client.exe') foundExe = true;
    }
    if (!foundExe) report(3, 'ZIP did not contain expected executable');
    fs.mkdirSync(outDir, { recursive: true });

    const written = new Set();
    for (const e of entries) {
        const n = e.name.replace(/\\/g, '/');
        if (n.endsWith('/')) {
            fs.mkdirSync(path.join(outDir, n), { recursive: true });
            continue;
        }
        const destPath = safeJoin(outDir, e.name);
        const key = destPath.toLowerCase();
        if (written.has(key)) continue;

        const compressed = readLocalData(buf, e.localOffset, e.compSize);
        let data;
        if (e.method === 0) data = compressed;                       // STORED
        else if (e.method === 8) data = zlib.inflateRawSync(compressed); // DEFLATED
        else report(7, 'ZIP: unsupported compression method ' + e.method + ' for ' + e.name);

        writeEntry(destPath, data);
        written.add(key);
    }

    if (!fs.existsSync(path.join(outDir, 'Bat Client.exe'))) report(4, 'Extraction did not produce app executable');
    report(0, 'ok');
} catch (e) {
    report(5, String((e && e.stack) || e));
}