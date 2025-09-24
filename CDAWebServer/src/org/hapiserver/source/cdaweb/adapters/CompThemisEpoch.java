
package org.hapiserver.source.cdaweb.adapters;

import org.hapiserver.source.cdaweb.Adapter;
import org.hapiserver.source.cdaweb.CdawebServicesHapiRecordIterator;

/**
 * Implements by returning parameter only where the flag is (?) nonzero.
 * <ul>
 * <li>.../hapi/data?id=THG_L1_ASK@8&timerange=2025-09-08+0:00+to+12:55
 * </ul>
 * @author jbf
 */
public class CompThemisEpoch extends Adapter {

    double[] base;
    double[] offset;
    
    public CompThemisEpoch( double[] base, double[] offset ) {
        this.base= base;
        this.offset= offset;
    }
    
    @Override
    public double adaptDouble(int index) {
        double d= base[0];
        double d2= offset[index];
        return d+d2*1000;
    }

    @Override
    public String adaptString(int index) {
        double d= adaptDouble(index);
        //TODO: there might be a better implementation of this.
        return new CdawebServicesHapiRecordIterator.IsotimeEpochAdapter( new double[] { d }, 30 ).adaptString(0);
    }

    
    
    @Override
    public String getString(int index) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
    
}
