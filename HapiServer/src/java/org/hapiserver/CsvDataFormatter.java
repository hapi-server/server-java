
package org.hapiserver;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;


/**
 * Comma Separated Value (CSV) formatter
 * @author jbf
 */
public class CsvDataFormatter implements DataFormatter {

    private static final Logger logger= Logger.getLogger("hapi.csv");
    
    Charset CHARSET= Charset.forName("UTF-8");
    
    boolean[] unitsFormatter;
    int[] types;
    
    private final int TYPE_ISOTIME=0;
    private final int TYPE_STRING=9;
    private final int TYPE_DOUBLE=1;
    private final int TYPE_DOUBLE_ARRAY=2;
    
    /**
     * true if the field needs to be quoted.
     */
    boolean[] quotes;
    
    /**
     * the lengths of each field, for isotime and string types.
     */
    int[] lengths;
    String[] fill;
    
    private static final Charset CHARSET_UTF8= Charset.forName("UTF-8");
    
    
    /**
     * return the parameter number for the column.
     * @param col
     * @return 
     */
    int columnMap( int col ) {
        return col;
    }
    
    @Override
    public void initialize( JSONObject info, OutputStream out, HapiRecord record) {
        try {
            quotes= new boolean[record.length()];
            lengths= new int[record.length()];
            fill= new String[record.length()];
            types= new int[record.length()];
            int[] lens= Util.getNumberOfElements(info);
            JSONArray parameters= info.getJSONArray("parameters");
            JSONObject parameter= parameters.getJSONObject(0);
            int iparam=0;
            int iele=0;
            
            for ( int i=0; i<record.length(); i++ ) {
                parameter= parameters.getJSONObject(i);
                lengths[i]= parameter.has("length") ? parameter.getInt("length") : 1;
                switch ( parameter.getString("type") ) {
                    case "isotime": 
                        types[i]= TYPE_ISOTIME; 
                        break;
                    case "double": {
                        if ( parameter.has("size") ) {
                            types[i]= TYPE_DOUBLE_ARRAY;
                        } else {
                            types[i]= TYPE_DOUBLE;
                        }
                    } break;
                    case "string": {
                        types[i]= TYPE_STRING;
                    } break;
                    default:
                        throw new RuntimeException("type not supported");
                    
                }
            }
            for ( int i=0; i<record.length(); i++ ) {
                parameter= parameters.getJSONObject(i);
                switch ( types[i] ) {
                    case TYPE_ISOTIME:
                    case TYPE_DOUBLE:
                    case TYPE_DOUBLE_ARRAY:
                        quotes[i]= false;
                        break;
                    case TYPE_STRING:
                        quotes[i]= true;
                }
                    
                iele++;
                if ( iele==lens[iparam] ) {
                    iparam++;
                    iele=0;
                    if ( iparam==parameters.length() ) {
                        if ( i+1!=record.length() ) {
                            throw new IllegalStateException("things have gone wrong");
                        }
                    } else {
                        parameter= parameters.getJSONObject(iparam);
                    }
                }
            }

        } catch (JSONException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        
    }

    
    @Override
    public void sendRecord(OutputStream out, HapiRecord record) throws IOException {
        int n= record.length();
        StringBuilder build= new StringBuilder();
        for ( int i=0; i<record.length(); i++ ) {
            String s;
            if ( i>0 ) build.append(",");
            switch ( types[i] ) {
                case TYPE_ISOTIME:
                    s= record.getIsoTime(i);
                    build.append(s);
                    break;
                case TYPE_STRING: 
                    if ( quotes[i] ) build.append('"');
                    s= record.getString(i);
                    build.append(s);
                    if ( quotes[i] ) build.append('"');
                    break;
                case TYPE_DOUBLE:
                    s= String.valueOf(record.getDouble(i) );
                    build.append(s);
                    break;
                case TYPE_DOUBLE_ARRAY:
                    double[] dd= record.getDoubleArray(i);
                    for ( int j=0; j<dd.length; j++ ) {
                        if ( j>0 ) build.append(",");
                        build.append(dd[j]);
                    }
                    
            }
        }
        out.write( build.toString().getBytes( CHARSET ) );
        out.write((byte)10);
        
    }
    
    @Override
    public void finalize(OutputStream out) {
        
    }

}
