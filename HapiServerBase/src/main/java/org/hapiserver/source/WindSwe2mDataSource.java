
package org.hapiserver.source;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import org.hapiserver.HapiRecord;
import org.hapiserver.HapiRecordSource;
import org.hapiserver.TimeString;

/**
 * Example of class which loads data
 * @author jbf
 */
public class WindSwe2mDataSource implements HapiRecordSource {

    private final String dataHome;
        
    public WindSwe2mDataSource( String dataHome, String id ) throws MalformedURLException {
        this.dataHome= new URL( dataHome ).toString();
        System.err.println("id: "+id);
        if ( this.dataHome.startsWith("file:") ) {
            if ( !new File( new URL( dataHome ).getPath() ).exists() ) {
                throw new IllegalArgumentException("dataHome does not exist");
            }
        }
    }
    
    @Override
    public boolean hasGranuleIterator() {
        return true;
    }

    @Override
    public Iterator<TimeString[]> getGranuleIterator( TimeString startts, TimeString stopts) {
        int stopYear;
        int stopMonth;
        int[] stop= stopts.toComponents();
        int[] start= startts.toComponents();
        if ( stop[1]==1 && stop[2]==1 && stop[3]==1 && stop[4]==0 && stop[5]==0 && stop[6]==0 ) {
            stopYear= stop[0];
            stopMonth= stop[1];
        } else {
            stopYear= stop[0];
            stopMonth= stop[1]+1;
            if ( stopMonth==13 ) {
                stopYear= stopYear+1;
                stopMonth-= 12;
            }
        }
        int fstopMonth= stopMonth;
        int fstopYear= stopYear;
        
        return new Iterator<TimeString[]>() {
            int currentYear= start[0];
            int currentMonth= start[1];
            
            @Override
            public boolean hasNext() {
                return currentYear<fstopYear || ( currentYear==fstopYear && currentMonth<fstopMonth );
            }

            @Override
            public TimeString[] next() {
                int m= currentMonth;
                int y= currentYear;
                currentMonth++;
                if ( currentMonth==13 ) {
                    currentMonth-= 12;
                    currentYear++;
                }
                return new TimeString[] { new TimeString( y, m, 1 ), new TimeString( currentYear, currentMonth, 1 ) };
            }
        };
    }

    @Override
    public boolean hasParamSubsetIterator() {
        return false;
    }

    @Override
    public Iterator<HapiRecord> getIterator(TimeString start, TimeString stop, String[] params) {
        throw new IllegalArgumentException("not used");
    }

    @Override
    public Iterator<HapiRecord> getIterator(TimeString start, TimeString stop) {
        return new WindSwe2mIterator( this.dataHome, start, stop );
    }

    @Override
    public TimeString getTimeStamp(TimeString start, TimeString stop) {
        return null;
    }

    @Override
    public void doFinalize() {
        
    }

}
