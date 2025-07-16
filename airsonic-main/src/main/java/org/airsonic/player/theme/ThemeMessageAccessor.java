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

import org.springframework.context.MessageSource;

import java.util.Locale;

/**
 * Accessor for theme messages.
 */
public class ThemeMessageAccessor {
    private final MessageSource messageSource;
    private final Locale locale;

    public ThemeMessageAccessor(MessageSource messageSource, Locale locale) {
        this.messageSource = messageSource;
        this.locale = locale;
    }

    public String get(String code) {
        try {
            return messageSource.getMessage(code, null, locale);
        } catch (Exception e) {
            // Log the error or handle it as needed
            return code; // Fallback to the code itself if message not found
        }
    }
}