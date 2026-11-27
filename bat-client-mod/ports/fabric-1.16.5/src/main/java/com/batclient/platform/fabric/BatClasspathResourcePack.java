package com.batclient.platform.fabric;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.metadata.ResourceMetadataReader;
import net.minecraft.util.Identifier;

/** Exposes bundled batclient:* assets to 1.16.5's ResourceManager without Fabric API. */
public final class BatClasspathResourcePack implements ResourcePack {
    public static final BatClasspathResourcePack INSTANCE = new BatClasspathResourcePack();
    private static final Set<String> NAMESPACES = Collections.singleton("batclient");

    private BatClasspathResourcePack() {}

    @Override
    public InputStream openRoot(String fileName) throws IOException {
        throw new FileNotFoundException(fileName);
    }

    @Override
    public InputStream open(ResourceType type, Identifier id) throws IOException {
        if (type != ResourceType.CLIENT_RESOURCES || id == null || !"batclient".equals(id.getNamespace())) {
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
    public Collection<Identifier> findResources(
            ResourceType type, String namespace, String prefix, int maxDepth, Predicate<String> pathFilter) {
        return Collections.emptyList();
    }

    @Override
    public boolean contains(ResourceType type, Identifier id) {
        return type == ResourceType.CLIENT_RESOURCES
            && id != null
            && "batclient".equals(id.getNamespace())
            && BatClasspathResourcePack.class.getResource("/assets/batclient/" + id.getPath()) != null;
    }

    @Override
    public Set<String> getNamespaces(ResourceType type) {
        return type == ResourceType.CLIENT_RESOURCES ? NAMESPACES : Collections.emptySet();
    }

    @Override
    public <T> T parseMetadata(ResourceMetadataReader<T> metaReader) {
        return null;
    }

    @Override
    public String getName() {
        return "BatClient";
    }

    @Override
    public void close() {}
}
