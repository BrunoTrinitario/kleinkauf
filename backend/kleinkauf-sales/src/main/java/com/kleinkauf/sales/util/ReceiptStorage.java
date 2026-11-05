package com.kleinkauf.sales.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/*
Receipts on disk, one folder per store and order: <receipts.path>/<tenant>/<number>/<uuid>.<ext>.
They are not served as static files: only the administrator downloads them through the API.
The type is recognized by the file's first bytes, not by the content-type the client sends.
 */
@Component
public class ReceiptStorage {

    public enum FileType {
        JPG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        PDF("application/pdf", "pdf");

        public final String contentType;
        public final String extension;

        FileType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }
    }

    @Value("${receipts.path:uploads/sales/receipts}")
    private String receiptsPath;

    // null if it is not JPG, PNG or PDF
    public static FileType detect(byte[] bytes) {
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) return FileType.JPG;
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return FileType.PNG;
        if (startsWith(bytes, 0x25, 0x50, 0x44, 0x46, 0x2D)) return FileType.PDF;
        return null;
    }

    // Returns the relative path stored in PaymentReceipt
    public String save(String tenant, String orderNumber, byte[] bytes, FileType type) {
        String relative = tenant + "/" + orderNumber + "/" + UUID.randomUUID() + "." + type.extension;
        Path file = resolve(relative);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el comprobante", e);
        }
        return relative;
    }

    // To roll back if the order could not be saved
    public void deleteQuietly(String relative) {
        try {
            Files.deleteIfExists(resolve(relative));
        } catch (IOException ignored) {
        }
    }

    public Resource load(String relative) {
        return new FileSystemResource(resolve(relative));
    }

    // This component builds the path (tenant, number and uuid), but it is still checked that it does not leave the folder
    private Path resolve(String relative) {
        Path base = Paths.get(receiptsPath).toAbsolutePath().normalize();
        Path file = base.resolve(relative).normalize();
        if (!file.startsWith(base)) {
            throw new IllegalArgumentException("Ruta de comprobante invalida");
        }
        return file;
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes == null || bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) return false;
        }
        return true;
    }
}
