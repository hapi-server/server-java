
package org.hapiserver;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.IllegalFormatException;
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
    
    private static final Charset CHARSET_UTF8= Charset.forName("UTF-8");
    
    
    /**
     * return the parameter number for the column.
     * @param col
     * @return 
     */
    int columnMap( int col ) {
        return col;
    }
    
    /**
     * return the total number of elements of each parameter.
     * @param info the info the JSONObject for the info response
     * @return an int array with the number of elements in each parameter.
     * @throws JSONException when required tags are missing
     */
    public static int[] getNumberOfElements( JSONObject info ) throws JSONException {
        JSONArray parameters= info.getJSONArray("parameters");
        int[] result= new int[parameters.length()];
        for ( int i=0; i<parameters.length(); i++ ) {
            int len=1;
            if ( parameters.getJSONObject(i).has("size") ) {
                JSONArray jarray1= parameters.getJSONObject(i).getJSONArray("size");
                for ( int k=0; k<jarray1.length(); k++ ) {
                    len*= jarray1.getInt(k);
                }
            }
            result[i]= len;
        }    
        return result;
    }    
    
    /**
     * trim time subseconds or expand with spaces.  If the time is the correct length,
     * then leave it alone.
     * @param field
     * @param lengthsi
     * @return 
     */
    public static String trimExpandTime( String field, int lengthsi, boolean expand ) {
        field= field.trim();
        if ( field.length()<lengthsi ) {
            if ( field.length()==lengthsi-1 && field.charAt(field.length()-1)!='Z' ) {
                field= field+"Z";
            } else if ( field.endsWith("Z") ) {
                if ( expand ) {
                    field= field + "                                 ".substring(0,lengthsi-field.length());
                }
            } else {
                if ( expand ) {
                    field= field + "Z                                ".substring(0,lengthsi-field.length());
                } else {
                    field= field + "Z";
                }
            }
        } else if ( field.length()>lengthsi ) {
            field= field.substring(0,lengthsi-1)+"Z";
        }
        if ( field.charAt(lengthsi-1)!='Z' ) {
            field= field.substring(0,lengthsi-1)+"Z";
        }
        return field;
    }
    
    
    @Override
    public void initialize( JSONObject info, OutputStream out, HapiRecord record) {
        try {
            int len= record.length();
            quotes= new boolean[len];
            lengths= new int[len];
            fill= new String[len];
            dfill= new double[len];
            types= new int[len];
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
                    if ( !f.startsWith("%") && !f.startsWith("$") ) {
                        f= "%"+f;
                    }
                    try {
                        if ( f.startsWith("%") ) {
                            String.format(f,1.0);
                            formats[i]= f;
                        } else if ( f.startsWith("$") ) {
                            new URITemplate(f); // Note this is not used.
                            formats[i]= f;
                        }
                        
                    } catch ( IllegalFormatException ex ) {
                        logger.log(Level.FINE, "dropping format which cannot be used: {0}", f);
                        formats[i]= null;
                    }
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
                        } else {
                            types[i]= TYPE_INTEGER;
                        }
                    }
                    break;
                    case "double": {
                        if ( parameter.has("size") ) {
                            types[i]= TYPE_DOUBLE_ARRAY;
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

        } catch (JSONException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        
    }

    
    @Override
    public void sendRecord(OutputStream out, HapiRecord record) throws IOException {
        int n= record.length();
        StringBuilder build= new StringBuilder();
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
                        build.append(s);
                    } catch ( IllegalArgumentException ex ) {
                        s= TimeUtil.reformatIsoTime(exampleTimes[i],record.getIsoTime(i));
                        build.append(s);
                    }
                    break;
                case TYPE_STRING: 
                    if ( quotes[i] ) build.append('"');
                    s= record.getString(i);
                    build.append(s);
                    if ( quotes[i] ) build.append('"');
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
                    String f= formats[i];
                	for ( int j=0; j<dd.length; j++ ) {
                        d= dd[j];
                		if ( j>0 ) build.append(",");
                        if ( d==dfill[i] ) {
                            build.append(fill[i]);
                        } else {
                    		if ( f==null ) {
                                build.append(d);
                            } else {
                                build.append(String.format( f,d ));
                            }
                        }
                	}
                    break;
                case TYPE_INTEGER:
                    s= String.valueOf(record.getInteger(i) );
                    build.append(s);
                    break;
                case TYPE_INTEGER_ARRAY:
                	int[] ii= record.getIntegerArray(i);
                	for ( int j=0; j<ii.length; j++ ) {
                		if ( j>0 ) build.append(",");
                		build.append(ii[j]);
                	}
                    break;
            }
        }
        out.write( build.toString().getBytes( CHARSET ) );
        out.write((byte)10);
        
    }
    
    @Override
    public void finalize(OutputStream out) {
        
    }

}
