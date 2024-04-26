/*
 * This file is part of Airsonic.
 *
 *  Airsonic is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Airsonic is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  Copyright 2024 (C) Y.Tory
 */
package org.airsonic.player.service.sonos;

import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class MoxyConfiguration {

    @Bean
    public JAXBContext jaxbContext() {
        try {
            Map<String, Object> properties = new HashMap<>();
            return JAXBContextFactory.createContext("com.sonos.services._1", this.getClass().getClassLoader(), properties);
        } catch (JAXBException ex) {
            throw new RuntimeException(ex);
        }
    }

}
