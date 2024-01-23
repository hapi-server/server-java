
package org.hapiserver;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
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
    
    private static final Logger logger= Logger.getLogger("hapi");
    
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
        JSONObject dataConfig;
        try {
            dataConfig= HapiServerSupport.getDataConfig( hapiHome, id );
        } catch ( IOException | JSONException ex ) {
            throw new RuntimeException(ex);
        }
        
        String source= dataConfig.optString( "source", dataConfig.optString("x_source") );
        
        switch (source) {
            case "aggregation":
                return new AggregationRecordSource( hapiHome, id, info, dataConfig );
            case "spawn":
                return new SpawnRecordSource( hapiHome, id, info, dataConfig );
            case "hapiserver":
                return new HapiWrapperRecordSource( id, info, dataConfig );
            case "classpath":
                String clas= dataConfig.optString("class",dataConfig.optString("x_class"));
                if ( clas.endsWith(".java") ) {
                    throw new IllegalArgumentException("class should not end in .java");
                }
                ClassLoader cl=null;
                if ( dataConfig.has("classpath") || dataConfig.has("x_classpath") ) {
                    try {
                        String s= dataConfig.optString("classpath",dataConfig.optString("x_classpath"));
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
                    JSONArray args= dataConfig.optJSONArray("args"); 
                    if ( args==null ) {
                        args= dataConfig.optJSONArray("x_args");
                    }
                    String method= dataConfig.optString("method",dataConfig.optString("x_method","") );
                    
                    if ( args==null ) { // must have constructor that takes hapiHome, id, info, and data.
                        try {
                            Constructor constructor= c.getConstructor( String.class, String.class, JSONObject.class, JSONObject.class );
                            o= constructor.newInstance( hapiHome, id, info, dataConfig );
                        } catch ( NoSuchMethodException ex ) {
                            logger.fine("Constructor not found.  Found constructors: ");
                            for ( Constructor constructor : c.getConstructors() ) {
                                logger.log(Level.FINE, "  {0}", constructor.toGenericString());
                            }
                            throw ex;
                        }
                    } else {
                        Class[] cc= new Class[args.length()];
                        Object[] oo= new Object[args.length()];
                        for ( int i=0; i<cc.length; i++ ) {
                            try {
                                oo[i]= args.get(i);
                                cc[i]= oo[i].getClass();
                                if ( cc[i]==String.class ) { // check for macros
                                    if ( oo[i].equals("${info}") ) {
                                        oo[i]= info;
                                        cc[i]= JSONObject.class;
                                    } else if ( oo[i].equals("${data-config}") ) {
                                        oo[i]= dataConfig;
                                    } else {
                                        String s= SpawnRecordSource.doMacros( hapiHome, id, (String)oo[i] );
                                        oo[i]= s;
                                    }
                                }
                            } catch (JSONException ex) {
                                Logger.getLogger(SourceRegistry.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                        if ( method.length()>0 ) { // call static method
                            Method m;
                            try {
                                m= c.getMethod( method, cc );
                            } catch ( NoSuchMethodException ex ) {
                                String[] arglist= new String[cc.length];
                                for ( int i=0; i<cc.length; i++ ) {
                                    arglist[i]= cc[i].getSimpleName();
                                }
                                throw new IllegalArgumentException("No such method: "+clas+"."+method+"("+String.join(",", arglist)+")");
                            }
                            o= m.invoke( null, oo );
                        } else {
                            Constructor constructor;
                            try {
                                constructor= c.getConstructor( cc );
                            } catch ( NoSuchMethodException ex ) {
                                String[] arglist= new String[cc.length];
                                for ( int i=0; i<cc.length; i++ ) {
                                    arglist[i]= cc[i].getSimpleName();
                                }
                                throw new IllegalArgumentException("No such constructor: "+clas+"("+String.join(",", arglist)+")");
                            }
                            o= constructor.newInstance( oo );
                        }
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
