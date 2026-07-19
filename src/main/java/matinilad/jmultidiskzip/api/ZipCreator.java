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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

/**
 *
 * @author Cien
 */
public class ZipCreator {

    public static final String CHECKSUMS_ZIP_FILENAME = "jMultiDiskZip_checksums.zip";

    private final ZipOutputStream output;
    private final Path[] inputs;
    private final HashAlgorithm hash;

    private MessageDigest digest = null;
    private CRC32 crc = null;
    private ByteArrayOutputStream checksumsStream = null;
    private ZipOutputStream checksumsZip = null;

    public ZipCreator(ZipOutputStream output, Path[] inputs, HashAlgorithm hash) {
        this.output = Objects.requireNonNull(output, "output is null");
        this.inputs = Objects.requireNonNull(inputs, "inputs is null");
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] == null) {
                throw new NullPointerException("input at index " + i + " is null");
            }
        }
        this.hash = hash;
    }

    protected void onEntry(ZipEntry entry) {

    }

    protected void onFileRejected(Path file, IOException reason) {

    }

    private void init() throws IOException {
        if (this.hash != null) {
            try {
                this.digest = MessageDigest.getInstance(this.hash.getAlgorithm());
            } catch (NoSuchAlgorithmException ex) {
                throw new IOException(ex);
            }
        } else {
            this.digest = null;
        }

        this.crc = new CRC32();

        if (this.hash != null) {
            this.checksumsStream = new ByteArrayOutputStream();
            this.checksumsZip = new ZipOutputStream(this.checksumsStream, StandardCharsets.UTF_8);
        } else {
            this.checksumsStream = null;
            this.checksumsZip = null;
        }
    }

    private String createEntryName(Path root, Path file) {
        String entryName = "";

        Path entryPath = root.relativize(file);
        for (int i = 0; i < entryPath.getNameCount(); i++) {
            entryName += entryPath.getName(i).toString();
            if (i != (entryPath.getNameCount() - 1)) {
                entryName += "/";
            }
        }

        if (Files.isDirectory(file)) {
            entryName += "/";
        }

        return entryName;
    }

    private void writeToZip(Path root, Path file) throws IOException {
        String entryName = createEntryName(root, file);

        if (entryName.equals(CHECKSUMS_ZIP_FILENAME)) {
            onFileRejected(file, new IOException("file named " + CHECKSUMS_ZIP_FILENAME + " is not allowed."));
            return;
        }

        if (!Files.exists(file)) {
            onFileRejected(file, new IOException("file does not exists"));
            return;
        }

        if (!Files.isReadable(file)) {
            onFileRejected(file, new IOException("file is not readable"));
            return;
        }

        boolean isFile = Files.isRegularFile(file);

        if (!isFile && !Files.isDirectory(file)) {
            onFileRejected(file, new IOException("file type is unknown"));
            return;
        }

        ZipEntry entry = new ZipEntry(entryName);

        entry.setMethod(ZipEntry.STORED);

        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);

            entry.setCreationTime(attributes.creationTime());
            entry.setLastModifiedTime(attributes.lastModifiedTime());
            entry.setLastAccessTime(attributes.lastAccessTime());
        } catch (UnsupportedOperationException ex) {
            //todo
        }

        if (isFile) {
            long count = 0;

            this.crc.reset();
            if (this.digest != null) {
                this.digest.reset();
            }

            try {
                try (InputStream in = Files.newInputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int r;
                    while ((r = in.read(buffer, 0, buffer.length)) != -1) {
                        count += r;

                        this.crc.update(buffer, 0, r);
                        if (this.digest != null) {
                            this.digest.update(buffer, 0, r);
                        }
                    }
                }
            } catch (IOException ex) {
                onFileRejected(file, ex);
                return;
            }

            entry.setSize(count);
            entry.setCompressedSize(count);
            entry.setCrc(this.crc.getValue());
        } else {
            this.crc.reset();

            entry.setSize(0);
            entry.setCompressedSize(0);
            entry.setCrc(this.crc.getValue());
        }

        if (isFile) {
            try {
                this.output.putNextEntry(entry);

                try (InputStream in = Files.newInputStream(file)) {
                    byte[] buffer = new byte[16384];
                    int r;
                    while ((r = in.read(buffer, 0, buffer.length)) != -1) {
                        this.output.write(buffer, 0, r);
                    }
                }

                this.output.closeEntry();
                onEntry(entry);
            } catch (ZipException ex) {
                onFileRejected(file, ex);
                return;
            }
        }

        if (this.hash != null) {
            if (isFile) {
                byte[] hashHexBytes = HexFormat.of()
                        .formatHex(this.digest.digest()).getBytes(StandardCharsets.UTF_8);

                this.crc.reset();
                this.crc.update(hashHexBytes, 0, hashHexBytes.length);
                long crcValue = this.crc.getValue();

                ZipEntry hashEntry = new ZipEntry(entryName + "." + this.hash.getExtension());

                hashEntry.setMethod(ZipEntry.STORED);

                hashEntry.setCompressedSize(hashHexBytes.length);
                hashEntry.setSize(hashHexBytes.length);
                hashEntry.setCrc(crcValue);

                FileTime time = FileTime.fromMillis(System.currentTimeMillis());
                hashEntry.setCreationTime(time);
                hashEntry.setLastModifiedTime(time);
                hashEntry.setLastAccessTime(time);

                this.checksumsZip.putNextEntry(hashEntry);
                this.checksumsZip.write(hashHexBytes, 0, hashHexBytes.length);
                this.checksumsZip.closeEntry();
            } else {
                this.checksumsZip.putNextEntry(entry);
                this.checksumsZip.closeEntry();
            }
        }

        if (!isFile) {
            for (Path p : Files.list(file).toArray(Path[]::new)) {
                writeToZip(root, p);
            }

            try {
                this.output.putNextEntry(entry);
                this.output.closeEntry();
                
                onEntry(entry);
            } catch (ZipException ex) {
                onFileRejected(file, ex);
                return;
            }
        }
    }

    private void doFinal() throws IOException {
        try (this.output) {
            if (this.hash != null) {
                this.checksumsZip.close();
                byte[] checksumsData = this.checksumsStream.toByteArray();

                this.crc.reset();
                this.crc.update(checksumsData);
                long crcValue = this.crc.getValue();

                ZipEntry entry = new ZipEntry(CHECKSUMS_ZIP_FILENAME);

                entry.setMethod(ZipEntry.STORED);

                entry.setCompressedSize(checksumsData.length);
                entry.setSize(checksumsData.length);
                entry.setCrc(crcValue);

                FileTime time = FileTime.fromMillis(System.currentTimeMillis());
                entry.setCreationTime(time);
                entry.setLastModifiedTime(time);
                entry.setLastAccessTime(time);

                this.output.putNextEntry(entry);
                this.output.write(checksumsData, 0, checksumsData.length);
                this.output.closeEntry();
            }
        }
    }

    public void create() throws IOException {
        init();

        for (Path input : this.inputs) {
            try {
                Path realPath = input.toRealPath();
                input = realPath;
            } catch (IOException ex) {
                onFileRejected(input, ex);
                continue;
            }

            Path parent = input.getParent();
            if (parent == null) {
                if (!Files.isDirectory(input)) {
                    onFileRejected(input, new IOException("file has no parent and it's not a directory!"));
                    continue;
                }
                for (Path p : Files.list(input).toArray(Path[]::new)) {
                    writeToZip(input, p);
                }
                continue;
            }

            writeToZip(parent, input);
        }

        doFinal();
    }

}
