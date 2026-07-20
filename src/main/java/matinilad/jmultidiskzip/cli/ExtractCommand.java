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

    public static void run(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: [Input File (Must Start With .001)] [Output Directory]");
            return;
        }
        new ExtractCommand().extract(args);
    }

    public ExtractCommand() {

    }

    public void extract(String[] args) throws IOException, InterruptedException {
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
                        protected void onFile(Path file) {
                            System.out.println(file.toString());
                        }

                        @Override
                        protected void onFileError(Path file, IOException reason) {
                            System.out.println("Error on: " + file.toString());
                            reason.printStackTrace(System.out);
                        }
                    };
                    extractor.extract(new ZipChecksumTester() {
                        @Override
                        protected void onFile(Path file) {
                            System.out.println("Verifying " + file.toString());
                        }

                        @Override
                        protected void onFileError(Path file, IOException reason) {
                            System.out.println("Failed " + file.toString());
                            reason.printStackTrace(System.out);
                        }
                    });
                }
            }
        }
    }

}
