
package org.hapiserver.source.cdaweb;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.AbstractHapiRecord;
import org.hapiserver.AbstractHapiRecordSource;
import org.hapiserver.CsvDataFormatter;
import org.hapiserver.HapiRecord;
import org.hapiserver.TimeUtil;
import org.hapiserver.source.AggregationGranuleIterator;
import org.hapiserver.source.SourceUtil;
import org.w3c.dom.NodeList;

/**
 * return availability, showing when file granules are found.
 * @author jbf
 */ 
public class CdawebAvailabilitySource extends AbstractHapiRecordSource {

    private static final Logger logger= Logger.getLogger("hapi.cdaweb");
    
    /**
     * the field containing the partial filename.
     */
    public static int FIELD_FILENAME= 2;

    String spid;
    int rootlen;
    String roots;
    String root;
    String bobwurl;
    
    /**
     * 
     * @param hapiHome ignored.
     * @param idavail the id for the availability set, like "availability/AC_OR_SSC"
     * @param info the info for this availability set.
     * @param data the data configuration
     */
    public CdawebAvailabilitySource( String hapiHome, String idavail, JSONObject info, JSONObject data ) {
        String roots= "http://mag.gmu.edu/git-data/cdawmeta/data/orig_data/info/";
        spid= spidFor(idavail);
        bobwurl= roots + spid + ".json";
        try {
            JSONArray array= info.getJSONArray("parameters");
            JSONObject p= array.getJSONObject(2); // the filename parameter
            JSONObject stringType= p.getJSONObject("x_stringType");
            JSONObject urin= stringType.getJSONObject("uri");
            rootlen= urin.getString("base").length();
            if ( !urin.getString("base").contains("sp_phys/") ) {
                if ( idavail.endsWith("OMNI2_H0_MRG1HR") ) {
                    rootlen= rootlen - 8; // problem for another day...
                } else {
                    rootlen= rootlen + 4; //TODO: Bernie's server says "sp_phys" while all.xml says "pub".
                }
            }
            root= urin.getString("base");
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
        
    }
    
    /**
     * set the location of the files containing coverage
     * 
     * @param roots the directory holding all the info responses computed by Bob's Python code, e.g. "http://mag.gmu.edu/git-data/cdawmeta/data/orig_data/info/"
     */
    public void setRoots( String roots ) {
        this.roots= roots;
    }
    
    /**
     * return the root for references in availability response
     * @return 
     */
    public String getRoot() {
        return this.root;
    }
    
    /**
     * get the catalog
     * @return
     * @throws IOException 
     */
    public static String getAvailabilityCatalog() throws IOException {
        try {
            String catalogString= CdawebInfoCatalogSource.getCatalog("http://mag.gmu.edu/git-data/cdawmeta/data/hapi/catalog.json");
            JSONObject catalogContainer= new JSONObject(catalogString);
            JSONArray catalog= catalogContainer.getJSONArray("catalog");
            int n= catalog.length();
            JSONArray newArray= new JSONArray();
            String last=null;
            for ( int i=0; i<n; i++ ) {
                JSONObject jo= catalog.getJSONObject(i);
                jo.setEscapeForwardSlashAlways(false);
                String id= jo.getString("id");
                if ( last!=null && id.startsWith(last) ) {
                    continue;
                }
                //if ( !( id.startsWith("AMPTE") || id.startsWith("AC_OR_SSC") ) ) {
                //    continue;
                //}
                int ia= id.indexOf("@");
                if ( ia>-1 ) {
                    id= id.substring(0,ia);
                }
                last= id;
                jo.put( "id", id + "/availability" );
                if ( jo.has("title") ) {
                    jo.put("title","Availability of "+jo.getString("title") );
                }
                newArray.put( newArray.length(), jo );
            }
            catalogContainer.put("catalog", newArray);
            catalogContainer.setEscapeForwardSlashAlways(false);
            return catalogContainer.toString(4);
            
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * return a sample time for the id.  This will be the last full month containing data.  If all
     * data is contained within just one month, then this is that month.
     * 
     * @param id the data id, such as AC_H1_EPM
     * @return null or an iso8601 range.
     */
    public static String getSampleTime(String id) {
        String range= CdawebInfoCatalogSource.coverage.get(id);
        if ( range==null ) {
            try {
                CdawebInfoCatalogSource.getCatalog("http://mag.gmu.edu/git-data/cdawmeta/data/hapi/catalog.json");
                range= CdawebInfoCatalogSource.coverage.get(id);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
            if ( range==null ) {
                logger.fine("Expect sample times in catalog");
                return null;
            }
        }
        try {
            int[] irange= TimeUtil.parseISO8601TimeRange(range);
            int[] startTime= TimeUtil.getStartTime(irange);
            int[] stopTime= TimeUtil.getStopTime(irange);
            stopTime[2]=1;
            stopTime[3]=0;
            stopTime[4]=0;
            stopTime[5]=0;
            stopTime[6]=0;
            if ( TimeUtil.gt( startTime, stopTime ) ) { // whoops, went back too far
                stopTime[1]= stopTime[1]+1;
                TimeUtil.normalizeTime(stopTime);
            }
            startTime= TimeUtil.subtract( stopTime, new int[] { 0, 1, 0, 0, 0, 0, 0 } );
            return TimeUtil.formatIso8601TimeBrief( startTime ) + "/" +  TimeUtil.formatIso8601TimeBrief( stopTime );
        } catch (ParseException ex) {
            logger.log(Level.SEVERE, null, ex);
            return null;
        }
    }
    
    /**
     * get the info for the id.
     * @param roots root folder (website or file://...) containing "info" directory and "catalog.json"
     * @param availId the dataset id, starting with "availability/"
     * @return 
     */
    public static String getInfoAvail( String roots, String availId ) {
        
        try {
            
            synchronized ( CdawebInfoCatalogSource.class ) {
                if ( CdawebInfoCatalogSource.filenaming==null || CdawebInfoCatalogSource.filenaming.isEmpty() ) {
                    try {
                        CdawebInfoCatalogSource.getCatalog( roots + "catalog.json");
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                }
            }
                        
            URL sourceURL;
            
            String availString;
            try {
                int i= availId.indexOf("/");
                String id= availId.substring(0,i);
                sourceURL= new URL( roots + "info/" + id + ".json" );
                //File jsonfile= SourceUtil.downloadFile( url, File.createTempFile(id, ".json") );
                availString= SourceUtil.getAllFileLines( sourceURL );
            } catch (MalformedURLException ex) {
                throw new RuntimeException(ex); //TODO
            } catch (IOException ex) {
                throw new RuntimeException(ex); //TODO
            }
            
            
            String root;
            int filenameLen=0;
            
            JSONObject filesJson= new JSONObject(availString);
            
            JSONObject data= filesJson.getJSONObject("data");
            
            JSONArray array= data.getJSONArray("FileDescription");
            
            String start= array.getJSONObject(0).getString("StartTime");
            
            String stop=  array.getJSONObject(array.length()-1).getString("EndTime");
            
            String startFile= array.getJSONObject(0).getString("Name");
            
            String stopFile=  array.getJSONObject(array.length()-1).getString("Name");
            
            int n2= array.length()-1;
            int n1= Math.max( 0, n2-4 );
            
            String sampleStart= array.getJSONObject(n1).getString("StartTime");
            String sampleStop= array.getJSONObject(n2).getString("EndTime");
            
            int i;
            for ( i=0; i<startFile.length(); i++ ) {
                if ( startFile.charAt(i)!=stopFile.charAt(i) ) {
                    break;
                }
            }
            
            i= startFile.lastIndexOf("/",i);
            root= startFile.substring(0,i+1);
            
            String stringType= "{ \"uri\": { \"base\": \"" + root + "\" } }";
            
            return "{\n" +
                    "    \"x_sourceURL\": \""+sourceURL + "\", \n" +
                    "    \"HAPI\": \"3.1\",\n" +
                    "    \"modificationDate\": \"" + TimeUtil.previousDay( TimeUtil.isoTimeFromArray( TimeUtil.now() ) ) + "\",\n" +
                    "    \"parameters\": [\n" +
                    "        {\n" +
                    "            \"fill\": null,\n" +
                    "            \"length\": 24,\n" +
                    "            \"name\": \"StartTime\",\n" +
                    "            \"type\": \"isotime\",\n" +
                    "            \"units\": \"UTC\"\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"fill\": null,\n" +
                    "            \"length\": 24,\n" +
                    "            \"name\": \"StopTime\",\n" +
                    "            \"type\": \"isotime\",\n" +
                    "            \"units\": \"UTC\"\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"fill\": null,\n" +
                    "            \"name\": \"filename\",\n" +
                    "            \"type\": \"string\",\n" +
                    "            \"x_stringType\":" + stringType + ",\n" +
                    "            \"length\": "+filenameLen + ",\n" +
                    "            \"units\": null\n" +
                    "        }\n" +
                    "    ],\n" +
                    "    \"sampleStartDate\": \""+sampleStart+"\",\n" +
                    "    \"sampleStopDate\": \""+sampleStop+"\",\n" +
                    "    \"startDate\": \""+start+"\",\n" +
                    "    \"status\": {\n" +
                    "        \"code\": 1200,\n" +
                    "        \"message\": \"OK request successful\"\n" +
                    "    },\n" +
                    "    \"stopDate\": \""+stop+"\"\n" +
                    "}";
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }    
    
    @Override
    public boolean hasGranuleIterator() {
        return false;
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

            URL url = new URL( bobwurl );
            
            logger.log(Level.INFO, "readData URL: {0}", url);
            
            String availString;
            
            //File jsonfile= SourceUtil.downloadFile( url, File.createTempFile(id, ".json") );
            availString= SourceUtil.getAllFileLines( url );
                        
            JSONObject filesJson= new JSONObject(availString);
            
            JSONObject data= filesJson.getJSONObject("data");
            
            JSONArray array= data.getJSONArray("FileDescription");
            
            return fromJSONArray( array, root, rootlen );
            
        } catch ( IOException | JSONException ex) {
            throw new RuntimeException(ex); //TODO
        }
            
    }
    
    private static Iterator<HapiRecord> fromJSONArray( JSONArray array, final String root, final int rootlen ) {
        final int len=  array.length();
        
        logger.fine("creating "+len+" record iterator");
        
        return new Iterator<HapiRecord>() {
            int irec=0;
            
            JSONObject nextObject;
                    
            @Override
            public boolean hasNext() {
                boolean result= irec<len;
                if ( result ) {
                    try {
                        nextObject= array.getJSONObject(irec);
                    } catch (JSONException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                return irec<len;
            }

            @Override
            public HapiRecord next() {
                irec=irec+1; // just for debugging.
                return new AbstractHapiRecord() {
                    @Override
                    public int length() {
                        return 3;
                    }

                    @Override
                    public String getIsoTime(int i) {
                        switch (i) {
                            case 0:
                                return nextObject.optString("StartTime");
                            case 1:
                                return nextObject.optString("EndTime");
                            default:
                                throw new IllegalArgumentException("must be 0 or 1");
                        }
                    }

                    @Override
                    public String getString(int i) {
                        if ( i!=2 ) throw new IllegalArgumentException("must be 2");
                        return nextObject.optString("Name").substring(rootlen);
                    }
                    
                };
            }
        };
        
    }
    private static Iterator<HapiRecord> fromNodes( final NodeList starts, final NodeList stops, final String root, final int rootlen, final NodeList files ) {
        final int len= starts.getLength();
        String[] fields= new String[3];
                
        return new Iterator<HapiRecord>() {
            int irec=0;
            
            @Override
            public boolean hasNext() {
                boolean result= irec<len;
                if ( result ) {
                    fields[0]= starts.item(irec).getTextContent();
                    fields[1]= stops.item(irec).getTextContent();
                    fields[2]= files.item(irec).getTextContent();
                }
                return irec<len;
            }

            @Override
            public HapiRecord next() {
                irec=irec+1; // just for debugging.
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

                    @Override
                    public String getString(int i) {
                        return fields[i].substring(rootlen);
                    }
                    
                };
            }
        };
    }
    
    private static void printHelp() {
        System.err.println("CdawevAvailabilitySource [id] [start] [stop]");
        System.err.println("   no arguments will provide the catalog response");
        System.err.println("   if only id is present, then return the info response for the id");
        System.err.println("   if id,start,stop then return the data response.");
    }
    
    /**
     * AC_K1_SWE/availability -> AC_K1_SWE
     * @param idavail AC_K1_SWE/availability
     * @return AC_K1_SWE
     */
    private static String spidFor( String idavail ) {
        int i= idavail.indexOf("/");
        String spid= idavail.substring(0,i);
        return spid;
    }
    
    public static void main( String[] args ) throws IOException, ParseException {
        
        args= new String[] { };
        //args= new String[] { "availability/AC_K1_SWE" };
        //args= new String[] { "availability/BAR_1A_L2_SSPC" };
        //args= new String[] { "availability/AC_K1_SWE", "2022-01-01T00:00Z", "2023-05-01T00:00Z" };
        //args= new String[] { "availability/RBSP-A-RBSPICE_LEV-2_ESRHELT", "2014-01-01T00:00Z", "2014-02-01T00:00Z" };
        //args= new String[] { "availability/TSS-1R_M1_CSAA", "1996-02-28T02:00:00.000Z", "1996-02-28T05:59:46.000Z" };
        //args= new String[] { "availability/FORMOSAT5_AIP_IDN" };
        //args= new String[] { "http://mag.gmu.edu/git-data/cdawmeta/data/orig_data/info/", "RBSP-A-RBSPICE_LEV-2_ESRHELT" };
        switch (args.length) {
            case 0:
                System.out.println(getAvailabilityCatalog() );
                break;
            case 2:
                System.out.println(getInfoAvail(args[0],args[1]) );
                break;
            case 3:
                JSONObject info;
                String id= "http://mag.gmu.edu/git-data/cdawmeta/data/orig_data/info/";
                try {
                    info= new JSONObject( getInfoAvail(id,args[1]) );
                } catch (JSONException ex) {
                    throw new RuntimeException(ex);
                }
                Iterator<HapiRecord> iter = 
                        new CdawebAvailabilitySource("",args[0],info,null).getIterator( 
                                TimeUtil.parseISO8601Time(args[1]), 
                                TimeUtil.parseISO8601Time(args[2]) );
                if ( iter.hasNext() ) {
                    CsvDataFormatter format= new CsvDataFormatter();
                    format.initialize( info,System.out,iter.next() );
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
