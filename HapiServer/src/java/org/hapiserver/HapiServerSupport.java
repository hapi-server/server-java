
package org.hapiserver;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
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
                    return TimeUtil.createTimeRange( t1, t2 );
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
                    landing = TimeUtil.createTimeRange(t1, t2);
                    return landing;
                } catch (JSONException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            } 
            int[] end = TimeUtil.getStopTime(range);
            end[4]= end[5]= end[6]= 0;
            int[] start= TimeUtil.subtract( end, new int[] { 0, 0, 1, 0, 0, 0, 0 } );
            landing = TimeUtil.createTimeRange( start, end );
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

    private static final Map<String,CatalogData> catalogCache= new HashMap<>();

    /**
     * New 2025: the config.json file can contain node "options" which are a 
     * set of definitions for the node.  Note options' namespace is the same
     * as the servers, so "${id}", "${info}", and "${data-config}" are all
     * reserved words.  This will only look at the "args" of the "config" node.
     * @param options
     * @param item item with either catalog, info, or data node.
     * @return 
     */
    private static JSONObject resolveOptions( Map<String,Object> options, JSONObject item ) {
        JSONObject itemConfig;
        JSONObject config= item.optJSONObject("config");
        if ( config==null ) return item;
        String[] confs= new String[] { "catalog", "info", "data" };
        for (String s: confs ) {
            itemConfig= config.optJSONObject(s);
            if ( itemConfig==null ) continue;
            if ( "classpath".equals(itemConfig.opt("source")) ) {
                JSONArray args= itemConfig.optJSONArray("args");
                if (args!=null ) {
                    for ( int i=0; i<args.length(); i++ ) {
                        String sarg= args.optString(i,"");
                        if ( sarg.contains("${") ) {
                            String[] ss= sarg.split("\\$\\{");
                            for ( int j=1; j<ss.length; j++ ) {
                                int k2= ss[j].indexOf("}");
                                if ( k2==-1 ) {
                                    logger.log(Level.WARNING, "syntax error in args: {0}", sarg);
                                    continue;
                                } 
                                String k= ss[j].substring(0,k2);
                                if ( k.equals("id") || k.equals("info") || k.equals("data-config") ) {
                                    ss[j]= "${"+ k + ss[j].substring(k2); // we need this because because it is resolved later.
                                } else {
                                    String v= String.valueOf(options.get( k ));
                                    ss[j]= v + ss[j].substring(k2+1);
                                }
                            }
                            sarg= String.join("", ss);
                            try {
                                args.put(i,sarg);
                            } catch (JSONException ex) {
                                logger.log(Level.SEVERE, null, ex); // this shouldn't happen
                            }
                        }
                    }
                }
            }
        }
        return item;
    }
        
    /**
     * resolve the catalog, which can be a simple catalog with each dataset 
     * described in simple info responses in separate files, or with a json
     * document with "groups" listing groups which need to be resolved separately.
     * @param HAPI_HOME
     * @param jo contents of config.json
     * @return the catalog response.
     * @throws JSONException 
     */
    private static JSONObject resolveCatalog(String HAPI_HOME, JSONObject jo) throws JSONException {
        JSONArray catalog= jo.optJSONArray("catalog");
        if ( catalog==null ) {
            catalog= jo.optJSONArray("groups");
        }
        JSONArray resolvedCatalog= new JSONArray();
        JSONObject groups= new JSONObject();
        JSONObject datasetToGroupId= new JSONObject();
        
        int resolvedCatalogLength=0;
        
        JSONObject options = jo.optJSONObject("options");
        Map<String,Object> optionsMap;
        if ( options==null ) {
            optionsMap= Collections.emptyMap();
        } else {
            // go through and resolve any internal references within the options
            optionsMap = new HashMap( options.toMap() );
            Set<String> kk= optionsMap.keySet();
            for ( String k1 : kk ) {
                Object o= optionsMap.get(k1);
                if ( o instanceof String ) {
                    String v1= (String)o;
                    if ( v1.contains("${") ) {
                        String[] ss= v1.split("\\$\\{");
                        for ( int j=1; j<ss.length; j++ ) {
                            int k2= ss[j].indexOf("}");
                            if ( k2==-1 ) {
                                logger.log(Level.WARNING, "syntax error in args: {0}", v1);
                                continue;
                            } 
                            String k= ss[j].substring(0,k2);
                            if ( k.equals("id") || k.equals("info") || k.equals("data-config") ) {
                                ss[j]= "${"+ k + ss[j].substring(k2); // we need this because because it is resolved later.
                            } else {
                                if ( !optionsMap.containsKey(k) ) {
                                    throw new IllegalArgumentException("options map \""+k1+"\" uses key which is not found: "+k );
                                }
                                String v= optionsMap.get( k ).toString();
                                ss[j]= v + ss[j].substring(k2+1);
                            }
                        }
                        optionsMap.put( k1, String.join("", ss) );
                    }
                } 
            }
        }
        
        for ( int i=0; i<catalog.length(); i++ ) {
            JSONObject item= catalog.getJSONObject(i);
            item= resolveOptions( optionsMap, item );
            String groupId= item.optString("group_id", item.optString("x_group_id","") );
            String source= item.optString("source",item.optString("x_source",""));
            JSONObject config= item.optJSONObject("config");
            if ( config==null ) config= item.optJSONObject("x_config");
            
            if ( config==null ) {
                resolvedCatalog.put( resolvedCatalogLength, item );
                resolvedCatalogLength++;
            } else {
                if ( source.length()==0 ) {
                    item= config.optJSONObject("catalog"); 
                    source= item.optString("source",item.optString("x_source",""));
                }
                if ( source.length()==0 ) {
                    resolvedCatalog.put( resolvedCatalogLength, item );
                    resolvedCatalogLength++;
                } else if ( source.equals("spawn") ) {
                    String command = item.optString("x_command","");
                    if ( command.length()==0 ) throw new IllegalArgumentException("x_command is missing");
                    try {
                        jo= getCatalogFromSpawnCommand( command );
                        JSONArray items= jo.getJSONArray("catalog");
                        for ( int j=0; j<items.length(); j++ ) {
                            JSONObject catalogItem= items.getJSONObject(j);
                            String theId= catalogItem.getString("id");
                            logger.log(Level.INFO, "mapping in {0}", theId);
                            datasetToGroupId.put( theId, groupId );
                            resolvedCatalog.put( resolvedCatalogLength, catalogItem );
                            resolvedCatalogLength++;
                        }
                    } catch (JSONException | IOException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                    
                } else if ( source.equals("classpath") ) {
                    try {
                        jo= getCatalogFromClasspath( item,HAPI_HOME  );

                        JSONArray items= jo.getJSONArray("catalog");
                        for ( int j=0; j<items.length(); j++ ) {
                            JSONObject catalogItem= items.getJSONObject(j);
                            String theId= catalogItem.getString("id");
                            logger.log(Level.INFO, "mapping in {0}", theId);
                            datasetToGroupId.put( theId, groupId );
                            resolvedCatalog.put( resolvedCatalogLength, catalogItem );
                            resolvedCatalogLength++;
                        }
                    } catch (JSONException | IOException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                    
                } else {
                    warnWebMaster(new RuntimeException("catalog source can only be spawn") );
                }

                groups.put( groupId, config );
                
            }
        }
                
        JSONObject newCatalogResponse= Util.copyJSONObject(jo);
        newCatalogResponse.put( "catalog", resolvedCatalog );
        newCatalogResponse.put( "x_groups", groups );
        newCatalogResponse.put( "x_dataset_to_group", datasetToGroupId );
        
        return newCatalogResponse;
        
    }

    /**
     * create a temporary file in HAPI_HOME/tmp.
     * @param HAPI_HOME
     * @param typeFileName
     * @return 
     */
    private synchronized static File getTmpFile(String HAPI_HOME,String typeFileName) {
        File tmpDir= new File( HAPI_HOME, "tmp" );
        if ( !tmpDir.exists() ) {
            tmpDir.mkdirs();
        }
        return new File( tmpDir, Thread.currentThread().getName() + "_" + typeFileName );
    }

    private static class CatalogData {
        public CatalogData( JSONObject catalog, long catalogTimeStamp ) {
            this.catalog= catalog;
            this.catalogTimeStamp= catalogTimeStamp;
        }
        JSONObject catalog;
        long catalogTimeStamp;
        Set<String> datasetIds= new LinkedHashSet<>();
        Map<String,InfoData> infoCache= new HashMap<>();
        Map<String,ConfigData> configCache = new HashMap<>();
        Map<String,DataConfigData> dataConfigCache = new HashMap<>();
    }

    private static class InfoData {
        public InfoData( JSONObject info, long infoTimeStamp, long expiresTimeStamp ) {
            this.info= info;
            this.infoTimeStamp= infoTimeStamp;
            this.expiresTimeStamp= expiresTimeStamp;
        }
        JSONObject info;
        long infoTimeStamp;
        long expiresTimeStamp;
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
        logger.log(Level.FINE, "spawn command {0}", command);
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
     * @param jo configuration containing nodes like "x_command"
     * @param HAPI_HOME
     * @param id
     * @return
     * @throws IOException 
     */
    public static JSONObject getInfoFromSpawnCommand( JSONObject jo, String HAPI_HOME, String id ) throws IOException {
        logger.log(Level.FINE, "getInfoFromSpawnCommand {0}", id);        
        try {
            String command = SpawnRecordSource.doMacros( HAPI_HOME, id, jo.getString("command") );
            
            logger.log(Level.FINE, "spawn command {0}", command);
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
     * Allow a Java method to produce the catalog. 
     * @param jo configuration containing nodes like "x_classpath", "x_class", and "x_method"
     * @param HAPI_HOME
     * @return the JSONObject for the catalog.
     * @throws java.io.IOException
     */
    public static JSONObject getCatalogFromClasspath( JSONObject jo, String HAPI_HOME ) throws IOException {
        logger.log(Level.FINE, "getCatalogFromClasspath" );
                    
        ClassLoader cl=null;
        String s= jo.optString( "classpath", jo.optString("x_classpath","") );
        String methodString= jo.optString("method",jo.optString("x_method","") );
        String clas= jo.optString( "class", jo.optString("x_class","") );

        logger.log(Level.FINE, "class {0}", clas );
        logger.log(Level.FINE, "method {0}", methodString );
        
        if ( s.length()>0 ) {
            try {
                s= SpawnRecordSource.doMacros( HAPI_HOME, "", s );
                URL url= getClasspath(HAPI_HOME, s);
                cl= new URLClassLoader( new URL[] { url }, SourceRegistry.class.getClassLoader());
                cl.getParent();
            } catch (MalformedURLException ex) {
                Logger.getLogger(SourceRegistry.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        if ( clas.length()==0 ) {
            throw new IllegalArgumentException("class must be defined");
        }
        try {
            Class c;
            if ( cl!=null ) {
                c= Class.forName(clas,true,cl);
            } else {
                c= Class.forName(clas);
            }
            JSONArray args= jo.optJSONArray("args");
            if ( args==null ) {
                if ( jo.has("x_args") ) {
                    try {
                        if ( !( jo.get("x_args") instanceof JSONArray ) ) {
                            throw new IllegalArgumentException("x_args should be an array");
                        }
                    } catch (JSONException ex) {
                        Logger.getLogger(HapiServerSupport.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } 
                args= jo.optJSONArray("x_args");
            }
            if ( args==null ) {
                Method method= c.getMethod( methodString );
                if ( method.getReturnType()!=String.class ) {
                    throw new IllegalArgumentException("method should return String: " + clas + "."+ methodString );
                }
                String catalogString= (String)method.invoke( null );
                try {
                    return new JSONObject(catalogString);
                } catch ( JSONException ex ) {
                    throw new IllegalArgumentException("JSON parse error for String returned by " + clas + "."+ methodString);
                }


            } else {
                Class[] cc= new Class[args.length()];
                Object[] oo= new Object[args.length()];
                for ( int i=0; i<cc.length; i++ ) {
                    try {
                        oo[i]= args.get(i);
                        cc[i]= oo[i].getClass();
                        if ( cc[i]==String.class ) { // check for macros
                            String ss= SpawnRecordSource.doMacros( HAPI_HOME, "", (String)oo[i] );
                            oo[i]= ss;
                        }
                    } catch (JSONException ex) {
                        Logger.getLogger(SourceRegistry.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                Method method= c.getMethod( methodString, cc );
                String infoString= (String)method.invoke( null, oo ); // method is static
                try {
                    return new JSONObject(infoString);
                } catch ( JSONException ex ) {
                    throw new IllegalArgumentException("JSON parse error for String returned by " + clas + "."+ methodString);
                }
            }

        } catch ( ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException ex ) {
            throw new RuntimeException(ex);
        }
                        
    }
    
    /**
     * return the URL identified by s, which can be relative to the HAPI_HOME/config directory.
     * @param HAPI_HOME home of config and resolved responses, often /tmp/hapi-server/.
     * @param s the path for the jar file.
     * @return the resolved URL.
     * @throws MalformedURLException 
     */
    protected static URL getClasspath( String HAPI_HOME, String s ) throws MalformedURLException {
        URL url;
        if ( s.startsWith("http://") || s.startsWith("https://") || s.startsWith("file:") ) { 
            url= new URL( s );
        } else {
            if ( s.contains("/") ) {
                url= new File(s).toURI().toURL();
            } else {
                url= new File(HAPI_HOME + "/config/",s).toURI().toURL();
            }
        }    
        return url;
    }
    
    /**
     * Allow a java call to produce the info for a dataset id.  The JSONObject should 
     * have the tags "class" and "method" which identify a static method which takes the
     * id as an argument.
     * 
     * @param jo configuration containing nodes like "x_classpath", "x_class", and "x_method"
     * @param HAPI_HOME
     * @param id dataset id
     * @return
     * @throws IOException 
     */
    public static JSONObject getInfoFromClasspath( JSONObject jo, String HAPI_HOME, String id ) throws IOException {
        logger.log(Level.FINE, "getInfoFromClasspath {0}", id);
        String clas= jo.optString( "class", jo.optString("x_class",""));

        String methodString = jo.optString("method", jo.optString("x_method",""));
        String classpath= jo.optString("classpath", jo.optString("x_classpath",""));
        ClassLoader cl=null;
        if ( classpath.length()>0 ) {
            try {
                String s= classpath;
                s= SpawnRecordSource.doMacros( HAPI_HOME, id, s );
                URL url= getClasspath(HAPI_HOME, s);
                cl= new URLClassLoader( new URL[] { url }, SourceRegistry.class.getClassLoader());
                cl.getParent();
            } catch (MalformedURLException ex) {
                Logger.getLogger(SourceRegistry.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
        try {
            Class c;
            if ( cl!=null ) {
                c= Class.forName(clas,true,cl);
            } else {
                c= Class.forName(clas);
            }
            JSONArray args= jo.optJSONArray("args");
            if ( args==null ) args= jo.optJSONArray("x_args");
            if ( args==null ) {
                Method method= c.getMethod( methodString, String.class );
                if ( method.getReturnType()!=String.class ) {
                    throw new IllegalArgumentException("method should return String: " + clas + "."+ methodString );
                }
                String infoString;
                if ( Modifier.isStatic(method.getModifiers()) ) {
                    infoString= (String)method.invoke( null, id );
                } else {
                    infoString= (String)method.invoke( id );
                }
                try {
                    return new JSONObject(infoString);
                } catch ( JSONException ex ) {
                    throw new IllegalArgumentException("JSON parse error for String returned by " + clas + "."+ methodString);
                }


            } else {
                Class[] cc= new Class[args.length()];
                Object[] oo= new Object[args.length()];
                for ( int i=0; i<cc.length; i++ ) {
                    try {
                        oo[i]= args.get(i);
                        cc[i]= oo[i].getClass();
                        if ( cc[i]==String.class ) { // check for macros
                            String s= SpawnRecordSource.doMacros( HAPI_HOME, id, (String)oo[i] );
                            oo[i]= s;
                        }
                    } catch (JSONException ex) {
                        Logger.getLogger(SourceRegistry.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                Method method= c.getMethod( methodString, cc );
                logger.log(Level.FINER, "about to call into method to get info: {0}", method);
                String infoString= (String)method.invoke( id, oo );
                try {
                    return new JSONObject(infoString);
                } catch ( JSONException ex ) {
                    throw new IllegalArgumentException("JSON parse error for String returned by " + clas + "."+ methodString);
                }
            }

        } catch ( ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException ex ) {
            throw new RuntimeException(ex);
        }
         
    }
        
    /**
     * read the landing page configuration.  Note this is never served to clients.
     * @param HAPI_HOME
     * @return
     * @throws IOException
     * @throws JSONException 
     */
    public static JSONObject getLandingConfig( String HAPI_HOME ) throws IOException, JSONException {
        logger.fine("getLandingConfig");
        
        JSONObject result= loadAndCheckConfig( HAPI_HOME, "x-landing.json", new JSONObject() );
        return result;
        
    }
    
    private static JSONObject DEFAULT_ABOUT;
    
    static {
        String ss= Util.getTemplateAsString("about.json");
        try {
            DEFAULT_ABOUT= new JSONObject(ss);
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }
        
    /**
     * read the about file from the config directory if it has been modified.
     * @param HAPI_HOME
     * @return JSON for the about file.
     * @throws IOException
     * @throws JSONException 
     */
    public static JSONObject getAbout( String HAPI_HOME ) throws IOException, JSONException {
        
        logger.fine("getAbout");
        
        JSONObject result= loadAndCheckConfig( HAPI_HOME, "about.json", DEFAULT_ABOUT );
        result.put( "x_buildTime", Util.buildTime() );
        result.put( "HAPI", Util.hapiVersion() );
                
        return result;
    }


    /**
     * read the semantics file from the config directory if it has been modified.
     * Note this was an experimental feature which was never accepted.
     * @param HAPI_HOME
     * @return JSON for the about file.
     * @throws IOException
     * @throws JSONException 
     */
    public static JSONObject getSemantics( String HAPI_HOME ) throws IOException, JSONException {
        
        logger.fine("getSemantics");
        
        String ff= "semantics.json";
        
        JSONObject jo= loadAndCheckConfig(HAPI_HOME, ff, new JSONObject() );
        
        return jo;
    }
    
    
    /**
     * read the relations file from the config directory if it has been modified.
     * Note this was an experimental feature which was never accepted.
     * 
     * @param HAPI_HOME
     * @return JSON for the about file.
     * @throws IOException
     * @throws JSONException 
     */
    public static JSONObject getRelations( String HAPI_HOME ) throws IOException, JSONException {
        
        logger.fine("getRelations");
        
        String ff= "relations.json";
        
        JSONObject jo= loadAndCheckConfig(HAPI_HOME, ff, new JSONObject() );

        return jo;
    }
    
    private static JSONObject DEFAULT_CAPABILITIES;
    
    static {
        String ss= Util.getTemplateAsString("capabilities.json");
        try {
            DEFAULT_CAPABILITIES= new JSONObject(ss);
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * read the capabilities file from the config directory if it has been modified.
     * @param HAPI_HOME
     * @return JSON for the about file.
     * @throws IOException
     * @throws JSONException 
     */
    public static JSONObject getCapabilities( String HAPI_HOME ) throws IOException, JSONException {
        
        logger.fine("getCapabilities");
        
        String ff= "capabilities.json";
        
        JSONObject jo= loadAndCheckConfig(HAPI_HOME, ff, DEFAULT_CAPABILITIES );
      
        return jo;
    }
    
    private static JSONObject loadAndCheckConfig(String HAPI_HOME, String ff) throws IOException, JSONException {
        return loadAndCheckConfig( HAPI_HOME, ff, null );
    }

    /**
     * load the file, checking to see if there's a newer version in the config area, and loading the 
     * initial version from the templates area or deft object.
     * @param HAPI_HOME
     * @param typeFileName the name of the file, one of "about.json", "x-landing.json", "semantics.json", or "relations.json"
     * @param deft a deft value to use, if null then load from templates area.
     * @return the JSON object for the file, or deft if the configuration is not found.
     * @throws IOException
     * @throws JSONException 
     */
    private static JSONObject loadAndCheckConfig(String HAPI_HOME, String typeFileName, JSONObject deft ) throws IOException, JSONException {
        Initialize.maybeInitialize( HAPI_HOME );
        
        if ( typeFileName.contains("..") ) {
            throw new IllegalArgumentException("ff cannot contain ..");
        }
        
        if ( !Pattern.matches("[a-z\\-]+.json", typeFileName) ) {
            throw new IllegalArgumentException("ff must match [a-z]+");
        }
        
        File releaseFile= new File( HAPI_HOME, typeFileName );
        long releaseFileTimeStamp= releaseFile.exists() ? releaseFile.lastModified() : 0;
        File configDir= new File( HAPI_HOME, "config" );
        File typeFile= new File( configDir, typeFileName ); // typeFile is the file in the config directory, when config.json is not used.
        File catalogConfigFile= getConfigFile(HAPI_HOME);
        
        if ( !typeFile.exists() ) {

            if ( !catalogConfigFile.exists() ) {
                throw new IOException("config directory should contain config.json or "+ catalogConfigFile);
            }
            
            if ( catalogConfigFile.lastModified() > releaseFileTimeStamp ) {
            
                byte[] bb= Files.readAllBytes( Paths.get( catalogConfigFile.toURI() ) );
                String s= new String( bb, Charset.forName("UTF-8") );
                try {
                    JSONObject jo= Util.newJSONObject(s);
                
                    int i= typeFileName.indexOf(".");
                    String item= typeFileName.substring(0,i);
                    if ( jo.has(item) ) {
                        deft= jo.getJSONObject(item); // the item is defined in config.json
                    }
                    typeFile= catalogConfigFile;
                }  catch ( JSONException ex ) {
                    warnWebMaster(ex);
                }
            }
        }
        
        logger.log(Level.FINE, " configFile.lastModified(): {0}", typeFile.lastModified());
        logger.log(Level.FINE, " latestTimeStamp: {0}", releaseFileTimeStamp);
        if ( typeFile.lastModified() > releaseFileTimeStamp ) { // verify that it can be parsed and then copy it. 
            synchronized (HapiServerSupport.class) {
                if ( typeFile.lastModified() > releaseFileTimeStamp ) {
                    byte[] bb= Files.readAllBytes( Paths.get( typeFile.toURI() ) );
                    String s= new String( bb, CHARSET );
                    try {
                        logger.log(Level.INFO, "read {0} from config", typeFile);
                        JSONObject jo= Util.newJSONObject(s);
                        jo.put("x_hapi_home",HAPI_HOME);
                        
                        if ( typeFile.getName().endsWith("config.json") ) {
                            int i= typeFileName.indexOf(".");
                            String item= typeFileName.substring(0,i);
                            if ( jo.has(item) ) {
                                deft= jo.getJSONObject(item);
                            }
                        } else {
                            deft= jo;
                        }
                        
                        if ( deft==null ) {
                            return deft;
                        }

                        try ( InputStream ins= new ByteArrayInputStream(deft.toString().getBytes(CHARSET) ) ) {
                            logger.log(Level.INFO, "write resolved config to {0}", releaseFile.getPath());
                            File tmpFile= getTmpFile(HAPI_HOME,typeFileName);
                            Files.copy( ins,
                                    tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING );
                            Files.move( tmpFile.toPath(), releaseFile.toPath(), StandardCopyOption.ATOMIC_MOVE );
                        }
                    } catch ( JSONException ex ) {
                        warnWebMaster(ex);
                        throw ex;
                    }
                }
            }
        }
        
        logger.log(Level.FINE, "reading config json from {0}", releaseFile );
        byte[] bb= Files.readAllBytes( Paths.get( releaseFile.toURI() ) );
        String s= new String( bb, Charset.forName("UTF-8") );
        JSONObject jo= Util.newJSONObject(s);
        
        JSONObject status= Util.newJSONObject();
        status.put( "code", 1200 );
        status.put( "message", "OK request successful");
                
        jo.put( "status", status );
        jo.put("HAPI", HAPI_VERSION);    
        
        jo.put("x_modificationDate", TimeUtil.fromMillisecondsSince1970(releaseFileTimeStamp) );
     
        return jo;
    }
    
    /**
     * return the file which was once catalog.json and is now config.json.  This
     * file contains either the literal catalog response, or a configuration 
     * which will generate the catalog response.  If HAPI_HOME/config/config.json 
     * does not exist, then HAPI_HOME/config/catalog.json is returned.
     * @param HAPI_HOME
     * @return 
     */
    private static File getConfigFile( String HAPI_HOME ) {
        File configDirectory= new File( HAPI_HOME, "config" );
        
        File configFile= new File( configDirectory, "config.json" );
        if ( configFile.exists() ) {
            return configFile;
        } else {
            File catalogFile= new File( configDirectory, "catalog.json" );
            return catalogFile;
        }
    }
    
    /**
     * keep and monitor a cached version of the catalog in memory.
     * TODO: remove x_groups, which shows some server internals.  This should be done in the servlet code, before it leaves the server.
     * @param HAPI_HOME the location of the server definition
     * @return the JSONObject for the catalog.
     * @throws java.io.IOException 
     * @throws org.codehaus.jettison.json.JSONException 
     */
    public static JSONObject getCatalog( String HAPI_HOME ) throws IOException, JSONException {
        
        logger.fine("getCatalog");
        
        Initialize.maybeInitialize( HAPI_HOME );
        
        boolean caching= true;
        
        File catalogFile= new File( HAPI_HOME, "catalog.json" );
        CatalogData cc= catalogCache.get( HAPI_HOME );

        long latestTimeStamp= catalogFile.lastModified();
        
        File catalogConfigFile= getConfigFile(HAPI_HOME);     
        
        if ( !catalogConfigFile.exists() ) {
            throw new IOException("config directory should contain config.json or catalog.json");
        }
        
        if ( !caching || ( catalogConfigFile.lastModified() > latestTimeStamp ) ) { // verify that it can be parsed and then copy it. //TODO: synchronized
            byte[] bb= Files.readAllBytes( Paths.get( catalogConfigFile.toURI() ) );
            String s= new String( bb, Charset.forName("UTF-8") );
            try {
                JSONObject jo= Util.newJSONObject(s);
                
                logger.info("resolveCatalog");
                jo= resolveCatalog( HAPI_HOME, jo );
                jo.put("x_modificationDate", TimeUtil.fromMillisecondsSince1970(catalogConfigFile.lastModified()) );

                try ( InputStream ins= new ByteArrayInputStream(jo.toString(4).getBytes(CHARSET) ) ) {
                    logger.log(Level.INFO, "write resolved catalog to {0}", catalogFile.getPath());
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
        
        logger.info("reading catalog into json");
        byte[] bb= Files.readAllBytes( Paths.get( catalogFile.toURI() ) );
        String s= new String( bb, Charset.forName("UTF-8") );
        JSONObject jo= Util.newJSONObject(s);
        
        JSONObject status= Util.newJSONObject();
        status.put( "code", 1200 );
        status.put( "message", "OK request successful");
                
        jo.put( "status", status );
        
        jo.put("HAPI", HAPI_VERSION);
        
        cc= new CatalogData(jo,latestTimeStamp);

        JSONArray ids= jo.getJSONArray("catalog");
        for ( int i=0; i<ids.length(); i++ ) {
            JSONObject dataset= ids.getJSONObject(i);
            cc.datasetIds.add(dataset.getString("id"));
        }
                
        catalogCache.put( HAPI_HOME, cc );
        return jo;
    }
    
    public static final Object HAPI_VERSION_3_0 = "3.0";

    public static final Object HAPI_VERSION_3_1 = "3.1";
    
    public static final Object HAPI_VERSION_3_2 = "3.2";

    public static final Object HAPI_VERSION = HAPI_VERSION_3_2;
    
    /**
     * keep and monitor a cached version of the configuration in memory.
     * @param HAPI_HOME the location of the server definition
     * @param id the dataset identifier
     * @return the JSONObject for the configuration.
     * @throws java.io.IOException 
     * @throws org.codehaus.jettison.json.JSONException 
     * @throws org.hapiserver.exceptions.HapiException 
     * @see #loadAndCheckConfig(java.lang.String, java.lang.String) 
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
                String stime= TimeUtil.fromMillisecondsSince1970(latestTimeStamp);
                jo.put( "modificationDate", stime );
            } else if ( !( modificationDate.length()>0 && Character.isDigit( modificationDate.charAt(0) ) ) ) {
                try {
                    String stime= TimeUtil.formatIso8601TimeBrief( ExtendedTimeUtil.parseTime( modificationDate ) );
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
            long expiresTimeStamp= System.currentTimeMillis()+CONFIG_CACHE_FILE_MAX_LIFE_MILLIS;
            InfoData infoData= new InfoData(jo,latestTimeStamp,expiresTimeStamp );
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
            jo.put( "startDate",  TimeUtil.formatIso8601TimeBrief( ExtendedTimeUtil.parseTime( jo.getString("startDate") ) ) );
        } catch (ParseException ex) {
            throw new RuntimeException(ex);
        }
        try {
            jo.put( "stopDate",  TimeUtil.formatIso8601TimeBrief( ExtendedTimeUtil.parseTime( jo.getString("stopDate") ) ) );
        } catch (ParseException ex) {
            throw new RuntimeException(ex);
        }
        
        if ( jo.has("sampleStartDate" ) ) {
            try {
                jo.put( "sampleStartDate", 
                    TimeUtil.formatIso8601TimeBrief( ExtendedTimeUtil.parseTime( jo.getString("sampleStartDate") ) ) );
            } catch (ParseException ex) {
                throw new RuntimeException(ex);
            }
        }
        if ( jo.has("sampleStopDate" ) ) {
            try {
                jo.put( "sampleStopDate", 
                    TimeUtil.formatIso8601TimeBrief( ExtendedTimeUtil.parseTime( jo.getString("sampleStopDate") ) ) );
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
     * @throws org.hapiserver.exceptions.HapiException 
     * @throws IllegalArgumentException for bad id.
     */
    public static JSONObject getDataConfig( String HAPI_HOME, String id ) throws IOException, JSONException, HapiException {
        
        Initialize.maybeInitialize( HAPI_HOME );
        
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
                logger.log(Level.INFO, "parsing {0}", dataConfigFile);
                byte[] bb= Files.readAllBytes( Paths.get( dataConfigFile.toURI() ) );
                String s= new String( bb, Charset.forName("UTF-8") );
                jo= Util.newJSONObject(s);
            } else {
                logger.log(Level.INFO, "using {0}", config.toString() );
                jo= config;
            }
            
            
            try {
                if ( !jo.has("data") ) {
                    getInfo( HAPI_HOME, id );
                    JSONObject jo1= cc.catalog.optJSONObject("x_dataset_to_group");
                    String group= jo1.optString( id, null );
                    if ( group!=null ) {
                        config= cc.catalog.optJSONObject("x_groups");
                        if ( config==null ) throw new BadRequestIdException( safeId );
                        config= config.getJSONObject(group);
                        jo= config;
                    } else {
                        throw new BadRequestIdException( safeId );
                    }
                }
                jo= jo.getJSONObject("data");
                String dataString= jo.toString(4);
                if ( !file.getParentFile().exists() ) {
                    if ( !file.getParentFile().mkdirs() ) {
                        throw new RuntimeException("unable to create folder for dataset id: " + id );
                    }
                }
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
     * verifies that the info object contains required tags.  Note the config directory can contain infos from an earlier version
     * of HAPI, and they will be converted to a newer version if possible.  This is run once each time an info object is changed.
     * 
     * @param jo a JSONObject
     * @return true if valid
     * @throws IllegalArgumentException when not valid
     */
    public static boolean validInfoObject( JSONObject jo ) throws IllegalArgumentException {
        //String hapiVersion= jo.optString("HAPI","" );
        //if ( !hapiVersion.equals(Util.hapiVersion()) ) throw new IllegalArgumentException("HAPI version must be "+Util.hapiVersion());
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
        try {
            JSONArray parameters=  jo.getJSONArray("parameters");
            if ( parameters.length()==0 ) throw new IllegalArgumentException("info contains no parameters");
            JSONObject time= parameters.getJSONObject(0);
            if ( !time.getString("type").equals("isotime") ) throw new IllegalArgumentException("First parameter must be isotime");
            for ( int i=0; i<parameters.length(); i++ ) {
                JSONObject param=  parameters.getJSONObject(i);
                String name= param.getString("name");
                if ( name==null ) throw new IllegalArgumentException( String.format( "parameter #%d must have name",i ) );
                String type= param.getString("type");
                if ( type==null ) throw new IllegalArgumentException( String.format( "paramter \"%s\" must have type", name ) );
                if ( type.equals("isotime") || type.equals("string") ) {
                    if ( param.optInt("length",-1)==-1 ) {
                        throw new IllegalArgumentException( String.format( "parameter \"%s\" of type \"%s\" must have length",name,type ) );
                    }
                }
                JSONArray bins= param.optJSONArray("bins");
                if ( bins!=null ) {
                    for ( int j=0; j<bins.length(); j++ ) {
                        JSONObject bin= bins.getJSONObject(j);
                        if ( !bin.has("name") ) {
                            throw new IllegalArgumentException( String.format( "paramter \"%s\" bins[%d] has no name", name, j ) );
                        }
                        if ( !bin.has("units") ) {
                            throw new IllegalArgumentException( String.format( "paramter \"%s\" bins[%d] has no units", name, j ) );
                        }
                    }
                }
            }
        } catch (JSONException ex) {
            Logger.getLogger(HapiServerSupport.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return true;
    }
    
    /**
     * number of millis to allow a cached info to be used.
     */
    private static final long CONFIG_CACHE_FILE_MAX_LIFE_MILLIS=10000;
    
    /**
     * copy the stream into a temporary file, then move it into position once it is downloaded.  This will
     * create the directory for the file as well.
     * 
     * @param ins the input stream
     * @param infoFile the destination
     * @throws IOException 
     */
    private static void copyStreamSafely( InputStream ins, File infoFile ) throws IOException {
        File parentFile= infoFile.getParentFile();
        if ( !parentFile.exists() ) {
            synchronized ( HapiServerSupport.class ) {
                if ( !parentFile.exists() ) {
                    if ( !parentFile.mkdirs() ) {
                        throw new IllegalArgumentException("unable to make directory for info");
                    }
                }
            }
        }
        Path tmpFile= Files.createTempFile( parentFile.toPath(), infoFile.getName(), "--" + Thread.currentThread().getName() );
        try {
            Files.copy( ins, tmpFile, StandardCopyOption.REPLACE_EXISTING );
            synchronized ( HapiServerSupport.class ) {
                Files.move( tmpFile, infoFile.toPath(), StandardCopyOption.REPLACE_EXISTING );
            }
        } catch ( IOException ex ) {
            throw ex;
        } finally {
            if ( tmpFile.toFile().exists() ) {
                tmpFile.toFile().delete();
            }
        }
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
     * @throws IllegalArgumentException for a bad id.
     * @throws BadRequestIdException for a bad id.
     */
    public static JSONObject getInfo( String HAPI_HOME, String id ) throws IOException, JSONException, HapiException {
        
        Initialize.maybeInitialize( HAPI_HOME );
        
        boolean caching= true;
        
        File infoDir= new File( HAPI_HOME, "info" );
        String safeId= Util.fileSystemSafeName(id);
        File infoFile= new File( infoDir, safeId + ".json" );

        getCatalog(HAPI_HOME);
        
        CatalogData cc= catalogCache.get( HAPI_HOME );
        
        // Test to see that it is an identified id.
        if ( !cc.datasetIds.contains(id) ) {
            throw new BadRequestIdException("",id);
        }
        
        long latestTimeStamp= infoFile.exists() ? infoFile.lastModified() : 0;
        
        File configFile= new File( HAPI_HOME, "config" );
        
        File infoConfigFile= new File( configFile, safeId + ".json" );
        JSONObject config=null;
        
        if ( !infoConfigFile.exists() ) {
            if ( cc==null ) throw new BadRequestIdException("",id);
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
                if ( thisId.has("x_group_id") ) {
                    String groupId= thisId.getString("x_group_id");
                    JSONObject groups= cc.catalog.getJSONObject("x_groups");
                    config= groups.getJSONObject(groupId);
                    infoConfigFile= getConfigFile( HAPI_HOME );
                } else {
                    if ( cc.catalog.has("x_dataset_to_group") ) {
                        JSONObject map= cc.catalog.getJSONObject("x_dataset_to_group");
                        String groupId= map.getString(id);
                        JSONObject groups= cc.catalog.getJSONObject("x_groups");
                        config= groups.getJSONObject(groupId);
                        infoConfigFile= getConfigFile( HAPI_HOME );
                    }
                }

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
        
        // read infoFile (HAPI_HOME/info/ID.json) to see if it contains an expiration.
        JSONObject cachedInfoJsonObject=null;
        if ( infoFile.exists() ) {
            byte[] bb= Files.readAllBytes( Paths.get( infoFile.toURI() ) );
            String s= new String( bb, Charset.forName("UTF-8") );
            cachedInfoJsonObject= Util.newJSONObject(s);
            if ( !cachedInfoJsonObject.optBoolean("x_info_caching",true) ) {
                caching= false;
            }
        }
            
        if ( !caching || ( configTimeStamp - latestTimeStamp > 0 ) ) { // verify that it can be parsed and then copy it.
                
            cachedInfoJsonObject= null;
            
            JSONObject jo;
            if ( config==null ) {
                byte[] bb= Files.readAllBytes( Paths.get( infoConfigFile.toURI() ) );
                String s= new String( bb, Charset.forName("UTF-8") );
                jo= Util.newJSONObject(s);
            } else {
                jo= config;
            }
            
            try {
                if ( !jo.has("info") ) throw new IllegalArgumentException("info node not found in config");
                Object t= jo.get("info");
                if ( t instanceof JSONObject ) {
                    jo= (JSONObject)t;
                } else {
                    if ( t==null ) {
                        throw new IllegalArgumentException("info node is not found.");
                    } else {
                        throw new IllegalArgumentException("info node is not JSONObject, it is "+t.getClass());
                    }
                }
                String source= jo.optString("source",jo.optString("x_source","") );
                if ( source.length()>0 ) {
                    switch (source) {
                        case "spawn":
                            jo= getInfoFromSpawnCommand( jo, HAPI_HOME, id );
                            caching= jo.optBoolean("x_info_caching",true);
                            if ( caching ) {
                                try ( InputStream ins= new ByteArrayInputStream(jo.toString(4).getBytes(CHARSET) ) ) {
                                    copyStreamSafely( ins, infoFile );
                                } catch ( Exception ex ) {
                                    throw ex;
                                }
                            }
                            break;
                        case "classpath":
                            jo= getInfoFromClasspath( jo, HAPI_HOME, id );
                            caching= jo.optBoolean("x_info_caching",true);
                            if ( caching ) {
                                try ( InputStream ins= new ByteArrayInputStream(jo.toString(4).getBytes(CHARSET) ) ) {
                                    copyStreamSafely( ins, infoFile );
                                } catch ( Exception ex ) {
                                    throw ex;
                                }
                            }
                            break;
                        default:
                            warnWebMaster(new RuntimeException("catalog source can only be spawn or classpath") );
                            break;
                    }
                } else {
                    validInfoObject(jo);
                    caching= jo.optBoolean("x_info_caching",true);
                    if ( caching ) {
                        try ( InputStream ins= new ByteArrayInputStream(jo.toString(4).getBytes(CHARSET) ) ) {
                            copyStreamSafely( ins, infoFile );
                        }
                    }
                }
                if ( caching ) {
                    latestTimeStamp= infoFile.lastModified();
                    cachedInfoJsonObject= jo;
                } else {
                    latestTimeStamp= new Date().getTime();
                    cachedInfoJsonObject= jo;
                }
            } catch ( JSONException | IllegalArgumentException ex ) {
                warnWebMaster(ex);
            }
        }
        
        InfoData infoData= cc.infoCache.get( safeId );
        if ( infoData!=null ) {
            if ( infoData.infoTimeStamp==latestTimeStamp ) {
                JSONObject jo= infoData.info;
                jo= resolveTimes(jo);
                return jo;
            }
        }

        JSONObject jo;
        if ( cachedInfoJsonObject!=null ) {
            jo= cachedInfoJsonObject;
        } else {
            if ( caching==false ) throw new IllegalArgumentException("caching is disabled yet we have no object loaded, this is a server implementation error");
            byte[] bb= Files.readAllBytes( Paths.get( infoFile.toURI() ) );
            String s= new String( bb, Charset.forName("UTF-8") );
            jo= Util.newJSONObject(s);
        }
        
        if ( jo.has("modificationDate") ) {
            String modificationDate= jo.getString("modificationDate");
            if ( modificationDate.length()==0 ) {
                String stime= TimeUtil.fromMillisecondsSince1970(latestTimeStamp);
                jo.put( "modificationDate", stime );
            } else if ( !( modificationDate.length()>0 && Character.isDigit( modificationDate.charAt(0) ) ) ) {
                try {
                    String stime= TimeUtil.formatIso8601TimeBrief( ExtendedTimeUtil.parseTime( modificationDate ) );
                    jo.put( "modificationDate", stime );
                } catch (ParseException ex) {
                    throw new IllegalArgumentException(ex);
                }
            }
        }
        
        JSONObject jo0= jo;
        
        jo= resolveTimes(jo);
        
        JSONObject status= Util.newJSONObject();
        status.put( "code", 1200 );
        status.put( "message", "OK request successful");
                
        jo.put( "status", status );
        
        jo.put("HAPI", HAPI_VERSION);
        
        cc= catalogCache.get( HAPI_HOME );
        if ( cc==null ) {
            getCatalog(HAPI_HOME); // create a cache entry
        }
        synchronized (HapiServerSupport.class) {
            cc= catalogCache.get( HAPI_HOME );
            if ( cc==null ) {
                throw new IllegalArgumentException("This should not happen");
            }
            long expiresTimeStamp= System.currentTimeMillis()+CONFIG_CACHE_FILE_MAX_LIFE_MILLIS;
            infoData= new InfoData(jo0,latestTimeStamp,expiresTimeStamp);
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
        logger.info("warnWebMaster see catalina.out or server log files.");
        ex.printStackTrace();
    }
    
    /**
     * find the common parts of the two strings.
     * @param n1
     * @param n2
     * @return 
     */
    public static String findCommon( String n1, String n2 ) {
        int beginCommon=0;
        while ( n1.length()>beginCommon && n2.length()>beginCommon && 
            n1.charAt(beginCommon)==n2.charAt(beginCommon) ) {
            beginCommon++;
        }
        int endCommon= 1;
        while ( n1.length()-endCommon>=0 && n2.length()-endCommon>=0 && 
            n1.charAt(n1.length()-endCommon)==n2.charAt(n2.length()-endCommon) ) {
            endCommon++;
        }        
        if ( endCommon-1 > beginCommon ) {
            return n1.substring(n1.length()-endCommon+1);
        } else {
            return n1.substring(0,beginCommon);
        }
    }
    /**
     * take out all the common parts of the names.  Often the names contain the group they are in, and 
     * it would be convenient to have a shortened version of the name.
     * @param common
     * @param names
     * @return
     * @throws JSONException 
     */
    public static String[] maybeShortenLabels( String common, String[] names ) throws JSONException {
        String[] result= new String[names.length];
        if ( names[0].endsWith(common) ) {
            for ( int i=0; i<names.length; i++ ) {
                if ( names[i].endsWith(common) ) {
                    result[i]= "..." + names[i].substring(0,names[i].length()-common.length());
                } else {
                    return names;
                }
            }
        } else if ( names[0].startsWith(common) ) {
            for ( int i=0; i<names.length; i++ ) {
                if ( names[i].startsWith(common) ) {
                    result[i]= names[i].substring(common.length()) + "...";
                } else {
                    return names;
                }
            }
        } else {
            return names;
        }
        return result;
        
    }
    
}
