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

import java.awt.Toolkit;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import matinilad.jmultidiskzip.ui.UIUtils;

/**
 *
 * @author Cien
 */
@SuppressWarnings("serial")
public abstract class ProgressDialog extends javax.swing.JDialog {

    private String filename = "Filename";
    private long fileProgress = 0;
    private long fileSize = 0;

    private long lastFileUpdateProgress = 0;
    private long lastFileUpdateTime = -1;

    private Future<?> fileStatusUpdate = null;

    private long totalInputCount = 0;
    private long totalOutputCount = 0;
    private long partSize = -1;
    private boolean reverseCompressionRatio = false;
    private int fileCount = 0;

    private Future<?> statusUpdate = null;

    private int errors = 0;

    private Thread thread = null;
    private boolean sentInterruptSignal = false;

    private boolean closeAfterFinishEnabled = false;

    public ProgressDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
        initComponents();
        clearComponents();
        setLocationRelativeTo(parent);
    }

    public ProgressDialog(java.awt.Dialog dialog, boolean modal) {
        super(dialog, modal);
        initComponents();
        clearComponents();
        setLocationRelativeTo(dialog);
    }

    public void setCloseAfterFinishEnabled(boolean closeAfterFinishEnabled) {
        this.closeAfterFinishEnabled = closeAfterFinishEnabled;
    }

    public boolean isCloseAfterFinishEnabled() {
        return closeAfterFinishEnabled;
    }

    private void guiSetFilename(String name) {
        this.filenameField.setText(name);
    }

    private void guiSetFileProgress(long current, long size) {
        this.sizeField.setText(UIUtils.formatBytesShort(current) + " of " + UIUtils.formatBytesShort(size));
        double progress = current / ((double) size);
        if (!Double.isFinite(progress)) {
            progress = 0.0;
        }
        progress = Math.min(Math.max(progress, 0.0), 1.0);
        int progressInt = (int) (this.progressBar.getMaximum() * progress);
        progressInt += this.progressBar.getMinimum();
        this.progressBar.setValue(progressInt);
    }

    private void guiSetFileSpeed(long bytes) {
        this.speedField.setText(UIUtils.formatBytesShort(bytes) + "/s");
    }

    private void guiSetFileEstimatedTime(long time) {
        this.estimatedTimeField.setText(UIUtils.formatCountdownSeconds(time));
    }

    private void guiSetTotalInputOutput(long totalInput, long totalOutput, boolean reverseRatio) {
        this.totalInputField.setText(UIUtils.formatBytes(totalInput));
        this.totalOutputField.setText(UIUtils.formatBytes(totalOutput));
        double compressionRatio = totalOutput / ((double) totalInput);
        if (reverseRatio) {
            compressionRatio = 1.0 / compressionRatio;
        }
        if (!Double.isFinite(compressionRatio)) {
            compressionRatio = 0.0;
        }
        this.compressionRatioField.setText(String.format("%.2f", compressionRatio * 100.0) + "%");
    }

    private void guiSetTotalFiles(int files) {
        this.totalFilesField.setText(files + (files == 1 ? " File " : " Files "));
    }

    private void guiSetPartCount(long size, long partSize) {
        if (partSize <= 0) {
            this.partCountField.setText("");
            return;
        }
        long parts = size / partSize;
        long remainder = size - (parts * partSize);
        String partCount = "";
        if (parts != 0) {
            partCount += parts + (parts == 1 ? " Part" : " Parts") + " of " + UIUtils.formatBytes(partSize);
            if (remainder != 0) {
                partCount += " + ";
            }
        }
        if (remainder != 0) {
            partCount += "1 Part of " + UIUtils.formatBytes(remainder);
        }
        this.partCountField.setText(partCount);
    }

    private void guiSetLogErrorsLabel(int err) {
        this.logErrorsLabel.setText("Log (" + err + (err == 1 ? " Error" : " Errors") + ")");
    }

    private void clearComponents() {
        guiSetFilename("");
        guiSetFileProgress(0, 0);
        guiSetFileSpeed(0);
        guiSetFileEstimatedTime(0);
        guiSetTotalInputOutput(0, 0, false);
        guiSetTotalFiles(0);
        guiSetPartCount(0, -1);
        guiSetLogErrorsLabel(0);
        this.logTextArea.setText("");
        this.cancelButton.setEnabled(false);
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content of this method is always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        filenameField = new javax.swing.JTextField();
        progressBar = new javax.swing.JProgressBar();
        speedField = new javax.swing.JTextField();
        sizeField = new javax.swing.JTextField();
        estimatedTimeField = new javax.swing.JTextField();
        jPanel2 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        totalInputField = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        compressionRatioField = new javax.swing.JTextField();
        totalOutputField = new javax.swing.JTextField();
        logErrorsLabel = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        logTextArea = new javax.swing.JTextArea();
        jLabel4 = new javax.swing.JLabel();
        totalFilesField = new javax.swing.JTextField();
        cancelButton = new javax.swing.JButton();
        partCountField = new javax.swing.JTextField();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("Title goes here");
        setIconImage(new ImageIcon(MainWindow.class.getResource("progress.png")).getImage());
        setMinimumSize(new java.awt.Dimension(250, 450));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Current file status"));

        filenameField.setEditable(false);
        filenameField.setText("Filename");
        filenameField.setBorder(null);

        progressBar.setMaximum(10000);
        progressBar.setToolTipText("");
        progressBar.setName(""); // NOI18N
        progressBar.setStringPainted(true);

        speedField.setEditable(false);
        speedField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        speedField.setText("100 MiB/s");
        speedField.setBorder(null);

        sizeField.setEditable(false);
        sizeField.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        sizeField.setText("568 GiB of 48979 GiB");
        sizeField.setBorder(null);

        estimatedTimeField.setEditable(false);
        estimatedTimeField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        estimatedTimeField.setText("10m10s estimated");
        estimatedTimeField.setBorder(null);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(progressBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(filenameField)
                    .addComponent(estimatedTimeField, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addComponent(sizeField, javax.swing.GroupLayout.DEFAULT_SIZE, 283, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(speedField, javax.swing.GroupLayout.DEFAULT_SIZE, 239, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(filenameField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(6, 6, 6)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(sizeField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(speedField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(estimatedTimeField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("Status"));

        jLabel1.setText("Total (Input):");

        totalInputField.setEditable(false);
        totalInputField.setText("100 MiB");
        totalInputField.setBorder(null);

        jLabel2.setText("Total (Output):");

        jLabel3.setText("Compression Ratio:");

        compressionRatioField.setEditable(false);
        compressionRatioField.setText("100.00%");
        compressionRatioField.setBorder(null);

        totalOutputField.setEditable(false);
        totalOutputField.setText("100 MiB");
        totalOutputField.setBorder(null);

        logErrorsLabel.setText("Log (0 Errors):");

        logTextArea.setEditable(false);
        logTextArea.setColumns(20);
        logTextArea.setRows(5);
        jScrollPane1.setViewportView(logTextArea);

        jLabel4.setText("Total (Files):");

        totalFilesField.setEditable(false);
        totalFilesField.setText("100 Files");
        totalFilesField.setBorder(null);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(logErrorsLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(jLabel4, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabel3, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(9, 9, 9)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(compressionRatioField)
                            .addComponent(totalOutputField)
                            .addComponent(totalInputField)
                            .addComponent(totalFilesField))))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(totalInputField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(totalOutputField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(compressionRatioField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(totalFilesField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(logErrorsLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 150, Short.MAX_VALUE)
                .addContainerGap())
        );

        cancelButton.setText("Cancel");
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        partCountField.setEditable(false);
        partCountField.setText("10 Parts of 1MiB + 1 Part of 5 KiB");
        partCountField.setBorder(null);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(partCountField)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cancelButton)
                    .addComponent(partCountField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private int showCancelWindow() {
        return JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to cancel the operation?",
                "Cancel operation",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
    }

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        if (!this.sentInterruptSignal) {
            if (showCancelWindow() == JOptionPane.YES_OPTION) {
                cancel();
            }
        } else {
            cancel();
        }
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        if (this.cancelButton.isEnabled()) {
            if (!this.sentInterruptSignal) {
                if (showCancelWindow() != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            cancel();
        }
        setVisible(false);
        dispose();
    }//GEN-LAST:event_formWindowClosing

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        Objects.requireNonNull(filename, "fileName is null");
        this.filename = filename;
    }

    public long getFileProgress() {
        return fileProgress;
    }

    public void setFileProgress(long fileProgress) {
        if (fileProgress < 0) {
            throw new IllegalArgumentException("fileProgress < 0");
        }
        if (fileProgress > getFileSize()) {
            throw new IllegalArgumentException("fileProgress > getFileSize()");
        }
        if (fileProgress < getFileProgress()) {
            throw new IllegalArgumentException("fileProgress < getFileProgress()");
        }
        this.fileProgress = fileProgress;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        if (fileSize < 0) {
            throw new IllegalArgumentException("fileSize < 0");
        }
        this.fileSize = fileSize;
        this.fileProgress = 0;
        this.lastFileUpdateProgress = 0;
        this.lastFileUpdateTime = -1;
    }

    public boolean updateFileStatus(boolean force) {
        if (!force && this.fileStatusUpdate != null && !this.fileStatusUpdate.isDone()) {
            return false;
        }

        List<Runnable> toRun = new ArrayList<>();

        final String newName = getFilename();
        final long newProgress = getFileProgress();
        final long newSize = getFileSize();

        Runnable updateFile = () -> {
            guiSetFilename(newName);
            guiSetFileProgress(newProgress, newSize);
        };
        toRun.add(updateFile);

        if (this.lastFileUpdateTime == -1) {
            this.lastFileUpdateTime = System.nanoTime();
            Runnable updateTime = () -> {
                guiSetFileSpeed(0);
                guiSetFileEstimatedTime(0);
            };
            toRun.add(updateTime);
        } else {
            double timePassed = (System.nanoTime() - this.lastFileUpdateTime) / 1E9d;
            if (timePassed >= 1.0) {
                long bytesProcessed = getFileProgress() - this.lastFileUpdateProgress;

                final double transferSpeed = bytesProcessed / timePassed;
                final double estimatedTime = (getFileSize() - getFileProgress()) / transferSpeed;

                this.lastFileUpdateTime = System.nanoTime();
                this.lastFileUpdateProgress = getFileProgress();

                Runnable updateTime = () -> {
                    guiSetFileSpeed((long) transferSpeed);
                    guiSetFileEstimatedTime((long) estimatedTime);
                };
                toRun.add(updateTime);
            }
        }

        if (force) {
            CompletableFuture<Void> completableFuture = new CompletableFuture<>();
            SwingUtilities.invokeLater(() -> {
                try {
                    for (Runnable r : toRun) {
                        r.run();
                    }
                } finally {
                    completableFuture.complete(null);
                }
            });
            this.fileStatusUpdate = completableFuture;
        } else {
            this.fileStatusUpdate = CompletableFuture.runAsync(() -> {
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        for (Runnable r : toRun) {
                            r.run();
                        }
                    });
                } catch (InterruptedException | InvocationTargetException ex) {
                    //ignored
                }
            });
        }
        return true;
    }

    public void setTotalInputCount(long totalInputCount) {
        if (totalInputCount < 0) {
            throw new IllegalArgumentException("totalInputCount < 0");
        }
        this.totalInputCount = totalInputCount;
    }

    public long getTotalInputCount() {
        return totalInputCount;
    }

    public void setTotalOutputCount(long totalOutputCount) {
        if (totalOutputCount < 0) {
            throw new IllegalArgumentException("totalOutputCount < 0");
        }
        this.totalOutputCount = totalOutputCount;
    }

    public long getTotalOutputCount() {
        return totalOutputCount;
    }

    public void setReverseCompressionRatio(boolean reverseCompressionRatio) {
        this.reverseCompressionRatio = reverseCompressionRatio;
    }

    public boolean isReverseCompressionRatio() {
        return reverseCompressionRatio;
    }

    public long getPartSize() {
        return partSize;
    }

    public void setPartSize(long partSize) {
        if (partSize < 0) {
            partSize = -1;
        }
        if (partSize == 0) {
            throw new IllegalArgumentException("partSize == 0");
        }
        this.partSize = partSize;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void incrementFileCount(int toIncrement) {
        if (toIncrement == 0) {
            return;
        }
        int result = this.fileCount + toIncrement;
        if (result < 0) {
            result = 0;
        }
        this.fileCount = result;
    }

    public void resetFileCount() {
        this.fileCount = 0;
    }

    public boolean updateStatus(boolean force) {
        if (!force && this.statusUpdate != null && !this.statusUpdate.isDone()) {
            return false;
        }

        final long input = getTotalInputCount();
        final long output = getTotalOutputCount();
        final boolean reverseRatio = isReverseCompressionRatio();
        final long part = getPartSize();
        final int fCount = getFileCount();

        Runnable updateTask = () -> {
            guiSetTotalInputOutput(input, output, reverseRatio);
            guiSetPartCount(output, part);
            guiSetTotalFiles(fCount);
        };

        if (force) {
            CompletableFuture<Void> completableFuture = new CompletableFuture<>();
            SwingUtilities.invokeLater(() -> {
                try {
                    updateTask.run();
                } finally {
                    completableFuture.complete(null);
                }
            });
            this.statusUpdate = completableFuture;
        } else {
            this.statusUpdate = CompletableFuture.runAsync(() -> {
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        updateTask.run();
                    });
                } catch (InterruptedException | InvocationTargetException ex) {
                    //ignored
                }
            });
        }
        return true;
    }

    public int getErrors() {
        return errors;
    }

    public void incrementErrors(int toIncrement) {
        if (toIncrement == 0) {
            return;
        }
        if (toIncrement < 0) {
            throw new IllegalArgumentException("toIncrement < 0");
        }
        this.errors += toIncrement;

        final int finalErrors = getErrors();
        SwingUtilities.invokeLater(() -> {
            guiSetLogErrorsLabel(finalErrors);
        });
    }

    public void print(String text) {
        SwingUtilities.invokeLater(() -> {
            this.logTextArea.append(text);
        });
    }

    public void println(String text) {
        SwingUtilities.invokeLater(() -> {
            this.logTextArea.append(text);
            this.logTextArea.append("\n");
        });
    }

    public void start() {
        if (this.thread != null) {
            throw new UnsupportedOperationException("already started");
        }
        this.thread = new Thread(() -> {
            List<Runnable> finishTasks = new ArrayList<>();
            try {
                try {
                    ProgressDialog.this.run();
                } catch (Throwable t) {
                    boolean interrupted = (t instanceof InterruptedException);
                    if (!interrupted && t instanceof IOException io) {
                        interrupted = (io.getCause() instanceof InterruptedException);
                    }
                    if (!interrupted) {
                        ProgressDialog.this.incrementErrors(1);
                        ProgressDialog.this.println(UIUtils.stacktraceOf(t));
                        finishTasks.add(() -> {
                            Toolkit.getDefaultToolkit().beep();
                            JOptionPane.showMessageDialog(
                                    ProgressDialog.this,
                                    "The operation has failed, check log for details.",
                                    "Operation failed!",
                                    JOptionPane.ERROR_MESSAGE
                            );
                        });
                    }

                    finishTasks.add(() -> {
                        if (isErrorCleanupEnabled()) {
                            int response = JOptionPane.showConfirmDialog(
                                    ProgressDialog.this,
                                    "Clear files generated during the process?",
                                    "Clear files",
                                    JOptionPane.YES_NO_OPTION,
                                    JOptionPane.WARNING_MESSAGE
                            );
                            if (response == JOptionPane.YES_OPTION) {
                                try {
                                    doErrorCleanup();
                                } catch (Throwable e) {
                                    //ignore
                                }
                            }
                        }
                    });
                }
            } finally {
                finishTasks.add(() -> {
                    this.logTextArea.append("Finished\n");
                    this.cancelButton.setEnabled(false);
                });

                setFilename("Finished");
                setFileSize(0);
                updateFileStatus(true);

                finishTasks.add(() -> {
                    if (isCloseAfterFinishEnabled() && getErrors() == 0) {
                        setVisible(false);
                        dispose();
                    }
                });

                SwingUtilities.invokeLater(() -> {
                    for (Runnable r : finishTasks) {
                        r.run();
                    }
                });
            }
        });
        this.thread.setDaemon(true);
        this.thread.start();

        this.cancelButton.setEnabled(true);
    }

    public boolean isRunning() {
        return this.thread != null;
    }

    public void cancel() {
        SwingUtilities.invokeLater(() -> {
            if (this.thread != null && this.thread.isAlive()) {
                this.thread.interrupt();
                this.logTextArea.append("Sent interrupt signal\n");
                this.sentInterruptSignal = true;
            }
        });
    }

    public abstract void run() throws Throwable;

    public boolean isErrorCleanupEnabled() {
        return false;
    }

    public void doErrorCleanup() throws Throwable {

    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JTextField compressionRatioField;
    private javax.swing.JTextField estimatedTimeField;
    private javax.swing.JTextField filenameField;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel logErrorsLabel;
    private javax.swing.JTextArea logTextArea;
    private javax.swing.JTextField partCountField;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JTextField sizeField;
    private javax.swing.JTextField speedField;
    private javax.swing.JTextField totalFilesField;
    private javax.swing.JTextField totalInputField;
    private javax.swing.JTextField totalOutputField;
    // End of variables declaration//GEN-END:variables
}
