
package org.hapiserver.source;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import org.hapiserver.AbstractHapiRecord;
import org.hapiserver.HapiRecord;
import org.hapiserver.TimeUtil;

/**
 * given one ASCII file with time and double, return iterator
 * @author jbf
 */
public class DailyHapiRecordSource extends AbstractHapiRecordSource {

    String fileFormat;
    int digit;
    int nfield;
    
    /**
     * create a RecordSource which iterates through the records in the daily pre-formatted CSV files.
     * @param fileFormat
     * @param nfield the number of fields
     */
    public DailyHapiRecordSource( String fileFormat, int nfield ) {
        this( fileFormat, 2, nfield );
    }
    
    /**
     * create a RecordSource  which iterates through the records in the pre-formatted CSV file.
     * @param fileFormat
     * @param digit
     * @param nfield 
     */
    public DailyHapiRecordSource( String fileFormat, int digit, int nfield ) {
        this.digit= digit;
        this.nfield= nfield;
        this.fileFormat= fileFormat;
    }
    
    @Override
    public boolean hasGranuleIterator() {
        return true;
    }
    
    @Override
    public Iterator<int[]> getGranuleIterator(int[] start, int[] stop) {
        return SourceUtil.getGranuleIterator( start, stop, digit );
    }

    @Override
    public boolean hasParamSubsetIterator() {
        return false;
    }

    @Override
    public Iterator<HapiRecord> getIterator(String[] params, int[] start, int[] stop) {
        throw new IllegalArgumentException("not supported");
    }

    @Override
    public Iterator<HapiRecord> getIterator(int[] start, int[] stop) {
        Object[] range= new Object[TimeUtil.TIME_DIGITS*2];
        for ( int i=0; i<TimeUtil.TIME_DIGITS; i++ ) {
            range[i]= start[i];
            range[i+TimeUtil.TIME_DIGITS]= stop[i];
        }
        String file= String.format( fileFormat, range );
        try {
            File ff= new File(file);
            if ( !ff.exists() ) {
                return SourceUtil.getEmptyHapiRecordIterator();
            }
            Iterator<String> iter= SourceUtil.getFileLines( ff );
            return new Iterator<HapiRecord>() {
                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }
                @Override
                public HapiRecord next() {
                    String line= iter.next();
                    String[] ss= line.split(","); // TODO: need to guard for quoted fields
                    HapiRecord rec= new AbstractHapiRecord() {
                        @Override
                        public String getIsoTime(int i) {
                            return ss[i];
                        }
                        @Override
                        public double getDouble(int i) {
                            return Double.parseDouble(ss[i]);
                        }
                        @Override
                        public int length() {
                            return nfield;
                        }
                    };
                    return rec;
                }
            };
        } catch (FileNotFoundException ex) {
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    
}
