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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 *
 * @author Cien
 */
public class ZipExtractor {

    private final ZipInputStream input;
    private final Path output;
    
    private ZipInputStream checksumsZip = null;
    
    public ZipExtractor(ZipInputStream input, Path output) {
        this.input = Objects.requireNonNull(input, "input is null");
        this.output = Objects.requireNonNull(output, "output is null");
    }
    
    protected void onEntryFailed(ZipEntry entry, Path file, IOException reason) {
        
    }
    
    protected void onIntegrityFailed(Path file, IOException reason) {
        
    }
    
    private void verify() {
        
    }
    
    public void extract(boolean verifyIntegrity) throws IOException {
        this.checksumsZip = null;
        Path outputDirectory;
        
        
        if (verifyIntegrity && this.checksumsZip != null) {
            verify();
        }
    }
    
}
