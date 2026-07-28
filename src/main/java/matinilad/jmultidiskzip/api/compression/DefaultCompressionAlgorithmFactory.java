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

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Cien
 */
public class DefaultCompressionAlgorithmFactory implements CompressionAlgorithmFactory {
    
    private static final DefaultCompressionAlgorithmFactory defaultInstance = new DefaultCompressionAlgorithmFactory();
    
    static {
        defaultInstance.addCompressionAlgorithm(new GZIPCompressionAlgorithm());
        defaultInstance.addCompressionAlgorithm(new XZCompressionAlgorithm());
    }
    
    public static DefaultCompressionAlgorithmFactory getDefault() {
        return defaultInstance;
    }
    
    private final List<CompressionAlgorithm> algorithms = new ArrayList<>();
    
    public DefaultCompressionAlgorithmFactory() {
        
    }
    
    public boolean addCompressionAlgorithm(CompressionAlgorithm algorithm) {
        if (algorithm == null || this.algorithms.contains(algorithm)) {
            return false;
        }
        return this.algorithms.add(algorithm);
    }

    public boolean removeCompressionAlgorithm(CompressionAlgorithm algorithm) {
        if (algorithm == null) {
            return false;
        }
        return this.algorithms.remove(algorithm);
    }
    
    @Override
    public CompressionAlgorithm[] getAlgorithms() {
        return this.algorithms.toArray(CompressionAlgorithm[]::new);
    }

    @Override
    public CompressionAlgorithm fromName(String name) {
        if (name == null) {
            return null;
        }
        for (CompressionAlgorithm a : this.algorithms) {
            if (a.getName().equalsIgnoreCase(name)) {
                return a;
            }
        }
        return null;
    }

    @Override
    public CompressionAlgorithm fromExtension(String extension) {
        if (extension == null) {
            return null;
        }
        for (CompressionAlgorithm a : this.algorithms) {
            for (int i = 0; i < a.getNumberOfExtensions(); i++) {
                if (a.getExtension(i).equalsIgnoreCase(extension)) {
                    return a;
                }
            }
        }
        return null;
    }

    @Override
    public CompressionAlgorithm fromMagicNumber(String magicNumber) {
        if (magicNumber == null) {
            return null;
        }
        magicNumber = magicNumber.toLowerCase();
        for (CompressionAlgorithm a:this.algorithms) {
            for (int i = 0; i < a.getNumberOfMagicNumbers(); i++) {
                if (magicNumber.startsWith(a.getMagicNumber(i).toLowerCase())) {
                    return a;
                }
            }
        }
        return null;
    }
    
}
