package com.batclient;
/** Vanilla overlay alpha is the remaining base color, so intensity is inverted. */
public final class HitColor {
    private HitColor() { }
    public static int overlayPixel(){
        var mod=VisualModules.settings("hitcolor");int argb=HudRenderer.parseColor(HudConfig.str(mod,"color","#FF0000FF"),0xFFFF0000);
        float strength=VisualModules.number(mod,"strength",.35f,0,1)*((argb>>>24)/255f);
        int alpha=Math.round(255*(1-strength));
        return (alpha<<24)|((argb&255)<<16)|(argb&0xFF00)|((argb>>>16)&255);
    }
}
