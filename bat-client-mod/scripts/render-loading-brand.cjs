// Offline source-asset renderer. No web view, fonts or PNG generation at game startup.
const {app,BrowserWindow}=require('electron');
const fs=require('node:fs'),path=require('node:path');
app.disableHardwareAcceleration();
app.whenReady().then(async()=>{
    const win=new BrowserWindow({show:false,webPreferences:{backgroundThrottling:false}});
    try {
        await win.loadURL('data:text/html,<html><body></body></html>');
        const logo='data:image/png;base64,'+fs.readFileSync(path.join(__dirname,'../src/main/resources/assets/batclient/textures/gui/bat-logo.png')).toString('base64');
        const result=await win.webContents.executeJavaScript(`(async()=>{
            const image=new Image();image.src=${JSON.stringify(logo)};await image.decode();
            const canvas=document.createElement('canvas');const ctx=canvas.getContext('2d');
            const font='800 128px "Segoe UI",Arial,sans-serif';ctx.font=font;
            canvas.width=Math.ceil(288+ctx.measureText('BAT CLIENT').width+32)*2;canvas.height=512;
            ctx.scale(2,2);ctx.imageSmoothingEnabled=true;ctx.imageSmoothingQuality='high';
            ctx.font=font;ctx.fillStyle='#FFFFFF';ctx.textBaseline='alphabetic';
            ctx.drawImage(image,51,159,410,194,24,74,224,106);
            ctx.fillText('BAT CLIENT',288,173);
            // Preserve alpha/antialiasing while removing tinted fringes in the source logo.
            ctx.globalCompositeOperation='source-in';ctx.fillStyle='#FFFFFF';ctx.fillRect(0,0,canvas.width,canvas.height);
            return {png:canvas.toDataURL('image/png').split(',')[1],width:canvas.width,height:canvas.height};
        })()`);
        const out=path.join(__dirname,'../src/main/resources/assets/batclient/textures/gui/loading-brand.png');
        fs.writeFileSync(out,Buffer.from(result.png,'base64'));console.log('LOADING_BRAND_ASSET_OK',result.width,result.height);
    }catch(error){console.error(error);process.exitCode=1;}finally{win.destroy();app.quit();}
});
