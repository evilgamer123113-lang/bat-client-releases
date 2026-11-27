package com.batclient.hud;

/** Layout uses measured Minecraft glyph widths, including resource-pack fonts. */
public record KeystrokesLayout(int keySize, int width, int height, int spaceY,
                              int mouseY, int mouseWidth, int mouseHeight, int cpsY, int cpsHeight) {
    public static KeystrokesLayout measure(int keyTextWidth, int mouseTextWidth,
                                          int cpsTextWidth, int fontHeight, boolean showCps) {
        int requiredWidth = Math.max(2 * (mouseTextWidth + 10) + 2, showCps ? cpsTextWidth + 12 : 0);
        int key = Math.max(Math.max(20, fontHeight + 10), Math.max(keyTextWidth + 10, (requiredWidth - 4 + 2) / 3));
        int width = key * 3 + 4;
        int spaceY = 2 * (key + 2);
        int mouseY = spaceY + 12;
        int mouseHeight = Math.max(20, fontHeight + 10);
        int cpsY = mouseY + mouseHeight + 2;
        int cpsHeight = Math.max(18, fontHeight + 8);
        return new KeystrokesLayout(key, width, showCps ? cpsY + cpsHeight : mouseY + mouseHeight,
                spaceY, mouseY, (width - 2) / 2, mouseHeight, cpsY, cpsHeight);
    }
}
