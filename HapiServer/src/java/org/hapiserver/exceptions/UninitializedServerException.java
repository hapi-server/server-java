
package org.hapiserver.exceptions;

/**
 * Specific exception for when the server has not been initialized.  The
 * server is only initialized when the root request is made.
 * @author jbf
 */
public class UninitializedServerException extends RuntimeException {
    public UninitializedServerException( ) {
        super( "Server has not been initialized, its config folder is missing.");
    }
}
