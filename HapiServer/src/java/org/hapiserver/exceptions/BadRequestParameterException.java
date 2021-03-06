
package org.hapiserver.exceptions;

/**
 * the parameters control has a parameter which is not recognized
 * @author jbf
 */
public class BadRequestParameterException extends HapiException {

    public BadRequestParameterException( ) {
        super(1407, "Bad request - unknown dataset parameter" );
    }

    public BadRequestParameterException( String extra ) {
        super(1407, "Bad request - unknown dataset parameter", extra );
    }

}
