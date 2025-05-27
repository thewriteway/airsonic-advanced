package org.airsonic.player.security;

import com.google.common.collect.ImmutableMap;
import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.service.SettingsService;
import org.apache.commons.codec.binary.Base16;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.crypto.password.*;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.airsonic.player.security.MultipleCredsMatchingAuthenticationProvider.SALT_TOKEN_MECHANISM_SPECIALIZATION;

@Configuration
public class PasswordEncoderConfig {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordEncoderConfig.class);

    @Autowired
    private SettingsService settingsService;

    public static final Map<String, PasswordEncoder> ENCODERS = new HashMap<>(ImmutableMap
            .<String, PasswordEncoder>builderWithExpectedSize(9)
            .put("bcrypt", new BCryptPasswordEncoder())
            .put("pbkdf2", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8())
            .put("scrypt", SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8())
            .put("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8())

            // base decodable encoders
            .put("hex", new HexPasswordEncoder())
            .put("encrypted-AES-GCM", new AesGcmPasswordEncoder()) // placeholder (real instance created below)

            // base decodable encoders that rely on salt+token being passed in (not stored
            // in db with this type)
            .put("hex" + SALT_TOKEN_MECHANISM_SPECIALIZATION, new SaltedTokenPasswordEncoder(new HexPasswordEncoder()))
            .put("encrypted-AES-GCM" + SALT_TOKEN_MECHANISM_SPECIALIZATION,
                    new SaltedTokenPasswordEncoder(new AesGcmPasswordEncoder())) // placeholder (real instance created below)
            .build());

    public static final Set<String> NONLEGACY_ENCODERS = ENCODERS.keySet().stream()
            .filter(e -> !StringUtils.containsAny(e, "legacy", SALT_TOKEN_MECHANISM_SPECIALIZATION))
            .collect(Collectors.toSet());
    public static final Set<String> DECODABLE_ENCODERS = Set.of("hex", "encrypted-AES-GCM");
    public static final Set<String> NONLEGACY_DECODABLE_ENCODERS = SetUtils.intersection(DECODABLE_ENCODERS,
            NONLEGACY_ENCODERS);
    public static final Set<String> NONLEGACY_NONDECODABLE_ENCODERS = SetUtils.difference(NONLEGACY_ENCODERS,
            DECODABLE_ENCODERS);

    public static final Set<String> OPENTEXT_ENCODERS = Set.of("hex");

    // Define legacy encoders that should be upgraded
    public static final Set<String> LEGACY_ENCODERS = Set.of(
            "ldap", "MD4", "MD5", "SHA-1", "SHA-256", "sha256",
            "noop", "legacynoop", "legacyhex"
    );

    @Bean
    public PasswordEncoder passwordEncoder() {
        boolean generatedKeys = false;

        String encryptionKeyPass = settingsService.getEncryptionPassword();
        if (StringUtils.isBlank(encryptionKeyPass)) {
            LOG.warn("Generating new encryption key password");
            encryptionKeyPass = JWTSecurityService.generateKey();
            settingsService.setEncryptionPassword(encryptionKeyPass);
            generatedKeys = true;
        }

        String encryptionKeySalt = settingsService.getEncryptionSalt();
        if (StringUtils.isBlank(encryptionKeySalt)) {
            LOG.warn("Generating new encryption key salt");
            Base16 base16 = new Base16();
            encryptionKeySalt = base16.encodeToString(KeyGenerators.secureRandom(16).generateKey());
            settingsService.setEncryptionSalt(encryptionKeySalt);
            generatedKeys = true;
        }

        if (generatedKeys) {
            settingsService.save();
        }

        AesGcmPasswordEncoder encoder = new AesGcmPasswordEncoder(encryptionKeyPass, encryptionKeySalt);
        ENCODERS.put("encrypted-AES-GCM", encoder);
        ENCODERS.put("encrypted-AES-GCM" + SALT_TOKEN_MECHANISM_SPECIALIZATION,
                new SaltedTokenPasswordEncoder(encoder));

        DelegatingPasswordEncoder pEncoder = new DelegatingPasswordEncoder(
                settingsService.getNonDecodablePasswordEncoder(), ENCODERS) {
            @Override
            public boolean upgradeEncoding(String prefixEncodedPassword) {
                if (StringUtils.isBlank(prefixEncodedPassword)) {
                    return false;
                }

                // Extract the encoding type from the prefixed password
                String encodingType = extractEncodingType(prefixEncodedPassword);

                // Only upgrade if it's a legacy encoder
                if (LEGACY_ENCODERS.contains(encodingType)) {
                    return true;
                }

                // Don't upgrade argon2 or bcrypt
                if ("argon2".equals(encodingType) || "bcrypt".equals(encodingType)) {
                    return false;
                }

                // For other encoders, delegate to the original encoder's upgradeEncoding method
                PasswordEncoder originalEncoder = ENCODERS.get(encodingType);
                if (originalEncoder != null) {
                    return originalEncoder.upgradeEncoding(StringUtils.substringAfter(prefixEncodedPassword, "}"));
                }

                return false;
            }

            private String extractEncodingType(String prefixEncodedPassword) {
                if (prefixEncodedPassword.startsWith("{") && prefixEncodedPassword.contains("}")) {
                    int endIndex = prefixEncodedPassword.indexOf("}");
                    return prefixEncodedPassword.substring(1, endIndex);
                }
                // If no prefix found, consider it legacy
                return "legacy";
            }
        };

        // Set default encoder for passwords without prefixes (existing database passwords)
        // This determines what encoder to use when matching passwords that have no {id} prefix
        String currentDefaultEncoder = settingsService.getNonDecodablePasswordEncoder();
        PasswordEncoder defaultForMatching = ENCODERS.get(currentDefaultEncoder);
        if (defaultForMatching == null) {
            // Fallback to bcrypt if the configured encoder is not available
            defaultForMatching = ENCODERS.get("bcrypt");
        }
        pEncoder.setDefaultPasswordEncoderForMatches(defaultForMatching);

        return pEncoder;
    }
}