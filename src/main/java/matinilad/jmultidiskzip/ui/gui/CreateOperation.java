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
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipOutputStream;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import matinilad.jmultidiskzip.api.ZipCreator;
import matinilad.jmultidiskzip.api.utils.Base64File;
import matinilad.jmultidiskzip.api.utils.CountingOutputStream;
import matinilad.jmultidiskzip.api.utils.EncryptedOutputStream;
import matinilad.jmultidiskzip.api.utils.HexOutputStream;
import matinilad.jmultidiskzip.api.utils.PartOutputStream;
import matinilad.jmultidiskzip.ui.UIUtils;

/**
 *
 * @author Cien
 */
@SuppressWarnings("serial")
public class CreateOperation extends ProgressDialog {

    private final CreateOperationData inputData;
    private PartOutputStream partOut = null;

    public CreateOperation(CreateOperationData inputData, Frame parent, boolean modal) {
        super(parent, modal);
        this.inputData = new CreateOperationData(inputData);
    }

    public CreateOperation(CreateOperationData inputData, Dialog parent, boolean modal) {
        super(parent, modal);
        this.inputData = new CreateOperationData(inputData);
    }

    @Override
    public void run() throws Throwable {
        CreateOperationData data = this.inputData;
        try {
            CreateOperationSettings settings = data.getSettings();
            setPartSize(settings.getPartSize());

            SwingUtilities.invokeLater(() -> {
                setTitle(data.getOutput().getParent().toString());
            });

            if (Files.exists(data.getOutput())) {
                SwingUtilities.invokeAndWait(() -> {
                    int response = JOptionPane.showConfirmDialog(
                            CreateOperation.this,
                            "Replace " + data.getOutput() + " ?",
                            "Replace file",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE
                    );
                    if (response != JOptionPane.YES_OPTION) {
                        cancel();
                    }
                });
            }
            
            List<Closeable> toClose = new ArrayList<>();
            
            OutputStream out = new PartOutputStream(
                    data.getOutput(),
                    settings.getPartSize(),
                    (data.hasPassword() ? null : settings.getPartHash())
            );
            this.partOut = (PartOutputStream) out;
            toClose.add(out);
            try {
                CountingOutputStream countOut = new CountingOutputStream(out);
                out = countOut;

                if (settings.getOutputFormat() != null) {
                    switch (settings.getOutputFormat()) {
                        case BASE64 -> {
                            out = Base64File.encode(out);
                            toClose.add(out);
                        }
                        case HEX -> {
                            out = new HexOutputStream(out);
                            toClose.add(out);
                        }
                    }
                }

                if (data.hasPassword()) {
                    try {
                        out = new EncryptedOutputStream(out, data.getUserSalt(), data.getPassword());
                        toClose.add(out);
                    } finally {
                        data.clearPassword();
                    }
                }

                if (settings.getCompression() != null) {
                    out = settings.getCompression().compress(out, settings.getCompressionLevel());
                    toClose.add(out);
                }

                CountingOutputStream countIn = new CountingOutputStream(out);
                ZipOutputStream zipOut = new ZipOutputStream(countIn, StandardCharsets.UTF_8);
                out = zipOut;
                toClose.add(out);

                ZipCreator creator = new ZipCreator(zipOut, data.getInput(), settings.getFileHash()) {
                    @Override
                    protected void onFile(Path file) {
                        if (Files.isDirectory(file)) {
                            setFilename(file.toString());
                            setFileSize(0);

                            updateFileStatus(false);
                        }
                    }

                    @Override
                    protected void onFileProgress(Path file, boolean crc, long currentBytes, long totalBytes) {
                        if (currentBytes == 0) {
                            if (crc) {
                                setFilename("(CRC) " + file.toString());
                            } else {
                                setFilename(file.toString());
                            }
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
                creator.create();
                
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
                toClose.clear();
            }
        } finally {
            data.clearPassword();
        }
    }

    @Override
    public boolean isErrorCleanupEnabled() {
        return this.partOut != null;
    }

    @Override
    public void doErrorCleanup() throws Throwable {
        if (this.partOut != null) {
            this.partOut.deleteFiles();
            this.partOut = null;
        }
    }

}
