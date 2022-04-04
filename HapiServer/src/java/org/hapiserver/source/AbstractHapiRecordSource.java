
package org.hapiserver.source;

import java.util.Iterator;
import org.hapiserver.HapiRecord;
import org.hapiserver.HapiRecordSource;

/**
 * provides implementations for typical sources.
 * @author jbf
 */
public abstract class AbstractHapiRecordSource implements HapiRecordSource {

    @Override
    public boolean hasGranuleIterator() {
        return false;
    }

    @Override
    public Iterator<int[]> getGranuleIterator(int[] start, int[] stop) {
        throw new UnsupportedOperationException("not used");
    }

    @Override
    public boolean hasParamSubsetIterator() {
        return true;
    }

    @Override
    public abstract Iterator<HapiRecord> getIterator(String[] params, int[] start, int[] stop);

    @Override
    public Iterator<HapiRecord> getIterator(int[] start, int[] stop) {
        throw new UnsupportedOperationException("not used");
    }

    @Override
    public String getTimeStamp(int[] start, int[] stop) {
        return null;
    }
    
}
