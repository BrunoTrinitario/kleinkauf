package com.kleinkauf.common.files;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ImageFilesTest {

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
        return result;
    }

    @Test
    void extension_detectsTheTypeByContent() {
        assertEquals("png", ImageFiles.extension(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0)));
        assertEquals("jpg", ImageFiles.extension(bytes(0xFF, 0xD8, 0xFF, 0xE0)));
        assertEquals("gif", ImageFiles.extension("GIF89a".getBytes()));
        assertEquals("webp", ImageFiles.extension("RIFF\0\0\0\0WEBPVP8 ".getBytes()));
    }

    @Test
    void extension_rejectsSvgTextAndTruncatedFiles() {
        assertNull(ImageFiles.extension("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes()));
        assertNull(ImageFiles.extension("just text".getBytes()));
        assertNull(ImageFiles.extension(bytes(0x89, 'P', 'N')));
        assertNull(ImageFiles.extension(new byte[0]));
        assertNull(ImageFiles.extension(null));
    }
}
