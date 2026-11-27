package com.batclient.platform.mc189;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** LWJGL 2 compatibility constants and helpers for GLFW references in 1.8 / 1.8.9. */
public final class GLFW {
    private GLFW() {}

    public static final int GLFW_KEY_ESCAPE = Keyboard.KEY_ESCAPE;
    public static final int GLFW_KEY_BACKSPACE = Keyboard.KEY_BACK;
    public static final int GLFW_KEY_DELETE = Keyboard.KEY_DELETE;
    public static final int GLFW_KEY_ENTER = Keyboard.KEY_RETURN;
    public static final int GLFW_KEY_TAB = Keyboard.KEY_TAB;
    public static final int GLFW_KEY_LEFT = Keyboard.KEY_LEFT;
    public static final int GLFW_KEY_RIGHT = Keyboard.KEY_RIGHT;
    public static final int GLFW_KEY_UP = Keyboard.KEY_UP;
    public static final int GLFW_KEY_DOWN = Keyboard.KEY_DOWN;
    public static final int GLFW_KEY_HOME = Keyboard.KEY_HOME;
    public static final int GLFW_KEY_END = Keyboard.KEY_END;
    public static final int GLFW_KEY_PAGE_UP = Keyboard.KEY_PRIOR;
    public static final int GLFW_KEY_PAGE_DOWN = Keyboard.KEY_NEXT;
    public static final int GLFW_KEY_LEFT_ALT = Keyboard.KEY_LMENU;
    public static final int GLFW_KEY_RIGHT_SHIFT = Keyboard.KEY_RSHIFT;
    public static final int GLFW_KEY_C = Keyboard.KEY_C;
    public static final int GLFW_KEY_M = Keyboard.KEY_M;
    public static final int GLFW_KEY_N = Keyboard.KEY_N;
    public static final int GLFW_KEY_P = Keyboard.KEY_P;

    public static final int GLFW_MOD_SHIFT = 1;
    public static final int GLFW_MOD_CONTROL = 2;
    public static final int GLFW_MOD_ALT = 4;
    public static final int GLFW_MOD_SUPER = 8;

    public static final int GLFW_MOUSE_BUTTON_LEFT = 0;
    public static final int GLFW_MOUSE_BUTTON_RIGHT = 1;
    public static final int GLFW_MOUSE_BUTTON_MIDDLE = 2;

    public static final int GLFW_RELEASE = 0;
    public static final int GLFW_PRESS = 1;

    public static int glfwGetMouseButton(long window, int button) {
        return Mouse.isButtonDown(button) ? GLFW_PRESS : GLFW_RELEASE;
    }

    public static String glfwGetClipboardString(long window) {
        try {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            return data != null ? data.toString() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static void glfwSetClipboardString(long window, CharSequence value) {
        try {
            StringSelection sel = new StringSelection(value != null ? value.toString() : "");
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
        } catch (Throwable ignored) {}
    }
}
