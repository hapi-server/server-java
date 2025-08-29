
package org.hapiserver;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import static org.hapiserver.CsvDataFormatter.getNumberOfElements;
import static org.hapiserver.CsvDataFormatter.trimExpandTime;

/**
 * Format to JSON
 * @author jbf
 */
public class JsonDataFormatter implements DataFormatter {

    private static final Logger logger= Logger.getLogger("hapi.csv");
    
    Charset CHARSET= Charset.forName("UTF-8");
    
    boolean[] unitsFormatter;
    int[] types;
    JSONArray[] sizes;
    String[] exampleTimes;

    private final int TYPE_ISOTIME=0;
    private final int TYPE_STRING=9;
    private final int TYPE_DOUBLE=1;
    private final int TYPE_DOUBLE_ARRAY=2;
    private final int TYPE_INTEGER=3;
    private final int TYPE_INTEGER_ARRAY=4;
    
    /**
     * true if the field needs to be quoted.
     */
    boolean[] quotes;
    
    /**
     * the lengths of each field, for isotime and string types.
     */
    int[] lengths;
    String[] fill;
    double[] dfill;
    
    /**
     * format style for each field for CSV
     */
    private String[] formats;
        
    @Override
    public void initialize( JSONObject info, OutputStream out, HapiRecord record) {
        try {
            int len= record.length();
            quotes= new boolean[len];
            lengths= new int[len];
            fill= new String[len];
            dfill= new double[len];
            types= new int[len];
            sizes= new JSONArray[len];
            exampleTimes= new String[len];
            formats= new String[len];
            
            int[] lens= getNumberOfElements(info);
            JSONArray parameters= info.getJSONArray("parameters");
            int iparam=0;
            int iele=0;
            
            for ( int i=0; i<record.length(); i++ ) {
                JSONObject parameter= parameters.getJSONObject(i);
                lengths[i]= parameter.has("length") ? parameter.getInt("length") : 1;
                if ( parameter.has("x_format") ) {
                    String f= parameter.getString("x_format");
                    if ( !f.startsWith("%") ) {
                        f= "%"+f;
                    }
                    formats[i]= f;
                } else {
                    formats[i]=null;
                }
                if ( parameter.has("fill") ) {
                    fill[i]= parameter.getString("fill");
                } else {
                    fill[i]= null;
                }
                
                switch ( parameter.getString("type") ) {
                    case "isotime": 
                        types[i]= TYPE_ISOTIME; 
                        if ( record.getIsoTime(i).charAt(8)=='T' ) {
                            exampleTimes[i]= trimExpandTime( "2000-001T00:00:00.000000000Z", lengths[i], false );
                        } else {
                            if ( lengths[i]>30 ) { // CDF_EPOCH16
                                exampleTimes[i]= trimExpandTime( "2000-01-01T00:00:00.000000000000Z", 30, false );
                            } else {
                                exampleTimes[i]= trimExpandTime( "2000-01-01T00:00:00.000000000Z", lengths[i], false );
                            }
                        }
                        String field= TimeUtil.reformatIsoTime(exampleTimes[i],record.getIsoTime(i));
                        if ( field.charAt(field.length()-1)!='Z' ) throw new RuntimeException("isotime should end in Z");
                    break;
                    case "integer": {
                        if ( parameter.has("size") ) {
                            types[i]= TYPE_INTEGER_ARRAY;
                            sizes[i]= parameter.getJSONArray("size");
                        } else {
                            types[i]= TYPE_INTEGER;
                        }
                    }
                    break;
                    case "double": {
                        if ( parameter.has("size") ) {
                            types[i]= TYPE_DOUBLE_ARRAY;
                            sizes[i]= parameter.getJSONArray("size");
                        } else {
                            types[i]= TYPE_DOUBLE;
                        }
                        if ( fill[i]!=null ) {
                            dfill[i]= Double.parseDouble(fill[i]);
                        } else {
                            dfill[i]= Double.NaN;
                        }
                    } 
                    break;
                    case "string": {
                        types[i]= TYPE_STRING;
                        field= record.getString(i);
                        if ( field.length()>lengths[i] ) {
                            logger.log(Level.WARNING, "string field is longer than info length ({0}): {1}", new Object[]{lengths[i], parameter.getString("name")});
                        }
                    } 
                    break;
                    default:
                        throw new RuntimeException("\"" +parameter.getString("type")+ "\" type not supported.  Must be one of: isotime, integer, double, or string." );
                    
                }
            }
            for ( int i=0; i<record.length(); i++ ) {
                switch ( types[i] ) {
                    case TYPE_ISOTIME:
                    case TYPE_DOUBLE:
                    case TYPE_DOUBLE_ARRAY:
                        quotes[i]= false;
                        break;
                    case TYPE_INTEGER:
                    case TYPE_INTEGER_ARRAY:
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
                        JSONObject parameter= parameters.getJSONObject(iparam);
                    }
                }
            }
            
            //  Get the JSON string of this, but clip off the closing }, so that
            //  we can stream the data within the same JSON object.
            try {
                String infoSrc= info.toString(4);
                int i= infoSrc.lastIndexOf("}");
                int i1= infoSrc.lastIndexOf("\"", i);  // TODO: assumes last item is a string
                out.write( infoSrc.substring(0,i1+1).getBytes(CHARSET) );
                out.write( ",\n".getBytes(CHARSET) );
                out.write( "    \"data\":[\n".getBytes(CHARSET) );
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }

        } catch (JSONException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        
    }
    
    @Override
    public void sendRecord(OutputStream out, HapiRecord record) throws IOException {
        
        int n= record.length();
        StringBuilder build= new StringBuilder();
        
        build.append("[");
        
        for ( int i=0; i<n; i++ ) {
            String s;
            if ( i>0 ) build.append(",");
            switch ( types[i] ) {
                case TYPE_ISOTIME:
                    try {
                        s= TimeUtil.reformatIsoTime(exampleTimes[i],record.getIsoTime(i));
                        if ( lengths[i]>30 ) {
                            s= s.substring(0,29)+"000".substring(33-lengths[i])+"Z";
                        }
                        build.append('"');
                        build.append(s);
                        build.append('"');
                    } catch ( IllegalArgumentException ex ) {
                        s= TimeUtil.reformatIsoTime(exampleTimes[i],record.getIsoTime(i));
                        build.append('"');
                        build.append(s);
                        build.append('"');
                    }
                    break;
                case TYPE_STRING: 
                    build.append('"');
                    s= record.getString(i);
                    build.append(s);
                    build.append('"');
                    break;
                case TYPE_DOUBLE:
                    double d= record.getDouble(i);
                    if ( d==dfill[i] ) { // there are multiple ways to format a double, use the canonical one.
                        s= fill[i];
                    } else {
                        if ( formats[i]==null ) {
                            s= String.valueOf(d);
                        } else {
                            s= String.format(formats[i], d);
                        }
                    }
                    build.append(s);
                    break;
                case TYPE_DOUBLE_ARRAY:
                    double[] dd= record.getDoubleArray(i);
                    try {
                        doMultiArray(i,formats[i],dfill[i],sizes[i],dd,0,dd.length,build);
                    } catch ( JSONException ex ) {
                        throw new RuntimeException(ex);
                    }
                    break;
                case TYPE_INTEGER:
                    s= String.valueOf(record.getInteger(i) );
                    build.append(s);
                    break;
                case TYPE_INTEGER_ARRAY:
                    build.append("[");
                    int[] ii= record.getIntegerArray(i);
                    for ( int j=0; j<ii.length; j++ ) {
                            if ( j>0 ) build.append(",");
                            build.append(ii[j]);
                    }
                    build.append(']');
                    break;
            }
        }
        
        build.append("]");
                
        out.write( build.toString().getBytes(CHARSET) );
        // Note this should not write the comma and newline, kludge for JSON will handle this.

    }

    @Override
    public void finalize(OutputStream out) throws IOException {
        out.write("    ]\n}\n".getBytes(CHARSET));
    }

    
    private void doMultiArray1D( int i, String format, double dfill, JSONArray size, double[] dd, int offset, int len, StringBuilder build ) {
        build.append('[');
        int lastOffset= offset+len;
        for ( int j=offset; j<lastOffset; j++ ) {
            double d= dd[j];
            if ( j>offset ) build.append(",");
            if ( d==dfill ) {
                build.append(fill[i]);
            } else {
                if ( format==null ) {
                    build.append(d);
                } else {
                    build.append(String.format( format,d ));
                }
            }
        }
        build.append(']');
    }
    
    private void doMultiArray( int i, String format, double dfill, JSONArray size, double[] dd, int offset, int len, StringBuilder build) throws JSONException {
        int len0, len1, ind;
        switch ( size.length() ) {
            case 0:
                throw new IllegalArgumentException("can not get here 291");
            case 1:
                doMultiArray1D( i, format, dfill, size, dd, offset, len, build );
                break;
            default:
                build.append('[');
                len0= size.optInt(0);
                JSONArray subsize= new JSONArray(size.length()-1);
                int nele=1;
                for ( int j=1; j<size.length(); j++ ) {
                    int l= size.getInt(j);
                    subsize.put(j-1,l);
                    nele= nele*l;
                }
                for ( int k=0; k<len0; k++ ) {
                    if ( k>0 ) build.append(",");
                    doMultiArray( i, format, dfill, subsize, dd, offset, nele, build );
                    offset= offset+nele;
                }
                break;
        }
    }

}
