package com.batclient.performance;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Mesh CPU jobs only; generation, renderer mailbox, GL uploads and vanilla buffer limits stay unchanged. */
public final class MeshWorkExecutor implements Executor,AutoCloseable {
    private static final AtomicInteger IDS=new AtomicInteger();
    private final Executor fallback;
    private final ThreadPoolExecutor workers;
    private volatile boolean closed;
    public MeshWorkExecutor(Executor fallback){
        this.fallback=fallback;
        int parallelism=Math.max(1,Math.min(2,Runtime.getRuntime().availableProcessors()/4));
        workers=new ThreadPoolExecutor(0,parallelism,5,TimeUnit.SECONDS,new LinkedBlockingQueue<Runnable>(),task->{
            Thread thread=new Thread(task,"Bat chunk mesh-"+IDS.incrementAndGet());
            thread.setDaemon(true);thread.setPriority(Thread.NORM_PRIORITY);
            thread.setContextClassLoader(MeshWorkExecutor.class.getClassLoader());
            return thread;
        });
        // Start the intended workers on demand rather than accumulating jobs behind a
        // single zero-core worker on machines with enough cores for two mesh jobs.
        workers.setCorePoolSize(parallelism);workers.allowCoreThreadTimeOut(true);
    }
    @Override public void execute(Runnable task){
        if(closed){fallback.execute(task);return;}
        try{workers.execute(task);}
        catch(RejectedExecutionException failure){
            if(!closed)throw failure;
            fallback.execute(task);
        }
    }
    @Override public void close(){closed=true;workers.shutdown();}
}
