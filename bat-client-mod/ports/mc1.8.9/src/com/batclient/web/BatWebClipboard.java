package com.batclient.web;

import com.batclient.platform.mc189.GLFW;
import io.github.nilsen84.ultralight.UltralightClipboard;

/** Bridges Ultralight clipboard ops to the system clipboard on 1.8 / 1.8.9. */
public class BatWebClipboard implements UltralightClipboard {
    @Override
    public void clear() {
        write("");
    }

    @Override
    public String read() {
        String clip = GLFW.glfwGetClipboardString(0L);
        return clip != null ? clip : "";
    }

    @Override
    public void write(String text) {
        GLFW.glfwSetClipboardString(0L, text != null ? text : "");
    }
}
