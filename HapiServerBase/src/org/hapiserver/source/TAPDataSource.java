package org.hapiserver.source;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.hapiserver.AbstractHapiRecordSource;
import org.hapiserver.HapiRecord;

public class TAPDataSource extends AbstractHapiRecordSource {

    private static final Logger logger = Logger.getLogger("hapi.cef");

    private final String tapServerURL;
    private final String id;

    public TAPDataSource(String tapServerURL, String id) {
        this.tapServerURL = tapServerURL;
        this.id = id;

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
        String startTimeString = formatTime(start);
        String endTimeString = formatTime(stop);

        String queryString = tapServerURL + "data?RETRIEVAL_TYPE=product&RETRIEVAL_ACCESS=streamed&DATASET_ID=" + id
            + "&START_DATE=" + startTimeString + "&END_DATE=" + endTimeString;
        logger.log(Level.FINE, "Querying: {0}", queryString);
        try {
            URL uu = new URL(queryString);
            InputStream in = uu.openStream();
            ReadableByteChannel lun = Channels.newChannel(in);
            CefFileIterator iter = new CefFileIterator(lun);

            return iter;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String formatTime(int[] time) {
        String timeString = String.format("%4d-%02d-%02dT%02d:%02d:%02dZ",
            time[0], time[1], time[2], time[3], time[4], time[5]);
        return timeString;
    }

    public static void main( String[] args ) {
        String tapServerURL="https://csa.esac.esa.int/csa-sl-tap/";
        String id= "CL_SP_WHI";
        int[] start= new int[] { 2012, 12, 25, 0, 0, 0, 0 };
        int[] stop= new int[] { 2012, 12, 26, 0, 0, 0, 0 };
        Iterator<HapiRecord> iter= new TAPDataSource(tapServerURL, id).getIterator(start, stop);
        while ( iter.hasNext() ) {
            HapiRecord r= iter.next();
            System.err.println( String.format( "%s %d fields", r.getIsoTime(0), r.length() ) );
        }
    }
}
