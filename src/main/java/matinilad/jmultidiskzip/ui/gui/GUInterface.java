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
package matinilad.jmultidiskzip.ui.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipOutputStream;
import javax.swing.SwingUtilities;
import matinilad.jmultidiskzip.api.ZipCreator;
import matinilad.jmultidiskzip.api.checksum.ChecksumAlgorithm;
import matinilad.jmultidiskzip.api.checksum.ChecksumAlgorithmFactory;
import matinilad.jmultidiskzip.api.utils.CountingOutputStream;
import matinilad.jmultidiskzip.api.utils.PartOutputStream;
import matinilad.jmultidiskzip.ui.UIUtils;

/**
 *
 * @author Cien
 */
public class GUInterface {

    public static void run() {
        SwingUtilities.invokeLater(() -> {
            FlatDarkLaf.setup();

            MainWindow mainWindow = new MainWindow();
            mainWindow.setVisible(true);

            ProgressDialog progress = new ProgressDialog(mainWindow, true) {
                @Override
                public void run() throws Throwable {
                    Path output = Path.of("C:\\Users\\Mateusx132\\Desktop\\Output", "Amnesia.zip.gz.001");
                    long partSize = 10000000;
                    ChecksumAlgorithm algo = ChecksumAlgorithmFactory.getDefault().fromName("SHA-256");
                    Path input = Path.of("C:\\Games\\Amnesia.The.Dark.Descent.v02.10.2018");
                    List<Path> inputs = Files.list(input).toList();

                    setPartSize(partSize);
                    
                    PartOutputStream partOut = new PartOutputStream(output, partSize, algo);
                    registerErrorCleanupAction(() -> {
                        partOut.deleteFiles();
                    });
                    CountingOutputStream out = new CountingOutputStream(partOut) {
                        @Override
                        protected void incrementCount(int value) {
                            super.incrementCount(value);

                            setTotalOutputCount(getCount());
                        }
                    };
                    CountingOutputStream in = new CountingOutputStream(new GZIPOutputStream(out)) {
                        @Override
                        protected void incrementCount(int value) {
                            super.incrementCount(value);

                            setTotalInputCount(getCount());
                        }
                    };

                    try (ZipOutputStream zipOut = new ZipOutputStream(in, StandardCharsets.UTF_8)) {
                        ZipCreator creator = new ZipCreator(zipOut, inputs.toArray(Path[]::new), algo) {
                            @Override
                            protected void onFile(Path file) {
                                setFilename(file.toString());
                                setFileSize(0);

                                incrementFileCount(1);

                                updateFileStatus(false);
                                updateStatus(false);
                            }

                            @Override
                            protected void onFileError(Path file, IOException reason) {
                                incrementErrors(1);
                                incrementFileCount(-1);
                                println("Error on " + file.toString() + ":\n" + UIUtils.stacktraceOf(reason));
                            }

                            @Override
                            protected void onFileProgress(Path file, boolean crc, long currentBytes, long totalBytes) {
                                if (currentBytes == 0) {
                                    setFileSize(totalBytes);
                                    if (crc) {
                                        setFilename("(CRC) " + file.toString());
                                    } else {
                                        setFilename(file.toString());
                                    }
                                }

                                setFileProgress(currentBytes);
                                updateFileStatus(false);
                            }
                        };
                        creator.create();

                        setFilename("Finished");
                        setFileSize(0);
                        updateFileStatus(true);
                        updateStatus(true);
                    }
                }
            };
            progress.start();
            progress.setVisible(true);
        });
    }

    private GUInterface() {

    }
}
