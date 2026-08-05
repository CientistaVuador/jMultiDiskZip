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

import matinilad.jmultidiskzip.api.utils.OutputFormat;
import matinilad.jmultidiskzip.api.checksum.ChecksumAlgorithm;
import matinilad.jmultidiskzip.api.checksum.ChecksumAlgorithmFactory;
import matinilad.jmultidiskzip.api.compression.CompressionAlgorithm;
import matinilad.jmultidiskzip.api.compression.CompressionAlgorithmFactory;

/**
 *
 * @author Cien
 */
public class CreateOperationSettings {
    
    private long partSize = 100 * 1024 * 1024;
    private ChecksumAlgorithm partHash = ChecksumAlgorithmFactory.getDefault().fromName("sha-256");
    private ChecksumAlgorithm fileHash = ChecksumAlgorithmFactory.getDefault().fromName("sha-256");
    private CompressionAlgorithm compression = CompressionAlgorithmFactory.getDefault().fromName("gz");
    private int compressionLevel = this.compression.getDefaultCompressionLevel();
    private OutputFormat outputFormat = null;
    
    public CreateOperationSettings() {
        
    }
    
    public CreateOperationSettings(CreateOperationSettings settings) {
        this.partSize = settings.partSize;
        this.partHash = settings.partHash;
        this.fileHash = settings.fileHash;
        this.compression = settings.compression;
        this.compressionLevel = settings.compressionLevel;
        this.outputFormat = settings.outputFormat;
    }

    public long getPartSize() {
        return partSize;
    }

    public void setPartSize(long partSize) {
        if (partSize <= 0) {
            throw new IllegalArgumentException("partSize <= 0");
        }
        this.partSize = partSize;
    }

    public ChecksumAlgorithm getPartHash() {
        return partHash;
    }

    public void setPartHash(ChecksumAlgorithm partHash) {
        this.partHash = partHash;
    }
    
    public void setPartHash(String name) {
        if (name == null) {
            setPartHash((ChecksumAlgorithm)null);
            return;
        }
        ChecksumAlgorithm algo = ChecksumAlgorithmFactory.getDefault().fromName(name);
        if (algo == null) {
            throw new IllegalArgumentException("for name: "+name);
        }
        setPartHash(algo);
    }

    public ChecksumAlgorithm getFileHash() {
        return fileHash;
    }

    public void setFileHash(ChecksumAlgorithm fileHash) {
        this.fileHash = fileHash;
    }
    
    public void setFileHash(String name) {
        if (name == null) {
            setFileHash((ChecksumAlgorithm)null);
            return;
        }
        ChecksumAlgorithm algo = ChecksumAlgorithmFactory.getDefault().fromName(name);
        if (algo == null) {
            throw new IllegalArgumentException("for name: "+name);
        }
        setFileHash(algo);
    }
    
    public CompressionAlgorithm getCompression() {
        return compression;
    }

    public void setCompression(CompressionAlgorithm compression) {
        this.compression = compression;
        if (compression != null) {
            this.compressionLevel = compression.getDefaultCompressionLevel();
        } else {
            this.compressionLevel = 0;
        }
    }
    
    public void setCompression(String name) {
        if (name == null) {
            setCompression((CompressionAlgorithm)null);
            return;
        }
        CompressionAlgorithm algo = CompressionAlgorithmFactory.getDefault().fromName(name);
        if (algo == null) {
            throw new IllegalArgumentException("for name: "+name);
        }
        setCompression(algo);
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }

    public void setCompressionLevel(int compressionLevel) {
        if (this.compression == null && compressionLevel != 0) {
            throw new IllegalArgumentException("this.compression == null && compressionLevel != 0");
        }
        if (this.compression == null) {
            this.compressionLevel = 0;
            return;
        }
        if (compressionLevel >= this.compression.getMaxCompressionLevel()) {
            throw new IllegalArgumentException("compressionLevel >= this.compression.getMaxCompressionLevel()");
        }
        if (compressionLevel < this.compression.getMinCompressionLevel()) {
            throw new IllegalArgumentException("compressionLevel < this.compression.getMinCompressionLevel()");
        }
        this.compressionLevel = compressionLevel;
    }
    
    public OutputFormat getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(OutputFormat outputFormat) {
        this.outputFormat = outputFormat;
    }
    
    public void setOutputFormat(String name) {
        if (name == null) {
            this.outputFormat = null;
            return;
        }
        OutputFormat format = OutputFormat.valueOf(name);
        if (format == null) {
            throw new IllegalArgumentException("for name: "+name);
        }
        this.outputFormat = format;
    }
    
    public void save() {
        Config.set("create.partSize", this.partSize);
        Config.setNullEncoded("create.partHash", (this.partHash == null ? null : this.partHash.getName()));
        Config.setNullEncoded("create.fileHash", (this.fileHash == null ? null : this.fileHash.getName()));
        Config.setNullEncoded("create.compression", (this.compression == null ? null : this.compression.getName()));
        Config.set("create.compressionLevel", this.compressionLevel);
        Config.setNullEncoded("create.outputFormat", (this.outputFormat == null ? null : this.outputFormat.name()));
    }
    
    public void load() {
        setPartSize(Config.getLong("create.partSize", this.partSize));
        setPartHash(Config.getNullEncoded("create.partHash", (this.partHash == null ? null : this.partHash.getName())));
        setFileHash(Config.getNullEncoded("create.fileHash", (this.fileHash == null ? null : this.fileHash.getName())));
        setCompression(Config.getNullEncoded("create.compression", (this.compression == null ? null : this.compression.getName())));
        setCompressionLevel(Config.getInt("create.compressionLevel", this.compressionLevel));
        setOutputFormat(Config.getNullEncoded("create.outputFormat", (this.outputFormat == null ? null : this.outputFormat.name())));
    }
    
}
