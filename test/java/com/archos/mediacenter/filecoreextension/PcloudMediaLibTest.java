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
}
