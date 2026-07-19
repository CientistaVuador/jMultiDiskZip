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

    private final ZipInputStream input;
    private final Path output;

    private ZipInputStream checksumsZip = null;

    public ZipExtractor(ZipInputStream input, Path output) {
        this.input = Objects.requireNonNull(input, "input is null");
        this.output = Objects.requireNonNull(output, "output is null");
    }

    protected void onEntryFailed(ZipEntry entry, Path file, IOException reason) {

    }
    
    protected void onEntry(ZipEntry entry, Path file) {
        
    }

    protected void onIntegrityFailed(Path file, IOException reason) {

    }

    protected boolean shouldReplace(ZipEntry entry, Path file) {
        return true;
    }

    private void verify() {

    }

    public void extract(boolean verifyIntegrity) throws IOException {
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
            if (entry.getName().equals(ZipCreator.CHECKSUMS_ZIP_FILENAME)) {
                this.checksumsZip = new ZipInputStream(new ByteArrayInputStream(this.input.readAllBytes()), StandardCharsets.UTF_8);
                continue;
            }
            
            Path entryPath = Path.of(entry.getName());
            if (entryPath.getNameCount() == 0) {
                onEntryFailed(entry, entryPath, new IOException("empty entry name"));
                continue;
            }
            if (entryPath.isAbsolute()) {
                onEntryFailed(entry, entryPath, new IOException("absolute entry is not allowed"));
                return;
            }
            for (int i = 0; i < entryPath.getNameCount(); i++) {
                String name = entryPath.getName(i).toString();
                if (name.equals(".") || name.equals("..")) {
                    onEntryFailed(entry, entryPath, new IOException("malicious entry containing . or .. detected"));
                    continue;
                }
            }

            FileTime fallback = FileTime.from(Instant.now());
            FileTime created = Objects.requireNonNullElse(entry.getCreationTime(), fallback);
            FileTime modified = Objects.requireNonNullElse(entry.getLastModifiedTime(), fallback);
            FileTime access = Objects.requireNonNullElse(entry.getLastAccessTime(), fallback);

            Path path = this.output.resolve(entryPath);
            if (entry.isDirectory()) {
                if (!Files.exists(path)) {
                    Files.createDirectories(path);
                }
                if (!Files.isDirectory(path)) {
                    onEntryFailed(entry, path, new IOException("failed to create directory"));
                    continue;
                }

                BasicFileAttributeView view = Files.getFileAttributeView(path, BasicFileAttributeView.class);
                view.setTimes(modified, access, created);
                
                onEntry(entry, path);
                continue;
            }

            Path parent = path.getParent();
            if (parent != null) {
                if (!Files.exists(parent)) {
                    Files.createDirectories(parent);
                }
                if (!Files.isDirectory(parent)) {
                    onEntryFailed(entry, parent, new IOException("failed to create parent directory"));
                    continue;
                }
            }

            if (Files.exists(path)) {
                if (Files.isDirectory(path)) {
                    onEntryFailed(entry, path, new IOException("path is a directory"));
                    continue;
                }
                if (!shouldReplace(entry, path)) {
                    continue;
                }
            }
            
            try {
                byte[] buffer = new byte[16384];
                try (OutputStream out = Files.newOutputStream(path)) {
                    int r;
                    while ((r = this.input.read(buffer, 0, buffer.length)) != -1) {
                        out.write(buffer, 0, r);
                    }
                }
                
                BasicFileAttributeView view = Files.getFileAttributeView(path, BasicFileAttributeView.class);
                view.setTimes(modified, access, created);
                
                onEntry(entry, path);
            } catch (IOException ex) {
                onEntryFailed(entry, path, ex);
            }
        }
        
        if (verifyIntegrity && this.checksumsZip != null) {
            verify();
        }
    }

}
