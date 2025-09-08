
package org.hapiserver;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * This can be inserted into an OutputStream to allow eavesdropping on the
 * stream.
 * @author jbf
 */

public class DebugOutputStream extends FilterOutputStream {
    public DebugOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void write(int b) throws IOException {
        System.err.printf("write(int): 0x%02X%n", b);
        super.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        System.err.printf("write(byte[], off=%d, len=%d): %s%n", 
                          off, len, new String(b, off, len));
        super.write(b, off, len);
    }

}
