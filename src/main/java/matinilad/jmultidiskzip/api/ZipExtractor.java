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
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

    private ZipInputStream checksumsZip = null;

    public ZipExtractor(ZipInputStream input, Path output) {
        this.input = Objects.requireNonNull(input, "input is null");
        this.output = Objects.requireNonNull(output, "output is null");
    }

    protected boolean onShouldInterrupt() {
        return Thread.interrupted();
    }

    protected void onFile(Path file) {

    }

    protected void onFileProgress(Path file, long currentBytes, long totalBytes) {

    }

    protected void onFileError(Path file, IOException reason) {

    }

    protected boolean onShouldReplaceFile(Path file, long size) {
        return true;
    }

    public void extract(ZipChecksumTester tester) throws IOException, InterruptedException {
        this.checksumsZip = null;

        if (!Files.exists(this.output)) {
            Files.createDirectories(this.output);
            if (!Files.isDirectory(this.output)) {
                throw new IOException("failed to create output directory!");
            }
        } else if (!Files.isDirectory(this.output)) {
            throw new IOException("output path is not a directory!");
        }

        ZipEntry entry;
        while ((entry = this.input.getNextEntry()) != null) {
            if (onShouldInterrupt()) {
                throw new InterruptedException();
            }

            if (entry.getName().equals(ZipCreator.CHECKSUMS_ZIP_FILENAME)) {
                this.checksumsZip = new ZipInputStream(new ByteArrayInputStream(this.input.readAllBytes()), StandardCharsets.UTF_8);
                continue;
            }

            Path entryPath = this.output.resolve(getEntryPath(entry.getName()));
            onFile(entryPath);

            FileTime fallback = FileTime.from(Instant.now());
            FileTime created = Objects.requireNonNullElse(entry.getCreationTime(), fallback);
            FileTime modified = Objects.requireNonNullElse(entry.getLastModifiedTime(), fallback);
            FileTime access = Objects.requireNonNullElse(entry.getLastAccessTime(), fallback);

            if (entry.isDirectory()) {
                if (!Files.exists(entryPath)) {
                    Files.createDirectories(entryPath);
                }
                if (!Files.isDirectory(entryPath)) {
                    onFileError(entryPath, new IOException("failed to create directory"));
                    continue;
                }

                BasicFileAttributeView view = Files.getFileAttributeView(entryPath, BasicFileAttributeView.class);
                view.setTimes(modified, access, created);
                continue;
            }

            Path parent = entryPath.getParent();
            if (parent != null) {
                if (!Files.exists(parent)) {
                    Files.createDirectories(parent);
                }
                if (!Files.isDirectory(parent)) {
                    onFileError(entryPath, new IOException("failed to create parent directory"));
                    continue;
                }
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
                try (OutputStream out = Files.newOutputStream(entryPath)) {
                    byte[] buffer = new byte[16384];
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
                view.setTimes(modified, access, created);
            } catch (IOException ex) {
                onFileError(entryPath, ex);
            }
        }
        
        if (tester != null && this.checksumsZip != null) {
            tester.test(this.output, this.checksumsZip);
        }
    }

}
