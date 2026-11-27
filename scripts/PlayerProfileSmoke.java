import com.batclient.cosmetics.PlayerListHook;
import batclient.shaded.org.objectweb.asm.*;
import java.lang.reflect.*;
import java.util.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** Real Authlib jar regression, local HTTP only. No account or skin service access. */
public class PlayerProfileSmoke extends ClassLoader {
    static int checks;
    static Object access(Object p,String old,String modern)throws Exception{try{return p.getClass().getMethod(old).invoke(p);}catch(NoSuchMethodException e){return p.getClass().getMethod(modern).invoke(p);}}
    static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);checks++;}
    static Object profile()throws Exception{return Class.forName("com.mojang.authlib.GameProfile").getConstructor(UUID.class,String.class).newInstance(UUID.randomUUID(),"TestPlayer");}
    static boolean textured(Object p)throws Exception{Object map=access(p,"getProperties","properties");return (Boolean)map.getClass().getMethod("containsKey",Object.class).invoke(map,"textures");}
    public static void main(String[] args)throws Exception{
        final int[] requests={0};final boolean[] slow={false};
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{requests[0]++;if(slow[0])try{Thread.sleep(450);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            byte[] body="{\"properties\":[{\"name\":\"textures\",\"value\":\"test-texture\",\"signature\":\"test-signature\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
        try{
            PlayerListHook.apiRoot="http://127.0.0.1:"+server.getAddress().getPort();
            // Warm only the fixture HTTP server/network classes before the
            // render-thread constructor timing contract is exercised.
            java.io.InputStream warm=new java.net.URL(PlayerListHook.apiRoot+"/warmup").openStream();
            while(warm.read()!=-1){} warm.close();
            check(PlayerListHook.prepareProfile(null)==null,"null safe");
            Object original=profile(),result=PlayerListHook.prepareProfile(original);
            long readyDeadline=System.nanoTime()+3_000_000_000L;
            while(!textured(result)&&System.nanoTime()<readyDeadline){Thread.sleep(25);result=PlayerListHook.prepareProfile(original);}
            check(textured(result),"real Authlib textures populated (including bounded late-cache path)");
            check(access(result,"getId","id").equals(access(original,"getId","id")),"UUID retained");
            boolean modern;try{original.getClass().getMethod("properties");modern=true;}catch(NoSuchMethodException e){modern=false;}
            check(!modern||!textured(original)&&result!=original,"immutable input untouched");
            int before=requests[0];check(PlayerListHook.prepareProfile(result)==result&&requests[0]==before,"existing premium/server texture preserved without fetch");
            Object same=Class.forName("com.mojang.authlib.GameProfile").getConstructor(UUID.class,String.class).newInstance(access(result,"getId","id"),"TestPlayer");
            check(textured(PlayerListHook.prepareProfile(same))&&requests[0]==before,"cached value reused");
            // Verify the real transformer stores the returned profile, including record replacements.
            ClassWriter cw=new ClassWriter(0);cw.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,"ProfileEntryFixture",null,"java/lang/Object",null);
            cw.visitField(Opcodes.ACC_PUBLIC,"profile","Lcom/mojang/authlib/GameProfile;",null,null).visitEnd();
            MethodVisitor mv=cw.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Lcom/mojang/authlib/GameProfile;)V",null,null);mv.visitCode();mv.visitVarInsn(Opcodes.ALOAD,0);mv.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);mv.visitVarInsn(Opcodes.ALOAD,0);mv.visitVarInsn(Opcodes.ALOAD,1);mv.visitFieldInsn(Opcodes.PUTFIELD,"ProfileEntryFixture","profile","Lcom/mojang/authlib/GameProfile;");mv.visitInsn(Opcodes.RETURN);mv.visitMaxs(2,2);mv.visitEnd();cw.visitEnd();
            Class<?> transformer=Class.forName("com.batclient.cosmetics.CapeTransformer");Class<?> mappings=Class.forName("com.batclient.cosmetics.Mappings");Constructor<?> mappingCtor=mappings.getDeclaredConstructor(String.class,String.class);mappingCtor.setAccessible(true);Constructor<?> transformCtor=transformer.getDeclaredConstructor(mappings);transformCtor.setAccessible(true);Object instance=transformCtor.newInstance(mappingCtor.newInstance("1.21.11","intermediary"));Method patch=transformer.getDeclaredMethod("patchPlayerListEntry",byte[].class);patch.setAccessible(true);
            byte[] transformed=(byte[])patch.invoke(instance,(Object)cw.toByteArray());Class<?> fixture=new PlayerProfileSmoke().defineClass("ProfileEntryFixture",transformed,0,transformed.length);
            Object entry=fixture.getConstructor(original.getClass()).newInstance(same);check(textured(fixture.getField("profile").get(entry)),"transformed constructor stores skin-bearing profile");
            slow[0]=true;Object delayed=profile();Object first=PlayerListHook.prepareProfile(delayed);Thread.sleep(650);check(textured(PlayerListHook.prepareProfile(delayed)),"late result cached for immutable next entry");
            System.out.println("AUTHLIB_PROFILE_OK: "+checks+" checks; "+(modern?"immutable record":"legacy mutable"));
        }finally{server.stop(0);}
    }
}
