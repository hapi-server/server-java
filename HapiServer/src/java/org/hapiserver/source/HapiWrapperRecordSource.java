
package org.hapiserver.source;

import java.util.Iterator;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.HapiRecord;

/**
 * RecordSource which simply wraps another HAPI server.  Its configuration 
 * looks like this:
 * <pre>
 * "data": {
 *      "source":"hapiserver",
 *      "url": "http://hapi-server.org/servers/SSCWeb/hapi/info?id=hawkeye"
 *  }
 * </pre>
 * If the id is missing, then the same ID will be used on the wrapped server.
 * 
 * @author jbf
 */
public class HapiWrapperRecordSource extends AbstractHapiRecordSource {

    private final String hapiServer;
    private final String id;
    private final JSONObject info;
    
    public HapiWrapperRecordSource( String id, JSONObject info, JSONObject data )  {
        String hapiServ = data.optString("url");
        int i= hapiServ.indexOf("/info?id=");
        if ( i!=-1 ) {
            id= hapiServ.substring(i+9);
            hapiServ= hapiServ.substring(0,i);
        }
        this.hapiServer= hapiServ;
        this.id= id;
        this.info= info;
    }

    @Override
    public boolean hasParamSubsetIterator() {
        return true;
    }
    
    @Override
    public Iterator<HapiRecord> getIterator(int[] start, int[] stop, String[] params) {
        return new HapiWrapperIterator( hapiServer, id, info, params, start, stop );
    }

    @Override
    public Iterator<HapiRecord> getIterator(int[] start, int[] stop) {
        return new HapiWrapperIterator( hapiServer, id, info, start, stop );
    }

}
