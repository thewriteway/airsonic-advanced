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
 */
package org.airsonic.player.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A folder of several videos holds the subtitles of each; a video only gets those named after it.
 */
public class CaptionsControllerNamingTest {

    @Test
    public void subtitlesNamedAfterTheVideoBelongToIt() {
        assertThat(CaptionsController.isNamedAfter(Paths.get("movies", "Movie A.srt"), "Movie A")).isTrue();
        assertThat(CaptionsController.isNamedAfter(Paths.get("movies", "Movie A.en.srt"), "Movie A")).isTrue();
        assertThat(CaptionsController.isNamedAfter(Paths.get("movies", "Subs", "movie a.fr.forced.vtt"), "Movie A")).isTrue();
    }

    @Test
    public void otherSubtitlesDoNotBelongToIt() {
        assertThat(CaptionsController.isNamedAfter(Paths.get("movies", "Movie B.srt"), "Movie A")).isFalse();
        // a longer title that starts with the same words is another video
        assertThat(CaptionsController.isNamedAfter(Paths.get("movies", "Movie A 2.srt"), "Movie A")).isFalse();
        assertThat(CaptionsController.isNamedAfter(Paths.get("movies", "English.srt"), "Movie A")).isFalse();
    }
}
