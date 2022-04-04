
package org.hapiserver.source;

import java.util.Iterator;
import org.hapiserver.ExtendedTimeUtil;
import org.hapiserver.HapiRecord;
import org.hapiserver.HapiRecordSource;

/**
 * Often we have granules of data which when "aggregated" together form the 
 * entire data set.  For example, data might be stored in daily files, and to
 * implement the HAPI server we must read each one.  This class creates a
 * HapiRecord iterator for any time range by combining each of the granules,
 * so the reader can be simple.
 * @author jbf
 */
public class AggregatingIterator implements Iterator<HapiRecord> {

    int[] granule;
    Iterator<int[]> granuleIterator;
    Iterator<HapiRecord> hapiRecordIterator;
    String[] parameters;
    HapiRecordSource source;
    
    /**
     * construct an iterator which will use the source create and go through a set of iterators, one for each granule.
     * @param source the source of data
     * @param start the start time
     * @param stop the stop time
     */
    public AggregatingIterator( HapiRecordSource source, int[] start, int[] stop ) {
        this( source, start, stop, null );
    }
    
    /**
     * construct an iterator which will use the source create and go through a set of iterators, one for each granule.
     * @param source the source of data
     * @param start the start time
     * @param stop the stop time
     * @param parameters null or the parameters to subset.
     */
    public AggregatingIterator( HapiRecordSource source, int[] start, int[] stop, String[] parameters ) {
        this.source= source;
        this.granuleIterator= source.getGranuleIterator(start, stop);
        this.parameters= parameters;
        if ( granuleIterator.hasNext() ) {
            this.granule= granuleIterator.next();
            if ( this.parameters==null ) {
                this.hapiRecordIterator= source.getIterator( granule, ExtendedTimeUtil.getStopTime(granule) );
            } else {
                this.hapiRecordIterator= source.getIterator(granule, ExtendedTimeUtil.getStopTime(granule), this.parameters );
            }
            findNextRecord();
        } else {
            this.granule= null;
        }
    }
    
    private void findNextRecord() {
        while ( !this.hapiRecordIterator.hasNext() ) {
            if ( !granuleIterator.hasNext() ) {
                this.granule=null; // we're done
                break;
            } else {
                this.granule= granuleIterator.next();
            }
            if ( this.parameters==null ) {
                this.hapiRecordIterator= source.getIterator( this.granule, ExtendedTimeUtil.getStopTime(this.granule) );
            } else {
                this.hapiRecordIterator= source.getIterator(                    this.granule, ExtendedTimeUtil.getStopTime(this.granule), this.parameters );
            }
        }
    }
    
    @Override
    public boolean hasNext() {
        return this.granule!=null && this.hapiRecordIterator.hasNext();
    }

    @Override
    public HapiRecord next() {
        HapiRecord next= this.hapiRecordIterator.next();
        if ( !this.hapiRecordIterator.hasNext() ) {
            findNextRecord();
        }
        return next;
    }

}
