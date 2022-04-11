
package org.hapiserver;

import java.util.Iterator;

/**
 *
 * @author jbf
 */
public class WindSwe2mDataSource implements HapiRecordSource {

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
        return new WindSwe2mIterator( start, stop );
    }

    @Override
    public String getTimeStamp(int[] start, int[] stop) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
