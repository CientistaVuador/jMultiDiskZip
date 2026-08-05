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
package matinilad.jmultidiskzip.ui.gui;

import java.nio.file.Path;
import java.util.Arrays;
import matinilad.jmultidiskzip.api.utils.Utils;

/**
 *
 * @author Cien
 */
public class CreateOperationData {

    private final CreateOperationSettings settings;
    private final Path output;
    private final Path[] input;
    private final byte[] userSalt;
    private final char[] password;
    
    public CreateOperationData(CreateOperationSettings settings, Path output, Path[] input, byte[] userSalt, char[] password) {
        if (password != null && password.length == 0) {
            throw new IllegalArgumentException("empty password");
        }
        this.settings = new CreateOperationSettings(settings);
        this.output = Utils.validateOutputFileExtension(output, password != null, settings.getCompression(), settings.getOutputFormat());
        this.input = input.clone();
        this.userSalt = (userSalt == null ? null : userSalt.clone());
        this.password = (password == null ? null : password.clone());
    }
    
    public CreateOperationData(CreateOperationData data) {
        this.settings = new CreateOperationSettings(data.settings);
        this.output = data.output;
        this.input = data.input.clone();
        this.userSalt = (data.userSalt == null ? null : data.userSalt.clone());
        this.password = (data.password == null ? null : data.password.clone());
    }

    public CreateOperationSettings getSettings() {
        return settings;
    }

    public Path getOutput() {
        return output;
    }

    public Path[] getInput() {
        return input.clone();
    }

    public byte[] getUserSalt() {
        if (userSalt == null) {
            return null;
        }
        return userSalt.clone();
    }

    public boolean hasPassword() {
        return this.password != null;
    }
    
    public char[] getPassword() {
        if (password == null) {
            return null;
        }
        return password.clone();
    }
    
    public void clearPassword() {
        if (this.password != null) {
            Arrays.fill(this.password, '\0');
        }
    }
}
