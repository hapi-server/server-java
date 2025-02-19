package org.hapiserver.source;

import hapi.cache.InputStreamProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * InputStreamProvider which builds a cache file as it reads the data
 * @author jbf
 */
public class BuildCacheInputStreamProvider implements InputStreamProvider {

    InputStream ins;
    OutputStream out;
    File cacheFile;
    File tmpCacheFile;

    public BuildCacheInputStreamProvider(InputStreamProvider insProvider, File cacheFile) throws FileNotFoundException, IOException {
        this.cacheFile = cacheFile;
        this.tmpCacheFile = new File(cacheFile.getAbsolutePath() + "." + Thread.currentThread().getName());
        this.out = new FileOutputStream(tmpCacheFile);
        this.ins = insProvider.openInputStream();
    }

    @Override
    public InputStream openInputStream() {
        return new TeeInputStream();
    }

    private class TeeInputStream extends InputStream {

        @Override
        public int read() throws IOException {
            int i = ins.read();
            out.write(i);
            return i;
        }

        @Override
        public int read(byte[] b) throws IOException {
            int bytesRead = ins.read(b);
            if (bytesRead > 0) {
                out.write(b, 0, bytesRead);
            }
            return bytesRead;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int bytesRead = ins.read(b, off, len);
            if (bytesRead > 0) {
                out.write(b, off, bytesRead);
            }
            return bytesRead;
        }

        @Override
        public void close() throws IOException {
            ins.close();
            out.close();
            tmpCacheFile.renameTo(cacheFile);
        }
    }
}
