
package org.hapiserver;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 * Generate the HAPI server "capabilities" response
 * @author jbf
 */
@WebServlet(urlPatterns = {"/capabilities"})
public class CapabilitiesServlet extends HttpServlet {
    
    private static final Logger logger= Util.getLogger();

    private String HAPI_HOME;
    
    @Override
    public void init() throws ServletException {
        super.init(); 
        HAPI_HOME= getServletContext().getInitParameter("hapi_home");
        logger.log(Level.INFO, "hapi_home is {0}", HAPI_HOME);
    }
    
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
        response.setContentType("application/json;charset=UTF-8");
                
        response.setHeader("Access-Control-Allow-Origin", "* " );
        response.setHeader("Access-Control-Allow-Methods","GET" );
        response.setHeader("Access-Control-Allow-Headers","Content-Type" );
        
        File capabilitiesFile= new File( HAPI_HOME, "capabilities.json" );
        if ( capabilitiesFile.exists() ) {
            logger.log(Level.FINE, "using cached about file {0}", capabilitiesFile);
            sendFile(capabilitiesFile, request, response );
            
        } else {
            synchronized ( this ) {
                if ( !capabilitiesFile.exists() ) { // double-check within synchronized block
                    logger.log(Level.INFO, "copy capabilities.json from internal templates to {0}", capabilitiesFile);
                    InputStream in= Util.getTemplateAsStream("capabilities.json");
                    File tmpFile= new File( HAPI_HOME, "_capabilities.json" );
                    Util.transfer( in, new FileOutputStream(tmpFile), true );
                    if ( !tmpFile.renameTo(capabilitiesFile) ) {
                        logger.log(Level.SEVERE, "Unable to write to {0}", capabilitiesFile);
                        throw new IllegalArgumentException("unable to write capabilities file");
                    } else {
                        logger.log(Level.FINE, "wrote cached capabilities file {0}", capabilitiesFile);
                    }
                }
                logger.log(Level.FINE, "using cached capabilities file {0}", capabilitiesFile);
            }
            sendFile( capabilitiesFile, request, response );
        }
        
    }

    private void sendFile(File aboutFile, HttpServletRequest request, HttpServletResponse response) throws IllegalArgumentException, IOException {
        byte[] bb= Files.readAllBytes( Paths.get( aboutFile.toURI() ) );
        String s= new String( bb, Charset.forName("UTF-8") );
        if ( Util.isTrustedClient(request) ) {
            // security says this should not be shown in production use, but include for localhost
            JSONObject jo;
            try {
                jo = new JSONObject(s);
                jo.put("x_HAPI_SERVER_HOME", HAPI_HOME );
                jo.setEscapeForwardSlashAlways(false);
                s= jo.toString(4);
            } catch (JSONException ex) {
                throw new IllegalArgumentException(ex);
            }
        }
        // isTrustedClient used to verify the JSON structure
        Util.transfer( new ByteArrayInputStream(s.getBytes("UTF-8")), response.getOutputStream(), true );
    }

}
