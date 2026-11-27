/*
 * Decompiled with CFR 0.152.
 */
package com.batclient.cosmetics;

import com.batclient.cosmetics.AgentMain;
import com.batclient.cosmetics.Mappings;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.imageio.ImageIO;

public final class CapeAnimator {
    private static Mappings m;
    private static File skinsDir;
    private static File launchCapeDir;
    private static String launchCapePlayer;
    private static final Map<String, AnimatedCape> CAPES;
    private static final Set<String> NO_CAPE;
    private static final Map<String, Object[]> SKIN_CACHE;
    private static final Map<Class<?>, Method> PROFILE_GETTERS;
    private static final Map<Class<?>, Method> PROFILE_NAME_GETTERS;
    private static final Method NO_PROFILE_MARKER = markerMethod();
    private static Class<?> mcClass;
    private static Class<?> tmClass;
    private static Class<?> rlocClass;
    private static Class<?> dynTexClass;
    private static Class<?> nativeImageClass;
    private static Method mcInstance;
    private static Method getTextureManager;
    private static Method read;
    private static Method copyFrom;
    private static Method upload;
    private static Method getPixels;
    private static Method intArrGetter;
    private static boolean nativeBand;
    private static Constructor<?> dynTexCtor;
    private static Method fromNamespaceAndPath;
    private static Throwable resolveError;
    private static boolean resolved;
    private static Field tmMapField;
    private static Object tmRef;
    private static boolean tmResolved;
    private static boolean tickHookLogged;
    private static final CapeFrameLoader FRAME_LOADER = new CapeFrameLoader();

    private CapeAnimator() {
    }

    public static void init(Mappings mappings) {
        m = mappings;
        String launchDirectory = System.getProperty("batclient.cape.directory");
        launchCapeDir = launchDirectory == null ? null : new File(launchDirectory);
        launchCapePlayer = System.getProperty("batclient.cape.player");
        String appData = System.getenv("APPDATA");
        if (appData != null) {
            skinsDir = new File(appData, "bat-client" + File.separator + "skins");
        }
    }

    public static Object getCape(Object player, Object original) {
        try {
            String name = CapeAnimator.playerName(player);
            if (name == null) {
                AgentMain.log("[getCape] playerName=null");
                return original;
            }
            AnimatedCape cape = CapeAnimator.capeFor(name, player.getClass().getClassLoader());
            if (cape == null) {
                AgentMain.log("[getCape] " + name + " no cape");
                return original;
            }
            AgentMain.log("[getCape] " + name + " -> " + cape.location);
            return cape.location;
        }
        catch (Throwable t) {
            AgentMain.log("[getCape] EXCEPTION: " + t);
            return original;
        }
    }

    public static Object getSkin(Object player, Object originalSkin) {
        AnimatedCape cape;
        String name;
        if (originalSkin == null) {
            return null;
        }
        try {
            name = CapeAnimator.playerName(player);
        }
        catch (Throwable t) {
            return originalSkin;
        }
        if (name == null) {
            return originalSkin;
        }
        String key = name.toLowerCase();
        try {
            cape = CAPES.get(key);
            if (cape == null && !NO_CAPE.contains(key)) {
                cape = CapeAnimator.capeFor(name, player.getClass().getClassLoader());
            }
        }
        catch (Throwable t) {
            return originalSkin;
        }
        if (cape == null) {
            return originalSkin;
        }
        try {
            Class<?> rt;
            Object[] cached = SKIN_CACHE.get(key);
            /* The client rebuilds/allocates a fresh originalSkin instance per
               frame, so identity checks miss every frame and we'd re-run the
               expensive record-component rebuild below each frame. Cache the
               final skin once per player name; reusing it across frames is
               safe because the underlying pixels are animated in-place on the
               same DynamicTexture. The per-name result is stable for the play
               session, so only the first call pays the rebuild cost. */
            if (cached != null) {
                return cached[0];
            }
            Object animatedCapeValue = cape.location;
            if (m.hasClass("ResourceTexture") && (rt = CapeAnimator.tryFindClass(m.cls("ResourceTexture"), player.getClass().getClassLoader(), Thread.currentThread().getContextClassLoader(), ClassLoader.getSystemClassLoader())) != null) {
                animatedCapeValue = rt.getConstructor(rlocClass).newInstance(cape.location);
            }
            Object result = CapeAnimator.rebuildPlayerSkin(originalSkin, animatedCapeValue, player.getClass().getClassLoader());
            SKIN_CACHE.put(key, new Object[]{result});
            return result;
        }
        catch (Throwable t) {
            return originalSkin;
        }
    }

    public static void tick() {
        try {
            if (!tickHookLogged) {
                tickHookLogged = true;
                AgentMain.log("[tick] Minecraft.tick hook FIRING (capes=" + CAPES.size() + ")");
            }
            long now = System.currentTimeMillis();
            FRAME_LOADER.discardUnused(now, CapeAnimator::closeImages);
            for (AnimatedCape cape : CAPES.values()) {
                CapeAnimator.ensureRegistered(cape);
                cape.tick(now);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    private static void ensureRegistered(AnimatedCape cape) {
        try {
            Map map;
            if (!tmResolved) {
                Object mc = mcInstance.invoke(null, new Object[0]);
                tmRef = getTextureManager.invoke(mc, new Object[0]);
                tmMapField = CapeAnimator.findMapField(tmRef.getClass());
                tmResolved = true;
            }
            if ((map = (Map)tmMapField.get(tmRef)).get(cape.location) == cape.texture) {
                return;
            }
            map.put(cape.location, cape.texture);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    private static String playerName(Object player) throws Exception {
        /* Cache the GameProfile getter and its name getter per concrete class
           — player.getClass() is stable, so doing a full getMethods() scan on
           every getSkin call (every frame, per rendered cape player) is pure
           waste. First call scans; later calls are two cached invokes. */
        Method gm = PROFILE_GETTERS.get(player.getClass());
        if (gm == null) {
            for (Method md : player.getClass().getMethods()) {
                if (md.getParameterCount() != 0 || !md.getReturnType().getName().equals("com.mojang.authlib.GameProfile")) continue;
                gm = md;
                break;
            }
            if (gm == null) {
                PROFILE_GETTERS.putIfAbsent(player.getClass(), NO_PROFILE_MARKER);
                return null;
            }
            try { gm.setAccessible(true); } catch (Throwable ignored) {}
            PROFILE_GETTERS.putIfAbsent(player.getClass(), gm);
        }
        if (gm == NO_PROFILE_MARKER) return null;
        Object gp = gm.invoke(player);
        if (gp == null) return null;
        Method nm = PROFILE_NAME_GETTERS.get(gp.getClass());
        if (nm == null) {
            try { nm = gp.getClass().getMethod("getName"); }
            catch (NoSuchMethodException e) { nm = gp.getClass().getMethod("name"); }
            if (nm == null) return null;
            try { nm.setAccessible(true); } catch (Throwable ignored) {}
            PROFILE_NAME_GETTERS.putIfAbsent(gp.getClass(), nm);
        }
        return (String) nm.invoke(gp);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static AnimatedCape capeFor(String name, ClassLoader loader) throws Exception {
        String key = name.toLowerCase();
        AnimatedCape cape = CAPES.get(key);
        if (cape != null) {
            return cape;
        }
        if (NO_CAPE.contains(key)) {
            AgentMain.log("[capeFor] " + name + " in NO_CAPE");
            return null;
        }
        CapeAnimator.resolveHandles(loader);
        if (resolveError != null) {
            AgentMain.log("[capeFor] resolve FAILED: " + resolveError);
            throw new Exception("reflection resolution failed", resolveError);
        }
        List<Object> imgs;
        try {
            imgs = FRAME_LOADER.poll(key, () -> {
                File[] files = CapeAnimator.findFrames(name);
                ArrayList<Object> decoded = new ArrayList<Object>();
                if (files == null || files.length < 2) return decoded;
                long started = System.nanoTime();
                try {
                    for (File file : files) {
                        try (FileInputStream in = new FileInputStream(file)) { decoded.add(read.invoke(null, in)); }
                    }
                    AgentMain.log("[capeFor] decoded " + decoded.size() + " frames off render thread in " + ((System.nanoTime()-started)/1_000_000) + "ms");
                    return decoded;
                } catch (Throwable failure) {
                    CapeAnimator.closeImages(decoded);
                    throw new Exception("Cape frame decoding failed", failure);
                }
            }, System.currentTimeMillis());
        } catch (Exception failure) {
            NO_CAPE.add(key); // One failure per session, never retry file decoding every frame.
            AgentMain.log("[capeFor] decoding failed: " + failure);
            return null;
        }
        if (imgs == null) return null; // Keep the original skin/cape while decoding.
        try {
            Object texture;
            if (imgs.size() < 2) {
                CapeAnimator.closeImages(imgs);
                NO_CAPE.add(key);
                AgentMain.log("[capeFor] " + name + " only " + imgs.size() + " images loaded");
                return null;
            }
            AgentMain.log("[capeFor] loaded " + imgs.size() + " images, creating DynamicTexture...");
            Object first = imgs.get(0);
            if (dynTexCtor.getParameterTypes().length == 2) {
                Supplier<String> nameSup = new Supplier<String>(){

                    @Override
                    public String get() {
                        return "animated_cape";
                    }
                };
                texture = dynTexCtor.newInstance(nameSup, first);
            } else {
                texture = dynTexCtor.newInstance(first);
            }
            upload.invoke(texture, new Object[0]);
            Object location = CapeAnimator.buildRloc(name);
            AgentMain.log("[capeFor] registering " + location + " ...");
            CapeAnimator.register(location, texture, loader);
            long[] delays = CapeAnimator.loadDelays(name, imgs.size());
            AnimatedCape built = new AnimatedCape(location, texture, imgs, delays);
            CAPES.put(key, built);
            try {
                int testFrame = imgs.size() > 1 ? 1 : 0;
                built.copyFrame(testFrame);
                AgentMain.log("[capeFor] animation smoke-test OK (frame " + testFrame + " -> " + (testFrame + 1) % imgs.size() + ")");
            }
            catch (Throwable st) {
                AgentMain.log("[capeFor] animation smoke-test FAILED: " + st);
            }
            AgentMain.log("[capeFor] " + name + " cape CREATED OK");
            return built;
        }
        catch (Throwable t) {
            AgentMain.log("[capeFor] EXCEPTION: " + t);
            CapeAnimator.closeImages(imgs);
            throw t;
        }
    }

    private static void closeImages(List<Object> imgs) {
        if (nativeImageClass == null) {
            return;
        }
        for (Object img : imgs) {
            try {
                nativeImageClass.getMethod("close", new Class[0]).invoke(img, new Object[0]);
            }
            catch (Throwable throwable) {}
        }
    }

    private static void register(Object location, Object texture, ClassLoader loader) throws Exception {
        Object mc = mcInstance.invoke(null, new Object[0]);
        Object tm = getTextureManager.invoke(mc, new Object[0]);
        Map map = (Map)CapeAnimator.findMapField(tm.getClass()).get(tm);
        map.put(location, texture);
        String regMsg = "registered " + location + " mapSize=" + map.size() + " verified=" + (map.get(location) == texture);
        System.out.println("[BatCape] " + regMsg);
        AgentMain.log("[register] " + regMsg);
    }

    private static Field findMapField(Class<?> tmImplClass) throws Exception {
        Field fallback = null;
        for (Field f : tmImplClass.getDeclaredFields()) {
            Type kt;
            Type g;
            if (Modifier.isStatic(f.getModifiers()) || !Map.class.isAssignableFrom(f.getType())) continue;
            if (fallback == null) {
                fallback = f;
            }
            if (!((g = f.getGenericType()) instanceof ParameterizedType) || (kt = ((ParameterizedType)g).getActualTypeArguments()[0]) != rlocClass) continue;
            f.setAccessible(true);
            return f;
        }
        if (fallback == null) {
            throw new IllegalStateException("no Map field in " + tmImplClass.getName());
        }
        fallback.setAccessible(true);
        return fallback;
    }

    private static Object buildRloc(String name) throws Exception {
        String ns = "batclient";
        String path = "animated_cape/" + CapeAnimator.safeName(name);
        if (fromNamespaceAndPath != null) {
            return fromNamespaceAndPath.invoke(null, ns, path);
        }
        return rlocClass.getConstructor(String.class, String.class).newInstance(ns, path);
    }

    private static Object rebuildPlayerSkin(Object skin, Object animatedCapeValue, ClassLoader loader) throws Exception {
        Class<?> skinClass = skin.getClass();
        Method getRC = Class.class.getMethod("getRecordComponents", new Class[0]);
        Object[] comps = (Object[])getRC.invoke(skinClass, new Object[0]);
        Class<?> rcCls = Class.forName("java.lang.reflect.RecordComponent", false, loader);
        Method getAcc = rcCls.getMethod("getAccessor", new Class[0]);
        Method getName = rcCls.getMethod("getName", new Class[0]);
        String capeAccessor = m.mtd("capeAccessor");
        int n = comps.length;
        Object[] values = new Object[n];
        Class[] types = new Class[n];
        int capeIdx = -1;
        for (int i = 0; i < n; ++i) {
            Method acc = (Method)getAcc.invoke(comps[i], new Object[0]);
            String cname = (String)getName.invoke(comps[i], new Object[0]);
            if (capeAccessor.equals(cname)) {
                capeIdx = i;
            }
            types[i] = acc.getReturnType();
            values[i] = acc.invoke(skin, new Object[0]);
        }
        if (capeIdx < 0) {
            throw new IllegalStateException("cape component not found");
        }
        values[capeIdx] = animatedCapeValue;
        Constructor<?> ctor = skinClass.getDeclaredConstructor(types);
        return ctor.newInstance(values);
    }

    private static String toDotName(String slashName) {
        if (slashName == null) {
            return null;
        }
        return slashName.replace('/', '.');
    }

    private static Class<?> tryFindClass(String name, ClassLoader ... loaders) {
        String dotName = CapeAnimator.toDotName(name);
        if (dotName == null) {
            return null;
        }
        for (ClassLoader cl : loaders) {
            if (cl == null) continue;
            try {
                Class<?> c = Class.forName(dotName, false, cl);
                if (c == null) continue;
                return c;
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        return null;
    }

    private static synchronized void resolveHandles(ClassLoader loader) throws Exception {
        if (resolved) {
            return;
        }
        try {
            ClassLoader sysCl = ClassLoader.getSystemClassLoader();
            ClassLoader ctxCl = Thread.currentThread().getContextClassLoader();
            ClassLoader agentCl = CapeAnimator.class.getClassLoader();
            ClassLoader[] candidates = new ClassLoader[]{loader, ctxCl, sysCl, agentCl};
            AgentMain.log("[resolve] trying classloaders: loader=" + loader + " ctx=" + ctxCl + " sys=" + sysCl + " agent=" + agentCl);
            AgentMain.log("[resolve] mapping names: MC=" + m.cls("Minecraft") + " TM=" + m.cls("TextureManager") + " RL=" + m.cls("ResourceLocation") + " DT=" + m.cls("DynamicTexture"));
            mcClass = CapeAnimator.tryFindClass(m.cls("Minecraft"), candidates);
            if (mcClass == null) {
                throw new ClassNotFoundException("Minecraft class not found in any classloader: " + m.cls("Minecraft"));
            }
            tmClass = CapeAnimator.tryFindClass(m.cls("TextureManager"), candidates);
            if (tmClass == null) {
                throw new ClassNotFoundException("TextureManager not found: " + m.cls("TextureManager"));
            }
            rlocClass = CapeAnimator.tryFindClass(m.cls("ResourceLocation"), candidates);
            if (rlocClass == null) {
                throw new ClassNotFoundException("ResourceLocation not found: " + m.cls("ResourceLocation"));
            }
            dynTexClass = CapeAnimator.tryFindClass(m.cls("DynamicTexture"), candidates);
            if (dynTexClass == null) {
                throw new ClassNotFoundException("DynamicTexture not found: " + m.cls("DynamicTexture"));
            }
            AgentMain.log("[resolve] classes OK: MC=" + mcClass.getName() + " TM=" + tmClass.getName() + " RL=" + rlocClass.getName() + " DT=" + dynTexClass.getName());
            nativeBand = m.hasClass("NativeImage");
            AgentMain.log("[resolve] nativeBand=" + nativeBand);
            if (nativeBand) {
                nativeImageClass = CapeAnimator.tryFindClass(m.cls("NativeImage"), candidates);
                if (nativeImageClass == null) {
                    throw new ClassNotFoundException("NativeImage not found: " + m.cls("NativeImage"));
                }
                read = CapeAnimator.findStatic(nativeImageClass, 1, nativeImageClass, new Class[]{InputStream.class});
                copyFrom = CapeAnimator.findInstance(nativeImageClass, 1, Void.TYPE, new Class[]{nativeImageClass});
                getPixels = CapeAnimator.findNamed(dynTexClass, m.mtd("getPixels"), 0, nativeImageClass, null);
            } else {
                read = ImageIO.class.getMethod("read", InputStream.class);
                intArrGetter = CapeAnimator.findInstance(dynTexClass, 0, int[].class, null);
                AgentMain.log("[resolve] int[] band: read=" + read + " intArrGetter=" + intArrGetter);
            }
            mcInstance = CapeAnimator.findStatic(mcClass, 0, mcClass, null);
            getTextureManager = CapeAnimator.findInstance(mcClass, 0, tmClass, null);
            upload = CapeAnimator.findNamed(dynTexClass, m.mtd("upload"), 0, Void.TYPE, null);
            AgentMain.log("[resolve] getPixels=" + getPixels + " copyFrom=" + copyFrom + " upload=" + upload + " intArrGetter=" + intArrGetter);
            String dynTexCtorMeta = m.meta("dynTexCtor");
            dynTexCtor = dynTexCtorMeta != null && dynTexCtorMeta.indexOf("Supplier") >= 0 ? dynTexClass.getConstructor(Supplier.class, nativeImageClass) : ("BufferedImage".equals(dynTexCtorMeta) ? dynTexClass.getConstructor(BufferedImage.class) : dynTexClass.getConstructor(nativeImageClass));
            AgentMain.log("[resolve] dynTexCtor=" + dynTexCtor + " meta=" + dynTexCtorMeta);
            if ("fromNamespaceAndPath".equals(m.meta("rlocCtor")) && (fromNamespaceAndPath = CapeAnimator.findStatic(rlocClass, 2, rlocClass, new Class[]{String.class, String.class})) != null) {
                fromNamespaceAndPath.setAccessible(true);
            }
            resolved = true;
            resolveError = null;
        }
        catch (Throwable t) {
            AgentMain.log("[resolve] FAILED: " + t);
            resolveError = t;
        }
    }

    private static Method findStatic(Class<?> clazz, int paramCount, Class<?> returnType, Class<?>[] params) {
        for (Method md : clazz.getDeclaredMethods()) {
            if (!Modifier.isStatic(md.getModifiers()) || !CapeAnimator.matches(md, paramCount, returnType, params)) continue;
            try {
                md.setAccessible(true);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            return md;
        }
        return null;
    }

    private static Method findInstance(Class<?> clazz, int paramCount, Class<?> returnType, Class<?>[] params) {
        for (Method md : clazz.getDeclaredMethods()) {
            if (Modifier.isStatic(md.getModifiers()) || !CapeAnimator.matches(md, paramCount, returnType, params)) continue;
            try {
                md.setAccessible(true);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            return md;
        }
        return null;
    }

    private static Method findNamed(Class<?> clazz, String name, int paramCount, Class<?> returnType, Class<?>[] params) {
        if (name == null) {
            return null;
        }
        for (Method md : clazz.getDeclaredMethods()) {
            if (!md.getName().equals(name) || !CapeAnimator.matches(md, paramCount, returnType, params)) continue;
            try {
                md.setAccessible(true);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            return md;
        }
        return null;
    }

    private static boolean matches(Method md, int paramCount, Class<?> returnType, Class<?>[] params) {
        if (md.getParameterCount() != paramCount) {
            return false;
        }
        if (returnType != null && md.getReturnType() != returnType) {
            return false;
        }
        if (params != null) {
            Class<?>[] got = md.getParameterTypes();
            for (int i = 0; i < params.length; ++i) {
                if (got[i] == params[i]) continue;
                return false;
            }
        }
        return true;
    }

    private static File capeDirectory(String playerName) {
        return launchCapeDir != null && playerName.equalsIgnoreCase(launchCapePlayer)
            ? launchCapeDir : skinsDir;
    }

    private static File[] findFrames(String playerName) {
        File directory = capeDirectory(playerName);
        if (directory == null || !directory.isDirectory()) {
            return null;
        }
        final String prefix = "cape-" + playerName.toLowerCase(Locale.ROOT) + "-frame-";
        File[] files = directory.listFiles(new FileFilter(){

            @Override
            public boolean accept(File f) {
                String lower = f.getName().toLowerCase(Locale.ROOT);
                return lower.startsWith(prefix) && lower.endsWith(".png");
            }
        });
        if (files == null || files.length < 2) {
            return null;
        }
        Arrays.sort(files, new Comparator<File>(){

            @Override
            public int compare(File a, File b) {
                return CapeAnimator.extractIndex(a.getName(), prefix) - CapeAnimator.extractIndex(b.getName(), prefix);
            }
        });
        return files;
    }

    private static int extractIndex(String name, String prefix) {
        String num = name.substring(prefix.length(), name.length() - 4);
        try {
            return Integer.parseInt(num);
        }
        catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    private static long[] loadDelays(String playerName, int count) {
        long[] delays = new long[count];
        for (int i = 0; i < count; ++i) {
            delays[i] = 120L;
        }
        File directory = capeDirectory(playerName);
        if (directory == null) {
            return delays;
        }
        String name = directory == launchCapeDir ? playerName.toLowerCase(Locale.ROOT) : playerName;
        File meta = new File(directory, "cape-" + name + ".json");
        if (meta.isFile()) {
            try {
                byte[] raw = Files.readAllBytes(meta.toPath());
                String s = new String(raw, StandardCharsets.UTF_8);
                int a = s.indexOf(91);
                int b = s.lastIndexOf(93);
                if (a >= 0 && b > a) {
                    String[] parts = s.substring(a + 1, b).split(",");
                    for (int i = 0; i < Math.min(parts.length, count); ++i) {
                        try {
                            long v = Math.round(Double.parseDouble(parts[i].trim()));
                            if (v <= 0L) continue;
                            delays[i] = v;
                            continue;
                        }
                        catch (Exception exception) {
                            // empty catch block
                        }
                    }
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        return delays;
    }

    private static String safeName(String playerName) {
        StringBuilder sb = new StringBuilder();
        for (char c : playerName.toLowerCase().toCharArray()) {
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '.' || c == '-') {
                sb.append(c);
                continue;
            }
            sb.append('_');
        }
        return sb.toString();
    }

    static {
        CAPES = new ConcurrentHashMap<String, AnimatedCape>();
        NO_CAPE = ConcurrentHashMap.newKeySet();
        SKIN_CACHE = new ConcurrentHashMap<String, Object[]>();
        PROFILE_GETTERS = new ConcurrentHashMap<Class<?>, Method>();
        PROFILE_NAME_GETTERS = new ConcurrentHashMap<Class<?>, Method>();
        resolved = false;
        tmResolved = false;
    }

    /** Sentinel Method standing in for "no GameProfile getter found on this class". */
    private static Method markerMethod() {
        try {
            /* A private no-op we own; never invoked. */
            return CapeAnimator.class.getDeclaredMethod("playerName", Object.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static final class AnimatedCape {
        final Object location;
        final Object texture;
        final List<Object> frames;
        final long[] delays;
        int frame;
        long next;
        private int tickFailureLogged;

        AnimatedCape(Object location, Object texture, List<Object> frames, long[] delays) {
            this.location = location;
            this.texture = texture;
            this.frames = frames;
            this.delays = delays;
            this.frame = 0;
            this.next = System.currentTimeMillis() + delays[0];
        }

        void tick(long now) {
            if (now < this.next || this.frames.size() < 2) {
                return;
            }
            this.frame = (this.frame + 1) % this.frames.size();
            this.next = now + this.delays[this.frame];
            try {
                this.copyFrame(this.frame);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }

        void copyFrame(int toFrame) throws Throwable {
            try {
                if (nativeBand) {
                    Object pixels = getPixels.invoke(this.texture, new Object[0]);
                    if (pixels == null) {
                        throw new IllegalStateException("getPixels returned null");
                    }
                    copyFrom.invoke(pixels, this.frames.get(toFrame));
                } else {
                    int[] px = (int[])intArrGetter.invoke(this.texture, new Object[0]);
                    BufferedImage img = (BufferedImage)this.frames.get(toFrame);
                    img.getRGB(0, 0, img.getWidth(), img.getHeight(), px, 0, img.getWidth());
                }
                upload.invoke(this.texture, new Object[0]);
            }
            catch (Throwable t) {
                try {
                    if (this.tickFailureLogged++ < 3) {
                        AgentMain.log("[tick] animation update FAILED frame=" + toFrame + " getPixels=" + getPixels + " copyFrom=" + copyFrom + " upload=" + upload + " err=" + t);
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                throw t;
            }
        }
    }
}

