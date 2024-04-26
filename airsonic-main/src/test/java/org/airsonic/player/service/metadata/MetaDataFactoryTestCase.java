package org.airsonic.player.service.metadata;

import org.airsonic.player.service.SettingsService;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Path;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

@SpringBootTest
@EnableConfigurationProperties
public class MetaDataFactoryTestCase {

    @TempDir
    private static Path airsonicHome;

    @TempDir
    private static Path tempDir;

    @BeforeAll
    public static void setupAll() throws IOException {
        System.setProperty("airsonic.home", airsonicHome.toString());
        someMp3 = tempDir.resolve("some.mp3");
        someFlv = tempDir.resolve("some.flv");
        someJunk = tempDir.resolve("some.junk");
        someMpc = tempDir.resolve("some.mpc");
        someMpPlus = tempDir.resolve("some.mp+");
        FileUtils.touch(someMp3.toFile());
        FileUtils.touch(someFlv.toFile());
        FileUtils.touch(someJunk.toFile());
        FileUtils.touch(someMpc.toFile());
        FileUtils.touch(someMpPlus.toFile());
    }

    private static Path someMp3;
    private static Path someFlv;
    private static Path someJunk;
    private static Path someMpc;
    private static Path someMpPlus;

    @Autowired
    private MetaDataParserFactory metaDataParserFactory;

    @Autowired
    private SettingsService settingsService;

    @Test
    public void testorder() {
        MetaDataParser parser;

        settingsService.setVideoFileTypes("flv");
        settingsService.setMusicFileTypes("mp3 mpc mp+");

        parser = metaDataParserFactory.getParser(someMp3);
        assertThat(parser, instanceOf(JaudiotaggerParser.class));

        parser = metaDataParserFactory.getParser(someFlv);
        assertThat(parser, instanceOf(FFmpegParser.class));

        parser = metaDataParserFactory.getParser(someJunk);
        assertThat(parser, instanceOf(FFmpegParser.class));

        parser = metaDataParserFactory.getParser(someMpc);
        assertThat(parser, instanceOf(FFmpegParser.class));

        parser = metaDataParserFactory.getParser(someMpPlus);
        assertThat(parser, instanceOf(FFmpegParser.class));
    }

}
