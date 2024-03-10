package org.airsonic.player.security;

public interface PasswordDecoder {
    String decode(String encoded) throws Exception;
}
