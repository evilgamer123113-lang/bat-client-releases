package com.batclient;

import com.batclient.hud.ResourcePackCardLayout;
import com.batclient.platform.mc1194.DrawContext;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.ResourcePackLoader;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Highest-priority active user-facing pack, with styled metadata and cached pack.png. */
public final class ResourcePackHud {
    private static final Identifier FALLBACK = new Identifier("textures/misc/unknown_pack.png");
    private static ResourcePackLoader.Entry cachedProfile;
    private static Identifier icon;
    private static int iconWidth = 32, iconHeight = 32;
    private static boolean dirty = true;

    private ResourcePackHud() { }

    public static void init() {
        com.batclient.platform.ClientResourceServices.get().reload(new Identifier("batclient", "resource_pack_hud"), () -> dirty = true);
    }

    public static ResourcePackLoader.Entry top(MinecraftClient client) {
        List<ResourcePackLoader.Entry> selected = client.getResourcePackLoader().getSelectedResourcePacks();
        return selected.isEmpty() ? null : selected.get(selected.size() - 1);
    }

    public static final class Layout {
        public final ResourcePackLoader.Entry pack;
        public final List<String> name;
        public final List<String> description;
        public final int width, height, iconSize, iconX, iconY, textX, textY, textWidth, lineStep;
        public final float textScale;

        public Layout(ResourcePackLoader.Entry pack, List<String> name, List<String> description,
                      int width, int height, int iconSize, int iconX, int iconY, int textX, int textY,
                      int textWidth, float textScale, int lineStep) {
            this.pack = pack;
            this.name = name;
            this.description = description;
            this.width = width;
            this.height = height;
            this.iconSize = iconSize;
            this.iconX = iconX;
            this.iconY = iconY;
            this.textX = textX;
            this.textY = textY;
            this.textWidth = textWidth;
            this.textScale = textScale;
            this.lineStep = lineStep;
        }

        public ResourcePackLoader.Entry pack() { return pack; }
        public List<String> name() { return name; }
        public List<String> description() { return description; }
        public int width() { return width; }
        public int height() { return height; }
        public int iconSize() { return iconSize; }
        public int iconX() { return iconX; }
        public int iconY() { return iconY; }
        public int textX() { return textX; }
        public int textY() { return textY; }
        public int textWidth() { return textWidth; }
        public float textScale() { return textScale; }
        public int lineStep() { return lineStep; }
    }

    private static float setting(JsonObject mod, String key, float fallback, float min, float max) {
        float value = HudConfig.setNumber(mod, key, fallback);
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }

    private static List<String> wrapLimit(MinecraftClient client, String text, int wrapWidth, int maxLines) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        List<String> wrapped = client.textRenderer.wrapLines(text, wrapWidth);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(maxLines, wrapped.size()); i++) out.add(wrapped.get(i));
        return out;
    }

    public static Layout layout(MinecraftClient client, JsonObject mod, boolean preview) {
        ResourcePackLoader.Entry pack = top(client);
        String nameStr = pack != null ? pack.getName() : (preview ? "Default" : null);
        String descStr = pack != null ? pack.getDescription() : (preview ? "The default look of Minecraft" : null);
        if (nameStr == null) {
            return new Layout(null, Collections.emptyList(), Collections.emptyList(), 0, 0, 0, 0, 0, 0, 0, 0, 1, client.textRenderer.fontHeight + 1);
        }
        int wrapWidth = Math.round(setting(mod, "textWidth", 200, 100, 320));
        List<String> names = HudConfig.bool(mod, "showName", true)
            ? wrapLimit(client, nameStr, wrapWidth, Math.round(setting(mod, "nameLines", 2, 1, 4)))
            : Collections.emptyList();
        List<String> descriptions = HudConfig.bool(mod, "showDescription", true)
            ? wrapLimit(client, descStr, wrapWidth, Math.round(setting(mod, "descriptionLines", 4, 1, 8)))
            : Collections.emptyList();
        float scale = setting(mod, "textScale", 1, .75f, 2);
        int lineStep = client.textRenderer.fontHeight + Math.round(setting(mod, "lineSpacing", 1, 0, 6));
        int textWidth = 0;
        for (String l : names) textWidth = Math.max(textWidth, client.textRenderer.getStringWidth(l));
        for (String l : descriptions) textWidth = Math.max(textWidth, client.textRenderer.getStringWidth(l));
        int textW = (int) Math.ceil(textWidth * scale);
        int textH = (int) Math.ceil((names.size() + descriptions.size()) * lineStep * scale);
        int iconSize = HudConfig.bool(mod, "showIcon", true) ? Math.round(setting(mod, "iconSize", 32, 16, 64)) : 0;
        ResourcePackCardLayout bounds = ResourcePackCardLayout.resolve(
            textW, textH, iconSize, Math.round(setting(mod, "gap", 6, 0, 20)), HudConfig.str(mod, "iconPosition", "Left"));
        return new Layout(pack, names, descriptions, bounds.width(), bounds.height(), iconSize,
            bounds.iconX(), bounds.iconY(), bounds.textX(), bounds.textY(), textWidth, scale, lineStep);
    }

    private static Identifier icon(MinecraftClient client, ResourcePackLoader.Entry pack) {
        if (!dirty && pack == cachedProfile && icon != null) return icon;
        close(client);
        cachedProfile = pack;
        dirty = false;
        icon = FALLBACK;
        iconWidth = iconHeight = 32;
        if (pack == null) return icon;
        try {
            BufferedImage image = pack.getResourcePack().getIcon();
            if (image == null) return icon;
            iconWidth = image.getWidth();
            iconHeight = image.getHeight();
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            icon = client.getTextureManager().registerDynamicTexture("bat_resource_pack", texture);
        } catch (Exception e) {
            icon = FALLBACK;
            iconWidth = iconHeight = 32;
        }
        return icon;
    }

    public static void draw(DrawContext ctx, MinecraftClient client, JsonObject mod, int defaultColor, boolean preview) {
        Layout layout = layout(client, mod, preview);
        if (layout.name().isEmpty() && layout.description().isEmpty()) return;
        if (layout.iconSize() > 0) {
            Identifier texture = icon(client, layout.pack());
            ctx.drawTexture(texture, layout.iconX(), layout.iconY(), layout.iconSize(), layout.iconSize(), 0f, 0f, iconWidth, iconHeight, iconWidth, iconHeight);
        }
        boolean shadow = HudConfig.bool(mod, "shadow", true);
        boolean originalColors = HudConfig.bool(mod, "originalColors", true);
        int descriptionColor = color(HudConfig.str(mod, "descriptionColor", "#AAAAAAFF"));
        ctx.getMatrices().push();
        try {
            ctx.getMatrices().translate(layout.textX(), layout.textY(), 0);
            ctx.getMatrices().scale(layout.textScale(), layout.textScale(), 1);
            int y = 0;
            for (String line : layout.name()) {
                drawLine(ctx, client, mod, layout, line, y, defaultColor, originalColors, shadow);
                y += layout.lineStep();
            }
            for (String line : layout.description()) {
                drawLine(ctx, client, mod, layout, line, y, descriptionColor, originalColors, shadow);
                y += layout.lineStep();
            }
        } finally {
            ctx.getMatrices().pop();
        }
    }

    private static void drawLine(DrawContext ctx, MinecraftClient client, JsonObject mod, Layout layout, String line, int y, int color, boolean originalColors, boolean shadow) {
        String align = HudConfig.str(mod, "alignment", "Left");
        int remaining = layout.textWidth() - client.textRenderer.getStringWidth(line);
        int x = align.equals("Right") ? remaining : align.equals("Center") ? remaining / 2 : 0;
        String value = originalColors ? line : line.replaceAll("\u00A7.", "");
        ctx.drawText(client.textRenderer, value, x, y, color, shadow);
    }

    private static int color(String hex) {
        return HudRenderer.parseColor(hex, 0xFFAAAAAA);
    }

    public static void close(MinecraftClient client) {
        icon = null;
        cachedProfile = null;
    }
}
