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
    private long counter = 0;
    
    private final byte[] buffer = new byte[16384];
    private int bufferLength = 0;
    private int bufferIndex = 0;
    
    private boolean eof = false;
    private boolean closed = false;
    
    public EncryptedInputStream(InputStream in, char[] password) {
        super(Objects.requireNonNull(in, "in is null"));
        this.password = password.clone();
    }
    
    private void readHeader() throws IOException {
        byte[] salt = this.in.readNBytes(32);
        if (salt.length != 32) {
            throw new EOFException("unexpected EOF, expected salt");
        }
        
        try {
            PBEKeySpec spec = new PBEKeySpec(this.password, salt, 1_000_000, 256);
            try {
                SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                SecretKey secretKey = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "HmacSHA256");
                
                byte[] magicBytes = EncryptedOutputStream.MAGIC.getBytes(StandardCharsets.UTF_8);
                
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(secretKey);
                mac.update(ByteBuffer.allocate(4).putInt(magicBytes.length).array());
                mac.update(magicBytes);
                byte[] signedMagic = mac.doFinal();
                
                byte[] streamMagic = this.in.readNBytes(32);
                if (streamMagic.length != 32) {
                    throw new EOFException("unexpected EOF, expected signed magic");
                }
                
                if (!MessageDigest.isEqual(signedMagic, streamMagic)) {
                    throw new IncorrectPasswordException("incorrect password or invalid magic");
                }
                
                mac.update(signedMagic);
                this.key = new SecretKeySpec(mac.doFinal(), "AES");
            } finally {
                spec.clearPassword();
                Arrays.fill(this.password, '\0');
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException ex) {
            throw new IOException(ex);
        }
    }
    
    private void readBuffer() throws IOException {
        try {
            if (this.cipher == null) {
                this.cipher = Cipher.getInstance("AES_256/GCM/NoPadding");
            }
            
            if (this.eof) {
                return;
            }
            
            int firstByte = this.in.read();
            if (firstByte == -1) {
                this.eof = true;
                return;
            }
            
            byte[] inputBuffer = new byte[this.buffer.length + 16];
            inputBuffer[0] = (byte) firstByte;
            int inputBufferLength = 1;
            
            do {
                int r = this.in.read(inputBuffer, inputBufferLength, inputBuffer.length - inputBufferLength);
                if (r == -1) {
                    this.eof = true;
                    break;
                }
                inputBufferLength += r;
            } while (inputBufferLength < inputBuffer.length);
            
            if (inputBufferLength < 16) {
                throw new IOException("input buffer must be at least 16 bytes!");
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
            this.cipher.init(Cipher.DECRYPT_MODE, this.key, gcm);
            this.bufferLength = this.cipher.doFinal(inputBuffer, 0, inputBufferLength, this.buffer);
            
            this.counter++;
            this.bufferIndex = 0;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException | ShortBufferException ex) {
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
            if (this.bufferIndex >= this.bufferLength) {
                return false;
            }
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
