package com.jspbook;

import java.io.IOException;
import java.util.zip.GZIPOutputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;

public class GZIPResponseStream extends ServletOutputStream {

    private final ServletOutputStream output;
    private final GZIPOutputStream gzip;
    private boolean closed = false;

    public GZIPResponseStream(HttpServletResponse response)
            throws IOException {

        response.setHeader("Content-Encoding", "gzip");
        response.setHeader("Vary", "Accept-Encoding");

        // Do not set Content-Length: compressed size is unknown.
        output = response.getOutputStream();

        // syncFlush=true allows flush() to push compressed
        // data to the underlying servlet output stream.
        gzip = new GZIPOutputStream(output, 8192, true);
    }

    private void checkOpen() throws IOException {
        if (closed) {
            throw new IOException("GZIP stream is closed");
        }
    }

    @Override
    public void write(int b) throws IOException {
        checkOpen();
        gzip.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len)
            throws IOException {
        checkOpen();
        gzip.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        checkOpen();
        gzip.flush();
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            gzip.close();
        }
    }

    @Override
    public boolean isReady() {
        return output.isReady();
    }

    @Override
    public void setWriteListener(WriteListener listener) {
        throw new UnsupportedOperationException(
            "Asynchronous servlet output is not supported");
    }
}
