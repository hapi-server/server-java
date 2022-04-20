
package org.hapiserver;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;

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
    public Iterator<int[]> getGranuleIterator(int[] start, int[] stop) {
        int stopYear;
        if ( stop[1]==1 && stop[2]==1 && stop[3]==0 && stop[4]==0 && stop[5]==0 && stop[6]==0 ) {
            stopYear= stop[0];
        } else {
            stopYear= stop[0]+1;
        }
        return new Iterator<int[]>() {
            int currentYear= start[0];
            @Override
            public boolean hasNext() {
                return currentYear<stopYear;
            }

            @Override
            public int[] next() {
                int y= currentYear;
                currentYear++;
                return new int[] { y, 1, 1, 0, 0, 0, 0, y+1, 1, 1, 0, 0, 0, 0};
            }
        };
    }

    @Override
    public boolean hasParamSubsetIterator() {
        return false;
    }

    @Override
    public Iterator<HapiRecord> getIterator(int[] start, int[] stop, String[] params) {
        throw new IllegalArgumentException("not used");
    }

    @Override
    public Iterator<HapiRecord> getIterator(int[] start, int[] stop) {
        return new WindSwe2mIterator( this.dataHome, start, stop );
    }

    @Override
    public String getTimeStamp(int[] start, int[] stop) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
