const fs = require('node:fs/promises');
const path = require('node:path');
const {minify: htmlMinify} = require('html-minifier-terser');
const {minify: jsMinify} = require('terser');
const CleanCSS = require('clean-css');
async function main() {
    const source = path.resolve(__dirname, '../src/main/resources/assets/batclient/web');
    const build = path.resolve(process.env.BATCLIENT_BUILD_DIR || path.resolve(__dirname, '../build'));
    const output = path.join(build, 'packaged-web/assets/batclient/web');
    await fs.mkdir(output, {recursive:true});
    for (const name of await fs.readdir(source)) {
        const input = path.join(source, name);
        if (!(await fs.stat(input)).isFile() || name.endsWith('.map')) continue;
        let data = await fs.readFile(input);
        if (name.endsWith('.js')) {
            const result = await jsMinify(data.toString('utf8'), {compress:true, mangle:true, format:{comments:false}});
            if (!result.code) throw new Error(`Empty JavaScript: ${name}`);
            data = result.code;
        } else if (name.endsWith('.css')) {
            const result = new CleanCSS({level:1}).minify(data.toString('utf8'));
            if (result.errors.length) throw new Error(result.errors.join('\n'));
            data = result.styles;
        } else if (name.endsWith('.html')) {
            data = await htmlMinify(data.toString('utf8'), {
                collapseWhitespace:true, removeComments:true, minifyCSS:true, minifyJS:true,
                removeAttributeQuotes:false, removeOptionalTags:false
            });
        }
        await fs.writeFile(path.join(output, name), data);
    }
    console.log('PACKAGED_WEB_OK (minified UI; no source maps)');
}
main().catch(error => {console.error(error); process.exitCode = 1;});
