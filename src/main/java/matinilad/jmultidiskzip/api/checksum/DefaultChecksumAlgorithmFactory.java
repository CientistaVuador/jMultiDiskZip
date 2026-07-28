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
package matinilad.jmultidiskzip.api.checksum;

import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author Cien
 */
public class DefaultChecksumAlgorithmFactory implements ChecksumAlgorithmFactory {

    private static final DefaultChecksumAlgorithmFactory defaultInstance = new DefaultChecksumAlgorithmFactory();

    static {
        try {
            ChecksumAlgorithm[] defaults = {
                new DefaultChecksumAlgorithm("SHA-256 (Secure) - 32 Bytes", "SHA-256", new String[]{"sha256"}),
                new DefaultChecksumAlgorithm("SHA1 (Insecure) - 20 Bytes", "SHA-1", new String[] {"sha1"}),
                new DefaultChecksumAlgorithm("MD5 (Fast, Insecure) - 16 Bytes", "MD5", new String[] {"md5"}),
                new CRC32ChecksumAlgorithm()
            };
            for (ChecksumAlgorithm d : defaults) {
                defaultInstance.addChecksumAlgorithm(d);
            }
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static DefaultChecksumAlgorithmFactory getDefault() {
        return defaultInstance;
    }

    private final Set<ChecksumAlgorithm> algorithms = new HashSet<>();

    public DefaultChecksumAlgorithmFactory() {

    }

    public boolean addChecksumAlgorithm(ChecksumAlgorithm algorithm) {
        if (algorithm == null) {
            return false;
        }
        return this.algorithms.add(algorithm);
    }

    public boolean removeChecksumAlgorithm(ChecksumAlgorithm algorithm) {
        if (algorithm == null) {
            return false;
        }
        return this.algorithms.remove(algorithm);
    }

    @Override
    public ChecksumAlgorithm[] getAlgorithms() {
        return this.algorithms.toArray(ChecksumAlgorithm[]::new);
    }

    @Override
    public ChecksumAlgorithm fromName(String name) {
        if (name == null) {
            return null;
        }
        for (ChecksumAlgorithm a : this.algorithms) {
            if (a.getName().equalsIgnoreCase(name)) {
                return a;
            }
        }
        return null;
    }

    @Override
    public ChecksumAlgorithm fromExtension(String extension) {
        if (extension == null) {
            return null;
        }
        for (ChecksumAlgorithm a : this.algorithms) {
            for (int i = 0; i < a.getNumberOfExtensions(); i++) {
                if (a.getExtension(i).equalsIgnoreCase(extension)) {
                    return a;
                }
            }
        }
        return null;
    }

}
