package org.airsonic.player.security;

import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.security.crypto.encrypt.AesGcmBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import java.security.GeneralSecurityException;

public class AesGcmPasswordEncoder implements PasswordEncoder, PasswordDecoder {
    private static final Logger LOG = LoggerFactory.getLogger(AesGcmPasswordEncoder.class);
    // Key derivation of the deprecated Encryptors.delux()/stronger(). AesGcmBytesEncryptor.withPassword() uses
    // PBKDF2WithHmacSHA256 with 600,000 iterations instead, which could not decrypt credentials already stored.
    private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final int KEY_DERIVATION_ITERATIONS = 1024;
    private static final int KEY_LENGTH_BITS = 256;

    private final BytesEncryptor encryptor;

    public AesGcmPasswordEncoder(String password, String salt) {
        this.encryptor = AesGcmBytesEncryptor.withSecretKey(deriveKey(password, salt)).build();
    }

    private static SecretKeySpec deriveKey(String password, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), Hex.decode(salt), KEY_DERIVATION_ITERATIONS, KEY_LENGTH_BITS);
            byte[] key = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM).generateSecret(spec).getEncoded();
            return new SecretKeySpec(key, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to derive encryption key", e);
        }
    }

    @Override
    public String decode(String encoded) throws Exception {
        return Utf8.decode(encryptor.decrypt(Hex.decode(encoded)));
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return new String(Hex.encode(encryptor.encrypt(Utf8.encode(rawPassword))));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        try {
            return Strings.CS.equals(rawPassword, decode(encodedPassword));
        } catch (Exception e) {
            LOG.warn("Exception while trying to match passwords", e);
            return false;
        }
    }

    // The following used as placeholder before the real instance with actual
    // password is instantiated
    private static final String CONSTANT_PASSWORD = "airsonicencryptorpassword";
    private static final String CONSTANT_SALT = "44C82DD88E1FA9672628C3637B7F6976";

    public AesGcmPasswordEncoder() {
        this(CONSTANT_PASSWORD, CONSTANT_SALT);
    }
}
