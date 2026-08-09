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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import matinilad.jmultidiskzip.api.checksum.Checksum;
import matinilad.jmultidiskzip.api.checksum.ChecksumAlgorithm;
import matinilad.jmultidiskzip.api.checksum.ChecksumAlgorithmFactory;

/**
 *
 * @author Cien
 */
public class PartInputStream extends InputStream {

    private final Object lock = new Object();
    private volatile boolean waitingForSignal = false;
    private volatile Path nextDirectory = null;
    private volatile boolean nextCloseStream = false;

    private Path currentPart = null;
    private InputStream partStream = null;

    private byte[] partHash = null;
    private Checksum partDigest = null;

    private boolean streamClosed = false;
    private boolean closed = false;

    public PartInputStream(Path partOne) {
        this.currentPart = PartOutputStream.getFirstPart(partOne);
    }

    public void continueSignal(Path newDirectory, boolean closeStream) {
        synchronized (this.lock) {
            if (!this.waitingForSignal) {
                return;
            }
            this.waitingForSignal = false;
            this.nextDirectory = newDirectory;
            this.nextCloseStream = closeStream;
            this.lock.notify();
        }
    }

    protected void onWaitingForNextPart(Path requiredPart) {

    }

    private void closePart() throws IOException {
        if (this.partHash != null && this.partDigest != null) {
            if (this.partStream != null) {
                byte[] buffer = new byte[4096];
                int r;
                while ((r = this.partStream.read(buffer)) != -1) {
                    this.partDigest.update(buffer, 0, r);
                }
            }

            byte[] resultHash = this.partDigest.digest();
            if (!MessageDigest.isEqual(resultHash, this.partHash)) {
                HexFormat hex = HexFormat.of();
                throw new IOException("checksum failed for part " + this.currentPart + ", expected " + hex.formatHex(this.partHash) + " found " + hex.formatHex(resultHash));
            }
        }

        if (this.partStream != null) {
            this.partStream.close();
            this.partStream = null;
            this.currentPart = PartOutputStream.getNextPart(this.currentPart);
        }
        this.partHash = null;
        this.partDigest = null;
    }

    private boolean nextPart() throws IOException {
        closePart();

        try {
            if (Files.size(this.currentPart) <= 0) {
                throw new IOException();
            }
            this.partStream = new BufferedInputStream(Files.newInputStream(this.currentPart));
        } catch (IOException t) {
            while (true) {
                boolean closeStream;
                synchronized (this.lock) {
                    this.waitingForSignal = true;
                    onWaitingForNextPart(this.currentPart);
                    if (this.waitingForSignal) {
                        try {
                            this.lock.wait();
                        } catch (InterruptedException ex) {
                            throw new IOException(ex);
                        }
                    }
                    if (this.nextDirectory != null) {
                        this.currentPart = this.nextDirectory.resolve(this.currentPart.getFileName());
                    }
                    closeStream = this.nextCloseStream;
                }
                if (closeStream) {
                    this.streamClosed = true;
                    return false;
                }
                try {
                    if (Files.size(this.currentPart) <= 0) {
                        throw new IOException();
                    }
                    this.partStream = new BufferedInputStream(Files.newInputStream(this.currentPart));
                } catch (IOException ex) {
                    continue;
                }
                break;
            }
        }

        for (ChecksumAlgorithm hash : ChecksumAlgorithmFactory.getDefault().getAlgorithms()) {
            for (int i = 0; i < hash.getNumberOfExtensions(); i++) {
                Path hashFile = this.currentPart.getFileSystem().getPath(this.currentPart.toString() + "." + hash.getExtension(i));
                if (Files.isRegularFile(hashFile)) {
                    if (Files.size(hashFile) > 1024) {
                        continue;
                    }
                    try {
                        this.partHash = HexFormat.of().parseHex(Files.readString(hashFile, StandardCharsets.UTF_8).trim());
                        this.partDigest = hash.newChecksum();
                        break;
                    } catch (IllegalArgumentException ex) {
                        //ignore
                    }
                }
            }
        }

        return true;
    }

    @Override
    public int read() throws IOException {
        if (this.closed) {
            throw new IOException("stream is closed");
        }
        if (this.streamClosed) {
            return -1;
        }

        int r = -1;
        if (this.partStream != null) {
            r = this.partStream.read();
        }

        if (r == -1) {
            do {
                if (!nextPart()) {
                    return -1;
                }
            } while ((r = this.partStream.read()) == -1);
        }

        if (this.partDigest != null) {
            this.partDigest.update((byte) r);
        }
        return r;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (this.closed) {
            throw new IOException("stream is closed");
        }
        if (this.streamClosed) {
            return -1;
        }

        int r = -1;
        if (this.partStream != null) {
            r = this.partStream.read(b, off, len);
        }

        if (r == -1) {
            do {
                if (!nextPart()) {
                    return -1;
                }
            } while ((r = this.partStream.read(b, off, len)) == -1);
        }

        len = Math.min(len, r);

        if (this.partDigest != null) {
            this.partDigest.update(b, off, len);
        }
        return r;
    }

    @Override
    public void close() throws IOException {
        if (this.closed) {
            return;
        }
        closePart();
        continueSignal(null, true);
        this.closed = true;
    }

}
