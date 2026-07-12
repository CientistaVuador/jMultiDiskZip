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
package matinilad.jmultidiskzip;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import matinilad.jmultidiskzip.api.HashAlgorithm;
import matinilad.jmultidiskzip.api.PartOutputStream;

/**
 *
 * @author Cien
 */
public class Main {

    private static void writeFile(Path root, Path path, HashAlgorithm hash, ZipOutputStream out) throws IOException, NoSuchAlgorithmException {
        Path relative = root.relativize(path);
        String entryName = "";
        for (int i = 0; i < relative.getNameCount(); i++) {
            entryName += relative.getName(i).toString();
            if (i != relative.getNameCount() - 1) {
                entryName += "/";
            }
        }

        if (entryName.isEmpty()) {
            if (Files.isDirectory(path)) {
                for (Path e : Files.list(path).toList()) {
                    writeFile(root, e, hash, out);
                }
            }
            return;
        }

        System.out.println(entryName);

        if (Files.isDirectory(path)) {
            entryName += "/";
            ZipEntry entry = new ZipEntry(entryName);
            entry.setMethod(ZipEntry.STORED);
            entry.setCompressedSize(0);
            entry.setSize(0);
            entry.setCrc(new CRC32().getValue());
            out.putNextEntry(entry);
            out.closeEntry();

            for (Path e : Files.list(path).toList()) {
                writeFile(root, e, hash, out);
            }
            return;
        }

        if (Files.isRegularFile(path)) {
            CRC32 crc = new CRC32();

            MessageDigest digest;
            if (hash != null) {
                digest = MessageDigest.getInstance(hash.getAlgorithm());
            } else {
                digest = null;
            }

            ZipEntry entry = new ZipEntry(entryName);
            entry.setMethod(ZipEntry.STORED);
            entry.setCompressedSize(Files.size(path));
            entry.setSize(Files.size(path));
            try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(path))) {
                byte[] buffer = new byte[1 * 1024 * 1024];
                int r;
                while ((r = in.read(buffer)) != -1) {
                    crc.update(buffer, 0, r);
                }
            }
            entry.setCrc(crc.getValue());
            out.putNextEntry(entry);
            try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(path))) {
                byte[] buffer = new byte[1 * 1024 * 1024];
                int r;
                while ((r = in.read(buffer)) != -1) {
                    out.write(buffer, 0, r);
                    if (digest != null) {
                        digest.update(buffer, 0, r);
                    }
                }
            }
            out.closeEntry();

            if (digest != null && hash != null) {
                crc.reset();

                byte[] hashData = HexFormat.of().formatHex(digest.digest()).getBytes(StandardCharsets.UTF_8);
                ZipEntry checksumEntry = new ZipEntry(entryName + "." + hash.getExtension());
                checksumEntry.setMethod(ZipEntry.STORED);
                checksumEntry.setCompressedSize(hashData.length);
                checksumEntry.setSize(hashData.length);
                crc.update(hashData);
                checksumEntry.setCrc(crc.getValue());
                out.putNextEntry(checksumEntry);
                out.write(hashData);
                out.closeEntry();
                
                System.out.println(checksumEntry.getName());
            }
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.out.println("Usage: [File Name] [Part Size] [SHA-256/SHA-1/MD5/None] [Input Directory] [Output Directory]");
            return;
        }
        String fileName = args[0];
        long partSize = Long.parseLong(args[1]);
        HashAlgorithm hash = HashAlgorithm.fromAlgorithm(args[2]);
        Path inputDirectory = Path.of(args[3]);
        Path outputDirectory = Path.of(args[4]);

        if (fileName.isBlank()) {
            System.out.println("fileName is empty!");
            return;
        }
        
        if (!Files.isDirectory(inputDirectory)) {
            System.out.println("Input directory is not a directory!");
            return;
        }

        Files.createDirectories(outputDirectory);

        try (PartOutputStream out = new PartOutputStream(outputDirectory, fileName + ".zip.gz", partSize, hash)) {
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                try (ZipOutputStream zip = new ZipOutputStream(gzip, StandardCharsets.UTF_8)) {
                    writeFile(inputDirectory, inputDirectory, hash, zip);
                }
            }
        }
    }

}
