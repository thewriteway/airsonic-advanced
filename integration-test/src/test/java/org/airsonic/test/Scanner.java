package org.airsonic.test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import org.apache.commons.io.FileUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.subsonic.restapi.Child;
import org.subsonic.restapi.MusicFolder;
import org.subsonic.restapi.Response;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class Scanner {
    public static final String SERVER = System.getProperty("DockerTestingHost", "http://localhost:4040");
    public static final String DEFAULT_MUSIC = System.getProperty("DockerTestingDefaultMusicFolder", "/tmp/music");
    public static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.WRAP_ROOT_VALUE)
            .enable(DeserializationFeature.UNWRAP_ROOT_VALUE)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .build();
    public static final RestTemplate rest = new RestTemplate();

    /**
     * Fetches the response as a string and parses it with our own mapper. Binding via the
     * RestTemplate message converters is unreliable here: Spring 7 prefers a Jackson 3
     * converter that lacks our root-unwrapping config and silently returns an empty object.
     */
    private static SubsonicResponse getForSubsonicResponse(String url) {
        String body = rest.getForObject(url, String.class);
        try {
            return MAPPER.readValue(body, SubsonicResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse response: " + body, e);
        }
    }

    public static UriComponentsBuilder addRestParameters(UriComponentsBuilder builder) {
        // Salted-token auth (s/t params) requires the server to decode the stored password,
        // which is impossible now that credentials are stored with bcrypt by default.
        return builder.queryParam("c", "inttest")
                .queryParam("v", "1.15.0")
                .queryParam("u", "admin")
                .queryParam("p", "admin");
    }

    public static void doScan() throws Exception {
        assertFalse(isScanning());

        String startScan = rest.getForObject(
                addRestParameters(UriComponentsBuilder.fromUriString(SERVER + "/rest/startScan")).toUriString(),
                String.class);

        System.out.println(startScan);

        Long waitTime = 30000L;
        Long sleepTime = 1000L;
        while (waitTime > 0 && isScanning()) {
            waitTime -= sleepTime;
            Thread.sleep(sleepTime);
        }

        assertFalse(isScanning());
    }

    private static boolean isScanning() {
        return getForSubsonicResponse(
                addRestParameters(UriComponentsBuilder.fromUriString(SERVER + "/rest/getScanStatus"))
                        .queryParam("f", "json").toUriString())
            .getScanStatus().isScanning();
    }

    public static void uploadToDefaultMusicFolder(Path directoryPath, String relativePath) {
        Path dest = Paths.get(DEFAULT_MUSIC, relativePath);
        try {
            FileUtils.copyDirectory(directoryPath.toFile(), dest.toFile(), false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Child> getMediaFilesInMusicFolder() {
        List<MusicFolder> musicFolder = getForSubsonicResponse(
                addRestParameters(UriComponentsBuilder.fromUriString(SERVER + "/rest/getMusicFolders"))
                        .queryParam("f", "json")
                        .toUriString())
            .getMusicFolders().getMusicFolder();

        MusicFolder music = musicFolder.stream().filter(folder -> Objects.equals(folder.getName(), "Music")).findFirst()
                .orElseThrow(() -> new RuntimeException("No top level folder named Music"));
        return getMediaFiles(music.getId());
    }

    private static List<Child> getMediaFiles(int folderId) {
        return getForSubsonicResponse(
                addRestParameters(UriComponentsBuilder.fromUriString(SERVER + "/rest/getIndexes"))
                        .queryParam("f", "json")
                        .queryParam("musicFolderId", folderId)
                        .toUriString())
            .getIndexes().getChild();
    }

    public static byte[] getMediaFileData(String mediaFileId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(MediaType.parseMediaTypes("audio/webm,audio/ogg,audio/wav,audio/*;"));
        ResponseEntity<byte[]> response = rest.exchange(
                addRestParameters(UriComponentsBuilder.fromUriString(SERVER + "/rest/stream"))
                        .queryParam("id", mediaFileId)
                        .toUriString(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class);

        assertThat(response.getBody()).hasSize((int) response.getHeaders().getContentLength());
        return response.getBody();
    }

    @JsonRootName(value = "subsonic-response")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubsonicResponse extends Response {
    }
}
