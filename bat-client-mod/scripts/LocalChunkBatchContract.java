import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Pinned-game contract: this hook selects ready chunks, never waits for generation. */
public final class LocalChunkBatchContract {
    private static ClassNode load(String name)throws Exception{
        ClassNode node=new ClassNode();
        try(var stream=ClassLoader.getSystemResourceAsStream(name+".class")){
            if(stream==null)throw new AssertionError("Missing "+name);
            new ClassReader(stream).accept(node,0);
        }
        return node;
    }
    private static MethodNode method(ClassNode owner,String name,String descriptor){
        return owner.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(descriptor)).findFirst().orElseThrow(()->new AssertionError("Changed game method "+name));
    }
    private static void nonblocking(MethodNode method){
        for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call){
            if(call.owner.equals("java/util/concurrent/CompletableFuture")&&(call.name.equals("join")||call.name.equals("get")))throw new AssertionError("Blocking readiness query");
        }
    }
    public static void main(String[] args)throws Exception{
        ClassNode sender=load("net/minecraft/server/network/ChunkDataSender");
        for(String field:new String[]{"local:Z","pending:F","chunks:Lit/unimi/dsi/fastutil/longs/LongSet;"}){
            int split=field.indexOf(':');
            if(sender.fields.stream().noneMatch(f->f.name.equals(field.substring(0,split))&&f.desc.equals(field.substring(split+1))))throw new AssertionError("Changed sender field "+field);
        }
        MethodNode batch=method(sender,"makeBatch","(Lnet/minecraft/server/world/ServerChunkLoadingManager;Lnet/minecraft/util/math/ChunkPos;)Ljava/util/List;");
        boolean localBranch=false;
        for(var instruction:batch.instructions)if(instruction instanceof FieldInsnNode field&&field.name.equals("local")&&field.getOpcode()==Opcodes.GETFIELD)localBranch=true;
        if(!localBranch)throw new AssertionError("Local batching semantics changed");
        nonblocking(method(load("net/minecraft/server/world/ServerChunkLoadingManager"),"getPostProcessedChunk","(J)Lnet/minecraft/world/chunk/WorldChunk;"));
        nonblocking(method(load("net/minecraft/server/world/ChunkHolder"),"getPostProcessedChunk","()Lnet/minecraft/world/chunk/WorldChunk;"));
        nonblocking(method(load("net/minecraft/server/world/ChunkHolder"),"getWorldChunk","()Lnet/minecraft/world/chunk/WorldChunk;"));
        System.out.println("LOCAL_CHUNK_BATCH_CONTRACT_OK: real1.21.1 local branch, exact target/fields and nonblocking readiness chain");
    }
}
