package org.airsonic.player.spring.migrations;

import liquibase.change.custom.CustomSqlChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.DatabaseException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;
import liquibase.statement.SqlStatement;
import org.airsonic.player.config.AirsonicHomeConfig;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ExtractCustomAvatars implements CustomSqlChange {
    private static final Logger LOG = LoggerFactory.getLogger(ExtractCustomAvatars.class);

    @Override
    public String getConfirmationMessage() {
        return "Extracted avatars from DB";
    }

    @Override
    public void setUp() throws SetupException {
        // No setup required for this custom change
    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {
        // Not needed for this implementation
    }

    @Override
    public ValidationErrors validate(Database database) {
        return new ValidationErrors();
    }

    @Override
    public SqlStatement[] generateStatements(Database database) throws CustomChangeException {
        JdbcConnection conn = null;
        if (database.getConnection() instanceof JdbcConnection) {
            conn = (JdbcConnection) database.getConnection();
        }
        AirsonicHomeConfig homeConfig = new AirsonicHomeConfig(
            System.getProperty("airsonic.home"),
            System.getProperty("libresonic.home")
        );

        if (conn != null) {
            try (PreparedStatement st = conn.prepareStatement("select * from custom_avatar");
                    ResultSet result = st.executeQuery()) {

                while (result.next()) {
                    try {
                        processAvatarRow(result, homeConfig);
                    } catch (Exception e) {
                        String user = "unknown";
                        try { user = result.getString("username"); } catch (Exception ignored) {}
                        LOG.warn("Exception while trying to extract avatar for user {}. Will skip this user.", user, e);
                    }
                }
            } catch (DatabaseException | SQLException e) {
                throw new CustomChangeException(e);
            }
        }

        return new SqlStatement[0];
    }

    private static String sanitizeName(String name) {
        if (name == null) {
            return "unknown";
        }
        String n = name.replaceAll("[^A-Za-z0-9@._-]", "_");
        if (n.isEmpty()) {
            n = "unknown";
        }
        if (n.length() > 64) {
            n = n.substring(0, 64);
        }
        return n;
    }

    private void processAvatarRow(ResultSet result, AirsonicHomeConfig homeConfig) throws Exception {
        String username = result.getString("username");
        String name = result.getString("name");
        String mime = result.getString("mime_type");

        String safeUsername = sanitizeName(username);
        String safeBaseName = sanitizeName(name);
        String suffix = StringUtils.substringAfter(mime, "/");

        Path folder = homeConfig.getAirsonicHome().resolve("avatars").resolve(safeUsername);
        Files.createDirectories(folder);
        Path filePath = folder.resolve(safeBaseName + "." + suffix);
        Files.copy(new ByteArrayInputStream(result.getBytes("data")), filePath);
    }

}
