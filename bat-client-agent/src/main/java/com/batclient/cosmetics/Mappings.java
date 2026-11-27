/*
 * Decompiled with CFR 0.152.
 */
package com.batclient.cosmetics;

import com.batclient.cosmetics.MappingsData;
import java.util.HashMap;
import java.util.Map;

final class Mappings {
    static final int OFFICIAL = 0;
    static final int INTERMEDIARY = 1;
    static final int NAMED = 2;
    private final String version;
    private final int ns;
    private final Map<String, String[]> symbols = new HashMap<String, String[]>();
    private final Map<String, String> meta = new HashMap<String, String>();

    Mappings(String version, String mapping) {
        int ns;
        this.version = Mappings.normalize(version);
        if ("official".equals(mapping)) {
            ns = 0;
        } else if ("intermediary".equals(mapping)) {
            ns = 1;
        } else if ("named".equals(mapping)) {
            ns = 2;
        } else if ("srgobf".equals(mapping)) {
            // Classic Forge (1.8-1.12.2) runtime loads obfuscated class names;
            // the agent only needs class lookup, so alias srgobf to official.
            ns = 0;
        } else {
            throw new IllegalArgumentException("unknown mapping: " + mapping);
        }
        this.ns = ns;
        for (String[] row : MappingsData.DATA) {
            if (!Mappings.normalize(row[2]).equals(this.version)) continue;
            if ("X".equals(row[0])) {
                this.meta.put(row[1], row[3]);
                continue;
            }
            this.symbols.put(row[0] + "/" + row[1], new String[]{row[3], row[4], row[5]});
        }
    }

    String version() {
        return this.version;
    }

    String cls(String symbol) {
        String[] r = this.symbols.get("C/" + symbol);
        return r == null ? null : r[this.ns];
    }

    String mtd(String symbol) {
        String[] r = this.symbols.get("M/" + symbol);
        return r == null ? null : r[this.ns];
    }

    String meta(String key) {
        return this.meta.get(key);
    }

    boolean hasClass(String symbol) {
        return this.cls(symbol) != null;
    }

    private static String normalize(String v) {
        if (v == null) {
            return "";
        }
        if (v.endsWith(".0")) {
            return Mappings.normalize(v.substring(0, v.length() - 2));
        }
        return v;
    }
}

