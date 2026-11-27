import com.batclient.HudConfig;
import com.batclient.MotionBlur;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.lwjgl.opengl.GL;
import java.nio.ByteBuffer;
import java.lang.reflect.Field;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Tests actual effect source against a hidden native OpenGL context; no Minecraft session modified. */
public class MotionBlurGpuSmoke {
    static int checks;
    static void check(boolean ok, String text) { if (!ok) throw new AssertionError(text); checks++; }
    static void frame() throws Exception {
        Field f = MotionBlur.class.getDeclaredField("lastFrame"); f.setAccessible(true);
        f.setLong(null, System.nanoTime()-16_666_667L); MotionBlur.render();
    }
    static void clear(float r,float g,float b) { glDisable(GL_SCISSOR_TEST); glClearColor(r,g,b,1); glClear(GL_COLOR_BUFFER_BIT); }
    static int[] pixel() {
        ByteBuffer b=org.lwjgl.BufferUtils.createByteBuffer(4); glReadPixels(16,16,1,1,GL_RGBA,GL_UNSIGNED_BYTE,b);
        return new int[]{b.get(0)&255,b.get(1)&255,b.get(2)&255};
    }
    public static void main(String[] args) throws Exception {
        check(glfwInit(),"GLFW init"); glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,2);
        glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(32,32,"Bat motion blur GPU test",0,0);
        check(window!=0,"hidden GL context"); glfwMakeContextCurrent(window); GL.createCapabilities();
        try {
            MinecraftClient client=MinecraftClient.getInstance();
            int color=glGenTextures(); glBindTexture(GL_TEXTURE_2D,color);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,32,32,0,GL_RGBA,GL_UNSIGNED_BYTE,0L);
            int fbo=glGenFramebuffers(); glBindFramebuffer(GL_FRAMEBUFFER,fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,color,0);
            check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"target complete"); client.target.fbo=fbo;
            HudConfig.update("{\"modules\":{\"motionblur\":{\"en\":true,\"set\":{\"strength\":40}}}}");
            clear(1,0,0); frame(); check(pixel()[0]>250,"first frame seeds without stale image");
            clear(0,0,1);
            glEnable(GL_SCISSOR_TEST); glScissor(0,0,1,1); glEnable(GL_DEPTH_TEST); glEnable(GL_CULL_FACE);
            glDisable(GL_BLEND); glViewport(2,3,20,21);
            frame(); int[] mixed=pixel();
            check(mixed[0]>90&&mixed[0]<115&&mixed[2]>140&&mixed[2]<165,"history blends red/blue at configured strength");
            check(glIsEnabled(GL_SCISSOR_TEST)&&glIsEnabled(GL_DEPTH_TEST)&&glIsEnabled(GL_CULL_FACE)&&!glIsEnabled(GL_BLEND),"GL enable state restored");
            int[] viewport=new int[4];glGetIntegerv(GL_VIEWPORT,viewport);
            check(viewport[0]==2&&viewport[1]==3&&viewport[2]==20&&viewport[3]==21,"viewport restored");
            check(glGetInteger(GL_CURRENT_PROGRAM)==0&&glGetInteger(GL_VERTEX_ARRAY_BINDING)==0,"shader/VAO restored");
            check(glGetInteger(GL_TEXTURE_BINDING_2D)==color&&glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)==fbo,"texture/framebuffer restored");
            client.currentScreen=new Object(); MotionBlur.prepare(); client.currentScreen=null;
            clear(0,1,0);frame();check(pixel()[1]>250&&pixel()[0]<2,"menu resets history");
            client.world=new ClientWorld();clear(0,0,1);frame();check(pixel()[2]>250&&pixel()[1]<2,"world change resets history");
            HudConfig.updateModule("motionblur","{\"en\":false}",true);MotionBlur.prepare();clear(1,1,0);frame();
            check(pixel()[0]>250&&pixel()[1]>250,"disabled effect passes through");
            check(Math.abs(Math.pow(MotionBlur.historyWeight(40,1.0/120),2)-MotionBlur.historyWeight(40,1.0/60))<.00001,"frame-rate independent decay");
            check(MotionBlur.historyWeight(Float.NaN,.016)==0&&MotionBlur.historyWeight(40,1)==0&&MotionBlur.historyWeight(-10,.016)==0,"invalid/zero strength and long gap guarded");
            MotionBlur.close();check(glGetError()==GL_NO_ERROR,"no OpenGL errors");
            glDeleteTextures(color);glDeleteFramebuffers(fbo);
            System.out.println("MOTION_BLUR_GPU_OK: "+checks+" native GPU blending, state isolation, resets and timing checks");
        } finally { MotionBlur.close();glfwDestroyWindow(window);glfwTerminate(); }
    }
}
