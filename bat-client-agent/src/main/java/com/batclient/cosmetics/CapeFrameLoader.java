package com.batclient.cosmetics;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Bounded CPU/file decoding only. Never creates or uploads a game texture. */
final class CapeFrameLoader {
    private static final int LIMIT = 16;
    private final Map<String, Pending> pending = new HashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Bat cape frame decoder");
        thread.setDaemon(true);
        return thread;
    });
    private static final class Pending {
        final Future<List<Object>> future;
        long requested;
        Pending(Future<List<Object>> future, long now) { this.future = future; requested = now; }
    }

    synchronized List<Object> poll(String key, Callable<List<Object>> decode, long now) throws Exception {
        Pending entry = pending.get(key);
        if (entry == null) {
            if (pending.size() >= LIMIT) return null;
            pending.put(key, new Pending(worker.submit(decode), now));
            return null;
        }
        entry.requested = now;
        if (!entry.future.isDone()) return null;
        pending.remove(key);
        return entry.future.get();
    }

    synchronized void discardUnused(long now, Consumer<List<Object>> dispose) {
        Iterator<Pending> entries = pending.values().iterator();
        while (entries.hasNext()) {
            Pending entry = entries.next();
            if (now - entry.requested < 30_000 || !entry.future.isDone()) continue;
            entries.remove();
            try { dispose.accept(entry.future.get()); } catch (Exception ignored) { }
        }
    }
}
