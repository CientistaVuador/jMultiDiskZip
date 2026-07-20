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

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import static matinilad.jmultidiskzip.api.EncryptedOutputStream.MAGIC;

/**
 *
 * @author Cien
 */
public class EncryptedInputStream extends FilterInputStream {

    public static class IncorrectPasswordException extends IOException {

        private static final long serialVersionUID = 1L;

        public IncorrectPasswordException(String message) {
            super(message);
        }
    }

    private final char[] password;

    private boolean header = false;

    private SecretKey key = null;
    private Cipher cipher = null;
    private long nonce = 0;

    private final byte[] buffer = new byte[EncryptedOutputStream.BUFFER_SIZE];
    private int bufferLength = 0;
    private int bufferIndex = 0;

    private boolean eof = false;
    private boolean closed = false;

    public EncryptedInputStream(InputStream in, char[] password) {
        super(Objects.requireNonNull(in, "in is null"));
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

    private void readHeader() throws IOException {
        try {
            byte[] salt = this.in.readNBytes(32);
            if (salt.length != 32) {
                throw new EOFException("unexpected EOF, expected salt");
            }

            Mac mac = Mac.getInstance("HmacSHA256");

            SecretKey signKey;
            SecretKey encryptionKey;

            PBEKeySpec spec = new PBEKeySpec(this.password, salt, 1_000_000, 256);
            try {
                SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                SecretKey secretKey = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "HmacSHA256");

                byte[] signKeyInfo = "sign".getBytes(StandardCharsets.UTF_8);
                byte[] encryptKeyInfo = "encrypt".getBytes(StandardCharsets.UTF_8);
                
                mac.init(secretKey);

                mac.update(ByteBuffer.allocate(4).putInt(signKeyInfo.length).array());
                mac.update(signKeyInfo);
                mac.update((byte) 0x01);
                signKey = new SecretKeySpec(mac.doFinal(), "HmacSHA256");

                mac.update(signKey.getEncoded());
                mac.update(ByteBuffer.allocate(4).putInt(encryptKeyInfo.length).array());
                mac.update(encryptKeyInfo);
                mac.update((byte) 0x02);
                encryptionKey = new SecretKeySpec(mac.doFinal(), "AES");
            } finally {
                spec.clearPassword();
                Arrays.fill(this.password, '\0');
            }
            
            mac.init(signKey);
            mac.update(MAGIC.getBytes(StandardCharsets.UTF_8));
            
            byte[] signedMagic = mac.doFinal();
            byte[] headerMagic = this.in.readNBytes(signedMagic.length);
            if (headerMagic.length != signedMagic.length) {
                throw new EOFException("unexpected EOF, expected signed magic");
            }
            if (!MessageDigest.isEqual(signedMagic, headerMagic)) {
                throw new IncorrectPasswordException("incorrect password or invalid magic");
            }

            this.key = encryptionKey;
            
            this.cipher = Cipher.getInstance("AES/GCM/NoPadding");
            this.cipher.init(Cipher.DECRYPT_MODE, this.key, nextIV());
            
            this.cipher.updateAAD(salt);
            this.cipher.updateAAD(signedMagic);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException | NoSuchPaddingException | InvalidAlgorithmParameterException ex) {
            throw new IOException(ex);
        }
    }

    private void readBuffer() throws IOException {
        try {
            byte[] encrypted = this.in.readNBytes(this.buffer.length + 16);
            if (encrypted.length == 0) {
                throw new EOFException("expected last buffer, found nothing");
            }
            if (encrypted.length < 17) {
                throw new EOFException("buffer must be at least 17 bytes!");
            }
            this.bufferLength = this.cipher.doFinal(encrypted, 0, encrypted.length, this.buffer, 0);
            this.bufferIndex = 1;
            
            this.eof = (this.buffer[0] == 0x01);
            
            this.cipher.init(Cipher.DECRYPT_MODE, this.key, nextIV());
            this.cipher.updateAAD(encrypted, encrypted.length - 16, 16);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException | ShortBufferException ex) {
            throw new IOException(ex);
        }
    }

    private boolean readChecks() throws IOException {
        if (this.closed) {
            throw new IOException("stream is closed");
        }

        if (!this.header) {
            readHeader();
            this.header = true;
        }

        if (this.bufferIndex >= this.bufferLength) {
            if (this.eof) {
                return false;
            }
            readBuffer();
        }

        return true;
    }

    @Override
    public int read() throws IOException {
        if (!readChecks()) {
            return -1;
        }

        int b = this.buffer[this.bufferIndex] & 0xFF;
        this.bufferIndex++;

        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);
        if (!readChecks()) {
            return -1;
        }

        int toCopy = Math.min(this.bufferLength - this.bufferIndex, len);
        System.arraycopy(this.buffer, this.bufferIndex, b, off, toCopy);
        this.bufferIndex += toCopy;

        return toCopy;
    }

    @Override
    public void close() throws IOException {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.in.close();
    }

}
