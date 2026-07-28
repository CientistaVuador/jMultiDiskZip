/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <https://unlicense.org>
 */
package matinilad.jmultidiskzip.api.compression;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 *
 * @author Cien
 */
public class GZIPCompressionAlgorithm extends DefaultCompressionAlgorithm {

    private static class GZOutputStream extends GZIPOutputStream {
        public GZOutputStream(OutputStream out) throws IOException {
            super(out);
        }
        
        public void setLevel(int level) {
            this.def.setLevel(level);
        }
    }
    
    public GZIPCompressionAlgorithm() {
        super("GZIP", "gz", new String[] {"gz"}, Deflater.NO_COMPRESSION, Deflater.BEST_COMPRESSION + 1, 6, new String[] {"1F8B"});
    }
    
    @Override
    public GZIPOutputStream compress(OutputStream out, int level) throws IOException {
        Objects.requireNonNull(out, "out is null");
        checkLevel(level);
        
        GZOutputStream gz = new GZOutputStream(out);
        gz.setLevel(level);
        return gz;
    }

    @Override
    public GZIPOutputStream compress(OutputStream out) throws IOException {
        return compress(out, getDefaultCompressionLevel());
    }
    
    @Override
    public GZIPInputStream decompress(InputStream in) throws IOException {
        Objects.requireNonNull(in, "in is null");
        return new GZIPInputStream(in);
    }
    
}
