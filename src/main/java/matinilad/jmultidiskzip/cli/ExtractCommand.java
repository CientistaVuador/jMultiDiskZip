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
package matinilad.jmultidiskzip.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Scanner;
import java.util.zip.ZipInputStream;
import matinilad.jmultidiskzip.api.utils.PartInputStream;
import matinilad.jmultidiskzip.api.ZipChecksumTester;
import matinilad.jmultidiskzip.api.ZipExtractor;
import matinilad.jmultidiskzip.api.compression.CompressionAlgorithm;
import matinilad.jmultidiskzip.api.compression.CompressionAlgorithmFactory;

/**
 *
 * @author Cien
 */
public class ExtractCommand {

    private static void printHelp(PrintStream out) {
        out.println("Arguments (Can be used in any order):");
        out.println("-in [part one] - Sets the part one file input (e.g: ./dir/name.001) [REQUIRED]");
        out.println("-out [directory] - Sets the output directory, files will be extracted to this directory [REQUIRED]");
        out.println("-noVerify - Disables file integrity verification [NOT REQUIRED]");
    }

    public static void run(PrintStream out, String[] args) throws Exception {
        if (args.length == 0) {
            printHelp(out);
            return;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("-help")) {
            printHelp(out);
            return;
        }

        Path partOne = null;
        Path outputDirectory = null;
        boolean verifyFiles = false;

        for (int i = 0; i < args.length; i++) {
            String argument = args[i].toLowerCase();
            String nextArgument = null;
            if ((i + 1) < args.length) {
                nextArgument = args[i + 1];
            }

            switch (argument) {
                case "-noverify" -> {
                    verifyFiles = true;
                    continue;
                }
            }

            if (nextArgument == null) {
                out.println("A argument is required for " + argument);
                out.println("Type -help for a list of arguments");
                return;
            }

            i++;

            switch (argument) {
                case "-in" -> {
                    if (partOne != null) {
                        out.println("Input file already set!");
                        return;
                    }
                    try {
                        partOne = Path.of(nextArgument);
                    } catch (InvalidPathException ex) {
                        out.println("Invalid path: " + nextArgument);
                        ex.printStackTrace(out);
                        return;
                    }
                }
                case "-out" -> {
                    if (outputDirectory != null) {
                        out.println("Output directory already set!");
                        return;
                    }
                    try {
                        outputDirectory = Path.of(nextArgument);
                    } catch (InvalidPathException ex) {
                        out.println("Invalid path: " + nextArgument);
                        ex.printStackTrace(out);
                        return;
                    }
                }
                default -> {
                    out.println("Unknown argument: " + argument);
                    out.println("Type -help for a list of arguments");
                    return;
                }
            }
        }

        if (partOne == null) {
            out.println("A input file is required");
            return;
        }

        if (outputDirectory == null) {
            out.println("A output directory is required");
            return;
        }

        try {
            extract(out, partOne, outputDirectory, verifyFiles);
        } catch (IOException ex) {
            out.println("Operation failed!");
            ex.printStackTrace(out);
        } catch (InterruptedException ex) {
            out.println("Canceled");
        }
    }

    private static InputStream getDecompressedStream(InputStream in) throws IOException {
        PushbackInputStream pushBack = new PushbackInputStream(in, 32);

        byte[] magicBytes = pushBack.readNBytes(32);
        pushBack.unread(magicBytes);

        String magic = HexFormat.of().formatHex(magicBytes);
        CompressionAlgorithm compression = CompressionAlgorithmFactory.getDefault().fromMagicNumber(magic);
        if (compression == null) {
            return pushBack;
        }

        return compression.decompress(pushBack);
    }

    private static void extract(
            PrintStream out,
            Path partOne,
            Path outputDirectory,
            boolean verifyFiles
    ) throws IOException, InterruptedException {
        Scanner scanner = new Scanner(System.in);

        try (PartInputStream in = new PartInputStream(partOne) {
            @Override
            protected void onWaitingForNextPart(Path requiredPart) {
                Path path = null;
                while (path == null) {
                    out.println("Please insert the directory for the next part: " + requiredPart.getFileName().toString());
                    out.print(">");
                    String input = scanner.nextLine();
                    if (input.isEmpty()) {
                        continueSignal(null, false);
                        return;
                    }
                    try {
                        path = Path.of(input);
                        continueSignal(path, false);
                    } catch (InvalidPathException ex) {
                        out.println("Invalid path: "+input);
                        ex.printStackTrace(out);
                    }
                }
            }
        }) {
            try (InputStream compressed = getDecompressedStream(in)) {
                try (ZipInputStream zip = new ZipInputStream(compressed, StandardCharsets.UTF_8)) {
                    ZipExtractor extractor = new ZipExtractor(zip, outputDirectory) {
                        @Override
                        protected void onFile(Path file) {
                            out.println(file.toString());
                        }
                        
                        @Override
                        protected void onFileError(Path file, IOException reason) {
                            out.println("Error on: " + file.toString());
                            reason.printStackTrace(System.out);
                        }
                    };
                    ZipChecksumTester tester = null;
                    if (verifyFiles) {
                        tester = new ZipChecksumTester() {
                            @Override
                            protected void onFile(Path file) {
                                out.println("Verifying " + file.toString());
                            }

                            @Override
                            protected void onFileError(Path file, IOException reason) {
                                out.println("Failed " + file.toString());
                                reason.printStackTrace(System.out);
                            }
                        };
                    }
                    extractor.extract(tester);
                }
            }
        }
    }

}
