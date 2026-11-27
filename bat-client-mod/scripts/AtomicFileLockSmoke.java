import com.batclient.platform.AtomicFiles;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.concurrent.CountDownLatch;

/** Real Windows sharing-lock regression, not a mocked Files.move implementation. */
public final class AtomicFileLockSmoke {
    public static void main(String[] args)throws Exception {
        Path directory=Files.createTempDirectory(Path.of("build"),"atomic-lock-test-");
        Path target=directory.resolve("settings.json"),temporary=directory.resolve("settings.json.tmp");
        Files.writeString(target,"old");Files.writeString(temporary,"new");
        AtomicFiles.replace(temporary,target);
        if(!Files.readString(target).equals("new"))throw new AssertionError("Basic replacement failed");
        if(!System.getProperty("os.name").startsWith("Windows")){
            System.out.println("ATOMIC_FILE_OK: basic replacement; Windows sharing test unavailable on this OS");return;
        }
        Files.writeString(temporary,"after-lock");
        FileChannel blocker=FileChannel.open(target,StandardOpenOption.READ);
        CountDownLatch released=new CountDownLatch(1);
        CountDownLatch started=new CountDownLatch(1);
        Thread release=new Thread(()->{try{started.countDown();Thread.sleep(8);blocker.close();}catch(Exception error){throw new RuntimeException(error);}finally{released.countDown();}});
        release.start();
        started.await();
        try{AtomicFiles.replace(temporary,target);}finally{released.await();blocker.close();}
        if(!Files.readString(target).equals("after-lock"))throw new AssertionError("Brief real sharing lock was not recovered");
        Files.writeString(temporary,"must-not-replace");
        try(FileChannel held=FileChannel.open(target,StandardOpenOption.READ)){
            try{AtomicFiles.replace(temporary,target);throw new AssertionError("Persistent sharing lock should fail");}
            catch(AccessDeniedException expected){ }
            if(!Files.readString(target).equals("after-lock")||!Files.readString(temporary).equals("must-not-replace"))throw new AssertionError("Failed replacement discarded data");
        }
        AtomicFiles.replace(temporary,target);
        if(!Files.readString(target).equals("must-not-replace"))throw new AssertionError("Retained pending write cannot be retried");
        System.out.println("ATOMIC_FILE_LOCK_OK: normal replacement, brief sharing lock, bounded persistent-lock failure, old/pending data preserved, retry after release");
    }
}
