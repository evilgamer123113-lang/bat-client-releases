package com.batclient;

import com.batclient.hud.ChatFlow;
import com.batclient.platform.mc1194.DrawContext;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.Texts;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

/** Native visual chat for 1.8 / 1.8.9. */
public final class NativeChat {
    private static final Map<Object, UUID> SENDERS = new LinkedHashMap<>();
    private static final Pattern LEGACY = Pattern.compile("^(?:<([A-Za-z0-9_]{1,16})>|([A-Za-z0-9_]{1,16}):) .+");
    private final Map<Text, Line> rendered = new IdentityHashMap<>();
    private Group last;
    private JsonObject heads, animations, repeats;
    private boolean refreshing;
    private String configuration = "";
    private boolean dirty;
    private JsonObject lastSnapshot;

    private static final class Group {
        final Text text;
        final UUID sender;
        int count = 1, tick;
        final long born;

        Group(ChatHudLine line, UUID sender, boolean refresh) {
            text = line.getText();
            this.sender = sender;
            tick = line.getCreationTick();
            born = refresh ? 0 : System.nanoTime() / 1_000_000L;
        }
    }

    private static final class Line {
        private final Group group;
        private final boolean first;
        private final boolean head;

        Line(Group group, boolean first, boolean head) {
            this.group = group;
            this.first = first;
            this.head = head;
        }
    }

    public static void remember(Object signature, UUID sender) {
        if (signature == null || sender == null) return;
        SENDERS.put(signature, sender);
        while (SENDERS.size() > 256) SENDERS.remove(SENDERS.keySet().iterator().next());
    }

    public boolean configure() {
        JsonObject cfg = HudConfig.snapshot();
        if (cfg == lastSnapshot) return dirty;
        lastSnapshot = cfg;
        heads = HudConfig.module(cfg, "chatheads");
        animations = HudConfig.module(cfg, "chatanimations");
        repeats = HudConfig.module(cfg, "chatrepeats");
        String next = String.valueOf(heads) + animations + repeats;
        dirty |= !configuration.equals(next);
        configuration = next;
        return dirty;
    }

    public void beginRefresh() { refreshing = true; dirty = false; last = null; rendered.clear(); }
    public void endRefresh() { refreshing = false; }
    public void clear() { last = null; rendered.clear(); SENDERS.clear(); refreshing = false; }

    private UUID sender(MinecraftClient c, ChatHudLine line) {
        if (c.getNetworkHandler() == null) return null;
        Matcher match = LEGACY.matcher(line.getText().asUnformattedString());
        if (!match.matches()) return null;
        String name = match.group(1) != null ? match.group(1) : match.group(2);
        for (PlayerListEntry player : c.getNetworkHandler().getPlayerList()) {
            if (player.getProfile().getName().equals(name)) return player.getProfile().getId();
        }
        return null;
    }

    public boolean append(MinecraftClient c, ChatHudLine message, List<ChatHudLine> visible, int width, boolean focused, int scrolled, IntConsumer scroll) {
        UUID sender = sender(c, message);
        boolean merge = last != null && ChatFlow.repeats(
            message.getText().asUnformattedString(),
            last.text.asUnformattedString(),
            sender, last.sender, message.getCreationTick(), last.tick,
            HudConfig.setNumber(repeats, "window", 10)
        );
        if (merge) {
            Group prior = last;
            visible.removeIf(line -> {
                Line metadata = rendered.get(line.getText());
                return metadata != null && metadata.group == prior;
            });
            Group next = new Group(message, sender, refreshing);
            next.count = Math.min(9999, prior.count + 1);
            last = next;
        } else {
            last = new Group(message, sender, refreshing);
        }
        boolean head = sender != null;
        Text text = message.getText();
        if (sender != null) {
            text = text.copy().append(new LiteralText(" [" + last.count + "x]").setStyle(new Style().setFormatting(Formatting.GRAY)));
        }
        if (head) text = new LiteralText("   ").append(text);
        List<Text> lines = Texts.wrapLines(text, Math.max(1, width), c.textRenderer, false, false);
        boolean unread = false;
        for (int i = 0; i < lines.size(); i++) {
            if (focused && scrolled > 0) { unread = true; scroll.accept(1); }
            Text content = lines.get(i);
            visible.add(0, new ChatHudLine(message.getCreationTick(), content, message.getId()));
            rendered.put(content, new Line(last, i == 0, head));
        }
        while (visible.size() > 100) visible.remove(visible.size() - 1);
        Set<Text> active = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ChatHudLine line : visible) active.add(line.getText());
        rendered.keySet().retainAll(active);
        return unread;
    }

    public int draw(DrawContext ctx, TextRenderer font, Text text, int x, int y, int color, boolean focused, boolean scrolled) {
        Line line = rendered.get(text);
        if (line == null) return ctx.drawTextWithShadow(font, text, x, y, color);
        float progress = 1;
        if (!focused && !scrolled) {
            progress = ChatFlow.progress(System.nanoTime() / 1_000_000L - line.group.born, HudConfig.setNumber(animations, "duration", 200));
        }
        float alpha = progress;
        if (focused) alpha *= ChatOpeningAnimation.progress();
        if (alpha < .02f) return 0;
        int originalAlpha = color >>> 24;
        int animatedColor = (color & 0xFFFFFF) | (Math.max(4, (int) (originalAlpha * alpha)) << 24);
        float distance = HudConfig.setNumber(animations, "slide", 8);
        if (!Float.isFinite(distance)) distance = 8;
        float offset = (1 - progress) * MathHelper.clamp(distance, 0, 16);
        ctx.getMatrices().push();
        try {
            ctx.getMatrices().translate(offset, 0, 0);
            if (line.head && line.first) {
                MinecraftClient c = MinecraftClient.getInstance();
                PlayerListEntry entry = c.getNetworkHandler() == null ? null : c.getNetworkHandler().getPlayerListEntry(line.group.sender);
                if (entry != null) {
                    GlStateManager.color(1, 1, 1, originalAlpha / 255f * alpha);
                    try {
                        ctx.drawTexture(entry.getSkinTexture(), x, y, 8, 8, 8.0f, 8.0f, 8, 8, 64, 64);
                        if (HudConfig.bool(heads, "hat", true)) {
                            ctx.drawTexture(entry.getSkinTexture(), x, y, 8, 8, 40.0f, 8.0f, 8, 8, 64, 64);
                        }
                    } finally {
                        GlStateManager.color(1, 1, 1, 1);
                    }
                }
            }
            return ctx.drawTextWithShadow(font, text, x, y, animatedColor);
        } finally {
            ctx.getMatrices().pop();
        }
    }

    public String copyText(Text content) {
        Line line = rendered.get(content);
        return line == null ? null : Formatting.strip(line.group.text.asUnformattedString());
    }
}
