
package hapi.cache;

import java.io.IOException;
import java.io.InputStream;

/**
 * This interface provides an InputStream when needed.
 * 
 * @author jbf
 */
public interface InputStreamProvider {
    InputStream openInputStream() throws IOException;
}
