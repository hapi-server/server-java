
package org.hapiserver.source;

import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.hapiserver.HapiRecord;
import org.hapiserver.HapiRecordSource;
import org.hapiserver.TimeUtil;

/**
 * Often we have granules of data which when "aggregated" together form the 
 * entire data set.  For example, data might be stored in daily files, and to
 * implement the HAPI server we must read each one.  This class creates a
 * HapiRecord iterator for any time range by combining each of the granules,
 * so the reader can be simple.
 * @author jbf
 */
public class AggregatingIterator implements Iterator<HapiRecord> {

    private static final Logger logger= Logger.getLogger("hapi.agg");
    
    int[] granule;
    Iterator<int[]> granuleIterator;
    Iterator<HapiRecord> hapiRecordIterator;
    String[] parameters;
    HapiRecordSource source;
    int[] stop;
    
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
        //this.granuleIterator= source.getGranuleIterator(start, stop);
        //System.err.println("=====" + parameters[0] );
        //while ( this.granuleIterator.hasNext() ) {
        //    System.err.println( TimeUtil.formatIso8601TimeRange( this.granuleIterator.next() ) );
        //}
        //System.err.println("=====");
        this.granuleIterator= source.getGranuleIterator(start, stop);
        this.stop= stop;
        this.parameters= parameters;
        if ( granuleIterator.hasNext() ) {
            this.granule= granuleIterator.next();
            while ( TimeUtil.gt( start, TimeUtil.getStopTime(this.granule) ) && granuleIterator.hasNext() ) {
                logger.log(Level.FINER, "skipping {0}", this.granule);
                this.granule= granuleIterator.next();
            }
            if ( TimeUtil.gt( this.granule, stop ) ) {
                logger.log(Level.FINER, "finished {0}", this.granule);
                this.granule= null;
            } else {
                if ( this.granule.length!=TimeUtil.TIME_RANGE_DIGITS ) {
                    throw new IllegalArgumentException("implementation error, granule iterator did not return 14 time range digits");
                }
                if ( this.parameters==null ) {
                    this.hapiRecordIterator= source.getIterator( granule, TimeUtil.getStopTime(granule) );
                } else {
                    // Here we make the iterator for the next granule.
                    this.hapiRecordIterator= source.getIterator(granule, TimeUtil.getStopTime(granule), this.parameters );
                }
                findNextRecord();
            }
        } else {
            this.granule= null;
        }
    }
    
    private void findNextRecord() {
        while ( !this.hapiRecordIterator.hasNext() ) {
            if ( !granuleIterator.hasNext() ) {
                granule=null; // we're done
                break;
            } else {
                granule= granuleIterator.next();
                if ( granule.length!=14 ) {
                    throw new IllegalArgumentException("granule length should be 14");
                }
                if ( TimeUtil.gt( this.granule, this.stop ) ) {
                    this.granule= null;
                    break;
                }
            }
            TimeUtil.isValidTimeRange(granule);
            try {
                if ( this.parameters==null ) {
                    hapiRecordIterator= source.getIterator( granule, TimeUtil.getStopTime(granule) );
                } else {
                    hapiRecordIterator= source.getIterator( granule, TimeUtil.getStopTime(granule), parameters );
                }
            } catch ( RuntimeException ex ) {
                logger.log(Level.WARNING, "RuntimeException in AggregatingIterator.findNextRecord: {0}", ex.getMessage());
                this.granule= null;
                break;
            }
        }
    }
    
    @Override
    public boolean hasNext() {
        return granule!=null && hapiRecordIterator.hasNext();
    }

    @Override
    public HapiRecord next() {
        HapiRecord next= hapiRecordIterator.next();
        if ( !hapiRecordIterator.hasNext() ) {
            findNextRecord();
        }
        return next;
    }

}
