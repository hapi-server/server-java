
package org.hapiserver.source;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.CsvHapiRecordConverter;
import org.hapiserver.HapiRecord;
import org.hapiserver.TimeUtil;
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
        if ( this.fileFormat.startsWith("file:") ) {
            throw new IllegalArgumentException("fileFormat should not start with file:, just /foo/bar/...");
        }
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
    public Iterator<int[]> getGranuleIterator(int[] start, int[] stop) {
        return new AggregationGranuleIterator( fileFormat, start, stop );
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
        
        String file= uriTemplate.format( TimeUtil.formatIso8601Time(start), TimeUtil.formatIso8601Time(stop) );
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
                    return recordConverter.convert(line);
                }
            };
        } catch (FileNotFoundException ex) {
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    
}
