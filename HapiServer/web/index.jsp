<%-- 
    Document   : index
    Created on : Mar 29, 2022, 8:56:44 AM
    Author     : jbf
--%>

<%@page import="org.hapiserver.Initialize"%>
<%@page import="org.hapiserver.Util"%>
<%@page import="java.io.File"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <title>HAPI Server</title>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
    </head>
    <body>
        <h1>Basic HAPI Server</h1>  More information about this type of server is found at <a href="https://github.com/hapi-server/server-java" target="_blank">GitHub</a>.
        This implementation of the HAPI server uses Autoplot URIs to load data, more information about Autoplot can be found <a href="http://autoplot.org" target="_blank">here</a>.

        <!-- <br>The HAPI server <a href="http://hapi-server.org/verify?url=">verifier</a> will test this HAPI server for correctness. -->

        <h3>Some example requests:</h3>
        <a href="catalog">Catalog</a> <i>Show the catalog of available data sets.</i><br>
        <a href="capabilities">Capabilities</a> <i>Capabilities of the server.</i><br>
        <a href="about">About</a> <i>More about this server, like contact info.</i><br>
        <br>
        
        <%
            String HAPI_HOME= getServletContext().getInitParameter("hapi_home");
            if ( HAPI_HOME==null ) {
                %>
                <h1>This server has not been configured.  Its hapi_home must be set.
                <%
            } else {
                File f= new File( HAPI_HOME );
                if ( !f.exists() ) {
                    Initialize.initialize(f);
                }
            }
            %>
    </body>
</html>
