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

import java.util.ArrayList;
import java.util.List;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.SpinnerNumberModel;
import matinilad.jmultidiskzip.api.checksum.ChecksumAlgorithm;
import matinilad.jmultidiskzip.api.checksum.ChecksumAlgorithmFactory;
import matinilad.jmultidiskzip.api.compression.CompressionAlgorithm;
import matinilad.jmultidiskzip.api.compression.CompressionAlgorithmFactory;
import matinilad.jmultidiskzip.api.utils.OutputFormat;

/**
 *
 * @author Cien
 */
@SuppressWarnings("serial")
public class SettingsDialog extends javax.swing.JDialog {

    public static enum Unit {
        B("B", 1),
        KIB("KiB", 1024),
        MIB("MiB", 1024 * 1024),
        GIB("GiB", 1024 * 1024 * 1024),
        KB("KB", 1000),
        MB("MB", 1000 * 1000),
        GB("GB", 1000 * 1000 * 1000);

        private final String name;
        private final long size;

        private Unit(String name, long size) {
            this.name = name;
            this.size = size;
        }

        public String getName() {
            return name;
        }

        public long getSize() {
            return size;
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    private final CreateOperationSettings createSettings = new CreateOperationSettings();
    private final ExtractOperationSettings extractSettings = new ExtractOperationSettings();

    public SettingsDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
        initComponents();
        setupComponents();
        setLocationRelativeTo(parent);
    }

    public SettingsDialog(java.awt.Dialog parent, boolean modal) {
        super(parent, modal);
        initComponents();
        setupComponents();
        setLocationRelativeTo(parent);
    }

    private Unit getPartSizeUnit() {
        return ((Unit) this.partSizeUnit.getSelectedItem());
    }

    private void setupComponents() {
        this.createSettings.load();
        this.extractSettings.load();

        this.partSizeUnit.setSelectedItem(Config.getObject("settingsDialog.partSizeUnit", Unit::valueOf, Unit.B));

        SpinnerNumberModel model = (SpinnerNumberModel) this.partSizeSpinner.getModel();
        model.setMinimum(1L);
        model.setMaximum(Long.MAX_VALUE / getPartSizeUnit().getSize());
        long partSize = this.createSettings.getPartSize() / getPartSizeUnit().getSize();
        if (partSize <= 0) {
            partSize = 1;
        }
        model.setValue(partSize);

        this.partSizeSpinner.addChangeListener((e) -> {
            this.createSettings.setPartSize(((Long) this.partSizeSpinner.getValue()) * getPartSizeUnit().getSize());
        });

        this.partSizeUnit.addActionListener((e) -> {
            long max = Long.MAX_VALUE / getPartSizeUnit().getSize();
            model.setMaximum(max);
            if (((Long) model.getValue()) >= max) {
                model.setValue(max - 1);
            }
            Config.setObject("settingsDialog.partSizeUnit", Unit::name, getPartSizeUnit());
        });

        List<String> hashes = new ArrayList<>();
        for (ChecksumAlgorithm algo : ChecksumAlgorithmFactory.getDefault().getAlgorithms()) {
            hashes.add(algo.getName());
        }
        hashes.add("None");

        this.partHashBox.setModel(new DefaultComboBoxModel<>(hashes.toArray(String[]::new)));
        this.fileHashBox.setModel(new DefaultComboBoxModel<>(hashes.toArray(String[]::new)));

        ChecksumAlgorithm ph = this.createSettings.getPartHash();
        this.partHashBox.setSelectedItem(ph == null ? "None" : ph.getName());
        ChecksumAlgorithm fh = this.createSettings.getFileHash();
        this.fileHashBox.setSelectedItem(fh == null ? "None" : fh.getName());

        this.partHashBox.addActionListener((e) -> {
            this.createSettings.setPartHash(ChecksumAlgorithmFactory.getDefault().fromName(this.partHashBox.getSelectedItem().toString()));
        });

        this.fileHashBox.addActionListener((e) -> {
            this.createSettings.setFileHash(ChecksumAlgorithmFactory.getDefault().fromName(this.fileHashBox.getSelectedItem().toString()));
        });

        List<String> compressionAlgorithms = new ArrayList<>();
        for (CompressionAlgorithm algo : CompressionAlgorithmFactory.getDefault().getAlgorithms()) {
            compressionAlgorithms.add(algo.getName());
        }
        compressionAlgorithms.add("none");

        this.compressionBox.setModel(new DefaultComboBoxModel<>(compressionAlgorithms.toArray(String[]::new)));

        CompressionAlgorithm selectedCompression = this.createSettings.getCompression();
        if (selectedCompression == null) {
            this.compressionBox.setSelectedItem("none");
            this.compressionSlider.setEnabled(false);
            this.compressionLevel.setEnabled(false);
            this.compressionSlider.setMinimum(0);
            this.compressionSlider.setMaximum(0);
            this.compressionSlider.setValue(0);
            this.compressionLevel.setText("0");
        } else {
            this.compressionBox.setSelectedItem(selectedCompression.getName());
            this.compressionSlider.setMinimum(selectedCompression.getMinCompressionLevel());
            this.compressionSlider.setMaximum(selectedCompression.getMaxCompressionLevel() - 1);
            this.compressionSlider.setValue(this.createSettings.getCompressionLevel());
            this.compressionLevel.setText(Integer.toString(this.createSettings.getCompressionLevel()));
        }

        this.compressionSlider.addChangeListener((e) -> {
            this.createSettings.setCompressionLevel(this.compressionSlider.getValue());
            this.compressionLevel.setText(Integer.toString(this.compressionSlider.getValue()));
        });
        
        this.compressionBox.addActionListener((e) -> {
            CompressionAlgorithm algo = CompressionAlgorithmFactory.getDefault().fromName(this.compressionBox.getSelectedItem().toString());
            if (algo == this.createSettings.getCompression()) {
                return;
            }
            this.createSettings.setCompression(algo);
            if (algo == null) {
                this.compressionSlider.setEnabled(false);
                this.compressionLevel.setEnabled(false);
                this.compressionSlider.setMinimum(0);
                this.compressionSlider.setMaximum(0);
            } else {
                this.compressionSlider.setEnabled(true);
                this.compressionLevel.setEnabled(true);
                this.compressionSlider.setMinimum(algo.getMinCompressionLevel());
                this.compressionSlider.setMaximum(algo.getMaxCompressionLevel() - 1);
            }
            this.createSettings.setCompressionLevel((algo == null ? 0 : algo.getDefaultCompressionLevel()));
            this.compressionSlider.setValue(this.createSettings.getCompressionLevel());
            this.compressionLevel.setText(Integer.toString(this.createSettings.getCompressionLevel()));
        });
        
        List<String> outputFormats = new ArrayList<>();
        for (OutputFormat o:OutputFormat.values()) {
            outputFormats.add(o.name().toLowerCase());
        }
        outputFormats.add("binary");
        
        this.outputFormatBox.setModel(new DefaultComboBoxModel<>(outputFormats.toArray(String[]::new)));
        this.outputFormatBox.setSelectedItem((this.createSettings.getOutputFormat() == null ? "binary" : this.createSettings.getOutputFormat().name().toLowerCase()));
        
        this.outputFormatBox.addActionListener((e) -> {
            OutputFormat selected;
            try {
                selected = OutputFormat.valueOf(this.outputFormatBox.getSelectedItem().toString().toUpperCase());
            } catch (IllegalArgumentException ex) {
                selected = null;
            }
            this.createSettings.setOutputFormat(selected);
        });
        
        this.includeHiddenFiles.setSelected(this.createSettings.isHiddenFilesEnabled());
        this.includeHiddenFiles.addActionListener((e) -> {
            this.createSettings.setHiddenFilesEnabled(this.includeHiddenFiles.isSelected());
        });
        
        this.zipInZip.setSelected(this.extractSettings.isZipInZipEnabled());
        this.zipInZip.addActionListener((e) -> {
            this.extractSettings.setZipInZipEnabled(this.zipInZip.isSelected());
        });
        
        this.dontVerifyFiles.setSelected(this.extractSettings.isNoVerifyEnabled());
        this.dontVerifyFiles.addActionListener((e) -> {
            this.extractSettings.setNoVerifyEnabled(this.dontVerifyFiles.isSelected());
        });
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content of this method is always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        partSizeSpinner = new javax.swing.JSpinner();
        partSizeUnit = new javax.swing.JComboBox<>();
        jLabel2 = new javax.swing.JLabel();
        fileHashBox = new javax.swing.JComboBox<>();
        jLabel3 = new javax.swing.JLabel();
        partHashBox = new javax.swing.JComboBox<>();
        jLabel4 = new javax.swing.JLabel();
        compressionBox = new javax.swing.JComboBox<>();
        compressionSlider = new javax.swing.JSlider();
        compressionLevel = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        outputFormatBox = new javax.swing.JComboBox<>();
        includeHiddenFiles = new javax.swing.JCheckBox();
        jPanel2 = new javax.swing.JPanel();
        dontVerifyFiles = new javax.swing.JCheckBox();
        zipInZip = new javax.swing.JCheckBox();
        okButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Settings");
        setResizable(false);

        jLabel1.setText("Part Size:");

        partSizeSpinner.setModel(new javax.swing.SpinnerNumberModel(Long.valueOf(9223372036854775807L), Long.valueOf(0L), Long.valueOf(9223372036854775807L), Long.valueOf(1L)));

        partSizeUnit.setModel(new DefaultComboBoxModel<Unit>(Unit.values()));

        jLabel2.setText("Part Hash:");

        fileHashBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        jLabel3.setText("File Hash:");

        partHashBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        jLabel4.setText("Compression:");

        compressionBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        compressionSlider.setMajorTickSpacing(1);
        compressionSlider.setMaximum(10);
        compressionSlider.setSnapToTicks(true);
        compressionSlider.setValue(5);

        compressionLevel.setText("0");

        jLabel6.setText("Output Format:");

        outputFormatBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        includeHiddenFiles.setText("Include hidden files");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(jLabel6, javax.swing.GroupLayout.DEFAULT_SIZE, 85, Short.MAX_VALUE)
                            .addComponent(jLabel4, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabel3, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(compressionBox, 0, 90, Short.MAX_VALUE)
                            .addComponent(fileHashBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(outputFormatBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(compressionSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 149, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(compressionLevel, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, 85, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(partSizeSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 169, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(8, 8, 8)
                                .addComponent(partSizeUnit, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(partHashBox, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(includeHiddenFiles))
                .addContainerGap(32, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(8, 8, 8)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(partSizeSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(partSizeUnit, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(8, 8, 8)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(partHashBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(8, 8, 8)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(fileHashBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3))
                .addGap(8, 8, 8)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(compressionBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(compressionSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(compressionLevel)
                    .addComponent(jLabel4))
                .addGap(8, 8, 8)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel6)
                    .addComponent(outputFormatBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(8, 8, 8)
                .addComponent(includeHiddenFiles)
                .addContainerGap(62, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Create", jPanel1);

        dontVerifyFiles.setText("Don't verify files");

        zipInZip.setText("Zip in zip (If the zip file is contained inside another zip file)");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(dontVerifyFiles)
                    .addComponent(zipInZip))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(8, 8, 8)
                .addComponent(dontVerifyFiles)
                .addGap(8, 8, 8)
                .addComponent(zipInZip)
                .addContainerGap(184, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Extract", jPanel2);

        okButton.setText("OK");
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        cancelButton.setText("Cancel");
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jTabbedPane1)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(cancelButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(okButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jTabbedPane1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(okButton)
                    .addComponent(cancelButton))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        this.createSettings.save();
        this.extractSettings.save();
        setVisible(false);
        dispose();
    }//GEN-LAST:event_okButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        setVisible(false);
        dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    public void selectTab(String name) {
        int index = -1;
        for (int i = 0; i < this.jTabbedPane1.getTabCount(); i++) {
            if (this.jTabbedPane1.getTitleAt(i).equalsIgnoreCase(name)) {
                index = i;
                break;
            }
        }
        if (index != -1) {
            this.jTabbedPane1.setSelectedIndex(index);
        }
    }
    
    public void selectCreateTab() {
        selectTab("create");
    }
    
    public void selectExtractTab() {
        selectTab("extract");
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JComboBox<String> compressionBox;
    private javax.swing.JLabel compressionLevel;
    private javax.swing.JSlider compressionSlider;
    private javax.swing.JCheckBox dontVerifyFiles;
    private javax.swing.JComboBox<String> fileHashBox;
    private javax.swing.JCheckBox includeHiddenFiles;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JButton okButton;
    private javax.swing.JComboBox<String> outputFormatBox;
    private javax.swing.JComboBox<String> partHashBox;
    private javax.swing.JSpinner partSizeSpinner;
    private javax.swing.JComboBox<Unit> partSizeUnit;
    private javax.swing.JCheckBox zipInZip;
    // End of variables declaration//GEN-END:variables
}
