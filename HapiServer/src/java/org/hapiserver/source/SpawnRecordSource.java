
package org.hapiserver.source;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.CsvHapiRecordConverter;
import org.hapiserver.ExtendedTimeUtil;
import org.hapiserver.HapiRecord;
import org.hapiserver.HapiRecordSource;
import org.hapiserver.HapiServerSupport;
import org.hapiserver.TimeUtil;
import org.hapiserver.URITemplate;
import org.hapiserver.Util;
import org.hapiserver.exceptions.BadRequestParameterException;

/**
 * RecordSource that spawns command and reads the result.  This is configured with a JSON object like so:
 * <pre>
 *   "data": {
 *      "source": "spawn",
 *      "command": "/home/jbf/ct/hapi/git/server-java/SSCWebServer/src/SSCWebRecordSource.sh data ace ${start} ${stop}",
 *      "timeFormat": "$Y-$m-$d",
 *      "granuleSize": "P1D"
 *   }
 * </pre>
 * Here command is the command which is run on the command line, with start, stop, format, parameters, and HAPI_HOME macros.
 * And the control timeFormat is used to format the start and stop times.  "stepSize" will cause the calls to be broken up 
 * into separate calls for each step.  
 * @author jbf
 */
public class SpawnRecordSource implements HapiRecordSource {

    private static final Logger logger = Util.getLogger();
    String hapiHome;
    String id;
    JSONObject info;
    String command;
    String timeFormat; // "$Y-$m-$d"
    URITemplate uriTemplate;
    int[] granuleSize;
    
    /**
     * create a new SpawnRecordSource for the command.  Examples include:<ul>
     * <li>"python ${HAPI_HOME}/bin/Example.py --params ${parameters} --start ${start} --stop ${stop} --fmt ${format}"
     * <li>"cat /tmp/mydata.csv"
     * </ul>
     * 
     * @param hapiHome the server configuration directory, containing catalog.json
     * @param id the dataset id
     * @param info the info for the data set.
     * @param dataConfig description of how data records are created
     */
    public SpawnRecordSource( String hapiHome, String id, JSONObject info, JSONObject dataConfig ) {
        this.hapiHome= hapiHome;
        this.id= id;
        this.info= info;
        this.command= dataConfig.optString("command","");
        if ( this.command.equals("") ) {
            throw new IllegalArgumentException("command not found for spawn");
        }
        String tf= dataConfig.optString("timeFormat","");
        if ( tf.length()>0 ) {
            this.timeFormat= tf;
            this.uriTemplate= new URITemplate(this.timeFormat);
        } else {
            this.timeFormat= null;
        }
        
        String granuleSize= dataConfig.optString("granuleSize","");
        if ( granuleSize.length()>0 ) {
            try {
                this.granuleSize= TimeUtil.parseISO8601Duration( granuleSize );
            } catch (ParseException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Override
    public boolean hasGranuleIterator() {
        return this.granuleSize!=null;
    }

    @Override
    public Iterator<int[]> getGranuleIterator(int[] start, int[] stop) {
        try {
            String time0= this.uriTemplate.format( TimeUtil.formatIso8601Time(start),  TimeUtil.formatIso8601Time(start) );
            int[] time= this.uriTemplate.parse( time0 );
                
            return new Iterator<int[]>() {
                int[] timeIt= time;
                
                @Override
                public boolean hasNext() {
                    return ExtendedTimeUtil.gt(stop, timeIt );
                }
                
                @Override
                public int[] next() {
                    int[] result= timeIt;
                    timeIt = ExtendedTimeUtil.nextRange( timeIt );
                    return result;
                }
            };
            
        } catch ( ParseException e ) {
            throw new RuntimeException(e); // we would have already thrown exception in constructor
        }
    }

    @Override
    public boolean hasParamSubsetIterator() {
        return this.command.contains("${parameters}");
    }

    @Override
    public Iterator<HapiRecord> getIterator(int[] start, int[] stop, String[] params) {
        try {
            JSONObject infoSubset= Util.subsetParams( info, HapiServerSupport.joinParams( info, params ) );
            return new SpawnRecordSourceIterator( hapiHome, id, infoSubset, command, start, stop, params );
        } catch ( BadRequestParameterException | JSONException ex ) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public Iterator<HapiRecord> getIterator(int[] start, int[] stop) {
        return new SpawnRecordSourceIterator( hapiHome, id, info, command, start, stop, null );
    }

    @Override
    public String getTimeStamp(int[] start, int[] stop) {
        return null;
    }

    /**
     * add code for implementing hapiHome and id macros.
     * @param hapiHome
     * @param id
     * @param command
     * @return 
     */
    public static String doMacros( String hapiHome, String id, String command ) {
        String[] ss= command.split("\\$\\{");
        for ( int i=0; i<ss.length; i++ ) {
            String s1= ss[i];
            if ( s1.startsWith("id}") ) {
                id= id.replaceAll(" ","+");
                if ( !Util.constrainedId(id) ) throw new IllegalArgumentException("id is not conformant");
                ss[i]= id + s1.substring(3);

            } else if ( s1.startsWith("HAPI_HOME}" ) ) {
                ss[i]= hapiHome + s1.substring(10);

            }
        }

        command= String.join("",ss);
        return command;
    }
    
    private class SpawnRecordSourceIterator implements Iterator<HapiRecord> {
        
        Process process;
        BufferedReader reader;
        String nextRecord;
        boolean initialized;
        CsvHapiRecordConverter converter;
        
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
                            if ( uriTemplate!=null ) {
                                String s= TimeUtil.formatIso8601Time( start );
                                ss[i] = uriTemplate.format( s, s ) + ss[i].substring(6) ;
                            } else {
                                ss[i]= TimeUtil.formatIso8601Time( start ) + ss[i].substring(6);
                            }
                        } else {
                            if ( s1.substring(5).startsWith(";format=" ) ) {
                                throw new IllegalArgumentException("should this support URI_Templates? Wait until JS version...");
                            } else {
                                throw new IllegalArgumentException("not supported: "+command);
                            }
                        }
                    } else if ( s1.startsWith("stop") ) {
                        if ( s1.length()>4 && s1.charAt(4)=='}' ) {
                            if ( uriTemplate!=null ) {
                                String s= TimeUtil.formatIso8601Time( stop );
                                ss[i] = uriTemplate.format( s, s ) + ss[i].substring(5) ; 
                            } else {
                                ss[i]= TimeUtil.formatIso8601Time( stop ) + ss[i].substring(5);
                            }
                        } else {
                            throw new IllegalArgumentException("not supported: "+command);
                        }
                    } else if ( s1.startsWith("parameters}") ) {
                        if ( params==null ) throw new IllegalArgumentException("parameters found in command, server implementation error");
                        ss[i]= HapiServerSupport.joinParams( info, params ) + s1.substring(11);
                        
                    } else if ( s1.startsWith("id}") ) {
                        id= id.replaceAll(" ","+");
                        if ( !Util.constrainedId(id) ) throw new IllegalArgumentException("id is not conformant");
                        ss[i]= id + s1.substring(3);
                        
                    } else if ( s1.startsWith("format}" ) ) {
                        ss[i]= "csv" + s1.substring(7); // we always parse and reformat, and output must be csv for now
                        
                    } else if ( s1.startsWith("HAPI_HOME}" ) ) {
                        ss[i]= hapiHome + s1.substring(10);
                        
                    }
                }
                
                command= String.join("",ss);
                
                logger.log(Level.INFO, "spawn command {0}", command);
                ss= command.split("\\s+");
                
                ProcessBuilder pb= new ProcessBuilder( ss );
                process= pb.start();
                reader= new BufferedReader( new InputStreamReader( process.getInputStream() ) );
                nextRecord= reader.readLine();
                if ( nextRecord==null ) {
                    String errorMessage= 
                        new BufferedReader( new InputStreamReader( process.getErrorStream() ) )
                            .lines().collect( Collectors.joining("\n") );
                    logger.fine( errorMessage );
                }
                converter= new CsvHapiRecordConverter(info);
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
