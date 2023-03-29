
package org.hapiserver.source;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.hapiserver.HapiRecord;
import org.hapiserver.TimeUtil;

/**
 *
 * @author jbf
 */
public class SourceUtil {
    
    private static final Logger logger= Logger.getLogger("hapi");
    
    private static boolean lineStartsWithTimeTag( String line ) {
        if ( line.length()<2 ) {
            return false;
        } else if ( line.charAt(0)=='1' ) {
            switch ( line.charAt(1) ) {
                case 6:
                case 7:
                case 8:
                case 9:
                    return true;
                default:
                    return false;
            }
        } else if ( line.charAt(0)=='2' ) {
            return Character.isDigit(line.charAt(1) );
        } else if ( Character.isDigit(line.charAt(0) ) ) {
            // TODO: check upper limits of times.
            return false;
        } else {
            return false;
        }
    }
    
    private static class AsciiSourceIterator implements Iterator<String> {

        BufferedReader reader;
        String line;
        
        public AsciiSourceIterator( File file ) throws IOException {
            try {
                this.reader= new BufferedReader( new FileReader(file) );
                this.line= reader.readLine();
                // allow for one or two header lines.
                int headerLinesLimit = 2;
                int iline= 1;
                while ( line!=null && iline<=headerLinesLimit ) {
                    if ( lineStartsWithTimeTag(line) ) {
                        break;
                    } else {
                        logger.finer("advance to next line because this appears to be header: ");
                        this.line= reader.readLine();
                        iline= iline+1;
                    } 
                }
            } catch ( IOException ex ) {
                this.reader.close();
                throw ex;
            }
        }
        
        public AsciiSourceIterator( URL url ) throws IOException {
            try {
                this.reader= new BufferedReader( new InputStreamReader( url.openStream() ) );
                this.line= reader.readLine();
                // allow for one or two header lines.
                int headerLinesLimit = 2;
                int iline= 1;
                while ( line!=null && iline<=headerLinesLimit ) {
                    if ( lineStartsWithTimeTag(line) ) {
                         break;
                    } else {
                        logger.finer("advance to next line because this appears to be header: ");
                        this.line= reader.readLine();
                        iline= iline+1;
                    } 
                }
            } catch ( IOException ex ) {
                this.reader.close();
                throw ex;
            }
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
                try {
                    reader.close();
                } catch ( IOException ex1 ) {
                    // intentionally hide this exception--the first is the important one.
                }
                throw new RuntimeException(ex);
            }
        }
    }
    
    /**
     * return an iterator for each line of the ASCII file.
     * @param f a file
     * @return an iterator for the lines.
     * @throws FileNotFoundException
     * @throws IOException 
     */
    public static Iterator<String> getFileLines( File f ) throws FileNotFoundException, IOException {
        return new AsciiSourceIterator(f);
    }
    
    /**
     * return an iterator for each line of the URL file.
     * @param url a URL pointing to an ASCII file.
     * @return an iterator for the lines.
     * @throws FileNotFoundException
     * @throws IOException 
     */
    public static Iterator<String> getFileLines( URL url ) throws FileNotFoundException, IOException {
        return new AsciiSourceIterator(url);
    }
    
    /**
     * return the entire ASCII response as a string.
     * @param url a URL pointing to an ASCII file.
     * @return the content as a string.
     * @throws java.io.IOException 
     */
    public static String getAllFileLines( URL url ) throws IOException {
        StringBuilder sb= new StringBuilder();
        Iterator<String> s= getFileLines(url);
        while ( s.hasNext() ) {
            sb.append(s.next()).append("\n");
        }
        return sb.toString();
    }
    
    /**
     * return an empty iterator whose hasNext trivially returns false.
     * @return an iterator
     */
    public static Iterator<HapiRecord> getEmptyHapiRecordIterator() {
        return new Iterator<HapiRecord>() {
            @Override
            public boolean hasNext() {
                return false;
            }
            @Override
            public HapiRecord next() {
                throw new UnsupportedOperationException("iterator is used improperly");
            }
        };
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
                return !TimeUtil.gt( first, stop );
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
            
    /**
     * See https://stackoverflow.com/questions/18893390/splitting-on-comma-outside-quotes
     * which provides the regular expression for splitting a line on commas, but not commas within
     * quotes.
     */
    public static final String PATTERN_SPLIT_QUOTED_FIELDS_COMMA= ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)";
    
    /**
     * only split on the delimiter when we are not within the exclude delimiters.  For example,
     * <code>
     * 2022-02-02T02:02:02,"thruster,mode2,on",2
     * </code>
     * @param s the string to split.
     * @param delim the delimiter to split on, for example the ampersand (&).
     * @param exclude1 for example the single quote (")
     * @return the split.
     * 
     * This is a copy of another code.  See org.autoplot.jythonsupport.Util.java
     * TODO: this makes an unnecessary copy of the string.  This should be re-implemented.
     */
    public static String[] guardedSplit( String s, char delim, char exclude1 ) {    
        if ( delim=='_') throw new IllegalArgumentException("_ not allowed for delim");
        StringBuilder scopyb= new StringBuilder(s.length());
        char inExclude= (char)0;
        
        for ( int i=0; i<s.length(); i++ ) {
            char c= s.charAt(i);
            if ( inExclude==0 ) {
                if ( c==exclude1 ) inExclude= c;
            } else {
                if ( c==inExclude ) inExclude= 0;
            }
            if ( inExclude>(char)0 ) c='_';
            scopyb.append(c);            
        }
        String[] ss= scopyb.toString().split(String.valueOf(delim),-2);
        
        int i1= 0;
        for ( int i=0; i<ss.length; i++ ) {
            int i2= i1+ss[i].length();
            ss[i]= s.substring(i1,i2);
            i1= i2+1;
        } 
        return ss;
    }
    
    /**
     * Split the CSV string into fields, minding commas within quotes should not be used to
     * split the string.  Finally, quotes around fields are removed.
     * @param s string like "C3_PP_CIS,\"Proton and ion densities, bulk velocities and temperatures, spin resolution\""
     * @return array like [ "C3_PP_CIS","Proton and ion densities, bulk velocities and temperatures, spin resolution" ]
     */
    public static String[] stringSplit( String s ) {
        String[] ss;
        if ( s.contains("\"") ) {
            ss= guardedSplit( s, ',', '"' );
            for ( int i=0; i<ss.length; i++ ) {
                int l= ss[i].length();
                if ( ss[i].charAt(0)=='"' && ss[i].charAt(l-1)=='"' ) {
                    ss[i]= ss[i].substring(1,l-1);
                }
            }
        } else {
            ss= s.split(",",-2);
        }
        return ss;
    }

}
