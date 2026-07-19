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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import matinilad.jmultidiskzip.api.HashAlgorithm;
import matinilad.jmultidiskzip.api.PartInputStream;
import matinilad.jmultidiskzip.api.PartOutputStream;
import matinilad.jmultidiskzip.api.ZipCreator;
import matinilad.jmultidiskzip.api.ZipExtractor;

/**
 *
 * @author Cien
 */
public class Main {
    
    private static void create(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: [Output File (Must end with .001)] [Part Size] [SHA-256/SHA-1/MD5/None] [Input 1] [Input 2] ...");
            return;
        }
        Path outputFile = Path.of(args[0]).toAbsolutePath();
        long partSize = Long.parseLong(args[1]);
        HashAlgorithm hash = HashAlgorithm.fromAlgorithm(args[2]);

        if (outputFile.getParent() != null) {
            Files.createDirectories(outputFile.getParent());
        }
        
        List<Path> inputs = new ArrayList<>();
        for (int i = 3; i < args.length; i++) {
            inputs.add(Path.of(args[i]));
        }
        
        try (PartOutputStream out = new PartOutputStream(outputFile, partSize, hash)) {
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                try (ZipOutputStream zip = new ZipOutputStream(gzip, StandardCharsets.UTF_8)) {
                    ZipCreator writer = new ZipCreator(zip, inputs.toArray(Path[]::new), hash) {
                        @Override
                        protected void onEntry(ZipEntry entry) {
                            System.out.println(entry.getName());
                        }
                    };
                    writer.create();
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
        
        try (PartInputStream in = new PartInputStream(inputFile) {
            @Override
            protected void onWaitingForNextPart(Path requiredPart) {
                System.out.println("Please insert the directory for the next part: " + requiredPart.getFileName().toString());
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
                    ZipExtractor extractor = new ZipExtractor(zip, outputDirectory) {
                        @Override
                        protected void onEntry(ZipEntry entry, Path file) {
                            System.out.println(file.toString());
                        }
                    };
                    extractor.extract(true);
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
