
package org.hapiserver.source.cdaweb;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.AbstractHapiRecord;
import org.hapiserver.AbstractHapiRecordSource;
import org.hapiserver.CsvDataFormatter;
import org.hapiserver.HapiRecord;
import org.hapiserver.TimeUtil;
import org.hapiserver.source.AggregationGranuleIterator;
import org.hapiserver.source.SourceUtil;
import static org.hapiserver.source.tap.TapAvailabilitySource.getCatalog;
import static org.hapiserver.source.tap.TapAvailabilitySource.getInfo;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * return availability, showing when file granules are found.
 * @author jbf
 */
public class CdawebAvailabilitySource extends AbstractHapiRecordSource {

    private static final Logger logger= Logger.getLogger("hapi.cdaweb");

    String spid;
    
    public CdawebAvailabilitySource( String idavail ) {
        int i= idavail.indexOf("/");
        spid= idavail.substring(i+1);
    }
    
    @Override
    public boolean hasGranuleIterator() {
        return true;
    }
    
    @Override
    public Iterator<int[]> getGranuleIterator(int[] start, int[] stop) {
        return new AggregationGranuleIterator( "$Y-$m", start, stop );
    }

    @Override
    public boolean hasParamSubsetIterator() {
        return false;
    }

    @Override
    public Iterator<HapiRecord> getIterator(int[] start, int[] stop) {
        
        try {
            
            String sstart= String.format( "%04d%02d%02dT%02d%02d%02dZ", start[0], start[1], start[2], start[3], start[4], start[5] );
            String sstop= String.format( "%04d%02d%02dT%02d%02d%02dZ", stop[0], stop[1], stop[2], stop[3], stop[4], stop[5] );
            
            URL url = new URL(String.format( CdawebInfoCatalogSource.CDAWeb + "WS/cdasr/1/dataviews/sp_phys/datasets/%s/orig_data/%s,%s", spid, sstart, sstop) );
            
            logger.log(Level.INFO, "readData URL: {0}", url);
            
            System.out.println("url: "+url );
            
            try {
                Document doc= SourceUtil.readDocument( url );
                XPathFactory factory = XPathFactory.newInstance();
                XPath xpath = (XPath) factory.newXPath();
                NodeList starts = (NodeList) xpath.evaluate( "//DataResult/FileDescription/StartTime", doc, XPathConstants.NODESET );
                NodeList stops = (NodeList) xpath.evaluate( "//DataResult/FileDescription/EndTime", doc, XPathConstants.NODESET );
                NodeList lengths = (NodeList) xpath.evaluate( "//DataResult/FileDescription/Length", doc, XPathConstants.NODESET );
                return fromNodes( starts, stops, lengths );
            } catch (IOException | SAXException | ParserConfigurationException | XPathExpressionException ex) {
                throw new RuntimeException(ex);
            }
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    
    private static Iterator<HapiRecord> fromNodes( final NodeList starts, final NodeList stops, final NodeList lengths ) {
        final int len= starts.getLength();
        
        return new Iterator<HapiRecord>() {
            int i=0;
            
            @Override
            public boolean hasNext() {
                return i<len;
            }

            @Override
            public HapiRecord next() {
                String[] fields= new String[] { starts.item(i).getTextContent(), stops.item(i).getTextContent(), lengths.item(i).getTextContent() };
                i=i+1;
                return new AbstractHapiRecord() {
                    @Override
                    public int length() {
                        return 3;
                    }

                    @Override
                    public String getIsoTime(int i) {
                        String f= fields[i];
                        int n1= f.length()-1;
                        if ( f.charAt(0)=='"' && f.charAt(n1)=='"' ) {
                            return f.substring(1,n1);
                        } else {
                            return f;
                        }
                    }

                    @Override
                    public int getInteger(int i) {
                        return Integer.parseInt(fields[i]); 
                    }
                };
            }
        };
    }
    
    private static void printHelp() {
        System.err.println("TapAvailabilitySource [id] [start] [stop]");
        System.err.println("   no arguments will provide the catalog response");
        System.err.println("   if only id is present, then return the info response for the id");
        System.err.println("   if id,start,stop then return the data response.");
    }
    
    public static void main( String[] args ) throws IOException, ParseException {
        
        args= new String[] { "availability/AC_K1_SWE", "2022-01-01T00:00Z", "2023-05-01T00:00Z" };
    
        switch (args.length) {
            case 0:
                System.out.println( getCatalog() );
                break;
            case 1:
                System.out.println( getInfo(args[0]) );
                break;
            case 3:
                Iterator<HapiRecord> iter = 
                        new CdawebAvailabilitySource(args[0]).getIterator( 
                                TimeUtil.parseISO8601Time(args[1]), 
                                TimeUtil.parseISO8601Time(args[2]) );
                if ( iter.hasNext() ) {
                    CsvDataFormatter format= new CsvDataFormatter();
                    try {
                        format.initialize( new JSONObject( getInfo(args[0]) ),System.out,iter.next() );
                    } catch (JSONException ex) {
                        throw new RuntimeException(ex);
                    }
                    do {
                        HapiRecord r= iter.next();
                        format.sendRecord( System.out, r );
                    } while ( iter.hasNext() );
                }   
                break;
            default:
                printHelp();
        }
                
    }
    
}
