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

 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.domain.entity;

import org.airsonic.player.repository.UserSettingDetailConverter;

import jakarta.persistence.*;

@Entity
@Table(name = "user_settings")
public class UserSetting {

    @Id
    @Column(name = "username")
    private String username;

    @Column(name = "settings")
    @Convert(converter = UserSettingDetailConverter.class)
    private UserSettingDetail setting;

    public UserSetting() {
    }

    public UserSetting(String username) {
        this.username = username;
        this.setting = new UserSettingDetail();
    }

    public UserSetting(String username, UserSettingDetail setting) {
        this.username = username;
        this.setting = setting;
    }

    public String getUsername() {
        return username;
    }

    public UserSettingDetail getSettings() {
        return setting;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setSettings(UserSettingDetail setting) {
        this.setting = setting;
    }

}
