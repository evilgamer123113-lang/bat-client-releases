'use strict';
// Development-only HTML renderer. Not a launcher backend or a packaged runtime.
const {app,BrowserWindow}=require('electron');
const path=require('node:path'),fs=require('node:fs');
const {createPreviewServer,fetchChangelog}=require('./preview-server.cjs');
const isolatedData='D:/BatClientBuildCache/rust-preview-session';fs.mkdirSync(isolatedData,{recursive:true});app.setPath('userData',isolatedData);
app.commandLine.appendSwitch('disable-gpu');
app.whenReady().then(async()=>{
  const output=path.resolve(__dirname,'../output');fs.mkdirSync(output,{recursive:true});
  let offline=false;const probeRequests=[];
  const server=createPreviewServer({getChangelog:()=>offline?Promise.reject(Error('offline test')):fetchChangelog()});
  server.on('request',req=>{if(req.url==='/__news_probe')probeRequests.push(req.url);});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const window=new BrowserWindow({show:false,frame:false,width:1320,height:850,webPreferences:{nodeIntegration:false,contextIsolation:true,sandbox:true,backgroundThrottling:false,offscreen:true,partition:'bat-next-preview-render'}});
  try{
    await window.loadURL(`http://127.0.0.1:${server.address().port}/`);
    await window.webContents.insertCSS('*,*::before,*::after{animation:none!important;transition:none!important}');
    await window.webContents.executeJavaScript('document.fonts.ready');
    await window.webContents.executeJavaScript('window.BatNews.ready');
    await window.webContents.executeJavaScript(`document.body.classList.add('reduce-motion');new Promise((resolve,reject)=>{const art=new Image();art.onload=resolve;art.onerror=reject;art.src='assets/home-widget-generated-v3.png';})`);
    await new Promise(resolve=>setTimeout(resolve,650));
    const initial=await window.webContents.executeJavaScript(`({title:document.title,opacity:getComputedStyle(document.querySelector('.page.active')).opacity,overflow:document.documentElement.scrollWidth>innerWidth,mainScroll:document.querySelector('main').scrollHeight,mainHeight:document.querySelector('main').clientHeight,launchDisabled:document.querySelector('#play-button').disabled,launchInHero:!!document.querySelector('.hero #play-button'),loadoutInHero:!!document.querySelector('.hero #profile-select'),controls:[...document.querySelectorAll('[data-window]')].map(el=>el.textContent)})`);
    fs.writeFileSync(path.join(output,'home-preview.png'),(await window.webContents.capturePage()).toPNG());
    const picker=await window.webContents.executeJavaScript(`(()=>{document.querySelector('#loadout-trigger').click();const popup=document.querySelector('#loadout-popup'),rect=popup.getBoundingClientRect();return{open:popup.matches(':popover-open'),selected:document.querySelector('#loadout-options .picker-option[aria-selected="true"] strong')?.textContent,inViewport:rect.top>=0&&rect.left>=0&&rect.right<=innerWidth&&rect.bottom<=innerHeight};})()`);
    await new Promise(resolve=>setTimeout(resolve,200));
    fs.writeFileSync(path.join(output,'loadout-picker-preview.png'),(await window.webContents.capturePage()).toPNG());
    const pickerClosed=await window.webContents.executeJavaScript(`(()=>{document.querySelector('#loadout-options .picker-option').click();return !document.querySelector('#loadout-popup').matches(':popover-open')&&document.activeElement.id==='loadout-trigger';})()`);
    if(!picker.open||!picker.selected||!picker.inViewport||!pickerClosed)throw Error('Custom loadout picker interaction failed');
    const accounts=await window.webContents.executeJavaScript(`(()=>{document.querySelector('#account-trigger').click();const popup=document.querySelector('#account-popup');const opened=popup.matches(':popover-open');const entries=[...document.querySelectorAll('#account-options .picker-option')];entries[1].click();const switched=document.querySelector('#account-trigger .username').textContent==='Guest';document.querySelector('#account-trigger').click();document.querySelector('#account-options .picker-option').click();return {opened,switched,closed:!popup.matches(':popover-open'),restored:document.querySelector('#account-trigger .username').textContent==='Player'};})()`);
    if(!accounts.opened||!accounts.switched||!accounts.closed||!accounts.restored)throw Error('Account picker failed');
    const news=await window.webContents.executeJavaScript(`({state:document.querySelector('#news-panel').dataset.state,release:window.BatNews.cached})`);
    if(news.state!=='live'||news.release.source!=='https://batclient.xyz/changelog.html')throw Error('Live changelog fetch failed');
    const parsing=await window.webContents.executeJavaScript(`(()=>{
      const input='<div class="changelog"><div class="cl-item"><div class="cl-ver">1.0.0</div><div class="cl-body"><h3>Older release</h3><ul><li>Old</li></ul></div></div><div class="cl-item"><div class="cl-ver">9.9.9<span class="cl-date">Test date</span><span class="cl-tag">Latest</span></div><div class="cl-body"><h3><b>Safe title</b><img src="/__news_probe" onerror="window.__newsInjected=true"></h3><ul><li>Only text<script>window.__newsInjected=true<'+ '/script></li></ul></div></div></div>';
      const result=window.BatNews.parse(input);let malformedRejected=false;try{window.BatNews.parse('<h1>No releases</h1>');}catch{malformedRejected=true;}
      return {latestSelected:result.version==='9.9.9',plainTitle:result.title==='Safe title',plainSummary:result.summary==='Only text',malformedRejected,injected:window.__newsInjected===true};
    })()`);
    await new Promise(resolve=>setTimeout(resolve,100));
    offline=true;await window.webContents.executeJavaScript('window.BatNews.refresh()');
    const cached=await window.webContents.executeJavaScript(`({state:document.querySelector('#news-panel').dataset.state,title:document.querySelector('#news-title').textContent,status:document.querySelector('#news-source').textContent})`);
    if(!parsing.latestSelected||!parsing.plainTitle||!parsing.plainSummary||!parsing.malformedRejected||parsing.injected||probeRequests.length||cached.state!=='cached'||cached.title!==news.release.title||!cached.status.includes('Offline'))throw Error('Changelog safety/offline checks failed');
    await window.webContents.executeJavaScript(`document.querySelector('[data-page="settings"]').click();document.querySelector('#art-toggle').click();document.querySelector('#motion-toggle').click();`);
    const settings=await window.webContents.executeJavaScript(`({page:document.querySelector('.page.active').id,artOff:document.body.classList.contains('no-art'),reduced:document.body.classList.contains('reduce-motion')})`);
    if(initial.opacity!=='1'||initial.overflow||!initial.launchDisabled||!initial.launchInHero||!initial.loadoutInHero||initial.controls.join('')!=='−□×'||settings.page!=='page-settings'||!settings.artOff||!settings.reduced)throw Error('Preview navigation/settings/layout checks failed');
    fs.writeFileSync(path.join(output,'preview-checks.json'),JSON.stringify({initial,settings,news,parsing,cached},null,2));
    console.log('PREVIEW_CHECKS_PASS',JSON.stringify({initial,settings,news,parsing,cached}));
  }finally{window.destroy();server.closeAllConnections();await new Promise(resolve=>server.close(resolve));app.quit();}
}).catch(error=>{console.error(error);app.exit(1);});
