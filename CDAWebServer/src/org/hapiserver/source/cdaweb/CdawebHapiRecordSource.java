
package org.hapiserver.source.cdaweb;

import java.io.File;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.HapiRecordSource;

/**
 * Delegates to the correct implementation
 * @author jbf
 */
public class CdawebHapiRecordSource {
    private static final Logger logger= Logger.getLogger("hapi.cdaweb");
    
    public static HapiRecordSource create( String availRoot, String id, JSONObject info, JSONObject data, String cacheDir ) {
        File cache= new File(cacheDir);
        if ( !cache.exists() ) {
            if (!cache.mkdirs()) {
                logger.warning("fail to make download area");
                throw new IllegalArgumentException("unable to continue");
            }
        }
        return new CdawebServicesHapiRecordSource(availRoot,id,info,data,cache);
    }
    
}
