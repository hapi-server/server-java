
package org.hapiserver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
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
 * Capabilities servlet indicates what formats are supported.
 * @author jbf
 */
@WebServlet(urlPatterns = {"/capabilities"})
public class CapabilitiesServlet extends HttpServlet {
    
    private static final Logger logger= Util.getLogger();

    private String HAPI_HOME;
    
    @Override
    public void init() throws ServletException {
        super.init(); 
        HAPI_HOME= Initialize.getHapiHome(getServletContext());
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
        
        try {
            JSONObject content= HapiServerSupport.getCapabilities(HAPI_HOME);
            String modificationDate= content.optString("x_modificationDate","");
            if ( modificationDate.length()>0 ) {
                SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
                sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
                String rfc2616= sdf.format( new Date( TimeUtil.toMillisecondsSince1970(modificationDate) ) );
                response.setHeader("Last-Modified", rfc2616 );
            }
            try (PrintWriter out = response.getWriter()) {
                String s= content.toString(4);
                out.write(s);
            } catch ( JSONException ex ) {
                throw new ServletException(ex);
            }
        } catch ( JSONException ex ) {
            throw new RuntimeException(ex);
        }
                
    }

}
