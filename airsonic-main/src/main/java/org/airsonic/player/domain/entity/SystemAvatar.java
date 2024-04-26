package org.airsonic.player.domain.entity;

import org.airsonic.player.domain.Avatar;
import org.airsonic.player.repository.PathConverter;

import jakarta.persistence.*;

import java.nio.file.Path;
import java.time.Instant;

@Entity
@Table(name = "system_avatar")
public class SystemAvatar {

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

    public SystemAvatar() {
    }

    public SystemAvatar(String name, Instant createdDate, String mimeType, int width, int height, Path path) {
        this.name = name;
        this.createdDate = createdDate;
        this.mimeType = mimeType;
        this.width = width;
        this.height = height;
        this.path = path;
    }

    public SystemAvatar(Integer id, String name, Instant createdDate, String mimeType, int width, int height,
            Path path) {
        this.id = id;
        this.name = name;
        this.createdDate = createdDate;
        this.mimeType = mimeType;
        this.width = width;
        this.height = height;
        this.path = path;
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

    public Avatar toAvatar() {
        return new Avatar(id, name, createdDate, mimeType, width, height, path);
    }

}
