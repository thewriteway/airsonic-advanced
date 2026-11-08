package org.airsonic.player.security;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.service.SecurityService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@EnableConfigurationProperties({AirsonicHomeConfig.class})
public class RESTAuthTest {

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin";
    private static final String API_VERSION = "1.15.0";

    @TempDir
    private static Path tempDir;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SecurityService securityService;

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    /**
     * The admin user is created at startup with a random (or env-provided) password,
     * so give it the well-known test password these tests authenticate with.
     */
    @BeforeEach
    public void ensureTestAdminCredential() {
        if (!securityService.checkDefaultAdminCredsPresent()) {
            securityService.recoverCredential(USERNAME, PASSWORD, "Test admin credential");
        }
    }

    @Test
    public void testRequestParamAuthSuccess() throws Exception {
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("f", "json")
                .param("c", "test")
                .param("u", USERNAME)
                .param("p", PASSWORD)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    public void testFailedCallIsLoggedWithAuthenticatedUser(CapturedOutput output) throws Exception {
        // authenticated, but the required id is missing: a Subsonic error the logging filter reports
        mvc.perform(get("/rest/getAlbum")
                .param("v", "1.15.0")
                .param("f", "json")
                .param("c", "log-test")
                .param("u", USERNAME)
                .param("p", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(10));

        assertThat(output.getOut()).contains("REST GET /rest/getAlbum").contains("client=log-test")
                .contains("user=" + USERNAME + " ").contains("Subsonic error 10")
                .contains("params={p=***}");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    public void testFailedLoginIsNotLogged(CapturedOutput output) throws Exception {
        mvc.perform(get("/rest/ping")
                .param("v", "1.15.0")
                .param("f", "json")
                .param("c", "bad-login-test")
                .param("u", USERNAME)
                .param("p", "wrong-password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(40));

        assertThat(output.getOut()).doesNotContain("client=bad-login-test");
    }

    @Test
    public void testNewerMinorClientVersionIsAccepted() throws Exception {
        mvc.perform(get("/rest/ping")
                .param("v", "1.16.1")
                .param("f", "json")
                .param("c", "test")
                .param("u", USERNAME)
                .param("p", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
    }

    @Test
    public void testNewerMajorClientVersionIsRejected() throws Exception {
        mvc.perform(get("/rest/ping")
                .param("v", "2.0.0")
                .param("f", "json")
                .param("c", "test")
                .param("u", USERNAME)
                .param("p", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(30));
    }

    @Test
    public void testOlderMajorClientVersionIsRejected() throws Exception {
        mvc.perform(get("/rest/ping")
                .param("v", "0.9.0")
                .param("f", "json")
                .param("c", "test")
                .param("u", USERNAME)
                .param("p", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(20));
    }

    @Test
    public void testRequestParamAuthFailure() throws Exception {
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .param("u", USERNAME)
                .param("p", "incorrectpassword"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(40));
    }

    @Test
    public void testBasicAuthSuccess() throws Exception {
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .with(httpBasic(USERNAME, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
    }

    @Test
    public void testBasicAuthFailure() throws Exception {
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .with(httpBasic(USERNAME, "incorrectpassword"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
