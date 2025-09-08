
package org.hapiserver.source.cdaweb.adapters;

import org.hapiserver.source.cdaweb.Adapter;

/**
 * Implements by returning parameter only where the flag is (?) nonzero.
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
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public double adaptDouble(int index) {
        double d= param.adaptDouble(index);
        int i= flag.adaptInteger(index);
        if ( i>0 ) {
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
            if ( ffill==null ) {
                ffill= new double[d.length];
                for ( int j=0; j<d.length; j++ ) {
                    ffill[j]= fill;
                }
            }
            return ffill;
        }
    }
    
    
    
}
