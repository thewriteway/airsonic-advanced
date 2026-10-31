package org.airsonic.player.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class GenresTest {

    @Test
    public void singleGenreIsCountedAsIs() {
        Genres genres = new Genres();
        genres.incrementAlbumCount("Rock", ";");
        genres.incrementSongCount("Rock", ";");

        Map<String, Genre> byName = byName(genres);
        assertThat(byName).containsOnlyKeys("Rock");
        assertThat(byName.get("Rock").getAlbumCount()).isEqualTo(1);
        assertThat(byName.get("Rock").getSongCount()).isEqualTo(1);
    }

    @Test
    public void combinedTagCountsOnlyTowardsItsParts() {
        Genres genres = new Genres();
        genres.incrementAlbumCount("Rock;Pop", ";");
        genres.incrementSongCount("Jazz; Blues", ";");
        genres.incrementSongCount("Jazz", ";");

        Map<String, Genre> byName = byName(genres);
        assertThat(byName).containsOnlyKeys("Rock", "Pop", "Jazz", "Blues");
        assertThat(byName.get("Rock").getAlbumCount()).isEqualTo(1);
        assertThat(byName.get("Pop").getAlbumCount()).isEqualTo(1);
        assertThat(byName.get("Jazz").getSongCount()).isEqualTo(2);
        assertThat(byName.get("Blues").getSongCount()).isEqualTo(1);
    }

    @Test
    public void blankAndRepeatedPartsAreIgnored() {
        Genres genres = new Genres();
        genres.incrementSongCount("Rock;; Pop;Rock", ";");

        Map<String, Genre> byName = byName(genres);
        assertThat(byName).containsOnlyKeys("Rock", "Pop");
        assertThat(byName.get("Rock").getSongCount()).isEqualTo(1);
    }

    @Test
    public void tagIsKeptWithoutSeparators() {
        Genres genres = new Genres();
        genres.incrementSongCount("Rock;Pop", "");

        assertThat(byName(genres)).containsOnlyKeys("Rock;Pop");
    }

    private static Map<String, Genre> byName(Genres genres) {
        return genres.getGenres().stream().collect(Collectors.toMap(Genre::getName, g -> g));
    }
}
