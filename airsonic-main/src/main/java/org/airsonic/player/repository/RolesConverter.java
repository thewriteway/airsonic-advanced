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

import org.airsonic.player.domain.User.Role;
import org.airsonic.player.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;

import java.util.HashSet;
import java.util.Set;

public class RolesConverter implements AttributeConverter<Set<Role>, String> {

    private final ObjectMapper objectMapper = Util.jsonMapperBuilder().build();

    private static Logger LOG = LoggerFactory.getLogger(RolesConverter.class);

    @Override
    public String convertToDatabaseColumn(Set<Role> roles) {
        try {
            return roles == null ? null : objectMapper.writeValueAsString(roles);
        } catch (JacksonException e) {
            LOG.warn("Failed to convert roles to json", e);
            return null;
        }
    }

    @Override
    public Set<Role> convertToEntityAttribute(String roles) {
        TypeReference<Set<Role>> typeReference = new TypeReference<>() {};
        try {
            return roles == null ? new HashSet<>() : objectMapper.readValue(roles, typeReference);
        } catch (JacksonException e) {
            LOG.warn("Failed to convert roles from json", e);
            return new HashSet<>();
        }
    }
}
