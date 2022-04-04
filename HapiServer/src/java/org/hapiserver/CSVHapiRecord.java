
package org.hapiserver;

import java.util.Map;
import org.codehaus.jettison.json.JSONObject;

/**
 * HapiRecord backed by CSV response.
 * @author jbf
 */
public class CSVHapiRecord implements HapiRecord {
        
    String[] fields;
    Map<Integer,Integer> indexMap;
    
    public CSVHapiRecord( JSONObject info, String[] fields ) {
        this.fields= fields;   
    }
    
    @Override
    public String getIsoTime(int i) {
        return fields[i];
    }
    
    @Override
    public String[] getIsoTimeArray(int i) {
        String[] result= fields[i].split(",");
        return result;
    }    

    @Override
    public String getString(int i) {
        return fields[i];
    }
    
    @Override
    public String[] getStringArray(int i) {
        String[] result= fields[i].split(","); // note no commas are allowed in fields.
        return result;
    }    

    @Override
    public double getDouble(int i) {
        return Double.valueOf(fields[i]);
    }

    @Override
    public double[] getDoubleArray(int i) {
        String[] ss= fields[i].split(",");
        double[] result= new double[ss.length];
        for ( int j=0; j<ss.length; j++ ) {
            result[j]= Double.parseDouble(ss[j]);
        }
        return result;
    }

    @Override
    public int getInteger(int i) {
        return Integer.valueOf(fields[i]);
    }

    @Override
    public int[] getIntegerArray(int i) {
        String[] ss= fields[i].split(",");
        int[] result= new int[ss.length];
        for ( int j=0; j<ss.length; j++ ) {
            result[j]= Integer.parseInt(ss[j]);
        }
        return result;
    }
    
    @Override
    public int length() {
        return fields.length;
    }
    
    @Override
    public String toString() {
        return String.format( "%s: %d fields", fields[0], fields.length );
    }

    @Override
    public String getAsString(int i) {
        return fields[i];
    }
}