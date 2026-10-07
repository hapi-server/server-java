
package org.hapiserver;

/**
 * Introduced to have one switch for debugging.
 * @author jbf
 */
public class Config {
    private static final boolean DEBUGGING= false;
    
    public static boolean getDebugging() {
        return DEBUGGING;
    }
}
