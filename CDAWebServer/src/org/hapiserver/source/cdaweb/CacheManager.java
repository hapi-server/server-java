
package org.hapiserver.source.cdaweb;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Process which will remove old products from the cache.  This checks every
 * 60 seconds for files more than 5 minutes old.
 * @author jbf
 */
public class CacheManager {
    
    private static final Logger logger= Logger.getLogger("hapi.cdaweb");
    
    private CacheManager(File dir) {
        cacheDir= dir.toPath();
        timer= new Timer("CacheManager",true);
        timer.scheduleAtFixedRate( getTimerTask(), new Date(), 60000 );
    }
    
    private static Map<File,CacheManager> instances= new HashMap<>();
    
    public static CacheManager getInstance(File dir) {
        CacheManager instance= instances.get(dir);
        if ( instance!=null ) {
            return instance;
        } else {
            synchronized (CacheManager.class) {
                instance= instances.get(dir);
                if ( instance==null ) {
                    instance= new CacheManager(dir);
                    instances.put( dir, instance );
                    return instance;
                }
                return instance;
                
            }
        }
    }
    
    private final int maxAgeMilliseconds = 300000; // 5 minutes
    
    private final Path cacheDir;
            
    private final Timer timer;
    
    private TimerTask getTimerTask() {
        return new TimerTask() {
            @Override
            public void run() {
                try {
                    System.err.println("run cleanup");
                    cleanup();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
        };
    }
    
    
    public void requestCleanup() {
        
    }
    
    private void cleanup() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(cacheDir)) {
            long now = System.currentTimeMillis();
            for (Path path : stream) {
                long ageMillis = now - Files.getLastModifiedTime(path).toMillis();
                System.err.println(path.toString()+"..."+(ageMillis/1000.)+" seconds old");
                if ( ageMillis > maxAgeMilliseconds) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
    
}
