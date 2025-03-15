
package org.hapiserver;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * redirect the browser to the page without the slash.  So, <ul>
 * <li>.../hapi/info/?dataset=AC_H0_SWE/availability &rarr; .../hapi/info?dataset=AC_H0_SWE/availability
 * <li>.../hapi/ &rarr; .../hapi
 * <li>.../ &rarr; .../hapi
 * <li>.../happy &rarr; .../hapi
 * </ul>
 * @author jbf
 */
@WebServlet(name = "EmptyServlet", urlPatterns = {"/"})
public class EmptyServlet extends HttpServlet {

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
        String url= request.getRequestURI();
        if ( request.getQueryString()!=null ) {
            response.sendRedirect( url.substring(0,url.length()-1) + "?" + request.getQueryString() );
        } else {
            if ( !url.endsWith("/hapi") ) {
                int lastSlash= url.lastIndexOf("/");
                if ( lastSlash>-1 ) {
                    url= url.substring(0,lastSlash+1);
                }
                response.sendRedirect( url + "hapi" );
            } else {
                response.sendRedirect( url.substring(0,url.length()-1) );
            }
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
