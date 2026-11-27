import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.*;
import net.minecraft.util.Identifier;
import com.google.gson.JsonObject;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Test-only actual GL uploads and protected mesh lifecycle, never loaded by player builds. */
public final class CustomRenderingNativeChecks {
    private static List<String> mapping;
    private static long started;
    private static Object first;
    private static NativeImageBackedTexture texture;
    private static final Identifier ID=Identifier.of("batclient","render_checks");
    private static Class<?> layers;
    private static Method mesh;
    private static int checks;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static Object beforeReload;
    private static final Identifier DEFAULT_SKIN=Identifier.ofVanilla("textures/entity/player/wide/steve.png");
    private static Class<?> type(String name)throws Exception{
        if(mapping==null)mapping=Files.readAllLines(Path.of(System.getProperty("bat.bench.mapping")));
        String line=mapping.stream().filter(s->s.startsWith(name+" -> ")).findFirst().orElseThrow();
        return Class.forName(line.substring(line.indexOf(" -> ")+4,line.length()-1));
    }
    private static Method method(String owner,String name,Class<?>...params)throws Exception{
        boolean inside=false;
        for(String line:mapping){
            if(!line.startsWith(" ")){inside=line.startsWith(owner+" -> ");continue;}
            if(inside&&line.contains(" "+name+"(")){
                try{var method=type(owner).getDeclaredMethod(line.substring(line.lastIndexOf(" -> ")+4),params);method.setAccessible(true);return method;}catch(NoSuchMethodException ignored){}
            }
        }
        throw new NoSuchMethodException(owner+"."+name);
    }
    private static void check(boolean value,String detail){if(!value)throw new AssertionError(detail);checks++;}
    private static int hatQuads(Object mesh)throws Exception{
        var accessor=Arrays.stream(mesh.getClass().getDeclaredMethods()).filter(m->m.getReturnType()==List.class&&m.getParameterCount()==0).findFirst().orElseThrow();accessor.setAccessible(true);
        return ((List<?>)((List<?>)accessor.invoke(mesh)).get(0)).size();
    }
    private static Object mesh(MinecraftClient client)throws Exception{return mesh.invoke(null,client,ID,false);}
    public static boolean tick(MinecraftClient client)throws Exception{
        if(started==0){
            String owner="com.batclient.SkinLayers3D";layers=type(owner);
            Class<?> cfg=type("com.batclient.HudConfig");JsonObject root=(JsonObject)method("com.batclient.HudConfig","snapshot").invoke(null);
            JsonObject module=new JsonObject();module.addProperty("en",true);root.getAsJsonObject("modules").add("skinlayers3d",module);
            method(owner,"clear").invoke(null);
            var pixels=new NativeImage(64,64,false);pixels.fillRect(0,0,64,64,0);pixels.setColor(40,8,0xFFFFFFFF);
            texture=new NativeImageBackedTexture(pixels);client.getTextureManager().registerTexture(ID,texture);texture.upload();
            mesh=method(owner,"mesh",MinecraftClient.class,Identifier.class,boolean.class);
            first=mesh(client);check(first!=null,"native CPU-backed skin mesh");check(mesh(client)==first,"mesh reused");check(hatQuads(first)==5,"single-pixel extrusion geometry changed");
            Field read=Arrays.stream(layers.getDeclaredFields()).filter(f->Modifier.isStatic(f.getModifiers())&&f.getType()==long.class).findFirst().orElseThrow();read.setAccessible(true);check(read.getLong(null)==0,"CPU skin path unexpectedly downloaded GPU pixels");
            Class<?> block=type("com.batclient.BlockIndicator");method("com.batclient.BlockIndicator","beginRender").invoke(null);
            try{var readBlock=method("com.batclient.BlockIndicator","read",MinecraftClient.class,JsonObject.class,boolean.class);Object one=readBlock.invoke(null,client,new JsonObject(),true);JsonObject settings=new JsonObject();Object a=readBlock.invoke(null,client,settings,true);check(a==readBlock.invoke(null,client,settings,true),"block preview recomputed inside render pass");}
            finally{method("com.batclient.BlockIndicator","endRender").invoke(null);}
            started=System.nanoTime();return false;
        }
        if(reload!=null){
            if(!reload.isDone())return false;reload.join();
            check(mesh.invoke(null,client,DEFAULT_SKIN,false)!=beforeReload,"actual resource reload retained old skin mesh");
            texture.clearGlId();texture.close();
            String result="CUSTOM_RENDERING_NATIVE_OK: "+checks+" checks; real GL uploads, unchanged geometry, CPU mask/no GPU download, >3s reuse, same-GL/downloaded skin invalidation, actual resource reload, block pass reuse";
            Files.writeString(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("rendering-result.txt"),result);System.out.println(result);return true;
        }
        if(System.nanoTime()-started<3_200_000_000L)return false;
        check(mesh(client)==first,"unchanged mesh expired after three seconds");
        int gl=texture.getGlId();texture.getImage().setColor(41,8,0xFFFFFFFF);texture.upload();
        Object updated=mesh(client);check(updated!=first,"same-texture upload did not invalidate mesh");check(texture.getGlId()==gl,"test changed GL identity");check(mesh(client)==updated,"updated mesh not cached");check(hatQuads(updated)==8,"neighboring pixel side-wall culling changed");
        texture.getImage().setColor(40,8,0xFF123456);texture.upload();check(mesh(client)==updated,"RGB-only animation rebuilt unchanged geometry");
        var downloaded=new PlayerSkinTexture(null,"http://127.0.0.1:9/not-requested",Identifier.ofVanilla("textures/entity/player/wide/steve.png"),false,null);
        try(var image=new NativeImage(64,64,false)){
            image.fillRect(0,0,64,64,0);image.setColor(40,8,0xFFFFFFFF);
            try(var stream=CustomRenderingNativeChecks.class.getClassLoader().getResourceAsStream("com/batclient/mixin/SkinLayerPixelsMixin.class")){
                var node=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(stream).accept(node,0);
                String reference=null;
                for(var member:node.methods){
                    var annotations=new ArrayList<org.objectweb.asm.tree.AnnotationNode>();
                    if(member.visibleAnnotations!=null)annotations.addAll(member.visibleAnnotations);if(member.invisibleAnnotations!=null)annotations.addAll(member.invisibleAnnotations);
                    for(var annotation:annotations)if(annotation.desc.endsWith("/Inject;"))for(int i=0;i<annotation.values.size();i+=2)if(annotation.values.get(i).equals("method"))reference=((List<?>)annotation.values.get(i+1)).get(0).toString();
                }
                if(reference==null)throw new AssertionError("Remapped upload target missing");
                int open=reference.indexOf('(');String head=open<0?reference:reference.substring(0,open);String uploadName=head.substring(head.lastIndexOf(';')+1);
                var upload=PlayerSkinTexture.class.getDeclaredMethod(uploadName,NativeImage.class);upload.setAccessible(true);upload.invoke(downloaded,image);
            }
            Field masks=Arrays.stream(layers.getDeclaredFields()).filter(f->f.getType()==WeakHashMap.class).findFirst().orElseThrow();masks.setAccessible(true);
            check(((Map<?,?>)masks.get(null)).containsKey(downloaded),"downloaded-skin upload hook missing");
        }finally{downloaded.clearGlId();downloaded.close();}
        method("com.batclient.SkinLayers3D","clear").invoke(null);check(mesh(client)!=updated,"reload/disconnect clear did not invalidate mesh");
        beforeReload=mesh.invoke(null,client,DEFAULT_SKIN,false);check(beforeReload!=null,"default resource skin unavailable");
        reload=client.reloadResources();return false;
    }
}
