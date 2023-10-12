
package org.hapiserver.source.das2server;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
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
import org.hapiserver.HapiRecord;
import org.hapiserver.source.SourceUtil;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Simple Das2Stream parser.  Note this can only parse Das2Streams with one kind of packet, which is
 * also the only sort of stream which can be converted to a HAPI stream.
 * @author jbf
 */
public class Das2StreamParser implements Iterator<HapiRecord> {

    private final ReadableByteChannel channel;
    
    private Document packetDescriptor;
    
    private JSONObject infoResponse;
    
    // The number identifying the packet 
    private int packetId;
    
    // The number of bytes in each record
    private int recordLengthBytes;
    
    // the bytes within the hapi record
    byte[] recordBytes;
    int[] offs;
    int[] lens;
    String[] d2stypes;

    private HapiRecord nextRecord;
    
    
    public Das2StreamParser( InputStream ins ) throws IOException, SAXException, ParserConfigurationException, XPathExpressionException {
        this.channel= Channels.newChannel(ins);    
        byte[] b4bb= new byte[4];
        ByteBuffer b4= ByteBuffer.wrap(b4bb);
        this.channel.read(b4);
        if ( b4.get(0)!='[' || b4.get(3)!=']' ) {
            throw new IllegalArgumentException("It was not a stream, expected [xx]");
        }
        byte[] b6bb= new byte[6];
        ByteBuffer b6= ByteBuffer.wrap(b6bb);
        this.channel.read(b6);
        int len= Integer.parseInt( new String(b6bb) );
        ByteBuffer bn= ByteBuffer.allocateDirect(len);
        this.channel.read(bn);
        
        b4= ByteBuffer.wrap(b4bb);
        this.channel.read(b4);
        if ( b4.get(0)!='[' || b4.get(3)!=']' ) {
            throw new IllegalArgumentException("It was not a stream, expected [xx]");
        }
        String spacketId = new String(b4bb,1,2);
        packetId= Integer.parseInt( spacketId );
        
        b6bb= new byte[6];
        b6= ByteBuffer.wrap(b6bb);
        this.channel.read(b6);
        len= Integer.parseInt( new String(b6bb) );
        
        byte[] bnbb= new byte[len];
        bn= ByteBuffer.wrap(bnbb);
        this.channel.read(bn);
        
        String xmlString= new String(bnbb,Charset.forName("UTF-8"));
        packetDescriptor=  SourceUtil.readDocument(xmlString);
        
        recordLengthBytes= getRecordLengthBytes(packetDescriptor);
        
        // read the first record
        nextRecord= setUpNextRecord();
        
    }
    
    private HapiRecord setUpNextRecord() throws IOException {
        byte[] b4bb= new byte[4];
        ByteBuffer b4= ByteBuffer.wrap(b4bb);
        int bytesRead= this.channel.read(b4);
        if ( bytesRead==-1 ) {
            return null;
        }
        while ( b4.remaining()>0 ) this.channel.read(b4);
        if ( b4.get(0)!=':' || b4.get(3)!=':' ) {
            return null;
        }
        
        int recordLength= recordLengthBytes;
        recordBytes= new byte[recordLength];
        
        ByteBuffer bn= ByteBuffer.wrap(recordBytes);
        while ( bn.remaining()>0 ) this.channel.read(bn);

        return parseNextRecord(recordBytes);
        
    }
    /**
     * convert the Das2Stream data type to HAPI type
     * @param das2streamType
     * @return 
     */
    private String convertType( String das2streamType ) {
        if ( das2streamType.startsWith("time") ) {
            return "isotime";
        } else if ( das2streamType.startsWith("ascii") ) {
            return "double";
        } else if ( das2streamType.startsWith("little_endian_real4") ) {
            return "double";
        } else if ( das2streamType.startsWith("little_endian_real8") ) {
            return "double";
        } else {
            throw new IllegalArgumentException("unsupported type: "+das2streamType);
        }
    }
    
    private int getBytesFor( String das2streamType ) {
        if ( das2streamType.startsWith("time") ) {
            return Integer.parseInt(das2streamType.substring(4));
        } else if ( das2streamType.startsWith("ascii") ) {
            return Integer.parseInt(das2streamType.substring(5));
        } else if ( das2streamType.startsWith("little_endian_real4") ) {
            return 4;
        } else if ( das2streamType.startsWith("little_endian_real8") ) {
            return 8;
        } else {
            throw new IllegalArgumentException("unsupported type: "+das2streamType);
        }
    }
    
    private int getRecordLengthBytes( Document packetDescriptor ) throws XPathExpressionException {
        XPath xpath= XPathFactory.newInstance().newXPath();
        NodeList nl= (NodeList) xpath.evaluate( "//packet/*", packetDescriptor, XPathConstants.NODESET );
        int recordLengthBytes= 0;
        
        offs= new int[nl.getLength()];
        lens= new int[nl.getLength()];
        d2stypes= new String[nl.getLength()];
        
        for ( int i=0; i<nl.getLength(); i++ ) {
            offs[i]= recordLengthBytes;
            int len;
            Node n= nl.item(i);
            NamedNodeMap attributes= n.getAttributes();
            JSONObject param= new JSONObject();
            String name = i==0 ? "time" : attributes.getNamedItem("name").getNodeValue();
            String d2stype= attributes.getNamedItem("type").getNodeValue();
            d2stypes[i]= d2stype;
            if ( d2stype.startsWith("time") ) {
                len = Integer.parseInt(d2stype.substring(4));
            }
            if ( n.getNodeName().equals("yscan") ) {
                int nitems= Integer.parseInt(attributes.getNamedItem("nitems").getNodeValue());
                len = (nitems*getBytesFor(d2stype));
            } else if ( n.getNodeName().equals("x") ) {
                len = getBytesFor(d2stype);
            } else if ( n.getNodeName().equals("y") ) {
                len = getBytesFor(d2stype);
            } else if ( n.getNodeName().equals("z") ) {
                len = getBytesFor(d2stype);
            } else {
                throw new RuntimeException("Unsupported sub-packet type: "+n.getNodeName());
            }
            recordLengthBytes+= len;
            lens[i]= len;
        }
        return recordLengthBytes;
    }
        
    HapiRecord parseNextRecord( byte[] buffer ) {
        return new HapiRecord() {
            @Override
            public String getIsoTime(int i) {
                return new String( buffer, offs[i], lens[i] );
            }

            @Override
            public String[] getIsoTimeArray(int i) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public String getString(int i) {
                return new String( buffer, offs[i], lens[i] );  // Note, Das2 does not use this.
            }

            @Override
            public String[] getStringArray(int i) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public double getDouble(int i) {
                if ( d2stypes[i].equals("little_endian_real4") ) {
                    ByteBuffer buff= ByteBuffer.wrap( buffer, offs[i], lens[i] );
                    return buff.getFloat();
                } else if ( d2stypes[i].equals("little_endian_real8") ) {
                    ByteBuffer buff= ByteBuffer.wrap( buffer, offs[i], lens[i] );
                    return buff.getDouble();
                } else if ( d2stypes[i].startsWith("ascii") ) {
                    try {
                        return Double.parseDouble(getString(i));
                    } catch ( NumberFormatException ex ) {
                        throw new IllegalArgumentException("unable to parse das2stream: "+getString(i));
                    }
                    
                } else {
                    throw new IllegalArgumentException("hmm, exception should have been thrown already.");
                }
            }

            @Override
            public double[] getDoubleArray(int i) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public int getInteger(int i) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public int[] getIntegerArray(int i) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public String getAsString(int i) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public int length() {
                return offs.length;
            }
        };
                
    }
    
    /**
     * return the beginnings of the info response for this stream.
     * @return 
     */
    public JSONObject getInfo() {
        try {
            JSONObject result= new JSONObject();
            JSONArray parameters= new JSONArray();
            XPath xpath= XPathFactory.newInstance().newXPath();
            NodeList nl= (NodeList) xpath.evaluate( "//packet/*", packetDescriptor, XPathConstants.NODESET );
            int recordLengthBytes= 0;
            for ( int i=0; i<nl.getLength(); i++ ) {
                Node n= nl.item(i);
                NamedNodeMap attributes= n.getAttributes();
                JSONObject param= new JSONObject();
                String name = i==0 ? "time" : attributes.getNamedItem("name").getNodeValue();
                param.put("name",name);
                String d2stype= attributes.getNamedItem("type").getNodeValue();
                param.put("type",convertType(d2stype));
                if ( d2stype.startsWith("time") ) {
                    int len= Integer.parseInt(d2stype.substring(4));
                    param.put("length",len);
                    recordLengthBytes+=len;
                }
                if ( n.getNodeName().equals("yscan") ) {
                    int len= Integer.parseInt(attributes.getNamedItem("nitems").getNodeValue());
                    param.put("length",len);
                    recordLengthBytes+=(len*getBytesFor(d2stype));
                } else if ( n.getNodeName().equals("y") ) {
                    recordLengthBytes+= getBytesFor(d2stype);
                } else if ( n.getNodeName().equals("z") ) {
                    recordLengthBytes+= getBytesFor(d2stype);
                }
                parameters.put(i,param);
            }
            result.put("parameters",parameters);
            infoResponse= result;
            return result;
            
        } catch (XPathExpressionException | JSONException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * this must be called after getInfo is called.
     * @return 
     */
    public Iterator<HapiRecord> getHapiRecordIterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        return nextRecord!=null;
    }

    @Override
    public HapiRecord next() {
        HapiRecord r= nextRecord;
        
        try {
            nextRecord = setUpNextRecord();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        
        return r;
        
    }
    
    public static void main( String[] args ) throws Exception {
        InputStream ins;
        args= new String[] {"test1"};
        if ( args[0].equals("test1") ) {
            String surl= "https://jupiter.physics.uiowa.edu/das/server?"
                    + "server=dataset"
                    + "&dataset=Juno/Ephemeris/EuropaCoRotational"
                    + "&start_time=2021-04-15T00:00Z"
                    + "&end_time=2021-04-17T00:00Z"
                    + "&interval=600";
            ins= new URL(surl).openStream();
        } else {
            throw new IllegalArgumentException("bad arg1");
        }
        Das2StreamParser p= new Das2StreamParser(ins);
        System.out.println( p.getInfo().toString(4) );
        Iterator<HapiRecord> rec= p.getHapiRecordIterator();
        while ( rec.hasNext() ) {
            System.out.println(rec.next());
        }
    }    
}
