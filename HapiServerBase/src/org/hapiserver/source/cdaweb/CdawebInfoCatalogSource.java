
package org.hapiserver.source.cdaweb;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    
    protected static Map<String,String> coverage= new HashMap<>();
    protected static Map<String,String> filenaming= new HashMap<>();

    private static String getURL( String id, Node dataset ) {
        NodeList kids= dataset.getChildNodes();
        String lookfor= "ftp://cdaweb.gsfc.nasa.gov/pub/istp/";
        String lookfor2= "ftp://cdaweb.gsfc.nasa.gov/pub/cdaweb_data";
        for ( int j=0; j<kids.getLength(); j++ ) {
            Node childNode= kids.item(j);
            if ( childNode.getNodeName().equals("access") ) {
                NodeList kids2= childNode.getChildNodes();
                for ( int k=0; k<kids2.getLength(); k++ ) {
                    if ( kids2.item(k).getNodeName().equals("URL") ) {
                        if ( kids2.item(k).getFirstChild()==null ) {
                            logger.log(Level.FINE, "URL is missing for {0}, data cannot be accessed.", id);
                            return null;
                        }
                        
                        String url= kids2.item(k).getFirstChild().getTextContent().trim();
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
                        return url;
                    }
                }
            }
        }
        return null;
    }

    /**
     * return the catalog response by parsing all.xml.
     * @return
     * @throws IOException 
     */
    public static String getCatalog() throws IOException {
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
                
                String st= attrs.getNamedItem("timerange_start").getTextContent();
                String en= attrs.getNamedItem("timerange_stop").getTextContent();
                if ( st.length()>1 && Character.isDigit(st.charAt(0))
                        && en.length()>1 && Character.isDigit(en.charAt(0))
                        //&& nssdc_ID.contains("None") ) {
                         ) {
                    String name= attrs.getNamedItem("serviceprovider_ID").getTextContent();
                    String sourceurl= getURL(name,node);
                    if ( sourceurl!=null && 
                            ( sourceurl.startsWith( CDAWeb ) ||
                            sourceurl.startsWith("ftp://cdaweb.gsfc.nasa.gov" ) ) && !sourceurl.startsWith("/tower3/private" ) ) {
                        JSONObject jo= new JSONObject();
                        jo.put( "id", name );
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
     * return the info response generated by combining several sources.  These info
     * responses are stored (presently) at http://mag.gmu.edu/git-data/cdaweb-hapi-metadata/hapi/bw/CDAWeb/info/.
     * @param id
     * @return
     * @throws MalformedURLException
     * @throws IOException 
     */
    public static String getInfo( String id ) throws MalformedURLException, IOException {
        int i= id.indexOf('_');
        String g;
        if ( i>-1 ) {
            g= id.substring(0,i);
        } else {
            throw new IllegalArgumentException("bad id: "+id);
        }
        URL url = new URL( "http://mag.gmu.edu/git-data/cdaweb-hapi-metadata/hapi/bw/CDAWeb/info/"+id+".json" );
        String src= SourceUtil.getAllFileLines( url );
        return src;
        
    }
    
    public static void main( String[] args ) throws IOException {
        //args= new String[] { "AC_AT_DEF" };
        args= new String[0];
        
        if ( args.length==0 ) {
            System.out.println( CdawebInfoCatalogSource.getCatalog() );
        } else if ( args.length==1 ) {
            System.out.println( CdawebInfoCatalogSource.getInfo( args[0] ) );
        }
    }
    
}
