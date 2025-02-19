package org.hapiserver.source.tap;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.AbstractHapiRecordSource;
import org.hapiserver.HapiRecord;
import org.hapiserver.TimeUtil;

/**
 * provide the data stream from the ESAC TAP server
 * @author jbf
 */
public class TAPDataSource extends AbstractHapiRecordSource {

    private static final Logger logger = Logger.getLogger("hapi.cef");

    private final String tapServerURL;
    private final String id;
    private final JSONObject info;
    
    private InputStream in=null;

    public TAPDataSource(String tapServerURL, String id, JSONObject info) {
        if ( info==null ) {
            throw new NullPointerException("info is null, check configuration to make sure info is passed in.");
        }
        this.tapServerURL = tapServerURL;
        this.id = id;
        this.info= info;
    }

    public TAPDataSource(String tapServerURL, String id) {
        this(tapServerURL,id,null);
    }

    @Override
    public boolean hasGranuleIterator() {
        return false;
    }

    @Override
    public boolean hasParamSubsetIterator() {
        return false;
    }

    @Override
    public Iterator<HapiRecord> getIterator(int[] start, int[] stop) {
        String startTimeString;
        String stopTimeString;
        int minimumDurationNs=200000000;
        int[] duration = TimeUtil.subtract(stop, start);
        if ( duration[0]==0 && duration[1]==0 && duration[2]==0 
                && duration[3]==0 && duration[4]==0 && duration[5]==0 
                && duration[6]<minimumDurationNs ) {
            startTimeString = formatTime(start);
            stopTimeString = formatTime( TimeUtil.add( start, new int[] { 0, 0, 0, 0, 0, 0, minimumDurationNs } ) );
        } else {
            startTimeString = formatTime(start);
            stopTimeString = formatTime(stop);
        }
        
        String queryString = tapServerURL + "data?RETRIEVAL_TYPE=product&RETRIEVAL_ACCESS=streamed&DATASET_ID=" + id
            + "&START_DATE=" + startTimeString + "&END_DATE=" + stopTimeString;
        logger.log(Level.FINE, "Querying: {0}", queryString);
        try {
            URL uu = new URL(queryString);
            in = uu.openStream();
            ReadableByteChannel lun = Channels.newChannel(in);
            CefFileIterator iter = new CefFileIterator(lun,info);

            return iter;
        } catch (IOException e) {
            try {
                if ( in!=null ) in.close();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
            throw new RuntimeException(e);
        }
    }

    @Override
    public void doFinalize() {
        if ( in!=null ) {
            try {
                in.close();
            } catch ( IOException ex ) {
                logger.log( Level.WARNING, ex.getMessage(), ex );
            }
        }
    }

    
    private String formatTime(int[] time) {
        String timeString = String.format("%4d-%02d-%02dT%02d:%02d:%02dZ",
            time[0], time[1], time[2], time[3], time[4], time[5]);
        return timeString;
    }

}
