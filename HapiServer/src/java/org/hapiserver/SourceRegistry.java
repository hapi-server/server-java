
package org.hapiserver;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.source.DailyHapiRecordSource;
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
     * <li>"aggregation" which to aggregates of complete CSV records
     * <li>"hapiserver" data is provided by another server.
     * <li>"spawn" data is returned by executing a command at the command line.
     * </ul>
     * @param hapiHome the root of the HAPI server configuration, containing files like "catalog.json" 
     * @param info the info for the data
     * @param id the HAPI id
     * @return null or the record source
     */
    public HapiRecordSource getSource( String hapiHome, String id, JSONObject info ) {
        JSONObject data;
        try {
            data= HapiServerSupport.getDataConfig( hapiHome, id );
        } catch ( IOException | JSONException ex ) {
            throw new RuntimeException(ex);
        }
        
        String source= data.optString( "source", "" );
        
        switch (source) {
            case "aggregation":
                String files= data.optString( "files", "" );
                int nfields= info.optJSONArray("parameters").length();
                return DailyHapiRecordSource.fromAggregation(files,nfields);
            case "spawn":
                return new SpawnRecordSource( hapiHome, id, info, data );
            case "hapiserver":
                return new HapiWrapperRecordSource( id, info, data );
            case "classpath":
                String impl= data.optString("class");
                try {
                    Class c= SourceRegistry.class.forName(impl);
                    Constructor constructor= c.getConstructor( String.class, String.class, JSONObject.class, JSONObject.class );
                    Object o= constructor.newInstance( hapiHome, id, info, data );
                    if ( !( o instanceof HapiRecordSource ) ) {
                        throw new RuntimeException("classpath refers to class which is not an instance of HapiRecordSource");
                    } else {
                        HapiRecordSource result= (HapiRecordSource)o;
                        return result;
                    }
                } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | SecurityException ex) {
                    throw new RuntimeException(ex);
                }
            case "wind_swe_2m":
                return new WindSwe2mDataSource( );
            default:
                throw new IllegalArgumentException("unknown source: " + source );
        }
    }
    
}
