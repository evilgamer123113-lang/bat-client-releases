package com.batclient.hud;

import java.util.function.IntBinaryOperator;

/** Immutable512-byte alpha occupancy; never retains skin colors, images or GPU state. */
public final class SkinAlphaMask {
    private final long[] rows=new long[64];
    public SkinAlphaMask(IntBinaryOperator pixels) {
        for(int y=0;y<64;y++)for(int x=0;x<64;x++)
            if((pixels.applyAsInt(x,y)>>>24)>=128)rows[y]|=1L<<x;
    }
    public boolean opaque(int x,int y) {
        return x>=0&&x<64&&y>=0&&y<64&&(rows[y]&(1L<<x))!=0;
    }
    public boolean sameOpacity(SkinAlphaMask other){return other!=null&&java.util.Arrays.equals(rows,other.rows);}
}
