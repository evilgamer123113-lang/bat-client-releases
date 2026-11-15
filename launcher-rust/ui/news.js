"use strict";
(() => {
  const SOURCE="https://batclient.xyz/changelog.html",KEY="bat-next-changelog",MAX=1024*1024;
  const panel=document.getElementById("news-panel"),version=document.getElementById("news-version"),title=document.getElementById("news-title"),summary=document.getElementById("news-summary"),status=document.getElementById("news-source"),refreshButton=document.getElementById("news-refresh");
  const navigation=document.getElementById("news-navigation"),previous=document.getElementById("news-prev"),next=document.getElementById("news-next"),position=document.getElementById("news-position");
  const compact=(text,limit)=>String(text||"").replace(/\s+/g," ").trim().slice(0,limit);
  function parseReleases(html){
    if(typeof html!=="string"||html.length>MAX)throw Error("Invalid changelog response");
    // Template content stays inert: never attach downloaded nodes to the live page.
    const template=document.createElement("template");template.innerHTML=html;
    template.content.querySelectorAll("script,style,iframe,object,embed,link,meta,base").forEach(node=>node.remove());
    const entries=[...template.content.querySelectorAll(".changelog .cl-item")];
    const latest=entries.findIndex(item=>/^latest$/i.test(item.querySelector(".cl-tag")?.textContent.trim()||""));
    if(latest>0)entries.unshift(...entries.splice(latest,1));
    const releases=new Map();
    const fetchedAt=Date.now();
    for(const entry of entries){
    const versionNode=entry.querySelector(".cl-ver")?.cloneNode(true);versionNode?.querySelectorAll("*").forEach(node=>node.remove());
    const number=compact(versionNode?.textContent,64).match(/^\d+\.\d+\.\d+(?:[-+][\w.-]+)?$/)?.[0];
    const heading=compact(entry.querySelector(".cl-body h3")?.textContent,160);
    const description=compact(entry.querySelector(".cl-body li")?.textContent,220);
      if(!number||!heading||!description||releases.has(number))continue;
      releases.set(number,{version:number,title:heading,summary:description,date:compact(entry.querySelector(".cl-date")?.textContent,64),source:SOURCE,fetchedAt});
      if(releases.size===12)break;
    }
    if(!releases.size)throw Error("Release fields are missing");
    return [...releases.values()];
  }
  const parse=html=>parseReleases(html)[0];
  function valid(data){return data&&data.source===SOURCE&&typeof data.version==="string"&&/^\d+\.\d+\.\d+(?:[-+][\w.-]+)?$/.test(data.version)&&typeof data.title==="string"&&data.title.length>0&&data.title.length<=160&&typeof data.summary==="string"&&data.summary.length<=220&&typeof data.date==="string"&&data.date.length<=64&&Number.isFinite(data.fetchedAt);}
  function readCache(){
    try{
      const raw=localStorage.getItem(KEY);
      if(!raw||raw.length>24000)return [];
      const value=JSON.parse(raw);
      return (Array.isArray(value)?value:[value]).filter(valid).slice(0,12);
    }catch{return [];}
  }
  let releases=readCache(),index=0,pending=false;
  function render(data,mode="live"){
    version.textContent=`${data.version} UPDATE`;title.textContent=data.title;summary.textContent=data.summary;
    summary.title=data.summary;status.textContent=`${data.date}${data.date?" · ":""}${mode==="checking"?"Saved update · Checking…":mode==="cached"?"Saved update · Offline":"batclient.xyz"}`;
    panel.dataset.state=mode;
    navigation.hidden=releases.length<2;
    position.textContent=(index+1)+" / "+releases.length;
    previous.disabled=index===0;
    next.disabled=index===releases.length-1;
  }
  async function request(){
    if(window.__TAURI__?.core?.invoke)return window.__TAURI__.core.invoke("fetch_changelog");
    if(location.protocol!=="http:"||location.hostname!=="127.0.0.1")throw Error("Use the desktop app or local preview server");
    const response=await fetch("/api/changelog",{credentials:"omit",signal:AbortSignal.timeout(10000)});
    if(!response.ok)throw Error("Changelog request failed");
    const value=await response.json();if(typeof value.html!=="string")throw Error("Invalid changelog response");return value.html;
  }
  async function refresh(){
    if(pending)return;pending=true;panel.setAttribute("aria-busy","true");refreshButton.disabled=true;
    if(releases.length)render(releases[index],"checking");
    try{
      const current=releases[index]?.version;
      releases=parseReleases(await request());
      index=Math.max(0,releases.findIndex(data=>data.version===current));
      try{localStorage.setItem(KEY,JSON.stringify(releases));}catch{}
      render(releases[index]);
    }
    catch{if(releases.length)render(releases[index],"cached");else{version.textContent="UPDATES";title.textContent="Updates couldn't be loaded";summary.textContent="Open release notes to check the latest changes.";status.textContent="batclient.xyz · Unavailable";panel.dataset.state="error";}}
    finally{pending=false;panel.setAttribute("aria-busy","false");refreshButton.disabled=false;}
  }
  refreshButton.addEventListener("click",refresh);
  function move(step){
    const target=index+step;
    if(target<0||target>=releases.length)return;
    index=target;
    render(releases[index],panel.dataset.state);
  }
  previous.addEventListener("click",()=>move(-1));
  next.addEventListener("click",()=>move(1));
  window.BatNews={parse,refresh,get cached(){return releases[0]||null;},ready:null};
  window.BatNews.ready=refresh();
})();
