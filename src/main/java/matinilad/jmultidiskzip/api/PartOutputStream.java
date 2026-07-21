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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 *
 * @author Cien
 */
public class PartOutputStream extends OutputStream {

    private final Path directory;
    private final String name;
    private final int leadingZeros;
    
    private final long partSize;
    private final MessageDigest digest;
    private final HashAlgorithm hashAlgorithm;
    
    private OutputStream output = null;
    private long count = 0;
    private int partNumber = 0;
    private String partString = "";
    
    public PartOutputStream(Path partOne, long partSize, HashAlgorithm hashAlgorithm) {
        Object[] pathData = PartInputStream.splitPathData(partOne);
        
        this.directory = (Path) pathData[0];
        this.name = (String) pathData[1];
        this.leadingZeros = (int) pathData[2];
        
        int number = (int) pathData[3];
        if (number != 1) {
            throw new IllegalArgumentException("Part number must be 1! Found: " + number);
        }
        
        if (partSize < 1) {
            throw new IllegalArgumentException("part size < 1");
        }
        
        this.partSize = partSize;
        this.hashAlgorithm = hashAlgorithm;

        if (this.hashAlgorithm != null) {
            try {
                this.digest = MessageDigest.getInstance(this.hashAlgorithm.getAlgorithm());
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalArgumentException(ex);
            }
        } else {
            this.digest = null;
        }
    }

    private Path createFile(String suffix) {
        return this.directory.resolve(this.name + suffix);
    }

    private void closePart() throws IOException {
        if (this.output != null) {
            this.output.close();
            this.output = null;
            this.count = 0;

            if (this.digest != null) {
                Path checksumFile = createFile(this.partString+"."+this.hashAlgorithm.getExtension());
                Files.writeString(checksumFile, HexFormat.of().formatHex(this.digest.digest()), StandardCharsets.UTF_8);
            }
        }
    }

    private void nextPart() throws IOException {
        closePart();

        this.partNumber++;

        this.partString = Integer.toString(this.partNumber);
        this.partString = "." + "0".repeat(Math.max(this.leadingZeros - this.partString.length(), 0)) + this.partString;
        this.output = new BufferedOutputStream(Files.newOutputStream(createFile(this.partString)));
    }
    
    @Override
    public void write(int b) throws IOException {
        if (this.output == null || this.count >= this.partSize) {
            nextPart();
        }
        this.output.write(b);
        this.count++;
        
        if (this.digest != null) {
            this.digest.update((byte) b);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (this.output == null) {
            nextPart();
        }

        int from = off;
        int to = off + len;

        while (from < to) {
            if (this.count >= this.partSize) {
                nextPart();
            }
            int toWrite = (int) Math.min(to - from, this.partSize - this.count);
            this.output.write(b, from, toWrite);
            if (this.digest != null) {
                this.digest.update(b, from, toWrite);
            }
            this.count += toWrite;
            from += toWrite;
        }
    }

    @Override
    public void close() throws IOException {
        closePart();
    }

}
