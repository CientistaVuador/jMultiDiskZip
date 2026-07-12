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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 *
 * @author Cien
 */
public class PartOutputStream extends OutputStream {

    private final Path path;
    private final String fileName;
    private final long partSize;
    private final MessageDigest digest;
    private final String digestName;
    
    private OutputStream output = null;
    private long written = 0;
    private int partNumber = 0;
    private String partString = "";
    
    public PartOutputStream(Path path, String fileName, long partSize, String digestAlgorithm) throws NoSuchAlgorithmException {
        this.path = path;
        this.fileName = fileName;
        this.partSize = partSize;
        
        if (digestAlgorithm != null) {
            this.digest = MessageDigest.getInstance(digestAlgorithm);
            
            String tmpDigestName = this.digest.getAlgorithm().toLowerCase().replace('/', '_');
            if (tmpDigestName.startsWith("sha-")) {
                tmpDigestName = tmpDigestName.replace("-", "");
            } else if (tmpDigestName.startsWith("sha3-")) {
                tmpDigestName = tmpDigestName.replace("-", "_");
            }
            this.digestName = tmpDigestName;
        } else {
            this.digest = null;
            this.digestName = null;
        }
    }
    
    @Override
    public void write(int b) throws IOException {
        if (this.output == null) {
            this.partNumber++;
            this.partString = Integer.toString(this.partNumber);
            this.partString = "0".repeat(Math.max(3 - this.partString.length(), 0)) + this.partString;
            this.output = new BufferedOutputStream(Files.newOutputStream(this.path.resolve(this.fileName+"."+this.partString)));
            this.digest.reset();
        }
        
        this.output.write(b);
        this.written++;
        
        if (this.written >= this.partSize) {
            this.output.close();
            this.output = null;
            this.written = 0;
            
            byte[] md5HexBytes = HexFormat.of().formatHex(this.digest.digest()).getBytes(StandardCharsets.UTF_8);
            Files.write(this.path.resolve(this.fileName+"."+this.partString+".md5"), md5HexBytes);
        }
    }

    @Override
    public void close() throws IOException {
        if (this.output == null) {
            return;
        }
        this.output.close();
    }
    
}
