
package org.hapiserver.exceptions;

/**
 * base class for all HAPI error codes.
 * @author jbf
 */
public class HapiException extends Throwable {
    
    public int code;
    
    public HapiException(int code,String message) {
        super( message );
        this.code= code;
    }
    
    /**
     * return the code for the exception, from 
     * https://github.com/hapi-server/data-specification/blob/master/hapi-3.0.1/HAPI-data-access-spec-3.0.1.md#42-status-error-codes
     * @return 
     */
    public int getCode() {
        return this.code;
    }
    
}
