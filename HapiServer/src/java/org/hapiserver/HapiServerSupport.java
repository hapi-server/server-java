
package org.hapiserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;


/**
 *
 * @author jbf
 */
public class HapiServerSupport {
    
    private static final Logger logger= Util.getLogger();
    
    private static final int[] myValidTime= ExtendedTimeUtil.parseValidTime( "2200-01-01T00:00" );

    /**
     * return the range of available data. For example, Polar/Hydra data is available
     * from 1996-03-20 to 2008-04-15.
     * @param info
     * @return the range of available data, or null if it is not available.
     */
    public static int[] getRange( JSONObject info ) {
        try {
            
            if ( info.has("startDate") ) { // note startDate is required.
                String startDate= info.getString("startDate");
                String stopDate;
                if (info.has("stopDate")) {
                    stopDate = info.getString("stopDate");
                } else {
                    stopDate = "now";
                }
                if ( startDate!=null && stopDate!=null ) {
                    int[] t1= ExtendedTimeUtil.parseValidTime(startDate);
                    int[] t2= ExtendedTimeUtil.parseValidTime(stopDate);
                    return ExtendedTimeUtil.createTimeRange( t1, t2 );
                } else {
                    throw new IllegalArgumentException("info doesn't have start and stop date");
                }
            }
        } catch ( JSONException ex ) {
            logger.log( Level.WARNING, ex.getMessage(), ex );
        }
        return null;
    }
    
    public static int[] getExampleRange(JSONObject info) {
        int[] range = getRange(info);
        if (range == null) {
            logger.warning("server is missing required startDate and stopDate parameters.");
            return null;
        } else {
            int[] landing;
            if ( info.has("sampleStartDate") && info.has("sampleStopDate") ) {
                try {
                    int[] t1= ExtendedTimeUtil.parseValidTime(info.getString("sampleStartDate"));
                    int[] t2= ExtendedTimeUtil.parseValidTime(info.getString("sampleStopDate"));
                    landing = ExtendedTimeUtil.createTimeRange(t1, t2);
                    return landing;
                } catch (JSONException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            } 
            int[] end = ExtendedTimeUtil.getStopTime(range);
            end[4]= end[5]= end[6]= 0;
            int[] start= TimeUtil.subtract( end, new int[] { 0, 0, 1, 0, 0, 0, 0 } );
            landing = ExtendedTimeUtil.createTimeRange( start, end );
            return landing;
        }
    }

//    /**
//     * return the example time range for the dataset.
//     * @param id
//     * @return
//     * @throws IOException
//     * @throws FileNotFoundException
//     * @throws JSONException 
//     */
//    public static int[] getExampleRange( String id ) throws IOException, FileNotFoundException, JSONException {
//        File infoFile= new File( new File( Util.getHapiHome(), "info" ), id+".json" );
//        JSONObject info= readJSON( infoFile );
//        int[] range = getRange(info);
//        if (range == null) {
//            logger.warning("server is missing required startDate and stopDate parameters.");
//            return null;
//        } else {
//            int[] landing;
//            if (range.max().ge(myValidTime)) { // Note stopDate is required since 2017-01-17.
//                logger.warning("server is missing required stopDate parameter.");
//                landing = new DatumRange(range.min(), range.min().add(1, Units.days));
//            } else {
//                Datum end = TimeUtil.prevMidnight(range.max());
//                landing = new DatumRange(end.subtract(1, Units.days), end);
//            }
//            return landing;
//        }
//    }
//        
//    /**
//     * return the list of datasets available at the server
//     * @return list of dataset ids
//     */
//    public static List<String> getCatalogIds( ) throws IOException {
//        try {
//            JSONArray catalog= getCatalog();
//            List<String> result= new ArrayList<>(catalog.length());
//            for ( int i=0; i<catalog.length(); i++ ) {
//                JSONObject jo= catalog.getJSONObject(i);
//                result.add(jo.getString("id"));
//            }
//            return result;
//        } catch (JSONException ex) {
//            throw new RuntimeException(ex);
//        }
//    }
//    
//    public static JSONArray getCatalog() throws JSONException, IOException {
//        JSONArray array= new JSONArray();
//        JSONObject catalog= getCatalogNew();
//        JSONArray cat= catalog.getJSONArray("catalog");
//        for ( int i=0; i<cat.length(); i++ ) {
//            array.put( cat.get(i) );
//        }
//        return array;
//    }
//    
//    /**
//     * read the JSONObject from the file.
//     * @param jsonFile file containing JSONObject.
//     * @return the JSONObject
//     * @throws FileNotFoundException
//     * @throws IOException
//     * @throws JSONException 
//     */
//    public static JSONObject readJSON( File jsonFile ) throws FileNotFoundException, IOException, JSONException {
//        logger.entering( "HapiServerSupport", "readJSON", jsonFile );
//        StringBuilder builder= new StringBuilder();
//        try ( BufferedReader in= new BufferedReader( new FileReader( jsonFile ) ) ) {
//            String line= in.readLine();
//            while ( line!=null ) {
//                builder.append(line);
//                line= in.readLine();
//            }
//        }
//        if ( builder.length()==0 ) {
//            throw new IOException("file is empty: "+jsonFile);
//        }
//        try {
//            JSONObject catalog= new JSONObject(builder.toString());
//            return catalog;
//        } catch ( JSONException ex ) {
//            logger.log( Level.WARNING, "Exception encountered when reading "+jsonFile, ex );
//            throw ex;
//        } finally {
//            logger.exiting( "HapiServerSupport", "readJSON" );
//        }
//    }
//    
//    private static JSONObject getCatalogNew() throws IOException, JSONException {
//        try {
//            File catalogFile= new File( Util.getHapiHome(), "catalog.json" );
//            JSONObject catalog= readJSON(catalogFile);
//            return catalog;
//        } catch ( IllegalArgumentException ex ) {
//            throw new IllegalArgumentException("Util.HAPI_HOME is not set, which might be because the root (hapi/index.jsp) was never loaded.");
//        }
//    }
//    
//    public static class ParamDescription {
//        boolean hasFill= false;
//        double fill= -1e38;
//        String units= "";
//        String name= "";
//        String description= "";
//        String type= "";
//        int length= 0;
//        int[] size= new int[0]; // array of scalars
//        QDataSet depend1= null; // for spectrograms
//        ParamDescription( String name ) {
//            this.name= name;
//        }
//    }
    
}
