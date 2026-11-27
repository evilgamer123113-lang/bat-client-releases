package com.batclient.hud;

/** Shared unscaled content bounds: hidden elements do not reserve space. */
public record ResourcePackCardLayout(int width,int height,int iconX,int iconY,int textX,int textY) {
    public static ResourcePackCardLayout resolve(int textWidth,int textHeight,int iconSize,int gap,String position) {
        int spacing=iconSize>0&&textHeight>0?gap:0;
        if ("Above".equals(position)) {
            int width=Math.max(iconSize,textWidth);
            return new ResourcePackCardLayout(width,iconSize+spacing+textHeight,(width-iconSize)/2,0,(width-textWidth)/2,iconSize+spacing);
        }
        int width=iconSize+spacing+textWidth,height=Math.max(iconSize,textHeight);
        boolean right="Right".equals(position);
        return new ResourcePackCardLayout(width,height,right?textWidth+spacing:0,(height-iconSize)/2,right?0:iconSize+spacing,(height-textHeight)/2);
    }
}
