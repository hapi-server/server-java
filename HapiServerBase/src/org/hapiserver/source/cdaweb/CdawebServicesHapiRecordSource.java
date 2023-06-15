/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.hapiserver.source.cdaweb;

import java.net.URL;
import java.util.Iterator;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.AbstractHapiRecordSource;
import org.hapiserver.HapiRecord;
import org.hapiserver.source.AggregationGranuleIterator;

/**
 *
 * @author jbf
 */
public class CdawebServicesHapiRecordSource extends AbstractHapiRecordSource {
    
    private String id;
    JSONObject info;
    JSONObject data;
    
    public CdawebServicesHapiRecordSource( String hapiHome, String id, JSONObject info, JSONObject data ) {
        this.id= id;
        this.info= info;
        this.data= data;
    }
    
    @Override
    public boolean hasGranuleIterator() {
        return true;
    }
    
    @Override
    public Iterator<int[]> getGranuleIterator(int[] start, int[] stop) {
        String availInfo= CdawebAvailabilitySource.getInfo( "availability/"+id );
        JSONObject jsonObject;
        try {
            jsonObject = new JSONObject(availInfo);
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
        CdawebAvailabilitySource source= new CdawebAvailabilitySource( "notUsed", "availability/"+id, jsonObject, null );
        Iterator<HapiRecord> it = source.getIterator(start, stop);
        return new AvailabilityIterator(it);
    }
    
    @Override
    public boolean hasParamSubsetIterator() {
        return true;
    }

    @Override
    public Iterator<HapiRecord> getIterator(int[] start, int[] stop, String[] params) {
        return new CdawebServicesHapiRecordIterator(id, info, start, stop, params);
    }    
 
}
