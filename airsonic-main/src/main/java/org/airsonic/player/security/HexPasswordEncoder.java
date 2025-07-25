package org.airsonic.player.security;

import org.apache.commons.codec.binary.Base16;
import org.apache.commons.lang3.Strings;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;

public final class HexPasswordEncoder implements PasswordEncoder, PasswordDecoder {
    private final static Base16 hex = new Base16(true);

    @Override
    public String encode(CharSequence rawPassword) {
        return hex.encodeToString(rawPassword.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String decode(String encoded) {
        return new String(hex.decode(encoded), StandardCharsets.UTF_8);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return Strings.CS.equals(encode(rawPassword), encodedPassword);
    }

    public HexPasswordEncoder() {
    }
}
