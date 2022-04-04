
package org.hapiserver.source;

import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.HapiRecord;
import org.hapiserver.HapiServerSupport;

/**
 *
 * @author jbf
 */
public class HapiWrapperRecordSource extends AbstractHapiRecordSource {

    private String hapiServer;
    private String id;
    private JSONObject info;
    
    public HapiWrapperRecordSource( JSONObject info, String hapiServer, String id ) {
        this.hapiServer= hapiServer;
        this.id= id;
        this.info= info;
    }

    @Override
    public boolean hasParamSubsetIterator() {
        return true;
    }
    
    @Override
    public Iterator<HapiRecord> getIterator(String[] params, int[] start, int[] stop) {
        return new HapiWrapperIterator( hapiServer, id, info, params, start, stop );
    }

    @Override
    public Iterator<HapiRecord> getIterator(int[] start, int[] stop) {
        return new HapiWrapperIterator( hapiServer, id, info, start, stop );
    }

}
