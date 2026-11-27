package com.batclient.platform.mc1215;

import com.batclient.hud.SkinAlphaMask;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

/** Fallback for textures without retained CPU pixels; preserves the game's pack state. */
public final class SkinReadback {
    private SkinReadback(){}
    public static SkinAlphaMask readBound64(){
        int pbo=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int alignment=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        int row=GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        int rows=GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
        int pixels=GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        var bytes=MemoryUtil.memAlloc(64*64*4);
        try{
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,0);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);
            // SkinAlphaMask copies opacity immediately; it never retains this native buffer.
            return new SkinAlphaMask((x,y)->(bytes.get((y*64+x)*4+3)&255)<<24);
        }finally{
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pbo);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,alignment);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,row);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,rows);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,pixels);
            MemoryUtil.memFree(bytes);
        }
    }
}
