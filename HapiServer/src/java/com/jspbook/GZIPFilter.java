/*
 * Copyright 2003 Jayson Falkner (jayson@jspinsider.com)
 * This code is from "Servlets and JavaServer pages; the J2EE Web Tier",
 * http://www.jspbook.com. You may freely use the code both commercially
 * and non-commercially. If you like the code, please pick up a copy of
 * the book and help support the authors, development of more free code,
 * and the JSP/Servlet/J2EE community.
 */
package com.jspbook;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.*;
import javax.servlet.http.*;

/**
 * GZIPFilter compresses the response supporting efficient transmission of data.
 * @author jbf
 */
public class GZIPFilter implements Filter {

    private static final Logger logger = Logger.getLogger("hapi.gzip");

    public void doFilter(ServletRequest req, ServletResponse res,
            FilterChain chain) throws IOException, ServletException {
        if (req instanceof HttpServletRequest) {
            HttpServletRequest request = (HttpServletRequest) req;
            HttpServletResponse response = (HttpServletResponse) res;
            String ae = request.getHeader("accept-encoding");
            if (ae != null && ae.contains("gzip")) {
                logger.log(Level.FINER, "GZIP supported, compressing.");
                GZIPResponseWrapper wrappedResponse
                        = new GZIPResponseWrapper(response);
                chain.doFilter(req, wrappedResponse);
                wrappedResponse.finishResponse();
                return;
            }
            chain.doFilter(req, res);
        }
    }

    public void init(FilterConfig filterConfig) {
        logger.fine("initialize GZIPFilter");
        // noop
    }

    public void destroy() {
        // noop
    }
}
