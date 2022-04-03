
package org.hapiserver;

import java.util.HashMap;
import java.util.Map;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.source.DailyHapiRecordSource;

/**
 * The source registry will be used to look up the object used to
 * service the data request.  This may be a Java class or a 
 * Java class which wraps a Unix process.
 * 
 * @author jbf
 */
public class SourceRegistery {
    
    private static SourceRegistery instance= new SourceRegistery();
    
    public static SourceRegistery getInstance() {
        return instance;
    }
    
    private Map<String,HapiRecordSource> sources= new HashMap<>();
    
    /**
     * return the record source for the id.
     * @param id the HAPI id
     * @param info the info for the data
     * @return null or the record source
     */
    public HapiRecordSource getSource( String id, JSONObject info ) {
        String source= info.optString( "x_source", "" );
        if ( source.length()==0 ) {
            if ( id.equals("28.FF6319A21705_") ) {
                String s= "/home/jbf/data/gardenhouse/data/28.FF6319A21705/" + 
                    "%1$04d/28.FF6319A21705.%1$04d%2$02d%3$02d.csv" ;
                return new DailyHapiRecordSource(s,2,2);
            } else {
                return sources.get(id);
            }
        } else {
            if ( source.startsWith("aggregation:") ) {
                String agg= source.substring(12);
                int nfields= info.optJSONArray("parameters").length();
                if ( nfields==0 ) throw new IllegalArgumentException("no parameters found");
                return DailyHapiRecordSource.fromAggregation(agg,nfields);
            } else {
                throw new IllegalArgumentException("unable to handle source");
            }
        }
    }
    
}
