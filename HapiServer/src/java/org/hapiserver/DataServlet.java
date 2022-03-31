/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.hapiserver;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 *
 * @author jbf
 */
@WebServlet(urlPatterns = {"/data"})
public class DataServlet extends HttpServlet {

    private static final Logger logger= Util.getLogger();
    
    private String HAPI_HOME;
    
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
            throw new ServletException("unrecognized parameters: "+params);
        }
        
        logger.log(Level.FINE, "data request for {0} {1}/{2}", new Object[]{id, timeMin, timeMax});
        
        DataFormatter dataFormatter;
        if ( format.equals("binary") ) {
            throw new IllegalArgumentException("binary is not supported yet");
            //response.setContentType("application/binary");
            //dataFormatter= new BinaryDataFormatter();
            //response.setHeader("Content-disposition", "attachment; filename="
            //    + Ops.safeName(id) + "_"+timeMin+ "_"+timeMax + ".bin" );
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
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
                
        boolean allowStream= !stream.equals("false");

        OutputStream out = response.getOutputStream();
        
        long t0= System.currentTimeMillis();
        
        //if ( timer.equals("true") || Util.isTrustedClient(request) ) {
        //    out= new IdleClockOutputStream(out);
        //}
                
        File[] dataFiles= null; // cached data files
        dsiter= null;
        
        // Look to see if we can cover the time range using cached files.  These files
        // must: be csv, contain all data, cover all data within $Y$m$d
        boolean allowCache= false; //dataFormatter instanceof CsvDataFormatter;
        if ( allowCache ) {
//            File dataFileHome= new File( Util.getHapiHome(), "cache" );
//            dataFileHome= new File( dataFileHome, Util.fileSystemSafeName(id) );
//            if ( dataFileHome.exists() ) {
//                FileStorageModel fsm= FileStorageModel.create( FileSystem.create(dataFileHome.toURI()), "$Y/$m/$Y$m$d.csv.gz" );
//                File[] files= fsm.getFilesFor(dr); 
//                // make sure we have all files.
//                if ( files.length>0 ) {
//                    DatumRange dr1= fsm.getRangeFor(fsm.getNameFor(files[0]));
//                    while ( dr1.min().gt(dr.min()) ) dr1= dr1.previous();
//                    int nfiles= 0;
//                    while ( dr1.min().lt(dr.max()) ) {
//                        nfiles++;
//                        dr1= dr1.next();
//                    }
//                    if ( nfiles==files.length ) { // we have all files.
//                        dataFiles= files;
//                    }
//                }
//            }
        }
        
        logger.log(Level.FINE, "dataFiles(one): {0}", dataFiles);
        
        assert dataFiles==null; // caching is disabled
        if ( dataFiles==null ) {
            try {
                logger.log(Level.FINER, "data files is null at {0} ms.", System.currentTimeMillis()-t0);
                //dsiter= checkAutoplotSource( id, dr, allowStream );
                if ( id.equals("wind_swe_2m") ) {
                    dsiter= new WindSwe2mIterator( dr, ExtendedTimeUtil.getStopTime(dr) );
                } else {
                    throw new IllegalArgumentException("only wind_swe_2m supported");
                }
                
                logger.log(Level.FINER, "have dsiter {0} ms.", System.currentTimeMillis()-t0);
            } catch ( Exception ex ) {
                throw new IllegalArgumentException("Exception thrown by data read", ex);
            }
        }
        
        logger.log(Level.FINE, "dataFiles(two): {0}", dataFiles);
        logger.log(Level.FINE, "dsiter: {0}", dsiter);
        
        assert dataFiles==null; // caching is disabled
        if ( dataFiles!=null ) {
//            // implement if-modified-since logic, where a 302 can be used instead of expensive data response.
//            String ifModifiedSince= request.getHeader("If-Modified-Since");
//            logger.log(Level.FINE, "If-Modified-Since: {0}", ifModifiedSince);
//            if ( ifModifiedSince!=null ) {
//                try {
//                    long requestIfModifiedSinceMs1970= parseTime(ifModifiedSince);
//                    boolean can304= true;
//                    for ( File f: dataFiles ) {
//                        if ( f.lastModified()-requestIfModifiedSinceMs1970 > 0 ) {
//                            logger.log(Level.FINER, "file is newer than ifModifiedSince header: {0}", f);
//                            can304= false;
//                        }
//                    }
//                    logger.log(Level.FINE, "If-Modified-Since allows 304 response: {0}", can304);
//                    if ( can304 ) {
//                        response.setStatus( HttpServletResponse.SC_NOT_MODIFIED ); //304
//                        out.close();
//                        return;
//                    }
//                } catch ( ParseException ex ) {
//                    response.setHeader("X-WARNING-IF-MODIFIED-SINCE", "date cannot be parsed.");
//                }
//
//            }
        }
        
        response.setStatus( HttpServletResponse.SC_OK );
                
        JSONObject jo0;
        
        try {

            jo0= HapiServerSupport.getInfo( HAPI_HOME, id );
            int[] indexMap=null;
            
            if ( !parameters.equals("") ) {
                jo= Util.subsetParams( jo0, parameters );
                indexMap= (int[])jo.get("x_indexmap");
                if ( dsiter!=null ) {
                    dsiter= new SubsetFieldsDataSetIterator( dsiter, indexMap );
                }
            } else {
                jo= jo0;
            }
            
            if ( include.equals("header") ) {
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
            
//            if ( dataFiles!=null ) {
//                for ( File dataFile : dataFiles ) {
//                    cachedDataCsv( jo, out, dataFile, dr, parameters, indexMap );
//                }
//                
//                if ( out instanceof IdleClockOutputStream ) {
//                    logger.log(Level.FINE, "request handled with cache in {0} ms, ", new Object[]{System.currentTimeMillis()-t0, 
//                        ((IdleClockOutputStream)out).getStatsOneLine() });
//                } else {
//                    logger.log(Level.FINE, "request handled with cache in {0} ms.", System.currentTimeMillis()-t0);
//                }
//                return;
//            }
            
        } catch (JSONException ex) {
            throw new ServletException(ex);
        }

//        // To cache days, post a single-day request for CSV of all parameters.
//        boolean createCache= true;
//        if ( createCache && 
//                dataFormatter instanceof CsvDataFormatter &&
//                parameters.equals("") &&
//                TimeUtil.getSecondsSinceMidnight(dr.min())==0 && 
//                TimeUtil.getSecondsSinceMidnight(dr.max())==0 && 
//                dr.width().doubleValue(Units.seconds)==86400 || 
//                dr.width().doubleValue(Units.seconds)==86401 ) {
//            boolean proceed= true;
//            
//            File dataFileHome= new File( Util.getHapiHome(), "cache" );
//            dataFileHome= new File( dataFileHome, id );
//            if ( !dataFileHome.exists() ) {
//                if ( !dataFileHome.mkdirs() ) {
//                    logger.log(Level.FINE, "unable to mkdir {0}", dataFileHome);
//                    proceed=false;
//                }
//            }
//            if ( proceed && dataFileHome.exists() ) {
//                TimeParser tp= TimeParser.create( "$Y/$m/$Y$m$d.csv.gz");
//                String s= tp.format(dr);
//                File ff= new File( dataFileHome, s );
//                if ( !ff.getParentFile().exists() ) {
//                    if ( !ff.getParentFile().mkdirs() ) {
//                        logger.log(Level.FINE, "unable to mkdir {0}", ff.getParentFile());
//                        proceed= false;
//                    }
//                }
//                if ( !ff.exists() && !ff.getParentFile().canWrite() ) {
//                    proceed= false;
//                }
//                if ( proceed ) {
//                    FileOutputStream fout= new FileOutputStream(ff);
//                    GZIPOutputStream gzout= new GZIPOutputStream(fout);
//                    org.apache.commons.io.output.TeeOutputStream tout= new TeeOutputStream( out, gzout );
//                    out= tout;
//                    logger.log( Level.FINE, "wrote cache file {0}", ff );
//                } else {
//                    logger.log( Level.FINE, "unable to write file {0}", ff );
//                }
//            }
//        }
        
        try {
            assert dsiter!=null;
            while ( dsiter.hasNext() ) {
                logger.fine("dsiter has at least one record");
                            
                HapiRecord first= dsiter.next();
            
                dataFormatter.initialize( jo, out, first );
        
                String startTime= "2019-365T23:00Z";
                String stopTime= "2019-365T23:30Z";
                if ( first.getIsoTime(0).compareTo( startTime )>=0 && first.getIsoTime(0).compareTo( stopTime )<0 ) {
                    dataFormatter.sendRecord( out, first );
                }
                while ( dsiter.hasNext() ) {
                    HapiRecord record= dsiter.next();
                    String isoTime= record.getIsoTime(0);
                    if ( isoTime.compareTo( startTime )>=0 && isoTime.compareTo( stopTime )<0 ) {
                        dataFormatter.sendRecord( out,record );
                    }
                }
            }
            
            dataFormatter.finalize(out);
            
        } finally {
            
            out.close();
            
        }
        
        logger.log(Level.FINE, "request handled in {0} ms.", System.currentTimeMillis()-t0);

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
