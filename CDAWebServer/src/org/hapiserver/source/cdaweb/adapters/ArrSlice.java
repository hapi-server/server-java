
package org.hapiserver.source.cdaweb.adapters;

import java.util.Arrays;
import org.hapiserver.source.cdaweb.Adapter;

/**
 * Create a variable by extracting a subset (slice) of a multidimensional array.
;          Works on variables up to 7 dimensions.
* 
* pitch_angle is 64,96.
* pitch_angle_median is 64
* 
 * <ul>
 * <li>vap+hapi:http://localhost:8280/HapiServer/hapi?id=FA_ESA_L2_EEB&parameters=Time,energy_median&timerange=2009-04-30T05:32:25Z/2009-04-30T05:47:04Z
 * <li>vap+hapi:http://localhost:8280/HapiServer/hapi?id=MVN_SWE_L2_SVYPAD&parameters=Time,
 * </ul>
 * @author jbf
 */
public class ArrSlice extends Adapter {

    Adapter slicable;
    int sliceIndex; // ARR_INDEX in CDF file
    int sliceDim; // ARR_DIM in CDF file
    int offs1;
    int len1;
    int offs0;
    int len0;
    double[] buf;
    
    public ArrSlice( Adapter slicable, int[] qube, int sliceDim, int sliceIndex ) {
        this.slicable= slicable;
        this.sliceDim= sliceDim;
        this.sliceIndex= sliceIndex;
        if ( this.sliceDim==2 ) {
            int len0= 1;
            for ( int i=1; i<qube.length; i++ ) {
                len0=len0 * qube[i];
            }
            this.offs1= len0 *sliceIndex ;
            this.len1= len0;
        } else if ( this.sliceDim==1 ) {
            this.len0= qube[1];
            this.offs0= sliceIndex;
            for ( int i=2; i<qube.length; i++ ) {
                len0=len0 * qube[i];
            }
            buf= new double[qube[0]];
        }
    }
    
    @Override
    public String getString(int index) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public double[] adaptDoubleArray(int index) {
        double[] slicableArray= slicable.adaptDoubleArray(index);
        if ( sliceDim==2 ) {
            return Arrays.copyOfRange( slicableArray, offs1, offs1+len1 );
        } else if ( sliceDim==1 ) {
            for ( int i=0; i<buf.length; i++ ) {
                buf[i]= slicableArray[i*len0+offs0];
            }
            return buf;
        } else {
            // TODO: implement me!
            throw new IllegalArgumentException("not implemented");
        }
    }
    
    
}
