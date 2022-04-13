
package org.hapiserver.sscweb;

import gov.nasa.gsfc.sscweb.schema.BFieldModel;
import gov.nasa.gsfc.sscweb.schema.BFieldTraceOptions;
import gov.nasa.gsfc.sscweb.schema.CoordinateComponent;
import gov.nasa.gsfc.sscweb.schema.CoordinateData;
import gov.nasa.gsfc.sscweb.schema.CoordinateSystem;
import gov.nasa.gsfc.sscweb.schema.DataRequest;
import gov.nasa.gsfc.sscweb.schema.DataResult;
import gov.nasa.gsfc.sscweb.schema.DistanceFromOptions;
import gov.nasa.gsfc.sscweb.schema.FilteredCoordinateOptions;
import gov.nasa.gsfc.sscweb.schema.FootpointRegion;
import gov.nasa.gsfc.sscweb.schema.Hemisphere;
import gov.nasa.gsfc.sscweb.schema.HemisphereOptions;
import gov.nasa.gsfc.sscweb.schema.InternalBFieldModel;
import gov.nasa.gsfc.sscweb.schema.LocationFilter;
import gov.nasa.gsfc.sscweb.schema.LocationFilterOptions;
import gov.nasa.gsfc.sscweb.schema.MappedRegionFilterOptions;
import gov.nasa.gsfc.sscweb.schema.ObjectFactory;
import gov.nasa.gsfc.sscweb.schema.ObservatoryDescription;
import gov.nasa.gsfc.sscweb.schema.ObservatoryResponse;
import gov.nasa.gsfc.sscweb.schema.OutputOptions;
import gov.nasa.gsfc.sscweb.schema.RegionFilterOptions;
import gov.nasa.gsfc.sscweb.schema.RegionOptions;
import gov.nasa.gsfc.sscweb.schema.Response;
import gov.nasa.gsfc.sscweb.schema.ResultStatusCode;
import gov.nasa.gsfc.sscweb.schema.SatelliteData;
import gov.nasa.gsfc.sscweb.schema.SatelliteSpecification;
import gov.nasa.gsfc.sscweb.schema.SpaceRegion;
import gov.nasa.gsfc.sscweb.schema.SpaceRegionsFilterOptions;
import gov.nasa.gsfc.sscweb.schema.TimeInterval;
import gov.nasa.gsfc.sscweb.schema.Tsyganenko89CBFieldModel;
import gov.nasa.gsfc.sscweb.schema.Tsyganenko89CKp;
import gov.nasa.gsfc.sscweb.schema.ValueOptions;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.TimeUtil;

/**
 *
 * @author jbf
 */
public class SSCWebRecordSource {
    
    private static final Logger logger = Logger.getLogger(SSCWebRecordSource.class.getName());
    
    public SSCWebRecordSource() {
        try {
            client = ClientBuilder.newClient();
            sscFactory = new ObjectFactory();
            datatypeFactory = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * JAX-RS client.
     */
    private Client client;

    private ObjectFactory sscFactory;

    private DatatypeFactory datatypeFactory;
    
    
    private static String USER_AGENT = "SSCWebRecordSource";
    
    /**
     * Endpoint address of SSC REST web service.
     */
    private static String ENDPOINT = "https://sscweb.gsfc.nasa.gov/WS/sscr/2";
    
    /**
     * Factory that creates new <code>gov.nasa.gsfc.sscweb.schema</code>
     * objects.
     */
    private ObjectFactory getObjectFactory() {
        return sscFactory;
    }
    
    
    private OutputOptions getTestOutputOptions() {

        LocationFilter locationFilter = 
            getObjectFactory().createLocationFilter();
                                       // a test LocationFilter to use
                                       //  with all
        locationFilter.setMinimum(true);
        locationFilter.setMaximum(true);
        locationFilter.setLowerLimit(-100.0);
        locationFilter.setUpperLimit(100.0);

        List<FilteredCoordinateOptions> options =
            new ArrayList<FilteredCoordinateOptions>();
                                       // some test coordinate options

        for (CoordinateComponent component :
             CoordinateComponent.values()) {

            FilteredCoordinateOptions option =
                new FilteredCoordinateOptions();

            option.setCoordinateSystem(CoordinateSystem.GSE);
            option.setComponent(component);
            option.setFilter(null);
//            option.setFilter(locationFilter);

            options.add(option);
        }

        RegionOptions regionOptions = 
            getObjectFactory().createRegionOptions();
                                       // region listing options
        regionOptions.setSpacecraft(true);
        regionOptions.setRadialTracedFootpoint(true);
        regionOptions.setNorthBTracedFootpoint(true);
        regionOptions.setSouthBTracedFootpoint(false);

        ValueOptions valueOptions = getObjectFactory().createValueOptions();
                                       // value listing options
        valueOptions.setBFieldStrength(true);
        valueOptions.setDipoleInvLat(true);
        valueOptions.setDipoleLValue(true);
        valueOptions.setRadialDistance(true);

        DistanceFromOptions distanceFromOptions =
            getObjectFactory().createDistanceFromOptions();
                                       // distance from options
        distanceFromOptions.setBGseXYZ(true);
        distanceFromOptions.setBowShock(true);
        distanceFromOptions.setMPause(true);
        distanceFromOptions.setNeutralSheet(true);

        BFieldTraceOptions geoNorthBFieldTrace =
            getObjectFactory().createBFieldTraceOptions();
                                       // GEO north B field trace
                                       // options
        geoNorthBFieldTrace.setCoordinateSystem(CoordinateSystem.GEO);
        geoNorthBFieldTrace.setFieldLineLength(true);
        geoNorthBFieldTrace.setFootpointLatitude(true);
        geoNorthBFieldTrace.setFootpointLongitude(true);
        geoNorthBFieldTrace.setHemisphere(Hemisphere.NORTH);

        BFieldTraceOptions geoSouthBFieldTrace =
            getObjectFactory().createBFieldTraceOptions();
                                       // GEO south B field trace
                                       // options
        geoSouthBFieldTrace.setCoordinateSystem(CoordinateSystem.GEO);
        geoSouthBFieldTrace.setFieldLineLength(true);
        geoSouthBFieldTrace.setFootpointLatitude(true);
        geoSouthBFieldTrace.setFootpointLongitude(true);
        geoSouthBFieldTrace.setHemisphere(Hemisphere.SOUTH);

        BFieldTraceOptions gmNorthBFieldTrace =
            getObjectFactory().createBFieldTraceOptions();
                                       // GM north B field trace options
        gmNorthBFieldTrace.setCoordinateSystem(CoordinateSystem.GM);
        gmNorthBFieldTrace.setFieldLineLength(true);
        gmNorthBFieldTrace.setFootpointLatitude(true);
        gmNorthBFieldTrace.setFootpointLongitude(true);
        gmNorthBFieldTrace.setHemisphere(Hemisphere.NORTH);

        BFieldTraceOptions gmSouthBFieldTrace =
            getObjectFactory().createBFieldTraceOptions();
                                       // GM south B field trace options
        gmSouthBFieldTrace.setCoordinateSystem(CoordinateSystem.GM);
        gmSouthBFieldTrace.setFieldLineLength(true);
        gmSouthBFieldTrace.setFootpointLatitude(true);
        gmSouthBFieldTrace.setFootpointLongitude(true);
        gmSouthBFieldTrace.setHemisphere(Hemisphere.SOUTH);

        OutputOptions outputOptions = 
            getObjectFactory().createOutputOptions();

        outputOptions.setAllLocationFilters(true);
        outputOptions.getCoordinateOptions().addAll(options);
        outputOptions.setRegionOptions(regionOptions);
        outputOptions.setValueOptions(valueOptions);
        outputOptions.setDistanceFromOptions(distanceFromOptions);
        outputOptions.setMinMaxPoints(2);
        outputOptions.getBFieldTraceOptions().add(geoNorthBFieldTrace);
        outputOptions.getBFieldTraceOptions().add(geoSouthBFieldTrace);
        outputOptions.getBFieldTraceOptions().add(gmNorthBFieldTrace);
        outputOptions.getBFieldTraceOptions().add(gmSouthBFieldTrace);

        return outputOptions;
    }
    
    private DataRequest getDataRequest( String satelliteId, int[] startTime, int[] stopTime ) {

        System.err.println( "satelliteId: " +satelliteId );
        System.err.println( "startTime: " + Arrays.toString( startTime ) );
        System.err.println( "stopTime: " + Arrays.toString( stopTime ) );
        
        XMLGregorianCalendar start = 
            datatypeFactory.newXMLGregorianCalendar(
                startTime[0], startTime[1], startTime[2],
                startTime[3], startTime[4], startTime[5], startTime[6]/1000000, 0 );
        XMLGregorianCalendar end = 
            datatypeFactory.newXMLGregorianCalendar(
                stopTime[0], stopTime[1], stopTime[2],
                stopTime[3], stopTime[4], stopTime[5], stopTime[6]/1000000, 0 );

        TimeInterval timeInterval = 
            getObjectFactory().createTimeInterval();
        timeInterval.setStart(start);
        timeInterval.setEnd(end);

        SatelliteSpecification satelliteSpecification = 
            getObjectFactory().createSatelliteSpecification();
        satelliteSpecification.setId( satelliteId );
        satelliteSpecification.setResolutionFactor(1);

        SpaceRegionsFilterOptions spaceRegionsFilter =
            getObjectFactory().createSpaceRegionsFilterOptions();
                                       // space regions filter options
        spaceRegionsFilter.setDaysideMagnetosheath(true);
        spaceRegionsFilter.setDaysideMagnetosphere(true);
        spaceRegionsFilter.setDaysidePlasmasphere(true);
        spaceRegionsFilter.setHighLatitudeBoundaryLayer(true);
        spaceRegionsFilter.setInterplanetaryMedium(true);
        spaceRegionsFilter.setLowLatitudeBoundaryLayer(true);
        spaceRegionsFilter.setNightsideMagnetosheath(true);
        spaceRegionsFilter.setNightsideMagnetosphere(true);
        spaceRegionsFilter.setNightsidePlasmasphere(true);
        spaceRegionsFilter.setPlasmaSheet(true);
        spaceRegionsFilter.setTailLobe(true);

        HemisphereOptions hemisphereOptions =
            new HemisphereOptions();
                                       // hemisphere listing options
                                       //  requesting both the north and
                                       //  south hemisphere
        hemisphereOptions.setNorth(true);
        hemisphereOptions.setSouth(true);

        MappedRegionFilterOptions radialTraceRegionsFilter =
            new MappedRegionFilterOptions();
                                       // radial traced regions filter
        radialTraceRegionsFilter.setCusp(hemisphereOptions);
        radialTraceRegionsFilter.setCleft(hemisphereOptions);
        radialTraceRegionsFilter.setAuroralOval(hemisphereOptions);
        radialTraceRegionsFilter.setPolarCap(hemisphereOptions);
        radialTraceRegionsFilter.setMidLatitude(hemisphereOptions);
        radialTraceRegionsFilter.setLowLatitude(true);

        MappedRegionFilterOptions magneticTraceRegionsFilter =
            radialTraceRegionsFilter;
                                       // magnetic traced regions filter

        RegionFilterOptions regionFilters =
            new RegionFilterOptions();
                                       // region filter
        regionFilters.setSpaceRegions(spaceRegionsFilter);
        regionFilters.setRadialTraceRegions(radialTraceRegionsFilter);
        regionFilters.setMagneticTraceRegions(
            magneticTraceRegionsFilter);

        LocationFilter locationFilter = new LocationFilter();
                                       // a location filter
        locationFilter.setMinimum(true);
        locationFilter.setMaximum(true);
        locationFilter.setLowerLimit(-500.0);
        locationFilter.setUpperLimit(500.0);

        LocationFilterOptions locationFilterOptions =
            new LocationFilterOptions();
                                       // location filter options
        locationFilterOptions.setAllFilters(true);
        locationFilterOptions.setDistanceFromCenterOfEarth(
            locationFilter);
        locationFilterOptions.setMagneticFieldStrength(locationFilter);
        locationFilterOptions.setDistanceFromNeutralSheet(
            locationFilter);
        locationFilterOptions.setDistanceFromBowShock(locationFilter);
        locationFilterOptions.setDistanceFromMagnetopause(
            locationFilter);
        locationFilterOptions.setDipoleLValue(locationFilter);
        locationFilterOptions.setDipoleInvariantLatitude(
            locationFilter);

        BFieldModel bFieldModel =
            getObjectFactory().createBFieldModel();
        bFieldModel.setTraceStopAltitude(100);
        bFieldModel.setInternalBFieldModel(
            InternalBFieldModel.IGRF);

        Tsyganenko89CBFieldModel t89c =
            getObjectFactory().createTsyganenko89CBFieldModel();
        t89c.setKeyParameterValues(Tsyganenko89CKp.KP_3_3_3);

        bFieldModel.setExternalBFieldModel(t89c);

        DataRequest dataRequest = getObjectFactory().createDataRequest();
                                        // test data request
        dataRequest.setTimeInterval(timeInterval);
        dataRequest.setBFieldModel(bFieldModel);
        dataRequest.getSatellites().add(satelliteSpecification);

        dataRequest.setOutputOptions(getTestOutputOptions());

//        dataRequest.setRegionFilterOptions(regionFilters);
//        dataRequest.setLocationFilterOptions(locationFilterOptions);
//        dataRequest.setBFieldModelOptions(bFieldModelOptions);

//        dataRequest.setFormatOptions(getTestFormatOptions());


        return dataRequest;
    }
    
    public List<SatelliteData> getData( DataRequest dataRequest )  throws Exception {

        String url = ENDPOINT + "/locations/";

        WebTarget ssc = client.target(url);

        Entity<DataRequest> dataRequestEntity =
            Entity.entity(dataRequest, MediaType.APPLICATION_XML);

        Invocation.Builder request = 
            ssc.request(MediaType.APPLICATION_XML);

        Invocation invocation = 
            request.header("User-Agent", USER_AGENT).
                buildPost(dataRequestEntity);

        Response dataResponse = invocation.invoke(Response.class);

        DataResult dataResult = (DataResult)dataResponse.getResult();

        if (dataResult.getStatusCode() != 
            ResultStatusCode.SUCCESS) {

            System.err.println("getData: dataResult.getStatusCode() = " +
                dataResult.getStatusCode());
        }

        return dataResult.getData();
    }

    private static String getSpaceRegion(SpaceRegion value) {

        switch (value) {

        case INTERPLANETARY_MEDIUM:

            return "Intpl Med";

        case DAYSIDE_MAGNETOSHEATH:

            return "D Msheath";

        case NIGHTSIDE_MAGNETOSHEATH:

            return "N Msheath";

        case DAYSIDE_MAGNETOSPHERE:

            return "D Msphere";

        case NIGHTSIDE_MAGNETOSPHERE:

            return "N Msphere";

        case PLASMA_SHEET:

            return "Plasma Sh";

        case TAIL_LOBE:

            return "Tail Lobe";

        case HIGH_LATITUDE_BOUNDARY_LAYER:

            return "HLB Layer";

        case LOW_LATITUDE_BOUNDARY_LAYER:

            return "LLB Layer";

        case DAYSIDE_PLASMASPHERE:

            return "D Psphere";

        case NIGHTSIDE_PLASMASPHERE:

            return "N Psphere";

        default:

            return value.toString();
        }
    }

    private static String getTracedRegion(FootpointRegion value) {

        switch (value) {

        case NORTH_CUSP:

            return "N Cusp   ";

        case SOUTH_CUSP:

            return "S Cusp   ";

        case NORTH_CLEFT:

            return "N Cleft  ";

        case SOUTH_CLEFT:

            return "S Cleft  ";

        case NORTH_AURORAL_OVAL:

            return "N Oval   ";

        case SOUTH_AURORAL_OVAL:

            return "S Oval   ";

        case NORTH_POLAR_CAP:

            return "N PolrCap";

        case SOUTH_POLAR_CAP:

            return "S PolrCap";

        case NORTH_MID_LATITUDE:

            return "N Mid-Lat";

        case SOUTH_MID_LATITUDE:

            return "S Mid-Lat";

        case LOW_LATITUDE:

            return "Low Lat  ";

        default:

            return "  None   ";
        }
    }


    
    private static void print(List<XMLGregorianCalendar> time,
        List<CoordinateData> coords, List<Double> radialLength,
        List<Double> magneticStrength, List<Double> ns,
        List<Double> bs, List<Double> mp, List<Double> lv,
        List<Float> il, List<SpaceRegion> sr,
        List<FootpointRegion> rtr, List<FootpointRegion> nbtr,
        List<FootpointRegion> sbtr, List<Double> bGseX,
        List<Double> bGseY, List<Double> bGseZ, int numValues) {

        if (numValues < 0) {

            numValues = time.size();
        }

        for (int i = 0; i < time.size() && i < numValues; i++) {

            System.out.print(time.get(i));

            for (int j = 0; j < coords.size(); j++) {

                List<Double> x = coords.get(j).getX();
                List<Double> y = coords.get(j).getY();
                List<Double> z = coords.get(j).getZ();
                List<Float> lat = coords.get(j).getLatitude();
                List<Float> lon = coords.get(j).getLongitude();
                List<Double> lt = coords.get(j).getLocalTime();

                if (x != null && i < x.size()) {

                    System.out.printf(",%10.2f", x.get(i));
                }

                if (y != null && i < y.size()) {

                    System.out.printf(",%10.2f", y.get(i));
                }

                if (z != null && i < z.size()) {

                    System.out.printf(",%10.2f", z.get(i));
                }

                if (lat != null && i < lat.size()) {

                    System.out.printf(",%10.2f", lat.get(i));
                }

                if (lon != null && i < lon.size()) {

                    System.out.printf(",%10.2f", lon.get(i));
                }

                if (lt != null && i < lt.size()) {

                    System.out.printf(",%10.2f", lt.get(i));
                }
            } // endfor each coords

            if (radialLength != null && i < radialLength.size()) {

                System.out.printf(",%10.2f" , radialLength.get(i));
            }

            if (magneticStrength != null &&
                i < magneticStrength.size()) {

                if (magneticStrength.get(i) == -1.0E31D) {
                    System.out.printf(",%10.1e",-1.0E31D);
                }
                else {
                    System.out.printf(",%10.2f" ,
                        magneticStrength.get(i));
                }
            }

            if (ns != null && i < ns.size()) {

                if (ns.get(i) == -1.0E31D) {
                    System.out.printf(",%10.1e",-1.0E31D);
                }
                else {
                    System.out.printf(",%10.2f" , ns.get(i));
                }
            }

            if (bs != null && i < bs.size()) {
                System.out.printf(",%10.2f" , bs.get(i));
            }

            if (mp != null && i < mp.size()) {
                System.out.printf(",%10.2f" , mp.get(i));
            }

            if (lv != null && i < lv.size()) {

                System.out.printf(",%10.2f" , lv.get(i));
            }

            if (il != null && i < il.size()) {

                System.out.printf(",%10.2f" , il.get(i));
            }

            if (sr != null && i < sr.size()) {

                System.out.printf(",%10s", getSpaceRegion(sr.get(i)));
            }

            if (rtr != null && i < rtr.size()) {

                System.out.printf(",%10s" ,
                    getTracedRegion(rtr.get(i)));
            }

            if (nbtr != null && i < nbtr.size()) {

                System.out.printf(",%10s" ,
                    getTracedRegion(nbtr.get(i)));
            }

            if (sbtr != null && i < sbtr.size()) {

                System.out.printf(",%10s" ,
                    getTracedRegion(sbtr.get(i)));
            }

            if (bGseX != null && i < bGseX.size()) {

                if (bGseX.get(i) == -1.0E31D) {
                    System.out.printf(",%10.1e",-1.0E31D);
                }
                else {

                    System.out.printf(",%10.2f" , bGseX.get(i));
                }
            }

            if (bGseY != null && i < bGseY.size()) {

                if (bGseY.get(i) == -1.0E31D) {
                    System.out.printf(",%10e",-1.0E31D);
                }
                else {
                    System.out.printf(",%10.2f" , bGseY.get(i));
                }
            }

            if (bGseZ != null && i < bGseZ.size()) {

                if (bGseZ.get(i) == -1.0E31D) {
                    System.out.printf(",%10e",-1.0E31D);
                }
                else {
                    System.out.printf(",%10.2f" , bGseZ.get(i));
                }
            }

            System.out.println();
        } // endfor each time value
    }
    
    private static void printHeading(List<CoordinateData> coords,
        List<Double> radialLength, List<Double> magneticStrength,
        List<Double> ns, List<Double> bs, List<Double> mp,
        List<Double> lv, List<Float> il, List<SpaceRegion> sr,
        List<FootpointRegion> rtr, List<FootpointRegion> nbtr,
        List<FootpointRegion> sbtr,
        List<Double> bGseX, List<Double> bGseY, List<Double> bGseZ) {

        StringBuffer hdr1 = new StringBuffer();
        StringBuffer hdr2 = new StringBuffer();

        hdr1.append("  Time                 ");
        hdr2.append("                       ");

        for (CoordinateData point : coords) {

            List<Double> x = point.getX();
            List<Double> y = point.getY();
            List<Double> z = point.getZ();
            List<Float> lat = point.getLatitude();
            List<Float> lon = point.getLongitude();
            List<Double> lt = point.getLocalTime();

            if (x != null && x.size() > 0) {

                hdr1.append("            ");
                hdr2.append("         X  ");
            }

            if (y != null && y.size() > 0) {

                hdr1.append("            ");
                hdr2.append("         Y  ");
            }

            hdr1.append(point.getCoordinateSystem() + "         ");

            if (z != null && z.size() > 0) {

                hdr2.append("         Z  ");
            }

            if (lat != null && lat.size() > 0) {

                hdr1.append("            ");
                hdr2.append("       Lat  ");
            }

            if (lon != null && lon.size() > 0) {

                hdr1.append("            ");
                hdr2.append("       Lon  ");
            }

            if (lt != null && lt.size() > 0) {

                hdr1.append("        Local  ");
                hdr2.append("         Time  ");
            }
        } // endfor each coords

        if (radialLength != null && radialLength.size() > 0) {

            hdr1.append("    Radial ");
            hdr2.append("    Length ");
        }

        if (magneticStrength != null && magneticStrength.size() > 0) {

            hdr1.append("    Magnetic ");
            hdr2.append("    Strength ");
        }

        if (ns != null && ns.size() > 0) {

            hdr1.append("    Neutral ");
            hdr2.append("    Sheet   ");
        }

        if (bs != null && bs.size() > 0) {

            hdr1.append("    Bow     ");
            hdr2.append("    Shock   ");
        }

        if (mp != null && mp.size() > 0) {

            hdr1.append("    Magneto ");
            hdr2.append("    Pause   ");
        }

        if (lv != null && lv.size() > 0) {

            hdr1.append("    Dipole  ");
            hdr2.append("    L Value ");
        }

        if (il != null && il.size() > 0) {

            hdr1.append("    Dipole  ");
            hdr2.append("    InvLat  ");
        }

        if (sr != null && sr.size() > 0) {

            hdr1.append("  Spacecraft ");
            hdr2.append("  Region     ");
        }

        if (rtr != null && rtr.size() > 0) {

            hdr1.append("  Radial Trc ");
            hdr2.append("  Region     ");
        }

        if (nbtr != null && nbtr.size() > 0) {

            hdr1.append("  N BTraced ");
            hdr2.append("  Region    ");
        }

        if (sbtr != null && sbtr.size() > 0) {

            hdr1.append("  S BTraced ");
            hdr2.append("  Region    ");
        }

        if (bGseX != null && bGseX.size() > 0) {

            hdr1.append("    GSE    ");
            hdr2.append("    X      ");
        }

        if (bGseY != null && bGseY.size() > 0) {

            hdr1.append("  Magnetic ");
            hdr2.append("    Y      ");
        }

        if (bGseZ != null && bGseZ.size() > 0) {

            hdr1.append("  Vectors ");
            hdr2.append("    Z     ");
        }


        System.err.println(hdr1);
        System.err.println(hdr2);
    }

    
    private static void print(SatelliteData data) {

        System.err.println("  " + data.getId());

        List<XMLGregorianCalendar> time = data.getTime();
        List<CoordinateData> coords = data.getCoordinates();
        List<Double> radialLength = data.getRadialLength();
        List<Double> magneticStrength = data.getMagneticStrength();
        List<Double> ns = data.getNeutralSheetDistance();
        List<Double> bs = data.getBowShockDistance();
        List<Double> mp = data.getMagnetoPauseDistance();
        List<Double> lv = data.getDipoleLValue();
        List<Float> il = data.getDipoleInvariantLatitude();
        List<SpaceRegion> sr = data.getSpacecraftRegion();
        List<FootpointRegion> rtr =
            data.getRadialTracedFootpointRegions();
        List<FootpointRegion> nbtr = 
            data.getNorthBTracedFootpointRegions();
        List<FootpointRegion> sbtr = 
            data.getSouthBTracedFootpointRegions();
        List<Double> bGseX = data.getBGseX();
        List<Double> bGseY = data.getBGseY();
        List<Double> bGseZ = data.getBGseZ();

        printHeading(coords, radialLength, magneticStrength, ns, bs, mp,
              lv, il, sr, rtr, nbtr, sbtr, bGseX, bGseY, bGseZ);

        print(time, coords, radialLength, magneticStrength, ns, bs, mp,
              lv, il, sr, rtr, nbtr, sbtr, bGseX, bGseY, bGseZ,
              -1 );

    }


    public void run( String satellite, int[] startTime, int[] stopTime ) throws Exception {
        DataRequest request = getDataRequest( satellite, startTime, stopTime );

        List<SatelliteData> satData = getData(request);

        print( satData.get(0) );
        
    }
    
    public void printCatalog( ) {

        try {
            String url = ENDPOINT + "/observatories/";
            
            WebTarget ssc = client.target(url);
            Invocation.Builder request =
                ssc.request(MediaType.APPLICATION_XML);
            Invocation invocation =
                request.header("User-Agent", USER_AGENT).buildGet();
            
            ObservatoryResponse response =
                invocation.invoke(ObservatoryResponse.class);
            
            List<ObservatoryDescription> dd=  response.getObservatory();
            
            JSONArray ids= new JSONArray();
            
            for ( ObservatoryDescription d : dd ) {
                System.err.println(""+d.getId());
                JSONObject id= new JSONObject();
                id.put("id",d.getId());
                id.put("title",d.getName());
                ids.put( ids.length(), id );
            }
            
            JSONObject catalog= new JSONObject();
            catalog.put( "catalog", ids );
            
            
            JSONObject status= new JSONObject();
            status.put( "code", 1200 );
            status.put( "message", "OK" );
            
            catalog.put( "status", status );
            
            System.out.println( catalog.toString(4) );
            
        } catch (JSONException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        

    }
    
    public void printInfo( String satellite ) {
        
    }
    
    public static void main( String[] args ) throws Exception {
        if ( args.length==0 ) {
            System.err.println("SSCWebRecordSource -- prints this help");
            System.err.println("SSCWebRecordSource catalog");
            System.err.println("SSCWebRecordSource info <sc>");
            System.err.println("SSCWebRecordSource data <sc> <startTime> <stopTime>");
            System.exit(1);
        }
        if ( args[0].equals("catalog")) {
            new SSCWebRecordSource().printCatalog();
        } else if ( args[0].equals("info") ) {
            new SSCWebRecordSource().printInfo( args[1] );
        } else if ( args[1].equals("data") ) {
            new SSCWebRecordSource().run( args[1], TimeUtil.parseISO8601Time(args[2]), TimeUtil.parseISO8601Time(args[3]) );
        } else {
            main( new String[0] );
        }
    }
}
