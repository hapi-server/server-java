
package org.hapiserver.source.cdaweb;

import gov.nasa.gsfc.spdf.cdfj.CDFException;
import gov.nasa.gsfc.spdf.cdfj.CDFReader;
import java.io.File;

/**
 * Demo bug where runtime error occurs when trying to read FEDU with reader.get(vname).  Note
 * Autoplot uses NIO to access the data, and this works fine.
 * @author jbf
 */
public class DemoBugCDFJ {
    public static void main( String[] args ) throws CDFException.ReaderError {
        File tmpFile= new File("/var/www/cdaweb/htdocs/sp_phys/data/erg/mepe/l2/3dflux/2024/erg_mepe_l2_3dflux_20240830_v01_01.cdf");
        CDFReader reader = new CDFReader(tmpFile.toString());
        reader.get("FEDU");
    }
}
