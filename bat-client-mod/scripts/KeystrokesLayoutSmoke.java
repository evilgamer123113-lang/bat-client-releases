import com.batclient.hud.KeystrokesLayout;

class KeystrokesLayoutSmoke {
    public static void main(String[] args) {
        for (int[] font : new int[][] {{6, 18, 30, 9}, {24, 78, 108, 22}, {7, 21, 240, 10}}) {
            for (boolean cps : new boolean[] {false, true}) {
                var layout = KeystrokesLayout.measure(font[0], font[1], font[2], font[3], cps);
                require(layout.keySize() >= font[0] + 10, "Key horizontal padding");
                require(layout.keySize() >= font[3] + 10, "Key vertical padding");
                require(layout.mouseWidth() >= font[1] + 10, "Mouse horizontal padding");
                require(layout.mouseHeight() >= font[3] + 10, "Mouse vertical padding");
                require(layout.spaceY() > layout.keySize() * 2, "Space below ASD");
                require(layout.mouseY() >= layout.spaceY() + 12, "Mouse below space");
                if (cps) {
                    require(layout.width() >= font[2] + 12, "CPS horizontal padding");
                    require(layout.cpsHeight() >= font[3] + 8, "CPS vertical padding");
                    require(layout.height() == layout.cpsY() + layout.cpsHeight(), "CPS bounds");
                } else require(layout.height() == layout.mouseY() + layout.mouseHeight(), "No hidden CPS gap");
            }
        }
        System.out.println("KEYSTROKES_LAYOUT_OK");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
