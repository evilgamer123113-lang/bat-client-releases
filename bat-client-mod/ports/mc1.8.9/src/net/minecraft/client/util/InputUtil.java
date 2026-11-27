package net.minecraft.client.util;

import java.util.Locale;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Minimal InputUtil shim for 1.8 / 1.8.9 backed by LWJGL 2 Keyboard and Mouse. */
public final class InputUtil {
    public enum Type {
        KEYSYM,
        MOUSE;

        public Key createFromCode(int code) {
            return new Key(this, code);
        }
    }

    public static final class Key {
        private final Type category;
        private final int code;

        public Key(Type category, int code) {
            this.category = category;
            this.code = code;
        }

        public Type getCategory() {
            return category;
        }

        public int getCode() {
            return code;
        }

        public String getTranslationKey() {
            if (category == Type.MOUSE) {
                if (code == 0) return "key.mouse.left";
                if (code == 1) return "key.mouse.right";
                if (code == 2) return "key.mouse.middle";
                return "key.mouse." + (code + 1);
            }
            if (code <= 0) return "key.keyboard.unknown";
            String name = Keyboard.getKeyName(code);
            if (name == null || name.isEmpty()) return "key.keyboard.unknown";
            return "key.keyboard." + name.toLowerCase(Locale.ROOT).replace(' ', '.');
        }
    }

    private InputUtil() {}

    public static Key fromKeyCode(int keyCode, int scanCode) {
        return new Key(Type.KEYSYM, keyCode);
    }

    public static Key fromTranslationKey(String key) {
        if (key == null || key.isEmpty() || "key.keyboard.unknown".equals(key)) {
            return new Key(Type.KEYSYM, 0);
        }
        if (key.startsWith("key.mouse.")) {
            String suffix = key.substring("key.mouse.".length());
            if ("left".equals(suffix)) return new Key(Type.MOUSE, 0);
            if ("right".equals(suffix)) return new Key(Type.MOUSE, 1);
            if ("middle".equals(suffix)) return new Key(Type.MOUSE, 2);
            try {
                return new Key(Type.MOUSE, Math.max(0, Integer.parseInt(suffix) - 1));
            } catch (NumberFormatException e) {
                return new Key(Type.MOUSE, 0);
            }
        }
        if (key.startsWith("key.keyboard.")) {
            String suffix = key.substring("key.keyboard.".length()).toUpperCase(Locale.ROOT).replace('.', ' ');
            if ("LEFT ALT".equals(suffix) || "LEFT.ALT".equals(suffix)) return new Key(Type.KEYSYM, Keyboard.KEY_LMENU);
            if ("RIGHT ALT".equals(suffix)) return new Key(Type.KEYSYM, Keyboard.KEY_RMENU);
            if ("LEFT SHIFT".equals(suffix)) return new Key(Type.KEYSYM, Keyboard.KEY_LSHIFT);
            if ("RIGHT SHIFT".equals(suffix)) return new Key(Type.KEYSYM, Keyboard.KEY_RSHIFT);
            if ("LEFT CONTROL".equals(suffix)) return new Key(Type.KEYSYM, Keyboard.KEY_LCONTROL);
            if ("RIGHT CONTROL".equals(suffix)) return new Key(Type.KEYSYM, Keyboard.KEY_RCONTROL);
            if ("ENTER".equals(suffix)) return new Key(Type.KEYSYM, Keyboard.KEY_RETURN);
            if ("BACKSPACE".equals(suffix)) return new Key(Type.KEYSYM, Keyboard.KEY_BACK);
            if ("PAGE UP".equals(suffix)) return new Key(Type.KEYSYM, Keyboard.KEY_PRIOR);
            if ("PAGE DOWN".equals(suffix)) return new Key(Type.KEYSYM, Keyboard.KEY_NEXT);
            int code = Keyboard.getKeyIndex(suffix.replace(' ', '_'));
            if (code == Keyboard.KEY_NONE) code = Keyboard.getKeyIndex(suffix);
            return new Key(Type.KEYSYM, code);
        }
        throw new IllegalArgumentException("Unknown key: " + key);
    }

    public static boolean isKeyPressed(long windowHandle, int code) {
        return code > 0 && Keyboard.isKeyDown(code);
    }
}
