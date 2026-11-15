import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import com.batclient.cosmetics.CapeAnimator;

/** Uses the exact bundled agent, no Minecraft class stubs or user files. */
class NativeCapeContract {
    public static final class Texture {
        final int[] pixels = new int[1]; int uploads;
        public int[] pixels() { return pixels; }
        public void upload() { uploads++; }
    }
    static void field(String name,Object value)throws Exception {
        Field f=CapeAnimator.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);
    }
    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[0]);Path stage=Files.createDirectory(root.resolve("stage"));
        Files.write(stage.resolve("cape-batuser-frame-1.png"),new byte[]{2});
        Files.write(stage.resolve("cape-batuser-frame-0.png"),new byte[]{1});
        Files.writeString(stage.resolve("cape-batuser.json"),"[80,140]");
        System.setProperty("batclient.cape.directory",stage.toString());
        System.setProperty("batclient.cape.player","BatUser");CapeAnimator.init(null);
        Method frames=CapeAnimator.class.getDeclaredMethod("findFrames",String.class);frames.setAccessible(true);
        Method delays=CapeAnimator.class.getDeclaredMethod("loadDelays",String.class,int.class);delays.setAccessible(true);
        File[] selected=(File[])frames.invoke(null,"BATUSER");
        if(selected.length!=2||!selected[0].getName().endsWith("frame-0.png")||
           !Arrays.equals((long[])delays.invoke(null,"BATUSER",2),new long[]{80,140})||frames.invoke(null,"OtherUser")!=null)throw new AssertionError("Staged animation contract");
        // Execute the actual agent's private animation clock and pixel upload path.
        field("nativeBand",false);field("intArrGetter",Texture.class.getMethod("pixels"));field("upload",Texture.class.getMethod("upload"));
        BufferedImage red=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);red.setRGB(0,0,0xffff0000);
        BufferedImage blue=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);blue.setRGB(0,0,0xff0000ff);
        Texture texture=new Texture();Class<?> type=Class.forName("com.batclient.cosmetics.CapeAnimator$AnimatedCape");
        Constructor<?> ctor=type.getDeclaredConstructor(Object.class,Object.class,List.class,long[].class);ctor.setAccessible(true);
        Object animation=ctor.newInstance(new Object(),texture,List.of(red,blue),new long[]{80,140});
        Field next=type.getDeclaredField("next");next.setAccessible(true);next.setLong(animation,100);
        Method tick=type.getDeclaredMethod("tick",long.class);tick.setAccessible(true);
        tick.invoke(animation,100L);if(texture.pixels[0]!=0xff0000ff||texture.uploads!=1||next.getLong(animation)!=240)throw new AssertionError("Blue frame/timing");
        tick.invoke(animation,200L);if(texture.uploads!=1)throw new AssertionError("Frame advanced early");
        tick.invoke(animation,240L);if(texture.pixels[0]!=0xffff0000||texture.uploads!=2)throw new AssertionError("Animation did not loop");
        System.out.println("BUNDLED_AGENT_CAPE_STAGING_TIMING_PIXEL_UPLOAD_PASS (not in-game rendering certification)");
    }
}
