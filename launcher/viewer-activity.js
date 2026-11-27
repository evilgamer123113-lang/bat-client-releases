'use strict';

/** Stop hidden/background skin render loops, keeping loaded skins/capes ready for reopening. */
function createViewerActivity({window: win=globalThis.window, document: doc=globalThis.document}={}) {
    const entries=new Set();let pending=null,closed=false;
    const style=doc.createElement('style');
    style.textContent='html.launcher-background *,html.launcher-background *::before,html.launcher-background *::after{animation-play-state:paused!important}';
    doc.head.appendChild(style);
    function visible(canvas) {
        if(!canvas?.isConnected||!canvas.getClientRects().length)return false;
        const css=win.getComputedStyle(canvas);
        if(css.visibility==='hidden'||css.visibility==='collapse')return false;
        const r=canvas.getBoundingClientRect();
        return r.width>0&&r.height>0&&r.bottom>0&&r.right>0&&r.top<win.innerHeight&&r.left<win.innerWidth;
    }
    function refresh() {
        if(closed)return;
        const foreground=!doc.hidden&&doc.hasFocus();
        if(doc.documentElement.classList.contains('launcher-background')===foreground)
            doc.documentElement.classList.toggle('launcher-background',!foreground);
        for(const viewer of entries) {
            if(viewer.disposed){viewer.__pauseCape?.();entries.delete(viewer);continue;}
            const paused=!foreground||!visible(viewer.canvas);
            if(viewer.renderPaused!==paused)viewer.renderPaused=paused;
            if(paused)viewer.__pauseCape?.();else viewer.__resumeCape?.();
        }
    }
    function schedule() {
        if(!closed&&pending===null)pending=win.requestAnimationFrame(()=>{pending=null;refresh();});
    }
    const observer=new win.MutationObserver(schedule);
    observer.observe(doc.documentElement,{subtree:true,childList:true,attributes:true,attributeFilter:['class','style']});
    for(const name of ['focus','blur'])win.addEventListener(name,refresh);
    win.addEventListener('resize',schedule);win.addEventListener('scroll',schedule,true);
    doc.addEventListener('visibilitychange',refresh);
    function dispose() {
        if(closed)return;
        for(const viewer of entries){viewer.renderPaused=true;viewer.__pauseCape?.();}
        entries.clear();closed=true;observer.disconnect();
        if(pending!==null)win.cancelAnimationFrame(pending);
        for(const name of ['focus','blur'])win.removeEventListener(name,refresh);
        win.removeEventListener('resize',schedule);win.removeEventListener('scroll',schedule,true);
        doc.removeEventListener('visibilitychange',refresh);win.removeEventListener('beforeunload',dispose);
    }
    win.addEventListener('beforeunload',dispose);
    refresh();
    return {track(viewer){if(closed)throw Error('Viewer activity is closed');entries.add(viewer);refresh();return viewer;},refresh,dispose};
}
module.exports={createViewerActivity};
