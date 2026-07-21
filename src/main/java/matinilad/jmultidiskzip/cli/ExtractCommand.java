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
import java.nio.file.Path;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;
import matinilad.jmultidiskzip.api.PartInputStream;
import matinilad.jmultidiskzip.api.ZipChecksumTester;
import matinilad.jmultidiskzip.api.ZipExtractor;

/**
 *
 * @author Cien
 */
public class ExtractCommand {

    private static void printHelp() {
        System.out.println("Arguments (Can be used in any order):");
        System.out.println("-in=partOne (Input part 001) [Required]");
        System.out.println("-out=directory (Output directory) [Required]");
        System.out.println("-verify=true/false (Verify files after extraction) [Not required, Default is true]");
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

        String inputString = arguments.getFirst("-in");
        if (inputString == null) {
            System.out.println("Input is required");
            printHelp();
            return;
        }
        Path input = Path.of(inputString);

        String outputString = arguments.getFirst("-out");
        if (outputString == null) {
            System.out.println("Output is required");
            printHelp();
            return;
        }
        Path output = Path.of(outputString);

        boolean verify = true;
        if (arguments.contains("-verify")) {
            verify = Boolean.parseBoolean(arguments.getFirst("-verify"));
        }

        new ExtractCommand(input, output, verify).extract();
    }

    private final Path input;
    private final Path output;
    private final boolean verify;

    public ExtractCommand(Path input, Path output, boolean verify) {
        this.input = input;
        this.output = output;
        this.verify = verify;
    }

    public void extract() throws IOException, InterruptedException {
        Scanner scanner = new Scanner(System.in);

        try (PartInputStream in = new PartInputStream(this.input) {
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
                    ZipExtractor extractor = new ZipExtractor(zip, this.output) {
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
                    ZipChecksumTester tester = null;
                    if (this.verify) {
                        tester = new ZipChecksumTester() {
                            @Override
                            protected void onFile(Path file) {
                                System.out.println("Verifying " + file.toString());
                            }

                            @Override
                            protected void onFileError(Path file, IOException reason) {
                                System.out.println("Failed " + file.toString());
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
