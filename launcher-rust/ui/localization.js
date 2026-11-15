"use strict";
(()=>{
 const languages=['en','es','fr','de','pt','ru','tr','ar'];let current='en',queued=false;
 const original=new WeakMap(),attributes=new WeakMap();
 const aliases={'Wardrobe':'Woredore','Your wardrobe.':'Woredore','Your loadouts.':'Loadouts','Your accounts.':'Accounts','Your settings.':'Settings'};
 function text(value){if(current==='en')return value;const trimmed=value.trim(),key=aliases[trimmed]||trimmed,dict=window.I18N?.[current]||{};return value.replace(trimmed,dict[key]||trimmed);}
 function apply(){queued=false;for(const element of document.querySelectorAll('[data-i18n]')){for(const node of element.childNodes){if(node.nodeType!==Node.TEXT_NODE)continue;let source=original.get(node);if(source===undefined){source=node.nodeValue;original.set(node,source);}const next=text(source);if(node.nodeValue!==next)node.nodeValue=next;}for(const key of ['aria-label','title','placeholder']){let saved=attributes.get(element);if(!saved){saved={};attributes.set(element,saved);}if(saved[key]===undefined&&element.hasAttribute(key))saved[key]=element.getAttribute(key);if(saved[key]){const next=text(saved[key]);if(element.getAttribute(key)!==next)element.setAttribute(key,next);}}}}
 function schedule(){if(!queued){queued=true;queueMicrotask(apply);}}
 function set(language){current=languages.includes(language)?language:'en';document.documentElement.lang=current;document.documentElement.dir=current==='ar'?'rtl':'ltr';schedule();window.dispatchEvent(new CustomEvent('bat-language-changed',{detail:current}));}
 // Translate owned interface labels only, never account names, loadout names, logs or remote mod content.
 document.querySelectorAll('.sidebar [data-page],#settings-save,#page-settings h1,#page-loadouts h1,#page-wardrobe h1,#page-accounts h1,#page-mods h1,#page-settings legend,#page-settings label>span,.settings-tabs button').forEach(el=>el.dataset.i18n='');
 document.querySelectorAll('#play-button,#microsoft-add,#offline-add,#create-loadout,#java-check,#mods-browse-tab,#mods-installed-tab,#modrinth-form button,#profile-editor label,#profile-editor option,.quick-card h3,.sidebar .community,.section-heading h3').forEach(el=>el.dataset.i18n='');
 new MutationObserver(schedule).observe(document.body,{childList:true,subtree:true});
 window.BatLanguage={set,text,refresh:schedule};
 if(window.__TAURI__?.core?.invoke)window.__TAURI__.core.invoke('launcher_settings').then(s=>set(s.language)).catch(()=>set('en'));else set('en');
})();
