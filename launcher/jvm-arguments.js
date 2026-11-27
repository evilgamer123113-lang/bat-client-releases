/** Tokenize JVM options without shell interpretation; keep quoted paths intact. */
function splitJvmArgs(value) {
    const tokens=[];let token='',quote=null,started=false;const text=String(value || '');
    for(let i=0;i<text.length;i++) {
        const c=text[i];
        if(c==='\\'&&quote&&text[i+1]===quote){token+=text[++i];started=true;continue;}
        if(c==='"'||c==="'") {if(quote===c)quote=null;else if(!quote)quote=c;else token+=c;started=true;continue;}
        if(/\s/.test(c)&&!quote){if(started){tokens.push(token);token='';started=false;}}else{token+=c;started=true;}
    }
    if(quote)throw new Error('Custom Java arguments contain an unclosed quote.');
    if(started)tokens.push(token);
    return tokens;
}
function validateJvmArgs(value, source='Custom Java arguments') {
    const tokens=splitJvmArgs(value);
    const paired=new Set(['--add-opens','--add-exports','--add-reads','--add-modules','--limit-modules','--patch-module','--module-path','-p','--upgrade-module-path','--enable-native-access']);
    const forbidden=new Set(['-cp','-classpath','--class-path','-jar','-m','--module','--source','-version','--version','-help','--help','-h','--dry-run','-X']);
    for(let i=0;i<tokens.length;i++) {
        const token=tokens[i],name=token.split('=')[0];
        if(forbidden.has(name))throw new Error(source+': classpath, main-class and exit options are managed by Bat Client.');
        if(paired.has(token)) {if(!tokens[i+1]||tokens[i+1].startsWith('-'))throw new Error(source+': an option is missing its value.');i++;continue;}
        if(!token.startsWith('-')||token==='-'||token.startsWith('@'))throw new Error(source+': remove plain text/main-class names; only JVM options belong here.');
        if(!/^(?:-D[^=\s]+(?:=[\s\S]*)?|-XX:.+|-X.+|-javaagent:.+|-agentlib:.+|-agentpath:.+|-(?:ea|da|esa|dsa)(?::.*)?|-verbose(?::.*)?|-server|-client|--enable-preview|--(?:add-opens|add-exports|add-reads|add-modules|limit-modules|patch-module|module-path|upgrade-module-path|enable-native-access)=.+)$/.test(token))throw new Error(source+': unsupported Java option. Check the Custom JVM Arguments field.');
    }
    return tokens;
}
const GC_SELECTOR=/^-XX:([+-])(Use(?:Serial|Parallel|ParallelOld|G1|Z|Shenandoah|Epsilon|ConcMarkSweep)GC)$/;
function composeJvmArgs(defaults, globalValue, profileValue) {
    const groups=[validateJvmArgs(globalValue,'Launcher Custom JVM Arguments'),validateJvmArgs(profileValue,'Loadout Custom JVM Arguments')];
    let selected=null,customPolicy=false;
    for(const group of groups){
        const policy=new Map();for(const arg of group){const match=arg.match(GC_SELECTOR);if(match)policy.set(match[2],match[1]);}
        const enabled=new Set([...policy].filter(([name,sign])=>sign==='+').map(([name])=>name));
        if(enabled.size>1)throw Error('Custom JVM Arguments select multiple garbage collectors. Choose only one collector in each settings field.');
        if(enabled.size){selected=[...enabled][0];customPolicy=true;}
        else if(policy.size){customPolicy=true;if(policy.get(selected)==='-')selected=null;}
    }
    // A loadout's collector overrides the global choice. Keep user tuning and
    // agents, but never combine a custom collector with our default G1 policy.
    const base=customPolicy?defaults.filter(a=>!GC_SELECTOR.test(a)&&(selected==='UseG1GC'||!/^-(?:XX:(?:G1|InitiatingHeapOccupancyPercent|UseStringDeduplication|\+UseStringDeduplication)|Xmn)/.test(a))):defaults;
    const extras=groups.flat().filter(a=>{const m=a.match(GC_SELECTOR);return !m||m[1]==='-'&&m[2]!==selected;});
    return [...base,...(selected?['-XX:+'+selected]:[]),...extras];
}
function compatibleJvmArgs(args, javaMajor) {
    // Removed from HotSpot in Java 11. Preserve saved settings and every other
    // custom option; report this known compatibility repair to the player.
    const removed = args.filter(arg => javaMajor >= 11 && /^-XX:[+-]AggressiveOpts$/.test(arg));
    return {args:args.filter(arg => !removed.includes(arg)), removed};
}
module.exports={splitJvmArgs,validateJvmArgs,composeJvmArgs,compatibleJvmArgs};
