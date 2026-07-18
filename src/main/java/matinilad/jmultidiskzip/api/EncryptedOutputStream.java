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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 *
 * @author Cien
 */
public class EncryptedOutputStream extends FilterOutputStream {

    public static final String MAGIC = "jMultiDiskZip Encrypted Stream v1";
    public static final int BUFFER_SIZE = 1 + 65536;

    private final char[] password;

    private boolean header = false;

    private SecretKey key = null;
    private Cipher cipher = null;
    private long nonce = 0;
    
    private final byte[] buffer = new byte[BUFFER_SIZE];
    private int bufferIndex = 1;

    private boolean closed = false;

    public EncryptedOutputStream(OutputStream out, char[] password) {
        super(Objects.requireNonNull(out, "out is null"));
        this.password = password.clone();
    }

    private GCMParameterSpec nextIV() {
        byte[] iv = new byte[12];
        for (int i = 0; i < 8; i++) {
            iv[i] = (byte) (this.nonce >>> ((7 - i) * 8));
        }
        this.nonce++;
        
        return new GCMParameterSpec(128, iv);
    }

    private void writeHeader() throws IOException {
        try {
            byte[] salt = new byte[32];
            new SecureRandom().nextBytes(salt);
            this.out.write(salt);

            Mac mac = Mac.getInstance("HmacSHA256");

            SecretKey signKey;
            SecretKey encryptionKey;

            PBEKeySpec spec = new PBEKeySpec(this.password, salt, 1_000_000, 256);
            try {
                SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                SecretKey secretKey = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "HmacSHA256");

                mac.init(secretKey);

                mac.update((byte) 0x01);
                signKey = new SecretKeySpec(mac.doFinal(), "HmacSHA256");
                
                mac.update(signKey.getEncoded());
                mac.update((byte) 0x02);
                encryptionKey = new SecretKeySpec(mac.doFinal(), "AES");
            } finally {
                Arrays.fill(this.password, '\0');
                spec.clearPassword();
            }
            
            mac.init(signKey);
            mac.update(MAGIC.getBytes(StandardCharsets.UTF_8));
            
            byte[] signedMagic = mac.doFinal();
            this.out.write(signedMagic);
            
            this.key = encryptionKey;
            
            this.cipher = Cipher.getInstance("AES/GCM/NoPadding");
            this.cipher.init(Cipher.ENCRYPT_MODE, this.key, nextIV());
            
            this.cipher.updateAAD(salt);
            this.cipher.updateAAD(signedMagic);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException | NoSuchPaddingException | InvalidAlgorithmParameterException ex) {
            throw new IOException(ex);
        }
    }

    private void writeBuffer(boolean lastBuffer) throws IOException {
        try {
            this.buffer[0] = (lastBuffer ? (byte) 0x01 : (byte) 0x00);
            
            byte[] encrypted = this.cipher.doFinal(this.buffer, 0, this.bufferIndex);
            this.out.write(encrypted);
            
            this.bufferIndex = 1;
            
            this.cipher.init(Cipher.ENCRYPT_MODE, this.key, nextIV());
            this.cipher.updateAAD(encrypted, encrypted.length - 16, 16);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException ex) {
            throw new IOException(ex);
        }
    }

    private void writeChecks() throws IOException {
        if (this.closed) {
            throw new IOException("stream is closed");
        }

        if (!this.header) {
            writeHeader();
            this.header = true;
        }
    }

    @Override
    public void write(int b) throws IOException {
        writeChecks();

        if (this.bufferIndex >= this.buffer.length) {
            writeBuffer(false);
        }
        this.buffer[this.bufferIndex] = (byte) b;
        this.bufferIndex++;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);
        writeChecks();

        int from = off;
        int to = off + len;

        while (from < to) {
            if (this.bufferIndex >= this.buffer.length) {
                writeBuffer(false);
            }
            int toCopy = Math.min(to - from, this.buffer.length - this.bufferIndex);
            System.arraycopy(b, from, this.buffer, this.bufferIndex, toCopy);
            this.bufferIndex += toCopy;
            from += toCopy;
        }
    }

    @Override
    public void close() throws IOException {
        if (this.closed) {
            return;
        }

        if (!this.header) {
            writeHeader();
            this.header = true;
        }
        writeBuffer(true);

        this.closed = true;
        this.out.close();
    }

}
