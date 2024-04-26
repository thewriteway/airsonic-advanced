package org.airsonic.player.controller;

import org.airsonic.player.service.PersonalSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;

import java.security.Principal;
import java.util.Map;

@Controller
@RequestMapping({"/bookmarks", "/bookmarks.view"})
public class BookmarksController {
    @Autowired
    private PersonalSettingsService personalSettingsService;

    @GetMapping
    public ModelAndView doGet(Principal user, HttpServletRequest request) {
        return new ModelAndView("bookmarks", "model",
                Map.of("initialPaginationSize",
                        personalSettingsService.getUserSettings(user.getName()).getPaginationSizeBookmarks()));
    }
}
