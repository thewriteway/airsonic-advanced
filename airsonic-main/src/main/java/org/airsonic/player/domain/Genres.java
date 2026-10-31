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

 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.domain;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Represents a list of genres.
 *
 * @author Sindre Mehus
 * @version $Revision: 1.2 $ $Date: 2005/12/25 13:48:46 $
 */
public class Genres {

    private final Map<String, Genre> genres = new ConcurrentHashMap<>();

    // genre names can be ([genre] --> [counted as])
    // - abc --> ['abc']
    // - abc; --> ['abc;']
    // - abc;xyz --> ['abc', 'xyz']
    // - abc; xyz --> ['abc', 'xyz']
    // A tag holding several genres only counts towards its parts, not as a genre of its own.

    public void incrementAlbumCount(String genreName, String separators) {
        split(genreName, separators).forEach(s -> genres.computeIfAbsent(s, k -> new Genre(k)).incrementAlbumCount());
    }

    public void incrementSongCount(String genreName, String separators) {
        split(genreName, separators).forEach(s -> genres.computeIfAbsent(s, k -> new Genre(k)).incrementSongCount());
    }

    private static Stream<String> split(String genreName, String separators) {
        String[] splitGenres = StringUtils.split(genreName, separators);
        if (splitGenres.length <= 1) { // otherwise it's the same genre as the original
            return Stream.of(genreName);
        }
        return Stream.of(splitGenres)
                .map(StringUtils::trim)
                .filter(StringUtils::isNotBlank)
                .distinct();
    }

    public List<Genre> getGenres() {
        return new ArrayList<>(genres.values());
    }
}
