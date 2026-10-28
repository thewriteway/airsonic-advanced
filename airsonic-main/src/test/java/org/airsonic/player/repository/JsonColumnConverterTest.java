package org.airsonic.player.repository;

import org.airsonic.player.domain.AlbumListType;
import org.airsonic.player.domain.AvatarScheme;
import org.airsonic.player.domain.TranscodeScheme;
import org.airsonic.player.domain.User.Role;
import org.airsonic.player.domain.entity.UserSettingDetail;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JSON written by these converters is stored in the database, so it must stay
 * compatible with the format Jackson 2 produced before the move to Jackson 3.
 */
class JsonColumnConverterTest {

    @Test
    void userSettingDetailMatchesJackson2Format() throws Exception {
        String jackson2Json = readResource("/json/user-setting-detail-jackson2.json");
        UserSettingDetailConverter converter = newUserSettingDetailConverter();

        UserSettingDetail detail = new UserSettingDetail();
        detail.setLocale(Locale.forLanguageTag("pt-BR"));
        detail.setThemeId("dark");
        detail.setDefaultAlbumList(AlbumListType.NEWEST);
        detail.setTranscodeScheme(TranscodeScheme.MAX_192);
        detail.setAvatarScheme(AvatarScheme.SYSTEM);
        detail.setSystemAvatarId(3);
        detail.setChanged(Instant.ofEpochSecond(1700000000L, 123456789L));
        detail.setListenBrainzUrl("https://api.listenbrainz.org");

        assertEquals(jackson2Json, converter.convertToDatabaseColumn(detail));

        UserSettingDetail read = converter.convertToEntityAttribute(jackson2Json);
        assertEquals(Instant.ofEpochSecond(1700000000L, 123456789L), read.getChanged());
        assertEquals(Locale.forLanguageTag("pt-BR"), read.getLocale());
        assertEquals(jackson2Json, converter.convertToDatabaseColumn(read));
    }

    @Test
    void rolesMatchJackson2Format() {
        RolesConverter converter = new RolesConverter();

        assertEquals("[\"ADMIN\",\"SETTINGS\",\"STREAM\"]",
                converter.convertToDatabaseColumn(EnumSet.of(Role.ADMIN, Role.SETTINGS, Role.STREAM)));
        assertEquals(Set.of(Role.ADMIN, Role.SETTINGS, Role.STREAM),
                converter.convertToEntityAttribute("[\"ADMIN\",\"SETTINGS\",\"STREAM\"]"));
        assertTrue(converter.convertToEntityAttribute("not json").isEmpty());
    }

    private static UserSettingDetailConverter newUserSettingDetailConverter() throws Exception {
        Constructor<UserSettingDetailConverter> constructor = UserSettingDetailConverter.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static String readResource(String name) throws Exception {
        try (InputStream in = JsonColumnConverterTest.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
    }
}
