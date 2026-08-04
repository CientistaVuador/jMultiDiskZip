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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import matinilad.jmultidiskzip.api.utils.TempFileList;

/**
 *
 * @author Cien
 */
public class ZipExtractor {

    public static Path getEntryPath(String entryName) {
        Path entryPath = Path.of(entryName);
        if (entryPath.getNameCount() == 0) {
            throw new IllegalArgumentException("empty entry name");
        }
        if (entryPath.isAbsolute()) {
            throw new IllegalArgumentException("absolute entry is not allowed");
        }
        for (int i = 0; i < entryPath.getNameCount(); i++) {
            String name = entryPath.getName(i).toString();
            if (name.equals(".") || name.equals("..")) {
                throw new IllegalArgumentException("malicious entry containing . or .. detected");
            }
        }
        return entryPath;
    }

    private final ZipInputStream input;
    private final Path output;
    private final TempFileList createdFiles = new TempFileList();

    public ZipExtractor(ZipInputStream input, Path output) {
        this.input = Objects.requireNonNull(input, "input is null");
        this.output = Objects.requireNonNull(output, "output is null");
    }

    protected boolean onShouldInterrupt() {
        return Thread.interrupted();
    }

    protected void onFile(Path file, boolean directory, long expectedSize) {

    }

    protected void onFileProgress(Path file, long currentBytes, long totalBytes) {

    }

    protected void onFileError(Path file, IOException reason) {

    }

    protected boolean onShouldReplaceFile(Path file, long expectedSize) {
        return true;
    }

    private void addFallbackChecksum(ZipOutputStream out, ZipEntry entry) throws IOException {
        if (!entry.isDirectory()) {
            byte[] crc32 = HexFormat.of()
                    .formatHex(ByteBuffer.allocate(4).putInt((int) entry.getCrc()).array())
                    .getBytes(StandardCharsets.UTF_8);

            ZipEntry crc32Entry = new ZipEntry(entry.getName() + ".crc32");

            crc32Entry.setMethod(ZipEntry.STORED);
            crc32Entry.setSize(crc32.length);
            crc32Entry.setCompressedSize(crc32.length);

            CRC32 crc = new CRC32();
            crc.update(crc32);
            crc32Entry.setCrc(crc.getValue());

            out.putNextEntry(crc32Entry);
            out.write(crc32);
            out.closeEntry();
        } else {
            out.putNextEntry(entry);
            out.closeEntry();
        }
    }
    
    public void extract(ZipChecksumTester tester) throws IOException, InterruptedException {
        this.createdFiles.clearList();
        this.createdFiles.createDirectories(this.output);

        ZipInputStream checksumsZip = null;

        ByteArrayOutputStream fallbackChecksums = new ByteArrayOutputStream();
        ZipOutputStream fallbackChecksumsZip = new ZipOutputStream(new GZIPOutputStream(fallbackChecksums), StandardCharsets.UTF_8);

        List<Runnable> directoryTimestamps = new ArrayList<>();

        ZipEntry entry;
        while ((entry = this.input.getNextEntry()) != null) {
            if (onShouldInterrupt()) {
                throw new InterruptedException();
            }

            if (entry.getName().equals(ZipCreator.CHECKSUMS_ZIP_FILENAME)) {
                checksumsZip = new ZipInputStream(new GZIPInputStream(new ByteArrayInputStream(this.input.readAllBytes())), StandardCharsets.UTF_8);
                continue;
            }

            final Path entryPath = this.output.resolve(getEntryPath(entry.getName()));
            onFile(entryPath, entry.isDirectory(), entry.getSize());

            if (tester != null) {
                addFallbackChecksum(fallbackChecksumsZip, entry);
            }

            final FileTime fallback = FileTime.from(Instant.now());
            final FileTime created = Objects.requireNonNullElse(entry.getCreationTime(), fallback);
            final FileTime modified = Objects.requireNonNullElse(entry.getLastModifiedTime(), fallback);
            final FileTime access = Objects.requireNonNullElse(entry.getLastAccessTime(), fallback);

            if (entry.isDirectory()) {
                try {
                    this.createdFiles.createDirectories(entryPath);
                } catch (IOException ex) {
                    onFileError(entryPath, ex);
                    continue;
                }

                directoryTimestamps.add(() -> {
                    try {
                        BasicFileAttributeView view = Files.getFileAttributeView(entryPath, BasicFileAttributeView.class);
                        if (view == null) {
                            throw new IOException("failed to get attributes");
                        }
                        view.setTimes(modified, access, created);
                    } catch (IOException ex) {
                        onFileError(entryPath, new IOException("failed to set directory timestamps", ex));
                    }
                });
                continue;
            }

            try {
                this.createdFiles.createDirectories(entryPath.getParent());
            } catch (IOException ex) {
                onFileError(entryPath, ex);
                continue;
            }
            
            if (Files.exists(entryPath)) {
                if (Files.isDirectory(entryPath)) {
                    onFileError(entryPath, new IOException("path is a directory"));
                    continue;
                }
                if (!onShouldReplaceFile(entryPath, entry.getSize())) {
                    continue;
                }
            }

            try {
                long fileSize = entry.getSize();
                long count = 0;

                onFileProgress(entryPath, count, fileSize);
                try (OutputStream out = this.createdFiles.newOutputStream(entryPath)) {
                    byte[] buffer = new byte[1 * 1024 * 1024];
                    int r;
                    while ((r = this.input.read(buffer, 0, buffer.length)) != -1) {
                        if (onShouldInterrupt()) {
                            throw new InterruptedException();
                        }

                        out.write(buffer, 0, r);
                        count += r;
                        onFileProgress(entryPath, count, fileSize);
                    }
                }

                BasicFileAttributeView view = Files.getFileAttributeView(entryPath, BasicFileAttributeView.class);
                if (view == null) {
                    throw new IOException("failed to get attributes");
                }
                view.setTimes(modified, access, created);
            } catch (IOException ex) {
                onFileError(entryPath, ex);
            }
        }

        for (int i = (directoryTimestamps.size() - 1); i >= 0; i--) {
            directoryTimestamps.get(i).run();
        }

        fallbackChecksumsZip.close();
        if (tester != null) {
            if (checksumsZip == null) {
                checksumsZip = new ZipInputStream(new GZIPInputStream(new ByteArrayInputStream(fallbackChecksums.toByteArray())), StandardCharsets.UTF_8);
            }
            tester.test(this.output, checksumsZip);
        }
    }
    
    public void deleteFiles() {
        this.createdFiles.deleteFiles();
    }

}
