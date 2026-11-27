package com.batclient;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Vanilla Game Menu plus a Feather-style top-right "Bat Client Screen" button
 * that returns to the Bat pause page (mirrors Feather's "Feather Pause Screen"
 * button on their original-pause screenshot).
 *
 * <p>No mixins: the vanilla GameMenuScreen is composed as a <b>delegate</b> —
 * we build its widgets at init, render it first, forward every input event to
 * it, and stamp our own button on top. Any button inside the delegate that
 * swaps screens (Options, Advancements, Back to Game...) works unchanged;
 * when vanilla returns to its own menu the tick handler re-wraps it.</p>
 */
public class OriginalPauseScreen extends Screen {
	private final GameMenuScreen delegate;

	public OriginalPauseScreen(GameMenuScreen delegate) {
		super(Text.literal("Game Menu"));
		this.delegate = delegate;
	}

	@Override
	protected void init() {
		// Build the vanilla menu against our size first...
		delegate.init(client, width, height);
		// ...then add the Bat Client button on top (vanilla-style grey button,
		// top-right, exactly where Feather puts theirs).
		addDrawableChild(ButtonWidget.builder(Text.literal("Bat Client Screen"), b -> {
			BatClientMod.setPreferOriginalPause(false);
			BatClientMod.showPauseScreen(client);
		}).dimensions(width - 170, 6, 164, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// Vanilla menu first (its own renderBackground + its widgets)...
		delegate.render(context, mouseX, mouseY, delta);
		// ...then ours. super.render = renderBackground (overridden no-op below)
		// + our drawables (the Bat Client button).
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// No-op: delegate.render() already drew the vanilla pause background
		// (blur + dirt). Drawing it again here would paint over the menu.
	}

	// ---- input forwarding: our widgets first, then the vanilla menu ----

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		delegate.mouseMoved(mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) return true;
		return delegate.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (super.mouseReleased(mouseX, mouseY, button)) return true;
		return delegate.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
		return delegate.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
		return delegate.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			// ESC closes the original pause back to the world (vanilla behavior);
			// the tick handler then resets preferOriginalPause, so the NEXT ESC
			// opens the Bat pause screen again.
			this.close();
			return true;
		}
		if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
		return delegate.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (super.keyReleased(keyCode, scanCode, modifiers)) return true;
		return delegate.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (super.charTyped(chr, modifiers)) return true;
		return delegate.charTyped(chr, modifiers);
	}
}
