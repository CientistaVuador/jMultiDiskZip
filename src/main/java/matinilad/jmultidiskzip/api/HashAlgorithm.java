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
package matinilad.jmultidiskzip.api;

/**
 *
 * @author Cien
 */
public enum HashAlgorithm {
    SHA256("SHA256 [32 Bytes] (Recommended, Secure)", "SHA-256", "sha256"),
    SHA1("SHA1 [20 Bytes] (Insecure)", "SHA-1", "sha1"),
    MD5("MD5 [16 Bytes] (Fast, Insecure)", "MD5", "md5")
    ;
    
    public static HashAlgorithm fromExtension(String extension) {
        for (HashAlgorithm a:values()) {
            if (a.getExtension().equalsIgnoreCase(extension)) {
                return a;
            }
        }
        return null;
    }
    
    public static HashAlgorithm fromAlgorithm(String algorithm) {
        for (HashAlgorithm a:values()) {
            if (a.getAlgorithm().equalsIgnoreCase(algorithm)) {
                return a;
            }
        }
        return null;
    }
    
    private final String displayName;
    private final String algorithm;
    private final String extension;
    
    private HashAlgorithm(String displayName, String algorithm, String extension) {
        this.displayName = displayName;
        this.algorithm = algorithm;
        this.extension = extension;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getExtension() {
        return extension;
    }
    
    
}
