package org.airsonic.player.security;

import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.service.SettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JWTAuthenticationProviderTest {

    private static final String KEY = "someKey";

    private final JWTSecurityService jwtSecurityService = new JWTSecurityService(new SettingsService() {
        @Override
        public String getJWTKey() {
            return KEY;
        }
    });
    private final JWTAuthenticationProvider provider = new JWTAuthenticationProvider(KEY);

    @Test
    public void sameParamsMatch() {
        String token = sign("ext/stream?id=1&player=2");

        Authentication result = authenticate(token, "ext/stream?id=1&player=2");

        assertEquals("user", result.getName());
    }

    @Test
    public void paramOrderIsIgnored() {
        String token = sign("ext/stream?id=1&player=2");

        Authentication result = authenticate(token, "ext/stream?player=2&id=1");

        assertEquals("user", result.getName());
    }

    @Test
    public void differentIdIsRejected() {
        String token = sign("ext/stream?id=1");

        assertThrows(InsufficientAuthenticationException.class, () -> authenticate(token, "ext/stream?id=2"));
    }

    @Test
    public void differentPathIsRejected() {
        String token = sign("ext/stream?id=1");

        assertThrows(InsufficientAuthenticationException.class, () -> authenticate(token, "ext/coverArt.view?id=1"));
    }

    @Test
    public void extraJwtParamIsAllowed() {
        String token = sign("ext/stream?id=1");

        Authentication result = authenticate(token, "ext/stream?id=1&jwt=" + token);

        assertEquals("user", result.getName());
    }

    @Test
    public void repeatedJwtParamIsRejected() {
        String token = sign("ext/stream?id=1");

        assertThrows(InsufficientAuthenticationException.class,
            () -> authenticate(token, "ext/stream?id=1&jwt=" + token + "&jwt=" + token));
    }

    @ParameterizedTest
    @ValueSource(strings = { "ext/stream?id=1&player=2", "ext/stream?id=1&id=2", "ext/stream?id=1&format=raw&jwt=x" })
    public void otherExtraParamIsRejected(String requested) {
        String token = sign("ext/stream?id=1");

        assertThrows(InsufficientAuthenticationException.class, () -> authenticate(token, requested));
    }

    @ParameterizedTest
    @ValueSource(strings = { "ext/stream?id=1", "ext/stream?player=2", "ext/stream" })
    public void missingParamIsRejected(String requested) {
        String token = sign("ext/stream?id=1&player=2");

        assertThrows(InsufficientAuthenticationException.class, () -> authenticate(token, requested));
    }

    @ParameterizedTest
    @ValueSource(strings = { "ext/coverArt.view?id=1&size=1200", "ext/coverArt.view?id=1", "ext/coverArt.view?id=1&size=60&size=90" })
    public void sizeDifferenceIsIgnored(String requested) {
        String token = sign("ext/coverArt.view?id=1&size=500");

        Authentication result = authenticate(token, requested);

        assertEquals("user", result.getName());
    }

    @Test
    public void preEncodedParamMatchesUrlAsEmitted() {
        // CaptionsController URL-encodes external caption paths itself and emits the URL without re-encoding
        String captionId = URLEncoder.encode("/music/My Movie+Subs.srt", StandardCharsets.UTF_8);
        String url = jwtSecurityService.addJWTToken("user", UriComponentsBuilder.fromUriString("ext/captions")
                .queryParam("id", 1)
                .queryParam("captionId", captionId))
                .build().toUriString();

        Authentication result = authenticate(tokenOf(url), url);

        assertEquals("user", result.getName());
    }

    @Test
    public void multiValuedParamMatchesUrlAsEmitted() {
        // ExternalPlayerController signs every bit rate on the HLS master playlist URL
        String url = jwtSecurityService.addJWTToken("user", UriComponentsBuilder.fromUriString("ext/hls/hls.m3u8")
                .queryParam("id", 1)
                .queryParam("maxBitRate", List.of(200, 300)))
                .build().toUriString();

        assertEquals("user", authenticate(tokenOf(url), url).getName());
        assertThrows(InsufficientAuthenticationException.class,
            () -> authenticate(tokenOf(url), url.replace("maxBitRate=300", "maxBitRate=5000")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/ws/Sonos", "ws/Sonos" })
    public void additionalCheckRunsForItsPath(String registeredPath) {
        // SonosService signs and SonosLinkSecurityInterceptor requests the context-relative path, query string included
        AtomicInteger checks = new AtomicInteger();
        provider.addAdditionalCheck(registeredPath, jwt -> checks.incrementAndGet());
        String token = sign("ws/Sonos?null");

        authenticate(token, "ws/Sonos?null");

        assertEquals(1, checks.get());
    }

    @Test
    public void failingAdditionalCheckRejects() {
        provider.addAdditionalCheck("/ws/Sonos", jwt -> {
            throw new InsufficientAuthenticationException("Sonos creds not valid");
        });
        String token = sign("ws/Sonos?null");

        assertThrows(InsufficientAuthenticationException.class, () -> authenticate(token, "ws/Sonos?null"));
    }

    @Test
    public void additionalCheckDoesNotRunForOtherPaths() {
        AtomicInteger checks = new AtomicInteger();
        provider.addAdditionalCheck("/ws/Sonos", jwt -> checks.incrementAndGet());
        String token = sign("ext/coverArt.view?id=1");

        authenticate(token, "ext/coverArt.view?id=1&jwt=" + token);

        assertEquals(0, checks.get());
    }

    private String sign(String path) {
        return tokenOf(jwtSecurityService.addJWTToken("user", path));
    }

    private static String tokenOf(String url) {
        return UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst(JWTSecurityService.JWT_PARAM_NAME);
    }

    private Authentication authenticate(String token, String requestedPath) {
        return provider.authenticate(new JWTAuthenticationToken(null, token, requestedPath));
    }
}
