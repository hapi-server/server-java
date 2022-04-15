
package org.hapiserver;

import java.io.IOException;
import java.io.PrintWriter;
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

        try {
            response.setContentType("application/json;charset=UTF-8");
            
            response.setHeader("Access-Control-Allow-Origin", "* " );
            response.setHeader("Access-Control-Allow-Methods","GET" );
            response.setHeader("Access-Control-Allow-Headers","Content-Type" );
            
            JSONObject catalog= HapiServerSupport.getCatalog(HAPI_HOME);
            
            try (PrintWriter out = response.getWriter()) {
                catalog.setEscapeForwardSlashAlways(false);
                String s= catalog.toString(4);
                out.write(s);
            } catch ( JSONException ex ) {
                throw new ServletException(ex);
            }
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
        
    }

}
