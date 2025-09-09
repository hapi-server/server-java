
package org.hapiserver;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.Arrays;
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
import org.hapiserver.exceptions.BadRequestParameterException;
import org.hapiserver.exceptions.HapiException;

/**
 * Container for useful routines 
 * @author jbf
 */
public final class Util {
    
    private static final Logger logger= Logger.getLogger("hapi");
    
    private static final Charset CHARSET= Charset.forName("UTF-8");

    /**
     * return true if the client is trusted and additional information about
     * the server for debugging can be included in the response.
     * @param request the request
     * @return true if the client is trusted 
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
     * send the file to the response.  If the client is trusted, then also include HAPI_HOME in x_HAPI_SERVER_HOME
     * to aid in debugging.
     * 
     * @param jsonFile the file containing JSON data.
     * @param request the request 
     * @param response the response
     * @throws IllegalArgumentException when a JSONException occurs.
     * @throws IOException any IOException
     */
    public static void sendFile( File jsonFile, HttpServletRequest request, HttpServletResponse response) 
        throws IllegalArgumentException, IOException {
        
        byte[] bb= Files.readAllBytes( Paths.get( jsonFile.toURI() ) );
        String s= new String( bb, Charset.forName("UTF-8") );
        if ( Util.isTrustedClient(request) ) {
            // security says this should not be shown in production use, but include for localhost
            JSONObject jo;
            try {
                jo = newJSONObject(s);
                String HAPI_HOME = request.getServletContext().getInitParameter("hapi_home");
                jo.put("x_HAPI_SERVER_HOME", HAPI_HOME );
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
     * If the name is modified, it will start with an underscore (_). <ul>
     * <li>poolTemperature &rarr; poolTemperature
     * <li>Iowa City Conditions &rarr; _Iowa+City+Conditions
     * </ul>
     * Note too that forward-slashes are allowed, so special handling of these is required,
     * for example when subdirectories are created.
     * @param s the name of the ID.
     * @return a file-system safe name, not containing spaces, spaces replaced by pluses.  If strange characters remain everything is escaped.
     */
    public static final String fileSystemSafeName( String s ) {
        Pattern p= Pattern.compile("[a-zA-Z0-9\\-\\+\\*\\._\\/]+");
        Matcher m= p.matcher(s);
        if ( m.matches() && !s.contains("..") ) {
            return s;
        } else {
            String s1= s.replaceAll("\\+","2B");
            s1= s1.replaceAll(" ","\\+");
            if ( p.matcher(s1).matches() && !s.contains("..") ) {
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
     * @param info the info the JSONObject for the info response
     * @return an int array with the number of elements in each parameter.
     * @throws JSONException when required tags are missing
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
     * @throws org.hapiserver.exceptions.BadRequestParameterException if a parameter is not supported
     */
    public static JSONObject subsetParams( JSONObject info, String parameters ) throws JSONException, BadRequestParameterException {
        info= copyJSONObject( info );
        String[] pps= parameters.split(",");
        Map<String,Integer> map= new HashMap();  // map from name to index in dataset.
        JSONArray jsonParameters= info.getJSONArray("parameters");
        int[] lens= getNumberOfElements(info);
        for ( int i=0; i<jsonParameters.length(); i++ ) {
            String name= jsonParameters.getJSONObject(i).getString("name");
            map.put( name, i ); 
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
                throw new BadRequestParameterException();
            }
            indexMap[ip]= map.get(pps[ip]);
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
            for ( int k=newParameters.length()-1; k>=0; k-- ) {
                newParameters.put( k+1, newParameters.get(k) );
            }
            newParameters.put(0,jsonParameters.get(0));
        }

        if ( indexMap[indexMap.length-1]==-1 ) {
            throw new IllegalArgumentException("last index of index map wasn't set--server implementation error");
        }

        jsonParameters= newParameters;
        info.put( "parameters", jsonParameters );        
        info.put( "x_indexmap", indexMap );
        
        return info;
    }
    
    /**
     * All HAPI responses have a status node, which is a JSONObject including a static code and message.
     * @param statusCode HAPI status code, such as 1406
     * @param message the message, such as "HAPI error 1406: unknown dataset id"
     * @return the JSON object ready to be completed
     */
    public static JSONObject createHapiResponse( int statusCode, String message ) {
        try {
            JSONObject jo= newJSONObject();
            jo.put("HAPI",Util.hapiVersion());
            jo.put("x_createdAt",String.format("%tFT%<tRZ",Calendar.getInstance(TimeZone.getTimeZone("Z"))));
            JSONObject status= newJSONObject();
            status.put( "code", statusCode );
            status.put( "message", message );
            jo.put("status",status);
            jo.setEscapeForwardSlashAlways(false);
            return jo;
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    private static int httpForHapiStatusCode( int statusCode ) {
        switch ( statusCode ) {
            case 1200:
            case 1201:
                return 200;
            case 1400: 
            case 1401: 
            case 1402:
            case 1403:
            case 1404:
            case 1405:
                return 400;
            case 1406:
                return 404;
            case 1407:
                return 404;
            case 1408:
            case 1409:
            case 1410:
                return 400;                
            case 1500:
            case 1501:
            default:
                return 500;
            
        }
    }
    
    /**
     * log the error so that the webmaster can see it.
     * @param ex the exception
     */
    public static void logError(Exception ex) {
        ex.printStackTrace();
    }
        
    /**
     * send an error response to the client. The document 
     * <a href="https://github.com/hapi-server/data-specification/blob/master/hapi-3.1.0/HAPI-data-access-spec-3.1.0.md#42-status-codes">status codes</a>
     * talks about the status codes.  The error will be logged in the web logs,
     * but not otherwise.  (This is for errors which don't need resolution on the 
     * server side.)  Note the OutputStream is not closed here and must
     * be closed elsewhere.
     * @param statusCode HAPI status code, such as 1406
     * @param statusMessage the message, such as "HAPI error 1406: unknown dataset id"
     * @param response the response object
     * @param out the output stream from the response object.
     * @throws java.io.IOException when there are errors in I/O
     * @see #logError(java.lang.Exception) 
     */
    public static void raiseError( int statusCode, String statusMessage, HttpServletResponse response, final OutputStream out ) 
        throws IOException {
        try {
            JSONObject jo= createHapiResponse(statusCode,statusMessage);
            String s= jo.toString(4);
            int httpStatus= httpForHapiStatusCode(statusCode);
            response.setContentType("application/json;charset=UTF-8");
            if ( statusCode==1201 ) {
                response.setStatus( httpStatus, statusMessage );
                //response.sendError( httpStatus, statusMessage );
                // no data means empty response
            } else {
                if ( statusCode==1406 && statusMessage.equals("HAPI error 1406: unknown dataset id") ) {
                    response.setStatus( httpStatus, "Not Found; HAPI error 1406: unknown dataset id" );
                    //response.setStatus( httpStatus,  "Not Found; HAPI error 1406: unknown dataset id" );
                } else {
                    response.setStatus( httpStatus, statusMessage );
                }
                out.write(s.getBytes(CHARSET));
                
            }
            
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * send an error response to the client. The document 
     * <a href="https://github.com/hapi-server/data-specification/blob/master/hapi-3.0.1/HAPI-data-access-spec-3.0.1.md#42-status-codes">status codes</a>
     * talks about the status codes.
     * @param ex the HapiException
     * @param response the response object
     * @param out the output stream from the response object.
     * @throws java.io.IOException when there are errors in I/O
     */    
    public static void raiseError( HapiException ex, HttpServletResponse response, final OutputStream out ) throws IOException {
        raiseError( ex.getCode(), ex.getMessage(), response, out);
    }
    
    /**
     * properly trim the byte array containing a UTF-8 String to a limit
     * @param bytes the bytes
     * @param k the number of bytes
     * @return the bytes properly trimmed.
     */
    public static byte[] trimUTF8( byte[] bytes, int k ) {
        if ( bytes.length==k ) return bytes;
        int b= bytes[k];
        if ( b>127 ) { // uh-oh, we are mid-UTF8-extended character.
            while ( k>0 && b>127 ) {
                k=k-1;
                b= bytes[k];
            }
        }
        bytes= Arrays.copyOf( bytes, k );
        return bytes;
    }
    
    
    /**
     * return the logger used for the web application
     * @return the logger
     */
    public static Logger getLogger() {
        return logger;
    }

    /**
     * returns true if the id is safe to include in a unix command,
     * not containing spaces or dot-dot (..)
     * @param id the dataset id.
     * @return true if the id is safe to use, or throws IllegalArgumentException if it is not.
     */
    public static boolean constrainedId(String id) {
        if ( id.length()==0 ) {
            throw new IllegalArgumentException("dataset id length is zero");
        } else {
            if ( Pattern.compile("[a-zA-Z0-9.,_.~/:\\+\\-\\@]+").matcher(id).matches() ) {
                if ( id.contains("..") ) {
                    throw new IllegalArgumentException("dataset id cannot contain ..");
                }
                return true;
            } else {
                throw new IllegalArgumentException("id must match [a-zA-Z0-9.,_.~/:\\+\\-\\@]+");
            }
        }
    }
    
    /**
     * create a new JSONObject with the escapeForwardSlashAlways set, to minimize
     * special code for the JSON library used.
     * @return a new JSONObject
     */
    public static JSONObject newJSONObject() {
        JSONObject jo= new JSONObject();
        jo.setEscapeForwardSlashAlways(false);
        return jo;
    }
    
    /**
     * create a copy of the JSONObject which can be modified without affecting 
     * memory caches.
     * @param jo the JSONObject 
     * @return a copy of the JSONObject
     */
    public static JSONObject copyJSONObject( JSONObject jo ) {
        return newJSONObject( jo.toString() );
    }
    
    /**
     * create a copy of the JSONObject which can be modified without affecting 
     * memory caches.
     * @param s the string encoding the JSONObject 
     * @return a copy of the JSONObject
     */
    public static JSONObject newJSONObject( String s ) {
        try {
            JSONObject jo= new JSONObject( s );
            jo.setEscapeForwardSlashAlways(false);
            return jo;
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * return a unique string for the build to aid in debugging.  This is the last commit
     * of this file, only this file.  This should be updated by the revision control system.
     * Note this has not been working.
     * @return the time this file was last modified.
     */
    public static String buildTime() {
        String time= "last_modified_at: 2025-09-09T13:28";
        return time.substring(18);
    }
    
    /**
     * return the HAPI version of the server.
     * @return the HAPI version
     * @see https://github.com/hapi-server/data-specification for a spec for each version.
     */
    public static String hapiVersion() {
        return (String)HapiServerSupport.HAPI_VERSION; 
    }
    
    public static void main(String[] args ) throws ParseException {
        TimeUtil.parseISO8601TimeRange( "2017-07-01T00:00Z/2024-09-14T23:59:56Z" );
    }
}
