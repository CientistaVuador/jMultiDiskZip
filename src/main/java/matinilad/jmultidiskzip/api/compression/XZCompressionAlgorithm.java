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
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZ;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

/**
 *
 * @author Cien
 */
public class XZCompressionAlgorithm extends DefaultCompressionAlgorithm {

    public XZCompressionAlgorithm() {
        super("xz", new String[] {"xz"}, LZMA2Options.PRESET_MIN, LZMA2Options.PRESET_MAX + 1, LZMA2Options.PRESET_DEFAULT, new String[] {"FD377A585A00"});
    }
    
    public XZOutputStream compress(OutputStream out, LZMA2Options options) throws IOException {
        Objects.requireNonNull(out, "out is null");
        Objects.requireNonNull(options, "options is null");
        
        return new XZOutputStream(out, options, XZ.CHECK_CRC32);
    }
    
    @Override
    public XZOutputStream compress(OutputStream out, int level) throws IOException {
        checkLevel(level);
        return compress(out, new LZMA2Options(level));
    }

    @Override
    public XZOutputStream compress(OutputStream out) throws IOException {
        return compress(out, getDefaultCompressionLevel());
    }

    @Override
    public XZInputStream decompress(InputStream in) throws IOException {
        return new XZInputStream(in);
    }
    
}
