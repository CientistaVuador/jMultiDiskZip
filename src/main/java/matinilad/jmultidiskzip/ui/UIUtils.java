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
package matinilad.jmultidiskzip.ui;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 *
 * @author Cien
 */
public class UIUtils {
    
    public static final long BYTE = 1;
    public static final long KIBIBYTE = BYTE * 1024;
    public static final long MEBIBYTE = KIBIBYTE * 1024;
    public static final long GIBIBYTE = MEBIBYTE * 1024;
    public static final long TEBIBYTE = GIBIBYTE * 1024;
    
    public static final String BYTE_SUFFIX = "B";
    public static final String KIBIBYTE_SUFFIX = "KiB";
    public static final String MEBIBYTE_SUFFIX = "MiB";
    public static final String GIBIBYTE_SUFFIX = "GiB";
    public static final String TEBIBYTE_SUFFIX = "TiB";
    
    private static final long[] sizes = {BYTE, KIBIBYTE, MEBIBYTE, GIBIBYTE, TEBIBYTE};
    private static final String[] suffixes = {BYTE_SUFFIX, KIBIBYTE_SUFFIX, MEBIBYTE_SUFFIX, GIBIBYTE_SUFFIX, TEBIBYTE_SUFFIX};
    
    public static String formatBytes(long byteCount, boolean shortened) {
        int unit = 0;
        for (int i = (sizes.length - 1); i >= 0; i--) {
            if (Math.abs(byteCount) >= sizes[i]) {
                unit = i;
                break;
            }
        }
        if (unit == 0) {
            return byteCount + " " + suffixes[0];
        }
        double div = byteCount / ((double)sizes[unit]);
        return String.format("%.2f", div) + " " + suffixes[unit] + (shortened ? "" : " (" + byteCount + " " + suffixes[0] + ")");
    }
    
    public static String formatBytesShort(long byteCount) {
        return formatBytes(byteCount, true);
    }
    
    public static String formatBytes(long byteCount) {
        return formatBytes(byteCount, false);
    }
    
    public static String formatCountdownSeconds(long seconds) {
        long days = seconds / 86400;
        seconds -= days * 86400;
        
        long hours = seconds / 3600;
        seconds -= hours * 3600;
        
        long minutes = seconds / 60;
        seconds -= minutes * 60;
        
        StringBuilder b = new StringBuilder();
        if (days != 0) {
            b.append(days).append("d ");
        }
        if (hours != 0) {
            b.append(hours).append("h ");
        }
        if (minutes != 0) {
            b.append(minutes).append("m ");
        }
        b.append(seconds).append("s");
        
        return b.toString();
    }
    
    public static String stacktraceOf(Throwable t) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintStream print = new PrintStream(out, false, StandardCharsets.UTF_8)) {
            t.printStackTrace(print);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
