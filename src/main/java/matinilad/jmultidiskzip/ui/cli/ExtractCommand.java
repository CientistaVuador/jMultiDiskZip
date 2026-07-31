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
package matinilad.jmultidiskzip.ui.cli;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PushbackInputStream;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Scanner;
import java.util.zip.ZipInputStream;
import matinilad.jmultidiskzip.api.utils.PartInputStream;
import matinilad.jmultidiskzip.api.ZipChecksumTester;
import matinilad.jmultidiskzip.api.ZipExtractor;
import matinilad.jmultidiskzip.api.checksum.ChecksumAlgorithm;
import matinilad.jmultidiskzip.api.compression.CompressionAlgorithm;
import matinilad.jmultidiskzip.api.compression.CompressionAlgorithmFactory;
import matinilad.jmultidiskzip.api.utils.EncryptedInputStream;
import matinilad.jmultidiskzip.ui.ByteCountFormat;

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
        out.println("-decrypt - Use this if the part files are encrypted [NOT REQUIRED]");
        out.println("-zipInZip - Use this if the input file is a single zip file inside of a zip file [NOT REQUIRED]");
        out.println("-auto - Enables automatic mode, checks the part directory every few seconds instead of asking for a directory [NOT REQUIRED]");
        out.println("-verbose - Enables verbose output mode, otherwise, only errors will be displayed [NOT REQUIRED]");
        out.println("-replacePolicy [yesForAll/noForAll/ask] - If files should be replaced or not [DEFAULT IS ASK]");
    }

    public static void run(InputStream in, PrintStream out, String[] args) throws Exception {
        if (args.length == 0) {
            printHelp(out);
            return;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("-help")) {
            printHelp(out);
            return;
        }

        Scanner scanner = new Scanner(System.in);

        Path partOne = null;
        Path outputDirectory = null;
        boolean verifyFiles = true;
        boolean decrypt = false;
        boolean zipInZip = false;
        boolean auto = false;
        boolean verbose = false;
        int replacePolicy = 0;

        for (int i = 0; i < args.length; i++) {
            String argument = args[i].toLowerCase();
            String nextArgument = null;
            if ((i + 1) < args.length) {
                nextArgument = args[i + 1];
            }

            switch (argument) {
                case "-zipinzip" -> {
                    zipInZip = true;
                    continue;
                }
                case "-noverify" -> {
                    verifyFiles = false;
                    continue;
                }
                case "-decrypt" -> {
                    decrypt = true;
                    continue;
                }
                case "-auto" -> {
                    auto = true;
                    continue;
                }
                case "-verbose" -> {
                    verbose = true;
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
                        if (partOne.getFileName() == null) {
                            throw new InvalidPathException(nextArgument, "empty filename");
                        }
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
                case "-replacepolicy" -> {
                    switch (nextArgument.toLowerCase()) {
                        case "yesforall" -> {
                            replacePolicy = 1;
                        }
                        case "noforall" -> {
                            replacePolicy = -1;
                        }
                        case "ask" -> {
                            replacePolicy = 0;
                        }
                        default -> {
                            out.println("Unknown replace policy: " + nextArgument);
                            return;
                        }
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

        String partOneFileName = partOne.getFileName().toString().toLowerCase();
        if (!decrypt && partOneFileName.endsWith(".bin.001")) {
            out.println("Is " + partOne.toString() + " encrypted?");
            out.print("[Y/N]");
            String output = scanner.nextLine();
            if (output != null && (output.equalsIgnoreCase("y") || output.equalsIgnoreCase("yes"))) {
                decrypt = true;
            }
        }

        try {
            extract(scanner,
                    out,
                    partOne,
                    outputDirectory,
                    verifyFiles, decrypt, zipInZip, auto, verbose,
                    replacePolicy
            );
        } catch (IOException ex) {
            out.println("Operation failed!");
            ex.printStackTrace(out);
        } catch (InterruptedException ex) {
            out.println("Canceled");
        }
    }

    private static PartInputStream createPartStream(Scanner scanner, PrintStream out, Path partOne, boolean auto) {
        PartInputStream partStream = new PartInputStream(partOne) {
            private Path lastPart = null;
            private int retryTime = 2;

            @Override
            protected void onWaitingForNextPart(Path requiredPart) {
                if (auto) {
                    if (!requiredPart.equals(this.lastPart)) {
                        this.retryTime = 2;
                        this.lastPart = requiredPart;
                    }
                    out.println("Please insert the next part (" + requiredPart.getFileName().toString() + ") in directory: " + requiredPart.getParent().toString());
                    try {
                        Thread.sleep(this.retryTime * 1000);
                        this.retryTime += 2;
                        if (this.retryTime >= 12) {
                            this.retryTime = 12;
                        }
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                    continueSignal(null, false);
                    return;
                }
                Path path = null;
                while (path == null) {
                    out.println("Please insert the directory for the next part: " + requiredPart.getFileName().toString());
                    out.println("(Leave empty to use current part directory)");
                    out.print("[Directory:]");
                    String input = scanner.nextLine();
                    if (input.isEmpty()) {
                        continueSignal(null, false);
                        return;
                    }
                    try {
                        path = Path.of(input);
                        continueSignal(path, false);
                    } catch (InvalidPathException ex) {
                        out.println("Invalid path: " + input);
                        ex.printStackTrace(out);
                    }
                }
            }
        };
        return partStream;
    }

    private static ZipInputStream getZipInZip(InputStream in, Charset charset) throws IOException {
        ZipInputStream zip = new ZipInputStream(in, charset);
        if (zip.getNextEntry() == null) {
            throw new IOException("empty or corrupted zip file");
        }
        return zip;
    }

    private static EncryptedInputStream getDecryptedStream(PrintStream out, InputStream in) throws IOException {
        byte[] sample = in.readNBytes(1024);

        Console console = System.console();
        if (console == null) {
            throw new IOException("Console is not available for password reading");
        }

        while (true) {
            char[] password = null;
            try {
                password = console.readPassword("[%s]", "Password:");
                if (password == null || password.length == 0) {
                    out.println("Empty password");
                    continue;
                }

                try {
                    EncryptedInputStream e = new EncryptedInputStream(new ByteArrayInputStream(sample), password);
                    e.read();
                } catch (EncryptedInputStream.IncorrectPasswordException ex) {
                    out.println("Incorrect password or corrupted file!");
                    continue;
                } catch (IOException ex) {
                    //ignored
                }

                PushbackInputStream pushBack = new PushbackInputStream(in, sample.length);
                pushBack.unread(sample);
                return new EncryptedInputStream(pushBack, password);
            } finally {
                if (password != null) {
                    Arrays.fill(password, '\0');
                }
            }
        }
    }

    private static InputStream getDecompressedStream(InputStream in) throws IOException {
        byte[] magicBytes = in.readNBytes(32);

        PushbackInputStream pushBack = new PushbackInputStream(in, magicBytes.length);
        pushBack.unread(magicBytes);

        String magic = HexFormat.of().formatHex(magicBytes);
        CompressionAlgorithm compression = CompressionAlgorithmFactory.getDefault().fromMagicNumber(magic);

        if (compression != null) {
            return compression.decompress(pushBack);
        }
        return pushBack;
    }

    private static InputStream checkZipMagic(InputStream in) throws IOException {
        byte[] magicBytes = in.readNBytes(4);

        PushbackInputStream pushBack = new PushbackInputStream(in, magicBytes.length);
        pushBack.unread(magicBytes);

        boolean found = false;
        String[] zipMagic = {
            "504B0304", "504B0506", "504B0708"
        };
        String magic = HexFormat.of().formatHex(magicBytes).toUpperCase();
        for (String m : zipMagic) {
            if (magic.startsWith(m)) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IOException("Invalid or corrupt zip file");
        }

        return pushBack;
    }

    private static void extract(
            Scanner scanner,
            PrintStream out,
            Path partOne,
            Path outputDirectory,
            boolean verifyFiles,
            boolean decrypt,
            boolean zipInZip,
            boolean auto,
            boolean verbose,
            int replacePolicy
    ) throws IOException, InterruptedException {
        Charset charset;
        try {
            charset = Charset.forName("ibm-850");
        } catch (IllegalCharsetNameException | UnsupportedCharsetException ex) {
            charset = Charset.defaultCharset();
        }
        
        InputStream in = null;
        try {
            String name = partOne.getFileName().toString().toLowerCase();
            if (name.endsWith(".001") || name.endsWith(".01") || name.endsWith(".1")) {
                in = createPartStream(scanner, out, partOne, auto);
            } else {
                in = new BufferedInputStream(Files.newInputStream(partOne));
            }

            if (zipInZip) {
                in = getZipInZip(in, charset);
            }

            if (decrypt) {
                in = getDecryptedStream(out, in);
            }

            in = getDecompressedStream(in);
            in = checkZipMagic(in);

            ZipInputStream zip = new ZipInputStream(in, charset);
            in = zip;

            ZipExtractor extractor = new ZipExtractor(zip, outputDirectory) {
                private int replaceAll = replacePolicy;

                @Override
                protected void onFile(Path file, boolean directory, long expectedSize) {
                    if (verbose) {
                        if (directory) {
                            out.println("Creating " + file.toString());
                        } else {
                            out.println("Extracting " + file.toString() + " (" + ByteCountFormat.formatShort(expectedSize) + ")");
                        }
                    }
                }

                @Override
                protected void onFileError(Path file, IOException reason) {
                    out.println("Error on: " + file.toString());
                    reason.printStackTrace(out);
                }

                @Override
                protected boolean onShouldReplaceFile(Path file, long expectedSize) {
                    if (this.replaceAll == 1) {
                        return true;
                    }
                    if (this.replaceAll == -1) {
                        return false;
                    }

                    long size = 0;
                    try {
                        size = Files.size(file);
                    } catch (IOException ex) {
                        //ignored
                    }

                    out.println("Replace");
                    out.println(file + " -- " + ByteCountFormat.format(size));
                    out.println("With");
                    out.println(file + " -- " + ByteCountFormat.format(expectedSize));
                    out.println("?");

                    while (true) {
                        out.print("[Y/N/YesForAll/NoForAll:]");
                        String a = scanner.nextLine();
                        if (a == null || a.isEmpty()) {
                            continue;
                        }
                        a = a.toLowerCase();
                        switch (a) {
                            case "y", "yes" -> {
                                return true;
                            }
                            case "n", "no" -> {
                                return false;
                            }
                            case "yesforall" -> {
                                this.replaceAll = 1;
                                return true;
                            }
                            case "noforall" -> {
                                this.replaceAll = -1;
                                return false;
                            }
                        }
                    }
                }
            };
            ZipChecksumTester tester = null;
            if (verifyFiles) {
                tester = new ZipChecksumTester() {
                    @Override
                    protected void onFile(Path file, boolean directory, ChecksumAlgorithm algorithm) {
                        if (verbose) {
                            if (directory) {
                                out.println("Checking " + file.toString());
                            } else {
                                long size = 0;
                                try {
                                    size = Files.size(file);
                                } catch (IOException ex) {
                                    //ignored
                                }
                                String text = "Verifying " + file.toString() + " (" + ByteCountFormat.formatShort(size) + ")";
                                if (algorithm != null) {
                                    out.println("(" + algorithm.getName() + ") " + text);
                                } else {
                                    out.println(text);
                                }
                            }
                        }
                    }

                    @Override
                    protected void onFileError(Path file, IOException reason) {
                        out.println("Failed " + file.toString());
                        reason.printStackTrace(out);
                    }
                };
            }
            extractor.extract(tester);
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

}
