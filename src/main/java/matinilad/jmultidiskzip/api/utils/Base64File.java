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
package matinilad.jmultidiskzip.api.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

/**
 *
 * @author Cien
 */
public class Base64File {
    
    public static final String EXTENSION = "base64";
    public static final String HEADER = "data:application/octet-stream;base64,";
    public static final String HEADER_HEX = HexFormat.of().formatHex(HEADER.getBytes(StandardCharsets.US_ASCII));
    
    public static boolean isBase64File(String sampleHex) {
        return sampleHex.toLowerCase().startsWith(HEADER_HEX);
    }
    
    public static InputStream decode(InputStream in) throws IOException {
        byte[] header = in.readNBytes(HEADER_HEX.length() / 2);
        if (header.length != (HEADER_HEX.length() / 2) || !HexFormat.of().formatHex(header).equals(HEADER_HEX)) {
            throw new IOException("Base64 header not found");
        }
        return Base64.getDecoder().wrap(in);
    }
    
    public static OutputStream encode(OutputStream out) throws IOException {
        out.write(Base64File.HEADER.getBytes(StandardCharsets.US_ASCII));
        return Base64.getEncoder().wrap(out);
    }
    
    private Base64File() {
        
    }
}
