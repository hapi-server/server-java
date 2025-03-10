
package org.hapiserver;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.exceptions.BadRequestIdException;
import org.hapiserver.exceptions.BadRequestParameterException;
import org.hapiserver.exceptions.HapiException;

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
        HAPI_HOME= Initialize.getHapiHome(getServletContext());
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
                    
        String dataset;
        
        // HAPI 3.x servers must accept both old and new parameters.
        dataset= request.getParameter("dataset");
        if ( dataset==null ) {
            dataset= request.getParameter("id"); 
        }            
        
        logger.log(Level.FINE, "info request for {0}", dataset);
        
        if ( dataset==null ) throw new ServletException("required parameter 'dataset' is missing from request");
        
        response.setContentType("application/json;charset=UTF-8");        
        
        response.setHeader("Access-Control-Allow-Origin", "* " );
        response.setHeader("Access-Control-Allow-Methods","GET" );
        response.setHeader("Access-Control-Allow-Headers","Content-Type" );

        JSONObject jo;
        try {
            
            jo = HapiServerSupport.getInfo( HAPI_HOME, dataset );
            
        } catch ( BadRequestIdException ex ) {
            OutputStream outs= response.getOutputStream();
            Util.raiseError( 1406, "HAPI error 1406: unknown dataset id", response, outs );
            outs.close();
            return;
        } catch ( HapiException | JSONException ex ) {
            throw new RuntimeException(ex);
        } catch (java.nio.file.NoSuchFileException ex ) {
            // don't show server-side information.
            Util.raiseError( 1406, "HAPI error 1406: unknown dataset id", response, response.getOutputStream() );
            return;
        }
        
        try ( OutputStream out = response.getOutputStream() ) {
            String parameters= request.getParameter("parameters");
            if ( parameters!=null) {
                parameters= parameters.replaceAll(" ","+");
                try {
                    jo= Util.subsetParams(jo,parameters);
                } catch ( BadRequestParameterException ex2 ) {
                    Util.raiseError( ex2, response, out );
                    return;
                }
            }
            jo.remove("x_indexmap");
            if ( !jo.has("HAPI") ) {
                jo.put("HAPI",Util.hapiVersion()); // TODO: this needs review.
            }
            JSONObject status= new JSONObject("{ \"code\":1200, \"message\":\"OK\" }");
            jo.put( "status", status );
            
            String s= jo.toString(4);
            out.write(s.getBytes( HapiServerSupport.CHARSET ));
            
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
