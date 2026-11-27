/*
 * Decompiled with CFR 0.152.
 */
package com.batclient.cosmetics;

import com.batclient.cosmetics.CapeAnimator;
import com.batclient.cosmetics.CapeTransformer;
import com.batclient.cosmetics.Mappings;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.instrument.Instrumentation;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class AgentMain {
    private static PrintWriter log;

    private AgentMain() {
    }

    private static void openLog() {
        try {
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                File dir = new File(appData, "bat-client");
                dir.mkdirs();
                log = new PrintWriter(new FileWriter(new File(dir, "bat-cape-agent.log"), true));
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    static void log(String msg) {
        String ts = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        String line = "[" + ts + "] " + msg;
        System.out.println(line);
        if (log != null) {
            log.println(line);
            log.flush();
        }
    }

    static void logErr(String msg) {
        String ts = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        String line = "[" + ts + "] " + msg;
        System.err.println(line);
        if (log != null) {
            log.println(line);
            log.flush();
        }
    }

    public static void premain(String args, Instrumentation inst) {
        AgentMain.openLog();
        AgentMain.log("[BatCape] premain called args=" + args);
        String version = "";
        String mapping = "intermediary";
        String apiRoot = "";
        if (args != null) {
            String[] parts = args.split(",");
            if (parts.length > 0) {
                version = parts[0].trim();
            }
            if (parts.length > 1) {
                mapping = parts[1].trim();
            }
            if (parts.length > 2) {
                apiRoot = parts[2].trim();
            }
        }
        AgentMain.log("[BatCape] version=" + version + " mapping=" + mapping + " apiRoot=" + apiRoot);
        try {
            Mappings mappings = new Mappings(version, mapping);
            AgentMain.log("[BatCape] Mappings loaded, version=" + mappings.version());
            boolean hasCape = mappings.cls("CapeGetterOwner") != null && mappings.mtd("capeGetter") != null;
            boolean hasPlayer = !apiRoot.isEmpty() && mappings.cls("PlayerListEntry") != null;
            if (!hasCape && !hasPlayer) {
                AgentMain.logErr("[BatCape] unsupported version/mapping: " + version + "/" + mapping + " CapeGetterOwner=" + mappings.cls("CapeGetterOwner") + " capeGetter=" + mappings.mtd("capeGetter") + " PlayerListEntry=" + mappings.cls("PlayerListEntry") + " apiRoot=" + apiRoot);
                return;
            }
            if (hasCape) {
                AgentMain.log("[BatCape] CapeGetterOwner=" + mappings.cls("CapeGetterOwner") + " capeGetter=" + mappings.mtd("capeGetter") + " Minecraft=" + mappings.cls("Minecraft") + " tick=" + mappings.mtd("tick"));
                CapeAnimator.init(mappings);
                PerformanceTuner.init(mappings);
            } else {
                AgentMain.log("[BatCape] cape symbols missing (legacy version), capes disabled");
            }
            if (hasPlayer) {
                PlayerListHook.apiRoot = apiRoot;
                AgentMain.log("[BatCape] PlayerListHook enabled for PlayerListEntry=" + mappings.cls("PlayerListEntry"));
            } else {
                AgentMain.log("[BatCape] PlayerListHook disabled (apiRoot=" + apiRoot + " PlayerListEntry=" + mappings.cls("PlayerListEntry") + ")");
            }
            inst.addTransformer(new CapeTransformer(mappings), false);
            AgentMain.log("[BatCape] installed for MC " + mappings.version() + " (" + mapping + ")");
            AgentMain.log("[BatCape] Legacy performance tuner available; delegates when Bat mod claims performance");
        }
        catch (Throwable t) {
            AgentMain.logErr("[BatCape] agent init failed: " + t);
            t.printStackTrace();
        }
    }

    public static void agentmain(String args, Instrumentation inst) {
        AgentMain.premain(args, inst);
    }
}

