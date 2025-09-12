package org.hapiserver.source.cdaweb.adapters;

import org.hapiserver.TimeUtil;
import org.hapiserver.source.cdaweb.Adapter;

/**
 * Add 1800 seconds to the times.
 * @author jbf
 */
public class Add1800 extends Adapter {
 
    Adapter base;

    public Add1800( Adapter base ) {
        this.base= base;
    }

    @Override
    public String adaptString(int index) {
        String s= base.adaptString(index);
        if ( s.charAt(14)=='0' && s.charAt(15)=='0' ) {
            s= s.substring(0,14) + "30" + s.substring(16);
        } else {
            throw new IllegalArgumentException("add1800 assumes there are no minutes!");
        }
        return s;
        //return base.adaptString(index);
    }
    
    
    @Override
    public String getString(int index) {
        String time= base.getString(index);
        return time;
    }
    
    
    
}
