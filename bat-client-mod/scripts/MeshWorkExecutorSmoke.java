import com.batclient.performance.MeshWorkExecutor;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class MeshWorkExecutorSmoke {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        ExecutorService shared=Executors.newSingleThreadExecutor();
        CountDownLatch sharedBlocked=new CountDownLatch(1),releaseShared=new CountDownLatch(1);
        shared.execute(()->{sharedBlocked.countDown();try{releaseShared.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        check(sharedBlocked.await(10,TimeUnit.SECONDS),"shared generation executor occupied");
        MeshWorkExecutor mesh=new MeshWorkExecutor(shared);
        try{
            CompletableFuture<Thread> independent=CompletableFuture.supplyAsync(Thread::currentThread,mesh);
            Thread worker=independent.get(10,TimeUnit.SECONDS);
            check(worker.getName().startsWith("Bat chunk mesh-")&&worker.isDaemon(),"mesh uses owned daemon worker");
            check(worker.getPriority()==Thread.NORM_PRIORITY,"new worker does not inherit elevated render priority");
            check(worker.getContextClassLoader()==MeshWorkExecutor.class.getClassLoader(),"game/mod class loader preserved");
            AtomicInteger count=new AtomicInteger();CountDownLatch accepted=new CountDownLatch(200);
            for(int i=0;i<200;i++)mesh.execute(()->{count.incrementAndGet();accepted.countDown();});
            mesh.close();mesh.close();
            check(accepted.await(10,TimeUnit.SECONDS)&&count.get()==200,"shutdown drains accepted jobs without loss or duplicates");
            CompletableFuture<Integer> late=CompletableFuture.supplyAsync(()->123,mesh);
            check(!late.isDone(),"late cleanup work uses existing fallback queue");
            releaseShared.countDown();
            check(late.get(10,TimeUnit.SECONDS)==123,"late cleanup work completes after owner closes");
        }finally{mesh.close();releaseShared.countDown();shared.shutdown();check(shared.awaitTermination(10,TimeUnit.SECONDS),"fallback terminates cleanly");}

        ExecutorService fallback=Executors.newSingleThreadExecutor();
        ExecutorService submitters=Executors.newFixedThreadPool(4);
        MeshWorkExecutor racing=new MeshWorkExecutor(fallback);
        AtomicIntegerArray seen=new AtomicIntegerArray(1000);CountDownLatch finished=new CountDownLatch(1000);
        try{
            for(int i=0;i<1000;i++){
                final int id=i;
                submitters.submit(()->racing.execute(()->{seen.incrementAndGet(id);finished.countDown();}));
                if(i==500)racing.close();
            }
            submitters.shutdown();check(submitters.awaitTermination(10,TimeUnit.SECONDS),"submitters complete during close race");
            check(finished.await(10,TimeUnit.SECONDS),"close race does not strand accepted work");
            for(int i=0;i<1000;i++)check(seen.get(i)==1,"exact-once execution during close race");
        }finally{racing.close();submitters.shutdown();fallback.shutdown();fallback.awaitTermination(10,TimeUnit.SECONDS);}
        MeshWorkExecutor rejected=new MeshWorkExecutor(task->{throw new RejectedExecutionException("closed fallback");});
        rejected.close();
        try{rejected.execute(()->{});throw new AssertionError("fallback rejection swallowed");}catch(RejectedExecutionException expected){}
        System.out.println("MESH_WORK_EXECUTOR_OK: shared-pool isolation, context, priority, 1200 jobs, close race and late cleanup");
    }
}
