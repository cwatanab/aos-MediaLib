package com.archos.mediacenter.filecoreextension;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import com.archos.medialib.SmbProxy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PcloudMediaLibTest {

    @Test
    public void testUriUtils_recognizesPcloudUsAndEu() {
        Uri usUri = Uri.parse("pcloud://api.pcloud.com/Movies/sample.mkv");
        Uri euUri = Uri.parse("pcloud://eapi.pcloud.com/Movies");

        assertTrue(UriUtils.isImplementedByFileCore(usUri));
        assertTrue(UriUtils.isImplementedByFileCore(euUri));
        assertTrue(UriUtils.isIndexable(usUri));
        assertTrue(UriUtils.isIndexable(euUri));
        assertTrue(UriUtils.isCompatibleWithRemoteDB(usUri));
        assertTrue(UriUtils.isValidStringUri("pcloud://api.pcloud.com/Movies"));
        assertTrue(UriUtils.isValidStringUri("pcloud://eapi.pcloud.com/"));
        assertFalse(UriUtils.requiresDomain("pcloud"));
    }

    @Test
    public void testSmbProxy_needsToStreamPcloud() {
        assertTrue(SmbProxy.needToStream("pcloud"));
        assertTrue(SmbProxy.needToStream("PCLOUD"));
    }

    @Test
    public void testSmbProxy_pcloudSpecialChars_doesNotDoubleEncodeOrTruncateUri() throws Exception {
        Uri pcloudUri = new Uri.Builder()
                .scheme("pcloud")
                .authority("api.pcloud.com")
                .appendPath("日本語 フォルダ")
                .appendPath("movie #1 100% ?test.mkv")
                .build();
        SmbProxy[] holder = new SmbProxy[1];
        Uri streamUri = new SmbProxy(pcloudUri) {
            Uri callStart() {
                holder[0] = this;
                return start();
            }
        }.callStart();
        try {
            assertTrue("Stream URI must start with http://localhost:", streamUri != null && streamUri.toString().startsWith("http://localhost:"));
            assertTrue("Stream URI must not contain raw fragment #", streamUri.getFragment() == null);
            assertTrue("Stream URI must not contain raw query ?", streamUri.getQuery() == null);
            assertFalse("Stream URI must not double-encode spaces (%2520)", streamUri.toString().contains("%2520"));
            assertFalse("Stream URI must not double-encode # (%2523)", streamUri.toString().contains("%2523"));
        } finally {
            if (holder[0] != null) {
                holder[0].stop();
            }
        }
    }
}
