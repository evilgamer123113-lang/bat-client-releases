package com.batclient;

/** Built-in scheduling. Legacy smoothchunks module flags/budgets no longer control the engine. */
public final class ChunkSmoothing {
    private ChunkSmoothing() { }
    public static boolean enabled(){return true;}
    public static float rebuildBudget(){return com.batclient.performance.BuiltinPerformance.rebuildBudget();}
    public static float uploadBudget(){return com.batclient.performance.BuiltinPerformance.uploadBudget();}
}
