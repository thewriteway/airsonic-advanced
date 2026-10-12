package org.airsonic.player.controller;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Access rules of the status page and the endpoints it reads.
 */
@SpringBootTest(properties = {
    // the test application.properties replaces the main one, which exposes these endpoints
    "management.endpoints.web.exposure.include=metrics,health,caches,customscheduledtasks,pathwatcher",
    "management.endpoint.health.show-details=when-authorized",
    "management.endpoint.health.roles=ADMIN"})
@AutoConfigureMockMvc
public class StatusSecurityTest {

    @Autowired
    private MockMvc mvc;

    @TempDir
    private static Path tempDir;

    @BeforeAll
    public static void setup() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @Test
    @WithMockUser(username = "someuser")
    public void actuatorIsForbiddenForRegularUsers() throws Exception {
        mvc.perform(get("/actuator/caches")).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/customscheduledtasks")).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/pathwatcher")).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isForbidden());
        mvc.perform(delete("/actuator/caches").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    public void actuatorIsAvailableToAdministrators() throws Exception {
        mvc.perform(get("/actuator/customscheduledtasks")).andExpect(status().isOk());
        mvc.perform(get("/actuator/pathwatcher")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    public void clearingCachesRequiresCsrfToken() throws Exception {
        mvc.perform(delete("/actuator/caches")).andExpect(status().isForbidden());
        mvc.perform(delete("/actuator/caches").with(csrf())).andExpect(status().is2xxSuccessful());
    }

    @Test
    @WithMockUser(username = "someuser")
    public void regularUsersOnlySeeTheirOwnStatistics() throws Exception {
        mvc.perform(get("/statistics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(false))
                .andExpect(jsonPath("$.users[*].user", not(hasItem("admin"))))
                .andExpect(jsonPath("$.library").exists())
                .andExpect(jsonPath("$.uptimeMillis").value(nullValue()))
                .andExpect(jsonPath("$.sessions").value(nullValue()))
                .andExpect(jsonPath("$.caches").value(nullValue()))
                .andExpect(jsonPath("$.tasks").value(nullValue()))
                .andExpect(jsonPath("$.pathWatchers").value(nullValue()));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    public void administratorsSeeAllStatistics() throws Exception {
        mvc.perform(get("/statistics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(true))
                .andExpect(jsonPath("$.users[*].user", hasItem("admin")))
                .andExpect(jsonPath("$.uptimeMillis").isNumber())
                .andExpect(jsonPath("$.caches").isArray())
                .andExpect(jsonPath("$.tasks").isArray())
                .andExpect(jsonPath("$.pathWatchers").isArray());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    public void administratorsSeeTheServerSections() throws Exception {
        mvc.perform(get("/statistics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.system.version").exists())
                .andExpect(jsonPath("$.system.heapUsed").isNumber())
                .andExpect(jsonPath("$.storage[0].kind").value("home"))
                .andExpect(jsonPath("$.network.upnpPort").isNumber())
                .andExpect(jsonPath("$.playback.activePlayers").isNumber())
                .andExpect(jsonPath("$.log").exists());
    }

    @Test
    @WithMockUser(username = "someuser")
    public void regularUsersDoNotSeeTheServerSections() throws Exception {
        mvc.perform(get("/statistics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.system").value(nullValue()))
                .andExpect(jsonPath("$.storage").value(nullValue()))
                .andExpect(jsonPath("$.network").value(nullValue()))
                .andExpect(jsonPath("$.playback").value(nullValue()))
                .andExpect(jsonPath("$.log").value(nullValue()));
    }

    @Test
    @WithMockUser(username = "someuser")
    public void historyHasOneEntryPerDay() throws Exception {
        mvc.perform(get("/statistics/history").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(7))
                .andExpect(jsonPath("$.days[6].date").value(java.time.LocalDate.now().toString()))
                .andExpect(jsonPath("$.days[6].streamed").value(0));
    }

    @Test
    @WithMockUser(username = "someuser")
    public void statusPageHidesServerInternalsFromRegularUsers() throws Exception {
        mvc.perform(get("/status")).andExpect(status().isOk()).andExpect(model().attribute("admin", false));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    public void statusPageShowsServerInternalsToAdministrators() throws Exception {
        mvc.perform(get("/status")).andExpect(status().isOk()).andExpect(model().attribute("admin", true));
    }
}
