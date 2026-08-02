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
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.zip.ZipOutputStream;
import matinilad.jmultidiskzip.api.utils.PartOutputStream;
import matinilad.jmultidiskzip.api.ZipCreator;
import matinilad.jmultidiskzip.api.checksum.ChecksumAlgorithm;
import matinilad.jmultidiskzip.api.checksum.ChecksumAlgorithmFactory;
import matinilad.jmultidiskzip.api.compression.CompressionAlgorithm;
import matinilad.jmultidiskzip.api.compression.CompressionAlgorithmFactory;
import matinilad.jmultidiskzip.api.utils.Base64File;
import matinilad.jmultidiskzip.api.utils.CountingOutputStream;
import matinilad.jmultidiskzip.api.utils.EncryptedOutputStream;
import matinilad.jmultidiskzip.api.utils.HexOutputStream;
import matinilad.jmultidiskzip.ui.ByteCountFormat;

/**
 *
 * @author Cien
 */
public class CreateCommand {

    private static final String[] units = {
        "B",
        "KB", "MB", "GB",
        "KiB", "MiB", "GiB"
    };
    private static final long[] unitsSize = {
        1,
        1000, 1000 * 1000, 1000 * 1000 * 1000,
        1024, 1024 * 1024, 1024 * 1024 * 1024
    };

    private static void printHelp(PrintStream out) {
        out.println("Arguments (Can be used in any order):");
        out.println("-out [output file] - Sets the output file (e.g.: ./directory/name or ./directory/name.001) [REQUIRED]");

        out.print("-size [size[");
        for (int i = 0; i < units.length; i++) {
            out.print(units[i]);
            if (i != (units.length - 1)) {
                out.print("/");
            }
        }
        out.println("]] - Sets the part size [REQUIRED]");

        out.print("-hash [");
        for (ChecksumAlgorithm a : ChecksumAlgorithmFactory.getDefault().getAlgorithms()) {
            out.print(a.getName());
            out.print("/");
        }
        out.println("NONE] - Sets the hash/checksum algorithm for each part and each file [DEFAULT IS SHA-256]");

        out.print("-partHash [");
        for (ChecksumAlgorithm a : ChecksumAlgorithmFactory.getDefault().getAlgorithms()) {
            out.print(a.getName());
            out.print("/");
        }
        out.println("NONE] - Sets the hash/checksum algorithm for each part [DEFAULT IS SHA-256]");

        out.print("-fileHash [");
        for (ChecksumAlgorithm a : ChecksumAlgorithmFactory.getDefault().getAlgorithms()) {
            out.print(a.getName());
            out.print("/");
        }
        out.println("NONE] - Sets the hash/checksum algorithm for each file [DEFAULT IS SHA-256]");

        out.print("-compression [");
        for (CompressionAlgorithm a : CompressionAlgorithmFactory.getDefault().getAlgorithms()) {
            out.print(a.getName() + "/");
            out.print(a.getName());
            out.print(":");
            out.print(a.getMinCompressionLevel());
            out.print("-");
            out.print(a.getMaxCompressionLevel() - 1);
            out.print("/");
        }
        out.println("none] - Sets the compression algorithm (and level) to apply on top of the output [DEFAULT IS gz:6]");

        out.println("-in [file or directory] - Adds a input file or directory [NOT REQUIRED]");
        out.println("-inDir [directory] - Adds the contents of a directory as input [NOT REQUIRED]");
        out.println("-encrypt - Encrypts the output with a password [NOT REQUIRED]");
        out.println("-verbose - Enables verbose output, otherwise only errors will be displayed [NOT REQUIRED]");
        out.println("-replaceFiles [yes/no/ask] - If the output file should be replaced if one already exists [DEFAULT IS ASK]");
        out.println("-format [binary/hex/base64] - Sets the output format [DEFAULT IS BINARY]");
        out.println("-noZip - Passthrough mode, only compression and encryption is applied to a single file input [NOT REQUIRED]");
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

        Scanner scanner = new Scanner(in);

        Path outputFile = null;
        long partSize = -1;
        ChecksumAlgorithm partHash = ChecksumAlgorithmFactory.getDefault().fromName("sha-256");
        ChecksumAlgorithm fileHash = ChecksumAlgorithmFactory.getDefault().fromName("sha-256");
        CompressionAlgorithm compression = CompressionAlgorithmFactory.getDefault().fromName("gz");
        int compressionLevel = compression.getDefaultCompressionLevel();
        List<Path> inputFiles = new ArrayList<>();
        boolean encrypt = false;
        boolean verbose = false;
        int replaceFiles = 0;
        String format = "binary";
        boolean noZip = false;

        for (int i = 0; i < args.length; i++) {
            String argument = args[i].toLowerCase();
            String nextArgument = null;
            if ((i + 1) < args.length) {
                nextArgument = args[i + 1];
            }

            switch (argument) {
                case "-encrypt" -> {
                    encrypt = true;
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
                case "-out" -> {
                    if (outputFile != null) {
                        out.println("Can't set output file twice!");
                        return;
                    }
                    try {
                        outputFile = Path.of(nextArgument);
                    } catch (InvalidPathException ex) {
                        out.println("Invalid path: " + nextArgument);
                        ex.printStackTrace(out);
                        return;
                    }
                }
                case "-size" -> {
                    if (partSize != -1) {
                        out.println("Can't set part size twice!");
                        return;
                    }

                    long multiplier = 1;

                    int digitEnd = -1;
                    for (int j = 0; j < nextArgument.length(); j++) {
                        char c = nextArgument.charAt(j);
                        if ((c < '0' || c > '9') && c != '+' && c != '-') {
                            digitEnd = j;
                            break;
                        }
                    }
                    if (digitEnd != -1) {
                        long mul = -1;
                        String unit = nextArgument.substring(digitEnd, nextArgument.length());
                        for (int j = 0; j < units.length; j++) {
                            if (units[j].equalsIgnoreCase(unit)) {
                                mul = unitsSize[j];
                                break;
                            }
                        }
                        if (mul == -1) {
                            out.println("Unknown size unit: " + nextArgument);
                            return;
                        }
                        multiplier = mul;
                        nextArgument = nextArgument.substring(0, digitEnd);
                    }

                    try {
                        partSize = Long.parseLong(nextArgument) * multiplier;
                        if (partSize <= 0) {
                            throw new NumberFormatException("Negative or zero part size");
                        }
                    } catch (NumberFormatException ex) {
                        out.println("Invalid part size: " + nextArgument);
                        ex.printStackTrace(out);
                        return;
                    }
                }
                case "-hash", "-parthash", "-filehash" -> {
                    boolean setPartHash = argument.equals("-parthash") || argument.equals("-hash");
                    boolean setFileHash = argument.equals("-filehash") || argument.equals("-hash");
                    if (nextArgument.equalsIgnoreCase("none")) {
                        if (setPartHash) {
                            partHash = null;
                        }
                        if (setFileHash) {
                            fileHash = null;
                        }
                        continue;
                    }
                    ChecksumAlgorithm hash = ChecksumAlgorithmFactory.getDefault().fromName(nextArgument);
                    if (hash == null) {
                        out.println("Unknown hash algorithm: " + nextArgument);
                        return;
                    }
                    if (setPartHash) {
                        partHash = hash;
                    }
                    if (setFileHash) {
                        fileHash = hash;
                    }
                }
                case "-compression" -> {
                    if (nextArgument.equalsIgnoreCase("none")) {
                        compression = null;
                        compressionLevel = -1;
                        continue;
                    }

                    String[] split = nextArgument.split(":", 2);
                    compression = CompressionAlgorithmFactory.getDefault().fromName(split[0]);
                    if (compression == null) {
                        out.println("Unknown compression algorithm: " + nextArgument);
                        return;
                    }

                    compressionLevel = compression.getDefaultCompressionLevel();

                    if (split.length == 2) {
                        try {
                            compressionLevel = Integer.parseInt(split[1]);
                            if (compressionLevel < compression.getMinCompressionLevel()) {
                                throw new NumberFormatException("compressionLevel < compression.getMinCompressionLevel()");
                            }
                            if (compressionLevel >= compression.getMaxCompressionLevel()) {
                                throw new NumberFormatException("compressionLevel >= compression.getMaxCompressionLevel()");
                            }
                        } catch (NumberFormatException ex) {
                            out.println("Invalid compression level: " + nextArgument);
                            ex.printStackTrace(out);
                            return;
                        }
                    }
                }
                case "-in" -> {
                    try {
                        inputFiles.add(Path.of(nextArgument));
                    } catch (InvalidPathException ex) {
                        out.println("Invalid input path: " + nextArgument);
                        ex.printStackTrace(out);
                        return;
                    }
                }
                case "-indir" -> {
                    Path directory;
                    try {
                        directory = Path.of(nextArgument);
                    } catch (InvalidPathException ex) {
                        out.println("Invalid input directory path: " + nextArgument);
                        ex.printStackTrace(out);
                        return;
                    }

                    if (!Files.isDirectory(directory)) {
                        out.println("Not a directory: " + nextArgument);
                        return;
                    }

                    try {
                        inputFiles.addAll(Files.list(directory).toList());
                    } catch (IOException ex) {
                        out.println("Failed to add files of: " + nextArgument);
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
                case "-format" -> {
                    switch (nextArgument.toLowerCase()) {
                        case "bin", "binary" -> {
                            format = "binary";
                        }
                        case "hex" -> {
                            format = "hex";
                        }
                        case "base64" -> {
                            format = "base64";
                        }
                        default -> {
                            out.println("Unknown format option: " + nextArgument);
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

        if (outputFile == null) {
            out.println("A output file is required!");
            return;
        }

        if (partSize <= 0) {
            out.println("A part size is required!");
            return;
        }

        outputFile = outputFile.toAbsolutePath().normalize();

        Path parent = outputFile.getParent();
        if (parent == null) {
            out.println("Output file has no parent!");
            return;
        }
        Path nameFile = outputFile.getFileName();
        if (nameFile == null || nameFile.toString().isEmpty()) {
            out.println("Output file has no name!");
            return;
        }

        String filename = nameFile.toString();
        if (PartOutputStream.getPartNumber(outputFile) != 1) {
            if (!encrypt) {
                if (!noZip) {
                    filename += "." + ZipCreator.EXTENSION;
                }
                if (compression != null) {
                    filename += "." + compression.getExtension(0);
                }
            } else {
                filename += "." + EncryptedOutputStream.EXTENSION;
            }
            if (!format.equals("binary")) {
                switch (format) {
                    case "hex" -> {
                        filename += "." + HexOutputStream.EXTENSION;
                    }
                    case "base64" -> {
                        filename += "." + Base64File.EXTENSION;
                    }
                }
            }
            filename += "." + PartOutputStream.EXTENSION;
        }
        outputFile = parent.resolve(filename);

        if (Files.exists(outputFile)) {
            if (replaceFiles == -1) {
                out.println("Error: " + outputFile.toString() + " already exists!");
                return;
            }
            if (replaceFiles == 0) {
                out.println("Replace " + outputFile.toString() + " ?");
                out.print("[Y/N:]");
                String a = scanner.nextLine();
                if ((a == null || a.isEmpty()) || (!a.equalsIgnoreCase("yes") && !a.equalsIgnoreCase("y"))) {
                    out.println("Operation canceled");
                    return;
                }
            }
        }

        if (noZip) {
            if (inputFiles.size() != 1) {
                out.println("-noZip Error: Only one input is required but found " + inputFiles.size() + " inputs");
                return;
            }
            Path file = inputFiles.get(0);
            if (!Files.isRegularFile(file)) {
                out.println("-noZip Error: Not a regular file " + file.toString());
                return;
            }
        }

        try {
            create(out, outputFile, partSize, partHash, fileHash, compression, compressionLevel, inputFiles, encrypt, verbose, format, noZip);
        } catch (IOException ex) {
            out.println("Operation failed!");
            ex.printStackTrace(out);
        } catch (InterruptedException ex) {
            out.print("Canceled");
        }
    }

    private static EncryptedOutputStream createEncryptedStream(PrintStream log, OutputStream out) throws IOException {
        Console console = System.console();
        if (console == null) {
            throw new IOException("Console is not available for password reading");
        }

        while (true) {
            char[] pass = null;
            try {
                pass = console.readPassword("[%s]", "Password:");
                if (pass == null) {
                    throw new IOException("end of input");
                }
                if (pass.length == 0) {
                    log.println("Password is empty, try again");
                    continue;
                }

                char[] confirm = null;
                try {
                    confirm = console.readPassword("[%s]", "Confirm password:");
                    if (confirm == null) {
                        throw new IOException("end of input");
                    }
                    if (!Arrays.equals(pass, confirm)) {
                        log.println("Passwords are not equal, try again");
                        continue;
                    }
                } finally {
                    if (confirm != null) {
                        Arrays.fill(confirm, '\0');
                    }
                }

                log.println("Type random characters below (you don't need to remember them)");
                String saltString = console.readLine("[%s]", "Additional entropy (leave empty to skip):");
                byte[] salt = null;
                if (saltString != null) {
                    salt = saltString.getBytes(StandardCharsets.UTF_8);
                }

                return new EncryptedOutputStream(out, salt, pass);
            } finally {
                if (pass != null) {
                    Arrays.fill(pass, '\0');
                }
            }
        }
    }

    private static void create(
            PrintStream log,
            Path outputFile,
            long partSize,
            ChecksumAlgorithm partHash,
            ChecksumAlgorithm fileHash,
            CompressionAlgorithm compression, int compressionLevel,
            List<Path> inputFiles,
            boolean encrypt,
            boolean verbose,
            String format,
            boolean noZip
    ) throws IOException, InterruptedException {
        if (outputFile.getParent() != null) {
            Files.createDirectories(outputFile.getParent());
        }

        CountingOutputStream countIn = null;
        CountingOutputStream countOut = null;
        OutputStream out = null;
        try {
            countOut = new CountingOutputStream(new PartOutputStream(outputFile, partSize, (encrypt ? null : partHash)));
            out = countOut;

            if (!format.equals("binary")) {
                switch (format) {
                    case "base64" -> {
                        out.write(Base64File.HEADER.getBytes(StandardCharsets.US_ASCII));
                        out = Base64File.encode(out);
                    }
                    case "hex" -> {
                        out = new HexOutputStream(out);
                    }
                }
            }

            if (encrypt) {
                out = createEncryptedStream(log, out);
            }

            if (compression != null) {
                out = compression.compress(out, compressionLevel);
            }

            if (noZip) {
                Path file = inputFiles.get(0);

                if (verbose) {
                    long size = Files.size(file);
                    log.println(file.toString() + " (" + ByteCountFormat.formatShort(size) + ")");
                }

                try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(file))) {
                    byte[] buffer = new byte[1 * 1024 * 1024];
                    int r;
                    while ((r = in.read(buffer, 0, buffer.length)) != -1) {
                        out.write(buffer, 0, r);
                    }
                }
                return;
            }

            countIn = new CountingOutputStream(out);
            ZipOutputStream zipOut = new ZipOutputStream(countIn, StandardCharsets.UTF_8);
            out = zipOut;

            final CountingOutputStream inCount = countIn;
            final CountingOutputStream outCount = countOut;

            ZipCreator writer = new ZipCreator(zipOut, inputFiles.toArray(Path[]::new), fileHash) {
                @Override
                protected void onFile(Path file) {
                    if (verbose && Files.isDirectory(file)) {
                        log.println("Adding " + file.toString());
                    }
                }

                @Override
                protected void onFileProgress(Path file, boolean crc, long currentBytes, long totalBytes) {
                    if (verbose && currentBytes == 0) {
                        if (crc) {
                            String sizeFormatted = "(" + ByteCountFormat.formatShort(totalBytes) + ")";

                            long dataIn = inCount.getCount();
                            long dataOut = outCount.getCount();

                            String dataInText = ByteCountFormat.formatShort(dataIn);
                            String dataOutText = ByteCountFormat.formatShort(dataOut);
                            String ratio = "0%";
                            if (dataIn != 0) {
                                ratio = String.format("%.2f", (dataOut / ((double) dataIn)) * 100.0) + "%";
                            }

                            String crcName = "(CRC32";
                            if (getHash() != null) {
                                crcName += "/" + getHash().getName();
                            }
                            crcName += ")";

                            log.print("(" + dataInText + ">" + dataOutText + "; " + ratio + ") " + file.toString() + " " + sizeFormatted + " " + crcName);
                        } else {
                            log.println(" (Writing)");
                        }
                    }
                }

                @Override
                protected void onFileError(Path file, IOException reason) {
                    log.println("Error on: " + file.toString());
                    reason.printStackTrace(log);
                }
            };
            writer.create();
        } finally {
            if (out != null) {
                out.close();
            }
        }
        if (verbose && countIn != null && countOut != null) {
            long dataIn = countIn.getCount();
            long dataOut = countOut.getCount();

            String dataInText = ByteCountFormat.format(dataIn);
            String dataOutText = ByteCountFormat.format(dataOut);
            String ratio = "0%";
            if (dataIn != 0) {
                ratio = String.format("%.2f", (dataOut / ((double) dataIn)) * 100.0) + "%";
            }

            long parts = dataOut / partSize;
            long remainder = dataOut - (parts * partSize);

            log.println("Total (input): " + dataInText);
            log.println("Total (output): " + dataOutText);
            log.println("Ratio: " + ratio);
            if (parts != 0) {
                log.print(parts + (parts == 1 ? " Part" : " Parts") + " of " + ByteCountFormat.format(partSize));
                if (remainder != 0) {
                    log.print(" + ");
                }
            }
            log.println("1 Part of "+ByteCountFormat.format(remainder));
        }
    }

}
