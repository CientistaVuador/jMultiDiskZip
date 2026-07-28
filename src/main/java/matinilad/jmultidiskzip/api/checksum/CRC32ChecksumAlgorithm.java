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

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 *
 * @author Cien
 */
public class CRC32ChecksumAlgorithm implements ChecksumAlgorithm {

    public static class CRC32Checksum implements Checksum {

        private final CRC32ChecksumAlgorithm algorithm;
        private final CRC32 crc32 = new CRC32();
        
        public CRC32Checksum(CRC32ChecksumAlgorithm algorithm) {
            this.algorithm = Objects.requireNonNull(algorithm, "algorithm is null");
        }
        
        @Override
        public CRC32ChecksumAlgorithm getAlgorithm() {
            return this.algorithm;
        }
        
        public CRC32 getCRC32() {
            return this.crc32;
        }

        @Override
        public void update(byte data) {
            this.crc32.update(data);
        }

        @Override
        public void update(byte[] data, int offset, int length) {
            this.crc32.update(data, offset, length);
        }

        @Override
        public byte[] digest() {
            int value = (int) this.crc32.getValue();
            this.crc32.reset();
            return ByteBuffer.allocate(4).putInt(value).array();
        }

        @Override
        public void reset() {
            this.crc32.reset();
        }

    }

    @Override
    public String getDisplayName() {
        return "CRC32 (Very Fast, Totally Insecure) - 4 Bytes";
    }

    @Override
    public String getName() {
        return "CRC32";
    }

    @Override
    public int getNumberOfExtensions() {
        return 1;
    }

    @Override
    public String getExtension(int index) {
        if (index == 0) {
            return "crc32";
        }
        throw new IndexOutOfBoundsException(index);
    }

    @Override
    public int getLength() {
        return 4;
    }

    @Override
    public Checksum newChecksum() {
        return new CRC32Checksum(this);
    }

}
