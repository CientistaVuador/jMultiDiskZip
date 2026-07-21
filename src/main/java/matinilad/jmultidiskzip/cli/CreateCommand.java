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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipOutputStream;
import matinilad.jmultidiskzip.api.HashAlgorithm;
import matinilad.jmultidiskzip.api.PartOutputStream;
import matinilad.jmultidiskzip.api.ZipCreator;

/**
 *
 * @author Cien
 */
public class CreateCommand {
    
    private static void printHelp() {
        System.out.println("Arguments (Can be used in any order):");
        System.out.println("-out=outputFile (Output file, must end with .001) [Required]");
        System.out.println("-partSize=sizeInBytes (Part size, in bytes) [Required]");
        System.out.println("-hash=SHA-256/SHA-1/MD5/NONE (Hash algorithm) [Default is SHA-256]");
        System.out.println("-in=inputFile (Adds a input file) [Not Required]");
        System.out.println("-inDir=inputDirectory (Adds the contents of a directory as input) [Not Required]");
    }
    
    public static void run(String[] args) throws Exception {
        if (args.length == 0) {
            printHelp();
            return;
        }
        if (args.length == 1 && args[0].equals("-help")) {
            printHelp();
            return;
        }
        CLIArguments arguments = new CLIArguments();
        arguments.load(args);
        
        String outputString = arguments.getFirst("-out");
        if (outputString == null) {
            System.out.println("A output is required");
            printHelp();
            return;
        }
        Path output = Path.of(outputString);
        
        String partSizeString = arguments.getFirst("-partSize");
        if (partSizeString == null) {
            System.out.println("Part size is required");
            printHelp();
            return;
        }
        long partSize = Long.parseLong(partSizeString);
        
        String hashString = arguments.getFirst("-hash");
        String[] inputsString = arguments.get("-in");
        String[] inputDirectoriesString = arguments.get("-inDir");
        
        HashAlgorithm hash = HashAlgorithm.SHA256;
        if (hashString != null) {
            if (hashString.toLowerCase().equals("none")) {
                hash = null;
            } else {
                hash = HashAlgorithm.fromAlgorithm(hashString);
            }
        }
        
        List<Path> inputs = new ArrayList<>();
        
        if (inputsString != null) {
            for (int i = 0; i < inputsString.length; i++) {
                inputs.add(Path.of(inputsString[i]));
            }
        }
        
        if (inputDirectoriesString != null) {
            for (int i = 0; i < inputDirectoriesString.length; i++) {
                inputs.addAll(Files.list(Path.of(inputDirectoriesString[i])).toList());
            }
        }
        
        new CreateCommand(output, partSize, hash, inputs.toArray(Path[]::new)).create();
    }
    
    private final Path outputFile;
    private final long partSize;
    private final HashAlgorithm hash;
    private final Path[] inputs;
    
    public CreateCommand(Path outputFile, long partSize, HashAlgorithm hash, Path[] inputs) {
        this.outputFile = outputFile;
        this.partSize = partSize;
        this.hash = hash;
        this.inputs = inputs;
    }

    public void create() throws IOException, InterruptedException {
        if (this.outputFile.getParent() != null) {
            Files.createDirectories(this.outputFile.getParent());
        }
        
        try (PartOutputStream out = new PartOutputStream(this.outputFile, this.partSize, this.hash)) {
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                try (ZipOutputStream zip = new ZipOutputStream(gzip, StandardCharsets.UTF_8)) {
                    ZipCreator writer = new ZipCreator(zip, this.inputs, this.hash) {
                        @Override
                        protected void onFile(Path file) {
                            System.out.println(file.toString());
                        }

                        @Override
                        protected void onFileError(Path file, IOException reason) {
                            System.out.println("Error on: " + file.toString());
                            reason.printStackTrace(System.out);
                        }
                    };
                    writer.create();
                }
            }
        }
    }

}
