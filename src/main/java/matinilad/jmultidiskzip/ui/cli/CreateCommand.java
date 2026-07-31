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

import java.io.BufferedOutputStream;
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
import java.util.zip.ZipOutputStream;
import matinilad.jmultidiskzip.api.utils.PartOutputStream;
import matinilad.jmultidiskzip.api.ZipCreator;
import matinilad.jmultidiskzip.api.checksum.ChecksumAlgorithm;
import matinilad.jmultidiskzip.api.checksum.ChecksumAlgorithmFactory;
import matinilad.jmultidiskzip.api.compression.CompressionAlgorithm;
import matinilad.jmultidiskzip.api.compression.CompressionAlgorithmFactory;
import matinilad.jmultidiskzip.api.utils.EncryptedOutputStream;

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
        out.println("NONE] - Sets the hash/checksum algorithm [DEFAULT IS SHA-256]");

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
        out.println("none] - Sets the compression algorithm (and the respective level if needed) [DEFAULT IS gz:6]");

        out.println("-in [file] - Adds a input file [NOT REQUIRED]");
        out.println("-inDir [directory] - Adds the contents of a directory as input [NOT REQUIRED]");

        out.println("-encrypt - Encrypts the output with a password [NOT REQUIRED]");
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

        Path outputFile = null;
        long partSize = -1;
        ChecksumAlgorithm hash = ChecksumAlgorithmFactory.getDefault().fromName("sha-256");
        CompressionAlgorithm compression = CompressionAlgorithmFactory.getDefault().fromName("gz");
        int compressionLevel = compression.getDefaultCompressionLevel();
        List<Path> inputFiles = new ArrayList<>();
        boolean encrypt = false;

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
                case "-hash" -> {
                    if (nextArgument.equalsIgnoreCase("none")) {
                        hash = null;
                        continue;
                    }
                    hash = ChecksumAlgorithmFactory.getDefault().fromName(nextArgument);
                    if (hash == null) {
                        out.println("Unknown hash algorithm: " + nextArgument);
                        return;
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

        outputFile = outputFile.toAbsolutePath();
        Path parent = outputFile.getParent();
        if (parent == null) {
            out.println("Output file has no parent!");
            return;
        }
        Path nameFile = outputFile.getFileName();
        if (nameFile == null) {
            out.println("Output file has no name!");
            return;
        }

        String filename = nameFile.toString();
        if (!filename.endsWith(".001")) {
            if (!encrypt) {
                filename += ".zip";
                if (compression != null) {
                    filename += "." + compression.getExtension(0);
                }
            } else {
                filename += ".bin";
            }
            filename += ".001";
        }
        outputFile = parent.resolve(filename);

        try {
            create(out, outputFile, partSize, hash, compression, compressionLevel, inputFiles, encrypt);
        } catch (IOException ex) {
            out.println("Operation failed!");
            ex.printStackTrace(out);
        } catch (InterruptedException ex) {
            out.print("Canceled");
        }
    }

    private static OutputStream getCompressedStream(
            OutputStream out,
            CompressionAlgorithm compression, int compressionLevel
    ) throws IOException {
        if (compression == null) {
            return new BufferedOutputStream(out);
        }
        return compression.compress(out, compressionLevel);
    }

    private static OutputStream getEncryptedStream(OutputStream out, boolean encrypt) throws IOException {
        if (!encrypt) {
            return new BufferedOutputStream(out);
        }

        Console console = System.console();
        if (console == null) {
            throw new IOException("Console is not available for password reading");
        }

        char[] password = new char[0];
        try {
            while (true) {
                char[] pass = null;
                char[] passConfirm = null;
                try {
                    pass = console.readPassword("[%s]", "Password:");
                    passConfirm = console.readPassword("[%s]", "Confirm password:");
                    if (pass == null || passConfirm == null) {
                        throw new IOException("end of input");
                    }
                    if (pass.length == 0) {
                        console.writer().println("Password is empty! Try again");
                        continue;
                    }
                    if (!Arrays.equals(pass, passConfirm)) {
                        console.writer().println("Passwords are not equal! Try again");
                        continue;
                    }
                    password = pass.clone();
                    break;
                } finally {
                    if (pass != null) {
                        Arrays.fill(pass, '\0');
                    }
                    if (passConfirm != null) {
                        Arrays.fill(passConfirm, '\0');
                    }
                }
            }

            console.writer().println("Type random characters below (you don't need to remember them)");
            String saltString = console.readLine("[%s]", "Additional entropy (leave empty to skip):");
            byte[] salt = null;
            if (saltString != null) {
                salt = saltString.getBytes(StandardCharsets.UTF_8);
            }

            return new EncryptedOutputStream(out, salt, password);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static void create(
            PrintStream out,
            Path outputFile,
            long partSize,
            ChecksumAlgorithm hash,
            CompressionAlgorithm compression, int compressionLevel,
            List<Path> inputFiles,
            boolean encrypt
    ) throws IOException, InterruptedException {
        if (outputFile.getParent() != null) {
            Files.createDirectories(outputFile.getParent());
        }
        
        try (PartOutputStream partOut = new PartOutputStream(outputFile, partSize, (encrypt ? null : hash))) {
            try (OutputStream encryptedStream = getEncryptedStream(partOut, encrypt)) {
                try (OutputStream compressedStream = getCompressedStream(encryptedStream, compression, compressionLevel)) {
                    try (ZipOutputStream zipOut = new ZipOutputStream(compressedStream, StandardCharsets.UTF_8)) {
                        ZipCreator writer = new ZipCreator(zipOut, inputFiles.toArray(Path[]::new), hash) {
                            @Override
                            protected void onFile(Path file) {
                                out.println(file.toString());
                            }

                            @Override
                            protected void onFileError(Path file, IOException reason) {
                                out.println("Error on: " + file.toString());
                                reason.printStackTrace(out);
                            }
                        };
                        writer.create();
                    }
                }
            }
        }
        
        out.println("Done!");
    }

}
