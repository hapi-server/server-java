
package org.hapiserver.source;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Iterator;
import org.hapiserver.TimeString;
import org.hapiserver.URITemplate;

/**
 *
 * @author jbf
 */
public class AggregationGranuleIterator implements Iterator<TimeString[]> {

    String[] result;
    int next=0;
    URITemplate uriTemplate;
    
    public AggregationGranuleIterator( String fileFormat, TimeString start, TimeString stop ) {
        this.uriTemplate= new URITemplate(fileFormat);
        
        try {
            result= URITemplate.formatRange( fileFormat, start.toString(), stop.toString() );
        } catch (ParseException ex) {
            throw new RuntimeException(ex);
        }
            
           
    }
    @Override
    public boolean hasNext() {
        return result.length>next;
    }

    @Override
    public TimeString[] next() {
        try {
            int i= next;
            next++;
            int[] rr= uriTemplate.parse(result[i]);
            TimeString start= new TimeString( Arrays.copyOfRange( rr, 0, 7 )  );
            TimeString stop= new TimeString( Arrays.copyOfRange( rr, 7, 14 ) );
            return new TimeString[] { start, stop };
        } catch ( ParseException ex ) {
            throw new RuntimeException(ex);
        }

    }

}
