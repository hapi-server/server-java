
package org.hapiserver;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 * Container for useful routines 
 * @author jbf
 */
public class Util {
    
    private static final Logger logger= Logger.getLogger("hapi");
    
    /**
     * return true if the client is trusted and additional information about
     * the server for debugging can be included in the response.
     * @param request
     * @return 
     */
    public static final boolean isTrustedClient( HttpServletRequest request ) {
        String remoteAddr= request.getRemoteAddr();
        if ( remoteAddr.equals("127.0.0.1" ) || remoteAddr.equals("0:0:0:0:0:0:0:1") ) {
            Enumeration<String> hh= request.getHeaders("X-Forwarded-For");
            if ( hh.hasMoreElements() ) {
                remoteAddr = hh.nextElement();
            }
        }            
        return remoteAddr.equals("127.0.0.1" ) || remoteAddr.equals("0:0:0:0:0:0:0:1");
    }
    
    /**
     * send the file to the response.
     * @param aboutFile
     * @param request
     * @param response
     * @throws IllegalArgumentException
     * @throws IOException 
     */
    public static void sendFile( File aboutFile, HttpServletRequest request, HttpServletResponse response) throws IllegalArgumentException, IOException {
        byte[] bb= Files.readAllBytes( Paths.get( aboutFile.toURI() ) );
        String s= new String( bb, Charset.forName("UTF-8") );
        if ( Util.isTrustedClient(request) ) {
            // security says this should not be shown in production use, but include for localhost
            JSONObject jo;
            try {
                jo = new JSONObject(s);
                String HAPI_HOME = request.getServletContext().getInitParameter("hapi_home");
                jo.put("x_HAPI_SERVER_HOME", HAPI_HOME );
                jo.setEscapeForwardSlashAlways(false);
                s= jo.toString(4);
            } catch (JSONException ex) {
                throw new IllegalArgumentException(ex);
            }
        }
        // isTrustedClient used to verify the JSON structure
        Util.transfer( new ByteArrayInputStream(s.getBytes("UTF-8")), response.getOutputStream(), true );
    }

    /**
     * transfer the contents of the input stream to the output stream
     * @param source the inputStream
     * @param target the outputStream
     * @param close if true, then close the two streams
     * @throws IOException 
     */
    public static void transfer(InputStream source, OutputStream target, boolean close ) throws IOException {
        byte[] buf = new byte[65536];
        int length;
        while ((length = source.read(buf)) > 0) {
            target.write(buf, 0, length);
        }
        if ( close ) {
            try {
                target.close();
            } catch ( IOException ex ) {
                logger.log( Level.WARNING, ex.getMessage(), ex );
            }
            source.close();
        }
    }

    /**
     * return the input stream for the template, throwing a useful error when the template does not exist.
     * @param jsonTemplate, like "about.json" or "capabilities.json"
     * @return the inputStream
     * @throws NullPointerException when a template is not found.
     */
    public static InputStream getTemplateAsStream(String jsonTemplate) {
        InputStream in= Util.class.getResourceAsStream("/templates/"+jsonTemplate);
        if ( in==null ) throw new NullPointerException("/templates/"+jsonTemplate+" not found");
        return in;
    }

    /**
     * convert IDs and NAMEs into safe names which will work on all platforms.
     * If the name is modified, it will start with an underscore (_). 
     * <li>poolTemperature -> poolTemperature
     * <li>Iowa City Conditions -> _Iowa+City+Conditions
     * @param s
     * @return 
     */
    public static final String fileSystemSafeName( String s ) {
        Pattern p= Pattern.compile("[a-zA-Z0-9\\-\\+\\*\\._]+");
        Matcher m= p.matcher(s);
        if ( m.matches() ) {
            return s;
        } else {
            String s1= s.replaceAll("\\+","2B");
            s1= s1.replaceAll(" ","\\+");
            if ( p.matcher(s1).matches() ) {
                return "_" + s1;
            } else {
                byte[] bb= s.getBytes( Charset.forName("UTF-8") );
                StringBuilder sb= new StringBuilder("_");
                for ( byte b: bb ) {
                    sb.append( String.format("%02X", b) );
                }
                return sb.toString();
            }
        }
    }
    
    /**
     * return the total number of elements of each parameter.
     * @param info the info
     * @return an int array with the number of elements in each parameter.
     * @throws JSONException 
     */
    public static int[] getNumberOfElements( JSONObject info ) throws JSONException {
        JSONArray parameters= info.getJSONArray("parameters");
        int[] result= new int[parameters.length()];
        for ( int i=0; i<parameters.length(); i++ ) {
            int len=1;
            if ( parameters.getJSONObject(i).has("size") ) {
                JSONArray jarray1= parameters.getJSONObject(i).getJSONArray("size");
                for ( int k=0; k<jarray1.length(); k++ ) {
                    len*= jarray1.getInt(k);
                }
            }
            result[i]= len;
        }    
        return result;
    }
    
    /**
     * return a new JSONObject for the info request, with the subset of parameters.
     * @param info the root node of the info response.
     * @param parameters comma-delimited list of parameters.
     * @return the new JSONObject, with special tag __indexmap__ showing which columns are to be included in a data response.
     * @throws JSONException 
     */
    public static JSONObject subsetParams( JSONObject info, String parameters ) throws JSONException {
        info= new JSONObject( info.toString() ); // force a copy
        String[] pps= parameters.split(",");
        Map<String,Integer> map= new HashMap();  // map from name to index in dataset.
        Map<String,Integer> iMap= new HashMap(); // map from name to position in csv.
        JSONArray jsonParameters= info.getJSONArray("parameters");
        int index=0;
        int[] lens= getNumberOfElements(info);
        for ( int i=0; i<jsonParameters.length(); i++ ) {
            String name= jsonParameters.getJSONObject(i).getString("name");
            map.put( name, i ); 
            iMap.put( name, index );
            index+= lens[i];
        }
        JSONArray newParameters= new JSONArray();
        int[] indexMap= new int[pps.length];
        for ( int i=0; i<pps.length; i++ ) {
            indexMap[i]=-1;
        }
        int[] lengths= new int[pps.length]; //lengths for the new infos
        boolean hasTime= false;
        for ( int ip=0; ip<pps.length; ip++ ) {
            Integer i= map.get(pps[ip]);
            if ( i==null ) {
                throw new IllegalArgumentException("bad parameter: "+pps[ip]);
            }
            indexMap[ip]= iMap.get(pps[ip]);
            if ( i==0 ) {
                hasTime= true;
            }
            newParameters.put( ip, jsonParameters.get(i) );
            lengths[ip]= lens[i];
        }

        // add time if it was missing.  This demonstrates a feature that is burdensome to implementors, I believe.
        if ( !hasTime ) {
            int[] indexMap1= new int[1+indexMap.length];
            int[] lengths1= new int[1+lengths.length];
            indexMap1[0]= 0;
            System.arraycopy( indexMap, 0, indexMap1, 1, indexMap.length );
            lengths1[0]= 1;
            System.arraycopy( lengths, 0, lengths1, 1, indexMap.length );
            indexMap= indexMap1;
            lengths= lengths1;
            for ( int k=newParameters.length()-1; k>=0; k-- ) {
                newParameters.put( k+1, newParameters.get(k) );
            }
            newParameters.put(0,jsonParameters.get(0));
        }

        // unpack the resort where the lengths are greater than 1.
//        int sum=0;
//        for ( int i=0; i<lengths.length; i++ ) sum+= lengths[i];
//        int[] indexMap1= new int[ sum ];
//        int c= 0;
//        if ( indexMap1.length>indexMap.length ) {
//            for ( int k=0; k<lengths.length; k++ ) {
//                if ( lengths[k]==1 ) {
//                    indexMap1[c]= indexMap[k];
//                    c++;
//                } else {
//                    for ( int l=0; l<lengths[k]; l++ ) { 
//                        indexMap1[c]= indexMap[k]+l;
//                        c++;
//                    }
//                }
//            }
//        }
        if ( indexMap[indexMap.length-1]==-1 ) {
            throw new IllegalArgumentException("last index of index map wasn't set--server implementation error");
        }

        jsonParameters= newParameters;
        jsonParameters.setEscapeForwardSlashAlways(false);
        info.put( "parameters", jsonParameters );        

        info.put( "x_indexmap", indexMap );
        info.setEscapeForwardSlashAlways(false);
        return info;
    }
    
    /**
     * All HAPI responses have 
     * @param statusCode
     * @param message
     * @return the JSON object ready to be completed
     */
    public static JSONObject createHapiResponse( int statusCode, String message ) {
        try {
            JSONObject jo= new JSONObject();
            jo.put("HAPI","3.0");
            jo.put("createdAt",String.format("%tFT%<tRZ",Calendar.getInstance(TimeZone.getTimeZone("Z"))));
            JSONObject status= new JSONObject();
            status.put( "code", statusCode );
            status.put( "message", message );
            jo.put("status",status);
            jo.setEscapeForwardSlashAlways(false);
            return jo;
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }
    
/**
     * send an error response to the client.
     * @param statusCode
     * @param statusMessage
     * @param response the response object
     * @param out the print writer for the response object.
     */
    public static void raiseError( int statusCode, String statusMessage, HttpServletResponse response, final PrintWriter out ) {
        try {
            JSONObject jo= createHapiResponse(statusCode,statusMessage);
            String s= jo.toString(4);
            response.setStatus(404);
            response.setContentType("application/json;charset=UTF-8");
            out.write(s);
            
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * return the logger used for the web application
     * @return 
     */
    public static Logger getLogger() {
        return logger;
    }
}
