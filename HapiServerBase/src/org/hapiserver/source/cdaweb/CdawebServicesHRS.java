/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.hapiserver.source.cdaweb;

import java.util.Iterator;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.AbstractHapiRecordSource;
import org.hapiserver.HapiRecord;
import org.hapiserver.source.AggregationGranuleIterator;

/**
 *
 * @author jbf
 */
public class CdawebServicesHRS extends AbstractHapiRecordSource {
    
    private String id;
    JSONObject info;
    JSONObject data;
    
    public CdawebServicesHRS( String hapiHome, String id, JSONObject info, JSONObject data ) {
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
        return new AggregationGranuleIterator("/tmp/$Y_$m_$d", start, stop );
    }
    
    @Override
    public boolean hasParamSubsetIterator() {
        return true;
    }

    @Override
    public Iterator<HapiRecord> getIterator(int[] start, int[] stop, String[] params) {
        return new CdawebServicesHapiRecordSource(id, info, start, stop, params);
    }    
 
}
