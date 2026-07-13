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
package matinilad.jmultidiskzip.api;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 *
 * @author Cien
 */
public class PartInputStream extends InputStream {

    protected static Object[] splitPathData(Path part) {
        Objects.requireNonNull(part, "part path is null");

        Path fileName = part.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("part path has no name");
        }
        String fileNameString = fileName.toString();

        String[] fileNameExtensions = fileNameString.split(Pattern.quote("."));
        String extension = fileNameExtensions[fileNameExtensions.length - 1];
        int partNumber = Integer.parseInt(extension);
        int leadingZeros = extension.length() - Integer.toString(partNumber).length();
        if (leadingZeros != 0) {
            leadingZeros = extension.length();
        }

        return new Object[]{
            part.getParent(),
            fileNameString.substring(0, fileNameString.length() - (extension.length() + 1)),
            leadingZeros,
            partNumber
        };
    }

    private final Object lock = new Object();
    private volatile boolean waitingForSignal = false;
    private volatile Path nextDirectory = null;
    private volatile boolean nextCloseStream = false;

    private Path directory = null;
    private final String name;
    private final int leadingZeros;

    private int partNumber = 0;
    private InputStream partStream = null;

    private byte[] partHash = null;
    private MessageDigest partDigest = null;

    private boolean closed = false;

    public PartInputStream(Path partOne) {
        Object[] pathData = splitPathData(partOne);

        this.directory = (Path) pathData[0];
        this.name = (String) pathData[1];
        this.leadingZeros = (int) pathData[2];

        int number = (int) pathData[3];
        if (number != 1) {
            throw new IllegalArgumentException("Part number must be 1! Found: " + number);
        }
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
                //todo
                throw new IOException("checksum failed");
            }
        }

        if (this.partStream != null) {
            this.partStream.close();
            this.partStream = null;
        }
        this.partHash = null;
        this.partDigest = null;
    }

    private boolean nextPart() throws IOException {
        closePart();

        this.partNumber++;

        String partString = Integer.toString(this.partNumber);
        partString = "." + "0".repeat(Math.max(this.leadingZeros - partString.length(), 0)) + partString;

        Path partFile = this.directory.resolve(this.name + partString);
        if (!Files.isReadable(partFile)) {
            do {
                boolean closeStream;
                synchronized (this.lock) {
                    this.waitingForSignal = true;
                    onWaitingForNextPart(partFile);
                    if (this.waitingForSignal) {
                        try {
                            this.lock.wait();
                        } catch (InterruptedException ex) {
                            throw new IOException(ex);
                        }
                    }
                    if (this.nextDirectory != null) {
                        this.directory = this.nextDirectory;
                    }
                    closeStream = this.nextCloseStream;
                }
                if (closeStream) {
                    close();
                    return false;
                }
                partFile = this.directory.resolve(this.name + partString);
            } while (!Files.isReadable(partFile));
        }
        this.partStream = new BufferedInputStream(Files.newInputStream(partFile));

        for (HashAlgorithm hash : HashAlgorithm.values()) {
            Path hashFile = this.directory.resolve(this.name + partString + "." + hash.getExtension());
            if (Files.isRegularFile(hashFile)) {
                if (Files.size(hashFile) > 1024) {
                    continue;
                }
                try {
                    this.partHash = HexFormat.of().parseHex(Files.readString(hashFile, StandardCharsets.UTF_8).trim());
                    try {
                        this.partDigest = MessageDigest.getInstance(hash.getAlgorithm());
                    } catch (NoSuchAlgorithmException ex) {
                        throw new IOException(ex);
                    }
                    break;
                } catch (IllegalArgumentException ex) {
                    //todo
                }
            }
        }

        return true;
    }

    @Override
    public int read() throws IOException {
        if (this.closed) {
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
        this.closed = true;
    }

}
