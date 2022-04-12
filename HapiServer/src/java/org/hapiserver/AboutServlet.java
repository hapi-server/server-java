
package org.hapiserver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
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
 * Generate the HAPI server "about" response
 * @author jbf
 */
@WebServlet(urlPatterns = {"/about"})
public class AboutServlet extends HttpServlet {
    
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
        
        File aboutFile= new File( HAPI_HOME, "about.json" );
        if ( aboutFile.exists() ) {
            logger.log(Level.FINE, "using cached about file {0}", aboutFile);
            ByteArrayOutputStream outs= new ByteArrayOutputStream();
            Files.copy( aboutFile.toPath(), outs );
            try {
                JSONObject jo= new JSONObject( new String( outs.toByteArray(), HapiServerSupport.CHARSET ) );
                jo.setEscapeForwardSlashAlways(false);
                jo.put( "x_hapi_home", HAPI_HOME );
                Util.transfer( new ByteArrayInputStream( jo.toString(4).getBytes( HapiServerSupport.CHARSET) ), 
                    response.getOutputStream(), true );
            } catch (JSONException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
            
        } else {
            synchronized ( this ) {
                if ( !aboutFile.exists() ) { // double-check within synchronized block
                    logger.log(Level.INFO, "copy about.json from internal templates to {0}", aboutFile);
                    InputStream in= Util.getTemplateAsStream("about.json");
                    File tmpFile= new File( HAPI_HOME, "_about.json" );
                    Util.transfer( in, new FileOutputStream(tmpFile), true );
                    if ( !tmpFile.renameTo(aboutFile) ) {
                        logger.log(Level.SEVERE, "Unable to write to {0}", aboutFile);
                        throw new IllegalArgumentException("unable to write about file");
                    } else {
                        logger.log(Level.FINE, "wrote cached about file {0}", aboutFile);
                    }
                }
                logger.log(Level.FINE, "using cached about file {0}", aboutFile);
            }
            Util.sendFile( aboutFile, request, response );
        }
        
    }

}
