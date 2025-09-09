
package org.hapiserver.source.cdaweb.adapters;

import org.hapiserver.source.cdaweb.Adapter;

/**
 * virtual variable component is just single record.
 * THA_L2_GMOM@2&parameters=Time,tha_ptirf_sc_potQ
 * @author jbf
 */
public class ConstantAdapter extends Adapter {

    private final double value;

    public ConstantAdapter( double value ) {
        this.value= value;
    }
    
    @Override
    public String getString(int index) {
        return String.valueOf(this.value);
    }

    @Override
    public double adaptDouble(int index) {
        return value;
    }
    
}
