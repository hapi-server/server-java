
package org.hapiserver;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.exceptions.HapiException;
import org.hapiserver.source.AggregationRecordSource;
import org.hapiserver.source.HapiWrapperRecordSource;
import org.hapiserver.source.SpawnRecordSource;

/**
 * The source registry will be used to look up the object used to
 * service the data request.  This may be a Java class or a 
 * Java class which wraps a Unix process.
 * 
 * @author jbf
 */
public class SourceRegistry {
    
    private static SourceRegistry instance= new SourceRegistry();
    
    public static SourceRegistry getInstance() {
        return instance;
    }
        
    /**
     * return the record source for the id.  The list of sources
     * is growing as the server is developed:<ul>
     * <li>"aggregation" which to aggregates complete CSV records
     * <li>"hapiserver" data is provided by another server.
     * <li>"spawn" data is returned by executing a command at the command line.
     * <li>"classpath" a Java class is called to supply the records.
     * </ul>
     * @param hapiHome the root of the HAPI server configuration, containing files like "catalog.json" 
     * @param info the info for the data
     * @param id the HAPI id
     * @return null or the record source
     * @throws org.hapiserver.exceptions.HapiException
     */
    public HapiRecordSource getSource( String hapiHome, String id, JSONObject info ) throws HapiException {
        JSONObject data;
        try {
            data= HapiServerSupport.getDataConfig( hapiHome, id );
        } catch ( IOException | JSONException ex ) {
            throw new RuntimeException(ex);
        }
        
        String source= data.optString( "source", data.optString("x_source") );
        
        switch (source) {
            case "aggregation":
                return new AggregationRecordSource( hapiHome, id, info, data );
            case "spawn":
                return new SpawnRecordSource( hapiHome, id, info, data );
            case "hapiserver":
                return new HapiWrapperRecordSource( id, info, data );
            case "classpath":
                String clas= data.optString("class",data.optString("x_class"));
                if ( clas.endsWith(".java") ) {
                    throw new IllegalArgumentException("class should not end in .java");
                }
                ClassLoader cl=null;
                if ( data.has("classpath") || data.has("x_classpath") ) {
                    try {
                        String s= data.optString("classpath",data.optString("x_classpath"));
                        s= SpawnRecordSource.doMacros( hapiHome, id, s );
                        URL url;
                        if ( s.startsWith("http://") || s.startsWith("https://") || s.startsWith("file:") ) { 
                            url= new URL( s );
                        } else {
                            url= new File(s).toURI().toURL();
                        }
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
                        if ( clas.length()==0 ) {
                            throw new IllegalArgumentException("class is not specified in configuration");
                        }
                        c= Class.forName(clas);
                    }
                    Object o;
                    JSONArray args= data.optJSONArray("args"); //TODO: x_args
                    if ( args==null ) { // must have constructor that takes hapiHome, id, info, and data.
                        Constructor constructor= c.getConstructor( String.class, String.class, JSONObject.class, JSONObject.class );
                        o= constructor.newInstance( hapiHome, id, info, data );
                    } else {
                        Class[] cc= new Class[args.length()];
                        Object[] oo= new Object[args.length()];
                        for ( int i=0; i<cc.length; i++ ) {
                            try {
                                oo[i]= args.get(i);
                                cc[i]= oo[i].getClass();
                                if ( cc[i]==String.class ) { // check for macros
                                    String s= SpawnRecordSource.doMacros( hapiHome, id, (String)oo[i] );
                                    oo[i]= s;
                                }
                            } catch (JSONException ex) {
                                Logger.getLogger(SourceRegistry.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                        Constructor constructor= c.getConstructor( cc );
                        o= constructor.newInstance( oo );
                    }
                    if ( !( o instanceof HapiRecordSource ) ) {
                        throw new RuntimeException("classpath refers to class which is not an instance of HapiRecordSource");
                    } else {
                        HapiRecordSource result= (HapiRecordSource)o;
                        return result;
                    }
                } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | SecurityException ex) {
                    throw new RuntimeException(ex);
                }
            default:
                throw new IllegalArgumentException("unknown source: " + source );
        }
    }
    
}
