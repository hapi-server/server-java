
package org.hapiserver;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.exceptions.BadRequestIdException;
import org.hapiserver.exceptions.HapiException;
import org.hapiserver.exceptions.UninitializedServerException;
import org.hapiserver.source.SpawnRecordSource;


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

    private static JSONObject resolveCatalog(JSONObject jo) throws JSONException {
        JSONArray catalog= jo.getJSONArray("catalog");
        JSONArray resolvedCatalog= new JSONArray();
        JSONObject groups= new JSONObject();
        JSONObject datasetToGroupId= new JSONObject();
        
        int resolvedCatalogLength=0;
        
        for ( int i=0; i<catalog.length(); i++ ) {
            JSONObject item= catalog.getJSONObject(i);
            String source= item.optString("x_source","");
            if ( source.length()==0 ) {
                resolvedCatalog.put( resolvedCatalogLength, item );
                resolvedCatalogLength++;
            } else if ( source.equals("spawn") ) {
                String command = item.optString("x_command","");
                String groupId= item.optString("x_group_id","");
                if ( command.length()==0 ) throw new IllegalArgumentException("x_command is missing");
                try {
                    jo= getCatalogFromSpawnCommand( command );
                    JSONArray items= jo.getJSONArray("catalog");
                    for ( int j=0; j<items.length(); j++ ) {
                        JSONObject catalogItem= items.getJSONObject(j);
                        String theId= catalogItem.getString("id");
                        logger.log(Level.INFO, "mapping in {0}", theId);
                        catalogItem.put( "x_group_id", groupId );
                        datasetToGroupId.put( theId, groupId );
                        resolvedCatalog.put( resolvedCatalogLength, catalogItem );
                        resolvedCatalogLength++;
                    }
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
                JSONObject config= item.optJSONObject("x_config");
                if ( config!=null ) {
                    groups.put( groupId, config );
                }
            } else {
                warnWebMaster(new RuntimeException("catalog source can only be spawn") );
            }
        }
        
        JSONObject newCatalogResponse= Util.copyJSONObject(jo);
        newCatalogResponse.put( "catalog", resolvedCatalog );
        newCatalogResponse.put( "x_groups", groups );
        newCatalogResponse.put( "x_dataset_to_group", datasetToGroupId );
        
        return newCatalogResponse;
        
    }

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
     * Allow a command to produce the catalog. 
     * @param command the command to spawn
     * @return the JSONObject for the catalog.
     * @throws java.io.IOException
     */
    public static JSONObject getCatalogFromSpawnCommand( String command ) throws IOException {
        logger.log(Level.INFO, "spawn command {0}", command);
        String[] ss= command.split("\\s+");

        ProcessBuilder pb= new ProcessBuilder( ss );
        Process process= pb.start();
        String text = new BufferedReader(
            new InputStreamReader( process.getInputStream(), StandardCharsets.UTF_8))
            .lines()
            .collect(Collectors.joining("\n"));

        JSONObject jo= Util.newJSONObject(text);
        return jo;

    }
    
    /**
     * Allow a command to produce the info for a dataset id
     * @param jo
     * @param HAPI_HOME
     * @param id
     * @return
     * @throws IOException 
     */
    public static JSONObject getInfoFromSpawnCommand( JSONObject jo, String HAPI_HOME, String id ) throws IOException {
        try {
            String command = SpawnRecordSource.doMacros( HAPI_HOME, id, jo.getString("command") );
            
            logger.log(Level.INFO, "spawn command {0}", command);
            String[] ss= command.split("\\s+");

            ProcessBuilder pb= new ProcessBuilder( ss );
            Process process= pb.start();
            String text = new BufferedReader(
                new InputStreamReader( process.getInputStream(), StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));
            
            jo= Util.newJSONObject(text);
            return jo;
            
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
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
        if ( catalogConfigFile.lastModified() > latestTimeStamp ) { // verify that it can be parsed and then copy it. //TODO: synchronized
            byte[] bb= Files.readAllBytes( Paths.get( catalogConfigFile.toURI() ) );
            String s= new String( bb, Charset.forName("UTF-8") );
            try {
                JSONObject jo= Util.newJSONObject(s);
                
                jo= resolveCatalog( jo );
                
                try ( InputStream ins= new ByteArrayInputStream(jo.toString(4).getBytes(CHARSET) ) ) {
                    Files.copy( ins, 
                                catalogFile.toPath(), StandardCopyOption.REPLACE_EXISTING );
                }
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
        JSONObject jo= Util.newJSONObject(s);
        cc= new CatalogData(jo,latestTimeStamp);
        catalogCache.put( HAPI_HOME, cc );
        return jo;
    }
    
    /**
     * keep and monitor a cached version of the configuration in memory.
     * @param HAPI_HOME the location of the server definition
     * @param id the identifier
     * @return the JSONObject for the configuration.
     * @throws java.io.IOException 
     * @throws org.codehaus.jettison.json.JSONException 
     * @throws org.hapiserver.exceptions.HapiException 
     */
    public static JSONObject getConfig( String HAPI_HOME, String id ) throws IOException, JSONException, HapiException {
        File configDir= new File( HAPI_HOME, "config" );
        id= Util.fileSystemSafeName(id);
        File configFile= new File( configDir, id + ".json" );
        if ( !configFile.exists() ) {
            throw new BadRequestIdException("id does not exist",id);
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
        JSONObject jo= Util.newJSONObject(s);
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
        
        JSONObject status= Util.newJSONObject();
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
     * replace macros like "stopDate":"now-P1D" with the computed value, and reformat into 
     * compliant isotime.
     * 
     * @param jo the info JSON
     * @return info with times resolved into a valid info response.
     * @throws JSONException 
     */
    private static JSONObject resolveTimes( JSONObject jo ) throws JSONException {
                // I had 2022-01-01 for my stopDate, and the verifier didn't like this format (no Z?)   
        
        jo= Util.copyJSONObject(jo); 
        
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
    public static JSONObject getDataConfig( String HAPI_HOME, String id ) throws IOException, JSONException, HapiException {
        File dir= new File( HAPI_HOME, "data" );
        String safeId= Util.fileSystemSafeName(id);
        File file= new File( dir, safeId + ".json" );

        CatalogData cc= catalogCache.get( HAPI_HOME );
        long latestTimeStamp= file.exists() ? file.lastModified() : 0;

        File dataConfigFile= new File( new File( HAPI_HOME, "config" ), safeId + ".json" );        
        JSONObject config=null;
        
        if ( !dataConfigFile.exists() ) {
            dataConfigFile= new File(  new File( HAPI_HOME, "config" ), "config.json" ); // allow config.json to handle all ids.
        }
        
        if ( !dataConfigFile.exists() ) {
            getInfo( HAPI_HOME, id );
            JSONObject jo= cc.catalog.optJSONObject("x_dataset_to_group");
            String group= jo.optString( id, null );
            if ( group!=null ) {
                config= cc.catalog.optJSONObject("x_groups");
                if ( config==null ) throw new BadRequestIdException( safeId );
                config= config.getJSONObject(group);
            } else {
                throw new BadRequestIdException( safeId );
            }
        }
        
        long configTimeStamp= config==null ? dataConfigFile.lastModified() : cc.catalogTimeStamp;
        
        if ( configTimeStamp > latestTimeStamp ) { // verify that it can be parsed and then copy it.
            
            JSONObject jo;
            if ( config==null ) {
                byte[] bb= Files.readAllBytes( Paths.get( dataConfigFile.toURI() ) );
                String s= new String( bb, Charset.forName("UTF-8") );
                jo= Util.newJSONObject(s);
            } else {
                jo= config;
            }
            
            try {
                jo= jo.getJSONObject("data");
                String dataString= jo.toString(4);
                Files.copy( new ByteArrayInputStream( dataString.getBytes(CHARSET) ), file.toPath(), StandardCopyOption.REPLACE_EXISTING );
                latestTimeStamp= dataConfigFile.lastModified();
            } catch ( JSONException ex ) {
                warnWebMaster(ex);
            }
        }
        
        if ( cc!=null ) {
            DataConfigData dataConfigData= cc.dataConfigCache.get( safeId );
            if ( dataConfigData!=null ) {
                if ( dataConfigData.dataConfigTimeStamp==latestTimeStamp ) {
                    JSONObject jo= dataConfigData.dataConfig;
                    return jo;
                }
            }
        }
        byte[] bb= Files.readAllBytes( Paths.get( file.toURI() ) );
        String s= new String( bb, Charset.forName("UTF-8") );
        JSONObject jo= Util.newJSONObject(s);
        
        JSONObject status= Util.newJSONObject();
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
            cc.dataConfigCache.put( safeId, dataConfigData );
        }
        return jo;
    }
    
    /**
     * verifies that the info object contains required tags.
     * @param jo a JSONObject
     * @return true if valid
     * @throws IllegalArgumentException when not valid
     */
    public static boolean validInfoObject( JSONObject jo ) throws IllegalArgumentException {
        String hapiVersion= jo.optString("HAPI","" );
        if ( !hapiVersion.equals("3.0") ) throw new IllegalArgumentException("HAPI version must be 3.0");
        if ( !jo.has("parameters") ) throw new IllegalArgumentException("Info must contain parameters");
        if ( !jo.has("startDate") )  throw new IllegalArgumentException("Info must have startDate");
        if ( !jo.has("stopDate") )  throw new IllegalArgumentException("Info must have stopDate");
        if ( jo.has("cadence") ) {
            String cadence= jo.optString("cadence","");
            try {
                TimeUtil.parseISO8601Duration(cadence);
            } catch ( ParseException ex ) {
                throw new IllegalArgumentException("cadence cannot be parsed: "+cadence);
            }
        }
        return true;
    }
    
    /**
     * keep and monitor a cached version of the info in memory.  If not in memory,
     * it will be read from the "info" folder, and if not there it will be read from
     * the config folder.  If the config folder timestamp is newer than what's loaded
     * the configuration is reloaded.  See https://github.com/hapi-server/server-java/wiki#dataset-configurations
     * @param HAPI_HOME the location of the server definition
     * @param id the identifier
     * @return the JSONObject for the catalog.
     * @throws java.io.IOException 
     * @throws org.codehaus.jettison.json.JSONException 
     * @throws org.hapiserver.exceptions.HapiException 
     * @throws IllegalArgumentException for bad id.
     */
    public static JSONObject getInfo( String HAPI_HOME, String id ) throws IOException, JSONException, HapiException {
        File infoDir= new File( HAPI_HOME, "info" );
        String safeId= Util.fileSystemSafeName(id);
        File infoFile= new File( infoDir, safeId + ".json" );

        CatalogData cc= catalogCache.get( HAPI_HOME );
        long latestTimeStamp= infoFile.exists() ? infoFile.lastModified() : 0;
        
        File configFile= new File( HAPI_HOME, "config" );
        
        File infoConfigFile= new File( configFile, safeId + ".json" );
        JSONObject config=null;
        
        if ( !infoConfigFile.exists() ) {
            JSONArray arr= cc.catalog.getJSONArray("catalog");
            JSONObject thisId=null;
            for ( int i=0; i<arr.length(); i++ ) {
                JSONObject jo= arr.getJSONObject(i);
                if ( jo.get("id").equals(id) ) {
                    thisId= jo;
                    break;
                }
            }
            if ( thisId==null ) {
                infoConfigFile= new File( configFile, "config.json" ); // allow config.json to contain "source"
            } else {
                String groupId= thisId.getString("x_group_id");
                JSONObject groups= cc.catalog.getJSONObject("x_groups");
                config= groups.getJSONObject(groupId);
            }
        }
        
        if ( config==null && !infoConfigFile.exists() ) {
            if ( !configFile.exists() ) {
                throw new UninitializedServerException( );
            } else {
                throw new BadRequestIdException( id );
            }
        }
        
        long configTimeStamp= config==null ? infoConfigFile.lastModified() : cc.catalogTimeStamp;
            
        if ( configTimeStamp > latestTimeStamp ) { // verify that it can be parsed and then copy it.
                
            JSONObject jo;
            if ( config==null ) {
                byte[] bb= Files.readAllBytes( Paths.get( infoConfigFile.toURI() ) );
                String s= new String( bb, Charset.forName("UTF-8") );
                jo= Util.newJSONObject(s);
            } else {
                jo= config;
            }
            
            try {
                jo= jo.getJSONObject("info");
                if ( jo.has("source") ) {
                    if ( jo.optString("source","").equals("spawn") ) {
                        jo= getInfoFromSpawnCommand( jo, HAPI_HOME, id );
                        try ( InputStream ins= new ByteArrayInputStream(jo.toString(4).getBytes(CHARSET) ) ) {
                            Files.copy( ins, 
                                infoFile.toPath(), StandardCopyOption.REPLACE_EXISTING );
                        }
                    } else {
                        warnWebMaster(new RuntimeException("catalog source can only be spawn") );
                    }
                } else {
                    validInfoObject(jo);
                    String infoString= jo.toString(4);
                    Files.copy( new ByteArrayInputStream( infoString.getBytes(CHARSET) ), infoFile.toPath(), StandardCopyOption.REPLACE_EXISTING );
                }
                latestTimeStamp= infoFile.lastModified();
            } catch ( JSONException | IllegalArgumentException ex ) {
                warnWebMaster(ex);
            }
        }
        
        if ( cc!=null ) {
            InfoData infoData= cc.infoCache.get( safeId );
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
        JSONObject jo= Util.newJSONObject(s);
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
        
        JSONObject status= Util.newJSONObject();
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
            cc.infoCache.put( safeId, infoData );
        }
        return jo;
    }
    
    
    /**
     * split the parameters into an array of parameters, adding time when it is implicit.
     * @param info the info object
     * @param parameters the parameters, which might be provided by the client.
     * @return the list of parameters, including the time parameter.
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
     * @throws RuntimeException when JSONException occurs.
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
    private static void warnWebMaster(Exception ex) {
        ex.printStackTrace();
    }
    
    
}
