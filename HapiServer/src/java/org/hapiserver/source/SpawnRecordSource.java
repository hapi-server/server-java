
package org.hapiserver.source;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.CSVHapiRecordConverter;
import org.hapiserver.HapiRecord;
import org.hapiserver.HapiRecordSource;
import org.hapiserver.HapiServerSupport;
import org.hapiserver.TimeUtil;
import org.hapiserver.Util;

/**
 * RecordSource that spawns command and reads the result
 * @author jbf
 */
public class SpawnRecordSource implements HapiRecordSource {

    private static final Logger logger = Util.getLogger();
    String hapiHome;
    String id;
    JSONObject info;
    String command;
    
    /**
     * create a new SpawnRecordSource for the command.  Examples include:<ul>
     * <li>"python ${HAPI_HOME}/bin/Example.py --params ${parameters} --start ${start} --stop ${stop} --fmt ${format}"
     * <li>"cat /tmp/mydata.csv"
     * </ul>
     * 
     * @param hapiHome the server configuration directory, containing catalog.json
     * @param id the dataset id
     * @param info the info for the data set.
     * @param source the source string 
     */
    public SpawnRecordSource( String hapiHome, String id, JSONObject info, String source ) {
        this.hapiHome= hapiHome;
        this.id= id;
        this.info= info;
        this.command= source.substring(6);
    }

    @Override
    public boolean hasGranuleIterator() {
        return false;
    }

    @Override
    public Iterator<int[]> getGranuleIterator(int[] start, int[] stop) {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    public boolean hasParamSubsetIterator() {
        return this.command.contains("${parameters}");
    }

    @Override
    public Iterator<HapiRecord> getIterator(int[] start, int[] stop, String[] params) {
        return new SpawnRecordSourceIterator( hapiHome, id, info, command, start, stop, params );
    }

    @Override
    public Iterator<HapiRecord> getIterator(int[] start, int[] stop) {
        return new SpawnRecordSourceIterator( hapiHome, id, info, command, start, stop, null );
    }

    @Override
    public String getTimeStamp(int[] start, int[] stop) {
        return null;
    }

    private static class SpawnRecordSourceIterator implements Iterator<HapiRecord> {
        
        Process process;
        BufferedReader reader;
        String nextRecord;
        boolean initialized;
        CSVHapiRecordConverter converter;
        
        /**
         * Create a new SpawnRecordSourceIterator
         * @param hapiHome the server home
         * @param id the dataset id
         * @param info the info object
         * @param command the unix command to spawn
         * @param start the seven-component start time 
         * @param stop the seven-component stop time
         * @param params null or the parameters which should be sent
         */
        public SpawnRecordSourceIterator( String hapiHome, String id, JSONObject info, String command, int[] start, int[] stop, String[] params ) {
            try {
                String[] ss= command.split("\\$\\{");
                for ( int i=0; i<ss.length; i++ ) {
                    String s1= ss[i];
                    if ( s1.startsWith("start") ) {
                        if ( s1.length()>5 && s1.charAt(5)=='}' ) {
                            ss[i]= TimeUtil.formatIso8601Time( start ) + ss[i].substring(6);
                        } else {
                            if ( s1.substring(5).startsWith(";format=" ) ) {
                                throw new IllegalArgumentException("should this support URI_Templates? Wait until JS version...");
                            } else {
                                throw new IllegalArgumentException("not supported: "+command);
                            }
                        }
                    } else if ( s1.startsWith("stop") ) {
                        if ( s1.length()>4 && s1.charAt(4)=='}' ) {
                            ss[i]= TimeUtil.formatIso8601Time( stop ) + ss[i].substring(5);
                        } else {
                            throw new IllegalArgumentException("not supported: "+command);
                        }
                    } else if ( s1.startsWith("parameters}") ) {
                        if ( params==null ) throw new IllegalArgumentException("parameters found in command, server implementation error");
                        ss[i]= HapiServerSupport.joinParams( info, params ) + s1.substring(11);
                        
                    } else if ( s1.startsWith("id}") ) {
                        if ( !Util.constrainedId(id) ) throw new IllegalArgumentException("id is not conformant");
                        ss[i]= id + s1.substring(3);
                        
                    } else if ( s1.startsWith("format}" ) ) {
                        ss[i]= "csv" + s1.substring(7); // we always parse and reformat, and output must be csv for now
                        
                    } else if ( s1.startsWith("HAPI_HOME}" ) ) {
                        ss[i]= hapiHome + s1.substring(10);
                        
                    }
                }
                
                command= String.join("",ss);
                
                ss= command.split("\\s+");
                
                ProcessBuilder pb= new ProcessBuilder( ss );
                process= pb.start();
                reader= new BufferedReader( new InputStreamReader( process.getInputStream() ) );
                nextRecord= reader.readLine();
                converter= new CSVHapiRecordConverter(info);
            } catch (JSONException | IOException ex ) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public boolean hasNext() {
            return nextRecord!=null;
        }

        @Override
        public HapiRecord next() {
            try {
                String t= nextRecord;
                nextRecord= reader.readLine();
                return converter.convert(t);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

}
