package com.kleinkauf.common.files;

/*
Detects an image type from its first bytes (not the file name or the Content-Type, which the client chooses).
Only PNG, JPG, GIF and WEBP: SVG is not accepted because it can carry scripts.
 */
public final class ImageFiles {

    private ImageFiles() {
    }

    // File extension for the image ("png", "jpg", "gif" or "webp"), or null if it is none of them
    public static String extension(byte[] bytes) {
        if (startsWith(bytes, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return "png";
        if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF)) return "jpg";
        if (startsWith(bytes, 0, 'G', 'I', 'F', '8')) return "gif";
        if (startsWith(bytes, 0, 'R', 'I', 'F', 'F') && startsWith(bytes, 8, 'W', 'E', 'B', 'P')) return "webp";
        return null;
    }

    private static boolean startsWith(byte[] bytes, int offset, int... signature) {
        if (bytes == null || bytes.length < offset + signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[offset + i] & 0xFF) != signature[i]) return false;
        }
        return true;
    }
}
