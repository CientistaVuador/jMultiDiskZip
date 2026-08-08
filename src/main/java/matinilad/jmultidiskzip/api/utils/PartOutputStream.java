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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import matinilad.jmultidiskzip.api.checksum.Checksum;
import matinilad.jmultidiskzip.api.checksum.ChecksumAlgorithm;

/**
 *
 * @author Cien
 */
public class PartOutputStream extends OutputStream {

    public static final String EXTENSION = "001";
    
    private static String reverse(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = (s.length() - 1); i >= 0; i--) {
            b.append(s.charAt(i));
        }
        return b.toString();
    }
    
    private static String[] split(String filename) {
        String[] split = reverse(filename).split("\\.", 2);
        String name = reverse(split[split.length - 1]);
        String extension = null;
        if (split.length > 1) {
            extension = reverse(split[0]);
        }
        return new String[] {name, extension};
    }
    
    private static int getPartNumber(String extension) {
        int partNumber;
        if (extension != null) {
            try {
                partNumber = Integer.parseInt(extension);
                if (partNumber <= 0) {
                    partNumber = -1;
                }
            } catch (NumberFormatException ex) {
                partNumber = -1;
            }
        } else {
            partNumber = -1;
        }
        return partNumber;
    }
    
    private static String getPartExtension(String extension, int oldPartNumber, int newPartNumber) {
        int leadingZeros = 3;
        if (oldPartNumber != -1) {
            String old = Integer.toString(oldPartNumber);
            leadingZeros = Math.min(leadingZeros, old.length());
            if (old.length() != extension.length()) {
                leadingZeros = extension.length();
            }
        }
        String newNumber = Integer.toString(newPartNumber);
        return "0".repeat(Math.max(leadingZeros - newNumber.length(), 0)) + newNumber;
    }
    
    private static Path resolve(Path oldPath, String name, String extension) {
        Path parent = oldPath.getParent();
        if (parent == null) {
            return oldPath.getFileSystem().getPath(name+"."+extension);
        }
        return parent.resolve(name+"."+extension);
    }
    
    public static Path getFirstPart(Path part) {
        if (part == null) {
            throw new NullPointerException("part is null");
        }
        Path filenamePath = part.getFileName();
        if (filenamePath == null) {
            throw new IllegalArgumentException("no filename: "+part.toString());
        }
        String filename = filenamePath.toString();
        
        String[] split = split(filename);
        String name = split[0];
        String extension = split[1];
        
        int partNumber = getPartNumber(extension);
        if (partNumber == 1) {
            return part;
        }
        if (partNumber == -1 && extension != null) {
            name += "." + extension;
            extension = null;
        }
        extension = getPartExtension(extension, partNumber, 1);
        
        return resolve(part, name, extension);
    }
    
    public static Path getNextPart(Path part) {
        if (part == null) {
            throw new NullPointerException("part is null");
        }
        Path filenamePath = part.getFileName();
        if (filenamePath == null) {
            throw new IllegalArgumentException("no filename: "+part.toString());
        }
        String filename = filenamePath.toString();
        
        String[] split = split(filename);
        String name = split[0];
        String extension = split[1];
        
        int partNumber = getPartNumber(extension);
        if (partNumber == -1 && extension != null) {
            name += "." + extension;
            extension = null;
        }
        extension = getPartExtension(extension, partNumber, (partNumber == -1 ? 1 : partNumber + 1));
        
        return resolve(part, name, extension);
    }
    
    public static int getPartNumber(Path part) {
        if (part == null) {
            throw new NullPointerException("part is null");
        }
        Path filenamePath = part.getFileName();
        if (filenamePath == null) {
            return -1;
        }
        return getPartNumber(split(filenamePath.toString())[1]);
    }
    
    private final long partSize;
    private final ChecksumAlgorithm hashAlgorithm;
    private final Checksum digest;
    private final TempFileList createdFiles = new TempFileList();
    
    private Path currentPart = null;
    private OutputStream output = null;
    private long count = 0;
    
    private boolean closed = false;

    public PartOutputStream(Path partOne, long partSize, ChecksumAlgorithm hashAlgorithm) {
        this.currentPart = getFirstPart(partOne);
        this.partSize = partSize;
        this.hashAlgorithm = hashAlgorithm;
        
        if (this.hashAlgorithm != null) {
            this.digest = hashAlgorithm.newChecksum();
        } else {
            this.digest = null;
        }
    }
    
    private void closePart() throws IOException {
        if (this.output != null) {
            this.output.close();
            this.output = null;
            this.count = 0;

            if (this.digest != null) {
                Path checksumFile = this.currentPart.getFileSystem().getPath(this.currentPart.toString()+"."+this.hashAlgorithm.getExtension(0));
                Files.writeString(checksumFile, HexFormat.of().formatHex(this.digest.digest()), StandardCharsets.UTF_8);
                
                this.createdFiles.addFile(checksumFile);
            }
            
            this.currentPart = getNextPart(this.currentPart);
        }
    }

    private void nextPart() throws IOException {
        closePart();
        this.output = new BufferedOutputStream(this.createdFiles.newOutputStream(this.currentPart));
    }

    @Override
    public void write(int b) throws IOException {
        if (this.closed) {
            throw new IOException("stream is closed");
        }
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
        if (this.closed) {
            throw new IOException("stream is closed");
        }
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
    public void flush() throws IOException {
        if (this.closed) {
            throw new IOException("stream is closed");
        }
        if (this.output != null) {
            this.output.flush();
        }
    }

    public void deleteFiles() {
        this.createdFiles.deleteFiles();
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
