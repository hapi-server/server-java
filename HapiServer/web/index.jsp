<%-- 
    Document   : index
    Created on : Oct 3, 2016, 6:52:22 AM
    Author     : jbf
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>JSP Page</title>
    </head>
    <body>
        <%
	        String redirectURL = "hapi/index.jsp";
	        response.sendRedirect(redirectURL);
	    %>
    </body>
</html>
