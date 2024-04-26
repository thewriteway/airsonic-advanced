package org.airsonic.player.service;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.util.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("EmbeddedTestCategory")
@SpringBootTest
public class LegacyDatabaseStartupTestCase {

    @TempDir
    private static Path tempAirsonicHome;

    @BeforeAll
    public static void setupOnce() throws IOException, URISyntaxException {
        System.setProperty("airsonic.home", tempAirsonicHome.toString());
        Path dbDirectory = tempAirsonicHome.resolve("db");
        FileUtils.copyRecursively(LegacyDatabaseStartupTestCase.class.getResource("/db/pre-liquibase/db"), dbDirectory);
        AirsonicHomeConfig config = new AirsonicHomeConfig(tempAirsonicHome.toString(), null);
        System.setProperty(SettingsService.KEY_DATABASE_URL,
                config.getDefaultJDBCUrl().replaceAll("airsonic;", "libresonic;"));
        System.setProperty(SettingsService.KEY_DATABASE_USERNAME, "sa");
        System.setProperty(SettingsService.KEY_DATABASE_PASSWORD, "");
    }

    @Autowired
    DataSource dataSource;

    @Test
    public void testStartup() {
        assertThat(dataSource).isNotNull();
    }
}
