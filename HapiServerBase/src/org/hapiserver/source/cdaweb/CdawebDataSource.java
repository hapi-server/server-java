
package org.hapiserver.source.cdaweb;

import java.util.Iterator;
import org.hapiserver.AbstractHapiRecordSource;
import org.hapiserver.HapiRecord;
import org.hapiserver.source.AggregationGranuleIterator;

/**
 *
 * @author jbf
 */
public class CdawebDataSource extends AbstractHapiRecordSource {

    private String id;
    
    public CdawebDataSource(String id) {
        this.id = id;

    }
    @Override
    public boolean hasGranuleIterator() {
        return true;
    }

    /**
     * return the iterator that identifies the intervals to load.  This only needs to be
     * implemented if hasGranuleIterator returns true.
     * @param start the seven component start time [ Y, m, d, H, M, S, N ]
     * @param stop the seven component stop time [ Y, m, d, H, M, S, N ]
     * @return the granule iterator which returns [ start Y, m, d, H, M, S, N, stop Y, m, d, H, M, S, N ]
     */
    @Override
    public Iterator<int[]> getGranuleIterator( int[] start, int[] stop ) {
        return new AggregationGranuleIterator( "$Y_$m_$d", start, stop );
    }
    
    @Override
    public boolean hasParamSubsetIterator() {
        return true;
    }

    @Override
    public Iterator<HapiRecord> getIterator(int[] start, int[] stop, String[] params) {
        try {
            return new CdawebServicesHapiRecordSource( id, start, stop, params );
        } catch ( Exception ex ) {
            throw new RuntimeException(ex);
        }
    }
    
}
