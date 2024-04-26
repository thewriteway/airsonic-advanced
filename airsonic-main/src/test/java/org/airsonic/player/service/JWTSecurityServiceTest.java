package org.airsonic.player.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.util.UriComponentsBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
public class JWTSecurityServiceTest {

    private final String key = "someKey";
    private final JWTSecurityService service = new JWTSecurityService(settingsWithKey(key));
    private final Algorithm algorithm = JWTSecurityService.getAlgorithm(key);
    private final JWTVerifier verifier = JWT.require(algorithm).build();

    @ParameterizedTest
    @CsvSource({ "http://localhost:8080/airsonic/stream?id=4, /airsonic/stream?id=4",
        "/airsonic/stream?id=4, /airsonic/stream?id=4" })
    public void addJWTToken(String uriString, String expectedClaimString) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(uriString);
        String actualUri = service.addJWTToken("xyz", builder).build().toUriString();
        String jwtToken = UriComponentsBuilder.fromUriString(actualUri).build().getQueryParams().getFirst(
                JWTSecurityService.JWT_PARAM_NAME);
        DecodedJWT verify = verifier.verify(jwtToken);
        Claim claim = verify.getClaim(JWTSecurityService.CLAIM_PATH);
        assertEquals(expectedClaimString, claim.asString());
        assertEquals("xyz", verify.getSubject());
    }

    private SettingsService settingsWithKey(String jwtKey) {
        return new SettingsService() {
            @Override
            public String getJWTKey() {
                return jwtKey;
            }
        };
    }

}