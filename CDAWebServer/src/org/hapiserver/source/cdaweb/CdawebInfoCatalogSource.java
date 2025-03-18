
package org.hapiserver.source.cdaweb;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.TimeUtil;
import org.hapiserver.source.SourceUtil;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
        
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

    private static final String CDAWEB_HAPI_VERSION = "20250310.1404";
    
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
        
        
         
//        URL skipsFile= new URL( "https://raw.githubusercontent.com/rweigel/cdawmeta/refs/heads/main/cdawmeta/config/hapi.json" );
//        
//        try (BufferedReader r = new BufferedReader(new InputStreamReader( skipsFile.openStream() ))) {
//            String s = r.readLine();
//            while ( s!=null ) {  
//                int i=s.indexOf("#");
//                if ( i>-1 ) s= s.substring(0,i).trim();
//                String[] ss= s.split(",",-2);
//                if ( ss.length==2 ) {
//                    if ( ss[0].contains(".") ) {
//                        skipsPatterns.add( Pattern.compile(ss[0]) );
//                    } else {
//                        skips.add(ss[0].trim());
//                    }
//                }
//                s = r.readLine();
//            }
//        }
    }
    
    /**
     * read all available cached infos and form a catalog.
     * @return
     * @throws IOException 
     */
    public static String getCatalog20230629() throws IOException {
        File cache= new File("/home/jbf/ct/autoplot/project/cdf/2023/bobw/nl/");
        File[] ff= cache.listFiles((File dir, String name) -> name.endsWith(".json"));

        ArrayList<File> catalog= new ArrayList<>( Arrays.asList(ff) );
        Collections.sort(catalog, (File o1, File o2) -> o1.getName().compareTo(o2.getName()));
        
        JSONObject result= new JSONObject();
        JSONArray jscat= new JSONArray();

        try {
            for ( File f : catalog ) {
                JSONObject item= new JSONObject();
                String n= f.getName();
                item.put("id",n.substring(0,n.length()-5));
                jscat.put( jscat.length(), item);            
            }
            result.put( "catalog", jscat );
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
        
        return result.toString();
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
     * I need the file naming to be kept, so I can know how to granularize the data request.
     * @param location
     * @return
     * @throws IOException 
     */
    public static String getCatalogOld( String location )  throws IOException {
        readSkips();
        try {
            URL url= new URL("https://cdaweb.gsfc.nasa.gov/pub/catalogs/all.xml");
            Document doc= SourceUtil.readDocument(url);
            XPathFactory factory = XPathFactory.newInstance();
            XPath xpath = (XPath) factory.newXPath();
            NodeList nodes = (NodeList) xpath.evaluate( "//sites/datasite/dataset", doc, XPathConstants.NODESET );

            int ic= 0;
            JSONArray catalog= new JSONArray();
            for ( int i=0; i<nodes.getLength(); i++ ) {
                Node node= nodes.item(i);
                NamedNodeMap attrs= node.getAttributes();
                String name= attrs.getNamedItem("serviceprovider_ID").getTextContent();
                if ( name.startsWith("APOLLO") ) {
                    System.err.println("here stop");
                }
                String st= attrs.getNamedItem("timerange_start").getTextContent();
                String en= attrs.getNamedItem("timerange_stop").getTextContent();
                if ( st.length()>1 && Character.isDigit(st.charAt(0))
                        && en.length()>1 && Character.isDigit(en.charAt(0)) ) {
                    
                    if ( name.contains(" ") ) {
                        logger.log(Level.FINE, "skipping because space in name: {0}", name); //TODO: trailing spaces can probably be handled.
                        continue;
                    }

                    if ( skips.contains(name) ) {
                        logger.log(Level.FINE, "skipping {0}", name);
                        continue;
                    }
                    boolean doSkip= false;
                    for ( Pattern p: skipsPatterns ) {
                        if ( p.matcher(name).matches() ) {
                            doSkip= true;
                            logger.log(Level.FINE, "skipping {0} because of match", name);
                        }
                    }
                    if ( doSkip ) {
                        continue;
                    }

                    JSONObject jo= new JSONObject();
                    jo.setEscapeForwardSlashAlways(false);
                    
                    jo.put( "id", name );
                    
                    String sourceurl;
                    try {
                        sourceurl = getURL(name,node,jo);
                    } catch ( Exception ex ) {
                        continue;
                    }
                    if ( sourceurl!=null && 
                            ( sourceurl.startsWith( CDAWeb ) ||
                            sourceurl.startsWith("ftp://cdaweb.gsfc.nasa.gov" ) ) && !sourceurl.startsWith("/tower3/private" ) ) {
                        jo.put( "x_sourceUrl", sourceurl );
                                                
                        try {
                            st = TimeUtil.formatIso8601TimeBrief( TimeUtil.parseISO8601Time(st) );
                            en = TimeUtil.formatIso8601TimeBrief( TimeUtil.parseISO8601Time(en) );
                            String range= st+"/"+en;
                            jo.put( "x_range", range );
                            CdawebInfoCatalogSource.coverage.put( name, range );
                        } catch (ParseException ex) {
                            logger.log(Level.SEVERE, null, ex);
                        }

                        catalog.put( ic++, jo );
                    }
                }

            }

            JSONObject result= new JSONObject();
            result.put( "catalog", catalog );
            return result.toString(4);
        } catch (MalformedURLException | SAXException | ParserConfigurationException | XPathExpressionException | JSONException ex) {

            throw new RuntimeException(ex);
        }
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
            
            if ( url.getProtocol().equals("file") ) {
                long lastModified= new File( url.getFile() ).lastModified();
                jo.put( "lastModified", TimeUtil.fromMillisecondsSince1970(lastModified) );
            }
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
