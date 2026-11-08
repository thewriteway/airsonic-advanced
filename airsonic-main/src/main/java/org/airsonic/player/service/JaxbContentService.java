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

 Copyright 2025 (C) Y.Tory
 */
package org.airsonic.player.service;

import org.airsonic.player.controller.CoverArtController;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.domain.Transcoding;
import org.airsonic.player.util.StringUtil;
import org.springframework.stereotype.Service;
import org.subsonic.restapi.AlbumID3;
import org.subsonic.restapi.ArtistID3;
import org.subsonic.restapi.Child;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

@Service
public class JaxbContentService {

    private final JAXBWriter jaxbWriter = new JAXBWriter();
    private final ArtistService artistService;
    private final CoverArtService coverArtService;
    private final PlaylistService playlistService;
    private final AlbumService albumService;
    private final MediaFileService mediaFileService;
    private final TranscodingService transcodingService;
    private final RatingService ratingService;

    JaxbContentService(
            ArtistService artistService,
            CoverArtService coverArtService,
            PlaylistService playlistService,
            AlbumService albumService,
            MediaFileService mediaFileService,
            TranscodingService transcodingService,
            RatingService ratingService) {
        this.artistService = artistService;
        this.coverArtService = coverArtService;
        this.playlistService = playlistService;
        this.albumService = albumService;
        this.mediaFileService = mediaFileService;
        this.transcodingService = transcodingService;
        this.ratingService = ratingService;
    }

    public <T extends ArtistID3> T createJaxbArtist(T jaxbArtist, org.airsonic.player.domain.Artist artist, String username) {
        return fillJaxbArtist(jaxbArtist, artist, artistService.getStarredDate(artist.getId(), username),
                !CoverArt.NULL_ART.equals(coverArtService.getArtistArt(artist.getId())));
    }

    public org.subsonic.restapi.Artist createJaxbArtist(MediaFile artist, String username) {
        return fillJaxbFolderArtist(artist, mediaFileService.getMediaFileStarredDate(artist, username),
                ratingService.getRatingForUser(username, artist), ratingService.getAverageRating(artist));
    }

    /**
     * Builds the response entries for a list of artist directories. The starred dates and ratings are loaded for the
     * whole list in one query each, instead of the three queries per artist that
     * {@link #createJaxbArtist(MediaFile, String)} makes.
     */
    public List<org.subsonic.restapi.Artist> createJaxbArtists(List<MediaFile> artists, String username) {
        if (artists.isEmpty()) {
            return List.of();
        }
        List<Integer> ids = artists.stream().map(MediaFile::getId).toList();
        Map<Integer, Instant> starredDates = mediaFileService.getMediaFileStarredDates(ids, username);
        Map<Integer, Integer> userRatings = ratingService.getRatingsForUser(username, ids);
        Map<Integer, Double> averageRatings = ratingService.getAverageRatings(ids);
        return artists.stream()
                .map(artist -> fillJaxbFolderArtist(artist, starredDates.get(artist.getId()),
                        userRatings.get(artist.getId()), averageRatings.get(artist.getId())))
                .toList();
    }

    private org.subsonic.restapi.Artist fillJaxbFolderArtist(MediaFile artist, Instant starred, Integer userRating,
            Double averageRating) {
        org.subsonic.restapi.Artist result = new org.subsonic.restapi.Artist();
        result.setId(String.valueOf(artist.getId()));
        result.setName(artist.getTitle() != null ? artist.getTitle() : artist.getArtist());
        result.setStarred(jaxbWriter.convertDate(starred));
        result.setUserRating(userRating);
        result.setAverageRating(averageRating);
        return result;
    }

    /**
     * Builds the response entries for one page of artists. The starred dates and cover art are loaded for just these
     * artists, in one query each, instead of the two queries per artist that
     * {@link #createJaxbArtist(ArtistID3, org.airsonic.player.domain.Artist, String)} makes. For the whole library at
     * once use {@link #createJaxbArtistConverter}, which loads everything up front.
     */
    public List<ArtistID3> createJaxbArtistsID3(List<org.airsonic.player.domain.Artist> artists, String username) {
        if (artists.isEmpty()) {
            return List.of();
        }
        List<Integer> ids = artists.stream().map(org.airsonic.player.domain.Artist::getId).toList();
        Map<Integer, Instant> starredDates = artistService.getStarredDates(ids, username);
        Set<Integer> idsWithArt = coverArtService.getEntityIdsWithArt(CoverArt.EntityType.ARTIST, ids);
        return artists.stream()
                .map(artist -> fillJaxbArtist(new ArtistID3(), artist, starredDates.get(artist.getId()),
                        idsWithArt.contains(artist.getId())))
                .toList();
    }

    /**
     * Returns a converter for building many artists at once. The user's starred artists and the ids of artists with
     * cover art are loaded once up front, instead of the two queries per artist that {@link #createJaxbArtist} makes.
     */
    public Function<org.airsonic.player.domain.Artist, ArtistID3> createJaxbArtistConverter(String username) {
        Map<Integer, Instant> starredDates = artistService.getStarredDates(username);
        Set<Integer> artistIdsWithArt = coverArtService.getEntityIdsWithArt(CoverArt.EntityType.ARTIST);
        return artist -> fillJaxbArtist(new ArtistID3(), artist, starredDates.get(artist.getId()),
                artistIdsWithArt.contains(artist.getId()));
    }

    private <T extends ArtistID3> T fillJaxbArtist(T jaxbArtist, org.airsonic.player.domain.Artist artist,
            Instant starred, boolean hasArt) {
        jaxbArtist.setId(String.valueOf(artist.getId()));
        jaxbArtist.setName(artist.getName());
        jaxbArtist.setStarred(jaxbWriter.convertDate(starred));
        jaxbArtist.setAlbumCount(artist.getAlbumCount());
        if (hasArt) {
            jaxbArtist.setCoverArt(CoverArtController.ARTIST_COVERART_PREFIX + artist.getId());
        }
        return jaxbArtist;
    }

    public <T extends AlbumID3> T createJaxbAlbum(T jaxbAlbum, Album album, String username) {
        org.airsonic.player.domain.Artist artist = album.getArtist() == null ? null : artistService.getArtist(album.getArtist());
        return fillJaxbAlbum(jaxbAlbum, album,
                artist == null ? null : artist.getId(),
                !CoverArt.NULL_ART.equals(coverArtService.getAlbumArt(album.getId())),
                albumService.getAlbumStarredDate(album.getId(), username));
    }

    /**
     * Builds the response entries for a list of albums. The artist ids, cover art and the user's starred dates are
     * loaded for the whole list in one query each, instead of the three queries per album that
     * {@link #createJaxbAlbum} makes; a page of 500 albums goes from about 1,500 queries to 3.
     */
    public List<AlbumID3> createJaxbAlbums(List<Album> albums, String username) {
        if (albums.isEmpty()) {
            return List.of();
        }
        List<Integer> albumIds = albums.stream().map(Album::getId).toList();
        Map<String, Integer> artistIds = artistService.getArtistIdsByName(
                albums.stream().map(Album::getArtist).filter(Objects::nonNull).toList());
        Set<Integer> albumIdsWithArt = coverArtService.getEntityIdsWithArt(CoverArt.EntityType.ALBUM, albumIds);
        Map<Integer, Instant> starredDates = albumService.getAlbumStarredDates(albumIds, username);
        return albums.stream()
                .map(album -> fillJaxbAlbum(new AlbumID3(), album,
                        album.getArtist() == null ? null : artistIds.get(album.getArtist()),
                        albumIdsWithArt.contains(album.getId()),
                        starredDates.get(album.getId())))
                .toList();
    }

    private <T extends AlbumID3> T fillJaxbAlbum(T jaxbAlbum, Album album, Integer artistId, boolean hasArt,
            Instant starred) {
        jaxbAlbum.setId(String.valueOf(album.getId()));
        jaxbAlbum.setName(album.getName());
        if (album.getArtist() != null) {
            jaxbAlbum.setArtist(album.getArtist());
            if (artistId != null) {
                jaxbAlbum.setArtistId(String.valueOf(artistId));
            }
        }
        if (hasArt) {
            jaxbAlbum.setCoverArt(CoverArtController.ALBUM_COVERART_PREFIX + album.getId());
        }
        jaxbAlbum.setSongCount(album.getSongCount());
        jaxbAlbum.setDuration((int) Math.round(album.getDuration()));
        jaxbAlbum.setCreated(jaxbWriter.convertDate(album.getCreated()));
        jaxbAlbum.setStarred(jaxbWriter.convertDate(starred));
        jaxbAlbum.setPlayCount((long) album.getPlayCount());
        jaxbAlbum.setYear(album.getYear());
        jaxbAlbum.setGenre(album.getGenre());
        return jaxbAlbum;
    }

    public <T extends org.subsonic.restapi.Playlist> T createJaxbPlaylist(T jaxbPlaylist, Playlist playlist) {
        jaxbPlaylist.setId(String.valueOf(playlist.getId()));
        jaxbPlaylist.setName(playlist.getName());
        jaxbPlaylist.setComment(playlist.getComment());
        jaxbPlaylist.setOwner(playlist.getUsername());
        jaxbPlaylist.setPublic(playlist.getShared());
        jaxbPlaylist.setSongCount(playlist.getFileCount());
        jaxbPlaylist.setDuration((int) Math.round(playlist.getDuration()));
        jaxbPlaylist.setCreated(jaxbWriter.convertDate(playlist.getCreated()));
        jaxbPlaylist.setChanged(jaxbWriter.convertDate(playlist.getChanged()));
        jaxbPlaylist.setCoverArt(CoverArtController.PLAYLIST_COVERART_PREFIX + playlist.getId());

        for (String username : playlistService.getPlaylistUsers(playlist.getId())) {
            jaxbPlaylist.getAllowedUser().add(username);
        }
        return jaxbPlaylist;
    }

    public Child createJaxbChild(Player player, MediaFile mediaFile, String username) {
        return createJaxbChild(new Child(), player, mediaFile, username);
    }

    public <T extends Child> T createJaxbChild(T child, Player player, MediaFile mediaFile, String username) {
        MediaFile parent = mediaFileService.getParentOf(mediaFile);
        MediaFile artDir = mediaFile.isDirectory() ? mediaFile : parent;
        boolean hasArt = artDir != null && !CoverArt.NULL_ART.equals(coverArtService.getMediaFileArt(artDir.getId()));
        Integer albumId = null;
        Integer artistId = null;
        String transcodedSuffix = null;
        if (mediaFile.isFile()) {
            Album album = albumService.getAlbumByMediaFile(mediaFile);
            albumId = album == null ? null : album.getId();
            org.airsonic.player.domain.Artist artist = artistService.getArtist(mediaFile.getArtist());
            artistId = artist == null ? null : artist.getId();
            transcodedSuffix = transcodingService.getTranscodedSuffix(mediaFile,
                    transcodingService.getTranscodingsForPlayer(player));
        }
        return fillJaxbChild(child, mediaFile, parent, hasArt ? artDir : null,
                mediaFileService.getMediaFileStarredDate(mediaFile, username),
                ratingService.getRatingForUser(username, mediaFile), ratingService.getAverageRating(mediaFile),
                albumId, artistId, transcodedSuffix);
    }

    /**
     * Builds the response entries for a list of songs, videos or directories. Everything that
     * {@link #createJaxbChild(Player, MediaFile, String)} looks up per file (parent, cover art, starred date, ratings,
     * album and artist ids, and the player's transcodings) is loaded once for the whole list instead, so a page of
     * 500 songs takes a handful of queries rather than several thousand.
     */
    public List<Child> createJaxbChildren(Player player, List<MediaFile> mediaFiles, String username) {
        if (mediaFiles.isEmpty()) {
            return List.of();
        }
        // songs of one album share a parent, so look each parent up once
        Map<String, MediaFile> parentsByPath = new HashMap<>();
        List<MediaFile> parents = new ArrayList<>(mediaFiles.size());
        List<Integer> artDirIds = new ArrayList<>();
        List<MediaFile> files = new ArrayList<>();
        for (MediaFile mediaFile : mediaFiles) {
            MediaFile parent = null;
            if (mediaFile.getParentPath() != null && mediaFile.getFolder() != null) {
                String key = mediaFile.getFolder().getId() + "/" + mediaFile.getParentPath();
                if (parentsByPath.containsKey(key)) {
                    parent = parentsByPath.get(key);
                } else {
                    parent = mediaFileService.getParentOf(mediaFile);
                    parentsByPath.put(key, parent);
                }
            }
            parents.add(parent);
            MediaFile artDir = mediaFile.isDirectory() ? mediaFile : parent;
            if (artDir != null) {
                artDirIds.add(artDir.getId());
            }
            if (mediaFile.isFile()) {
                files.add(mediaFile);
            }
        }

        List<Integer> ids = mediaFiles.stream().map(MediaFile::getId).toList();
        Set<Integer> dirIdsWithArt = coverArtService.getEntityIdsWithArt(CoverArt.EntityType.MEDIA_FILE, artDirIds);
        Map<Integer, Instant> starredDates = mediaFileService.getMediaFileStarredDates(ids, username);
        Map<Integer, Integer> userRatings = ratingService.getRatingsForUser(username, ids);
        Map<Integer, Double> averageRatings = ratingService.getAverageRatings(ids);
        Map<Integer, Integer> albumIds = files.isEmpty() ? Map.of() : albumService.getAlbumIdsByMediaFile(files);
        Map<String, Integer> artistIds = files.isEmpty() ? Map.of()
                : artistService.getArtistIdsByName(files.stream().map(MediaFile::getArtist).filter(Objects::nonNull).toList());
        List<Transcoding> transcodings = files.isEmpty() ? List.of() : transcodingService.getTranscodingsForPlayer(player);

        List<Child> result = new ArrayList<>(mediaFiles.size());
        for (int i = 0; i < mediaFiles.size(); i++) {
            MediaFile mediaFile = mediaFiles.get(i);
            MediaFile parent = parents.get(i);
            MediaFile artDir = mediaFile.isDirectory() ? mediaFile : parent;
            boolean file = mediaFile.isFile();
            result.add(fillJaxbChild(new Child(), mediaFile, parent,
                    artDir != null && dirIdsWithArt.contains(artDir.getId()) ? artDir : null,
                    starredDates.get(mediaFile.getId()),
                    userRatings.get(mediaFile.getId()),
                    averageRatings.get(mediaFile.getId()),
                    file ? albumIds.get(mediaFile.getId()) : null,
                    file && mediaFile.getArtist() != null ? artistIds.get(mediaFile.getArtist()) : null,
                    file ? transcodingService.getTranscodedSuffix(mediaFile, transcodings) : null));
        }
        return result;
    }

    private <T extends Child> T fillJaxbChild(T child, MediaFile mediaFile, MediaFile parent, MediaFile coverArtDir,
            Instant starred, Integer userRating, Double averageRating, Integer albumId, Integer artistId,
            String transcodedSuffix) {
        child.setId(String.valueOf(mediaFile.getId()));
        try {
            if (Objects.nonNull(parent) && !mediaFileService.isRoot(parent)) {
                child.setParent(String.valueOf(parent.getId()));
            }
        } catch (SecurityException x) {
            // Ignored.
        }
        child.setTitle(mediaFile.getName());
        child.setAlbum(mediaFile.getAlbumName());
        child.setArtist(mediaFile.getArtist());
        child.setIsDir(mediaFile.isDirectory());
        child.setCoverArt(coverArtDir == null ? null : String.valueOf(coverArtDir.getId()));
        child.setYear(mediaFile.getYear());
        child.setGenre(mediaFile.getGenre());
        child.setCreated(jaxbWriter.convertDate(mediaFile.getCreated()));
        child.setStarred(jaxbWriter.convertDate(starred));
        child.setUserRating(userRating);
        child.setAverageRating(averageRating);
        child.setPlayCount((long) mediaFile.getPlayCount());

        if (mediaFile.isFile()) {
            Double mediaFileDuration = mediaFile.getDuration();
            child.setDuration((int) Math.round(mediaFileDuration == null ? 0 : mediaFileDuration));
            child.setBitRate(mediaFile.getBitRate());
            child.setTrack(mediaFile.getTrackNumber());
            child.setDiscNumber(mediaFile.getDiscNumber());
            child.setSize(mediaFile.getFileSize());
            String suffix = mediaFile.getFormat();
            child.setSuffix(suffix);
            child.setContentType(StringUtil.getMimeType(suffix));
            child.setIsVideo(mediaFile.isVideo());
            child.setPath(mediaFile.getPath());

            if (albumId != null) {
                child.setAlbumId(String.valueOf(albumId));
            }
            if (artistId != null) {
                child.setArtistId(String.valueOf(artistId));
            }
            switch (mediaFile.getMediaType()) {
                case MUSIC:
                    child.setType(org.subsonic.restapi.MediaType.MUSIC);
                    break;
                case PODCAST:
                    child.setType(org.subsonic.restapi.MediaType.PODCAST);
                    break;
                case AUDIOBOOK:
                    child.setType(org.subsonic.restapi.MediaType.AUDIOBOOK);
                    break;
                case VIDEO:
                    child.setType(org.subsonic.restapi.MediaType.VIDEO);
                    child.setOriginalWidth(mediaFile.getWidth());
                    child.setOriginalHeight(mediaFile.getHeight());
                    break;
                default:
                    break;
            }

            if (transcodedSuffix != null) {
                child.setTranscodedSuffix(transcodedSuffix);
                child.setTranscodedContentType(StringUtil.getMimeType(transcodedSuffix));
            }
        }
        return child;
    }
}
