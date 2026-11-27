package com.batclient.web;

import io.github.nilsen84.ultralight.UltralightClipboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.nio.ByteBuffer;

/**
 * Bridges Ultralight clipboard ops to the GLFW window clipboard
 * (mirrors the game's own Ctrl+C/V source/target window).
 */
public class BatWebClipboard implements UltralightClipboard {
	@Override
	public void clear() {
		write("");
	}

	@Override
	public String read() {
		long window = windowHandle();
		if (window == 0L) {
			return "";
		}
		String clip = GLFW.glfwGetClipboardString(window);
		return clip != null ? clip : "";
	}

	@Override
	public void write(String text) {
		long window = windowHandle();
		if (window == 0L) {
			return;
		}
		GLFW.glfwSetClipboardString(window, text != null ? text : "");
	}

	private static long windowHandle() {
		try {
			return MinecraftClient.getInstance().getWindow().getHandle();
		} catch (Exception e) {
			return 0L;
		}
	}
}