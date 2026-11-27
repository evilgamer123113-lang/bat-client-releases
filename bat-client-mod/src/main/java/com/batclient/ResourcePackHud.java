package com.batclient;

import com.google.gson.JsonObject;
import com.batclient.hud.ResourcePackCardLayout;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.List;

/** Highest-priority active user-facing pack, with styled metadata and cached pack.png. */
public final class ResourcePackHud {
    private static final Identifier FALLBACK = Identifier.ofVanilla("textures/misc/unknown_pack.png");
    private static ResourcePackProfile cachedProfile;
    private static Identifier icon;
    private static int iconWidth = 32, iconHeight = 32;
    private static boolean dirty = true;
    private ResourcePackHud() { }

    public static void init() {
        com.batclient.platform.ClientResourceServices.get().reload(Identifier.of("batclient","resource_pack_hud"),()->dirty=true);
    }

    public static ResourcePackProfile top(MinecraftClient client) {
        ResourcePackProfile selected = null;
        for (var pack : client.getResourcePackManager().getEnabledProfiles()) {
            if (pack.getId().equals("vanilla")) continue;
            else if (pack.getSource() != ResourcePackSource.FEATURE && !pack.getId().equals("fabric")
                    && (!pack.isRequired() || pack.getSource() == ResourcePackSource.SERVER
                        || pack.getSource() == ResourcePackSource.WORLD || pack.getId().startsWith("file/"))) selected = pack;
        }
        return selected;
    }

    public record Layout(ResourcePackProfile pack, List<OrderedText> name, List<OrderedText> description,
                         int width, int height, int iconSize, int iconX, int iconY, int textX, int textY,
                         int textWidth, float textScale, int lineStep) { }

    private static float setting(JsonObject mod, String key, float fallback, float min, float max) {
        float value=HudConfig.setNumber(mod,key,fallback);
        return Float.isFinite(value)?Math.clamp(value,min,max):fallback;
    }

    public static Layout layout(MinecraftClient client, JsonObject mod, boolean preview) {
        var pack = top(client);
        if (pack == null && preview) {
            for (var candidate : client.getResourcePackManager().getEnabledProfiles()) {
                if (candidate.getId().equals("vanilla")) { pack = candidate; break; }
            }
        }
        if (pack == null) return new Layout(null,List.of(),List.of(),0,0,0,0,0,0,0,0,1,client.textRenderer.fontHeight+1);
        Text name = pack.getDisplayName();
        Text description = pack.getDescription();
        int wrapWidth = Math.round(setting(mod,"textWidth",200,100,320));
        // Keep Text/OrderedText throughout: JSON styles, legacy section colors and line breaks survive.
        List<OrderedText> names = HudConfig.bool(mod,"showName",true)?client.textRenderer.wrapLines(name,wrapWidth).stream().limit(Math.round(setting(mod,"nameLines",2,1,4))).toList():List.of();
        List<OrderedText> descriptions = HudConfig.bool(mod,"showDescription",true)?client.textRenderer.wrapLines(description,wrapWidth).stream().limit(Math.round(setting(mod,"descriptionLines",4,1,8))).toList():List.of();
        float scale=setting(mod,"textScale",1,.75f,2);
        int lineStep=client.textRenderer.fontHeight+Math.round(setting(mod,"lineSpacing",1,0,6));
        int textWidth=java.util.stream.Stream.concat(names.stream(),descriptions.stream()).mapToInt(client.textRenderer::getWidth).max().orElse(0);
        int textW=(int)Math.ceil(textWidth*scale),textH=(int)Math.ceil((names.size()+descriptions.size())*lineStep*scale);
        int iconSize=HudConfig.bool(mod,"showIcon",true)?Math.round(setting(mod,"iconSize",32,16,64)):0;
        var bounds=ResourcePackCardLayout.resolve(textW,textH,iconSize,Math.round(setting(mod,"gap",6,0,20)),HudConfig.str(mod,"iconPosition","Left"));
        return new Layout(pack,names,descriptions,bounds.width(),bounds.height(),iconSize,bounds.iconX(),bounds.iconY(),bounds.textX(),bounds.textY(),textWidth,scale,lineStep);
    }

    private static Identifier icon(MinecraftClient client, ResourcePackProfile pack) {
        if (!dirty && pack == cachedProfile && icon != null) return icon;
        close(client); cachedProfile = pack;
        dirty = false;
        icon = FALLBACK; iconWidth = iconHeight = 32;
        if (pack == null) return icon;
        // Only read/decode when the selected profile changes (including resource reload).
        try (var resource = pack.createResourcePack()) {
            var supplier = resource.openRoot("pack.png");
            if (supplier == null) return icon;
            try (var input = supplier.get()) {
                NativeImage image = NativeImage.read(input);
                iconWidth = image.getWidth(); iconHeight = image.getHeight();
                NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
                try { icon = client.getTextureManager().registerDynamicTexture("bat_resource_pack",texture); }
                catch (RuntimeException e) { texture.close(); throw e; }
            }
        } catch (Exception e) {
            if (icon != null && !icon.equals(FALLBACK)) client.getTextureManager().destroyTexture(icon);
            icon = FALLBACK; iconWidth = iconHeight = 32;
            BatClientMod.LOGGER.debug("[resourcepacks] Pack icon unavailable for {}: {}", pack.getId(),e.toString());
        }
        return icon;
    }

    public static void draw(DrawContext ctx, MinecraftClient client, JsonObject mod, int defaultColor, boolean preview) {
        var layout = layout(client,mod,preview);
        if (layout.pack() == null) return;
        if(layout.iconSize()>0) {
            Identifier texture = icon(client,layout.pack());
            ctx.drawTexture(texture,layout.iconX(),layout.iconY(),layout.iconSize(),layout.iconSize(),0f,0f,iconWidth,iconHeight,iconWidth,iconHeight);
        }
        boolean shadow = HudConfig.bool(mod,"shadow",true);
        boolean originalColors=HudConfig.bool(mod,"originalColors",true);
        int descriptionColor=color(HudConfig.str(mod,"descriptionColor","#AAAAAAFF"));
        ctx.getMatrices().push();
        try {
            ctx.getMatrices().translate(layout.textX(),layout.textY(),0);
            ctx.getMatrices().scale(layout.textScale(),layout.textScale(),1);
            int y=0;
            for(var line:layout.name()) { drawLine(ctx,client,mod,layout,line,y,defaultColor,originalColors,shadow);y+=layout.lineStep(); }
            for(var line:layout.description()) { drawLine(ctx,client,mod,layout,line,y,descriptionColor,originalColors,shadow);y+=layout.lineStep(); }
        } finally { ctx.getMatrices().pop(); }
    }

    private static void drawLine(DrawContext ctx,MinecraftClient client,JsonObject mod,Layout layout,OrderedText line,int y,int color,boolean originalColors,boolean shadow) {
        String align=HudConfig.str(mod,"alignment","Left");
        int remaining=layout.textWidth()-client.textRenderer.getWidth(line);
        int x=align.equals("Right")?remaining:align.equals("Center")?remaining/2:0;
        OrderedText styled=originalColors?line:visitor->line.accept((index,style,codePoint)->visitor.accept(index,style.withColor(color&0xFFFFFF),codePoint));
        ctx.drawText(client.textRenderer,styled,x,y,color,shadow);
    }

    private static int color(String value) {
        try {
            String hex=value.startsWith("#")?value.substring(1):value;
            if(hex.length()==6) return 0xFF000000|Integer.parseInt(hex,16);
            if(hex.length()==8) { long rgba=Long.parseLong(hex,16);return (int)((rgba>>>8)|((rgba&255)<<24)); }
        } catch(RuntimeException ignored) { }
        return 0xFFAAAAAA;
    }

    public static void close(MinecraftClient client) {
        if (icon != null && !icon.equals(FALLBACK)) client.getTextureManager().destroyTexture(icon);
        icon = null; cachedProfile = null; dirty = true;
    }
}
