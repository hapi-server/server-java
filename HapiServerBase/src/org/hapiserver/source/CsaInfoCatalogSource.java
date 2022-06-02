package org.hapiserver.source;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * returns catalog and info responses for the TAP server at ESAC. Requests are made to https://csa.esac.esa.int/csa-sl-tap/tap/sync
 * and reformatted into HAPI responses. This can be used on the command line (see main method) or the getCatalog and getInfo methods
 * can be called using the classpath source.
 *
 * Here is an example configuration:
 * <pre>
 * {
 *     "HAPI": "3.0",
 *     "catalog": [
 *         {
 *             "x_group_id": "cfa",
 *             "x_source": "classpath",
 *             "x_class": "org.hapiserver.source.CsaInfoCatalogSource",
 *             "x_method": "getCatalog",
 *             "x_config": {
 *                 "info": {
 *                     "x_source":"classpath",
 *                     "x_class":"org.hapiserver.source.CsaInfoCatalogSource",
 *                     "x_method": "getInfo",
 *                     "x_args": [ "${id}" ]
 *                 },
 *                 "data": {
 *                     "source": "classpath",
 *                     "class":"org.hapiserver.source.TAPDataSource",
 *                     "args":["https://csa.esac.esa.int/csa-sl-tap/","${id}"]
 *                 }
 *             }
 *         }
 *     ],
 *     "status": {
 *         "code": 1200,
 *         "message": "OK request successful"
 *     }
 * }
 * </pre>
 *
 * @author jbf
 */
public class CsaInfoCatalogSource {

    private static final Logger logger = Logger.getLogger("hapi.cef");

    private static Document readDoc(InputStream is) throws SAXException, IOException, ParserConfigurationException {
        DocumentBuilder builder;
        builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        InputSource source = new InputSource(new InputStreamReader(is));
        Document document = builder.parse(source);
        return document;
    }

    /**
     * produce the info response for a given ID. This assumes the response will be cached and performance is not an issue.
     *
     * @param id the dataset id.
     * @return the JSON formatted response.
     * @throws IOException
     */
    public static String getInfo(String id) throws IOException {

        // get the start and stop date by posting another request.  TODO: consider if this should be done during the catalog request.
        String startDate, stopDate;
        String trurl = String.format("https://csa.esac.esa.int/csa-sl-tap/tap/sync?REQUEST=doQuery&LANG=ADQL&FORMAT=CSV&QUERY=SELECT+dataset_id,title,start_date,end_date+FROM+csa.v_dataset+where+dataset_id=%%27%s%%27", id);
        logger.log(Level.FINE, "wrap {0}", trurl);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(trurl).openStream()))) {
            String header = reader.readLine();
            String data = reader.readLine();
            String[] ss = SourceUtil.stringSplit(data);
            if (ss.length != 4) {
                throw new IllegalArgumentException("expected four fields when getting time range");
            }
            startDate = ss[ss.length - 2];
            stopDate = ss[ss.length - 1];
        }

        String url = String.format("https://csa.esac.esa.int/csa-sl-tap/data?retrieval_type=HEADER&DATASET_ID=%s&FORCEPACK=false", id);
        JSONObject jo = new JSONObject();

        try (InputStream ins = new URL(url).openStream()) {
            //String s= SourceUtil.getAllFileLines( new URL(url) );

            Document document;

            try {
                document = readDoc(ins);
            } catch (SAXException | ParserConfigurationException ex) {
                logger.log(Level.WARNING, "Unable to parse document returned by {0}", url);
                throw new RuntimeException(ex);
            }

            jo.put("HAPI", "3.0");

            XPathFactory factory = XPathFactory.newInstance();
            XPath xpath = (XPath) factory.newXPath();

            String sval = (String) xpath.evaluate("/DATASETS/DATASET_METADATA/DATASET_DESCRIPTION", document, XPathConstants.STRING);
            jo.put("x_description", sval.trim());

            sval = (String) xpath.evaluate("/DATASETS/DATASET_METADATA/TIME_RESOLUTION/text()", document, XPathConstants.STRING);
            if (sval != null) {
                jo.put("cadence", "PT" + sval + "S");
            }

            NodeList nl = (NodeList) xpath.evaluate("/DATASETS/DATASET_METADATA/PARAMETERS/*", document, XPathConstants.NODESET);
            JSONArray parameters = new JSONArray();

            for (int i = 0; i < nl.getLength(); i++) {
                Node p = nl.item(i);
                JSONObject parameter = new JSONObject();
                NodeList n = p.getChildNodes();
                for (int j = 0; j < n.getLength(); j++) {
                    Node c = n.item(j); // parameter
                    switch (c.getNodeName()) {
                        case "PARAMETER_ID":
                            parameter.put("name", c.getTextContent());
                            break;
                        case "UNITS":
                            if (c.getTextContent().equals("unitless")) {
                                parameter.put("units", JSONObject.NULL);
                            } else {
                                if ( parameter.optString("type","").equals("isotime") ) {
                                    parameter.put("units", "UTC" );
                                } else {
                                    parameter.put("units", c.getTextContent());
                                }
                            }
                            break;
                        case "VALUE_TYPE":
                            String t = c.getTextContent();
                            if (t.equals("ISO_TIME")) {
                                parameter.put("type", "isotime");
                                parameter.put("units", "UTC" );
                            } else if (t.equals("ISO_TIME_RANGE")) {
                                parameter.put("type", "isotime");
                                parameter.put("x_type", "ISO_TIME_RANGE");
                                parameter.put("units", "UTC" );
                            } else if (t.equals("FLOAT")) {
                                parameter.put("type", "double");
                            } else if (t.equals("INT")) {
                                parameter.put("type", "integer");
                            } else if (t.equals("CHAR")) {
                                parameter.put("type", "string");
                            }
                            break;
                        case "SIGNIFICANT_DIGITS":
                            String type = parameter.optString("type", "");
                            if (type.equals("isotime") || type.equals("string")) {
                                if (parameter.optString("x_type", "").equals("ISO_TIME_RANGE")) {
                                    parameter.put("length", 25);
                                } else {
                                    parameter.put("length", Integer.parseInt(c.getTextContent()));
                                }
                            }
                            break;
                        case "SIZES":
                            if (!c.getTextContent().equals("1")) {
                                JSONArray array = new JSONArray();
                                array.put(0, Integer.parseInt(c.getTextContent())); //TODO: multi-dimensional
                                parameter.put("size", array);
                            }
                            break;
                        case "CATDESC":
                            parameter.put("description", c.getTextContent());
                            break;
                        case "FIELDNAM":
                            parameter.put("label", c.getTextContent());
                            break;
                        case "FILLVAL":
                            if ( j==0 ) {
                                parameter.put("fill", JSONObject.NULL );
                            } else {
                                parameter.put("fill", c.getTextContent());
                            }
                            break;
                        default:
                            break;
                    }
                }
                
                if ( parameter.optString("type","").equals("isotime") ) {
                    parameter.remove("units");
                }

                parameters.put(parameters.length(), parameter);
            }
            jo.put("parameters", parameters);
            jo.put("startDate", startDate);
            jo.put("stopDate", stopDate);
            jo.put("x_tap_url", url);
            return jo.toString(4);

        } catch (JSONException | XPathExpressionException ex) {
            throw new RuntimeException(ex);
        }

    }

    /**
     * return the catalog
     * @return @throws IOException
     */
    public static String getCatalog() throws IOException {
        try {
            JSONArray catalog = new JSONArray();
            String url = "https://csa.esac.esa.int/csa-sl-tap/tap/sync?REQUEST=doQuery&LANG=ADQL&FORMAT=CSV&QUERY=SELECT+dataset_id,title+FROM+csa.v_dataset";
            try (InputStream in = new URL(url).openStream()) {
                BufferedReader ins = new BufferedReader(new InputStreamReader(in));
                String s = ins.readLine();
                if (s != null) {
                    s = ins.readLine(); // skip the first header line
                }
                while (s != null) {
                    int i = s.indexOf(",");
                    JSONObject jo = new JSONObject();
                    jo.put("id", s.substring(0, i).trim());
                    String t = s.substring(i + 1).trim();
                    if (t.startsWith("\"") && t.endsWith("\"")) {
                        t = t.substring(1, t.length() - 1);
                    }
                    jo.put("title", t);
                    catalog.put(catalog.length(), jo);
                    s = ins.readLine();
                }
            }
            JSONObject result = new JSONObject();
            result.put("HAPI", "3.0");
            result.put("catalog", catalog);
            result.put("status", getOKStatus());
            result.put("x_tap_url",url);    

            if (logger.isLoggable(Level.FINER)) {
                logger.finer(result.toString(4));
            }

            return result.toString(4);

        } catch (JSONException ex) {
            throw new RuntimeException(ex);

        }
    }

    private static JSONObject getOKStatus() throws JSONException {
        JSONObject status = new JSONObject();
        status.put("code", 1200);
        status.put("message", "OK request successful");
        return status;
    }

    private static void printHelp() {
        System.err.println("CsaInfoCatalogSource [id]");
        System.err.println("   [id] if present, then return the info response for the id");
        System.err.println("   [id] if missing, then return the catalog response");
    }

    /**
     *
     * @param args
     */
    public static void main(String[] args) {
        if (args.length == 1) {
            if (args[0].equals("--help")) {
                printHelp();
                System.exit(1);
            } else {
                try {
                    String s = getInfo(args[0]);
                    System.out.println(s);
                    System.exit(0);
                } catch (IOException ex) {
                    ex.printStackTrace();
                    System.exit(-2);
                }
            }
        } else if (args.length == 0) {
            try {
                String s = getCatalog();
                System.out.println(s);
                System.exit(0);
            } catch (IOException ex) {
                ex.printStackTrace();
                System.exit(-1);
            }

        }
    }
}
