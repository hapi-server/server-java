
package org.hapiserver.source;

import java.util.Iterator;
import org.hapiserver.HapiRecord;
import org.hapiserver.HapiRecordSource;
import org.hapiserver.TimeString;

/**
 * provides implementations for typical sources.
 * @author jbf
 * @see org.hapiserver.AbstractHapiRecordSource
 */
public abstract class AbstractHapiRecordSource implements HapiRecordSource {

    @Override
    public boolean hasGranuleIterator() {
        return false;
    }

    @Override
    public Iterator<TimeString[]> getGranuleIterator(TimeString start, TimeString stop) {
        throw new UnsupportedOperationException("not used");
    }

    @Override
    public boolean hasParamSubsetIterator() {
        return true;
    }

    @Override
    public abstract Iterator<HapiRecord> getIterator(TimeString start, TimeString stop, String[] params);

    @Override
    public Iterator<HapiRecord> getIterator(TimeString start, TimeString stop) {
        throw new UnsupportedOperationException("not used");
    }

    @Override
    public TimeString getTimeStamp(TimeString start, TimeString stop) {
        return null;
    }

    @Override
    public void doFinalize() {
        
    }
    
}
