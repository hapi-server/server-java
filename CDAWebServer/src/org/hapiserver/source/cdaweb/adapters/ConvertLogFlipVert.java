
package org.hapiserver.source.cdaweb.adapters;

import org.hapiserver.source.cdaweb.Adapter;

/**
 * return log10 of another dataset, but then flip the data vertically.
 * <ul>
 * <li>.../hapi/data?id=IM_K0_WIC&parameters=Time,WIC_PIXELS_LOG&timerange=2005-12-17+2:32+to+23:55
 * <li>https://github.com/autoplot/cdfj/blob/virtual_variable_descriptions/virtual/convert_log10_flip_vert.md
 * </ul>
 * This is a nice example because we'll need to know the sizes to implement this.
 * @author jbf
 */
public class ConvertLogFlipVert extends Adapter {
 
    Adapter base;
    double[] stage;
    
    public ConvertLogFlipVert( Adapter base ) {
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
        if (true) {
            throw new IllegalArgumentException("not implemented");
        } else {
            double[] result= base.adaptDoubleArray(index);
            // it seems likely that mutating the original data is a bad idea, so make a copy.
            if ( stage==null ) stage= new double[result.length];
            for ( int i=0; i<result.length; i++ ) {
                stage[i]= Math.log10( result[i] );
            }
            return stage;
        }
    }
    
    
}
