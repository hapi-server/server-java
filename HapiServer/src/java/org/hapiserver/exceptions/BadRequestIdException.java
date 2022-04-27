
package org.hapiserver.exceptions;

/**
 * exception for unknown ID, error 1406.
 * @author jbf
 */
public class BadRequestIdException extends HapiException {
    
    public BadRequestIdException( ) {
        super( 1406, "Bad request - unknown dataset id");
    }
    
    public BadRequestIdException( String extra ) {
        super( 1406, "Bad request - unknown dataset id", extra );
    }
    
    public BadRequestIdException( String msg, String id ) {
        super(1406,msg);
    }
}
