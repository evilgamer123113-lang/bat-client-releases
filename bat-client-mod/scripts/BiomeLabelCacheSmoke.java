import com.batclient.hud.BiomeLabelCache;

public final class BiomeLabelCacheSmoke {
    private static int checks;
    private static void check(boolean ok) { checks++; if (!ok) throw new AssertionError(checks); }
    public static void main(String[] args) {
        BiomeLabelCache cache = new BiomeLabelCache();
        Object world = new Object(), language = new Object();
        check(cache.find(world, language, 12, -1, 64, 7) == null);
        cache.store(world, language, 12, -1, 64, 7, "Forest");
        for (int i = 0; i < 10000; i++) check("Forest".equals(cache.find(world, language, 12, -1, 64, 7)));
        check(cache.find(new Object(), language, 12, -1, 64, 7) == null);
        check(cache.find(world, new Object(), 12, -1, 64, 7) == null);
        check(cache.find(world, language, 13, -1, 64, 7) == null);
        check(cache.find(world, language, 12, 0, 64, 7) == null);
        check(cache.find(world, language, 12, -1, 65, 7) == null);
        check(cache.find(world, language, 12, -1, 64, 8) == null);
        cache.store(world, language, 13, -1, 64, 7, "Plains");
        check("Plains".equals(cache.find(world, language, 13, -1, 64, 7)));
        cache.clear();
        check(cache.find(world, language, 13, -1, 64, 7) == null);
        System.out.println("BIOME_LABEL_CACHE_OK " + checks);
    }
}
