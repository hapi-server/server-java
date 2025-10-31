
package org.hapiserver.source;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.CsvHapiRecordConverter;
import org.hapiserver.HapiRecord;
import org.hapiserver.TimeString;
import org.hapiserver.URITemplate;

/**
 * given one pre-formatted CSV file with a known number of fields, return iterator.  This will likely be deprecated.
 * @author jbf
 */
public class AggregationRecordSource extends AbstractHapiRecordSource {

    String fileFormat;
    URITemplate uriTemplate;
    int nfield;
    CsvHapiRecordConverter recordConverter;
    
    /**
     * create a RecordSource  which iterates through the records in the pre-formatted CSV file.
     * @param hapiHome
     * @param id
     * @param info
     * @param dataConfig
     */
    public AggregationRecordSource( String hapiHome, String id, JSONObject info, JSONObject dataConfig ) {
        this.fileFormat= dataConfig.optString("files","");
        if ( this.fileFormat.length()==0 ) throw new IllegalArgumentException("files is empty or missing");
        this.uriTemplate= new URITemplate(fileFormat);
        this.nfield= info.optJSONArray("parameters").length();
        try {
            recordConverter= new CsvHapiRecordConverter(info);
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public boolean hasGranuleIterator() {
        return true;
    }
    
    @Override
    public Iterator<TimeString[]> getGranuleIterator(TimeString start, TimeString stop) {
        return new AggregationGranuleIterator( fileFormat, start, stop );
    }

    @Override
    public boolean hasParamSubsetIterator() {
        return false;
    }

    @Override
    public Iterator<HapiRecord> getIterator(TimeString start, TimeString stop, String[] params) {
        throw new IllegalArgumentException("not supported");
    }

    @Override
    public Iterator<HapiRecord> getIterator(TimeString start, TimeString stop) {
        
        String file= uriTemplate.format( start.toString(), stop.toString() );
        try {
            
            Iterator<String> iter;
            
            if ( file.startsWith(FILE_URL_PROTOCOL) ) {
                File ff= new File(file.substring(FILE_URL_PROTOCOL.length()));
                if ( !ff.exists() ) {
                    return SourceUtil.getEmptyHapiRecordIterator();
                }
                iter= SourceUtil.getFileLines( ff );
            } else {
                URL url= new URL(file);
                iter= SourceUtil.getFileLines( url );
            }
            
            return new Iterator<HapiRecord>() {
                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }
                @Override
                public HapiRecord next() {
                    String line= iter.next();
                    return recordConverter.convert(line);
                }
            };
        } catch (FileNotFoundException ex) {
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    private static final String FILE_URL_PROTOCOL = "file:";

    @Override
    public void doFinalize() {
        
    }
    
}
