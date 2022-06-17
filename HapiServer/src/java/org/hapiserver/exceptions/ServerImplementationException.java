
package org.hapiserver.exceptions;

/**
 * catch-all exception indicating there is something wrong with the server implementation.
 * @author jbf
 */
public class ServerImplementationException extends RuntimeException {
    public ServerImplementationException( String message ) {
        super(message);
    }
}
