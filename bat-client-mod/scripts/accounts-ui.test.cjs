const {app,BrowserWindow}=require('electron');
const path=require('node:path');const fs=require('node:fs');
app.disableHardwareAcceleration();
app.whenReady().then(async()=>{
    const win=new BrowserWindow({show:false,width:1100,height:760,webPreferences:{backgroundThrottling:false,offscreen:true}});
    try {
        await win.loadFile(path.join(process.env.BAT_WEB_TEST_ROOT||path.join(__dirname,'../src/main/resources/assets/batclient/web'),'index.html'));
        const result=await win.webContents.executeJavaScript(`(${async function(){
            let checks=0;const assert=(ok,text)=>{if(!ok)throw Error(text);checks++;};
            const state={active:'launch',current:'Player186',busy:false,connected:false,phase:'idle',message:'',accounts:[{id:'launch',name:'Player186',kind:'Launcher',expired:false}]};
            const sourceHead=document.createElement('canvas');sourceHead.width=8;sourceHead.height=8;const sourcePixels=sourceHead.getContext('2d');
            for(let y=0;y<8;y++)for(let x=0;x<8;x++){sourcePixels.fillStyle=(x+y)%2?'#ff0000':'#000000';sourcePixels.fillRect(x,y,1,1);}
            const skinHead=sourceHead.toDataURL('image/png');
            state.currentHead=skinHead;state.accounts[0].head=skinHead;window.batPlayerHead=()=>state.currentHead;
            const calls=[];window.batAccounts=(action,value)=>{
                calls.push([action,value]);
                if(action==='offline')state.accounts.push({id:'offline:'+value,name:value,kind:'Offline',expired:false,head:skinHead});
                if(action==='switch'){state.active=value;state.current=state.accounts.find(a=>a.id===value).name;}
                if(action==='remove'){if(value===state.active){state.active='launch';state.current='Player186';}state.accounts=state.accounts.filter(a=>a.id!==value);}
                if(action==='microsoft')state.phase='browser';if(action==='cancel')state.phase='idle';
                return JSON.stringify(state);
            };
            document.getElementById('account-open').click();
            assert(!document.getElementById('account-panel').classList.contains('hidden'),'panel opens from player pill');
            assert(document.getElementById('account-open').getAttribute('aria-expanded')==='true','expanded accessible state');
            assert(document.querySelector('.account-row strong').textContent==='Player186','launcher account present');
            assert(document.querySelector('.account-face').dataset.skinUrl===skinHead&&document.getElementById('user-avatar').dataset.skinUrl===skinHead,'account card and player pill both use native skin head PNGs');
            await new Promise(resolve=>{const image=new Image();image.onload=resolve;image.src=skinHead;});
            await new Promise(resolve=>requestAnimationFrame(resolve));
            for(const [canvas,scale] of [[document.querySelector('.account-face'),8],[document.getElementById('user-avatar'),5]]) {
                const pixels=canvas.getContext('2d').getImageData(0,0,canvas.width,canvas.height).data;
                let exact=true;for(let y=0;y<canvas.height;y++)for(let x=0;x<canvas.width;x++){const i=(y*canvas.width+x)*4;const red=(Math.floor(x/scale)+Math.floor(y/scale))%2?255:0;if(pixels[i]!==red||pixels[i+1]!==0||pixels[i+2]!==0||pixels[i+3]!==255)exact=false;}
                assert(exact,'head enlargement preserves exact source pixel colors at scale '+scale);
            }
            const originalCard=document.querySelector('.account-row');
            state.accounts[0].head='';renderAccounts(state);state.accounts[0].head=skinHead;renderAccounts(state);
            assert(document.querySelector('.account-row')===originalCard,'skin loading does not recreate cards or replay their animations');
            assert(!document.getElementById('account-layer').classList.contains('hidden'),'modal backdrop opens');
            assert(document.getElementById('account-panel').getAttribute('aria-modal')==='true','compact picker is modal');
            assert(document.getElementById('account-panel').getBoundingClientRect().width<=500,'picker stays compact');
            document.querySelector('.account-add-tile').click();
            assert(document.getElementById('account-panel').classList.contains('account-adding'),'add account opens a separate step');
            document.getElementById('account-tab-offline').click();
            assert(document.getElementById('account-ms-pane').classList.contains('hidden')&&!document.getElementById('account-offline-pane').classList.contains('hidden'),'offline tab shows only offline form');
            const name=document.getElementById('account-name');name.value='NightBat';
            document.getElementById('account-offline').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
            assert(calls.some(c=>c[0]==='offline'&&c[1]==='NightBat'),'offline add passes exact name');
            assert(document.querySelectorAll('.account-row').length===2,'saved offline row rendered');
            assert(document.querySelectorAll('.account-remove').length===2,'every card has a remove button');
            assert(document.querySelector('.account-remove').disabled,'launcher account remains protected');
            document.querySelectorAll('.account-row')[1].click();
            assert(document.getElementById('user-name').textContent==='NightBat','active username updates immediately');
            assert(document.querySelectorAll('.account-row')[1].classList.contains('active'),'active account highlighted');
            accountRequest('offline','RemoveMe');
            const switchCount=calls.filter(c=>c[0]==='switch').length;
            [...document.querySelectorAll('.account-row')].find(row=>row.querySelector('strong').textContent==='RemoveMe').querySelector('.account-remove').click();
            assert(state.current==='NightBat'&&calls.filter(c=>c[0]==='switch').length===switchCount,'removing a card does not trigger switching');
            document.querySelectorAll('.account-row')[1].querySelector('.account-remove').click();
            assert(state.current==='Player186'&&state.accounts.length===1,'removing active account returns to launcher');
            accountRequest('offline','NightBat');document.querySelectorAll('.account-row')[1].querySelector('.account-pick').focus();document.querySelectorAll('.account-row')[1].querySelector('.account-pick').click();
            assert(document.getElementById('account-list').contains(document.activeElement),'keyboard focus survives switching and rebuilding cards');
            assert(document.querySelector('.account-pick').getAttribute('aria-pressed')==='false'&&document.querySelectorAll('.account-pick')[1].getAttribute('aria-pressed')==='true','keyboard-accessible card buttons expose active state');
            document.querySelector('.account-add-tile').click();
            document.getElementById('account-tab-microsoft').click();
            assert(document.getElementById('account-offline-pane').classList.contains('hidden'),'Microsoft tab hides offline form');
            document.getElementById('account-microsoft').click();
            assert(!document.getElementById('account-finish').classList.contains('hidden'),'browser sign-in finish step appears');
            assert(document.getElementById('account-redirect').type==='password','sign-in address is masked');
            const redirect=document.getElementById('account-redirect');redirect.value='private-test-url';
            document.getElementById('account-finish').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
            assert(!redirect.value&&calls.some(c=>c[0]==='finish'&&c[1]==='private-test-url'),'submitted address cleared from input');
            state.busy=true;renderAccounts(state);
            assert(document.getElementById('account-microsoft').disabled,'concurrent login disabled');
            assert([...document.querySelectorAll('.account-row button')].every(b=>b.disabled),'switch/remove disabled during login');
            state.busy=false;state.connected=true;renderAccounts(state);
            assert([...document.querySelectorAll('.account-pick')].every(b=>b.disabled),'cannot switch in a world');
            state.connected=false;state.phase='idle';state.accounts.push({id:'untrusted',name:'<img src=x onerror=alert(1)>',kind:'Offline'});renderAccounts(state);
            assert(!document.querySelector('#account-list img'),'account names cannot inject HTML');
            assert(closeAccounts()&&document.getElementById('account-panel').classList.contains('hidden'),'close dismisses panel');
            assert(document.getElementById('account-open').getAttribute('aria-expanded')==='false','closed accessible state');
            assert(document.getElementById('account-layer').classList.contains('hidden'),'closing removes backdrop');
            state.accounts.pop();state.message='Now playing as NightBat.';document.getElementById('account-open').click();
            return {status:'ACCOUNTS_UI_OK',checks};
        }.toString()})()`);
        console.log(JSON.stringify(result));
        await new Promise(resolve=>setTimeout(resolve,300));
        fs.mkdirSync(path.join(__dirname,'../build'),{recursive:true});
        fs.writeFileSync(path.join(__dirname,'../build/accounts-preview.png'),(await win.webContents.capturePage()).toPNG());
        app.exit(0);
    }catch(error){console.error(error.stack);app.exit(1);}
});
