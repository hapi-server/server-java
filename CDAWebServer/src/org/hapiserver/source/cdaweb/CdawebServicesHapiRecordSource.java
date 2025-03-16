
package org.hapiserver.source.cdaweb;

import java.util.Iterator;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.AbstractHapiRecordSource;
import org.hapiserver.HapiRecord;

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
    
    /**
     * Constructor for the record source.  This reads the CDF files needed from the availability files.
     * @param availRoot folder containing orig_data responses, with a file "info/AC_AT_DEF.pkl"
     * @param id the id, like "AC_H0_EPM"
     * @param info the resolved info configuration object
     * @param data the data configuration object
     */
    public CdawebServicesHapiRecordSource( String availRoot, String id, JSONObject info, JSONObject data ) {
        logger.entering("CdawebServicesHapiRecordSource","constructor");
        this.id= id;
        this.info= info;
        this.data= data;
        this.availRoot= availRoot;
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
                
        String availInfo= CdawebAvailabilityRecordSource.getInfoAvail( availRoot, availId + "/availability" );
        JSONObject infoObject;
        try {
            infoObject = new JSONObject(availInfo);
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
        CdawebAvailabilityRecordSource source= new CdawebAvailabilityRecordSource( availRoot, availId + "/availability", infoObject );
        
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
        logger.entering("CdawebServicesHapiRecordSource","getIterator");
        String f= this.root + availabilityIterator.getFile();
        
        CdawebServicesHapiRecordIterator result= CdawebServicesHapiRecordIterator.create(id, info, start, stop, params, f );
        
        logger.exiting("CdawebServicesHapiRecordSource","getIterator");
        return result;
    }    
 
}
