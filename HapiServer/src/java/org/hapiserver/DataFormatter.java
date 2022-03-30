
package org.hapiserver;

import java.io.IOException;
import java.io.OutputStream;
import org.codehaus.jettison.json.JSONObject;

/**
 * A DataFormatter converts a HAPI record in to a formatted record on the stream.
 * @author jbf
 */
public interface DataFormatter {
        
    /**
     * configure the format.
     * @param info JSON info describing the records.
     * @param out
     * @param record a single HAPI record
     * @throws java.io.IOException
     */
    public void initialize( JSONObject info, OutputStream out, HapiRecord record) throws IOException;
    
    public void sendRecord( OutputStream out, HapiRecord record ) throws IOException;
    
    /**
     * perform any final operations to the stream.  This DOES NOT close the stream!
     * @param out 
     * @throws java.io.IOException 
     */
    public void finalize( OutputStream out )  throws IOException;
    
}
