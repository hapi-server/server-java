
package org.hapiserver.source.cdaweb.adapters;

import org.hapiserver.source.cdaweb.Adapter;

/**
 * Implements by returning parameter only where the flag is zero, fill otherwise.
 * <ul>
 * <li>vap+hapi:http://localhost:8280/HapiServer/hapi?id=THE_L2_GMOM@5&parameters=Time,the_ptebb_avgtempQ&timerange=2025-09-07
 * </ul>
 * @author jbf
 */
public class ApplyEsaQflag extends Adapter {

    Adapter param;
    Adapter flag;
    double fill;
    double[] ffill;
    
    public ApplyEsaQflag( Adapter param, Adapter flag, double fill ) {
        this.param= param;
        this.flag= flag;
        this.fill= fill;
    }
    
    @Override
    public String getString(int index) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public double adaptDouble(int index) {
        double d= param.adaptDouble(index);
        int i= flag.adaptInteger(index);
        if ( i==0 ) {
            return d;
        } else {
            return fill;
        }
    }

    @Override
    public double[] adaptDoubleArray(int index) {
        double[] d= param.adaptDoubleArray(index);
        int i= flag.adaptInteger(index);
        if ( i==0 ) {
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
