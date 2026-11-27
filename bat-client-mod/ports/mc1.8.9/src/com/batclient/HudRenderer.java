package com.batclient;

import com.batclient.hud.HudLayoutEngine;
import com.batclient.hud.KeystrokesLayout;
import com.batclient.hud.layout.AbsoluteBounds;
import com.batclient.hud.layout.HudComponentState;
import com.batclient.hud.layout.HudCoordinateResolver;
import com.batclient.web.WebWindow;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import com.batclient.platform.ClientHookServices;
import net.minecraft.client.MinecraftClient;
import com.batclient.platform.mc1194.DrawContext;
import java.awt.image.BufferedImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.util.Identifier;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;

import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.scoreboard.Team;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Minecraft-native HUD drawing, shared by gameplay and the web editor hitboxes. */
public final class HudRenderer {
	private static final String[] IDS = {
			"fps", "cps", "coords", "keystrokes", "potions", "ping",
			"armor", "durability", "crosshair", "target", "togglesprint", "scoreboard", "bossbar", "blockindicator", "iteminfo", "compass", "clock", "speedometer", "resourcepacks", "combo", "reach", "inventory", "totems", "arrows", "itemtracker", "minimap", "foodpreview", "bowcharge", "deathcoords", "mounthud", "elytrahud", "shieldindicator"
	};

	private static final int[] ARMOR_SLOTS = { 3, 2, 1, 0 };

	private static final int PLATE = 0x6B000000;
	private static final int PAD_H = 6;
	private static final int PAD_V = 4;
	private static final int RADIUS = 6;
	private static Identifier roundedMask;

	private static final com.google.gson.Gson GSON = new com.google.gson.Gson();
	private static long lastFeedMs;
	private static boolean feedErrorLogged;
	private static String lastFedConfig;
	private static String lastFedPayload;
	private static WebWindow lastFedWindow;

	private HudRenderer() {
	}

	public static void init() {
		ClientHookServices.get().hud(HudRenderer::render);
		BatClientMod.LOGGER.info("[hud] Minecraft Java font renderer enabled for gameplay, editor, and module previews");
	}

	private static boolean editorPreview;

	private static void render(DrawContext ctx) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null || client.options.hudHidden) return;
		// WebScreen draws native modules after the world blur, before editor controls.
		if (client.currentScreen instanceof com.batclient.web.WebScreen ||
			client.currentScreen instanceof com.batclient.hud.editor.HudEditorScreen) return;
		drawNative(ctx, client, false);
	}

	public static void drawNative(DrawContext ctx, MinecraftClient client, boolean editing) {
		if (client.player == null || client.world == null || client.options.hudHidden) return;
		editorPreview = editing;
		BlockIndicator.beginRender();
		try { drawModules(ctx, client); }
		finally { BlockIndicator.endRender(); editorPreview = false; }
	}

	public static void feedEditor(WebWindow window, MinecraftClient client) {
		if (window == null || client.player == null) return;
		editorPreview = true;
		try { pushFeedThrottled(window, client, BatClientMod.isHudEditorActive() ? "edit" : "menu"); }
		finally { editorPreview = false; }
	}

	private static void drawModules(DrawContext ctx, MinecraftClient client) {
		TextRenderer fr = client.textRenderer;
		float sw = ctx.getScaledWindowWidth();
		float sh = ctx.getScaledWindowHeight();
		JsonObject cfg = HudConfig.snapshot();
		List<HudComponentState> states = HudLayoutEngine.loadRenderStates(sw, sh);

		for (HudComponentState st : states) {
			String id = st.id;
			// Crosshair replaces vanilla at its native render hook, never a draggable plate.
			// Pre-1.9 clients lack totems, elytra, and shields.
			if (id.equals("crosshair") || id.equals("totems") || id.equals("elytrahud") || id.equals("shieldindicator")) continue;
			if (!st.enabled) continue;

			JsonObject mod = HudConfig.module(cfg, id);
			// Self-hiding modules (no live data -> no widget).
			if (!editorPreview) switch (id) { case "ping": { if (ping(client) == null) continue; } break; case "target": { if (TargetHud.read(client,mod,false) == null) continue; } break; case "itemtracker": { if(ItemPickupTracker.height(mod,false)==0)continue; } break; case "elytrahud": case "shieldindicator": { continue; } case "foodpreview": case "bowcharge": case "deathcoords": case "mounthud": { if(AdventureHud.height(client,id,mod,false)==0)continue; } break; case "potions": { if (client.player.getStatusEffectInstances().isEmpty()) continue; } break; case "armor": case "durability": { if (armorStacks(client).isEmpty()) continue; } break; case "scoreboard": { if (sidebar(client, mod).isEmpty()) continue; } break; case "bossbar": { if (BossBar.name == null || BossBar.framesToLive <= 0) continue; } break; case "blockindicator": { if (BlockIndicator.read(client, mod, false) == null) continue; } break; case "iteminfo": { if (ExtraHud.held(client,mod,false) == null) continue; } break; case "totems": { continue; } case "arrows": { if (HudConfig.bool(mod,"hideZero",false) && ItemCounterHud.count(client,id,mod,false)==0) continue; } break; case "combo": case "reach": { if (HudConfig.bool(mod,"hideInactive",false) && CombatHud.empty(id,mod)) continue; } break; default: {  } break; }

			// Measure for matrix-isolated draw (content at local 0,0).
			float[] wh = measure(client, id, mod);
			if (id.equals("resourcepacks") && wh[0] == 0 && wh[1] == 0 && !editorPreview) continue;
			st.baseWidth = wh[0] + (id.equals("crosshair") ? 0 : PAD_H * 2);
			st.baseHeight = wh[1] + (id.equals("crosshair") ? 0 : PAD_V * 2);

			// Resolver: anchor-relative offsets -> absolute top-left AABB.
			AbsoluteBounds bounds = HudCoordinateResolver.bounds(st, sw, sh);

			ctx.getMatrices().push();
			float drawX = Math.max(0, Math.min(Math.max(0, sw - st.baseWidth * st.scale), bounds.minX));
			float drawY = Math.max(0, Math.min(Math.max(0, sh - st.baseHeight * st.scale), bounds.minY));
			ctx.getMatrices().translate(drawX, drawY, 0);
			ctx.getMatrices().scale(st.scale, st.scale, 1f);
			ctx.getMatrices().translate(id.equals("crosshair") ? 11 : PAD_H, id.equals("crosshair") ? 11 : PAD_V, 0);
			try {
				drawContent(ctx, client, id, mod);
			} catch (Throwable t) {
				BatClientMod.LOGGER.warn("[hud] draw {} failed: {}", id, t.toString());
			}
			ctx.getMatrices().pop();
			if (editorPreview && BatClientMod.isHudEditorActive()) {
				String label = com.batclient.platform.java8.J8.eval(id, _sw -> { switch (_sw) { case "fps": return "FPS"; case "cps": return "CPS"; case "coords": return "Coordinates"; case "armor": return "Armor Status"; case "durability": return "Durability"; case "potions": return "Potion Effects"; case "keystrokes": return "Keystrokes"; case "ping": return "Ping"; case "target": return "Target HUD"; case "togglesprint": return "Toggle Sprint"; case "scoreboard": return "Scoreboard"; case "bossbar": return "Boss Bar"; case "blockindicator": return "Block Indicator"; default: return "Crosshair"; case "iteminfo": return "Item Info"; case "compass": return "Compass"; case "clock": return "Clock"; case "speedometer": return "Speedometer"; case "resourcepacks": return "Resource Pack Display"; case "combo": return "Combo Counter"; case "reach": return "Reach Display"; case "inventory": return "Inventory HUD"; case "totems": return "Totem Counter"; case "arrows": return "Arrow Counter"; case "itemtracker": return "Item Pickup Tracker"; case "minimap": return "Minimap"; case "elytrahud": return "Elytra HUD"; case "shieldindicator": return "Shield Indicator"; case "foodpreview": return "Food Preview"; case "bowcharge": return "Bow Charge Indicator"; case "deathcoords": return "Death Coordinates"; case "mounthud": return "Mount HUD"; } });
				int x = (int)drawX;
				if (drawX >= sw * .65f) x += (int)(st.baseWidth * st.scale) - fr.getStringWidth(label);
				int y = drawY >= sh * .65f ? (int)drawY - fr.fontHeight - 3 : (int)(drawY + st.baseHeight * st.scale) + 6;
				ctx.drawText(fr, label, x, y, 0xFFFFFFFF, true);
			}
		}
	}

	private static void drawContent(DrawContext ctx, MinecraftClient client, String id, JsonObject mod) {
				switch (id) { case "tntcountdown": drawTextW(ctx,client,TntCountdown.text(50,HudConfig.bool(mod,"ticks",false)),mod); break; case "elytrahud": case "shieldindicator": { int width=TravelHud.width(client,id,mod,editorPreview),height=TravelHud.height(client,id,mod,editorPreview);
                        if(height>0&&HudConfig.bool(mod,"background",true))fillRounded(ctx,-PAD_H,-PAD_V,width+PAD_H*2,height+PAD_V*2,RADIUS,plate(mod));
                        TravelHud.draw(ctx,client,id,mod,editorPreview); } break; case "foodpreview": case "bowcharge": case "deathcoords": case "mounthud": { int width=AdventureHud.width(client,id,mod,editorPreview),height=AdventureHud.height(client,id,mod,editorPreview);
						if(height>0&&HudConfig.bool(mod,"background",true))fillRounded(ctx,-PAD_H,-PAD_V,width+PAD_H*2,height+PAD_V*2,RADIUS,plate(mod));
						AdventureHud.draw(ctx,client,id,mod,editorPreview); } break; case "fps": drawTextW(ctx, client, ((com.batclient.ClientAccountAccess)client).bat$currentFps() + " FPS", mod); break; case "coords": drawCoordinates(ctx, client, mod); break; case "ping": drawTextW(ctx, client, ping(client) == null ? "0 ms" : ping(client), mod); break; case "cps": drawTextW(ctx, client,
							ClientMods.lmbCps() + " | " + ClientMods.rmbCps(), mod); break; case "togglesprint": drawTextW(ctx, client, ClientMods.movementLabel(client,mod,editorPreview), mod); break; case "combo": case "reach": drawTextW(ctx,client,CombatHud.text(id,mod,editorPreview),mod); break; case "inventory": drawInventory(ctx,client,mod); break; case "minimap": { int size=MinimapHud.size(mod),height=MinimapHud.height(mod);
						if(HudConfig.bool(mod,"background",true))fillRounded(ctx,-PAD_H,-PAD_V,size+PAD_H*2,height+PAD_V*2,RADIUS,plate(mod));
						MinimapHud.draw(ctx,client,mod,editorPreview); } break; case "itemtracker": { int width=ItemPickupTracker.width(mod),height=ItemPickupTracker.height(mod,editorPreview);
						if(HudConfig.bool(mod,"background",true))fillRounded(ctx,-PAD_H,-PAD_V,width+PAD_H*2,height+PAD_V*2,RADIUS,plate(mod));
						ItemPickupTracker.draw(ctx,mod,editorPreview); } break; case "totems": case "arrows": drawItemCounter(ctx,client,id,mod); break; case "potions": drawPotions(ctx, client, mod); break; case "armor": drawArmor(ctx, client, mod); break; case "durability": drawDurability(ctx, client, mod); break; case "target": drawTarget(ctx, client, mod); break; case "crosshair": drawCrosshair(ctx, client, mod); break; case "blockindicator": drawBlockIndicator(ctx, client, mod); break; case "keystrokes": drawKeystrokes(ctx, client, mod); break; case "scoreboard": drawScoreboard(ctx, client, mod); break; case "bossbar": drawBossBars(ctx, client, mod); break; case "nametags": drawNametagPreview(ctx, client, mod); break; case "healthindicator": { int width=HealthIndicator.previewWidth(mod),height=HealthIndicator.previewHeight(mod);
						if(HudConfig.bool(mod,"background",true))fillRounded(ctx,-PAD_H,-PAD_V,width+PAD_H*2,height+PAD_V*2,RADIUS,plate(mod));
						HealthIndicator.drawPreview(ctx,mod); } break; case "iteminfo": case "compass": case "clock": case "speedometer": case "resourcepacks": drawExtra(ctx, client, id, mod); break; default: {  } break; }
	}

	/** Settings previews use the same Java font and item renderer as gameplay. */
	public static void drawSettingsPreview(DrawContext ctx, MinecraftClient client, JsonObject preview) {
		if (preview == null || !preview.has("id")) return;
		String id = preview.get("id").getAsString();
		if (!id.equals("nametags") && !id.equals("tntcountdown") && !java.util.Arrays.asList(IDS).contains(id)) return;
		float sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
		float left = preview.get("left").getAsFloat() * sw;
		float top = preview.get("top").getAsFloat() * sh;
		float right = preview.get("right").getAsFloat() * sw;
		float bottom = preview.get("bottom").getAsFloat() * sh;
		if (right <= left || bottom <= top) return;
		JsonObject mod = HudConfig.module(HudConfig.snapshot(), id);
		editorPreview = true;
		BlockIndicator.beginRender();
		try {
			float[] size = measure(client, id, mod);
			float w = size[0] + (id.equals("crosshair") ? 0 : PAD_H * 2);
			float h = size[1] + (id.equals("crosshair") ? 0 : PAD_V * 2);
			float scale = Math.min(HudConfig.scale(mod), Math.min((right - left - 8) / Math.max(1, w), (bottom - top - 8) / Math.max(1, h)));
			if (scale <= 0) return;
			ctx.enableScissor((int)left, (int)top, (int)right, (int)bottom);
			ctx.getMatrices().push();
			try {
				ctx.getMatrices().translate(left + (right-left-w*scale)/2, top+(bottom-top-h*scale)/2, 200);
				ctx.getMatrices().scale(scale, scale, 1);
				ctx.getMatrices().translate(id.equals("crosshair") ? 4.5f : PAD_H, id.equals("crosshair") ? 4.5f : PAD_V, 0);
				drawContent(ctx, client, id, mod);
			} finally { ctx.getMatrices().pop(); ctx.disableScissor(); }
		} finally { BlockIndicator.endRender(); editorPreview = false; }
	}

	// ---- live feed (20Hz; payload already dirty-checked in the page) ----

	private static void pushFeedThrottled(WebWindow overlay, MinecraftClient client, String mode) {
		long now = System.currentTimeMillis();
		// 50ms (~20Hz): snappy enough for keystrokes/CPS without evaluateScript spam.
		if (now - lastFeedMs < 50) return;
		lastFeedMs = now;
		try {
			JsonObject cfg = HudConfig.snapshot();
			JsonObject o = new JsonObject();
			o.addProperty("mode", mode);
			o.addProperty("native", true);
			o.addProperty("movement", ClientMods.movementLabel(client,HudConfig.module(cfg,"togglesprint"),false));
			JsonObject sizes = new JsonObject();
			float ratioX = (float) client.width / (float) new net.minecraft.client.util.Window(client).getScaledWidth();
			float ratioY = (float) client.height / (float) new net.minecraft.client.util.Window(client).getScaledHeight();
			for (String id : IDS) {
				float[] wh = measure(client, id, HudConfig.module(cfg, id));
				JsonArray size = new JsonArray();
				size.add(new com.google.gson.JsonPrimitive((wh[0] + (id.equals("crosshair") ? 0 : PAD_H * 2)) * ratioX));
				size.add(new com.google.gson.JsonPrimitive((wh[1] + (id.equals("crosshair") ? 0 : PAD_V * 2)) * ratioY));
				sizes.add(id, size);
			}
			o.add("sizes", sizes);
			JsonObject webConfig = new JsonObject();
			// Keep profile histories out of both the per-frame copy and the web payload.
			for (java.util.Map.Entry<java.lang.String,com.google.gson.JsonElement> field : cfg.entrySet()) if (!field.getKey().equals("profiles")) webConfig.add(field.getKey(), com.batclient.platform.mc1171.GsonCompat.copy(field.getValue()));
			// Anchor offsets are Minecraft GUI pixels, whereas the web canvas uses framebuffer pixels.
			for (HudComponentState state : HudLayoutEngine.loadStates((int) new net.minecraft.client.util.Window(client).getScaledWidth(), (int) new net.minecraft.client.util.Window(client).getScaledHeight())) {
				JsonObject mod = HudConfig.module(webConfig, state.id);
				if (mod == null || !mod.has("anchor")) continue;
				mod.addProperty("x", HudCoordinateResolver.absX(state, (float) new net.minecraft.client.util.Window(client).getScaledWidth()) / (float) new net.minecraft.client.util.Window(client).getScaledWidth() * 100);
				mod.addProperty("y", HudCoordinateResolver.absY(state, (float) new net.minecraft.client.util.Window(client).getScaledHeight()) / (float) new net.minecraft.client.util.Window(client).getScaledHeight() * 100);
				mod.remove("anchor"); mod.remove("offset_x"); mod.remove("offset_y");
			}
			String configJson = webConfig.toString();
			if (overlay != lastFedWindow || !configJson.equals(lastFedConfig)) o.add("config", webConfig);
			String payload = GSON.toJson(o);
			if (overlay == lastFedWindow && payload.equals(lastFedPayload)) return;
			String js = "window.__batHud&&window.__batHud(" + payload + ");";
			overlay.view().evaluateScript(js);
			lastFedConfig = configJson;
			lastFedPayload = payload;
			lastFedWindow = overlay;
		} catch (Throwable t) {
			if (!feedErrorLogged) {
				feedErrorLogged = true;
				BatClientMod.LOGGER.warn("[hud] live feed failed: {}", t.toString());
			}
		}
	}

	private static JsonArray potionsFeed(MinecraftClient c, JsonObject mod) {
		JsonArray arr = new JsonArray();
		boolean full = "Full".equals(HudConfig.str(mod, "format", "Shorthand"));
		boolean skipFakeNight = ClientMods.isFakeNightVision();
		for (StatusEffectInstance eff : statusEffects(c)) {
			if (skipFakeNight && eff.getEffectId() == StatusEffect.NIGHTVISION.id) continue;
			JsonObject it = new JsonObject();
			String name = new net.minecraft.text.TranslatableText(eff.getTranslationKey()).asUnformattedString();
			int amp = eff.getAmplifier() + 1;
			if (amp > 1) name = name + " " + roman(amp);
			int dur = eff.getDuration();
			String time;
			if (dur < 0) time = "\u221E";
			else {
				int secs = dur / 20;
				time = full
						? secs / 60 + "m " + (secs % 60) + "s"
						: secs / 60 + ":" + String.format(Locale.ROOT, "%02d", secs % 60);
			}
			it.addProperty("n", name);
			it.addProperty("t", time);
			arr.add(it);
		}
		return arr;
	}

	private static JsonArray armorFeed(MinecraftClient c, JsonObject mod) {
		JsonArray arr = new JsonArray();
		boolean numbers = HudConfig.bool(mod, "numbers", true);
		for (ItemStack st : armorStacks(c)) {
			if (numbers && st.isDamageable()) {
				arr.add(new com.google.gson.JsonPrimitive(Integer.toString(st.getMaxDamage() - st.getDamage())));
			} else {
				arr.add(com.google.gson.JsonNull.INSTANCE);
			}
		}
		return arr;
	}

	private static JsonArray durabilityFeed(MinecraftClient c, JsonObject mod) {
		JsonArray arr = new JsonArray();
		boolean numbers = HudConfig.bool(mod, "numbers", true);
		for (ItemStack st : armorStacks(c)) {
			if (numbers && st.isDamageable()) {
				arr.add(new com.google.gson.JsonPrimitive((int) (100f * (st.getMaxDamage() - st.getDamage()) / st.getMaxDamage()) + "%"));
			} else {
				arr.add(com.google.gson.JsonNull.INSTANCE);
			}
		}
		return arr;
	}

	private static JsonObject keysFeed(MinecraftClient c) {
		JsonObject k = new JsonObject();
		k.addProperty("w", c.options.forwardKey.isPressed());
		k.addProperty("a", c.options.leftKey.isPressed());
		k.addProperty("s", c.options.backKey.isPressed());
		k.addProperty("d", c.options.rightKey.isPressed());
		k.addProperty("lmb", c.options.attackKey.isPressed());
		k.addProperty("rmb", c.options.useKey.isPressed());
		k.addProperty("space", c.options.jumpKey.isPressed());
		k.addProperty("lc", ClientMods.lmbCps());
		k.addProperty("rc", ClientMods.rmbCps());
		return k;
	}

	// ---- measure (unscaled content size at scale=1) ----

	private static float[] measure(MinecraftClient c, String id, JsonObject mod) {
		TextRenderer fr = c.textRenderer;
		float w = 40f, h = fr.fontHeight;
		switch (id) {
			case "tntcountdown":
				w = fr.getStringWidth(TntCountdown.text(50, HudConfig.bool(mod, "ticks", false)));
				break;
			case "fps":
				w = fr.getStringWidth(((com.batclient.ClientAccountAccess) c).bat$currentFps() + " FPS");
				break;
			case "elytrahud":
			case "shieldindicator":
				w = TravelHud.width(c, id, mod, editorPreview);
				h = TravelHud.height(c, id, mod, editorPreview);
				break;
			case "foodpreview":
			case "bowcharge":
			case "deathcoords":
			case "mounthud":
				w = AdventureHud.width(c, id, mod, editorPreview);
				h = AdventureHud.height(c, id, mod, editorPreview);
				break;
			case "itemtracker":
				w = ItemPickupTracker.width(mod);
				h = ItemPickupTracker.height(mod, editorPreview);
				break;
			case "minimap":
				w = MinimapHud.size(mod);
				h = MinimapHud.height(mod);
				break;
			case "coords": {
				List<String> lines = coordinateLines(c, mod);
				w = lines.stream().mapToInt(fr::getStringWidth).max().orElse(0);
				h = lines.size() * (fr.fontHeight + 1);
				break;
			}
			case "ping": {
				String p = ping(c);
				w = fr.getStringWidth(p == null ? "0 ms" : p);
				break;
			}
			case "cps":
				w = fr.getStringWidth(ClientMods.lmbCps() + " | " + ClientMods.rmbCps());
				break;
			case "togglesprint":
				w = fr.getStringWidth(ClientMods.movementLabel(c, mod, editorPreview));
				break;
			case "combo":
			case "reach":
				w = fr.getStringWidth(CombatHud.text(id, mod, editorPreview));
				break;
			case "inventory": {
				int gap = inventoryGap(mod), rows = HudConfig.bool(mod, "hotbar", false) ? 4 : 3;
				w = 9 * (16 + gap) - gap;
				h = rows * (16 + gap) - gap + inventoryTitle(c, mod);
				break;
			}
			case "nametags":
				w = fr.getStringWidth(nametagPreview(c).asFormattedString());
				break;
			case "resourcepacks": {
				com.batclient.ResourcePackHud.Layout pack = ResourcePackHud.layout(c, mod, editorPreview);
				w = pack.width();
				h = pack.height();
				break;
			}
			case "totems":
			case "arrows": {
				boolean icon = HudConfig.bool(mod, "icon", true);
				w = fr.getStringWidth(ItemCounterHud.text(id, mod, ItemCounterHud.count(c, id, mod, editorPreview))) + (icon ? 21 : 0);
				h = Math.max(icon ? 16 : 0, fr.fontHeight);
				break;
			}
			case "iteminfo":
			case "compass":
			case "clock":
			case "speedometer": {
				java.util.List<java.lang.String> lines = ExtraHud.lines(c, id, mod, editorPreview);
				w = lines.stream().mapToInt(fr::getStringWidth).max().orElse(0) + (id.equals("iteminfo") ? 22 : 0);
				h = Math.max(id.equals("iteminfo") ? 16 : 0, lines.size() * (fr.fontHeight + 1));
				if (id.equals("compass") && "Bar".equals(HudConfig.str(mod, "style", "Bar"))) {
					w = 160;
					h = fr.fontHeight * 2 + 10;
				}
				break;
			}
			case "potions": {
				List<StatusEffectInstance> effects = visibleEffects(c);
				int maxW = 0;
				for (StatusEffectInstance e : effects) {
					maxW = Math.max(maxW, 22 + Math.max(fr.getStringWidth(effectName(e)), fr.getStringWidth(effectTime(e, mod))));
				}
				w = maxW;
				h = effects.size() * (fr.fontHeight * 2 + 4);
				break;
			}
			case "armor": {
				List<ItemStack> items = armorStacks(c);
				boolean numbers = HudConfig.bool(mod, "numbers", true);
				int maxNum = 0;
				if (numbers) {
					for (ItemStack st : items) {
						if (st.isDamageable()) {
							maxNum = Math.max(maxNum, fr.getStringWidth(Integer.toString(st.getMaxDamage() - st.getDamage())));
						}
					}
				}
				w = 16 + (numbers && maxNum > 0 ? 3 + maxNum : 0);
				h = Math.max(16, items.size() * 16);
				break;
			}
			case "durability": {
				int n = Math.max(1, armorStacks(c).size());
				w = n * 16 + (n - 1) * 5;
				h = 16 + fr.fontHeight + 2;
				break;
			}
			case "target":
				if (TargetHud.read(c, mod, editorPreview) != null) {
					w = TargetHud.width(mod);
					h = TargetHud.height(mod);
				} else {
					w = 0;
					h = 0;
				}
				break;
			case "healthindicator":
				w = HealthIndicator.previewWidth(mod);
				h = HealthIndicator.previewHeight(mod);
				break;
			case "crosshair":
				w = 9;
				h = 9;
				break;
			case "blockindicator": {
				com.batclient.BlockIndicator.Info info = BlockIndicator.read(c, mod, editorPreview);
				if (info == null) {
					w = 0;
					h = 0;
				} else {
					w = 30 + Math.max(fr.getStringWidth(BlockIndicator.title(info)), HudConfig.bool(mod, "showId", true) ? fr.getStringWidth(info.id()) : 0);
					h = Math.max(24, HudConfig.bool(mod, "showId", true) ? fr.fontHeight * 2 + 4 : fr.fontHeight);
				}
				break;
			}
			case "keystrokes": {
				com.batclient.hud.KeystrokesLayout layout = keystrokesLayout(c, mod);
				w = layout.width();
				h = layout.height();
				break;
			}
			case "scoreboard": {
				java.util.List<com.batclient.HudRenderer.SidebarRow> rows = sidebar(c, mod);
				int maxW = 0;
				for (com.batclient.HudRenderer.SidebarRow row : rows) {
					maxW = Math.max(maxW, sidebarWidth(fr, row));
				}
				w = maxW;
				h = rows.size() * (fr.fontHeight + 1);
				break;
			}
			case "bossbar": {
				boolean active = (BossBar.name != null && BossBar.framesToLive > 0) || editorPreview;
				String bName = BossBar.name != null && BossBar.framesToLive > 0 ? BossBar.name : "Bat Client";
				w = active ? Math.max(182, HudConfig.bool(mod, "showName", true) ? fr.getStringWidth(bName) : 0) : 0;
				h = active ? bossRowHeight(fr, mod) : 0;
				break;
			}
			default:
				break;
		}
		float[] r = { w, h };
		return r;
	}

	// ---- shared ----
	private static void drawItemCounter(DrawContext ctx,MinecraftClient client,String id,JsonObject mod) {
		int count=ItemCounterHud.count(client,id,mod,editorPreview);
		String text=ItemCounterHud.text(id,mod,count);
		boolean icon=HudConfig.bool(mod,"icon",true);
		int left=icon?21:0, h=Math.max(icon?16:0,client.textRenderer.fontHeight);
		int width=left+client.textRenderer.getStringWidth(text);
		if (HudConfig.bool(mod,"background",true)) fillRounded(ctx,-PAD_H,-PAD_V,width+PAD_H*2,h+PAD_V*2,RADIUS,plate(mod));
		if (icon) ctx.drawItem(ItemCounterHud.icon(id),0,0);
		boolean warn=ItemCounterHud.warning(id,mod,count) && com.batclient.hud.ArmorWarning.visible(HudConfig.bool(mod,"warningFlash",false),System.nanoTime()/1_000_000L);
		int color=warn?parseColor(HudConfig.str(mod,"warningColor","#FF5555FF"),0xFFFF5555):textColor(mod);
		ctx.drawText(client.textRenderer,text,left,(h-client.textRenderer.fontHeight)/2,color,HudConfig.bool(mod,"shadow",true));
	}
	private static void drawExtra(DrawContext ctx, MinecraftClient c, String id, JsonObject mod) {
		float[] size = measure(c,id,mod);
		if (id.equals("resourcepacks") && size[0] == 0 && size[1] == 0) return;
		if (HudConfig.bool(mod,"background",true)) fillRounded(ctx,-PAD_H,-PAD_V,(int)size[0]+PAD_H*2,(int)size[1]+PAD_V*2,RADIUS,plate(mod));
		boolean shadow = HudConfig.bool(mod,"shadow",true);
		if (id.equals("resourcepacks")) { ResourcePackHud.draw(ctx,c,mod,textColor(mod),editorPreview); return; }
		if (id.equals("compass") && "Bar".equals(HudConfig.str(mod,"style","Bar"))) {
			double heading = ExtraHud.heading(c);
			String[] points = {"N","NE","E","SE","S","SW","W","NW"};
			ctx.fill(79,0,81,4,textColor(mod));
			for (int i=0;i<8;i++) {
				float offset = (float)MathHelper.wrapDegrees(i*45-heading)*.8f;
				if (Math.abs(offset) > 70) continue;
				ctx.drawText(c.textRenderer,points[i],Math.round(80+offset-c.textRenderer.getStringWidth(points[i])/2f),6,textColor(mod),shadow);
			}
			WaypointRenderer.compass(ctx,c,heading,editorPreview);
			String label = ExtraHud.lines(c,id,mod,editorPreview).get(0);
			ctx.drawText(c.textRenderer,label,80-c.textRenderer.getStringWidth(label)/2,c.textRenderer.fontHeight+10,textColor(mod),shadow);
			return;
		}
		int left = id.equals("iteminfo") ? 22 : 0;
		if (left > 0) ctx.drawItem(ExtraHud.held(c,mod,editorPreview),0,0);
		java.util.List<java.lang.String> lines = ExtraHud.lines(c,id,mod,editorPreview);
		for (int i=0;i<lines.size();i++) ctx.drawText(c.textRenderer,lines.get(i),left,i*(c.textRenderer.fontHeight+1),textColor(mod),shadow);
	}
	private static Text nametagPreview(MinecraftClient client) {
		return NativeNametags.ownName(new net.minecraft.text.LiteralText(client.getSession().getUsername()));
	}

	private static void drawNametagPreview(DrawContext ctx, MinecraftClient client, JsonObject mod) {
		Text name = nametagPreview(client);
		if (HudConfig.bool(mod, "background", true)) fillRounded(ctx, -PAD_H, -PAD_V,
				client.textRenderer.getStringWidth(name.asFormattedString()) + PAD_H * 2, client.textRenderer.fontHeight + PAD_V * 2, RADIUS, PLATE);
		ctx.drawText(client.textRenderer, name.asFormattedString(), 0, 0, 0xFFFFFFFF, HudConfig.bool(mod, "shadow", true));
		NativeNametags.drawGuiLogo(ctx, name, 0, 0);
	}

	private static final com.batclient.hud.BiomeLabelCache BIOME_LABEL = new com.batclient.hud.BiomeLabelCache();
	private static List<String> coordinateLines(MinecraftClient client, JsonObject mod) {
		Vec3d p = client.player == null ? new Vec3d(-16, 64, -132) : new Vec3d(client.player.x, client.player.y, client.player.z);
		boolean labels = HudConfig.bool(mod, "labels", true);
		String facing = "East -83";
		String biome = "Forest";
		if (client.player != null) {
			String direction = client.player.getHorizontalDirection().getName();
			facing = Character.toUpperCase(direction.charAt(0)) + direction.substring(1) + " " + MathHelper.floor(MathHelper.wrapDegrees(client.player.yaw));
			if (client.world != null) {
				net.minecraft.util.math.BlockPos blockPos = client.player.getBlockPos();
				Object language = "en_US";
				long tick = client.world.getTimeOfDay();
				String cached = BIOME_LABEL.find(client.world, language, tick, blockPos.getX(), blockPos.getY(), blockPos.getZ());
				if (cached != null) biome = cached;
				else {
				biome = client.world.getBiome(blockPos).name;
				BIOME_LABEL.store(client.world, language, tick, blockPos.getX(), blockPos.getY(), blockPos.getZ(), biome);
				}
			}
		} else BIOME_LABEL.clear();
		return com.batclient.platform.java8.J8.listOf((labels ? "X: " : "") + MathHelper.floor(p.x), (labels ? "Y: " : "") + MathHelper.floor(p.y),
				(labels ? "Z: " : "") + MathHelper.floor(p.z), "", facing, "Biome : " + biome);
	}

	private static void drawCoordinates(DrawContext ctx, MinecraftClient c, JsonObject mod) {
		List<String> lines = coordinateLines(c, mod);
		TextRenderer fr = c.textRenderer;
		int w = lines.stream().mapToInt(fr::getStringWidth).max().orElse(0);
		if (HudConfig.bool(mod, "background", true)) fillRounded(ctx, -PAD_H, -PAD_V, w + PAD_H * 2, lines.size() * (fr.fontHeight + 1) + PAD_V * 2, RADIUS, plate(mod));
		boolean shadow = HudConfig.bool(mod, "shadow", true);
		// Old coordinate presets colored the entire block cyan; retain cyan labels with white values.
		int valueColor = "Aqua".equalsIgnoreCase(HudConfig.str(mod, "color", "")) ? 0xFFFFFFFF : textColor(mod);
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			int split = line.indexOf(':');
			String label = split < 0 ? "" : line.substring(0, split + 1);
			int y = i * (fr.fontHeight + 1);
			ctx.drawText(fr, label, 0, y, 0xFF55FFFF, shadow);
			ctx.drawText(fr, line.substring(label.length()), fr.getStringWidth(label), y, valueColor, shadow);
		}
	}

	private static void drawLines(DrawContext ctx, MinecraftClient client, List<String> lines, JsonObject mod) {
		TextRenderer fr = client.textRenderer;
		int w = lines.stream().mapToInt(fr::getStringWidth).max().orElse(0);
		int h = lines.size() * (fr.fontHeight + 1);
		if (HudConfig.bool(mod, "background", true)) fillRounded(ctx, -PAD_H, -PAD_V, w + PAD_H * 2, h + PAD_V * 2, RADIUS, plate(mod));
		for (int i = 0; i < lines.size(); i++) ctx.drawText(fr, lines.get(i), 0, i * (fr.fontHeight + 1), textColor(mod), HudConfig.bool(mod, "shadow", true));
	}

	/** Text widget: optional plate + single line (shadow/color/bg settings). */
	private static void drawTextW(DrawContext ctx, MinecraftClient c, String text, JsonObject mod) {
		if (text == null) return;
		TextRenderer fr = c.textRenderer;
		int tw = fr.getStringWidth(text);
		int th = fr.fontHeight;
		if (HudConfig.bool(mod, "background", true)) {
			fillRounded(ctx, -PAD_H, -PAD_V, tw + PAD_H * 2, th + PAD_V * 2,
					RADIUS, plate(mod));
		}
		ctx.drawText(fr, text, 0, 0, textColor(mod), HudConfig.bool(mod, "shadow", true));
	}

	private static List<StatusEffectInstance> visibleEffects(MinecraftClient c) {
		List<StatusEffectInstance> effects = new ArrayList<>();
		for (StatusEffectInstance effect : statusEffects(c)) {
			if (ClientMods.isFakeNightVision() && effect.getEffectId() == net.minecraft.entity.effect.StatusEffect.NIGHTVISION.id) continue;
			effects.add(effect);
		}
		if (effects.isEmpty() && editorPreview) { effects.add(new StatusEffectInstance(net.minecraft.entity.effect.StatusEffect.SPEED.id, 1680, 1)); effects.add(new StatusEffectInstance(net.minecraft.entity.effect.StatusEffect.STRENGTH.id, 160)); }
		return effects;
	}

	private static String effectName(StatusEffectInstance effect) {
		String name = new net.minecraft.text.TranslatableText(effect.getTranslationKey()).asUnformattedString();
		return effect.getAmplifier() > 0 ? name + " " + roman(effect.getAmplifier() + 1) : name;
	}

	private static String effectTime(StatusEffectInstance effect, JsonObject mod) {
		if (effect.isPermanent()) return "\u221E";
		int secs = Math.max(0, effect.getDuration() / 20);
		return "Full".equals(HudConfig.str(mod, "format", "Shorthand"))
				? secs / 60 + "m " + secs % 60 + "s"
				: secs / 60 + ":" + String.format(Locale.ROOT, "%02d", secs % 60);
	}

	private static void drawPotions(DrawContext ctx, MinecraftClient c, JsonObject mod) {
		List<StatusEffectInstance> effects = visibleEffects(c);
		if (effects.isEmpty()) return;
		TextRenderer fr = c.textRenderer;
		int rowH = fr.fontHeight * 2 + 4;
		int width = effects.stream().mapToInt(e -> 22 + Math.max(fr.getStringWidth(effectName(e)), fr.getStringWidth(effectTime(e, mod)))).max().orElse(0);
		if (HudConfig.bool(mod, "background", true)) fillRounded(ctx, -PAD_H, -PAD_V, width + PAD_H * 2, effects.size() * rowH + PAD_V * 2, RADIUS, plate(mod));
		boolean shadow = HudConfig.bool(mod, "shadow", true);
		for (int i = 0; i < effects.size(); i++) {
			StatusEffectInstance effect = effects.get(i);
			int y = i * rowH;
			StatusEffect effType = StatusEffect.STATUS_EFFECTS[effect.getEffectId()];
			if (effType != null && effType.hasIcon()) { int iconIdx = effType.getIconLevel(); ctx.drawTexture(new Identifier("textures/gui/container/inventory.png"), 0, y + (rowH - 18) / 2, iconIdx % 8 * 18, 198 + iconIdx / 8 * 18, 18, 18); }
			ctx.drawText(fr, effectName(effect), 22, y, textColor(mod), shadow);
			boolean expiring=com.batclient.hud.PotionExpiry.warning(effect.getDuration(),effect.isPermanent(),HudConfig.bool(mod,"expiryWarning",true),HudConfig.setNumber(mod,"warningSeconds",10));
			int timerColor=expiring?parseColor(HudConfig.str(mod,"warningColor","#FF5555FF"),0xFFFF5555):textColor(mod);
			ctx.drawText(fr, effectTime(effect, mod), 22, y + fr.fontHeight + 1, timerColor, shadow);
		}
	}
	private static int inventoryGap(JsonObject mod) { float gap=HudConfig.setNumber(mod,"slotGap",2);return Float.isFinite(gap)?Math.round(net.minecraft.util.math.MathHelper.clamp(gap,0,6)):2; }
	private static int inventoryTitle(MinecraftClient c,JsonObject mod) { return HudConfig.bool(mod,"title",true)?c.textRenderer.fontHeight+4:0; }
	private static void drawInventory(DrawContext ctx,MinecraftClient c,JsonObject mod) {
		float[] bounds=measure(c,"inventory",mod);
		if(HudConfig.bool(mod,"background",true))fillRounded(ctx,-PAD_H,-PAD_V,(int)bounds[0]+PAD_H*2,(int)bounds[1]+PAD_V*2,RADIUS,plate(mod));
		int top=inventoryTitle(c,mod),gap=inventoryGap(mod),slots=HudConfig.bool(mod,"hotbar",false)?36:27;
		if(top>0)ctx.drawText(c.textRenderer,"Inventory",0,0,textColor(mod),HudConfig.bool(mod,"shadow",true));
		boolean empty=true; for (ItemStack _st : c.player.inventory.main) if (_st != null) { empty = false; break; }
		for(int i=0;i<slots;i++) {
			int x=(i%9)*(16+gap),y=top+(i/9)*(16+gap);
			if(HudConfig.bool(mod,"slotBackground",true))ctx.fill(x,y,x+16,y+16,0x18FFFFFF);
			ItemStack stack=c.player.inventory.main[i<27?i+9:i-27];
			if(editorPreview&&empty&&i<4)stack=com.batclient.platform.java8.J8.eval(i, _sw -> { switch (_sw) { case 0: return new ItemStack(net.minecraft.item.Items.DIAMOND_SWORD); case 1: return new ItemStack(net.minecraft.item.Items.ENDER_PEARL,16); case 2: return new ItemStack(net.minecraft.item.Item.fromBlock(net.minecraft.block.Blocks.COBBLESTONE),64); default: return new ItemStack(net.minecraft.item.Items.GOLDEN_APPLE); } });
			if(stack == null)continue;
			ctx.drawItem(stack,x,y);
			ctx.drawItemInSlot(c.textRenderer,stack,x,y,""); // Keep native durability/cooldown overlays.
			if(HudConfig.bool(mod,"counts",true)&&stack.count!=1) {
				String count=Integer.toString(stack.count);
				ctx.getMatrices().push();
				try { ctx.getMatrices().translate(0,0,200);ctx.drawText(c.textRenderer,count,x+17-c.textRenderer.getStringWidth(count),y+9,textColor(mod),HudConfig.bool(mod,"shadow",true)); }
				finally { ctx.getMatrices().pop(); }
			}
		}
	}

	private static int armorWarning(ItemStack stack, JsonObject mod) {
		if (!stack.isDamageable() || !com.batclient.hud.ArmorWarning.low(stack.getMaxDamage(), stack.getDamage(),
				HudConfig.bool(mod,"lowWarning",true), HudConfig.setNumber(mod,"warningPercent",15))) return 0;
		if (!com.batclient.hud.ArmorWarning.visible(HudConfig.bool(mod,"warningFlash",true),
				System.nanoTime()/1_000_000L)) return 0;
		return parseColor(HudConfig.str(mod,"warningColor","#FF5555FF"),0xFFFF5555);
	}

	private static void drawArmor(DrawContext ctx, MinecraftClient c, JsonObject mod) {
		TextRenderer fr = c.textRenderer;
		List<ItemStack> items = armorStacks(c);
		if (items.isEmpty()) return;
		boolean numbers = HudConfig.bool(mod, "numbers", true);
		int maxNumW = 0;
		if (numbers) {
			for (ItemStack st : items) {
				if (st.isDamageable()) {
					maxNumW = Math.max(maxNumW, fr.getStringWidth(
							Integer.toString(st.getMaxDamage() - st.getDamage())));
				}
			}
		}
		int contentW = 16 + (numbers && maxNumW > 0 ? 3 + maxNumW : 0);
		int rowH = 16;
		int h = items.size() * rowH;
		if (HudConfig.bool(mod, "background", true)) {
			fillRounded(ctx, -PAD_H, -PAD_V, contentW + PAD_H * 2, h + PAD_V * 2,
					RADIUS, plate(mod));
		}
		int col = textColor(mod);
		for (int i = 0; i < items.size(); i++) {
			ItemStack st = items.get(i);
			int ry = i * rowH;
			ctx.drawItem(st, 0, ry);
			int warning = armorWarning(st, mod);
			if (warning != 0) ctx.drawBorder(0, ry, 16, 16, warning);
			if (numbers && st.isDamageable()) {
				String n = Integer.toString(st.getMaxDamage() - st.getDamage());
				ctx.drawText(fr, n, 19, ry + (16 - fr.fontHeight) / 2, warning != 0 ? warning : col, HudConfig.bool(mod, "shadow", true));
			}
		}
	}

	private static void drawDurability(DrawContext ctx, MinecraftClient c, JsonObject mod) {
		TextRenderer fr = c.textRenderer;
		List<ItemStack> items = armorStacks(c);
		if (items.isEmpty()) return;
		boolean numbers = HudConfig.bool(mod, "numbers", true);
		int cellW = 16;
		int gap = 5;
		int contentW = items.size() * cellW + (items.size() - 1) * gap;
		int contentH = 16 + (numbers ? fr.fontHeight + 2 : 0);
		if (HudConfig.bool(mod, "background", true)) {
			fillRounded(ctx, -PAD_H, -PAD_V, contentW + PAD_H * 2, contentH + PAD_V * 2,
					RADIUS, plate(mod));
		}
		int col = textColor(mod);
		for (int i = 0; i < items.size(); i++) {
			ItemStack st = items.get(i);
			int cx = i * (cellW + gap);
			ctx.drawItem(st, cx, 0);
			int warning = armorWarning(st, mod);
			if (warning != 0) ctx.drawBorder(cx, 0, 16, 16, warning);
			if (numbers && st.isDamageable()) {
				String p = (int) (100f * (st.getMaxDamage() - st.getDamage()) / st.getMaxDamage()) + "%";
				int pw = fr.getStringWidth(p);
				ctx.drawText(fr, p, cx + (cellW - pw) / 2, 18, warning != 0 ? warning : col, HudConfig.bool(mod, "shadow", true));
			}
		}
	}

	private static void drawTarget(DrawContext ctx, MinecraftClient c, JsonObject mod) {
		com.batclient.TargetHud.Info t=TargetHud.read(c,mod,editorPreview);
		if (t == null) return;
		TextRenderer fr = c.textRenderer;
		int w=TargetHud.width(mod),h=TargetHud.height(mod);
		if (HudConfig.bool(mod, "background", true)) {
			fillRounded(ctx, -PAD_H, -PAD_V, w + PAD_H * 2, h + PAD_V * 2,
					RADIUS, plate(mod));
		}
		int col = textColor(mod);
		boolean shadow = HudConfig.bool(mod, "shadow", true);
		boolean portrait=HudConfig.bool(mod,"portrait",true);int left=portrait?30:0;
		if(portrait) {
			if(t.entity() instanceof net.minecraft.client.network.AbstractClientPlayerEntity)
				{ net.minecraft.client.network.AbstractClientPlayerEntity player = (net.minecraft.client.network.AbstractClientPlayerEntity) (Object) (t.entity()); ctx.drawTexture(player.getSkinId(),0,0,24,24,8.0f,8.0f,8,8,64,64); ctx.drawTexture(player.getSkinId(),0,0,24,24,40.0f,8.0f,8,8,64,64); }
			else {
				ctx.getMatrices().push();ctx.getMatrices().scale(1.5f,1.5f,1);ctx.drawItem(new ItemStack(net.minecraft.item.Items.IRON_SWORD),0,0);ctx.getMatrices().pop();
			}
		}
		ctx.drawText(fr,fr.trimToWidth(t.name(),w-left),left,0,col,shadow);
		String distance=HudConfig.bool(mod,"distance",true)?String.format(Locale.ROOT,"%.1fm",t.distance()):"";
		int healthWidth=w-left-(distance.isEmpty()?0:fr.getStringWidth(distance)+6);
		ctx.drawText(fr,fr.trimToWidth(TargetHud.health(t,mod),healthWidth),left,11,col,shadow);
		if(!distance.isEmpty())ctx.drawText(fr,distance,w-fr.getStringWidth(distance),11,col,shadow);
		int barColor=parseColor(HudConfig.str(mod,"barColor","#FFFFFFFF"),0xFFFFFFFF);
		if(HudConfig.bool(mod,"lowWarning",true)&&t.health()/t.maximum()<=.25f)barColor=0xFFFF5555;
		fillRounded(ctx,0,26,w,4,2,0x55444444);
		float ratio=TargetHud.bar(t,HudConfig.bool(mod,"smooth",true));
		if(ratio>0)fillRounded(ctx,0,26,Math.max(1,Math.round(w*ratio)),4,2,barColor);
		if(HudConfig.bool(mod,"armor",true)) {
			String armor="Armor: "+t.armor()+(t.absorption()>0?String.format(Locale.ROOT,"  +%.1f â™¥",t.absorption()/2):"");
			ctx.drawText(fr,fr.trimToWidth(armor,w),0,34,col,shadow);
		}
	}

	private static void drawCrosshair(DrawContext ctx, MinecraftClient c, JsonObject mod) {
		int col = textColor(mod);
		String style = HudConfig.str(mod, "style", "Cross");
		Identifier texture = com.batclient.platform.java8.J8.eval(style, _sw -> { switch (_sw) { case "Dot": return CROSSHAIR_DOT; case "Cross + Dot": return CROSSHAIR_CROSS_DOT; default: return CROSSHAIR_CROSS; } });
		ctx.draw();
		float[] oldColor = new float[]{1f, 1f, 1f, 1f};
		ctx.getMatrices().push();
		try {
			com.mojang.blaze3d.platform.GlStateManager.enableBlend();
			com.mojang.blaze3d.platform.GlStateManager.blendFunc(770, 771);
			com.mojang.blaze3d.platform.GlStateManager.color((col >>> 16 & 255) / 255f,
				(col >>> 8 & 255) / 255f, (col & 255) / 255f, (col >>> 24 & 255) / 255f);
			ctx.getMatrices().translate(-4.5f, -4.5f, 0);
			ctx.drawTexture(texture, 0, 0, 0, 0, 9, 9, 9, 9);
			ctx.draw();
		} finally {
			ctx.getMatrices().pop();
			com.mojang.blaze3d.platform.GlStateManager.color(oldColor[0], oldColor[1], oldColor[2], oldColor[3]);
		}
	}

	private static void drawBlockIndicator(DrawContext ctx, MinecraftClient client, JsonObject mod) {
		com.batclient.BlockIndicator.Info info = BlockIndicator.read(client, mod, editorPreview);
		if (info == null) return;
		net.minecraft.client.font.TextRenderer fr = client.textRenderer;
		boolean showId = HudConfig.bool(mod, "showId", true);
		int w = 30 + Math.max(fr.getStringWidth(BlockIndicator.title(info)), showId ? fr.getStringWidth(info.id()) : 0);
		int h = Math.max(24, showId ? fr.fontHeight * 2 + 4 : fr.fontHeight);
		if (HudConfig.bool(mod, "background", true)) fillRounded(ctx, -PAD_H, -PAD_V, w + 2 * PAD_H, h + 2 * PAD_V, RADIUS, plate(mod));
		if (HudConfig.bool(mod, "border", false)) {
			int thickness = 1;
			try { thickness = MathHelper.clamp(Integer.parseInt(HudConfig.str(mod, "borderThickness", "1")), 1, 4); }
			catch (NumberFormatException ignored) {}
			strokeRounded(ctx, -PAD_H, -PAD_V, w + 2 * PAD_H, h + 2 * PAD_V, RADIUS, thickness,
				parseColor(HudConfig.str(mod, "borderColor", "#FFFFFFFF"), 0xFFFFFFFF));
		}
		ctx.getMatrices().push();
		ctx.getMatrices().translate(0, (h - 24) / 2f, 0);
		ctx.getMatrices().scale(1.5f, 1.5f, 1);
		ctx.drawItem(info.icon(), 0, 0);
		ctx.getMatrices().pop();
		int textY = (h - (showId ? fr.fontHeight * 2 + 4 : fr.fontHeight)) / 2;
		boolean shadow = HudConfig.bool(mod, "shadow", true);
		ctx.drawText(fr, BlockIndicator.title(info), 30, textY, textColor(mod), shadow);
		if (showId) ctx.drawText(fr, info.id(), 30, textY + fr.fontHeight + 4,
			parseColor(HudConfig.str(mod, "idColor", "#AAAAAAFF"), 0xFFAAAAAA), shadow);
	}

	private static int roundedInset(int row, int height, int radius) {
		int edge = Math.min(row, height - row - 1);
		if (radius <= 0 || edge >= radius) return 0;
		double dy = radius - edge - .5;
		return (int)Math.ceil(radius - Math.sqrt(Math.max(0, radius * radius - dy * dy)));
	}
	/** Ring geometry leaves the centre transparent even with background disabled. */
	private static void strokeRounded(DrawContext ctx, int x, int y, int w, int h, int radius, int thickness, int color) {
		for (int row = 0; row < h; row++) {
			int outer = roundedInset(row, h, radius);
			if (row < thickness || row >= h - thickness) ctx.fill(x + outer, y + row, x + w - outer, y + row + 1, color);
			else {
				int inner = thickness + roundedInset(row - thickness, h - 2 * thickness, Math.max(0, radius - thickness));
				ctx.fill(x + outer, y + row, x + inner, y + row + 1, color);
				ctx.fill(x + w - inner, y + row, x + w - outer, y + row + 1, color);
			}
		}
	}
	private static final Identifier CROSSHAIR_CROSS = new Identifier("batclient", "textures/gui/crosshair/cross.png");
	private static final Identifier CROSSHAIR_CROSS_DOT = new Identifier("batclient", "textures/gui/crosshair/cross_dot.png");
	private static final Identifier CROSSHAIR_DOT = new Identifier("batclient", "textures/gui/crosshair/dot.png");

	/** Called only where vanilla would draw its crosshair sprite. */
	public static boolean drawCustomCrosshair(DrawContext ctx) {
		JsonObject mod = HudConfig.module(HudConfig.snapshot(), "crosshair");
		if (!HudConfig.enabled(mod)) return false;
		ctx.getMatrices().push();
		try {
			ctx.getMatrices().translate(ctx.getScaledWindowWidth() / 2f, ctx.getScaledWindowHeight() / 2f, 0);
			float scale = HudConfig.scale(mod);
			ctx.getMatrices().scale(scale, scale, 1);
			drawCrosshair(ctx, MinecraftClient.getInstance(), mod);
		} finally { ctx.getMatrices().pop(); }
		return true;
	}

	// Individual cells share the same geometry in gameplay, editor and settings previews.
	private static KeystrokesLayout keystrokesLayout(MinecraftClient c, JsonObject mod) {
		TextRenderer fr = c.textRenderer;
		int keyWidth = com.batclient.platform.java8.J8.listOf("W", "A", "S", "D").stream().mapToInt(fr::getStringWidth).max().orElse(0);
		return KeystrokesLayout.measure(keyWidth, Math.max(fr.getStringWidth("LMB"), fr.getStringWidth("RMB")),
				fr.getStringWidth(ClientMods.lmbCps() + " CPS"), fr.fontHeight, HudConfig.bool(mod, "showCps", true));
	}

	private static void drawKeystrokes(DrawContext ctx, MinecraftClient c, JsonObject mod) {
		com.batclient.hud.KeystrokesLayout layout = keystrokesLayout(c, mod);
		int key = layout.keySize(), step = key + 2;
		keyCell(ctx, c, mod, step, 0, key, key, "W", c.options.forwardKey.isPressed());
		keyCell(ctx, c, mod, 0, step, key, key, "A", c.options.leftKey.isPressed());
		keyCell(ctx, c, mod, step, step, key, key, "S", c.options.backKey.isPressed());
		keyCell(ctx, c, mod, step * 2, step, key, key, "D", c.options.rightKey.isPressed());
		keyCell(ctx, c, mod, 0, layout.spaceY(), layout.width(), 10, "", c.options.jumpKey.isPressed());
		ctx.fill(layout.width() / 2 - 11, layout.spaceY() + 4, layout.width() / 2 + 11, layout.spaceY() + 5, textColor(mod));
		keyCell(ctx, c, mod, 0, layout.mouseY(), layout.mouseWidth(), layout.mouseHeight(), "LMB", c.options.attackKey.isPressed());
		keyCell(ctx, c, mod, layout.mouseWidth() + 2, layout.mouseY(), layout.width() - layout.mouseWidth() - 2, layout.mouseHeight(), "RMB", c.options.useKey.isPressed());
		if (HudConfig.bool(mod, "showCps", true)) keyCell(ctx, c, mod, 0, layout.cpsY(), layout.width(), layout.cpsHeight(), ClientMods.lmbCps() + " CPS", false);
	}

	private static void keyCell(DrawContext ctx, MinecraftClient c, JsonObject mod,
			int x, int y, int width, int height, String label, boolean pressed) {
		boolean background = HudConfig.bool(mod, "background", true);
		if (background || pressed) {
			int color = pressed ? pressFlash(mod) : plate(mod);
			int radius = Math.min(RADIUS, Math.min(width, height) / 3);
			fillRounded(ctx, x, y, width, height, radius, color);
			if (HudConfig.bool(mod, "border", false) && background) {
				// Subtle straight rim stops before the curved corners. It never tints the center.
				ctx.fill(x + radius, y, x + width - radius, y + 1, 0x15FFFFFF);
				ctx.fill(x + radius, y + height - 1, x + width - radius, y + height, 0x15FFFFFF);
				ctx.fill(x, y + radius, x + 1, y + height - radius, 0x15FFFFFF);
				ctx.fill(x + width - 1, y + radius, x + width, y + height - radius, 0x15FFFFFF);
			}
		}
		TextRenderer fr = c.textRenderer;
		if (!label.isEmpty()) ctx.drawText(fr, label, x + (width - fr.getStringWidth(label)) / 2,
				y + (height - fr.fontHeight) / 2, textColor(mod), HudConfig.bool(mod, "shadow", true));
	}
	private static final class SidebarRow {
	    private final String name;
	    private final String score;
	    private final boolean title;
	    private SidebarRow(String name, String score, boolean title) { this.name = name; this.score = score; this.title = title; }
	    public String name() { return this.name; }
	    public String score() { return this.score; }
	    public boolean title() { return this.title; }
	}

	private static List<SidebarRow> sidebar(MinecraftClient c, JsonObject mod) {
		List<SidebarRow> rows = new ArrayList<>();
		if (c.world != null) {
			net.minecraft.scoreboard.Scoreboard board = c.world.getScoreboard();
			net.minecraft.scoreboard.ScoreboardObjective objective = board.getObjectiveForSlot(1);
			if (c.player != null && c.player.getScoreboardTeam() instanceof Team team) {
				int slot = team.getFormatting().getColorIndex();
				net.minecraft.scoreboard.ScoreboardObjective teamObjective = slot < 0 ? null : board.getObjectiveForSlot(3 + slot);
				if (teamObjective != null) objective = teamObjective;
			}
			if (objective != null) {
				if (HudConfig.bool(mod, "showTitle", true)) rows.add(new SidebarRow(objective.getDisplayName(), "", true));
				board.getAllPlayerScores(objective).stream().filter(entry -> !entry.getPlayerName().startsWith("#"))
						.sorted(java.util.Comparator.comparingInt(ScoreboardPlayerScore::getScore).reversed().thenComparing(ScoreboardPlayerScore::getPlayerName, String.CASE_INSENSITIVE_ORDER))
						.limit(15).forEach(entry -> rows.add(new SidebarRow(Team.decorateName(board.getPlayerTeam(entry.getPlayerName()), entry.getPlayerName()),
								HudConfig.bool(mod, "showScores", false) ? net.minecraft.util.Formatting.RED + Integer.toString(entry.getScore()) : "", false)));
			}
		}
		if (rows.isEmpty() && editorPreview) {
			if (HudConfig.bool(mod, "showTitle", true)) rows.add(new SidebarRow("Bat Client", "", true));
			rows.add(new SidebarRow("Player: BatPlayer", HudConfig.bool(mod, "showScores", false) ? "10" : "", false));
			rows.add(new SidebarRow("batclient.xyz", "", false));
		}
		return rows;
	}

	private static int sidebarWidth(TextRenderer fr, SidebarRow row) {
		return fr.getStringWidth(row.name()) + (row.score().isEmpty() ? 0 : fr.getStringWidth(row.score()) + 6);
	}

	private static void drawScoreboard(DrawContext ctx, MinecraftClient c, JsonObject mod) {
		java.util.List<com.batclient.HudRenderer.SidebarRow> rows = sidebar(c, mod);
		if (rows.isEmpty()) return;
		TextRenderer fr = c.textRenderer;
		int width = rows.stream().mapToInt(row -> sidebarWidth(fr, row)).max().orElse(0);
		int height = rows.size() * (fr.fontHeight + 1);
		if (HudConfig.bool(mod, "background", true)) fillRounded(ctx, -PAD_H, -PAD_V, width + PAD_H * 2, height + PAD_V * 2, RADIUS, plate(mod));
		for (int i = 0; i < rows.size(); i++) {
			com.batclient.HudRenderer.SidebarRow row = rows.get(i);
			ctx.drawText(fr, row.name(), row.title() ? (width - fr.getStringWidth(row.name())) / 2 : 0, i * (fr.fontHeight + 1), textColor(mod), HudConfig.bool(mod, "shadow", true));
			if (!row.score().isEmpty()) ctx.drawText(fr, row.score(), width - fr.getStringWidth(row.score()), i * (fr.fontHeight + 1), textColor(mod), HudConfig.bool(mod, "shadow", true));
		}
	}

	private static int bossRowHeight(TextRenderer fr, JsonObject mod) {
		return HudConfig.bool(mod, "showName", true) ? fr.fontHeight + 12 : 12;
	}

	private static void drawBossBars(DrawContext ctx, MinecraftClient c, JsonObject mod) {
		boolean active = (BossBar.name != null && BossBar.framesToLive > 0) || editorPreview;
		if (!active) return;
		String name = BossBar.name != null && BossBar.framesToLive > 0 ? BossBar.name : "Bat Client";
		float pct = BossBar.name != null && BossBar.framesToLive > 0 ? BossBar.percent : 0.8f;
		TextRenderer fr = c.textRenderer;
		boolean names = HudConfig.bool(mod, "showName", true);
		int width = Math.max(182, names ? fr.getStringWidth(name) : 0);
		int rowHeight = bossRowHeight(fr, mod);
		if (HudConfig.bool(mod, "background", true)) fillRounded(ctx, -PAD_H, -PAD_V, width + PAD_H * 2, rowHeight + PAD_V * 2, RADIUS, plate(mod));
		if (names) ctx.drawText(fr, name, (width - fr.getStringWidth(name)) / 2, 0, textColor(mod), HudConfig.bool(mod, "shadow", true));
		int bx = (width - 182) / 2, by = names ? fr.fontHeight + 3 : 2;
		ctx.drawTexture(net.minecraft.client.gui.DrawableHelper.GUI_ICONS_TEXTURE, bx, by, 0, 74, 182, 5);
		int fillW = (int) (pct * 183.0f);
		if (fillW > 0) ctx.drawTexture(net.minecraft.client.gui.DrawableHelper.GUI_ICONS_TEXTURE, bx, by, 0, 79, fillW, 5);
	}

	// ---- helpers ----

	private static int plate(JsonObject mod) {
		String color = HudConfig.str(mod, "bgColor", "");
		if ("#000000A6".equalsIgnoreCase(color) || "#08080BC7".equalsIgnoreCase(color)) return PLATE;
		return parseColor(color, PLATE);
	}

	private static int textColor(JsonObject mod) {
		return parseColor(HudConfig.str(mod, "color", ""), 0xFFFFFFFF);
	}

	private static int pressFlash(JsonObject mod) {
		return (parseColor(HudConfig.str(mod, "pressedColor", ""), 0xFFFFFFFF) & 0x00FFFFFF)
				| 0x55000000;
	}

	/** #RRGGBBAA / #RRGGBB / legacy color names -> ARGB. */
	public static int parseColor(String s, int def) {
		if (s == null || s.isEmpty()) return def;
		try {
			if (s.startsWith("#")) {
				String h = s.substring(1);
				if (h.length() == 6) return 0xFF000000 | Integer.parseInt(h, 16);
				if (h.length() == 8) {
					int r = Integer.parseInt(h.substring(0, 2), 16);
					int g = Integer.parseInt(h.substring(2, 4), 16);
					int b = Integer.parseInt(h.substring(4, 6), 16);
					int a = Integer.parseInt(h.substring(6, 8), 16);
					return (a << 24) | (r << 16) | (g << 8) | b;
				}
			}
			switch (s.toLowerCase(Locale.ROOT)) { case "white": return 0xFFFFFFFF; case "gray": case "grey": return 0xFF9A9AA8; case "green": return 0xFF2FD06F; case "yellow": return 0xFFFFD75A; case "aqua": case "cyan": return 0xFF5AD7FF; case "black": return 0xFF000000; case "red": return 0xFFE5484D; case "blue": return 0xFF4DA3FF; default: return def; }
		} catch (Throwable t) {
			return def;
		}
	}

	/** One filtered corner mask, reused at every size. Nine-slicing avoids per-frame rasterization. */
	static void fillRounded(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
		if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
		r = Math.min(r, Math.min(w, h) / 3);
		if (r <= 0) { ctx.fill(x, y, x + w, y + h, color); return; }
		if (roundedMask == null) {
			NativeImageBackedTexture texture = new NativeImageBackedTexture(64, 64);
			int[] pixels = texture.getPixels();
			for (int py = 0; py < 64; py++) for (int px = 0; px < 64; px++) {
				double distance = Math.hypot(px + .5 - 32, py + .5 - 32);
				int alpha = (int) Math.round(255 * MathHelper.clamp(32.5 - distance, 0, 1));
				pixels[py * 64 + px] = (alpha << 24) | 0xFFFFFF;
			}
			texture.upload();
			roundedMask = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("bat_hud_rounding", texture);
		}
		ctx.fill(x + r, y, x + w - r, y + h, color);
		ctx.fill(x, y + r, x + r, y + h - r, color);
		ctx.fill(x + w - r, y + r, x + w, y + h - r, color);
		ctx.setShaderColor(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, (color >>> 24) / 255f);
		GlStateManager.enableBlend();
		GlStateManager.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
		try {
			ctx.drawTexture(roundedMask, x, y, r, r, 0f, 0f, 32, 32, 64, 64);
			ctx.drawTexture(roundedMask, x + w - r, y, r, r, 32f, 0f, 32, 32, 64, 64);
			ctx.drawTexture(roundedMask, x, y + h - r, r, r, 0f, 32f, 32, 32, 64, 64);
			ctx.drawTexture(roundedMask, x + w - r, y + h - r, r, r, 32f, 32f, 32, 32, 64, 64);
		} finally {
			GlStateManager.disableBlend();
			ctx.setShaderColor(1f, 1f, 1f, 1f);
		}
	}
	private static String coords(MinecraftClient c, JsonObject mod) {
		if (c.player == null) return HudConfig.bool(mod, "labels", true) ? "X: 125  Y: 64  Z: -340" : "125  64  -340";
		Vec3d p = c.player.getPos();
		String xs = Integer.toString(MathHelper.floor(p.x));
		String ys = Integer.toString(MathHelper.floor(p.y));
		String zs = Integer.toString(MathHelper.floor(p.z));
		return HudConfig.bool(mod, "labels", true)
				? "X: " + xs + "  Y: " + ys + "  Z: " + zs
				: xs + " " + ys + " " + zs;
	}

	private static String ping(MinecraftClient c) {
		if (c.isInSingleplayer()) return "0 ms";
		if (c.getNetworkHandler() == null || c.player == null) return null;
		net.minecraft.client.network.PlayerListEntry entry = c.getNetworkHandler().getPlayerListEntry(c.player.getUuid());
		if (entry == null) return null;
		int latency = entry.getLatency();
		if (latency < 0 || latency > 30000) return "0 ms";
		return latency + " ms";
	}

	private static JsonObject target(MinecraftClient c) {
		if (c.result == null || !(c.result.entity instanceof LivingEntity)) {
			if (!editorPreview) return null;
			JsonObject example = new JsonObject();
			example.addProperty("name", "Target"); example.addProperty("hp", "20.0 / 20.0");
			return example;
		}
		LivingEntity living = (LivingEntity) c.result.entity;
		JsonObject t = new JsonObject();
		t.addProperty("name", living.getName().asUnformattedString());
		t.addProperty("hp", String.format(Locale.ROOT, "%.1f / %.1f",
				living.getHealth(), living.getMaxHealth()));
		return t;
	}

	private static String roman(int n) {
		switch (Math.min(n, 10)) { case 2: return "II"; case 3: return "III"; case 4: return "IV"; case 5: return "V"; case 6: return "VI"; case 7: return "VII"; case 8: return "VIII"; case 9: return "IX"; case 10: return "X"; default: return "I"; }
	}

	private static java.util.Collection<StatusEffectInstance> statusEffects(MinecraftClient c) {
		return c.player == null ? com.batclient.platform.java8.J8.listOf() : c.player.getStatusEffectInstances();
	}

	private static List<ItemStack> armorStacks(MinecraftClient c) {
		List<ItemStack> items = new ArrayList<>();
		if (c.player != null) for (int slot : ARMOR_SLOTS) {
			ItemStack st = c.player.inventory.armor[slot];
			if (st != null) items.add(st);
		}
		if (items.isEmpty() && editorPreview) {
			items.add(new ItemStack(net.minecraft.item.Items.DIAMOND_HELMET));
			items.add(new ItemStack(net.minecraft.item.Items.DIAMOND_CHESTPLATE));
			items.add(new ItemStack(net.minecraft.item.Items.DIAMOND_LEGGINGS));
			items.add(new ItemStack(net.minecraft.item.Items.DIAMOND_BOOTS));
		}
		return items;
	}
}
