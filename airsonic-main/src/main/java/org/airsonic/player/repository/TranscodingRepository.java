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

import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.Transcoding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TranscodingRepository extends JpaRepository<Transcoding, Integer> {

    public List<Transcoding> findByIdIn(List<Integer> ids);

    public List<Transcoding> findByPlayersContaining(Player player);

    public List<Transcoding> findByDefaultActiveTrue();

}
