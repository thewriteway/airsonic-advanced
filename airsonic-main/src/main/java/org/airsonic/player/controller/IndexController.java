package org.airsonic.player.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.PersonalSettingsService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/index")
public class IndexController {

    @Autowired
    private SecurityService securityService;
    @Autowired
    private SettingsService settingsService;
    @Autowired
    private PersonalSettingsService personalSettingsService;

    @GetMapping
    public ModelAndView index(HttpServletRequest request) {
        UserSettings userSettings = personalSettingsService.getUserSettings(securityService.getCurrentUsername(request));

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


        return new ModelAndView("index", "model", map);
    }
}
