
package org.hapiserver.source;

import hapi.cache.InputStreamProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.HapiRecord;
import org.hapiserver.TimeUtil;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
public class SourceUtil {
    
    private static final Logger logger= Logger.getLogger("hapi");
    
    private static boolean lineStartsWithTimeTag( String line ) {
        if ( line.startsWith("\"") ) {
            line= line.substring(1);
        }
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
        
        /**
         * read csv from the URL, advancing until the first timetag.
         * @param url
         * @throws IOException 
         */
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
                while ( line!=null && line.length()==0 ) {
                    line= reader.readLine();
                }
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
     * return an iterator for each line of the ASCII data file, only returning the records which 
     * start with timetags or quoted timetags.
     * @param f a file containing timetags.
     * @return an iterator for the lines.
     * @throws FileNotFoundException
     * @throws IOException 
     */
    public static Iterator<String> getFileLines( File f ) throws FileNotFoundException, IOException {
        return new AsciiSourceIterator(f);
    }
    
    /**
     * return an iterator for each line of the ASCII data URL, only returning the records which 
     * start with timetags or quoted timetags.
     * @param url a URL pointing to an ASCII file containing timetags.
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
        try ( BufferedReader r= new BufferedReader( new InputStreamReader( url.openStream() ) ) ) {
            for ( String line= r.readLine(); line!=null; line=r.readLine() ) {
                sb.append(line).append("\n");
            }
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
    
    /**
     * read the XML document from a remote site.
     * @param url the XML document
     * @return the XML document
     * @throws org.xml.sax.SAXException 
     * @throws java.io.IOException 
     * @throws javax.xml.parsers.ParserConfigurationException 
     */
    public static Document readDocument( URL url )  throws SAXException, IOException, ParserConfigurationException {
        try ( InputStream is= url.openStream() ) {
            DocumentBuilder builder;
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputSource source = new InputSource(new InputStreamReader(is));
            Document document = builder.parse(source);
            return document;
        }
    }
    
    /**
     * return an input stream for the resource, possibly using a cached copy which is no older than ageSeconds.
     * Note this should only be used for testing, and not in production use.
     * @param url the URL
     * @param ageSeconds the maximum allowed age in seconds.
     * @return an InputStream
     * @throws java.io.IOException
     */
    public static InputStream getInputStream( URL url, int ageSeconds ) throws IOException {
        InputStream result;
        boolean allowCaching= true;
        if ( allowCaching ) {
            String hash = Integer.toHexString(url.hashCode());
            File cacheFile= new File( "/tmp/HapiServerCache/" + url.getHost() + "/" + hash );
            if ( !cacheFile.getParentFile().exists() ) {
                if ( !cacheFile.getParentFile().mkdirs() ) {
                    logger.log(Level.FINE, "unable to mkdirs for {0}", cacheFile);
                }
            }
            if ( cacheFile.exists() ) {
                long timeTag= cacheFile.lastModified();
                if ( System.currentTimeMillis() - timeTag < ( ageSeconds * 1000 ) ) {
                    return new FileInputStream(cacheFile);
                }
            }
            InputStreamProvider urlInputStreamProvider= () -> url.openStream();
            InputStreamProvider bcisp= new BuildCacheInputStreamProvider( urlInputStreamProvider, cacheFile );
            
            result= bcisp.openInputStream();
            
        } else {
            result= url.openStream();
        }
        return result;
    }
    
    /**
     * read the XML document from a remote site, allowing cached response to be used.  The cache
     * of files is kept in /tmp/HapiServerCache/.
     * @param url the XML document
     * @param ageSeconds the age of the document allowed, since the last read.
     * @return the XML document
     * @throws org.xml.sax.SAXException 
     * @throws java.io.IOException 
     * @throws javax.xml.parsers.ParserConfigurationException 
     */
    public static Document readDocument( URL url, int ageSeconds )  throws SAXException, IOException, ParserConfigurationException {
        
        try ( InputStream is= getInputStream( url, ageSeconds ) ) {
            DocumentBuilder builder;
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputSource source = new InputSource(new InputStreamReader(is));
            Document document = builder.parse(source);
            return document;
        }
    }
    
    /**
     * read the XML document from a String.
     * @param src
     * @return the XML document
     * @throws SAXException
     * @throws IOException
     * @throws ParserConfigurationException 
     */
    public static Document readDocument( String src ) throws SAXException, IOException, ParserConfigurationException {
        StringReader reader= new StringReader(src);
        DocumentBuilder builder;
        builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        InputSource source = new InputSource( reader );
        Document document = builder.parse(source);
        return document;
    }
    
    /**
     * retrieve the parameter from the info.
     * @param info info JSON.
     * @param name name of the parameter
     * @return parameter JSONObject.
     * @throws IllegalArgumentException when the name is not found or JSON object does not follow schema.
     */
    public static JSONObject getParam( JSONObject info, String name ) {
        try {
            JSONArray array= info.getJSONArray("parameters");
            for ( int i=0; i<array.length(); i++ ) {
                JSONObject p= array.getJSONObject(i);
                if ( p.getString("name").equals(name) ) {
                    return p;
                }
            }
        } catch ( JSONException e ) {
            throw new IllegalArgumentException(e);
        }
        throw new IllegalArgumentException("name is not found: "+name);
    }
    
    /**
     * read the JSONObject from a remote site.
     * @param url the url
     * @return the JSONObject
     * @throws IOException 
     */
    public static JSONObject readJSONObject( URL url ) throws IOException {
        try {
            byte[] bb= Files.readAllBytes( Paths.get( url.toURI() ) );
            String s= new String( bb, Charset.forName("UTF-8") );
            JSONObject jo= new JSONObject(s);
            return jo;
        } catch (URISyntaxException | JSONException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();
    
    /**
     * Download the file, but if another thread is loading the same URL to the same
     * File, then wait for it to complete.  
     * @param url the URL to load
     * @param file the file accepting the result.
     * @param tmpFile the file accepting the data as the file is downloaded.
     * @return the downloaded file.
     */
    public static File downloadFileLocking( URL url, File file, String tmpFile ) throws IOException {
        String surl= url.toString();
        String key = surl + "::" + file.toString();
        ReentrantLock lock = lockMap.computeIfAbsent(key, k -> new ReentrantLock());

        lock.lock();
        try {
            
            if (file.exists()) {                
                logger.log(Level.WARNING, "File exists!  Someone else must have loaded it! {0}", new Object[]{file});
                return file;
            }

            logger.log(Level.FINE, "Downloading {0} to {1}", new Object[]{surl, file});
            try (InputStream in = url.openStream();
                 FileOutputStream out = new FileOutputStream(tmpFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                logger.log(Level.FINE, "Download complete: {0}", file);
            }
            new File(tmpFile).renameTo( file );
            
            return file;
        } finally {
            lock.unlock();
            // Optional: remove lock if no other threads are using it
            lockMap.remove(key, lock);
        }
    }
    
    /**
     * download the resource to the given file
     * @param url the URL to load
     * @param file name of the file where data should be written.
     * @return the name of the file
     * @throws IOException 
     */
    public static File downloadFile( URL url, File file ) throws IOException {
        // Get the URL of the file to download.
        
        // Open a connection to the URL.
        try ( InputStream inputStream = url.openStream(); OutputStream outputStream = new FileOutputStream(file) ) {
            // Copy the contents of the input stream to the output stream.
            byte[] buffer = new byte[10240];
            int bytesRead;
            long totalBytesRead=0;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytesRead+=bytesRead;
            }
        }
        return file;
       
    }
}
