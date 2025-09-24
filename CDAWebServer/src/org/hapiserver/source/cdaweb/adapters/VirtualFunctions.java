
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
        virtualFunctions.add("add_1800"); // verified
        virtualFunctions.add("apply_esa_qflag"); // verified vap+hapi:http://localhost:8280/HapiServer/hapi?id=MMS1_FGM_BRST_L2@0&parameters=mms1_fgm_b_gse_brst_l2_clean&timerange=2025-08-16+14:19:43+to+14:22:13
        virtualFunctions.add("alternate_view"); // verified vap+hapi:http://localhost:8280/HapiServer/hapi?id=AMPTECCE_H0_MEPA@0&parameters=ION_protons_COUNTS_stack&timerange=1988-12-22+0:00+to+16:18
        virtualFunctions.add("apply_esa_qflag"); // vap+hapi:http://localhost:8280/HapiServer/hapi?id=MMS1_FGM_BRST_L2@0&parameters=mms1_fgm_b_gse_brst_l2_clean&timerange=2025-08-16+14:19:43+to+14:22:13
        virtualFunctions.add("apply_fgm_qflag"); // vap+hapi:http://localhost:8280/HapiServer/hapi?id=THA_L2_FGM@0&parameters=tha_fgs_btotalQ&timerange=2025-09-20
        virtualFunctions.add("apply_gmom_qflag"); // vap+hapi:http://localhost:8280/HapiServer/hapi?id=THA_L2_GMOM@0&parameters=tha_ptiff_densityQ&timerange=2025-09-19
        virtualFunctions.add("apply_rtn_qflag"); // vap+hapi:http://localhost:8280/HapiServer/hapi?id=MESSENGER_MAG_RTN@0&parameters=B_radial_q&timerange=2015-04-29+0:00+to+23:59
        virtualFunctions.add("comp_themis_epoch"); // Needs more study bc of NetCDF: vap+hapi:http://localhost:8280/HapiServer/hapi?id=DN_MAGN-L2-HIRES_G08&parameters=Time&timerange=2001-12-08+0:00+to+23:59
        virtualFunctions.add("comp_themis_epoch16"); // vap+hapi:http://localhost:8280/HapiServer/hapi?id=THA_L2_FGM@1&parameters=Time&timerange=2025-09-20
        virtualFunctions.add("add_1800"); // vap+hapi:http://localhost:8280/HapiServer/hapi?id=OMNI2_H0_MRG1HR&parameters=Time&timerange=2025-01-01+00:00+to+2025-06-30+23:00
        virtualFunctions.add("apply_filter_flag");  // NOT CHECKED vap+hapi:http://localhost:8280/HapiServer/hapi?id=MMS1_FPI_BRST_L2_DES-DIST&parameters=mms1_des_dist_brst1_even&timerange=2025-07-31+18:29:23+to+18:31:22
        virtualFunctions.add("convert_log10"); // vap+hapi:http://localhost:8280/HapiServer/hapi?id=IM_K0_EUV&parameters=IMAGE_LOG&timerange=2005-12-17+3:14+to+23:40
        virtualFunctions.add("clamp_to_zero"); // error!!!! vap+hapi:http://localhost:8280/HapiServer/hapi?id=RBSPA_REL04_ECT-MAGEIS-L3@0&parameters=FEDU_plasmagram&timerange=2019-10-13+0:00+to+23:59
        virtualFunctions.add("alternate_view");
        virtualFunctions.add("arr_slice"); // error!!! vap+hapi:http://localhost:8280/HapiServer/hapi?id=ERG_MEPE_L2_3DFLUX@0&parameters=FEDU_e2&timerange=2024-08-30+1:14+to+24:00
        
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
