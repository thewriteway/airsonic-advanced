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

package org.airsonic.player.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Entity
@Table(name = "user_rating")
@IdClass(UserRatingKey.class)
public class UserRating {

    @Id
    private String username;

    @Id
    @Column(name = "media_file_id")
    private int mediaFileId;

    @Min(1)
    @Max(5)
    private Integer rating;

    public UserRating() {
    }

    public UserRating(String username, int mediaFileId, int rating) {
        this.username = username;
        this.mediaFileId = mediaFileId;
        this.rating = rating;
    }

    public String getUsername() {
        return username;
    }

    public int getMediaFileId() {
        return mediaFileId;
    }

    public Integer getRating() {
        return rating;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setMediaFileId(int mediaFileId) {
        this.mediaFileId = mediaFileId;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    @Override
    public String toString() {
        return "UserRating{" +
                "username='" + username + '\'' +
                ", media_file_id=" + mediaFileId +
                ", rating=" + rating +
                '}';
    }


}
