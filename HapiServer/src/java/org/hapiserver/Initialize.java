
package org.hapiserver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
    public static void initialize( File hapiHome ) {
        if ( !hapiHome.mkdirs() ) {
            throw new RuntimeException("Unable to make hapi_home");
        }
        File infoDir= new File( hapiHome, "info" );
        if ( !infoDir.mkdirs() ) {
            throw new RuntimeException("Unable to make info area");
        }
        String[] examples= new String[] { "wind_swe_2m" };
        
        for ( String s: examples ) {
            File exampleData= new File( infoDir, s + ".json" );
            try {
                Util.transfer( Util.getTemplateAsStream( s+".json" ), new FileOutputStream(exampleData), true );
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
        
    }
}
