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
package org.airsonic.player.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * The transfer totals of a user as of a day, i.e. the byte counters of {@link User} when the snapshot was last
 * updated that day.
 */
@Entity
@Table(name = "user_transfer_snapshot")
public class UserTransferSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "bytes_streamed", nullable = false)
    private long bytesStreamed;

    @Column(name = "bytes_downloaded", nullable = false)
    private long bytesDownloaded;

    @Column(name = "bytes_uploaded", nullable = false)
    private long bytesUploaded;

    public UserTransferSnapshot() {
    }

    public UserTransferSnapshot(String username, LocalDate snapshotDate, long bytesStreamed, long bytesDownloaded,
            long bytesUploaded) {
        this.username = username;
        this.snapshotDate = snapshotDate;
        this.bytesStreamed = bytesStreamed;
        this.bytesDownloaded = bytesDownloaded;
        this.bytesUploaded = bytesUploaded;
    }

    public Integer getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public long getBytesStreamed() {
        return bytesStreamed;
    }

    public long getBytesDownloaded() {
        return bytesDownloaded;
    }

    public long getBytesUploaded() {
        return bytesUploaded;
    }

    public void setTotals(long bytesStreamed, long bytesDownloaded, long bytesUploaded) {
        this.bytesStreamed = bytesStreamed;
        this.bytesDownloaded = bytesDownloaded;
        this.bytesUploaded = bytesUploaded;
    }
}
