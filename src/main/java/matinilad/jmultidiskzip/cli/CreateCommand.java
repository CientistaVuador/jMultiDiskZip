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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipOutputStream;
import matinilad.jmultidiskzip.api.utils.PartOutputStream;
import matinilad.jmultidiskzip.api.ZipCreator;
import matinilad.jmultidiskzip.api.checksum.ChecksumAlgorithm;
import matinilad.jmultidiskzip.api.checksum.ChecksumAlgorithmFactory;
import matinilad.jmultidiskzip.api.compression.CompressionAlgorithm;
import matinilad.jmultidiskzip.api.compression.CompressionAlgorithmFactory;

/**
 *
 * @author Cien
 */
public class CreateCommand {

    private static final String[] units = {
        "KB", "MB", "GB",
        "KiB", "MiB", "GiB"
    };
    private static final long[] unitsSize = {
        1000, 1000 * 1000, 1000 * 1000 * 1000,
        1024, 1024 * 1024, 1024 * 1024 * 1024
    };

    private static void printHelp(PrintStream out) {
        out.println("Arguments (Can be used in any order):");
        out.println("-out [output file] - Sets the output file (e.g.: ./directory/name or ./directory/name.001) [REQUIRED]");
        out.print("-size [sizeInBytes/");
        for (int i = 0; i < units.length; i++) {
            out.print("size");
            out.print("_");
            out.print(units[i]);
            if (i != (units.length - 1)) {
                out.print("/");
            }
        }
        out.println("] - Sets the part size [REQUIRED]");

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
            out.print("_");
            out.print(a.getMinCompressionLevel());
            out.print("-");
            out.print(a.getMaxCompressionLevel() - 1);
            out.print("/");
        }
        out.println("none] - Sets the compression algorithm (and the respective level if needed) [DEFAULT IS gz_6]");

        out.println("-in [file] - Adds a input file [NOT REQUIRED]");
        out.println("-inDir [directory] - Adds the contents of a directory as input [NOT REQUIRED]");
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

        Path outputFile = null;
        long partSize = -1;
        ChecksumAlgorithm hash = ChecksumAlgorithmFactory.getDefault().fromName("sha-256");
        CompressionAlgorithm compression = CompressionAlgorithmFactory.getDefault().fromName("gz");
        int compressionLevel = compression.getDefaultCompressionLevel();
        List<Path> inputFiles = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String argument = args[i].toLowerCase();
            String nextArgument = null;
            if ((i + 1) < args.length) {
                nextArgument = args[i + 1];
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

                    String[] split = nextArgument.split("_", 2);
                    if (split.length == 2) {
                        long unit = -1;
                        for (int j = 0; j < units.length; j++) {
                            if (split[1].equalsIgnoreCase(units[j])) {
                                unit = unitsSize[j];
                                break;
                            }
                        }
                        if (unit == -1) {
                            out.println("Unknown unit: " + split[1]);
                            return;
                        }
                        multiplier = unit;
                    }

                    try {
                        partSize = Long.parseLong(split[0]) * multiplier;
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

                    String[] split = nextArgument.split("_", 2);
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
            filename += ".zip";
            if (compression != null) {
                filename += "." + compression.getExtension(0);
            }
            filename += ".001";
        }
        outputFile = parent.resolve(filename);
        
        try {
            create(out, outputFile, partSize, hash, compression, compressionLevel, inputFiles);
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
    
    private static void create(
            PrintStream out,
            Path outputFile,
            long partSize,
            ChecksumAlgorithm hash,
            CompressionAlgorithm compression, int compressionLevel,
            List<Path> inputFiles
    ) throws IOException, InterruptedException {
        if (outputFile.getParent() != null) {
            Files.createDirectories(outputFile.getParent());
        }
        
        try (PartOutputStream partOut = new PartOutputStream(outputFile, partSize, hash)) {
            try (OutputStream compressedStream = getCompressedStream(partOut, compression, compressionLevel)) {
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
        
        out.println("Done!");
    }
    
}
