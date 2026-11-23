// Copyright 2017 Archos SA
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

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import com.archos.medialib.R;
import com.archos.mediaprovider.video.VideoStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Language-independent helpers for TMDB genres.
 *
 * <p>Historically Nova stored only the genre display name, localized at scrape time, in
 * {@code movie.m_genres} / {@code show.s_genres}. Filtering (e.g. "exclude animation from All
 * movies") and display therefore depended on the language active at scrape time and broke as soon
 * as the UI language changed.
 *
 * <p>We now persist the TMDB genre ids in {@code movie.m_genre_ids} / {@code show.s_genre_ids},
 * delimiter wrapped (e.g. {@code ",16,10751,"}) so that {@code LIKE '%,16,%'} is unambiguous, and
 * derive the localized name at render time from the current app locale. The legacy name columns are
 * kept and used as a fallback for rows that predate the migration or that were imported from NFO
 * without ids.
 */
public final class GenreUtils {

    /** TMDB genre id for Animation, shared by movies and TV shows. */
    public static final int ANIMATION_GENRE_ID = 16;

    private static final int[] MOVIE_GENRE_IDS = {
            28, 12, 16, 35, 80, 99, 18, 10751, 14, 36, 27,
            10402, 9648, 10749, 878, 10770, 53, 10752, 37
    };

    private static final int[] SHOW_GENRE_IDS = {
            10759, 16, 35, 80, 99, 18, 10751, 10762, 9648,
            10763, 10764, 10765, 10766, 10767, 10768, 37
    };

    private static final int[] LEGACY_SHOW_GENRE_STRINGS = {
            R.string.tv_show_genre_action,
            R.string.tv_show_genre_adventure,
            R.string.tv_show_genre_animation,
            R.string.tv_show_genre_anime,
            R.string.tv_show_genre_awards_show,
            R.string.tv_show_genre_children,
            R.string.tv_show_genre_comedy,
            R.string.tv_show_genre_crime,
            R.string.tv_show_genre_documentary,
            R.string.tv_show_genre_drama,
            R.string.tv_show_genre_family,
            R.string.tv_show_genre_fantasy,
            R.string.tv_show_genre_food,
            R.string.tv_show_genre_game_show,
            R.string.tv_show_genre_home_garden,
            R.string.tv_show_genre_horror,
            R.string.tv_show_genre_indie,
            R.string.tv_show_genre_martial_arts,
            R.string.tv_show_genre_mini_series,
            R.string.tv_show_genre_musical,
            R.string.tv_show_genre_mystery,
            R.string.tv_show_genre_news,
            R.string.tv_show_genre_podcast,
            R.string.tv_show_genre_reality,
            R.string.tv_show_genre_romance,
            R.string.tv_show_genre_science_fiction,
            R.string.tv_show_genre_soap,
            R.string.tv_show_genre_special_interest,
            R.string.tv_show_genre_sport,
            R.string.tv_show_genre_suspense,
            R.string.tv_show_genre_talk_show,
            R.string.tv_show_genre_thriller,
            R.string.tv_show_genre_travel,
            R.string.tv_show_genre_war,
            R.string.tv_show_genre_western
    };

    /**
     * BCP-47 tags for every shipped locale that translates genre names (plus base "en" and "ja").
     * Used to build the name-to-id reverse map and the legacy fallback predicate.
     */
    private static final String[] TRANSLATED_LOCALES = {
            "en", "ja",
            "ar", "cs-CZ", "de", "el-GR", "es", "fr", "hu-HU", "it", "he", "iw",
            "kmr-TR", "ko", "lt-LT", "nl", "pl", "pt-BR", "ru", "sk-SK", "sv-SE",
            "tr-TR", "uk-UA", "vi", "zh-CN", "zh-TW"
    };

    // Built once per process from every shipped locale's resources. A runtime app-language
    // change (Android 13+ per-app language) is only reflected after the process restarts;
    // acceptable for genre name lookups and cheaper than rebuilding on every query.
    private static Map<String, Integer> sMovieNameToId;
    private static Map<String, Integer> sShowNameToId;
    private static Map<String, Integer> sLegacyGenreNameToRes;
    private static List<Resources> sTranslatedResources;
    private static final Map<Integer, Set<String>> sResVariantCache = new ConcurrentHashMap<>();

    private GenreUtils() {
    }

    /** R.string for a movie genre id, or 0 if unknown. */
    public static int getMovieGenreStringRes(int id) {
        switch (id) {
            case 28: return R.string.movie_genre_action;
            case 12: return R.string.movie_genre_adventure;
            case 16: return R.string.movie_genre_animation;
            case 35: return R.string.movie_genre_comedy;
            case 80: return R.string.movie_genre_crime;
            case 99: return R.string.movie_genre_documentary;
            case 18: return R.string.movie_genre_drama;
            case 10751: return R.string.movie_family;
            case 14: return R.string.movie_genre_fantasy;
            case 36: return R.string.movie_genre_history;
            case 27: return R.string.movie_genre_horror;
            case 10402: return R.string.movie_genre_music;
            case 9648: return R.string.movie_genre_mystery;
            case 10749: return R.string.movie_genre_romance;
            case 878: return R.string.movie_genre_science_fiction;
            case 10770: return R.string.movie_genre_tv_movie;
            case 53: return R.string.movie_genre_thriller;
            case 10752: return R.string.movie_genre_war;
            case 37: return R.string.movie_genre_western;
            default: return 0;
        }
    }

    /** R.string for a TV show genre id, or 0 if unknown. */
    public static int getShowGenreStringRes(int id) {
        switch (id) {
            case 10759: return R.string.tvshow_genre_action_adventure;
            case 16: return R.string.tvshow_genre_animation;
            case 35: return R.string.tvshow_genre_comedy;
            case 80: return R.string.tvshow_genre_crime;
            case 99: return R.string.tvshow_genre_documentary;
            case 18: return R.string.tvshow_genre_drama;
            case 10751: return R.string.tvshow_genre_family;
            case 10762: return R.string.tvshow_genre_kids;
            case 9648: return R.string.tvshow_genre_mystery;
            case 10763: return R.string.tvshow_genre_news;
            case 10764: return R.string.tvshow_genre_reality;
            case 10765: return R.string.tvshow_genre_scifi_fantasy;
            case 10766: return R.string.tvshow_genre_soap;
            case 10767: return R.string.tvshow_genre_talk;
            case 10768: return R.string.tvshow_genre_war_politics;
            case 37: return R.string.tvshow_genre_western;
            default: return 0;
        }
    }

    /**
     * Localized name for a genre id in the current app locale, falling back to {@code fallback}
     * (typically the TMDB name) when the id is not mapped by the app.
     */
    public static String getGenreName(Context context, int id, boolean isShow, String fallback) {
        int res = isShow ? getShowGenreStringRes(id) : getMovieGenreStringRes(id);
        if (res == 0) {
            res = isShow ? getMovieGenreStringRes(id) : getShowGenreStringRes(id);
        }
        if (res != 0) {
            return context.getString(res);
        }
        return fallback;
    }

    /**
     * Reverse lookup of a (possibly localized) genre name to its TMDB id, using all shipped
     * locales. Returns -1 when unknown. Matching is case-insensitive and trims the input.
     */
    public static int getGenreIdFromName(Context context, String name, boolean isShow) {
        if (name == null) {
            return -1;
        }
        Map<String, Integer> map = isShow ? getShowNameToId(context) : getMovieNameToId(context);
        Integer id = map.get(name.trim().toLowerCase(Locale.ROOT));
        return id != null ? id : -1;
    }

    /**
     * Converts a legacy localized genre name list (as stored in m_genres/s_genres, e.g.
     * "Action, Animazione") into the wrapped id format ",28,16,". Returns null when no token can
     * be mapped.
     */
    public static String genreIdsFromNames(Context context, String names, boolean isShow) {
        if (names == null) {
            return null;
        }
        List<Integer> ids = new ArrayList<>();
        for (String token : names.split(",")) {
            int id = getGenreIdFromName(context, token, isShow);
            if (id > 0 && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return formatGenreIds(ids);
    }

    /** Wraps a list of genre ids into the stored format ",16,10751,"; null when empty. */
    public static String formatGenreIds(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(",");
        for (Integer id : ids) {
            if (id != null && id > 0) {
                sb.append(id).append(',');
            }
        }
        return sb.length() > 1 ? sb.toString() : null;
    }

    /** Parses the stored format ",16,10751," into a list of ids; never null. */
    public static List<Integer> parseGenreIds(String genreIds) {
        List<Integer> ids = new ArrayList<>();
        if (genreIds == null) {
            return ids;
        }
        for (String token : genreIds.split(",")) {
            token = token.trim();
            if (token.isEmpty()) {
                continue;
            }
            try {
                ids.add(Integer.parseInt(token));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }

    /**
     * Builds the localized genre display string from stored genre ids. Returns null when no id can
     * be translated (caller should then fall back to the stored name string).
     */
    public static String formatGenreNames(Context context, String genreIds, boolean isShow) {
        List<Integer> ids = parseGenreIds(genreIds);
        if (ids.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Integer id : ids) {
            String name = getGenreName(context, id, isShow, null);
            // Ids without a known mapping (e.g. a genre added by a newer scraper before this app
            // release) are deliberately dropped: the stored legacy name would not contain them
            // either, since the same id->name mapping produced it.
            if (name == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(name);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * Translates a stored (scrape-time localized) genre name into the current app locale by
     * reverse mapping it to its TMDB id, cross-domain id, or legacy TVDB genre string resource.
     * Returns {@code storedName} unchanged when it cannot be mapped (non-genre category such as
     * a year, or an unknown/NFO genre).
     */
    public static String getGenreDisplayName(Context context, String storedName, boolean isShow) {
        if (storedName == null) {
            return null;
        }
        String trimmed = storedName.trim();
        if (trimmed.isEmpty()) {
            return storedName;
        }
        int id = getGenreIdFromName(context, trimmed, isShow);
        if (id >= 0) {
            String name = getGenreName(context, id, isShow, null);
            if (name != null) {
                return name;
            }
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (isShow) {
            Integer legacyRes = getLegacyGenreNameToRes(context).get(normalized);
            if (legacyRes != null && legacyRes != 0) {
                return context.getString(legacyRes);
            }
        }
        int crossId = getGenreIdFromName(context, trimmed, !isShow);
        if (crossId >= 0) {
            String name = getGenreName(context, crossId, !isShow, null);
            if (name != null) {
                return name;
            }
        }
        if (!isShow) {
            Integer legacyRes = getLegacyGenreNameToRes(context).get(normalized);
            if (legacyRes != null && legacyRes != 0) {
                return context.getString(legacyRes);
            }
        }
        return storedName;
    }

    /**
     * Translates a comma-separated list of stored genre names into the current app locale,
     * preserving order and removing duplicates after translation.
     */
    public static String localizeGenreList(Context context, String rawGenres, boolean isShow) {
        if (rawGenres == null || rawGenres.isEmpty()) {
            return rawGenres;
        }
        String[] tokens = rawGenres.split(",");
        Set<String> translated = new LinkedHashSet<>();
        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            translated.add(getGenreDisplayName(context, trimmed, isShow));
        }
        if (translated.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String item : translated) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(item);
        }
        return sb.toString();
    }

    /**
     * SQL predicate selecting/excluding animation, language independent.
     *
     * <p>Rows with genre ids use the ids column. Rows without ids (legacy/NFO) fall back to
     * matching the stored name against every shipped translation, which fixes the count bug for
     * data that was never re-scraped while still honoring the old behavior.
     *
     * @param isShow      true for shows (uses s_ columns), false for movies
     * @param includeAnime true to keep only animation, false to exclude it
     * @return a fully parenthesized boolean expression, without a leading AND
     */
    public static String getAnimeSelection(Context context, boolean isShow, boolean includeAnime) {
        String idsColumn = isShow ? VideoStore.Video.VideoColumns.SCRAPER_S_GENRE_IDS
                : VideoStore.Video.VideoColumns.SCRAPER_M_GENRE_IDS;
        String namesColumn = isShow ? VideoStore.Video.VideoColumns.SCRAPER_S_GENRES
                : VideoStore.Video.VideoColumns.SCRAPER_M_GENRES;
        int animationRes = isShow ? R.string.tvshow_genre_animation : R.string.movie_genre_animation;

        Set<String> names = getGenreNamesForRes(context, animationRes);
        StringBuilder legacy = new StringBuilder();
        for (String name : names) {
            if (legacy.length() > 0) {
                legacy.append(includeAnime ? " OR " : " AND ");
            }
            legacy.append(namesColumn).append(includeAnime ? " LIKE " : " NOT LIKE ")
                    .append("'%").append(escapeSqlLiteral(name)).append("%'");
        }
        String legacyPredicate = includeAnime
                ? "(" + legacy + ")"
                : "(" + namesColumn + " IS NULL OR (" + legacy + "))";

        String idPredicate = idsColumn + (includeAnime ? " LIKE " : " NOT LIKE ")
                + "'%," + ANIMATION_GENRE_ID + ",%'";
        return "((" + idsColumn + " IS NOT NULL AND " + idPredicate + ") OR ("
                + idsColumn + " IS NULL AND " + legacyPredicate + "))";
    }

    /**
     * SQL predicate excluding a genre category by any of its shipped localized names, e.g.
     * {@code genre.name_genre NOT IN ('Animation','Animazione',...)}. Used by the by-genre browse
     * loaders so category filtering does not depend on the scrape-time language.
     */
    public static String getGenreNameExclusion(Context context, int genreId, boolean isShow, String column) {
        int res = isShow ? getShowGenreStringRes(genreId) : getMovieGenreStringRes(genreId);
        Set<String> names = getGenreNamesForRes(context, res);
        StringBuilder sb = new StringBuilder(column).append(" NOT IN (");
        boolean first = true;
        for (String name : names) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append("'").append(escapeSqlLiteral(name)).append("'");
        }
        return sb.append(")").toString();
    }

    /** Distinct localized names for a genre string resource across all shipped locales. */
    private static Set<String> getGenreNamesForRes(Context context, int res) {
        Set<String> names = new LinkedHashSet<>();
        names.add(context.getString(res));
        Set<String> cached = sResVariantCache.get(res);
        if (cached == null) {
            Set<String> built = new LinkedHashSet<>();
            for (Resources resources : getTranslatedResources(context)) {
                try {
                    String value = resources.getString(res);
                    if (value != null && !value.isEmpty()) {
                        built.add(value);
                    }
                } catch (Exception ignored) {
                }
            }
            cached = Collections.unmodifiableSet(built);
            sResVariantCache.put(res, cached);
        }
        names.addAll(cached);
        return names;
    }

    private static Map<String, Integer> getMovieNameToId(Context context) {
        ensureReverseMaps(context);
        return sMovieNameToId;
    }

    private static Map<String, Integer> getShowNameToId(Context context) {
        ensureReverseMaps(context);
        return sShowNameToId;
    }

    private static Map<String, Integer> getLegacyGenreNameToRes(Context context) {
        ensureReverseMaps(context);
        return sLegacyGenreNameToRes;
    }

    private static synchronized List<Resources> getTranslatedResources(Context context) {
        if (sTranslatedResources == null) {
            Context baseContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
            Configuration baseConfig = baseContext.getResources().getConfiguration();
            List<Resources> list = new ArrayList<>(TRANSLATED_LOCALES.length);
            for (String tag : TRANSLATED_LOCALES) {
                try {
                    Configuration config = new Configuration(baseConfig);
                    config.setLocale(Locale.forLanguageTag(tag));
                    list.add(baseContext.createConfigurationContext(config).getResources());
                } catch (Exception ignored) {
                }
            }
            sTranslatedResources = Collections.unmodifiableList(list);
        }
        return sTranslatedResources;
    }

    private static synchronized void ensureReverseMaps(Context context) {
        if (sMovieNameToId != null && sShowNameToId != null && sLegacyGenreNameToRes != null) {
            return;
        }
        Map<String, Integer> movieMap = new LinkedHashMap<>();
        Map<String, Integer> showMap = new LinkedHashMap<>();
        Map<String, Integer> legacyMap = new LinkedHashMap<>();

        Resources currentRes = context.getResources();
        addLocaleNames(movieMap, currentRes, MOVIE_GENRE_IDS, false);
        addLocaleNames(showMap, currentRes, SHOW_GENRE_IDS, true);
        addLegacyLocaleNames(legacyMap, currentRes);

        for (Resources resources : getTranslatedResources(context)) {
            addLocaleNames(movieMap, resources, MOVIE_GENRE_IDS, false);
            addLocaleNames(showMap, resources, SHOW_GENRE_IDS, true);
            addLegacyLocaleNames(legacyMap, resources);
        }

        // Common English aliases returned by NFO files or older scrapers
        movieMap.putIfAbsent("sci-fi", 878);
        movieMap.putIfAbsent("scifi", 878);
        movieMap.putIfAbsent("science-fiction", 878);
        showMap.putIfAbsent("action and adventure", 10759);
        showMap.putIfAbsent("war and politics", 10768);
        showMap.putIfAbsent("アクション & アドベンチャー", 10759);
        showMap.putIfAbsent("アクション＆アドベンチャー", 10759);
        legacyMap.putIfAbsent("sci-fi", R.string.tv_show_genre_science_fiction);
        legacyMap.putIfAbsent("scifi", R.string.tv_show_genre_science_fiction);
        legacyMap.putIfAbsent("science-fiction", R.string.tv_show_genre_science_fiction);
        legacyMap.putIfAbsent("sports", R.string.tv_show_genre_sport);
        legacyMap.putIfAbsent("miniseries", R.string.tv_show_genre_mini_series);
        legacyMap.putIfAbsent("home & garden", R.string.tv_show_genre_home_garden);

        sMovieNameToId = Collections.unmodifiableMap(movieMap);
        sShowNameToId = Collections.unmodifiableMap(showMap);
        sLegacyGenreNameToRes = Collections.unmodifiableMap(legacyMap);
    }

    private static void addLocaleNames(Map<String, Integer> map, Resources resources, int[] ids, boolean isShow) {
        for (int id : ids) {
            int res = isShow ? getShowGenreStringRes(id) : getMovieGenreStringRes(id);
            if (res == 0) {
                continue;
            }
            try {
                String name = resources.getString(res);
                if (name != null) {
                    map.putIfAbsent(name.trim().toLowerCase(Locale.ROOT), id);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void addLegacyLocaleNames(Map<String, Integer> map, Resources resources) {
        for (int res : LEGACY_SHOW_GENRE_STRINGS) {
            try {
                String name = resources.getString(res);
                if (name != null) {
                    map.putIfAbsent(name.trim().toLowerCase(Locale.ROOT), res);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static String escapeSqlLiteral(String value) {
        return value.replace("'", "''");
    }
}
