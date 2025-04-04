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

    @SuppressWarnings("deprecation")
    public static final Map<String, PasswordEncoder> ENCODERS = new HashMap<>(ImmutableMap
            .<String, PasswordEncoder>builderWithExpectedSize(19)
            .put("bcrypt", new BCryptPasswordEncoder())
            .put("ldap", new org.springframework.security.crypto.password.LdapShaPasswordEncoder())
            .put("pbkdf2", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8())
            .put("scrypt", SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8())
            .put("SHA-256", new org.springframework.security.crypto.password.MessageDigestPasswordEncoder("SHA-256"))
            .put("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8())

            // base decodable encoders
            .put("noop",
                    new PasswordEncoderDecoderWrapper(
                            org.springframework.security.crypto.password.NoOpPasswordEncoder.getInstance(), p -> p))
            .put("hex", new HexPasswordEncoder())
            .put("encrypted-AES-GCM", new AesGcmPasswordEncoder()) // placeholder (real instance created below)

            // base decodable encoders that rely on salt+token being passed in (not stored
            // in db with this type)
            .put("noop" + SALT_TOKEN_MECHANISM_SPECIALIZATION, new SaltedTokenPasswordEncoder(p -> p))
            .put("hex" + SALT_TOKEN_MECHANISM_SPECIALIZATION, new SaltedTokenPasswordEncoder(new HexPasswordEncoder()))
            .put("encrypted-AES-GCM" + SALT_TOKEN_MECHANISM_SPECIALIZATION,
                    new SaltedTokenPasswordEncoder(new AesGcmPasswordEncoder())) // placeholder (real instance created
                                                                                 // below)
            .build());

    public static final Set<String> NONLEGACY_ENCODERS = ENCODERS.keySet().stream()
            .filter(e -> !StringUtils.containsAny(e, SALT_TOKEN_MECHANISM_SPECIALIZATION))
            .collect(Collectors.toSet());
    public static final Set<String> DECODABLE_ENCODERS = Set.of("noop", "hex", "encrypted-AES-GCM");
    public static final Set<String> NONLEGACY_DECODABLE_ENCODERS = SetUtils.intersection(DECODABLE_ENCODERS,
            NONLEGACY_ENCODERS);
    public static final Set<String> NONLEGACY_NONDECODABLE_ENCODERS = SetUtils.difference(NONLEGACY_ENCODERS,
            DECODABLE_ENCODERS);

    public static final Set<String> OPENTEXT_ENCODERS = Set.of("noop", "hex");

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
                PasswordEncoder encoder = ENCODERS.get(StringUtils.substringBetween(prefixEncodedPassword, "{", "}"));
                if (encoder != null) {
                    return encoder.upgradeEncoding(StringUtils.substringAfter(prefixEncodedPassword, "}"));
                }

                return false;
            }
        };

        pEncoder.setDefaultPasswordEncoderForMatches(new PasswordEncoder() {
            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return false;
            }

            @Override
            public String encode(CharSequence rawPassword) {
                return null;
            }
        });

        return pEncoder;
    }

}