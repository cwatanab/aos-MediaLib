// Copyright 2026 Courville Software
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.mediascraper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.MatrixCursor;
import android.net.Uri;

import com.archos.mediaprovider.video.ScraperStore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShowTagsPersistenceTest {

    @Test
    public void rescrapeRepairsMissingPlotOnExistingShow() throws Exception {
        Context context = mock(Context.class);
        ContentResolver resolver = mock(ContentResolver.class);
        when(context.getContentResolver()).thenReturn(resolver);
        when(resolver.query(any(Uri.class), any(String[].class), nullable(String.class),
                nullable(String[].class), nullable(String.class))).thenAnswer(invocation -> {
                    Uri uri = invocation.getArgument(0);
                    String[] projection = invocation.getArgument(1);
                    MatrixCursor cursor = new MatrixCursor(projection);
                    if (ScraperStore.Show.URI.ALL.equals(uri) && projection.length == 2) {
                        cursor.addRow(new Object[] { 31L, 2252L });
                    } else if (ScraperStore.Show.URI.ALL.equals(uri) && projection.length == 14) {
                        cursor.addRow(new Object[] {
                                31L, null, 0f, null, null, "tt0103514", 2252L, -1L, -1L,
                                null, "und", "", "", "und"
                        });
                    } else if (projection.length == 1) {
                        cursor.addRow(new Object[] { 0 });
                    }
                    return cursor;
                });
        Uri showUri = ContentUris.withAppendedId(ScraperStore.Show.URI.ID, 31L);
        when(resolver.update(eq(showUri), any(ContentValues.class), nullable(String.class),
                nullable(String[].class))).thenReturn(1);

        ShowTags show = new ShowTags();
        show.setTitle("X-Men: The Animated Series");
        show.setOnlineId(2252L);
        show.setImdbId("tt0103514");
        show.setPlot("Restored NFO description");

        assertEquals(31L, show.save(context, 42L));
        ArgumentCaptor<ContentValues> values = ArgumentCaptor.forClass(ContentValues.class);
        verify(resolver).update(eq(showUri), values.capture(), nullable(String.class),
                nullable(String[].class));
        assertEquals("Restored NFO description",
                values.getValue().getAsString(ScraperStore.Show.PLOT));
    }

    @Test
    public void duplicateInsertRaceReusesExistingShow() {
        Context context = mock(Context.class);
        ContentResolver resolver = mock(ContentResolver.class);
        AtomicBoolean insertAttempted = new AtomicBoolean();
        when(context.getContentResolver()).thenReturn(resolver);
        when(resolver.insert(eq(ScraperStore.Show.URI.BASE), any(ContentValues.class)))
                .thenAnswer(invocation -> {
                    insertAttempted.set(true);
                    return null;
                });
        when(resolver.query(any(Uri.class), any(String[].class), nullable(String.class),
                nullable(String[].class), nullable(String.class))).thenAnswer(invocation -> {
                    Uri uri = invocation.getArgument(0);
                    String[] projection = invocation.getArgument(1);
                    MatrixCursor cursor = new MatrixCursor(projection);
                    if (insertAttempted.get() && ScraperStore.Show.URI.ALL.equals(uri)
                            && projection.length == 14) {
                        cursor.addRow(new Object[] {
                                77L, null, 0f, null, null, null, 5678L, -1L, -1L, null,
                                "und", "", "", "und"
                        });
                    }
                    return cursor;
                });

        ShowTags show = new ShowTags();
        show.setTitle("Concurrent show");
        show.setOnlineId(5678L);

        assertEquals(77L, show.save(context, 42L));
        verify(resolver).insert(eq(ScraperStore.Show.URI.BASE), any(ContentValues.class));
    }

    @Test
    public void nfoShowWithoutOnlineIdDoesNotCollideWithUnrelatedZeroOnlineIdShow() {
        Context context = mock(Context.class);
        ContentResolver resolver = mock(ContentResolver.class);
        when(context.getContentResolver()).thenReturn(resolver);
        when(resolver.query(any(Uri.class), any(String[].class), nullable(String.class),
                nullable(String[].class), nullable(String.class))).thenAnswer(invocation -> {
                    String[] projection = invocation.getArgument(1);
                    String selection = invocation.getArgument(2);
                    String[] selectionArgs = invocation.getArgument(3);
                    MatrixCursor cursor = new MatrixCursor(projection);
                    if (selection != null && selection.startsWith("s_online_id=")
                            && selectionArgs != null && selectionArgs.length > 0
                            && "0".equals(selectionArgs[0])) {
                        cursor.addRow(baseRowIfRequested(projection, 10L, 0L, null, 0f, "ja", "ja"));
                    }
                    return cursor;
                });
        when(resolver.insert(eq(ScraperStore.Show.URI.BASE), any(ContentValues.class)))
                .thenReturn(ContentUris.withAppendedId(ScraperStore.Show.URI.ID, 99L));

        ShowTags show = new ShowTags();
        show.setTitle("VIVANT");
        show.setOnlineId(0L);

        assertEquals(99L, show.save(context, 42L));
        verify(resolver).insert(eq(ScraperStore.Show.URI.BASE), any(ContentValues.class));
        verify(resolver, never()).query(any(Uri.class), any(String[].class),
                eq("s_online_id=?"), any(String[].class), nullable(String.class));
    }

    @Test
    public void nfoShowWithoutOnlineIdReusesExistingTmdbShowByNameWithoutClobberingMetadata() {
        Context context = mock(Context.class);
        ContentResolver resolver = mock(ContentResolver.class);
        when(context.getContentResolver()).thenReturn(resolver);
        when(resolver.query(any(Uri.class), any(String[].class), nullable(String.class),
                nullable(String[].class), nullable(String.class))).thenAnswer(invocation -> {
                    Uri uri = invocation.getArgument(0);
                    String[] projection = invocation.getArgument(1);
                    String selection = invocation.getArgument(2);
                    String[] selectionArgs = invocation.getArgument(3);
                    MatrixCursor cursor = new MatrixCursor(projection);
                    if (ScraperStore.Show.URI.ALL.equals(uri) && selection != null) {
                        if (selection.startsWith("s_online_id=")
                                && selectionArgs != null && selectionArgs.length > 0
                                && "0".equals(selectionArgs[0])) {
                            cursor.addRow(baseRowIfRequested(projection, 10L, 0L, null, 0f, "und", "und"));
                        } else if ("name_show=?".equals(selection)
                                && selectionArgs != null && selectionArgs.length > 0
                                && "VIVANT".equals(selectionArgs[0])) {
                            if (projection.length == 1) {
                                cursor.addRow(new Object[] { 2L });
                            } else if (projection.length == 14) {
                                cursor.addRow(new Object[] {
                                        2L, "/cover.jpg", 8.1f, "TV-14", "/bd.jpg", "tt999",
                                        218038L, 1L, 2L, "TMDB plot", "ja", "VIVANT", "ja", "ja"
                                });
                            }
                        }
                    } else if (projection.length == 1) {
                        cursor.addRow(new Object[] { 0 });
                    }
                    return cursor;
                });

        ShowTags show = new ShowTags();
        show.setTitle("VIVANT");
        show.setOnlineId(0L);

        assertEquals(2L, show.save(context, 42L));
        verify(resolver, never()).query(any(Uri.class), any(String[].class),
                eq("s_online_id=?"), any(String[].class), nullable(String.class));
        verify(resolver, never()).update(any(Uri.class), any(ContentValues.class),
                nullable(String.class), nullable(String[].class));
    }

    @Test
    public void nfoShowWithAdditionalPlotPreservesExistingOnlineIdAndLanguageOnUpdate() {
        Context context = mock(Context.class);
        ContentResolver resolver = mock(ContentResolver.class);
        when(context.getContentResolver()).thenReturn(resolver);
        when(resolver.query(any(Uri.class), any(String[].class), nullable(String.class),
                nullable(String[].class), nullable(String.class))).thenAnswer(invocation -> {
                    Uri uri = invocation.getArgument(0);
                    String[] projection = invocation.getArgument(1);
                    String selection = invocation.getArgument(2);
                    String[] selectionArgs = invocation.getArgument(3);
                    MatrixCursor cursor = new MatrixCursor(projection);
                    if (ScraperStore.Show.URI.ALL.equals(uri) && "name_show=?".equals(selection)
                            && selectionArgs != null && selectionArgs.length > 0
                            && "VIVANT".equals(selectionArgs[0])) {
                        if (projection.length == 1) {
                            cursor.addRow(new Object[] { 2L });
                        } else if (projection.length == 14) {
                            cursor.addRow(new Object[] {
                                    2L, "/cover.jpg", 8.1f, "TV-14", "/bd.jpg", "tt999",
                                    218038L, 1L, 2L, null, "ja", "VIVANT", "ja", "ja"
                            });
                        }
                    } else if (projection.length == 1) {
                        cursor.addRow(new Object[] { 0 });
                    }
                    return cursor;
                });
        Uri showUri = ContentUris.withAppendedId(ScraperStore.Show.URI.ID, 2L);
        when(resolver.update(eq(showUri), any(ContentValues.class), nullable(String.class),
                nullable(String[].class))).thenReturn(1);

        ShowTags show = new ShowTags();
        show.setTitle("VIVANT");
        show.setOnlineId(0L);
        show.setPlot("Local NFO plot");

        assertEquals(2L, show.save(context, 42L));
        ArgumentCaptor<ContentValues> values = ArgumentCaptor.forClass(ContentValues.class);
        verify(resolver).update(eq(showUri), values.capture(), nullable(String.class),
                nullable(String[].class));
        ContentValues captured = values.getValue();
        assertEquals("Local NFO plot", captured.getAsString(ScraperStore.Show.PLOT));
        assertFalse(captured.containsKey(ScraperStore.Show.ONLINE_ID));
        assertFalse(captured.containsKey(ScraperStore.Show.ORIGINAL_LANGUAGE));
        assertFalse(captured.containsKey(ScraperStore.Show.TITLE_LANGUAGE));
        assertNull(captured.get(ScraperStore.Show.RATING));
    }

    @Test
    public void onlineScrapeEnrichesExistingNfoShowWithSameNameAndZeroOnlineId() {
        Context context = mock(Context.class);
        ContentResolver resolver = mock(ContentResolver.class);
        when(context.getContentResolver()).thenReturn(resolver);
        when(resolver.query(any(Uri.class), any(String[].class), nullable(String.class),
                nullable(String[].class), nullable(String.class))).thenAnswer(invocation -> {
                    Uri uri = invocation.getArgument(0);
                    String[] projection = invocation.getArgument(1);
                    String selection = invocation.getArgument(2);
                    String[] selectionArgs = invocation.getArgument(3);
                    MatrixCursor cursor = new MatrixCursor(projection);
                    if (ScraperStore.Show.URI.ALL.equals(uri) && selection != null
                            && selectionArgs != null && selectionArgs.length > 0
                            && "明鏡止水".equals(selectionArgs[0])) {
                        if (selection.equals("name_show=?") || selection.startsWith("name_show=? AND (")) {
                            if (projection.length == 1) {
                                cursor.addRow(new Object[] { 10L });
                            } else if (projection.length == 14) {
                                cursor.addRow(new Object[] {
                                        10L, null, 0f, null, null, null, 0L, -1L, -1L,
                                        null, "und", "", "", "und"
                                });
                            }
                        }
                    } else if (projection.length == 1) {
                        cursor.addRow(new Object[] { 0 });
                    }
                    return cursor;
                });
        Uri showUri = ContentUris.withAppendedId(ScraperStore.Show.URI.ID, 10L);
        when(resolver.update(eq(showUri), any(ContentValues.class), nullable(String.class),
                nullable(String[].class))).thenReturn(1);

        ShowTags show = new ShowTags();
        show.setTitle("明鏡止水");
        show.setOnlineId(99999L);
        show.setPremiered("2022-04-01");
        show.setOriginalLanguage("ja");
        show.setTitleLanguage("ja");

        assertEquals(10L, show.save(context, 42L));
        ArgumentCaptor<ContentValues> values = ArgumentCaptor.forClass(ContentValues.class);
        verify(resolver).update(eq(showUri), values.capture(), nullable(String.class),
                nullable(String[].class));
        assertEquals(Long.valueOf(99999L), values.getValue().getAsLong(ScraperStore.Show.ONLINE_ID));
    }

    @Test
    public void duplicateInsertRaceWithoutOnlineIdReusesSameNameNotZeroOnlineId() {
        Context context = mock(Context.class);
        ContentResolver resolver = mock(ContentResolver.class);
        AtomicBoolean insertAttempted = new AtomicBoolean();
        when(context.getContentResolver()).thenReturn(resolver);
        when(resolver.insert(eq(ScraperStore.Show.URI.BASE), any(ContentValues.class)))
                .thenAnswer(invocation -> {
                    insertAttempted.set(true);
                    return null;
                });
        when(resolver.query(any(Uri.class), any(String[].class), nullable(String.class),
                nullable(String[].class), nullable(String.class))).thenAnswer(invocation -> {
                    Uri uri = invocation.getArgument(0);
                    String[] projection = invocation.getArgument(1);
                    String selection = invocation.getArgument(2);
                    String[] selectionArgs = invocation.getArgument(3);
                    MatrixCursor cursor = new MatrixCursor(projection);
                    if (ScraperStore.Show.URI.ALL.equals(uri) && selection != null) {
                        if (selection.startsWith("s_online_id=")
                                && selectionArgs != null && selectionArgs.length > 0
                                && "0".equals(selectionArgs[0])) {
                            cursor.addRow(baseRowIfRequested(projection, 10L, 0L, null, 0f, "und", "und"));
                        } else if (insertAttempted.get() && "name_show=?".equals(selection)
                                && selectionArgs != null && selectionArgs.length > 0
                                && "Concurrent NFO Show".equals(selectionArgs[0])) {
                            if (projection.length == 14) {
                                cursor.addRow(new Object[] {
                                        88L, null, 0f, null, null, null, 0L, -1L, -1L, null,
                                        "und", "", "", "und"
                                });
                            }
                        }
                    }
                    return cursor;
                });

        ShowTags show = new ShowTags();
        show.setTitle("Concurrent NFO Show");
        show.setOnlineId(0L);

        assertEquals(88L, show.save(context, 42L));
        verify(resolver).insert(eq(ScraperStore.Show.URI.BASE), any(ContentValues.class));
        verify(resolver, never()).query(any(Uri.class), any(String[].class),
                eq("s_online_id=?"), any(String[].class), nullable(String.class));
    }

    private static Object[] baseRowIfRequested(String[] projection, long id, long onlineId,
            String plot, float rating, String origLang, String titleLang) {
        if (projection.length == 14) {
            return new Object[] {
                    id, null, rating, null, null, null, onlineId, -1L, -1L,
                    plot, origLang, "", "", titleLang
            };
        }
        return new Object[] { id };
    }
}
