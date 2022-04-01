
package org.hapiserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
/**
 *
 * @author jbf
 */
public class HapiClientCSVIterator implements Iterator<HapiRecord> {
    
    private static final Logger logger= Logger.getLogger("org.hapiserver");
    
    String nextLine;
    JSONObject info;
    BufferedReader reader;
    CSVHapiRecordConverter converter;
    
    /**
     * because JSON has been detected in the first line, read the rest of the
     * content from the BufferedReader.  Lines are read into builder, and a 
     * non-JSON line may be returned.  Either way the reader will not be closed.
     * 
     * @param reader
     * @param builder
     * @return the next line if read, or null.
     */
    private static String readJSON( BufferedReader reader, StringBuilder builder ) throws IOException {
        String line= reader.readLine();
        while ( line!=null ) {
            builder.append(line);
            line= reader.readLine();
        }
        return line; // this implementation assumes there is no data following.
    }
    
    /**
     * create an iterator for the CSV stream.
     * @param info the info describing the fields.
     * @param reader buffered reader providing each parseable line.
     * @throws IOException when there is an issue reading the data.
     * @throws org.codehaus.jettison.json.JSONException
     */
    public HapiClientCSVIterator(JSONObject info, BufferedReader reader) throws IOException, JSONException {
        this.info= info;
        this.reader = reader;
        this.nextLine = this.reader.readLine();
        if ( this.nextLine!=null && this.nextLine.startsWith("{") ) {
            StringBuilder b= new StringBuilder(this.nextLine);
            this.nextLine= readJSON( reader, b );
            if ( this.nextLine==null ) {
                reader.close();
            }
            JSONObject o= new JSONObject(b.toString());
            if ( !o.has("status") ) {
                throw new IllegalArgumentException("expected to see status in JSON response.");
            } else {
                JSONObject status= o.getJSONObject("status");
                int statusCode= status.getInt("code");
                if ( statusCode==1201 ) {
                    logger.fine("do nothing, this.nextLine is already set.");
                } else {
                    throw new IOException("error from server: "+statusCode+ " " +status.getString("message") );
                }
            }
        }
        this.converter= new CSVHapiRecordConverter(info);
    }

    @Override
    public boolean hasNext() {
        boolean result = nextLine != null;
        return result;
    }

    @Override
    public HapiRecord next() {
        if (this.nextLine == null) {
            throw new NoSuchElementException("No more records");
        }        
        HapiRecord result = this.converter.convert(this.nextLine);
        try {
            nextLine = reader.readLine();
            if ( nextLine==null ) {
                reader.close();
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException(ex);
        }
        return (HapiRecord) result;
    }
    
}
