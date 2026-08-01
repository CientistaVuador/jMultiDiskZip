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

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 *
 * @author Cien
 */
public class HexInputStream extends FilterInputStream {

    private static final int[] map = new int[256];

    static {
        Arrays.fill(map, -1);
        for (int i = 0; i < 10; i++) {
            map['0' + i] = i;
        }
        for (int i = 0; i < 6; i++) {
            map['A' + i] = 10 + i;
        }
        for (int i = 0; i < 6; i++) {
            map['a' + i] = 10 + i;
        }
    }

    private boolean closed = false;
    private boolean header = false;

    private final int[] pushback = new int[2];
    private boolean hasPushback = false;
    
    public HexInputStream(InputStream in) {
        super(Objects.requireNonNull(in, "in is null"));
    }

    private void readChecks() throws IOException {
        if (this.closed) {
            throw new IOException("stream is closed");
        }

        if (!this.header) {
            int b0 = this.in.read();
            if (b0 == -1) {
                this.header = true;
                return;
            }
            
            int b1 = this.in.read();
            if (b1 == -1) {
                throw new EOFException("unexpected eof, expected 0x");
            }

            if (!(b0 == '0' && (b1 == 'x' || b1 == 'X'))) {
                this.pushback[0] = b0 & 0xFF;
                this.pushback[1] = b1 & 0xFF;
                this.hasPushback = true;
            }
            this.header = true;
        }
    }

    private int processPushback() throws IOException {
        if (this.hasPushback) {
            int b0 = map[this.pushback[0]];
            int b1 = map[this.pushback[1]];
            if (b0 == -1) {
                throw new IOException("unknown character "+this.pushback[0]);
            }
            if (b1 == -1) {
                throw new IOException("unknown character "+this.pushback[1]);
            }
            this.hasPushback = false;
            return (b0 << 4) | (b1 << 0);
        }
        return -1;
    }
    
    @Override
    public int read() throws IOException {
        readChecks();
        
        int push = processPushback();
        if (push != -1) {
            return push;
        }
        
        int b0 = this.in.read();
        if (b0 == -1) {
            return -1;
        }
        int b1 = this.in.read();
        if (b1 == -1) {
            throw new EOFException("unexpected eof, expected another byte");
        }
        
        b0 = b0 & 0xFF;
        b1 = b1 & 0xFF;
        
        int p0 = map[b0];
        if (p0 == -1) {
            throw new IOException("unknown character "+b0);
        }
        int p1 = map[b1];
        if (p1 == -1) {
            throw new IOException("unknown character "+b1);
        }
        
        return (p0 << 4) | (p1 << 0);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);
        if (len == 0) {
            return 0;
        }
        readChecks();
        
        int push = processPushback();
        if (push != -1) {
            b[off] = (byte) push;
            off++;
            len--;
            if (len == 0) {
                return 1;
            }
        }
        
        byte[] readBuffer = new byte[len * 2];
        int readBufferLength = this.in.read(readBuffer);
        if (readBufferLength == -1) {
            return (push == -1 ? -1 : 1);
        }
        if (readBufferLength == 0) {
            return (push == -1 ? 0 : 1);
        }
        if (readBufferLength % 2 != 0) {
            int data = this.in.read();
            if (data == -1) {
                throw new EOFException("unexpected eof, expected another byte");
            }
            readBuffer[readBufferLength] = (byte) data;
            readBufferLength++;
        }
        
        for (int i = 0; i < readBufferLength / 2; i++) {
            int b0 = readBuffer[(i * 2) + 0] & 0xFF;
            int b1 = readBuffer[(i * 2) + 1] & 0xFF;
            
            int p0 = map[b0];
            int p1 = map[b1];
            if (p0 == -1) {
                throw new IOException("unknown character "+b0);
            }
            if (p1 == -1) {
                throw new IOException("unknown character "+b1);
            }
            
            b[off + i] = (byte) ((p0 << 4) | (p1 << 0));
        }
        
        return (readBufferLength / 2) + (push == -1 ? 0 : 1);
    }

    @Override
    public void close() throws IOException {
        if (this.closed) {
            return;
        }
        readChecks();
        this.closed = true;
        super.close();
    }

    @Override
    public long skip(long n) throws IOException {
        long remaining = n;
        int nr;

        if (n <= 0) {
            return 0;
        }

        int size = (int) Math.min(2048, remaining);
        byte[] skipBuffer = new byte[size];
        while (remaining > 0) {
            nr = read(skipBuffer, 0, (int) Math.min(size, remaining));
            if (nr < 0) {
                break;
            }
            remaining -= nr;
        }

        return n - remaining;
    }

    @Override
    public int available() throws IOException {
        return 0;
    }

    @Override
    public synchronized void mark(int readlimit) {

    }

    @Override
    public synchronized void reset() throws IOException {
        throw new IOException("mark/reset not supported");
    }

    @Override
    public boolean markSupported() {
        return false;
    }

}
