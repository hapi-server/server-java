<%-- 
    Document   : index
    Created on : Mar 29, 2022, 8:56:44 AM
    Author     : jbf
--%>

<%@page import="java.io.IOException"%>
<%@page import="org.hapiserver.ExtendedTimeUtil"%>
<%@page import="org.codehaus.jettison.json.JSONException"%>
<%@page import="java.net.URLEncoder"%>
<%@page import="org.hapiserver.TimeUtil"%>
<%@page import="org.hapiserver.HapiServerSupport"%>
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

        <%
            final int MAX_PARAMETERS=10;
            final int MAX_DATASETS=10;
            
            String HAPI_HOME= Initialize.getHapiHome(getServletContext());
                
            Initialize.maybeInitialize( HAPI_HOME );

            JSONObject about= HapiServerSupport.getAbout(HAPI_HOME);

            %>

            <h1><%= about.optString("title","Basic HAPI Server") %></h1>  More information about this type of server is found at <a href="https://github.com/hapi-server/server-java" target="_blank">GitHub</a>.
        This implementation of the HAPI server uses plug-in readers to load data.  Discussion and more about this 
        server can be found <a href="https://github.com/hapi-server/server-java/blob/main/README.md">here</a>.

        <!-- <br>The HAPI server <a href="http://hapi-server.org/verify?url=">verifier</a> will test this HAPI server for correctness. -->

        <h3>Some example requests:</h3>
        <a href="about">About</a> <i>More about this server, like contact info.</i><br>
        <a href="capabilities">Capabilities</a> <i>Capabilities of the server.</i><br>
        <a href="catalog">Catalog</a> <i>Show the catalog of available data sets.</i><br>
        <a href="semantics">Semantics</a> <i>Show declared relationships of data sets.</i><br>
        <br>
                
        <%
            try {
            
                JSONObject catalog= HapiServerSupport.getCatalog(HAPI_HOME);
                
                JSONArray dss= catalog.getJSONArray("catalog");
                
                String pps= dss.length()>1 ? "s" : "";
                out.println("This server provides "+dss.length()+" dataset" + pps + ", examples follow.");
                
                String autoplotServer= "https://jfaden.net/AutoplotServlet";
                //String autoplotServer= "http://localhost:8084/AutoplotServlet";
                    
                String me= "http://spot9/hapi"; // TODO: address this, what is the public name for the server
                boolean sparklines= false;      // don't draw sparklines using external server.
                
                int numDataSets= Math.min( dss.length(), MAX_DATASETS );
                                
                for ( int i=0; i<numDataSets; i++ ) {
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

                    try {
                        JSONObject info= HapiServerSupport.getInfo( HAPI_HOME, id );

                        int[] availableRange= HapiServerSupport.getRange(info);
                        int[] exampleRange= HapiServerSupport.getExampleRange(info);
                        if ( exampleRange!=null ) {
                            title= title+ "<em> (available "+ TimeUtil.formatIso8601TimeRange(availableRange)
                                + ", example range "+TimeUtil.formatIso8601TimeRange(exampleRange) + 
                                ( sparklines ? " shown)</em>" : ")</em>" );
                        }

                        String exampleTimeRange= exampleRange==null ? null : 
                            String.format( "start=%s&stop=%s", 
                                TimeUtil.formatIso8601TimeBrief(exampleRange), 
                                TimeUtil.formatIso8601TimeBrief(exampleRange,TimeUtil.TIME_DIGITS) ); 
                        out.println( String.format( "<p style=\"background-color: #e0e0e0;\">%s</p>", title ) );
                        if ( exampleRange!=null ) {
                            out.println( String.format("[<a href=\"info?id=%s\">Info</a>] [<a href=\"data?id=%s&%s\">Data</a>]", 
                                ds.getString("id"), ds.getString("id"), exampleTimeRange ) );
                        } else {
                            out.println( String.format("[<a href=\"info?id=%s\">Info</a>] [Data]", 
                                ds.getString("id"), ds.getString("id") ) );
                        }

                        out.println(" ");
                        JSONArray parameters= info.getJSONArray("parameters");
                        String[] labels= new String[parameters.length()];
                        for ( int ii=0; ii<labels.length; ii++ ) {
                            labels[ii]= parameters.getJSONObject(ii).getString("name");
                        }
                        if ( labels.length>1 ) {
                            String common= HapiServerSupport.findCommon(labels[0], labels[1]);
                            if ( common.length()>3 ) {
                                common= HapiServerSupport.findCommon(labels[0], labels[1]);
                                labels= HapiServerSupport.maybeShortenLabels(common,labels);
                            }
                        }
                        for ( int j=0; j<Math.min(MAX_PARAMETERS,parameters.length()); j++ ) {
                            if ( j>0 ) out.print("  ");
                            try {
                                String pname= parameters.getJSONObject(j).getString("name");
                                out.print( String.format( "<a href=\"data?id=%s&parameters=%s&%s\">%s</a>", ds.getString("id"), pname, exampleTimeRange, labels[j] ) );
                                if ( j>0 && sparklines ) { //sparklines
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
                        if ( parameters.length()>MAX_PARAMETERS ) {
                            out.print("...");
                        }
                    } catch ( JSONException | IOException | RuntimeException ex ) {
                        out.println( String.format( "<p style=\"background-color: #e0e0e0;\">%s</p>", title ) );
                        out.println( "<p>Unable to load info for dataset: <a href=\"info?id="+id+"\">"+id+"</a>, log files should notify the server host.<br></p>" ) ;
                        Util.logError(ex);
                        //out.println( "ex: " + ex ); //TODO: security!!!
                    }
                }
            } catch ( JSONException ex ) {
                out.print("<br><br><b>Something has gone wrong, see logs or send an email to faden at cottagesystems.com</b>");
                //out.println("<br>"+ex.getMessage()); //TODO: security
                Util.logError(ex);
            }
            
            out.println("<br><br><br><small>build id: "+Util.buildTime()+"</small>");
            
        %>
            
    </body>
</html>
