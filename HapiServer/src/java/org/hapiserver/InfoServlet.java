/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.hapiserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 *
 * @author jbf
 */
@WebServlet(urlPatterns = {"/info"})
public class InfoServlet extends HttpServlet {
    
    private static final Logger logger= Util.getLogger();

    private String HAPI_HOME;
    
    @Override
    public void init() throws ServletException {
        super.init(); 
        HAPI_HOME= getServletContext().getInitParameter("hapi_home");
        logger.log(Level.INFO, "hapi_home is {0}", HAPI_HOME);
    }
    
    /**
     * 
     * @param id the identifier
     * @return the JSON object
     * @throws JSONException
     * @throws IllegalArgumentException if the id is not defined.
     * @throws IOException 
     * @throws ParseException
     */
    protected JSONObject getInfo( String id ) throws JSONException, IllegalArgumentException, IOException, ParseException {
        
        //if ( !HapiServerSupport.getCatalogIds().contains(id) ){
        //    throw new IllegalArgumentException("invalid parameter id: \""+id+"\" is not known.");
        //}
        
        JSONArray parameters= new JSONArray();

        File infoFileHome= new File( HAPI_HOME, "info" );
        File infoFile= new File( infoFileHome, Util.fileSystemSafeName(id)+".json" );
        
        if ( !infoFile.exists() ) {
            throw new FileNotFoundException("Server misconfiguration, expected to find file for "+id+" ("+Util.fileSystemSafeName(id)+")" );
        }
        
        Path path = infoFile.toPath();
        String json = String.join( "\n", Files.readAllLines(path) );
        JSONObject o= new JSONObject(json);
        
        o.put("HAPI","3.0");
        o.put("x_createdAt",String.format("%tFT%<tRZ",Calendar.getInstance(TimeZone.getTimeZone("Z"))));
        
        JSONArray parametersRead= o.getJSONArray("parameters");
        for ( int i=0; i<parametersRead.length(); i++ ) {
            JSONObject jo1= parametersRead.getJSONObject(i);
            jo1.setEscapeForwardSlashAlways(false); // How annoying!
            parameters.put( i,jo1  );
        }
        
        if ( o.has("modificationDate") ) { // allow modification date to be "lasthour"
            String modificationDate= o.getString("modificationDate");
            try {
                int[] ss= ExtendedTimeUtil.parseTime( modificationDate );
                o.put( "modificationDate", TimeUtil.formatIso8601Time(ss) );
            } catch ( ParseException ex ) {
                
            }
        }
        
        // support local features like "now-P3D", which are not hapi features.
        if ( o.has("startDate") && o.has("stopDate") ) { 
            String startDate= o.getString("startDate");
            String stopDate= o.getString("stopDate");
            o.put( "startDate", TimeUtil.formatIso8601Time( ExtendedTimeUtil.parseTime(startDate) ) );
            o.put( "stopDate", TimeUtil.formatIso8601Time( ExtendedTimeUtil.parseTime(stopDate) ) );
        } else {
            if ( !o.has("startDate") ) {
                logger.warning("non-conformant server needs to have startDate");
            }
            if ( !o.has("stopDate") ) {
                logger.warning("non-conformant server needs to have stopDate");
            }
        }
        
        if ( o.has("sampleStartDate") && o.has("sampleStopDate") ) { 
            String startDate= o.getString("sampleStartDate");
            String stopDate= o.getString("sampleStopDate");
            o.put( "sampleStartDate", TimeUtil.formatIso8601Time(ExtendedTimeUtil.parseTime(startDate) ) );
            o.put( "sampleStopDate", TimeUtil.formatIso8601Time( ExtendedTimeUtil.parseTime(stopDate) ) );
        }
        
        JSONObject status= new JSONObject();
        status.put( "code", 1200 );
        status.put( "message", "OK request successful");
                
        o.put( "status", status );
        o.put( "x_infoVersion__", "20220329.1" );
        return o;

    }

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
                    
        String id= request.getParameter("id");
        
        logger.log(Level.FINE, "info request for {0}", id);
        
        if ( id==null ) throw new ServletException("required parameter 'id' is missing from request");
        
        response.setContentType("application/json;charset=UTF-8");        
        
        response.setHeader("Access-Control-Allow-Origin", "* " );
        response.setHeader("Access-Control-Allow-Methods","GET" );
        response.setHeader("Access-Control-Allow-Headers","Content-Type" );
        
        try (PrintWriter out = response.getWriter()) {
            try {
                JSONObject jo= getInfo( id );
                String parameters= request.getParameter("parameters");
                if ( parameters!=null) {
                    jo= Util.subsetParams(jo,parameters);
                }
                jo.setEscapeForwardSlashAlways(false);
                String s= jo.toString(4);
                out.write(s);
            } catch ( FileNotFoundException ex ) {
                Util.raiseError( 1406, "bad id: "+id, response, out);
            } catch (IllegalArgumentException | ParseException ex) {
                throw new RuntimeException(ex);
            }
        } catch ( JSONException ex ) {
            throw new ServletException(ex);
        }
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
