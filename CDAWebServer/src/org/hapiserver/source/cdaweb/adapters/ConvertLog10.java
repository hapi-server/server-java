
package org.hapiserver.source.cdaweb.adapters;

import org.hapiserver.source.cdaweb.Adapter;

/**
 * return log10 of another dataset.
 * @author jbf
 */
public class ConvertLog10 extends Adapter {
 
    Adapter base;
    double[] stage;
    
    public ConvertLog10( Adapter base ) {
        this.base= base;
    }
    @Override
    public String getString(int index) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public double adaptDouble(int index) {
        return Math.log10( base.adaptDouble(index) );
    }

    @Override
    public double[] adaptDoubleArray(int index) {
        double[] result= base.adaptDoubleArray(index);
        // it seems likely that mutating the original data is a bad idea, so make a copy.
        if ( stage==null ) stage= new double[result.length];
        for ( int i=0; i<result.length; i++ ) {
            stage[i]= Math.log10( result[i] );
        }
        return stage;
    }
    
    
}
