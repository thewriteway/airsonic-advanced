package org.airsonic.player.repository;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.AvatarScheme;
import org.airsonic.player.domain.TranscodeScheme;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.User.Role;
import org.airsonic.player.domain.UserCredential;
import org.airsonic.player.domain.UserCredential.App;
import org.airsonic.player.domain.entity.UserSetting;
import org.airsonic.player.domain.entity.UserSettingDetail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Unit test of {@link UserDao}.
 *
 * @author Sindre Mehus
 */
@SpringBootTest
@EnableConfigurationProperties({ AirsonicHomeConfig.class })
@Transactional
public class UserRepositoryTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserCredentialRepository userCredentialRepository;

    @Autowired
    UserSettingRepository userSettingRepository;

    @TempDir
    private static Path tempDir;

    private final String TEST_USER_NAME = "sindre";

    @BeforeAll
    public static void setUp() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @BeforeEach
    public void clear() {
        userRepository.findByUsername(TEST_USER_NAME).ifPresent(user -> userRepository.delete(user));
        userRepository.flush();
    }

    @Test
    public void testCreateUser() {
        User user = new User(TEST_USER_NAME, "sindre@activeobjects.no", false, 1000L, 2000L, 3000L, Set.of(Role.ADMIN, Role.COMMENT, Role.COVERART, Role.PLAYLIST, Role.PODCAST, Role.STREAM, Role.JUKEBOX, Role.SETTINGS));
        UserCredential uc = new UserCredential(user, TEST_USER_NAME, "secret", "noop", App.AIRSONIC);
        userRepository.save(user);
        userCredentialRepository.save(uc);

        User newUser = userRepository.findByUsername(TEST_USER_NAME).get();
        assertThat(newUser).usingRecursiveComparison().isEqualTo(user);
        assertThat(userCredentialRepository.findByUserUsernameAndApp(TEST_USER_NAME, App.AIRSONIC).get(0)).usingRecursiveComparison().isEqualTo(uc);
    }

    @Test
    public void testUpdateUser() {
        User user = new User(TEST_USER_NAME, null);
        user.setRoles(Set.of(Role.ADMIN, Role.COMMENT, Role.COVERART, Role.PLAYLIST, Role.PODCAST, Role.STREAM, Role.JUKEBOX, Role.SETTINGS));
        UserCredential uc = new UserCredential(user, TEST_USER_NAME, "secret", "noop", App.AIRSONIC);
        userRepository.save(user);
        userCredentialRepository.save(uc);

        user.setEmail("sindre@foo.bar");
        user.setLdapAuthenticated(true);
        user.setBytesStreamed(1);
        user.setBytesDownloaded(2);
        user.setBytesUploaded(3);
        user.setRoles(Set.of(Role.DOWNLOAD, Role.UPLOAD));

        userRepository.save(user);

        assertThat(userRepository.findByUsername(TEST_USER_NAME).get()).usingRecursiveComparison().isEqualTo(user);
        assertThat(userCredentialRepository.findByUserUsernameAndApp(TEST_USER_NAME, App.AIRSONIC).get(0)).usingRecursiveComparison().isEqualTo(uc);
    }

    @Test
    public void testUpdateCredential() {
        User user = new User(TEST_USER_NAME, null);
        user.setRoles(Set.of(Role.ADMIN, Role.COMMENT, Role.COVERART));
        UserCredential uc = new UserCredential(user, TEST_USER_NAME, "secret", "noop", App.AIRSONIC);
        userRepository.save(user);
        userCredentialRepository.save(uc);

        uc.setCredential("foo");

        userCredentialRepository.save(uc);

        assertThat(userRepository.findByUsername(TEST_USER_NAME).get()).usingRecursiveComparison().isEqualTo(user);
        assertThat(userCredentialRepository.findByUserUsernameAndApp(TEST_USER_NAME, App.AIRSONIC).get(0)).usingRecursiveComparison().isEqualTo(uc);
    }

    @Test
    public void testGetUserByName() {
        User user = new User(TEST_USER_NAME, null);
        userRepository.save(user);

        assertThat(userRepository.findByUsername(TEST_USER_NAME).get()).usingRecursiveComparison().isEqualTo(user);

        assertTrue(userRepository.findByUsername("sindre2").isEmpty());
        // assertNull("Error in getUserByName().", userRepository.findByUsername("Sindre ", true)); // depends on the collation of the DB
        assertTrue(userRepository.findByUsername("bente").isEmpty());
        assertTrue(userRepository.findByUsername("").isEmpty());
        assertTrue(userRepository.findByUsername(null).isEmpty());
    }

    @Test
    public void testDeleteUser() {
        long count = userRepository.count();

        userRepository.save(new User(TEST_USER_NAME, null));
        assertEquals(count + 1L, userRepository.count());

        userRepository.save(new User("bente", null));
        assertEquals(count + 2L, userRepository.count());

        userRepository.deleteById(TEST_USER_NAME);
        assertEquals(count + 1L, userRepository.count());
        assertFalse(userRepository.findByUsername(TEST_USER_NAME).isPresent());

        userRepository.deleteById("bente");
        assertEquals(count, userRepository.count());
        assertFalse(userRepository.findByUsername("bente").isPresent());
    }

    @Test
    public void testGetRolesForUser() {
        User user = new User(TEST_USER_NAME, null);
        user.setRoles(Set.of(Role.ADMIN, Role.COMMENT, Role.PODCAST, Role.STREAM, Role.SETTINGS));
        userRepository.save(user);

        Set<Role> roles = userRepository.findByUsername(TEST_USER_NAME).get().getRoles();
        assertThat(roles).containsOnly(Role.ADMIN, Role.COMMENT, Role.PODCAST, Role.STREAM, Role.SETTINGS);
    }

    @Test
    public void testUserSettings() {
        assertFalse(userSettingRepository.findById(TEST_USER_NAME).isPresent());

        //assertThrows(DataIntegrityViolationException.class, () -> userSettingRepository.save(new UserSetting(TEST_USER_NAME)));
        User user = new User(TEST_USER_NAME, null);

        userRepository.saveAndFlush(user);
        userCredentialRepository.saveAndFlush(new UserCredential(user, TEST_USER_NAME, "secret", "noop", App.AIRSONIC));

        assertFalse(userSettingRepository.findById(TEST_USER_NAME).isPresent());

        UserSetting setting = new UserSetting(TEST_USER_NAME);
        userSettingRepository.save(setting);
        UserSetting userSetting = userSettingRepository.findById(TEST_USER_NAME).orElse(null);
        assertThat(userSetting).usingComparatorForType(Comparator.comparing(Instant::toEpochMilli), Instant.class)
            .usingRecursiveComparison().isEqualTo(setting);

        UserSettingDetail detail = userSetting.getSettings();
        detail.setLocale(Locale.SIMPLIFIED_CHINESE);
        detail.setThemeId("midnight");
        detail.setBetaVersionNotificationEnabled(true);
        detail.setSongNotificationEnabled(false);
        detail.setShowSideBar(true);
        detail.getMainVisibility().setBitRateVisible(true);
        detail.getPlaylistVisibility().setYearVisible(true);
        detail.setLastFmEnabled(true);
        detail.setListenBrainzEnabled(true);
        detail.setTranscodeScheme(TranscodeScheme.MAX_192);
        detail.setShowNowPlayingEnabled(false);
        detail.setSelectedMusicFolderId(3);
        detail.setPartyModeEnabled(true);
        detail.setNowPlayingAllowed(true);
        detail.setAvatarScheme(AvatarScheme.SYSTEM);
        detail.setSystemAvatarId(1);
        detail.setChanged(Instant.ofEpochMilli(9412L));
        detail.setKeyboardShortcutsEnabled(true);
        detail.setPaginationSizeFiles(120);
        detail.setPaginationSizeFolders(9);
        detail.setPaginationSizePlayqueue(121);
        detail.setPaginationSizePlaylist(122);

        userSettingRepository.save(userSetting);
        UserSetting actual = userSettingRepository.findById(TEST_USER_NAME).orElse(null);
        assertThat(actual).usingRecursiveComparison().isEqualTo(userSetting);

        userRepository.deleteById(TEST_USER_NAME);
        userRepository.flush();
    }
}
