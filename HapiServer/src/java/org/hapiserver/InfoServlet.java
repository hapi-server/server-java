
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
import org.hapiserver.exceptions.BadIdException;

/**
 * Info servlet describes a data set.
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
                JSONObject jo= HapiServerSupport.getInfo( HAPI_HOME, id );
                String parameters= request.getParameter("parameters");
                if ( parameters!=null) {
                    jo= Util.subsetParams(jo,parameters);
                }
                jo.setEscapeForwardSlashAlways(false);
                String s= jo.toString(4);
                out.write(s);
            } catch ( BadIdException ex ) {
                Util.raiseError( 1406, "Bad request - unknown dataset id " + id, response, out);
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
