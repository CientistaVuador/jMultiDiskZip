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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;
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

    protected void onIntegrityFailed(ZipEntry entry, Path file, IOException reason) {

    }

    protected void onIntegrity(ZipEntry entry, Path file) {

    }

    protected boolean shouldReplace(ZipEntry entry, Path file) {
        return true;
    }

    private Path getEntryPath(ZipEntry entry) throws IOException {
        Path entryPath = Path.of(entry.getName());
        if (entryPath.getNameCount() == 0) {
            throw new IOException("empty entry name");
        }
        if (entryPath.isAbsolute()) {
            throw new IOException("absolute entry is not allowed");
        }
        for (int i = 0; i < entryPath.getNameCount(); i++) {
            String name = entryPath.getName(i).toString();
            if (name.equals(".") || name.equals("..")) {
                throw new IOException("malicious entry containing . or .. detected");
            }
        }
        return entryPath;
    }

    private void verify() throws IOException {
        ZipEntry entry;
        while ((entry = this.checksumsZip.getNextEntry()) != null) {
            try {

            } catch (Throwable ex) {
                if (ex instanceof IOException io) {
                    onIntegrityFailed(entry, null, io);
                } else {
                    onIntegrityFailed(entry, null, new IOException(ex));
                }
            }
            Path entryPath;
            try {
                entryPath = getEntryPath(entry);
            } catch (IOException ex) {
                onIntegrityFailed(entry, null, ex);
                continue;
            }

            if (entry.isDirectory()) {
                Path resolved = this.output.resolve(entryPath);
                if (!Files.isDirectory(resolved)) {
                    onIntegrityFailed(entry, resolved, new IOException("not a directory"));
                }
                continue;
            }

            String fileName = entryPath.getFileName().toString();
            String[] extensions = fileName.split(Pattern.quote("."));
            HashAlgorithm hashAlgorithm = HashAlgorithm.fromExtension(extensions[extensions.length - 1]);

            if (hashAlgorithm == null) {
                onIntegrityFailed(entry, null, new IOException("no hash algorithm found for " + extensions[extensions.length - 1]));
                continue;
            }

            fileName = fileName.substring(0, fileName.length() - (extensions[extensions.length - 1].length() + 1));
            Path parent = entryPath.getParent();
            if (parent == null) {
                entryPath = Path.of(fileName);
            } else {
                entryPath = parent.resolve(fileName);
            }

            String hashString = new String(this.checksumsZip.readNBytes(1024), StandardCharsets.UTF_8).trim();
            byte[] hash;
            try {
                hash = HexFormat.of().parseHex(hashString);
            } catch (IllegalArgumentException ex) {
                onIntegrityFailed(entry, null, new IOException(ex));
                continue;
            }

            Path resolved = this.output.resolve(entryPath);
            byte[] otherHash;
            try {
                MessageDigest digest = MessageDigest.getInstance(hashAlgorithm.getAlgorithm());

                try (InputStream in = Files.newInputStream(resolved)) {
                    byte[] buffer = new byte[4096];
                    int r;
                    while ((r = in.read(buffer, 0, buffer.length)) != -1) {
                        digest.update(buffer, 0, r);
                    }
                }

                otherHash = digest.digest();
            } catch (NoSuchAlgorithmException | IOException ex) {
                onIntegrityFailed(entry, resolved, new IOException(ex));
                continue;
            }

            if (!MessageDigest.isEqual(hash, otherHash)) {
                HexFormat hex = HexFormat.of();
                onIntegrityFailed(entry, resolved, new IOException("hash is not equal, expected " + hex.formatHex(hash) + " found " + hex.formatHex(otherHash)));
                continue;
            }

            onIntegrity(entry, resolved);
        }
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

            Path entryPath;
            try {
                entryPath = getEntryPath(entry);
            } catch (IOException ex) {
                onEntryFailed(entry, null, ex);
                continue;
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
