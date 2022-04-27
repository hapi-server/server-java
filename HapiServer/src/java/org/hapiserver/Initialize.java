
package org.hapiserver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One place where the hapi-server home directory is configured.  This folder
 * should be writable by the web server, and should be sufficiently large
 * that caching can be performed there.
 * @author jbf
 */
public class Initialize {
    
    private static final Logger logger= Logger.getLogger("hapi");
    
    /**
     * initialize the hapi_home area.
     * @param hapiHome area where infos are stored.
     */
    public static synchronized void initialize( File hapiHome ) {
        if ( hapiHome.exists() ) {
            if ( !hapiHome.canWrite() ) {
                throw new RuntimeException("Unable to write in hapi_home: "+hapiHome);
            }
        } else {
            if ( !hapiHome.mkdirs() ) {
                throw new RuntimeException("Unable to make hapi_home: "+hapiHome);
            }
        }

        File configDir= new File( hapiHome, "config" );
        if ( !configDir.mkdirs() ) {
            throw new RuntimeException("Unable to make config area: "+configDir);
        }

        try {
            File aboutFile= new File( hapiHome, "about.json" );
            
            logger.log(Level.INFO, "copy about.json from internal templates to {0}", aboutFile);
            
            InputStream in= Util.getTemplateAsStream("about.json");
            File tmpFile= new File( hapiHome, "_about.json" );
            Util.transfer( in, new FileOutputStream(tmpFile), true );
            if ( !tmpFile.renameTo(aboutFile) ) {
                logger.log(Level.SEVERE, "Unable to write to {0}", aboutFile);
                throw new IllegalArgumentException("unable to write about file");
            } else {
                logger.log(Level.FINE, "wrote cached about file {0}", aboutFile);
            }

        } catch ( IOException ex ) {
            throw new RuntimeException(ex);
        }

        try {
            File catalogFile= new File( new File( hapiHome, "config" ), "catalog.json" );
            
            logger.log(Level.INFO, "copy catalog.json from internal templates to {0}", catalogFile);
            
            InputStream in= Util.getTemplateAsStream("catalog.json");
            File tmpFile= new File( new File( hapiHome, "config" ), "_catalog.json" );
            Util.transfer( in, new FileOutputStream(tmpFile), true );
            if ( !tmpFile.renameTo(catalogFile) ) {
                logger.log(Level.SEVERE, "Unable to write to {0}", catalogFile);
                throw new IllegalArgumentException("unable to write catalog file");
            } else {
                logger.log(Level.FINE, "wrote cached catalog file {0}", catalogFile);
            }

        } catch ( IOException ex ) {
            throw new RuntimeException(ex);
        }
        
        File infoDir= new File( hapiHome, "info" );
        if ( !infoDir.mkdirs() ) {
            throw new RuntimeException("Unable to make info area");
        }
        
        File dataDir= new File( hapiHome, "data" );
        if ( !dataDir.mkdirs() ) {
            throw new RuntimeException("Unable to make data area");
        }
        
        String[] examples= new String[] { "wind_swe_2m", "temperature", "spawnsource" };
        
        for ( String s: examples ) {
            File exampleData= new File( configDir, s + ".json" );
            try {
                Util.transfer( Util.getTemplateAsStream( s+".json" ), new FileOutputStream(exampleData), true );
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
        
    }
}
