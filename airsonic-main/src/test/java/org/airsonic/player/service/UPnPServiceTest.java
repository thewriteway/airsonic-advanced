/*
 This file is part of Airsonic.

 Airsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2026 (C) Airsonic Authors
 */
package org.airsonic.player.service;

import org.airsonic.player.domain.Version;
import org.airsonic.player.service.upnp.CustomContentDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.jupnp.model.meta.LocalDevice;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UPnPServiceTest {

    @Mock
    private SettingsService settingsService;

    @Mock
    private VersionService versionService;

    @Mock
    private CustomContentDirectory dispatchingContentDirectory;

    @InjectMocks
    private UPnPService upnpService;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "http://bad host:4040"})
    public void testResolvePresentationURIShouldReturnNullWhenUnsetOrInvalid(String dlnaBaseLANURL) {
        assertNull(UPnPService.resolvePresentationURI(dlnaBaseLANURL));
    }

    @Test
    public void testResolvePresentationURIShouldUseConfiguredURL() {
        assertEquals(URI.create("http://192.168.1.10:4040/"),
                UPnPService.resolvePresentationURI(" http://192.168.1.10:4040/ "));
    }

    @Test
    public void testCreateMediaServerDeviceShouldNotFailWhenBaseLANURLIsNull() throws Exception {
        when(settingsService.getDlnaServerName()).thenReturn("Airsonic");
        when(settingsService.getDlnaServerId()).thenReturn("server-id");
        when(settingsService.getDlnaBaseLANURL()).thenReturn(null);
        when(versionService.getLocalBuildNumber()).thenReturn("revision");
        when(versionService.getLocalVersion()).thenReturn(new Version("11.1.0"));

        LocalDevice device = upnpService.createMediaServerDevice();

        assertNotNull(device);
        assertNull(device.getDetails().getPresentationURI());
    }

    @Test
    public void testCreateMediaServerDeviceShouldAdvertiseBaseLANURL() throws Exception {
        when(settingsService.getDlnaServerName()).thenReturn("Airsonic");
        when(settingsService.getDlnaServerId()).thenReturn("server-id");
        when(settingsService.getDlnaBaseLANURL()).thenReturn("http://192.168.1.10:4040");
        when(versionService.getLocalBuildNumber()).thenReturn("revision");
        when(versionService.getLocalVersion()).thenReturn(new Version("11.1.0"));

        LocalDevice device = upnpService.createMediaServerDevice();

        assertEquals(URI.create("http://192.168.1.10:4040"), device.getDetails().getPresentationURI());
    }
}
