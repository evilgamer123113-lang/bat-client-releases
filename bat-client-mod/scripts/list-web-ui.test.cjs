const { app, BrowserWindow } = require('electron');
const path = require('path');
const fs = require('fs');
app.disableHardwareAcceleration();
app.whenReady().then(async () => {
    const win = new BrowserWindow({show:false,width:1280,height:800,
        webPreferences:{offscreen:true,backgroundThrottling:false}});
    try {
        const root = process.env.BAT_WEB_TEST_ROOT || path.join(__dirname,'../src/main/resources/assets/batclient/web');
        await win.loadFile(path.join(root,'brand.html'));
        await win.webContents.executeJavaScript(`document.documentElement.style.width='272px'; document.documentElement.style.height='56px';`);
        await win.webContents.executeJavaScript(`document.body.style.background='#202020'`);
        await new Promise(resolve => setTimeout(resolve, 150));
        const brand = await win.webContents.executeJavaScript(`({text:document.querySelector('span').textContent,
            font:getComputedStyle(document.body).fontFamily,weight:getComputedStyle(document.body).fontWeight,
            logo:document.querySelector('img').naturalWidth,width:document.body.scrollWidth})`);
        if (brand.text !== 'BAT CLIENT' || !brand.font.includes('Segoe UI') || brand.weight !== '800' || !brand.logo || brand.width > 272)
            throw Error('Web brand failed: '+JSON.stringify(brand));
        fs.writeFileSync(path.join(__dirname,'../build/list-brand-preview.png'),(await win.webContents.capturePage({x:0,y:0,width:272,height:56})).toPNG());
        await win.loadFile(path.join(root,'list-icons.html'));
        await win.webContents.executeJavaScript(`document.body.style.background='#202020'`);
        await new Promise(resolve => setTimeout(resolve, 150));
        const icons = await win.webContents.executeJavaScript(`Array.from(document.querySelectorAll('span')).map(el=>({label:el.getAttribute('aria-label'),width:el.getBoundingClientRect().width,svg:!!el.querySelector('svg path')}))`);
        if (icons.length !== 4 || icons.some(icon=>icon.width !== 32 || !icon.svg) || icons[0].label !== 'Favorite' || icons[1].label !== 'Favorited') throw Error('Web list icons failed');
        fs.writeFileSync(path.join(__dirname,'../build/list-icons-preview.png'),(await win.webContents.capturePage({x:0,y:0,width:128,height:32})).toPNG());
        console.log('WEB_BRANDING_AND_LIST_ICONS_OK');
    } catch(error) { console.error(error); process.exitCode=1; }
    finally { win.destroy(); app.exit(process.exitCode || 0); }
});
