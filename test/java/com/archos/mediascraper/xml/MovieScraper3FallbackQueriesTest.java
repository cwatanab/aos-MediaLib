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

package com.archos.mediascraper.xml;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class MovieScraper3FallbackQueriesTest {

    @Test
    public void stripsSeriesIndexAndSpAndDualTitles() {
        assertTrue(MovieScraper3.buildFallbackMovieQueries("アナと雪の女王 01").contains("アナと雪の女王"));

        List<String> frozen2 = MovieScraper3.buildFallbackMovieQueries("アナと雪の女王 02");
        assertTrue(frozen2.contains("アナと雪の女王 2"));
        assertTrue(frozen2.contains("アナと雪の女王2"));
        assertTrue(frozen2.contains("アナと雪の女王"));

        List<String> hp71 = MovieScraper3.buildFallbackMovieQueries("ハリー・ポッター 07-1 死の秘宝 PART1");
        assertTrue(hp71.contains("ハリー・ポッター 死の秘宝 PART1"));
        assertTrue(hp71.contains("ハリー・ポッター"));

        assertTrue(MovieScraper3.buildFallbackMovieQueries("タイタニック 01 前編").contains("タイタニック"));

        List<String> odoru = MovieScraper3.buildFallbackMovieQueries("踊る大捜査線 スピンオフ 容疑者 室井慎次");
        assertTrue(odoru.contains("容疑者 室井慎次"));

        List<String> conan = MovieScraper3.buildFallbackMovieQueries("名探偵コナン SP01 江戸川コナン失踪事件 史上最悪の二日間");
        assertTrue(conan.contains("名探偵コナン 江戸川コナン失踪事件 史上最悪の二日間"));

        List<String> jackie = MovieScraper3.buildFallbackMovieQueries("ジャッキー・チェン／成龍拳");
        assertTrue(jackie.contains("成龍拳"));

        assertTrue(MovieScraper3.buildFallbackMovieQueries("マッチスティック・メン 特別版").contains("マッチスティック・メン"));
        assertTrue(MovieScraper3.buildFallbackMovieQueries("麒麟の翼～劇場版・新参者～").contains("麒麟の翼"));
        assertTrue(MovieScraper3.buildFallbackMovieQueries("劇場版「頭文字（イニシャル）D Third Stage").contains("頭文字D Third Stage"));
    }

    @Test
    public void leavesStandardTitlesWithoutJapaneseFallbacksUntouched() {
        for (String title : new String[] { "Ocean's 11", "District 9", "James Bond 007", "Titanic" }) {
            assertTrue(title + " should produce no fallback queries",
                    MovieScraper3.buildFallbackMovieQueries(title).isEmpty());
        }
    }
}
