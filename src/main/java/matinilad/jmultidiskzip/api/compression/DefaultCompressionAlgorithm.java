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
package matinilad.jmultidiskzip.api.compression;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 *
 * @author Cien
 */
public abstract class DefaultCompressionAlgorithm implements CompressionAlgorithm {

    private final String name;
    private final String[] extensions;
    private final int minCompressionLevel;
    private final int maxCompressionLevel;
    private final int defaultCompressionLevel;
    private final String[] magicNumbers;

    public DefaultCompressionAlgorithm(
            String name,
            String[] extensions,
            int minCompressionLevel,
            int maxCompressionLevel,
            int defaultCompressionLevel,
            String[] magicNumbers
    ) {
        if (minCompressionLevel > maxCompressionLevel) {
            throw new IllegalArgumentException("minCompressionLevel > maxCompressionLevel");
        }
        if (defaultCompressionLevel < minCompressionLevel) {
            throw new IllegalArgumentException("defaultCompressionLevel < minCompressionLevel");
        }
        if (defaultCompressionLevel >= maxCompressionLevel) {
            throw new IllegalArgumentException("defaultCompressionLevel >= maxCompressionLevel");
        }
        
        this.name = Objects.requireNonNull(name, "name is null");
        this.extensions = Objects.requireNonNull(extensions, "extensions is null").clone();
        this.minCompressionLevel = minCompressionLevel;
        this.maxCompressionLevel = maxCompressionLevel;
        this.defaultCompressionLevel = defaultCompressionLevel;
        this.magicNumbers = Objects.requireNonNull(magicNumbers, "magicNumbers is null").clone();
    }
    
    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public int getNumberOfExtensions() {
        return this.extensions.length;
    }

    @Override
    public String getExtension(int index) {
        return this.extensions[index];
    }

    @Override
    public int getMinCompressionLevel() {
        return this.minCompressionLevel;
    }

    @Override
    public int getMaxCompressionLevel() {
        return this.maxCompressionLevel;
    }

    @Override
    public int getDefaultCompressionLevel() {
        return this.defaultCompressionLevel;
    }

    @Override
    public int getNumberOfMagicNumbers() {
        return this.magicNumbers.length;
    }

    @Override
    public String getMagicNumber(int index) {
        return this.magicNumbers[index];
    }
    
    protected void checkLevel(int compressionLevel) throws IOException {
        if (compressionLevel < getMinCompressionLevel()) {
            throw new IOException("compressionLevel < getMinCompressionLevel()");
        }
        if (compressionLevel >= getMaxCompressionLevel()) {
            throw new IOException("compressionLevel >= getMaxCompressionLevel()");
        }
    }
    
    @Override
    public abstract OutputStream compress(OutputStream out, int level) throws IOException;
    
    @Override
    public abstract InputStream decompress(InputStream in) throws IOException;
    
}
