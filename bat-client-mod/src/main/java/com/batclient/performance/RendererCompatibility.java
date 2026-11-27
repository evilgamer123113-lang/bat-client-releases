package com.batclient.performance;

import java.util.function.Predicate;

/** Capability ownership for renderers which replace vanilla's chunk scheduler. */
public final class RendererCompatibility {
    private RendererCompatibility() { }
    public static boolean useVanillaChunkHooks(Predicate<String> installed){
        for(String id:new String[]{"sodium","embeddium","rubidium","optifabric"})if(installed.test(id))return false;
        return true;
    }
}
