
package org.hapiserver;

import java.util.Map;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 * convert CSV records into HapiRecords.
 * @author jbf
 */
public class CsvHapiRecordConverter {
    
    Map<Integer,Integer> indexMap;
    JSONObject info;
    JSONArray params;
    int[] sizes;
    
    public CsvHapiRecordConverter( JSONObject info ) throws JSONException {
        this.info= info;
        this.params= info.getJSONArray("parameters");
        this.sizes= new int[params.length()];
        for ( int i=0; i<params.length(); i++ ) {
            JSONObject jo= params.getJSONObject(i);
            if ( jo.has("size") ) {
                JSONArray size= jo.getJSONArray("size");
                sizes[i]= size.getInt(0);
                for ( int j=1; j<size.length(); j++ ) {
                    sizes[i]*= size.getInt(j);
                }
            } else {
                sizes[i]= 1;
            }
        }
    }

    /**
     * convert the line into a HapiRecord, breaking the string on commas.
     * @param record the line containing the ASCII-encoded data.
     * @return a HapiRecord containing the data.
     */
    public HapiRecord convert( String record ) {
        String[] fields= record.trim().split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)",-2);
        String[] ff= new String[params.length()];
        int i=0;
        for ( int j=0; j<params.length(); j++ ) {
            if ( sizes[j]==1 ) {
                ff[j]= fields[i];
                i=i+1;
            } else {
                StringBuilder build= new StringBuilder(fields[i]);
                for ( int k=1; k<sizes[j]; k++ ) {
                    build.append(",").append(fields[i+k]);
                }
                ff[j]= build.toString();
                i+=sizes[j];
            }
        }
        return new CsvHapiRecord(info,ff);
    }
}