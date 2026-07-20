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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 *
 * @author Cien
 */
public class ZipChecksumTester {
    
    public ZipChecksumTester() {
        
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

    public void test(Path directory, ZipInputStream checksums) throws IOException, InterruptedException {
        Objects.requireNonNull(directory, "directory is null");
        Objects.requireNonNull(checksums, "checksums is null");
        
        HexFormat hex = HexFormat.of();

        ZipEntry entry;
        while ((entry = checksums.getNextEntry()) != null) {
            if (onShouldInterrupt()) {
                throw new InterruptedException();
            }
            
            Path entryPath = directory.resolve(ZipExtractor.getEntryPath(entry.getName()));
            
            if (entry.isDirectory()) {
                onFile(entryPath);
                if (!Files.isDirectory(entryPath)) {
                    onFileError(entryPath, new IOException("not a directory"));
                }
                continue;
            }

            String[] extensions = entryPath.getFileName().toString().split(Pattern.quote("."));
            String lastExtension = extensions[extensions.length - 1];
            
            String entryPathString = entryPath.toString();
            entryPath = Path.of(entryPathString.substring(0, entryPathString.length() - (lastExtension.length() + 1)));
            
            onFile(entryPath);

            HashAlgorithm hashAlgorithm = HashAlgorithm.fromExtension(lastExtension);
            if (hashAlgorithm == null) {
                onFileError(entryPath, new IOException("unknown hash extension"));
                continue;
            }

            String hashHex = new String(checksums.readNBytes(1024), StandardCharsets.UTF_8);
            byte[] hash;
            try {
                hash = hex.parseHex(hashHex.trim());
            } catch (IllegalArgumentException ex) {
                onFileError(entryPath, new IOException("invalid hash hex", ex));
                continue;
            }

            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance(hashAlgorithm.getAlgorithm());
            } catch (NoSuchAlgorithmException ex) {
                onFileError(entryPath, new IOException(ex));
                continue;
            }

            if (!Files.exists(entryPath)) {
                onFileError(entryPath, new IOException("file does not exists"));
                continue;
            }

            try {
                long fileSize = Files.size(entryPath);
                long count = 0;

                onFileProgress(entryPath, count, fileSize);
                try (InputStream in = Files.newInputStream(entryPath)) {
                    byte[] buffer = new byte[4096];
                    int r;
                    while ((r = in.read(buffer, 0, buffer.length)) != -1) {
                        if (onShouldInterrupt()) {
                            throw new InterruptedException();
                        }

                        digest.update(buffer, 0, r);
                        count += r;
                        onFileProgress(entryPath, count, fileSize);
                    }
                }
            } catch (IOException ex) {
                onFileError(entryPath, ex);
                continue;
            }

            byte[] fileHash = digest.digest();
            if (!MessageDigest.isEqual(fileHash, hash)) {
                onFileError(entryPath, new IOException("invalid hash! expected " + hex.formatHex(hash) + " found " + hex.formatHex(fileHash)));
            }
        }
    }
}
