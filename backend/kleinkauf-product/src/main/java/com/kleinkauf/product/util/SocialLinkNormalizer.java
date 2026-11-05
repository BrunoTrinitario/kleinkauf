package com.kleinkauf.product.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/*
Turns what the administrator types for a social network into the profile link shown on the storefront:
- Just the user ("mitienda" or "@mitienda"): the profile link is built (https://www.instagram.com/mitienda).
- A link: it must be from that network (instagram.com for Instagram, x.com or twitter.com for X...). It is shown to
  every customer, so it cannot point to any other site. Always https.
Returns null if the value is not valid for that network.
 */
public final class SocialLinkNormalizer {

    public enum Network {
        INSTAGRAM("https://www.instagram.com/", Set.of("instagram.com")),
        X("https://x.com/", Set.of("x.com", "twitter.com")),
        FACEBOOK("https://www.facebook.com/", Set.of("facebook.com", "fb.com", "fb.me")),
        TIKTOK("https://www.tiktok.com/@", Set.of("tiktok.com"));

        private final String profilePrefix;
        private final Set<String> domains;

        Network(String profilePrefix, Set<String> domains) {
            this.profilePrefix = profilePrefix;
            this.domains = domains;
        }
    }

    public static final int MAX_LENGTH = 255;

    // Users of the four networks: letters, digits, dot, underscore and dash
    private static final Pattern HANDLE = Pattern.compile("^[A-Za-z0-9._-]{1,60}$");

    private SocialLinkNormalizer() {
    }

    public static String normalize(Network network, String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) {
            return null;
        }
        String handle = clean.startsWith("@") ? clean.substring(1) : clean;
        if (HANDLE.matcher(handle).matches() && !handle.contains("..") && !looksLikeDomain(handle)) {
            return network.profilePrefix + handle;
        }

        String link = clean.matches("(?i)^https?://.*") ? clean : "https://" + clean;
        URI uri;
        try {
            uri = new URI(link);
        } catch (URISyntaxException e) {
            return null;
        }
        String host = uri.getHost();
        if (host == null || uri.getUserInfo() != null || uri.getPort() != -1) {
            return null;
        }
        if (!fromNetwork(network, host.toLowerCase(Locale.ROOT))) {
            return null;
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        String normalized = "https://" + host.toLowerCase(Locale.ROOT) + path + query;
        return normalized.length() > MAX_LENGTH ? null : normalized;
    }

    // instagram.com, www.instagram.com, m.facebook.com, vm.tiktok.com... but not instagram.com.evil.io
    private static boolean fromNetwork(Network network, String host) {
        for (String domain : network.domains) {
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    // "instagram.com/mitienda" without https is a link, not a user
    private static boolean looksLikeDomain(String value) {
        return value.matches("(?i)^[a-z0-9-]+(\\.[a-z0-9-]+)*\\.(com|me|net|org|io|ar)$");
    }
}
