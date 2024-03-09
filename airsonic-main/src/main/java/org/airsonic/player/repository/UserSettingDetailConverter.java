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

 Copyright 2023 (C) Y.Tory
 */
package org.airsonic.player.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.airsonic.player.domain.entity.UserSettingDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.AttributeConverter;

public class UserSettingDetailConverter implements AttributeConverter<UserSettingDetail, String> {

    private ObjectMapper objectMapper = new ObjectMapper();

    private UserSettingDetailConverter() {
        objectMapper.registerModule(new JavaTimeModule());
    }

    private static Logger LOG = LoggerFactory.getLogger(UserSettingDetailConverter.class);

    @Override
    public String convertToDatabaseColumn(UserSettingDetail setting) {
        try {
            return setting == null ? null : objectMapper.writeValueAsString(setting);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to convert settings column of user_settings to json", e);
            return null;
        }
    }

    @Override
    public UserSettingDetail convertToEntityAttribute(String setting) {
        try {
            return setting == null ? null : objectMapper.readValue(setting, UserSettingDetail.class);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to convert user setting detail from json", e);
            return null;
        }
    }
}