package com.batclient.cosmetics;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Performance tweaks injected into Minecraft's tick loop via the same ASM
 * hook that drives cape animation:
 *
 *  - boosts the main game thread priority once at startup,
 *  - estimates frame time every 20 ticks (~1 s) and dynamically adjusts the
 *    game's "chunk updates" option.
 *
 * Chunk-update changes use hysteresis: dropping chunk threads under stress is
 * immediate (relief), raising them back requires the higher target to persist
 * for a couple of evaluation windows. This prevents the 1 stimulus -> 2 <->
 * 1 type churn that made chunk loading feel janky while flying.
 *
 * NOTE: forced GC is intentionally NOT done here. Calling System.gc() on the
 * render thread stops the world for hundreds of ms at the worst moments, which
 * produced 1-FPS hitches under load. The JVM's own garbage collector manages
 * the heap on its own; the tuner stays out of that entirely.
 *
 * Resolution is version-aware (MC 1.8 - 1.20.x): the mapped Minecraft and
 * GameOptions names are used when available, with 1.8-era fallbacks. The chunk
 * knob is tuned only when it is int-based (1.8 - 1.19.x); the enum-based
 * chunkBuilderMode on 1.19.4+ is detected and left untouched. Failures silently
 * disable the affected feature.
 */
public class PerformanceTuner {

    /* ── timing state ─────────────────────────────────────────────── */
    private static long   lastTickNanos;
    private static long   frameTimeSum;
    private static int    frameCount;
    private static long   tickCount;
    private static boolean initialised;
    private static boolean delegated;

    /* ── thresholds ───────────────────────────────────────────────── */
    private static final long STRESS_NS     = 25_000_000L;   // < ~40 FPS
    private static final long OK_NS         = 14_000_000L;   // > ~70 FPS

    /* ── chunk-updates reflection (lazy, one-time) ─────────────────── */
    private static Mappings m;
    private static Object optionsInstance;
    private static Field  chunkUpdatesField;
    private static boolean chunkUpdatesIsOptionInstance;
    private static boolean reflectionDone;

    /** Pass the per-version mappings table (called once at premain). */
    public static void init(Mappings mappings) {
        m = mappings;
    }

    /* ── chunk-updates hysteresis state ───────────────────────────── */
    private static int  appliedChunkUpdates  = -1;   // value actually in the option
    private static int  pendingChunkUpdates  = -1;   // candidate being evaluated
    private static long pendingSinceTick;           // first tick the candidate held
    private static final long HOLD_WINDOWS = 2;     // sustain N windows before raising

    /* ── shared FPS health (read by CapeAnimator to throttle uploads) ── */
    private static volatile int estFps = 0;

    /** Estimated frames-per-second over the last ~1 s window. */
    public static int fps() { return estFps; }

    /* =================================================================
     * onTick  –  called every game tick from the ASM-injected hook.
     * ================================================================= */
    public static void onTick() {
        try {
            if (delegated || "mod".equals(System.getProperty("batclient.performance.owner"))) {
                if (!delegated) {
                    delegated = true;
                    estFps = 0; // Tick cadence is not display FPS; do not throttle cape uploads from it.
                    // The mod owns chunk work, not OS thread scheduling. Removing the
                    // existing one-time boost here regresses render responsiveness
                    // under contention. Do not reapply it after an external change.
                    if (!initialised) {
                        initialised = true;
                        boostThread();
                    }
                    AgentMain.log("[tuner] Bat mod owns chunk tuning; render priority retained, cosmetics remain active");
                }
                return;
            }
            long now = System.nanoTime();
            tickCount++;

            /* ── first tick: boost thread, start timing ──────────── */
            if (!initialised) {
                initialised = true;
                lastTickNanos = now;
                boostThread();
                AgentMain.log("[tuner] tick hook alive, thread priority -> " + Thread.currentThread().getPriority());
                return;
            }

            /* ── frame-time accounting ───────────────────────────── */
            if (lastTickNanos > 0) {
                long delta = now - lastTickNanos;
                frameTimeSum += delta;
                frameCount++;

                /* every 20 ticks (~1 s) re-evaluate */
                if (frameCount >= 20) {
                    long avg = frameTimeSum / frameCount;
                    estFps = (avg > 0) ? (int) Math.round(1_000_000_000.0 / avg) : 0;
                    adjustChunkUpdates(avg);
                    frameTimeSum = 0;
                    frameCount   = 0;
                }
            }
            lastTickNanos = now;
        } catch (Throwable ignored) {
            // Never crash the game.
        }
    }

    /* =================================================================
     * Thread priority
     * ================================================================= */
    private static void boostThread() {
        try {
            Thread t = Thread.currentThread();
            if (t.getPriority() < 7) {
                t.setPriority(7);
            }
        } catch (Throwable ignored) {}
    }

    /* =================================================================
     * Chunk-updates adjustment with hysteresis.
     *
     * Pre-1.13:  Options.chunkUpdates is a plain int field.
     * Post-1.13: Options.chunkUpdates is OptionInstance<Integer> whose
     *            effective value is read/written via .get() / .set().
     * ================================================================= */
    private static void adjustChunkUpdates(long avgNanos) {
        try {
            if (!reflectionDone) {
                reflectionDone = true;
                resolveReflection();
            }
            if (chunkUpdatesField == null || optionsInstance == null) return;

            if (appliedChunkUpdates < 0) {
                appliedChunkUpdates = readChunkUpdates();
                if (appliedChunkUpdates < 0) return;
            }

            int target;
            if (avgNanos > STRESS_NS)      target = 1;
            else if (avgNanos < OK_NS)     target = 4;
            else                           target = 2;

            int current = readChunkUpdates();
            if (current < 0) return;
            if (appliedChunkUpdates != current) appliedChunkUpdates = current;

            if (target == current) {
                pendingChunkUpdates = target;
                return;
            }

            /* relief: drop chunk threads immediately under pressure */
            if (target < current) {
                applyChunkUpdates(target, current, avgNanos);
                return;
            }

            /* recovery: hold the higher target for a few windows */
            if (pendingChunkUpdates != target) {
                pendingChunkUpdates = target;
                pendingSinceTick = tickCount;
                return;
            }
            if (tickCount - pendingSinceTick >= HOLD_WINDOWS * 20) {
                applyChunkUpdates(target, current, avgNanos);
                pendingChunkUpdates = target;
            }
        } catch (Throwable ignored) {}
    }

    private static void applyChunkUpdates(int target, int current, long avgNanos) {
        try {
            writeChunkUpdates(target);
            appliedChunkUpdates = target;
            pendingChunkUpdates = target;
            AgentMain.log("[tuner] chunkUpdates " + current + " -> " + target
                    + " avg=" + (avgNanos / 1_000_000L) + "ms fps=" + estFps);
        } catch (Throwable ignored) {}
    }

    private static int readChunkUpdates() {
        try {
            if (chunkUpdatesField == null) return -1;
            if (chunkUpdatesIsOptionInstance) {
                Object opt = chunkUpdatesField.get(optionsInstance);
                if (opt == null) return -1;
                Object cur = opt.getClass().getMethod("get").invoke(opt);
                return (cur instanceof Number) ? ((Number) cur).intValue() : -1;
            }
            return chunkUpdatesField.getInt(optionsInstance);
        } catch (Throwable ignored) { return -1; }
    }

    private static void writeChunkUpdates(int target) {
        try {
            if (chunkUpdatesIsOptionInstance) {
                Object opt = chunkUpdatesField.get(optionsInstance);
                if (opt == null) return;
                Method setValue = findSetMethod(opt.getClass());
                if (setValue != null) setValue.invoke(opt, target);
            } else {
                chunkUpdatesField.setInt(optionsInstance, target);
            }
        } catch (Throwable ignored) {}
    }

    /* =================================================================
     * Reflection resolution  –  runs once, results cached.
     * ================================================================= */
    private static void resolveReflection() {
        try {
            Class<?> mcClass = null;
            /* Prefer the per-version mapped name (intermediary/official/named),
               then fall back to the classic 1.8-era literal names. */
            if (m != null && m.cls("Minecraft") != null) {
                mcClass = tryLoad(m.cls("Minecraft"));
            }
            if (mcClass == null) {
                for (String name : new String[]{
                        "net.minecraft.client.Minecraft",
                        "net.minecraft.client.main.Minecraft"}) {
                    mcClass = tryLoad(name);
                    if (mcClass != null) break;
                }
            }
            if (mcClass == null) {
                AgentMain.log("[tuner] Minecraft class not found; tuner disabled");
                return;
            }

            Object mc = findMinecraftInstance(mcClass);
            if (mc == null) {
                AgentMain.log("[tuner] Minecraft instance not found in " + mcClass.getName());
                return;
            }

            Object opts = findOptionsObject(mc);
            if (opts == null) {
                AgentMain.log("[tuner] GameOptions object not found on " + mcClass.getName());
                return;
            }
            optionsInstance = opts;
            AgentMain.log("[tuner] GameOptions resolved: " + opts.getClass().getName());

            resolveChunkUpdatesField(opts.getClass());
        } catch (Throwable e) {
            AgentMain.log("[tuner] resolveReflection failed: " + e);
        }
    }

    private static Class<?> tryLoad(String name) {
        try {
            String dot = name.replace('/', '.');
            return Class.forName(dot);
        } catch (Throwable ignored) {}
        return null;
    }

    /** Find a single-arg "set" method on an OptionInstance-compatible object. */
    private static Method findSetMethod(Class<?> cls) {
        for (Method md : cls.getDeclaredMethods()) {
            if (md.getName().equals("set") && md.getParameterCount() == 1) {
                try { md.setAccessible(true); } catch (Throwable ignored) {}
                return md;
            }
        }
        return null;
    }

    /** Find the running Minecraft instance via static method or field. */
    private static Object findMinecraftInstance(Class<?> mcClass) {
        try {
            /* getInstance across eras: named "getInstance", 1.8 srg
               "func_71410_x", modern intermediary "method_2357",
               1.21.4+ "method_26283" */
            for (String m : new String[]{"getInstance", "func_71410_x", "method_2357", "method_26283"}) {
                try {
                    Method gm = mcClass.getMethod(m);
                    if (gm.getReturnType() == mcClass) return gm.invoke(null);
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Throwable ignored) {}
        try {
            for (Field f : mcClass.getDeclaredFields()) {
                if (f.getType() == mcClass && Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    return f.get(null);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Walk up from the Minecraft instance looking for an Options-like object. */
    private static Object findOptionsObject(Object mc) {
        try {
            Class<?> c = mc.getClass();
            while (c != null && c != Object.class) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    String n = f.getName();
                    if (n.equals("options") || n.equals("field_71474_y")
                            || n.equals("gameSettings") || n.equals("field_74320_I")
                            || n.equals("field_3244")          // MC 1.17-1.21 options
                            || n.equals("f_91065_")) {         // MC 1.21.4+ options
                        f.setAccessible(true);
                        return f.get(mc);
                    }
                }
                c = c.getSuperclass();
            }
            /* type sniff: first non-static field whose type looks like Options */
            c = mc.getClass();
            while (c != null && c != Object.class) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    String tn = f.getType().getSimpleName();
                    if (tn.contains("ption") || tn.equals("Options")
                            || tn.equals("GameOptions") || tn.equals("class_315")) {
                        f.setAccessible(true);
                        return f.get(mc);
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Find the chunk-updates related field in the Options object.
     *
     *  Classic (1.8 - 1.19.2): chunkUpdates is a plain int or OptionInstance<Integer>.
     *  Modern (1.19.4+):       replaced by chunkBuilderMode, an enum-based
     *                          OptionInstance<PrioritizeChunkUpdates>. That knob is
     *                          thread-priority policy, not an int count, so we cannot
     *                          (and should not) write 1/2/4 into it — we resolve and
     *                          then leave it untouched, logging once.
     */
    private static void resolveChunkUpdatesField(Class<?> optsClass) {
        try {
            String[] names = new String[]{
                    "field_74335_Z", "chunkUpdates", "f_92082_",
                    "chunkBuilderMode", "field_34787", "f_193769_"};
            for (String n : names) {
                try {
                    Field f = optsClass.getDeclaredField(n);
                    f.setAccessible(true);
                    chunkUpdatesField = f;
                    chunkUpdatesIsOptionInstance = f.getType() != Integer.TYPE;
                    if (chunkUpdatesIsOptionInstance) {
                        Class<?> t = f.getType();
                        if (!isIntOption(t)) {
                            enforceThreadedChunkBuilder(f, n);
                            AgentMain.log("[tuner] chunk option '" + n + "' is "
                                    + t.getSimpleName() + " on this MC version; not int-based, skipping writes");
                            chunkUpdatesField = null;
                        }
                    } else {
                        AgentMain.log("[tuner] chunkUpdates field '" + n + "' is int; tuner active");
                    }
                    return;
                } catch (NoSuchFieldException ignored) {}
            }
            for (Field f : optsClass.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.getName().toLowerCase().contains("chunk")) {
                    f.setAccessible(true);
                    chunkUpdatesField = f;
                    chunkUpdatesIsOptionInstance = f.getType() != Integer.TYPE;
                    if (chunkUpdatesIsOptionInstance && !isIntOption(f.getType())) {
                        chunkUpdatesField = null;
                    }
                    return;
                }
            }
            AgentMain.log("[tuner] no chunk-updates option found on this MC version; tuner passive (thread build-modes only)");
        } catch (Throwable ignored) {}
    }

    /** On 1.19.4+ the chunk knob is chunkBuilderMode, an enum policy
     *  (PrioritizeChunkUpdates). Threaded/NONE keeps chunk rebuilds on
     *  worker threads so world loading doesn't block the render thread;
     *  blockier policies (BY_PLAYER/NEARBY/SEMI/FULL_BLOCKING) cause the
     *  render-thread stutter spikes seen while flying into new chunks.
     *  Force the threaded policy once — safe and version-aware. */
    private static void enforceThreadedChunkBuilder(Field f, String name) {
        try {
            Object opt = f.get(optionsInstance);
            if (opt == null) return;
            Method getMethod = null;
            for (Method md : opt.getClass().getMethods()) {
                if (md.getName().equals("get") && md.getParameterCount() == 0) {
                    getMethod = md;
                    break;
                }
            }
            if (getMethod == null) return;
            Object current = getMethod.invoke(opt);
            if (current == null || !current.getClass().isEnum()) return;

            Object best = null, fallback = null;
            for (Object c : current.getClass().getEnumConstants()) {
                String cn = ((Enum<?>) c).name();
                if (cn.equals("THREADED")) { best = c; break; }
                if (cn.equals("NONE")) fallback = c;  // 1.19.4-1.20.1 era
            }
            if (best == null) best = fallback;
            if (best == null) return;

            if (best == current) {
                AgentMain.log("[tuner] " + name + " already " + ((Enum<?>) current).name());
                return;
            }
            Method setValue = findSetMethod(opt.getClass());
            if (setValue != null) {
                setValue.invoke(opt, best);
                AgentMain.log("[tuner] " + name + " " + ((Enum<?>) current).name()
                        + " -> " + ((Enum<?>) best).name() + " (threaded, less load-stutter)");
            }
        } catch (Throwable ignored) {}
    }

    /** True if the OptionInstance-ish type holds an Integer value. */
    private static boolean isIntOption(Class<?> optType) {
        try {
            /* OptionInstance stores its value in a field named "value"
               (intermediary field_37868 / f_231481_); check its type. */
            for (Field f : optType.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.getName().equals("value") || f.getName().equals("field_37868") || f.getName().equals("f_231481_")) {
                    f.setAccessible(true);
                    Class<?> vt = f.getType();
                    return vt == Integer.TYPE || vt == Integer.class;
                }
            }
            /* Fallback: look at the OptionInstance's declared value field by
               generic parameter if the inner class is OptionInstance$Enum etc. */
            for (Field f : optType.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.getType() == Integer.TYPE || f.getType() == Integer.class) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
