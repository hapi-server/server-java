
package org.hapiserver.source.cdaweb;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.AbstractHapiRecordSource;
import org.hapiserver.HapiRecord;
import org.hapiserver.TimeUtil;

/**
 * CdawebServicesHapiRecordSource creates a HapiRecord iterator from CDF files,
 * using the WebServices or reading directly from files.
 * @author jbf
 */
public class CdawebServicesHapiRecordSource extends AbstractHapiRecordSource {
    
    private static final Logger logger= Logger.getLogger("hapi.cdaweb");
    
    private String id;
    JSONObject info;
    JSONObject data;
    AvailabilityIterator availabilityIterator;
    String root;
    String availRoot; // Root containing "info" and the data granule availability files.
    File cache;
    
    /**
     * Constructor for the record source.  This reads the CDF files needed from the availability files.
     * @param availRoot folder containing orig_data responses, with a file "info/AC_AT_DEF.pkl"
     * @param id the id, like "AC_H0_EPM"
     * @param info the resolved info configuration object
     * @param data the data configuration object
     * @param cacheDir cacheDir staging area where files can be stored for ~ 1 hour for reuse
     */
    public CdawebServicesHapiRecordSource( String availRoot, String id, JSONObject info, JSONObject data, File cacheDir ) {
        logger.entering("CdawebServicesHapiRecordSource","constructor");
        this.id= id;
        this.info= info;
        this.data= data;
        this.availRoot= availRoot;
        this.cache= cacheDir;
        logger.exiting("CdawebServicesHapiRecordSource","constructor");
    }
    
    @Override
    public boolean hasGranuleIterator() {
        return true;
    }
    
    @Override
    public Iterator<int[]> getGranuleIterator(int[] start, int[] stop) {
        logger.entering("CdawebServicesHapiRecordSource","getGranuleIterator");
        
        int ia= id.indexOf("@");
        String availId= ia==-1 ? id : id.substring(0,ia);
                
        String availInfo= CdawebAvailabilityHapiRecordSource.getInfoAvail( availRoot, availId + "/source" );
        JSONObject infoObject;
        try {
            infoObject = new JSONObject(availInfo);
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
        CdawebAvailabilityHapiRecordSource source= new CdawebAvailabilityHapiRecordSource( availRoot, availId + "/source", infoObject );
        TimeUtil.formatIso8601Time(start);
        TimeUtil.formatIso8601Time(stop);
        Iterator<HapiRecord> it = source.getIterator(start, stop);
        this.root= source.getRoot();
        
        availabilityIterator= new AvailabilityIterator(it);
        logger.exiting("CdawebServicesHapiRecordSource","getGranuleIterator");
        return availabilityIterator;
    }
    
    @Override
    public boolean hasParamSubsetIterator() {
        return true;
    }

    @Override
    public Iterator<HapiRecord> getIterator(int[] start, int[] stop, String[] params) {
        try {
            logger.entering("CdawebServicesHapiRecordSource","getIterator");
            URL f= new URL( this.root + availabilityIterator.getFile() );
            
            CdawebServicesHapiRecordIterator result= CdawebServicesHapiRecordIterator.create(id, info, start, stop, params, f, cache );
            
            logger.exiting("CdawebServicesHapiRecordSource","getIterator");
            return result;
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
    }    
 
    public static void main( String[] args ) throws IOException, JSONException {
        String origRoot= "file:/net/spot10/hd1_8t/home/weigel/cdawmeta/data/orig_data/";
        String hapiRoot= "file:/net/spot10/hd1_8t/home/weigel/cdawmeta/data/hapi/info/AMPTECCE_H0_MEPA@0.json";
        String id= "AMPTECCE_H0_MEPA@0";
        JSONObject info= new JSONObject( CdawebInfoCatalogSource.getInfo( origRoot, hapiRoot ) );
        CdawebServicesHapiRecordSource crs= new CdawebServicesHapiRecordSource( origRoot, id, info, null,  new File("/tmp/hapi-server-cache") );
        
        System.err.println("crs: "+ crs);
        
        int[] start=  new int[]{1988,12,22,0,0,0,0};
        int[] stop= new int[]{1988,12,22,16,19,0,0};
        
        // This is the "alternate_view" one
        String[] params= new String[]{"Time", "ION_protons_COUNTS_stack"};
        
        // This is the a non-virtual one
        //String[] params= new String[]{"Time", "ION_protons_COUNTS"};
        
        Iterator<int[]> granules= crs.getGranuleIterator( start, stop );
        if ( granules.hasNext() ) {
            int[] tr= granules.next();
            Iterator<HapiRecord> records= crs.getIterator( TimeUtil.getStartTime(tr), TimeUtil.getStopTime(tr), params);
            while ( records.hasNext() ) {
                HapiRecord rec= records.next();
                System.err.println( "next: "+ rec.getIsoTime(0)+" " +rec.getAsString(1) );
            }
        }

    }
}
