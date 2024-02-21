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

 Copyright 2024 (C) Y.Tory
 */
package org.airsonic.player.command;

import org.airsonic.player.domain.InternetRadio;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class InternetRadioCommand {

    public InternetRadioCommand() {
    }

    public InternetRadioCommand(List<InternetRadio> internetRadios) {
        List<InternetRadioDTO> internetRadioDTOs = new ArrayList<>();
        internetRadios.forEach(internetRadio -> {
            InternetRadioDTO dto = new InternetRadioDTO();
            dto.setId(internetRadio.getId());
            dto.setName(internetRadio.getName());
            dto.setStreamUrl(internetRadio.getStreamUrl());
            dto.setHomepageUrl(internetRadio.getHomepageUrl());
            dto.setEnabled(internetRadio.isEnabled());
            internetRadioDTOs.add(dto);
        });
        this.internetRadios = internetRadioDTOs;
    }

    private InternetRadioDTO newRadio = new InternetRadioDTO();
    private List<InternetRadioDTO> internetRadios = new ArrayList<>();

    public InternetRadioDTO getNewRadio() {
        return newRadio;
    }

    public void setNewRadio(InternetRadioDTO newRadio) {
        this.newRadio = newRadio;
    }

    public List<InternetRadioDTO> getInternetRadios() {
        return internetRadios;
    }

    public void setInternetRadios(List<InternetRadioDTO> internetRadios) {
        this.internetRadios = internetRadios;
    }

    public static class InternetRadioDTO {
        private Integer id;
        private String name;
        private String streamUrl;
        private String homepageUrl;
        private boolean enabled = true;
        private boolean delete;

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = StringUtils.trimToNull(name);
        }

        public String getStreamUrl() {
            return streamUrl;
        }

        public void setStreamUrl(String streamUrl) {
            this.streamUrl = StringUtils.trimToNull(streamUrl);
        }

        public String getHomepageUrl() {
            return homepageUrl;
        }

        public void setHomepageUrl(String homepageUrl) {
            this.homepageUrl = StringUtils.trimToNull(homepageUrl);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isDelete() {
            return delete;
        }

        public void setDelete(boolean delete) {
            this.delete = delete;
        }
    }

}
