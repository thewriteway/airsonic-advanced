package org.airsonic.player.ajax;

import org.airsonic.player.service.PersonalSettingsService;
import org.airsonic.player.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Arrays;

@Controller
@MessageMapping("/settings")
public class UserSettingsWSController {

    @Autowired
    private PersonalSettingsService personalSettingsService;

    @Autowired
    private SettingsService settingsService;

    @MessageMapping("/sidebar")
    @SendToUser
    public boolean setShowSideBar(Principal p, boolean show) {
        personalSettingsService.updateShowSideBarStatus(p.getName(), show);
        return show;
    }

    @MessageMapping("/viewAsList")
    @SendToUser
    public boolean setViewAsList(Principal p, boolean viewAsList) {
        personalSettingsService.updateViewAsListStatus(p.getName(), viewAsList);
        return viewAsList;
    }

    @MessageMapping("/theme")
    @SendToUser
    public String setTheme(Principal p, String themeId) {
        if (Arrays.stream(settingsService.getAvailableThemes()).noneMatch(theme -> theme.getId().equals(themeId))) {
            throw new IllegalArgumentException("Unknown theme: " + themeId);
        }
        personalSettingsService.updateTheme(p.getName(), themeId);
        return themeId;
    }

}
