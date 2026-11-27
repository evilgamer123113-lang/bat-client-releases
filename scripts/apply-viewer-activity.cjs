'use strict';
function applyViewerActivity(html) {
    if(html.includes("require('./launcher/viewer-activity')"))return html;
    const ipc="const { ipcRenderer } = require('electron');";
    if(!html.includes(ipc))throw Error('Unrecognized launcher renderer');
    html=html.replace(ipc,ipc+"\n        const viewerActivity = require('./launcher/viewer-activity').createViewerActivity({window,document});");
    let count=0;
    html=html.replace(/new skinview3d\.SkinViewer\((\{[^]*?\})\)/g,(_,options)=>{count++;return 'viewerActivity.track(new skinview3d.SkinViewer('+options+'))';});
    if(count!==4)throw Error('Expected four known viewer constructors; got '+count);
    const start=html.indexOf('        function attachAnimatedCape('),closing=/\r?\n        }\r?\n/g;
    closing.lastIndex=start;const match=closing.exec(html),end=match?match.index+match[0].length:-1;
    if(start<0||end<start||!html.slice(start,end).includes('viewer.__capeAnimTimer'))throw Error('Unrecognized cape scheduler');
    const cape=`        function attachAnimatedCape(viewer,capeDataUrl,frames,delays){
            if(!viewer)return;
            if(viewer.__pauseCape)viewer.__pauseCape();
            if(viewer.__capeAnimTimer)clearTimeout(viewer.__capeAnimTimer);
            viewer.__capeAnimTimer=null;viewer.__resumeCape=null;viewer.__pauseCape=null;
            const generation=viewer.__capeGeneration=(viewer.__capeGeneration||0)+1;
            if(!frames||frames.length<2){safeLoadCape(viewer,capeDataUrl);return;}
            const imgs=[];let pending=frames.length;
            const start=()=>{
                if(viewer.disposed||viewer.__capeGeneration!==generation)return;
                if(imgs.length<2){safeLoadCape(viewer,capeDataUrl);return;}
                try{viewer.playerObject.backEquipment='cape';}catch(e){}
                let fi=0;
                const pause=()=>{if(viewer.__capeAnimTimer)clearTimeout(viewer.__capeAnimTimer);viewer.__capeAnimTimer=null;};
                const step=()=>{
                    viewer.__capeAnimTimer=null;
                    if(viewer.disposed||viewer.renderPaused||viewer.__capeGeneration!==generation)return;
                    const im=imgs[fi];
                    if(im){const size=capeCanvasSizeFor(im);try{const c=viewer.capeCanvas;if(c){c.width=size.w;c.height=size.h;const ctx=c.getContext('2d');ctx.clearRect(0,0,size.w,size.h);ctx.drawImage(im,0,0,size.w,size.h);}if(viewer.recreateCapeTexture)viewer.recreateCapeTexture();}catch(e){}}
                    const delay=(delays&&delays[fi])?delays[fi]:100;fi=(fi+1)%imgs.length;
                    viewer.__capeAnimTimer=setTimeout(step,delay);
                };
                viewer.__pauseCape=pause;
                viewer.__resumeCape=()=>{if(!viewer.disposed&&!viewer.renderPaused&&viewer.__capeAnimTimer===null&&viewer.__capeGeneration===generation)viewer.__capeAnimTimer=setTimeout(step,50);};
                viewer.__resumeCape();
            };
            frames.forEach(src=>{const im=new Image();im.onload=()=>{imgs.push(im);if(--pending===0)start();};im.onerror=()=>{if(--pending===0)start();};im.src=src;});
        }`;
    return html.slice(0,start)+cape+html.slice(end);
}
module.exports={applyViewerActivity};
