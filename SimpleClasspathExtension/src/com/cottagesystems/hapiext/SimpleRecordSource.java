
package com.cottagesystems.hapiext;

import java.util.Iterator;
import org.hapiserver.HapiRecord;
import org.hapiserver.HapiRecordSource;
import org.hapiserver.TimeUtil;

/**
 * Simple RecordSource for demonstrating interface
 * @author jbf
 */
public class SimpleRecordSource implements HapiRecordSource {

    public SimpleRecordSource() {
        System.err.println("Instantiate SimpleRecordSource");
    }
    
    @Override
    public boolean hasGranuleIterator() {
        return false;
    }

    @Override
    public Iterator<int[]> getGranuleIterator(int[] start, int[] stop) {
        throw new UnsupportedOperationException("Not Applicable Here");
    }

    @Override
    public boolean hasParamSubsetIterator() {
        return false;
    }

    @Override
    public Iterator<HapiRecord> getIterator(int[] start, int[] stop, String[] params) {
        throw new UnsupportedOperationException("Not Applicable Here");
    }

    @Override
    public Iterator<HapiRecord> getIterator(int[] start, int[] stop) {
        int[] first= new int[] { start[0], start[1], start[2], 0, 0, 0, 0 };
        return new Iterator<HapiRecord>() {
            
            int[] current= first;
            int count=0;
            
            @Override
            public boolean hasNext() {
                return TimeUtil.gt( stop, current );
            }

            @Override
            public HapiRecord next() {
                count++;
                HapiRecord rec= getHapiRecord( current );
                current= TimeUtil.add( current, new int[] { 0,0,0,0,0,15,0 } );
                return rec;
            }
            
        };
            
    }

    private HapiRecord getHapiRecord(int[] current) {
        return new HapiRecord() {
            @Override
            public String getIsoTime(int i) {
                return TimeUtil.formatIso8601Time(current).substring(0,23) + "Z";
            }

            @Override
            public String[] getIsoTimeArray(int i) {
                throw new UnsupportedOperationException("Not supported yet."); 
            }

            @Override
            public String getString(int i) {
                throw new UnsupportedOperationException("Not supported yet."); 
            }

            @Override
            public String[] getStringArray(int i) {
                throw new UnsupportedOperationException("Not supported yet."); 
            }

            @Override
            public double getDouble(int i) {
                return current[5];
            }

            @Override
            public double[] getDoubleArray(int i) {
                throw new UnsupportedOperationException("Not supported yet."); 
            }

            @Override
            public int getInteger(int i) {
                throw new UnsupportedOperationException("Not supported yet."); 
            }

            @Override
            public int[] getIntegerArray(int i) {
                throw new UnsupportedOperationException("Not supported yet."); 
            }

            @Override
            public String getAsString(int i) {
                throw new UnsupportedOperationException("Not supported yet."); 
            }

            @Override
            public int length() {
                return 2;
            }
        };
    }

    @Override
    public String getTimeStamp(int[] start, int[] stop) {
        return null;
    }

}