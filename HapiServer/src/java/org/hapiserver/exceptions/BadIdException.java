
package org.hapiserver.exceptions;

/**
 * exception for unknown ID.
 * @author jbf
 */
public class BadIdException extends RuntimeException {
    public BadIdException( String msg, String id ) {
        super(msg);
    }
}
