package org.hapiserver.source.tilde.geonet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import org.hapiserver.AbstractHapiRecord;
import org.hapiserver.AbstractHapiRecordSource;
import org.hapiserver.HapiRecord;
import org.hapiserver.source.AggregationGranuleIterator;

/**
 *
 * @author jbf
 */
public class DartDataHapiRecordSource extends AbstractHapiRecordSource {

    String id;
    String location;
    
    public DartDataHapiRecordSource( String id ) {
        if ( id.charAt(3)!='_') throw new IllegalArgumentException("expecting underscore in string, as in NZA_40");
        this.id= id.substring(0,3);
        this.location= id.substring(id.length()-2);
    }
    
    @Override
    public boolean hasGranuleIterator() {
        return true;
    }

    @Override
    public Iterator<int[]> getGranuleIterator(int[] start, int[] stop) {
        return new AggregationGranuleIterator("$Y-$m-$d", start, stop );
    }

    @Override
    public boolean hasParamSubsetIterator() {
        return false;
    }

    @Override
    public Iterator<HapiRecord> getIterator(int[] start, int[] stop) {
        try {
            String requestTemplate= "https://tilde.geonet.org.nz/v4/data/dart/%s/water-height/%s/15s/nil/%s/%s";
            String starts= String.format( "%04d-%02d-%02d",start[0],start[1],start[2]);
            String request= String.format( requestTemplate, id, location, starts, starts );
            URL url= new URL(request);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Accept", "text/csv");
            
            BufferedReader read= new BufferedReader( new InputStreamReader( conn.getInputStream() ) );
            
            return getRecordIterator( read );
            
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private Iterator<HapiRecord> getRecordIterator(final BufferedReader read) {
        final Iterator<String> linesStream = read.lines().iterator();
        String header= linesStream.next();
        
        return new Iterator<HapiRecord>() {
            @Override
            public boolean hasNext() {
                return linesStream.hasNext();
            }

            @Override
            public HapiRecord next() {
                String[] s= linesStream.next().split(",");
                return new AbstractHapiRecord() {
                    @Override
                    public String getIsoTime(int i) {
                        return s[6];
                    }

                    @Override
                    public String getString(int i) {
                        return s[6+i];
                    }

                    @Override
                    public double getDouble(int i) {
                        assert i==1;
                        return Double.parseDouble(s[7]);
                    }

                    @Override
                    public int length() {
                        return 2;
                    }
                };
            }
            
        };
    }
    
    
    
    
}
