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
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import matinilad.jmultidiskzip.api.HashAlgorithm;
import matinilad.jmultidiskzip.api.PartInputStream;
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

    private static void create(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: [Output File (Must end with .001)] [Part Size] [SHA-256/SHA-1/MD5/None] [Input 1] [Input 2] ...");
            return;
        }
        Path outputFile = Path.of(args[0]);
        long partSize = Long.parseLong(args[1]);
        HashAlgorithm hash = HashAlgorithm.fromAlgorithm(args[2]);
        
        List<Path> inputs = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int i = 3; i < args.length; i++) {
            Path p = Path.of(args[i]);
            if (!Files.exists(p)) {
                System.out.println("does not exists: "+p);
            }
            p = p.toRealPath();
            
            Path fileName = p.getFileName();
            if (fileName == null) {
                if (!Files.isDirectory(p)) {
                    System.out.println("file is root and it's not a directory! "+p);
                    continue;
                }
                for (Path e : Files.list(p).toList()) {
                    Path name = e.getFileName();
                    if (!names.add(name.toString())) {
                        System.out.println("file is duplicated: "+e);
                        continue;
                    }
                    inputs.add(e);
                }
                continue;
            }
            if (!names.add(fileName.toString())) {
                System.out.println("file is duplicated: "+p);
                continue;
            }
            inputs.add(p);
        }
        
        Files.createDirectories(outputFile.getParent());
        
        try (PartOutputStream out = new PartOutputStream(outputFile, partSize, hash)) {
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                try (ZipOutputStream zip = new ZipOutputStream(gzip, StandardCharsets.UTF_8)) {
                    for (Path input:inputs) {
                        writeFile(input.getParent(), input, hash, zip);
                    }
                }
            }
        }
    }

    private static void extract(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: [Input File (Must Start With .001)] [Output Directory]");
            return;
        }
        
        Scanner scanner = new Scanner(System.in);
        
        Path inputFile = Path.of(args[0]);
        Path outputDirectory = Path.of(args[1]);
        
        Files.createDirectories(outputDirectory);
        
        try (PartInputStream in = new PartInputStream(inputFile) {
            @Override
            protected void onWaitingForNextPart(Path requiredPart) {
                System.out.println("Please insert the directory for the next part: "+requiredPart.getFileName().toString());
                System.out.print(">");
                String input = scanner.nextLine();
                if (input.isEmpty()) {
                    continueSignal(null, false);
                    return;
                }
                continueSignal(Path.of(input), false);
            }
        }) {
            try (GZIPInputStream gzip = new GZIPInputStream(in)) {
                try (ZipInputStream zip = new ZipInputStream(gzip, StandardCharsets.UTF_8)) {
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        Path entryPath = outputDirectory.resolve(entry.getName());
                        
                        System.out.println(entry.getName()+" -> "+entryPath);
                        
                        Files.createDirectories(entryPath.getParent());
                        if (entry.isDirectory()) {
                            Files.createDirectory(entryPath);
                            continue;
                        }
                        
                        try (BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(entryPath))) {
                            byte[] buffer = new byte[4096];
                            int r;
                            while ((r = zip.read(buffer)) != -1) {
                                out.write(buffer, 0, r);
                            }
                        }
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: -create or -extract");
            return;
        }
        switch (args[0]) {
            case "-create" -> {
                create(Arrays.copyOfRange(args, 1, args.length));
            }
            case "-extract" -> {
                extract(Arrays.copyOfRange(args, 1, args.length));
            }
            default -> {
                System.out.println("Usage: -create or -extract");
                return;
            }
        }
    }

}
