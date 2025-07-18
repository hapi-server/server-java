
package org.hapiserver.source.cdaweb;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.TimeUtil;
import org.hapiserver.source.SourceUtil;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
        
/**
 * Returns catalog response based on all.xml, and info responses from
 * either Bob's process, Nand's existing server, or a future implementation (and this
 * documentation needs to be updated).
 * @author jbf
 * @see https://cdaweb.gsfc.nasa.gov/pub/catalogs/all.xml
 */
public class CdawebInfoCatalogSource {
    
    private static final Logger logger= Logger.getLogger("hapi.cdaweb");
    
    public static final String CDAWeb = "https://cdaweb.gsfc.nasa.gov/";

    private static final String CDAWEB_HAPI_VERSION = "20250614.0743";
    
    protected static Map<String,String> coverage= new HashMap<>();
    protected static Map<String,String> filenaming= new HashMap<>();

    /**
     * read the node and note the filenaming and form a template
     * @param id
     * @param dataset
     * @param jo the JSON object for the catalog
     * @return the URL found.
     */
    private static String getURL( String id, Node dataset, JSONObject jo ) throws JSONException {
        
        Node childNode= getNodeByName( dataset, "access" );
        
        String lookfor= "ftp://cdaweb.gsfc.nasa.gov/pub/istp/";
        String lookfor2= "ftp://cdaweb.gsfc.nasa.gov/pub/cdaweb_data";

        Node urlNode= getNodeByName( childNode, "URL" );
        
        if ( urlNode.getFirstChild()==null ) {
            logger.log(Level.FINE, "URL is missing for {0}, data cannot be accessed.", id);
            return null;
        }

        String url= urlNode.getFirstChild().getTextContent().trim();
        if ( url.startsWith( lookfor ) ) {
            // "ftp://cdaweb.gsfc.nasa.gov/pub/istp/ace/mfi_h2"
            //  http://cdaweb.gsfc.nasa.gov/istp_public/data/
            url= CDAWeb + "sp_phys/data/" + url.substring(lookfor.length());
        }
        if ( url.startsWith(lookfor2) ) {
            url= CDAWeb + "sp_phys/data/" + url.substring(lookfor2.length());
        }
        String templ= url + "/";
        String subdividedby= childNode.getAttributes().getNamedItem("subdividedby").getTextContent();
        String filenaming= childNode.getAttributes().getNamedItem("filenaming").getTextContent();

        if ( !subdividedby.equals("None") ) {
            templ= templ + subdividedby + "/";
        }
        templ= templ + filenaming;

        CdawebInfoCatalogSource.filenaming.put(id,templ);

        jo.put( "x_sourceUrl", url );
        if ( !subdividedby.equals("None") ) {
            jo.put( "x_subdividedby", subdividedby );
        }
        jo.put( "x_filenaming", filenaming );

        return url;

    }

    private static HashSet<String> skips;
    private static HashSet<Pattern> skipsPatterns;
    
    private static void readSkips() throws IOException {
        logger.info("reading skips");
        skips= new HashSet<>();
        skipsPatterns= new HashSet<>();
        URL skipsFile= CdawebAvailabilityHapiRecordSource.class.getResource("skips.txt");
        
        try (BufferedReader r = new BufferedReader(new InputStreamReader( skipsFile.openStream() ))) {
            String line = r.readLine();
            while ( line!=null ) {  
                int i=line.indexOf("#");
                if ( i>-1 ) line= line.substring(0,i).trim();
                String[] ss= line.split(",",-2);
                if ( line.length()>0 && ss.length==1 ) {
                    if ( ss[0].contains(".") || ss[0].contains("[") ) {
                        skipsPatterns.add( Pattern.compile(ss[0]) );
                    } else {
                        skips.add(ss[0].trim());
                    }
                }
                line = r.readLine();
            }
        }
    }
    
    private static Node getNodeByName( Node parent, String childName ) {
        NodeList nn = parent.getChildNodes();
        for ( int i=0; i<nn.getLength(); i++ ) {
            Node n= nn.item(i);
            if ( n.getNodeName().equals(childName) ) {
                return n;
            }
        }
        throw new IllegalArgumentException("unable to find child node named: "+childName);
    }
    
    
    /**
     * return the catalog response by parsing all.xml.
     * @param location the URL of the catalog response.
     * @return
     * @throws IOException 
     */
    public static String getCatalog( String location ) throws IOException {
        readSkips();
        try {
            URL url= new URL(location);
            String content= SourceUtil.getAllFileLines(url);
            JSONArray readCatalog= new JSONArray(content);
            
            JSONArray catalog= new JSONArray();
            for ( int i=0; i<readCatalog.length(); i++ ) {
                //String n= readCatalog.getJSONObject(i).getString("id");
                //if ( !( n.startsWith("AMPTE") || n.startsWith("AC_OR_SSC") ) ) {
                //    continue;
                //}
                String id= readCatalog.getJSONObject(i).getString("id");
                for ( Pattern p: skipsPatterns ) {
                    if ( p.matcher(id).matches() ) {
                        continue;
                    }
                }
                catalog.put( catalog.length(), readCatalog.get(i) );
                //TODO: filenaming
            }

            JSONObject result= new JSONObject();
            result.put( "catalog", catalog );
            return result.toString(4);
            
        } catch (MalformedURLException | JSONException ex) {
            throw new RuntimeException(ex);
        }
        
    }
    
    /**
     * return the info response generated by combining several sources.  These info
     * responses are stored (presently) at https://cottagesystems.com/~jbf/hapi/p/cdaweb/data/.
     * This uses the undocumented x_info_cache_timeout_sec feature since this is simply
     * reading a file.
     * @param urlorig (ignored) the orig files.
     * @param surl the url which will provide the info
     * @return the info response. 
     * @throws MalformedURLException
     * @throws IOException 
     */
    public static String getInfo( String urlorig, String surl ) throws MalformedURLException, IOException {
        
        JSONObject jo;
        
        //String srcorig= SourceUtil.getAllFileLines( new URL( urlorig) );
        //try {
        //    jo= new JSONObject(srcorig);
        //} catch ( JSONException ex ) {
        //    throw new IllegalArgumentException("input file has JSON syntax issue: " +surl );
        //}
        try {

//            JSONArray data= jo.getJSONObject("data").getJSONArray("FileDescription");
//            String lastModified= "0000-00-00T00:00:00.000Z";
//        
//            for ( int i=0; i<data.length(); i++ ) {
//                String lm= data.getJSONObject(i).getString("LastModified");
//                if ( lm.compareTo(lastModified)>0 ) lastModified=lm;
//            }

            URL url= new URL(surl);
            String src= SourceUtil.getAllFileLines( url );
            
            try {
                jo= new JSONObject(src);
            } catch ( JSONException ex ) {
                throw new IllegalArgumentException("input file has JSON syntax issue: " +surl );
            }

            if ( jo.has("info") ) {
                jo= jo.getJSONObject("info");
            }
            jo.put("x_info_author", "bw");
            jo.put("x_cdaweb_hapi_version", CDAWEB_HAPI_VERSION);
            JSONArray ja= jo.getJSONArray("parameters");
            for ( int ip= 0; ip<ja.length(); ip++ ) { 
                JSONObject p= ja.getJSONObject(ip);
                String sfill= p.getString("fill");
                String type= p.getString("type");
                if ( sfill!=null && type.equals("double") ) {
                    sfill= String.valueOf(Double.parseDouble(sfill));
                    p.put("fill",sfill);
                    ja.put(ip,p);
                }
            }
            String sampleStartDate= jo.optString("sampleStartDate","");
            String sampleStopDate=  jo.optString("sampleStopDate","");
            if ( sampleStopDate.length()>0 && sampleStartDate.equals(sampleStopDate) ) { //C3_PP_CIS has start and stop times equal for each granule.
                logger.info("kludge for sampleStartDate.equals(sampleStopDate).");
                jo.put("sampleStopDate",jo.getString("stopDate") );
            }
            
            if ( url.getProtocol().equals("file") ) {
                long lastModified= new File( url.getFile() ).lastModified();
                jo.put( "x_info_modificationDate", TimeUtil.fromMillisecondsSince1970(lastModified) );
            }
            
            // communicate to the server that it should not use cached info responses.
            jo.put( "x_info_caching", false );
                    
            //if ( !lastModified.startsWith("00") ) {
            //    jo.put("lastModified",lastModified);
            //}
            return jo.toString(4);
        } catch ( JSONException ex ) {
            throw new IllegalArgumentException("input file has JSON schema issue (something required was missing, etc): " +surl );
        }
        
    }
    
    public static void main( String[] args ) throws IOException {
        args= new String[] { "AC_AT_DEF" };
        //args= new String[0];
        
        if ( args.length==0 ) {
            System.out.println( CdawebInfoCatalogSource.getCatalog("http://mag.gmu.edu/git-data/cdawmeta/data/hapi/catalog.json") );
        } else if ( args.length==1 ) {
            System.out.println(
                    CdawebInfoCatalogSource.getInfo("https://cottagesystems.com/~jbf/hapi/p/cdaweb/data/orig_data/info/A1_K0_MPA.json", 
                            "https://cottagesystems.com/~jbf/hapi/p/cdaweb/data/hapi/info/A1_K0_MPA.json" ) );
        }
    }
    
}
