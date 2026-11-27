const {app,BrowserWindow}=require('electron');
const path=require('node:path');
app.disableHardwareAcceleration();
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
app.whenReady().then(async()=>{
    const win=new BrowserWindow({show:false,width:1280,height:800,webPreferences:{partition:'bat-scroll-smoke',backgroundThrottling:false,offscreen:true}});
    try{
        await win.loadFile(path.join(process.env.BAT_WEB_TEST_ROOT||path.join(__dirname,'../src/main/resources/assets/batclient/web'),'overlay.html'));
        const js=code=>win.webContents.executeJavaScript(code);
        await js(`document.body.classList.remove('hud-idle');document.body.classList.add('hud-menu');openPanel('mods');document.getAnimations().forEach(animation=>animation.finish())`);
        await pause(100);
        await js(`document.querySelector('.mm-body').scrollTop=0;document.querySelector('.mm-body').dispatchEvent(new WheelEvent('wheel',{bubbles:true,cancelable:true,deltaY:200}))`);
        await pause(35);
        const midway=await js(`document.querySelector('.mm-body').scrollTop`);
        if(!(midway>0&&midway<200))throw Error('Wheel must move progressively: '+midway+' '+JSON.stringify(await js(`(()=>{const el=document.querySelector('.mm-body');return {height:el.clientHeight,total:el.scrollHeight,body:document.body.className,reduced:matchMedia('(prefers-reduced-motion: reduce)').matches};})()`)));
        await pause(240);
        if(Math.abs(await js(`document.querySelector('.mm-body').scrollTop`)-200)>2)throw Error('Scroll did not settle');
        await js(`document.querySelector('.mm-body').dispatchEvent(new WheelEvent('wheel',{bubbles:true,cancelable:true,deltaY:100000}))`);
        await pause(300);
        if(!await js(`(()=>{const el=document.querySelector('.mm-body');return el.scrollTop<=el.scrollHeight-el.clientHeight+1;})()`))throw Error('Scroll escaped bounds');
        console.log('MENU_SCROLL_OK: progressive movement, settling and bounds');app.exit(0);
    }catch(error){console.error(error.stack);app.exit(1);}
});
