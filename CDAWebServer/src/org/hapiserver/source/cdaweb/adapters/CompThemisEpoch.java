
package org.hapiserver.source.cdaweb.adapters;

import org.hapiserver.source.cdaweb.Adapter;
import org.hapiserver.source.cdaweb.CdawebServicesHapiRecordIterator;
import org.hapiserver.source.cdaweb.CdawebServicesHapiRecordSource;

/**
 * Implements by returning parameter only where the flag is (?) nonzero.
 * @author jbf
 */
public class CompThemisEpoch extends Adapter {

    Adapter base;
    Adapter offset;
    
    public CompThemisEpoch( Adapter base, Adapter offset ) {
        this.base= base;
        this.offset= offset;
    }
    
    @Override
    public double adaptDouble(int index) {
        double d= base.adaptDouble(index);
        double d2= offset.adaptDouble(index);
        return d+d2*1000;
    }

    @Override
    public String adaptString(int index) {
        double d= adaptDouble(index);
        return new CdawebServicesHapiRecordIterator.IsotimeEpochAdapter( new double[] { d }, 30 ).adaptString(0);
    }

    
    
    @Override
    public String getString(int index) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
    
}
