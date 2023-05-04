/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.hapiserver.source.cdaweb;

import gov.nasa.gsfc.spdf.cdfj.CDFException;
import gov.nasa.gsfc.spdf.cdfj.CDFReader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.hapiserver.HapiRecord;
import org.hapiserver.TimeUtil;
import org.hapiserver.source.SourceUtil;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
public class CdawebServicesHapiRecordSource implements Iterator<HapiRecord> {

    HapiRecord nextRecord;
    Adapter[] adapters;
    Object[] oparams;
    double[][] dparams;
    double[][][] ddparams;
    int[][] iparams;
    int[][][] iiparams;
    String[][] sparams;
    String[][][] ssparams;
    
    int index;
    int nindex;
        
    /**
     * one of these methods will be implemented by the adapter.
     */
    private static abstract class Adapter {
        public String adaptString( int index ) {
            return null;
        }
        public double adaptDouble( int index ) {
            return Double.NEGATIVE_INFINITY;
        }
    }
    
    private static class IsotimeEpochAdapter extends Adapter {
        int julianDay;
        long cdfTT2000= Long.MAX_VALUE;
        
        /**
         * the time in milliseconds since year 1 for cdfEpoch.
         */
        double baseTime;
        
        /**
         * 1000000 for epoch, which is a milliseconds offset.
         */
        double baseUnitsFactor;
        String baseYYYYmmddTHH;
        
        double[] array;
        
        private IsotimeEpochAdapter( double[] array ) {
            this.array= array;
            double d= array[0];
            double us2000= ( d - 6.3113904E13 ) * 1000; // ms -> microseconds
            double day2000= Math.floor( us2000 / 86400000000. ); // days since 2000-01-01.
            double usDay= us2000 - day2000 * 86400000000.; // microseconds within this day.
            double ms1970= day2000 * 86400000. + 946684800000.;
            String baseDay= TimeUtil.fromMillisecondsSince1970((long)ms1970);
            baseYYYYmmddTHH= baseDay.substring(0,10)+"T00";
            baseTime= (long)(d-usDay/1000);
        }
        
        private String formatTime( double t ) {
            double offset= t-baseTime;  // milliseconds
            while ( offset>=3600000. ) {
                double hours= offset / 3600000.;
                baseTime = baseTime + hours * 3600000.;
                int hour= Integer.parseInt(baseYYYYmmddTHH.substring(11,13));
                baseYYYYmmddTHH= baseYYYYmmddTHH.substring(0,11)+String.format("%02d",(int)(hour+hours));
                baseYYYYmmddTHH= TimeUtil.normalizeTimeString(baseYYYYmmddTHH).substring(0,13);
                offset= t-baseTime;
            }
            int nanos= (int)( (offset*1000000) % 1000000000. );
            offset= (int)( offset / 1000 ); // now it's in seconds.  Note offset must be positive for this to work.
            int seconds= (int)(offset % 60);
            int minutes= (int)(offset / 60); // now it's in minutes
            return baseYYYYmmddTHH + String.format( ":%02d:%02d.%09d", minutes, seconds, nanos );        
        }        
        
        @Override
        public String adaptString( int index) {
            return formatTime( array[index] );
        }
        
    }
    
    private static class IsotimeTT2000Adapter extends Adapter {
        int julianDay;
        long cdfTT2000= Long.MAX_VALUE;
        /**
         * the time in milliseconds since year 1 for cdfEpoch, or nanoseconds for tt2000.
         */
        double baseTime;
        /**
         * 1 for tt2000, 1000000 for epoch.
         */
        double baseUnitsFactor;
        String baseYYYYmmddTHH;
        
        long[] array;
        
        private IsotimeTT2000Adapter( long[] array ) {
            this.array= array;
            double d= Array.getDouble(array,0);
            double us2000= new LeapSecondsConverter(false).convert(d);
            double day2000= Math.floor( us2000 / 86400000000. ); // days since 2000-01-01.
            double usDay= us2000 - day2000 * 86400000000.; // seconds within this day.
            double ms1970= day2000 * 86400000. + 946684800000.;
            String baseDay= TimeUtil.fromMillisecondsSince1970((long)ms1970);
            baseYYYYmmddTHH= baseDay.substring(0,10)+"T00";
            baseTime= (long)(d-usDay*1000);
        }
        
        private String formatTime( double t ) {
            long offset= (long)((t-baseTime));  // This must not cross a leap second, will always be in nanos
            while ( offset>=3600000000000L ) {
                double hours= offset / 3600000000000L;
                baseTime = baseTime + hours * 3600000000000L;
                int hour= Integer.parseInt(baseYYYYmmddTHH.substring(11,13));
                baseYYYYmmddTHH= baseYYYYmmddTHH.substring(0,11)+String.format("%02d",(int)(hour+hours));
                baseYYYYmmddTHH= TimeUtil.normalizeTimeString(baseYYYYmmddTHH).substring(0,13);
                offset= (long)((t-baseTime));
            }
            int nanos= (int)( (offset) % 1000000000. );
            offset= offset / 1000000000; // now it's in seconds
            int seconds= (int)(offset % 60);
            int minutes= (int)(offset / 60); // now it's in minutes
            return baseYYYYmmddTHH + String.format( ":%02d:%02d.%09d", minutes, seconds, nanos );        
        }        
        
        @Override
        public String adaptString(int index) {
            return formatTime( array[index] );
        }
    }
            
    public CdawebServicesHapiRecordSource(String id, int[] start, int[] stop, String[] params) {
        try {
            String sstart= String.format( "%04d%02d%02dT%02d%02d%02dZ", start[0], start[1], start[2], start[3], start[4], start[5] );
            String sstop= String.format( "%04d%02d%02dT%02d%02d%02dZ", stop[0], stop[1], stop[2], stop[3], stop[4], stop[5] );
            String ss= String.join(",", Arrays.copyOfRange( params, 1, params.length ) ); // CDAWeb WS will send time.
            
            String name= String.format( "%s_%s_%s_%s", id, sstart, sstop, ss );
            String surl=
                    String.format( "https://cdaweb.gsfc.nasa.gov/WS/cdasr/1/dataviews/sp_phys/datasets/%s/data/%s,%s/%s?format=cdf",
                            id, sstart, sstop, ss );
            URL url;
            try {
                url= new URL( surl );
            } catch (MalformedURLException ex) {
                throw new RuntimeException(ex);
            }
            Document doc= SourceUtil.readDocument(url);
            XPathFactory factory = XPathFactory.newInstance();
            XPath xpath = (XPath) factory.newXPath();
            String sval = (String) xpath.evaluate("/DataResult/FileDescription/Name/text()", doc, XPathConstants.STRING);

            URL cdfUrl= new URL(sval);
            
            File tmpFile= File.createTempFile( name, ".cdf" );
            tmpFile= SourceUtil.downloadFile( cdfUrl, tmpFile );
            
            oparams= new Object[params.length];
            sparams= new String[params.length][];
            dparams= new double[params.length][];
            ddparams= new double[params.length][][];
            iparams= new int[params.length][];
            iiparams= new int[params.length][][];
            adapters= new Adapter[params.length];
            
            CDFReader reader= new CDFReader(tmpFile.toString());
            for ( int i=0; i<params.length; i++ ) {
                if ( i==0 ) {
                    String[] deps= reader.getDependent(params[1]);
                    String dep0= deps[0];
                    int type= reader.getType(dep0); // 31=Epoch
                    Object o= reader.get(dep0);
                    oparams[i]= o;
                    if ( Array.getLength(o)>0 ) {
                        if ( type==31 ) {
                            adapters[i]= new IsotimeEpochAdapter( (double[])o );

                        } else if ( type==33 ) {
                            adapters[i]= new IsotimeTT2000Adapter( (long[])o );
                     
                        } else {
                            throw new IllegalArgumentException("type not supported for column 0 time");
                        }
                    }
                    
                } else {
                    oparams[i]= reader.get(params[i]);
                }
                if ( i==0 ) {
                    nindex= Array.getLength(oparams[i]);
                }
            }
            index= 0;
            
        } catch (XPathExpressionException | CDFException.ReaderError | SAXException | IOException | ParserConfigurationException ex) {
            Logger.getLogger(CdawebServicesHapiRecordSource.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    @Override
    public boolean hasNext() {
        return index<nindex;
    }

    @Override
    public HapiRecord next() {
        final int j= index;
        index++;
        return new HapiRecord() {
            @Override
            public String getIsoTime(int i) {
                return adapters[i].adaptString(j);
            }

            @Override
            public String[] getIsoTimeArray(int i) {
                return null;
            }

            @Override
            public String getString(int i) {
                return ((String[])oparams[i])[j];
            }

            @Override
            public String[] getStringArray(int i) {
                return ssparams[i][j];
            }

            @Override
            public double getDouble(int i) {
                return adapters[i].adaptDouble(j);
            }

            @Override
            public double[] getDoubleArray(int i) {
                return ddparams[i][j];
            }

            @Override
            public int getInteger(int i) {
                return iparams[i][j];
            }

            @Override
            public int[] getIntegerArray(int i) {
                return iiparams[i][j];
            }

            @Override
            public String getAsString(int i) {
                return String.valueOf(Array.get(oparams[i],j));
            }

            @Override
            public int length() {
                return oparams.length;
            }
        };
    }
    
    //RBSP-B_DENSITY_EMFISIS-L4
    public static void mainCase2( ) {
//        CdawebServicesHapiRecordSource dd= new CdawebServicesHapiRecordSource( 
//                "AC_H2_SWE", 
//                new int[] { 2021, 3, 12, 0, 0, 0, 0 },
//                new int[] { 2021, 3, 13, 0, 0, 0, 0 }, 
//                new String[] { "Time", "Np", "Vp" } );
        CdawebServicesHapiRecordSource dd= new CdawebServicesHapiRecordSource( 
                "RBSP-B_DENSITY_EMFISIS-L4", 
                new int[] { 2019, 7, 15, 0, 0, 0, 0 },
                new int[] { 2019, 7, 16, 0, 0, 0, 0 }, 
                new String[] { "Time", "fce" } );
        while ( dd.hasNext() ) {
            HapiRecord rec= dd.next();
            System.err.println( rec.getIsoTime(0) );
        }
    }
    
    public static void mainCase1( ) {
//        CdawebServicesHapiRecordSource dd= new CdawebServicesHapiRecordSource( 
//                "AC_H2_SWE", 
//                new int[] { 2021, 3, 12, 0, 0, 0, 0 },
//                new int[] { 2021, 3, 13, 0, 0, 0, 0 }, 
//                new String[] { "Time", "Np", "Vp" } );
        CdawebServicesHapiRecordSource dd= new CdawebServicesHapiRecordSource( 
                "AC_K0_MFI", 
                new int[] { 2023, 4, 26, 0, 0, 0, 0 },
                new int[] { 2023, 4, 27, 0, 0, 0, 0 }, 
                new String[] { "Time", "Magnitude" } );
        while ( dd.hasNext() ) {
            HapiRecord rec= dd.next();
            System.err.println( rec.getIsoTime(0) );
        }
    }
    
    public static void main( String[] args ) {
        //mainCase1();
        mainCase2();
    }
    
}
