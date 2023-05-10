/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.hapiserver.source.cdaweb;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
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
import org.hapiserver.source.SourceUtil;
import org.hapiserver.source.tap.CsaInfoCatalogSource;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
        
/**
 *
 * @author jbf
 */
public class CdawebInfoCatalogSource {
    
    private static final Logger logger= Logger.getLogger("hapi.cdaweb");
    
    public static final String CDAWeb = "https://cdaweb.gsfc.nasa.gov/";
    
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
    
    public static String getInfo( String id ) throws MalformedURLException, IOException {
        int i= id.indexOf('_');
        String g;
        if ( i>-1 ) {
            g= id.substring(0,i);
        } else {
            throw new IllegalArgumentException("bad id: "+id);
        }
        URL url = new URL( "http://mag.gmu.edu/git-data/cdaweb-hapi-metadata/cache/bw/"+g+"/"+id+"/"+id+"-info-full.json" );
        String src= SourceUtil.getAllFileLines( url );
        try {
            JSONObject jo= new JSONObject(src);
            if ( jo.has("info") ) {
                jo= jo.getJSONObject("info");
                return jo.toString(4);
            } else {
                return src;
            }
        } catch (JSONException ex) {
            logger.log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
        
    }
    
    public static void main( String[] args ) throws IOException {
        args= new String[] { "AC_AT_DEF" };
        if ( args.length==0 ) {
            System.out.println( CdawebInfoCatalogSource.getCatalog() );
        } else if ( args.length==1 ) {
            System.out.println( CdawebInfoCatalogSource.getInfo( args[0] ) );
        }
    }
    
}
