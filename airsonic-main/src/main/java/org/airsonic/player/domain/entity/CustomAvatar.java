package org.airsonic.player.domain.entity;

import org.airsonic.player.domain.Avatar;
import org.airsonic.player.repository.PathConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;


@Entity
@Table(name = "custom_avatar")
public class CustomAvatar {

    private final static String AIRSONIC_HOME_REPLACEMENT = "$[AIRSONIC_HOME]";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name")
    private String name;

    @Column(name = "created_date")
    private Instant createdDate;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "width")
    private int width;

    @Column(name = "height")
    private int height;

    @Column(name = "path")
    @Convert(converter = PathConverter.class)
    private Path path;

    @Column(name = "username")
    private String username;

    public CustomAvatar() {
    }

    public CustomAvatar(String name, Instant createdDate, String mimeType, int width, int height, Path path, String username) {
        this.name = name;
        this.createdDate = createdDate;
        this.mimeType = mimeType;
        this.width = width;
        this.height = height;
        this.path = path;
        this.username = username;
    }

    public CustomAvatar(Integer id, String name, Instant createdDate, String mimeType, int width, int height, Path path, String username) {
        this.id = id;
        this.name = name;
        this.createdDate = createdDate;
        this.mimeType = mimeType;
        this.width = width;
        this.height = height;
        this.path = path;
        this.username = username;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public String getMimeType() {
        return mimeType;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Path getPath() {
        return path;
    }

    public String getUsername() {
        return username;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setPath(Path path) {
        this.path = path;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Convert this CustomAvatar to an Avatar, replacing the path with the absolute path to the avatar file.
     *
     * @param airsonicHome The absolute path to the Airsonic home directory.
     * @return The converted Avatar.
     */
    public Avatar toAvatar(Path airsonicHome) {
        if (path.startsWith(AIRSONIC_HOME_REPLACEMENT)) {
            Path relativePath = Paths.get(AIRSONIC_HOME_REPLACEMENT).relativize(path);
            return new Avatar(id, name, createdDate, mimeType, width, height, airsonicHome.resolve(relativePath));
        } else {
            return new Avatar(id, name, createdDate, mimeType, width, height, path);
        }
    }
}
