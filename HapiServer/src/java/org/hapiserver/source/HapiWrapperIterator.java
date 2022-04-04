
package org.hapiserver.source;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.CSVHapiRecord;
import org.hapiserver.CSVHapiRecordConverter;
import org.hapiserver.HapiRecord;
import org.hapiserver.HapiServerSupport;
import org.hapiserver.TimeUtil;

/**
 * Use another HAPI server as a source of HapiRecords.
 * @author jbf
 */
public class HapiWrapperIterator implements Iterator<HapiRecord> {

    boolean initialized= false;
    
    String server;
    String id;
    JSONObject info;
    URL request;
    InputStream in;
    BufferedReader reader;
    String nextRecord;
    String[] params;
    CSVHapiRecordConverter converter;
    
    public HapiWrapperIterator( String server, String id, JSONObject info, String[] params, int[] start, int[] stop) {
        this.server= server;
        this.id= id;
        this.info= info;
        this.params= params;
        String surl;
        if ( params==null ) {
            surl= String.format( "%s/data?id=%s&time.min=%s&time.max=%s", 
                server, id, TimeUtil.formatIso8601Time(start), TimeUtil.formatIso8601Time(stop) );
        } else {
            String sparams= HapiServerSupport.joinParams(info,params);
            surl= String.format( "%s/data?id=%s&time.min=%s&time.max=%s&params=%s", 
                server, id, TimeUtil.formatIso8601Time(start), TimeUtil.formatIso8601Time(stop), sparams );
        }
        try {
            request= new URL( surl );
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    public HapiWrapperIterator( String server, String id, JSONObject info, int[] start, int[] stop) {
        this( server, id, info, null, start, stop );
    }
    
    @Override
    public boolean hasNext() {
        if ( !initialized ) {
            try {
                initialized= true;
                in= request.openStream();
                reader= new BufferedReader( new InputStreamReader(in) );
                nextRecord= reader.readLine();
                converter= new CSVHapiRecordConverter(info);
            } catch (IOException | JSONException ex) {
                throw new RuntimeException(ex);
            }
        }
        return nextRecord!=null;
    }

    @Override
    public HapiRecord next() {
        try {
            String t= nextRecord;
            nextRecord= reader.readLine();
            return converter.convert(t);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

}
