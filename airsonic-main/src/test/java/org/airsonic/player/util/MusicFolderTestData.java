package org.airsonic.player.util;

import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class MusicFolderTestData {

    private static String baseResources = "/MEDIAS/";

    public static Path resolveBaseMediaPath() {
        try {
            return Paths.get(MusicFolderTestData.class.getResource(baseResources).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Could not find base resources for tests", e);
        }
    }

    public static Path resolveMusicFolderPath() {
        return resolveBaseMediaPath().resolve("Music");
    }

    public static Path resolveMusic2FolderPath() {
        return resolveBaseMediaPath().resolve("Music2");
    }

    public static Path resolveMusic3FolderPath() {
        return resolveBaseMediaPath().resolve("Music3");
    }

    public static Path resolveMusic4FolderPath() {
        return resolveBaseMediaPath().resolve("Music4");
    }

    public static Path resolveMusicCueFolderPath() {
        return resolveBaseMediaPath().resolve("cue");
    }

    public static Path resolveMusicDisableCueFolderPath() {
        return resolveBaseMediaPath().resolve("disableCue");
    }

    public static Path resolveMusicInvalidCue2FolderPath() {
        return resolveBaseMediaPath().resolve("invalidCue2");
    }

    public static Path resolveMusicInvalidCue3FolderPath() {
        return resolveBaseMediaPath().resolve("invalidCue3");
    }

    public static Path resolveMusicMpcFolderPath() {
        return resolveBaseMediaPath().resolve("mpc");
    }

    public static Path resolveM4bAudioPath() {
        return resolveBaseMediaPath().resolve("m4baudiobook");
    }

    public static Path resolveMusicLoopFolderPath() {
        return resolveBaseMediaPath().resolve("loop");
    }

    public static Path resolveMusicCueAndFlacFolderPath() {
        return resolveBaseMediaPath().resolve("cueAndFlac");
    }

    public static Path resolveMusicFlacWithCueFolderPath() {
        return resolveBaseMediaPath().resolve("flacWithCue");
    }


    public static List<MusicFolder> getTestMusicFolders() {
        List<MusicFolder> liste = new ArrayList<>();
        Path musicDir = resolveMusicFolderPath();
        MusicFolder musicFolder = new MusicFolder(musicDir, "MusicTest", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        liste.add(musicFolder);

        Path music2Dir = resolveMusic2FolderPath();
        MusicFolder musicFolder2 = new MusicFolder(music2Dir, "MusicTest2", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        liste.add(musicFolder2);
        return liste;
    }
}
