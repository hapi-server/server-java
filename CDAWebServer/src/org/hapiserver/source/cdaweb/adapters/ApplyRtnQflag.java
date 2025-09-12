
package org.hapiserver.source.cdaweb.adapters;

import org.hapiserver.source.cdaweb.Adapter;

/**
 * To use the quality variable to "filter out bad messenger data points," where quality is 222 or 223.
 * <ul>
 * <li>https://github.com/autoplot/cdfj/blob/virtual_variable_descriptions/virtual/apply_rtn_qflag.md
 * </ul>
 * @author jbf
 */
public class ApplyRtnQflag extends Adapter {

    Adapter data;
    Adapter quality;
    double fill;
    double[] ffill;
    
    public ApplyRtnQflag( Adapter data, Adapter quality, double fill ) {
        this.data= data;
        this.quality= quality;
        this.fill= fill;
    }
    
    @Override
    public String getString(int index) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public double adaptDouble(int index) {
        double d= data.adaptDouble(index);
        int i= quality.adaptInteger(index);
        if ( i==222 || i==223 ) {
            return d;
        } else {
            return fill;
        }
    }

    @Override
    public double[] adaptDoubleArray(int index) {
        double[] d= data.adaptDoubleArray(index);
        int i= quality.adaptInteger(index);
        if ( i==222 || i==223 ) {
            return d;
        } else {
            if ( ffill==null ) { // initialize once, now that we know the size of param.
                ffill= new double[d.length];
                for ( int j=0; j<d.length; j++ ) {
                    ffill[j]= fill;
                }
            }
            return ffill;
        }
    }
    
    
    
}
