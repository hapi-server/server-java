
package org.hapiserver.source;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Logger;
import org.hapiserver.ExtendedTimeUtil;
import org.hapiserver.TimeUtil;
import org.hapiserver.Util;

/**
 *
 * @author jbf
 */
public class SourceUtil {
    
    private static final Logger logger= Util.getLogger();
    
    private static class AsciiFileIterator implements Iterator<String> {

        BufferedReader reader;
        String line;
        
        public AsciiFileIterator( File file ) throws IOException {
            this.reader= new BufferedReader( new FileReader(file) );
            this.line= reader.readLine();
        }
        
        @Override
        public boolean hasNext() {
            return line!=null;
        }

        @Override
        public String next() {
            try {
                String t= line;
                line= reader.readLine();
                return t;
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
    
    public static Iterator<String> getFileLines( File f ) throws FileNotFoundException, IOException {
        return new AsciiFileIterator(f);
    }
    
    /**
     * return an iterator counting from start to stop in increments.  For example, 
     * if digit is 2 (d of YmdHMSN), then this will count off days.  The first 
     * interval will include start, and the last will include stop if it is not at
     * a boundary.
     * @param start start time
     * @param stop end time
     * @param digit 14-digit range
     * @return iterator for the intervals.
     */
    public static Iterator<int[]> getGranuleIterator( int[] start, int[] stop, int digit ) {
        int[] first= Arrays.copyOf( start, 7 );
        for ( int i=digit+1; i<TimeUtil.TIME_DIGITS; i++ ) {
            first[i]=0;
        }
        return new Iterator<int[]>() {
            @Override
            public boolean hasNext() {
                return !ExtendedTimeUtil.gt( first, stop );
            }
            @Override
            public int[] next() {
                int[] result= new int[ TimeUtil.TIME_DIGITS * 2 ]; 
                System.arraycopy( first, 0, result, 0, 7 );
                first[digit]= first[digit]+1;
                TimeUtil.normalizeTime( first );
                System.arraycopy( first, 0, result, 7, 7 );
                return result;
            }
        };
    }
}
