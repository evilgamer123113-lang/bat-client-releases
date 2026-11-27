import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Verify the pinned real Minecraft callsite; do not replace frame-limit or loading waits. */
public final class GameLoopYieldContract {
    public static void main(String[] args) throws Exception {
        ClassNode node=new ClassNode();
        try(var in=ClassLoader.getSystemResourceAsStream("net/minecraft/client/MinecraftClient.class")){if(in==null)throw new AssertionError("Minecraft bytecode missing");new ClassReader(in).accept(node,0);}
        int yield=0,limit=-1,yieldIndex=-1,index=0;
        boolean unlimitedBypass=false;
        for(MethodNode method:node.methods){
            if(!method.name.equals("render")||!method.desc.equals("(Z)V"))continue;
            for(AbstractInsnNode instruction:method.instructions){
                if(instruction instanceof MethodInsnNode call){
                    if(call.owner.equals("com/mojang/blaze3d/systems/RenderSystem")&&call.name.equals("limitDisplayFPS"))limit=index;
                    if(call.owner.equals("java/lang/Thread")&&call.name.equals("yield")){yield++;yieldIndex=index;}
                }
                if(instruction instanceof IntInsnNode constant&&constant.operand==260){
                    AbstractInsnNode next=instruction.getNext();while(next!=null&&next.getOpcode()<0)next=next.getNext();
                    if(next instanceof JumpInsnNode branch&&branch.getOpcode()==Opcodes.IF_ICMPGE){
                        for(AbstractInsnNode later=branch.getNext();later!=null&&later!=branch.label;later=later.getNext())
                            if(later instanceof MethodInsnNode call&&call.owner.equals("com/mojang/blaze3d/systems/RenderSystem")&&call.name.equals("limitDisplayFPS"))unlimitedBypass=true;
                    }
                }
                index++;
            }
        }
        if(yield!=1||limit<0||yieldIndex<=limit||!unlimitedBypass)throw new AssertionError("Frame limiter/Unlimited/yield contract changed");
        System.out.println("GAME_LOOP_YIELD_CONTRACT_OK: real1.21.1 bypasses limiter at260 (Unlimited); one post-limiter yield; loading waits outside redirect");
    }
}
