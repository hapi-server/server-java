
package org.hapiserver.source.cdaweb.adapters;

import java.util.HashSet;
import java.util.Set;

/**
 * Utility functions for virtual variables.
 * @author jbf
 */
public class VirtualFunctions {
    
    private static final Set<String> virtualFunctions;
    static {
        virtualFunctions= new HashSet<>();
        virtualFunctions.add("add_1800");
        virtualFunctions.add("apply_esa_qflag");
        virtualFunctions.add("alternate_view");
        virtualFunctions.add("apply_esa_qflag"); 
        virtualFunctions.add("apply_fgm_qflag");
        virtualFunctions.add("apply_gmom_qflag");
        virtualFunctions.add("apply_rtn_qflag");
        virtualFunctions.add("comp_themis_epoch");
        virtualFunctions.add("comp_themis_epoch16");
        virtualFunctions.add("add_1800");
        virtualFunctions.add("apply_filter_flag");
        virtualFunctions.add("convert_log10");
        virtualFunctions.add("clamp_to_zero");
        virtualFunctions.add("alternate_view");
        virtualFunctions.add("arr_slice");
        
    }
    
    /**
     * return true if the virtual function is supported.
     * @param funct
     * @return true if the virtual function is supported.
     */
    public static boolean virtualFunctionSupported( String funct ) {
        return virtualFunctions.contains(funct);
    }
    
}
