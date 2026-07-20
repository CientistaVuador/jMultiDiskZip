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

import java.util.Arrays;

/**
 *
 * @author Cien
 */
public class CLInterface {
    
    private static void printHelp() {
        System.out.println("Available Commands:");
        System.out.println("-create (Creates a ZIP file with multiple volumes)");
        System.out.println("-extract (Extracts a ZIP file with multiple volumes)");
    }
    
    public static void run(String[] args) throws Exception {
        if (args.length == 0) {
            printHelp();
            return;
        }
        switch (args[0]) {
            case "-create" -> {
                CreateCommand.run(Arrays.copyOfRange(args, 1, args.length));
            }
            case "-extract" -> {
                ExtractCommand.run(Arrays.copyOfRange(args, 1, args.length));
            }
            default -> {
                if (!args[0].equals("-help")) {
                    System.out.println("Unknown Command: "+args[0]);
                }
                printHelp();
            }
        }
    }

    private CLInterface() {

    }

}
