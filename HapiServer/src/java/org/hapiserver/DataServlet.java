
package org.hapiserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.exceptions.BadRequestIdException;
import org.hapiserver.exceptions.HapiException;
import org.hapiserver.exceptions.ServerImplementationException;
import org.hapiserver.source.AggregatingIterator;

/**
 * Data servlet sends the data
 * @author jbf
 */
@WebServlet(urlPatterns = {"/data"})
public class DataServlet extends HttpServlet {

    private static final Logger logger= Util.getLogger();
    
    private String HAPI_HOME;
    
    private static Charset CHARSET= Charset.forName("UTF-8");
    
    @Override
    public void init() throws ServletException {
        super.init();
        HAPI_HOME= Initialize.getHapiHome( getServletContext() );
    }
    
    private String getParam( Map<String,String[]> request, String name, String deft, String doc, Pattern constraints ) {
        String[] vs= request.remove(name);
        String v;
        if ( vs==null ) {
            v= deft;
        } else {
            v= vs[0];
        }
        if ( v==null ) v= deft;
        if ( constraints!=null ) {
            if ( !constraints.matcher(v).matches() ) {
                throw new IllegalArgumentException("parameter "+name+"="+v +" doesn't match pattern");
            }
        }
        if ( v==null ) throw new IllegalArgumentException("required parameter "+name+" is needed");
        return v;
    }
    
    private static final Pattern PATTERN_TRUE_FALSE = Pattern.compile("(|true|false)");
    private static final Pattern PATTERN_FORMAT = Pattern.compile("(|csv|binary)");
    private static final Pattern PATTERN_INCLUDE = Pattern.compile("(|header)");
    
    /**
     * throw exception when times are out of bounds
     * @param info info for the parameter, which contains startDate and stopDate
     * @param start requested start time 
     * @param stop requested stop time
     * @return true if everything is okay, throw HapiException otherwise
     */
    private boolean check1405TimeRange( JSONObject info, String start, String stop ) throws HapiException {
        String startTime= info.optString("startDate","");
        String stopTime= info.optString("stopDate","");
        if ( startTime.length()==0 ) throw new IllegalArgumentException("info must contain startDate");
        if ( stopTime.length()==0 ) throw new IllegalArgumentException("info must contain stopDate");
        if ( stop.compareTo(start)<=0 ) {
            throw new HapiException( 1404, "Bad request - start equal to or after stop" );
        }
        try {
            start= TimeUtil.reformatIsoTime( startTime, start );
        } catch ( IllegalArgumentException ex ) {
            throw new HapiException( 1402, "Bad request - error in start time" );
        }
        try {
            stop= TimeUtil.reformatIsoTime( stopTime, stop );
        } catch ( IllegalArgumentException ex ) {
            throw new HapiException( 1403, "Bad request - error in stop time" );
        }
        if ( start.compareTo(startTime)<0 ) {
            throw new HapiException( 1405, "time outside valid range", "start time must be no earlier than "+startTime );
        }
        if ( stopTime.compareTo(stopTime)<0 ) {
            throw new HapiException( 1405, "time outside valid range", "stop time must be no later than "+stopTime );
        }
        if ( info.has("x_requestLimits") ) {
            try {
                JSONObject requestLimits= info.getJSONObject("x_requestLimits");
                String duration= requestLimits.optString("duration","");
                if ( duration.length()>0 ) {
                    try {
                        int[] iduration= TimeUtil.parseISO8601Duration(duration);
                        int[] istart= TimeUtil.parseISO8601Time(start);
                        int[] stopLimit= TimeUtil.add( istart, iduration );
                        String fstopLimit= TimeUtil.formatIso8601Time(stopLimit);
                        fstopLimit= TimeUtil.reformatIsoTime( stop, fstopLimit );
                        if ( stop.compareTo(fstopLimit)>0 ) {
                            throw new HapiException( 1408, "Bad request - too much time or data requested", "limit is "+duration );
                        }
                    } catch (ParseException ex) {
                        throw new ServerImplementationException("unable to parse time duration");
                    }
                }
            } catch (JSONException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
        return true;
    }
    
    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

        Map<String,String[]> params= new HashMap<>( request.getParameterMap() );
        
        String dataset;
        String start;
        String stop;

        // HAPI 3.0 servers must accept both old and new parameters.
        dataset= getParam( params,"id","","The identifier for the resource.", null );
        if ( dataset.equals("") ) {
            dataset= getParam( params,"dataset",null,"The identifier for the resource.", null ); // allowed in 3.0
        }
        start= getParam( params, "time.min", "", "The earliest value of time to include in the response.", null );
        stop= getParam( params, "time.max", "", "Include values of time up to but not including this time in the response.", null );
        if ( start.length()==0 ) {
            start= getParam( params, "start", null, "The earliest value of time to include in the response.", null );
            stop= getParam( params, "stop", null, "Include values of time up to but not including this time in the response.", null );
        }

        String parameters= 
            getParam( params, "parameters", "", "The comma separated list of parameters to include in the response ", null );
        if ( parameters!=null ) {
            parameters= parameters.replaceAll(" ","+");
        }
        String include= getParam(params, "include", "", "include header at the top", PATTERN_INCLUDE);
        String format= getParam(params, "format", "", "The desired format for the data stream.", PATTERN_FORMAT);
        
        if ( !params.isEmpty() ) {
            Util.raiseError( 1401, "Bad request - unknown API parameter name", 
                response, response.getOutputStream() );
            return;
        }
        
        try {
            TimeUtil.parseISO8601Time(start);
        } catch ( IllegalArgumentException | ParseException ex ) {
            Util.raiseError( 1402, "Bad request - syntax error in start time", response, response.getOutputStream() );
            return;
        }        
        
        try {
            TimeUtil.parseISO8601Time(stop);
        } catch ( IllegalArgumentException | ParseException ex ) {
            Util.raiseError( 1403, "Bad request - syntax error in stop time", response, response.getOutputStream() );            
            return;
        }
        
        if ( stop.length()>start.length() ) {
            start= TimeUtil.reformatIsoTime( stop, start );
        } else if ( start.length()>stop.length() ) {
            stop= TimeUtil.reformatIsoTime( start, stop );
        }
        
        logger.log(Level.FINE, "data request for {0} {1}/{2}", new Object[]{dataset, start, stop});
        
        DataFormatter dataFormatter;
        if ( format.equals("binary") ) {
            response.setContentType("application/binary");
            dataFormatter= new BinaryDataFormatter();
            response.setHeader("Content-disposition", "attachment; filename="
                + Util.fileSystemSafeName(dataset).replaceAll("\\/", "_" ) + "_"+start+ "_"+stop + ".bin" );
        } else {
            response.setContentType("text/csv;charset=UTF-8");  
            dataFormatter= new CsvDataFormatter();
            response.setHeader("Content-disposition", "attachment; filename=" 
                + Util.fileSystemSafeName(dataset).replaceAll("\\/", "_" ) + "_"+start+ "_"+stop + ".csv" ); 
        }
        
        
        response.setHeader("Access-Control-Allow-Origin", "* " );
        response.setHeader("Access-Control-Allow-Methods","GET" );
        response.setHeader("Access-Control-Allow-Headers","Content-Type" );
        
        Iterator<HapiRecord> dsiter;
        
        JSONObject jo;
        try {
            jo= HapiServerSupport.getInfo( HAPI_HOME, dataset );
        } catch ( BadRequestIdException ex ) {
            Util.raiseError( ex, response, response.getOutputStream() );
            return;
        } catch (JSONException | HapiException ex) {
            throw new RuntimeException(ex);
        }

        try {
            check1405TimeRange( jo, start, stop );
        } catch ( HapiException ex ) {
            try (ServletOutputStream out = response.getOutputStream()) {
                Util.raiseError( ex.getCode(), ex.getMessage(), response, out );
                return;
            }
        }

        OutputStream out = response.getOutputStream();

        int[] dr;
        try {
            dr = TimeUtil.createTimeRange( TimeUtil.parseISO8601Time(start), TimeUtil.parseISO8601Time(stop) );
        } catch ( ParseException ex ) {
            throw new RuntimeException(ex); //TODO: HAPI Exceptions
        }
        
        long t0= System.currentTimeMillis();

        boolean dataNeedsParameterSubsetting;
        
        // allowCache code was here
        
        logger.log(Level.FINER, "data files is null at {0} ms.", System.currentTimeMillis()-t0);
        //dsiter= checkAutoplotSource( id, dr, allowStream );

        HapiRecordSource source;
        try {
            source= SourceRegistry.getInstance().getSource(HAPI_HOME, dataset, jo);
        } catch ( BadRequestIdException ex ) {
            Util.raiseError( 1406, "HAPI error 1406: unknown dataset id", response, response.getOutputStream() );
            return;
        } catch ( HapiException ex ) {
            throw new RuntimeException(ex);
        }
        
        String ifModifiedSince= request.getHeader("If-Modified-Since");
        if ( ifModifiedSince!=null ) {
            String ts= source.getTimeStamp( dr, TimeUtil.getStopTime(dr) );
            if ( ts!=null ) { // this will often be null.
                try {
                    String clientModifiedTime= parseTime(ifModifiedSince);
                    if ( clientModifiedTime.compareTo(ts)>=0 ) {
                        response.setStatus( HttpServletResponse.SC_NOT_MODIFIED ); //304
                        out.close();
                        return;
                    }
                } catch ( ParseException ex ) {
                    logger.info( "client sends If-Modified-Since with unsupported format, ignoring");
                }
            }
        }

        if ( parameters.equals("") ) {
            if ( source.hasParamSubsetIterator() ) {
                String[] parametersArray= HapiServerSupport.getAllParameters( jo );
                dataNeedsParameterSubsetting= false;
                if ( source.hasGranuleIterator() ) {
                    dsiter= new AggregatingIterator( source, dr, TimeUtil.getStopTime(dr), parametersArray );
                } else {
                    dsiter= source.getIterator( TimeUtil.getStartTime(dr), TimeUtil.getStopTime(dr), parametersArray );
                }
            } else {
                dataNeedsParameterSubsetting= false;                    
                if ( source.hasGranuleIterator() ) {
                    dsiter= new AggregatingIterator( source, TimeUtil.getStartTime(dr), TimeUtil.getStopTime(dr) );
                } else {
                    dsiter= source.getIterator( TimeUtil.getStartTime(dr), TimeUtil.getStopTime(dr) );
                }
            }
        } else {
            if ( source.hasParamSubsetIterator() ) {
                dataNeedsParameterSubsetting= false;
                String[] parametersSplit= HapiServerSupport.splitParams( jo, parameters );
                if ( source.hasGranuleIterator() ) {
                    dsiter= new AggregatingIterator( source, TimeUtil.getStartTime(dr), TimeUtil.getStopTime(dr), parametersSplit );
                } else {
                    dsiter= source.getIterator( TimeUtil.getStartTime(dr), TimeUtil.getStopTime(dr), parametersSplit );
                }                    
            } else {
                dataNeedsParameterSubsetting= true;                    
                if ( source.hasGranuleIterator() ) {
                    dsiter= new AggregatingIterator( source, TimeUtil.getStartTime(dr), TimeUtil.getStopTime(dr) );
                } else {
                    dsiter= source.getIterator( TimeUtil.getStartTime(dr), TimeUtil.getStopTime(dr) );
                }
            }

        }

        if ( dsiter==null ) {
            Util.raiseError( 1500, "HAPI error 1500: internal server error, id has no reader " + dataset, 
                response, response.getOutputStream() );
            source.doFinalize();
            return;
        }

        logger.log(Level.FINER, "have dsiter {0} ms.", System.currentTimeMillis()-t0);


        assert dsiter!=null;
        logger.log(Level.FINE, "dsiter: {0}", dsiter);
        
        boolean sentSomething= false;
                
        response.setStatus( HttpServletResponse.SC_OK );
                
        JSONObject jo0;
        
        try {

            jo0= HapiServerSupport.getInfo( HAPI_HOME, dataset );
            int[] indexMap;
            
            if ( !parameters.equals("") ) {
                jo= Util.subsetParams( jo0, parameters );
                indexMap= (int[])jo.remove("x_indexmap");
                if ( dataNeedsParameterSubsetting ) {
                    dsiter= new SubsetFieldsDataSetIterator( dsiter, indexMap );
                }
            } else {
                jo= jo0;
            }
            
        } catch (JSONException ex) {
            throw new ServletException(ex);
        } catch (HapiException ex) {
            Logger.getLogger(DataServlet.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        boolean sendHeader= include.equals("header");
        
        try {
            assert dsiter!=null;
            if ( dsiter.hasNext() ) {
                logger.fine("dsiter has at least one record");
                            
                HapiRecord first= dsiter.next();
            
                logger.log(Level.FINER, "first record read from source: {0}", first.getIsoTime(0));
                
                dataFormatter.initialize( jo, out, first );
                
                doVerify(dataFormatter, first, jo);
                
                // format time boundaries so they are in the same format as the data, and simple string comparisons can be made.
                String startTime= TimeUtil.reformatIsoTime( first.getIsoTime(0), start );
                String stopTime= TimeUtil.reformatIsoTime( first.getIsoTime(0), stop );
        
                if ( first.getIsoTime(0).compareTo( startTime )>=0 && first.getIsoTime(0).compareTo( stopTime )<0 ) {
                    if ( sentSomething==false ) {
                        if ( sendHeader ) {
                            try {
                                sendHeader( jo, format, out);
                            } catch (JSONException | UnsupportedEncodingException ex) {
                                logger.log(Level.SEVERE, null, ex);
                            }
                        }
                        sentSomething= true;
                    }
                    dataFormatter.sendRecord( out, first );
                }
                while ( dsiter.hasNext() ) {
                    HapiRecord record= dsiter.next();
                    String isoTime= record.getIsoTime(0);
                    if ( isoTime.compareTo( startTime )>=0 && isoTime.compareTo( stopTime )<0 ) { //TODO: repeat code, consider do..while
                        if ( sentSomething==false ) {
                            if ( sendHeader ) {
                                try {
                                    sendHeader( jo, format, out);
                                } catch (JSONException | UnsupportedEncodingException ex) {
                                    logger.log(Level.SEVERE, null, ex);
                                }
                            }
                            sentSomething= true;
                        }
                        dataFormatter.sendRecord( out,record );
                    }
                }
            }

            if ( !sentSomething ) {
                Util.raiseError( 1201, "HAPI error 1201: no data found " + dataset, response, out );   
            }
            
            dataFormatter.finalize(out);
            
        } catch ( RuntimeException ex ) {
            Util.logError( ex );
            Util.raiseError( 1500, ex.getMessage(), response, out );
            
        } finally {
            
            source.doFinalize();
            out.close();
            
        }
        
        logger.log(Level.FINE, "request handled in {0} ms.", System.currentTimeMillis()-t0);

    }

    /**
     * verify that the output of the data formatter is consistent with the info response.
     * @param dataFormatter
     * @param first
     * @param jo
     * @throws IOException 
     */
    private void doVerify(DataFormatter dataFormatter, HapiRecord first, JSONObject jo) throws IOException {
        ByteArrayOutputStream testOut= new ByteArrayOutputStream(1024);
        dataFormatter.sendRecord( testOut, first);
        byte[] bb= testOut.toByteArray();
        try {
            int len= jo.getJSONArray("parameters").getJSONObject(0).getInt("length");
            if ( bb[len-1]!='Z' ) {
                logger.log(Level.WARNING,
                    "time is not the correct length or Z is missing, expected Z at byte offset {0}", len);
            }
        } catch (JSONException ex) {
            logger.log(Level.WARNING, null, ex);
        }
    }

    private void sendHeader(JSONObject jo, String format, OutputStream out) throws JSONException, IOException, UnsupportedEncodingException {
        ByteArrayOutputStream boas= new ByteArrayOutputStream(10000);
        PrintWriter pw= new PrintWriter(boas);
        jo.put( "format", format ); // Thanks Bob's verifier for catching this.
        pw.write( jo.toString(4) );
        pw.close();
        boas.close();
        String[] ss= boas.toString("UTF-8").split("\n");
        for ( String s: ss ) {
            out.write( "# ".getBytes("UTF-8") );
            out.write( s.getBytes("UTF-8") );
            out.write( (char)10 );
        }
    }
    
        
    /**
     * parse RFC 822, RFC 850, and asctime format.
     * @return the time in milliseconds since 1970-01-01T00:00Z.
     */
    private static String parseTime(String str) throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat( "EEE, dd MMM yyyy HH:mm:ss z");
        Date result;
        try {
            result= dateFormat.parse(str);
        } catch ( ParseException ex ) {
            dateFormat = new SimpleDateFormat( "EEE MMM dd HH:mm:ss yyyy" );
            try {
                result= dateFormat.parse(str);
            } catch ( ParseException ex2 ) {
                dateFormat = new SimpleDateFormat( "E, dd-MMM-yyyy HH:mm:ss z" );
                try {
                    result= dateFormat.parse(str);
                } catch ( ParseException ex3 ) {
                    return str;
                }
            }
        }
        return TimeUtil.fromMillisecondsSince1970(result.getTime());
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

}
