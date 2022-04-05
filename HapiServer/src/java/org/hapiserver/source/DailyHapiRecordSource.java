
package org.hapiserver.source;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import org.hapiserver.AbstractHapiRecord;
import org.hapiserver.HapiRecord;
import org.hapiserver.HapiRecordSource;
import org.hapiserver.TimeUtil;

/**
 * given one ASCII file with time and double, return iterator.  This will likely be deprecated.
 * @author jbf
 */
public class DailyHapiRecordSource extends AbstractHapiRecordSource {

    /**
     * take a string like "/tmp/$Y/$Y$m$d.csv" and convert it into a
     * template used with String.format, so that DailyHapiRecordSource 
     * can be used.  Note this does not support all of the aggregations
     * possible with URI_Templates, only $Y, $m, $d, $H, $M, $S are
     * allowed, and only CSV-formatted files which match the info
     * response are allowed.  Also, $(Y;end) $(m;end) $(d;end) $(H;end) $(M;end) $(S;end) are supported.
     * Note $j is not supported.
     * 
     * String ins= "$Y/28.FF6319A21705.$Y$m$d.csv" ;
     * String out= "%1$04d/28.FF6319A21705.%1$04d%2$02d%3$02d.csv" ;
     * @param agg the aggregation specification.
     * @param nfield number of fields in each file
     * @return 
     */
    public static HapiRecordSource fromAggregation(String agg,int nfield) {
        String[] ss= agg.split("\\$",-2);
        StringBuilder build= new StringBuilder(ss[0]);
        boolean end=false;
        boolean begin=!end;
        int resolution= -1;
        String nonCodeStuff;
        
        for ( int i=1; i<ss.length; i++ ) {
            String s= ss[i];
            if ( s.length()==0 ) throw new IllegalArgumentException("$$ cannot be in template");
            char c= s.charAt(0);
            if ( c=='(' ) {
                if ( s.length()==7 && s.substring(2,7).equals(";end)") ) {
                    begin= false;
                    c= s.charAt(1);
                    nonCodeStuff= s.substring(7);
                } else {
                    throw new IllegalArgumentException("unable to parse parenthesis with "+s);
                }
            } else {
                nonCodeStuff= s.substring(1);
            }
            switch ( c ) {
                case 'Y': build.append( begin ? "%1$04d" : "%8$04d"); resolution=Math.max(0,resolution); break;
                case 'm': build.append( begin ? "%2$02d" : "%9$02d"); resolution=Math.max(1,resolution); break;
                case 'd': build.append( begin ? "%3$02d" : "%10$02d"); resolution=Math.max(2,resolution); break;
                case 'H': build.append( begin ? "%4$02d" : "%11$02d"); resolution=Math.max(3,resolution); break;
                case 'M': build.append( begin ? "%5$02d" : "%12$02d"); resolution=Math.max(5,resolution); break;
                case 'S': build.append( begin ? "%6$02d" : "%13$02d"); resolution=Math.max(5,resolution); break;
            }
            build.append(nonCodeStuff);
        }
        
        return new DailyHapiRecordSource( build.toString(), nfield, resolution );
    }

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
    public Iterator<HapiRecord> getIterator(int[] start, int[] stop, String[] params) {
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
