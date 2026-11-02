package org.airsonic.player.ajax;

import org.airsonic.player.domain.LastFmCoverArt;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.CoverArtService;
import org.airsonic.player.service.LastFmService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.SecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;

@Controller
@MessageMapping("/coverart")
public class CoverArtWSController {
    public static final Logger LOG = LoggerFactory.getLogger(CoverArtWSController.class);

    @Autowired
    private LastFmService lastFmService;
    @Autowired
    private CoverArtService coverArtService;
    @Autowired
    private MediaFileService mediaFileService;
    @Autowired
    private SecurityService securityService;

    @MessageMapping("/search")
    @SendToUser(broadcast = false)
    public List<LastFmCoverArt> searchCoverArt(CoverArtSearchRequest req) {
        return lastFmService.searchCoverArt(req.getArtist(), req.getAlbum());
    }

    /**
     * Downloads and saves the cover art at the given URL.
     *
     * @return The error string if something goes wrong, <code>"OK"</code> otherwise.
     */
    @MessageMapping("/set")
    @SendToUser(broadcast = false)
    public String setCoverArtImage(Principal principal, CoverArtSetRequest req) {
        // WebSocket messages bypass the URL rules that limit the cover art page to the cover art role
        User user = securityService.getUserByName(principal.getName());
        if (user == null || !user.isCoverArtRole()) {
            return "Permission denied: changing cover art requires the cover art role.";
        }
        MediaFile dir = mediaFileService.getMediaFile(req.getId());
        if (dir == null || !securityService.isFolderAccessAllowed(dir, user.getUsername())) {
            return "Media file not found: " + req.getId();
        }
        try {
            coverArtService.setCoverArtImageFromUrl(req.getId(), req.getUrl());
            return "OK";
        } catch (Exception e) {
            LOG.warn("Failed to save cover art for media file {}", req.getId(), e);
            return e.toString();
        }
    }

    public void setLastFmService(LastFmService lastFmService) {
        this.lastFmService = lastFmService;
    }

    public static class CoverArtSearchRequest {
        private String artist;
        private String album;

        public String getArtist() {
            return artist;
        }

        public void setArtist(String artist) {
            this.artist = artist;
        }

        public String getAlbum() {
            return album;
        }

        public void setAlbum(String album) {
            this.album = album;
        }
    }

    public static class CoverArtSetRequest {
        private int id;
        private String url;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}
