
package org.hapiserver;

import java.io.ByteArrayOutputStream;
import java.io.File;
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
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.exceptions.BadIdException;
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
        HAPI_HOME= getServletContext().getInitParameter("hapi_home");
        logger.log(Level.INFO, "hapi_home is {0}", HAPI_HOME);
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
        String id= getParam( params,"id",null,"The identifier for the resource.", null );
        String timeMin= getParam( params, "time.min", "", "The earliest value of time to include in the response.", null );
        String timeMax= 
            getParam( params, "time.max", "", "Include values of time up to but not including this time in the response.", null );
        if ( timeMin.length()==0 ) { // support 3.0
            timeMin= getParam( params, "start", null, "The earliest value of time to include in the response.", null );
            timeMax= 
                getParam( params, "stop", null, "Include values of time up to but not including this time in the response.", null );
        }
        String parameters= 
            getParam( params, "parameters", "", "The comma separated list of parameters to include in the response ", null );
        String include= getParam(params, "include", "", "include header at the top", PATTERN_INCLUDE);
        String format= getParam(params, "format", "", "The desired format for the data stream.", PATTERN_FORMAT);
        String stream= getParam(params, "_stream", "true", "allow/disallow streaming.", PATTERN_TRUE_FALSE);
        //String timer= getParam(params, "_timer", "false", "service request with timing output stream", PATTERN_TRUE_FALSE);
        
        if ( !params.isEmpty() ) {
            Util.raiseError( 1401, "Bad request - unknown API parameter name " + params.entrySet().iterator().next().getKey(), 
                response, response.getOutputStream() );
            return;
        }
        
        logger.log(Level.FINE, "data request for {0} {1}/{2}", new Object[]{id, timeMin, timeMax});
        
        DataFormatter dataFormatter;
        if ( format.equals("binary") ) {
            response.setContentType("application/binary");
            dataFormatter= new BinaryDataFormatter();
            response.setHeader("Content-disposition", "attachment; filename="
                + Util.fileSystemSafeName(id) + "_"+timeMin+ "_"+timeMax + ".bin" );
        } else {
            response.setContentType("text/csv;charset=UTF-8");  
            dataFormatter= new CsvDataFormatter();
            response.setHeader("Content-disposition", "attachment; filename=" 
                + Util.fileSystemSafeName(id) + "_"+timeMin+ "_"+timeMax + ".csv" ); 
        }
        
        
        response.setHeader("Access-Control-Allow-Origin", "* " );
        response.setHeader("Access-Control-Allow-Methods","GET" );
        response.setHeader("Access-Control-Allow-Headers","Content-Type" );
        
        int[] dr;
        try {
            dr = ExtendedTimeUtil.createTimeRange( TimeUtil.parseISO8601Time(timeMin), TimeUtil.parseISO8601Time(timeMax) );
        } catch ( ParseException ex ) {
            throw new RuntimeException(ex); //TODO: HAPI Exceptions
        }

        Iterator<HapiRecord> dsiter;
        
        JSONObject jo;
        try {
            jo= HapiServerSupport.getInfo( HAPI_HOME, id );
        } catch ( BadIdException ex ) {
            Util.raiseError( 1406, "HAPI error 1406: unknown dataset id " + id, response, response.getOutputStream() );
            return;
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
                
        // boolean allowStream= !stream.equals("false");

        OutputStream out = response.getOutputStream();
        
        long t0= System.currentTimeMillis();
        
        //if ( timer.equals("true") || Util.isTrustedClient(request) ) {
        //    out= new IdleClockOutputStream(out);
        //}
                
        dsiter= null;
        boolean dataNeedsParameterSubsetting;
        
        // allowCache code was here
        
        try {
            logger.log(Level.FINER, "data files is null at {0} ms.", System.currentTimeMillis()-t0);
            //dsiter= checkAutoplotSource( id, dr, allowStream );

            if ( id.equals("wind_swe_2m") ) {
                dsiter= new WindSwe2mIterator( dr, ExtendedTimeUtil.getStopTime(dr) );
                dataNeedsParameterSubsetting= true;
                
            } else {
                HapiRecordSource source= SourceRegistry.getInstance().getSource(HAPI_HOME, id, jo);

                String ifModifiedSince= request.getHeader("If-Modified-Since");

                if ( ifModifiedSince!=null ) {
                    String ts= source.getTimeStamp( dr, ExtendedTimeUtil.getStopTime(dr) );
                    if ( ts!=null ) { // this will often be null.
                        String clientModifiedTime= parseTime(ifModifiedSince);
                        if ( clientModifiedTime.compareTo(ts)>=0 ) {
                            response.setStatus( HttpServletResponse.SC_NOT_MODIFIED ); //304
                            out.close();
                            return;
                        }
                    }
                }
                
                if ( parameters.equals("") ) {
                    if ( source.hasParamSubsetIterator() ) {
                        String[] parametersArray= HapiServerSupport.getAllParameters( jo );
                        dataNeedsParameterSubsetting= false;
                        if ( source.hasGranuleIterator() ) {
                            dsiter= new AggregatingIterator( source, dr, ExtendedTimeUtil.getStopTime(dr), parametersArray );
                        } else {
                            dsiter= source.getIterator( dr, ExtendedTimeUtil.getStopTime(dr), parametersArray );
                        }
                    } else {
                        dataNeedsParameterSubsetting= false;                    
                        if ( source.hasGranuleIterator() ) {
                            dsiter= new AggregatingIterator( source, dr, ExtendedTimeUtil.getStopTime(dr) );
                        } else {
                            dsiter= source.getIterator( dr, ExtendedTimeUtil.getStopTime(dr) );
                        }
                    }
                } else {
                    if ( source.hasParamSubsetIterator() ) {
                        dataNeedsParameterSubsetting= false;
                        String[] parametersSplit= HapiServerSupport.splitParams( jo, parameters );
                        if ( source.hasGranuleIterator() ) {
                            dsiter= new AggregatingIterator( source, dr, ExtendedTimeUtil.getStopTime(dr), parametersSplit );
                        } else {
                            dsiter= source.getIterator(dr, ExtendedTimeUtil.getStopTime(dr), parametersSplit );
                        }                    
                    } else {
                        dataNeedsParameterSubsetting= true;                    
                        if ( source.hasGranuleIterator() ) {
                            dsiter= new AggregatingIterator( source, dr, ExtendedTimeUtil.getStopTime(dr) );
                        } else {
                            dsiter= source.getIterator( dr, ExtendedTimeUtil.getStopTime(dr) );
                        }
                    }
                    
                }
                    
                if ( dsiter==null ) {
                    Util.raiseError( 1500, "HAPI error 1500: internal server error, id has no reader " + id, 
                        response, response.getOutputStream() );
                    return;
                }
            }

            logger.log(Level.FINER, "have dsiter {0} ms.", System.currentTimeMillis()-t0);
        } catch ( Exception ex ) {
            throw new IllegalArgumentException("Exception thrown by data read", ex);
        }
        
        assert dsiter!=null;
        logger.log(Level.FINE, "dsiter: {0}", dsiter);
        
        boolean sentSomething= false;
                
        response.setStatus( HttpServletResponse.SC_OK );
                
        JSONObject jo0;
        
        try {

            jo0= HapiServerSupport.getInfo( HAPI_HOME, id );
            int[] indexMap;
            
            if ( !parameters.equals("") ) {
                jo= Util.subsetParams( jo0, parameters );
                indexMap= (int[])jo.get("x_indexmap");
                if ( dataNeedsParameterSubsetting ) {
                    dsiter= new SubsetFieldsDataSetIterator( dsiter, indexMap );
                }
            } else {
                jo= jo0;
            }
            
        } catch (JSONException ex) {
            throw new ServletException(ex);
        }
        
        boolean verify= true;
        boolean sendHeader= include.equals("header");
        
        try {
            assert dsiter!=null;
            while ( dsiter.hasNext() ) {
                logger.fine("dsiter has at least one record");
                            
                HapiRecord first= dsiter.next();
            
                dataFormatter.initialize( jo, out, first );
                
                if ( verify ) {
                    doVerify(dataFormatter, first, jo);
                    verify= false;
                }
                
                // format time boundaries so they are in the same format as the data, and simple string comparisons can be made.
                String startTime= TimeUtil.reformatIsoTime( first.getIsoTime(0), timeMin );
                String stopTime= TimeUtil.reformatIsoTime( first.getIsoTime(0), timeMax );
        
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
                Util.raiseError( 1201, "HAPI error 1201: no data found " + id, response, out );   
            }
            
            dataFormatter.finalize(out);
            
        } catch ( RuntimeException ex ) {
            Util.raiseError( 1500, ex.getMessage(), response, out );
            
        } finally {
            
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
        return ExtendedTimeUtil.fromMillisecondsSince1970(result.getTime());
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
