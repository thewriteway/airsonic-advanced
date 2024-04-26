package org.airsonic.player.controller;

import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.PersonalSettingsService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping({"/index", "/index.view"})
public class IndexController {

    @Autowired
    private SecurityService securityService;
    @Autowired
    private SettingsService settingsService;
    @Autowired
    private PersonalSettingsService personalSettingsService;
    @Autowired
    private PlayerService playerService;

    @GetMapping
    public ModelAndView index(HttpServletRequest request) {
        User user = securityService.getCurrentUser(request);
        UserSettings userSettings = personalSettingsService.getUserSettings(user.getUsername());

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("showRight", userSettings.getShowNowPlayingEnabled());
        map.put("autoHidePlayQueue", userSettings.getAutoHidePlayQueue());
        map.put("keyboardShortcutsEnabled", userSettings.getKeyboardShortcutsEnabled());
        map.put("showSideBar", userSettings.getShowSideBar());
        map.put("brand", settingsService.getBrand());

        //Add settings from PlayQueueController as well
        map.put("visibility", userSettings.getPlayqueueVisibility());
        map.put("partyMode", userSettings.getPartyModeEnabled());
        map.put("notify", userSettings.getSongNotificationEnabled());
        map.put("autoHide", userSettings.getAutoHidePlayQueue());
        map.put("initialPaginationSize", userSettings.getPaginationSizePlayqueue());
        map.put("autoBookmark", userSettings.getAutoBookmark());
        map.put("audioBookmarkFrequency", userSettings.getAudioBookmarkFrequency());

        // add player info
        map.put("user", user);
        map.put("players", playerService.getPlayersForUserAndClientId(user.getUsername(), null));

        return new ModelAndView("index", "model", map);
    }
}
