package com.kleinkauf.product;

import com.kleinkauf.product.util.SocialLinkNormalizer;
import com.kleinkauf.product.util.SocialLinkNormalizer.Network;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SocialLinkNormalizerTest {

    @Test
    void justTheUser_buildsTheProfileLink() {
        assertEquals("https://www.instagram.com/casa.roble", SocialLinkNormalizer.normalize(Network.INSTAGRAM, "@casa.roble"));
        assertEquals("https://x.com/casaroble", SocialLinkNormalizer.normalize(Network.X, "casaroble"));
        assertEquals("https://www.facebook.com/casa-roble", SocialLinkNormalizer.normalize(Network.FACEBOOK, " casa-roble "));
        assertEquals("https://www.tiktok.com/@casa_roble", SocialLinkNormalizer.normalize(Network.TIKTOK, "@casa_roble"));
    }

    @Test
    void linksOfTheNetwork_withOrWithoutHttps_alwaysEndInHttps() {
        assertEquals("https://www.instagram.com/casaroble/",
                SocialLinkNormalizer.normalize(Network.INSTAGRAM, "http://www.instagram.com/casaroble/"));
        assertEquals("https://instagram.com/casaroble", SocialLinkNormalizer.normalize(Network.INSTAGRAM, "instagram.com/casaroble"));
        assertEquals("https://twitter.com/casaroble", SocialLinkNormalizer.normalize(Network.X, "https://twitter.com/casaroble"));
        assertEquals("https://m.facebook.com/profile.php?id=123",
                SocialLinkNormalizer.normalize(Network.FACEBOOK, "https://m.facebook.com/profile.php?id=123"));
        assertEquals("https://vm.tiktok.com/ZMabc/", SocialLinkNormalizer.normalize(Network.TIKTOK, "https://vm.tiktok.com/ZMabc/"));
    }

    @Test
    void otherSitesOrOtherNetworks_areNotValid() {
        assertNull(SocialLinkNormalizer.normalize(Network.INSTAGRAM, "https://www.facebook.com/casaroble"));
        assertNull(SocialLinkNormalizer.normalize(Network.INSTAGRAM, "https://instagram.com.phishing.io/casa"));
        assertNull(SocialLinkNormalizer.normalize(Network.INSTAGRAM, "https://notinstagram.com/casa"));
        assertNull(SocialLinkNormalizer.normalize(Network.X, "javascript:alert(1)"));
        assertNull(SocialLinkNormalizer.normalize(Network.TIKTOK, "https://user:pass@tiktok.com/@casa"));
        assertNull(SocialLinkNormalizer.normalize(Network.TIKTOK, "https://tiktok.com:8443/@casa"));
        assertNull(SocialLinkNormalizer.normalize(Network.FACEBOOK, "casa roble"));
        assertNull(SocialLinkNormalizer.normalize(Network.INSTAGRAM, "https://instagram.com/" + "a".repeat(300)));
    }

    @Test
    void blank_isNull() {
        assertNull(SocialLinkNormalizer.normalize(Network.INSTAGRAM, null));
        assertNull(SocialLinkNormalizer.normalize(Network.INSTAGRAM, "   "));
    }
}
