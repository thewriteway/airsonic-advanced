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

 Copyright 2023 (C) Y.Tory
 */
package org.airsonic.player.service;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.Player;
import org.airsonic.player.repository.AlbumRepository;
import org.airsonic.player.repository.MediaFileRepository;
import org.airsonic.player.repository.MusicFileInfoRepository;
import org.airsonic.player.service.cache.MediaFileCache;
import org.airsonic.player.service.metadata.MetaDataParserFactory;
import org.airsonic.player.util.FileUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.digitalmediaserver.cuelib.CueSheet;
import org.digitalmediaserver.cuelib.FileData;
import org.digitalmediaserver.cuelib.Index;
import org.digitalmediaserver.cuelib.Position;
import org.digitalmediaserver.cuelib.TrackData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MediaFileServiceTest {

    @Mock
    private MetaDataParserFactory metaDataParserFactory;
    @Mock
    private MediaFileRepository mediaFileRepository;
    @Mock
    private CoverArtService coverArtService;
    @Mock
    private MediaFileCache mediaFileCache;
    @Mock
    private MediaFolderService mediaFolderService;
    @Mock
    private SettingsService settingsService;
    @Mock
    private MusicFileInfoRepository musicFileInfoRepository;
    @Mock
    private AlbumRepository albumRepository;

    @InjectMocks
    private MediaFileService mediaFileService;


    @Mock
    private MusicFolder mockedFolder;

    @Mock
    private MediaFile mockedMediaFile;

    private final Path CLASS_PATH = Paths.get("src", "test", "resources");

    @BeforeEach
    public void setUp() {
        lenient().when(mockedFolder.getPath()).thenReturn(CLASS_PATH.resolve("MEDIAS"));
    }

    @Test
    public void createIndexedTracksFailedByNoIndexTracksReturnEmptyList() {
        // prepare test data
        MediaFile base = new MediaFile();
        base.setIndexPath("invalidCue/airsonic-test.cue");
        base.setPath("valid/airsonic-test.wav");
        base.setMediaType(MediaType.MUSIC);
        base.setFormat("wav");
        base.setId(10);
        base.setFolder(mockedFolder);

        when(mediaFileRepository.findByFolderAndPath(any(), eq("valid/airsonic-test.wav"))).thenReturn(List.of(mockedMediaFile));
        when(mockedMediaFile.isIndexedTrack()).thenReturn(true);
        when(mediaFileRepository.existsById(any())).thenReturn(true);

        // execute
        List<MediaFile> actual = ReflectionTestUtils.invokeMethod(mediaFileService, "createIndexedTracks", base);

        // check empty list is returned
        assertTrue(actual.isEmpty());
        // verify updateMedia does not called
        verify(mediaFileRepository).findByFolderAndPath(any(), eq("valid/airsonic-test.wav"));
        verify(mediaFileRepository).save(base);
        verify(coverArtService).persistIfNeeded(eq(base));
    }

    @Test
    public void checkLastModifiedRestoresRemountedFile() {
        // a file that was marked non-present (e.g. its mount dropped) and is back with the same mtime
        MediaFile file = new MediaFile();
        file.setId(20);
        file.setPath("piano.mp3");
        file.setFolder(mockedFolder);
        file.setMediaType(MediaType.MUSIC);
        file.setChanged(FileUtil.lastModified(file.getFullPath()));
        file.setPresent(false);

        when(mediaFileRepository.existsById(20)).thenReturn(true);

        MediaFile actual = mediaFileService.checkLastModified(file);

        assertTrue(actual.isPresent());
        verify(mediaFileRepository).save(file);
    }

    @Test
    public void checkLastModifiedDoesNotRewriteMissingFile() {
        MediaFile file = new MediaFile();
        file.setId(21);
        file.setPath("missing.mp3");
        file.setFolder(mockedFolder);
        file.setMediaType(MediaType.MUSIC);
        file.setPresent(false);

        MediaFile actual = mediaFileService.checkLastModified(file);

        assertFalse(actual.isPresent());
        verify(mediaFileRepository, never()).save(any());
    }

    @Test
    public void getSongsForAlbumHidesCueSourceFile() {
        MediaFile source = new MediaFile();
        source.setPath("album/disc.flac");
        source.setIndexPath("album/disc.cue");
        source.setMediaType(MediaType.MUSIC);

        MediaFile track = new MediaFile();
        track.setPath("album/disc.flac");
        track.setStartPosition(0.0);
        track.setMediaType(MediaType.MUSIC);

        when(mediaFileRepository.findByAlbumArtistAndAlbumNameAndMediaTypeInAndPresentTrue(eq("Artist"), eq("Album"), any(), any(Sort.class)))
                .thenReturn(List.of(source, track));
        when(settingsService.getHideVirtualTracks()).thenReturn(false);

        assertEquals(List.of(track), mediaFileService.getSongsForAlbum("Artist", "Album"));

        when(settingsService.getHideVirtualTracks()).thenReturn(true);

        assertEquals(List.of(source), mediaFileService.getSongsForAlbum("Artist", "Album"));
    }

    @Test
    public void createIndexedTracksUsesOnlyTracksFromMatchingCueFileData() {
        CueSheet cueSheet = new CueSheet();
        cueSheet.setTitle("Album");
        cueSheet.setPerformer("Album Artist");

        FileData otherFileData = new FileData(cueSheet, "airsonic-test.wav", "WAVE");
        otherFileData.getTrackData().add(createTrack(otherFileData, 1, "Wrong File Track", 0, 0, 0));
        cueSheet.getFileData().add(otherFileData);

        FileData matchingFileData = new FileData(cueSheet, "piano.mp3", "MP3");
        matchingFileData.getTrackData().add(createTrack(matchingFileData, 2, "Matching Track 1", 0, 0, 0));
        matchingFileData.getTrackData().add(createTrack(matchingFileData, 3, "Matching Track 2", 0, 10, 37));
        cueSheet.getFileData().add(matchingFileData);

        MediaFile base = createCueBase();

        when(mediaFileRepository.findByFolderAndPath(any(), eq("piano.mp3"))).thenReturn(List.of());
        when(mediaFileRepository.findByPathAndFolderAndStartPosition(any(), any(), any())).thenReturn(Optional.empty());
        when(musicFileInfoRepository.findByPath(any())).thenReturn(Optional.empty());

        List<MediaFile> actual = ReflectionTestUtils.invokeMethod(mediaFileService, "createIndexedTracks", base, cueSheet);

        assertEquals(2, actual.size());
        assertEquals("Matching Track 1", actual.get(0).getTitle());
        assertEquals("Matching Track 2", actual.get(1).getTitle());
        assertEquals("piano.mp3", actual.get(1).getPath());
        assertEquals(0.0d, actual.get(0).getStartPosition(), 0.0d);
        // 37 frames = 37/75 s
        assertEquals(10.493d, actual.get(1).getStartPosition(), 0.001d);
        assertEquals(10.493d, actual.get(0).getDuration(), 0.001d);
        assertEquals(19.507d, actual.get(1).getDuration(), 0.001d);
    }

    @Test
    public void createIndexedTracksRefreshesExistingCueTrack() {
        CueSheet cueSheet = new CueSheet();
        cueSheet.setTitle("Album");
        cueSheet.setPerformer("Album Artist");

        FileData fileData = new FileData(cueSheet, "piano.mp3", "MP3");
        fileData.getTrackData().add(createTrack(fileData, 2, "Fresh Title", 0, 0, 0));
        cueSheet.getFileData().add(fileData);

        MediaFile staleTrack = createStoredTrack(42, 0.0);

        when(mediaFileRepository.findByFolderAndPath(any(), eq("piano.mp3"))).thenReturn(List.of(staleTrack));
        when(mediaFileRepository.existsById(eq(42))).thenReturn(true);

        List<MediaFile> actual = ReflectionTestUtils.invokeMethod(mediaFileService, "createIndexedTracks", createCueBase(), cueSheet);

        assertEquals(List.of(staleTrack), actual);
        assertEquals("Fresh Title", staleTrack.getTitle());
        assertEquals(Integer.valueOf(2), staleTrack.getTrackNumber());
        assertEquals(30.0d, staleTrack.getDuration(), 0.001d);
        assertEquals(7, staleTrack.getPlayCount());
        verify(mediaFileRepository).save(staleTrack);
        verify(mediaFileRepository, never()).delete(any());
    }

    @Test
    public void createIndexedTracksKeepsTrackStoredWithoutFrames() {
        CueSheet cueSheet = new CueSheet();
        FileData fileData = new FileData(cueSheet, "piano.mp3", "MP3");
        fileData.getTrackData().add(createTrack(fileData, 1, "Intro", 0, 0, 0));
        fileData.getTrackData().add(createTrack(fileData, 2, "Song", 0, 10, 37));
        cueSheet.getFileData().add(fileData);

        // stored before the frames were taken into account: start 10.0 instead of 10.493
        MediaFile first = createStoredTrack(41, 0.0);
        MediaFile second = createStoredTrack(42, 10.0);

        when(mediaFileRepository.findByFolderAndPath(any(), eq("piano.mp3"))).thenReturn(List.of(first, second));
        when(mediaFileRepository.existsById(any())).thenReturn(true);

        List<MediaFile> actual = ReflectionTestUtils.invokeMethod(mediaFileService, "createIndexedTracks", createCueBase(), cueSheet);

        assertEquals(List.of(first, second), actual);
        assertEquals(10.493d, second.getStartPosition(), 0.001d);
        verify(mediaFileRepository, never()).delete(any());
    }

    @Test
    public void createIndexedTracksRejectsInvalidTrackNumber() {
        CueSheet cueSheet = new CueSheet();
        FileData fileData = new FileData(cueSheet, "piano.mp3", "MP3");
        fileData.getTrackData().add(createTrack(fileData, -1, "Broken", 0, 0, 0));
        cueSheet.getFileData().add(fileData);

        MediaFile base = createCueBase();
        base.setId(10);

        when(mediaFileRepository.findByFolderAndPath(any(), eq("piano.mp3"))).thenReturn(List.of());
        when(mediaFileRepository.existsById(10)).thenReturn(true);

        List<MediaFile> actual = ReflectionTestUtils.invokeMethod(mediaFileService, "createIndexedTracks", base, cueSheet);

        assertTrue(actual.isEmpty());
        assertFalse(base.hasIndex());
    }

    @Test
    public void createIndexedTracksNullBaseDurationSkipsBlockWithoutNpe() {
        // The eager unbox of base.getDuration() aborted the whole FILE block with an NPE when the
        // base file's duration was null (unparsed). The 0.0-sentinel guard removes the NPE; the
        // existing guard (lastTrackStart >= wholeFileLength) then fires against the 0.0
        // length and the block is skipped gracefully — empty result, index path cleared — rather than
        // crashing the scan. The tracks reappear on a later scan once the base duration parses.
        CueSheet cueSheet = new CueSheet();
        cueSheet.setTitle("Album");
        cueSheet.setPerformer("Album Artist");

        FileData fileData = new FileData(cueSheet, "piano.mp3", "MP3");
        fileData.getTrackData().add(createTrack(fileData, 1, "Sole Track", 0, 5, 0));
        cueSheet.getFileData().add(fileData);

        MediaFile base = createCueBase();
        base.setDuration(null);
        when(mediaFileRepository.findByFolderAndPath(any(), eq("piano.mp3"))).thenReturn(List.of());

        List<MediaFile> actual = assertDoesNotThrow(
                () -> ReflectionTestUtils.<List<MediaFile>>invokeMethod(mediaFileService, "createIndexedTracks", base, cueSheet));

        // block skipped gracefully: no tracks, and the base's index path is cleared by that guard
        assertTrue(actual.isEmpty());
        assertNull(base.getIndexPath());
    }

    @Test
    public void createIndexedTracksNonNullBaseDurationStillMaterialises() {
        // Sanity counterpart to the null-base case: with a known base duration the lastTrackStart guard does
        // NOT fire, so the block materialises normally with a non-null computed duration. Proves the
        // 0.0-sentinel guard only degrades the degenerate null case and leaves the happy path intact.
        CueSheet cueSheet = new CueSheet();
        cueSheet.setTitle("Album");
        cueSheet.setPerformer("Album Artist");

        FileData fileData = new FileData(cueSheet, "piano.mp3", "MP3");
        fileData.getTrackData().add(createTrack(fileData, 1, "Sole Track", 0, 5, 0));
        cueSheet.getFileData().add(fileData);

        MediaFile base = createCueBase();
        when(mediaFileRepository.findByFolderAndPath(any(), eq("piano.mp3"))).thenReturn(List.of());
        when(mediaFileRepository.findByPathAndFolderAndStartPosition(any(), any(), any())).thenReturn(Optional.empty());
        when(musicFileInfoRepository.findByPath(any())).thenReturn(Optional.empty());

        List<MediaFile> actual = ReflectionTestUtils.invokeMethod(mediaFileService, "createIndexedTracks", base, cueSheet);

        assertEquals(1, actual.size());
        assertNotNull(actual.get(0).getDuration());
        // file length (30.0) - start (5.0) = 25.0
        assertEquals(25.0d, actual.get(0).getDuration(), 0.001d);
    }

    private MediaFile createCueBase() {
        MediaFile base = new MediaFile();
        base.setIndexPath("cue/airsonic-test.cue");
        base.setPath("piano.mp3");
        base.setMediaType(MediaType.MUSIC);
        base.setFormat("mp3");
        base.setDuration(30.0);
        base.setFolder(mockedFolder);
        return base;
    }

    private MediaFile createStoredTrack(int id, double startPosition) {
        MediaFile track = new MediaFile();
        track.setId(id);
        track.setPath("piano.mp3");
        track.setFolder(mockedFolder);
        track.setStartPosition(startPosition);
        track.setDuration(10.0);
        track.setTitle("Stale Title");
        track.setTrackNumber(5);
        track.setPlayCount(7);
        return track;
    }

    private TrackData createTrack(FileData fileData, int number, String title, int minutes, int seconds, int frames) {
        TrackData trackData = new TrackData(fileData, number, "AUDIO");
        trackData.setTitle(title);
        trackData.getIndices().add(new Index(1, new Position(minutes, seconds, frames)));
        return trackData;
    }

    @Test
    public void incrementPlayCountNullDurationFallsBackToMinimumReplayWindow() {
        // The replay-debounce threshold is max(1.0, duration / 2); an unknown-length file must
        // fall back to the 1-second floor instead of NPE-ing the whole increment.
        MediaFile file = new MediaFile();
        file.setId(30);

        Player player = new Player();
        player.setId(1);

        // simulate an earlier counted play of the same file, outside any debounce window
        @SuppressWarnings("unchecked")
        Map<Integer, Pair<Integer, Instant>> lastPlayed =
                (Map<Integer, Pair<Integer, Instant>>) ReflectionTestUtils.getField(mediaFileService, "lastPlayed");
        lastPlayed.put(player.getId(), Pair.of(file.getId(), Instant.now().minusSeconds(10)));

        when(mediaFileRepository.existsById(30)).thenReturn(true);

        assertDoesNotThrow(() -> mediaFileService.incrementPlayCount(player, file));
        assertEquals(1, file.getPlayCount());
        assertNotNull(file.getLastPlayed());

        // a repeat play inside the 1-second floor is still debounced
        mediaFileService.incrementPlayCount(player, file);
        assertEquals(1, file.getPlayCount());
    }

    @Test
    public void refreshMediaFileStampsCurrentVersionOnReparsedRow() {
        // A VERSION bump flags every older row for one re-parse, and the re-parse must stamp the
        // new version onto the row it saves; otherwise every scan is a full re-parse.
        MediaFile mediaFile = new MediaFile();
        mediaFile.setId(7);
        mediaFile.setPath("piano.mp3");
        mediaFile.setFolder(mockedFolder);
        // a row written by an older release: stored version below current
        ReflectionTestUtils.setField(mediaFile, "version", MediaFile.VERSION - 1);

        when(mockedFolder.getId()).thenReturn(1);
        when(mockedFolder.getType()).thenReturn(MusicFolder.Type.MEDIA);
        when(mediaFolderService.getMusicFolderForFile(any(), eq(true), eq(true))).thenReturn(Optional.empty());
        when(metaDataParserFactory.getParser(any())).thenReturn(null);
        when(settingsService.getVideoFileTypesSet()).thenReturn(Set.of());
        when(mediaFileRepository.existsById(any())).thenReturn(true);

        mediaFileService.refreshMediaFile(mediaFile);

        ArgumentCaptor<MediaFile> saved = ArgumentCaptor.forClass(MediaFile.class);
        verify(mediaFileRepository).save(saved.capture());
        assertEquals(MediaFile.VERSION, saved.getValue().getVersion());
    }

    @Test
    public void needsUpdateSkipsRowAtCurrentVersionWithUnchangedFile() {
        MediaFile mediaFile = new MediaFile(); // entity default: version == MediaFile.VERSION
        mediaFile.setPath("piano.mp3");
        mediaFile.setFolder(mockedFolder);
        mediaFile.setPresent(true);
        mediaFile.setChanged(FileUtil.lastModified(mediaFile.getFullPath()));

        when(settingsService.getFullScan()).thenReturn(false);

        Boolean result = ReflectionTestUtils.invokeMethod(mediaFileService, "needsUpdate", mediaFile, false);
        assertEquals(Boolean.FALSE, result);
    }

    @Test
    public void needsUpdateReparsesRowBelowCurrentVersion() {
        MediaFile mediaFile = new MediaFile();
        mediaFile.setPath("piano.mp3");
        mediaFile.setFolder(mockedFolder);
        mediaFile.setPresent(true);
        mediaFile.setChanged(FileUtil.lastModified(mediaFile.getFullPath()));
        ReflectionTestUtils.setField(mediaFile, "version", MediaFile.VERSION - 1);

        // no getFullScan stub: version < VERSION short-circuits the OR before the setting is read
        Boolean result = ReflectionTestUtils.invokeMethod(mediaFileService, "needsUpdate", mediaFile, false);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    public void getRandomSongsForParentRunsSingleBoundedQueryWithoutChildWalk() {
        // one subtree-scoped query bounded to count rows: no walk of the directory tree loading
        // every descendant, and no checkLastModified-driven refresh write
        MediaFile parent = new MediaFile();
        parent.setMediaType(MediaType.DIRECTORY);
        parent.setPath("Artist");
        parent.setFolder(mockedFolder);

        MediaFile song = new MediaFile();
        song.setMediaType(MediaType.MUSIC);

        when(settingsService.getHideVirtualTracks()).thenReturn(false);
        when(settingsService.getDatabaseType()).thenReturn("HSQLDB");
        // unmodifiable, like the real paged repository result
        when(mediaFileRepository.findAll(ArgumentMatchers.<Specification<MediaFile>>any(), any(Pageable.class))).thenReturn(List.copyOf(List.of(song)));

        List<MediaFile> songs = mediaFileService.getRandomSongsForParent(parent, 10);

        verify(mediaFileRepository, never()).findByFolderAndParentPathAndPresentTrue(any(), any());
        verify(mediaFileRepository, never()).findByFolderAndParentPathAndPresentTrue(any(), any(), any());
        verify(mediaFileRepository, never()).save(any());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(mediaFileRepository).findAll(ArgumentMatchers.<Specification<MediaFile>>any(), pageableCaptor.capture());
        assertEquals(10, pageableCaptor.getValue().getPageSize());
        // callers mutate the result (PlayQueueService.doPlay runs removeVideoFiles on it), so the
        // unmodifiable paged result must come back as a mutable copy
        assertDoesNotThrow(() -> songs.removeIf(MediaFile::isVideo));
        assertDoesNotThrow(() -> songs.add(song));
    }

    @Test
    public void getRandomSongsForParentWithZeroOrNegativeCountReturnsEmptyWithoutQuery() {
        MediaFile parent = new MediaFile();
        parent.setMediaType(MediaType.DIRECTORY);

        assertTrue(mediaFileService.getRandomSongsForParent(parent, 0).isEmpty());
        assertTrue(mediaFileService.getRandomSongsForParent(parent, -1).isEmpty());

        verifyNoInteractions(mediaFileRepository);
    }

    @Test
    public void getRandomSongsForParentWithFileParentReturnsTheFileItself() {
        MediaFile parent = new MediaFile();
        parent.setMediaType(MediaType.MUSIC);

        List<MediaFile> result = mediaFileService.getRandomSongsForParent(parent, 10);

        assertEquals(List.of(parent), result);
        verifyNoInteractions(mediaFileRepository);
    }

    @Test
    public void getRandomSongsForParentWithVideoFileParentReturnsEmpty() {
        MediaFile parent = new MediaFile();
        parent.setMediaType(MediaType.VIDEO);

        assertTrue(mediaFileService.getRandomSongsForParent(parent, 10).isEmpty());

        verifyNoInteractions(mediaFileRepository);
    }
}
