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
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.domain;

import org.airsonic.player.domain.entity.UserSetting;
import org.airsonic.player.domain.entity.UserSettingDetail;

import java.time.Instant;
import java.util.Locale;

/**
 * Represent user-specific settings.
 *
 * @author Sindre Mehus
 */
public class UserSettings {

    private String username;
    private Locale locale;
    private String themeId;
    private boolean showNowPlayingEnabled;
    private boolean showArtistInfoEnabled;
    private boolean finalVersionNotificationEnabled;
    private boolean betaVersionNotificationEnabled;
    private boolean songNotificationEnabled;
    private boolean keyboardShortcutsEnabled;
    private boolean autoHidePlayQueue;
    private boolean showSideBar;
    private boolean viewAsList;
    private boolean queueFollowingSongs;
    private AlbumListType defaultAlbumList = AlbumListType.RANDOM;
    private UserSettingVisibility mainVisibility = new UserSettingVisibility();
    private UserSettingVisibility playlistVisibility = new UserSettingVisibility();
    private UserSettingVisibility playqueueVisibility = new UserSettingVisibility();
    private boolean lastFmEnabled;
    private boolean listenBrainzEnabled;
    private String listenBrainzUrl;
    private boolean podcastIndexEnabled;
    private String podcastIndexUrl;
    private TranscodeScheme transcodeScheme = TranscodeScheme.OFF;
    private int selectedMusicFolderId = -1;
    private boolean partyModeEnabled;
    private boolean nowPlayingAllowed;
    private AvatarScheme avatarScheme = AvatarScheme.NONE;
    private Integer systemAvatarId;
    private Instant changed = Instant.now();
    private int paginationSizeFiles = 20;
    private int paginationSizeFolders = 7;
    private int paginationSizePlaylist = 10;
    private int paginationSizePlayqueue = 10;
    private int paginationSizeBookmarks = 20;
    private boolean autoBookmark = true;
    private int videoBookmarkFrequency = 40;
    private int audioBookmarkFrequency = 10;
    private int searchCount = 25;

    public UserSettings() {
    }

    public UserSettings(String username) {
        this.username = username;
    }

    /**
     * Creates a new instance based on the given {@link UserSetting}.
     * @param username The username.
     * @param setting The user setting. not <code>null</code>.
     */
    public UserSettings(String username, UserSettingDetail setting) {
        this.username = username;
        this.updateByDetail(setting);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Locale getLocale() {
        return locale;
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    public String getThemeId() {
        return themeId;
    }

    public void setThemeId(String themeId) {
        this.themeId = themeId;
    }

    public boolean getShowNowPlayingEnabled() {
        return showNowPlayingEnabled;
    }

    public void setShowNowPlayingEnabled(boolean showNowPlayingEnabled) {
        this.showNowPlayingEnabled = showNowPlayingEnabled;
    }

    public boolean getShowArtistInfoEnabled() {
        return showArtistInfoEnabled;
    }

    public void setShowArtistInfoEnabled(boolean showArtistInfoEnabled) {
        this.showArtistInfoEnabled = showArtistInfoEnabled;
    }

    public boolean getFinalVersionNotificationEnabled() {
        return finalVersionNotificationEnabled;
    }

    public void setFinalVersionNotificationEnabled(boolean finalVersionNotificationEnabled) {
        this.finalVersionNotificationEnabled = finalVersionNotificationEnabled;
    }

    public boolean getBetaVersionNotificationEnabled() {
        return betaVersionNotificationEnabled;
    }

    public void setBetaVersionNotificationEnabled(boolean betaVersionNotificationEnabled) {
        this.betaVersionNotificationEnabled = betaVersionNotificationEnabled;
    }

    public boolean getSongNotificationEnabled() {
        return songNotificationEnabled;
    }

    public void setSongNotificationEnabled(boolean songNotificationEnabled) {
        this.songNotificationEnabled = songNotificationEnabled;
    }

    public UserSettingVisibility getMainVisibility() {
        return mainVisibility;
    }

    public void setMainVisibility(UserSettingVisibility mainVisibility) {
        this.mainVisibility = mainVisibility;
    }

    public UserSettingVisibility getPlaylistVisibility() {
        return playlistVisibility;
    }

    public void setPlaylistVisibility(UserSettingVisibility playlistVisibility) {
        this.playlistVisibility = playlistVisibility;
    }

    public UserSettingVisibility getPlayqueueVisibility() {
        return playqueueVisibility;
    }

    public void setPlayqueueVisibility(UserSettingVisibility playqueueVisibility) {
        this.playqueueVisibility = playqueueVisibility;
    }

    public boolean getLastFmEnabled() {
        return lastFmEnabled;
    }

    public void setLastFmEnabled(boolean lastFmEnabled) {
        this.lastFmEnabled = lastFmEnabled;
    }

    public boolean getListenBrainzEnabled() {
        return listenBrainzEnabled;
    }

    public void setListenBrainzEnabled(boolean listenBrainzEnabled) {
        this.listenBrainzEnabled = listenBrainzEnabled;
    }

    public String getListenBrainzUrl() {
        return listenBrainzUrl;
    }

    public void setListenBrainzUrl(String listenBrainzUrl) {
        this.listenBrainzUrl = listenBrainzUrl;
    }

    public boolean getPodcastIndexEnabled() {
        return podcastIndexEnabled;
    }

    public void setPodcastIndexEnabled(boolean podcastIndexEnabled) {
        this.podcastIndexEnabled = podcastIndexEnabled;
    }

    public String getPodcastIndexUrl() {
        return podcastIndexUrl;
    }

    public void setPodcastIndexUrl(String podcastIndexUrl) {
        this.podcastIndexUrl = podcastIndexUrl;
    }

    public TranscodeScheme getTranscodeScheme() {
        return transcodeScheme;
    }

    public void setTranscodeScheme(TranscodeScheme transcodeScheme) {
        this.transcodeScheme = transcodeScheme;
    }

    public int getSelectedMusicFolderId() {
        return selectedMusicFolderId;
    }

    public void setSelectedMusicFolderId(int selectedMusicFolderId) {
        this.selectedMusicFolderId = selectedMusicFolderId;
    }

    public boolean getPartyModeEnabled() {
        return partyModeEnabled;
    }

    public void setPartyModeEnabled(boolean partyModeEnabled) {
        this.partyModeEnabled = partyModeEnabled;
    }

    public boolean getNowPlayingAllowed() {
        return nowPlayingAllowed;
    }

    public void setNowPlayingAllowed(boolean nowPlayingAllowed) {
        this.nowPlayingAllowed = nowPlayingAllowed;
    }

    public boolean getAutoHidePlayQueue() {
        return autoHidePlayQueue;
    }

    public void setAutoHidePlayQueue(boolean autoHidePlayQueue) {
        this.autoHidePlayQueue = autoHidePlayQueue;
    }

    public boolean getKeyboardShortcutsEnabled() {
        return keyboardShortcutsEnabled;
    }

    public void setKeyboardShortcutsEnabled(boolean keyboardShortcutsEnabled) {
        this.keyboardShortcutsEnabled = keyboardShortcutsEnabled;
    }

    public boolean getShowSideBar() {
        return showSideBar;
    }

    public void setShowSideBar(boolean showSideBar) {
        this.showSideBar = showSideBar;
    }

    public boolean getViewAsList() {
        return viewAsList;
    }

    public void setViewAsList(boolean viewAsList) {
        this.viewAsList = viewAsList;
    }

    public AlbumListType getDefaultAlbumList() {
        return defaultAlbumList;
    }

    public void setDefaultAlbumList(AlbumListType defaultAlbumList) {
        this.defaultAlbumList = defaultAlbumList;
    }

    public AvatarScheme getAvatarScheme() {
        return avatarScheme;
    }

    public void setAvatarScheme(AvatarScheme avatarScheme) {
        this.avatarScheme = avatarScheme;
    }

    public Integer getSystemAvatarId() {
        return systemAvatarId;
    }

    public void setSystemAvatarId(Integer systemAvatarId) {
        this.systemAvatarId = systemAvatarId;
    }

    /**
     * Returns when the corresponding database entry was last changed.
     *
     * @return When the corresponding database entry was last changed.
     */
    public Instant getChanged() {
        return changed;
    }

    /**
     * Sets when the corresponding database entry was last changed.
     *
     * @param changed When the corresponding database entry was last changed.
     */
    public void setChanged(Instant changed) {
        this.changed = changed;
    }

    public boolean getQueueFollowingSongs() {
        return queueFollowingSongs;
    }

    public void setQueueFollowingSongs(boolean queueFollowingSongs) {
        this.queueFollowingSongs = queueFollowingSongs;
    }

    public int getPaginationSizeFiles() {
        return paginationSizeFiles;
    }

    public void setPaginationSizeFiles(int paginationSizeFiles) {
        this.paginationSizeFiles = paginationSizeFiles;
    }

    public int getPaginationSizeFolders() {
        return paginationSizeFolders;
    }

    public void setPaginationSizeFolders(int paginationSizeFolders) {
        this.paginationSizeFolders = paginationSizeFolders;
    }

    public int getPaginationSizePlaylist() {
        return paginationSizePlaylist;
    }

    public void setPaginationSizePlaylist(int paginationSizePlaylist) {
        this.paginationSizePlaylist = paginationSizePlaylist;
    }

    public int getPaginationSizePlayqueue() {
        return paginationSizePlayqueue;
    }

    public void setPaginationSizePlayqueue(int paginationSizePlayqueue) {
        this.paginationSizePlayqueue = paginationSizePlayqueue;
    }

    public int getPaginationSizeBookmarks() {
        return paginationSizeBookmarks;
    }

    public void setPaginationSizeBookmarks(int paginationSizeBookmarks) {
        this.paginationSizeBookmarks = paginationSizeBookmarks;
    }

    public boolean getAutoBookmark() {
        return autoBookmark;
    }

    public void setAutoBookmark(boolean autoBookmark) {
        this.autoBookmark = autoBookmark;
    }

    public int getVideoBookmarkFrequency() {
        return videoBookmarkFrequency;
    }

    public void setVideoBookmarkFrequency(int videoBookmarkFrequency) {
        this.videoBookmarkFrequency = videoBookmarkFrequency;
    }

    public int getAudioBookmarkFrequency() {
        return audioBookmarkFrequency;
    }

    public void setAudioBookmarkFrequency(int audioBookmarkFrequency) {
        this.audioBookmarkFrequency = audioBookmarkFrequency;
    }

    public int getSearchCount() {
        return searchCount;
    }

    public void setSearchCount(int searchCount) {
        this.searchCount = searchCount;
    }

    /**
     * Updates this instance based on the given {@link UserSettingDetail}.
     * @param settingDetail The user setting detail. not <code>null</code>.
     */
    public void updateByDetail(UserSettingDetail settingDetail) {
        this.locale = settingDetail.getLocale();
        this.themeId = settingDetail.getThemeId();
        this.showNowPlayingEnabled = settingDetail.getShowNowPlayingEnabled();
        this.showArtistInfoEnabled = settingDetail.getShowArtistInfoEnabled();
        this.finalVersionNotificationEnabled = settingDetail.getFinalVersionNotificationEnabled();
        this.betaVersionNotificationEnabled = settingDetail.getBetaVersionNotificationEnabled();
        this.songNotificationEnabled = settingDetail.getSongNotificationEnabled();
        this.keyboardShortcutsEnabled = settingDetail.getKeyboardShortcutsEnabled();
        this.autoHidePlayQueue = settingDetail.getAutoHidePlayQueue();
        this.showSideBar = settingDetail.getShowSideBar();
        this.viewAsList = settingDetail.getViewAsList();
        this.queueFollowingSongs = settingDetail.getQueueFollowingSongs();
        this.defaultAlbumList = settingDetail.getDefaultAlbumList();
        this.mainVisibility = settingDetail.getMainVisibility();
        this.playlistVisibility = settingDetail.getPlaylistVisibility();
        this.playqueueVisibility = settingDetail.getPlayqueueVisibility();
        this.lastFmEnabled = settingDetail.getLastFmEnabled();
        this.listenBrainzEnabled = settingDetail.getListenBrainzEnabled();
        this.listenBrainzUrl = settingDetail.getListenBrainzUrl();
        this.podcastIndexEnabled = settingDetail.getPodcastIndexEnabled();
        this.podcastIndexUrl = settingDetail.getPodcastIndexUrl();
        this.transcodeScheme = settingDetail.getTranscodeScheme();
        this.selectedMusicFolderId = settingDetail.getSelectedMusicFolderId();
        this.partyModeEnabled = settingDetail.getPartyModeEnabled();
        this.nowPlayingAllowed = settingDetail.getNowPlayingAllowed();
        this.avatarScheme = settingDetail.getAvatarScheme();
        this.systemAvatarId = settingDetail.getSystemAvatarId();
        this.changed = Instant.now();
        this.paginationSizeFiles = settingDetail.getPaginationSizeFiles();
        this.paginationSizeFolders = settingDetail.getPaginationSizeFolders();
        this.paginationSizePlaylist = settingDetail.getPaginationSizePlaylist();
        this.paginationSizePlayqueue = settingDetail.getPaginationSizePlayqueue();
        this.paginationSizeBookmarks = settingDetail.getPaginationSizeBookmarks();
        this.autoBookmark = settingDetail.getAutoBookmark();
        this.videoBookmarkFrequency = settingDetail.getVideoBookmarkFrequency();
        this.audioBookmarkFrequency = settingDetail.getAudioBookmarkFrequency();
        this.searchCount = settingDetail.getSearchCount();
    }
}
