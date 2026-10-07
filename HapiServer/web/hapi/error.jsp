<%@page import="org.hapiserver.Config"%>
<%@ page isErrorPage="true" import="java.io.*" contentType="text/plain"%>

Message:
<%=exception.getMessage()%>
<%
    if ( exception.getCause()!=null ) {
        out.println( "Caused by: " );
        out.println( exception.getCause().getMessage() ); 
    }
    if ( Config.getDebugging() ) {
        out.println("StackTrace:\n");

	StringWriter stringWriter = new StringWriter();
	PrintWriter printWriter = new PrintWriter(stringWriter);
        exception.printStackTrace(printWriter);
	out.println(stringWriter);
	printWriter.close();
	stringWriter.close();
    }
%>