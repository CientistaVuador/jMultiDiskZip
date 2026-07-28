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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 *
 * @author Cien
 */
public class DefaultChecksumAlgorithm implements ChecksumAlgorithm {

    private final String displayName;
    private final String name;
    private final String[] extensions;
    private final int length;

    public DefaultChecksumAlgorithm(String displayName, String name, String[] extensions) throws NoSuchAlgorithmException {
        this.displayName = Objects.requireNonNull(displayName, "displayName is null");
        this.name = Objects.requireNonNull(name, "name is null");
        this.extensions = Objects.requireNonNull(extensions, "extensions is null").clone();
        this.length = MessageDigest.getInstance(name).getDigestLength();
    }
    
    @Override
    public String getDisplayName() {
        return this.displayName;
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
    public int getLength() {
        return this.length;
    }
    
    @Override
    public Checksum newChecksum() {
        try {
            return new DefaultChecksum(this);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
    }
    
}
