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
import java.io.BufferedOutputStream;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Scanner;
import java.util.zip.ZipInputStream;
import matinilad.jmultidiskzip.api.utils.PartInputStream;
import matinilad.jmultidiskzip.api.ZipChecksumTester;
import matinilad.jmultidiskzip.api.ZipCreator;
import matinilad.jmultidiskzip.api.ZipExtractor;
import matinilad.jmultidiskzip.api.checksum.ChecksumAlgorithm;
import matinilad.jmultidiskzip.api.compression.CompressionAlgorithm;
import matinilad.jmultidiskzip.api.compression.CompressionAlgorithmFactory;
import matinilad.jmultidiskzip.api.utils.Base64File;
import matinilad.jmultidiskzip.api.utils.CountingInputStream;
import matinilad.jmultidiskzip.api.utils.EncryptedInputStream;
import matinilad.jmultidiskzip.api.utils.EncryptedOutputStream;
import matinilad.jmultidiskzip.api.utils.HexInputStream;
import matinilad.jmultidiskzip.api.utils.HexOutputStream;
import matinilad.jmultidiskzip.api.utils.PartOutputStream;
import matinilad.jmultidiskzip.api.utils.TempFileList;
import matinilad.jmultidiskzip.ui.UIUtils;

/**
 *
 * @author Cien
 */
public class ExtractCommand {

    private static void printHelp(PrintStream out) {
        out.println("Arguments (Can be used in any order):");
        out.println("-in [part one] - Sets the part one file input (e.g.: ./dir/name.001) [REQUIRED]");
        out.println("-out [directory] - Sets the output directory, files will be extracted to this directory [REQUIRED]");
        out.println("-noVerify - Disables file integrity verification [NOT REQUIRED]");
        out.println("-decrypt - Use this if the input file is encrypted [NOT REQUIRED]");
        out.println("-zipInZip - Use this if the input file is a single zip file inside of a zip file [NOT REQUIRED]");
        out.println("-auto - Enables automatic mode, checks the part directory every few seconds instead of asking for a directory [NOT REQUIRED]");
        out.println("-verbose - Enables verbose output mode, otherwise only errors will be displayed [NOT REQUIRED]");
        out.println("-replaceFiles [yes/no/ask] - If files should be replaced or not [DEFAULT IS ASK]");
        out.println("-noZip - No zip archives, passthrough only [NOT REQUIRED]");
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
        int replaceFiles = 0;
        boolean noZip = false;

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
                case "-nozip" -> {
                    noZip = true;
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
                        partOne = Path.of(nextArgument).toAbsolutePath().normalize();
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
                case "-replacefiles" -> {
                    switch (nextArgument.toLowerCase()) {
                        case "y", "yes" -> {
                            replaceFiles = 1;
                        }
                        case "n", "no" -> {
                            replaceFiles = -1;
                        }
                        case "ask" -> {
                            replaceFiles = 0;
                        }
                        default -> {
                            out.println("Unknown replace option: " + nextArgument);
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
            out.println("A input file is required, add with -in");
            return;
        }

        if (outputDirectory == null) {
            out.println("A output directory is required, add with -out");
            return;
        }
        
        outputDirectory = outputDirectory.toAbsolutePath().normalize();

        if (!decrypt) {
            String[] extensions = partOne.getFileName().toString().split("\\.");
            for (int i = 1; i < extensions.length; i++) {
                if (extensions[i].equalsIgnoreCase(EncryptedOutputStream.EXTENSION)) {
                    out.println("Is " + partOne.toString() + " encrypted?");
                    out.print("[Y/N:]");
                    String output = scanner.nextLine();
                    if (output != null && (output.equalsIgnoreCase("y") || output.equalsIgnoreCase("yes"))) {
                        decrypt = true;
                    }
                    break;
                }
            }
        }
        
        if (noZip && auto && !decrypt) {
            out.println("auto mode cannot be combined with noZip without encryption");
            return;
        }

        try {
            extract(scanner,
                    out,
                    partOne,
                    outputDirectory,
                    verifyFiles, decrypt, zipInZip, auto, verbose,
                    replaceFiles,
                    noZip
            );
        } catch (IOException ex) {
            out.println("Operation failed!");
            ex.printStackTrace(out);
        } catch (InterruptedException ex) {
            out.println("Canceled");
        }
    }

    private static PartInputStream getPartStream(Scanner scanner, PrintStream out, Path partOne, boolean auto, List<String> extensions) {
        extensions.remove(extensions.size() - 1);
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
                    out.println("Leave empty to use current part directory");
                    out.println("If no more parts are available, type //close to close the stream");
                    out.print("[Directory:]");
                    String input = scanner.nextLine();
                    if (input.equalsIgnoreCase("//close")) {
                        continueSignal(null, true);
                        return;
                    }
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

    private static boolean removeExtensionFromList(String extension, List<String> extensions) {
        if (!extensions.isEmpty()) {
            String last = extensions.get(extensions.size() - 1);
            if (last.equalsIgnoreCase(extension)) {
                extensions.remove(extensions.size() - 1);
                return true;
            }
        }
        return false;
    }

    private static ZipInputStream getZipInZip(InputStream in, Charset charset, List<String> extensions) throws IOException {
        removeExtensionFromList(ZipCreator.EXTENSION, extensions);
        ZipInputStream zip = new ZipInputStream(in, charset);
        if (zip.getNextEntry() == null) {
            throw new IOException("empty or corrupted zip file");
        }
        return zip;
    }

    private static InputStream getFormatStream(InputStream in, List<String> extensions) throws IOException {
        HexFormat hex = HexFormat.of();

        byte[] magic = in.readNBytes(256);
        String magicHex = hex.formatHex(magic);

        PushbackInputStream pushback = new PushbackInputStream(in, magic.length);
        pushback.unread(magic);

        if (Base64File.isBase64File(magicHex)) {
            removeExtensionFromList(Base64File.EXTENSION, extensions);
            return Base64File.decode(pushback);
        } else if (HexOutputStream.isHexFile(magicHex)) {
            removeExtensionFromList(HexOutputStream.EXTENSION, extensions);
            return new HexInputStream(pushback);
        }

        return pushback;
    }

    private static EncryptedInputStream getEncryptedStream(PrintStream out, InputStream in, List<String> extensions) throws IOException {
        removeExtensionFromList(EncryptedOutputStream.EXTENSION, extensions);

        byte[] sample = in.readNBytes(256);

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

    private static InputStream getCompressedStream(InputStream in, List<String> extensions) throws IOException {
        byte[] magicBytes = in.readNBytes(32);

        PushbackInputStream pushBack = new PushbackInputStream(in, magicBytes.length);
        pushBack.unread(magicBytes);

        String magic = HexFormat.of().formatHex(magicBytes);
        CompressionAlgorithm compression = CompressionAlgorithmFactory.getDefault().fromMagicNumber(magic);

        if (compression != null) {
            for (int i = 0; i < compression.getNumberOfExtensions(); i++) {
                if (removeExtensionFromList(compression.getExtension(i), extensions)) {
                    break;
                }
            }
            return compression.decompress(pushBack);
        }
        return pushBack;
    }

    private static InputStream getVerifiedZipFileStream(InputStream in) throws IOException {
        byte[] magicBytes = in.readNBytes(32);

        PushbackInputStream pushBack = new PushbackInputStream(in, magicBytes.length);
        pushBack.unread(magicBytes);

        if (!ZipCreator.isZipFile(HexFormat.of().formatHex(magicBytes))) {
            throw new IOException("Invalid or corrupt zip file");
        }

        return pushBack;
    }

    private static void printFinalResultInformation(CountingInputStream countIn, CountingInputStream countOut, PrintStream out) {
        long dataIn = countIn.getCount();
        long dataOut = countOut.getCount();

        String dataInText = UIUtils.formatBytes(dataIn);
        String dataOutText = UIUtils.formatBytes(dataOut);
        String ratio = "0%";
        if (dataOut != 0) {
            ratio = String.format("%.2f", (dataIn / ((double) dataOut)) * 100.0) + "%";
        }

        out.println("Total (input): " + dataInText);
        out.println("Total (output): " + dataOutText);
        out.println("Ratio: " + ratio);
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
            int replaceFiles,
            boolean noZip
    ) throws IOException, InterruptedException {
        ZipExtractor extractor = null;
        try {
            Charset charset;
            try {
                charset = Charset.forName("ibm-850");
                //and hope it works
            } catch (IllegalCharsetNameException | UnsupportedCharsetException ex) {
                charset = Charset.defaultCharset();
            }

            CountingInputStream countIn = null;
            CountingInputStream countOut = null;
            InputStream in = null;
            try {
                List<String> extensions = new ArrayList<>(Arrays.asList(partOne.getFileName().toString().split("\\.")));
                String name = extensions.get(0);
                extensions.remove(0);

                if (PartOutputStream.getPartNumber(partOne) != -1) {
                    in = getPartStream(scanner, out, partOne, auto, extensions);
                } else {
                    in = new BufferedInputStream(Files.newInputStream(partOne));
                }
                countIn = new CountingInputStream(in);
                in = countIn;

                if (zipInZip) {
                    in = getZipInZip(in, charset, extensions);
                }

                in = getFormatStream(in, extensions);

                if (decrypt) {
                    in = getEncryptedStream(out, in, extensions);
                }

                in = getCompressedStream(in, extensions);

                if (noZip) {
                    TempFileList createdFiles = new TempFileList();
                    try {
                        try {
                            String filename = name;
                            for (int i = 0; i < extensions.size(); i++) {
                                filename += "." + extensions.get(i);
                            }

                            Path outputFile = outputDirectory.resolve(filename);

                            if (Files.exists(outputFile)) {
                                if (replaceFiles == -1) {
                                    out.println(outputFile.toString() + " already exists!");
                                    return;
                                } else if (replaceFiles == 0) {
                                    out.println("Replace " + outputFile.toString() + " ?");
                                    out.print("[Y/N:]");
                                    String response = scanner.nextLine();
                                    if (response == null || (!response.equalsIgnoreCase("y") && !response.equalsIgnoreCase("yes"))) {
                                        out.println("Canceled");
                                        return;
                                    }
                                }
                            }

                            createdFiles.createDirectories(outputDirectory);

                            if (verbose) {
                                out.println(outputFile.toString());
                            }
                            
                            countOut = new CountingInputStream(in);
                            in = countOut;

                            try (BufferedOutputStream o = new BufferedOutputStream(createdFiles.newOutputStream(outputFile))) {
                                byte[] buffer = new byte[1 * 1024 * 1024];
                                int r;
                                while ((r = in.read(buffer, 0, buffer.length)) != -1) {
                                    o.write(buffer, 0, r);
                                }
                            }
                        } finally {
                            if (in != null) {
                                in.close();
                                in = null;
                            }
                        }
                        if (verbose) {
                            printFinalResultInformation(countIn, countOut, out);
                        }
                    } catch (Throwable t) {
                        createdFiles.deleteFiles();
                        throw t;
                    }
                    return;
                }

                in = getVerifiedZipFileStream(in);

                countOut = new CountingInputStream(in);
                ZipInputStream zip = new ZipInputStream(countOut, charset);
                in = zip;

                final CountingInputStream inCount = countIn;
                final CountingInputStream outCount = countOut;

                extractor = new ZipExtractor(zip, outputDirectory) {
                    private int replaceAll = replaceFiles;

                    @Override
                    protected void onFile(Path file, boolean directory, long expectedSize) {
                        if (verbose) {
                            if (directory) {
                                out.println("Creating " + file.toString());
                            } else {
                                long dataIn = inCount.getCount();
                                long dataOut = outCount.getCount();

                                String dataInText = UIUtils.formatBytesShort(dataIn);
                                String dataOutText = UIUtils.formatBytesShort(dataOut);
                                String ratio = "0%";
                                if (dataOut != 0) {
                                    ratio = String.format("%.2f", (dataIn / ((double) dataOut)) * 100.0) + "%";
                                }

                                out.println("(" + dataInText + ">" + dataOutText + "; " + ratio + ") " + "Extracting " + file.toString() + " (" + UIUtils.formatBytesShort(expectedSize) + ")");
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
                        out.println(file + " -- " + UIUtils.formatBytes(size));
                        out.println("With");
                        out.println(file + " -- " + UIUtils.formatBytes(expectedSize));
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
                                    String text = "Verifying " + file.toString() + " (" + UIUtils.formatBytesShort(size) + ")";
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
                    in = null;
                }
            }
            if (verbose && countIn != null && countOut != null) {
                printFinalResultInformation(countIn, countOut, out);
            }
        } catch (Throwable t) {
            if (extractor != null) {
                extractor.deleteFiles();
            }
            throw t;
        }
    }

}
