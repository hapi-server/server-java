package com.jspbook;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * HttpServletResponse wrapper which compresses response data
 * using GZIPResponseStream.
 *
 * Data is compressed and transmitted incrementally.
 *
 * Call finishResponse() after the servlet has finished writing
 * to ensure the gzip trailer is written.
 */
public class GZIPResponseWrapper
        extends HttpServletResponseWrapper {

    private GZIPResponseStream stream;
    private PrintWriter writer;
    private boolean finished = false;

    public GZIPResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    protected ServletOutputStream createOutputStream()
            throws IOException {
        return new GZIPResponseStream(
                (HttpServletResponse) getResponse());
    }

    @Override
    public ServletOutputStream getOutputStream()
            throws IOException {

        if (writer != null) {
            throw new IllegalStateException(
                "getWriter() has already been called");
        }

        if (finished) {
            throw new IllegalStateException(
                "Response has already been finished");
        }

        if (stream == null) {
            stream = (GZIPResponseStream) createOutputStream();
        }

        return stream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {

        if (stream != null && writer == null) {
            throw new IllegalStateException(
                "getOutputStream() has already been called");
        }

        if (finished) {
            throw new IllegalStateException(
                "Response has already been finished");
        }

        if (writer == null) {
            if (stream == null) {
                stream = (GZIPResponseStream) createOutputStream();
            }

            String encoding = getCharacterEncoding();
            writer = new PrintWriter(
                new OutputStreamWriter(
                    stream, Charset.forName(encoding)));
        }

        return writer;
    }

    @Override
    public void flushBuffer() throws IOException {

        if (writer != null) {
            writer.flush();
            if (writer.checkError()) {
                throw new IOException("Error flushing GZIP writer");
            }
        } else if (stream != null) {
            stream.flush();
        }

        super.flushBuffer();
    }

    @Override
    public void setContentLength(int length) {
        // Compressed length is not known in advance.
    }

    @Override
    public void setContentLengthLong(long length) {
        // Compressed length is not known in advance.
    }

    /**
     * Finalize the gzip stream.
     *
     * This must be called once all response data has been written.
     */
    public void finishResponse() throws IOException {

        if (finished) {
            return;
        }

        finished = true;

        if (writer != null) {
            writer.flush();
            boolean error = writer.checkError();
            stream.close();
            if (error) {
                throw new IOException("Error writing GZIP response");
            }
        } else if (stream != null) {
            stream.close();
        }
    }
}
