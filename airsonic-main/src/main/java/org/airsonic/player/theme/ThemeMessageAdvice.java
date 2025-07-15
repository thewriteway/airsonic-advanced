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
package org.airsonic.player.theme;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Locale;

@ControllerAdvice
public class ThemeMessageAdvice {

    @Autowired
    private ThemeMessageSourceFactory themeMessageSourceFactory;

    @Autowired
    private ThemeService themeService;

    @ModelAttribute("themes")
    public ThemeMessageAccessor exposeThemeMessages(HttpServletRequest request, Locale locale) {
        String themeId = themeService.getCurrentTheme(request);
        MessageSource messageSource = themeMessageSourceFactory.createThemeMessageSource(themeId);
        return new ThemeMessageAccessor(messageSource, locale);
    }
}
