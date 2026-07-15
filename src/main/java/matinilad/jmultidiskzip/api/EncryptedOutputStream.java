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
import java.nio.ByteBuffer;
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

    public static final String MAGIC = "Encrypted Stream v1.0";
    
    private final char[] password;
    
    private boolean header = false;
    
    private SecretKey key = null;
    private Cipher cipher = null;
    private long counter = 0;
    
    private final byte[] buffer = new byte[16384];
    private int bufferIndex = 0;

    private boolean closed = false;

    public EncryptedOutputStream(OutputStream out, char[] password) {
        super(Objects.requireNonNull(out, "out is null"));
        this.password = password.clone();
    }
    
    private void writeHeader() throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            
            byte[] salt = new byte[32];
            new SecureRandom().nextBytes(salt);
            
            mac.init(new SecretKeySpec(salt, "HmacSHA256"));
            mac.update(ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array());
            
            salt = mac.doFinal();
            this.out.write(salt, 0, salt.length);
            
            PBEKeySpec spec = new PBEKeySpec(this.password, salt, 1_000_000, 256);
            try {
                SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                SecretKey secretKey = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "HmacSHA256");
                
                byte[] magicBytes = MAGIC.getBytes(StandardCharsets.UTF_8);
                
                mac.init(secretKey);
                mac.update(ByteBuffer.allocate(4).putInt(magicBytes.length).array());
                mac.update(magicBytes);
                byte[] signedMagic = mac.doFinal();
                this.out.write(signedMagic, 0, signedMagic.length);
                
                mac.update(signedMagic);
                this.key = new SecretKeySpec(mac.doFinal(), "AES");
            } finally {
                Arrays.fill(this.password, '\0');
                spec.clearPassword();
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException ex) {
            throw new IOException(ex);
        }
    }
    
    private void writeBuffer() throws IOException {
        try {
            if (this.cipher == null) {
                this.cipher = Cipher.getInstance("AES_256/GCM/NoPadding");
            }
            
            byte[] iv = new byte[] {
                0, 0, 0, 0,
                (byte) (this.counter >>> (7 * 8)),
                (byte) (this.counter >>> (6 * 8)),
                (byte) (this.counter >>> (5 * 8)),
                (byte) (this.counter >>> (4 * 8)),
                (byte) (this.counter >>> (3 * 8)),
                (byte) (this.counter >>> (2 * 8)),
                (byte) (this.counter >>> (1 * 8)),
                (byte) (this.counter >>> (0 * 8))
            };
            
            GCMParameterSpec gcm = new GCMParameterSpec(128, iv);
            this.cipher.init(Cipher.ENCRYPT_MODE, this.key, gcm);
            byte[] encrypted = this.cipher.doFinal(this.buffer, 0, this.bufferIndex);
            this.out.write(encrypted, 0, encrypted.length);
            
            this.counter++;
            this.bufferIndex = 0;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException ex) {
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
            writeBuffer();
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
                writeBuffer();
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
        if (this.bufferIndex > 0) {
            writeBuffer();
        }
        this.closed = true;
        this.out.close();
    }

}
