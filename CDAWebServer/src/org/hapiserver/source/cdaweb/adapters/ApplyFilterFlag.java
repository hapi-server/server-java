
package org.hapiserver.source.cdaweb.adapters;

import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.source.cdaweb.Adapter;

/**
 *
 * @author jbf
 */
public class ApplyFilterFlag extends Adapter {

    private final Adapter data;
    private final Adapter filter;
    private final String condition;
    private final double value;
    private final double fill;
    private double[] ffill;
    
    public ApplyFilterFlag( JSONObject param, Adapter data, Adapter filter, String condition, double value ) {
        this.data= data;
        this.filter= filter;
        this.condition= condition;
        this.value= value;
        this.fill= Double.parseDouble( param.optString("fill","NaN") );
    }    

    @Override
    public String getString(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double adaptDouble(int index) {
        boolean filt;
        double d= data.adaptDouble(index);
        double f= filter.adaptDouble(index);
        double v= this.value;
        switch ( condition ) {
            case "eq": 
                filt= f==v;
                break;
            case "ne": 
                filt= f!=v;
                break;
            case "ge": 
                filt= f>=v;
                break;
            case "gt": 
                filt= f>v;
                break;
            case "le": 
                filt= f<=v;
                break;
            case "lt": 
                filt= f<v;
                break;
            default:
                throw new IllegalArgumentException("not implemented applyFilterFlag");
        }
        if ( filt ) {
            return d;
        } else {
            return fill;
        }
    }

    @Override
    public double[] adaptDoubleArray(int index) {
        double[] d= data.adaptDoubleArray(index);
        double f= filter.adaptDouble(index);
        double v= this.value;
        boolean filt;
        switch ( condition ) {
            case "eq": 
                filt= f==v;
                break;
            case "ne": 
                filt= f!=v;
                break;
            case "ge": 
                filt= f>=v;
                break;
            case "gt": 
                filt= f>v;
                break;
            case "le": 
                filt= f<=v;
                break;
            case "lt": 
                filt= f<v;
                break;
            default:
                throw new IllegalArgumentException("not implemented applyFilterFlag");
        }
        if ( filt ) {
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
