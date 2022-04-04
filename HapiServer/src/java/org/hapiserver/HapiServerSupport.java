
package org.hapiserver;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.exceptions.BadIdException;


/**
 * static utility routines to support the HAPI server.  This includes a
 * cache which will store and maintain the catalog and infos in memory along
 * with the timetags for the files which define the responses.
 * 
 * @author jbf
 */
public class HapiServerSupport {
    
    private static final Logger logger= Util.getLogger();
    
    /**
     * reference for assumptions about time, no time in the system can be greater than or equal to this time.
     */
    public static final int[] lastValidTime= ExtendedTimeUtil.parseValidTime( "2100-01-01T00:00" );

    /**
     * reference for assumptions about time, no time in the system can be less than this time.
     */
    public static final int[] firstValidTime= ExtendedTimeUtil.parseValidTime( "1900-01-01T00:00" );

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

    private static class CatalogData {
        public CatalogData( JSONObject catalog, long catalogTimeStamp ) {
            this.catalog= catalog;
            this.catalogTimeStamp= catalogTimeStamp;
        }
        JSONObject catalog;
        long catalogTimeStamp;
        Map<String,InfoData> infoCache= new HashMap<>();
    }

    private static Map<String,CatalogData> catalogCache= new HashMap<>();
    
    private static class InfoData {
        public InfoData( JSONObject info, long infoTimeStamp ) {
            this.info= info;
            this.infoTimeStamp= infoTimeStamp;
        }
        JSONObject info;
        long infoTimeStamp;
    }
    
    /**
     * keep and monitor a cached version of the catalog in memory.
     * @param HAPI_HOME the location of the server definition
     * @return the JSONObject for the catalog.
     * @throws java.io.IOException 
     * @throws org.codehaus.jettison.json.JSONException 
     */
    public static JSONObject getCatalog( String HAPI_HOME ) throws IOException, JSONException {
        File catalogFile= new File( HAPI_HOME, "catalog.json" );
        CatalogData cc= catalogCache.get( HAPI_HOME );
        long latestTimeStamp= catalogFile.lastModified();
        if ( cc!=null ) {
            if ( cc.catalogTimeStamp == latestTimeStamp ) {
                return cc.catalog;
            }
        }
        byte[] bb= Files.readAllBytes( Paths.get( catalogFile.toURI() ) );
        String s= new String( bb, Charset.forName("UTF-8") );
        JSONObject jo= new JSONObject(s);
        cc= new CatalogData(jo,latestTimeStamp);
        catalogCache.put( HAPI_HOME, cc );
        return jo;
    }

    private static JSONObject resolveTimes( JSONObject jo ) throws JSONException {
                // I had 2022-01-01 for my stopDate, and the verifier didn't like this format (no Z?)   
        
        jo= new JSONObject(jo.toString()); // copy JSONObject
        
        try {
            jo.put( "startDate",  ExtendedTimeUtil.formatIso8601TimeBrief( ExtendedTimeUtil.parseTime( jo.getString("startDate") ) ) );
        } catch (ParseException ex) {
            throw new RuntimeException(ex);
        }
        try {
            jo.put( "stopDate",  ExtendedTimeUtil.formatIso8601TimeBrief( ExtendedTimeUtil.parseTime( jo.getString("stopDate") ) ) );
        } catch (ParseException ex) {
            throw new RuntimeException(ex);
        }
        
        if ( jo.has("sampleStartDate" ) ) {
            try {
                jo.put( "sampleStartDate", 
                    ExtendedTimeUtil.formatIso8601TimeBrief( ExtendedTimeUtil.parseTime( jo.getString("sampleStartDate") ) ) );
            } catch (ParseException ex) {
                throw new RuntimeException(ex);
            }
        }
        if ( jo.has("sampleStopDate" ) ) {
            try {
                jo.put( "sampleStopDate", 
                    ExtendedTimeUtil.formatIso8601TimeBrief( ExtendedTimeUtil.parseTime( jo.getString("sampleStopDate") ) ) );
            } catch (ParseException ex) {
                throw new RuntimeException(ex);
            }
        }
        return jo;
    }
    
    /**
     * keep and monitor a cached version of the catalog in memory.
     * @param HAPI_HOME the location of the server definition
     * @param id the identifier
     * @return the JSONObject for the catalog.
     * @throws java.io.IOException 
     * @throws org.codehaus.jettison.json.JSONException 
     * @throws IllegalArgumentException for bad id.
     */
    public static JSONObject getInfo( String HAPI_HOME, String id ) throws IOException, JSONException {
        File infoDir= new File( HAPI_HOME, "info" );
        id= Util.fileSystemSafeName(id);
        File infoFile= new File( infoDir, id + ".json" );
        if ( !infoFile.exists() ) {
            throw new BadIdException("id does not exist",id);
        }
        CatalogData cc= catalogCache.get( HAPI_HOME );
        long latestTimeStamp= infoFile.lastModified();
        if ( cc!=null ) {
            InfoData infoData= cc.infoCache.get( id );
            if ( infoData!=null ) {
                if ( infoData.infoTimeStamp==latestTimeStamp ) {
                    JSONObject jo= infoData.info;
                    jo= resolveTimes(jo);
                    return jo;
                }
            }
        }
        byte[] bb= Files.readAllBytes( Paths.get( infoFile.toURI() ) );
        String s= new String( bb, Charset.forName("UTF-8") );
        JSONObject jo= new JSONObject(s);
        if ( jo.has("modificationDate") ) {
            String modificationDate= jo.getString("modificationDate");
            if ( modificationDate.length()==0 ) {
                String stime= ExtendedTimeUtil.fromMillisecondsSince1970(latestTimeStamp);
                jo.put( "modificationDate", stime );
            } else if ( !( modificationDate.length()>0 && Character.isDigit( modificationDate.charAt(0) ) ) ) {
                try {
                    String stime= ExtendedTimeUtil.formatIso8601TimeBrief( ExtendedTimeUtil.parseTime( modificationDate ) );
                    jo.put( "modificationDate", stime );
                } catch (ParseException ex) {
                    throw new IllegalArgumentException(ex);
                }
            }
        }
        
        JSONObject status= new JSONObject();
        status.put( "code", 1200 );
        status.put( "message", "OK request successful");
                
        jo.put( "status", status );
        
        cc= catalogCache.get( HAPI_HOME );
        if ( cc==null ) {
            getCatalog(HAPI_HOME); // create a cache entry
        }
        synchronized (HapiServerSupport.class) {
            cc= catalogCache.get( HAPI_HOME );
            if ( cc==null ) {
                throw new IllegalArgumentException("This should not happen");
            }
            InfoData infoData= new InfoData(jo,latestTimeStamp);
            cc.infoCache.put( id, infoData );
        }
        return jo;
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
    
    /**
     * split the parameters into an array of parameters, adding time when it is implicit.
     * @param info
     * @param parameters
     * @return 
     */
    public static String[] splitParams( JSONObject info, String parameters )  {
        String[] ss= parameters.split(",");
        try {
            JSONArray jsonArray= info.getJSONArray("parameters");
            JSONObject time= jsonArray.getJSONObject(0);
            if ( ss[0].equals(time.getString("name") ) ) {
                return ss;
            } else {
                String[] result= new String[ss.length+1];
                result[0]= time.getString("name");
                System.arraycopy( ss, 0, result, 1, ss.length );
                return result;
            }

        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }

    }
    
    /**
     * combine the parameters into a comma-delimited string, also checking for validity.  The parameters
     * must be a subset of the parameters found in info, and must be in the same order.
     * @param info the JSONObject for the server
     * @param params array of parameter names
     * @return comma-delimited string, including the time if it wasn't found
     * @throws RuntimeException
     */
    public static String joinParams( JSONObject info, String[] params )  {
        try {
            JSONArray jsonArray= info.getJSONArray("parameters");
            JSONObject time= jsonArray.getJSONObject(0);
            StringBuilder build= new StringBuilder();
            int i=1;
            int iparam=0;
            if ( params[0].equals(time.getString("name")) ) {
                build.append(params[0]);
                iparam++;
                i=1;
            } else {
                build.append(time.getString("name"));
                i=0;
            }
            for ( ; i<params.length; i++ ) {
                if ( params[i].contains(",") ) throw new IllegalArgumentException("parameter contains comma: "+params[i]);
                while ( iparam<params.length && !params[i].equals(jsonArray.getJSONObject(iparam).getString("name")) ) {
                    iparam++;
                }
                if ( iparam==params.length ) throw new IllegalArgumentException("parameter not found: "+params[i]);
                build.append(",");
                build.append(params[i]);
                iparam++;
            }
            return build.toString();

        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }
}
