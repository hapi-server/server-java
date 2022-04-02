
package org.hapiserver;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.hapiserver.source.AbstractHapiRecordSource;
import org.hapiserver.source.DailyHapiRecordSource;
import org.hapiserver.source.SourceUtil;

/**
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
     * @return null or the record source
     */
    public HapiRecordSource getSource( String id ) {
        if ( id.equals("28.FF6319A21705") ) {
            String s= "/home/jbf/data/gardenhouse/data/28.FF6319A21705/" + 
                "%1$04d/28.FF6319A21705.%1$04d%2$02d%3$02d.csv" ;
            return new DailyHapiRecordSource(s,2,2);
        } else {
            return sources.get(id);
        }
    }
    
}
