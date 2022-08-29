
package org.hapiserver.sscweb;

import gov.nasa.gsfc.sscweb.schema.SatelliteData;
import java.util.Iterator;
import org.hapiserver.AbstractHapiRecord;
import org.hapiserver.AbstractHapiRecordSource;
import org.hapiserver.HapiRecord;
import org.hapiserver.TimeUtil;
import org.hapiserver.source.SourceUtil;

/**
 *
 * @author jbf
 */
public class SSCWebRecordSource extends AbstractHapiRecordSource {

    String satellite;
    
    public SSCWebRecordSource( String satellite ) {
        this.satellite= satellite;
    }
    
    @Override
    public boolean hasGranuleIterator() {
        return true;
    }

    @Override
    public Iterator<int[]> getGranuleIterator(int[] start, int[] stop) {
        return SourceUtil.getGranuleIterator( start, stop, TimeUtil.COMPONENT_DAY );
    }

    
    @Override
    public boolean hasParamSubsetIterator() {
        return false;
    }

    private class SSCWebIterator implements Iterator<HapiRecord> {
        
        int len;
        int i;
        SatelliteData data;
        
        private SSCWebIterator( SatelliteData data ) {
            i=0;
            len= data.getBGseX().size();
            this.data= data;
        }
        
        @Override
        public boolean hasNext() {
            return i<len;
        }
    
        @Override
        public HapiRecord next() {
            return new AbstractHapiRecord() {
                @Override
                public String getIsoTime(int i) {
                    return data.getTime().get(i).toString();
                }

                @Override
                public double getDouble(int i) {
                    switch (i) {
                        case 1: return data.getBGseX().get(i);
                        case 2: return data.getBGseY().get(i);
                        case 3: return data.getBGseZ().get(i);
                        default: throw new RuntimeException("should not happen");
                    }
                }

            };
        }
    }
    @Override
    public Iterator<HapiRecord> getIterator(int[] start, int[] stop) {
        SatelliteData data= new SSCWebReader().readSatelliteData( satellite, start, stop );
        return new SSCWebIterator(data);        
    }


}
