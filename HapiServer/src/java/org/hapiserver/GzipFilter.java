package org.hapiserver;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import javax.servlet.*;
import javax.servlet.http.*;

/**
 * Synchronous Servlet 3.1 gzip filter. Do not use with async/nonblocking responses.
 */
public final class GzipFilter implements Filter {

    @Override
    public void init(FilterConfig config) {
    }

    @Override
    public void destroy() {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        if (!"GET".equalsIgnoreCase(req.getMethod()) && !"POST".equalsIgnoreCase(req.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        if (req.getHeader("Range") != null || !acceptsGzip(req)) {
            chain.doFilter(request, response);
            return;
        }
        Response wrapped = new Response(res);
        try {
            chain.doFilter(request, wrapped);
        } finally {
            if (!req.isAsyncStarted()) {
                wrapped.finish();
            }
        }
    }

    private static boolean acceptsGzip(HttpServletRequest req) {
        double gzip = -1, wildcard = -1;
        Enumeration<String> headers = req.getHeaders("Accept-Encoding");
        while (headers.hasMoreElements()) {
            for (String part : headers.nextElement().split(",")) {
                String[] fields = part.trim().split(";");
                String name = fields[0].trim().toLowerCase(Locale.ROOT);
                if (!name.equals("gzip") && !name.equals("*")) {
                    continue;
                }
                double q = 1;
                boolean valid = true;
                for (int i = 1; i < fields.length; i++) {
                    String p = fields[i].trim();
                    if (p.toLowerCase(Locale.ROOT).startsWith("q=")) {
                        try {
                            q = Double.parseDouble(p.substring(2).trim());
                            if (!Double.isFinite(q) || q < 0 || q > 1) {
                                valid = false;
                            }
                        } catch (NumberFormatException e) {
                            valid = false;
                        }
                    }
                }
                if (!valid) {
                    q = 0;
                }
                if (name.equals("gzip")) {
                    gzip = q;
                } else {
                    wildcard = q;
                }
            }
        }
        return (gzip >= 0 ? gzip : wildcard) > 0;
    }

    private static final class Response extends HttpServletResponseWrapper {

        private final HttpServletResponse target;
        private final ByteArrayOutputStream pending = new ByteArrayOutputStream(8192);
        private ServletOutputStream output;
        private PrintWriter writer;
        private GZIPOutputStream gzip;
        private OutputStream sink;
        private boolean started, finished, error, flushing;
        private int status = 200;
        private String encoding;
        private Long length;
        private final Map<String, List<String>> headers = new LinkedHashMap<>();

        Response(HttpServletResponse target) {
            super(target);
            this.target = target;
        }

        private static String key(String s) {
            return s.toLowerCase(Locale.ROOT);
        }

        private void checkMutable() {
            if (started || target.isCommitted() || finished) {
                throw new IllegalStateException("Response committed");
            }
        }

        private boolean compressible() {
            if (error || status < 200 || status == 204 || status == 205 || status == 206 || status == 304
                    || headers.containsKey("content-encoding") || headers.containsKey("content-range")
                    || headers.containsKey("upgrade")) {
                return false;
            }
            String ct = getContentType();
            if (ct == null) {
                return true;
            }
            ct = ct.toLowerCase(Locale.ROOT);
            return ct.startsWith("text/") || ct.contains("json") || ct.contains("xml")
                    || ct.contains("javascript") || ct.contains("csv") || ct.contains("svg");
        }

        private void put(String name, String value, boolean append) {
            checkMutable();
            String k = key(name);
            if (k.equals("content-length")) {
                if (!append) {
                    try {
                        length = Long.parseLong(value);
                    } catch (NumberFormatException e) {
                        length = null;
                    }
                }
                return;
            }
            List<String> values = headers.get(k);
            if (!append || values == null) {
                values = new ArrayList<>();
                headers.put(k, values);
            }
            values.add(value);
        }

        @Override
        public void setHeader(String name, String value) {
            put(name, value, false);
        }

        @Override
        public void addHeader(String name, String value) {
            put(name, value, true);
        }

        @Override
        public void setIntHeader(String name, int value) {
            setHeader(name, Integer.toString(value));
        }

        @Override
        public void addIntHeader(String name, int value) {
            addHeader(name, Integer.toString(value));
        }

        @Override
        public void setDateHeader(String name, long value) {
            setHeader(name, Long.toString(value));
        }

        @Override
        public void addDateHeader(String name, long value) {
            addHeader(name, Long.toString(value));
        }

        @Override
        public void setContentLength(int value) {
            checkMutable();
            length = (long) value;
        }

        @Override
        public void setContentLengthLong(long value) {
            checkMutable();
            length = value;
        }

        @Override
        public boolean containsHeader(String name) {
            return key(name).equals("content-length") ? length != null : headers.containsKey(key(name));
        }

        @Override
        public String getHeader(String name) {
            if (key(name).equals("content-length")) {
                return length == null ? null : length.toString();
            }
            List<String> values = headers.get(key(name));
            return values == null || values.isEmpty() ? null : values.get(0);
        }

        @Override
        public Collection<String> getHeaders(String name) {
            String v = getHeader(name);
            if (key(name).equals("content-length")) {
                return v == null ? Collections.emptyList() : Collections.singletonList(v);
            }
            List<String> values = headers.get(key(name));
            return values == null ? Collections.emptyList() : Collections.unmodifiableList(values);
        }

        @Override
        public Collection<String> getHeaderNames() {
            List<String> names = new ArrayList<>(headers.keySet());
            if (length != null) {
                names.add("content-length");
            }
            return names;
        }

        @Override
        public void setStatus(int sc) {
            checkMutable();
            status = sc;
        }

        @Override
        public int getStatus() {
            return status;
        }

        @Override
        public void setContentType(String type) {
            if (type != null) {
                setHeader("Content-Type", type);
            }
        }

        @Override
        public String getContentType() {
            String t = getHeader("Content-Type");
            return t == null ? target.getContentType() : t;
        }

        @Override
        public void setCharacterEncoding(String charset) {
            checkMutable();
            encoding = charset;
        }

        @Override
        public String getCharacterEncoding() {
            return encoding == null ? target.getCharacterEncoding() : encoding;
        }

        @Override
        public void setBufferSize(int size) {
            checkMutable();
            target.setBufferSize(size);
        }

        @Override
        public int getBufferSize() {
            return target.getBufferSize();
        }

        @Override
        public boolean isCommitted() {
            return started || target.isCommitted();
        }

        @Override
        public ServletOutputStream getOutputStream() {
            if (writer != null) {
                throw new IllegalStateException("getWriter already called");
            }
            if (output == null) {
                output = new ServletOutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        byte[] one = {(byte) b};
                        write(one, 0, 1);
                    }

                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        if (finished || error) {
                            throw new IOException("Response closed");
                        }
                        if (started) {
                            sink.write(b, off, len);
                        } else {
                            pending.write(b, off, len);
                        }
                    }

                    @Override
                    public void flush() throws IOException {
                        Response.this.flushBuffer();
                    }

                    @Override
                    public void close() throws IOException {
                        Response.this.finish();
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(WriteListener listener) {
                        throw new UnsupportedOperationException("Nonblocking I/O unsupported");
                    }
                };
            }
            return output;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (output != null && writer == null) {
                throw new IllegalStateException("getOutputStream already called");
            }
            if (writer == null) {
                ServletOutputStream out = getOutputStream();
                String cs = getCharacterEncoding();
                writer = new PrintWriter(new OutputStreamWriter(out, Charset.forName(cs)));
            }
            return writer;
        }

        private void start() throws IOException {
            if (started) {
                return;
            }
            started = true;
            target.setStatus(status);
            boolean compress = compressible();
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                String k = entry.getKey();
                if (compress && (k.equals("content-encoding") || k.equals("content-length") || k.equals("vary"))) {
                    continue;
                }
                boolean first = true;
                for (String v : entry.getValue()) {
                    if (first) {
                        target.setHeader(k, v);
                    } else {
                        target.addHeader(k, v);
                    }
                    first = false;
                }
            }
            if (encoding != null) {
                target.setCharacterEncoding(encoding);
            }
            if (compress) {
                target.setHeader("Content-Encoding", "gzip");
                List<String> vary = headers.get("vary");
                boolean star = false, found = false;
                if (vary != null) {
                    for (String v : vary) {
                        for (String token : v.split(",")) {
                            if (token.trim().equals("*")) {
                                star = true;
                            }
                            if (token.trim().equalsIgnoreCase("Accept-Encoding")) {
                                found = true;
                            }
                        }
                    }
                }
                if (vary != null) {
                    for (String v : vary) {
                        target.addHeader("Vary", v);
                    }
                }
                if (!star && !found) {
                    target.addHeader("Vary", "Accept-Encoding");
                }
                gzip = new GZIPOutputStream(target.getOutputStream(), 8192, true);
                sink = gzip;
            } else {
                if (length != null) {
                    target.setContentLengthLong(length);
                }
                sink = target.getOutputStream();
            }
            pending.writeTo(sink);
            pending.reset();
        }

        @Override
        public void flushBuffer() throws IOException {
            if (finished || flushing) {
                return;
            }
            if (writer != null && !flushing) {
                flushing = true;
                try {
                    writer.flush();
                } finally {
                    flushing = false;
                }
            }
            start();
            if (gzip != null) {
                gzip.flush();
            } else {
                sink.flush();
            }
            target.flushBuffer();
        }

        @Override
        public void resetBuffer() {
            checkMutable();
            pending.reset();
        }

        @Override
        public void reset() {
            checkMutable();
            pending.reset();
            headers.clear();
            length = null;
            encoding = null;
            status = 200;
            error = false;
            target.reset();
        }

        @Override
        public void sendError(int sc) throws IOException {
            sendError(sc, null);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            checkMutable();
            pending.reset();
            error = true;
            finished = true;
            if (msg == null) {
                target.sendError(sc);
            } else {
                target.sendError(sc, msg);
            }
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            checkMutable();
            pending.reset();
            error = true;
            finished = true;
            target.sendRedirect(location);
        }

        void finish() throws IOException {
            if (finished) {
                return;
            }
            if (writer != null && !flushing) {
                flushing = true;
                try {
                    writer.flush();
                } finally {
                    flushing = false;
                }
            }
            start();
            if (gzip != null) {
                gzip.finish();
            } else {
                sink.flush();
            }
            finished = true;
        }
    }
}
