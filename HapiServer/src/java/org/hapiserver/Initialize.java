
package org.hapiserver;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 * One place where the hapi-server home directory is configured.  This folder
 * should be writable by the web server, and should be sufficiently large
 * that caching can be performed there.
 * @author jbf
 */
public class Initialize {
    
    private static final Logger logger= Logger.getLogger("hapi");
    
    /**
     * This is the default location for the HAPI Server configuration.  When the
     * configuration is this, then check the environment variable HAPI_HOME to
     * see if it has been set.
     */
    public static final String DEFAULT_HAPI_HOME = "/tmp/hapi-server/";
            
    /**
     * initialize the HAPI_HOME if does not exist or the config directory does not exist.
     * @param hapiHome 
     */
    public static void maybeInitialize( String hapiHome ) {
        if ( hapiHome==null ) {
            throw new IllegalArgumentException("hapiHome is not set, set in web.xml or with environment variable HAPI_HOME.");
        } else {
            File f= new File( hapiHome );
            if ( !f.exists() ) {
                Initialize.initialize(f);
            } else {
                File configFile= new File( f, "config" );
                if ( !configFile.exists() ) {
                    Initialize.initialize(f);
                }
            }
        }        
    }
    
    /**
     * initialize the hapi_home area, writing initial configuration.  
     * 
     * @param hapiHome directory where configuration is stored.
     */
    public static synchronized void initialize( File hapiHome ) {
        
        logger.log(Level.INFO, "initialize to create config directory");
        
        if ( hapiHome.exists() ) {
            if ( !hapiHome.canWrite() ) {
                throw new RuntimeException("Unable to write in hapi_home: "+hapiHome);
            }
        } else {
            if ( !hapiHome.mkdirs() ) {
                throw new RuntimeException("Unable to make hapi_home: "+hapiHome);
            }
        }

        File configLock= new File( hapiHome, "config.lock" );
        
        try {
    
            try ( PrintWriter write= new PrintWriter( new FileWriter( configLock ) ) ) {
                write.println( System.getProperty("CATALINA_PID","unknownPID") );
            }
        
            File configDir= new File( hapiHome, "config" );
            if ( !configDir.mkdirs() ) {
                throw new RuntimeException("Unable to make config area: "+configDir);
            }

            File aboutFile= new File( configDir, "about.json" );
            
            logger.log(Level.INFO, "copy about.json from internal templates to {0}", aboutFile);
            
            InputStream in= Util.getTemplateAsStream("about.json");
            File tmpFile= new File( configDir, "_about.json" );
            Util.transfer( in, new FileOutputStream(tmpFile), true );
            if ( !tmpFile.renameTo(aboutFile) ) {
                logger.log(Level.SEVERE, "Unable to write to {0}", aboutFile);
                throw new IllegalArgumentException("unable to write about file");
            } else {
                logger.log(Level.FINE, "wrote config about file {0}", aboutFile);
            }

            File catalogFile= new File( configDir, "catalog.json" );
            
            logger.log(Level.INFO, "copy catalog.json from internal templates to {0}", catalogFile);
            
            in= Util.getTemplateAsStream("catalog.json");
            tmpFile= new File( configDir, "_catalog.json" );
            Util.transfer( in, new FileOutputStream(tmpFile), true );
            if ( !tmpFile.renameTo(catalogFile) ) {
                logger.log(Level.SEVERE, "Unable to write to {0}", catalogFile);
                throw new IllegalArgumentException("unable to write catalog file");
            } else {
                logger.log(Level.FINE, "wrote config catalog file {0}", catalogFile);
            }
            
            // load each of the template's info files.
            try {
                byte[] bb= Files.readAllBytes( Paths.get( catalogFile.toURI() ) );        
                JSONObject jo= new JSONObject( new String(bb,"UTF-8") );
                JSONObject jo2= HapiServerSupport.getCatalog(hapiHome.toString());
                JSONArray cat=  jo.getJSONArray("catalog");
                for ( int i=0; i<cat.length(); i++ ) {
                    try {
                        JSONObject ds= cat.getJSONObject(i);
                        String id= ds.getString("id");
                        id= Util.fileSystemSafeName(id);
                        in= Util.getTemplateAsStream(id + ".json");
                        tmpFile= new File( configDir, "_" + id + ".json" );
                        Util.transfer( in, new FileOutputStream(tmpFile), true );
                        File infoConfigFile = new File( configDir, id + ".json" );
                        if ( !tmpFile.renameTo( infoConfigFile ) ) {
                            logger.log(Level.SEVERE, "Unable to write to {0}", infoConfigFile);
                            throw new IllegalArgumentException("unable to write info file");
                        } else {
                            logger.log(Level.FINE, "wrote config info file {0}", infoConfigFile);
                        }
                    } catch ( IOException ex2 ) {
                        logger.log(Level.SEVERE, "Unable to write to {0}",ex2);
                    }
                }
            } catch ( JSONException ex ) {
                ex.printStackTrace();
            }
        
            File infoDir= new File( hapiHome, "info" );
            if ( !infoDir.exists() && !infoDir.mkdirs() ) {
                throw new RuntimeException("Unable to make info area");
            }

            File dataDir= new File( hapiHome, "data" );
            if ( !dataDir.exists() && !dataDir.mkdirs() ) {
                throw new RuntimeException("Unable to make data area");
            }

        } catch ( IOException ex ) {
            throw new RuntimeException(ex);
        } finally {
            if ( !configLock.delete() ) {
                logger.warning("unable to delete config.lock");
            }
        }
        
//        String[] examples= new String[] { "wind_swe_2m", "temperature", "spawnsource" };
//        
//        for ( String s: examples ) {
//            File exampleData= new File( configDir, s + ".json" );
//            try {
//                Util.transfer( Util.getTemplateAsStream( s+".json" ), new FileOutputStream(exampleData), true );
//            } catch (IOException ex) {
//                logger.log(Level.SEVERE, null, ex);
//            }
//        }
        
    }

    /**
     * This is the one spot where the HAPI_HOME is calculated.  If the value from the servletContext is
     * the default, Initialize.DEFAULT_HAPI_HOME, then also check if the environment variable HAPI_HOME
     * is set.
     * @param servletContext
     * @return the directory where the HAPI configuration is found. 
     */
    public static String getHapiHome(ServletContext servletContext) {
        String HAPI_HOME= servletContext.getInitParameter("hapi_home");
        if ( HAPI_HOME.equals(Initialize.DEFAULT_HAPI_HOME) ) {
            String x= System.getenv("HAPI_HOME");
            if ( x==null ) {
                x= System.getProperty("HAPI_HOME");
            }
            if ( x!=null ) {
                logger.info("setting HAPI_HOME with environment variable.");
                HAPI_HOME= x;
            }
        }
        return HAPI_HOME;
    }
}
