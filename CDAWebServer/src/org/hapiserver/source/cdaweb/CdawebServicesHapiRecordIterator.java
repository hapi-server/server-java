package org.hapiserver.source.cdaweb;

import gov.nasa.gsfc.spdf.cdfj.CDFException;
import gov.nasa.gsfc.spdf.cdfj.CDFReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.logging.ConsoleHandler;
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
import org.hapiserver.source.cdaweb.adapters.Add1800;
import org.hapiserver.source.cdaweb.adapters.ApplyEsaQflag;
import org.hapiserver.source.cdaweb.adapters.ApplyFilterFlag;
import org.hapiserver.source.cdaweb.adapters.ApplyRtnQflag;
import org.hapiserver.source.cdaweb.adapters.ArrSlice;
import org.hapiserver.source.cdaweb.adapters.ClampToZero;
import org.hapiserver.source.cdaweb.adapters.CompThemisEpoch;
import org.hapiserver.source.cdaweb.adapters.ConstantAdapter;
import org.hapiserver.source.cdaweb.adapters.ConvertLog10;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Implement the CDAWeb HAPI server by calculating a set of adapters which go from CDF variables
 * in a file to the Strings, ints, and doubles which implement the HapiRecord.  This will download
 * and cache CDF files when the server is running remotely, and will call web services and
 * cache the response when virtual variables must be calculated.  One relatively simple virtual
 * variable is resolved, alternate_view, so that the web services are not needed.
 * 
 * This uses CDAWeb Web Services described at https://cdaweb.gsfc.nasa.gov/WebServices/REST/.
 *
 * @author jbf
 */
public class CdawebServicesHapiRecordIterator implements Iterator<HapiRecord> {

    private static final Logger logger = Logger.getLogger("hapi.cdaweb");

    /**
     * the variable was not found in the CDF, so just return fill values.
     * @param param
     * @param nrec
     * @return 
     */
    private static Object makeFillValues( JSONObject param, int nrec) throws JSONException {
        JSONArray sizej= param.optJSONArray("size");
        int[] size;
        int ndims;
        if ( sizej==null ) {
            size= new int[1];
            ndims=0;
        } else {
            size= new int[1+sizej.length()];
            ndims= sizej.length();
        }
        size[0]= nrec;
        for ( int i=0; i<ndims; i++ ) {
            size[i+1]= sizej.getInt(i);
        }
        switch ( param.getString("type") ) {
            case "double":
            switch (size.length) {
                case 1: {
                    double[] aa= (double[])Array.newInstance( double.class, size );
                    Arrays.fill( aa, Double.NaN );
                    return aa;
                }
                case 2: {
                    double[][] aa= (double[][])Array.newInstance( double.class, size );
                    for ( int i=0; i<nrec; i++ ) {
                        Arrays.fill( aa[i], Double.NaN );
                    }
                    return aa;
                }
                default:
                    throw new IllegalArgumentException("not supported: missing high-dimensional array.");
                    // return Array.newInstance( double.class, size );
            }

                
            case "integer":
                return Array.newInstance( int.class, size );
            case "isotime":
                return Array.newInstance( char.class, size );
            case "string":
                return Array.newInstance( char.class, size );
        }
        throw new IllegalArgumentException("unsupported type: "+param.getString("type"));
    }

    private static void performFuzzyFill(double[] dd, double fill ) {
        for ( int i=0; i<dd.length; i++ ) {
            double d= dd[i];            
            if ( fill!=0 ) {
                double check= d/fill;
                if ( check>0.999999 && check<1.000001 ) {
                    dd[i]= fill;
                }
            }
        }
    }
    
    private static double performFuzzyFill(double d, double fill ) {
        if ( fill!=0 ) {
            double check= d/fill;
            if ( check>0.999999 && check<1.000001 ) {
                return fill;
            }
        }
        return d;
    }
    
    static class TimerFormatter extends Formatter {

        long t0 = System.currentTimeMillis();
        String resetMessage = "ENTRY";

        @Override
        public String format(LogRecord record) {
            if (record.getMessage().equals(resetMessage)) {
                t0 = record.getMillis();
            }
            String message = MessageFormat.format(record.getMessage(), record.getParameters());
            if (message.equals("ENTRY") || message.equals("RETURN")) {
                message = message + " " + record.getSourceClassName() + " " + record.getSourceMethodName();
            }
            return String.format("%06d: %s\n", record.getMillis() - t0, message);
        }

    }

    static {
        logger.setLevel(Level.FINER);
        ConsoleHandler h = new ConsoleHandler();
        h.setFormatter(new TimerFormatter());
        h.setLevel(Level.ALL);
        logger.addHandler(h);
    }

    HapiRecord nextRecord;
    Adapter[] adapters;

    int index;
    int nindex;
    
    private static class StringAdapter extends Adapter {

        String[] array; 
        
        protected StringAdapter( String[] array ) {
            this.array= array;
        }
        
        @Override
        public String adaptString(int index) {
            return this.array[index];
        }

        @Override
        public String getString(int index) {
            return this.array[index];
        }
    
    }
    
    private static String addTime( String baseYYYYmmddTHH, double hours ) {
        int[] dc;
        try {
            dc = TimeUtil.parseISO8601Time(baseYYYYmmddTHH);
            dc= TimeUtil.add( dc, new int[] { 0, 0, 0, (int)hours, 0, 0, 0, 0 } );
            return String.format("%d-%02d-%02dT%02d", dc[0], dc[1], dc[2], dc[3] );
        } catch ( ParseException ex ) {
            throw new RuntimeException(ex);

        }
    }    
    
    public static class IsotimeEpochAdapter extends Adapter {

        /**
         * the time in milliseconds since year 1 for cdfEpoch, and this
         * marks the epoch value of the previous hour boundary.
         */
        double baseTime;

        String baseYYYYmmddTHH;

        double[] array;

        String format = ":%02d:%02d.%09d";
        int formatFactor = 1; // number by which to round

        public IsotimeEpochAdapter(double[] array, int length) {
            this.array = array;
            double d = array[0];
            double us2000 = (d - 6.3113904E13) * 1000; // ms -> microseconds
            double day2000 = Math.floor(us2000 / 86400000000.); // days since 2000-01-01.
            double usDay = us2000 - day2000 * 86400000000.; // microseconds within this day.
            double ms1970 = day2000 * 86400000. + 946684800000.;
            String baseDay = TimeUtil.fromMillisecondsSince1970((long) ms1970);
            baseYYYYmmddTHH = baseDay.substring(0, 10) + "T00";
            baseTime = (long) (d - usDay / 1000);
            switch (length) { // YYYY4hh7mm0HH3MM6SS9NNNNNNNNNZ
                case 24:
                    format = ":%02d:%02d.%03dZ";
                    formatFactor = 1000000;
                    break;
                case 27:
                    format = ":%02d:%02d.%06dZ";
                    formatFactor = 1000000;
                    break;
                case 30:
                    format = ":%02d:%02d.%09dZ";
                    break;
                default:
                    throw new IllegalArgumentException("not supported");
            }
        }
        
        private String formatTime(double t) {
            double offset = t - baseTime;  // milliseconds
            while (offset < 0.) {
                // Not sure why we need this, some sort of miscalculation of baseTime 
                double hours = Math.floor( offset / 3600000. ); 
                baseTime = baseTime + hours * 3600000.;
                baseYYYYmmddTHH= addTime( baseYYYYmmddTHH, hours );
                try {
                    baseYYYYmmddTHH = TimeUtil.normalizeTimeString(baseYYYYmmddTHH).substring(0, 13);
                } catch ( IllegalArgumentException ex ) {
                    System.err.println("Here stop");
                }
                offset = t - baseTime;                
            }
            while (offset >= 3600000.) {
                double hours = Math.floor( offset / 3600000. ); 
                baseTime = baseTime + hours * 3600000.;
                int hour = Integer.parseInt(baseYYYYmmddTHH.substring(11, 13));
                baseYYYYmmddTHH = baseYYYYmmddTHH.substring(0, 11) + String.format("%02d", (int) (hour + hours));
                baseYYYYmmddTHH = TimeUtil.normalizeTimeString(baseYYYYmmddTHH).substring(0, 13);
                offset = t - baseTime;
            }
            int nanos = (int) ((offset * 1000000) % 1000000000.);
            offset = (int) (offset / 1000); // now it's in seconds.  Note offset must be positive for this to work.
            int seconds = (int) (offset % 60);
            int minutes = (int) (offset / 60); // now it's in minutes
            return baseYYYYmmddTHH + String.format(format, minutes, seconds, nanos / formatFactor);
        }

        @Override
        public String adaptString(int index) {
            return formatTime(array[index]);
        }

        @Override
        public String getString(int index) {
            return adaptString(index);
        }

    }
    
    private static class IsotimeEpoch16Adapter extends Adapter {

        /**
         * the time in milliseconds since year 1 for cdfEpoch.
         */
        double baseTime;

        String baseYYYYmmddTHH;

        double[][] array;

        String format = ":%02d:%02d.%09d";
        int formatFactor = 1; // number by which to round
        private IsotimeEpoch16Adapter(double[][] array, int length) {
            this.array = array;
            double d = array[0][0];
            double us2000 = (d - 6.3113904e+10 ) * 1e6; // ms -> microseconds
            double day2000 = Math.floor(us2000 / 86400000000.); // days since 2000-01-01.
            double usDay = us2000 - day2000 * 86400000000.; // microseconds within this day.
            double ms1970 = day2000 * 86400000. + 946684800000.;
            String baseDay = TimeUtil.fromMillisecondsSince1970((long) ms1970);
            baseYYYYmmddTHH = baseDay.substring(0, 10) + "T00";
            baseTime = (long) (d - usDay / 1000);
            switch (length) { // YYYY4hh7mm0HH3MM6SS9NNNNNNNNNZ
                case 24:
                    format = ":%02d:%02d.%03dZ";
                    formatFactor = 1000000;
                    break;
                case 27:
                    format = ":%02d:%02d.%06dZ";
                    formatFactor = 1000000;
                    break;
                case 30:
                    format = ":%02d:%02d.%09dZ";
                    break;
                case 33:
                    format = ":%02d:%02d.%09dZ";
                    break;
                default:
                    throw new IllegalArgumentException("not supported");
            }
        }

        private String formatTime(double t,double t1) {
            double offset = t - baseTime + t1 / 1e6;  // milliseconds
            while (offset < 0.) {
                // Not sure why we need this, some sort of miscalculation of baseTime 
                double hours = Math.floor( offset / 3600000. ); 
                baseTime = baseTime + hours * 3600000.;
                baseYYYYmmddTHH= addTime( baseYYYYmmddTHH, hours );
                try {
                    baseYYYYmmddTHH = TimeUtil.normalizeTimeString(baseYYYYmmddTHH).substring(0, 13);
                } catch ( IllegalArgumentException ex ) {
                    System.err.println("Here stop");
                }
                offset = t - baseTime;                
            }
            while (offset >= 3600000.) {
                double hours = Math.floor( offset / 3600000. ); 
                baseTime = baseTime + hours * 3600000.;
                int hour = Integer.parseInt(baseYYYYmmddTHH.substring(11, 13));
                baseYYYYmmddTHH = baseYYYYmmddTHH.substring(0, 11) + String.format("%02d", (int) (hour + hours));
                baseYYYYmmddTHH = TimeUtil.normalizeTimeString(baseYYYYmmddTHH).substring(0, 13);
                offset = t - baseTime;
            }
            int nanos = (int) ((offset * 1000000) % 1000000000.);
            offset = (int) (offset / 1000); // now it's in seconds.  Note offset must be positive for this to work.
            int seconds = (int) (offset % 60);
            int minutes = (int) (offset / 60); // now it's in minutes
            return baseYYYYmmddTHH + String.format(format, minutes, seconds, nanos / formatFactor);
        }

        @Override
        public String adaptString(int index) {
            return formatTime(array[index][0],array[index][1]);
        }

        @Override
        public String getString(int index) {
            return adaptString(index);
        }

    }
    
    private static class IsotimeTT2000Adapter extends Adapter {

        /**
         * the time in milliseconds since year 1 for cdfEpoch, or nanoseconds for tt2000.
         */
        long baseTime;

        String baseYYYYmmddTHH;

        long[] array;

        private IsotimeTT2000Adapter(long[] array, int width) {
            this.array = array;
            double d = Array.getDouble(array, 0);
            double us2000 = new LeapSecondsConverter(false).convert(d);
            double day2000 = Math.floor(us2000 / 86400000000.); // days since 2000-01-01.
            double usDay = us2000 - day2000 * 86400000000.; // seconds within this day.
            double ms1970 = day2000 * 86400000. + 946684800000.;
            String baseDay = TimeUtil.fromMillisecondsSince1970((long) ms1970);
            baseYYYYmmddTHH = baseDay.substring(0, 10) + "T00";
            baseTime = (long) (d - usDay * 1000);
        }

        private String formatTime(double t) {
            long offset = (long) ((t - baseTime));  // This must not cross a leap second, will always be in nanos
            while (offset < 0.) {
                // Not sure why we need this, some sort of miscalculation of baseTime 
                long hours = Math.floorDiv( offset, 3600000000000L );
                baseTime = baseTime + hours * 3600000000000L;
                baseYYYYmmddTHH= addTime( baseYYYYmmddTHH, hours );
                try {
                    baseYYYYmmddTHH = TimeUtil.normalizeTimeString(baseYYYYmmddTHH).substring(0, 13);
                } catch ( IllegalArgumentException ex ) {
                    System.err.println("Here stop");
                }
                offset = (long)(t - baseTime);
            }
            while (offset >= 3600000000000L) {
                long hours = offset / 3600000000000L;
                baseTime = baseTime + hours * 3600000000000L;
                int hour = Integer.parseInt(baseYYYYmmddTHH.substring(11, 13));
                baseYYYYmmddTHH = baseYYYYmmddTHH.substring(0, 11) + String.format("%02d", (int) (hour + hours));
                baseYYYYmmddTHH = TimeUtil.normalizeTimeString(baseYYYYmmddTHH).substring(0, 13);
                offset = (long) ((t - baseTime));
            }
            int nanos = (int) ((offset) % 1000000000.);
            offset = offset / 1000000000; // now it's in seconds
            int seconds = (int) (offset % 60);
            int minutes = (int) (offset / 60); // now it's in minutes
            return baseYYYYmmddTHH + String.format(":%02d:%02d.%09dZ", minutes, seconds, nanos);
        }

        @Override
        public String adaptString(int index) {
            return formatTime(array[index]);
        }
        
        @Override
        public String getString(int index) {
            return adaptString(index);
        }        
    }
    
    /**
     * Integers come out of the library as doubles.
     * wget -O - 'http://localhost:8080/HapiServer/hapi/data?id=WI_OR_DEF&start=1997-07-01T23:00:00.000Z&stop=1997-07-01T23:50:00.000Z&parameters=Time,CRN_EARTH' 
     */
    private static class IntDoubleAdapter extends Adapter {

        double[] array;
        double fill; // numerical errors mean we need to make fill data canonical
        
        private IntDoubleAdapter(double[] array,double fill) {
            this.fill= fill;
            this.array = array;
        }

        @Override
        public double adaptDouble(int index) {
            if (index >= this.array.length) {
                throw new ArrayIndexOutOfBoundsException("can't find the double at position " + index);
            }
            double d= this.array[index];
            if ( fill!=0 ) {
                double check= d/fill;
                if ( check>0.999999 && check<1.000001 ) {
                    return fill;
                }
            }
            return d;
        }

        @Override
        public int adaptInteger(int index) {
            if (index >= this.array.length) {
                throw new ArrayIndexOutOfBoundsException("can't find the double at position " + index);
            }
            double d= this.array[index];
            d= performFuzzyFill(d, fill);
            return (int)d;
        }

        @Override
        public String getString(int index) {
            return String.valueOf(adaptInteger(index));
        }

    }
    

    private static class DoubleDoubleAdapter extends Adapter {

        double[] array;
        double fill; // numerical errors mean we need to make fill data canonical
        
        private DoubleDoubleAdapter(double[] array,double fill) {
            this.fill= fill;
            this.array = array;
        }

        @Override
        public double adaptDouble(int index) {
            if (index >= this.array.length) {
                throw new ArrayIndexOutOfBoundsException("can't find the double at position " + index);
            }
            double d= this.array[index];
            d= performFuzzyFill(d, fill);
            return d;
        }

        @Override
        public String getString(int index) {
            return String.valueOf(adaptDouble(index));
        }
    }

    private static class DoubleArrayDoubleAdapter extends Adapter {

        double[][] array;
        int n; // there's a weird bit of code where the Java library is giving me double arrays containing ints.
        int items; // number of items in array
        double fill; // fill for extra values

        private DoubleArrayDoubleAdapter(double[][] array, int items,double fill) {
            this.items= items;
            this.array = array;
            if (array.length > 0) {
                this.n = array[0].length;
            }
            this.fill= fill;
        }

        @Override
        public double[] adaptDoubleArray(int index) {
            if ( this.array[index].length==items ) { // TODO: comment where this is not the case
                double[] dd= this.array[index];
                performFuzzyFill( dd, fill );
                return dd;
            } else {
                double[] result= new double[items];
                System.arraycopy( this.array[index], 0, result, 0, n );
                Arrays.fill( result, n, items, fill );
                return result;
            }
        }

        @Override
        public int[] adaptIntegerArray(int index) {
            int[] adapt = new int[n];
            double[] rec = this.array[index];
            for (int i = 0; i < n; i++) {
                adapt[i] = (int) rec[i];
            }
            return adapt;
        }

        @Override
        public String getString(int index) {
            double[] dd= adaptDoubleArray(index);
            if ( dd.length>2 ) {
                return "["+dd[0]+","+dd[1]+",...]";
            } else if ( dd.length==2 ) {
                return "["+dd[0]+","+dd[1]+"]";
            } else {
                return "["+dd[0]+"]";
            }
        }

    }

    private static class DoubleFloatAdapter extends Adapter {

        float[] array;
        double fill;

        private DoubleFloatAdapter(float[] array,double fill) {
            this.array = array;
            this.fill= fill;
        }

        @Override
        public double adaptDouble(int index) {
            double d= this.array[index];
            d= performFuzzyFill( d, fill );
            return d;
        }

        @Override
        public String getString(int index) {
            return String.valueOf(adaptDouble(index));
        }
    }

    private static class IntegerLongAdapter extends Adapter {

        long[] array;

        private IntegerLongAdapter(long[] array) {
            this.array = array;
        }

        @Override
        public int adaptInteger(int index) {
            return (int) this.array[index];
        }

        @Override
        public String getString(int index) {
            return String.valueOf(adaptInteger(index));
        }
    }

    private static class IntegerIntegerAdapter extends Adapter {

        int[] array;

        private IntegerIntegerAdapter(int[] array) {
            this.array = array;
        }

        @Override
        public int adaptInteger(int index) {
            return this.array[index];
        }
        
        @Override
        public String getString(int index) {
            return String.valueOf(adaptInteger(index));
        }        
    }

    private static class IntegerShortAdapter extends Adapter {

        short[] array;

        private IntegerShortAdapter(short[] array) {
            this.array = array;
        }

        @Override
        public int adaptInteger(int index) {
            return this.array[index];
        }
        
        @Override
        public String getString(int index) {
            return String.valueOf(adaptInteger(index));
        }        
    }

    private static class IntegerByteAdapter extends Adapter {

        byte[] array;

        private IntegerByteAdapter(byte[] array) {
            this.array = array;
        }

        @Override
        public int adaptInteger(int index) {
            return this.array[index];
        }
        
        @Override
        public String getString(int index) {
            return String.valueOf(adaptInteger(index));
        }        
    }

    private static class IntegerArrayIntegerAdapter extends Adapter {

        int[][] array;

        private IntegerArrayIntegerAdapter(int[][] array) {
            this.array = array;
        }

        @Override
        public int[] adaptIntegerArray(int index) {
            return this.array[index];
        }
        
        @Override
        public String getString(int index) {
            return String.valueOf(adaptInteger(index));
        }        

    }


    /**
     * Returns the name of the integer data type, for example, 8 is type 8-byte integer (a.k.a. Java long), and 33 is CDF_TT2000.
     *
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

    /**
     * List of datasets which are known to be readable from the files, containing no virtual variables or virtual variables
     * which can be implemented within the HAPI server. Eventually there will be
     * metadata in the infos which contains this information.
     */
    private static final HashSet<String> readDirect = new HashSet<String>();

    static {
        URL virt = CdawebServicesHapiRecordIterator.class.getResource("virtualVariables.txt");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(virt.openStream()))) {
            String line = reader.readLine();
            while (line != null) {
                if (line.length() > 0 && line.charAt(0) == '#') {
                    // skip comment line
                } else {
                    String[] ss = line.split("\t");
                    if (ss[1].equals("0")) {
                        readDirect.add(ss[0]);
                    } else {
                        boolean canImplementVVar= true;
                        if ( ss.length>2 ) {
                            String[] vvars= ss[2].split(",");
                            for ( String v : vvars ) {
                                switch ( v ) {
                                    case "alternate_view":
                                    case "add_1800":
                                    case "apply_esa_qflag":
                                    case "apply_fgm_qflag":
                                    case "apply_gmom_qflag":
                                    //case "apply_filter_flag": // COMPARE_VAL and COMPARE_OPERATOR are missing from metadata.
                                    //case "apply_qflag": // too difficult, old mission
                                    case "comp_themis_epoch":
                                    case "convert_log10":
                                    //case "clamp_to_zero":
                                        break;
                                    default:
                                        canImplementVVar= false;
                                }
                            }
                            if ( canImplementVVar ) {
                                readDirect.add(ss[0]);
                            } else {
                                logger.log(Level.FINE, "must use web services to read {0}", ss[0]);
                            }
                        } else {
                            logger.log(Level.FINE, "strange one... must use web services to read {0}", line);
                        }
                    }
                }
                line = reader.readLine();
            }
            logger.info("read in table of virtual variable use.  TODO: use Bob's database");
        } catch (IOException ex) {
            logger.log(Level.WARNING, ex.getMessage(), ex);
        }
        logger.log(Level.INFO, "readDirect has {0} entries", readDirect.size());
    }

    /**
     * return true if the data contain virtual variables which must be calculated by CDAWeb web services. This is slower than
     * reading the files directly. Some virtual variables may be implemented within this server in the future.
     *
     * @param id the id, for example RBSP-A_DENSITY_EMFISIS-L4
     * @return true if web services must be used.
     */
    private static boolean mustUseWebServices(String id) {
        int iat = id.indexOf("@");  // multiple timetags cdf files will have @\d for each set of timetags.
        if (iat > 0) {
            id = id.substring(0, iat);
        }
        if ( hapiServerResolvesId(id) ) return false;
        if ( id.equals("AC_OR_SSC") ) {
            return true; // uses both rvars and zvars
        }  
        return !readDirect.contains(id);
    }
    
    /**
     * some virtual variables are easily implemented, so we resolve those within the server
     * and data files can be cached.
     * @param id the id, for example "AMPTECCE_H0_MEPA" which has only "alternate_view"
     * @return true if the id can be resolved.
     */
    private static boolean hapiServerResolvesId(String id) {
        if ( id.equals("AMPTECCE_H0_MEPA") ) return true;
        return false;
    }
                
    /**
     * return null or the file which should be used locally.  When the server is at Goddard/CDAWeb, 
     * this is the file in their database.  When running the server remotely, this is a mirror
     * of their data (in /var/www/cdaweb/htdocs/).
     * @param url the file URL
     * @return null or the file.
     */
    private static File getCdfLocalFile( URL url ) {
        if ( url.getHost().equals("cdaweb.gsfc.nasa.gov") && url.getFile().startsWith("/pub/data/") ) {
            File f= new File( "/var/www/cdaweb/htdocs/" + url.getFile() );
            return f;
        }
        if ( url.getHost().equals("cdaweb.gsfc.nasa.gov") && url.getFile().startsWith("/sp_phys/data/") ) {
            File f= new File( "/var/www/cdaweb/htdocs/" + url.getFile() );
            return f;
        }
        return null;
    }
    
    private static String escapeParameters( String s ) {
        try {
            return URLEncoder.encode(s,"US-ASCII");
        } catch ( UnsupportedEncodingException ex ) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * return either the URL of the CDF generated by the web services, or the URL of the CDF file in the https area. Many CDFs
     * contain virtual variables which are only computed within the IDL web services. When a file does not contain virtual variables
     * (or in the future the virtual variable is trivial to compute), then a reference to the direct file is returned.
     *
     * @param id the dataset id, such as AC_OR_SSC or RBSP-A_DENSITY_EMFISIS-L4
     * @param info the info object
     * @param start the seven-component start time
     * @param stop the seven-component stop time
     * @param params the list of parameters to read
     * @param origFile null or the file which contains the original data (pre Bernie's services)
     * @return the URL of the file containing the data.
     */
    private static URL getCdfDownloadURL(String id, JSONObject info, int[] start, int[] stop, String[] params, URL origFile) throws MalformedURLException {
        logger.entering("CdawebServicesHapiRecordIterator", "getCdfDownloadURL");
        String sstart = String.format("%04d%02d%02dT%02d%02d%02dZ", start[0], start[1], start[2], start[3], start[4], start[5]);
        String sstop = String.format("%04d%02d%02dT%02d%02d%02dZ", stop[0], stop[1], stop[2], stop[3], stop[4], stop[5]);

        int iat = id.indexOf("@");  // multiple timetags cdf files will have @\d for each set of timetags.
        if (iat > 0) {
            id = id.substring(0, iat);
        }

        if (origFile == null || mustUseWebServices(id)) {

            String ss;
            if (params.length == 1) {
                try {
                    // special case where we have to request some DATA variable, cannot just request time.
                    JSONArray parameters = info.getJSONArray("parameters");
                    String dependent = parameters.getJSONObject(parameters.length() - 1).getString("name");
                    ss = dependent;
                } catch (JSONException ex) {
                    throw new RuntimeException(ex);
                }
            } else {
                ss = String.join(",", Arrays.copyOfRange(params, 1, params.length)); // CDAWeb WS will send time.
            }
            if (params.length > 2 || (params.length == 2 && !params[0].equals("Time"))) {
                ss = "ALL-VARIABLES";
            }

            String surl
                = String.format("https://cdaweb.gsfc.nasa.gov/WS/cdasr/1/dataviews/sp_phys/datasets/%s/data/%s,%s/%s?format=cdf",
                    id, sstart, sstop, escapeParameters(ss) );

            logger.log(Level.FINER, "request {0}", surl);

            try {
                Document doc = SourceUtil.readDocument(new URL(surl));
                XPathFactory factory = XPathFactory.newInstance();
                XPath xpath = (XPath) factory.newXPath();
                String sval = (String) xpath.evaluate("/DataResult/FileDescription/Name/text()", doc, XPathConstants.STRING);
                logger.exiting("CdawebServicesHapiRecordIterator", "getCdfDownloadURL");
                return new URL(sval);
            } catch (XPathExpressionException | SAXException | IOException | ParserConfigurationException ex) {
                throw new RuntimeException("unable to handle XML response", ex);
            }

        } else {
            logger.exiting("CdawebServicesHapiRecordIterator", "getCdfDownloadURL");
            return origFile;
        }

    }

    /**
     * return the processID (pid), or the fallback if the pid cannot be found.
     *
     * @param fallback the string (null is okay) to return when the pid cannot be found.
     * @return the process id or the fallback provided by the caller. //TODO: Java9 has method for accessing process ID.
     */
    public static String getProcessId(final String fallback) {
        // Note: may fail in some JVM implementations
        // therefore fallback has to be provided

        // something like '<pid>@<hostname>', at least in SUN / Oracle JVMs
        final String jvmName = ManagementFactory.getRuntimeMXBean().getName();
        final int index = jvmName.indexOf('@');

        if (index < 1) {
            // part before '@' empty (index = 0) / '@' not found (index = -1)
            return fallback;
        }

        try {
            return Long.toString(Long.parseLong(jvmName.substring(0, index)));
        } catch (NumberFormatException e) {
            // ignore
        }
        return fallback;
    }

    /**
     * flatten 3-D array into 2-D. Thanks, (Google) Bard!
     * TODO: review--looks like IBEX_H3_ENA_HI_R13_CG_NOSP_RAM_1YR needs transpose
     * @param array
     * @return
     */
    public static double[][] flatten(double[][][] array) {
        int len1 = array[0].length * array[0][0].length;
        double[][] flattenedArray = new double[array.length][len1];
        int index;
        for (int i = 0; i < array.length; i++) {
            index = 0;
            for (int j = 0; j < array[i].length; j++) {
                System.arraycopy(array[i][j], 0, flattenedArray[i], index, array[i][j].length);
                index += array[i][j].length;
            }
        }

        return flattenedArray;
    }
    
    /**
     * flatten 4-D array into 2-D array.  Thanks, ChatGPT!
     * @param input
     * @return 
     */
    public static double[][] flatten4D(double[][][][] input) {
        int dim0 = input.length;
        int dim1 = input[0].length;
        int dim2 = input[0][0].length;
        int dim3 = input[0][0][0].length;

        int cols = dim1 * dim2 * dim3;
        double[][] output = new double[dim0][cols];

        for (int i = 0; i < dim0; i++) {
            for (int j = 0; j < dim1; j++) {
                for (int k = 0; k < dim2; k++) {
                    for (int l = 0; l < dim3; l++) {
                        int colIndex = j * (dim2 * dim3) + k * dim3 + l;
                        output[i][colIndex] = input[i][j][k][l];
                    }
                }
            }
        }

        return output;
    }
 

    private static double[][] flattenDoubleArray(Object array) {
        int numDimensions = 1;
        Class<?> componentType = array.getClass().getComponentType();
        while (componentType != double.class) {
            numDimensions++;
            componentType = componentType.getComponentType();
        }
        switch (numDimensions) {
            case 2:
                return (double[][]) array;
            case 3:
                return flatten((double[][][]) array);
            case 4:
                return flatten4D((double[][][][]) array);
            default:
                throw new IllegalArgumentException("Not supported: rank 4");
        }
    }

    /**
     * limit the lifespan of locally cached copies of data on spot9 and spot10, Jeremy's
     * computers which are used to model the environment at Goddard.  Presently these
     * just limit the file to one hour, but a future implementation may check URL last modified
     * and make better decisions.
     * 
     * @param cdfUrl
     * @param maybeLocalFile
     * @return the file
     */
    private static File checkLocalFileFreshness( URL cdfUrl, File maybeLocalFile ) {
        try {
            InetAddress addr = InetAddress.getLocalHost();
            String hostname= addr.getCanonicalHostName();
            if ( hostname.equals("spot9") || hostname.equals("spot10") ) {
                if ( maybeLocalFile!=null && maybeLocalFile.exists() ) {
                    if ( ( System.currentTimeMillis() - maybeLocalFile.lastModified() ) > 3600000 ) {
                        logger.log(Level.INFO, "Removing stale copy of {0} on {1}", new Object[]{maybeLocalFile.getName(), hostname});
                        maybeLocalFile.delete();
                    }
                }
            }
            return maybeLocalFile;
        } catch (UnknownHostException ex) {
            return maybeLocalFile;
        }
    }
    
    /**
     * return the record iterator for the dataset.This presumes that start and stop are based on the intervals calculated by
     * CdawebServicesHapiRecordSource, and an incomplete set of records will be returned if this is not the case. The file, 
     * possibly calculated when figuring out intervals, can be provided as well, so that the web service identifying the file 
     * is only called once.
     *
     * @param id the dataset id, such as AC_OR_SSC or RBSP-A_DENSITY_EMFISIS-L4
     * @param info the info for this id
     * @param start the start time
     * @param stop the stop time
     * @param params the parameters to read
     * @param origFile the file, (or null if not known), of the data. 
     * @param cacheDir staging area where files can be stored for ~ 1 hour for reuse
     * @return the record iterator.
     */
    public static CdawebServicesHapiRecordIterator create(
        String id, JSONObject info, int[] start, int[] stop, String[] params, URL origFile, File cacheDir) {
        try {

            logger.entering(CdawebServicesHapiRecordIterator.class.getCanonicalName(), "constructor");

            String ss = String.join(",", Arrays.copyOfRange(params, 1, params.length)); // CDAWeb WS will send time.
            if (params.length > 2 || (params.length == 2 && !params[0].equals("Time"))) {
                ss = "ALL-VARIABLES";
            }

            String sstart = String.format("%04d%02d%02dT%02d%02d%02dZ", start[0], start[1], start[2], start[3], start[4], start[5]);
            String sstop = String.format("%04d%02d%02dT%02d%02d%02dZ", stop[0], stop[1], stop[2], stop[3], stop[4], stop[5]);

            String name = String.format("%s_%s_%s_%s", id, sstart, sstop, escapeParameters(ss) );

            String u = System.getProperty("user.name"); // getProcessId("000");
            File p = cacheDir;

            File cdfFile; // this is the file we'll use to read the data, possibly created by Bernie's web services
            
            File tmpFile = new File(p, name + ".cdf"); // madness...  apparently tomcat can't write to /tmp

            if ( tmpFile.exists() && (System.currentTimeMillis() - tmpFile.lastModified()) < ( 3600000 )) {
                logger.fine("no need to download file I already have loaded within the last hour!");
                cdfFile= tmpFile;
                
            } else {
                URL cdfUrl = getCdfDownloadURL(id, info, start, stop, params, origFile); //TODO: must there be a download here?
                logger.log(Level.FINER, "request {0}", cdfUrl);
                
                File maybeLocalFile= getCdfLocalFile( cdfUrl );
                
                if ( maybeLocalFile!=null ) {
                    maybeLocalFile= checkLocalFileFreshness( cdfUrl, maybeLocalFile );
                }
                
                if ( maybeLocalFile!=null && maybeLocalFile.exists() ) {
                    cdfFile= maybeLocalFile;
                    logger.log(Level.FINER, "using local file {0}", cdfFile.toString() );
                } else {
                    logger.log(Level.INFO, "Downloading {0}", cdfUrl);
                    tmpFile = SourceUtil.downloadFileLocking(cdfUrl, tmpFile, tmpFile.toString()+".tmp" );
                    
                    if ( maybeLocalFile!=null && !mustUseWebServices(id) ) {
                        if ( maybeLocalFile.getParentFile().exists() || maybeLocalFile.getParentFile().mkdirs() ) {
                            Files.move( tmpFile.toPath(), maybeLocalFile.toPath() );
                            cdfFile= maybeLocalFile;
                        } else {
                            logger.log(Level.INFO, "unable to mkdir -p {0}", maybeLocalFile.getParentFile());
                            cdfFile= tmpFile;
                        }
                    } else {
                        cdfFile= tmpFile;
                    }
                    logger.log(Level.FINER, "downloaded {0}", cdfUrl);
                }
            }

            return new CdawebServicesHapiRecordIterator(info, start, stop, params, cdfFile);

        } catch (CDFException.ReaderError | JSONException | IOException r ) {
            throw new RuntimeException(r);
        }
    }

    /**
     * CDF files coming from CDAWeb will not contain characters which 
     * are not allowed in IDL tag names.  # was removed from list, see DE1_2SEC_OA "Orbit_#_9"
     * 
     */
    private static String mungeParameterName( String paramName ) {
        //['\','/','.','%','!','@','^','&','*','(',')','-','+','=', $ , '`','~','|','?','<','>',' ']
        String result= paramName.replaceAll("\\/|\\.|\\%|\\!|\\@|\\^|\\&|\\*|\\(|\\)|\\-|\\+|\\=|\\`|\\~|\\?|\\<|\\>|\\ ", "\\$");
        return result;
    }

    /**
     * return true if one of the parameters is virtual.  A virtual parameter is one like "alternate_view" where
     * a different variable is used (with different display, which is not relevant in HAPI), or "apply_esa_qflag"
     * where another variable is used to filter.  While most virtual data is resolved using CDAWeb web
     * services, some are easily implemented here within the HAPI server.
     * @param info
     * @param params
     * @return
     * @throws JSONException 
     */
    private static boolean isVirtual( JSONObject info, String[] params ) throws JSONException {
        JSONArray parameters= info.getJSONArray("parameters");
        for ( String s: params ) {
            JSONObject param= SourceUtil.getParam( info, s );
            if ( param.optBoolean( "x_cdf_VIRTUAL", false ) ) return true;
        }
        return false;
    }
    
    /**
     * return the sizes for the array.  This can be an array of arrays, too.
     * @param o object which is an array.
     * @return the JSONArray of sizes for each dimension.
     */
    private static JSONArray getSizeFor( Object o ) {
        if ( !o.getClass().isArray() ) throw new IllegalArgumentException("Expected array");
        o= Array.get( o, 0 );  // HAPI wants size for each record.
        List<Integer> sizes= new ArrayList<>(); 
        while ( o.getClass().isArray() ) {
            sizes.add( Array.getLength(o) );
            o= Array.get( o, 0 );
        }
        return new JSONArray(sizes);
    }
    
    private static Adapter getAdapterFor( 
            CDFReader reader, 
            JSONObject param1, 
            String param, 
            int nrec ) throws CDFException.ReaderError, JSONException {
                   
        Adapter result;

        // BB_xyz_xyz_sr2__C1_CP_STA_SM is crash
        //if ( param.equals("BB_xyz_xyz_sr2__C1_CP_STA_SM") ) {
        //    System.err.println("stop here");
        //}

        int type = reader.getType(param);
        Object o = reader.get(param);
        if ( o==null || !o.getClass().isArray() ) {
            try {
                o= makeFillValues( param1, nrec );
                //throw new RuntimeException("didn't get array from reader: "+param+" file: "+tmpFile.toString());
            } catch (JSONException ex) {
                throw new RuntimeException(ex);
            }
        }
        if (Array.getLength(o) != nrec) {
            if ( nrec==1 ) { // IBEX_H3_ENA_HI_R13_CG_NOSP_RAM_1YR has one record of 30x60 map
                Object newo= Array.newInstance( o.getClass(), nrec );
                Array.set(newo, 0, o);
                o= newo;
            } else if ( nrec==-1 ) {
                nrec= Array.getLength(o);
            } else {
                if (Array.getLength(o) == 1) {
                    // let's assume they meant for this to non-time varying.
                    if ( nrec==-1 ) {
                        return new ConstantAdapter( Array.getDouble(o,0) );
                    } else {
                        Object newO = Array.newInstance(o.getClass().getComponentType(), nrec);
                        Object v1 = Array.get(o, 0);
                        for (int irec = 0; irec < nrec; irec++) {
                            Array.set(newO, irec, v1);
                        }
                        o = newO;
                    }
                } else {
                    throw new IllegalArgumentException("nrec is inconsistent!  This internal error must be fixed.");
                }
            }
        }
        String stype = nameForType(type);
        Class c = o.getClass().getComponentType();
        double fill= param1==null ? -1e31 : param1.getDouble("fill"); //TODO: I think this is actually a string.
        if (!c.isArray()) {
            if (c == double.class) {
                if ( stype.startsWith("CDF_INT") ) {
                    result = new IntDoubleAdapter((double[]) o,fill);
                } else if ( stype.startsWith("CDF_UINT") ) {
                    result = new IntDoubleAdapter((double[]) o,fill);
                } else {
                    result = new DoubleDoubleAdapter((double[]) o,fill);
                }
            } else if (c == float.class) {
                result = new DoubleFloatAdapter((float[]) o,fill);
            } else if (c == int.class) {
                result = new IntegerIntegerAdapter((int[]) o);
            } else if (c == short.class) {
                result = new IntegerShortAdapter((short[]) o);
            } else if (c == byte.class) {
                result = new IntegerByteAdapter((byte[]) o);
            } else if (c == long.class) {
                result = new IntegerLongAdapter((long[]) o);
            } else if (stype.equals("CDF_UINT2")) {
                result = new IntegerIntegerAdapter((int[]) o);
            } else if (stype.equals("CDF_UINT1")) {
                result = new IntegerShortAdapter((short[]) o);
            } else if ( c == String.class ) {
                result = new StringAdapter((String[])o);
            } else {
                throw new IllegalArgumentException("unsupported type");
            }
        } else {
            c = c.getComponentType();
            if (c == double.class) {
                JSONArray size;
                size= getSizeFor(o);
                int items= size.getInt(0);
                for ( int k=1; k<size.length(); k++ ) {
                    items*= size.getInt(k);
                }
                result = new DoubleArrayDoubleAdapter((double[][]) o,items,fill);
            } else if (c == int.class) {
                result = new IntegerArrayIntegerAdapter((int[][]) o);
            } else if (c.isArray()) {
                JSONArray size= getSizeFor(o);
                int items= size.getInt(0);
                for ( int k=1; k<size.length(); k++ ) {
                    items*= size.getInt(k);
                }
                o = flattenDoubleArray(o);
                result = new DoubleArrayDoubleAdapter((double[][]) o,items,fill);
            } else {
                throw new IllegalArgumentException("unsupported type");
            }
        }
        return result;
    }
    
    /**
     * loop through the parameters returning the parameter with the given name.
     * @param pp the info parameters array.
     * @param name the parameter name, like mms1_fgm_b_gsm_srvy_l2
     * @return null or the parameter.
     * @throws JSONException 
     */
    public static JSONObject getParamFor( JSONArray pp, String name ) throws JSONException {
        for ( int j=0; j<pp.length(); j++ ) {
            JSONObject p= pp.getJSONObject(j);
            if ( p.getString("name").equals(name) ) {
                return p;
            }
        }
        return null;
    }
    
    private static String getAttribute( CDFReader reader, String varName, String attributeName ) throws CDFException.ReaderError {
        Object o= reader.getAttribute( varName, attributeName );
        if ( o==null ) return null;
        if ( o instanceof Vector ) {
            Vector v= (Vector)o;
            if ( v.size()==0 ) return null;
            Object v1= v.get(0);
            if ( v1.getClass().isArray() ) {
                return String.valueOf(Array.get(v1, 0));
            } else {
                return String.valueOf(v1);
            }
        } else {
            return null;
        }
    }
    
    private static Integer getIntegerAttribute( CDFReader reader, String varName, String attributeName ) throws CDFException.ReaderError {
        Object o= reader.getAttribute( varName, attributeName );
        if ( o==null ) return null;
        if ( o instanceof Vector ) {
            Vector v= (Vector)o;
            if ( v.size()==0 ) return null;
            Object v1= v.get(0);
            if ( v1.getClass().isArray() ) {
                if ( v1.getClass().getComponentType()==double.class ) {
                    return (int)Array.getDouble(v1, 0);
                } else {
                    throw new IllegalArgumentException("expected different type");
                }
            } else {
                return ((Number)v.get(0)).intValue();
            }
        } else {
            return null;
        }
    }
    
    /**
     * 
     * @param info info for the dataset
     * @param start start time [Y,m,D,H,M,S,N]
     * @param stop stop time [Y,m,D,H,M,S,N]
     * @param params list of parameters to send
     * @param tmpFile the file returned from NASA/GSFC/CDAWeb web services, or the downloaded file location, or location on the local storage.
     * @throws gov.nasa.gsfc.spdf.cdfj.CDFException.ReaderError
     * @throws JSONException 
     */
    public CdawebServicesHapiRecordIterator(JSONObject info, int[] start, int[] stop, String[] params, File tmpFile) throws CDFException.ReaderError, JSONException {

        if ( tmpFile==null ) {
            throw new NullPointerException("tmpFile is null");
        }

        String[] virtualParams;
        JSONArray[] virtualComponents;
        
        // The virtual variables are implemented.
        if ( isVirtual(info,params) ) { 
            virtualParams= new String[params.length];
            virtualComponents= new JSONArray[params.length];
            for ( int i=0; i<params.length; i++ ) {
                JSONObject param= SourceUtil.getParam( info, params[i] );
                if ( param.optBoolean("x_cdf_VIRTUAL",false) ) {
                    String funct= param.getString("x_cdf_FUNCT");
                    JSONArray components= param.getJSONArray("x_cdf_COMPONENTS");

                    virtualParams[i]= funct;
                    virtualComponents[i]= components;
                    
                } else {
                    virtualParams[i]= null;
                    virtualComponents[i]= null;
                }
            }
        } else {
            virtualParams= null;
            virtualComponents= null;
        }
        
        try {
            adapters = new Adapter[params.length];

            int nrec = -1;

            logger.log(Level.FINE, "opening CDF file {0}", tmpFile);
            CDFReader reader = new CDFReader(tmpFile.toString());
            JSONArray pp;
            try {
                pp = info.getJSONArray("parameters");
            } catch (JSONException ex) {
                throw new RuntimeException("info has wrong form, expecting parameters");
            }
            for (int i = 0; i < params.length; i++) {
                JSONObject param1=null;
                JSONObject p;
                p= pp.getJSONObject( Math.min(i,pp.length() ) );
                if ( p.getString("name").equals(params[i]) ) { // check for "all" response, otherwise this is N^2 code.
                    param1= p;
                } else {
                    param1= getParamFor( pp, params[i] );
                }
                if ( param1==null ) {
                    throw new IllegalArgumentException("didn't find parameter named \""+params[i]+"\"");
                }
                
                if ( virtualParams!=null && virtualParams[i]!=null ) {
                    switch ( virtualParams[i] ) {
                        case "alternate_view": {
                            String param= virtualComponents[i].getString(0);
                            JSONObject param1_1= getParamFor( pp, param );
                            Adapter paramAdapter= getAdapterFor( reader, param1_1, param, nrec );
                            adapters[i]= paramAdapter;
                            continue;
                        }
                        case "apply_esa_qflag": 
                        case "apply_fgm_qflag": 
                        case "apply_gmom_qflag": {
                            String param= virtualComponents[i].getString(0);
                            String flag= virtualComponents[i].getString(1);
                            JSONObject param1_1= getParamFor( pp, param );
                            Adapter paramAdapter= getAdapterFor( reader, param1_1, param, nrec );
                            JSONObject flagParam= getParamFor( pp, flag );
                            Adapter flagAdapter= getAdapterFor( reader, flagParam, flag, nrec );
                            double dfill= param1.getDouble("fill");
                            adapters[i]= new ApplyEsaQflag(paramAdapter, flagAdapter, dfill);
                            continue;
                        }
                        case "apply_rtn_qflag": {
                            String param= virtualComponents[i].getString(0);
                            String flag= virtualComponents[i].getString(1);
                            JSONObject param1_1= getParamFor( pp, param );
                            Adapter dataAdapter= getAdapterFor( reader, param1_1, param, nrec );
                            JSONObject qualityParam= getParamFor( pp, flag );
                            Adapter flagAdapter= getAdapterFor( reader, qualityParam, flag, nrec );
                            double dfill= param1.getDouble("fill");
                            adapters[i]= new ApplyRtnQflag(dataAdapter, flagAdapter, dfill);
                            continue;
                        }                        
                        case "comp_themis_epoch": { //  THG_L1_ASK@8
                            String base= virtualComponents[i].getString(0);
                            String plus= virtualComponents[i].getString(1);
                            JSONObject param1_1= getParamFor( pp, base );
                            Adapter paramAdapter= getAdapterFor( reader, param1_1, base, nrec );
                            JSONObject param1_2= getParamFor( pp, plus );
                            double[] dplus= (double[])reader.get(plus);
                            nrec= dplus.length;
                            nindex = dplus.length;
                            Adapter flagAdapter= getAdapterFor( reader, param1_2, plus, nrec );
                            adapters[i]= new CompThemisEpoch(paramAdapter, flagAdapter);
                            continue;
                        }
                        case "add_1800": {
                            String param= virtualComponents[i].getString(0);
                            double[] epoch=(double[])reader.get(param);
                            Adapter epochAdapter = new IsotimeEpochAdapter( epoch, 30 );
                            nrec= epoch.length;
                            nindex= epoch.length; // Any virtual time will need this!!!!
                            adapters[i]= new Add1800(epochAdapter);
                            continue;
                        }                        
                        case "apply_filter_flag": { //http://localhost:8280/HapiServer/hapi/data?dataset=PSP_SWP_SPC_L3I&parameters=vp_moment_SC_gd&start=2025-05-29T00:00Z&stop=2025-05-30T00:00Z
                            String param= virtualComponents[i].getString(0);
                            String flag= virtualComponents[i].getString(1);
                            JSONObject param1_1= getParamFor( pp, param );
                            Adapter paramAdapter= getAdapterFor( reader, param1_1, param, nrec );
                            JSONObject flagParam= getParamFor( pp, flag );
                            Adapter flagAdapter= getAdapterFor( reader, flagParam, flag, nrec );
                            String vvarName= param1.getString("name");
                            String val= getAttribute( reader, vvarName, "COMPARE_VAL" );
                            if ( val==null ) val= "0.0";
                            String operator= getAttribute( reader, vvarName, "COMPARE_OPERATOR" );
                            if ( operator==null ) operator="eq";
                            adapters[i]= new ApplyFilterFlag( param1, paramAdapter, flagAdapter, operator, Double.parseDouble(val) );
                            continue;
                        }                        
                        case "convert_log10": {
                            String name1= virtualComponents[i].getString(0);
                            JSONObject param1_1= getParamFor( pp, name1 );
                            Adapter paramAdapter= getAdapterFor( reader, param1_1, name1, nrec );
                            adapters[i]= new ConvertLog10(paramAdapter);
                            continue;
                        }
                        case "clamp_to_zero": {
                            // This one is interesting because it uses a variable which is not found in the CDF, FEDU_CORR!
                            String name1= virtualComponents[i].getString(0);
                            JSONObject param1_1= getParamFor( pp, name1 );
                          Adapter paramAdapter= getAdapterFor( reader, param1_1, name1, nrec );
                            String amount= virtualComponents[i].getString(1);
                            adapters[i]= new ClampToZero(paramAdapter,Double.parseDouble(amount));
                            continue;
                        }    
                        case "arr_slice": {
                            String name1= virtualComponents[i].getString(0);
                            JSONObject param1_1= getParamFor( pp, name1 );
                            Adapter paramAdapter= getAdapterFor( reader, param1_1, name1, nrec );
                            String vvarName= param1.getString("name");
                            Integer arrIndex= getIntegerAttribute( reader, vvarName, "ARR_INDEX" );
                            Integer arrDim= getIntegerAttribute( reader, vvarName, "ARR_DIM" );
                            JSONArray size= param1_1.getJSONArray("size");
                            int[] qube= new int[size.length()];
                            for ( int jj=0; jj<size.length(); jj++ ) {
                                qube[jj]= size.getInt(jj);
                            }
                            adapters[i]= new ArrSlice(paramAdapter, qube, arrDim, arrIndex );
                            continue;
                        }
                        default:
                            throw new IllegalArgumentException("not implemented:" + virtualParams[i]);
                    }
                }

                if (i == 0) {
                    int length = param1.optInt("length",24);
                    
                    String dep0= param1.getString("x_cdf_NAME"); 
                    
                    int type = reader.getType(dep0); // 31=Epoch
                    Object o = reader.get(dep0);
                    nrec = Array.getLength(o);
                    if (nrec > 0) {
                        switch (type) {
                            case 31:
                                adapters[i] = new IsotimeEpochAdapter((double[]) o, length);
                                break;
                            case 32:
                                adapters[i] = new IsotimeEpoch16Adapter((double[][]) o, length);
                                break;                                
                            case 33:
                                adapters[i] = new IsotimeTT2000Adapter((long[]) o, length);
                                break;
                            default:
                                //TODO: epoch16.
                                throw new IllegalArgumentException("type not supported for column 0 time: "+ nameForType(type) );
                        }
                        nindex = nrec;
                    } else {
                        nindex = 0;
                    }

                } else {
                    
                    String param = params[i]; 
                    
                    adapters[i]= getAdapterFor( reader, param1, param, nrec );
                    
                }
            }

            logger.log(Level.FINER, "calculated adapters");

            index = 0;
            logger.exiting(CdawebServicesHapiRecordIterator.class.getCanonicalName(), "constructor");

        } catch (CDFException.ReaderError ex) {
            throw new RuntimeException(ex);
        }

    }

    @Override
    public boolean hasNext() {
        return index < nindex;
    }

    @Override
    public HapiRecord next() {
        final int j = index;
        
        index++;
        while ( index<nindex && ( adapters[0].adaptString(j).compareTo(adapters[0].adaptString(index))>=0 ) ) {
            index++; // typically one increment.
        }
        
        if ( index==nindex ) {
            System.err.println("all done");
        }
            
        return new HapiRecord() {
            @Override
            public String getIsoTime(int i) {
                String s= adapters[i].adaptString(j);
                return s;
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
                return adapters[i].adaptInteger(j);
            }

            @Override
            public int[] getIntegerArray(int i) {
                return adapters[i].adaptIntegerArray(j);
            }

            @Override
            public String getAsString(int i) {
                return adapters[i].getString(j);
            }

            @Override
            public int length() {
                return adapters.length;
            }
        };
    }

    //RBSP-B_DENSITY_EMFISIS-L4
    public static void mainCase2() {
//        CdawebServicesHapiRecordIterator dd= new CdawebServicesHapiRecordIterator( 
//                "AC_H2_SWE", 
//                new int[] { 2021, 3, 12, 0, 0, 0, 0 },
//                new int[] { 2021, 3, 13, 0, 0, 0, 0 }, 
//                new String[] { "Time", "Np", "Vp" } );
        CdawebServicesHapiRecordIterator dd = CdawebServicesHapiRecordIterator.create(
            "RBSP-B_DENSITY_EMFISIS-L4",
            null,
            new int[]{2019, 7, 15, 0, 0, 0, 0},
            new int[]{2019, 7, 16, 0, 0, 0, 0},
            new String[]{"Time", "fce", "bmag"}, null, new File("/tmp/hapi-server-cache/") );
        while (dd.hasNext()) {
            HapiRecord rec = dd.next();
            System.err.println(String.format("%s %.2f %.2f", rec.getIsoTime(0), rec.getDouble(1), rec.getDouble(2)));
        }
    }

    // array-of-array handling
    public static void mainCase3() {
        CdawebServicesHapiRecordIterator dd = CdawebServicesHapiRecordIterator.create(
            "AC_K0_MFI",
            null,
            new int[]{2023, 4, 26, 0, 0, 0, 0},
            new int[]{2023, 4, 27, 0, 0, 0, 0},
            new String[]{"Time", "BGSEc"}, null, new File("/tmp/hapi-server-cache/"));
        while (dd.hasNext()) {
            HapiRecord rec = dd.next();
            double[] ds = rec.getDoubleArray(1);
            System.err.println(String.format("%s: %.1f %.1f %.1f", rec.getIsoTime(0), ds[0], ds[1], ds[2]));
        }
    }

    // array-of-array handling
    public static void mainCase4() {
        CdawebServicesHapiRecordIterator dd = CdawebServicesHapiRecordIterator.create(
            "VG1_PWS_WF",
            null,
            new int[]{1979, 3, 5, 6, 0, 0, 0},
            new int[]{1979, 3, 5, 7, 0, 0, 0},
            new String[]{"Time", "Waveform"}, null, new File("/tmp/hapi-server-cache/"));
        while (dd.hasNext()) {
            HapiRecord rec = dd.next();
            double[] ds = rec.getDoubleArray(1);
            System.err.println(String.format("%s: %.1f %.1f %.1f", rec.getIsoTime(0), ds[0], ds[1], ds[2]));
        }
    }

    // array-of-array handling
    public static void mainCase5() {
        CdawebServicesHapiRecordIterator dd = CdawebServicesHapiRecordIterator.create(
            "AC_H1_SIS",
            null,
            new int[]{2023, 4, 6, 0, 0, 0, 0},
            new int[]{2023, 4, 7, 0, 0, 0, 0},
            new String[]{"Time", "cnt_Si", "cnt_S"}, null, new File("/tmp/hapi-server-cache/"));
        while (dd.hasNext()) {
            HapiRecord rec = dd.next();
            double[] ds1 = rec.getDoubleArray(1);
            double[] ds2 = rec.getDoubleArray(2);
            System.err.println(String.format("%s: %.1f %.1f %.1f ; %.1f %.1f %.1f", rec.getIsoTime(0), ds1[0], ds1[1], ds1[2], ds2[0], ds2[1], ds2[2]));
        }
    }

    // large request handling
    public static void mainCase6() {
        //vap+hapi:http://localhost:8080/HapiServer/hapi?id=AC_H2_CRIS&parameters=Time,flux_B&timerange=2022-12-16+through+2022-12-20
        for (int iday = 16; iday < 21; iday++) {
            CdawebServicesHapiRecordIterator dd = CdawebServicesHapiRecordIterator.create(
                "AC_H2_CRIS",
                null,
                new int[]{2022, 12, iday, 0, 0, 0, 0},
                new int[]{2022, 12, iday + 1, 0, 0, 0, 0},
                "Time,flux_B".split(",", -2), null, new File("/tmp/hapi-server-cache/"));
            while (dd.hasNext()) {
                HapiRecord rec = dd.next();
                //double[] ds1= rec.getDoubleArray(1);
                //System.err.println(  String.format( "%s: %.1e %.1e %.1e %.1e %.1e %.1e %.1e", 
                //        rec.getIsoTime(0), ds1[0], ds1[1], ds1[2], ds1[3], ds1[4], ds1[5], ds1[6] ) );
            }
        }
    }

    // AC_H2_CRIS gets three months for the sample range.  My measurements and calculations have the extra startup per day as
    // about three seconds, so this means the request will take an extra 270 seconds.
    public static void mainCase7() {
        long t0 = System.currentTimeMillis();
        //http://localhost:8080/HapiServer/hapi/data?id=AC_H2_CRIS&parameters=flux_C&start=2022-12-14T22:00Z&stop=2023-02-12T23:00Z
        int[] start = new int[]{2022, 12, 14, 0, 0, 0, 0};
        int[] stop = new int[]{2023, 02, 13, 0, 0, 0, 0};
        while (TimeUtil.gt(stop, start)) {
            int[] next = TimeUtil.add(start, new int[]{0, 0, 1, 0, 0, 0, 0});
            System.err.println("t: " + TimeUtil.formatIso8601Time(start));
            CdawebServicesHapiRecordIterator dd = CdawebServicesHapiRecordIterator.create(
                "AC_H2_CRIS",
                null,
                start,
                next,
                "Time,flux_B".split(",", -2), null, new File("/tmp/hapi-server-cache/"));
            while (dd.hasNext()) {
                HapiRecord rec = dd.next();
                //double[] ds1= rec.getDoubleArray(1);
                //System.err.println(  String.format( "%s: %.1e %.1e %.1e %.1e %.1e %.1e %.1e", 
                //        rec.getIsoTime(0), ds1[0], ds1[1], ds1[2], ds1[3], ds1[4], ds1[5], ds1[6] ) );
            }
            start = next;
        }
        System.err.println("time (sec): " + (System.currentTimeMillis() - t0) / 1000.);
    }

    // AC_OR_SSC isn't sending anything over for Bob's sample range.
    // AC_OR_SSC should format using x_format.
    public static void mainCase8() {
        long t0 = System.currentTimeMillis();
        //http://localhost:8080/HapiServer/hapi/data?id=AC_H2_CRIS&parameters=flux_C&start=2022-12-14T22:00Z&stop=2023-02-12T23:00Z
        int[] start = new int[]{2023, 1, 1, 0, 0, 0, 0};
        int[] stop = new int[]{2023, 01, 11, 0, 0, 0, 0};
        while (TimeUtil.gt(stop, start)) {
            int[] next = TimeUtil.add(start, new int[]{0, 0, 1, 0, 0, 0, 0});
            System.err.println("t: " + TimeUtil.formatIso8601Time(start));
            CdawebServicesHapiRecordIterator dd = CdawebServicesHapiRecordIterator.create(
                "AC_OR_SSC",
                null,
                start,
                next,
                "Time,XYZ_GSEO".split(",", -2), null, new File("/tmp/hapi-server-cache/"));
            int nrec = 0;
            while (dd.hasNext()) {
                HapiRecord rec = dd.next();
                nrec++;
                //double[] ds1= rec.getDoubleArray(1);
                //System.err.println(  String.format( "%s: %.1e %.1e %.1e %.1e %.1e %.1e %.1e", 
                //        rec.getIsoTime(0), ds1[0], ds1[1], ds1[2], ds1[3], ds1[4], ds1[5], ds1[6] ) );
            }
            System.err.println("  nrec..." + nrec);
            start = next;
        }
        System.err.println("time (sec): " + (System.currentTimeMillis() - t0) / 1000.);
    }

    /**
     * ICON_L2-5_FUV_NIGHT has channels which change size with each file.  The info says there should
     * be 129,6 but the file might only have 127,6.
     */
    public static void mainCase10() throws IOException, JSONException {
        long t0 = System.currentTimeMillis();
        //http://localhost:8080/HapiServer/hapi/data?id=ICON_L2-5_FUV_NIGHT&parameters=ICON_L25_O_Plus_Density&start=2022-11-23T00:54:54Z&stop=2022-11-23T23:58:38Z
        int[] start = new int[]{2022, 11, 23, 0, 54, 54, 0};
        int[] stop = new int[]{2022, 11, 23, 23, 58, 38, 0};
        
        JSONObject info= new JSONObject( 
                CdawebInfoCatalogSource.getInfo(
                        "https://cottagesystems.com/~jbf/hapi/p/cdaweb/orig_data/info/ICON_L2-5_FUV_NIGHT.json",
                        "https://cottagesystems.com/~jbf/hapi/p/cdaweb/data/info/ICON_L2-5_FUV_NIGHT.json" ) );
        while (TimeUtil.gt(stop, start)) {
            int[] next = TimeUtil.add(start, new int[]{0, 0, 1, 0, 0, 0, 0});
            System.err.println("t: " + TimeUtil.formatIso8601Time(start));
            CdawebServicesHapiRecordIterator dd = CdawebServicesHapiRecordIterator.create(
                "ICON_L2-5_FUV_NIGHT",
                info,
                start,
                next,
                "Time,ICON_L25_O_Plus_Density".split(",", -2), null, new File("/tmp/hapi-server-cache/"));
            int nrec = 0;
            
            double lastT=0;
            int irec=0;
            while (dd.hasNext()) {
                HapiRecord rec = dd.next();
                nrec++;
                double[] rec1= rec.getDoubleArray(1);
                if ( ( TimeUtil.toMillisecondsSince1970(rec.getIsoTime(0))-lastT ) > 30000 ) {
                    System.err.print(  String.format( "%4d %s: ", irec, rec.getIsoTime(0) ) );
                    for ( int i=0; i<Math.min(5,rec1.length); i++ ) {
                        System.err.print( (i>0?",":"")+String.format("%15.3e",rec1[i]) );
                    }
                    System.err.println();
                }
                irec=irec+1;
                lastT= TimeUtil.toMillisecondsSince1970(rec.getIsoTime(0));
                if ( irec>575 ) System.exit(0);
            }
            System.err.println("  nrec..." + nrec);
            start = next;
        }
        System.err.println("time (sec): " + (System.currentTimeMillis() - t0) / 1000.);
    }
    
    /**
     * Virtual variable OMNI2_H0_MRG1HR&parameters=SIGMA-ABS_B1800 doesn't load with Nand's.
     */
    public static void mainCase9() {
        long t0 = System.currentTimeMillis();
        //http://localhost:8080/HapiServer/hapi/data?id=OMNI2_H0_MRG1HR&parameters=SIGMA-ABS_B1800&start=1979-03-03T00:00Z&stop=1979-03-04T00:00Z
        int[] start = new int[]{2024, 1, 4, 0, 0, 0, 0};
        int[] stop = new int[]{2024, 1, 5, 0, 0, 0, 0};
        while (TimeUtil.gt(stop, start)) {
            int[] next = TimeUtil.add(start, new int[]{0, 0, 1, 0, 0, 0, 0});
            System.err.println("t: " + TimeUtil.formatIso8601Time(start));
            CdawebServicesHapiRecordIterator dd = CdawebServicesHapiRecordIterator.create(
                "OMNI2_H0_MRG1HR",
                null,
                start,
                next,
                "Time,ABS_B1800".split(",", -2), null, new File("/tmp/hapi-server-cache/"));
            int nrec = 0;
            while (dd.hasNext()) {
                HapiRecord rec = dd.next();
                nrec++;
                System.err.println(  String.format( "%s: %.1e", rec.getIsoTime(0), rec.getDouble(1) ) );
            }
            System.err.println("  nrec..." + nrec);
            start = next;
        }
        System.err.println("time (sec): " + (System.currentTimeMillis() - t0) / 1000.);
    }

    public static void mainCase1() {
//        CdawebServicesHapiRecordIterator dd= new CdawebServicesHapiRecordIterator( 
//                "AC_H2_SWE", 
//                new int[] { 2021, 3, 12, 0, 0, 0, 0 },
//                new int[] { 2021, 3, 13, 0, 0, 0, 0 }, 
//                new String[] { "Time", "Np", "Vp" } );
        CdawebServicesHapiRecordIterator dd = CdawebServicesHapiRecordIterator.create(
            "AC_K0_MFI",
            null,
            new int[]{2023, 4, 26, 0, 0, 0, 0},
            new int[]{2023, 4, 27, 0, 0, 0, 0},
            new String[]{"Time", "Magnitude"}, null, new File("/tmp/hapi-server-cache/"));
        while (dd.hasNext()) {
            HapiRecord rec = dd.next();
            System.err.println(rec.getIsoTime(0));
        }
    }

    public static void mainCase11() {
//        CdawebServicesHapiRecordIterator dd= new CdawebServicesHapiRecordIterator( 
//                "AC_H2_SWE", 
//                new int[] { 2021, 3, 12, 0, 0, 0, 0 },
//                new int[] { 2021, 3, 13, 0, 0, 0, 0 }, 
//                new String[] { "Time", "Np", "Vp" } );
      // http://localhost:8280/HapiServer/hapi/data?dataset=AC_OR_SSC&start=2023-01-01T00:00Z&stop=2024-01-01T00:00Z
        CdawebServicesHapiRecordIterator dd = CdawebServicesHapiRecordIterator.create(
            "AC_OR_SSC",
            null,
            new int[]{2023,01,01,00,00,0,0},
            new int[]{2024,01,01,00,00,0,0},
            new String[]{"Time", "Radius"}, null, new File("/tmp/hapi-server-cache/"));
        while (dd.hasNext()) {
            HapiRecord rec = dd.next();
            System.err.println(rec.getIsoTime(0));
        }
    }
        
    /**
     * see if we can implement alt_view and Themis quality without web services
     * @throws java.io.IOException
     * @throws org.codehaus.jettison.json.JSONException
     */
    public static void mainCase12() throws IOException, JSONException {
        // http://localhost:8280/HapiServer/hapi/data?dataset=AC_OR_SSC&start=2023-01-01T00:00Z&stop=2024-01-01T00:00Z
        String id= "AMPTECCE_H0_MEPA@0";
        String urlorig= "file:/net/spot10/hd1_8t/home/weigel/cdawmeta/data/orig_data/info/"+id+".json";
        String surl= "file:/net/spot10/hd1_8t/home/weigel/cdawmeta/data/hapi/info/"+id+".json";
        
        JSONObject info = new JSONObject( CdawebInfoCatalogSource.getInfo(urlorig, surl) );
        CdawebServicesHapiRecordIterator dd = CdawebServicesHapiRecordIterator.create(
            id,
            info,
            new int[]{1988,12,22,0,0,0,0},
            new int[]{1988,12,22,16,19,0,0},
            new String[]{"Time", "ION_protons_COUNTS_stack"}, null, new File("/tmp/hapi-server-cache/"));
        while (dd.hasNext()) {
            HapiRecord rec = dd.next();
            System.err.println(rec.getIsoTime(0)+","+rec.getAsString(1));
        }
    }
        
    public static void main(String[] args) throws Exception {
        //mainCase1();
        //mainCase2();
        //mainCase3();
        //mainCase4();
        //mainCase5();
        //mainCase6();
        //mainCase7();
        //mainCase8();
        mainCase9();
        //mainCase10();
        //mainCase11();
        //mainCase12();
    }

}
