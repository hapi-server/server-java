
package org.hapiserver.source.cdaweb;

import java.io.File;
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
import org.hapiserver.source.SourceUtil;
import org.w3c.dom.NodeList;

/**
 * return availability, showing when file granules are found.
 * @author jbf
 */ 
public class CdawebAvailabilityRecordSource extends AbstractHapiRecordSource {

    private static final Logger logger= Logger.getLogger("hapi.cdaweb");
    
    /**
     * the field containing the partial filename.
     */
    public static int FIELD_FILENAME= 2;

    String spid;
    int rootlen;
    String root;
    String bobwurl;
    
    /**
     * @param availRoot folder containing orig_data responses, with a file "info/AC_AT_DEF.pkl"
     * @param idavail the id for the availability set, like "AC_OR_SSC/source"
     * @param info the info for this availability set.
     */
    public CdawebAvailabilityRecordSource( String availRoot, String idavail, JSONObject info ) {
        String roots= availRoot + "/" + "info/";
        spid= spidFor(idavail);
        bobwurl= roots + spid + ".json";
        try {
            JSONArray array= info.getJSONArray("parameters");
            JSONObject p= array.getJSONObject(2); // the filename parameter
            JSONObject stringType= p.getJSONObject("stringType");
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
     * return the root for references in availability response
     * @return 
     */
    public String getRoot() {
        return this.root;
    }
    
    /**
     * get the catalog of the source files.
     * @param url the catalog of the datasets.  Note foo@1 will be just "foo" in this result
     * @return
     * @throws IOException 
     */
    public static String getAvailabilityCatalog(String url) throws IOException {
        try {
            String catalogString= CdawebInfoCatalogSource.getCatalog(url);
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
                jo.put( "id", id + "/source" );
                if ( jo.has("title") ) {
                    jo.put("title","Source of "+jo.getString("title") );
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
     * @param roots root orig_data folder (website or file://...) containing the file "info/AC_H0_MFI.pkl" file
     * @param availId the dataset id, like "AC_H0_SWE/availability"
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
            String lastModified= null;
            
            String availString;
            try {
                int i= availId.indexOf("/");
                String id= availId.substring(0,i);
                String surl= roots + "info/" + id + ".json";
                sourceURL= new URL(surl);
                //File jsonfile= SourceUtil.downloadFile( url, File.createTempFile(id, ".json") );
                availString= SourceUtil.getAllFileLines( sourceURL );
                if ( roots.startsWith("file:") ) {
                    File file= new File( surl.substring(5) );
                    lastModified= TimeUtil.reformatIsoTime("2000-01-01T00:00:00Z", 
                            TimeUtil.fromMillisecondsSince1970( file.lastModified() ) );
                } else {
                    lastModified= TimeUtil.previousDay( TimeUtil.isoTimeFromArray( TimeUtil.now() ) );
                }
            } catch (MalformedURLException ex) {
                throw new RuntimeException(ex); //TODO
            } catch (IOException ex) {
                throw new RuntimeException(ex); //TODO
            }
            
            
            String root;
            
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
            
            int maxLen=0;
            for ( i=0; i<array.length(); i++ ) {
                JSONObject file= array.getJSONObject(i);
                String name= file.getString("Name");
                if ( maxLen<name.length() ) maxLen= name.length();
            }
            int filenameLen= maxLen-root.length();
            
            String stringType= "{ \"uri\": { \"mediaType\": \"application/x-cdf\", \"base\": \"" + root + "\" } }";
            
            return "{\n" +
                    "    \"x_sourceURL\": \""+sourceURL + "\", \n" +
                    "    \"HAPI\": \"3.1\",\n" +
                    "    \"modificationDate\": \"" + lastModified + "\",\n" +
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
                    "            \"stringType\":" + stringType + ",\n" +
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
        return null; //not used
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

    @Override
    public String getTimeStamp(int[] start, int[] stop) {
        if ( bobwurl.startsWith("file:") ) {
            File file= new File( bobwurl.substring(5) );
            return TimeUtil.fromMillisecondsSince1970( file.lastModified() );
        }
        return null;
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
        args= new String[] { "TSS-1R_M1_CSAA/source", "1996-02-28T02:00:00.000Z", "1996-02-28T05:59:46.000Z" };
        //args= new String[] { "availability/FORMOSAT5_AIP_IDN" };
        //args= new String[] { "http://mag.gmu.edu/git-data/cdawmeta/data/orig_data/", "RBSP-A-RBSPICE_LEV-2_ESRHELT" };
        switch (args.length) {
            case 0:
                System.out.println(getAvailabilityCatalog("http://mag.gmu.edu/git-data/cdawmeta/data/hapi/catalog.json") );
                break;
            case 2:
                System.out.println(getInfoAvail(args[0],args[1]) );
                break;
            case 3:
                JSONObject info;
                String orig_data= "http://mag.gmu.edu/git-data/cdawmeta/data/orig_data/";
                try {
                    info= new JSONObject( getInfoAvail(orig_data,args[0]) );
                } catch (JSONException ex) {
                    throw new RuntimeException(ex);
                }
                Iterator<HapiRecord> iter = 
                        new CdawebAvailabilityRecordSource( orig_data,args[0],info).getIterator( 
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
