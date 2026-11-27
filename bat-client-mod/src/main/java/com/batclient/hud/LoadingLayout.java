package com.batclient.hud;

/** Keeps loading branding compact in physical pixels, independent of GUI scaling. */
public record LoadingLayout(int left,int top,int width,int height,int barLeft,int barTop,int barWidth,int barHeight) {
    public static LoadingLayout of(int screenWidth,int screenHeight,double scale,int textureWidth,int textureHeight){
        double guiScale=Double.isFinite(scale)?Math.max(1,scale):1;
        int width=Math.max(1,(int)Math.floor(Math.min(screenWidth*.36,480/guiScale)));
        int height=Math.max(1,Math.round((float)width*textureHeight/Math.max(1,textureWidth)));
        int left=(screenWidth-width)/2,top=(screenHeight-height)/2;
        int barWidth=Math.max(1,(int)(width*.8)),barHeight=Math.max(1,(int)Math.round(3/guiScale));
        int barTop=Math.min(screenHeight-barHeight,top+height+Math.max(2,(int)Math.round(16/guiScale)));
        return new LoadingLayout(left,top,width,height,(screenWidth-barWidth)/2,barTop,barWidth,barHeight);
    }
}
