package org.airsonic.player.domain;

import org.airsonic.player.security.PasswordDecoder;
import org.airsonic.player.security.PasswordEncoderConfig;
import org.springframework.util.StringUtils;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Entity
@Table(name = "user_credentials")
public class UserCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "username", nullable = false)
    private User user;
    @Column(name = "app_username")
    private String appUsername;
    @Column(name = "credential")
    private String credential;
    @Column(name = "encoder")
    private String encoder;
    @Column(name = "app")
    @Enumerated(EnumType.STRING)
    private App app;
    @Column(name = "comment", nullable = true)
    private String comment;
    @Column(name = "expiration", nullable = true)
    private Instant expiration;
    @Column(name = "created")
    private Instant created;
    @Column(name = "updated")
    private Instant updated;

    public UserCredential() {
        super();
    }

    public UserCredential(User user, String appUsername, String credential, String encoder, App app,
            String comment, Instant expiration, Instant created, Instant updated) {
        super();
        this.user = user;
        this.appUsername = appUsername;
        this.credential = credential;
        this.encoder = encoder;
        this.app = app;
        this.comment = comment;
        this.expiration = expiration;
        this.created = created;
        this.updated = updated;
    }

    public UserCredential(User user, String appUsername, String credential, String encoder, App app,
            String comment, Instant expiration) {
        this(user, appUsername, credential, encoder, app, comment, expiration, null, null);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        setCreated(now);
        setUpdated(now);
    }

    public UserCredential(User user, String appUsername, String credential, String encoder, App app,
            String comment) {
        this(user, appUsername, credential, encoder, app, comment, null);
    }

    public UserCredential(User user, String appUsername, String credential, String encoder, App app) {
        this(user, appUsername, credential, encoder, app, null);
    }

    public UserCredential(UserCredential uc) {
        this(uc.getUser(), uc.getAppUsername(), uc.getCredential(), uc.getEncoder(), uc.getApp(),
                uc.getComment(), uc.getExpiration(), uc.getCreated(), uc.getUpdated());
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getAppUsername() {
        return appUsername;
    }

    public void setAppUsername(String appUsername) {
        this.appUsername = appUsername;
    }

    public String getCredential() {
        return credential;
    }

    public void setCredential(String credential) {
        this.credential = credential;
    }

    public String getEncoder() {
        return encoder;
    }

    public void setEncoder(String encoder) {
        this.encoder = encoder;
    }

    public App getApp() {
        return app;
    }

    public void setApp(App app) {
        this.app = app;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public Instant getUpdated() {
        return updated;
    }

    public void setUpdated(Instant updated) {
        this.updated = updated;
    }

    public Instant getExpiration() {
        return expiration;
    }

    public void setExpiration(Instant expiration) {
        this.expiration = expiration;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public int hashCode() {
        return Objects.hash(comment, created, credential, expiration, app, appUsername, encoder, updated, user.getUsername());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        UserCredential other = (UserCredential) obj;
        if (comment == null) {
            if (other.comment != null)
                return false;
        } else if (!comment.equals(other.comment))
            return false;
        if (created == null) {
            if (other.created != null)
                return false;
        } else if (!created.equals(other.created))
            return false;
        if (credential == null) {
            if (other.credential != null)
                return false;
        } else if (!credential.equals(other.credential))
            return false;
        if (expiration == null) {
            if (other.expiration != null)
                return false;
        } else if (!expiration.equals(other.expiration))
            return false;
        if (app == null) {
            if (other.app != null)
                return false;
        } else if (!app.equals(other.app))
            return false;
        if (appUsername == null) {
            if (other.appUsername != null)
                return false;
        } else if (!appUsername.equals(other.appUsername))
            return false;
        if (encoder == null) {
            if (other.encoder != null)
                return false;
        } else if (!encoder.equals(other.encoder))
            return false;
        if (updated == null) {
            if (other.updated != null)
                return false;
        } else if (!updated.equals(other.updated))
            return false;
        if (user == null) {
            if (other.user != null)
                return false;
        } else if (!user.getUsername().equals(other.user.getUsername()))
            return false;
        return true;
    }

    /**
     * Update the encoder and credential for this UserCredential.
     *
     * @param newEncoder                new encoder to use
     * @param reencodePlaintextNewCreds if true, reencode the new credential using
     *                                  the new encoder
     * @return true if the update was successful, false otherwise
     */
    public boolean updateEncoder(String newEncoder, boolean reencodePlaintextNewCreds) {

        if (!StringUtils.hasLength(this.encoder) || !StringUtils.hasLength(newEncoder)) {
            return false;
        }
        if (!this.encoder.equals(newEncoder) || reencodePlaintextNewCreds) {
            if (reencodePlaintextNewCreds) {
                String newCredential = PasswordEncoderConfig.ENCODERS.get(newEncoder).encode(this.credential);
                if (!this.credential.equals(newCredential)) {
                    this.credential = newCredential;
                    this.setUpdated(Instant.now().truncatedTo(ChronoUnit.MICROS));
                }
            } else if (PasswordEncoderConfig.DECODABLE_ENCODERS.contains(this.encoder)) {
                try {
                    PasswordDecoder decoder = (PasswordDecoder) PasswordEncoderConfig.ENCODERS.get(this.encoder);
                    String decodedCredential = decoder.decode(this.credential);
                    this.credential = PasswordEncoderConfig.ENCODERS.get(newEncoder).encode(decodedCredential);
                    this.setUpdated(Instant.now().truncatedTo(ChronoUnit.MICROS));
                } catch (Exception e) {
                    return false;
                }
            }
        }
        this.encoder = newEncoder;
        return true;
    }

    public enum App {
        AIRSONIC("Airsonic", false, true),
        LASTFM("Last.fm", true, false),
        LISTENBRAINZ("Listenbrainz", false, false),
        PODCASTINDEX("Podcast Index", true, false);

        private final String name;
        private final boolean usernameRequired;
        private final boolean nonDecodableEncodersAllowed;

        private App(String name, boolean usernameRequired, boolean nonDecodableEncodersAllowed) {
            this.name = name;
            this.usernameRequired = usernameRequired;
            this.nonDecodableEncodersAllowed = nonDecodableEncodersAllowed;
        }

        public String getName() {
            return name;
        }

        public boolean getUsernameRequired() {
            return usernameRequired;
        }

        public boolean getNonDecodableEncodersAllowed() {
            return nonDecodableEncodersAllowed;
        }

    }
}
