package org.hapiserver.source.cdaweb;

import gov.nasa.gsfc.spdf.cdfj.CDFException;
import gov.nasa.gsfc.spdf.cdfj.CDFReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
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
    private Object makeFillValues( JSONObject param, int nrec) throws JSONException {
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

                
            case "int":
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

    /**
     * one of these methods will be implemented by the adapter.
     */
    private static abstract class Adapter {

        public String adaptString(int index) {
            throw new IllegalArgumentException("incorrect adapter used");
        }

        public double adaptDouble(int index) {
            throw new IllegalArgumentException("incorrect adapter used");
        }

        public int adaptInteger(int index) {
            throw new IllegalArgumentException("incorrect adapter used");
        }

        public double[] adaptDoubleArray(int index) {
            throw new IllegalArgumentException("incorrect adapter used");
        }

        public int[] adaptIntegerArray(int index) {
            throw new IllegalArgumentException("incorrect adapter used");
        }

        public String[] adaptStringArray(int index) {
            throw new IllegalArgumentException("incorrect adapter used");
        }
    }
    private static class StringAdapter extends Adapter {

        String[] array; 
        
        protected StringAdapter( String[] array ) {
            this.array= array;
        }
        
        @Override
        public String adaptString(int index) {
            return this.array[index];
        }
    
    }
    
    private static class IsotimeEpochAdapter extends Adapter {

        int julianDay;
        long cdfTT2000 = Long.MAX_VALUE;

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

        String format = ":%02d:%02d.%09d";
        int formatFactor = 1; // number by which to round

        private IsotimeEpochAdapter(double[] array, int length) {
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

    }

    private static class IsotimeTT2000Adapter extends Adapter {

        int julianDay;
        long cdfTT2000 = Long.MAX_VALUE;
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
            while (offset >= 3600000000000L) {
                double hours = offset / 3600000000000L;
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
     * List of datasets which are known to be readable from the files, containing no virtual variables. Eventually there will be
     * metadata in the infos which contains this information.
     */
    private static final HashSet<String> readDirect = new HashSet<String>();

    static {
        //readDirect.add("RBSP-A_DENSITY_EMFISIS-L4");
        //readDirect.add("RBSP-B_DENSITY_EMFISIS-L4");
        //readDirect.add("RBSP-A_MAGNETOMETER_4SEC-GEI_EMFISIS-L3");
        //readDirect.add("RBSP-B_MAGNETOMETER_4SEC-GEI_EMFISIS-L3");
        //readDirect.add("RBSPA_REL04_ECT-HOPE-PA-L3");
        //readDirect.add("RBSPB_REL04_ECT-HOPE-PA-L3");
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
                        System.err.println("virtual variable: "+ss[0]);
                    }
                }
                line = reader.readLine();
            }
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
        return !readDirect.contains(id);
    }

    /**
     * return null or the file if cached locally, or if the backend of the https server is available (at Goddard).
     * @param url the file URL
     * @return null or the file.
     */
    private static File getCdfLocalFile( URL url ) {
        if ( url.getHost().equals("cdaweb.gsfc.nasa.gov") && url.getFile().startsWith("/pub/data/") ) {
            File f= new File( "/var/www/cdaweb/htdocs/" + url.getFile() );
            return f;
        }
        return null;
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
     * @param file null or the file which contains the data
     * @return the URL of the file containing the data.
     */
    private static URL getCdfDownloadURL(String id, JSONObject info, int[] start, int[] stop, String[] params, String file) throws MalformedURLException {
        logger.entering("CdawebServicesHapiRecordIterator", "getCdfDownloadURL");
        String sstart = String.format("%04d%02d%02dT%02d%02d%02dZ", start[0], start[1], start[2], start[3], start[4], start[5]);
        String sstop = String.format("%04d%02d%02dT%02d%02d%02dZ", stop[0], stop[1], stop[2], stop[3], stop[4], stop[5]);

        int iat = id.indexOf("@");  // multiple timetags cdf files will have @\d for each set of timetags.
        if (iat > 0) {
            id = id.substring(0, iat);
        }

        if (file == null || mustUseWebServices(id)) {

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
                    id, sstart, sstop, ss);

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
            return new URL(file);
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
     * flatten 3-D array into 2-D. Thanks, Bard!
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

    private double[][] flattenDoubleArray(Object array) {
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
            default:
                throw new IllegalArgumentException("Not supported: rank 4");
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
     * @param file the file, (or null if not known), of the data. (or URL)
     * @return the record iterator.
     */
    public static CdawebServicesHapiRecordIterator create(
        String id, JSONObject info, int[] start, int[] stop, String[] params, String file) {
        try {

            logger.entering(CdawebServicesHapiRecordIterator.class.getCanonicalName(), "constructor");

            String ss = String.join(",", Arrays.copyOfRange(params, 1, params.length)); // CDAWeb WS will send time.
            if (params.length > 2 || (params.length == 2 && !params[0].equals("Time"))) {
                ss = "ALL-VARIABLES";
            }

            String sstart = String.format("%04d%02d%02dT%02d%02d%02dZ", start[0], start[1], start[2], start[3], start[4], start[5]);
            String sstop = String.format("%04d%02d%02dT%02d%02d%02dZ", stop[0], stop[1], stop[2], stop[3], stop[4], stop[5]);

            String name = String.format("%s_%s_%s_%s", id, sstart, sstop, ss);

            String u = System.getProperty("user.name"); // getProcessId("000");
            File p = new File("/home/tomcat/tmp/" + u + "/");

            if (!p.exists()) {
                if (!p.mkdirs()) {
                    logger.warning("fail to make download area");
                }
            }

            File tmpFile = new File(p, name + ".cdf"); // madness...  apparently tomcat can't write to /tmp

            if ( tmpFile.exists() && (System.currentTimeMillis() - tmpFile.lastModified()) < (5 * 86400000)) {
                logger.fine("no need to download file I already have loaded within the last 5 days!");
                file= tmpFile.toString();
            } else {
                URL cdfUrl = getCdfDownloadURL(id, info, start, stop, params, file);
                logger.log(Level.FINER, "request {0}", cdfUrl);
                
                File maybeLocalFile= getCdfLocalFile( cdfUrl );
                if ( maybeLocalFile!=null && maybeLocalFile.exists() ) {
                    file= maybeLocalFile.toString();
                    logger.log(Level.FINER, "using local file {0}", file);
                } else {
                    tmpFile = SourceUtil.downloadFile(cdfUrl, tmpFile);
                    if ( maybeLocalFile!=null && !mustUseWebServices(id) ) {
                        if ( maybeLocalFile.getParentFile().mkdirs() ) {
                            Files.copy( tmpFile.toPath(), maybeLocalFile.toPath() );
                            file= maybeLocalFile.toString();
                        } else {
                            file= tmpFile.toString();
                        }
                    } else {
                        file= tmpFile.toString();
                    }
                    logger.log(Level.FINER, "downloaded {0}", cdfUrl);
                }
            }

            return new CdawebServicesHapiRecordIterator(info, start, stop, params, file);

        } catch (CDFException.ReaderError | JSONException | IOException r ) {
            throw new RuntimeException(r);
        }
    }

    /**
     * CDF files coming from CDAWeb will not contain characters which 
     * are not allowed in IDL tag names.
     */
    private static String mungeParameterName( String paramName ) {
        //['\','/','.','%','!','@','#','^','&','*','(',')','-','+','=', $ , '`','~','|','?','<','>',' ']
        String result= paramName.replaceAll("\\/|\\.|\\%|\\!|\\@|\\#|\\^|\\&|\\*|\\(|\\)|\\-|\\+|\\=|\\`|\\~|\\?|\\<|\\>|\\ ", "\\$");
        return result;
    }
    
    public CdawebServicesHapiRecordIterator(JSONObject info, int[] start, int[] stop, String[] params, String tmpFile) throws CDFException.ReaderError, JSONException {

        if ( tmpFile==null ) {
            throw new NullPointerException("tmpFile is null");
        }
        
        try {
            adapters = new Adapter[params.length];

            int nrec = -1;

            logger.log(Level.FINE, "opening CDF file {0}", tmpFile);
            CDFReader reader = new CDFReader(tmpFile);
            for (int i = 0; i < params.length; i++) {
                JSONArray pp;
                JSONObject param1=null;
                try {
                    pp = info.getJSONArray("parameters");
                    for ( int j=0; j<pp.length(); j++ ) {
                        JSONObject p= pp.getJSONObject(j);
                        if ( p.getString("name").equals(params[i]) ) {
                            param1= p;
                        }
                    }
                    if ( param1==null ) {
                        throw new IllegalArgumentException("didn't find parameter named \""+params[i]+"\"");
                    }
                } catch (JSONException ex) {
                    throw new RuntimeException("info has wrong form, expecting parameters");
                }
                if (i == 0) {
                    int length = param1.optInt("length",24);
                    
                    String dep0;
                    if (params.length == 1) {
                        try {
                            // this is stupid, why must we rename it Time and then have to figure out the original name again?
                            JSONArray parameters = info.getJSONArray("parameters");
                            String dependent = parameters.getJSONObject(parameters.length() - 1).getString("name");
                            String[] deps = reader.getDependent(dependent);
                            if ( deps.length==0 ) {
                                //throw new IllegalArgumentException("data has no dep 0");
                                String[] ss= reader.getVariableNames();
                                List<String> candidates= new ArrayList<>();
                                for ( String s: ss ) {
                                    if ( s.toLowerCase().endsWith("epoch") ) {
                                        candidates.add(s);
                                    }
                                }
                                if ( candidates.size()==1 ) {
                                    deps= candidates.toArray( new String[candidates.size()] );
                                }
                            }
                            dep0 = deps[0];
                        } catch (JSONException ex) {
                            throw new RuntimeException(ex);
                        }
                    } else {
                        String p= mungeParameterName(params[1]);
                        String[] deps = reader.getDependent(p);
                        if ( deps.length==0 ) {
                            throw new IllegalArgumentException("unable to find dependences for "+p+" in "+tmpFile);
                        }
                        dep0 = deps[0];
                    }
                    int type = reader.getType(dep0); // 31=Epoch
                    Object o = reader.get(dep0);
                    nrec = Array.getLength(o);
                    if (nrec > 0) {
                        switch (type) {
                            case 31:
                                adapters[i] = new IsotimeEpochAdapter((double[]) o, length);
                                break;
                            case 33:
                                adapters[i] = new IsotimeTT2000Adapter((long[]) o, length);
                                break;
                            default:
                                //TODO: epoch16.
                                throw new IllegalArgumentException("type not supported for column 0 time (cdf_epoch16");
                        }
                        nindex = Array.getLength(o);
                    } else {
                        nindex = 0;
                    }

                } else {
                    
                    String param = mungeParameterName(params[i]);
                    int type = reader.getType(param);
                    Object o = reader.get(param);
                    if ( o==null || !o.getClass().isArray() ) {
                        try {
                            o= makeFillValues( info.getJSONArray("parameters").getJSONObject(i), nrec );
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
                        } else {
                            if (Array.getLength(o) == 1) {
                                // let's assume they meant for this to non-time varying.
                                Object newO = Array.newInstance(o.getClass().getComponentType(), nrec);
                                Object v1 = Array.get(o, 0);
                                for (int irec = 0; irec < nrec; irec++) {
                                    Array.set(newO, irec, v1);
                                }
                                o = newO;
                            } else {
                                throw new IllegalArgumentException("nrec is inconsistent!  This internal error must be fixed.");
                            }
                        }
                    }
                    String stype = nameForType(type);
                    Class c = o.getClass().getComponentType();
                    double fill= param1.getDouble("fill"); //TODO: I think this is actually a string.
                    if (!c.isArray()) {
                        if (c == double.class) {
                            if ( stype.startsWith("CDF_INT") ) {
                                adapters[i] = new IntDoubleAdapter((double[]) o,fill);
                            } else if ( stype.startsWith("CDF_UINT") ) {
                                adapters[i] = new IntDoubleAdapter((double[]) o,fill);
                            } else {
                                adapters[i] = new DoubleDoubleAdapter((double[]) o,fill);
                            }
                        } else if (c == float.class) {
                            adapters[i] = new DoubleFloatAdapter((float[]) o,fill);
                        } else if (c == int.class) {
                            adapters[i] = new IntegerIntegerAdapter((int[]) o);
                        } else if (c == short.class) {
                            adapters[i] = new IntegerShortAdapter((short[]) o);
                        } else if (c == byte.class) {
                            adapters[i] = new IntegerByteAdapter((byte[]) o);
                        } else if (c == long.class) {
                            adapters[i] = new IntegerLongAdapter((long[]) o);
                        } else if (stype.equals("CDF_UINT2")) {
                            adapters[i] = new IntegerIntegerAdapter((int[]) o);
                        } else if (stype.equals("CDF_UINT1")) {
                            adapters[i] = new IntegerShortAdapter((short[]) o);
                        } else if ( c == String.class ) {
                            adapters[i] = new StringAdapter((String[])o);
                        } else {
                            throw new IllegalArgumentException("unsupported type");
                        }
                    } else {
                        c = c.getComponentType();
                        if (c == double.class) {
                            JSONArray size= param1.getJSONArray("size");
                            int items= size.getInt(0);
                            for ( int k=1; k<size.length(); k++ ) {
                                items*= size.getInt(k);
                            }
                            adapters[i] = new DoubleArrayDoubleAdapter((double[][]) o,items,fill);
                        } else if (c == int.class) {
                            adapters[i] = new IntegerArrayIntegerAdapter((int[][]) o);
                        } else if (c.isArray()) {
                            JSONArray size= param1.getJSONArray("size");
                            int items= size.getInt(0);
                            for ( int k=1; k<size.length(); k++ ) {
                                items*= size.getInt(k);
                            }
                            o = flattenDoubleArray(o);
                            adapters[i] = new DoubleArrayDoubleAdapter((double[][]) o,items,fill);
                        } else {
                            throw new IllegalArgumentException("unsupported type");
                        }
                    }
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
                return adapters[i].adaptInteger(j);
            }

            @Override
            public int[] getIntegerArray(int i) {
                return adapters[i].adaptIntegerArray(j);
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
            new String[]{"Time", "fce", "bmag"}, null);
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
            new String[]{"Time", "BGSEc"}, null);
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
            new String[]{"Time", "Waveform"}, null);
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
            new String[]{"Time", "cnt_Si", "cnt_S"}, null);
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
                "Time,flux_B".split(",", -2), null);
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
                "Time,flux_B".split(",", -2), null);
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
                "Time,XYZ_GSEO".split(",", -2), null);
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
        
        JSONObject info= new JSONObject( CdawebInfoCatalogSource.getInfo("ICON_L2-5_FUV_NIGHT", "bw") );
        while (TimeUtil.gt(stop, start)) {
            int[] next = TimeUtil.add(start, new int[]{0, 0, 1, 0, 0, 0, 0});
            System.err.println("t: " + TimeUtil.formatIso8601Time(start));
            CdawebServicesHapiRecordIterator dd = CdawebServicesHapiRecordIterator.create(
                "ICON_L2-5_FUV_NIGHT",
                info,
                start,
                next,
                "Time,ICON_L25_O_Plus_Density".split(",", -2), null);
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
                "Time,ABS_B1800".split(",", -2), null);
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
            new String[]{"Time", "Magnitude"}, null);
        while (dd.hasNext()) {
            HapiRecord rec = dd.next();
            System.err.println(rec.getIsoTime(0));
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
        mainCase8();
        //mainCase9();
        //mainCase10();
    }

}