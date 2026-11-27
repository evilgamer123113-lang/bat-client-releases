package com.batclient.platform.fabric;

import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;
import net.minecraft.client.resource.ResourceMetadataProvider;
import net.minecraft.resource.ResourcePack;
import net.minecraft.util.Identifier;
import net.minecraft.util.MetadataSerializer;

/** Exposes bundled batclient:* assets to 1.8.9's ResourceManager without Fabric API. */
public final class BatClasspathResourcePack implements ResourcePack {
    public static final BatClasspathResourcePack INSTANCE = new BatClasspathResourcePack();
    private static final Set<String> NAMESPACES = Collections.singleton("batclient");

    private BatClasspathResourcePack() {}

    @Override
    public InputStream open(Identifier id) throws IOException {
        if (id == null || !"batclient".equals(id.getNamespace())) {
            throw new FileNotFoundException(String.valueOf(id));
        }
        String path = "/assets/batclient/" + id.getPath();
        InputStream stream = BatClasspathResourcePack.class.getResourceAsStream(path);
        if (stream == null) {
            throw new FileNotFoundException(id.toString());
        }
        return stream;
    }

    @Override
    public boolean contains(Identifier id) {
        return id != null
            && "batclient".equals(id.getNamespace())
            && BatClasspathResourcePack.class.getResource("/assets/batclient/" + id.getPath()) != null;
    }

    @Override
    public Set<String> getNamespaces() {
        return NAMESPACES;
    }

    @Override
    public <T extends ResourceMetadataProvider> T parseMetadata(MetadataSerializer serializer, String key) {
        return null;
    }

    @Override
    public BufferedImage getIcon() {
        return null;
    }

    @Override
    public String getName() {
        return "BatClient";
    }
}
