import batclient.shaded.org.objectweb.asm.*;
import batclient.shaded.org.objectweb.asm.tree.*;
import java.util.*;
import java.util.jar.*;

/** Check the real game contract used to avoid per-frame OpenGL readbacks. */
public final class CrosshairBlendContractSmoke {
    public static void main(String[] args)throws Exception{
        try(JarFile jar=new JarFile(args[0])){
            ClassNode node=new ClassNode();
            new ClassReader(jar.getInputStream(jar.getJarEntry("net/minecraft/class_329.class"))).accept(node,0);
            MethodNode crosshair=null;
            for(MethodNode method:node.methods)if(method.name.equals("method_1736"))crosshair=method;
            if(crosshair==null)throw new AssertionError("Expected actual intermediary renderCrosshair");
            boolean enabled=false,blend=false,sprite=false;
            List<String> factors=new ArrayList<>();
            for(AbstractInsnNode insn:crosshair.instructions){
                if(insn instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)insn;
                    if(f.owner.startsWith("com/mojang/blaze3d/platform/GlStateManager$"))factors.add(f.name);
                }
                if(!(insn instanceof MethodInsnNode))continue;
                MethodInsnNode call=(MethodInsnNode)insn;
                if(call.owner.equals("com/mojang/blaze3d/systems/RenderSystem")){
                    if(call.name.equals("enableBlend"))enabled=true;
                    if(call.name.equals("disableBlend"))enabled=false;
                    if(call.name.equals("blendFuncSeparate")){
                        if(!factors.equals(Arrays.asList("ONE_MINUS_DST_COLOR","ONE_MINUS_SRC_COLOR","ONE","ZERO")))throw new AssertionError("Changed vanilla blend contract: "+factors);
                        blend=true;factors.clear();
                    }
                    if(call.name.equals("defaultBlendFunc"))blend=false;
                }
                if(call.owner.equals("net/minecraft/class_332")&&call.name.equals("method_52706")){
                    if(!enabled||!blend)throw new AssertionError("First crosshair sprite is not in expected vanilla state");
                    sprite=true;break;
                }
            }
            if(!sprite)throw new AssertionError("Native crosshair sprite absent");
            System.out.println("CROSSHAIR_BLEND_CONTRACT_OK: actual MC1.21.1 enabled blend + inverse-colour factors before first sprite");
        }
    }
}
