        
package org.hapiserver.source.cdaweb;

import gov.nasa.gsfc.spdf.cdfj.CDFException;
import gov.nasa.gsfc.spdf.cdfj.CDFReader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
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
import org.hapiserver.TimeUtil;
import org.hapiserver.source.SourceUtil;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * This uses CDAWeb Web Services described at https://cdaweb.gsfc.nasa.gov/WebServices/REST/.
 * 
 * @author jbf
 */
public class CdawebServicesHapiRecordIterator implements Iterator<HapiRecord> {

    private static final Logger logger= Logger.getLogger("hapi.cdaweb");
    
    static class TimerFormatter extends Formatter {
        long t0= System.currentTimeMillis();
        String resetMessage= "ENTRY";
        
        @Override
        public String format(LogRecord record) {
            if ( record.getMessage().equals(resetMessage) ) {
                t0= record.getMillis();
            }
            String message= MessageFormat.format( record.getMessage(), record.getParameters() );
            return String.format( "%06d: %s\n", record.getMillis()-t0, message );
        }
        
    }
    static {
        //logger.setLevel(Level.FINER);
        //ConsoleHandler h= new ConsoleHandler();
        //h.setFormatter( new TimerFormatter() );
        //h.setLevel(Level.ALL);
        //logger.addHandler(h);
    }
    
    HapiRecord nextRecord;
    Adapter[] adapters;
    
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
        public int adaptInteger( int index ) {
            return Integer.MIN_VALUE;
        }
        public double[] adaptDoubleArray( int index ) {
            return null;
        }
        public int[] adaptIntegerArray( int index ) {
            return null;
        }        
        public String[] adaptStringArray( int index ) {
            return null;
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
        
        String format= ":%02d:%02d.%09d";
        int formatFactor= 1; // number by which to round
        
        private IsotimeEpochAdapter( double[] array, int length ) {
            this.array= array;
            double d= array[0];
            double us2000= ( d - 6.3113904E13 ) * 1000; // ms -> microseconds
            double day2000= Math.floor( us2000 / 86400000000. ); // days since 2000-01-01.
            double usDay= us2000 - day2000 * 86400000000.; // microseconds within this day.
            double ms1970= day2000 * 86400000. + 946684800000.;
            String baseDay= TimeUtil.fromMillisecondsSince1970((long)ms1970);
            baseYYYYmmddTHH= baseDay.substring(0,10)+"T00";
            baseTime= (long)(d-usDay/1000);
            switch ( length ) { // YYYY4hh7mm0HH3MM6SS9NNNNNNNNNZ
                case 24:
                    format=":%02d:%02d.%03dZ";
                    formatFactor= 1000000;
                    break;
                case 27:
                    format=":%02d:%02d.%06dZ";
                    formatFactor= 1000000;
                    break;
                case 30:
                    format=":%02d:%02d.%09dZ";
                    break;
                default:
                    throw new IllegalArgumentException("not supported");
            }
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
            return baseYYYYmmddTHH + String.format( format, minutes, seconds, nanos/formatFactor );
        }        
        
        @Override
        public String adaptString( int index) {
            return formatTime( array[index] );
        }
        
    }
    
    private static class DoubleDoubleAdapter extends Adapter {
        double[] array;
        
        private DoubleDoubleAdapter( double[] array ) {
            this.array= array;
        }
        
        @Override
        public double adaptDouble(int index) {
            return this.array[index];
        }
    }
    
    private static class DoubleArrayDoubleAdapter extends Adapter {
        double[][] array;
        
        private DoubleArrayDoubleAdapter( double[][] array ) {
            this.array= array;
        }
        
        @Override
        public double[] adaptDoubleArray(int index) {
            return this.array[index];
        }
    }
    
    private static class DoubleFloatAdapter extends Adapter {
        float[] array;
        
        private DoubleFloatAdapter( float[] array ) {
            this.array= array;
        }
        
        @Override
        public double adaptDouble(int index) {
            return this.array[index];
        }
    }
    
    private static class IntegerLongAdapter extends Adapter {
        long[] array;
        
        private IntegerLongAdapter( long[] array ) {
            this.array= array;
        }
        
        @Override
        public int adaptInteger( int index ) {
            return (int)this.array[index];
        }
    }
    
    private static class IntegerIntegerAdapter extends Adapter {
        int[] array;
        
        private IntegerIntegerAdapter( int[] array ) {
            this.array= array;
        }
        
        @Override
        public int adaptInteger( int index ) {
            return this.array[index];
        }
    }
    
    private static class IntegerShortAdapter extends Adapter {
        short[] array;
        
        private IntegerShortAdapter( short[] array ) {
            this.array= array;
        }
        
        @Override
        public int adaptInteger( int index ) {
            return this.array[index];
        }
    }
    private static class IntegerByteAdapter extends Adapter {
        byte[] array;
        
        private IntegerByteAdapter( byte[] array ) {
            this.array= array;
        }
        
        @Override
        public int adaptInteger( int index ) {
            return this.array[index];
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
        
        private IsotimeTT2000Adapter( long[] array, int width ) {
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
    
    /**
     * Returns the name of the integer data type, for example, 8 is type
     * 8-byte integer (a.k.a. Java long), and 33 is CDF_TT2000.
     * @param type the code for data type
     * @return a name identifying the type.
     * @see https://spdf.gsfc.nasa.gov/pub/software/cdf/doc/cdf380/cdf38ifd.pdf page 33.
     */
    public static String nameForType(int type) {
        switch (type) {
            case 1:
                return "CDF_INT1";
            case 41:
                return "CDF_BYTE";  // 1-byte signed integer
            case 2:
                return "CDF_INT2"; 
            case 4:
                return "CDF_INT4";
            case 8:
                return "CDF_INT8";
            case 11:
                return "CDF_UINT1";
            case 12:
                return "CDF_UINT2";
            case 14:
                return "CDF_UINT4";
            case 21:
                return "CDF_REAL4";
            case 44:
                return "CDF_FLOAT"; 
            case 22:
                return "CDF_REAL8";
            case 45:
                return "CDF_DOUBLE"; 
            case 31:
                return "CDF_EPOCH";
            case 32:
                return "CDF_EPOCH16";  // make of two CDF_REAL8,
            case 33:
                return "CDF_TT2000";
            case 51:
                return "CDF_CHAR";
            case 52:
                return "CDF_UCHAR";
            default:
                return "???";
        }
    }
    
           
    public CdawebServicesHapiRecordIterator(String id, JSONObject info, int[] start, int[] stop, String[] params) {
        try {
            logger.entering( CdawebServicesHapiRecordIterator.class.getCanonicalName(), "constructor" );
            String sstart= String.format( "%04d%02d%02dT%02d%02d%02dZ", start[0], start[1], start[2], start[3], start[4], start[5] );
            String sstop= String.format( "%04d%02d%02dT%02d%02d%02dZ", stop[0], stop[1], stop[2], stop[3], stop[4], stop[5] );
            String ss= String.join(",", Arrays.copyOfRange( params, 1, params.length ) ); // CDAWeb WS will send time.
            if ( params.length>2 || ( params.length==2 && params[0].equals("Time") ) ) {
                ss= "ALL-VARIABLES";
            }
            
            int iat= id.indexOf("@");  // multiple timetags cdf files will have @\d for each set of timetags.
            if ( iat>0 ) {
                id= id.substring(0,iat);
            }
            
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
            
            logger.log(Level.FINER, "request {0}", url);
                    
            Document doc= SourceUtil.readDocument(url);
            XPathFactory factory = XPathFactory.newInstance();
            XPath xpath = (XPath) factory.newXPath();
            String sval = (String) xpath.evaluate("/DataResult/FileDescription/Name/text()", doc, XPathConstants.STRING);

            URL cdfUrl= new URL(sval);
            logger.log(Level.FINER, "request {0}", cdfUrl);
            
            File p= new File( "/home/tomcat/tmp/" );
            if ( !p.exists() ) {
                if ( !p.mkdirs() ) {
                    logger.warning("fail to make download area");
                }
            }
            
            File tmpFile= new File( "/home/tomcat/tmp/" + name + ".cdf" ); // madness...  apparently tomcat can't write to /tmp
            tmpFile= SourceUtil.downloadFile( cdfUrl, tmpFile );
            
            logger.log(Level.FINER, "downloaded {0}", cdfUrl);
            
            adapters= new Adapter[params.length];
            
            CDFReader reader= new CDFReader(tmpFile.toString());
            for ( int i=0; i<params.length; i++ ) {
                if ( i==0 ) {
                    int length= 24;
                    try {
                        if ( info!=null ) {
                            JSONArray pp = info.getJSONArray("parameters");
                            length= pp.getJSONObject(0).getInt("length");
                        }
                    } catch ( JSONException ex ) {
                        logger.warning("There should always be a length on parameters[0]");
                    }
                    String[] deps= reader.getDependent(params[1]);
                    String dep0= deps[0];
                    int type= reader.getType(dep0); // 31=Epoch
                    Object o= reader.get(dep0);
                    if ( Array.getLength(o)>0 ) {
                        switch (type) {
                            case 31:
                                adapters[i]= new IsotimeEpochAdapter( (double[])o, length );
                                break;
                            case 33:
                                adapters[i]= new IsotimeTT2000Adapter( (long[])o, length );
                                break;
                            default:
                                throw new IllegalArgumentException("type not supported for column 0 time (cdf_epoch16");
                        }
                        nindex= Array.getLength(o);
                    } else {
                        nindex=0;
                    }
                    
                } else {
                    String param= params[i];
                    int type= reader.getType(param);
                    Object o= reader.get(param);
                    String stype= nameForType(type);
                    Class c= o.getClass().getComponentType();
                    if ( !c.isArray() ) {
                        if ( c==double.class ) {
                            adapters[i]= new DoubleDoubleAdapter( (double[])o );
                        } else if ( c==float.class ) {
                            adapters[i]= new DoubleFloatAdapter( (float[])o );
                        } else if ( c==int.class ) {
                            adapters[i]= new IntegerIntegerAdapter( (int[])o );
                        } else if ( c==short.class ) {
                            adapters[i]= new IntegerShortAdapter( (short[])o );
                        } else if ( c==byte.class ) {
                            adapters[i]= new IntegerByteAdapter( (byte[])o );
                        } else if ( c==long.class ) {
                            adapters[i]= new IntegerLongAdapter( (long[])o );
                        } else if ( stype.equals("CDF_UINT2") ) {
                            adapters[i]= new IntegerIntegerAdapter( (int[])o );
                        } else if ( stype.equals("CDF_UINT1") ) {
                            adapters[i]= new IntegerShortAdapter( (short[])o );                        
                        } else {
                            throw new IllegalArgumentException("unsupported type");
                        }
                    } else {
                        c= c.getComponentType();
                        if ( c==double.class ) {
                            adapters[i]= new DoubleArrayDoubleAdapter( (double[][])o );
                        } else {
                            throw new IllegalArgumentException("unsupported type");
                        }
                    }
                }
            }
            
            logger.log(Level.FINER, "calculated adapters" );
            
            index= 0;
            logger.exiting( CdawebServicesHapiRecordIterator.class.getCanonicalName(), "constructor" );
            
        } catch (XPathExpressionException | CDFException.ReaderError | SAXException | IOException | ParserConfigurationException ex) {
            Logger.getLogger(CdawebServicesHapiRecordIterator.class.getName()).log(Level.SEVERE, null, ex);
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
                return adapters[i].adaptString(j);
            }

            @Override
            public String[] getStringArray(int i) {
                return null;
            }

            @Override
            public double getDouble(int i) {
                return adapters[i].adaptDouble(j);
            }

            @Override
            public double[] getDoubleArray(int i) {
                return adapters[i].adaptDoubleArray(j);
            }

            @Override
            public int getInteger(int i) {
                return adapters[i].adaptInteger(i);
            }

            @Override
            public int[] getIntegerArray(int i) {
                return null;
            }

            @Override
            public String getAsString(int i) {
                return null;
            }

            @Override
            public int length() {
                return adapters.length;
            }
        };
    }
    
    //RBSP-B_DENSITY_EMFISIS-L4
    public static void mainCase2( ) {
//        CdawebServicesHapiRecordIterator dd= new CdawebServicesHapiRecordIterator( 
//                "AC_H2_SWE", 
//                new int[] { 2021, 3, 12, 0, 0, 0, 0 },
//                new int[] { 2021, 3, 13, 0, 0, 0, 0 }, 
//                new String[] { "Time", "Np", "Vp" } );
        CdawebServicesHapiRecordIterator dd= new CdawebServicesHapiRecordIterator( 
                "RBSP-B_DENSITY_EMFISIS-L4", 
                null,
                new int[] { 2019, 7, 15, 0, 0, 0, 0 },
                new int[] { 2019, 7, 16, 0, 0, 0, 0 }, 
                new String[] { "Time", "fce", "bmag" } );
        while ( dd.hasNext() ) {
            HapiRecord rec= dd.next();
            System.err.println( String.format( "%s %.2f %.2f", rec.getIsoTime(0), rec.getDouble(1), rec.getDouble(2) ) );
        }
    }
    
    // array-of-array handling
    public static void mainCase3( ) {
        CdawebServicesHapiRecordIterator dd= new CdawebServicesHapiRecordIterator( 
                "AC_K0_MFI", 
                null,
                new int[] { 2023, 4, 26, 0, 0, 0, 0 },
                new int[] { 2023, 4, 27, 0, 0, 0, 0 }, 
                new String[] { "Time", "BGSEc" } );
        while ( dd.hasNext() ) {
            HapiRecord rec= dd.next();
            double[] ds= rec.getDoubleArray(1);
            System.err.println(  String.format( "%s: %.1f %.1f %.1f", rec.getIsoTime(0), ds[0], ds[1], ds[2] ) );
        }
    }
    
    // array-of-array handling
    public static void mainCase4( ) {
        CdawebServicesHapiRecordIterator dd= new CdawebServicesHapiRecordIterator( 
                "VG1_PWS_WF", 
                null,
                new int[] { 1979, 3, 5, 6, 0, 0, 0 },
                new int[] { 1979, 3, 5, 7, 0, 0, 0 }, 
                new String[] { "Time", "Waveform" } );
        while ( dd.hasNext() ) {
            HapiRecord rec= dd.next();
            double[] ds= rec.getDoubleArray(1);
            System.err.println(  String.format( "%s: %.1f %.1f %.1f", rec.getIsoTime(0), ds[0], ds[1], ds[2] ) );
        }
    }
    
    // array-of-array handling
    public static void mainCase5( ) {
        CdawebServicesHapiRecordIterator dd= new CdawebServicesHapiRecordIterator( 
                "AC_H1_SIS", 
                null,
                new int[] { 2023, 4, 6, 0, 0, 0, 0 },
                new int[] { 2023, 4, 7, 0, 0, 0, 0 }, 
                new String[] { "Time", "cnt_Si", "cnt_S" } );
        while ( dd.hasNext() ) {
            HapiRecord rec= dd.next();
            double[] ds1= rec.getDoubleArray(1);
            double[] ds2= rec.getDoubleArray(2);
            System.err.println(  String.format( "%s: %.1f %.1f %.1f ; %.1f %.1f %.1f", rec.getIsoTime(0), ds1[0], ds1[1], ds1[2], ds2[0], ds2[1], ds2[2] ) );
        }
    }
    
    // large request handling
    public static void mainCase6( ) {
        //vap+hapi:http://localhost:8080/HapiServer/hapi?id=AC_H2_CRIS&parameters=Time,flux_B&timerange=2022-12-16+through+2022-12-20
        for ( int iday=16; iday<21; iday++ ) {
            CdawebServicesHapiRecordIterator dd= new CdawebServicesHapiRecordIterator( 
                    "AC_H2_CRIS", 
                    null,
                    new int[] { 2022, 12, iday, 0, 0, 0, 0 },
                    new int[] { 2022, 12, iday+1, 0, 0, 0, 0 }, 
                    "Time,flux_B".split(",",-2) );
            while ( dd.hasNext() ) {
                HapiRecord rec= dd.next();
                //double[] ds1= rec.getDoubleArray(1);
                //System.err.println(  String.format( "%s: %.1e %.1e %.1e %.1e %.1e %.1e %.1e", 
                //        rec.getIsoTime(0), ds1[0], ds1[1], ds1[2], ds1[3], ds1[4], ds1[5], ds1[6] ) );
            }
        }
    }    

    // AC_H2_CRIS gets three months for the sample range.  My measurements and calculations have the extra startup per day as
    // about three seconds, so this means the request will take an extra 270 seconds.
    public static void mainCase7( ) {
        long t0= System.currentTimeMillis();
        //http://localhost:8080/HapiServer/hapi/data?id=AC_H2_CRIS&parameters=flux_C&start=2022-12-14T22:00Z&stop=2023-02-12T23:00Z
        int[] start= new int[] { 2022, 12, 14, 0, 0, 0, 0 };
        int[] stop= new int[] { 2023, 02, 13, 0, 0, 0, 0 };
        while ( TimeUtil.gt( stop, start ) ) {
            int[] next= TimeUtil.add( start, new int[] { 0, 0, 1, 0, 0, 0, 0 } );
            System.err.println( "t: "+ TimeUtil.formatIso8601Time(start) );
            CdawebServicesHapiRecordIterator dd= new CdawebServicesHapiRecordIterator( 
                    "AC_H2_CRIS", 
                    null,
                    start,
                    next,
                    "Time,flux_B".split(",",-2) );
            while ( dd.hasNext() ) {
                HapiRecord rec= dd.next();
                //double[] ds1= rec.getDoubleArray(1);
                //System.err.println(  String.format( "%s: %.1e %.1e %.1e %.1e %.1e %.1e %.1e", 
                //        rec.getIsoTime(0), ds1[0], ds1[1], ds1[2], ds1[3], ds1[4], ds1[5], ds1[6] ) );
            }
            start= next;
        }
        System.err.println("time (sec): "+(System.currentTimeMillis()-t0)/1000. );
    }
    
    // AC_OR_SSC isn't sending anything over for Bob's sample range.
    public static void mainCase8( ) {
        long t0= System.currentTimeMillis();
        //http://localhost:8080/HapiServer/hapi/data?id=AC_H2_CRIS&parameters=flux_C&start=2022-12-14T22:00Z&stop=2023-02-12T23:00Z
        int[] start= new int[] { 2023, 1, 1, 0, 0, 0, 0 };
        int[] stop= new int[] { 2023, 01, 11, 0, 0, 0, 0 };
        while ( TimeUtil.gt( stop, start ) ) {
            int[] next= TimeUtil.add( start, new int[] { 0, 0, 1, 0, 0, 0, 0 } );
            System.err.println( "t: "+ TimeUtil.formatIso8601Time(start) );
            CdawebServicesHapiRecordIterator dd= new CdawebServicesHapiRecordIterator( 
                    "AC_OR_SSC", 
                    null,
                    start,
                    next,
                    "Time,XYZ_GSEO".split(",",-2) );
            int nrec=0;
            while ( dd.hasNext() ) {
                HapiRecord rec= dd.next();
                nrec++;
                //double[] ds1= rec.getDoubleArray(1);
                //System.err.println(  String.format( "%s: %.1e %.1e %.1e %.1e %.1e %.1e %.1e", 
                //        rec.getIsoTime(0), ds1[0], ds1[1], ds1[2], ds1[3], ds1[4], ds1[5], ds1[6] ) );
            }
            System.err.println("  nrec..."+nrec);
            start= next;
        }
        System.err.println("time (sec): "+(System.currentTimeMillis()-t0)/1000. );
    }
        
    
    public static void mainCase1( ) {
//        CdawebServicesHapiRecordIterator dd= new CdawebServicesHapiRecordIterator( 
//                "AC_H2_SWE", 
//                new int[] { 2021, 3, 12, 0, 0, 0, 0 },
//                new int[] { 2021, 3, 13, 0, 0, 0, 0 }, 
//                new String[] { "Time", "Np", "Vp" } );
        CdawebServicesHapiRecordIterator dd= new CdawebServicesHapiRecordIterator( 
                "AC_K0_MFI", 
                null,
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
        //mainCase2();
        //mainCase3();
        //mainCase4();
        //mainCase5();
        //mainCase6();
        //mainCase7();
        mainCase8();
    }
    
}
