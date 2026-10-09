<%@page import="org.hapiserver.SourceRegistry"%>
<%@page import="java.net.URLClassLoader"%>
<%@page import="java.net.URL"%>
<%@page import="org.hapiserver.source.SpawnRecordSource"%>
<%@page import="java.lang.reflect.Method"%>
<%@page import="java.util.List"%>
<%@page import="java.util.ArrayList"%>
<%@page import="java.util.ArrayList"%>
<%@page import="java.util.logging.Logger"%>
<%@page import="java.util.regex.PatternSyntaxException"%>
<%@page import="java.util.regex.Pattern"%>
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
<%
        String HAPI_HOME= Initialize.getHapiHome(getServletContext());           
        
        String uri = request.getRequestURI();

        boolean withinHapi= uri.endsWith("/hapi/"); // if true, then references must be relative
        String h="hapi/";
        if ( withinHapi ) h="";
        
        JSONObject landingConfig= HapiServerSupport.getLandingConfig(HAPI_HOME);
        JSONObject about= HapiServerSupport.getAbout(HAPI_HOME);
%>
<html>
    <head>
        <title><%= Util.escapeHtml(about.optString("title","Basic HAPI Server")) %></title>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
<%
            if ( landingConfig.has("style") ) {
                String styleUrl= landingConfig.getString("style");
                out.println("<link rel=\"stylesheet\" href=\""+styleUrl+"\">");
            }
%>
    </head>
    <body>

<%
            final int MAX_PARAMETERS=10;
            final int MAX_DATASETS=10;
            
            Initialize.maybeInitialize( HAPI_HOME );
            
            Logger logger= Util.getLogger();
            
%>

            <h1><%= Util.escapeHtml(about.optString("title","Basic HAPI Server")) %></h1>  
<%
                String defaultDescription="More information about this type of server is found at "
                    + "<a href=\"https://github.com/hapi-server/server-java\" target=\"_blank\">GitHub</a>."
        + " This implementation of the HAPI server uses plug-in readers to load data.  Discussion and more about this "
        + " server can be found <a href=\"https://github.com/hapi-server/server-java/blob/main/README.md\">here</a>.";
%>
        <%= Util.escapeHtml(about.optString("description",defaultDescription)) %> 

        <!-- <br>The HAPI server <a href="http://hapi-server.org/verify?url=">verifier</a> will test this HAPI server for correctness. -->

        <h3>Some example requests:</h3>
        <a href="<%=h%>about">About</a> <i>More about this server, like contact info.</i><br>
        <a href="<%=h%>capabilities">Capabilities</a> <i>Capabilities of the server.</i><br>
        <a href="<%=h%>catalog">Catalog</a> <i>Show the catalog of available data sets.</i><br>
        
        <br>
                
<%
            try {
            
                JSONObject catalog= HapiServerSupport.getCatalog(HAPI_HOME);
                
                JSONArray dss= catalog.getJSONArray("catalog");
                
                if ( dss.length()>1 ) {
                    out.println("This server provides "+dss.length()+" datasets, examples follow.");
                }
                    
                int numDataSets= Math.min( dss.length(), landingConfig.optInt( "x-landing-count", MAX_DATASETS ) );
                
                Pattern[] incl;
                if ( landingConfig.has("x-landing-include") ) {
                    JSONArray inclRegex= landingConfig.getJSONArray("x-landing-include");
                    incl= new Pattern[inclRegex.length()];
                    for ( int i=0; i<incl.length; i++ ) {
                        try {
                            incl[i]= Pattern.compile(inclRegex.getString(i));
                        } catch ( PatternSyntaxException ex ) {
                            logger.warning("bad pattern in landing: "+inclRegex.getString(i));
                            incl[i]= null;
                        }
                    }
                } else {
                    incl= null;
                }
                
                Pattern[] excl;
                if ( landingConfig.has("x-landing-exclude") ) {
                    JSONArray exclRegex= landingConfig.getJSONArray("x-landing-exclude");
                    excl= new Pattern[exclRegex.length()];
                    for ( int i=0; i<excl.length; i++ ) {
                        try {
                            excl[i]= Pattern.compile(exclRegex.getString(i));
                        } catch ( PatternSyntaxException ex ) {
                            logger.warning("bad pattern in landing: "+exclRegex.getString(i));
                            excl[i]= null;
                        }
                    }
                } else {
                    excl= null;
                }
                
                List<String> ids= new ArrayList<>();
                List<String> titles= new ArrayList<>();
                
                for ( int i=0; i<dss.length(); i++ ) {
                    if ( ids.size()==numDataSets ) break;
                    
                    JSONObject ds= dss.getJSONObject(i);
                    String id= ds.getString("id");
                    if ( excl!=null ) {
                        boolean doExclude=false;
                        for ( Pattern p: excl ) {
                            if ( p==null ) continue; 
                            if ( p.matcher(id).matches() ) {
                                doExclude=true;
                                break;
                            }
                        }
                        if ( doExclude ) continue;
                    }                                      
                    if ( incl!=null ) {
                        boolean doInclude=false;
                        for ( Pattern p: incl ) {
                            if ( p==null ) continue; 
                            if ( p.matcher(id).matches() ) {
                                ids.add(id);
                                titles.add( ds.optString( "title", "" ) );
                            }
                        }
                        if ( !doInclude ) continue;
                    }
                }
                if ( ids.size()<numDataSets ) {
                    for ( int i=0; i<dss.length(); i++ ) {
                        JSONObject ds= dss.getJSONObject(i);
                        String id= ds.getString("id");
                        if ( ids.size()==numDataSets ) break;
                        if ( excl!=null ) {
                            boolean doExclude=false;
                            for ( Pattern p: excl ) {
                                if ( p==null ) continue; 
                                if ( p.matcher(id).matches() ) {
                                    doExclude=true;
                                    break;
                                }
                            }
                            if ( doExclude ) continue;
                        }  
                        ids.add(id);
                        titles.add( ds.optString( "title", "" ) );
                    }
                }
                        
                for ( int i=0; i<ids.size(); i++ ) {

                    String id= ids.get(i);
                    
                    String title= titles.get(i);
                    if ( title.length()>0 ) {
                        if ( !title.equals(id) ) {
                            title= id + ": "+ title;
                        }
                    } else {
                        title= id;
                    }
                    
                    title= Util.escapeHtml(title);

                    try {
                        
                        JSONObject info= HapiServerSupport.getInfo( HAPI_HOME, id );

                        int[] availableRange= HapiServerSupport.getRange(info);
                        int[] exampleRange= HapiServerSupport.getExampleRange(info);
                        if ( exampleRange!=null ) {
                            title= title+ "<em> (available "+ TimeUtil.formatIso8601TimeRange(availableRange)
                                + ", example range "+TimeUtil.formatIso8601TimeRange(exampleRange) + 
                                ")</em>";
                        }

                        String exampleTimeRange= exampleRange==null ? null : 
                            String.format( "start=%s&stop=%s", 
                                TimeUtil.formatIso8601TimeBrief(exampleRange), 
                                TimeUtil.formatIso8601TimeBrief( TimeUtil.getStopTime(exampleRange) ) ); 
                        out.println( String.format( "<p style=\"background-color: #e0e0e0;\">%s</p>", title ) );
                        String url= h + "info?dataset="+ Util.urlEncode(id);
                        if ( exampleRange!=null ) {
                            String dataUrl= h + String.format( "data?dataset=%s&%s", Util.urlEncode(id), exampleTimeRange );
                            out.println( "[<a href=\""+Util.escapeHtml(url) +"\">Info</a>] [<a href=\""+Util.escapeHtml(dataUrl)+"\">Data</a>]" );
                        } else {
                            out.println( "[<a href=\""+Util.escapeHtml(url) +"\">Info</a>] [Data]" );
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
                        int nparm= Math.min(MAX_PARAMETERS,parameters.length());
                        for ( int j=0; j<nparm; j++ ) {
                            if ( j>0 ) out.print("  ");
                            try {
                                String parameter= Util.urlEncode(parameters.getJSONObject(j).getString("name"));
                                String dataUrl= h + String.format( "data?dataset=%s&parameters=%s&%s", Util.urlEncode(id), parameter, exampleTimeRange );
                                out.print( "<a href=\""+Util.escapeHtml(dataUrl) +"\">"+Util.escapeHtml(labels[j])+"</a>" );

                            } catch ( JSONException ex ) {
                                out.print( "???" );
                            }
                        }
                        if ( parameters.length()>MAX_PARAMETERS ) {
                            out.print("...");
                        }
                    } catch ( Exception ex ) {
                        out.println( String.format( "<p style=\"background-color: #e0e0e0;\">%s</p>", Util.escapeHtml(title) ) );
                        out.println( "<p>Unable to load info for dataset: <a href=\""+Util.escapeHtml(h)+"info?dataset="+id+"\">"+Util.escapeHtml(id)+"</a>, log files should notify the server host.<br></p>" ) ;
                        Util.logError(ex);
                        //out.println( "ex: " ;+ ex ); //TODO: security!!!
                    }
                }
                if ( numDataSets<dss.length() ) {
                    String pps= (dss.length()-numDataSets)>1 ? "s" : "";
                    out.println("<br><p>("+(dss.length()-numDataSets)+" additional dataset" + pps +" can be accessed using a HAPI client.)</p>" );
                }
                
            } catch ( JSONException ex ) {
                out.print("<br><br><b>Something has gone wrong, see logs or send an email to faden at cottagesystems.com</b>");
                //out.println("<br>"+ex.getMessage()); //TODO: security
                Util.logError(ex);
            }
            
            out.println("<br><br><br><small>build id: "+Util.escapeHtml(Util.buildTime())+"</small>");
            JSONObject footer= (JSONObject)landingConfig.opt("x_footer");
            if ( footer!=null ) {
                String s= footer.optString( "classpath", footer.optString("x_classpath","") );
                String clas= footer.getString("x_class");
                String method= footer.getString("x_method");
                if ( clas!=null && method!=null ) {
                    s= SpawnRecordSource.doMacros( HAPI_HOME, "", s );
                    ClassLoader cl= new URLClassLoader( new URL[] { new URL( s ) }, SourceRegistry.class.getClassLoader() );
                    cl.getParent();
                    Class c= Class.forName(clas,true,cl);
                    Method m = c.getMethod( method );
                    String sfooter= (String)m.invoke(null);
                    out.println("<small>"+Util.escapeHtml(sfooter)+"</small>");
                } 
            }
            
            
%>
    </body> 
</html>
