
package org.hapiserver.source.cdaweb;

import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.HapiRecordSource;

/**
 * Delegates to the correct implementation
 * @author jbf
 */
public class CdawebHapiRecordSource {
    
    public static HapiRecordSource create( String availRoot, String id, JSONObject info, JSONObject data ) {
        return new CdawebServicesHapiRecordSource(availRoot,id,info,data);
    }
    
}
