
package org.hapiserver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Catalog servlet serves list of available data sets.
 * @author jbf
 */
@WebServlet(urlPatterns = {"/catalog"})
public class CatalogServlet extends HttpServlet {

    private static final Logger logger= Util.getLogger();

    private String HAPI_HOME;
    
    @Override
    public void init() throws ServletException {
        super.init(); 
        HAPI_HOME= getServletContext().getInitParameter("hapi_home");
        logger.log(Level.INFO, "hapi_home is {0}", HAPI_HOME);
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
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
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
        
        File catalogFile= new File( HAPI_HOME, "catalog.json" );
        if ( catalogFile.exists() ) {
            logger.log(Level.FINE, "using cached about file {0}", catalogFile);
            Util.sendFile(catalogFile, request, response );
            
        } else {
            synchronized ( this ) {
                if ( !catalogFile.exists() ) { // double-check within synchronized block
                    logger.log(Level.INFO, "copy catalog.json from internal templates to {0}", catalogFile);
                    InputStream in= Util.getTemplateAsStream("catalog.json");
                    File tmpFile= new File( HAPI_HOME, "_catalog.json" );
                    Util.transfer( in, new FileOutputStream(tmpFile), true );
                    if ( !tmpFile.renameTo(catalogFile) ) {
                        logger.log(Level.SEVERE, "Unable to write to {0}", catalogFile);
                        throw new IllegalArgumentException("unable to write catalog file");
                    } else {
                        logger.log(Level.FINE, "wrote cached catalog file {0}", catalogFile);
                    }
                }
                logger.log(Level.FINE, "using cached catalog file {0}", catalogFile);
            }
            Util.sendFile( catalogFile, request, response );
        }
        
    }

}