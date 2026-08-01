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
package matinilad.jmultidiskzip.api.utils;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 *
 * @author Cien
 */
public class HexOutputStream extends FilterOutputStream {
    
    private static final byte[] map = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };
    
    private boolean closed = false;
    private boolean header = false;
    
    public HexOutputStream(OutputStream out) {
        super(Objects.requireNonNull(out, "out is null"));
    }
    
    private void writeChecks() throws IOException {
        if (this.closed) {
            throw new IOException("stream is closed");
        }
        if (!this.header) {
            this.out.write('0');
            this.out.write('x');
            this.header = true;
        }
    }
    
    @Override
    public void write(int b) throws IOException {
        writeChecks();
        this.out.write(map[(b & 0xF0) >>> 4]);
        this.out.write(map[(b & 0x0F) >>> 0]);
    }
    
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);
        writeChecks();
        if (len == 0) {
            return;
        }
        byte[] hex = new byte[len * 2];
        for (int i = 0; i < len; i++) {
            byte data = b[off + i];
            hex[(i * 2) + 0] = map[(data & 0xF0) >>> 4];
            hex[(i * 2) + 1] = map[(data & 0x0F) >>> 0];
        }
        this.out.write(hex, 0, hex.length);
    }
    
    @Override
    public void close() throws IOException {
        if (this.closed) {
            return;
        }
        writeChecks();
        this.closed = true;
        super.close();
    }
    
}
