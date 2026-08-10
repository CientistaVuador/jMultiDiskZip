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

import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Toolkit;
import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipInputStream;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import matinilad.jmultidiskzip.api.ZipChecksumTester;
import matinilad.jmultidiskzip.api.ZipCreator;
import matinilad.jmultidiskzip.api.ZipExtractor;
import matinilad.jmultidiskzip.api.checksum.ChecksumAlgorithm;
import matinilad.jmultidiskzip.api.compression.CompressionAlgorithm;
import matinilad.jmultidiskzip.api.compression.CompressionAlgorithmFactory;
import matinilad.jmultidiskzip.api.utils.Base64File;
import matinilad.jmultidiskzip.api.utils.CountingInputStream;
import matinilad.jmultidiskzip.api.utils.EncryptedInputStream;
import matinilad.jmultidiskzip.api.utils.HexInputStream;
import matinilad.jmultidiskzip.api.utils.HexOutputStream;
import matinilad.jmultidiskzip.api.utils.PartInputStream;
import matinilad.jmultidiskzip.api.utils.PartOutputStream;
import matinilad.jmultidiskzip.ui.UIUtils;

/**
 *
 * @author Cien
 */
@SuppressWarnings("serial")
public class ExtractOperation extends ProgressDialog {

    private final ExtractOperationData inputData;
    private ZipExtractor extractor = null;

    public ExtractOperation(ExtractOperationData inputData, Frame parent, boolean modal) {
        super(parent, modal);
        this.inputData = new ExtractOperationData(inputData);
    }

    public ExtractOperation(ExtractOperationData inputData, Dialog parent, boolean modal) {
        super(parent, modal);
        this.inputData = new ExtractOperationData(inputData);
    }

    @Override
    public void run() throws Throwable {
        SwingUtilities.invokeLater(() -> {
            setTitle(this.inputData.getOut().toAbsolutePath().normalize().toString());
        });
        setReverseCompressionRatio(true);
        
        HexFormat hex = HexFormat.of();

        ExtractOperationData data = this.inputData;
        ExtractOperationSettings settings = data.getSettings();

        Charset charset;
        try {
            charset = Charset.forName("ibm-850");
            //and hope it works
        } catch (IllegalCharsetNameException | UnsupportedCharsetException ex) {
            charset = Charset.defaultCharset();
        }

        List<Closeable> toClose = new ArrayList<>();
        try {
            try {
                InputStream in;
                if (PartOutputStream.getPartNumber(data.getIn()) != -1) {
                    in = new PartInputStream(data.getIn()) {
                        @Override
                        protected void onWaitingForNextPart(Path requiredPart) {
                            SwingUtilities.invokeLater(() -> {
                                Toolkit.getDefaultToolkit().beep();
                                InsertPartDialog partDialog = new InsertPartDialog(requiredPart, ExtractOperation.this, true);
                                partDialog.setVisible(true);
                                Path result = partDialog.getResult();
                                if (result != null) {
                                    continueSignal(result, false);
                                } else {
                                    cancel();
                                }
                            });
                        }
                    };
                } else {
                    in = new BufferedInputStream(Files.newInputStream(data.getIn()));
                }
                toClose.add(in);

                CountingInputStream countIn = new CountingInputStream(in);
                in = countIn;

                formatStream:
                {
                    byte[] magic = in.readNBytes(256);
                    String magicHex = hex.formatHex(magic);

                    PushbackInputStream pushback = new PushbackInputStream(in, magic.length);
                    pushback.unread(magic);

                    if (Base64File.isBase64File(magicHex)) {
                        in = Base64File.decode(pushback);
                        toClose.add(in);
                    } else if (HexOutputStream.isHexFile(magicHex)) {
                        in = new HexInputStream(pushback);
                        toClose.add(in);
                    } else {
                        in = pushback;
                    }
                }
                
                if (settings.isZipInZipEnabled()) {
                    ZipInputStream z = new ZipInputStream(in, charset);
                    if (z.getNextEntry() == null) {
                        throw new IOException("empty or corrupt zip file");
                    }
                    in = z;
                    toClose.add(in);
                }
                
                if (data.hasPassword()) {
                    try {
                        in = new EncryptedInputStream(in, data.getPassword());
                        toClose.add(in);
                    } finally {
                        data.clearPassword();
                    }
                }

                compressedStream:
                {
                    byte[] magicBytes = in.readNBytes(32);

                    PushbackInputStream pushBack = new PushbackInputStream(in, magicBytes.length);
                    pushBack.unread(magicBytes);

                    String magic = hex.formatHex(magicBytes);
                    CompressionAlgorithm compression = CompressionAlgorithmFactory.getDefault().fromMagicNumber(magic);

                    if (compression != null) {
                        in = compression.decompress(pushBack);
                        toClose.add(in);
                    } else {
                        in = pushBack;
                    }
                }

                verifyZip:
                {
                    byte[] magicBytes = in.readNBytes(32);

                    PushbackInputStream pushBack = new PushbackInputStream(in, magicBytes.length);
                    pushBack.unread(magicBytes);

                    if (!ZipCreator.isZipFile(hex.formatHex(magicBytes))) {
                        throw new IOException("Invalid or corrupt zip file");
                    }

                    in = pushBack;
                }

                CountingInputStream countOut = new CountingInputStream(in);
                ZipInputStream zip = new ZipInputStream(countOut, charset);
                in = zip;
                toClose.add(in);

                this.extractor = new ZipExtractor(zip, data.getOut()) {
                    private int replaceFiles = 0;

                    @Override
                    protected void onFile(Path file, boolean directory, long expectedSize) {
                        if (directory) {
                            setFilename("Creating " + file.toString());
                            setFileSize(0);

                            updateFileStatus(true);
                        }
                    }

                    @Override
                    protected boolean onShouldReplaceFile(Path file, long expectedSize) throws InterruptedException {
                        if (this.replaceFiles == 1) {
                            return true;
                        }
                        if (this.replaceFiles == -1) {
                            return false;
                        }

                        AtomicBoolean result = new AtomicBoolean(false);
                        AtomicBoolean forAll = new AtomicBoolean(false);
                        try {
                            SwingUtilities.invokeAndWait(() -> {
                                long otherSize = 0;
                                try {
                                    otherSize = Files.size(file);
                                } catch (IOException ex) {
                                    //ignore
                                }
                                int r = JOptionPane.showOptionDialog(
                                        ExtractOperation.this,
                                        "Replace\n" + file + "\n  " + UIUtils.formatBytes(otherSize) + "\nWith\n" + file + "\n  " + UIUtils.formatBytes(expectedSize) + "\n?",
                                        "Replace file?",
                                        JOptionPane.DEFAULT_OPTION,
                                        JOptionPane.QUESTION_MESSAGE,
                                        null,
                                        new Object[]{"Yes", "No", "Yes for all", "No for all"},
                                        "No"
                                );
                                if (r != JOptionPane.CLOSED_OPTION) {
                                    if (r == 0 || r == 2) {
                                        result.set(true);
                                    }
                                    if (r == 2 || r == 3) {
                                        forAll.set(true);
                                    }
                                }
                            });
                        } catch (InvocationTargetException ex) {
                            throw new RuntimeException(ex);
                        }

                        if (forAll.get()) {
                            if (result.get()) {
                                this.replaceFiles = 1;
                            } else {
                                this.replaceFiles = -1;
                            }
                        }
                        return result.get();
                    }

                    @Override
                    protected void onFileProgress(Path file, long currentBytes, long totalBytes) {
                        if (currentBytes == 0) {
                            setFilename(file.toString());
                            setFileSize(totalBytes);
                        }
                        setFileProgress(currentBytes);

                        setTotalInputCount(countIn.getCount());
                        setTotalOutputCount(countOut.getCount());

                        updateFileStatus(false);
                        updateStatus(false);
                    }

                    @Override
                    protected void onFileError(Path file, IOException reason) {
                        incrementErrors(1);
                        println("Error on: " + file.toString());
                        println(UIUtils.stacktraceOf(reason));
                    }

                    @Override
                    protected void onFileSuccess(Path file) {
                        setTotalInputCount(countIn.getCount());
                        setTotalOutputCount(countOut.getCount());
                        incrementFileCount(1);

                        updateStatus(false);
                    }
                };

                ZipChecksumTester tester = null;
                if (!settings.isNoVerifyEnabled()) {
                    tester = new ZipChecksumTester() {
                        @Override
                        protected void onFile(Path file, boolean directory, ChecksumAlgorithm algorithm) {
                            if (directory) {
                                setFilename("Checking " + file.toString());
                                setFileSize(0);
                            } else {
                                setFilename("Verifying ("+algorithm.getName()+") "+file.toString());
                            }
                            
                            updateFileStatus(true);
                        }

                        @Override
                        protected void onFileProgress(Path file, long currentBytes, long totalBytes) {
                            if (currentBytes == 0) {
                                setFileSize(totalBytes);
                            }
                            setFileProgress(currentBytes);

                            setTotalInputCount(countIn.getCount());
                            setTotalOutputCount(countOut.getCount());

                            updateFileStatus(false);
                            updateStatus(false);
                        }

                        @Override
                        protected void onFileError(Path file, IOException reason) {
                            incrementErrors(1);
                            println("Verify failed on: " + file.toString());
                            println(UIUtils.stacktraceOf(reason));
                        }
                    };
                }

                this.extractor.extract(tester);
                
                setTotalInputCount(countIn.getCount());
                setTotalOutputCount(countOut.getCount());
                
                updateStatus(true);
            } finally {
                for (int i = toClose.size() - 1; i >= 0; i--) {
                    try {
                        toClose.get(i).close();
                    } catch (Throwable t) {
                        //ignore
                    }
                }
            }
        } finally {
            this.inputData.clearPassword();
        }
    }

    @Override
    public boolean isErrorCleanupEnabled() {
        return this.extractor != null;
    }

    @Override
    public void doErrorCleanup() throws Throwable {
        if (this.extractor != null) {
            this.extractor.deleteFiles();
            this.extractor = null;
        }
    }

}
