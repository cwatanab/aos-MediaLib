// Copyright 2026 Courville Software
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0

package com.archos.mediascraper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.archos.filecorelibrary.FileUtilsQ;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class NfoParserTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        FileUtilsQ.getInstance(context);
    }

    @Test
    public void rejectsDoctypeAndExternalEntities() {
        String xml = "<!DOCTYPE movie [<!ENTITY xxe SYSTEM \"file:///etc/hosts\">]>"
                + "<movie><title>&xxe;</title></movie>";

        assertThrows(SAXException.class, () -> NfoParser.getNewParser().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                new DefaultHandler()));
    }

    @Test
    public void customEpisodeFindsTvShowNfoInSameFolder() throws Exception {
        File folder = temporaryFolder.newFolder("show");
        File video = write(folder, "S01E01.mkv", "");
        write(folder, "S01E01.archos.nfo", "<episodedetails/>");
        File showNfo = write(folder, "tvshow.nfo", "<tvshow/>");

        NfoParser.NfoFile result = NfoParser.determineNfoFile(Uri.fromFile(video));

        assertNotNull(result);
        assertTrue(result.hasNfo());
        assertEquals(Uri.fromFile(showNfo), result.showNfo);
    }

    @Test
    public void customEpisodeFindsTvShowNfoInParentFolder() throws Exception {
        File showFolder = temporaryFolder.newFolder("parent-show");
        File seasonFolder = new File(showFolder, "Season 01");
        assertTrue(seasonFolder.mkdir());
        File video = write(seasonFolder, "S01E01.mkv", "");
        write(seasonFolder, "S01E01.archos.nfo", "<episodedetails/>");
        File showNfo = write(showFolder, "tvshow.nfo", "<tvshow/>");

        NfoParser.NfoFile result = NfoParser.determineNfoFile(Uri.fromFile(video));

        assertNotNull(result);
        assertEquals(Uri.fromFile(showNfo), result.showNfo);
    }

    @Test
    public void malformedMovieDoesNotContaminateFollowingEpisode() throws Exception {
        File folder = temporaryFolder.newFolder("reuse");
        File video = write(folder, "episode.mkv", "");
        File malformed = write(folder, "broken.nfo", "<movie><title>Broken");
        File episode = write(folder, "episode.nfo", "<episodedetails><title>Pilot</title>"
                + "<showtitle>Example Show</showtitle><season>1</season><episode>1</episode>"
                + "</episodedetails>");
        write(folder, StringUtils.fileSystemEncode("Example Show")
                + NfoParser.CUSTOM_SHOW_NFO_EXTENSION,
                "<tvshow><title>Example Show</title></tvshow>");

        NfoParser.ImportContext importContext = new NfoParser.ImportContext();
        NfoParser.NfoFile brokenNfo = nfoFile(video, folder, malformed);
        assertNull(NfoParser.getTagForFile(brokenNfo, context, importContext));

        BaseTags parsed = NfoParser.getTagForFile(nfoFile(video, folder, episode),
                context, importContext);

        assertNotNull(parsed);
        assertTrue(parsed instanceof EpisodeTags);
        EpisodeTags episodeTags = (EpisodeTags) parsed;
        assertEquals("Pilot", episodeTags.getTitle());
        assertEquals("Example Show", episodeTags.getShowTags().getTitle());
    }

    @Test
    public void episodeRetriesParentTvShowNfoWhenDiscoveryDidNotFindIt() throws Exception {
        File showFolder = temporaryFolder.newFolder("x-men-show");
        File seasonFolder = new File(showFolder, "S01");
        assertTrue(seasonFolder.mkdir());
        File video = write(seasonFolder, "X-Men - S01E01.mkv", "");
        File episode = write(seasonFolder, "X-Men - S01E01.nfo",
                "<episodedetails><title>Night of the Sentinels</title>"
                        + "<showtitle>X-Men</showtitle><season>1</season><episode>1</episode>"
                        + "<plot>Episode plot</plot><uniqueid type=\"tmdb\">76118</uniqueid>"
                        + "</episodedetails>");
        write(showFolder, "tvshow.nfo",
                "<tvshow><title>X-Men</title><plot>Show plot</plot><year>1992</year>"
                        + "<uniqueid type=\"tmdb\">1423</uniqueid></tvshow>");

        NfoParser.NfoFile nfo = nfoFile(video, seasonFolder, episode);
        // Reproduce the SMB trace: episode discovery succeeded but the earlier show-NFO stat did not.
        assertNull(nfo.showNfo);

        BaseTags parsed = NfoParser.getTagForFile(nfo, context, new NfoParser.ImportContext());

        assertNotNull(parsed);
        assertTrue(parsed instanceof EpisodeTags);
        EpisodeTags episodeTags = (EpisodeTags) parsed;
        assertEquals("Episode plot", episodeTags.getPlot());
        assertEquals(76118, episodeTags.getOnlineId());
        assertEquals("Show plot", episodeTags.getShowTags().getPlot());
        assertEquals(1423, episodeTags.getShowTags().getOnlineId());
    }

    private static Uri forwardSlashFileUri(File file) {
        String path = file.getAbsolutePath().replace('\\', '/');
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return new Uri.Builder().scheme("file").authority("").path(path).build();
    }

    @Test
    public void datedEpisodeUsesParentFolderTvShowNfoAndReusesCache() throws Exception {
        File showFolder = temporaryFolder.newFolder("buratamori");
        File ep1 = write(showFolder, "20150411 #1 長崎〜坂の街〜.mp4", "");
        File ep2 = write(showFolder, "20150418 #2 鎌倉.mp4", "");
        File nonDated = write(showFolder, "20240224_133333.mp4", "");
        File plain = write(showFolder, "movie.mp4", "");
        String nfdShowTitle = java.text.Normalizer.normalize("ブラタモリ", java.text.Normalizer.Form.NFD);
        write(showFolder, "tvshow.nfo",
                "<tvshow><title>" + nfdShowTitle + "</title><plot>散歩番組</plot></tvshow>");

        NfoParser.ImportContext importContext = new NfoParser.ImportContext();
        Uri ep1Uri = forwardSlashFileUri(ep1);
        Uri ep2Uri = forwardSlashFileUri(ep2);
        Uri nonDatedUri = forwardSlashFileUri(nonDated);
        Uri plainUri = forwardSlashFileUri(plain);

        NfoParser.NfoFile nfo1 = NfoParser.determineNfoFile(ep1Uri, importContext);
        assertNotNull(nfo1);
        assertTrue(nfo1.datedShowEpisode);
        assertTrue(nfo1.hasNfo());

        BaseTags parsed1 = NfoParser.getTagForFile(ep1Uri, context, importContext);
        assertTrue(parsed1 instanceof EpisodeTags);
        EpisodeTags e1 = (EpisodeTags) parsed1;
        assertEquals("ブラタモリ", e1.getShowTags().getTitle());
        assertEquals("#1 長崎〜坂の街〜", e1.getTitle());
        assertEquals(2015, e1.getSeason());
        assertEquals(411, e1.getEpisode());

        BaseTags parsed2 = NfoParser.getTagForFile(ep2Uri, context, importContext);
        assertTrue(parsed2 instanceof EpisodeTags);
        EpisodeTags e2 = (EpisodeTags) parsed2;
        assertEquals("ブラタモリ", e2.getShowTags().getTitle());
        assertEquals("#2 鎌倉", e2.getTitle());
        assertEquals(2015, e2.getSeason());
        assertEquals(418, e2.getEpisode());

        assertNull("camera timestamp must not match dated show episode",
                NfoParser.getTagForFile(nonDatedUri, context, importContext));
        assertNull("undated file must not match dated show episode",
                NfoParser.getTagForFile(plainUri, context, importContext));
    }

    private static NfoParser.NfoFile nfoFile(File video, File folder, File nfo) {
        NfoParser.NfoFile result = new NfoParser.NfoFile();
        result.videoFile = Uri.fromFile(video);
        result.videoFolder = Uri.fromFile(folder);
        result.videoNfo = Uri.fromFile(nfo);
        return result;
    }

    private static File write(File folder, String name, String contents) throws Exception {
        File file = new File(folder, name);
        Files.write(file.toPath(), contents.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
