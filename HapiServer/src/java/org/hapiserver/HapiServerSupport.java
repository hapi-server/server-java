
package org.hapiserver;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

    public static final Charset CHARSET= Charset.forName("UTF-8");
    
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

    /**
     * for the info, return all the parameters as a string array of the names
     * @param jo
     * @return 
     */
    public static String[] getAllParameters(JSONObject jo) {
        try {
            JSONArray array = jo.getJSONArray("parameters");
            String[] result= new String[array.length()];
            for ( int i=0; i<array.length(); i++ ) {
                result[i]= array.getJSONObject(i).getString("name");
            }
            return result;
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Map<String,CatalogData> catalogCache= new HashMap<>();

    private static class CatalogData {
        public CatalogData( JSONObject catalog, long catalogTimeStamp ) {
            this.catalog= catalog;
            this.catalogTimeStamp= catalogTimeStamp;
        }
        JSONObject catalog;
        long catalogTimeStamp;
        JSONObject about;
        long aboutTimeStamp;
        Map<String,InfoData> infoCache= new HashMap<>();
        Map<String,ConfigData> configCache = new HashMap<>();
        Map<String,DataConfigData> dataConfigCache = new HashMap<>();
    }

    private static class InfoData {
        public InfoData( JSONObject info, long infoTimeStamp ) {
            this.info= info;
            this.infoTimeStamp= infoTimeStamp;
        }
        JSONObject info;
        long infoTimeStamp;
    }
    
    private static class DataConfigData {
        public DataConfigData( JSONObject dataConfig, long dataConfigTimeStamp ) {
            this.dataConfig= dataConfig;
            this.dataConfigTimeStamp= dataConfigTimeStamp;
        }
        JSONObject dataConfig;
        long dataConfigTimeStamp;
    }
        
    
    private static class ConfigData {
        public ConfigData( JSONObject config, long infoTimeStamp ) {
            this.config= config;
            this.configTimeStamp= infoTimeStamp;
        }
        JSONObject config;
        long configTimeStamp;
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
        
        File catalogConfigFile= new File( new File( HAPI_HOME, "config" ), "catalog.json" );        
        if ( catalogConfigFile.lastModified() > latestTimeStamp ) { // verify that it can be parsed and then copy it.
            byte[] bb= Files.readAllBytes( Paths.get( catalogConfigFile.toURI() ) );
            String s= new String( bb, Charset.forName("UTF-8") );
            try {
                JSONObject jo= new JSONObject(s);        
                Files.copy( catalogConfigFile.toPath(), catalogFile.toPath(), StandardCopyOption.REPLACE_EXISTING );
                latestTimeStamp= catalogFile.lastModified();
            } catch ( JSONException ex ) {
                warnWebMaster(ex);
            }
        }
        
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
    
    /**
     * keep and monitor a cached version of the catalog in memory.
     * @param HAPI_HOME the location of the server definition
     * @param id the identifier
     * @return the JSONObject for the configuration.
     * @throws java.io.IOException 
     * @throws org.codehaus.jettison.json.JSONException 
     * @throws IllegalArgumentException for bad id.
     */
    public static JSONObject getConfig( String HAPI_HOME, String id ) throws IOException, JSONException {
        File configDir= new File( HAPI_HOME, "config" );
        id= Util.fileSystemSafeName(id);
        File configFile= new File( configDir, id + ".json" );
        if ( !configFile.exists() ) {
            throw new BadIdException("id does not exist",id);
        }
        CatalogData cc= catalogCache.get( HAPI_HOME );
        long latestTimeStamp= configFile.lastModified();
        if ( cc!=null ) {
            ConfigData configData= cc.configCache.get( id );
            if ( configData!=null ) {
                if ( configData.configTimeStamp==latestTimeStamp ) {
                    JSONObject jo= configData.config;
                    return jo;
                }
            }
        }
        byte[] bb= Files.readAllBytes( Paths.get( configFile.toURI() ) );
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
     * keep and monitor a cached version of the data description in memory.  This is
     * presently used to store the method used to read the data.
     * @param HAPI_HOME the location of the server definition
     * @param id the identifier
     * @return the JSONObject for the data description, with "server" tag.
     * @throws java.io.IOException 
     * @throws org.codehaus.jettison.json.JSONException 
     * @throws IllegalArgumentException for bad id.
     */
    public static JSONObject getDataConfig( String HAPI_HOME, String id ) throws IOException, JSONException {
        File dir= new File( HAPI_HOME, "data" );
        id= Util.fileSystemSafeName(id);
        File file= new File( dir, id + ".json" );

        CatalogData cc= catalogCache.get( HAPI_HOME );
        long latestTimeStamp= file.exists() ? file.lastModified() : 0;

        File dataConfigFile= new File( new File( HAPI_HOME, "config" ), id + ".json" );        
        if ( dataConfigFile.lastModified() > latestTimeStamp ) { // verify that it can be parsed and then copy it.
            byte[] bb= Files.readAllBytes( Paths.get( dataConfigFile.toURI() ) );
            String s= new String( bb, Charset.forName("UTF-8") );
            try {
                JSONObject jo= new JSONObject(s);        
                jo= jo.getJSONObject("data");
                String dataString= jo.toString(4);
                Files.copy( new ByteArrayInputStream( dataString.getBytes(CHARSET) ), file.toPath(), StandardCopyOption.REPLACE_EXISTING );
                latestTimeStamp= dataConfigFile.lastModified();
            } catch ( JSONException ex ) {
                warnWebMaster(ex);
            }
        }
        
        if ( cc!=null ) {
            DataConfigData dataConfigData= cc.dataConfigCache.get( id );
            if ( dataConfigData!=null ) {
                if ( dataConfigData.dataConfigTimeStamp==latestTimeStamp ) {
                    JSONObject jo= dataConfigData.dataConfig;
                    return jo;
                }
            }
        }
        byte[] bb= Files.readAllBytes( Paths.get( file.toURI() ) );
        String s= new String( bb, Charset.forName("UTF-8") );
        JSONObject jo= new JSONObject(s);
        
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
            DataConfigData dataConfigData= new DataConfigData(jo,latestTimeStamp);
            cc.dataConfigCache.put( id, dataConfigData );
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

        CatalogData cc= catalogCache.get( HAPI_HOME );
        long latestTimeStamp= infoFile.exists() ? infoFile.lastModified() : 0;
        
        File infoConfigFile= new File( new File( HAPI_HOME, "config" ), id + ".json" );        
        if ( infoConfigFile.lastModified() > latestTimeStamp ) { // verify that it can be parsed and then copy it.
            byte[] bb= Files.readAllBytes( Paths.get( infoConfigFile.toURI() ) );
            String s= new String( bb, Charset.forName("UTF-8") );
            try {
                JSONObject jo= new JSONObject(s);
                jo= jo.getJSONObject("info");
                String infoString= jo.toString(4);
                Files.copy( new ByteArrayInputStream( infoString.getBytes(CHARSET) ), infoFile.toPath(), StandardCopyOption.REPLACE_EXISTING );
                latestTimeStamp= infoFile.lastModified();
            } catch ( JSONException ex ) {
                warnWebMaster(ex);
            }
        }
        
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
            int i;
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
                while ( iparam<jsonArray.length() && !params[i].equals(jsonArray.getJSONObject(iparam).getString("name")) ) {
                    iparam++;
                }
                if ( iparam==jsonArray.length() ) throw new IllegalArgumentException("parameter not found: "+params[i]);
                build.append(",");
                build.append(params[i]);
                iparam++;
            }
            return build.toString();

        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * information needs to be conveyed to the HAPI website administrator.
     * @param ex 
     */
    private static void warnWebMaster(JSONException ex) {
        ex.printStackTrace();
    }
    
    
}
