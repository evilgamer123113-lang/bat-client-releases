package com.batclient;

import com.batclient.platform.mc189.GLFW;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;

/** Vanilla Game Menu plus a top-right "Bat Client Screen" button on 1.8 / 1.8.9. */
public class OriginalPauseScreen extends Screen {
    private static final int BAT_BUTTON_ID = 9001;
    private final GameMenuScreen delegate;

    public OriginalPauseScreen(GameMenuScreen delegate) {
        this.delegate = delegate;
    }

    @Override
    public void init() {
        delegate.init(client, width, height);
        buttons.add(new ButtonWidget(BAT_BUTTON_ID, width - 170, 6, 164, 20, "Bat Client Screen"));
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        delegate.render(mouseX, mouseY, delta);
        super.render(mouseX, mouseY, delta);
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == BAT_BUTTON_ID) {
            BatClientMod.setPreferOriginalPause(false);
            BatClientMod.showPauseScreen(client);
            return;
        }
        try {
            java.lang.reflect.Method m = Screen.class.getDeclaredMethod("buttonClicked", ButtonWidget.class);
            m.setAccessible(true);
            m.invoke(delegate, button);
        } catch (ReflectiveOperationException ignored) {}
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        try {
            java.lang.reflect.Method m = Screen.class.getDeclaredMethod("mouseClicked", int.class, int.class, int.class);
            m.setAccessible(true);
            m.invoke(delegate, mouseX, mouseY, button);
        } catch (ReflectiveOperationException ignored) {}
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        super.mouseReleased(mouseX, mouseY, button);
        try {
            java.lang.reflect.Method m = Screen.class.getDeclaredMethod("mouseReleased", int.class, int.class, int.class);
            m.setAccessible(true);
            m.invoke(delegate, mouseX, mouseY, button);
        } catch (ReflectiveOperationException ignored) {}
    }

    @Override
    protected void keyPressed(char id, int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            client.setScreen(null);
            return;
        }
        try {
            java.lang.reflect.Method m = Screen.class.getDeclaredMethod("keyPressed", char.class, int.class);
            m.setAccessible(true);
            m.invoke(delegate, id, keyCode);
        } catch (ReflectiveOperationException ignored) {}
    }

    @Override
    public void tick() {
        delegate.tick();
    }

    @Override
    public boolean shouldPauseGame() {
        return delegate.shouldPauseGame();
    }
}
