package com.batclient.platform;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.locks.LockSupport;

/** Retains the old file when replacement fails; tolerates brief Windows sharing locks. */
public final class AtomicFiles {
    private AtomicFiles() {}
    public static void replace(Path temporary,Path target)throws IOException {
        for(int attempt=0;;attempt++){
            try{
                try{Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
                catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
                return;
            }catch(AccessDeniedException denied){
                if(attempt==7||Thread.currentThread().isInterrupted())throw denied;
                // Saving settings only; never used by rendering/input/chunk work.
                LockSupport.parkNanos((2L<<Math.min(attempt,5))*1_000_000L);
            }
        }
    }
}
