<%-- 
    Document   : index
    Created on : Mar 29, 2022, 8:56:44 AM
    Author     : jbf
--%>

<%@page import="org.codehaus.jettison.json.JSONObject"%>
<%@page import="org.codehaus.jettison.json.JSONArray"%>
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
        <a href="about">About</a> <i>More about this server, like contact info.</i><br>
        <a href="capabilities">Capabilities</a> <i>Capabilities of the server.</i><br>
        <a href="catalog">Catalog</a> <i>Show the catalog of available data sets.</i><br>
        <a href="info?id=wind_swe_2m">Info</a> <i>Get information about a dataset.</i><br>
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
            
        <%
            try {
            
            JSONArray dss= HapiServerSupport.getCatalog();
            for ( int i=0; i<dss.length(); i++ ) {
                JSONObject ds= dss.getJSONObject(i);
                
                String id= ds.getString("id");
                String title= "";
                if ( ds.has("title") ) {
                    title= ds.getString("title");
                    if ( title.length()==0 ) {
                        title= id;
                    } else {
                        if ( !title.equals(id) ) {
                            title= id + ": "+ title;
                        }
                    }
                } else {
                    title= id;
                }
                
                File infoFile= new File( new File( HAPI_HOME, "info" ), Util.fileSystemSafeName(id)+".json" );
                if ( !infoFile.exists() ) {
                    out.println( "<p style=\"background-color: #e0e0e0;\">misconfigured: "+id+" ("+Util.fileSystemSafeName(id)+")</p>\n" );
                    continue;
                }
                JSONObject info= HapiServerSupport.readJSON( infoFile );
                
                DatumRange availableRange= HapiServerSupport.getRange(info);
                DatumRange exampleRange= HapiServerSupport.getExampleRange(info);
                if ( exampleRange!=null ) {
                    title= title+ "<em> (available "+availableRange + ", example range "+exampleRange + " shown)</em>";
                }

                String exampleTimeRange= exampleRange==null ? null : String.format( "start=%s&stop=%s", exampleRange.min().toString(), exampleRange.max().toString() ); 
                out.println( String.format( "<p style=\"background-color: #e0e0e0;\">%s</p>", title ) );
                if ( exampleRange!=null ) {
                    out.println( String.format("[<a href=\"info?id=%s\">Info</a>] [<a href=\"data?id=%s&%s\">Data</a>]", 
                        ds.getString("id"), ds.getString("id"), exampleTimeRange ) );
                } else {
                    out.println( String.format("[<a href=\"info?id=%s\">Info</a>] [Data]", 
                        ds.getString("id"), ds.getString("id") ) );
                }
                
                String autoplotServer= "https://jfaden.net/AutoplotServlet";
                //String autoplotServer= "http://localhost:8084/AutoplotServlet";
                
                out.println(" ");
                JSONArray parameters= info.getJSONArray("parameters");
                for ( int j=0; j<parameters.length(); j++ ) {
                    if ( j>0 ) out.print("  ");
                    try {
                        String pname= parameters.getJSONObject(j).getString("name");
                        out.print( String.format( "<a href=\"data?id=%s&parameters=%s&%s\">%s</a>", ds.getString("id"), pname, exampleTimeRange, pname ) );
                        if ( j>0 ) { //sparklines
                            //     vap  +hapi  :https      ://jfaden.net  /HapiServerDemo  /hapi  ?id=?parameters=Temperature
                            //?url=vap%2Bhapi%3Ahttps%3A%2F%2Fjfaden.net%2FHapiServerDemo%2Fhapi%3Fid%3DpoolTemperature%26timerange%3D2020-08-06&format=image%2Fpng&width=70&height=20&column=0%2C100%25&row=0%2C100%25&timeRange=2003-mar&renderType=&color=%23000000&symbolSize=&fillColor=%23aaaaff&foregroundColor=%23000000&backgroundColor=none
                            StringBuilder sb= new StringBuilder();
                            sb.append("uri=");
                            StringBuilder ub= new StringBuilder();
                            ub.append("vap+hapi:"+me);
                            ub.append("?");
                            ub.append("id="+id);
                            ub.append("&parameters="+pname);
                            ub.append("&timerange="+exampleRange.toString().replaceAll(" ","+") );
                            sb.append( URLEncoder.encode(ub.toString()) );
                            sb.append("&format=image%2Fpng");
                            sb.append("&width=70");
                            sb.append("&height=16");
                            sb.append("&row=0%25-1px%2C100%25");
                            sb.append("&column=0%25-1px%2C100%25");
                            sb.append("&timerange="+URLEncoder.encode(exampleRange.toString()) );
                            out.print( "<a href='"+autoplotServer+"/thin/zoom/demo.jsp?"+sb.toString()+"' target='top'>");
                            out.print( "<img src='"+autoplotServer+"/SimpleServlet?"+sb.toString()+"'>" );
                            out.print( "</a>");
                            //out.print( "<img src=\"http://localhost:8084/AutoplotServlet/SimpleServlet?"+sb.toString()+"\">" );                        
                        }

                    } catch ( JSONException ex ) {
                        out.print( "???" );
                    }
                }
                
            }
            } catch ( JSONException ex ) {
                out.print("<br><br><b>Something has gone wrong, see logs or send an email to faden at cottagesystems.com</b>");
                out.println("<br>"+ex.getMessage());
                out.println("<br>"+out.toString());
            }
        %>
            
    </body>
</html>
