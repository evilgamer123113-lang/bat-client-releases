"use strict";
(() => {
  const screen=$('#startup-screen'),status=$('#startup-status'),retry=$('#startup-retry'),track=screen.querySelector('.startup-track');
  const progress=document.createElement('progress');progress.id='startup-update-progress';progress.max=1;progress.hidden=true;progress.setAttribute('aria-label','Downloading launcher update');track.after(progress);
  let minimum=false,ready=!invoke||window.BatLibraryLoad==="ready",updatesDone=!invoke,finished=false;
  function finish(){if(finished||!minimum||!ready||!updatesDone)return;finished=true;screen.classList.add('finished');document.body.classList.remove('starting');setTimeout(()=>{screen.hidden=true;window.dispatchEvent(new Event('bat-startup-complete'));},220);}
  window.BatStartup={
    update(value){if(finished)return;status.textContent=value.message||'Checking for updates…';const determinate=Number.isFinite(value.progress);track.hidden=determinate;progress.hidden=!determinate;if(determinate){progress.value=Math.max(0,Math.min(1,value.progress));status.textContent+=' '+Math.round(progress.value*100)+'%';}},
    complete(delay=0){setTimeout(()=>{updatesDone=true;finish();},delay);}
  };
  window.addEventListener('bat-library-ready',()=>{ready=true;finish();});
  window.addEventListener('bat-library-error',()=>{status.textContent='Your library could not be opened. Its files have been left untouched.';retry.hidden=false;});
  if(window.BatLibraryLoad==='failed'){status.textContent='Your library could not be opened. Its files have been left untouched.';retry.hidden=false;}
  retry.addEventListener('click',()=>location.reload());
  Promise.allSettled([document.fonts.ready,new Promise(resolve=>{const logo=new Image();logo.onload=logo.onerror=resolve;logo.src='assets/bat-logo.png';})]).then(()=>setTimeout(()=>{minimum=true;finish();},450));
  setTimeout(()=>{if(!ready){status.textContent='Still opening your library…';retry.hidden=false;}},12000);
})();
