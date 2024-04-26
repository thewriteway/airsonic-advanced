package org.airsonic.player.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@Entity
@Table(name = "sonoslink")
public class SonosLink {

    @Id
    @NotNull
    @Column(name = "linkcode")
    private String linkcode;

    @NotNull
    @Column(name = "username")
    private String username;

    @NotNull
    @Column(name = "householdid")
    private String householdId;

    @Column(name = "initiator")
    private String initiator;

    @Column(name = "initiated")
    private Instant initiated;

    public SonosLink() {
    }

    public SonosLink(String username, String linkcode, String householdId, String initiator, Instant initiated) {
        this.username = username;
        this.linkcode = linkcode;
        this.householdId = householdId;
        this.initiator = initiator;
        this.initiated = initiated;
    }

    public String getUsername() {
        return username;
    }

    public String getHouseholdId() {
        return householdId;
    }

    public String getLinkcode() {
        return linkcode;
    }

    public String getInitiator() {
        return initiator;
    }

    public Instant getInitiated() {
        return initiated;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((householdId == null) ? 0 : householdId.hashCode());
        result = prime * result + ((linkcode == null) ? 0 : linkcode.hashCode());
        result = prime * result + ((username == null) ? 0 : username.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SonosLink other = (SonosLink) obj;
        if (householdId == null) {
            if (other.householdId != null)
                return false;
        } else if (!householdId.equals(other.householdId))
            return false;
        if (linkcode == null) {
            if (other.linkcode != null)
                return false;
        } else if (!linkcode.equals(other.linkcode))
            return false;
        if (username == null) {
            if (other.username != null)
                return false;
        } else if (!username.equals(other.username))
            return false;
        return true;
    }
}
